#!/usr/bin/env python3
"""Synthesises every Arsenal sound effect, encodes it to Ogg Vorbis and writes sounds.json. Pure Python DSP (no
numpy); encoding uses an ffmpeg with libvorbis (found automatically, see find_ffmpeg). No recordings are used.

A gunshot is built the way it happens: a pressure pulse and the supersonic crack at t = 0, the noisy muzzle blast,
a low falling thump for the body, the action cycling a few tens of milliseconds later (or a pump / bolt much later),
early reflections off the ground, then the environment answering: a darkening noise tail and slapback echoes off
distant terrain. Everything goes through a soft-clipping stage, like a recording that is too loud for the mic, which
is most of what makes a shot sound heavy. Every gun has its own parameters (GUN_SHOTS); each sound gets three
variants that differ in the random parts so repeated shots don't sound identical.

Players further away hear a separate 'distant' sound (duller, echoey, with the crack arriving before the boom); the
server picks near or far per listener (ModSounds.broadcast).

Run:  py tools/gen_sounds.py            -> assets/arsenal/sounds/**.ogg + sounds.json (+ build/sounds/*.wav)
      py tools/gen_sounds.py preview    -> also build/sounds_preview.png (level envelope + band energies)
      py tools/gen_sounds.py ak47_fire  -> only the sounds whose file stem contains 'ak47_fire' (quick iterations)
"""
import glob
import json
import math
import os
import random
import shutil
import subprocess
import sys
import wave
import zlib
from concurrent.futures import ProcessPoolExecutor

SR = 44100
TAU = 2.0 * math.pi
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'arsenal'))
SOUND_DIR = os.path.join(ASSETS, 'sounds')
BUILD = os.path.normpath(os.path.join(HERE, '..', 'build', 'sounds'))


# =====================================================================================================
# DSP
# =====================================================================================================

def ns(seconds):
    return max(1, int(round(seconds * SR)))


def at_index(seconds):
    return int(round(seconds * SR))


def add(dst, src, at=0.0, gain=1.0):
    o = at_index(at)
    n = min(len(src), len(dst) - o)
    if n <= 0:
        return
    dst[o:o + n] = [a + b * gain for a, b in zip(dst[o:o + n], src[:n])]


def mul(a, b):
    return [x * y for x, y in zip(a, b)]


def white(n, rng):
    r = rng.random
    return [2.0 * r() - 1.0 for _ in range(n)]


def env(n, attack=0.0, decay=None, hold=0.0, t60=None):
    """Linear attack, optional hold, then exponential decay (time constant `decay`, or `t60` seconds to -60 dB)."""
    if t60 is not None:
        decay = t60 / 6.9078
    a = int(attack * SR)
    h = int(hold * SR)
    k = math.exp(-1.0 / (decay * SR)) if decay else 1.0
    out = [0.0] * n
    v = 1.0
    for i in range(n):
        if i < a:
            out[i] = (i + 1.0) / a
        elif i < a + h:
            out[i] = 1.0
        else:
            out[i] = v
            v *= k
    return out


def biquad(x, kind, f, q=0.7071, gain_db=0.0):
    """RBJ cookbook filters: lp, hp, bp (0 dB peak), peak, low / high shelf."""
    f = max(10.0, min(f, SR * 0.45))
    w = TAU * f / SR
    cw, sw = math.cos(w), math.sin(w)
    alpha = sw / (2.0 * q)
    A = 10.0 ** (gain_db / 40.0)
    if kind == 'lp':
        b0, b1, b2, a0, a1, a2 = (1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha
    elif kind == 'hp':
        b0, b1, b2, a0, a1, a2 = (1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha
    elif kind == 'bp':
        b0, b1, b2, a0, a1, a2 = alpha, 0.0, -alpha, 1 + alpha, -2 * cw, 1 - alpha
    elif kind == 'peak':
        b0, b1, b2 = 1 + alpha * A, -2 * cw, 1 - alpha * A
        a0, a1, a2 = 1 + alpha / A, -2 * cw, 1 - alpha / A
    else:
        sa = 2.0 * math.sqrt(A) * alpha
        if kind == 'low':
            b0 = A * ((A + 1) - (A - 1) * cw + sa)
            b1 = 2 * A * ((A - 1) - (A + 1) * cw)
            b2 = A * ((A + 1) - (A - 1) * cw - sa)
            a0 = (A + 1) + (A - 1) * cw + sa
            a1 = -2 * ((A - 1) + (A + 1) * cw)
            a2 = (A + 1) + (A - 1) * cw - sa
        else:
            b0 = A * ((A + 1) + (A - 1) * cw + sa)
            b1 = -2 * A * ((A - 1) + (A + 1) * cw)
            b2 = A * ((A + 1) + (A - 1) * cw - sa)
            a0 = (A + 1) - (A - 1) * cw + sa
            a1 = 2 * ((A - 1) - (A + 1) * cw)
            a2 = (A + 1) - (A - 1) * cw - sa
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    y = [0.0] * len(x)
    z1 = z2 = 0.0
    for i, v in enumerate(x):
        o = b0 * v + z1
        z1 = b1 * v - a1 * o + z2
        z2 = b2 * v - a2 * o
        y[i] = o
    return y


def lp_glide(x, f0, f1, tau, poles=2):
    """One-pole low-passes whose cutoff glides exponentially from f0 to f1 (time constant tau): a darkening tail."""
    y = x
    for _ in range(poles):
        out = [0.0] * len(y)
        s = 0.0
        a = 0.0
        for i, v in enumerate(y):
            if i & 31 == 0:
                f = f1 + (f0 - f1) * math.exp(-i / (tau * SR))
                a = math.exp(-TAU * f / SR)
            s = v + a * (s - v)
            out[i] = s
        y = out
    return y


def bp_glide(x, f0, f1, tau, q=1.0):
    """A band-pass whose centre glides from f0 to f1 (whooshes, rocket motors)."""
    y = [0.0] * len(x)
    z1 = z2 = 0.0
    b0 = b2 = a1 = a2 = 0.0
    for i, v in enumerate(x):
        if i & 63 == 0:
            f = f1 + (f0 - f1) * math.exp(-i / (tau * SR))
            w = TAU * min(f, SR * 0.45) / SR
            alpha = math.sin(w) / (2 * q)
            a0 = 1 + alpha
            b0, b2, a1, a2 = alpha / a0, -alpha / a0, -2 * math.cos(w) / a0, (1 - alpha) / a0
        o = b0 * v + z1
        z1 = -a1 * o + z2
        z2 = b2 * v - a2 * o
        y[i] = o
    return y


def chirp(n, f0, f1, glide, phase=0.0):
    """A sine whose frequency glides exponentially from f0 to f1 (time constant `glide`)."""
    out = [0.0] * n
    ph = phase
    k = TAU / SR
    g = math.exp(-1.0 / (glide * SR))
    d = f0 - f1
    for i in range(n):
        ph += k * (f1 + d)
        d *= g
        out[i] = math.sin(ph)
    return out


def rising(n, f0, f1, power=2.0):
    """A sine sweeping up from f0 to f1 over n samples (capacitor whine)."""
    out = [0.0] * n
    ph = 0.0
    for i in range(n):
        t = i / float(n)
        ph += TAU * (f0 + (f1 - f0) * t ** power) / SR
        out[i] = math.sin(ph)
    return out


def ring(n, freqs, taus, amps, rng):
    """Damped sine modes: struck metal."""
    out = [0.0] * n
    for f, tau, a in zip(freqs, taus, amps):
        w = TAU * f / SR
        if w >= math.pi:
            continue
        ph = rng.random() * TAU
        c2 = 2.0 * math.cos(w)
        s1, s0 = math.sin(ph - w), math.sin(ph - 2 * w)
        k = math.exp(-1.0 / (tau * SR))
        e = a
        for i in range(min(n, int(tau * SR * 7))):
            s = c2 * s1 - s0
            s0, s1 = s1, s
            out[i] += e * s
            e *= k
    return out


def friedlander(T, b=1.8, length=7.0):
    """The blast-wave pressure pulse: a sharp positive peak, then the negative phase."""
    n = ns(T * length)
    return [(1.0 - t / T) * math.exp(-b * t / T) for t in (i / float(SR) for i in range(n))]


def wobble(n, rate, rng):
    """Smooth random modulation in -1..1 changing about `rate` times a second."""
    step = max(1, int(SR / rate))
    pts = [rng.random() * 2 - 1 for _ in range(n // step + 3)]
    out = [0.0] * n
    for i in range(n):
        k, r = divmod(i, step)
        t = r / float(step)
        t = t * t * (3 - 2 * t)
        out[i] = pts[k] + (pts[k + 1] - pts[k]) * t
    return out


def crackle(n, rng, rate, decay, start=0.0, hp=2500.0, level=1.0, burst=0.0015):
    """Sparse random ticks thinning out over time: shrapnel, sparks, burning wood."""
    out = [0.0] * n
    t = start
    m = ns(burst)
    while True:
        density = rate * math.exp(-(t - start) / decay)
        if density < 0.3:
            break
        t += rng.expovariate(density)
        i = at_index(t)
        if i >= n:
            break
        amp = level * math.exp(-(t - start) / decay) * (0.3 + 0.7 * rng.random())
        for j in range(min(m, n - i)):
            out[i + j] += amp * (rng.random() * 2 - 1) * (1 - j / float(m))
    return biquad(out, 'hp', hp)


def saturate(x, drive):
    if drive <= 0:
        return x
    k = 1.0 / math.tanh(drive)
    return [math.tanh(drive * v) * k for v in x]


def normalize(x, peak):
    m = max(1e-9, max(abs(v) for v in x))
    g = peak / m
    return [v * g for v in x]


def fade(x, seconds):
    n = min(len(x), ns(seconds))
    for i in range(n):
        x[len(x) - n + i] *= 1.0 - (i + 1.0) / n
    return x


def master(x, peak, drive=0.0, hp=25.0):
    """DC/rumble blocker, soft clip against the peak, level, end fade."""
    x = biquad(x, 'hp', hp, 0.6)
    x = normalize(x, 1.0)
    x = saturate(x, drive)
    x = normalize(x, peak)
    return fade(x, 0.04)


# =====================================================================================================
# building blocks
# =====================================================================================================

def jitter(rng, v, amount=0.06):
    return v * (1.0 + (rng.random() * 2.0 - 1.0) * amount)


def burst(rng, seconds, tau, hp=None, lp=None):
    m = ns(seconds)
    x = mul(white(m, rng), env(m, 0.00003, tau))
    if hp:
        x = biquad(x, 'hp', hp, 0.7)
    if lp:
        x = biquad(x, 'lp', lp, 0.7)
    return x


def clack(rng, freqs, tau=0.03, click=1.0, thump=0.6, thump_f=150.0):
    """A metal part slamming home: click, ringing modes, a little thump."""
    n = ns(tau * 7 + 0.03)
    out = [0.0] * n
    add(out, burst(rng, 0.004, 0.0006, hp=1800), 0, click)
    fs = [jitter(rng, f, 0.05) for f in freqs]
    taus = [tau * (0.55 + 0.8 * rng.random()) for _ in freqs]
    amps = [0.6 * (0.72 ** k) for k in range(len(freqs))]
    add(out, ring(n, fs, taus, amps, rng))
    if thump:
        m = ns(0.06)
        add(out, mul(chirp(m, thump_f * 1.5, thump_f, 0.01), env(m, 0.0005, 0.014)), 0, thump)
    return out


def scrape(rng, seconds, fc, q=1.0, grain=90.0, rise=0.3, fc_end=None):
    """Metal or polymer sliding on metal: band-passed noise with a rough, grainy envelope."""
    n = ns(seconds)
    x = white(n, rng)
    x = bp_glide(x, fc, fc_end or fc, seconds / 2.0, q) if fc_end else biquad(x, 'bp', fc, q)
    rough = wobble(n, grain, rng)
    out = []
    for i, (a, r) in enumerate(zip(x, rough)):
        e = min(1.0, i / (rise * n + 1.0)) * min(1.0, (n - i) / (0.25 * n + 1.0))
        out.append(a * e * (0.5 + 0.5 * abs(r)) * 1.6)
    return out


def rattle(rng, seconds, count, fmin=2500.0, fmax=6000.0):
    """Loose cartridges shaking in a magazine."""
    out = [0.0] * ns(seconds)
    for _ in range(count):
        t = rng.random() * seconds * 0.85
        add(out, ring(ns(0.02), [rng.uniform(fmin, fmax)], [0.004], [1.0], rng), t, rng.uniform(0.2, 0.6))
    return out


def action(rng, freqs, tau):
    """The mechanism cycling after a shot: bolt back (click), bolt home (bigger clack)."""
    out = [0.0] * ns(tau * 9 + 0.06)
    add(out, clack(rng, [f * 1.25 for f in freqs], tau * 0.5, click=0.6, thump=0.0), 0, 0.45)
    add(out, clack(rng, freqs, tau, click=1.0, thump=0.35, thump_f=210.0), jitter(rng, 0.028, 0.15), 1.0)
    return out


def tail_noise(rng, n, t60, lp=(2600.0, 600.0), rate=9.0, attack=0.006):
    """The environment answering: noise that decays and gets darker, with a slow rumble in its level."""
    t = mul(white(n, rng), env(n, attack, t60=t60))
    t = lp_glide(t, lp[0], lp[1], t60 / 3.0)
    w = wobble(n, rate, rng)
    return [a * (0.72 + 0.28 * v) for a, v in zip(t, w)]


# =====================================================================================================
# gunshots
# =====================================================================================================

def gunshot(seed, length=1.3, delay=0.0,
            pulse=1.5, pulse_T=0.0012, crack=0.8, crack_hp=2200.0, crack_tau=0.0008,
            blast=1.0, blast_tau=0.018, blast_lp=6500.0, blast_peak=(1000.0, 5.0), blast_q=0.8,
            body=1.2, body_f=(220.0, 60.0), body_glide=0.02, body_tau=0.05,
            sub=0.0, sub_f=48.0, sub_tau=0.1,
            mech=0.14, mech_at=0.035, mech_freqs=(1800.0, 2700.0, 3900.0), mech_tau=0.02,
            tail=0.06, t60=1.1, tail_lp=(2600.0, 600.0), echo=(0.13, 0.27, 0.45), echo_gain=0.13, echo_lp=1400.0,
            reflect=0.35, drive=2.4, peak=0.89, extras=(), after=()):
    """The direct bang (pulse, crack, blast, body, sub, ground reflections, `extras`) is soft-clipped to a peak of 1;
    then the action (`mech`, peak level), the environment tail (`tail`, RMS level), slapback echoes (`echo_gain`)
    and `after` layers (pump, bolt...) are added at those levels relative to it."""
    rng = random.Random(seed)
    n = ns(length)
    direct = [0.0] * n
    # the supersonic crack arrives first (distant listeners hear it before the boom: `delay`)
    if crack:
        c = burst(rng, 0.012, jitter(rng, crack_tau))
        c = biquad(biquad(c, 'hp', jitter(rng, crack_hp), 0.7), 'hp', crack_hp, 0.7)
        add(direct, c, 0, crack * 2.5)
    # pressure pulse: the punch
    add(direct, friedlander(jitter(rng, pulse_T)), delay, pulse)
    # muzzle blast: the noisy bark
    m = ns(min(length, blast_tau * 10))
    b = mul(white(m, rng), env(m, 0.0002, jitter(rng, blast_tau)))
    b = biquad(b, 'lp', jitter(rng, blast_lp), 0.7)
    b = biquad(b, 'peak', jitter(rng, blast_peak[0]), blast_q, blast_peak[1])
    b = biquad(b, 'low', 200.0, 0.7, 4.0)
    add(direct, b, delay, blast * 1.4)
    # body: a low falling thump, roughened so it isn't a clean kick drum
    m = ns(min(length, body_tau * 8))
    s = chirp(m, jitter(rng, body_f[0]), jitter(rng, body_f[1]), body_glide)
    e = env(m, 0.001, jitter(rng, body_tau))
    grit = biquad(white(m, rng), 'lp', 320.0)
    add(direct, [a * b_ * (1.0 + 1.6 * g) for a, b_, g in zip(s, e, grit)], delay, body)
    if sub:
        m = ns(min(length, sub_tau * 7))
        add(direct, mul(chirp(m, sub_f * 1.4, sub_f, 0.03), env(m, 0.004, jitter(rng, sub_tau))), delay, sub)
    # early reflections: the ground a few ms later, then whatever is close by
    head = biquad(direct[:ns(0.05)], 'lp', 3800.0)
    for d, g in ((0.0045, 0.55), (0.017, 0.33), (0.029, 0.22)):
        add(direct, head, jitter(rng, d, 0.15), g * reflect)
    for fn in extras:
        fn(direct, rng)
    direct = master(direct, 1.0, drive)
    out = list(direct)
    # the action cycling
    if mech:
        add(out, action(rng, mech_freqs, mech_tau), delay + jitter(rng, mech_at, 0.08), mech)
    # the environment: a darkening tail and slapback echoes off terrain
    if tail:
        t = tail_noise(rng, n, jitter(rng, t60, 0.08), tail_lp)
        ref = t[ns(0.01):ns(0.05)]
        rms = math.sqrt(sum(v * v for v in ref) / len(ref)) or 1.0
        add(out, t, delay + 0.002, tail / rms)
    if echo:
        slap = biquad(biquad(direct[:ns(0.3)], 'lp', echo_lp), 'lp', echo_lp)
        for k, d in enumerate(echo):
            add(out, slap, delay + jitter(rng, d, 0.1), echo_gain * (0.62 ** k))
    for fn in after:
        fn(out, rng)
    out = normalize(biquad(out, 'hp', 25.0, 0.6), peak)
    return fade(out, max(0.04, 0.1 * length))


def pump_action(at):
    """Racking a pump shotgun after the shot: slide back, slide home."""
    def fn(out, rng):
        add(out, scrape(rng, 0.075, 1600.0, 1.2, fc_end=2200.0), at, 0.06)
        add(out, clack(rng, (1300.0, 2100.0, 3000.0), 0.025, click=0.8, thump=0.5, thump_f=160.0), at + 0.075, 0.09)
        add(out, scrape(rng, 0.06, 1900.0, 1.2), at + 0.14, 0.055)
        add(out, clack(rng, (1400.0, 2200.0, 3200.0, 4500.0), 0.03, click=1.0, thump=0.8, thump_f=140.0), at + 0.2,
            0.13)
    return fn


def bolt_cycle(at):
    """Working a bolt action: lift, pull, push, lock."""
    def fn(out, rng):
        add(out, clack(rng, (2400.0, 3600.0), 0.01, click=0.8, thump=0.0), at, 0.06)
        add(out, scrape(rng, 0.1, 2000.0, 1.6), at + 0.04, 0.06)
        add(out, clack(rng, (1900.0, 2900.0), 0.012, click=0.6, thump=0.0), at + 0.14, 0.05)
        add(out, scrape(rng, 0.09, 1800.0, 1.6), at + 0.26, 0.06)
        add(out, clack(rng, (1500.0, 2300.0, 3400.0), 0.03, click=1.0, thump=0.5, thump_f=170.0), at + 0.35, 0.11)
        add(out, clack(rng, (2600.0, 3900.0), 0.012, click=0.7, thump=0.0), at + 0.42, 0.06)
    return fn


def rocket_motor(at):
    """The RPG's sustainer lighting a few metres out: a roar that tears away."""
    def fn(out, rng):
        n = ns(2.0)
        roar = bp_glide(white(n, rng), 950.0, 320.0, 0.45, 0.9)
        e = env(n, 0.03, 0.42)
        grain = wobble(n, 45.0, rng)
        roar = saturate([a * b * (0.6 + 0.4 * abs(g)) * 2.0 for a, b, g in zip(roar, e, grain)], 1.5)
        add(out, fade(roar, 0.5), at, 0.45)
        add(out, crackle(n, rng, 180.0, 0.35, hp=1800.0, level=0.5), at, 0.12)
    return fn


def ratchet(at, clicks=3, spacing=0.03):
    def fn(out, rng):
        for k in range(clicks):
            add(out, clack(rng, (2800.0, 4100.0), 0.006, click=0.7, thump=0.0), at + k * spacing, 0.06)
    return fn


def rail_zap(out, rng):
    """The capacitor dump: a falling FM zap under the crack (goes through the soft clip with the shot)."""
    n = ns(0.45)
    car = mod = 0.0
    zap = []
    for i in range(n):
        t = i / float(SR)
        fc = 150.0 + 3000.0 * math.exp(-t / 0.07)
        car += TAU * fc / SR
        mod += TAU * fc * 1.41 / SR
        zap.append(math.sin(car + 2.6 * math.sin(mod)) * math.exp(-t / 0.12))
    add(out, zap, 0.0, 0.7)


def rail_ring(out, rng):
    """The rails ringing like a struck bar, and sparks crackling off them."""
    n2 = ns(2.5)
    rails = ring(n2, [1250.0, 2830.0, 4170.0, 6210.0], [0.55, 0.42, 0.3, 0.2], [0.3, 0.22, 0.14, 0.08], rng)
    add(out, fade(rails, 0.6), 0.004, 0.3)
    add(out, crackle(ns(1.5), rng, 90.0, 0.4, start=0.02, hp=3000.0, level=0.6), 0.0, 0.12)


GUN_SHOTS = {
    # rifles
    'ak47': dict(pulse=1.6, pulse_T=0.0013, crack=0.7, crack_hp=1900, blast_tau=0.02, blast_lp=6000,
                 blast_peak=(900, 5), body=1.3, body_f=(210, 58), body_glide=0.018, body_tau=0.05, sub=0.3,
                 sub_tau=0.09, mech=0.17, mech_at=0.033, mech_freqs=(1400, 2150, 3100, 4300), mech_tau=0.026,
                 tail=0.065, t60=1.5, echo=(0.13, 0.27, 0.45), echo_gain=0.14, drive=2.4, length=1.6),
    'm4a1': dict(pulse=1.3, pulse_T=0.001, crack=1.0, crack_hp=2600, crack_tau=0.0006, blast_tau=0.013,
                 blast_lp=7800, blast_peak=(1350, 5), body=1.0, body_f=(250, 72), body_glide=0.014, body_tau=0.035,
                 sub=0.15, sub_tau=0.07, mech=0.13, mech_at=0.027, mech_freqs=(2200, 3300, 4700, 6100),
                 mech_tau=0.02, tail=0.055, t60=1.3, echo=(0.12, 0.24, 0.39), echo_gain=0.12, drive=2.2,
                 length=1.45),
    'scar_h': dict(pulse=1.8, pulse_T=0.0014, crack=0.95, crack_hp=2100, blast_tau=0.024, blast_lp=6600,
                   blast_peak=(820, 6), body=1.45, body_f=(190, 52), body_tau=0.06, sub=0.45, sub_f=46, sub_tau=0.1,
                   mech=0.14, mech_at=0.038, mech_freqs=(1600, 2450, 3500, 4900), mech_tau=0.024,
                   tail=0.07, t60=1.7, echo=(0.15, 0.3, 0.5), echo_gain=0.15, drive=2.6, length=1.8),
    'aug': dict(pulse=1.3, pulse_T=0.001, crack=1.0, crack_hp=2700, blast_tau=0.012, blast_lp=8200,
                blast_peak=(1500, 4.5), body=0.95, body_f=(265, 78), body_tau=0.032, sub=0.1, sub_tau=0.06,
                mech=0.16, mech_at=0.024, mech_freqs=(1700, 2500, 3600), mech_tau=0.016,
                tail=0.05, t60=1.25, echo=(0.12, 0.24, 0.38), echo_gain=0.12, drive=2.1, length=1.4),
    # pistols
    'glock17': dict(pulse=1.2, pulse_T=0.0009, crack=0.8, crack_hp=2400, crack_tau=0.0007, blast_tau=0.01,
                    blast_lp=7500, blast_peak=(1500, 4), body=0.8, body_f=(300, 95), body_glide=0.012,
                    body_tau=0.025, mech=0.15, mech_at=0.019, mech_freqs=(2600, 3700, 5200, 6800), mech_tau=0.018,
                    tail=0.045, t60=0.9, tail_lp=(3000, 900), echo=(0.11, 0.22), echo_gain=0.1, drive=2.0,
                    length=1.0),
    'm9': dict(pulse=1.25, pulse_T=0.00095, crack=0.75, crack_hp=2300, blast_tau=0.011, blast_lp=7200,
               blast_peak=(1400, 4), body=0.85, body_f=(285, 90), body_glide=0.012, body_tau=0.026,
               mech=0.15, mech_at=0.02, mech_freqs=(2300, 3400, 4800, 6300), mech_tau=0.02,
               tail=0.045, t60=0.95, tail_lp=(3000, 900), echo=(0.11, 0.23), echo_gain=0.1, drive=2.0, length=1.05),
    'm1911': dict(pulse=1.5, pulse_T=0.0012, crack=0.45, crack_hp=1800, blast_tau=0.014, blast_lp=6000,
                  blast_peak=(1000, 5), body=1.1, body_f=(240, 70), body_tau=0.035, sub=0.15, sub_f=55, sub_tau=0.06,
                  mech=0.16, mech_at=0.023, mech_freqs=(1900, 2800, 4100, 5600), mech_tau=0.022,
                  tail=0.05, t60=1.0, echo=(0.12, 0.24), echo_gain=0.11, drive=2.2, length=1.1),
    'deagle': dict(pulse=2.0, pulse_T=0.0016, crack=1.0, crack_hp=2000, blast_tau=0.022, blast_lp=6500,
                   blast_peak=(850, 6), body=1.5, body_f=(200, 52), body_tau=0.055, sub=0.5, sub_f=46, sub_tau=0.1,
                   mech=0.15, mech_at=0.03, mech_freqs=(1500, 2300, 3300, 4600), mech_tau=0.03,
                   tail=0.07, t60=1.7, echo=(0.14, 0.29, 0.47), echo_gain=0.15, drive=2.7, length=1.8),
    # snipers
    'barrett_m82': dict(pulse=2.6, pulse_T=0.0024, crack=1.0, crack_hp=1600, crack_tau=0.0012, blast_tau=0.036,
                        blast_lp=5000, blast_peak=(600, 6), body=1.8, body_f=(160, 40), body_glide=0.03,
                        body_tau=0.1, sub=1.0, sub_f=40, sub_tau=0.18, mech=0.12, mech_at=0.07,
                        mech_freqs=(1100, 1700, 2600, 3700), mech_tau=0.04, tail=0.1, t60=2.6, tail_lp=(2000, 400),
                        echo=(0.18, 0.36, 0.62, 0.95), echo_gain=0.2, drive=3.0, length=3.0),
    'svd_dragunov': dict(pulse=2.0, pulse_T=0.0017, crack=1.0, crack_hp=2000, blast_tau=0.026, blast_lp=6000,
                         blast_peak=(780, 6), body=1.5, body_f=(185, 50), body_tau=0.07, sub=0.55, sub_f=44,
                         sub_tau=0.12, mech=0.13, mech_at=0.048, mech_freqs=(1450, 2200, 3200), mech_tau=0.03,
                         tail=0.085, t60=1.9, echo=(0.16, 0.33, 0.55, 0.85), echo_gain=0.18, drive=2.8, length=2.3),
    'awp': dict(pulse=2.3, pulse_T=0.002, crack=1.2, crack_hp=2200, crack_tau=0.0009, blast_tau=0.03,
                blast_lp=6500, blast_peak=(740, 6), body=1.7, body_f=(175, 44), body_tau=0.085, sub=0.75, sub_f=42,
                sub_tau=0.15, mech=0.0, tail=0.09, t60=2.1, echo=(0.17, 0.34, 0.58, 0.9), echo_gain=0.19,
                drive=2.9, length=2.3, after=(bolt_cycle(0.78),)),
    # shotguns
    'remington_870': dict(pulse=2.2, pulse_T=0.0022, crack=0.45, crack_hp=1500, blast_tau=0.028, blast_lp=5000,
                          blast_peak=(700, 6), body=1.7, body_f=(170, 46), body_tau=0.075, sub=0.65, sub_f=45,
                          sub_tau=0.12, mech=0.0, tail=0.08, t60=1.4, tail_lp=(2200, 500),
                          echo=(0.15, 0.31, 0.52), echo_gain=0.17, drive=2.9, length=1.8,
                          after=(pump_action(0.42),)),
    'spas12': dict(pulse=2.1, pulse_T=0.0021, crack=0.5, crack_hp=1600, blast_tau=0.026, blast_lp=5200,
                   blast_peak=(720, 6), body=1.65, body_f=(175, 48), body_tau=0.07, sub=0.6, sub_f=45, sub_tau=0.11,
                   mech=0.15, mech_at=0.045, mech_freqs=(1300, 2000, 2900), mech_tau=0.03, tail=0.075, t60=1.6,
                   tail_lp=(2200, 500), echo=(0.15, 0.3, 0.5), echo_gain=0.16, drive=2.8, length=1.8),
    'aa12': dict(pulse=1.9, pulse_T=0.0019, crack=0.5, crack_hp=1600, blast_tau=0.022, blast_lp=5400,
                 blast_peak=(760, 6), body=1.5, body_f=(180, 50), body_tau=0.06, sub=0.5, sub_f=46, sub_tau=0.1,
                 mech=0.16, mech_at=0.032, mech_freqs=(1250, 1900, 2800, 3900), mech_tau=0.028, tail=0.065,
                 t60=1.4, tail_lp=(2200, 500), echo=(0.14, 0.28, 0.46), echo_gain=0.14, drive=2.6, length=1.5),
    'sawed_off': dict(pulse=2.6, pulse_T=0.0026, crack=0.5, crack_hp=1500, blast_tau=0.032, blast_lp=4800,
                      blast_peak=(650, 7), body=1.9, body_f=(150, 42), body_tau=0.085, sub=0.8, sub_f=42,
                      sub_tau=0.13, mech=0.0, tail=0.085, t60=1.5, tail_lp=(2100, 450), echo=(0.15, 0.32, 0.54),
                      echo_gain=0.18, drive=3.1, length=1.8),
    # launchers and the railgun
    'rpg7': dict(pulse=1.8, pulse_T=0.003, crack=0.3, crack_hp=1200, blast_tau=0.035, blast_lp=2600,
                 blast_peak=(420, 6), body=1.5, body_f=(130, 40), body_tau=0.1, sub=0.6, sub_f=40, sub_tau=0.14,
                 mech=0.0, tail=0.07, t60=1.6, tail_lp=(1800, 400), echo=(0.16, 0.34, 0.6), echo_gain=0.14,
                 drive=2.6, length=2.2, after=(rocket_motor(0.09),)),
    'm32_launcher': dict(pulse=1.0, pulse_T=0.002, crack=0.0, blast_tau=0.045, blast_lp=2500, blast_peak=(260, 10),
                         blast_q=2.0, body=1.3, body_f=(160, 75), body_tau=0.05, sub=0.3, sub_f=60, sub_tau=0.08,
                         mech=0.0, tail=0.04, t60=1.0, tail_lp=(1500, 500), echo=(0.14, 0.3), echo_gain=0.1,
                         drive=1.8, length=1.2, after=(ratchet(0.16),)),
    'railgun': dict(pulse=1.6, pulse_T=0.0009, crack=1.6, crack_hp=3200, crack_tau=0.0015, blast_tau=0.018,
                    blast_lp=9000, blast_peak=(2500, 4), body=1.4, body_f=(120, 35), body_glide=0.04, body_tau=0.12,
                    sub=0.6, sub_f=38, sub_tau=0.16, mech=0.0, tail=0.08, t60=2.0, tail_lp=(3500, 600),
                    echo=(0.17, 0.35, 0.6), echo_gain=0.15, drive=2.6, length=2.6, extras=(rail_zap,),
                    after=(rail_ring,)),
}

SUPPRESSED = {
    'pistol': dict(pulse=0.5, pulse_T=0.0015, crack=0.0, blast_tau=0.008, blast_lp=1800, blast_peak=(500, 3),
                   body=0.5, body_f=(380, 180), body_tau=0.015, mech=0.45, mech_at=0.012,
                   mech_freqs=(2600, 3800, 5400), mech_tau=0.016, tail=0.03, t60=0.3, tail_lp=(1500, 600), echo=(),
                   reflect=0.2, drive=1.4, peak=0.55, length=0.45),
    'rifle': dict(pulse=0.6, pulse_T=0.0016, crack=0.25, crack_hp=3500, blast_tau=0.01, blast_lp=2400,
                  blast_peak=(600, 3), body=0.6, body_f=(320, 150), body_tau=0.02, mech=0.5, mech_at=0.014,
                  mech_freqs=(2000, 3000, 4300), mech_tau=0.02, tail=0.035, t60=0.35, tail_lp=(1600, 600), echo=(),
                  reflect=0.2, drive=1.5, peak=0.65, length=0.5),
    'heavy': dict(pulse=0.9, pulse_T=0.0022, crack=0.3, crack_hp=3000, blast_tau=0.014, blast_lp=1600,
                  blast_peak=(400, 4), body=0.9, body_f=(230, 90), body_tau=0.03, mech=0.4, mech_at=0.02,
                  mech_freqs=(1500, 2300, 3300), mech_tau=0.03, tail=0.04, t60=0.5, tail_lp=(1400, 500),
                  echo=(0.14,), echo_gain=0.06, reflect=0.25, drive=1.8, peak=0.72, length=0.7),
}

DISTANT = {
    'small': dict(pulse=0.8, pulse_T=0.002, crack=0.0, blast_tau=0.02, blast_lp=1200, blast_peak=(500, 4), body=0.9,
                  body_f=(200, 80), body_tau=0.04, mech=0.0, tail=0.16, t60=1.2, tail_lp=(1100, 300),
                  echo=(0.2, 0.42, 0.7), echo_gain=0.3, echo_lp=900, reflect=0.5, drive=1.8, peak=0.55, length=1.6),
    'rifle': dict(delay=0.06, pulse=0.9, pulse_T=0.0025, crack=0.8, crack_hp=2200, blast_tau=0.03, blast_lp=1000,
                  blast_peak=(400, 5), body=1.0, body_f=(150, 55), body_tau=0.07, mech=0.0, tail=0.2, t60=1.9,
                  tail_lp=(900, 250), echo=(0.25, 0.55, 0.95), echo_gain=0.32, echo_lp=800, reflect=0.5, drive=2.0,
                  peak=0.6, length=2.4),
    'heavy': dict(delay=0.08, pulse=1.2, pulse_T=0.004, crack=0.6, crack_hp=1800, blast_tau=0.05, blast_lp=700,
                  blast_peak=(300, 6), body=1.3, body_f=(110, 35), body_tau=0.12, sub=0.7, sub_f=35, sub_tau=0.22,
                  mech=0.0, tail=0.24, t60=2.8, tail_lp=(700, 180), echo=(0.3, 0.65, 1.1, 1.6), echo_gain=0.35,
                  echo_lp=600, reflect=0.5, drive=2.2, peak=0.65, length=3.4),
}


def make_shot(params):
    return lambda seed: gunshot(seed, **params)


# =====================================================================================================
# gun handling: dry fire, reloads, headshot
# =====================================================================================================

def foley(seed, parts, length, peak):
    """Mix a list of (at, maker(rng) -> samples, gain) and level it."""
    rng = random.Random(seed)
    out = [0.0] * ns(length)
    for at, maker, gain in parts:
        add(out, maker(rng), at, gain)
    return master(out, peak, 0.0, hp=60.0)


def s_dry_fire(seed):
    return foley(seed, [
        (0.0, lambda r: clack(r, (4200, 6100, 7900), 0.008, click=1.0, thump=0.15, thump_f=300.0), 1.0),
        (0.018, lambda r: clack(r, (5200, 7400), 0.005, click=0.6, thump=0.0), 0.4)], 0.14, 0.5)


def s_headshot(seed):
    return foley(seed, [
        (0.0, lambda r: ring(ns(0.45), [2900, 4150, 5900, 7400], [0.12, 0.09, 0.06, 0.04], [0.6, 0.4, 0.25, 0.15], r),
         1.0),
        (0.0, lambda r: burst(r, 0.05, 0.012, lp=3500.0), 0.8),
        (0.0, lambda r: mul(chirp(ns(0.08), 220.0, 140.0, 0.01), env(ns(0.08), 0.0005, 0.02)), 0.7)], 0.45, 0.7)


def s_mag_out(kind):
    f = {'rifle': (1.0, 1700.0), 'pistol': (1.3, 2400.0), 'heavy': (0.7, 1100.0)}[kind]

    def make(seed):
        k, fc = f
        return foley(seed, [
            (0.0, lambda r: clack(r, (3000 * k, 4500 * k, 6200 * k), 0.006, click=0.8, thump=0.0), 0.5),
            (0.02, lambda r: scrape(r, 0.13 / k, fc, 1.2), 0.6),
            (0.06, lambda r: rattle(r, 0.15, 4), 0.5)], 0.32 if kind != 'heavy' else 0.42,
            {'rifle': 0.5, 'pistol': 0.45, 'heavy': 0.55}[kind])
    return make


def s_mag_in(kind):
    spec = {'rifle': ((950, 1600, 2400, 3500), 0.035, 130.0, 1500.0, 0.09, 0.1, 0.6),
            'pistol': ((1600, 2600, 3800), 0.02, 180.0, 2200.0, 0.05, 0.06, 0.55),
            'heavy': ((700, 1150, 1750, 2600), 0.05, 100.0, 1000.0, 0.12, 0.13, 0.7)}[kind]

    def make(seed):
        freqs, tau, thump_f, fc, slide, at, peak = spec
        return foley(seed, [
            (0.0, lambda r: scrape(r, slide, fc, 1.1), 0.55),
            (at, lambda r: clack(r, freqs, tau, click=1.2, thump=0.9, thump_f=thump_f), 1.0)], at + tau * 7 + 0.05,
            peak)
    return make


def s_charge(kind):
    spec = {'rifle': ((1250, 1950, 2900, 4200), 0.04, 120.0, 2200.0, 0.11, 0.19, 0.7),
            'heavy': ((800, 1300, 2000, 3000), 0.055, 90.0, 1400.0, 0.16, 0.24, 0.75)}[kind]

    def make(seed):
        freqs, tau, thump_f, fc, pull, at, peak = spec
        return foley(seed, [
            (0.0, lambda r: scrape(r, pull, fc * 0.8, 2.0, fc_end=fc * 1.3), 0.6),
            (0.0, lambda r: mul(rising(ns(pull), 900.0, 1800.0), env(ns(pull), 0.02, None)), 0.05),
            (at, lambda r: clack(r, freqs, tau, click=1.3, thump=1.0, thump_f=thump_f), 1.0)], at + tau * 7 + 0.05,
            peak)
    return make


def s_slide(seed):
    return foley(seed, [(0.0, lambda r: clack(r, (1800, 2800, 4100, 5600), 0.028, click=1.3, thump=0.7,
                                               thump_f=160.0), 1.0)], 0.3, 0.65)


def s_bolt(open_):
    def make(seed):
        if open_:
            parts = [(0.0, lambda r: clack(r, (2400, 3600), 0.01, click=0.8, thump=0.0), 0.6),
                     (0.05, lambda r: scrape(r, 0.1, 2000.0, 1.6), 0.6),
                     (0.15, lambda r: clack(r, (1900, 2900), 0.012, click=0.7, thump=0.0), 0.5)]
            return foley(seed, parts, 0.3, 0.55)
        parts = [(0.0, lambda r: scrape(r, 0.09, 1800.0, 1.6), 0.6),
                 (0.1, lambda r: clack(r, (1500, 2300, 3400), 0.03, click=1.0, thump=0.5, thump_f=170.0), 1.0),
                 (0.17, lambda r: clack(r, (2600, 3900), 0.012, click=0.7, thump=0.0), 0.5)]
        return foley(seed, parts, 0.34, 0.62)
    return make


def s_shell(seed):
    return foley(seed, [
        (0.0, lambda r: scrape(r, 0.06, 1200.0, 0.9), 0.55),
        (0.06, lambda r: clack(r, (2200, 3300), 0.012, click=0.8, thump=0.6, thump_f=180.0), 0.9)], 0.2, 0.5)


def s_pump(seed):
    rng = random.Random(seed)
    out = [0.0] * ns(0.42)
    pump_action(0.0)(out, rng)
    return master(out, 0.7, 0.0, hp=60.0)


def s_break(open_):
    def make(seed):
        if open_:
            parts = [(0.0, lambda r: clack(r, (2600, 3900), 0.01, click=0.9, thump=0.0), 0.6),
                     (0.02, lambda r: scrape(r, 0.1, 900.0, 1.5), 0.35),
                     (0.1, lambda r: clack(r, (700, 1100, 1700), 0.04, click=0.8, thump=0.9, thump_f=110.0), 1.0)]
            return foley(seed, parts, 0.38, 0.6)
        return foley(seed, [(0.0, lambda r: clack(r, (1100, 1800, 2700, 3900), 0.035, click=1.4, thump=1.1,
                                                  thump_f=120.0), 1.0)], 0.3, 0.7)
    return make


def s_rocket_load(seed):
    return foley(seed, [
        (0.0, lambda r: scrape(r, 0.45, 800.0, 1.0, grain=60.0, fc_end=1200.0), 0.7),
        (0.47, lambda r: clack(r, (600, 950, 1500, 2300), 0.05, click=1.0, thump=1.1, thump_f=95.0), 1.0),
        (0.56, lambda r: clack(r, (2400, 3500), 0.01, click=0.8, thump=0.0), 0.4)], 0.85, 0.65)


def s_cylinder(which):
    def make(seed):
        if which == 'open':
            parts = [(0.0, lambda r: clack(r, (2600, 3900), 0.008, click=0.9, thump=0.0), 0.6)]
            parts += [(0.03 + 0.025 * k, lambda r: clack(r, (2800, 4100), 0.006, click=0.6, thump=0.0), 0.3)
                      for k in range(4)]
            parts += [(0.14, lambda r: clack(r, (900, 1500), 0.03, click=0.7, thump=0.8, thump_f=120.0), 0.9)]
            return foley(seed, parts, 0.35, 0.55)
        if which == 'round':
            return foley(seed, [
                (0.0, lambda r: scrape(r, 0.05, 900.0, 1.0), 0.5),
                (0.05, lambda r: ring(ns(0.15), [320.0, 680.0, 1400.0], [0.03, 0.02, 0.01], [0.7, 0.4, 0.2], r), 1.0),
                (0.05, lambda r: burst(r, 0.02, 0.004, lp=1500.0), 0.5)], 0.22, 0.55)
        parts = [(0.0, lambda r: clack(r, (800, 1300, 2000), 0.04, click=1.1, thump=1.0, thump_f=110.0), 1.0)]
        parts += [(0.08 + 0.04 * k, lambda r: clack(r, (2900, 4300), 0.006, click=0.5, thump=0.0), 0.25)
                  for k in range(6)]
        return foley(seed, parts, 0.45, 0.65)
    return make


def s_rail_charge(seed):
    rng = random.Random(seed)
    out = [0.0] * ns(1.35)
    n = ns(0.95)
    whine = rising(n, 380.0, 4200.0, 2.2)
    whine2 = rising(n, 760.0, 8400.0, 2.2)
    buzz = [1.0 if math.sin(TAU * 120.0 * i / SR) > 0 else -1.0 for i in range(n)]
    buzz = biquad(buzz, 'lp', 900.0)
    swell = [(i / float(n)) ** 1.5 for i in range(n)]
    add(out, fade([(a + 0.3 * b + 0.25 * c) * s for a, b, c, s in zip(whine, whine2, buzz, swell)], 0.03), 0.0,
        0.6)
    for k, f in enumerate((1760.0, 2349.0)):
        m = ns(0.07)
        add(out, mul([math.sin(TAU * f * i / SR) for i in range(m)], env(m, 0.003, 0.05)), 0.98 + k * 0.09, 0.35)
    add(out, clack(rng, (1300, 2100, 3100), 0.025, click=0.9, thump=0.6, thump_f=150.0), 0.96, 0.6)
    return master(out, 0.55, 0.0, hp=60.0)


# ---- charging the railgun (hold the trigger) and looped sounds -----------------------------------------------

def periodic(x, fn):
    """Runs a filter chain over three copies of a loop and keeps the middle one: the filters have settled, so the
    result joins its own start seamlessly."""
    n = len(x)
    return fn(x * 3)[n:2 * n]


def loop_ticks(n, rng, rate, burst_s=0.0015):
    """Sparse random ticks spread evenly over a loop, wrapping round its end (sparks, motor crackle)."""
    out = [0.0] * n
    m = ns(burst_s)
    for _ in range(int(rate * n / SR)):
        i = rng.randrange(n)
        amp = 0.3 + 0.7 * rng.random()
        for j in range(m):
            out[(i + j) % n] += amp * (rng.random() * 2 - 1) * (1 - j / float(m))
    return out


def s_railgun_charge(seed):
    """Holding the railgun's trigger: the capacitors whine up over the two seconds a full charge takes (the client
    stops the sound wherever the player lets go), sparks thicken, and a double chirp says it is full."""
    rng = random.Random(seed)
    full = 2.0
    out = [0.0] * ns(full + 0.3)
    n = ns(full)
    whine = rising(n, 240.0, 3400.0, 1.7)
    octave = rising(n, 480.0, 6800.0, 1.7)
    hum = rising(n, 55.0, 150.0, 1.3)
    buzz = biquad([1.0 if v > 0 else -1.0 for v in rising(n, 50.0, 150.0, 1.3)], 'lp', 800.0)
    swell = [0.2 + 0.8 * (i / float(n)) ** 1.4 for i in range(n)]
    ph, flutter = 0.0, []
    for i in range(n):
        ph += TAU * (5.0 + 25.0 * (i / float(n)) ** 2) / SR           # the warble speeds up as it fills
        flutter.append(0.85 + 0.15 * math.sin(ph))
    tone = [(a + 0.3 * b + 0.5 * c + 0.18 * d) * s * f
            for a, b, c, d, s, f in zip(whine, octave, hum, buzz, swell, flutter)]
    add(out, fade(tone, 0.02), 0.0, 0.55)
    sparks = [0.0] * n
    t = 0.05
    while t < full:
        t += rng.expovariate(8.0 + 90.0 * (t / full) ** 2)
        i = at_index(t)
        m = ns(0.0015)
        for j in range(min(m, n - i)):
            sparks[i + j] += (t / full) * (rng.random() * 2 - 1) * (1 - j / float(m))
    add(out, biquad(sparks, 'hp', 3000.0), 0.0, 0.3)
    for k, f in enumerate((1760.0, 2637.0)):
        m = ns(0.08)
        add(out, mul([math.sin(TAU * f * i / SR) for i in range(m)], env(m, 0.003, 0.05)), full - 0.05 + k * 0.09, 0.3)
    return master(out, 0.5, 0.0, hp=45.0)


def s_railgun_charged(seed):
    """A railgun holding a full charge: a steady, slightly unstable capacitor hum. One second that loops seamlessly
    (every partial is a whole number of hertz and the noise is filtered as a loop), so it has no end fade."""
    rng = random.Random(seed)
    n = SR
    out = [0.0] * n
    for i in range(n):
        t = i / float(SR)
        hum = (math.sin(TAU * 150 * t) + 0.5 * math.sin(TAU * 300 * t) + 0.3 * math.sin(TAU * 450 * t)
               + 0.15 * math.sin(TAU * 750 * t))
        whine = (math.sin(TAU * 3400 * t) + 0.7 * math.sin(TAU * 3406 * t)) * (0.7 + 0.3 * math.sin(TAU * 7 * t))
        out[i] = 0.5 * hum * (0.85 + 0.15 * math.sin(TAU * 3 * t)) + 0.28 * whine
    hiss = periodic(white(n, rng), lambda x: biquad(x, 'hp', 4500.0))
    sparks = periodic(loop_ticks(n, rng, 30.0), lambda x: biquad(x, 'hp', 3000.0))
    out = [o + 0.08 * h * (0.6 + 0.4 * math.sin(TAU * 2 * i / SR)) + 0.35 * s
           for i, (o, h, s) in enumerate(zip(out, hiss, sparks))]
    return normalize(periodic(out, lambda x: biquad(x, 'hp', 40.0, 0.6)), 0.42)


def s_railgun_overcharge(seed):
    """Layered on a railgun shot fired at 85 %+ charge: the air slamming shut behind the slug - a deep thump, a
    rolling thunder tail and a fizz of arcing. Only the direct thump is soft-clipped (see gunshot)."""
    rng = random.Random(seed)
    out = [0.0] * ns(3.0)
    n = ns(0.8)
    direct = mul(chirp(n, jitter(rng, 95.0), 32.0, 0.09), env(n, 0.002, 0.3))
    add(direct, mul(biquad(white(n, rng), 'lp', 450.0), env(n, 0.001, 0.11)), 0.0, 0.8)
    m = ns(0.08)
    add(direct, mul(biquad(white(m, rng), 'hp', 1200.0), env(m, 0.0003, 0.012)), 0.0, 0.5)
    add(out, normalize(saturate(normalize(direct, 1.0), 2.0), 1.0))
    m = ns(2.8)
    thunder = lp_glide(white(m, rng), 1400.0, 110.0, 0.5)
    rumble = wobble(m, 7.0, rng)
    e = env(m, 0.02, t60=2.4)
    add(out, normalize([a * b * (0.65 + 0.35 * w) for a, b, w in zip(thunder, e, rumble)], 1.0), 0.03, 0.35)
    add(out, crackle(ns(1.2), rng, 140.0, 0.3, hp=3200.0, level=1.0), 0.0, 0.15)
    return fade(normalize(biquad(out, 'hp', 22.0, 0.6), 0.9), 0.6)


def s_rocket_flight(seed):
    """An RPG rocket's motor burning: a ragged, fluttering roar that follows the rocket. One second that loops
    seamlessly (whole-hertz flutter, loop-filtered noise and crackle)."""
    rng = random.Random(seed)
    n = SR
    roar = periodic(white(n, rng), lambda x: biquad(biquad(x, 'bp', 650.0, 0.7), 'lp', 2600.0))
    rumble = periodic(white(n, rng), lambda x: biquad(biquad(x, 'lp', 160.0, 0.7), 'lp', 160.0, 0.7))
    hiss = periodic(white(n, rng), lambda x: biquad(x, 'hp', 3500.0))
    crack = periodic(loop_ticks(n, rng, 70.0, 0.002), lambda x: biquad(x, 'hp', 1500.0))
    roar, rumble, hiss, crack = (normalize(v, 1.0) for v in (roar, rumble, hiss, crack))
    out = []
    for i in range(n):
        t = i / float(SR)
        flutter = 1.0 + 0.25 * math.sin(TAU * 13 * t) + 0.15 * math.sin(TAU * 29 * t + 1.0) \
            + 0.1 * math.sin(TAU * 47 * t + 2.0)
        out.append(0.6 * roar[i] * flutter + 0.55 * rumble[i] + 0.12 * hiss[i] + 0.3 * crack[i])
    return normalize(saturate(normalize(out, 1.0), 1.3), 0.6)


# =====================================================================================================
# ordnance
# =====================================================================================================

def explosion(seed, size=1.0, length=3.0, whoomp=False, distant=False, peak=0.9, extras=()):
    rng = random.Random(seed)
    n = ns(length)
    dry = [0.0] * n
    if not distant:
        add(dry, friedlander(0.004 * size), 0, 2.2)
        add(dry, biquad(burst(rng, 0.02, 0.002 * size), 'hp', 1000.0), 0, 1.4)
    m = ns(min(length, 0.07 * size * 9))
    b = mul(white(m, rng), env(m, 0.0005, 0.07 * size))
    b = biquad(biquad(b, 'lp', 2600.0 / math.sqrt(size), 0.7), 'low', 150.0, 0.7, 6.0)
    add(dry, b, 0, 1.6)
    m = ns(min(length, 0.3 * size * 7))
    s = chirp(m, 95.0 / size ** 0.3, 28.0, 0.05)
    e = env(m, 0.002, 0.3 * size)
    grit = biquad(white(m, rng), 'lp', 200.0)
    add(dry, [a * b_ * (1 + 1.4 * g) for a, b_, g in zip(s, e, grit)], 0, 2.0)
    m = ns(min(length, 0.55 * size * 6))
    add(dry, mul([math.sin(TAU * 30.0 * i / SR) for i in range(m)], env(m, 0.01, 0.55 * size)), 0, 1.2)
    if whoomp:
        m = ns(min(length, 3.2))
        fire = biquad(biquad(white(m, rng), 'lp', 320.0), 'lp', 320.0)
        add(dry, mul(fire, env(m, 0.05, hold=0.25, t60=2.5)), 0.01, 3.2)
        add(dry, mul(chirp(m, 70.0, 42.0, 0.3), env(m, 0.05, hold=0.2, t60=2.2)), 0.01, 1.2)
    for fn in extras:
        fn(dry, rng)
    # the blast itself is soft-clipped; debris, the rolling rumble and echoes are then added at fixed levels
    direct = master(dry, 1.0, 2.2 if distant else 3.2, hp=18.0)
    out = list(direct)
    if not distant:
        add(out, crackle(n, rng, 40.0 * size, 0.6 * size, start=0.08, hp=1500.0, level=0.9), 0, 0.12)
        m = ns(min(length, 2.5))
        dirt = biquad(white(m, rng), 'lp', 2500.0)
        add(out, mul(dirt, env(m, 0.2, t60=1.5)), 0.15, 0.03)
    rumble = tail_noise(rng, n, 2.6 * size, lp=(900.0, 180.0), rate=5.0, attack=0.02)
    ref = rumble[ns(0.03):ns(0.1)]
    rms = math.sqrt(sum(v * v for v in ref) / len(ref)) or 1.0
    add(out, rumble, 0, (0.2 if distant else 0.12 + 0.04 * size) / rms)
    slap = biquad(biquad(direct[:ns(0.4)], 'lp', 700.0), 'lp', 700.0)
    for k, (d, g) in enumerate(((0.22, 0.2), (0.5, 0.13), (0.9, 0.08))):
        add(out, slap, jitter(rng, d * size, 0.1), g * (1.6 if distant else 1.0))
    if distant:
        out = biquad(biquad(out, 'lp', 450.0), 'lp', 450.0)
    out = normalize(biquad(out, 'hp', 18.0, 0.6), peak)
    return fade(out, 0.18 * length)


def s_flash_ring(seed):
    n = ns(4.0)
    e = env(n, 0.05, hold=0.6, t60=3.5)
    out = [(math.sin(TAU * 3620.0 * i / SR) + math.sin(TAU * 3668.0 * i / SR) + 0.2 * math.sin(TAU * 7250.0 * i / SR))
           * v for i, v in enumerate(e)]
    return master(out, 0.28, 0.0, hp=200.0)


def small_pop(rng, out, level=0.6):
    add(out, friedlander(0.0012), 0, level)
    add(out, biquad(burst(rng, 0.03, 0.006), 'lp', 3000.0), 0, level * 0.8)


def s_incendiary(seed):
    rng = random.Random(seed)
    out = [0.0] * ns(3.0)
    small_pop(rng, out, 0.8)
    add(out, ring(ns(0.4), [rng.uniform(3000, 6500) for _ in range(6)], [0.05] * 6, [0.3] * 6, rng), 0.0, 0.3)
    n = ns(0.5)
    add(out, mul(bp_glide(white(n, rng), 280.0, 1100.0, 0.2, 0.8), env(n, 0.25, 0.1)), 0.02, 1.6)
    n = ns(2.8)
    roar = biquad(biquad(white(n, rng), 'lp', 900.0), 'lp', 900.0)
    flick = wobble(n, 11.0, rng)
    add(out, [a * e * (0.55 + 0.45 * f) for a, e, f in zip(roar, env(n, 0.25, hold=0.3, t60=2.4), flick)], 0.15, 2.2)
    add(out, crackle(n, rng, 25.0, 1.2, start=0.2, hp=2000.0, level=0.8), 0.0, 0.4)
    return master(out, 0.75, 1.6, hp=30.0)


def hiss(rng, seconds, hp, lp, attack, hold, t60, flutter=14.0):
    n = ns(seconds)
    x = biquad(biquad(white(n, rng), 'hp', hp), 'lp', lp)
    f = wobble(n, flutter, rng)
    return [a * e * (0.82 + 0.18 * v) for a, e, v in zip(x, env(n, attack, hold=hold, t60=t60), f)]


def s_smoke(seed):
    rng = random.Random(seed)
    out = [0.0] * ns(5.0)
    small_pop(rng, out, 0.5)
    add(out, hiss(rng, 4.9, 1800.0, 9000.0, 0.05, 0.4, 4.5), 0.03, 1.0)
    n = ns(4.0)
    add(out, mul(biquad(white(n, rng), 'lp', 400.0), env(n, 0.1, hold=0.3, t60=3.0)), 0.03, 0.3)
    return master(out, 0.5, 0.0, hp=40.0)


def s_chemical(seed):
    rng = random.Random(seed)
    out = [0.0] * ns(4.0)
    small_pop(rng, out, 0.6)
    n = ns(3.9)
    x = biquad(white(n, rng), 'bp', 2600.0, 0.6)
    add(out, mul(x, env(n, 0.08, hold=0.3, t60=3.5)), 0.02, 1.2)
    for _ in range(9):
        m = ns(0.05)
        blip = mul(rising(m, rng.uniform(170, 220), rng.uniform(380, 460), 1.0), env(m, 0.004, 0.015))
        add(out, blip, rng.uniform(0.2, 2.4), rng.uniform(0.15, 0.35))
    m = ns(3.0)
    add(out, mul(biquad(white(m, rng), 'lp', 350.0), env(m, 0.1, t60=2.5)), 0.03, 0.35)
    return master(out, 0.55, 0.0, hp=40.0)


def s_singularity(seed):
    rng = random.Random(seed)
    out = [0.0] * ns(3.8)
    # the collapse sucks in: a reversed swell, rising in level and pitch
    n = ns(0.6)
    swell = [((i + 1.0) / n) ** 3 for i in range(n)]
    x = lp_glide(white(n, rng), 200.0, 2500.0, 0.3)
    add(out, mul(x, swell), 0.0, 1.4)
    add(out, mul(rising(n, 40.0, 160.0, 2.0), swell), 0.0, 1.0)
    # the implosion: a deep whump and a falling suck
    m = ns(1.8)
    add(out, mul(chirp(m, 90.0, 22.0, 0.08), env(m, 0.002, 0.5)), 0.6, 2.0)
    add(out, friedlander(0.006), 0.6, 1.8)
    add(out, mul(lp_glide(white(m, rng), 1500.0, 100.0, 0.4), env(m, 0.01, t60=1.6)), 0.6, 1.4)
    m = ns(3.0)
    add(out, mul([math.sin(TAU * 28.0 * i / SR) for i in range(m)], env(m, 0.02, t60=2.8)), 0.6, 1.0)
    return master(out, 0.9, 2.8, hp=18.0)


def s_singularity_pulse(seed):
    rng = random.Random(seed)
    n = ns(0.9)
    e = env(n, 0.08, t60=0.8)
    out = [(math.sin(TAU * 40.0 * i / SR) + 0.5 * math.sin(TAU * 80.0 * i / SR))
           * (0.5 + 0.5 * math.sin(TAU * 6.0 * i / SR)) * v for i, v in enumerate(e)]
    add(out, mul(biquad(white(n, rng), 'lp', 200.0), e), 0, 0.4)
    return master(out, 0.8, 2.5, hp=20.0)


def s_ion_charge(seed):
    rng = random.Random(seed)
    out = [0.0] * ns(2.2)
    n = ns(1.8)
    saw = [0.0] * n
    ph = 0.0
    for i in range(n):
        t = i / float(n)
        ph += TAU * (55.0 + 165.0 * t * t) / SR
        saw[i] = sum(math.sin(k * ph) / k for k in range(1, 7))
    opening = [(i / float(n)) ** 1.5 for i in range(n)]
    lowpassed = biquad(saw, 'lp', 1400.0)
    add(out, fade([a * o for a, o in zip(lowpassed, opening)], 0.15), 0.0, 0.5)
    add(out, mul(biquad(white(n, rng), 'hp', 5000.0), opening), 0.0, 0.08)
    for k, f in enumerate((1318.5, 1568.0, 2093.0)):
        m = ns(0.12)
        add(out, mul([math.sin(TAU * f * i / SR) for i in range(m)], env(m, 0.004, 0.06)), 0.25 + 0.5 * k, 0.4)
    return master(out, 0.6, 1.2, hp=40.0)


def zap(out, rng):
    n = ns(0.5)
    car = mod = 0.0
    z = []
    for i in range(n):
        t = i / float(SR)
        fc = 150.0 + 2400.0 * math.exp(-t / 0.1)
        car += TAU * fc / SR
        mod += TAU * fc * 1.5 / SR
        z.append(math.sin(car + 3.0 * math.sin(mod)) * math.exp(-t / 0.16))
    add(out, z, 0.0, 1.0)
    add(out, crackle(ns(1.6), rng, 120.0, 0.5, hp=2500.0, level=1.0), 0.0, 0.5)


def s_nuke_beep(seed):
    n = ns(0.16)
    e = env(n, 0.003, hold=0.12, decay=0.01)
    out = [sum(a * math.sin(TAU * 1046.5 * k * i / SR) for k, a in ((1, 1.0), (3, 0.33), (5, 0.2), (7, 0.14))) * v
           for i, v in enumerate(e)]
    out += [0.0] * ns(0.05)
    return master(out, 0.55, 0.0, hp=60.0)


def s_grenade_throw(seed):
    return foley(seed, [
        (0.0, lambda r: ring(ns(0.3), [3900.0, 5700.0, 7600.0], [0.06, 0.045, 0.03], [0.5, 0.35, 0.2], r), 0.8),
        (0.12, lambda r: ring(ns(0.25), [5200.0, 7300.0], [0.04, 0.03], [0.4, 0.25], r), 0.7),
        (0.18, lambda r: mul(bp_glide(white(ns(0.28), r), 500.0, 1400.0, 0.1, 1.2),
                             [math.sin(math.pi * i / ns(0.28)) for i in range(ns(0.28))]), 1.0)], 0.5, 0.55)


def s_grenade_bounce(seed):
    return foley(seed, [
        (0.0, lambda r: clack(r, (1450, 2350, 3700, 5200), 0.03, click=1.0, thump=0.9, thump_f=170.0), 1.0),
        (0.0, lambda r: burst(r, 0.03, 0.006, lp=2000.0), 0.4)], 0.28, 0.55)


# =====================================================================================================
# the catalogue
# =====================================================================================================

# =====================================================================================================
# the F-14 (loops are one second long and seamless: whole-hertz partials, loop-filtered noise, no fades)
# =====================================================================================================

def loop_noise(rng, chain, n=SR):
    """Noise filtered as a loop (see periodic), normalised to 1."""
    return normalize(periodic(white(n, rng), chain), 1.0)


def s_jet_engine(seed):
    """The TF30 turbofans from outside: broadband jet roar, a low rumble, the fan's blade-passing whine with the
    buzz-saw of its supersonic blade tips, and a turbine whistle. JetSounds raises pitch and volume with the
    throttle and adds the Doppler shift."""
    rng = random.Random(seed)
    n = SR
    roar = loop_noise(rng, lambda x: biquad(biquad(x, 'bp', 700.0, 0.45), 'lp', 3200.0))
    rumble = loop_noise(rng, lambda x: biquad(biquad(x, 'lp', 140.0, 0.7), 'lp', 140.0, 0.7))
    hiss = loop_noise(rng, lambda x: biquad(x, 'hp', 5000.0))
    out = []
    for i in range(n):
        t = i / float(SR)
        whine = (math.sin(TAU * 2350 * t) + 0.6 * math.sin(TAU * 2362 * t + 1.0) + 0.35 * math.sin(TAU * 4700 * t)
                 + 0.2 * math.sin(TAU * 1175 * t + 2.0))
        buzz = sum(math.sin(TAU * 196 * k * t + k) / k for k in range(1, 9))
        whistle = math.sin(TAU * 7040 * t) * (0.8 + 0.2 * math.sin(TAU * 5 * t))
        swell = 1.0 + 0.08 * math.sin(TAU * 2 * t) + 0.05 * math.sin(TAU * 3 * t + 1.3)
        out.append(swell * (0.55 * roar[i] + 0.5 * rumble[i] + 0.06 * hiss[i]) + 0.13 * whine + 0.05 * buzz
                   + 0.035 * whistle)
    return normalize(periodic(out, lambda x: biquad(x, 'hp', 30.0, 0.6)), 0.6)


def s_jet_afterburner(seed):
    """Afterburner: a deep, tearing roar with the crackle of shock-laden exhaust that drowns everything nearby."""
    rng = random.Random(seed)
    n = SR
    low = loop_noise(rng, lambda x: biquad(biquad(x, 'lp', 220.0, 0.7), 'lp', 220.0, 0.7))
    body = loop_noise(rng, lambda x: biquad(biquad(x, 'bp', 420.0, 0.5), 'lp', 1800.0))
    crack = normalize(periodic(loop_ticks(n, rng, 110.0, 0.0025), lambda x: biquad(x, 'hp', 700.0)), 1.0)
    out = []
    for i in range(n):
        t = i / float(SR)
        flutter = 1.0 + 0.18 * math.sin(TAU * 7 * t) + 0.1 * math.sin(TAU * 17 * t + 0.7)
        out.append(0.8 * low[i] * flutter + 0.6 * body[i] + 0.45 * crack[i])
    return normalize(saturate(normalize(out, 1.0), 1.6), 0.8)


def s_jet_wind(seed):
    """Air rushing over the canopy, heard in the cockpit (JetSounds raises it with the airspeed)."""
    rng = random.Random(seed)
    n = SR
    rush = loop_noise(rng, lambda x: biquad(biquad(x, 'lp', 1400.0, 0.6), 'hp', 180.0, 0.6))
    whistle = loop_noise(rng, lambda x: biquad(x, 'bp', 2600.0, 6.0))
    out = []
    for i in range(n):
        t = i / float(SR)
        gust = 1.0 + 0.15 * math.sin(TAU * 1 * t) + 0.08 * math.sin(TAU * 3 * t + 0.5)
        out.append(gust * (0.85 * rush[i] + 0.2 * whistle[i]))
    return normalize(out, 0.5)


def s_jet_gun(seed):
    """The M61 Vulcan: 6000 rounds a minute, so a hundred shots a second fuse into one tearing 'brrrt' - a 100 Hz
    train of sharp blast pulses over the whine of the spinning barrel cluster."""
    rng = random.Random(seed)
    n = SR
    out = [0.0] * n
    period = SR // 100
    pulse = friedlander(0.0009, 1.6, 5.0)
    for k in range(100):
        amp = 0.75 + 0.25 * rng.random()
        o = k * period
        for j, v in enumerate(pulse):
            out[(o + j) % n] += amp * v
        m = ns(0.006)
        for j, v in enumerate(mul(white(m, rng), env(m, 0.00005, 0.0015))):
            out[(o + j) % n] += 0.6 * amp * v
    out = normalize(periodic(out, lambda x: biquad(biquad(x, 'lp', 4200.0, 0.7), 'hp', 45.0, 0.6)), 1.0)
    mix = []
    for i in range(n):
        t = i / float(SR)
        body = math.sin(TAU * 100 * t) + 0.5 * math.sin(TAU * 200 * t + 0.5)
        whine = 0.5 * math.sin(TAU * 1700 * t) + 0.3 * math.sin(TAU * 3400 * t)
        mix.append(out[i] + 0.35 * body + 0.06 * whine)
    return normalize(saturate(normalize(mix, 1.0), 2.2), 0.85)


def s_jet_gun_stop(seed):
    """The burst ends: its echo rolls off the terrain while the barrels spin down and stop."""
    rng = random.Random(seed)
    out = [0.0] * ns(1.6)
    n = ns(1.5)
    add(out, mul(lp_glide(white(n, rng), 2400.0, 400.0, 0.35), env(n, 0.005, t60=1.3)), 0.0, 0.5)
    m = ns(0.7)
    add(out, mul(chirp(m, 1700.0, 250.0, 0.25), env(m, 0.0, 0.3)), 0.0, 0.18)
    add(out, clack(rng, (900, 1500, 2600), 0.02, click=0.7, thump=0.4, thump_f=120.0), 0.62, 0.25)
    return master(out, 0.6, 0.0, hp=40.0)


def s_jet_missile(seed):
    """A missile leaving the rail: the motor lights with a bang, then a rushing roar tears away from the jet."""
    rng = random.Random(seed)
    out = [0.0] * ns(2.8)
    n = ns(0.4)
    bang = mul(biquad(white(n, rng), 'lp', 2200.0), env(n, 0.0005, 0.05))
    add(bang, mul(chirp(n, 140.0, 45.0, 0.08), env(n, 0.001, 0.12)), 0.0, 0.8)
    add(out, normalize(saturate(normalize(bang, 1.0), 2.0), 1.0), 0.0, 0.8)
    m = ns(2.6)
    roar = bp_glide(white(m, rng), 1300.0, 380.0, 0.9, q=0.8)
    hiss = biquad(white(m, rng), 'hp', 3000.0)
    e = env(m, 0.03, t60=2.4)
    flutter = wobble(m, 18.0, rng)
    motor = [(r + 0.15 * h) * a * (0.8 + 0.2 * w) for r, h, a, w in zip(roar, hiss, e, flutter)]
    add(out, normalize(motor, 1.0), 0.02, 0.75)
    return master(out, 0.8, 1.2, hp=30.0)


def s_jet_rocket(seed):
    """A Zuni leaving its pod: a sharp pop and a short, bright rush."""
    rng = random.Random(seed)
    out = [0.0] * ns(1.5)
    n = ns(0.25)
    add(out, mul(biquad(white(n, rng), 'bp', jitter(rng, 1400.0), 0.7), env(n, 0.0003, 0.025)), 0.0, 1.0)
    m = ns(1.4)
    rush = bp_glide(white(m, rng), 2200.0, 700.0, 0.4, q=0.9)
    add(out, normalize(mul(rush, env(m, 0.01, t60=1.2)), 1.0), 0.01, 0.6)
    return master(out, 0.7, 1.0, hp=60.0)


def s_jet_bomb_release(seed):
    """The ejector rack fires its cartridges and kicks the bomb away: a hard clunk and a pneumatic thump."""
    rng = random.Random(seed)
    out = [0.0] * ns(0.8)
    add(out, clack(rng, (420, 780, 1300), 0.05, click=1.0, thump=1.0, thump_f=90.0), 0.0, 0.9)
    add(out, burst(rng, 0.2, 0.04, hp=500.0, lp=3000.0), 0.005, 0.5)
    add(out, clack(rng, (600, 1100), 0.03, click=0.4, thump=0.5, thump_f=70.0), 0.09, 0.4)
    return master(out, 0.7, 0.8, hp=40.0)


def s_jet_flare(seed):
    """A flare cartridge fires out of the dispenser: a sharp pop, then the magnesium fizzing as it lights."""
    rng = random.Random(seed)
    out = [0.0] * ns(1.0)
    add(out, burst(rng, 0.08, 0.006, hp=800.0), 0.0, 1.0)
    m = ns(0.9)
    add(out, crackle(m, rng, 220.0, 0.35, hp=2000.0), 0.03, 0.4)
    add(out, mul(biquad(white(m, rng), 'hp', 2500.0), env(m, 0.04, t60=0.8)), 0.02, 0.35)
    return master(out, 0.6, 0.5, hp=80.0)


def s_jet_gear(seed):
    """The landing gear cycling: the hydraulic pumps whine through the 2.7 seconds it takes, the doors thump open,
    the legs lock with a clunk."""
    rng = random.Random(seed)
    out = [0.0] * ns(3.1)
    n = ns(2.7)
    whine = [0.0] * n
    ph = 0.0
    for i in range(n):
        t = i / float(SR)
        ph += TAU * (330.0 + 60.0 * math.sin(math.pi * t / 2.7)) / SR   # the pump labours mid-stroke
        whine[i] = math.sin(ph) + 0.4 * math.sin(2 * ph) + 0.2 * math.sin(3 * ph)
    e = [min(1.0, i / (0.15 * SR), (n - i) / (0.2 * SR)) for i in range(n)]
    add(out, mul(whine, e), 0.0, 0.25)
    add(out, mul(biquad(white(n, rng), 'bp', 900.0, 1.5), e), 0.0, 0.12)
    add(out, clack(rng, (300, 620, 1100), 0.05, click=0.8, thump=1.0, thump_f=80.0), 0.1, 0.5)
    add(out, clack(rng, (520, 980, 1650, 2400), 0.04, click=1.0, thump=0.8, thump_f=110.0), 2.7, 0.8)
    return master(out, 0.6, 0.0, hp=40.0)


def s_jet_canopy(seed):
    """The canopy actuator: an electric motor hums for the two and a quarter seconds of travel, then it seats."""
    rng = random.Random(seed)
    out = [0.0] * ns(2.6)
    n = ns(2.25)
    hum = [math.sin(TAU * 120 * i / SR) + 0.5 * math.sin(TAU * 240 * i / SR) + 0.25 * math.sin(TAU * 360 * i / SR)
           + 0.2 * math.sin(TAU * 1450 * i / SR) for i in range(n)]
    e = [min(1.0, i / (0.1 * SR), (n - i) / (0.15 * SR)) for i in range(n)]
    add(out, mul(hum, e), 0.0, 0.3)
    add(out, mul(biquad(white(n, rng), 'bp', 1800.0, 2.0), e), 0.0, 0.06)
    add(out, clack(rng, (700, 1300, 2100), 0.03, click=0.8, thump=0.7, thump_f=140.0), 2.25, 0.6)
    return master(out, 0.5, 0.0, hp=50.0)


def s_jet_touchdown(seed):
    """The main wheels hit the runway: a tyre squeal and the thump of the oleos taking the weight."""
    rng = random.Random(seed)
    out = [0.0] * ns(1.0)
    m = ns(0.45)
    add(out, mul(chirp(m, jitter(rng, 1150.0), 820.0, 0.2), env(m, 0.005, 0.12)), 0.0, 0.35)
    add(out, mul(biquad(white(m, rng), 'bp', 2400.0, 2.5), env(m, 0.003, 0.1)), 0.0, 0.5)
    n = ns(0.5)
    add(out, mul(chirp(n, 90.0, 45.0, 0.1), env(n, 0.003, 0.12)), 0.02, 0.9)
    add(out, burst(rng, 0.3, 0.05, lp=600.0), 0.02, 0.4)
    return master(out, 0.75, 1.0, hp=30.0)


def s_jet_eject(seed):
    """Ejection: the canopy's jettison charges bang, then the seat's rocket roars for a second."""
    rng = random.Random(seed)
    out = [0.0] * ns(2.6)
    add(out, explosion(seed, 0.6, 1.2, peak=1.0), 0.0, 0.6)
    m = ns(1.6)
    roar = bp_glide(white(m, rng), 900.0, 500.0, 0.8, q=0.7)
    e = [min(1.0, i / (0.02 * SR)) * (1.0 if i < 0.9 * SR else math.exp(-(i - 0.9 * SR) / (0.15 * SR)))
         for i in range(m)]
    add(out, normalize(mul(roar, e), 1.0), 0.18, 0.7)
    return master(out, 0.85, 1.0, hp=30.0)


def s_jet_seeker(seed):
    """The Sidewinder's seeker in the headset: a low, buzzy growl while it searches (JetSounds bends the pitch up
    as it closes on a lock)."""
    n = SR
    out = []
    for i in range(n):
        t = i / float(SR)
        tone = sum(math.sin(TAU * 420 * k * t) / k for k in range(1, 6))
        growl = 0.55 + 0.45 * math.sin(TAU * 23 * t) * math.sin(TAU * 7 * t + 0.4)
        out.append(tone * growl)
    return normalize(periodic(out, lambda x: biquad(x, 'lp', 2500.0)), 0.4)


def s_jet_lock(seed):
    """Lock: the growl turns into a steady, piercing tone."""
    n = SR
    return normalize([math.sin(TAU * 1250 * i / SR) + 0.3 * math.sin(TAU * 2500 * i / SR)
                      + 0.12 * math.sin(TAU * 3750 * i / SR) for i in range(n)], 0.32)


def s_jet_warning(seed):
    """Missile warning: a fast two-tone beeping in the headset (eight beeps a second, high-low)."""
    n = SR
    out = [0.0] * n
    step = SR // 8
    m = step - ns(0.02)
    for k in range(8):
        f = 1000.0 if k % 2 == 0 else 1400.0
        for j in range(m):
            a = min(1.0, j / 60.0, (m - j) / 60.0)
            out[k * step + j] = a * (math.sin(TAU * f * j / SR) + 0.25 * math.sin(TAU * 2 * f * j / SR))
    return normalize(out, 0.4)


def s_jet_stall(seed):
    """Stall warning: a harsh horn pulsing six times a second."""
    n = SR
    out = []
    for i in range(n):
        t = i / float(SR)
        horn = sum(math.sin(TAU * 440 * k * t) / k for k in (1, 3, 5, 7))
        gate = min(1.0, max(0.0, math.sin(TAU * 6 * t)) * 3.0)
        out.append(horn * gate)
    return normalize(periodic(out, lambda x: biquad(x, 'lp', 3000.0)), 0.35)


GUN_ORDER = ['ak47', 'm4a1', 'scar_h', 'aug', 'glock17', 'm1911', 'm9', 'deagle', 'barrett_m82', 'svd_dragunov',
             'awp', 'remington_870', 'spas12', 'aa12', 'sawed_off', 'rpg7', 'm32_launcher', 'railgun']
FIRE_RANGE = {'pistol': 64, 'rifle': 80, 'sniper': 96, 'shotgun': 80, 'launcher': 80, 'railgun': 96}
GUN_KIND = {'ak47': 'rifle', 'm4a1': 'rifle', 'scar_h': 'rifle', 'aug': 'rifle', 'glock17': 'pistol',
            'm1911': 'pistol', 'm9': 'pistol', 'deagle': 'pistol', 'barrett_m82': 'sniper', 'svd_dragunov': 'sniper',
            'awp': 'sniper', 'remington_870': 'shotgun', 'spas12': 'shotgun', 'aa12': 'shotgun',
            'sawed_off': 'shotgun', 'rpg7': 'launcher', 'm32_launcher': 'launcher', 'railgun': 'railgun'}

SUBTITLES = {
    'subtitles.arsenal.gun.fire': 'Gunshot',
    'subtitles.arsenal.gun.launch': 'Launcher fires',
    'subtitles.arsenal.gun.railgun': 'Railgun discharges',
    'subtitles.arsenal.railgun.charge': 'Railgun charges',
    'subtitles.arsenal.railgun.charged': 'Railgun hums, fully charged',
    'subtitles.arsenal.railgun.overcharge': 'Overcharged railgun booms',
    'subtitles.arsenal.rocket.flight': 'Rocket roars',
    'subtitles.arsenal.gun.suppressed': 'Suppressed gunshot',
    'subtitles.arsenal.gun.distant': 'Distant gunfire',
    'subtitles.arsenal.gun.dry_fire': 'Gun clicks empty',
    'subtitles.arsenal.gun.headshot': 'Headshot',
    'subtitles.arsenal.reload': 'Gun reloads',
    'subtitles.arsenal.reload.charge': 'Bolt racks',
    'subtitles.arsenal.grenade.throw': 'Grenade thrown',
    'subtitles.arsenal.grenade.bounce': 'Grenade bounces',
    'subtitles.arsenal.explosion': 'Explosion',
    'subtitles.arsenal.explosion.distant': 'Distant explosion',
    'subtitles.arsenal.flashbang': 'Flashbang goes off',
    'subtitles.arsenal.flashbang.ring': 'Ears ringing',
    'subtitles.arsenal.incendiary': 'Incendiary ignites',
    'subtitles.arsenal.smoke': 'Smoke grenade hisses',
    'subtitles.arsenal.chemical': 'Nerve agent released',
    'subtitles.arsenal.singularity': 'Singularity collapses',
    'subtitles.arsenal.singularity.pulse': 'Singularity pulls',
    'subtitles.arsenal.ion.charge': 'Orbital cannon locks on',
    'subtitles.arsenal.ion.strike': 'Ion beam strikes',
    'subtitles.arsenal.nuke': 'Nuclear detonation',
    'subtitles.arsenal.nuke.beep': 'Nuke beeps',
    'subtitles.arsenal.jet.engine': 'Jet engines roar',
    'subtitles.arsenal.jet.afterburner': 'Afterburner thunders',
    'subtitles.arsenal.jet.wind': 'Wind rushes past',
    'subtitles.arsenal.jet.gun': 'Cannon fires',
    'subtitles.arsenal.jet.missile': 'Missile launches',
    'subtitles.arsenal.jet.rocket': 'Rocket launches',
    'subtitles.arsenal.jet.bomb_release': 'Bomb released',
    'subtitles.arsenal.jet.flare': 'Flares fire',
    'subtitles.arsenal.jet.gear': 'Landing gear moves',
    'subtitles.arsenal.jet.canopy': 'Canopy moves',
    'subtitles.arsenal.jet.touchdown': 'Tyres screech',
    'subtitles.arsenal.jet.eject': 'Ejection seat fires',
    'subtitles.arsenal.jet.seeker': 'Missile seeker growls',
    'subtitles.arsenal.jet.lock': 'Missile lock tone',
    'subtitles.arsenal.jet.warning': 'Missile warning',
    'subtitles.arsenal.jet.stall': 'Stall warning',
}


def catalogue():
    """[(event, folder, stem, maker, variants, attenuation, subtitle)]; event names match ModSounds.java."""
    c = []

    def ev(event, folder, stem, maker, variants, attenuation, subtitle):
        c.append((event, folder, stem, maker, variants, attenuation, subtitle))

    for gun in GUN_ORDER:
        kind = GUN_KIND[gun]
        sub = {'launcher': 'gun.launch', 'railgun': 'gun.railgun'}.get(kind, 'gun.fire')
        ev('gun.%s.fire' % gun, 'gun', gun + '_fire', make_shot(GUN_SHOTS[gun]), 3, FIRE_RANGE[kind], sub)
    ranges = {'pistol': 24, 'rifle': 28, 'heavy': 32}
    for kind, params in SUPPRESSED.items():
        ev('gun.suppressed.' + kind, 'gun', 'suppressed_' + kind, make_shot(params), 3, ranges[kind], 'gun.suppressed')
    for kind, params in DISTANT.items():
        ev('gun.distant.' + kind, 'gun', 'distant_' + kind, make_shot(params), 2,
           {'small': 192, 'rifle': 256, 'heavy': 320}[kind], 'gun.distant')
    ev('gun.railgun.charge', 'gun', 'railgun_charge', s_railgun_charge, 1, 24, 'railgun.charge')
    ev('gun.railgun.charged', 'gun', 'railgun_charged', s_railgun_charged, 1, 24, 'railgun.charged')
    ev('gun.railgun.overcharge', 'gun', 'railgun_overcharge', s_railgun_overcharge, 2, 128, 'railgun.overcharge')
    ev('rocket.flight', 'ordnance', 'rocket_flight', s_rocket_flight, 1, 48, 'rocket.flight')
    ev('gun.dry_fire', 'gun', 'dry_fire', s_dry_fire, 2, 16, 'gun.dry_fire')
    ev('gun.headshot', 'gun', 'headshot', s_headshot, 2, 24, 'gun.headshot')

    for kind in ('rifle', 'pistol', 'heavy'):
        ev('reload.%s.mag_out' % kind, 'reload', kind + '_mag_out', s_mag_out(kind), 2, 16, 'reload')
        ev('reload.%s.mag_in' % kind, 'reload', kind + '_mag_in', s_mag_in(kind), 2, 16, 'reload')
    ev('reload.rifle.charge', 'reload', 'rifle_charge', s_charge('rifle'), 2, 16, 'reload.charge')
    ev('reload.heavy.charge', 'reload', 'heavy_charge', s_charge('heavy'), 2, 16, 'reload.charge')
    ev('reload.pistol.slide', 'reload', 'pistol_slide', s_slide, 2, 16, 'reload.charge')
    ev('reload.bolt.open', 'reload', 'bolt_open', s_bolt(True), 2, 16, 'reload')
    ev('reload.bolt.close', 'reload', 'bolt_close', s_bolt(False), 2, 16, 'reload.charge')
    ev('reload.shotgun.shell', 'reload', 'shotgun_shell', s_shell, 3, 16, 'reload')
    ev('reload.shotgun.pump', 'reload', 'shotgun_pump', s_pump, 2, 16, 'reload.charge')
    ev('reload.break.open', 'reload', 'break_open', s_break(True), 2, 16, 'reload')
    ev('reload.break.close', 'reload', 'break_close', s_break(False), 2, 16, 'reload')
    ev('reload.rocket', 'reload', 'rocket_load', s_rocket_load, 2, 16, 'reload')
    ev('reload.cylinder.open', 'reload', 'cylinder_open', s_cylinder('open'), 2, 16, 'reload')
    ev('reload.cylinder.round', 'reload', 'cylinder_round', s_cylinder('round'), 3, 16, 'reload')
    ev('reload.cylinder.close', 'reload', 'cylinder_close', s_cylinder('close'), 2, 16, 'reload')
    ev('reload.rail.charge', 'reload', 'rail_charge', s_rail_charge, 2, 24, 'reload.charge')

    ev('grenade.throw', 'ordnance', 'grenade_throw', s_grenade_throw, 2, 16, 'grenade.throw')
    ev('grenade.bounce', 'ordnance', 'grenade_bounce', s_grenade_bounce, 3, 16, 'grenade.bounce')
    ev('explosion.frag', 'ordnance', 'explosion_frag', lambda s: explosion(s, 1.0, 3.0), 3, 96, 'explosion')
    ev('explosion.big', 'ordnance', 'explosion_big', lambda s: explosion(s, 1.4, 3.8), 3, 128, 'explosion')
    ev('explosion.thermobaric', 'ordnance', 'explosion_thermobaric',
       lambda s: explosion(s, 1.9, 5.0, whoomp=True), 2, 192, 'explosion')
    ev('explosion.distant', 'ordnance', 'explosion_distant',
       lambda s: explosion(s, 1.6, 4.0, distant=True, peak=0.6), 2, 384, 'explosion.distant')
    ev('flashbang.bang', 'ordnance', 'flashbang_bang', make_shot(dict(
        pulse=2.5, pulse_T=0.0015, crack=1.6, crack_hp=1500, crack_tau=0.0015, blast_tau=0.02, blast_lp=10000,
        blast_peak=(2200, 4), body=1.0, body_f=(170, 60), body_tau=0.04, mech=0.0, tail=0.08, t60=1.2,
        tail_lp=(3500, 700), echo=(0.15, 0.32, 0.55), echo_gain=0.16, drive=3.0, peak=0.92, length=1.6)),
       2, 96, 'flashbang')
    ev('flashbang.ring', 'ordnance', 'flashbang_ring', s_flash_ring, 1, 8, 'flashbang.ring')
    ev('incendiary.ignite', 'ordnance', 'incendiary_ignite', s_incendiary, 2, 48, 'incendiary')
    ev('smoke.hiss', 'ordnance', 'smoke_hiss', s_smoke, 2, 32, 'smoke')
    ev('chemical.release', 'ordnance', 'chemical_release', s_chemical, 2, 48, 'chemical')
    ev('singularity.collapse', 'ordnance', 'singularity_collapse', s_singularity, 2, 128, 'singularity')
    ev('singularity.pulse', 'ordnance', 'singularity_pulse', s_singularity_pulse, 2, 96, 'singularity.pulse')
    ev('ion.charge', 'ordnance', 'ion_charge', s_ion_charge, 1, 96, 'ion.charge')
    ev('ion.strike', 'ordnance', 'ion_strike', lambda s: explosion(s, 1.5, 4.5, extras=(zap,)), 3, 192, 'ion.strike')
    ev('nuke.detonate', 'ordnance', 'nuke_detonate',
       lambda s: explosion(s, 2.8, 9.0, whoomp=True, peak=0.95), 1, 1024, 'nuke')
    ev('nuke.beep', 'ordnance', 'nuke_beep', s_nuke_beep, 1, 32, 'nuke.beep')

    ev('jet.engine', 'jet', 'engine', s_jet_engine, 1, 160, 'jet.engine')
    ev('jet.afterburner', 'jet', 'afterburner', s_jet_afterburner, 1, 240, 'jet.afterburner')
    ev('jet.wind', 'jet', 'wind', s_jet_wind, 1, 16, 'jet.wind')
    ev('jet.gun', 'jet', 'gun', s_jet_gun, 1, 160, 'jet.gun')
    ev('jet.gun_stop', 'jet', 'gun_stop', s_jet_gun_stop, 2, 64, 'jet.gun')
    ev('jet.missile', 'jet', 'missile', s_jet_missile, 2, 128, 'jet.missile')
    ev('jet.rocket', 'jet', 'rocket', s_jet_rocket, 3, 96, 'jet.rocket')
    ev('jet.bomb_release', 'jet', 'bomb_release', s_jet_bomb_release, 2, 32, 'jet.bomb_release')
    ev('jet.flare', 'jet', 'flare', s_jet_flare, 2, 48, 'jet.flare')
    ev('jet.gear', 'jet', 'gear', s_jet_gear, 1, 24, 'jet.gear')
    ev('jet.canopy', 'jet', 'canopy', s_jet_canopy, 1, 16, 'jet.canopy')
    ev('jet.touchdown', 'jet', 'touchdown', s_jet_touchdown, 2, 48, 'jet.touchdown')
    ev('jet.eject', 'jet', 'eject', s_jet_eject, 1, 96, 'jet.eject')
    # cockpit tones: played relative to the listener (no attenuation), the range only has to match ModSounds
    ev('jet.seeker', 'jet', 'seeker', s_jet_seeker, 1, 8, 'jet.seeker')
    ev('jet.lock', 'jet', 'lock', s_jet_lock, 1, 8, 'jet.lock')
    ev('jet.warning', 'jet', 'warning', s_jet_warning, 1, 8, 'jet.warning')
    ev('jet.stall', 'jet', 'stall', s_jet_stall, 1, 8, 'jet.stall')
    return c


# =====================================================================================================
# encoding
# =====================================================================================================

def find_ffmpeg():
    """An ffmpeg that has libvorbis: $FFMPEG, the PATH, then the copies other apps on this machine ship."""
    candidates = []
    if os.environ.get('FFMPEG'):
        candidates.append(os.environ['FFMPEG'])
    if shutil.which('ffmpeg'):
        candidates.append(shutil.which('ffmpeg'))
    local = os.environ.get('LOCALAPPDATA', '')
    candidates += sorted(glob.glob(os.path.join(local, 'Overwolf', 'Extensions', '*', '*', 'obs', 'bin', '64bit',
                                                'ffmpeg.exe')), reverse=True)
    candidates += sorted(glob.glob(os.path.join(local, 'CapCut', 'Apps', '*', 'ffmpeg.exe')), reverse=True)
    fallback = None
    for exe in candidates:
        try:
            encoders = subprocess.run([exe, '-hide_banner', '-encoders'], capture_output=True, text=True,
                                      timeout=30).stdout
        except (OSError, subprocess.SubprocessError):
            continue
        if 'libvorbis' in encoders:
            return exe, 'libvorbis'
        if ' vorbis ' in encoders and not fallback:
            fallback = (exe, 'vorbis')
    return fallback or (None, None)


def write_wav(path, samples):
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b''.join(int(max(-1.0, min(1.0, v)) * 32767).to_bytes(2, 'little', signed=True)
                               for v in samples))


def render(task):
    """Worker: synthesise one file and write its WAV. Returns (stem, wav path, samples for the preview)."""
    event, folder, stem, k = task
    maker = {e: m for e, _, _, m, _, _, _ in catalogue()}[event]
    samples = maker(zlib.crc32(('%s#%d' % (event, k)).encode()))
    name = '%s_%d' % (stem, k)
    path = os.path.join(BUILD, folder, name + '.wav')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    write_wav(path, samples)
    return folder, name, path, samples


def encode(ffmpeg, codec, wav, ogg):
    os.makedirs(os.path.dirname(ogg), exist_ok=True)
    cmd = [ffmpeg, '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', codec]
    cmd += ['-q:a', '5'] if codec == 'libvorbis' else ['-strict', '-2', '-b:a', '112k']
    cmd += ['-fflags', '+bitexact', '-flags:a', '+bitexact', '-map_metadata', '-1', ogg]
    subprocess.run(cmd, check=True)


# =====================================================================================================
# preview: level envelope + band energies per sound
# =====================================================================================================

def preview(results, path):
    sys.path.insert(0, HERE)
    from pixels import Img  # noqa: E402
    bands = [(60, 'lp'), (150, 'bp'), (400, 'bp'), (1000, 'bp'), (2500, 'bp'), (6000, 'bp'), (12000, 'hp')]
    panel_w, panel_h = 300, 96
    cols = 4
    rows = (len(results) + cols - 1) // cols
    img = Img(cols * (panel_w + 6) + 6, rows * (panel_h + 18) + 6, (28, 28, 32, 255))
    for idx, (name, samples) in enumerate(results):
        ox = 6 + (idx % cols) * (panel_w + 6)
        oy = 6 + (idx // cols) * (panel_h + 18)
        for y in range(panel_h):
            for x in range(panel_w):
                img.set(ox + x, oy + y, (16, 16, 20, 255))
        seconds = len(samples) / float(SR)
        frame = max(1, len(samples) // panel_w)
        # band energies: bottom 7 rows of 6 px
        for bi, (f, kind) in enumerate(bands):
            y = biquad(samples, kind, f, 1.0)
            for x in range(panel_w):
                seg = y[x * frame:(x + 1) * frame]
                if not seg:
                    continue
                e = math.sqrt(sum(v * v for v in seg) / len(seg))
                db = 20 * math.log10(e + 1e-9)
                t = max(0.0, min(1.0, (db + 60) / 60.0))
                c = (int(255 * t), int(180 * t * t), int(60 + 120 * (1 - t) * t), 255)
                for yy in range(6):
                    img.set(ox + x, oy + panel_h - 1 - (bi * 6 + yy), c)
        # level envelope (peak per column, dB) above
        top = panel_h - 7 * 6 - 2
        for x in range(panel_w):
            seg = samples[x * frame:(x + 1) * frame]
            if not seg:
                continue
            p = max(abs(v) for v in seg)
            db = 20 * math.log10(p + 1e-9)
            h = int(max(0.0, min(1.0, (db + 60) / 60.0)) * top)
            for yy in range(h):
                img.set(ox + x, oy + top - yy, (90, 200, 255, 255))
        # 100 ms ticks
        for k in range(int(seconds * 10) + 1):
            x = int(k * 0.1 * SR / frame)
            if x < panel_w:
                img.set(ox + x, oy + top + 1, (255, 255, 255, 255))
        _label(img, name, ox, oy + panel_h + 3)
    img.save(path)


FONT = {  # 3x5 digits/letters for labels
    'a': '010101111101101', 'b': '110101110101110', 'c': '011100100100011', 'd': '110101101101110',
    'e': '111100110100111', 'f': '111100110100100', 'g': '011100101101011', 'h': '101101111101101',
    'i': '111010010010111', 'j': '001001001101010', 'k': '101101110101101', 'l': '100100100100111',
    'm': '101111111101101', 'n': '110101101101101', 'o': '010101101101010', 'p': '110101110100100',
    'q': '010101101110011', 'r': '110101110101101', 's': '011100010001110', 't': '111010010010010',
    'u': '101101101101111', 'v': '101101101101010', 'w': '101101111111101', 'x': '101101010101101',
    'y': '101101010010010', 'z': '111001010100111', '_': '000000000000111', '0': '111101101101111',
    '1': '010110010010111', '2': '110001010100111', '3': '110001010001110', '4': '101101111001001',
    '5': '111100110001110', '6': '011100111101111', '7': '111001010010010', '8': '111101111101111',
    '9': '111101111001110',
}


def _label(img, text, x, y):
    for ch in text.lower():
        glyph = FONT.get(ch)
        if glyph:
            for i, bit in enumerate(glyph):
                if bit == '1':
                    img.set(x + i % 3, y + i // 3, (220, 220, 220, 255))
        x += 4


# =====================================================================================================
# main
# =====================================================================================================

def main():
    args = [a for a in sys.argv[1:] if a != 'preview']
    cat = catalogue()
    tasks = [(event, folder, stem, k) for event, folder, stem, _, variants, _, _ in cat
             for k in range(1, variants + 1)
             if not args or any(a in '%s_%d' % (stem, k) for a in args)]
    ffmpeg, codec = find_ffmpeg()
    if not ffmpeg:
        sys.exit('no ffmpeg with a Vorbis encoder found (set FFMPEG=path\\to\\ffmpeg.exe); sounds not written')
    print('encoding with %s (%s), %d files' % (ffmpeg, codec, len(tasks)))
    if not args and os.path.isdir(SOUND_DIR):
        shutil.rmtree(SOUND_DIR)
    results = []
    with ProcessPoolExecutor() as pool:
        for folder, name, wav, samples in pool.map(render, tasks):
            encode(ffmpeg, codec, wav, os.path.join(SOUND_DIR, folder, name + '.ogg'))
            results.append((name, samples))
    # sounds.json always lists everything
    sounds = {}
    for event, folder, stem, _, variants, attenuation, subtitle in cat:
        sounds[event] = {
            'subtitle': 'subtitles.arsenal.' + subtitle,
            'sounds': [{'name': 'arsenal:%s/%s_%d' % (folder, stem, k), 'attenuation_distance': attenuation}
                       for k in range(1, variants + 1)],
        }
    with open(os.path.join(ASSETS, 'sounds.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(sounds, f, indent=2)
        f.write('\n')
    size = sum(os.path.getsize(p) for p in glob.glob(os.path.join(SOUND_DIR, '**', '*.ogg'), recursive=True))
    print('%d sounds written (%d events), %.0f KB of Ogg Vorbis' % (len(results), len(sounds), size / 1024.0))
    if 'preview' in sys.argv:
        path = os.path.normpath(os.path.join(BUILD, '..', 'sounds_preview.png'))
        preview(results, path)
        print('preview: ' + path)


if __name__ == '__main__':
    main()

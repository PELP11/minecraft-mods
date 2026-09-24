#!/usr/bin/env python3
"""Generates every Juicer texture (pure Python, no dependencies).

Run from anywhere:  py tools/gen_textures.py  [--preview DIR]
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixels import (CLEAR, Img, WrapNoise, darken, from_ascii, hexc, lighten, mix, point_in_poly,
                    preview_sheet, shade, shaded_ellipse, star_poly, vstack)

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources'))
TEX = os.path.join(RES, 'assets', 'juicer', 'textures')

FRUITS = ['starfruit', 'dragonfruit', 'pomegranate', 'orange', 'lime']

# Signature colour of each fruit's concentrate / juice (also used by the Java code).
JUICE_COLORS = {
    'starfruit': '#ffc81e',
    'dragonfruit': '#f0287f',
    'pomegranate': '#b3122a',
    'orange': '#ff8a12',
    'lime': '#7ed63a',
}


def out(path):
    full = os.path.join(TEX, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


# =============================================================================
# Fruit items (16x16)
# =============================================================================

def fruit_orange():
    img = Img(16, 16)
    ramp = [hexc('#b04c00'), hexc('#d8650c'), hexc('#f2861a'), hexc('#ffa53a'), hexc('#ffc46e')]
    filled = shaded_ellipse(img, 8.0, 9.2, 6.3, 6.1, ramp, outline=hexc('#7a3304'),
                            spec=hexc('#ffe7bd'), spec_cut=0.93)
    rnd = random.Random(7)
    for (x, y) in sorted(filled):
        c = img.get(x, y)
        if c != hexc('#7a3304') and c != hexc('#ffe7bd') and rnd.random() < 0.14:
            img.set(x, y, darken(c, 0.12))
    # navel / stem and a leaf
    img.set(8, 3, hexc('#6b3a12'))
    img.set(8, 2, hexc('#5a3a1a'))
    for (x, y, c) in [(9, 2, '#6fbf3e'), (10, 1, '#6fbf3e'), (10, 2, '#4a9a2c'), (11, 1, '#4a9a2c'),
                      (12, 1, '#2f6e1e'), (11, 2, '#2f6e1e'), (9, 1, '#8fd65a')]:
        img.set(x, y, hexc(c))
    return img


def fruit_lime():
    img = Img(16, 16)
    ramp = [hexc('#2c6610'), hexc('#46891b'), hexc('#66ad29'), hexc('#8ccf45'), hexc('#bdeb80')]
    outline = hexc('#1d460a')
    filled = shaded_ellipse(img, 8.0, 8.6, 6.6, 5.0, ramp, rot_deg=-32, outline=outline,
                            spec=hexc('#e6ffc6'), spec_cut=0.93)
    rnd = random.Random(11)
    for (x, y) in sorted(filled):
        c = img.get(x, y)
        if c not in (outline, hexc('#e6ffc6')) and rnd.random() < 0.12:
            img.set(x, y, lighten(c, 0.12))
    # little nubs at both ends of the long axis
    a = math.radians(-32)
    for s in (1, -1):
        nx = 8.0 + s * 7.0 * math.cos(a)
        ny = 8.6 + s * 7.0 * math.sin(a)
        img.set(int(nx), int(ny), hexc('#35761a'))
    return img


def fruit_pomegranate():
    img = Img(16, 16)
    ramp = [hexc('#5c0913'), hexc('#8a0f1f'), hexc('#b3172a'), hexc('#d6303f'), hexc('#f0606c')]
    shaded_ellipse(img, 8.0, 9.4, 6.3, 6.0, ramp, outline=hexc('#3c050c'),
                   spec=hexc('#ffb3ba'), spec_cut=0.93)
    crown = from_ascii([
        "c.c.c",
        "cCCCc",
        ".DDD.",
    ], {'c': hexc('#b8323f'), 'C': hexc('#8e1a26'), 'D': hexc('#5e0b14')})
    img.paste(crown, 6, 1)
    return img


def fruit_dragonfruit():
    img = Img(16, 16)
    ramp = [hexc('#8a0c47'), hexc('#b5165d'), hexc('#dd2878'), hexc('#f25596'), hexc('#ff8cbf')]
    outline = hexc('#590630')
    shaded_ellipse(img, 8.0, 9.0, 5.4, 6.5, ramp, rot_deg=12, outline=outline,
                   spec=hexc('#ffc4de'), spec_cut=0.93)
    green, lgreen = hexc('#4f9e2f'), hexc('#86d05a')
    # green-tipped scales (bracts)
    for (x, y) in [(5, 6), (10, 5), (8, 9), (4, 11), (11, 10), (7, 13), (9, 12)]:
        img.set(x, y, hexc('#ff9ccb'))
        img.set(x + 1, y - 1, lgreen)
    for (x, y) in [(3, 8), (12, 7), (12, 12)]:
        img.set(x, y, green)
    # leafy top
    for (x, y, c) in [(7, 2, green), (8, 1, lgreen), (9, 2, green), (6, 1, lgreen), (10, 1, green), (8, 2, lgreen)]:
        img.set(x, y, c)
    return img


def fruit_starfruit():
    img = Img(16, 16)
    cx, cy = 8.0, 8.6
    poly = star_poly(cx, cy, 7.6, 3.5, rot_deg=-90)
    ramp = [hexc('#a17200'), hexc('#cf9a00'), hexc('#f2c214'), hexc('#ffdc45'), hexc('#fff09c')]
    light = (-0.55, -0.75)
    filled = set()
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            if not point_in_poly(px, py, poly):
                continue
            filled.add((x, y))
            dx, dy = px - cx, py - cy
            ang = math.atan2(dy, dx)
            # nearest arm direction
            best = None
            for k in range(5):
                a = math.radians(-90 + k * 72)
                diff = math.atan2(math.sin(ang - a), math.cos(ang - a))
                if best is None or abs(diff) < abs(best[1]):
                    best = (a, diff)
            a, diff = best
            # facet normal: tilts away from the ridge line
            side = 1 if diff > 0 else -1
            perp = (-math.sin(a) * side, math.cos(a) * side)
            nx, ny, nz = perp[0] * 0.75, perp[1] * 0.75, 0.66
            inten = nx * light[0] + ny * light[1] + nz * 0.45
            dist = math.hypot(dx, dy)
            inten += 0.25 * (1 - dist / 7.6)
            if abs(diff) * dist < 0.55:
                inten += 0.28  # ridge highlight
            t = max(0.0, min(0.999, (inten + 0.2) / 1.2))
            img.set(x, y, ramp[int(t * len(ramp))])
    outline = hexc('#6e4a00')
    for (x, y) in list(filled):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in filled:
                img.set(x, y, outline)
                break
    # small green stem on the top point
    img.set(8, 1, hexc('#5a8f2a'))
    return img


FRUIT_SPRITES = {
    'starfruit': fruit_starfruit,
    'dragonfruit': fruit_dragonfruit,
    'pomegranate': fruit_pomegranate,
    'orange': fruit_orange,
    'lime': fruit_lime,
}


# =============================================================================
# Juice bottles (16x16)
# =============================================================================

BOTTLE = [
    "................",
    "......cCCc......",
    "......cCCc......",
    ".....oGGGGo.....",
    "......oGGo......",
    "......oLLo......",
    ".....oLLLLo.....",
    "....oLLLLLLo....",
    "...oLLLLLLLLo...",
    "...oLwLLLLLLo...",
    "...oLwLLLLLLo...",
    "...oLLLLLLLLo...",
    "...oLLLLLLLLo...",
    "...oLLLLLLLLo...",
    "....oLLLLLLo....",
    ".....oooooo.....",
]


def juice_bottle(color_hex):
    base = hexc(color_hex)
    img = Img(16, 16)
    glass_out = hexc('#2c3140')
    for y, row in enumerate(BOTTLE):
        for x, ch in enumerate(row):
            if ch == 'c':
                img.set(x, y, hexc('#9a6534'))
            elif ch == 'C':
                img.set(x, y, hexc('#c08448'))
            elif ch == 'o':
                img.set(x, y, glass_out)
            elif ch == 'G':
                img.set(x, y, hexc('#d9eefc'))
            elif ch == 'w':
                img.set(x, y, hexc('#ffffff'))
            elif ch == 'L':
                # shade the liquid: lighter on the left/top, darker to the right/bottom
                row_l = row.index('L')
                row_r = row.rindex('L')
                t = (x - row_l) / max(1, (row_r - row_l))
                c = mix(lighten(base, 0.28), darken(base, 0.30), t * 0.85 + (y - 5) / 30.0)
                img.set(x, y, c)
    # liquid surface line + bubbles
    for x in range(6, 10):
        img.set(x, 5, lighten(base, 0.45))
    img.set(9, 11, lighten(base, 0.55))
    img.set(10, 9, lighten(base, 0.45))
    img.set(7, 13, lighten(base, 0.40))
    return img


# =============================================================================
# Saplings (16x16 cross textures)
# =============================================================================

SAPLING = [
    "................",
    "................",
    ".....lL..Ll.....",
    "...lLLLLLLLLl...",
    "..lLLLDLLDLLLl..",
    "..LLDLLffLLDLL..",
    "...LLLLffLLLL...",
    "....DLLtLLLD....",
    ".....LLtLD......",
    ".......tt.......",
    ".......t........",
    "......Tt........",
    ".......tT.......",
    ".......t........",
    ".......t........",
    ".......T........",
]


def sapling(leaf_hex, trunk_hex, fruit_hex):
    leaf = hexc(leaf_hex)
    trunk = hexc(trunk_hex)
    pal = {
        'l': lighten(leaf, 0.25), 'L': leaf, 'D': darken(leaf, 0.3),
        't': trunk, 'T': darken(trunk, 0.25), 'f': hexc(fruit_hex),
    }
    img = from_ascii(SAPLING, pal)
    img.set(7, 5, lighten(hexc(fruit_hex), 0.35))
    return img


# =============================================================================
# Fruit leaves (16x16) - four growth stages each
# =============================================================================

TREES = {
    # leaf colours (dark -> light), blossom colour, fruit mini sprite, fruit positions
    'starfruit': dict(leaf=['#1f5a1c', '#2e7a26', '#3f9a32', '#5bb844'], seed=101, blossom='#f3c4f0'),
    'dragonfruit': dict(leaf=['#1c5446', '#277260', '#34917a', '#4fb394'], seed=202, blossom='#fff5d6'),
    'pomegranate': dict(leaf=['#1a4a16', '#256420', '#327f2a', '#46a03a'], seed=303, blossom='#ff6a4d'),
    'orange': dict(leaf=['#1b4d19', '#276a22', '#35882d', '#4ea843'], seed=404, blossom='#ffffff'),
    'lime': dict(leaf=['#123b17', '#1b5220', '#256a2b', '#347f39'], seed=505, blossom='#fffbe0'),
}

MINI_RIPE = {
    'orange': (["..o.", ".hmo", "omMd", ".dd."], {'o': '#8a3c06', 'h': '#ffc46e', 'm': '#f2861a', 'M': '#e57a12', 'd': '#b04c00'}),
    'lime': ([".oo.", "ohmo", "omMo", ".oo."], {'o': '#3f7a12', 'h': '#f0ffc0', 'm': '#b4ec5a', 'M': '#94d63c', 'd': '#5a9e24'}),
    'pomegranate': (["c.c.", "hmMo", "mMMd", ".dd."], {'c': '#8e1a26', 'h': '#f0606c', 'm': '#c62437', 'M': '#a3172a', 'd': '#6a0a16', 'o': '#6a0a16'}),
    'dragonfruit': (["g..g", "hmmo", "mMgd", "gdd."], {'g': '#6cc24a', 'h': '#ff8cbf', 'm': '#e5307f', 'M': '#c41c66', 'd': '#8a0c47', 'o': '#8a0c47'}),
    'starfruit': ([".y..", "yhmy", ".mMd", "y.d."], {'y': '#d9a100', 'h': '#fff09c', 'm': '#ffd23a', 'M': '#f0b814', 'd': '#b58300'}),
}

MINI_UNRIPE = (["gG", "Gd"], {'g': '#b7d86a', 'G': '#8fbf45', 'd': '#6e9c30'})
FRUIT_SPOTS = [(2, 2), (10, 1), (6, 8), (12, 10), (1, 12)]


def leaves_base(name):
    t = TREES[name]
    ramp = [hexc(c) for c in t['leaf']]
    n1 = WrapNoise(t['seed'], cell=4)
    n2 = WrapNoise(t['seed'] + 1, cell=2)
    rnd = random.Random(t['seed'] + 2)
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            v = 0.65 * n1.at(x, y) + 0.35 * n2.at(x, y) + (rnd.random() - 0.5) * 0.18
            if v < 0.22:
                continue  # hole (cutout)
            idx = 0 if v < 0.36 else 1 if v < 0.55 else 2 if v < 0.74 else 3
            img.set(x, y, ramp[idx])
    # little dark "shadow" under bright clusters for depth
    for y in range(15, 0, -1):
        for x in range(16):
            if img.get(x, y)[3] and img.get(x, y - 1) == ramp[3] and img.get(x, y) == ramp[2]:
                img.set(x, y, ramp[1])
    return img


def leaves_stage(name, stage):
    img = leaves_base(name)
    t = TREES[name]
    if stage == 1:  # blossoms
        bl = hexc(t['blossom'])
        for (x, y) in FRUIT_SPOTS:
            for dx, dy in ((1, 0), (0, 1), (2, 1), (1, 2)):
                img.set(x + dx, y + dy, bl)
            img.set(x + 1, y + 1, hexc('#ffd83a'))
    elif stage == 2:  # small unripe fruit
        rows, pal = MINI_UNRIPE
        spr = from_ascii(rows, {k: hexc(v) for k, v in pal.items()})
        for (x, y) in FRUIT_SPOTS:
            img.paste(spr, x + 1, y + 1)
    elif stage == 3:  # ripe fruit
        rows, pal = MINI_RIPE[name]
        spr = from_ascii(rows, {k: hexc(v) for k, v in pal.items()})
        for (x, y) in FRUIT_SPOTS:
            img.paste(spr, x, y)
    return img


# =============================================================================
# Animated concentrate / juice liquid (16x16 per frame)
# =============================================================================

def liquid_frames(color_hex, seed, frames=16):
    base = hexc(color_hex, 222)  # slightly see-through so the spinning blades show
    out_frames = []
    na = WrapNoise(seed, cell=4)
    nb = WrapNoise(seed + 7, cell=8)
    for f in range(frames):
        img = Img(16, 16)
        ph = f / frames
        for y in range(16):
            for x in range(16):
                # two noise layers drifting in different directions -> swirl
                v = 0.6 * na.at(x + ph * 16, y + ph * 8) + 0.4 * nb.at(x - ph * 8, y + ph * 16)
                if v > 0.7:
                    c = lighten(base, 0.30)
                elif v > 0.58:
                    c = lighten(base, 0.14)
                elif v < 0.3:
                    c = darken(base, 0.18)
                else:
                    c = base
                img.set(x, y, c)
        out_frames.append(img)
    return vstack(out_frames)


# =============================================================================
# Machines
# =============================================================================

def brushed_metal(seed, base='#b9bec6'):
    b = hexc(base)
    rnd = random.Random(seed)
    img = Img(16, 16)
    for y in range(16):
        row_shift = (rnd.random() - 0.5) * 0.08
        for x in range(16):
            f = 1.0 + row_shift + (rnd.random() - 0.5) * 0.05
            img.set(x, y, shade(b, f))
    return img


def mixer_textures():
    res = {}
    # housing side: trim bands + vents in the lower part used by the base (y 10..16)
    side = brushed_metal(1)
    dark = hexc('#5d636d')
    for x in range(16):
        side.set(x, 10, hexc('#e6e9ee'))
        side.set(x, 15, dark)
    for x in range(3, 13, 2):
        side.vline(x, 12, 14, hexc('#3b4048'))
    res['block/mixer_side'] = side

    front = brushed_metal(2)
    for x in range(16):
        front.set(x, 10, hexc('#e6e9ee'))
        front.set(x, 15, dark)
    # control panel: dial + two buttons
    front.rect(3, 11, 13, 15, hexc('#2d3139'))
    dial = from_ascii([".kk.", "kwwk", "kwrk", ".kk."], {'k': hexc('#1b1e24'), 'w': hexc('#d9dde3'), 'r': hexc('#e0392b')})
    front.paste(dial, 4, 11)
    front.set(9, 12, hexc('#3ddc6b'))
    front.set(9, 13, hexc('#1e8a3c'))
    front.set(11, 12, hexc('#ff5a4a'))
    front.set(11, 13, hexc('#a8261c'))
    res['block/mixer_front'] = front

    top = brushed_metal(3)
    top.frame(0, 0, 16, 16, hexc('#8a9099'))
    top.frame(3, 3, 13, 13, hexc('#474c55'))
    res['block/mixer_top'] = top

    bottom = brushed_metal(4, '#6f757e')
    res['block/mixer_bottom'] = bottom

    # glass jar panel (the models use the top-left 8x8): opaque frame + streaks, lightly tinted
    # translucent inside so the jar reads as glass (26.3 picks the translucent layer automatically)
    glass = Img(16, 16, hexc('#e8f6ff', 46))
    edge = hexc('#cfeaf7')
    glass.frame(0, 0, 8, 8, edge)
    glass.set(2, 5, hexc('#ffffff', 210))
    glass.set(2, 4, hexc('#ffffff', 210))
    glass.set(3, 3, hexc('#ffffff', 210))
    glass.set(3, 2, hexc('#ffffff', 210))
    # measuring marks
    for y in (2, 4, 6):
        glass.set(6, y, hexc('#9fd0e6', 230))
    res['block/mixer_glass'] = glass

    # bright orange lid + jar collar so the blender reads well (and its item icon isn't a dark blob)
    lid = Img(16, 16, hexc('#f07f16'))
    lid.frame(0, 0, 16, 16, hexc('#c46210'))
    lid.frame(1, 1, 15, 15, hexc('#ff9f3d'))
    lid.frame(3, 3, 13, 13, hexc('#d86e12'))
    lid.rect(6, 6, 10, 10, hexc('#ffc27a'))
    lid.set(6, 6, hexc('#fff0d8'))
    res['block/mixer_lid'] = lid

    # blades (static) and spinning animation
    def blade_frame(angle_deg, blur=False):
        img = Img(16, 16)
        steel, steel_d = hexc('#e3e7ec'), hexc('#8d949e')
        for k in range(4):
            a = math.radians(angle_deg + k * 90)
            for r10 in range(8, 72):
                r = r10 / 10.0
                w = 1.4 if r < 5 else 1.0
                for s in (-w, 0, w):
                    x = 7.5 + r * math.cos(a) - s * math.sin(a) * 0.5
                    y = 7.5 + r * math.sin(a) + s * math.cos(a) * 0.5
                    xi, yi = int(round(x)), int(round(y))
                    img.set(xi, yi, steel if s <= 0 else steel_d)
        if blur:
            for y in range(16):
                for x in range(16):
                    d = math.hypot(x - 7.5, y - 7.5)
                    if 3.5 < d < 7.2 and not img.get(x, y)[3] and (x + y) % 3 == 0:
                        img.set(x, y, hexc('#c7cdd6'))
        for dx in range(6, 10):
            for dy in range(6, 10):
                img.set(dx, dy, hexc('#5a616b'))
        img.set(7, 7, hexc('#9aa2ad'))
        return img

    res['block/mixer_blades'] = blade_frame(20)
    res['block/mixer_blades_spinning'] = vstack([blade_frame(a, blur=True) for a in (0, 30, 60)])
    return res


def infuser_textures():
    res = {}
    copper = [hexc('#8c3f1e'), hexc('#b0552a'), hexc('#c96b38'), hexc('#e0895a'), hexc('#f2ae84')]
    rnd = random.Random(42)

    # The tub walls use rows 6-15 of this texture (10px tall walls).
    side = Img(16, 16)
    for y in range(16):
        for x in range(16):
            v = rnd.random()
            side.set(x, y, copper[2] if v < 0.7 else copper[3] if v < 0.88 else copper[1])
    for x in range(16):
        side.set(x, 6, copper[4])    # polished rim
        side.set(x, 7, copper[0])    # upper band
        side.set(x, 14, copper[0])   # lower band
        side.set(x, 15, hexc('#6e2e14'))
    for x in (2, 6, 10, 14):
        side.set(x, 7, hexc('#f5c9a8'))   # rivets
        side.set(x, 14, hexc('#f5c9a8'))
    side.vline(8, 8, 14, copper[1])       # plate seam
    for (x, y) in [(3, 10), (4, 11), (12, 9), (11, 12)]:
        side.set(x, y, hexc('#5fa88a'))   # a little patina
    res['block/infuser_side'] = side

    front = side.copy()
    # brass plate with a red juice drop in the middle of the front wall
    front.rect(5, 8, 11, 14, hexc('#d9b04a'))
    front.frame(5, 8, 11, 14, hexc('#8a6a1c'))
    for (x, y) in [(8, 9), (7, 10), (8, 10), (7, 11), (8, 11), (9, 11), (8, 12)]:
        front.set(x, y, hexc('#e0392b'))
    front.set(7, 10, hexc('#ff8a7a'))
    res['block/infuser_front'] = front

    inner = Img(16, 16)
    for y in range(16):
        for x in range(16):
            v = rnd.random()
            inner.set(x, y, copper[1] if v < 0.6 else copper[0] if v < 0.85 else copper[2])
    res['block/infuser_inner'] = inner

    top = Img(16, 16)
    for y in range(16):
        for x in range(16):
            top.set(x, y, copper[3] if (x + y) % 5 else copper[4])
    res['block/infuser_rim'] = top

    iron = [hexc('#34383f'), hexc('#4a4f57'), hexc('#5f656e'), hexc('#7d838c')]
    stand = Img(16, 16)
    for y in range(16):
        for x in range(16):
            v = rnd.random()
            stand.set(x, y, iron[1] if v < 0.65 else iron[2] if v < 0.9 else iron[0])
    stand.frame(0, 0, 16, 16, iron[0])
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        stand.set(x, y, iron[3])
    res['block/infuser_stand'] = stand

    # small brass parts (tap) only use tiny corner regions, so keep it a plain shaded brass
    tap = Img(16, 16)
    for y in range(16):
        for x in range(16):
            tap.set(x, y, hexc('#d6ae44') if (x + y) % 3 else hexc('#b58f2c'))
    tap.set(0, 0, hexc('#f0d27a'))
    res['block/infuser_tap'] = tap
    return res


def tubing_textures():
    """Tubing UV layout: cols 0-11 x rows 0-3 = pipe seen from the side (light top -> dark bottom),
    cols 12-15 = pipe seen from above/for vertical pipes (light left -> dark right)."""
    res = {}
    cu = [hexc('#6e2f16'), hexc('#a14a24'), hexc('#c8693a'), hexc('#e68d5e'), hexc('#f7b58e')]
    rnd = random.Random(9)
    tube = Img(16, 16, cu[2])
    profile_rows = [cu[4], cu[3], cu[2], cu[1]]
    for y in range(16):
        for x in range(12):
            tube.set(x, y, profile_rows[y % 4])
        for i, c in enumerate([cu[3], cu[4], cu[2], cu[1]]):
            tube.set(12 + i, y, c)
    # a darker clamp band every few pixels + a few specks of wear
    for y in range(4):
        tube.set(2, y, darken(profile_rows[y], 0.25))
        tube.set(9, y, darken(profile_rows[y], 0.25))
    for x in range(12, 16):
        tube.set(x, 3, darken(tube.get(x, 3), 0.25))
        tube.set(x, 11, darken(tube.get(x, 11), 0.25))
    for _ in range(6):
        x, y = rnd.randrange(12), rnd.randrange(4)
        tube.set(x, y, lighten(tube.get(x, y), 0.12))
    res['block/tubing'] = tube

    # pipe opening, used on the 4x4 region 6..10
    end = Img(16, 16, cu[2])
    end.frame(6, 6, 10, 10, cu[3])
    end.rect(7, 7, 9, 9, hexc('#2a1208'))
    res['block/tubing_end'] = end

    # brass joint, used on the 5..11 region (core 5.5..10.5 and flanges)
    joint = Img(16, 16)
    brass = [hexc('#6e4f12'), hexc('#a8801f'), hexc('#cfa233'), hexc('#ecc55a')]
    for y in range(16):
        for x in range(16):
            joint.set(x, y, brass[2])
    joint.frame(5, 5, 11, 11, brass[1])
    joint.hline(6, 10, 6, brass[3])
    joint.vline(6, 6, 10, brass[3])
    for (x, y) in [(6, 6), (9, 6), (6, 9), (9, 9)]:
        joint.set(x, y, hexc('#fff0b0'))
    joint.set(10, 10, brass[0])
    res['block/tubing_joint'] = joint
    return res


# =============================================================================
# Mob effect icons (18x18)
# =============================================================================

def icon_looting():
    img = Img(18, 18)
    # sword (diagonal) with a gold coin
    blade, blade_d, hilt, grip = hexc('#e6ebf0'), hexc('#9aa3ad'), hexc('#d9a520'), hexc('#6b4a22')
    for i in range(9):
        img.set(3 + i, 2 + i, blade)
        img.set(4 + i, 2 + i, blade_d)
    img.set(3, 2, hexc('#ffffff'))
    for (x, y) in [(10, 13), (11, 12), (12, 11), (13, 10), (9, 14), (14, 9)]:
        img.set(x, y, hilt)
    img.set(13, 13, grip)
    img.set(14, 14, grip)
    img.set(15, 15, hilt)
    coin = Img(18, 18)
    shaded_ellipse(coin, 5.0, 12.5, 4.2, 4.2, [hexc('#b37a00'), hexc('#e0a800'), hexc('#ffd000'), hexc('#ffe866')],
                   outline=hexc('#7a5200'))
    coin.set(5, 12, hexc('#fff6c2'))
    coin.vline(5, 11, 15, hexc('#9a6a00'))
    img.paste(coin, 0, 0)
    return img


def icon_fortune():
    img = Img(18, 18)
    gem = [
        "...oooooooo...",
        "..oLLwLLwLLo..",
        ".oLLLwLLwLLLo.",
        "oooooooooooooo",
        ".oMMmMMMmMMMo.",
        "..oMMmMMmMMo..",
        "...oMmMMmMo...",
        "....oMmmMo....",
        ".....oMMo.....",
        "......oo......",
    ]
    pal = {'o': hexc('#16505a'), 'L': hexc('#a8fff6'), 'w': hexc('#ffffff'),
           'M': hexc('#4fd9d0'), 'm': hexc('#2fa6a8')}
    img.paste(from_ascii(gem, pal), 2, 5)
    gold, spark = hexc('#ffd84a'), hexc('#fff6c2')
    for (cx, cy) in [(3, 2), (15, 14)]:
        img.set(cx, cy, spark)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            img.set(cx + dx, cy + dy, gold)
    img.set(14, 3, gold)
    return img


def icon_flight():
    img = Img(18, 18)
    # left wing, root at the bottom right; the right wing is its mirror image
    wing = [
        "W.......",
        "WW......",
        "WWG.....",
        "WWWW....",
        ".WWGW...",
        ".PWWWW..",
        "..PWGWW.",
        "..PPWWWW",
        "...PPWWW",
        "....PPWW",
        "......PW",
    ]
    pal = {'W': hexc('#f6f0ff'), 'G': hexc('#c9bfe0'), 'P': hexc('#f0287f')}
    left = from_ascii(wing, pal)
    img.paste(left, 1, 3)
    img.paste(left.flipped_x(), 9, 3)
    img.outline(hexc('#5a2a7a'))
    return img


def icon_titan():
    img = Img(18, 18)
    shield = [
        "..oooooooooo..",
        ".oRRRRGGRRRRo.",
        ".oRRRRGGRRRRo.",
        ".oRRRRGGRRRRo.",
        ".oGGGGGGGGGGo.",
        ".oGGGGGGGGGGo.",
        ".oRRRRGGRRRRo.",
        ".oRRRRGGRRRRo.",
        "..oRRRGGRRRo..",
        "...oRRGGRRo...",
        "....oRGGRo....",
        ".....oGGo.....",
        "......oo......",
    ]
    pal = {'o': hexc('#3c0710'), 'R': hexc('#c01a2e'), 'G': hexc('#f2c230')}
    img.paste(from_ascii(shield, pal), 2, 2)
    img.set(4, 4, hexc('#ff7a86'))
    img.set(5, 4, hexc('#ff7a86'))
    img.set(4, 5, hexc('#ff7a86'))
    return img


def icon_vitality():
    img = Img(18, 18)
    heart = [
        ".oooo..oooo.",
        "oHHOOooOOOOo",
        "oHOOOOOOOOOo",
        "oOOOOOOOOODo",
        "oOOOOOOOOODo",
        ".oOOOOOOODo.",
        "..oOOOOODo..",
        "...oOOODo...",
        "....oODo....",
        ".....oo.....",
    ]
    pal = {'o': hexc('#6a2300'), 'O': hexc('#ff8a12'), 'H': hexc('#ffd199'), 'D': hexc('#c85f00')}
    img.paste(from_ascii(heart, pal), 3, 4)
    plus = hexc('#ffffff')
    for i in range(-1, 2):
        img.set(9 + i, 8, plus)
        img.set(9, 8 + i, plus)
    return img


def icon_zest():
    img = Img(18, 18)
    bolt = [
        "......oooo",
        ".....oLLLo",
        "....oLLLo.",
        "...oLLLo..",
        "..oLLLLooo",
        ".oLLLLLLLo",
        "oooooLLLo.",
        "....oLLo..",
        "...oLLo...",
        "..oLLo....",
        ".oLo......",
        "oo........",
    ]
    pal = {'o': hexc('#1d460a'), 'L': hexc('#9be45a')}
    b = from_ascii(bolt, pal)
    img.paste(b, 4, 3)
    img.set(10, 4, hexc('#e6ffc6'))
    img.set(9, 5, hexc('#e6ffc6'))
    return img


# =============================================================================
# GUI (256x256 container textures)
# =============================================================================

GUI_BG = hexc('#c6c6c6')
GUI_LIGHT = hexc('#ffffff')
GUI_DARK = hexc('#555555')
GUI_BLACK = hexc('#000000')
SLOT_BG = hexc('#8b8b8b')
SLOT_DARK = hexc('#373737')


def gui_panel(w=176, h=166):
    img = Img(256, 256)
    img.rect(3, 3, w - 3, h - 3, GUI_BG)
    # outer black border with rounded corners
    img.hline(3, w - 3, 0, GUI_BLACK)
    img.hline(3, w - 3, h - 1, GUI_BLACK)
    img.vline(0, 3, h - 3, GUI_BLACK)
    img.vline(w - 1, 3, h - 3, GUI_BLACK)
    for (x, y) in [(1, 2), (2, 1), (w - 2, 2), (w - 3, 1), (1, h - 3), (2, h - 2), (w - 2, h - 3), (w - 3, h - 2)]:
        img.set(x, y, GUI_BLACK)
    # bevel
    img.rect(3, 1, w - 3, 3, GUI_LIGHT)
    img.rect(1, 3, 3, h - 3, GUI_LIGHT)
    img.set(3, 3, GUI_LIGHT)
    img.set(4, 3, GUI_LIGHT)
    img.set(3, 4, GUI_LIGHT)
    img.rect(3, h - 3, w - 3, h - 1, GUI_DARK)
    img.rect(w - 3, 3, w - 1, h - 3, GUI_DARK)
    img.set(w - 4, h - 4, GUI_DARK)
    img.set(w - 5, h - 4, GUI_DARK)
    img.set(w - 4, h - 5, GUI_DARK)
    img.set(2, 2, GUI_LIGHT)
    img.set(w - 3, h - 3, GUI_DARK)
    img.set(w - 3, 2, GUI_BG)
    img.set(2, h - 3, GUI_BG)
    return img


def gui_slot(img, x, y, big=False):
    """x,y = top-left of the 16x16 item area (like Slot coordinates)."""
    if big:
        x0, y0, s = x - 5, y - 5, 26
    else:
        x0, y0, s = x - 1, y - 1, 18
    img.rect(x0, y0, x0 + s, y0 + s, SLOT_BG)
    img.hline(x0, x0 + s - 1, y0, SLOT_DARK)
    img.vline(x0, y0, y0 + s - 1, SLOT_DARK)
    img.hline(x0 + 1, x0 + s, y0 + s - 1, GUI_LIGHT)
    img.vline(x0 + s - 1, y0 + 1, y0 + s, GUI_LIGHT)


def gui_player_inventory(img, top=84):
    for row in range(3):
        for col in range(9):
            gui_slot(img, 8 + col * 18, top + row * 18)
    for col in range(9):
        gui_slot(img, 8 + col * 18, top + 58)


ARROW = [
    "..........X...........",
    "..........XX..........",
    "..........XXX.........",
    "XXXXXXXXXXXXXX........",
    "XXXXXXXXXXXXXXX.......",
    "XXXXXXXXXXXXXXXX......",
    "XXXXXXXXXXXXXXXXX.....",
    "XXXXXXXXXXXXXXXXXX....",
    "XXXXXXXXXXXXXXXXX.....",
    "XXXXXXXXXXXXXXXX......",
    "XXXXXXXXXXXXXXX.......",
    "XXXXXXXXXXXXXX........",
    "..........XXX.........",
    "..........XX..........",
    "..........X...........",
]


def gui_arrow(img, x, y, color):
    for yy, row in enumerate(ARROW):
        for xx, ch in enumerate(row):
            if ch == 'X':
                img.set(x + xx, y + yy, color)


def gui_tank(img, x, y, w, h):
    """Recessed tank frame; interior (x..x+w, y..y+h) left dark for the fluid."""
    img.rect(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_BG)
    img.hline(x - 1, x + w, y - 1, SLOT_DARK)
    img.vline(x - 1, y - 1, y + h, SLOT_DARK)
    img.hline(x, x + w + 1, y + h, GUI_LIGHT)
    img.vline(x + w, y, y + h + 1, GUI_LIGHT)
    img.rect(x, y, x + w, y + h, hexc('#2b2b2b'))


def gui_gauge_overlay(img, x, y, w, h):
    """Tick marks drawn over the fluid (stored off-panel, blitted on top)."""
    for i in range(1, 8):
        yy = y + int(i * h / 8)
        length = 6 if i % 2 == 0 else 3
        img.hline(x, x + length, yy, hexc('#e8e8e8', 200))
    # glass shine
    img.vline(x + w - 3, y + 1, y + h - 1, hexc('#ffffff', 70))


def mixer_gui():
    img = gui_panel()
    gui_slot(img, 44, 35)          # fruit input
    gui_arrow(img, 72, 35, hexc('#8b8b8b'))
    gui_tank(img, 108, 17, 18, 52)  # concentrate tank
    gui_player_inventory(img)
    # sprites outside the panel
    gui_arrow(img, 176, 0, hexc('#ffffff'))          # progress fill (22x15) at (176,0)
    gui_gauge_overlay(img, 176, 20, 18, 52)          # gauge overlay at (176,20)
    # fruit silhouette hint in the input slot (drawn in the slot background)
    hint = fruit_orange()
    for yy in range(16):
        for xx in range(16):
            if hint.get(xx, yy)[3]:
                img.set(44 + xx, 35 + yy, hexc('#7d7d7d'))
    return img


def infuser_gui():
    img = gui_panel()
    gui_tank(img, 26, 17, 18, 52)  # concentrate tank
    gui_slot(img, 62, 20)          # sugar
    gui_slot(img, 62, 50)          # bottles
    gui_arrow(img, 88, 35, hexc('#8b8b8b'))
    gui_slot(img, 124, 35, big=True)  # output
    gui_player_inventory(img)
    gui_arrow(img, 176, 0, hexc('#ffffff'))
    gui_gauge_overlay(img, 176, 20, 18, 52)
    # slot hints: sugar pile + bottle outline
    sugar_hint = [(4, 11), (5, 10), (6, 10), (7, 9), (8, 9), (9, 10), (10, 10), (11, 11), (3, 12), (4, 12),
                  (5, 12), (6, 11), (7, 11), (8, 10), (9, 11), (10, 12), (11, 12), (12, 12), (6, 12), (7, 12),
                  (8, 11), (8, 12), (9, 12)]
    for (x, y) in sugar_hint:
        img.set(62 + x, 20 + y, hexc('#7d7d7d'))
    for y, row in enumerate(BOTTLE):
        for x, ch in enumerate(row):
            if ch == 'o':
                img.set(62 + x, 50 + y, hexc('#7d7d7d'))
    return img


# =============================================================================
# main
# =============================================================================

def main():
    preview_dir = None
    if '--preview' in sys.argv:
        preview_dir = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(preview_dir, exist_ok=True)

    written = []

    def save(img, rel, animation=None):
        img.save(out(rel))
        written.append((rel, img))
        meta = out(rel) + '.mcmeta'
        if animation:
            with open(meta, 'w', encoding='utf-8', newline='\n') as f:
                json.dump({'animation': animation}, f, indent=2)
                f.write('\n')
        elif os.path.exists(meta):
            os.remove(meta)

    for f in FRUITS:
        save(FRUIT_SPRITES[f](), f'item/{f}.png')
        save(juice_bottle(JUICE_COLORS[f]), f'item/{f}_juice.png')

    sap_cols = {
        'starfruit': ('#3f9a32', '#6b4a2a', '#ffd23a'),
        'dragonfruit': ('#34917a', '#7a5a3a', '#f0287f'),
        'pomegranate': ('#327f2a', '#4a2e1c', '#c62437'),
        'orange': ('#35882d', '#6b4a2a', '#f2861a'),
        'lime': ('#44a126', '#8a7a5a', '#8ccf45'),
    }
    for f in FRUITS:
        save(sapling(*sap_cols[f]), f'block/{f}_sapling.png')
        for stage in range(4):
            save(leaves_stage(f, stage), f'block/{f}_leaves_{stage}.png')
        save(liquid_frames(JUICE_COLORS[f], seed=FRUITS.index(f) * 17 + 3), f'block/{f}_concentrate.png',
             animation={'frametime': 3, 'interpolate': True})

    for rel, img in mixer_textures().items():
        save(img, rel + '.png', animation={'frametime': 1} if rel.endswith('_spinning') else None)
    for rel, img in infuser_textures().items():
        save(img, rel + '.png')
    for rel, img in tubing_textures().items():
        save(img, rel + '.png')

    save(icon_looting(), 'mob_effect/looting.png')
    save(icon_fortune(), 'mob_effect/fortune.png')
    save(icon_flight(), 'mob_effect/flight.png')
    save(icon_titan(), 'mob_effect/titan.png')
    save(icon_vitality(), 'mob_effect/vitality.png')
    save(icon_zest(), 'mob_effect/zest.png')

    save(mixer_gui(), 'gui/container/mixer.png')
    save(infuser_gui(), 'gui/container/infuser.png')

    # mod list banner (wide) and icon (square)
    banner = Img(80, 32)
    for i, f in enumerate(FRUITS):
        banner.paste(juice_bottle(JUICE_COLORS[f]), i * 16, 0)
        banner.paste(FRUIT_SPRITES[f](), i * 16, 16)
    banner.scaled(4).save(os.path.join(RES, 'juicer_banner.png'))
    icon = Img(16, 16)
    icon.paste(juice_bottle(JUICE_COLORS['starfruit']), 0, 0)
    icon.scaled(8).save(os.path.join(RES, 'juicer_icon.png'))
    old_logo = os.path.join(RES, 'juicer_logo.png')
    if os.path.exists(old_logo):
        os.remove(old_logo)

    print(f'wrote {len(written)} textures to {TEX}')

    if preview_dir:
        small = [img for rel, img in written if img.w == 16 and img.h == 16]
        preview_sheet(small, scale=6, cols=10).save(os.path.join(preview_dir, 'preview_16.png'))
        icons = [img for rel, img in written if img.w == 18]
        preview_sheet(icons, scale=6, cols=5).save(os.path.join(preview_dir, 'preview_icons.png'))
        for rel, img in written:
            if rel.startswith('gui/'):
                img.scaled(3).save(os.path.join(preview_dir, 'preview_' + os.path.basename(rel)))
        # leaves tiled 3x3 to check seams
        tiles = []
        for f in FRUITS:
            for stage in (0, 3):
                t = Img(48, 48)
                src = leaves_stage(f, stage)
                for ty in range(3):
                    for tx in range(3):
                        t.paste(src, tx * 16, ty * 16)
                tiles.append(t)
        preview_sheet(tiles, scale=3, cols=5).save(os.path.join(preview_dir, 'preview_leaves.png'))


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""3D models for every Arsenal gun and throwable: cuboid geometry, a hand-painted texture atlas per item, the
display transforms (first person, third person, GUI, ground, item frame) and a small software renderer for previews.

Conventions (model units, 16 = one block):
  guns:       x = across (8 is the centre line, +x is the RIGHT side: ejection port, charging handle)
              y = up, z = along the gun, muzzle towards -z (north), stock towards +z
  throwables: upright along y, centred on x = z = 8
  projectiles (launched ammo): along z, nose towards -z, centred on (8, 8, 8) - the rocket in flight is this model
Element coordinates must stay inside -16..32 on every axis (rotated corners included).

Textures are painted per face at TEXELS texels per model unit and packed into one atlas per item, so every face has
its own pixels (bevels, rivets, wood grain, serrations, lenses) instead of a stretched colour. Materials in GLOW render
full-bright (light_emission 15). Boxes with when='loaded' / when='empty' (or a predicate on the variant's state) split
the item into several models: '' and '_empty', picked with the arsenal:loaded item property (RPG warhead, railgun
coils), plus any 'states' an item lists, picked with its 'dispatch' range property (the railgun's charge meter steps
with arsenal:charge, the RPG's warhead follows the loaded rocket with arsenal:round). Every variant shares the base
model's display transforms so the item never jumps when it switches.

Display transforms are derived, not hand-typed: first person places the grip at a fixed point of the view (per kind),
third person solves the rotation that points the barrel forward from the crossbow-hold arm pose (the same maths as
ItemInHandLayer), GUI and frames auto-fit the slot. Per-item 'fp'/'tp' entries nudge the result.

gen_data.py writes the model JSON (write_models) and client/GunViewmodels.java (write_java);
gen_textures.py writes the atlases (write_texture).
  py tools/gun_models.py            -> build/models_gui.png, models_side.png, models_fp.png, models_tp.png
  py tools/gun_models.py ak47 m4a1  -> the same sheets for just those items
"""
import json
import math
import os
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixels import Img, hexc, mix  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources'))
ASSETS = os.path.join(RES, 'assets', 'arsenal')
BUILD = os.path.normpath(os.path.join(HERE, '..', 'build'))

TEXELS = 2          # texels per model unit
INSET = 0.06        # how far the inner boxes of a rounded part sit back from its end caps (no z-fighting)


def ramp(*hexes):
    return [hexc(h) for h in hexes]


# dark -> light, five tones each
MATERIALS = {
    'wood': ramp('#2e1409', '#5a2a12', '#7b3d1b', '#9c5427', '#bd6f36'),       # AK-style red laminate
    'walnut': ramp('#26150b', '#452714', '#62391e', '#7e4e2b', '#9c683f'),
    'steel': ramp('#111316', '#1e2227', '#2c3239', '#3e4650', '#5d6874'),      # blued / parkerized
    'black': ramp('#0a0b0d', '#15171a', '#1f2226', '#2c3036', '#434a53'),
    'gunmetal': ramp('#15171a', '#212429', '#2e3339', '#3e454d', '#58626d'),   # anodised aluminium
    'alu': ramp('#1c1f23', '#2a2e34', '#3a4048', '#4f5761', '#6f7985'),
    'silver': ramp('#3f444b', '#626973', '#8a929c', '#b2bac3', '#dde3ea'),
    'mag': ramp('#150f12', '#23191e', '#33252c', '#47343d', '#644a55'),        # plum-tinted steel
    'polymer': ramp('#0f1012', '#1a1c1f', '#25282c', '#32363b', '#474d54'),
    'rubber': ramp('#0b0b0c', '#131315', '#1b1c1e', '#242628', '#303336'),
    'tan': ramp('#4a3a22', '#6c5533', '#8b7146', '#a88c5d', '#c6aa7c'),        # flat dark earth
    'olive': ramp('#1d2315', '#2d3821', '#3f4d30', '#546642', '#6f8459'),
    'khaki': ramp('#2e2f1c', '#46482b', '#5f623b', '#7b7f4f', '#9a9f69'),
    'brass': ramp('#5e4210', '#97701c', '#bf9028', '#dfb44b', '#f3d27a'),
    'grey': ramp('#2d2f33', '#4a4d53', '#686c74', '#8a8f98', '#b5bac3'),
    'white': ramp('#6b6e72', '#94979c', '#b9bcc0', '#d6d8db', '#f0f1f2'),
    'red': ramp('#3a0b08', '#6a1712', '#94231a', '#bd3526', '#e05a45'),
    'warn': ramp('#4a3a05', '#8f700c', '#c89f16', '#efcb3f', '#fff09c'),
    'orange': ramp('#3d1a08', '#6e2f0e', '#a24815', '#d06a22', '#f39a4c'),
    'toxic': ramp('#10220c', '#26511b', '#3f8a29', '#6fc743', '#b8f27e'),
    'glass': ramp('#0a1219', '#132433', '#1d3d58', '#3c7199', '#a3d8f2'),
    'rail_dim': ramp('#101a1d', '#17272b', '#1f363c', '#2a4850', '#3b626c'),
    'missile': ramp('#6f7275', '#9a9d9f', '#bfc1c0', '#d9dad6', '#eeefeb'),     # off-white ordnance
    'radome': ramp('#6e6a5e', '#968f7c', '#b7ae96', '#cfc7ad', '#e3dcc4'),      # ivory missile radome
    'brown': ramp('#2a1a0e', '#4a2e18', '#6b4424', '#8c5c33', '#ab7849'),       # live rocket motor band
    # full-bright
    'rail': ramp('#0c4a55', '#138598', '#20bfd3', '#6ae8f5', '#d4fcff'),
    'void': ramp('#2a0b52', '#5b1fa8', '#8c45e6', '#c38cff', '#f2e2ff'),
    'ion': ramp('#0a2c55', '#1257a0', '#1f86d8', '#62b8ff', '#d6ecff'),
    'led': ramp('#4a0606', '#8a0d0d', '#c81d1d', '#ff4a4a', '#ffc0c0'),
    'rail_hot': ramp('#58d8e8', '#8ff0f8', '#c4fbff', '#e8feff', '#ffffff'),    # a fully charged railgun
}
GLOW = {'rail', 'void', 'ion', 'led', 'rail_hot'}
KIND_OF = {
    'wood': 'wood', 'walnut': 'wood',
    'steel': 'metal', 'black': 'metal', 'gunmetal': 'metal', 'alu': 'metal', 'silver': 'metal', 'mag': 'metal',
    'brass': 'metal', 'grey': 'metal',
    'polymer': 'polymer', 'tan': 'polymer', 'olive': 'polymer', 'khaki': 'polymer', 'white': 'polymer',
    'red': 'polymer', 'warn': 'polymer', 'orange': 'polymer', 'toxic': 'polymer', 'rail_dim': 'polymer',
    'missile': 'polymer', 'radome': 'polymer', 'brown': 'polymer',
    'rubber': 'rubber', 'glass': 'glass',
    'rail': 'glow', 'void': 'glow', 'ion': 'glow', 'led': 'glow', 'rail_hot': 'glow',
}


def noise(x, y, seed):
    return (zlib.crc32(b'%d,%d,%d' % (x, y, seed)) & 0xFFFF) / 65535.0


def frange(a, b, step):
    out, v = [], a
    while v < b - 1e-9:
        out.append(round(v, 4))
        v += step
    return out


# =====================================================================================================
# geometry
# =====================================================================================================

class Box:
    def __init__(self, name, frm, to, mat, rot=None, faces=None, decals=(), when=None):
        self.name, self.frm, self.to, self.mat = name, list(frm), list(to), mat
        # ('x' | 'y' | 'z', angle, origin) or ('euler', (x, y, z), origin)
        self.rot = rot
        self.faces = faces or ['north', 'south', 'east', 'west', 'up', 'down']
        self.decals = list(decals)
        self.when = when            # None, 'loaded', 'empty' or a predicate on the variant's state dict

    @property
    def glow(self):
        return self.mat in GLOW


def B(name, x0, y0, z0, x1, y1, z1, mat, **kw):
    return Box(name, (x0, y0, z0), (x1, y1, z1), mat, **kw)


def C(name, w, y0, y1, z0, z1, mat, xc=8.0, **kw):
    """A box centred on the gun's centre line (or on xc)."""
    return Box(name, (xc - w / 2.0, y0, z0), (xc + w / 2.0, y1, z1), mat, **kw)


def rod(name, d, yc, z0, z1, mat, xc=8.0, k=0.62, **kw):
    """A round part along z: two crossed boxes, a rounded square in section."""
    e = d * k
    return [Box(name, (xc - d / 2, yc - e / 2, z0), (xc + d / 2, yc + e / 2, z1), mat, **kw),
            Box(name + '_v', (xc - e / 2, yc - d / 2, z0 + INSET), (xc + e / 2, yc + d / 2, z1 - INSET), mat, **kw)]


def tube(name, d, yc, z0, z1, mat, xc=8.0, **kw):
    """A rounder part along z: three boxes, close to an octagon in section."""
    a, b, c = d, d * 0.52, d * 0.84
    return [Box(name, (xc - a / 2, yc - b / 2, z0), (xc + a / 2, yc + b / 2, z1), mat, **kw),
            Box(name + '_v', (xc - b / 2, yc - a / 2, z0 + INSET), (xc + b / 2, yc + a / 2, z1 - INSET), mat, **kw),
            Box(name + '_c', (xc - c / 2, yc - c / 2, z0 + 2 * INSET), (xc + c / 2, yc + c / 2, z1 - 2 * INSET),
                mat, **kw)]


def ycyl(name, d, y0, y1, mat, xc=8.0, zc=8.0, layers=3, **kw):
    """An upright round part (grenade bodies, fuses)."""
    a, b, c = d, d * 0.52, d * 0.84
    out = [Box(name, (xc - a / 2, y0, zc - b / 2), (xc + a / 2, y1, zc + b / 2), mat, **kw),
           Box(name + '_v', (xc - b / 2, y0 + INSET, zc - a / 2), (xc + b / 2, y1 - INSET, zc + a / 2), mat, **kw)]
    if layers >= 3:
        out.append(Box(name + '_c', (xc - c / 2, y0 + 2 * INSET, zc - c / 2), (xc + c / 2, y1 - 2 * INSET, zc + c / 2),
                       mat, **kw))
    return out


def xcyl(name, d, yc, zc, x0, x1, mat, **kw):
    """A round part lying across the gun (drum magazines)."""
    a, b, c = d, d * 0.52, d * 0.84
    return [Box(name, (x0, yc - a / 2, zc - b / 2), (x1, yc + a / 2, zc + b / 2), mat, **kw),
            Box(name + '_v', (x0 + INSET, yc - b / 2, zc - a / 2), (x1 - INSET, yc + b / 2, zc + a / 2), mat, **kw),
            Box(name + '_c', (x0 + 2 * INSET, yc - c / 2, zc - c / 2), (x1 - 2 * INSET, yc + c / 2, zc + c / 2),
                mat, **kw)]


def curved_mag(prefix, top_y, top_z, depth, seg, angles, w, mat, decals=(), when=None):
    """A magazine as chained segments, each tilted further forward (banana curve)."""
    out = []
    for i, angle in enumerate(angles):
        overlap = 0.5 if i else 0.25
        out.append(C('%s%d' % (prefix, i), w, top_y - seg, top_y + overlap, top_z - depth / 2, top_z + depth / 2, mat,
                     rot=('x', angle, (8, top_y, top_z)), decals=decals, when=when))
        r = math.radians(angle)
        top_y, top_z = top_y - seg * math.cos(r), top_z - seg * math.sin(r)
    return out


def flatten(items):
    out = []
    for item in items:
        if isinstance(item, (list, tuple)):
            out.extend(flatten(item))
        else:
            out.append(item)
    return out


# =====================================================================================================
# texture painting
# =====================================================================================================

UV_AXES = {
    # (u axis, u reversed, v axis, v reversed), following vanilla's default face UVs
    'north': ('x', True, 'y', True), 'south': ('x', False, 'y', True),
    'east': ('z', True, 'y', True), 'west': ('z', False, 'y', True),
    'up': ('x', False, 'z', False), 'down': ('x', False, 'z', True),
}
FACE_SETS = {
    'all': ('north', 'south', 'east', 'west', 'up', 'down'),
    'sides': ('east', 'west'),
    'around': ('east', 'west', 'up', 'down'),          # round a part that runs along z
    'around_y': ('north', 'south', 'east', 'west'),    # round an upright part
    'top': ('up',), 'bottom': ('down',), 'front': ('north',), 'back': ('south',),
    'left': ('west',), 'right': ('east',), 'ends': ('north', 'south'),
}


def face_size(box, face):
    (x0, y0, z0), (x1, y1, z1) = box.frm, box.to
    w, h = {
        'north': (x1 - x0, y1 - y0), 'south': (x1 - x0, y1 - y0),
        'east': (z1 - z0, y1 - y0), 'west': (z1 - z0, y1 - y0),
        'up': (x1 - x0, z1 - z0), 'down': (x1 - x0, z1 - z0),
    }[face]
    return max(1, int(round(w * TEXELS))), max(1, int(round(h * TEXELS)))


def color_of(spec, pal):
    if isinstance(spec, int):
        return pal[max(0, min(len(pal) - 1, spec))]
    if isinstance(spec, tuple) and len(spec) == 2 and isinstance(spec[0], str):
        return MATERIALS[spec[0]][spec[1]]
    if spec == 'hole':
        return (5, 6, 7, 255)
    if isinstance(spec, str):
        return hexc(spec)
    return spec


class Face:
    """A face being painted: converts model coordinates to its own texels and back."""

    def __init__(self, box, face):
        self.box, self.face = box, face
        self.w, self.h = face_size(box, face)
        self.img = Img(self.w, self.h)
        self.pal = MATERIALS[box.mat]
        self.seed = zlib.crc32((box.name + face).encode())
        ua, ur, va, vr = UV_AXES[face]
        self.ua, self.va = ua, va
        self.rev = {ua: ur, va: vr}

    def tex(self, axis, value):
        i = 'xyz'.index(axis)
        lo, hi = self.box.frm[i], self.box.to[i]
        return ((hi - value) if self.rev[axis] else (value - lo)) * TEXELS

    def model(self, axis, t):
        i = 'xyz'.index(axis)
        lo, hi = self.box.frm[i], self.box.to[i]
        d = (t + 0.5) / TEXELS
        return hi - d if self.rev[axis] else lo + d

    def span(self, axis, rng, size):
        if rng is None:
            return 0, size
        a, b = sorted((self.tex(axis, rng[0]), self.tex(axis, rng[1])))
        lo, hi = int(round(a)), int(round(b))
        if hi <= lo:
            hi = lo + 1
        return max(0, lo), min(size, hi)

    def rect(self, ranges):
        u0, u1 = self.span(self.ua, ranges.get(self.ua), self.w)
        v0, v1 = self.span(self.va, ranges.get(self.va), self.h)
        return u0, u1, v0, v1

    def put(self, u, v, c):
        if 0 <= u < self.w and 0 <= v < self.h:
            self.img.set(u, v, c)

    def get(self, u, v):
        return self.img.get(u, v)

    def tone(self, x, y, t, jitter=0.0):
        t = max(0, min(len(self.pal) - 1, t))
        c = self.pal[t]
        if jitter:
            c = mix(c, self.pal[min(len(self.pal) - 1, t + 1)], jitter)
        self.img.set(x, y, c)


def long_axis(box):
    dims = [box.to[i] - box.frm[i] for i in range(3)]
    return 'xyz'[dims.index(max(dims))]


def paint_base(f):
    kind = KIND_OF[f.box.mat]
    axis = long_axis(f.box)
    if kind == 'glow':
        for y in range(f.h):
            for x in range(f.w):
                edge = x in (0, f.w - 1) or y in (0, f.h - 1)
                t = 2 if edge and f.w > 2 and f.h > 2 else 3
                f.tone(x, y, t, jitter=0.5 if noise(x, y, f.seed) > 0.8 else 0.0)
        return
    if kind == 'glass':
        for y in range(f.h):
            for x in range(f.w):
                t = (x + (f.h - 1 - y)) / float(max(1, f.w + f.h - 2))
                c = mix(f.pal[1], f.pal[3], t)
                if abs(x - y - f.w * 0.2) < 1.0:
                    c = mix(c, f.pal[4], 0.6)
                f.img.set(x, y, c)
        return
    for y in range(f.h):
        for x in range(f.w):
            n = noise(x, y, f.seed)
            if kind == 'wood':
                # grain: bands along the part's length, wobbling slightly, with darker streaks; rings on end grain
                if axis == f.ua:
                    row, pos = y, x
                elif axis == f.va:
                    row, pos = x, y
                else:
                    cx, cy = f.w / 2.0, f.h * 0.8
                    row, pos = int(math.hypot(x - cx, (y - cy) * 1.3) * 1.2), 0
                band = noise(row, pos // 5, f.seed + 7)
                t = 2 if band < 0.55 else 3
                if noise(row, 0, f.seed + 3) < 0.18:
                    t = 1
                if n < 0.06:
                    t = max(1, t - 1)
                f.tone(x, y, t, jitter=0.25 if n > 0.8 else 0.0)
            elif kind == 'metal':
                f.tone(x, y, 2, jitter=0.35 if n > 0.72 else 0.0)
                if n < 0.1:
                    f.tone(x, y, 1, jitter=0.5)
            elif kind == 'polymer':
                f.tone(x, y, 2, jitter=0.3 if n > 0.8 else 0.0)
                if n < 0.14:
                    f.tone(x, y, 1, jitter=0.6)
            else:  # rubber
                f.tone(x, y, 1 if n < 0.5 else 2, jitter=0.2 if n > 0.9 else 0.0)
    # bevels: the upper edge catches the light, the lower edge is in shadow
    if f.w < 2 or f.h < 2:
        return
    if f.face not in ('up', 'down'):
        for x in range(f.w):
            f.img.set(x, 0, mix(f.img.get(x, 0), f.pal[4], 0.55))
            if f.h > 2:
                f.img.set(x, f.h - 1, mix(f.img.get(x, f.h - 1), f.pal[0], 0.6))
        for y in range(1, f.h - 1):
            for x in (0, f.w - 1):
                f.img.set(x, y, mix(f.img.get(x, y), f.pal[1], 0.35))
    else:
        for x in range(f.w):
            for y in range(f.h):
                if x in (0, f.w - 1) or y in (0, f.h - 1):
                    target = 4 if f.face == 'up' else 0
                    f.img.set(x, y, mix(f.img.get(x, y), f.pal[target], 0.35))


# ---- decals: each returns a function that paints one Face (ranges are model coordinates) --------------------------

def _faces(where):
    return FACE_SETS.get(where, (where,))


def mark(where, color, **ranges):
    """Fill a rectangle given in model coordinates, e.g. mark('right', 'hole', z=(13, 18), y=(9, 10))."""
    faces = _faces(where)

    def paint(f):
        if f.face not in faces:
            return
        c = color_of(color, f.pal)
        u0, u1, v0, v1 = f.rect(ranges)
        for v in range(v0, v1):
            for u in range(u0, u1):
                f.put(u, v, c)
    return paint


def lines(where, axis, at, color=0, hilite=None, **ranges):
    """One-texel lines across the face at positions along `axis` (serrations, rail slots, ribs)."""
    faces = _faces(where)

    def paint(f):
        if f.face not in faces or axis not in (f.ua, f.va):
            return
        c = color_of(color, f.pal)
        hc = color_of(hilite, f.pal) if hilite is not None else None
        u0, u1, v0, v1 = f.rect(ranges)
        for p in at:
            t = int(math.floor(f.tex(axis, p)))
            if axis == f.ua:
                for v in range(v0, v1):
                    f.put(t, v, c)
                    if hc:
                        f.put(t + 1, v, hc)
            else:
                for u in range(u0, u1):
                    f.put(u, t, c)
                    if hc:
                        f.put(u, t + 1, hc)
    return paint


def dots(where, points, color, size=1):
    """Single texels (or size x size squares) at model points, e.g. rivets, sight dots."""
    faces = _faces(where)

    def paint(f):
        if f.face not in faces:
            return
        c = color_of(color, f.pal)
        for p in points:
            if f.ua not in p or f.va not in p:
                continue
            u = int(math.floor(f.tex(f.ua, p[f.ua])))
            v = int(math.floor(f.tex(f.va, p[f.va])))
            for du in range(size):
                for dv in range(size):
                    f.put(u + du, v + dv, c)
    return paint


def rivets(where, points):
    """A lit texel with a shadow under it."""
    faces = _faces(where)

    def paint(f):
        if f.face not in faces:
            return
        for p in points:
            if f.ua not in p or f.va not in p:
                continue
            u = int(math.floor(f.tex(f.ua, p[f.ua])))
            v = int(math.floor(f.tex(f.va, p[f.va])))
            f.put(u, v, f.pal[4])
            f.put(u, v + 1, f.pal[0])
    return paint


def grid(where, axis_a, a_values, axis_b, b_values, color='hole', size=1):
    return dots(where, [{axis_a: a, axis_b: b} for a in a_values for b in b_values], color, size)


def checker(where, color=1, strength=0.55, **ranges):
    faces = _faces(where)

    def paint(f):
        if f.face not in faces:
            return
        c = color_of(color, f.pal)
        u0, u1, v0, v1 = f.rect(ranges)
        for v in range(max(v0, 1), min(v1, f.h - 1)):
            for u in range(max(u0, 1), min(u1, f.w - 1)):
                if (u + v) % 2 == 0:
                    f.put(u, v, mix(f.get(u, v), c, strength))
    return paint


def stipple(where, color=0, density=0.3, **ranges):
    faces = _faces(where)

    def paint(f):
        if f.face not in faces:
            return
        c = color_of(color, f.pal)
        u0, u1, v0, v1 = f.rect(ranges)
        for v in range(max(v0, 1), min(v1, f.h - 1)):
            for u in range(max(u0, 1), min(u1, f.w - 1)):
                if noise(u, v, f.seed + 99) < density:
                    f.put(u, v, mix(f.get(u, v), c, 0.5))
    return paint


def glass(where):
    """A lens: blue-black glass with a highlight, inside a thin dark rim."""
    faces = _faces(where)

    def paint(f):
        if f.face not in faces:
            return
        g = MATERIALS['glass']
        for v in range(f.h):
            for u in range(f.w):
                t = (u + (f.h - 1 - v)) / float(max(1, f.w + f.h - 2))
                c = mix(g[1], g[3], t)
                if abs(u - v - f.w * 0.25) < 0.8:
                    c = mix(c, g[4], 0.7)
                if u in (0, f.w - 1) or v in (0, f.h - 1):
                    c = f.pal[0]
                f.put(u, v, c)
    return paint


def circle(where, center, r, color='hole', width=None):
    """A filled disc (bore, chamber mouths) or, with width, a ring. center/r in model units."""
    faces = _faces(where)

    def paint(f):
        if f.face not in faces:
            return
        if f.ua not in center or f.va not in center:
            return
        c = color_of(color, f.pal)
        for v in range(f.h):
            for u in range(f.w):
                du = f.model(f.ua, u) - center[f.ua]
                dv = f.model(f.va, v) - center[f.va]
                d = math.hypot(du, dv)
                if (width is None and d <= r) or (width is not None and abs(d - r) <= width / 2.0):
                    f.put(u, v, c)
    return paint


def mag_rib():
    """A pressed reinforcing rib down the middle of a magazine's sides."""
    def paint(f):
        if f.face not in ('east', 'west') or f.w < 4:
            return
        mid = f.w // 2
        for v in range(f.h):
            f.put(mid - 1, v, f.pal[4])
            f.put(mid, v, f.pal[3])
            f.put(mid + 1, v, f.pal[1])
    return paint


def paint(box, face):
    f = Face(box, face)
    paint_base(f)
    for decal in box.decals:
        decal(f)
    return f.img


# =====================================================================================================
# the guns
# =====================================================================================================

def trigger_group(z_front, z_back, y_low, y_top, mat='steel', trigger_z=None):
    """Guard (front post + bottom bar) and the trigger blade."""
    tz = trigger_z if trigger_z is not None else z_front + (z_back - z_front) * 0.4
    return [C('guard_front', 0.8, y_low, y_top, z_front, z_front + 0.5, mat),
            C('guard_bottom', 0.8, y_low, y_low + 0.5, z_front + 0.5, z_back, mat),
            C('trigger', 0.4, y_low + 0.5, y_top, tz, tz + 0.4, 'steel')]


def rail(name, z0, z1, y0, w=1.3, h=0.6, xc=8.0):
    return C(name, w, y0, y0 + h, z0, z1, 'black', xc=xc,
             decals=[lines('top', 'z', frange(z0 + 0.4, z1 - 0.2, 0.8), 0, hilite=4),
                     lines('sides', 'z', frange(z0 + 0.4, z1 - 0.2, 0.8), 0)])


def m_ak47():
    b = []
    b.append(C('brake', 1.5, 8.25, 9.75, -15, -12, 'black',
               decals=[circle('front', {'x': 8, 'y': 9}, 0.55),
                       lines('sides', 'z', [-14.5, -13.5, -12.5], 'hole', y=(9.25, 9.75))]))
    b.append(C('barrel', 1.0, 8.5, 9.5, -12, 2, 'black'))
    b.append(C('front_sight', 1.5, 8.25, 10.5, -10, -8.5, 'steel'))
    b.append(B('sight_ear_l', 7.25, 10.5, -9.75, 7.5, 12, -8.75, 'steel'))
    b.append(B('sight_ear_r', 8.5, 10.5, -9.75, 8.75, 12, -8.75, 'steel'))
    b.append(C('sight_post', 0.25, 10.5, 11.5, -9.5, -9, 'steel'))
    b.append(C('gas_block', 1.5, 8.25, 11, -6.5, -5, 'steel'))
    b.append(C('gas_tube', 1.2, 9.75, 10.95, -5, 3, 'black'))
    b.append(C('ferrule', 2.5, 7.25, 9.75, 1.5, 2, 'steel'))
    b.append(C('lower_guard', 2.3, 7.35, 9.65, 2, 9.5, 'wood',
               decals=[mark('sides', 1, z=(3, 8.5), y=(8.5, 8.75)), mark('sides', 4, z=(3, 8.5), y=(8.25, 8.5))]))
    b.append(C('upper_guard', 1.8, 9.65, 11.15, 3, 9.5, 'wood'))
    b.append(C('receiver', 2.5, 6.75, 10.25, 9.5, 21, 'steel', decals=[
        rivets('sides', [{'z': z, 'y': y} for z, y in ((10.5, 7.5), (16.0, 7.5), (18.5, 7.5), (20.25, 8.75),
                                                        (11.25, 9.5), (16.5, 9.0))]),
        mark('sides', 0, z=(11.0, 15.0), y=(7.0, 7.5)),
        mark('right', 'hole', z=(13.5, 18.5), y=(9.25, 10.25)),
        mark('right', ('steel', 3), z=(14.0, 18.0), y=(9.5, 10.0)),
        dots('right', [{'z': z, 'y': 8.5} for z in (15.5, 17.0, 18.5)], 0)]))
    b.append(C('rear_sight', 1.8, 10.25, 11.25, 9.5, 12, 'steel'))
    b.append(C('sight_leaf', 1.3, 11.25, 11.75, 10.5, 12, 'steel'))
    b.append(C('dust_cover', 2.0, 10.25, 11.0, 12, 21, 'steel', decals=[
        lines('top', 'z', [18.5, 19.25, 20.0], 0, hilite=4), lines('sides', 'z', [18.5, 19.25, 20.0], 1)]))
    b.append(B('charging_handle', 9.25, 9.4, 12.5, 10.5, 10.1, 13.5, 'steel'))
    b.append(B('selector', 9.25, 8.75, 14.5, 9.5, 9.5, 19.5, 'steel'))
    b.append(C('mag_release', 1.0, 6.0, 6.75, 14.75, 15.25, 'steel'))
    b.append(C('guard_front', 0.8, 5.25, 6.75, 15.25, 15.75, 'steel'))
    b.append(C('guard_bottom', 0.8, 5.25, 5.75, 15.75, 19.25, 'steel'))
    b.append(C('trigger', 0.4, 5.75, 6.75, 16.75, 17.25, 'steel'))
    b += curved_mag('mag', 7.0, 13.0, 3.5, 1.95, (3, 13, 23, 33), 2.0, 'mag', decals=[mag_rib()])
    b[-1].decals.append(mark('bottom', 3))
    b.append(C('grip', 1.8, 1.75, 7.0, 19.25, 21.25, 'wood', rot=('x', -20, (8, 6.75, 20.25)),
               decals=[checker('sides', 1, y=(2.5, 6.0))]))
    pivot = (8, 9.6, 21.25)
    b.append(C('tang', 1.5, 7.25, 9.75, 20.75, 21.75, 'steel'))
    b.append(C('stock', 1.8, 5.4, 9.6, 21.25, 30.25, 'wood', rot=('x', 9, pivot),
               decals=[mark('sides', 0, z=(27.0, 28.0), y=(7.25, 7.75))]))
    b.append(C('stock_belly', 1.8, 4.0, 5.9, 24.75, 30.25, 'wood', rot=('x', 9, pivot)))
    b.append(C('buttplate', 2.0, 3.75, 9.75, 30.25, 30.75, 'steel', rot=('x', 9, pivot)))
    return b


def m_m4a1():
    b = []
    b += rod('flash', 1.4, 9.0, -13, -11, 'black',
             decals=[lines('around', 'z', [-12.6, -12.0], 'hole'), circle('front', {'x': 8, 'y': 9}, 0.45)])
    b.append(C('barrel', 1.0, 8.5, 9.5, -11, -1, 'black'))
    b.append(C('fsb', 1.5, 8.2, 10.0, -5.5, -4.3, 'black'))
    b.append(C('fsb_tower', 0.9, 10.0, 12.2, -5.3, -4.5, 'black'))
    b.append(B('fsb_ear_l', 7.3, 11.4, -5.2, 7.6, 12.6, -4.6, 'black'))
    b.append(B('fsb_ear_r', 8.4, 11.4, -5.2, 8.7, 12.6, -4.6, 'black'))
    b += rod('handguard', 2.8, 9.0, -1, 9, 'polymer', k=0.74, decals=[
        grid('sides', 'z', frange(0.2, 8.4, 1.0), 'y', [9.4], 'hole'),
        lines('sides', 'z', [-0.4, 8.6], 0)])
    b += rod('delta_ring', 3.1, 9.0, 9, 9.6, 'black', k=0.74)
    b.append(C('upper', 2.2, 9.0, 11.0, 9.6, 21, 'gunmetal', decals=[
        mark('right', 'hole', z=(13.2, 17.0), y=(9.3, 10.4)),
        mark('right', ('gunmetal', 4), z=(13.2, 17.0), y=(10.4, 10.6)),
        rivets('sides', [{'z': 10.4, 'y': 10.2}, {'z': 19.6, 'y': 10.2}])]))
    b.append(C('handle_front', 1.0, 11.0, 13.0, 11.2, 12.4, 'gunmetal'))
    b.append(C('handle_top', 1.0, 12.4, 13.1, 11.2, 19.6, 'gunmetal'))
    b.append(C('handle_rear', 1.2, 11.0, 13.9, 17.8, 19.6, 'gunmetal', decals=[mark('back', 'hole', x=(7.8, 8.2),
                                                                                        y=(13.0, 13.5))]))
    b.append(C('lower', 2.0, 7.3, 9.0, 11.0, 20.6, 'gunmetal', decals=[
        rivets('sides', [{'z': 16.2, 'y': 8.2}, {'z': 18.2, 'y': 8.2}])]))
    b.append(C('magwell', 2.1, 6.4, 7.3, 11.4, 14.6, 'gunmetal'))
    b += curved_mag('mag', 6.6, 13.0, 3.0, 1.9, (2, 7, 12), 1.6, 'alu', decals=[
        lines('sides', 'z', [12.2], 1), lines('sides', 'z', [13.8], 3)])
    b[-1].decals.append(mark('bottom', 3))
    b += trigger_group(15.0, 18.6, 5.7, 7.3, mat='gunmetal', trigger_z=16.7)
    b.append(C('grip', 1.7, 2.2, 7.8, 18.2, 20.2, 'polymer', rot=('x', -22, (8, 7.3, 19.2)),
               decals=[stipple('sides', 0, 0.35, y=(2.6, 6.6))]))
    b += rod('buffer', 1.5, 9.5, 20.6, 27.5, 'black')
    b.append(C('stock', 1.8, 7.6, 10.6, 23.5, 28.6, 'polymer', decals=[lines('sides', 'z', [25.0, 26.2], 0)]))
    b.append(C('stock_lower', 1.8, 5.8, 7.6, 25.8, 28.6, 'polymer'))
    b.append(C('butt', 1.9, 5.6, 10.8, 28.6, 29.2, 'rubber'))
    b.append(C('charging_handle', 1.6, 10.5, 11.0, 20.4, 21.2, 'gunmetal'))
    b.append(B('forward_assist', 9.1, 9.7, 18.2, 9.7, 10.4, 19.8, 'gunmetal'))
    b.append(B('mag_release', 9.0, 7.6, 14.8, 9.3, 8.2, 15.4, 'steel'))
    b.append(B('selector', 6.7, 8.2, 17.5, 7.0, 8.8, 18.6, 'steel'))
    return b


def m_scar_h():
    b = []
    b += rod('flash', 1.4, 9.2, -15, -12.6, 'black',
             decals=[lines('around', 'z', [-14.4, -13.6], 'hole'), circle('front', {'x': 8, 'y': 9.2}, 0.45)])
    b.append(C('barrel', 1.1, 8.65, 9.75, -12.6, -3, 'black'))
    b.append(C('gas_block', 1.6, 8.4, 10.6, -3.6, -2.4, 'black'))
    b.append(C('upper', 2.4, 8.4, 11.3, -2.4, 20.0, 'tan', decals=[
        mark('right', 'hole', z=(12.0, 16.0), y=(9.4, 10.6)),
        mark('left', 'hole', z=(1.0, 11.0), y=(10.2, 10.5)),
        lines('sides', 'z', [5.6], 1)]))
    b.append(rail('top_rail', -2.2, 19.8, 11.3))
    b.append(B('rail_l', 6.45, 9.2, -2.0, 6.8, 10.4, 5.5, 'black', decals=[lines('left', 'z', frange(-1.6, 5.4, 0.8), 0)]))
    b.append(B('rail_r', 9.2, 9.2, -2.0, 9.55, 10.4, 5.5, 'black', decals=[lines('right', 'z', frange(-1.6, 5.4, 0.8), 0)]))
    b.append(C('rail_b', 1.3, 7.8, 8.4, -2.0, 5.5, 'black', decals=[lines('bottom', 'z', frange(-1.6, 5.4, 0.8), 0)]))
    b.append(B('charging_handle', 5.7, 10.2, 3.2, 6.8, 10.8, 4.4, 'black'))
    b.append(C('front_sight', 0.9, 11.9, 12.5, -1.6, -0.4, 'black'))
    b.append(C('rear_sight', 1.1, 11.9, 12.6, 17.8, 19.2, 'black'))
    b.append(C('lower', 2.1, 6.6, 8.4, 8.0, 20.6, 'tan', decals=[
        rivets('sides', [{'z': 14.4, 'y': 7.6}, {'z': 16.8, 'y': 7.6}])]))
    b.append(C('magwell', 2.2, 5.8, 6.6, 9.2, 12.9, 'tan'))
    b += curved_mag('mag', 6.2, 11.0, 3.4, 2.2, (1, 4, 8), 1.9, 'black', decals=[mag_rib()])
    b[-1].decals.append(mark('bottom', 3))
    b += trigger_group(13.6, 17.4, 4.8, 6.6, mat='tan', trigger_z=15.2)
    b.append(C('grip', 1.8, 1.4, 7.4, 17.0, 19.0, 'polymer', rot=('x', -20, (8, 6.8, 18.0)),
               decals=[stipple('sides', 0, 0.35, y=(2.0, 6.2))]))
    b.append(C('hinge', 2.0, 7.8, 11.0, 20.0, 21.4, 'black'))
    b.append(C('stock_top', 1.8, 9.2, 11.2, 21.4, 30.6, 'tan'))
    b.append(C('cheek', 1.8, 11.2, 11.8, 23.5, 28.5, 'polymer'))
    b.append(C('stock_low', 1.8, 5.2, 8.6, 25.6, 30.6, 'tan'))
    b.append(C('stock_web', 1.6, 7.6, 9.4, 21.2, 26.2, 'tan', rot=('x', 12, (8, 9.4, 21.2))))
    b.append(C('butt', 2.0, 5.0, 11.4, 30.6, 31.4, 'rubber'))
    return b


def m_aug():
    b = []
    b += rod('flash', 1.4, 9.1, -12, -10, 'black',
             decals=[lines('around', 'z', [-11.6, -11.0], 'hole'), circle('front', {'x': 8, 'y': 9.1}, 0.45)])
    b.append(C('barrel', 1.0, 8.6, 9.6, -10, 2.5, 'black'))
    b.append(C('collar', 1.8, 8.2, 10.2, -4.2, -2.4, 'black'))
    b.append(C('foregrip', 1.4, 4.2, 8.4, -3.9, -2.7, 'olive', rot=('x', 10, (8, 8.2, -3.3)),
               decals=[lines('front', 'y', frange(4.8, 7.8, 0.8), 1)]))
    b.append(C('shell_front', 2.2, 7.8, 10.4, 1.0, 4.0, 'olive'))
    b.append(C('shell', 2.6, 7.2, 10.8, 4.0, 28.6, 'olive', decals=[
        mark('right', 'hole', z=(21.0, 24.0), y=(9.0, 10.2)),
        lines('sides', 'z', [11.0, 18.6], 1),
        dots('sides', [{'z': 16.0, 'y': 8.2}], ('steel', 3), 2)]))
    b.append(C('shell_low', 2.6, 6.2, 7.2, 16.0, 28.6, 'olive'))
    b.append(C('butt', 2.7, 6.0, 11.0, 28.6, 29.4, 'rubber'))
    b.append(C('cheek', 2.4, 10.8, 11.1, 20.5, 27.5, 'olive'))
    b.append(C('scope_mount', 1.4, 10.8, 11.8, 9.5, 17.5, 'olive'))
    b += rod('scope', 2.0, 12.6, 8.5, 19.0, 'olive', k=0.7)
    b += rod('scope_bell', 2.6, 12.6, 7.2, 8.5, 'olive', k=0.7, decals=[glass('front')])
    b += rod('scope_eye', 2.4, 12.6, 19.0, 20.4, 'olive', k=0.7, decals=[glass('back')])
    b.append(C('grip', 1.6, 2.6, 7.8, 6.8, 8.8, 'olive', rot=('x', -12, (8, 7.3, 7.8)),
               decals=[stipple('sides', 0, 0.3, y=(3.2, 7.0))]))
    b.append(C('guard_front', 0.8, 2.4, 7.8, 2.2, 2.9, 'olive'))
    b.append(C('guard_bottom', 0.8, 1.9, 2.5, 2.2, 9.4, 'olive'))
    b.append(C('trigger', 0.4, 4.8, 7.2, 5.6, 6.0, 'polymer'))
    b += curved_mag('mag', 7.2, 12.2, 2.8, 1.8, (4, 10, 16), 1.6, 'polymer', decals=[
        mark('sides', ('brass', 3), z=(11.4, 13.0), y=(6.0, 7.2))])
    b.append(B('charging_handle', 5.9, 9.8, 5.2, 6.6, 10.4, 6.4, 'black'))
    return b


def slide_serrations(z0, z1, y0, y1):
    return lines('sides', 'z', frange(z0, z1, 0.5), 0, y=(y0, y1))


def m_glock17():
    b = []
    b.append(C('slide', 3.4, 13.8, 17.6, -13.3, 12.6, 'black', decals=[
        slide_serrations(7.5, 12.0, 14.2, 17.2),
        mark('right', 'hole', z=(-3.0, 2.5), y=(15.8, 17.6)),
        mark('right', ('gunmetal', 3), z=(-2.5, 2.0), y=(16.2, 17.4)),
        circle('front', {'x': 8, 'y': 15.4}, 0.65)]))
    b.append(C('front_sight', 0.8, 17.6, 18.4, -12.2, -11.2, 'black', decals=[dots('back', [{'x': 8, 'y': 18.0}], '#e8e8e8')]))
    b.append(C('rear_sight', 2.6, 17.6, 18.5, 10.6, 12.0, 'black', decals=[
        dots('back', [{'x': 7.2, 'y': 18.0}, {'x': 8.6, 'y': 18.0}], '#e8e8e8'),
        mark('top', 'hole', x=(7.6, 8.4))]))
    b.append(C('dust_cover', 3.0, 11.6, 13.8, -12.2, -3.0, 'polymer', decals=[
        lines('bottom', 'z', frange(-11.6, -3.4, 1.0), 0)]))
    b.append(C('frame', 3.0, 11.2, 13.8, -3.0, 12.8, 'polymer'))
    b.append(C('tang', 2.6, 12.4, 13.8, 12.8, 13.8, 'polymer'))
    b += trigger_group(-3.6, 3.6, 8.2, 11.4, mat='polymer', trigger_z=0.9)
    grip_rot = ('x', -21, (8, 11.6, 7.7))
    b.append(C('grip', 3.0, -0.8, 13.0, 4.0, 11.4, 'polymer', rot=grip_rot, decals=[
        stipple('sides', 0, 0.45, y=(0.4, 10.4)), lines('front', 'y', [2.2, 4.8, 7.4], 0)]))
    b.append(C('mag_base', 3.2, -1.6, -0.6, 4.0, 11.6, 'polymer', rot=grip_rot))
    b.append(B('slide_stop', 6.2, 13.0, -1.0, 6.5, 13.6, 2.4, 'black'))
    b.append(B('takedown', 6.3, 12.6, -2.6, 6.5, 13.2, -1.6, 'black'))
    return b


def m_m1911():
    b = []
    b.append(C('slide', 2.9, 14.0, 17.4, -14.0, 13.6, 'steel', decals=[
        slide_serrations(8.5, 13.0, 14.4, 17.0),
        mark('right', 'hole', z=(-1.0, 4.0), y=(15.8, 17.4)),
        mark('right', ('silver', 2), z=(-0.6, 3.6), y=(16.2, 17.3)),
        circle('front', {'x': 8, 'y': 15.5}, 1.0, ('steel', 4)),
        circle('front', {'x': 8, 'y': 15.5}, 0.55)]))
    b.append(C('front_sight', 0.4, 17.4, 18.2, -12.8, -12.0, 'steel'))
    b.append(C('rear_sight', 2.0, 17.4, 18.3, 12.0, 13.4, 'black', decals=[mark('top', 'hole', x=(7.7, 8.3))]))
    b.append(C('dust_cover', 2.6, 12.3, 14.0, -13.4, -4.0, 'steel'))
    b.append(C('plug', 1.6, 12.4, 13.8, -14.2, -13.4, 'steel', decals=[circle('front', {'x': 8, 'y': 13.1}, 0.35)]))
    b.append(C('frame', 2.6, 11.6, 14.0, -4.0, 13.8, 'steel'))
    b.append(C('hammer', 1.0, 14.2, 16.6, 13.6, 14.6, 'steel', rot=('x', 20, (8, 14.2, 14.1)),
               decals=[checker('back', 0)]))
    b.append(C('beavertail', 2.2, 12.4, 13.9, 13.8, 15.8, 'steel'))
    b += trigger_group(-4.4, 2.8, 8.4, 11.8, mat='steel', trigger_z=0.2)
    b.append(C('guard_corner', 0.8, 8.7, 9.6, -4.2, -3.4, 'steel'))
    grip_rot = ('x', -17, (8, 11.8, 8.4))
    b.append(C('grip', 2.3, 0.2, 13.0, 5.0, 11.8, 'steel', rot=grip_rot,
               decals=[lines('back', 'y', frange(1.0, 11.0, 0.5), 0)]))
    for side, x0, x1 in (('l', 6.55, 6.85), ('r', 9.15, 9.45)):
        b.append(B('panel_' + side, x0, 1.2, 5.6, x1, 11.2, 11.2, 'walnut', rot=grip_rot, decals=[
            checker('sides', 0, 0.6, y=(2.0, 10.4)),
            dots('sides', [{'z': 7.0, 'y': 2.4}, {'z': 9.8, 'y': 9.8}], ('silver', 3))]))
    b.append(C('mag_base', 2.4, -0.6, 0.2, 5.2, 11.4, 'black', rot=grip_rot))
    b.append(B('slide_stop', 6.1, 12.8, -1.5, 6.55, 13.5, 2.0, 'steel'))
    b.append(B('thumb_safety', 6.1, 13.0, 10.2, 6.55, 13.7, 12.8, 'steel'))
    return b


def m_m9():
    b = []
    b.append(C('slide', 2.9, 13.6, 17.0, 0.0, 13.2, 'black', decals=[
        slide_serrations(8.5, 12.5, 14.0, 16.6),
        mark('right', 'hole', z=(0.4, 3.6), y=(15.4, 17.0))]))
    b.append(C('slide_nose', 2.9, 13.6, 16.4, -14.0, -11.6, 'black',
               decals=[circle('front', {'x': 8, 'y': 15.05}, 0.6)]))
    b.append(B('slide_l', 6.55, 13.6, -11.6, 7.2, 16.4, 0.0, 'black'))
    b.append(B('slide_r', 8.8, 13.6, -11.6, 9.45, 16.4, 0.0, 'black'))
    b.append(C('barrel', 1.5, 14.3, 15.8, -13.8, 0.2, 'gunmetal'))
    b.append(C('front_sight', 0.5, 16.4, 17.2, -13.4, -12.6, 'black', decals=[dots('back', [{'x': 8, 'y': 16.8}], '#e8e8e8')]))
    b.append(C('rear_sight', 2.1, 17.0, 17.8, 12.0, 13.2, 'black', decals=[mark('top', 'hole', x=(7.7, 8.3))]))
    b.append(B('decocker_l', 6.15, 15.0, 10.8, 6.55, 15.8, 12.6, 'black'))
    b.append(B('decocker_r', 9.45, 15.0, 10.8, 9.85, 15.8, 12.6, 'black'))
    b.append(C('dust_cover', 2.6, 12.0, 13.6, -13.2, -4.0, 'black'))
    b.append(C('frame', 2.6, 11.4, 13.6, -4.0, 13.4, 'black'))
    b.append(C('hammer', 1.0, 13.6, 15.8, 13.2, 14.2, 'black', rot=('x', 12, (8, 13.6, 13.7))))
    b += trigger_group(-5.2, 2.6, 8.2, 11.6, mat='black', trigger_z=0.0)
    b.append(C('guard_corner', 0.8, 8.4, 9.4, -5.0, -4.0, 'black'))
    grip_rot = ('x', -14, (8, 11.4, 8.6))
    b.append(C('grip', 2.4, 0.0, 12.8, 5.2, 12.0, 'black', rot=grip_rot))
    for side, x0, x1 in (('l', 6.5, 6.8), ('r', 9.2, 9.5)):
        b.append(B('panel_' + side, x0, 1.0, 5.8, x1, 11.2, 11.4, 'polymer', rot=grip_rot,
                   decals=[checker('sides', 0, 0.6, y=(1.8, 10.4))]))
    b.append(C('mag_base', 2.5, -0.8, 0.0, 5.4, 11.8, 'polymer', rot=grip_rot))
    return b


def m_deagle():
    b = []
    b.append(C('barrel', 3.4, 14.4, 16.6, -16.0, 2.0, 'silver', decals=[
        circle('front', {'x': 8, 'y': 15.5}, 0.8),
        lines('sides', 'z', [-15.2], 1)]))
    b.append(C('barrel_mid', 2.6, 16.6, 17.8, -16.0, 2.0, 'silver'))
    b.append(C('rib', 1.2, 17.8, 18.3, -15.8, 2.0, 'silver', decals=[lines('top', 'z', frange(-15.0, 1.6, 1.2), 2)]))
    b.append(C('slide', 3.4, 14.0, 18.0, 2.0, 16.2, 'silver', decals=[
        slide_serrations(11.0, 15.5, 14.6, 17.4),
        mark('right', 'hole', z=(3.0, 8.0), y=(16.0, 18.0))]))
    b.append(C('lug', 2.8, 12.6, 14.4, -10.0, 2.0, 'silver'))
    b.append(C('frame', 3.0, 11.6, 14.0, -4.0, 16.4, 'black'))
    b.append(C('front_sight', 0.5, 18.3, 19.1, -15.2, -14.2, 'silver'))
    b.append(C('rear_sight', 2.4, 18.0, 18.9, 14.6, 16.0, 'black', decals=[mark('top', 'hole', x=(7.7, 8.3))]))
    b.append(C('hammer', 1.2, 14.4, 16.8, 16.2, 17.4, 'black', rot=('x', 15, (8, 14.4, 16.8))))
    b.append(B('safety_l', 6.0, 16.2, 13.0, 6.3, 17.0, 15.2, 'black'))
    b.append(B('safety_r', 9.7, 16.2, 13.0, 10.0, 17.0, 15.2, 'black'))
    b += trigger_group(-5.2, 3.2, 7.6, 11.8, mat='black', trigger_z=0.6)
    grip_rot = ('x', -16, (8, 11.8, 9.6))
    b.append(C('grip', 3.0, -2.2, 12.8, 5.6, 13.6, 'rubber', rot=grip_rot, decals=[
        stipple('sides', 3, 0.3, y=(-1.4, 11.0)), lines('front', 'y', [0.6, 3.4, 6.2], 0, hilite=3)]))
    b.append(C('mag_base', 3.2, -3.0, -2.2, 5.8, 13.4, 'black', rot=grip_rot))
    return b


def m_barrett():
    b = []
    b.append(C('brake', 3.2, 8.2, 10.8, -16.0, -12.6, 'black', decals=[
        circle('front', {'x': 8, 'y': 9.5}, 0.7),
        mark('sides', 'hole', z=(-15.4, -14.6), y=(8.8, 10.2)),
        mark('sides', 'hole', z=(-13.8, -13.0), y=(8.8, 10.2))]))
    b += rod('barrel', 1.6, 9.5, -12.6, 2.0, 'black', decals=[lines('around', 'z', [-12.2, 1.6], 1)])
    b.append(C('upper_front', 2.8, 8.4, 11.0, 2.0, 12.0, 'gunmetal', decals=[
        grid('sides', 'z', [3.4, 5.4, 7.4, 9.4], 'y', [9.4], 'hole', 2)]))
    b.append(C('upper', 3.0, 8.4, 11.2, 12.0, 23.0, 'gunmetal', decals=[
        mark('right', 'hole', z=(15.0, 19.5), y=(9.2, 10.6)),
        rivets('sides', [{'z': 12.6, 'y': 10.4}, {'z': 22.4, 'y': 10.4}])]))
    b.append(C('lower', 2.6, 6.8, 8.4, 9.0, 26.5, 'gunmetal'))
    b.append(rail('top_rail', 6.0, 22.0, 11.2, w=1.4))
    b += rod('scope', 2.4, 13.6, 8.5, 19.5, 'black', k=0.72)
    b += rod('scope_bell', 3.2, 13.6, 5.5, 8.5, 'black', k=0.72, decals=[glass('front')])
    b += rod('scope_eye', 2.8, 13.6, 19.5, 22.0, 'black', k=0.72, decals=[glass('back')])
    b.append(C('turret_top', 1.2, 14.8, 15.9, 13.2, 14.6, 'black', decals=[lines('sides', 'z', [13.6, 14.2], 3)]))
    b.append(B('turret_side', 9.2, 13.0, 13.2, 10.1, 14.2, 14.6, 'black'))
    b.append(C('ring_f', 1.8, 11.8, 12.8, 10.0, 10.9, 'black'))
    b.append(C('ring_r', 1.8, 11.8, 12.8, 16.8, 17.7, 'black'))
    b.append(C('mag', 2.2, 3.6, 6.8, 10.6, 14.4, 'black', decals=[mag_rib(), mark('bottom', 3)]))
    b += trigger_group(15.3, 19.2, 4.8, 6.8, mat='gunmetal', trigger_z=16.9)
    b.append(C('grip', 1.8, 1.6, 7.4, 18.6, 20.6, 'polymer', rot=('x', -18, (8, 6.9, 19.6)),
               decals=[stipple('sides', 0, 0.35, y=(2.2, 6.4))]))
    b.append(C('stock', 2.8, 5.0, 10.8, 26.5, 31.0, 'gunmetal', decals=[lines('sides', 'z', [28.6], 1)]))
    b.append(C('cheek', 2.2, 10.8, 11.6, 24.0, 29.5, 'polymer'))
    b.append(C('pad', 3.0, 4.6, 11.0, 31.0, 32.0, 'rubber'))
    b.append(C('monopod', 0.8, 2.6, 5.0, 29.0, 29.8, 'black'))
    b.append(C('monopod_foot', 1.6, 2.2, 2.6, 28.6, 30.2, 'black'))
    b.append(B('bipod_l', 6.6, 7.6, -9.0, 7.1, 8.2, 3.0, 'black'))
    b.append(B('bipod_r', 8.9, 7.6, -9.0, 9.4, 8.2, 3.0, 'black'))
    b.append(C('bipod_mount', 3.0, 7.6, 8.6, 2.0, 3.4, 'black'))
    return b


def m_svd():
    b = []
    b += rod('flash', 1.3, 9.35, -16.0, -12.4, 'black', decals=[
        mark('sides', 'hole', z=(-15.4, -13.0), y=(9.2, 9.5)), circle('front', {'x': 8, 'y': 9.35}, 0.4)])
    b.append(C('barrel', 0.9, 8.9, 9.8, -12.4, 4.0, 'black'))
    b.append(C('front_sight', 0.8, 9.8, 11.3, -11.4, -10.6, 'black'))
    b.append(C('gas_block', 1.4, 8.8, 11.0, -3.2, -1.8, 'steel'))
    b.append(C('guard_low', 2.2, 8.0, 10.0, -1.8, 8.0, 'wood', decals=[
        grid('sides', 'z', frange(-0.6, 7.0, 1.6), 'y', [9.0], 'hole', 2)]))
    b.append(C('guard_up', 1.8, 10.0, 11.3, -1.2, 8.0, 'wood', decals=[
        grid('sides', 'z', frange(0.0, 7.2, 1.6), 'y', [10.6], 'hole', 1)]))
    b.append(C('receiver', 2.2, 7.6, 10.4, 8.0, 20.4, 'steel', decals=[
        rivets('sides', [{'z': 9.0, 'y': 8.2}, {'z': 18.0, 'y': 8.2}, {'z': 19.6, 'y': 9.6}]),
        mark('right', 'hole', z=(13.0, 17.0), y=(9.4, 10.4))]))
    b.append(C('dust_cover', 1.8, 10.4, 11.1, 11.0, 20.4, 'steel', decals=[lines('top', 'z', [18.6, 19.4], 0, hilite=4)]))
    b.append(C('rear_sight', 1.6, 10.4, 11.3, 8.4, 10.2, 'steel'))
    b.append(B('charging_handle', 9.1, 9.8, 11.4, 10.3, 10.4, 12.4, 'steel'))
    b.append(B('safety', 9.1, 8.3, 12.2, 9.3, 9.2, 17.0, 'steel'))
    b.append(B('scope_mount', 6.0, 9.0, 11.0, 6.9, 11.8, 17.0, 'black'))
    b.append(B('scope_brace', 6.2, 11.8, 12.2, 7.4, 12.6, 16.0, 'black'))
    b += rod('scope', 1.9, 13.4, 9.0, 19.0, 'black', xc=7.0)
    b += rod('scope_bell', 2.5, 13.4, 7.6, 9.0, 'black', xc=7.0, decals=[glass('front')])
    b += rod('scope_eye', 2.5, 13.4, 19.0, 21.6, 'rubber', xc=7.0, decals=[glass('back')])
    b.append(B('turret_top', 6.4, 14.35, 14.0, 7.6, 15.2, 15.4, 'black'))
    b.append(B('turret_side', 7.95, 12.8, 14.0, 8.8, 14.0, 15.4, 'black'))
    b += curved_mag('mag', 7.8, 12.8, 3.0, 2.2, (3, 10), 1.8, 'steel', decals=[mag_rib()])
    b[-1].decals.append(mark('bottom', 3))
    b += trigger_group(15.4, 18.6, 5.8, 7.6, mat='steel', trigger_z=16.9)
    b.append(C('stock_top', 1.8, 9.0, 10.5, 20.4, 30.2, 'wood'))
    b.append(B('cheek', 6.6, 9.3, 23.0, 7.1, 10.5, 28.0, 'rubber'))
    b.append(C('grip', 1.7, 3.2, 8.2, 18.8, 21.0, 'wood', rot=('x', -14, (8, 7.9, 19.9))))
    b.append(C('stock_low', 1.8, 2.8, 4.2, 20.6, 30.2, 'wood'))
    b.append(C('stock_butt', 1.8, 2.8, 10.5, 29.0, 30.6, 'wood'))
    b.append(C('buttplate', 1.9, 2.6, 10.6, 30.6, 31.2, 'steel', decals=[lines('back', 'y', frange(3.2, 10.2, 0.8), 1)]))
    return b


def m_awp():
    b = []
    b.append(C('brake', 1.8, 8.4, 10.2, -16.0, -13.6, 'black', decals=[
        circle('front', {'x': 8, 'y': 9.3}, 0.45),
        mark('sides', 'hole', z=(-15.4, -14.8), y=(8.8, 9.8)),
        mark('sides', 'hole', z=(-14.4, -13.8), y=(8.8, 9.8))]))
    b += rod('barrel', 1.5, 9.3, -13.6, 11.0, 'black')
    b.append(C('forend', 2.6, 6.8, 8.9, -1.0, 11.5, 'olive', decals=[
        grid('sides', 'z', frange(0.6, 10.0, 1.6), 'y', [7.9], 'hole', 2)]))
    b.append(B('forend_l', 6.7, 8.9, 0.0, 7.2, 9.8, 11.5, 'olive'))
    b.append(B('forend_r', 8.8, 8.9, 0.0, 9.3, 9.8, 11.5, 'olive'))
    b += rod('action', 2.2, 10.4, 11.0, 20.4, 'steel', k=0.72, decals=[mark('right', 'hole', z=(14.4, 17.2),
                                                                             y=(10.0, 11.0))])
    b.append(C('chassis', 2.6, 6.4, 9.6, 11.5, 21.0, 'olive'))
    b += rod('bolt_shroud', 1.5, 10.4, 20.4, 21.6, 'steel')
    b.append(B('bolt_arm', 9.2, 10.0, 17.8, 10.9, 10.6, 18.5, 'steel', rot=('z', -25, (9.2, 10.3, 18.15))))
    b.append(B('bolt_knob', 10.5, 8.8, 17.5, 11.9, 10.2, 18.8, 'black'))
    b += rod('scope', 2.4, 13.4, 8.0, 19.0, 'black', k=0.72)
    b += rod('scope_bell', 3.2, 13.4, 5.0, 8.0, 'black', k=0.72, decals=[glass('front')])
    b += rod('scope_eye', 2.8, 13.4, 19.0, 21.4, 'black', k=0.72, decals=[glass('back')])
    b.append(C('turret_top', 1.2, 14.6, 15.8, 12.8, 14.2, 'black', decals=[lines('sides', 'z', [13.2, 13.8], 3)]))
    b.append(B('turret_side', 9.2, 12.8, 12.8, 10.2, 14.0, 14.2, 'black'))
    b.append(C('ring_f', 1.8, 11.2, 12.6, 10.0, 10.9, 'black'))
    b.append(C('ring_r', 1.8, 11.2, 12.6, 16.0, 16.9, 'black'))
    b.append(C('mag', 1.9, 4.8, 6.4, 13.0, 16.2, 'black', decals=[mark('bottom', 3)]))
    b += trigger_group(16.6, 19.8, 4.4, 6.4, mat='black', trigger_z=18.0)
    b.append(C('grip', 1.8, 2.8, 7.6, 19.6, 21.8, 'olive', rot=('x', -10, (8, 7.0, 20.7)),
               decals=[stipple('sides', 0, 0.3, y=(3.4, 6.8))]))
    b.append(C('comb', 2.2, 8.8, 11.0, 21.0, 30.2, 'olive'))
    b.append(C('cheek', 2.0, 11.0, 11.6, 23.0, 28.6, 'olive'))
    b.append(C('stock_lowbar', 2.0, 2.8, 4.0, 21.2, 25.2, 'olive'))
    b.append(C('stock_rear', 2.2, 2.8, 8.8, 25.2, 30.2, 'olive'))
    b.append(C('pad', 2.4, 2.6, 11.1, 30.2, 31.2, 'rubber'))
    return b


def m_remington870():
    b = []
    b += rod('barrel', 1.5, 10.0, -15.0, 8.2, 'steel', decals=[circle('front', {'x': 8, 'y': 10.0}, 0.55)])
    b.append(C('bead', 0.4, 10.75, 11.2, -14.6, -14.2, 'brass'))
    b += rod('mag_tube', 1.3, 8.4, -12.2, 8.2, 'steel')
    b += rod('mag_cap', 1.6, 8.4, -13.0, -12.2, 'steel')
    b.append(C('clamp', 1.2, 8.4, 10.0, -12.0, -11.4, 'steel'))
    b += rod('pump', 2.4, 8.3, -4.0, 5.0, 'walnut', k=0.8, decals=[
        lines('around', 'y', frange(7.4, 9.4, 0.5), 1), lines('bottom', 'x', [7.4, 8.0, 8.6], 1)])
    b.append(C('receiver', 2.3, 7.4, 11.0, 8.2, 18.8, 'steel', decals=[
        mark('right', 'hole', z=(10.0, 15.0), y=(8.8, 10.4)),
        mark('bottom', 'hole', x=(7.4, 8.6), z=(9.0, 13.6)),
        rivets('sides', [{'z': 16.2, 'y': 7.9}, {'z': 17.6, 'y': 7.9}])]))
    b.append(C('hump', 2.0, 11.0, 11.4, 9.0, 18.0, 'steel'))
    b.append(C('trigger_plate', 1.4, 6.6, 7.4, 13.4, 18.6, 'black'))
    b += trigger_group(13.6, 17.4, 5.2, 6.6, mat='black', trigger_z=15.4)
    b.append(C('wrist', 1.9, 6.2, 10.6, 18.6, 23.4, 'walnut', rot=('x', 12, (8, 10.6, 18.6)),
               decals=[checker('sides', 1, 0.5, y=(7.0, 9.8))]))
    b.append(C('stock', 2.1, 4.2, 10.4, 22.8, 30.2, 'walnut', rot=('x', 7, (8, 10.4, 18.6))))
    b.append(C('pad', 2.2, 4.0, 10.6, 30.2, 31.0, 'rubber', rot=('x', 7, (8, 10.4, 18.6))))
    return b


def m_spas12():
    b = []
    b += rod('barrel', 1.5, 10.2, -14.5, 8.0, 'black', decals=[circle('front', {'x': 8, 'y': 10.2}, 0.55)])
    b.append(C('front_sight', 0.6, 10.9, 12.0, -13.8, -13.0, 'black'))
    b += rod('mag_tube', 1.3, 8.5, -11.0, 8.0, 'black')
    b += rod('mag_cap', 1.6, 8.5, -12.0, -11.0, 'black')
    b.append(C('handguard', 2.5, 7.4, 11.0, -6.0, 5.0, 'polymer', decals=[
        grid('sides', 'z', frange(-5.0, 4.4, 1.2), 'y', [8.4, 9.8], 'hole', 2),
        grid('top', 'z', frange(-5.0, 4.4, 1.2), 'x', [7.4, 8.4], 'hole', 1)]))
    b.append(C('receiver', 2.4, 7.2, 11.6, 8.0, 20.0, 'black', decals=[
        mark('right', 'hole', z=(10.0, 14.6), y=(9.0, 10.8)),
        lines('sides', 'z', [15.6], 1)]))
    b.append(C('rear_sight', 1.4, 11.6, 12.8, 16.4, 18.2, 'black', decals=[mark('back', 'hole', x=(7.8, 8.2),
                                                                                    y=(12.0, 12.8))]))
    b += trigger_group(13.4, 17.0, 5.4, 7.2, mat='black', trigger_z=15.0)
    b.append(C('grip', 1.8, 1.6, 7.8, 16.8, 18.8, 'polymer', rot=('x', -18, (8, 7.3, 17.8)),
               decals=[stipple('sides', 0, 0.35, y=(2.2, 6.6))]))
    b.append(C('stock_hinge', 2.8, 7.6, 12.8, 20.0, 21.4, 'steel'))
    b.append(B('stock_arm_l', 6.5, 12.2, 1.2, 6.9, 12.8, 21.4, 'steel'))
    b.append(B('stock_arm_r', 9.1, 12.2, 1.2, 9.5, 12.8, 21.4, 'steel'))
    b.append(C('stock_butt', 3.2, 11.6, 13.2, 0.0, 1.2, 'steel', decals=[lines('front', 'y', [12.0, 12.6], 1)]))
    b.append(C('stock_hook', 1.0, 10.2, 11.6, -0.6, 0.2, 'steel'))
    return b


def m_aa12():
    b = []
    b += rod('muzzle', 1.9, 9.7, -15.0, -13.4, 'black', decals=[circle('front', {'x': 8, 'y': 9.7}, 0.6)])
    b += rod('barrel', 1.5, 9.7, -13.4, 2.0, 'black')
    b.append(C('shield', 2.3, 8.4, 11.0, -6.0, 4.0, 'black', decals=[
        grid('sides', 'z', frange(-5.2, 3.6, 1.1), 'y', [9.0, 10.2], 'hole', 2)]))
    b.append(C('receiver', 2.9, 6.4, 11.8, 4.0, 22.0, 'black', decals=[
        mark('right', 'hole', z=(11.0, 15.4), y=(9.4, 11.0)),
        rivets('sides', [{'z': z, 'y': 7.2} for z in (5.0, 9.0, 16.0, 21.0)]),
        lines('sides', 'z', [8.0], 1)]))
    b.append(rail('rail', 5.0, 21.0, 11.8))
    b.append(C('front_sight', 1.2, 12.4, 13.4, 5.2, 6.6, 'black'))
    b.append(C('rear_sight', 1.4, 12.4, 13.6, 18.6, 20.4, 'black'))
    b.append(B('charging_handle', 5.9, 10.4, 7.0, 6.55, 11.0, 8.4, 'steel'))
    b.append(C('drum_neck', 2.0, 5.8, 6.6, 10.6, 13.4, 'black'))
    b += xcyl('drum', 7.0, 2.8, 12.0, 6.4, 9.6, 'gunmetal', decals=[
        circle('sides', {'z': 12.0, 'y': 2.8}, 2.9, 0, width=0.5),
        circle('sides', {'z': 12.0, 'y': 2.8}, 1.0, ('steel', 3)),
        circle('sides', {'z': 12.0, 'y': 2.8}, 0.4, 0)])
    b += trigger_group(16.8, 20.2, 4.6, 6.4, mat='black', trigger_z=18.2)
    b.append(C('grip', 1.8, 1.4, 7.0, 18.8, 20.8, 'polymer', rot=('x', -14, (8, 6.6, 19.8)),
               decals=[stipple('sides', 0, 0.35, y=(2.0, 6.0))]))
    b.append(C('stock', 2.2, 6.6, 10.8, 22.0, 30.4, 'black', decals=[lines('sides', 'z', [24.0], 1)]))
    b.append(C('stock_low', 2.2, 4.6, 6.6, 25.6, 30.4, 'black'))
    b.append(C('pad', 2.4, 4.4, 11.0, 30.4, 31.2, 'rubber'))
    return b


def m_sawed_off():
    b = []
    for side, xc in (('l', 6.95), ('r', 9.05)):
        b += rod('barrel_' + side, 2.0, 12.0, -14.0, 8.4, 'steel', xc=xc,
                 decals=[circle('front', {'x': xc, 'y': 12.0}, 0.7)])
    b.append(C('rib', 0.6, 12.8, 13.2, -13.6, 8.4, 'steel'))
    b.append(C('bead', 0.4, 13.2, 13.6, -13.4, -13.0, 'brass'))
    b.append(C('lug', 3.4, 10.0, 11.2, 2.4, 8.4, 'steel'))
    b.append(C('forend', 3.2, 9.6, 11.2, -2.0, 6.0, 'walnut', decals=[checker('sides', 1, 0.5, z=(-1.2, 5.2))]))
    b.append(C('action', 4.2, 9.4, 13.6, 8.4, 15.0, 'silver', decals=[
        lines('sides', 'y', [10.2, 12.6], 1),
        dots('sides', [{'z': z, 'y': y} for z in (9.4, 10.6, 11.8, 13.0) for y in (11.0, 11.8)], 1)]))
    b.append(C('top_lever', 0.8, 13.6, 14.1, 13.0, 16.2, 'silver', rot=('y', 20, (8, 13.8, 14.0))))
    b.append(B('hammer_l', 6.6, 13.2, 14.2, 7.2, 15.4, 15.2, 'steel', rot=('x', 25, (6.9, 13.2, 14.7))))
    b.append(B('hammer_r', 8.8, 13.2, 14.2, 9.4, 15.4, 15.2, 'steel', rot=('x', 25, (9.1, 13.2, 14.7))))
    b.append(C('guard_bottom', 0.8, 7.0, 7.6, 9.6, 13.8, 'steel'))
    b.append(C('guard_front', 0.8, 7.0, 9.4, 9.2, 9.8, 'steel'))
    b.append(C('trigger_f', 0.4, 7.6, 9.4, 10.6, 11.0, 'steel'))
    b.append(C('trigger_r', 0.4, 7.6, 9.4, 12.0, 12.4, 'steel'))
    grip_rot = ('x', -32, (8, 12.0, 15.6))
    b.append(C('grip', 2.6, 2.0, 12.4, 14.0, 18.4, 'walnut', rot=grip_rot,
               decals=[checker('sides', 1, 0.55, y=(3.4, 10.4))]))
    b.append(C('grip_cap', 2.8, 1.2, 2.0, 13.8, 18.6, 'steel', rot=grip_rot))
    return b


def m_rpg7():
    b = []
    # the rocket sticking out of the tube: the olive PG-7V (HEAT) or the fatter red TBG-7V (thermobaric)
    pg7 = dict(when=lambda s: s.get('round') == 'pg7v')
    tbg = dict(when=lambda s: s.get('round') == 'tbg')
    b += rod('fuze', 0.7, 9.6, -16.0, -15.0, 'black', **pg7)
    b += rod('nose1', 1.6, 9.6, -15.0, -13.6, 'khaki', k=0.72, **pg7)
    b += rod('nose2', 2.5, 9.6, -13.6, -12.0, 'khaki', k=0.72, **pg7)
    b += tube('warhead', 3.3, 9.6, -12.0, -7.0, 'khaki', decals=[mark('around', ('black', 1), z=(-8.2, -7.6)),
                                                                   mark('around', ('warn', 2), z=(-11.4, -11.0))],
              **pg7)
    b += rod('warhead_tail', 2.4, 9.6, -7.0, -5.0, 'khaki', k=0.72, **pg7)
    b += rod('probe', 0.6, 9.6, -16.0, -12.8, 'steel', **tbg)
    b += rod('tbg_cap', 2.2, 9.6, -12.8, -12.0, 'red', k=0.72, **tbg)
    b += tube('tbg_body', 3.9, 9.6, -12.0, -4.5, 'red', decals=[mark('around', ('warn', 3), z=(-10.6, -9.8)),
                                                                 mark('around', ('black', 1), z=(-5.6, -5.0))],
              **tbg)
    b += rod('tbg_tail', 2.2, 9.6, -4.5, -3.0, 'red', k=0.72, **tbg)
    b += rod('sustainer', 1.3, 9.6, -5.0, -1.0, 'black', **pg7)
    b += rod('tbg_sustainer', 1.3, 9.6, -3.0, -1.0, 'black', **tbg)
    b += rod('tube', 1.7, 9.6, -1.0, 27.4, 'black', k=0.72)
    b += rod('tube_front', 2.0, 9.6, -1.2, 0.0, 'steel', k=0.72,
             decals=[circle('front', {'x': 8, 'y': 9.6}, 0.7)])
    for name, z0, z1 in (('heat_f', 5.5, 11.5), ('heat_r', 15.5, 21.5)):
        b += tube(name, 2.6, 9.6, z0, z1, 'wood', decals=[mark('around', ('black', 1), z=(z0 + 0.2, z0 + 0.6)),
                                                           mark('around', ('black', 1), z=(z1 - 0.6, z1 - 0.2))])
    b += rod('venturi', 2.2, 9.6, 27.4, 29.5, 'black', k=0.72)
    b += tube('blast_cone', 3.0, 9.6, 29.5, 32.0, 'black', decals=[circle('back', {'x': 8, 'y': 9.6}, 1.1)])
    b.append(C('grip', 1.6, 3.6, 9.2, 12.0, 14.2, 'polymer', rot=('x', -12, (8, 8.8, 13.1)),
               decals=[stipple('sides', 0, 0.35, y=(4.2, 8.0))]))
    b.append(C('guard_front', 0.6, 6.4, 8.8, 10.6, 11.0, 'black'))
    b.append(C('guard_bottom', 0.6, 6.4, 6.8, 11.0, 12.6, 'black'))
    b.append(C('trigger', 0.35, 6.8, 8.6, 11.6, 12.0, 'steel'))
    b.append(C('fore_grip', 1.4, 4.6, 9.0, 3.2, 4.6, 'polymer', rot=('x', 8, (8, 8.8, 3.9))))
    b.append(C('front_sight', 0.5, 10.4, 12.0, 3.8, 4.4, 'black'))
    b.append(C('rear_sight', 1.0, 10.4, 11.8, 13.4, 14.2, 'black'))
    b.append(B('optic_mount', 6.4, 9.8, 12.6, 7.0, 11.2, 16.0, 'black'))
    b += rod('optic', 1.5, 12.0, 11.0, 17.6, 'black', xc=6.0, decals=[glass('front')])
    b += rod('optic_eye', 1.9, 12.0, 17.6, 19.0, 'rubber', xc=6.0, decals=[glass('back')])
    return b


def m_m32():
    b = []
    b += rod('barrel', 2.2, 10.2, -12.0, 3.0, 'black', k=0.72)
    b += rod('muzzle', 2.5, 10.2, -12.5, -11.4, 'black', k=0.72, decals=[circle('front', {'x': 8, 'y': 10.2}, 0.95)])
    b.append(C('rail_guard', 2.8, 8.8, 11.6, -8.0, 3.0, 'black', decals=[
        lines('top', 'z', frange(-7.6, 2.6, 0.8), 0, hilite=4),
        lines('sides', 'z', frange(-7.6, 2.6, 0.8), 0, y=(9.6, 10.8)),
        lines('bottom', 'z', frange(-7.6, 2.6, 0.8), 0)]))
    b.append(C('fore_grip', 1.4, 4.4, 9.0, -5.4, -3.8, 'polymer', rot=('x', 6, (8, 8.8, -4.6)),
               decals=[lines('front', 'y', frange(5.0, 8.4, 0.7), 0)]))
    chambers = [{'x': 8 + 2.2 * math.cos(math.radians(90 + 60 * k)),
                 'y': 7.9 + 2.2 * math.sin(math.radians(90 + 60 * k))} for k in range(6)]
    cyl_decals = [circle('front', c, 0.75) for c in chambers]
    cyl_decals += [circle('front', {'x': 8, 'y': 7.9}, 0.5, ('steel', 4)),
                   lines('around', 'z', [5.0, 9.0], 1)]
    b += tube('cylinder', 7.0, 7.9, 3.0, 11.0, 'steel', decals=cyl_decals)
    b += rod('axle', 1.2, 7.9, 2.4, 3.0, 'steel')
    b.append(C('top_strap', 1.6, 11.4, 12.2, 2.0, 12.0, 'olive'))
    b.append(C('frame', 2.4, 6.4, 11.8, 11.0, 17.4, 'olive', decals=[rivets('sides', [{'z': 12.0, 'y': 7.2},
                                                                                      {'z': 16.6, 'y': 11.0}])]))
    b.append(C('sight', 2.0, 12.2, 14.6, 4.6, 8.6, 'black', decals=[glass('front'), glass('back')]))
    b.append(C('rail', 1.2, 11.8, 12.2, 11.2, 17.0, 'black'))
    b.append(C('guard_front', 0.6, 4.8, 6.4, 11.6, 12.1, 'olive'))
    b.append(C('guard_bottom', 0.6, 4.8, 5.3, 12.1, 15.0, 'olive'))
    b.append(C('trigger', 0.35, 5.3, 6.4, 13.2, 13.6, 'steel'))
    b.append(C('grip', 1.7, 1.2, 7.2, 14.6, 16.6, 'polymer', rot=('x', -16, (8, 6.6, 15.6)),
               decals=[stipple('sides', 0, 0.35, y=(1.8, 6.4))]))
    b += rod('buffer', 1.5, 9.6, 17.4, 24.5, 'black')
    b.append(C('stock', 1.9, 6.6, 11.0, 22.0, 29.8, 'olive'))
    b.append(C('stock_low', 1.9, 5.2, 6.6, 25.0, 29.8, 'olive'))
    b.append(C('pad', 2.0, 5.0, 11.2, 29.8, 30.6, 'rubber'))
    return b


def m_railgun():
    b = []

    def energy(box_args, name, **kw):
        """A glowing part when loaded, white-hot at full charge, a dead grey one when the gun is empty."""
        x0, y0, z0, x1, y1, z1 = box_args
        return [B(name, x0, y0, z0, x1, y1, z1, 'rail', when=lambda s: s['loaded'] and s['charge'] < 5, **kw),
                B(name + '_hot', x0, y0, z0, x1, y1, z1, 'rail_hot', when=lambda s: s['charge'] >= 5, **kw),
                B(name + '_off', x0, y0, z0, x1, y1, z1, 'rail_dim', when='empty', **kw)]

    def charge_step(box_args, name, step):
        """Dark until the charge reaches `step` (1..5 of the item's charge models), then lit; white-hot when full."""
        x0, y0, z0, x1, y1, z1 = box_args
        return [B(name + '_off', x0, y0, z0, x1, y1, z1, 'rail_dim', when=lambda s: s['charge'] < step),
                B(name, x0, y0, z0, x1, y1, z1, 'rail', when=lambda s: step <= s['charge'] < 5),
                B(name + '_hot', x0, y0, z0, x1, y1, z1, 'rail_hot', when=lambda s: s['charge'] >= 5)]

    b.append(C('rail_top', 2.0, 11.2, 12.4, -16.0, 10.0, 'gunmetal', decals=[lines('sides', 'z', frange(-15, 9, 2.5), 1)]))
    b.append(C('rail_bottom', 2.0, 7.2, 8.4, -16.0, 10.0, 'gunmetal', decals=[lines('sides', 'z', frange(-15, 9, 2.5), 1)]))
    b += energy((7.5, 10.9, -15.6, 8.5, 11.2, 9.6), 'glow_top')
    b += energy((7.5, 8.4, -15.6, 8.5, 8.7, 9.6), 'glow_bottom')
    b += energy((7.0, 8.9, -16.0, 9.0, 10.7, -15.6), 'emitter')
    # the charge display: each coil has a window through its sides and one on top, dark while the gun idles; holding
    # the trigger lights them one after another from the breech to the muzzle, and all go white-hot when it is full
    for i, z in enumerate((-13.0, -8.0, -3.0, 2.0, 7.0)):
        b.append(C('coil_%d' % i, 3.6, 6.6, 13.0, z, z + 1.4, 'gunmetal', decals=[lines('front', 'y', [9.8], 0)]))
        step = 5 - i
        b += charge_step((6.0, 7.4, z + 0.2, 10.0, 12.2, z + 1.2), 'coil_glow_%d' % i, step)
        b += charge_step((6.7, 13.0, z + 0.2, 9.3, 13.15, z + 1.2), 'coil_top_%d' % i, step)
    b.append(C('housing', 3.8, 6.0, 12.8, 10.0, 23.0, 'black', decals=[
        lines('sides', 'z', [11.0, 21.6], 1), rivets('sides', [{'z': 10.6, 'y': 12.2}, {'z': 22.4, 'y': 12.2},
                                                               {'z': 10.6, 'y': 6.6}, {'z': 22.4, 'y': 6.6}])]))
    # the capacitor bank down both sides of the housing: a steady glow while loaded
    cells = [lines('sides', 'z', frange(13.2, 20.6, 1.2), ('rail_dim', 0))]
    b += energy((6.0, 7.4, 12.0, 6.1, 11.2, 20.6), 'cells_l', decals=cells)
    b += energy((9.9, 7.4, 12.0, 10.0, 11.2, 20.6), 'cells_r', decals=cells)
    for i, z in enumerate((15.0, 16.6, 18.2)):
        b.append(C('fin_%d' % i, 3.0, 12.8, 13.8, z, z + 0.6, 'steel'))
    b.append(C('sight', 1.6, 12.8, 14.8, 10.6, 13.8, 'gunmetal', decals=[glass('front'), glass('back')]))
    b.append(B('cable', 9.8, 8.0, 6.0, 10.6, 8.8, 11.0, 'rubber'))
    b += trigger_group(18.6, 21.6, 3.8, 6.0, mat='black', trigger_z=20.0)
    b.append(C('grip', 1.8, 1.2, 6.8, 20.4, 22.4, 'polymer', rot=('x', -16, (8, 6.2, 21.4)),
               decals=[stipple('sides', 0, 0.35, y=(1.8, 5.8))]))
    b.append(C('stock_top', 1.8, 9.6, 11.6, 23.0, 30.8, 'gunmetal'))
    b.append(C('battery', 2.4, 5.8, 9.6, 24.4, 30.0, 'black', decals=[lines('sides', 'z', [26.0, 28.4], 1)]))
    b += energy((9.2, 6.6, 25.4, 9.3, 8.6, 29.0), 'battery_led')
    b.append(C('pad', 2.2, 5.4, 11.8, 30.8, 31.8, 'rubber'))
    return b


# =====================================================================================================
# the throwables (upright along y)
# =====================================================================================================

def fuse_assembly(top, body_r, mat='steel'):
    """Fuse head, spoon down the right side and the pull ring on the left: every hand grenade has one."""
    b = []
    b += ycyl('fuse', 2.4, top, top + 1.6, mat)
    b += ycyl('fuse_cap', 1.7, top + 1.6, top + 2.2, mat, layers=2)
    sx = 8 + body_r
    b.append(B('spoon', sx, top - 5.6, 7.3, sx + 0.5, top + 1.8, 8.7, 'grey'))
    b.append(B('spoon_top', 8.8, top + 1.6, 7.3, sx + 0.5, top + 2.1, 8.7, 'grey'))
    rx = 8 - body_r - 0.6
    b.append(B('pin', rx + 0.2, top + 0.6, 7.8, 8.0 - 0.9, top + 1.0, 8.2, 'silver'))
    b.append(B('ring_t', rx - 2.4, top + 2.6, 7.7, rx, top + 3.0, 8.3, 'silver'))
    b.append(B('ring_b', rx - 2.4, top + 0.2, 7.7, rx, top + 0.6, 8.3, 'silver'))
    b.append(B('ring_l', rx - 2.4, top + 0.6, 7.7, rx - 2.0, top + 2.6, 8.3, 'silver'))
    b.append(B('ring_r', rx - 0.4, top + 0.6, 7.7, rx, top + 2.6, 8.3, 'silver'))
    return b


def m_frag():
    b = []
    grooves = [lines('around_y', 'y', [2.6, 4.6, 6.6, 8.6], 0, hilite=3),
               lines(('north', 'south'), 'x', [5.6, 8.0, 10.4], 0),
               lines(('east', 'west'), 'z', [5.6, 8.0, 10.4], 0)]
    b += ycyl('body', 7.6, 1.4, 10.2, 'olive', decals=grooves)
    b += ycyl('body_ends', 5.2, 0.6, 11.0, 'olive', layers=2, decals=grooves)
    b += fuse_assembly(11.0, 3.8)
    return b


def m_incendiary():
    b = []
    b += ycyl('body', 5.6, 0.6, 10.6, 'red', decals=[
        mark('around_y', ('warn', 3), y=(7.0, 8.4)),
        mark('around_y', ('black', 1), y=(2.4, 2.8)),
        dots('around_y', [{'x': 8, 'y': 5.2}, {'z': 8, 'y': 5.2}], ('warn', 4), 1)])
    b += ycyl('cap', 5.0, 10.6, 11.4, 'grey', layers=2)
    b += fuse_assembly(11.4, 2.8)
    return b


def m_flashbang():
    b = []
    holes = [grid(('north', 'south'), 'x', [6.2, 7.4, 8.6, 9.8], 'y', [3.0, 4.6, 7.0, 8.6], 'hole', 1),
             grid(('east', 'west'), 'z', [6.2, 7.4, 8.6, 9.8], 'y', [3.0, 4.6, 7.0, 8.6], 'hole', 1),
             mark('around_y', ('warn', 3), y=(5.6, 6.2))]
    b += ycyl('body', 5.2, 0.8, 10.8, 'grey', decals=holes)
    b += ycyl('cap_bottom', 5.6, 0.4, 1.4, 'steel', layers=2)
    b += ycyl('cap_top', 5.6, 10.2, 11.2, 'steel', layers=2)
    b += fuse_assembly(11.2, 2.8)
    return b


def m_smoke():
    b = []
    b += ycyl('body', 5.8, 0.6, 11.0, 'olive', decals=[
        mark('around_y', ('white', 3), y=(8.8, 10.4)),
        lines('around_y', 'y', [9.6], ('black', 2), x=(6.4, 9.6), z=(6.4, 9.6)),
        mark('around_y', ('warn', 3), y=(1.4, 1.8))])
    b += ycyl('cap', 5.0, 11.0, 11.6, 'grey', layers=2, decals=[
        grid('top', 'x', [6.8, 9.2], 'z', [6.8, 9.2], 'hole', 1)])
    b += fuse_assembly(11.6, 2.9)
    return b


def m_singularity():
    b = []
    b.append(B('core', 5.6, 4.6, 5.6, 10.4, 9.4, 10.4, 'void', rot=('euler', (35.26, 45.0, 0.0), (8, 7, 8))))
    for i, (x, z) in enumerate(((4.2, 4.2), (11.0, 4.2), (4.2, 11.0), (11.0, 11.0))):
        b.append(B('bar_%d' % i, x, 1.8, z, x + 0.8, 12.2, z + 0.8, 'gunmetal', decals=[
            lines('around_y', 'y', [4.0, 10.0], 1)]))
    plate = [circle('top', {'x': 8, 'z': 8}, 2.2, 'hole'), circle('top', {'x': 8, 'z': 8}, 2.2, 0, width=0.4),
             rivets('around_y', [{'x': 4.6, 'y': 1.3}, {'x': 11.4, 'y': 1.3}, {'z': 4.6, 'y': 1.3}, {'z': 11.4, 'y': 1.3}])]
    b.append(B('plate_bottom', 3.8, 0.8, 3.8, 12.2, 1.8, 12.2, 'gunmetal', decals=plate))
    b.append(B('plate_top', 3.8, 12.2, 3.8, 12.2, 13.2, 12.2, 'gunmetal', decals=plate))
    halo = ('y', 45, (8, 7, 8))
    b.append(B('halo_n', 2.6, 6.7, 2.6, 13.4, 7.3, 3.2, 'void', rot=halo))
    b.append(B('halo_s', 2.6, 6.7, 12.8, 13.4, 7.3, 13.4, 'void', rot=halo))
    b.append(B('halo_w', 2.6, 6.7, 3.2, 3.2, 7.3, 12.8, 'void', rot=halo))
    b.append(B('halo_e', 12.8, 6.7, 3.2, 13.4, 7.3, 12.8, 'void', rot=halo))
    b += ycyl('handle', 1.6, 13.2, 14.4, 'steel', layers=2)
    return b


def m_thermobaric():
    b = []
    b += ycyl('nose_tip', 1.6, 13.0, 14.2, 'black', layers=2)
    b += ycyl('nose', 3.4, 11.4, 13.0, 'orange')
    b += ycyl('body', 5.0, 4.0, 11.4, 'orange', decals=[
        mark('around_y', ('warn', 3), y=(8.6, 9.6)),
        mark('around_y', ('red', 2), y=(6.0, 6.6)),
        dots('around_y', [{'x': 8, 'y': 9.1}, {'z': 8, 'y': 9.1}], ('black', 1), 1)])
    b += ycyl('tail', 3.2, 2.2, 4.0, 'orange')
    b.append(B('fin_x', 3.6, 0.0, 7.7, 12.4, 3.4, 8.3, 'grey'))
    b.append(B('fin_z', 7.7, 0.0, 3.6, 8.3, 3.4, 12.4, 'grey'))
    b.append(B('fin_ring_n', 5.0, 0.4, 4.9, 11.0, 1.2, 5.3, 'grey'))
    b.append(B('fin_ring_s', 5.0, 0.4, 10.7, 11.0, 1.2, 11.1, 'grey'))
    return b


def m_ion_beacon():
    b = []
    b.append(B('base', 3.6, 0.4, 4.4, 12.4, 4.6, 11.6, 'gunmetal', decals=[
        lines('around_y', 'y', [1.4], 1), rivets('around_y', [{'x': 4.4, 'y': 3.8}, {'x': 11.6, 'y': 3.8},
                                                               {'z': 5.2, 'y': 3.8}, {'z': 10.8, 'y': 3.8}])]))
    b.append(B('panel_glow', 5.0, 1.6, 11.6, 11.0, 3.4, 11.8, 'ion', decals=[
        lines('back', 'x', [6.6, 8.2, 9.8], ('ion', 1))]))
    b.append(B('led', 4.4, 3.0, 11.6, 5.0, 3.6, 11.8, 'led'))
    b.append(C('mast', 1.2, 4.6, 11.6, 7.4, 8.6, 'steel', decals=[lines('around_y', 'y', [6.2, 8.4], 1)]))
    b += ycyl('emitter_ring', 3.4, 10.2, 11.0, 'gunmetal')
    b += ycyl('lens', 2.2, 11.0, 13.2, 'ion')
    b.append(B('antenna', 10.4, 4.6, 5.2, 10.8, 9.6, 5.6, 'steel'))
    b.append(B('antenna_led', 10.2, 9.6, 5.0, 11.0, 10.4, 5.8, 'led'))
    return b


def m_chemical():
    b = []
    label = [mark('around_y', ('toxic', 3), y=(2.0, 3.0)),
             mark('around_y', ('toxic', 3), y=(8.0, 9.0)),
             mark(('south', 'east'), ('warn', 3), y=(4.2, 7.0), x=(6.6, 9.4), z=(6.6, 9.4)),
             circle(('south',), {'x': 8, 'y': 5.6}, 0.9, ('black', 0)),
             circle(('east',), {'z': 8, 'y': 5.6}, 0.9, ('black', 0))]
    b += ycyl('body', 6.0, 0.6, 10.6, 'olive', decals=label)
    b += ycyl('shoulder', 4.4, 10.6, 11.6, 'olive')
    b += ycyl('valve', 1.6, 11.6, 13.0, 'brass', layers=2)
    b.append(B('wheel_x', 5.6, 13.0, 7.6, 10.4, 13.4, 8.4, 'red'))
    b.append(B('wheel_z', 7.6, 13.0, 5.6, 8.4, 13.4, 10.4, 'red'))
    return b


# =====================================================================================================
# launched ammunition (along z, nose towards -z, centred on 8, 8, 8): the item and the round in flight
# =====================================================================================================

def rocket_motor(z0):
    """Everything behind a rocket's warhead: the motor tube, four fins and the nozzle (ends at z = 19)."""
    b = []
    b += rod('motor', 1.6, 8.0, z0, 17.4, 'gunmetal', decals=[lines('around', 'z', [z0 + 1.2, 13.6], 0)])
    # stepped plates read as swept fins
    for r, f0 in ((1.9, 13.6), (2.7, 15.4)):
        b.append(B('fin_x', 8.0 - r, 7.92, f0, 8.0 + r, 8.08, 17.4, 'steel'))
        b.append(B('fin_y', 7.92, 8.0 - r, f0, 8.08, 8.0 + r, 17.4, 'steel'))
    b += rod('nozzle', 1.9, 8.0, 17.4, 19.0, 'black', k=0.72, decals=[circle('back', {'x': 8, 'y': 8}, 0.6)])
    return b


def m_rocket_pg7v():
    b = []
    b += rod('fuze', 0.7, 8.0, -3.0, -2.2, 'black')
    b += rod('nose1', 1.6, 8.0, -2.2, -0.8, 'khaki', k=0.72)
    b += rod('nose2', 2.6, 8.0, -0.8, 0.8, 'khaki', k=0.72)
    b += tube('warhead', 3.4, 8.0, 0.8, 6.2, 'khaki', decals=[mark('around', ('warn', 2), z=(1.4, 1.8)),
                                                               mark('around', ('black', 1), z=(5.0, 5.6))])
    b += rod('warhead_tail', 2.5, 8.0, 6.2, 7.8, 'khaki', k=0.72)
    b += rocket_motor(7.8)
    return b


def m_rocket_tbg():
    b = []
    b += rod('probe', 0.6, 8.0, -3.0, -0.5, 'steel')
    b += rod('cap', 2.2, 8.0, -0.5, 0.6, 'red', k=0.72)
    b += tube('body', 4.0, 8.0, 0.6, 8.4, 'red', decals=[mark('around', ('warn', 3), z=(2.0, 2.8)),
                                                          mark('around', ('black', 1), z=(7.2, 7.8))])
    b += rod('tail', 2.4, 8.0, 8.4, 9.8, 'red', k=0.72)
    b += rocket_motor(9.8)
    return b


def m_grenade_40mm():
    b = []
    b += rod('fuze', 2.8, 8.0, 2.9, 4.4, 'brass', k=0.72)
    b += tube('ogive', 4.0, 8.0, 4.4, 5.9, 'olive')
    b += tube('body', 4.6, 8.0, 5.9, 9.4, 'olive', decals=[mark('around', ('warn', 3), z=(6.6, 7.2))])
    b += tube('case', 4.8, 8.0, 9.4, 12.4, 'gunmetal', decals=[lines('around', 'z', [10.2], 0)])
    b += tube('rim', 5.2, 8.0, 12.4, 13.1, 'brass', decals=[circle('back', {'x': 8, 'y': 8}, 0.7, ('brass', 1))])
    return b


# ---- the F-14's stores (1 unit = 1/16 m unless the AMMO entry says otherwise): the item, the round in flight and
# the one hanging on the jet's pylon are all this geometry --------------------------------------------------------

def cross_fins(name, z0, z1, span, mat='missile', t=0.14, c=8.0):
    """Four fins in a + (a pair of crossed plates)."""
    return [B(name + '_h', c - span / 2, c - t / 2, z0, c + span / 2, c + t / 2, z1, mat),
            B(name + '_v', c - t / 2, c - span / 2, z0, c + t / 2, c + span / 2, z1, mat)]


def swept_fins(name, z_root, z_end, span, steps=3, mat='missile'):
    """Stepped cross plates that read as swept (delta) fins: longer chord near the body, shorter at the tip."""
    b = []
    for k in range(steps):
        f = (k + 1) / float(steps)
        z0 = z_root + (z_end - z_root) * (f - 1.0 / steps) * 0.85
        b += cross_fins('%s%d' % (name, k), z0, z_end, span * f, mat)
    return b


def m_aim9():
    """AIM-9 Sidewinder: seeker dome, grey guidance section with double-delta canards, warhead (yellow band), long
    motor (brown band) and the tail fins with their rollerons. 2.87 m."""
    b = []
    b += rod('dome', 1.5, 8.0, -15.0, -14.3, 'glass', k=0.72)
    b += rod('seeker', 2.0, 8.0, -14.3, -8.6, 'grey', k=0.72, decals=[mark('around', ('black', 1), z=(-9.1, -8.6))])
    b += swept_fins('canard', -12.8, -10.4, 6.2, steps=2, mat='grey')
    b += rod('warhead', 2.0, 8.0, -8.6, -4.6, 'missile', k=0.72, decals=[mark('around', ('warn', 3), z=(-7.6, -6.9))])
    b += rod('motor', 2.0, 8.0, -4.6, 29.0, 'missile', k=0.72,
             decals=[mark('around', ('brown', 2), z=(-3.8, -3.1)), lines('around', 'z', [10.0, 21.5], 1)])
    b += swept_fins('fin', 22.0, 28.6, 9.6, steps=3)
    for dx, dy in ((4.5, 0), (-4.5, 0), (0, 4.5), (0, -4.5)):
        b.append(B('rolleron', 8 + dx - 0.3, 8 + dy - 0.3, 27.4, 8 + dx + 0.3, 8 + dy + 0.3, 28.6, 'steel'))
    b += rod('nozzle', 1.6, 8.0, 29.0, 31.0, 'black', k=0.72, decals=[circle('back', {'x': 8, 'y': 8}, 0.5)])
    return b


def m_aim54():
    """AIM-54 Phoenix (1 unit = 1/12 m, 3.96 m): the big long-range missile with its ivory radome, long-chord wings
    and tail controls."""
    b = []
    b += rod('radome1', 1.4, 8.0, -16.0, -14.6, 'radome', k=0.72)
    b += rod('radome2', 2.8, 8.0, -14.6, -12.2, 'radome', k=0.72)
    b += tube('radome3', 4.0, 8.0, -12.2, -9.0, 'radome')
    b += tube('body', 4.6, 8.0, -9.0, 29.4, 'missile',
              decals=[mark('around', ('warn', 3), z=(-6.6, -5.6)), mark('around', ('brown', 2), z=(3.0, 4.0)),
                      lines('around', 'z', [-8.9, 12.0, 20.0], 1)])
    b += swept_fins('wing', 7.0, 19.0, 10.4, steps=3)
    b += swept_fins('tail', 22.5, 29.2, 11.0, steps=2)
    b += rod('nozzle', 3.0, 8.0, 29.4, 31.5, 'black', k=0.72, decals=[circle('back', {'x': 8, 'y': 8}, 0.9)])
    return b


def m_mk82():
    """Mk 82 500 lb low-drag bomb: nose fuze, olive body with the yellow high-explosive band, suspension lugs and the
    conical fin assembly. 2.21 m."""
    b = []
    b += rod('fuze', 0.9, 8.0, -13.0, -11.8, 'steel', k=0.72)
    b += rod('nose', 2.8, 8.0, -11.8, -9.6, 'olive', k=0.72)
    b += tube('ogive', 3.9, 8.0, -9.6, -6.0, 'olive', decals=[mark('around', ('warn', 3), z=(-8.8, -8.0))])
    b += tube('body', 4.4, 8.0, -6.0, 8.0, 'olive', decals=[mark('around', ('warn', 3), z=(-5.4, -4.7)),
                                                         lines('around', 'z', [2.0], 1)])
    b += tube('tail', 3.6, 8.0, 8.0, 12.0, 'olive')
    b += rod('cone', 2.6, 8.0, 12.0, 17.5, 'olive', k=0.72)
    b += swept_fins('fin', 14.0, 22.0, 6.4, steps=2, mat='olive')
    b.append(B('lug_f', 7.6, 10.1, -2.2, 8.4, 10.8, -1.4, 'steel'))
    b.append(B('lug_r', 7.6, 10.1, 5.2, 8.4, 10.8, 6.0, 'steel'))
    return b


def m_zuni():
    """Zuni 5-inch rocket: fuzed warhead (yellow band), white motor (brown band) and wrap-around fins. 2.79 m."""
    b = []
    b += rod('fuze', 0.8, 8.0, -15.0, -14.0, 'steel')
    b += rod('nose', 1.6, 8.0, -14.0, -11.6, 'olive', k=0.72)
    b += rod('warhead', 2.0, 8.0, -11.6, -4.0, 'olive', k=0.72, decals=[mark('around', ('warn', 3), z=(-9.0, -8.3))])
    b += rod('motor', 2.0, 8.0, -4.0, 26.5, 'missile', k=0.72,
             decals=[mark('around', ('brown', 2), z=(-3.2, -2.5)), lines('around', 'z', [11.0], 1)])
    b += cross_fins('fin', 23.6, 28.0, 4.4)
    b += rod('nozzle', 1.6, 8.0, 26.5, 29.6, 'black', k=0.72, decals=[circle('back', {'x': 8, 'y': 8}, 0.5)])
    return b


def m_cannon_shells():
    """A can of linked 20 mm rounds for the jet's M61 (upright)."""
    b = []
    b.append(B('can', 4.2, 0.4, 5.0, 11.8, 9.0, 11.0, 'olive', decals=[
        mark(('north', 'south'), ('warn', 3), y=(6.4, 7.2)), rivets('around_y', [{'x': 4.8, 'y': 8.2}, {'x': 11.2, 'y': 8.2}])]))
    b.append(B('lid', 4.0, 9.0, 4.8, 12.0, 9.8, 11.2, 'olive'))
    b.append(B('latch', 7.2, 7.6, 4.6, 8.8, 9.6, 5.0, 'steel'))
    b.append(B('handle', 6.2, 9.8, 7.6, 9.8, 10.6, 8.4, 'black'))
    # a length of linked rounds hanging over the rim
    for k in range(5):
        x = 4.9 + k * 1.35
        b.append(B('round_%d' % k, x, 9.8, 5.2, x + 0.9, 11.6, 6.1, 'brass'))
        b.append(B('tip_%d' % k, x + 0.15, 11.6, 5.35, x + 0.75, 12.4, 5.95, 'red'))
    return b


def m_flares():
    """A magazine of MJU-7 flare cartridges (upright): a grid of red end caps in a grey block."""
    b = []
    b.append(B('block', 4.0, 0.6, 5.0, 12.0, 8.6, 11.0, 'grey', decals=[lines('around_y', 'y', [2.0, 7.4], 1)]))
    for i in range(3):
        for j in range(2):
            x, z = 4.8 + i * 2.4, 5.8 + j * 2.6
            b.append(B('flare_%d%d' % (i, j), x, 8.6, z, x + 1.8, 9.4, z + 1.8, 'red'))
    return b


# =====================================================================================================
# catalogue
# =====================================================================================================
# kind: sets the first-person anchor, GUI angle and third-person handling. mm: millimetres per model unit (scales
# the gun so real sizes stay consistent). length_m: real length, for third person and the ground. muzzle: model
# point where the flash and tracers start. fp / tp: optional nudges (translation in units, rotation in degrees).

GUNS = {
    'ak47': dict(kind='rifle', parts=m_ak47, mm=19.1, length_m=0.88, muzzle=(8, 9.0, -15.0)),
    'm4a1': dict(kind='rifle', parts=m_m4a1, mm=19.1, length_m=0.84, muzzle=(8, 9.0, -13.0)),
    'scar_h': dict(kind='rifle', parts=m_scar_h, mm=20.5, length_m=0.96, muzzle=(8, 9.2, -15.0)),
    'aug': dict(kind='rifle', parts=m_aug, mm=19.3, length_m=0.79, muzzle=(8, 9.1, -12.0),
                fp=dict(shift=(-1.0, 0.5, -7.0))),
    'glock17': dict(kind='pistol', parts=m_glock17, mm=7.0, length_m=0.19, muzzle=(8, 15.4, -13.3)),
    'm1911': dict(kind='pistol', parts=m_m1911, mm=7.5, length_m=0.22, muzzle=(8, 15.5, -14.0)),
    'm9': dict(kind='pistol', parts=m_m9, mm=7.5, length_m=0.22, muzzle=(8, 15.05, -14.0)),
    'deagle': dict(kind='pistol', parts=m_deagle, mm=8.4, length_m=0.27, muzzle=(8, 15.5, -16.0)),
    'barrett_m82': dict(kind='sniper', parts=m_barrett, mm=30.4, length_m=1.45, muzzle=(8, 9.5, -16.0),
                        fp=dict(size=0.85)),
    'svd_dragunov': dict(kind='sniper', parts=m_svd, mm=26.0, length_m=1.22, muzzle=(8, 9.35, -16.0)),
    'awp': dict(kind='sniper', parts=m_awp, mm=25.0, length_m=1.18, muzzle=(8, 9.3, -16.0)),
    'remington_870': dict(kind='shotgun', parts=m_remington870, mm=21.0, length_m=0.97, muzzle=(8, 10.0, -15.0)),
    'spas12': dict(kind='shotgun', parts=m_spas12, mm=19.5, length_m=0.82, muzzle=(8, 10.2, -14.5)),
    'aa12': dict(kind='shotgun', parts=m_aa12, mm=21.0, length_m=0.97, muzzle=(8, 9.7, -15.0)),
    'sawed_off': dict(kind='shotgun', parts=m_sawed_off, mm=12.0, length_m=0.45, muzzle=(8, 12.0, -14.0),
                      fp=dict(size=1.35, shift=(0.0, 0.5, 0.0))),
    'rpg7': dict(kind='launcher', parts=m_rpg7, mm=28.0, length_m=1.30, muzzle=(8, 9.6, -1.2),
                 states={'': {'loaded': True, 'round': 'pg7v'}, '_tbg': {'loaded': True, 'round': 'tbg'},
                         '_empty': {'loaded': False}},
                 dispatch=('arsenal:round', [(2.0, '_tbg')])),
    'm32_launcher': dict(kind='launcher', parts=m_m32, mm=19.3, length_m=0.81, muzzle=(8, 10.2, -12.5)),
    'railgun': dict(kind='railgun', parts=m_railgun, mm=21.0, length_m=1.00, muzzle=(8, 9.8, -16.0),
                    states=dict([('', {'loaded': True, 'charge': 0})]
                                + [('_charge_%d' % k, {'loaded': True, 'charge': k}) for k in range(1, 6)]
                                + [('_empty', {'loaded': False, 'charge': 0})]),
                    # the first coil lights the moment the trigger goes down, then one per quarter, full = all hot
                    dispatch=('arsenal:charge', [(0.01, '_charge_1'), (0.25, '_charge_2'), (0.5, '_charge_3'),
                                                 (0.75, '_charge_4'), (1.0, '_charge_5')])),
}

THROWN = {
    'frag_grenade': dict(kind='thrown', parts=m_frag, height_m=0.11),
    'incendiary_grenade': dict(kind='thrown', parts=m_incendiary, height_m=0.14),
    'flashbang': dict(kind='thrown', parts=m_flashbang, height_m=0.13),
    'smoke_grenade': dict(kind='thrown', parts=m_smoke, height_m=0.15),
    'singularity_charge': dict(kind='thrown', parts=m_singularity, height_m=0.22),
    'thermobaric_bomb': dict(kind='thrown', parts=m_thermobaric, height_m=0.30),
    'ion_cannon_beacon': dict(kind='thrown', parts=m_ion_beacon, height_m=0.22),
    'chemical_warhead': dict(kind='thrown', parts=m_chemical, height_m=0.24),
}

# hand_m: how long the round looks in the hand and on the ground (blocks)
AMMO = {
    'rocket_round': dict(kind='projectile', parts=m_rocket_pg7v, hand_m=0.62),
    'rocket_thermobaric': dict(kind='projectile', parts=m_rocket_tbg, hand_m=0.62),
    'grenade_40mm': dict(kind='projectile', parts=m_grenade_40mm, hand_m=0.2),
    # the F-14's stores; unit_m = metres per model unit (the jet mesh and the in-flight render use it)
    'aim9_sidewinder': dict(kind='projectile', parts=m_aim9, hand_m=1.0, unit_m=1 / 16.0),
    'aim54_phoenix': dict(kind='projectile', parts=m_aim54, hand_m=1.2, unit_m=1 / 12.0),
    'mk82_bomb': dict(kind='projectile', parts=m_mk82, hand_m=0.9, unit_m=1 / 16.0),
    'zuni_rocket': dict(kind='projectile', parts=m_zuni, hand_m=1.0, unit_m=1 / 16.0),
    'cannon_shells_20mm': dict(kind='thrown', parts=m_cannon_shells, height_m=0.3),
    'flare_cartridges': dict(kind='thrown', parts=m_flares, height_m=0.22),
}

MODELS = dict(GUNS)
MODELS.update(THROWN)
MODELS.update(AMMO)


# =====================================================================================================
# maths: rotations and affine transforms
# =====================================================================================================

def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def transpose(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


def rx(d):
    r = math.radians(d)
    c, s = math.cos(r), math.sin(r)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(d):
    r = math.radians(d)
    c, s = math.cos(r), math.sin(r)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(d):
    r = math.radians(d)
    c, s = math.cos(r), math.sin(r)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


IDENTITY = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def euler_xyz(m):
    """Decompose M = Rx(a) Ry(b) Rz(c) (what the game builds from a display 'rotation')."""
    b = math.asin(max(-1.0, min(1.0, m[0][2])))
    a = math.atan2(-m[1][2], m[2][2])
    c = math.atan2(-m[0][1], m[0][0])
    return [round(math.degrees(a), 2), round(math.degrees(b), 2), round(math.degrees(c), 2)]


def euler_matrix(r):
    return mat_mul(rx(r[0]), mat_mul(ry(r[1]), rz(r[2])))


class Xf:
    """An affine transform p -> m p + t (m may include uniform scale)."""

    def __init__(self, m=None, t=(0.0, 0.0, 0.0)):
        self.m = m or IDENTITY
        self.t = list(t)

    def __matmul__(self, other):
        return Xf(mat_mul(self.m, other.m), [a + b for a, b in zip(apply(self.m, other.t), self.t)])

    def __call__(self, p):
        return [a + b for a, b in zip(apply(self.m, p), self.t)]


def T(x, y, z):
    return Xf(IDENTITY, (x, y, z))


def S(x, y=None, z=None):
    y = x if y is None else y
    z = x if z is None else z
    return Xf([[x, 0, 0], [0, y, 0], [0, 0, z]])


def R(m):
    return Xf(m)


def display_xf(entry):
    """The game's ItemTransform: T(translation/16) R(rotation) S(scale) T(-0.5), applied to model units / 16."""
    rot, tr, sc = entry['rotation'], entry['translation'], entry['scale'][0]
    return T(*[v / 16.0 for v in tr]) @ R(euler_matrix(rot)) @ S(sc) @ T(-0.5, -0.5, -0.5) @ S(1 / 16.0)


def rotate_point(box, p):
    if not box.rot:
        return list(p)
    kind, angle, origin = box.rot
    if kind == 'euler':
        m = mat_mul(rz(angle[2]), mat_mul(ry(angle[1]), rx(angle[0])))
    else:
        m = {'x': rx, 'y': ry, 'z': rz}[kind](angle)
    d = [p[i] - origin[i] for i in range(3)]
    r = apply(m, d)
    return [r[i] + origin[i] for i in range(3)]


def corners(box):
    (x0, y0, z0), (x1, y1, z1) = box.frm, box.to
    return [rotate_point(box, [x, y, z]) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]


def box_center(box):
    return rotate_point(box, [(box.frm[i] + box.to[i]) / 2.0 for i in range(3)])


# ---- the first-person hand and the third-person arm (FirstPersonHandsAndItemsRenderer, ItemInHandLayer) -----------

ARM = (0.56, -0.52, -0.72)
BODY = R(ry(180.0)) @ S(-1.0, -1.0, 1.0) @ S(0.9375) @ T(0.0, -1.501, 0.0)
RIGHT_ARM_PIVOT = (-5.0, 2.0, 0.0)
LEFT_ARM_PIVOT = (5.0, 2.0, 0.0)
POSES = {
    # (xRot, yRot, zRot) in radians for the right arm and the left arm, head level (HumanoidModel/AnimationUtils)
    'crossbow': ((-math.pi / 2 + 0.1, -0.3, 0.0), (-1.5, 0.6, 0.0)),
    'item': ((-math.pi / 10, 0.0, 0.0), (0.0, 0.0, 0.0)),
}


def part_xf(pivot, rot):
    xr, yr, zr = rot
    m = mat_mul(rz(math.degrees(zr)), mat_mul(ry(math.degrees(yr)), rx(math.degrees(xr))))
    return T(pivot[0] / 16.0, pivot[1] / 16.0, pivot[2] / 16.0) @ R(m)


def hand_frame(pose):
    """Model space of the player -> the right hand's item frame (before the item's own display transform)."""
    return (BODY @ part_xf(RIGHT_ARM_PIVOT, POSES[pose][0]) @ R(rx(-90.0)) @ R(ry(180.0))
            @ T(1 / 16.0, 2 / 16.0, -10 / 16.0))


# =====================================================================================================
# display transforms
# =====================================================================================================

# where the grip sits in first person (view space, blocks), per kind; the AK's approved placement defines 'rifle'
FP_ANCHOR = {
    'rifle': (0.491, -0.4587, -0.5596),
    'sniper': (0.491, -0.4587, -0.5596),
    'shotgun': (0.491, -0.4587, -0.5596),
    'railgun': (0.491, -0.4587, -0.5596),
    'launcher': (0.47, -0.40, -0.52),
    'pistol': (0.37, -0.43, -0.78),
    'thrown': (0.34, -0.28, -0.62),
}
FP_SIZE = {'pistol': 1.9}           # pistols read too small at true scale
GUI_TILT = {'pistol': 20.0, 'projectile': 45.0}
HELD_LIKE_ITEMS = ('thrown', 'projectile')


def extent(boxes, m=IDENTITY):
    pts = [apply(m, [(c[i] - 8.0) for i in range(3)]) for box in boxes for c in corners(box)]
    return [(min(p[i] for p in pts), max(p[i] for p in pts)) for i in range(3)]


def fit(boxes, m, size):
    """Scale + translation (units) that centre the model in a size x size square when rotated by m."""
    (x0, x1), (y0, y1), _ = extent(boxes, m)
    scale = size / max(x1 - x0, y1 - y0)
    return round(scale, 4), [round(-(x1 + x0) / 2 * scale, 3), round(-(y1 + y0) / 2 * scale, 3), 0.0]


def anchor_point(name, boxes):
    spec = MODELS[name]
    if spec['kind'] in HELD_LIKE_ITEMS:
        (x0, x1), (y0, y1), (z0, z1) = extent(boxes)
        return [8 + (x0 + x1) / 2, 8 + (y0 + y1) / 2, 8 + (z0 + z1) / 2]
    grip = [b for b in boxes if b.name == 'grip'] or [b for b in boxes if b.name == 'wrist']
    return box_center(grip[0])


def solve_translation(rot_m, scale, point, target):
    """Translation (units) so that model point `point` lands on `target` (frame blocks)."""
    q = [(point[i] / 16.0 - 0.5) * scale for i in range(3)]
    r = apply(rot_m, q)
    return [round((target[i] - r[i]) * 16.0, 3) for i in range(3)]


def first_person(name, boxes):
    spec = MODELS[name]
    kind = spec['kind']
    fp = spec.get('fp', {})
    # a round is held like a torch: nose up, leaning forward and in towards the middle of the screen
    default_rot = {'thrown': (8.0, -28.0, 0.0), 'projectile': (58.0, 24.0, 0.0)}.get(kind, (0.0, 3.0, 0.0))
    rot = fp.get('rotation', default_rot)
    if kind == 'thrown':
        (_, _), (y0, y1), _ = extent(boxes)
        scale = 0.34 * 16.0 / (y1 - y0) * (spec['height_m'] / 0.14) ** 0.5
    elif kind == 'projectile':
        _, _, (z0, z1) = extent(boxes)
        scale = spec['hand_m'] * 0.8 * 16.0 / (z1 - z0)
    else:
        scale = 0.58 * spec['mm'] / 19.1 * FP_SIZE.get(kind, 1.0)
    scale *= fp.get('size', 1.0)
    anchor = FP_ANCHOR['thrown' if kind == 'projectile' else kind]
    shift = fp.get('shift', (0.0, 0.0, 0.0))
    target = [anchor[i] - ARM[i] + shift[i] / 16.0 for i in range(3)]
    t = solve_translation(euler_matrix(rot), scale, anchor_point(name, boxes), target)
    return {'rotation': [round(v, 2) for v in rot], 'translation': t, 'scale': [round(scale, 4)] * 3}


def third_person(name, boxes):
    """Gun: crossbow-hold pose, barrel pointing where the player looks, grip in the fist. Throwable: in the hand."""
    spec = MODELS[name]
    kind = spec['kind']
    tp = spec.get('tp', {})
    pose = 'item' if kind in HELD_LIKE_ITEMS else 'crossbow'
    frame = hand_frame(pose)
    c = [[v / 0.9375 for v in row] for row in frame.m]          # the arm chain's rotation, scale removed
    target = ry(180.0)                                           # model -z -> forward, +y -> up
    rot_m = mat_mul(transpose(c), target)
    rot = euler_xyz(rot_m)
    rot_m = euler_matrix(rot)
    (x0, x1), (y0, y1), (z0, z1) = extent(boxes)
    if kind == 'thrown':
        scale = spec['height_m'] * 1.6 * 16.0 / (y1 - y0)
        point = anchor_point(name, boxes)
        target_pt = [0.0, 0.05, -0.02]
    elif kind == 'projectile':
        scale = spec['hand_m'] * 16.0 / (z1 - z0)
        point = anchor_point(name, boxes)
        target_pt = [0.0, 0.05, -0.02]
    else:
        scale = spec['length_m'] * (1.6 if kind == 'pistol' else 1.35) * 16.0 / (z1 - z0)
        point = anchor_point(name, boxes)
        target_pt = [0.0, 0.0, 0.0]
    shift = tp.get('shift', (0.0, 0.0, 0.0))
    target_pt = [target_pt[i] + shift[i] / 16.0 for i in range(3)]
    t = solve_translation(rot_m, scale, point, target_pt)
    return {'rotation': rot, 'translation': t, 'scale': [round(scale, 4)] * 3}


def gui_matrix(kind):
    if kind == 'thrown':
        return mat_mul(rx(22.0), ry(-32.0))
    tilt = GUI_TILT.get(kind, 40.0)
    # right side towards the viewer, muzzle up to the right, the top rolled a little towards you
    return mat_mul(rz(tilt), mat_mul(rx(22.0 if kind != 'pistol' else 14.0), ry(-90.0)))


def display(name, boxes):
    spec = MODELS[name]
    kind = spec['kind']
    gm = gui_matrix(kind)
    gui_scale, gui_t = fit(boxes, gm, 15.5 if kind == 'thrown' else 17.0)
    # item frames look at the model's north face at identity, i.e. from behind compared to the GUI
    fixed_m = mat_mul(ry(180.0), gm) if kind == 'thrown' else \
        mat_mul(ry(180.0), mat_mul(rz(GUI_TILT.get(kind, 40.0)), mat_mul(rx(10.0), ry(-90.0))))
    fixed_scale, fixed_t = fit(boxes, mat_mul(ry(180.0), fixed_m), 15.0)
    fixed_t = [-fixed_t[0], fixed_t[1], 0.0]
    (x0, x1), (y0, y1), (z0, z1) = extent(boxes)
    if kind == 'thrown':
        ground_scale = 0.26 / 1.1 * 16.0 / (y1 - y0)
        ground = {'rotation': [15.0, 0.0, 0.0], 'translation': [0.0, 2.0, 0.0], 'scale': [round(ground_scale, 4)] * 3}
    elif kind == 'projectile':
        ground_scale = spec['hand_m'] * 16.0 / (z1 - z0)
        ground = {'rotation': [0.0, 0.0, 0.0], 'translation': [0.0, 2.0, 0.0], 'scale': [round(ground_scale, 4)] * 3}
    else:
        length = min(1.1, max(0.5, spec['length_m']))
        ground_scale = length * 16.0 / (z1 - z0)
        ground = {'rotation': [0.0, 0.0, 0.0], 'translation': [0.0, 2.0, 0.0], 'scale': [round(ground_scale, 4)] * 3}
    gui = {'rotation': euler_xyz(gm), 'translation': gui_t, 'scale': [gui_scale] * 3}
    fixed = {'rotation': euler_xyz(fixed_m), 'translation': fixed_t, 'scale': [fixed_scale] * 3}
    return {
        'firstperson_righthand': first_person(name, boxes),
        'thirdperson_righthand': third_person(name, boxes),
        'ground': ground,
        'gui': gui,
        'fixed': fixed,
        'on_shelf': fixed,
    }


# =====================================================================================================
# atlas packing + model JSON
# =====================================================================================================

_CACHE = {}


def build(name):
    if name in _CACHE:
        return _CACHE[name]
    boxes = flatten(MODELS[name]['parts']())
    for box in boxes:
        for axis in range(3):
            for p in corners(box):
                if not -16.0 <= p[axis] <= 32.0:
                    raise ValueError('%s/%s leaves the -16..32 model space: %s' % (name, box.name, p))
    painted = []
    for box in boxes:
        for face in box.faces:
            painted.append((box, face, paint(box, face)))
    area = sum(img.w * img.h for _, _, img in painted)
    width = 128 if area < 128 * 100 else 256
    order = sorted(range(len(painted)), key=lambda i: (-painted[i][2].h, -painted[i][2].w))
    x = y = shelf = 0
    place = {}
    for i in order:
        img = painted[i][2]
        if x + img.w > width:
            x, y, shelf = 0, y + shelf, 0
        place[i] = (x, y)
        x += img.w
        shelf = max(shelf, img.h)
    height = 16
    while height < y + shelf:
        height *= 2
    atlas = Img(width, height)
    for i, (box, face, img) in enumerate(painted):
        atlas.paste(img, *place[i])
    uvs = {}
    for i, (box, face, img) in enumerate(painted):
        px, py = place[i]
        uvs.setdefault(id(box), {})[face] = [round(px * 16.0 / width, 4), round(py * 16.0 / height, 4),
                                             round((px + img.w) * 16.0 / width, 4),
                                             round((py + img.h) * 16.0 / height, 4)]
    result = (boxes, atlas, uvs, painted, place)
    _CACHE[name] = result
    return result


def states(name):
    """Variant suffix -> state, '' first: {'': {}} for a plain item, loaded + '_empty' when some boxes only show
    while loaded / empty, or the item's own list."""
    spec = MODELS[name]
    if 'states' in spec:
        return spec['states']
    boxes = build(name)[0]
    return {'': {'loaded': True}, '_empty': {'loaded': False}} if any(b.when for b in boxes) else {'': {}}


def variants(name):
    return list(states(name))


def visible(box, name, variant):
    when = box.when
    if when is None:
        return True
    state = states(name)[variant]
    if when == 'loaded':
        return state.get('loaded', True)
    if when == 'empty':
        return not state.get('loaded', True)
    return when(state)


def base_boxes(name):
    """The boxes of the default model; every variant uses the display transforms fitted to these."""
    return [b for b in build(name)[0] if visible(b, name, '')]


def model_json(name, variant=''):
    boxes, atlas, uvs, _, _ = build(name)
    tex = 'arsenal:item/' + name
    elements = []
    for box in boxes:
        if not visible(box, name, variant):
            continue
        el = {'name': box.name, 'from': [round(v, 4) for v in box.frm], 'to': [round(v, 4) for v in box.to],
              'faces': {face: {'uv': uv, 'texture': '#gun'} for face, uv in uvs[id(box)].items()}}
        if box.rot:
            kind, angle, origin = box.rot
            if kind == 'euler':
                el['rotation'] = {'x': angle[0], 'y': angle[1], 'z': angle[2], 'origin': list(origin)}
            else:
                el['rotation'] = {'angle': angle, 'axis': kind, 'origin': list(origin)}
        if box.glow:
            el['light_emission'] = 15
            el['shade'] = False
        elements.append(el)
    return {
        'texture_size': [atlas.w, atlas.h],
        'textures': {'gun': tex, 'particle': tex},
        'elements': elements,
        'display': display(name, base_boxes(name)),
    }


def item_definition(name):
    def model(variant):
        return {'type': 'minecraft:model', 'model': 'arsenal:item/' + name + variant}
    if variants(name) == ['']:
        return {'model': model('')}
    loaded = model('')
    if 'dispatch' in MODELS[name]:
        prop, entries = MODELS[name]['dispatch']
        loaded = {'type': 'minecraft:range_dispatch', 'property': prop,
                  'entries': [{'threshold': threshold, 'model': model(v)} for threshold, v in entries],
                  'fallback': model('')}
    return {'model': {'type': 'minecraft:condition', 'property': 'arsenal:loaded', 'on_true': loaded,
                      'on_false': model('_empty')}}


def write_models(name, models_dir, items_dir):
    """Writes models/item/<name><variant>.json and items/<name>.json; returns the number of files."""
    count = 0
    for variant in variants(name):
        path = os.path.join(models_dir, name + variant + '.json')
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(model_json(name, variant), f, indent=1)
            f.write('\n')
        count += 1
    with open(os.path.join(items_dir, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(item_definition(name), f, indent=2)
        f.write('\n')
    return count + 1


def write_texture(name, path):
    atlas = build(name)[1]
    os.makedirs(os.path.dirname(path), exist_ok=True)
    atlas.save(path)


# =====================================================================================================
# the muzzle for the Java side (client/GunViewmodels.java)
# =====================================================================================================

def first_person_muzzle(name):
    boxes = base_boxes(name)
    xf = T(*ARM) @ display_xf(first_person(name, boxes))
    return xf(MODELS[name]['muzzle'])


def third_person_muzzle(name):
    """Muzzle and right shoulder in player space (x = left, y = up, z = forward, feet at 0), head level."""
    boxes = base_boxes(name)
    xf = hand_frame('crossbow') @ display_xf(third_person(name, boxes))
    shoulder = BODY([RIGHT_ARM_PIVOT[0] / 16.0, RIGHT_ARM_PIVOT[1] / 16.0, RIGHT_ARM_PIVOT[2] / 16.0])
    return xf(MODELS[name]['muzzle']), shoulder


def java_name(name):
    return {'barrett_m82': 'BARRETT', 'svd_dragunov': 'SVD', 'remington_870': 'REMINGTON870',
            'm32_launcher': 'M32'}.get(name, name.upper())


def write_java(path):
    lines_ = []
    shoulder = None
    for name in GUNS:
        fp = first_person_muzzle(name)
        tp, shoulder = third_person_muzzle(name)
        off = [tp[i] - shoulder[i] for i in range(3)]
        lines_.append('            Map.entry(GunType.%s, new Muzzle(new Vec3(%.4f, %.4f, %.4f), new Vec3(%.4f, %.4f, %.4f)))'
                      % (java_name(name), fp[0], fp[1], fp[2], off[0], off[1], off[2]))
    src = '''package com.afjan.arsenal.client;

import java.util.Map;

import com.afjan.arsenal.gun.GunType;

import net.minecraft.world.phys.Vec3;

/**
 * Where each gun's muzzle is, measured on its 3D model. GENERATED by tools/gun_models.py (gen_data.py writes it):
 * edit the models there, not this file.
 */
public final class GunViewmodels {
    /**
     * @param firstPerson in first-person view space (blocks; x right, y up, -z forward) for the right hand
     * @param fromShoulder third person, from the right shoulder pivot with the head level (player space: x left,
     *                     y up, z forward)
     */
    public record Muzzle(Vec3 firstPerson, Vec3 fromShoulder) {}

    /** The right shoulder pivot in player space (feet at the origin, facing +z). */
    public static final Vec3 SHOULDER = new Vec3(%.4f, %.4f, %.4f);

    public static final Map<GunType, Muzzle> MUZZLES = Map.ofEntries(
%s);

    private GunViewmodels() {}
}
''' % (shoulder[0], shoulder[1], shoulder[2], ',\n'.join(lines_))
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(src)


# =====================================================================================================
# software renderer (previews)
# =====================================================================================================

FACE_CORNERS = {
    # corners in (u, v) order: top-left, top-right, bottom-right, bottom-left, as seen from outside
    'north': lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
    'south': lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], b[2])],
    'east': lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], a[2]), (b[0], a[1], b[2])],
    'west': lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
    'up': lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])],
    'down': lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
}


class Canvas:
    def __init__(self, w, h, bg):
        self.img = Img(w, h, bg)
        self.z = [[1e9] * w for _ in range(h)]
        self.w, self.h = w, h


def shade_of(quad, light):
    e1 = [quad[1][k] - quad[0][k] for k in range(3)]
    e2 = [quad[3][k] - quad[0][k] for k in range(3)]
    n = [e2[1] * e1[2] - e2[2] * e1[1], e2[2] * e1[0] - e2[0] * e1[2], e2[0] * e1[1] - e2[1] * e1[0]]
    nl = math.sqrt(sum(c * c for c in n)) or 1.0
    return 0.5 + 0.5 * max(0.0, sum(n[k] / nl * light[k] for k in range(3)))


def draw_model(canvas, name, variant, to_view, project, light=(0.35, 0.8, 0.5)):
    boxes, atlas, uvs, painted, place = build(name)
    ln = math.sqrt(sum(c * c for c in light))
    light = [c / ln for c in light]
    for box, face, fimg in painted:
        if not visible(box, name, variant):
            continue
        quad = [to_view(rotate_point(box, c)) for c in FACE_CORNERS[face](box.frm, box.to)]
        shade = 1.0 if box.glow else shade_of(quad, light)
        proj = [project(p) for p in quad]
        if any(p is None for p in proj):
            continue
        uv = [(0, 0), (fimg.w, 0), (fimg.w, fimg.h), (0, fimg.h)]
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster(canvas, [proj[t] for t in tri], [uv[t] for t in tri], fimg, shade)


def draw_flat_box(canvas, corners8, color, to_view, project, light=(0.35, 0.8, 0.5)):
    """A plain coloured box given its 8 corners in (x0/x1, y0/y1, z0/z1) order (player model parts)."""
    ln = math.sqrt(sum(c * c for c in light))
    light = [c / ln for c in light]
    idx = {(i, j, k): (i * 4 + j * 2 + k) for i in (0, 1) for j in (0, 1) for k in (0, 1)}
    faces = [
        [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)], [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
        [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)], [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
        [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
    ]
    tex = Img(1, 1, color)
    for face in faces:
        quad = [to_view(corners8[idx[c]]) for c in face]
        proj = [project(p) for p in quad]
        if any(p is None for p in proj):
            continue
        shade = 0.55 + 0.45 * abs(shade_of(quad, light) * 2 - 1)
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster(canvas, [proj[t] for t in tri], [(0, 0)] * 3, tex, shade)


def raster(canvas, pts, uvs, tex, shade):
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = pts
    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return
    minx, maxx = max(0, int(min(x0, x1, x2))), min(canvas.w - 1, int(max(x0, x1, x2)) + 1)
    miny, maxy = max(0, int(min(y0, y1, y2))), min(canvas.h - 1, int(max(y0, y1, y2)) + 1)
    img, zb = canvas.img, canvas.z
    for y in range(miny, maxy + 1):
        sy = y + 0.5
        for x in range(minx, maxx + 1):
            sx = x + 0.5
            w0 = ((x1 - sx) * (y2 - sy) - (x2 - sx) * (y1 - sy)) / area
            w1 = ((x2 - sx) * (y0 - sy) - (x0 - sx) * (y2 - sy)) / area
            w2 = 1.0 - w0 - w1
            if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                continue
            z = w0 * z0 + w1 * z1 + w2 * z2
            if z >= zb[y][x]:
                continue
            u = w0 * uvs[0][0] + w1 * uvs[1][0] + w2 * uvs[2][0]
            v = w0 * uvs[0][1] + w1 * uvs[1][1] + w2 * uvs[2][1]
            c = tex.get(min(tex.w - 1, max(0, int(u))), min(tex.h - 1, max(0, int(v))))
            if not c[3]:
                continue
            zb[y][x] = z
            img.set(x, y, (min(255, int(c[0] * shade)), min(255, int(c[1] * shade)), min(255, int(c[2] * shade)), 255))


def ortho(scale, w, h):
    return lambda p: (w / 2 + p[0] * scale, h / 2 - p[1] * scale, -p[2])


def perspective(fov_deg, w, h):
    f = 1.0 / math.tan(math.radians(fov_deg) / 2)
    aspect = w / h

    def proj(p):
        if p[2] > -0.05:
            return None
        return (w / 2 + (p[0] * f / aspect) / -p[2] * w / 2, h / 2 - (p[1] * f) / -p[2] * h / 2, -p[2])
    return proj


def model_units(xf):
    """Wrap a transform that expects model units / 16 as blocks."""
    return lambda p: xf(p)


def view_gui(name, variant='', size=96):
    disp = display(name, base_boxes(name))['gui']
    canvas = Canvas(size, size, (139, 139, 139, 255))
    xf = display_xf(disp)
    draw_model(canvas, name, variant, xf, ortho(size, size, size), light=(0.2, 0.9, 0.7))
    return canvas.img


def view_side(name, variant='', w=300, h=150):
    boxes = base_boxes(name)
    (x0, x1), (y0, y1), (z0, z1) = extent(boxes, ry(-90.0))
    scale = min((w - 16) / (x1 - x0), (h - 16) / (y1 - y0))
    canvas = Canvas(w, h, (170, 190, 205, 255))
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    xf = R(ry(-90.0)) @ T(-8.0, -8.0, -8.0)
    draw_model(canvas, name, variant, lambda p: [a - b for a, b in zip(xf(p), (cx, cy, 0))],
               ortho(scale, w, h))
    return canvas.img


def view_first_person(name, variant='', w=400, h=225):
    boxes = base_boxes(name)
    xf = T(*ARM) @ display_xf(first_person(name, boxes))
    canvas = Canvas(w, h, (120, 160, 210, 255))
    proj = perspective(70, w, h)
    draw_model(canvas, name, variant, xf, proj)
    for d in range(-5, 6):
        canvas.img.set(w // 2 + d, h // 2, (255, 255, 255, 255))
        canvas.img.set(w // 2, h // 2 + d, (255, 255, 255, 255))
    if name in GUNS:
        mz = proj(first_person_muzzle(name))
        if mz:
            for dx in range(-2, 3):
                for dy in range(-2, 3):
                    canvas.img.set(int(mz[0]) + dx, int(mz[1]) + dy, (255, 200, 40, 255))
    return canvas.img


PLAYER_PARTS = [
    # name, pivot, cube from, size, colour
    ('head', (0, 0, 0), (-4, -8, -4), (8, 8, 8), (196, 150, 110, 255)),
    ('body', (0, 0, 0), (-4, 0, -2), (8, 12, 4), (40, 120, 150, 255)),
    ('right_leg', (-1.9, 12, 0), (-2, 0, -2), (4, 12, 4), (50, 50, 120, 255)),
    ('left_leg', (1.9, 12, 0), (-2, 0, -2), (4, 12, 4), (50, 50, 120, 255)),
]


def view_third_person(name, variant='', w=260, h=260):
    kind = MODELS[name]['kind']
    pose = 'item' if kind in HELD_LIKE_ITEMS else 'crossbow'
    boxes = base_boxes(name)
    canvas = Canvas(w, h, (185, 205, 185, 255))
    # camera: front-right of the player, a little above; orthographic
    cam = ry(-38.0)
    view_m = mat_mul(rx(12.0), transpose(cam))
    centre = (0.0, 1.05, 0.25)

    def to_view(p):
        return apply(view_m, [p[i] - centre[i] for i in range(3)])

    scale = h / 2.3
    proj = ortho(scale, w, h)
    parts = list(PLAYER_PARTS)
    right_rot, left_rot = POSES[pose]
    arms = [('right_arm', RIGHT_ARM_PIVOT, (-3, -2, -2), (4, 12, 4), (196, 150, 110, 255), right_rot),
            ('left_arm', LEFT_ARM_PIVOT, (-1, -2, -2), (4, 12, 4), (196, 150, 110, 255), left_rot)]
    for pname, pivot, frm, size, color in parts:
        xf = BODY @ T(pivot[0] / 16.0, pivot[1] / 16.0, pivot[2] / 16.0)
        cs = [xf([(frm[0] + i * size[0]) / 16.0, (frm[1] + j * size[1]) / 16.0, (frm[2] + k * size[2]) / 16.0])
              for i in (0, 1) for j in (0, 1) for k in (0, 1)]
        draw_flat_box(canvas, cs, color, to_view, proj)
    for pname, pivot, frm, size, color, rot in arms:
        xf = BODY @ part_xf(pivot, rot)
        cs = [xf([(frm[0] + i * size[0]) / 16.0, (frm[1] + j * size[1]) / 16.0, (frm[2] + k * size[2]) / 16.0])
              for i in (0, 1) for j in (0, 1) for k in (0, 1)]
        draw_flat_box(canvas, cs, color, to_view, proj)
    item_xf = hand_frame(pose) @ display_xf(third_person(name, boxes))
    draw_model(canvas, name, variant, lambda p: to_view(item_xf(p)), proj)
    return canvas.img


def sheet(images, cols, pad=6, bg=(40, 40, 44, 255)):
    rows = (len(images) + cols - 1) // cols
    cw = max(i.w for i in images)
    ch = max(i.h for i in images)
    out = Img(cols * (cw + pad) + pad, rows * (ch + pad) + pad, bg)
    for n, img in enumerate(images):
        out.paste(img, pad + (n % cols) * (cw + pad), pad + (n // cols) * (ch + pad))
    return out


def main():
    # 'railgun@_charge_3' previews one variant; 'railgun@' every variant of the item
    picks = []
    for arg in sys.argv[1:]:
        name, _, variant = arg.partition('@')
        if name in MODELS:
            picks += [(name, v) for v in variants(name)] if arg.endswith('@') else [(name, variant)]
    picks = picks or [(n, '') for n in MODELS]
    os.makedirs(BUILD, exist_ok=True)
    sheet([view_gui(n, v) for n, v in picks], cols=9).save(os.path.join(BUILD, 'models_gui.png'))
    sheet([view_side(n, v) for n, v in picks], cols=4).save(os.path.join(BUILD, 'models_side.png'))
    sheet([view_first_person(n, v) for n, v in picks], cols=4).save(os.path.join(BUILD, 'models_fp.png'))
    sheet([view_third_person(n, v) for n, v in picks], cols=6).save(os.path.join(BUILD, 'models_tp.png'))
    for n in dict(picks):
        if n in GUNS:
            fp = first_person_muzzle(n)
            print('%-18s fp muzzle %.3f %.3f %.3f   fp %s' % (n, fp[0], fp[1], fp[2],
                                                               json.dumps(first_person(n, base_boxes(n)))))
    print('sheets written to build/models_*.png')


if __name__ == '__main__':
    main()

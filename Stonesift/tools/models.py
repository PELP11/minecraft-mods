#!/usr/bin/env python3
"""3D models for Stonesift (adapted from Drillworks): the Mining Drill (body, drill heads, fuel gauge), the canisters and the refinery blocks.

Everything is built from boxes (cuboid model elements), painted per face into one atlas per model, and written as
model JSON. A small software renderer draws a preview sheet so the look can be tuned without the game:
  python3 tools/models.py            -> build/models_preview.png

Units: 16 = one block. Element coordinates must stay inside -16..32 (rotated corners included).
Vehicle parts are designed in blocks (vehicle space: x right, y up from the ground, z backwards; the drill points
to -z) and converted with V(): x_m = 8 + 16x, y_m = -16 + 16y, z_m = 8 + 16z. Rendered with ItemDisplayContext.NONE a
model point p lands at p/16 - 0.5, so the renderer lifts the body by 1.5 blocks.
Drill heads point to -z and are centred on (8, 8, 8); their base (the mount) is at z = 17.
"""
import json
import math
import os
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from pixels import Img, hexc, mix  # noqa: E402

BUILD = os.path.normpath(os.path.join(HERE, '..', 'build'))
INSET = 0.06


def ramp(*hexes):
    return [hexc(h) for h in hexes]


# material -> (kind, palette dark..light)

MATERIALS = {
    'wood': ('brick', ramp('3b2614', '5a3c20', '7a5430', '96693e', 'b3844f')),
    'plank': ('metal', ramp('4a3218', '6e4c28', '94683a', 'b08048', 'c89a5e')),
    'darkwood': ('metal', ramp('24170c', '382414', '4c321c', '604028', '785034')),
    'stone': ('metal', ramp('3c3c3c', '5a5a5a', '747474', '8c8c8c', 'a8a8a8')),
    'darkstone': ('metal', ramp('1c1c1e', '2c2c30', '3c3c42', '4e4e56', '64646c')),
    'steel': ('metal', ramp('2c3035', '4a5058', '6e757e', '949ca5', 'c3cad1')),
    'dark': ('metal', ramp('141619', '22262b', '33383e', '474d55', '626973')),
    'copper': ('metal', ramp('4a2410', '7a3e1e', 'b0602f', 'd98652', 'f2b184')),
    'orange': ('paint', ramp('5a2a06', '8e4410', 'c8641c', 'e8842e', 'ffb060')),
    'blue': ('paint', ramp('0e2440', '1a3c68', '2a5a96', '3e78bc', '6ea0e0')),
    'glass': ('glass', ramp('1c2a30', '2b4450', '3f6878', '6c9fb0', 'b8e2ee')),
    'foam': ('paint', ramp('9aa8a8', 'b8c4c4', 'd4dede', 'e8f0f0', 'ffffff')),
    'water': ('water', ramp('10306a', '1a4a90', '2a64b4', '4a86d0', '8ab8f0')),
    'mesh1': ('mesh', ramp('6e6250', '9a8c74', 'c8baa0', 'e0d6c0', 'f4eee0')),
    'mesh2': ('mesh', ramp('3a3e44', '5a6068', '80888f', 'a8b0b8', 'd0d6dc')),
    'mesh3': ('mesh', ramp('0e4a4a', '1a7c7a', '2cb8b0', '5ce4d8', 'b8fff6')),
    'lamp': ('glow', ramp('9c8a50', 'd8c890', 'fff2c0', 'fffbe6', 'ffffff')),
    'fire': ('glow', ramp('6a1a02', 'b83a06', 'f06a10', 'ffa030', 'ffe070')),
    'lava': ('glow', ramp('7a1a02', 'c43c06', 'f07010', 'ffa030', 'ffd070')),
    'redstone': ('glow', ramp('4a0404', '8a0a0a', 'd01818', 'f04040', 'ff8a80')),
    'bubbles': ('glow', ramp('0a4a4a', '14807a', '2ec8b8', '7af0e0', 'd0fff8')),
    'soot': ('rubber', ramp('0a0a0a', '141312', '1e1c1a', '2a2724', '36322e')),
    'hazard': ('paint', ramp('5a4308', '9c7a0e', 'd9a916', 'f2c526', 'ffe27a')),
}
ROCK_COLORS = {
    'cobblestone': ('4a4a4a', '5e5e5e', '747474', '8a8a8a', 'a4a4a4'),
    'andesite': ('5d5f5f', '727474', '888a8a', '9ea0a0', 'b8baba'),
    'granite': ('6e4638', '8a5a48', 'a26c58', 'b98370', 'd0a090'),
    'diorite': ('8a8a8a', 'a8a8a8', 'c4c4c4', 'dcdcdc', 'f4f4f4'),
    'tuff': ('4f5048', '62645a', '76786c', '8a8d7f', 'a4a698'),
    'deepslate': ('26262c', '34343a', '44444a', '56565e', '6e6e76'),
    'blackstone': ('1d1a20', '2a262e', '36303a', '443c48', '564c5a'),
}
for _r, _c in ROCK_COLORS.items():
    MATERIALS['rock_' + _r] = ('stone', ramp(*_c))
GLOW = {'lamp', 'fire', 'lava', 'redstone', 'bubbles'}


def noise(x, y, seed):
    return (zlib.crc32(b'%d,%d,%d' % (x, y, seed)) & 0xFFFF) / 65535.0


# =====================================================================================================================
# geometry
# =====================================================================================================================

class Box:
    def __init__(self, name, frm, to, mat, rot=None, faces=None, deco=()):
        self.name, self.frm, self.to, self.mat = name, [float(v) for v in frm], [float(v) for v in to], mat
        self.rot = rot            # ('x'|'y'|'z', angle, origin)
        self.faces = faces or ['north', 'south', 'east', 'west', 'up', 'down']
        self.deco = list(deco)    # (decal name, face set, args)

    @property
    def glow(self):
        return self.mat in GLOW

    def moved(self, f):
        """A copy with every point mapped through f (a translation + uniform scale)."""
        b = Box(self.name, f(self.frm), f(self.to), self.mat, None, list(self.faces), self.deco)
        b.scale = getattr(self, 'scale', 1.0) * f.k
        if self.rot:
            b.rot = (self.rot[0], self.rot[1], tuple(f(self.rot[2])))
        return b


class Affine:
    def __init__(self, k, t):
        self.k, self.t = k, t

    def __call__(self, p):
        return [p[i] * self.k + self.t[i] for i in range(3)]


def B(name, x0, y0, z0, x1, y1, z1, mat, **kw):
    return Box(name, (x0, y0, z0), (x1, y1, z1), mat, **kw)


def V(name, x0, y0, z0, x1, y1, z1, mat, **kw):
    """A box given in vehicle blocks."""
    return Box(name, (8 + 16 * x0, -16 + 16 * y0, 8 + 16 * z0), (8 + 16 * x1, -16 + 16 * y1, 8 + 16 * z1), mat, **kw)


def vp(x, y, z):
    return (8 + 16 * x, -16 + 16 * y, 8 + 16 * z)


def zcyl(name, d, xc, yc, z0, z1, mat, **kw):
    """Round along z: three boxes, close to an octagon in section."""
    a, b, c = d, d * 0.42, d * 0.83
    return [Box(name, (xc - a / 2, yc - b / 2, z0), (xc + a / 2, yc + b / 2, z1), mat, faces=['east', 'west', 'north', 'south'], **kw),
            Box(name + '_v', (xc - b / 2, yc - a / 2, z0 + INSET), (xc + b / 2, yc + a / 2, z1 - INSET), mat, faces=['up', 'down', 'north', 'south'], **kw),
            Box(name + '_c', (xc - c / 2, yc - c / 2, z0 + 2 * INSET), (xc + c / 2, yc + c / 2, z1 - 2 * INSET), mat, **kw)]


def ycyl(name, d, xc, zc, y0, y1, mat, **kw):
    a, b, c = d, d * 0.42, d * 0.83
    return [Box(name, (xc - a / 2, y0, zc - b / 2), (xc + a / 2, y1, zc + b / 2), mat, **kw),
            Box(name + '_v', (xc - b / 2, y0 + INSET, zc - a / 2), (xc + b / 2, y1 - INSET, zc + a / 2), mat, **kw),
            Box(name + '_c', (xc - c / 2, y0 + 2 * INSET, zc - c / 2), (xc + c / 2, y1 - 2 * INSET, zc + c / 2), mat, **kw)]


def xcyl(name, d, yc, zc, x0, x1, mat, **kw):
    a, b, c = d, d * 0.42, d * 0.83
    return [Box(name, (x0, yc - a / 2, zc - b / 2), (x1, yc + a / 2, zc + b / 2), mat, **kw),
            Box(name + '_v', (x0 + INSET, yc - b / 2, zc - a / 2), (x1 - INSET, yc + b / 2, zc + a / 2), mat, **kw),
            Box(name + '_c', (x0 + 2 * INSET, yc - c / 2, zc - c / 2), (x1 - 2 * INSET, yc + c / 2, zc + c / 2), mat, **kw)]


def flatten(items):
    out = []
    for item in items:
        if isinstance(item, (list, tuple)):
            out.extend(flatten(item))
        else:
            out.append(item)
    return out


# =====================================================================================================================
# the models
# =====================================================================================================================

def rim(y0, y1, inset, t, mat, **kw):
    a, b = inset, 16 - inset
    return [B('rim_n', a, y0, a, b, y1, a + t, mat, **kw), B('rim_s', a, y0, b - t, b, y1, b, mat, **kw),
            B('rim_w', a, y0, a + t, a + t, y1, b - t, mat, **kw), B('rim_e', b - t, y0, a + t, b, y1, b - t, mat, **kw)]


def legs(y1, inset, t, mat):
    out = []
    for x in (inset, 16 - inset - t):
        for z in (inset, 16 - inset - t):
            out.append(B('leg', x, 0, z, x + t, y1, z + t, mat))
    return out


def hand_sieve(mesh):
    P = legs(11, 1, 1.6, 'wood')
    P += rim(10, 12.5, 1, 1.6, 'plank', deco=[('rivets', 'all', None)])
    P += [B('brace_n', 2.6, 3, 1.4, 13.4, 4.2, 2.2, 'darkwood'), B('brace_s', 2.6, 3, 13.8, 13.4, 4.2, 14.6, 'darkwood'),
          B('brace_w', 1.4, 5, 2.6, 2.2, 6.2, 13.4, 'darkwood'), B('brace_e', 13.8, 5, 2.6, 14.6, 6.2, 13.4, 'darkwood')]
    if mesh:
        P.append(B('mesh', 2.6, 10.6, 2.6, 13.4, 11.0, 13.4, 'mesh%d' % mesh, faces=['up', 'down']))
    return flatten(P)


def grinder(lit):
    P = [B('base', 0, 0, 0, 16, 9, 16, 'stone', deco=[('rivets', 'around', None)]),
         B('housing', 1, 9, 1, 15, 11.5, 15, 'steel', deco=[('bolts', 'top', None)]),
         B('chute', 5.5, 0.5, -0.0, 10.5, 4, 1.2, 'dark')]
    for k, z in enumerate((5.6, 10.4)):
        P += xcyl('wheel%d' % k, 5.6, 12.4, z, 2.6, 13.4, 'darkstone', deco=[('grooves', 'all', None)])
    P += rim(11.5, 16, 1.5, 1.2, 'steel')
    P += xcyl('gear', 8, 5, 8, 15.4, 16, 'copper', deco=[('bolts', 'ends', None)])
    P.append(B('lamp', 11.5, 6, -0.2, 13.5, 8, 0.4, 'lamp' if lit else 'soot', faces=['north']))
    return flatten(P)


def shaker_sieve(lit):
    P = legs(8, 0.5, 2, 'wood')
    for x in (1, 13):
        for z in (1, 13):
            P += ycyl('spring', 1.6, x + 1, z + 1, 8, 10, 'steel', deco=[('bands', 'around_y', None)])
    P += rim(10, 13.5, 0, 1.8, 'plank', deco=[('rivets', 'all', None)])
    P.append(B('mesh', 1.8, 11, 1.8, 14.2, 11.4, 14.2, 'mesh2', faces=['up', 'down']))
    P += [B('motor', 5, 2, 5, 11, 7, 11, 'dark'), B('eccentric', 7, 7, 7, 9, 10, 9, 'steel'),
          B('contact', 6.5, 3, -0.2, 9.5, 5, 0.6, 'redstone' if lit else 'soot', faces=['north', 'up'])]
    return flatten(P)


def rock_former(lit):
    P = [B('base', 0, 0, 0, 16, 6, 16, 'stone', deco=[('rivets', 'around', None)]),
         B('wall_w', 0.5, 6, 1.5, 6.5, 8, 14.5, 'darkstone'), B('water', 1.5, 6, 2.5, 5.5, 7.6, 13.5, 'water', faces=['up']),
         B('wall_e', 9.5, 6, 1.5, 15.5, 8, 14.5, 'darkstone'), B('lava', 10.5, 6, 2.5, 14.5, 7.6, 13.5, 'lava', faces=['up']),
         B('pedestal', 6.5, 6, 5.5, 9.5, 10, 10.5, 'darkstone'),
         B('post_a', 6.8, 6, 1.2, 9.2, 14, 2.6, 'steel'), B('post_b', 6.8, 6, 13.4, 9.2, 14, 14.8, 'steel'),
         B('beam', 6.8, 13, 1.2, 9.2, 14.4, 14.8, 'steel', deco=[('rivets', 'all', None)]),
         B('press', 6, 10.6 if lit else 11.5, 5, 10, 13, 11, 'dark')]
    P += zcyl('pipe_w', 2.2, 3, 8.8, 0, 16, 'blue')
    P += zcyl('pipe_e', 2.2, 13, 8.8, 0, 16, 'orange')
    return flatten(P)


def sluice():
    P = [B('floor', 0, 0, 0, 16, 2, 16, 'plank', deco=[('rivets', 'all', None)]),
         B('wall_w', 0, 2, 0, 2, 8, 16, 'plank'), B('wall_e', 14, 2, 0, 16, 8, 16, 'plank'),
         B('band_w', -0.01, 6.5, 0, 0.6, 7.5, 16, 'dark'), B('band_e', 15.4, 6.5, 0, 16.01, 7.5, 16, 'dark')]
    for k, z in enumerate((2.5, 6.5, 10.5, 14.5)):
        P.append(B('riffle%d' % k, 2, 2, z, 14, 3, z + 1, 'darkwood'))
    P.append(B('water', 2, 2, 0, 14, 2.9, 16, 'water', faces=['up']))
    return flatten(P)


def flotation_cell(lit):
    P = ycyl('tank', 14, 8, 8, 0, 12.5, 'steel', deco=[('plates', 'around_y', None)])
    P += ycyl('rim', 14.6, 8, 8, 11.8, 12.8, 'dark')
    P += ycyl('froth', 12, 8, 8, 12.2, 13.2, 'foam')
    P += [B('window', 5, 3, 0.7, 11, 9, 1.3, 'bubbles' if lit else 'glass', faces=['north']),
          B('motor', 5.5, 13, 5.5, 10.5, 16, 10.5, 'dark', deco=[('bolts', 'top', None)])]
    P += ycyl('shaft', 1.6, 8, 8, 12, 13.4, 'steel')
    P += xcyl('pipe', 2.4, 4, 8, 14, 16, 'copper')
    P += xcyl('launder', 2.8, 11.5, 12, 0, 2, 'copper')
    return flatten(P)


def coal_generator(lit):
    P = [B('body', 1, 0, 1, 15, 12, 15, 'steel', deco=[('rivets', 'around', None)]),
         B('plinth', 0, 0, 0, 16, 2, 16, 'dark'),
         B('door', 4, 2.5, 0.5, 12, 8.5, 1.2, 'dark', deco=[('rivets', 'front', None)]),
         B('fire', 5, 3.5, 0.3, 11, 7.5, 0.6, 'fire' if lit else 'soot', faces=['north']),
         B('port', 6, 5, 14.9, 10, 9, 15.6, 'redstone', faces=['south'])]
    P += ycyl('chimney', 3.6, 11.5, 11.5, 12, 16, 'dark')
    for k, y in enumerate((12.5, 14)):
        P += xcyl('coil%d' % k, 2.2, y, 5, 2, 9, 'copper')
    return flatten(P)


def drill_frame():
    P = []
    for x in (0, 14):
        for z in (0, 14):
            P.append(B('post', x, 0, z, x + 2, 16, z + 2, 'steel', deco=[('rivets', 'all', None)]))
    for y in (0, 14):
        P += [B('beam_n', 2, y, 0, 14, y + 2, 2, 'hazard' if y == 14 else 'steel', deco=[('stripes', 'all', None)] if y == 14 else []),
              B('beam_s', 2, y, 14, 14, y + 2, 16, 'hazard' if y == 14 else 'steel', deco=[('stripes', 'all', None)] if y == 14 else []),
              B('beam_w', 0, y, 2, 2, y + 2, 14, 'steel'), B('beam_e', 14, y, 2, 16, y + 2, 14, 'steel')]
    P += [B('brace_n', 0.5, 7.2, 0.3, 15.5, 8.8, 1.7, 'dark', rot=('z', 45, (8, 8, 1))),
          B('brace_s', 0.5, 7.2, 14.3, 15.5, 8.8, 15.7, 'dark', rot=('z', -45, (8, 8, 15))),
          B('brace_w', 0.3, 7.2, 0.5, 1.7, 8.8, 15.5, 'dark', rot=('x', 45, (1, 8, 8))),
          B('brace_e', 14.3, 7.2, 0.5, 15.7, 8.8, 15.5, 'dark', rot=('x', -45, (15, 8, 8)))]
    return flatten(P)


def deep_drill(lit):
    P = [B('housing', 1, 4, 1, 15, 13, 15, 'orange', deco=[('rivets', 'around', None)]),
         B('stripes', 0.8, 4, 0.8, 15.2, 5.5, 15.2, 'hazard', deco=[('stripes', 'all', None)]),
         B('motor', 3, 13, 3, 13, 16, 13, 'dark', deco=[('grille', 'around_y', None)])]
    P += ycyl('spindle', 5, 8, 8, 0, 4.2, 'steel', deco=[('grooves', 'all', None)])
    P += ycyl('collar', 8, 8, 8, 3, 4.5, 'dark')
    for x in (2.5, 11.5):
        P.append(B('lamp', x, 9, 0.5, x + 2, 11, 1.2, 'lamp' if lit else 'soot', faces=['north']))
    return flatten(P)


def pile(rock):
    m = 'rock_' + rock
    return [B('base', 3, 0, 3, 13, 2, 13, m), B('mid', 4.5, 2, 4.2, 11.5, 3.8, 11.8, m, rot=('y', 15, (8, 3, 8))),
            B('top', 6, 3.8, 6, 10, 5.2, 10, m), B('pebble_a', 11.8, 0, 6, 13.6, 1.4, 8, m),
            B('pebble_b', 3.4, 0, 9.5, 5, 1.2, 11.2, m, rot=('y', 30, (4, 0, 10)))]


MACHINES = ['grinder', 'shaker_sieve', 'rock_former', 'flotation_cell', 'coal_generator', 'deep_drill']
_FACT = {'grinder': grinder, 'shaker_sieve': shaker_sieve, 'rock_former': rock_former, 'flotation_cell': flotation_cell,
         'coal_generator': coal_generator, 'deep_drill': deep_drill}

# name -> (boxes factory, texel density, texture folder, display kind)
MODELS = {'sluice': (sluice, 2, 'block', 'block'), 'deep_drill_frame': (drill_frame, 2, 'block', 'block')}
for _m in range(4):
    MODELS['hand_sieve_mesh%d' % _m] = ((lambda m: lambda: hand_sieve(m))(_m), 2, 'block', 'block')
for _n in MACHINES:
    MODELS[_n] = ((lambda n: lambda: _FACT[n](False))(_n), 2, 'block', 'block')
    MODELS[_n + '_lit'] = ((lambda n: lambda: _FACT[n](True))(_n), 2, 'block', 'block')
for _r in ROCK_COLORS:
    MODELS['pile_' + _r] = ((lambda r: lambda: pile(r))(_r), 2, 'item', None)


# =====================================================================================================================
# painting
# =====================================================================================================================

UV_AXES = {
    'north': ('x', True, 'y', True), 'south': ('x', False, 'y', True),
    'east': ('z', True, 'y', True), 'west': ('z', False, 'y', True),
    'up': ('x', False, 'z', False), 'down': ('x', False, 'z', True),
}
FACE_SETS = {
    'all': ('north', 'south', 'east', 'west', 'up', 'down'), 'sides': ('east', 'west'),
    'around': ('east', 'west', 'up', 'down'), 'around_y': ('north', 'south', 'east', 'west'),
    'sides_z': ('north', 'south'), 'top': ('up',), 'front': ('north',), 'back': ('south',),
    'left': ('west',), 'right': ('east',), 'ends': ('north', 'south'),
}


def face_size(box, face, texels):
    (x0, y0, z0), (x1, y1, z1) = box.frm, box.to
    w, h = {'north': (x1 - x0, y1 - y0), 'south': (x1 - x0, y1 - y0), 'east': (z1 - z0, y1 - y0),
            'west': (z1 - z0, y1 - y0), 'up': (x1 - x0, z1 - z0), 'down': (x1 - x0, z1 - z0)}[face]
    k = texels / getattr(box, 'scale', 1.0)
    return max(1, int(round(w * k))), max(1, int(round(h * k)))


class Face:
    def __init__(self, box, face, texels):
        self.box, self.face = box, face
        self.w, self.h = face_size(box, face, texels)
        self.img = Img(self.w, self.h)
        self.kind, self.pal = MATERIALS[box.mat]
        self.seed = zlib.crc32((box.name + face + box.mat).encode())
        self.ua, ur, self.va, vr = UV_AXES[face]

    def tone(self, x, y, t, jitter=0.0):
        t = max(0, min(4, t))
        c = self.pal[t]
        if jitter:
            c = mix(c, self.pal[min(4, t + 1)], jitter)
        self.img.set(x, y, c)

    def shade(self, x, y, target, amount):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.set(x, y, mix(self.img.get(x, y), self.pal[target], amount))


def paint_base(f):
    for y in range(f.h):
        for x in range(f.w):
            n = noise(x, y, f.seed)
            if f.kind == 'glow':
                edge = x in (0, f.w - 1) or y in (0, f.h - 1)
                f.tone(x, y, 2 if edge and f.w > 2 and f.h > 2 else 3, 0.5 if n > 0.8 else 0.0)
            elif f.kind == 'water':
                t = 2 if noise(x // 2, y, f.seed) > 0.35 else 3
                c = f.pal[t]
                f.img.set(x, y, (c[0], c[1], c[2], 170))
            elif f.kind == 'mesh':
                wire = x % 3 == 0 or y % 3 == 0
                f.img.set(x, y, f.pal[3 if (x + y) % 2 else 2] if wire else (0, 0, 0, 0))
            elif f.kind == 'stone':
                f.tone(x, y, 1 + int(noise(x // 2, y // 2, f.seed) * 3.99), 0.3 if n > 0.8 else 0.0)
            elif f.kind == 'glass':
                t = (x + (f.h - 1 - y)) / float(max(1, f.w + f.h - 2))
                f.img.set(x, y, mix(f.pal[1], f.pal[3], t))
            elif f.kind == 'brick':
                row = y // 3
                off = 3 if row % 2 else 0
                mortar = y % 3 == 2 or (x + off) % 6 == 5
                if mortar:
                    f.img.set(x, y, hexc('6e6258'))
                else:
                    f.tone(x, y, 2 if noise(x // 6, row, f.seed) > 0.4 else 1, 0.4 if n > 0.75 else 0.0)
            elif f.kind == 'paint':
                f.tone(x, y, 2, 0.3 if n > 0.85 else 0.0)
                if n < 0.035:
                    f.img.set(x, y, MATERIALS['steel'][1][2])   # chipped paint shows the steel
            elif f.kind == 'metal':
                f.tone(x, y, 2, 0.35 if n > 0.72 else 0.0)
                if n < 0.1:
                    f.tone(x, y, 1, 0.5)
            else:
                f.tone(x, y, 1 if n < 0.5 else 2, 0.2 if n > 0.9 else 0.0)
    if f.kind in ('glow', 'glass', 'water', 'mesh') or f.w < 2 or f.h < 2:
        return
    if f.face not in ('up', 'down'):
        for x in range(f.w):
            f.shade(x, 0, 4, 0.5)
            if f.h > 2:
                f.shade(x, f.h - 1, 0, 0.55)
        for y in range(1, f.h - 1):
            f.shade(0, y, 1, 0.3)
            f.shade(f.w - 1, y, 1, 0.3)
    else:
        for x in range(f.w):
            for y in range(f.h):
                if x in (0, f.w - 1) or y in (0, f.h - 1):
                    f.shade(x, y, 4 if f.face == 'up' else 0, 0.3)


def axis_texels(f, axis):
    """Which texture axis ('u'/'v') runs along the given model axis on this face, or None."""
    if f.ua == axis:
        return 'u'
    if f.va == axis:
        return 'v'
    return None


def decal(f, name):
    w, h = f.w, f.h
    if name == 'treads':
        # grooves across the track (perpendicular to z)
        along = axis_texels(f, 'z')
        n = w if along == 'u' else h
        for i in range(n):
            if i % 3 == 0:
                for j in range(h if along == 'u' else w):
                    x, y = (i, j) if along == 'u' else (j, i)
                    f.img.set(x, y, f.pal[0])
            elif i % 3 == 1 and f.face in ('up', 'down', 'north', 'south'):
                for j in range(h if along == 'u' else w):
                    x, y = (i, j) if along == 'u' else (j, i)
                    f.shade(x, y, 4, 0.25)
    elif name == 'stripes':
        for y in range(h):
            for x in range(w):
                if ((x + y) // 3) % 2:
                    f.img.set(x, y, MATERIALS['dark'][1][1])
                else:
                    f.img.set(x, y, MATERIALS['hazard'][1][3])
    elif name == 'grille':
        if w < 5 or h < 5:
            return
        for y in range(2, h - 2):
            for x in range(2, w - 2):
                f.img.set(x, y, MATERIALS['dark'][1][0] if y % 2 == 0 else MATERIALS['dark'][1][2])
    elif name == 'rivets':
        if w < 6 or h < 6:
            return
        step = 6
        for x in range(2, w - 1, step):
            for y in (1, h - 2):
                f.img.set(x, y, f.pal[4])
                f.shade(x + 1, y + 1, 0, 0.5)
    elif name == 'bolts':
        if w < 4 or h < 4:
            return
        cx, cy, r = (w - 1) / 2.0, (h - 1) / 2.0, min(w, h) / 2.0 - 1.2
        for k in range(8):
            a = k * math.pi / 4 + math.pi / 8
            x, y = int(round(cx + r * math.cos(a))), int(round(cy + r * math.sin(a)))
            f.img.set(x, y, MATERIALS['steel'][1][4])
            f.shade(x + 1, y + 1, 0, 0.6)
    elif name == 'grooves':
        # the drill's spiral flight cut into the cone: dark diagonal lines + a bright cutting edge
        for y in range(h):
            for x in range(w):
                d = (x + 2 * y + f.seed % 9) % 9
                if d == 0:
                    f.tone(x, y, 1, 0.2)
                elif d == 1:
                    f.tone(x, y, 3, 0.4)
    elif name == 'bands':
        for y in range(0, h, 4):
            for x in range(w):
                f.tone(x, y, 1, 0.3)
    elif name == 'plates':
        for y in range(h):
            for x in range(w):
                if y % 8 == 7:
                    f.tone(x, y, 0, 0.4)
                elif y % 8 == 0:
                    f.tone(x, y, 4, 0.2)
                if x % 6 == 0 and y % 8 in (2, 5):
                    f.img.set(x, y, f.pal[4])
    elif name == 'emboss':
        # the X pressed into a jerry can's side
        for y in range(h):
            for x in range(w):
                u, v = x / max(1, w - 1), y / max(1, h - 1)
                if 0.12 < v < 0.88 and (abs(u - v) < 0.06 or abs(u - (1 - v)) < 0.06):
                    f.tone(x, y, 3, 0.3)
                if 0.12 < v < 0.88 and (abs(u - v - 0.07) < 0.03 or abs(u - (1 - v) - 0.07) < 0.03):
                    f.tone(x, y, 1, 0.4)


def paint(box, face, texels):
    f = Face(box, face, texels)
    paint_base(f)
    for name, where, _ in box.deco:
        if face in FACE_SETS.get(where, (where,)):
            decal(f, name)
    return f.img


# =====================================================================================================================
# atlas + JSON
# =====================================================================================================================

_CACHE = {}


def build(name):
    if name in _CACHE:
        return _CACHE[name]
    factory, texels, _, _ = MODELS[name]
    boxes = factory()
    for box in boxes:
        for p in corners(box):
            if not all(-16.0 <= v <= 32.0 for v in p):
                raise ValueError('%s/%s leaves the -16..32 model space: %s' % (name, box.name, p))
    painted = [(box, face, paint(box, face, texels)) for box in boxes for face in box.faces]
    area = sum(img.w * img.h for _, _, img in painted)
    width = 64
    while width * width < area * 1.4:
        width *= 2
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
    uvs = {}
    for i, (box, face, img) in enumerate(painted):
        px, py = place[i]
        atlas.paste(img, px, py)
        uvs.setdefault(id(box), {})[face] = [round(px * 16.0 / width, 4), round(py * 16.0 / height, 4),
                                             round((px + img.w) * 16.0 / width, 4), round((py + img.h) * 16.0 / height, 4)]
    _CACHE[name] = (boxes, atlas, uvs, painted)
    return _CACHE[name]


def texture_id(name):
    return 'stonesift:%s/%s' % (MODELS[name][2], name)


def model_json(name):
    boxes, atlas, uvs, _ = build(name)
    elements = []
    for box in boxes:
        el = {'from': [round(v, 4) for v in box.frm], 'to': [round(v, 4) for v in box.to],
              'faces': {face: {'uv': uv, 'texture': '#t'} for face, uv in uvs[id(box)].items()}}
        if box.rot:
            axis, angle, origin = box.rot
            el['rotation'] = {'angle': angle, 'axis': axis, 'origin': [round(v, 4) for v in origin]}
        if box.glow:
            el['light_emission'] = 15
            el['shade'] = False
        elements.append(el)
    out = {'textures': {'t': texture_id(name), 'particle': texture_id(name)}, 'elements': elements}
    kind = MODELS[name][3]
    if kind:
        out['display'] = display(name, kind)
    return out


def euler(rot):
    return mat_mul(rx(rot[0]), mat_mul(ry(rot[1]), rz(rot[2])))


def display(name, kind):
    boxes = build(name)[0]
    if kind == 'block':
        return {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625] * 3},
                'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25] * 3},
                'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5] * 3},
                'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375] * 3},
                'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4] * 3}}
    views = {
        'vehicle': {'gui': [25, 215, 0], 'ground': [0, 0, 0], 'fixed': [0, 180, 0],
                    'thirdperson_righthand': [0, 90, 0], 'firstperson_righthand': [0, 110, 0]},
        'head': {'gui': [70, -45, 0], 'ground': [0, 0, 0], 'fixed': [70, -45, 0],
                 'thirdperson_righthand': [90, 0, 0], 'firstperson_righthand': [0, 10, 0]},
        'canister': {'gui': [20, 210, 0], 'ground': [0, 0, 0], 'fixed': [0, 180, 0],
                     'thirdperson_righthand': [0, 90, 0], 'firstperson_righthand': [0, 70, 0]},
    }[kind]
    size = {'gui': 0.92, 'ground': 0.45, 'fixed': 0.85, 'thirdperson_righthand': 0.5, 'firstperson_righthand': 0.55}
    if kind == 'vehicle':
        size.update(thirdperson_righthand=0.45, firstperson_righthand=0.5)
    out = {}
    for ctx, rot in views.items():
        m = euler(rot)
        pts = [apply(m, [(v / 16.0 - 0.5) for v in p]) for b in boxes for p in corners(b)]
        lo = [min(p[i] for p in pts) for i in range(3)]
        hi = [max(p[i] for p in pts) for i in range(3)]
        ext = max(hi[0] - lo[0], hi[1] - lo[1]) if ctx in ('gui', 'fixed') else max(hi[i] - lo[i] for i in range(3))
        s = min(4.0, size[ctx] / ext)
        t = [-(lo[i] + hi[i]) / 2.0 * s * 16.0 for i in range(3)]
        if ctx == 'ground':
            t[1] += 2.0
        if ctx == 'thirdperson_righthand':
            t = [0.0, 3.0 if kind != 'head' else 1.0, 1.0]
        if ctx == 'firstperson_righthand':
            t = [0.0, 3.0, 0.0]
        out[ctx] = {'rotation': rot, 'translation': [round(v, 3) for v in t], 'scale': [round(s, 4)] * 3}
    return out


def write_all(assets_dir):
    """Writes the model JSON and the atlases; returns the number of files."""
    n = 0
    for name, (_, _, folder, kind) in MODELS.items():
        mdir = os.path.join(assets_dir, 'models', 'block' if folder == 'block' else 'item')
        os.makedirs(mdir, exist_ok=True)
        with open(os.path.join(mdir, name + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(model_json(name), f, indent=1)
            f.write('\n')
        tdir = os.path.join(assets_dir, 'textures', folder)
        os.makedirs(tdir, exist_ok=True)
        build(name)[1].save(os.path.join(tdir, name + '.png'))
        n += 2
    return n


# =====================================================================================================================
# maths + software renderer (previews)
# =====================================================================================================================

def rx(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, p):
    return [m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] for i in range(3)]


def rotate_point(box, p):
    if not box.rot:
        return list(p)
    axis, angle, origin = box.rot
    m = {'x': rx, 'y': ry, 'z': rz}[axis](angle)
    r = apply(m, [p[i] - origin[i] for i in range(3)])
    return [r[i] + origin[i] for i in range(3)]


def corners(box):
    (x0, y0, z0), (x1, y1, z1) = box.frm, box.to
    return [rotate_point(box, [x, y, z]) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]


FACE_CORNERS = {
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


def raster(canvas, pts, uvs, tex, shade):
    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = pts
    area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
    if abs(area) < 1e-9:
        return
    minx, maxx = max(0, int(min(x0, x1, x2))), min(canvas.w - 1, int(max(x0, x1, x2)) + 1)
    miny, maxy = max(0, int(min(y0, y1, y2))), min(canvas.h - 1, int(max(y0, y1, y2)) + 1)
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
            if z >= canvas.z[y][x]:
                continue
            u = w0 * uvs[0][0] + w1 * uvs[1][0] + w2 * uvs[2][0]
            v = w0 * uvs[0][1] + w1 * uvs[1][1] + w2 * uvs[2][1]
            c = tex.get(min(tex.w - 1, max(0, int(u))), min(tex.h - 1, max(0, int(v))))
            if not c[3]:
                continue
            canvas.z[y][x] = z
            canvas.img.set(x, y, (min(255, int(c[0] * shade)), min(255, int(c[1] * shade)), min(255, int(c[2] * shade)), 255))


def draw(canvas, name, xf, project, light=(0.3, 0.85, 0.45)):
    boxes, _, _, painted = build(name)
    ln = math.sqrt(sum(c * c for c in light))
    light = [c / ln for c in light]
    for box, face, fimg in painted:
        quad = [xf(rotate_point(box, c)) for c in FACE_CORNERS[face](box.frm, box.to)]
        e1 = [quad[1][k] - quad[0][k] for k in range(3)]
        e2 = [quad[3][k] - quad[0][k] for k in range(3)]
        n = [e2[1] * e1[2] - e2[2] * e1[1], e2[2] * e1[0] - e2[0] * e1[2], e2[0] * e1[1] - e2[1] * e1[0]]
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        shade = 1.0 if box.glow else 0.55 + 0.45 * abs(sum(n[k] / nl * light[k] for k in range(3)))
        proj = [project(p) for p in quad]
        uv = [(0, 0), (fimg.w, 0), (fimg.w, fimg.h), (0, fimg.h)]
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster(canvas, [proj[t] for t in tri], [uv[t] for t in tri], fimg, shade)


def view(names, rot, size=260, bg=(150, 165, 180, 255), offsets=None):
    """Orthographic view of one or more models (offsets in model units) rotated by euler rot."""
    m = euler(rot)
    offsets = offsets or [(0, 0, 0)] * len(names)
    pts = []
    for n, off in zip(names, offsets):
        for b in build(n)[0]:
            pts += [apply(m, [p[i] + off[i] for i in range(3)]) for p in corners(b)]
    lo = [min(p[i] for p in pts) for i in range(3)]
    hi = [max(p[i] for p in pts) for i in range(3)]
    scale = (size - 16) / max(hi[0] - lo[0], hi[1] - lo[1])
    cx, cy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    canvas = Canvas(size, size, bg)
    for n, off in zip(names, offsets):
        draw(canvas, n, lambda p, off=off: apply(m, [p[i] + off[i] for i in range(3)]),
             lambda p: (size / 2 + (p[0] - cx) * scale, size / 2 - (p[1] - cy) * scale, -p[2]))
    return canvas.img


def sheet(images, cols, pad=6, bg=(40, 40, 44, 255)):
    rows = (len(images) + cols - 1) // cols
    cw, ch = max(i.w for i in images), max(i.h for i in images)
    out = Img(cols * (cw + pad) + pad, rows * (ch + pad) + pad, bg)
    for n, img in enumerate(images):
        out.paste(img, pad + (n % cols) * (cw + pad), pad + (n // cols) * (ch + pad))
    return out



def main():
    os.makedirs(BUILD, exist_ok=True)
    names = ['hand_sieve_mesh1', 'grinder_lit', 'shaker_sieve', 'rock_former_lit', 'sluice', 'flotation_cell_lit',
             'coal_generator_lit', 'deep_drill_frame', 'deep_drill_lit', 'pile_granite']
    sheet([view([n], [25, 215, 0], 220) for n in names], 5).save(os.path.join(BUILD, 'preview_blocks.png'))
    for n in MODELS:
        b, atlas, _, _ = build(n)
        print('%-26s %3d boxes  atlas %dx%d' % (n, len(b), atlas.w, atlas.h))



if __name__ == '__main__':
    main()

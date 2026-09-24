#!/usr/bin/env python3
"""3D models for Drillworks: the Mining Drill (body, drill heads, fuel gauge), the canisters and the refinery blocks.

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
    'paint': ('paint', ramp('5a4308', '9c7a0e', 'd9a916', 'f2c526', 'ffe27a')),
    'red': ('paint', ramp('4a0e0c', '7e1814', 'b42a20', 'd8452f', 'f07a5e')),
    'grey': ('paint', ramp('34393e', '4f565d', '6f7780', '8d959e', 'b4bcc4')),
    'steel': ('metal', ramp('2c3035', '4a5058', '6e757e', '949ca5', 'c3cad1')),
    'dark': ('metal', ramp('141619', '22262b', '33383e', '474d55', '626973')),
    'rubber': ('rubber', ramp('0d0d0e', '18181a', '232326', '303034', '3e3e43')),
    'leather': ('rubber', ramp('2a1a12', '3f281b', '553726', '6d4832', '8a5e43')),
    'copper': ('metal', ramp('4a2410', '7a3e1e', 'b0602f', 'd98652', 'f2b184')),
    'brick': ('brick', ramp('2e1512', '5e2e22', '86432f', 'a45a40', 'c07a5c')),
    'knob': ('paint', ramp('3a0808', '7a1010', 'c01c1c', 'e84040', 'ff8a80')),
    'label': ('paint', ramp('6a5006', 'a88010', 'e8c020', 'f8dc50', 'fff0a0')),
    'glass': ('glass', ramp('1c2a30', '2b4450', '3f6878', '6c9fb0', 'b8e2ee')),
    'fuel': ('glow', ramp('7a3208', 'b8520c', 'e87d14', 'ffae33', 'ffd88a')),
    'lamp': ('glow', ramp('9c8a50', 'd8c890', 'fff2c0', 'fffbe6', 'ffffff')),
    'led': ('glow', ramp('0a4a10', '12801c', '2ed040', '7af08a', 'd0ffd8')),
    'fire': ('glow', ramp('6a1a02', 'b83a06', 'f06a10', 'ffa030', 'ffe070')),
    'soot': ('rubber', ramp('0a0a0a', '141312', '1e1c1a', '2a2724', '36322e')),
}
GLOW = {'fuel', 'lamp', 'led', 'fire'}

# drill head materials: (body palette, tip palette)
HEADS = {
    'stone': (ramp('3a3a3a', '555555', '747474', '8f8f8f', 'aaaaaa'), ramp('2e2e2e', '4a4a4a', '6a6a6a', '8a8a8a', 'a8a8a8')),
    'copper': (ramp('5a2a16', '8a4424', 'c06a3a', 'e08e5a', 'f7bd92'), ramp('2f6e5a', '3f8f76', '56b596', '7fd4b4', 'b4f0d8')),
    'iron': (ramp('4a4d52', '72767c', 'a0a5ab', 'c8ccd1', 'eef0f2'), ramp('3a3d42', '5e6268', '8a8f96', 'b4b9bf', 'e0e3e6')),
    'golden': (ramp('6e4a07', 'a87a0e', 'e0b21c', 'f7d548', 'fff3a4'), ramp('8a5a06', 'c08a10', 'f0c030', 'ffe070', 'fffbd0')),
    'amethyst': (ramp('3a1f5e', '5a348c', '8456c0', 'a97fe0', 'd8bcff'), ramp('5a2f8e', '8450c8', 'b07ef0', 'd4b0ff', 'f4e8ff')),
    'diamond': (ramp('0e4a4a', '1a7c7a', '2cb8b0', '5ce4d8', 'b8fff6'), ramp('1a6e6a', '2aa8a0', '4ee0d6', '9af8ee', 'e8fffc')),
    'netherite': (ramp('1c181a', '2e2729', '433a3c', '5a5052', '7a7072'), ramp('4a2a1a', '7a3e20', 'b05a2a', 'e08040', 'ffb070')),
}
for _name, (_body, _tip) in HEADS.items():
    MATERIALS['head_' + _name] = ('metal', _body)
    MATERIALS['tip_' + _name] = ('metal', _tip)


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

def drill_head(material):
    body, tip = 'head_' + material, 'tip_' + material
    parts = [zcyl('collar', 21, 8, 8, 14.8, 17, 'dark', deco=[('bolts', 'ends', None)]),
             zcyl('hub', 16, 8, 8, 16.4, 17.2, 'steel')]
    rings = [(10.5, 13.6, 19.5), (7.5, 10.6, 16), (4.8, 7.6, 12.5), (2.3, 4.9, 9), (0, 2.4, 6)]
    for i, (z0, z1, d) in enumerate(rings):
        parts.append(zcyl('ring%d' % i, d, 8, 8, z0, z1, body, deco=[('grooves', 'all', None)]))
        blade = d / 2 + 1.1
        parts.append(B('flight%d' % i, 8 - blade, 7.5, z0 + 0.4, 8 + blade, 8.5, z1 - 0.2, body,
                       rot=('z', ((i * 36 + 22.5) % 180) - 90, (8, 8, 8))))
    parts += [zcyl('tip', 3.4, 8, 8, -2, 0.1, tip), zcyl('point', 1.6, 8, 8, -3.5, -1.9, tip)]
    return flatten(parts)


def drill_body():
    P = []
    for side in (-1, 1):
        s = 'l' if side < 0 else 'r'
        xo0, xo1 = sorted((side * 1.30, side * 0.86))
        P += [V('track_' + s, xo0, 0.06, -0.80, xo1, 0.50, 1.00, 'rubber', deco=[('treads', 'all', None)]),
              V('track_end_f' + s, xo0 + 0.02, 0.12, -0.90, xo1 - 0.02, 0.44, -0.80, 'rubber', deco=[('treads', 'all', None)]),
              V('track_end_b' + s, xo0 + 0.02, 0.12, 1.00, xo1 - 0.02, 0.44, 1.10, 'rubber', deco=[('treads', 'all', None)])]
        xw = side * 1.31
        for k, z in enumerate((-0.55, 0.12, 0.75)):
            y, zc = vp(0, 0.28, z)[1], vp(0, 0, z)[2]
            x0, x1 = sorted((8 + 16 * xw, 8 + 16 * (xw - side * 0.06)))
            P += xcyl('wheel_%s%d' % (s, k), 5.2, y, zc, x0, x1, 'steel')
        fx0, fx1 = sorted((side * 1.34, side * 0.84))
        P += [V('fender_' + s, fx0, 0.50, -0.95, fx1, 0.58, 1.12, 'paint', deco=[('stripes', 'front', None)])]
        ex0, ex1 = sorted((side * 0.98, side * 0.48))
        P += [V('engine_' + s, ex0, 0.60, 0.44, ex1, 1.18, 1.05, 'paint',
                deco=[('grille', 'left' if side < 0 else 'right', None), ('grille', 'back', None), ('rivets', 'top', None)])]
    P += [V('chassis', -0.86, 0.18, -0.80, 0.86, 0.62, 1.10, 'paint', deco=[('stripes', 'front', None), ('rivets', 'sides', None)])]
    # the big drill housing, like the reference: a fat yellow cylinder with dark bands and cooling fins on top
    c = vp(0, 1.02, 0)
    P += zcyl('housing', 1.24 * 16, 8, c[1], vp(0, 0, -0.84)[2], vp(0, 0, 0.42)[2], 'paint', deco=[('rivets', 'all', None)])
    for k, z in enumerate((-0.40, 0.18)):
        P += zcyl('band%d' % k, 1.30 * 16, 8, c[1], vp(0, 0, z)[2], vp(0, 0, z + 0.07)[2], 'dark')
    P += zcyl('collar', 1.36 * 16, 8, c[1], vp(0, 0, -0.93)[2], vp(0, 0, -0.82)[2], 'dark', deco=[('bolts', 'ends', None)])
    for k in range(6):
        z = -0.70 + k * 0.18
        P.append(V('fin%d' % k, -0.20, 1.60, z, 0.20, 1.72, z + 0.07, 'dark'))
    P.append(V('fin_rail', -0.05, 1.58, -0.72, 0.05, 1.66, 0.28, 'steel'))
    # headlights and bumper
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.52, side * 0.74))
        P += [V('light_housing', x0 - 0.02, 0.38, -0.84, x1 + 0.02, 0.56, -0.80, 'dark'),
              V('light', x0, 0.40, -0.86, x1, 0.54, -0.83, 'lamp')]
    # cockpit: seat, backrest, dashboard with the fuel gauge window, two levers
    P += [V('seat', -0.34, 0.62, 0.55, 0.34, 0.78, 0.98, 'leather'),
          V('backrest', -0.34, 0.78, 0.92, 0.34, 1.30, 1.04, 'leather'),
          V('headrest', -0.22, 1.30, 0.94, 0.22, 1.42, 1.04, 'leather'),
          V('dash', -0.46, 0.62, 0.38, 0.46, 0.94, 0.46, 'dark'),
          V('gauge_frame', -0.42, 0.64, 0.455, -0.26, 0.92, 0.47, 'steel'),
          V('gauge_back', -0.40, 0.66, 0.46, -0.28, 0.90, 0.475, 'soot'),
          V('dash_led', 0.18, 0.84, 0.455, 0.26, 0.88, 0.47, 'led'),
          V('dash_led2', 0.30, 0.84, 0.455, 0.38, 0.88, 0.47, 'fuel')]
    for side in (-1, 1):
        x = side * 0.20
        P += [V('lever', x - 0.025, 0.62, 0.50, x + 0.025, 0.98, 0.55, 'dark'),
              V('knob', x - 0.045, 0.98, 0.49, x + 0.045, 1.06, 0.56, 'knob')]
    # rear fuel window (the fill is the separate gauge model), exhaust stack
    P += [V('tank_frame', -0.92, 0.66, 1.05, -0.60, 1.14, 1.07, 'steel'),
          V('tank_back', -0.90, 0.67, 1.06, -0.62, 1.12, 1.075, 'soot')]
    ex = vp(0.78, 0, 0.95)
    P += ycyl('exhaust', 2.4, ex[0], ex[2], vp(0, 1.10, 0)[1], vp(0, 1.86, 0)[1], 'steel')
    P += ycyl('exhaust_cap', 3.0, ex[0], ex[2], vp(0, 1.80, 0)[1], vp(0, 1.92, 0)[1], 'dark')
    return flatten(P)


def drill_gauge():
    """The fuel fill: scaled in y about GAUGE_BOTTOM by the renderer."""
    return [V('fill_dash', -0.39, GAUGE_BOTTOM, 0.465, -0.29, 0.89, 0.48, 'fuel'),
            V('fill_rear', -0.89, GAUGE_BOTTOM, 1.07, -0.63, 1.11, 1.08, 'fuel')]


GAUGE_BOTTOM = 0.67
HEAD_MOUNT = (0.0, 1.02, -0.90)   # vehicle blocks: where the head's base centre sits
HEAD_SCALE = 0.85


def drill_icon():
    """The whole machine with an iron head, for the item icon (shifted/scaled to fit -16..32)."""
    hx, hy, hz = vp(*HEAD_MOUNT)
    s = HEAD_SCALE
    head = Affine(s, [hx - 8 * s, hy - 8 * s, hz - 17 * s])
    raw = drill_body() + [b.moved(head) for b in drill_head('iron')]
    pts = [p for b in raw for p in corners(b)]
    lo = [min(p[i] for p in pts) for i in range(3)]
    hi = [max(p[i] for p in pts) for i in range(3)]
    k = min(1.0, 46.0 / max(hi[i] - lo[i] for i in range(3)))
    fit = Affine(k, [8 - k * (lo[i] + hi[i]) / 2 for i in range(3)])
    return [b.moved(fit) for b in raw]


def shift_after(first, second):
    return Affine(first.k * second.k, [first.t[i] * second.k + second.t[i] for i in range(3)])


def canister(full):
    body = 'red' if full else 'grey'
    P = [B('body', 3, 0, 5.5, 13, 13, 10.5, body, deco=[('emboss', 'sides_z', None)]),
         B('rim', 2.7, 0, 5.2, 13.3, 0.8, 10.8, 'dark'),
         B('shoulder', 3.4, 13, 5.9, 12.6, 13.6, 10.1, body),
         B('handle_l', 4.2, 13.6, 7.3, 5.4, 16, 8.7, body),
         B('handle_m', 6.6, 13.6, 7.3, 7.8, 16, 8.7, body),
         B('handle_top', 4.2, 15.2, 7.3, 11.0, 16.2, 8.7, body),
         B('handle_r', 9.8, 13.6, 7.3, 11.0, 16, 8.7, body)]
    P += ycyl('spout', 2.4, 11.6, 8, 13.4, 15.6, 'dark')
    if full:
        P += [B('label', 5, 4, 5.3, 11, 9, 5.5, 'label', faces=['north']),
              B('label_b', 5, 4, 10.5, 11, 9, 10.7, 'label', faces=['south'])]
    return flatten(P)


def refinery(lit):
    P = [B('plinth', 0, 0, 0, 16, 2, 16, 'dark', deco=[('rivets', 'around', None)]),
         B('firebox', 1, 2, 1, 15, 11, 15, 'brick'),
         B('door', 4, 3, 0.4, 12, 9.2, 1.0, 'steel', deco=[('rivets', 'front', None)]),
         B('window', 5.2, 4.2, 0.2, 10.8, 7.2, 0.5, 'fire' if lit else 'soot', faces=['north']),
         B('top', 0.5, 11, 0.5, 15.5, 12.6, 15.5, 'steel', deco=[('bolts', 'top', None)]),
         B('dial', 6.6, 9.4, 0.3, 9.4, 10.8, 1.0, 'lamp' if lit else 'steel'),
         B('dial_needle', 7.8, 9.8, 0.2, 8.2, 10.6, 0.3, 'knob', faces=['north'])]
    P += ycyl('riser', 9, 8, 8, 12.6, 16, 'steel', deco=[('bands', 'around_y', None)])
    # copper feed pipe up the back, valve wheel on the right
    P += ycyl('feed', 2.6, 12.5, 13.4, 12.6, 16, 'copper')
    P += xcyl('feed_in', 2.6, 6.5, 13.4, 15, 16, 'copper')
    P += [B('valve_stem', 15, 5.6, 7.4, 15.8, 6.6, 8.6, 'dark'),
          B('valve_a', 15.6, 3.5, 7.6, 16, 8.7, 8.4, 'knob', rot=('x', 45, (15.8, 6.1, 8))),
          B('valve_b', 15.6, 3.5, 7.6, 16, 8.7, 8.4, 'knob', rot=('x', -45, (15.8, 6.1, 8)))]
    return flatten(P)


def column(top):
    h = 10 if top else 16
    P = []
    P += ycyl('vessel', 11, 8, 8, 0, h, 'steel', deco=[('plates', 'around_y', None)])
    for k, y in enumerate((0, 7.4) if top else (0, 7.4, 15)):
        P += ycyl('flange%d' % k, 12.6, 8, 8, y, y + 1, 'dark', deco=[('bolts', 'around_y', None)])
    # porthole with the glowing distillate, side outlet pipe with a valve, ladder on the back
    P += [B('port', 6.5, 3.5, 2.2, 9.5, 6.5, 2.7, 'dark'), B('port_glass', 7, 4, 2.0, 9, 6, 2.3, 'fuel', faces=['north'])]
    P += xcyl('outlet', 2.4, 11 if not top else 6, 8, 13, 16, 'copper')
    P += [B('outlet_valve', 14, 11.5 if not top else 6.5, 7.2, 15, 13 if not top else 8, 8.8, 'knob')]
    for side in (-1, 1):
        x = 8 + side * 2.2
        P.append(B('rail', x - 0.4, 0, 13.4, x + 0.4, h, 14.2, 'dark'))
    for k in range(4 if not top else 2):
        y = 2 + k * 4
        P.append(B('rung%d' % k, 5.8, y, 13.6, 10.2, y + 0.6, 14.0, 'steel'))
    if top:
        P += ycyl('dome', 9, 8, 8, 10, 11.5, 'steel')
        P += ycyl('dome2', 6, 8, 8, 11.5, 12.6, 'dark')
        P += ycyl('stack', 2.2, 8, 8, 12.6, 15.2, 'steel')
        P += ycyl('flame', 1.6, 8, 8, 15.2, 16, 'fire')
    return flatten(P)


# name -> (boxes factory, texel density, texture folder, display kind)
MODELS = {
    'drill_body': (drill_body, 1, 'item', None),
    'drill_gauge': (drill_gauge, 1, 'item', None),
    'mining_drill': (drill_icon, 1, 'item', 'vehicle'),
    'empty_canister': (lambda: canister(False), 2, 'item', 'canister'),
    'gasoline_canister': (lambda: canister(True), 2, 'item', 'canister'),
    'refinery': (lambda: refinery(False), 2, 'block', 'block'),
    'refinery_lit': (lambda: refinery(True), 2, 'block', 'block'),
    'distillation_column': (lambda: column(False), 2, 'block', 'block'),
    'distillation_column_top': (lambda: column(True), 2, 'block', 'block'),
}
for _m in HEADS:
    MODELS[_m + '_drill_head'] = ((lambda m: lambda: drill_head(m))(_m), 2, 'item', 'head')


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
    if f.kind in ('glow', 'glass') or f.w < 2 or f.h < 2:
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
                    f.img.set(x, y, MATERIALS['paint'][1][3])
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
    return 'drillworks:%s/%s' % (MODELS[name][2], name)


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
    imgs = [view(['mining_drill'], [25, 215, 0], 400), view(['mining_drill'], [15, 125, 0], 400),
            view(['mining_drill'], [35, 20, 0], 400)]
    imgs += [view([m + '_drill_head'], [70, -45, 0], 200) for m in HEADS]
    imgs += [view(['gasoline_canister'], [20, 210, 0], 200), view(['empty_canister'], [20, 210, 0], 200)]
    imgs.append(view(['refinery_lit', 'distillation_column', 'distillation_column_top'], [25, 215, 0], 400,
                     offsets=[(0, 0, 0), (0, 16, 0), (0, 32, 0)]))
    imgs.append(view(['refinery_lit', 'distillation_column', 'distillation_column_top'], [20, 35, 0], 400,
                     offsets=[(0, 0, 0), (0, 16, 0), (0, 32, 0)]))
    sheet(imgs[:3] + imgs[-2:], 3).save(os.path.join(BUILD, 'preview_big.png'))
    sheet(imgs[3:-2], 5).save(os.path.join(BUILD, 'preview_items.png'))
    for n in MODELS:
        b, atlas, _, _ = build(n)
        print('%-28s %3d boxes  atlas %dx%d' % (n, len(b), atlas.w, atlas.h))


if __name__ == '__main__':
    main()

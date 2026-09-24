#!/usr/bin/env python3
"""The F-14 Tomcat at 1:1 scale (1 block = 1 metre): geometry, a painted texture atlas, the mesh file the game renders,
the item icon and preview renders.

Frame (metres): x = right (starboard), y = up (0 = engine centreline), z = aft (the nose points to -z, like every model
in the mod), origin at the centre of gravity. `s` below is the distance aft of the nose tip, z = s - S_CG.

The airframe is built from quads: lofts of cross-sections (fuselage, nacelles, canopy), 'slabs' for anything
wing-like (wings, tails, gloves: a planform with an airfoil-ish thickness) and boxes for the small parts (gear,
seats, crew, missiles), which reuse gun_models' boxes and their painter. Every quad gets its own patch in one atlas.
Airframe patches are painted from 3D position (paint scheme, panel lines, rivets, markings, weathering), so paint runs
continuously over neighbouring quads; each texel is mapped exactly as the game draws it (two affine triangles).

The mesh is a list of named parts. The body is static; the rest move in the renderer (wings sweep, flaps, slats,
spoilers, stabilators, rudders, speed brakes, canopy, gear + doors, nozzles, fans, ladder) or are switched on and off
(crew, missiles, bombs, rockets, lights). Each part carries the pivot and hinge axis it moves about.

  py tools/vehicle_models.py          -> build/f14_*.png previews
  gen_textures.py / gen_data.py call write_assets() for the atlas, the mesh and the item icon.
"""
import math
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixels import Img, hexc, mix  # noqa: E402
import gun_models as gm  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources'))
ASSETS = os.path.join(RES, 'assets', 'arsenal')
BUILD = os.path.normpath(os.path.join(HERE, '..', 'build'))

SKIN_TEXELS = 16        # texels per metre on the airframe: the same density as a Minecraft block
DETAIL_TEXELS = 32      # small parts (via gun_models boxes: 2 texels per 1/16 m)
S_CG = 10.5             # the centre of gravity, metres aft of the nose tip
GEAR_HEIGHT = 1.8       # the centre of gravity above the ground with the gear down (F14Entity.GEAR_HEIGHT)
ATLAS_W = 2048


def Z(s):
    return s - S_CG


# =====================================================================================================
# vector helpers
# =====================================================================================================

def v_add(a, b, k=1.0):
    return (a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k)


def v_sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def v_scale(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def v_dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def v_cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def v_len(a):
    return math.sqrt(v_dot(a, a))


def v_norm(a):
    n = v_len(a)
    return (a[0] / n, a[1] / n, a[2] / n) if n > 1e-12 else (0.0, 1.0, 0.0)


def v_lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)


def rot_axis(p, pivot, axis, ang):
    """Rotate p about the line through pivot along axis (Rodrigues)."""
    k = v_norm(axis)
    v = v_sub(p, pivot)
    c, s = math.cos(ang), math.sin(ang)
    r = v_add(v_add(v_scale(v, c), v_scale(v_cross(k, v), s)), k, v_dot(k, v) * (1 - c))
    return v_add(pivot, r)


def mirror(p):
    return (-p[0], p[1], p[2])


# =====================================================================================================
# quads and parts
# =====================================================================================================

class Quad:
    """Four corners (an 'outside' side is given by the winding: normal = (p1-p0) x (p3-p0)), and how to paint it."""
    __slots__ = ('p', 'n', 'paint', 'img', 'density', 'ctx', 'double')

    def __init__(self, p, paint, density=SKIN_TEXELS, ctx=None, img=None):
        self.p = [tuple(c) for c in p]
        self.paint = paint          # f(point, normal, ctx) -> RGBA, or None when img is given
        self.img = img
        self.density = density
        self.ctx = ctx or {}
        self.n = quad_normal(self.p)


def quad_normal(p):
    n = v_cross(v_sub(p[1], p[0]), v_sub(p[3], p[0]))
    if v_len(n) < 1e-12:
        n = v_cross(v_sub(p[2], p[1]), v_sub(p[0], p[1]))
    return v_norm(n)


class Part:
    def __init__(self, name, pivot=(0.0, 0.0, 0.0), axis=(1.0, 0.0, 0.0), translucent=False, emissive=False,
                 parent=None):
        self.name = name
        self.pivot = tuple(pivot)
        self.axis = v_norm(axis)
        self.translucent = translucent
        self.emissive = emissive
        self.parent = parent
        self.retract = 0.0          # an animation range in radians where the renderer needs one (gear retraction)
        self.quads = []

    def add(self, quads):
        self.quads.extend(quads)
        return self

    def mirrored(self, name, parent=None):
        """A left-hand copy of a right-hand part: x flipped, winding reversed so the normals still face out."""
        p = Part(name, mirror(self.pivot), (self.axis[0], -self.axis[1], -self.axis[2]), self.translucent,
                 self.emissive, parent)
        for q in self.quads:
            ctx = dict(q.ctx)
            ctx['mirrored'] = True
            ctx['part'] = name
            nq = Quad([mirror(c) for c in reversed(q.p)], q.paint, q.density, ctx,
                      q.img.flipped_x() if q.img is not None else None)
            p.quads.append(nq)
        return p


def orient_out(quads, centre_fn):
    """Flip quads whose normal points towards the given centre (used for closed lofts)."""
    out = []
    for q in quads:
        c = centre_fn(q)
        mid = v_scale(v_add(v_add(q.p[0], q.p[1]), v_add(q.p[2], q.p[3])), 0.25)
        if v_dot(q.n, v_sub(mid, c)) < 0:
            q = Quad(list(reversed(q.p)), q.paint, q.density, q.ctx, q.img.flipped_x() if q.img else None)
        out.append(q)
    return out


# =====================================================================================================
# builders
# =====================================================================================================

def loft(rings, paint, ctx=None, closed=True, skip=None, density=SKIN_TEXELS):
    """Quads between consecutive rings (lists of 3D points of equal length). skip(i_ring, j_seg) -> True drops one."""
    quads = []
    n = len(rings[0])
    segs = n if closed else n - 1
    for i in range(len(rings) - 1):
        a, b = rings[i], rings[i + 1]
        for j in range(segs):
            if skip and skip(i, j):
                continue
            k = (j + 1) % n
            quads.append(Quad([a[j], a[k], b[k], b[j]], paint, density, ctx))
    return quads


def cap(ring, paint, ctx=None, density=SKIN_TEXELS, reverse=False):
    """Close a ring with a fan of quads (pairs of triangles) around its centre."""
    c = v_scale(ring[0], 0.0)
    for p in ring:
        c = v_add(c, p)
    c = v_scale(c, 1.0 / len(ring))
    quads = []
    n = len(ring)
    for j in range(0, n, 2):
        a, b, d = ring[j], ring[(j + 1) % n], ring[(j + 2) % n]
        pts = [c, a, b, d] if not reverse else [c, d, b, a]
        quads.append(Quad(pts, paint, density, ctx))
    return quads


def superellipse(cx, cy, a, b_top, b_bot, n, count, z, start_top=True):
    """Points around a superellipse (clockwise seen from behind, starting at the top)."""
    pts = []
    for k in range(count):
        t = 2 * math.pi * k / count
        s, c = math.sin(t), math.cos(t)
        x = a * math.copysign(abs(s) ** (2.0 / n), s)
        yy = math.copysign(abs(c) ** (2.0 / n), c)
        y = cy + (b_top if yy >= 0 else b_bot) * yy
        pts.append((cx + x, y, z))
    return pts


def box(x0, y0, z0, x1, y1, z1, paint, ctx=None, density=DETAIL_TEXELS, faces='nsewud'):
    """An axis-aligned box (metres) painted by a position painter."""
    quads = []
    P = lambda x, y, z: (x, y, z)  # noqa: E731
    if 'n' in faces:
        quads.append(Quad([P(x1, y1, z0), P(x0, y1, z0), P(x0, y0, z0), P(x1, y0, z0)], paint, density, ctx))
    if 's' in faces:
        quads.append(Quad([P(x0, y1, z1), P(x1, y1, z1), P(x1, y0, z1), P(x0, y0, z1)], paint, density, ctx))
    if 'e' in faces:
        quads.append(Quad([P(x1, y1, z1), P(x1, y1, z0), P(x1, y0, z0), P(x1, y0, z1)], paint, density, ctx))
    if 'w' in faces:
        quads.append(Quad([P(x0, y1, z0), P(x0, y1, z1), P(x0, y0, z1), P(x0, y0, z0)], paint, density, ctx))
    if 'u' in faces:
        quads.append(Quad([P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)], paint, density, ctx))
    if 'd' in faces:
        quads.append(Quad([P(x0, y0, z1), P(x1, y0, z1), P(x1, y0, z0), P(x0, y0, z0)], paint, density, ctx))
    return quads


def cylinder(p0, p1, r0, r1, paint, ctx=None, sides=10, density=DETAIL_TEXELS, caps=True):
    """A tapered round part from p0 to p1."""
    axis = v_norm(v_sub(p1, p0))
    ref = (0.0, 1.0, 0.0) if abs(axis[1]) < 0.9 else (1.0, 0.0, 0.0)
    u = v_norm(v_cross(axis, ref))
    w = v_cross(u, axis)
    ring = lambda p, r: [v_add(v_add(p, u, r * math.cos(2 * math.pi * k / sides)),  # noqa: E731
                               w, r * math.sin(2 * math.pi * k / sides)) for k in range(sides)]
    a, b = ring(p0, r0), ring(p1, r1)
    quads = loft([a, b], paint, ctx, density=density)
    if caps:
        quads += cap(list(reversed(a)), paint, ctx, density)
        quads += cap(b, paint, ctx, density)
    return orient_out(quads, lambda q: v_add(p0, axis, v_dot(v_sub(q.p[0], p0), axis)))


def slab(origin, span_axis, chord_axis, up_axis, planform, thickness, paint, ctx=None, ridge=0.4,
         density=SKIN_TEXELS, caps=('root', 'tip'), chord_ref=None, t_edge=0.012, edges=('le', 'te')):
    """A wing-like solid. planform = (le_root, le_tip, te_tip, te_root) as (span, chord) pairs in the slab plane
    (chord grows aft). thickness(span_frac, chord_frac) -> metres; the section is a flat hexagon with its ridge at
    `ridge` chord. chord_ref = (le_root, te_root, le_tip, te_tip) of the complete surface when this slab is a piece of
    it (flaps, slats), so the thickness is taken from the whole wing."""
    le_r, le_t, te_t, te_r = planform
    ref = chord_ref or (le_r, te_r, le_t, te_t)

    def world(sp, ch, h):
        return v_add(v_add(v_add(origin, span_axis, sp), chord_axis, ch), up_axis, h)

    def frac(sp, ch):
        s0, s1 = ref[0][0], ref[2][0]
        sf = 0.0 if abs(s1 - s0) < 1e-9 else (sp - s0) / (s1 - s0)
        lec = ref[0][1] + (ref[2][1] - ref[0][1]) * sf
        tec = ref[1][1] + (ref[3][1] - ref[1][1]) * sf
        cf = (ch - lec) / max(1e-6, tec - lec)
        return sf, cf

    def half(sp, ch):
        sf, cf = frac(sp, ch)
        t = thickness(max(0.0, min(1.0, sf)), cf)
        if cf <= ridge:
            k = max(0.0, cf) / ridge
        else:
            k = max(0.0, 1.0 - cf) / (1.0 - ridge)
        return max(t_edge, 0.5 * t * (k ** 0.6))

    def section(t):
        sp = le_r[0] + (le_t[0] - le_r[0]) * t
        le = le_r[1] + (le_t[1] - le_r[1]) * t
        te = te_r[1] + (te_t[1] - te_r[1]) * t
        sp_te = te_r[0] + (te_t[0] - te_r[0]) * t
        pts = []
        for cf in (0.0, ridge, 1.0):
            ch = le + (te - le) * cf
            s = sp + (sp_te - sp) * cf
            h = half(s, ch)
            pts.append((s, ch, h))
        return pts

    steps = 3
    rows = [section(i / float(steps)) for i in range(steps + 1)]
    quads = []
    for i in range(steps):
        a, b = rows[i], rows[i + 1]
        for j in range(2):
            # top: le -> ridge -> te; bottom mirrored
            quads.append(Quad([world(a[j][0], a[j][1], a[j][2]), world(b[j][0], b[j][1], b[j][2]),
                               world(b[j + 1][0], b[j + 1][1], b[j + 1][2]), world(a[j + 1][0], a[j + 1][1], a[j + 1][2])],
                              paint, density, dict(ctx or {}, side='top')))
            quads.append(Quad([world(a[j + 1][0], a[j + 1][1], -a[j + 1][2]), world(b[j + 1][0], b[j + 1][1], -b[j + 1][2]),
                               world(b[j][0], b[j][1], -b[j][2]), world(a[j][0], a[j][1], -a[j][2])],
                              paint, density, dict(ctx or {}, side='bottom')))
        # leading and trailing edge strips (left out where two pieces meet, so they never z-fight)
        if 'le' in edges:
            quads.append(Quad([world(a[0][0], a[0][1], -a[0][2]), world(b[0][0], b[0][1], -b[0][2]),
                               world(b[0][0], b[0][1], b[0][2]), world(a[0][0], a[0][1], a[0][2])],
                              paint, density, dict(ctx or {}, side='le')))
        if 'te' in edges:
            quads.append(Quad([world(a[2][0], a[2][1], a[2][2]), world(b[2][0], b[2][1], b[2][2]),
                               world(b[2][0], b[2][1], -b[2][2]), world(a[2][0], a[2][1], -a[2][2])],
                              paint, density, dict(ctx or {}, side='te')))
    for which, row in (('root', rows[0]), ('tip', rows[-1])):
        if which not in caps:
            continue
        ring = [world(row[0][0], row[0][1], row[0][2]), world(row[1][0], row[1][1], row[1][2]),
                world(row[2][0], row[2][1], row[2][2]), world(row[2][0], row[2][1], -row[2][2]),
                world(row[1][0], row[1][1], -row[1][2]), world(row[0][0], row[0][1], -row[0][2])]
        q1 = Quad([ring[0], ring[1], ring[4], ring[5]], paint, density, dict(ctx or {}, side='cap'))
        q2 = Quad([ring[1], ring[2], ring[3], ring[4]], paint, density, dict(ctx or {}, side='cap'))
        quads += [q1, q2]
    centre = world((le_r[0] + le_t[0]) / 2, (le_r[1] + te_t[1]) / 2, 0.0)
    return orient_out(quads, lambda q: centre)


def gm_boxes(boxes, place, texels_scale=1.0):
    """gun_models boxes (1 unit = 1/16 m, centred on 8,8,8 for projectiles) -> quads carrying their painted faces.
    place(p) maps a point in unit space to model metres."""
    quads = []
    for box in gm.flatten(boxes):
        for face in box.faces:
            img = gm.paint(box, face)
            corners = gm.FACE_CORNERS[face](box.frm, box.to)
            pts = [place(gm.rotate_point(box, c)) for c in corners]
            q = Quad(pts, None, DETAIL_TEXELS, {'glow': box.glow}, img=img)
            quads.append(q)
    return quads


# =====================================================================================================
# paint
# =====================================================================================================

GULL = hexc('#9da1a3')        # FS 36440 light gull grey
GULL_DARK = hexc('#8a8e90')
WHITE = hexc('#e4e5e1')
BLACK = hexc('#17181b')
ANTIGLARE = hexc('#26282b')
RADOME = hexc('#8f918d')
LINE = hexc('#6f7376')
TAIL_BLACK = hexc('#141518')
YELLOW = hexc('#e7b928')
RED = hexc('#b3261e')
BLUE = hexc('#1f3b73')
METAL = gm.MATERIALS['steel']


def noise(x, y, seed):
    return (zlib.crc32(b'%d,%d,%d' % (x, y, seed)) & 0xFFFF) / 65535.0


def hash01(*vals):
    return (zlib.crc32(','.join('%d' % v for v in vals).encode()) & 0xFFFF) / 65535.0


# ---- a 5x7 pixel font -------------------------------------------------------------------------------------------

FONT = {
    'A': ['01110', '10001', '10001', '11111', '10001', '10001', '10001'],
    'B': ['11110', '10001', '10001', '11110', '10001', '10001', '11110'],
    'C': ['01111', '10000', '10000', '10000', '10000', '10000', '01111'],
    'D': ['11110', '10001', '10001', '10001', '10001', '10001', '11110'],
    'E': ['11111', '10000', '10000', '11110', '10000', '10000', '11111'],
    'F': ['11111', '10000', '10000', '11110', '10000', '10000', '10000'],
    'G': ['01111', '10000', '10000', '10011', '10001', '10001', '01111'],
    'H': ['10001', '10001', '10001', '11111', '10001', '10001', '10001'],
    'I': ['11111', '00100', '00100', '00100', '00100', '00100', '11111'],
    'J': ['00111', '00010', '00010', '00010', '00010', '10010', '01100'],
    'K': ['10001', '10010', '10100', '11000', '10100', '10010', '10001'],
    'L': ['10000', '10000', '10000', '10000', '10000', '10000', '11111'],
    'M': ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
    'N': ['10001', '11001', '10101', '10011', '10001', '10001', '10001'],
    'O': ['01110', '10001', '10001', '10001', '10001', '10001', '01110'],
    'P': ['11110', '10001', '10001', '11110', '10000', '10000', '10000'],
    'R': ['11110', '10001', '10001', '11110', '10100', '10010', '10001'],
    'S': ['01111', '10000', '10000', '01110', '00001', '00001', '11110'],
    'T': ['11111', '00100', '00100', '00100', '00100', '00100', '00100'],
    'U': ['10001', '10001', '10001', '10001', '10001', '10001', '01110'],
    'V': ['10001', '10001', '10001', '10001', '10001', '01010', '00100'],
    'W': ['10001', '10001', '10001', '10101', '10101', '11011', '10001'],
    'X': ['10001', '10001', '01010', '00100', '01010', '10001', '10001'],
    'Y': ['10001', '10001', '01010', '00100', '00100', '00100', '00100'],
    '0': ['01110', '10001', '10011', '10101', '11001', '10001', '01110'],
    '1': ['00100', '01100', '00100', '00100', '00100', '00100', '01110'],
    '2': ['01110', '10001', '00001', '00110', '01000', '10000', '11111'],
    '3': ['11110', '00001', '00001', '01110', '00001', '00001', '11110'],
    '4': ['00010', '00110', '01010', '10010', '11111', '00010', '00010'],
    '5': ['11111', '10000', '11110', '00001', '00001', '10001', '01110'],
    '6': ['00110', '01000', '10000', '11110', '10001', '10001', '01110'],
    '7': ['11111', '00001', '00010', '00100', '01000', '01000', '01000'],
    '8': ['01110', '10001', '10001', '01110', '10001', '10001', '01110'],
    '9': ['01110', '10001', '10001', '01111', '00001', '00010', '01100'],
    '-': ['00000', '00000', '00000', '11111', '00000', '00000', '00000'],
    ' ': ['00000'] * 7,
}


def text_bitmap(text, scale=1):
    """Rows of booleans for a string (1 texel gap between letters)."""
    rows = []
    for r in range(7):
        row = []
        for ch in text:
            g = FONT.get(ch, FONT[' '])[r]
            for c in g:
                row.extend([c == '1'] * scale)
            row.extend([False] * scale)
        for _ in range(scale):
            rows.append(list(row[:-scale]))
    return rows


SKULL = [
    '.......XXXXXXXXXX.......',
    '.....XXXXXXXXXXXXXX.....',
    '....XXXXXXXXXXXXXXXX....',
    '...XXXXXXXXXXXXXXXXXX...',
    '...XXXXXXXXXXXXXXXXXX...',
    '...XXXX....XX....XXXX...',
    '...XXX......X.....XXX...',
    '...XXX.....XX.....XXX...',
    '...XXXX...XXXX...XXXX...',
    '....XXXXXXX..XXXXXXX....',
    '.....XXXXXX..XXXXXX.....',
    '......XXXXXXXXXXXX......',
    '.......X.XX.XX.X........',
    '.......XXXXXXXXXX.......',
    '........................',
    'XXX..................XXX',
    'XXXX................XXXX',
    '..XXXX............XXXX..',
    '....XXXX........XXXX....',
    '......XXXX....XXXX......',
    '........XXXXXXXX........',
    '......XXXX....XXXX......',
    '....XXXX........XXXX....',
    '..XXXX............XXXX..',
    'XXXX................XXXX',
    'XXX..................XXX',
]


def star_bar_bitmap():
    """The US national insignia: a white star in a blue disc with white bars and a red stripe (40 x 17)."""
    w, h = 40, 17
    img = [[None] * w for _ in range(h)]
    cx, cy, r = 20, 8, 8.2
    star = [(cx + r * 0.95 * math.cos(math.radians(-90 + 72 * k)), cy + r * 0.95 * math.sin(math.radians(-90 + 72 * k)))
            for k in range(5)]
    inner = [(cx + r * 0.38 * math.cos(math.radians(-54 + 72 * k)), cy + r * 0.38 * math.sin(math.radians(-54 + 72 * k)))
             for k in range(5)]
    poly = []
    for k in range(5):
        poly.append(star[k])
        poly.append(inner[k])
    from pixels import point_in_poly
    for y in range(h):
        for x in range(w):
            px, py = x + 0.5, y + 0.5
            if 3 <= y <= 13 and (x < 12 or x >= 28) and 1 <= x <= 38:
                c = WHITE
                if 7 <= y <= 9:
                    c = RED
                if y in (3, 13) or x in (1, 38):
                    c = BLUE
                img[y][x] = c
            if math.hypot(px - cx, py - cy) <= r:
                img[y][x] = BLUE
            if point_in_poly(px, py, poly):
                img[y][x] = WHITE
    return img


STAR_BAR = star_bar_bitmap()


class Decal:
    """A flat image projected onto the airframe: `origin` is its top-left corner, u runs right and v down (metres
    per texel along each), only surfaces whose normal faces `facing` (within ~70 deg) receive it."""

    def __init__(self, bitmap, origin, u, v, facing, colour_fn, parts=None, depth=0.6):
        self.bitmap = bitmap
        self.origin = origin
        self.u = u
        self.v = v
        self.facing = v_norm(facing)
        self.colour_fn = colour_fn
        self.parts = parts
        self.depth = depth
        self.uu = v_dot(u, u)
        self.vv = v_dot(v, v)

    def sample(self, p, n, part):
        if self.parts is not None and part not in self.parts:
            return None
        if v_dot(n, self.facing) < 0.35:
            return None
        d = v_sub(p, self.origin)
        # stay on the near surface: ignore points far behind the decal plane
        if abs(v_dot(d, self.facing)) > self.depth:
            return None
        i = int(math.floor(v_dot(d, self.u) / self.uu))
        j = int(math.floor(v_dot(d, self.v) / self.vv))
        if 0 <= j < len(self.bitmap) and 0 <= i < len(self.bitmap[0]):
            return self.colour_fn(self.bitmap[j][i], i, j)
        return None


def texel(m):
    return 1.0 / SKIN_TEXELS * m


DECALS = []


def add_text(text, s_left, y_top, side, colour=BLACK, height_px=7, x_surface=0.8, parts=('body',)):
    """Black lettering on a fuselage side: side = +1 right (reads front to back), -1 left (reads back to front)."""
    bm = text_bitmap(text, max(1, height_px // 7))
    w = len(bm[0]) * texel(1)
    # seen from the right the nose is on the viewer's right, so the text runs towards the nose there
    if side > 0:
        origin = (x_surface, y_top, Z(s_left) + w)
        u = (0.0, 0.0, -texel(1))
    else:
        origin = (-x_surface, y_top, Z(s_left))
        u = (0.0, 0.0, texel(1))
    DECALS.append(Decal(bm, origin, u, (0.0, -texel(1), 0.0), (side, 0.0, 0.0),
                        lambda on, i, j: colour if on else None, parts))


def add_bitmap(rows, s_left, y_top, side, palette, x_surface, parts=('body',), px=1.0):
    w = len(rows[0]) * texel(px)
    if side > 0:
        origin, u = (x_surface, y_top, Z(s_left) + w), (0.0, 0.0, -texel(px))
    else:
        origin, u = (-x_surface, y_top, Z(s_left)), (0.0, 0.0, texel(px))
    DECALS.append(Decal(rows, origin, u, (0.0, -texel(px), 0.0), (side, 0.0, 0.0),
                        lambda c, i, j: palette(c), parts))


# station lines (frames) along the fuselage and their rivets
FRAMES = [1.25, 2.62, 3.55, 4.42, 5.2, 6.15, 7.05, 8.35, 9.5, 10.75, 12.0, 13.35, 14.7, 16.0, 17.15, 18.2]


def near_line(value, lines_, width):
    for l in lines_:
        if abs(value - l) < width:
            return l
    return None


def skin(p, n, ctx):
    """The airframe painter: scheme, panel lines, rivets, patchwork, weathering and decals."""
    part = ctx.get('part', 'body')
    region = ctx.get('region', 'fuselage')
    s = p[2] + S_CG
    x, y = p[0], p[1]
    ax = abs(x)
    under = n[1] < -0.35 or (region in ('wing', 'glove', 'stab', 'flap', 'slat') and ctx.get('side') == 'bottom')
    # base colours by region
    if region == 'radome':
        base = RADOME
    elif region in ('fin', 'rudder'):
        base = TAIL_BLACK
    elif region == 'antiglare':
        base = ANTIGLARE
    elif region == 'nozzle':
        return nozzle_paint(p, n, ctx)
    elif region == 'intake_in':
        depth = max(0.0, min(1.0, (s - 6.2) / 2.2))
        return mix(WHITE, hexc('#3a3d40'), depth ** 0.7)
    elif region == 'cockpit':
        return mix(hexc('#34383c'), hexc('#2a2d31'), noise(int(x * 16), int(p[2] * 16), 3))
    else:
        base = WHITE if under else GULL
    if region in ('fin', 'rudder'):
        h = (y - 0.58) / math.cos(FIN_CANT)
        if h > 2.33:
            return YELLOW if h < 2.47 else TAIL_BLACK
        if 2.29 < h <= 2.33:
            return WHITE
    # the nose gear bay (hidden by its doors unless the gear is down)
    if region == 'fuselage' and 3.55 < s < 4.75 and ax < 0.24 and n[1] < -0.6:
        return hexc('#cfd0cc') if noise(int(x * 16), int(p[2] * 16), 4) > 0.2 else hexc('#a9aaa6')
    # anti-glare panel ahead of the windscreen
    if region == 'fuselage' and 2.9 < s < 4.45 and y > 0.55 and n[1] > 0.5:
        base = ANTIGLARE
    # patchwork: every panel a slightly different touch-up grey
    if base in (GULL, WHITE, TAIL_BLACK):
        fi = sum(1 for f in FRAMES if s > f)
        band = int((y + 3) / 0.55) if region == 'fuselage' else int(ax / 0.9)
        k = hash01(fi, band, 7 if base == GULL else 9)
        base = mix(base, (0, 0, 0, 255), (k - 0.5) * 0.045) if k > 0.5 else mix(base, (255, 255, 255, 255), (0.5 - k) * 0.035)
    c = base
    w = texel(0.55)
    if region in ('fuselage', 'radome', 'nacelle', 'antiglare'):
        if near_line(s, FRAMES, w) is not None and region != 'radome':
            c = mix(c, LINE if base is not TAIL_BLACK else hexc('#2b2d31'), 0.55)
        elif near_line(s, [2.62], w) is not None:
            c = mix(c, LINE, 0.7)
        # longitudinal seams
        if region == 'fuselage' and (abs(y - 0.12) < w or (ax > 0.3 and abs(ax - 0.36) < w and n[1] > 0.6)):
            c = mix(c, LINE, 0.45)
        # rivet rows beside the frames
        fl = near_line(s, FRAMES, texel(2.2))
        if fl is not None and abs(s - fl) > w and int((y + x) * SKIN_TEXELS) % 3 == 0:
            c = mix(c, (255, 255, 255, 255), 0.12)
    elif region in ('wing', 'glove', 'stab', 'fin', 'rudder', 'flap', 'slat', 'ventral'):
        u = ctx.get('_u', 0.0)      # chord fraction
        v = ctx.get('_v', 0.0)      # span position (m)
        if region in ('wing', 'glove', 'stab', 'fin') and (abs(u - 0.15) < 0.012 or abs(u - 0.68) < 0.012):
            c = mix(c, LINE if base is not TAIL_BLACK else hexc('#2b2d31'), 0.5)
        if abs((v % 0.95) - 0.47) < texel(0.5):
            c = mix(c, LINE if base is not TAIL_BLACK else hexc('#2b2d31'), 0.35)
    # exhaust soot and gun smoke
    if region in ('nacelle', 'fuselage') and s > 16.0:
        c = mix(c, hexc('#3b3834'), min(0.55, (s - 16.0) / 3.5) * (0.6 + 0.4 * noise(int(x * 16), int(y * 16), 5)))
    if region == 'fuselage' and x < -0.5 and 4.6 < s < 6.6 and abs(y + 0.28) < 0.28:
        streak = (s - 4.6) / 2.0
        c = mix(c, hexc('#2e2c29'), max(0.0, 0.5 - streak * 0.4) * (0.5 + 0.5 * noise(int(s * 16), int(y * 16), 11)))
    # subtle grime
    g = noise(int(x * 16 + 0.5), int((y + p[2]) * 16 + 0.5), 17)
    if g > 0.93:
        c = mix(c, (0, 0, 0, 255), 0.06)
    # decals last
    for d in DECALS:
        dc = d.sample(p, n, part)
        if dc is not None:
            c = dc
    return c


def nozzle_paint(p, n, ctx):
    s = p[2] + S_CG
    ang = math.atan2(p[1], abs(p[0]) - 1.40)
    petal = int((ang + math.pi) / (2 * math.pi) * 18)
    base = mix(hexc('#6a5f55'), hexc('#4a5566'), hash01(petal, 3))
    heat = max(0.0, min(1.0, (s - 17.1) / 1.3))
    c = mix(base, hexc('#2d2a28'), heat * 0.6)
    frac = ((ang + math.pi) / (2 * math.pi) * 18) % 1.0
    if frac < 0.08:
        c = mix(c, hexc('#1c1b1a'), 0.6)
    return c


def flat(colour, jitter=0.03, seed=1):
    def paint(p, n, ctx):
        k = noise(int(p[0] * 32), int((p[1] + p[2]) * 32), seed)
        return mix(colour, (0, 0, 0, 255), k * jitter)
    return paint


def glass_paint(p, n, ctx):
    """Canopy glass: a faint blue-grey tint with streaky reflections (partly transparent)."""
    k = (p[1] * 1.7 + p[2] * 0.6) % 1.0
    a = 70 + int(40 * (1 - abs(k - 0.5) * 2) ** 3)
    base = (150, 180, 200, a)
    if abs(k - 0.35) < 0.04:
        base = (230, 240, 250, 150)
    return base


# =====================================================================================================
# the airframe
# =====================================================================================================

def fuselage_rings():
    """Key sections of the forward fuselage, spine, pancake and beaver tail (right half: top centre -> bottom
    centre), interpolated to a smooth set of rings."""
    # (s, [(x, y) ... 11 points, top centre to bottom centre])
    def sec(top, sill, side_top, side_mid, bottom_side, bottom, w, round_top=True):
        return [(0.0, top), (w * 0.38, top - (top - sill) * 0.12 if round_top else top), (w * 0.72, sill + (top - sill) * 0.45 if round_top else top),
                (w * 0.93, sill), (w, side_top), (w, side_mid), (w * 0.99, (side_mid + bottom_side) / 2),
                (w * 0.93, bottom_side), (w * 0.72, bottom + (bottom_side - bottom) * 0.35), (w * 0.38, bottom + 0.02), (0.0, bottom)]

    def circle(r, cy):
        return [(r * math.sin(math.pi * k / 10), cy + r * math.cos(math.pi * k / 10)) for k in range(11)]

    keys = [
        (0.00, circle(0.015, 0.30)),
        (0.35, circle(0.20, 0.30)),
        (1.00, circle(0.39, 0.30)),
        (1.90, circle(0.54, 0.29)),
        (2.62, circle(0.63, 0.28)),
        (3.55, sec(0.86, 0.80, 0.62, 0.20, -0.30, -0.40, 0.66)),
        (4.42, sec(0.76, 0.74, 0.60, 0.18, -0.36, -0.48, 0.70, round_top=False)),
        (5.50, sec(0.76, 0.76, 0.62, 0.16, -0.42, -0.54, 0.74, round_top=False)),
        (7.00, sec(0.80, 0.80, 0.66, 0.16, -0.44, -0.56, 0.78, round_top=False)),
        (8.30, sec(1.26, 0.84, 0.70, 0.18, -0.40, -0.50, 0.82)),
        (9.60, sec(1.04, 0.74, 0.64, 0.24, -0.30, -0.38, 0.94)),
        (11.2, sec(0.80, 0.66, 0.60, 0.26, -0.24, -0.32, 0.98)),
        (13.5, sec(0.66, 0.60, 0.56, 0.24, -0.18, -0.26, 0.90)),
        (16.0, sec(0.60, 0.56, 0.52, 0.22, -0.12, -0.20, 0.80)),
        (17.6, sec(0.52, 0.48, 0.44, 0.22, -0.02, -0.08, 0.70)),
        (18.7, sec(0.36, 0.34, 0.32, 0.22, 0.10, 0.08, 0.46)),
        (19.1, sec(0.28, 0.27, 0.26, 0.20, 0.13, 0.12, 0.30)),
    ]
    # extra rings between keys for a smoother nose and spine
    stations = []
    for i in range(len(keys) - 1):
        s0, a = keys[i]
        s1, b = keys[i + 1]
        n = 3 if s1 - s0 > 1.0 else 2
        for k in range(n):
            t = k / float(n)
            # ease the nose profile (radome ogive)
            stations.append((s0 + (s1 - s0) * t, [(pa[0] + (pb[0] - pa[0]) * t, pa[1] + (pb[1] - pa[1]) * t)
                                                  for pa, pb in zip(a, b)]))
    stations.append(keys[-1])
    rings = []
    for s, half in stations:
        right = [(x, y, Z(s)) for x, y in half]
        left = [(-x, y, Z(s)) for x, y in reversed(half[1:-1])]
        rings.append((s, right + left))
    return rings


COCKPIT = (4.44, 8.28)        # the cockpit opening, s
SILL_Y = 0.76


def build_fuselage():
    rings = fuselage_rings()
    body = []
    n = len(rings[0][1])
    for i in range(len(rings) - 1):
        s0, a = rings[i]
        s1, b = rings[i + 1]
        for j in range(n):
            k = (j + 1) % n
            mid_s = (s0 + s1) / 2
            # the cockpit opening: the top segments (between the sills) are left open
            top_seg = j in (0, 1, 2, n - 1, n - 2, n - 3)
            if COCKPIT[0] <= mid_s <= COCKPIT[1] and top_seg:
                continue
            region = 'radome' if s1 <= 2.63 else 'fuselage'
            body.append(Quad([a[j], a[k], b[k], b[j]], skin, SKIN_TEXELS, {'part': 'body', 'region': region}))
    centre = lambda q: (0.0, 0.2, (q.p[0][2] + q.p[2][2]) / 2)  # noqa: E731
    return orient_out(body, centre)


def nacelle_rings(side=1):
    """The right (side=1) nacelle from the raked intake lip back to the nozzle."""
    keys = [
        (6.20, 1.30, -0.36, 0.58, 0.60, 0.60, 7.0),
        (7.40, 1.33, -0.30, 0.60, 0.66, 0.62, 5.0),
        (9.40, 1.37, -0.16, 0.63, 0.72, 0.62, 3.4),
        (12.0, 1.40, 0.00, 0.66, 0.66, 0.64, 2.5),
        (15.0, 1.40, 0.00, 0.64, 0.64, 0.62, 2.2),
        (17.1, 1.40, 0.00, 0.58, 0.58, 0.58, 2.0),
    ]
    rings = []
    for i in range(len(keys) - 1):
        a, b = keys[i], keys[i + 1]
        steps = 2
        for k in range(steps):
            t = k / float(steps)
            vals = [a[m] + (b[m] - a[m]) * t for m in range(7)]
            rings.append(vals)
    rings.append(list(keys[-1]))
    out = []
    for vals in rings:
        s, xc, yc, a, bt, bb, nexp = vals
        pts = superellipse(xc * side, yc, a, bt, bb, nexp, 16, Z(s))
        if s < 6.3:
            # rake the lip: the top edge sits further forward than the bottom
            pts = [(px, py, Z(6.05 + (0.24 - py) / 1.2 * 0.75)) for px, py, _ in pts]
        out.append(pts)
    return out


def build_nacelle(side=1):
    rings = nacelle_rings(side)
    ctx = {'part': 'body', 'region': 'nacelle'}
    quads = loft(rings, skin, ctx)
    centre_x = 1.36 * side
    quads = orient_out(quads, lambda q: (centre_x, -0.1, (q.p[0][2] + q.p[2][2]) / 2))
    # intake duct: an inset copy of the lip ring running back to the fan face, facing inwards
    lip = rings[0]
    cx = sum(p[0] for p in lip) / len(lip)
    cy = sum(p[1] for p in lip) / len(lip)
    inner = [(cx + (p[0] - cx) * 0.9, cy + (p[1] - cy) * 0.9, p[2] + 0.02) for p in lip]
    deep = [(cx + (p[0] - cx) * 0.82, cy + (p[1] - cy) * 0.82 + 0.1, Z(8.4)) for p in lip]
    duct = loft([inner, deep], skin, {'part': 'body', 'region': 'intake_in'})
    duct = orient_out(duct, lambda q: (cx, cy, q.p[0][2] + 50.0))  # face inwards: 'out' is towards the axis
    duct = [Quad(list(reversed(q.p)), q.paint, q.density, q.ctx) for q in duct]
    lip_ring = loft([lip, inner], skin, {'part': 'body', 'region': 'nacelle'})
    lip_ring = orient_out(lip_ring, lambda q: (cx, cy, q.p[0][2] + 5.0))
    return quads + duct + lip_ring, (cx, cy + 0.1, Z(8.4))


def wing_thickness(root, tip):
    return lambda sf, cf: root + (tip - root) * sf


def build_glove(side=1):
    """The fixed inboard wing (glove) the outer wing slides into."""
    ctx = {'part': 'body', 'region': 'glove'}
    origin = (0.0, 0.46, 0.0)
    q = slab(origin, (1.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.0, 1.0, 0.0),
             ((0.78, Z(5.9)), (3.0, Z(9.45)), (3.0, Z(12.6)), (0.78, Z(13.4))),
             lambda sf, cf: 0.34 - 0.14 * sf, skin, ctx, ridge=0.35, caps=('tip',))
    return q if side > 0 else [Quad([mirror(c) for c in reversed(qq.p)], qq.paint, qq.density, dict(qq.ctx, mirrored=True))
                               for qq in q]


# outer wing planform at the minimum (20 deg) sweep: (span x, chord z)
WING_PIVOT = (2.72, 0.46, Z(9.6))
W_LE_ROOT = (2.45, Z(8.75))
W_LE_TIP = (9.72, Z(8.75) + (9.72 - 2.45) * math.tan(math.radians(20)))
W_TE_TIP = (9.72, W_LE_TIP[1] + 1.45)
W_TE_ROOT = (2.45, Z(12.55))
WING_REF = (W_LE_ROOT, W_TE_ROOT, W_LE_TIP, W_TE_TIP)
WING_THICK = wing_thickness(0.30, 0.11)


def wing_point(sp, cf):
    """Planform point at span x=sp and chord fraction cf."""
    t = (sp - W_LE_ROOT[0]) / (W_LE_TIP[0] - W_LE_ROOT[0])
    le = W_LE_ROOT[1] + (W_LE_TIP[1] - W_LE_ROOT[1]) * t
    te = W_TE_ROOT[1] + (W_TE_TIP[1] - W_TE_ROOT[1]) * t
    return (sp, le + (te - le) * cf)


def wing_piece(x0, x1, c0, c1, region, caps=(), edges=('le', 'te')):
    ctx = {'region': region}
    origin = (0.0, WING_PIVOT[1], 0.0)
    pl = (wing_point(x0, c0), wing_point(x1, c0), wing_point(x1, c1), wing_point(x0, c1))
    return slab(origin, (1.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.0, 1.0, 0.0), pl, WING_THICK, skin, ctx,
                ridge=0.4, chord_ref=WING_REF, caps=caps, edges=edges)


FLAP_SPAN = (3.25, 8.55)
FLAP_CHORD = 0.74
SLAT_CHORD = 0.13
SPOILER = (3.6, 7.6, 0.56, 0.72)


def build_wing(side=1):
    """The right outer wing and its moving surfaces (in the 20 deg sweep reference pose)."""
    wing = Part('wing_r', WING_PIVOT, (0.0, 1.0, 0.0))
    wing.add(wing_piece(W_LE_ROOT[0], FLAP_SPAN[0], SLAT_CHORD, 1.0, 'wing', caps=('root',), edges=('te',)))
    wing.add(wing_piece(FLAP_SPAN[0], FLAP_SPAN[1], SLAT_CHORD, FLAP_CHORD, 'wing', edges=()))
    wing.add(wing_piece(FLAP_SPAN[1], W_LE_TIP[0], SLAT_CHORD, 1.0, 'wing', caps=('tip',), edges=('te',)))
    # slats: full span, hinged at their trailing edge (top)
    slat = Part('slat_r', parent='wing_r')
    slat.add(wing_piece(W_LE_ROOT[0] + 0.25, W_LE_TIP[0] - 0.1, 0.0, SLAT_CHORD, 'slat', caps=('root', 'tip'),
                        edges=('le',)))
    a = wing_point(W_LE_ROOT[0] + 0.25, SLAT_CHORD)
    b = wing_point(W_LE_TIP[0] - 0.1, SLAT_CHORD)
    top = WING_PIVOT[1] + 0.5 * WING_THICK(0.1, SLAT_CHORD) * 0.8
    slat.pivot = (a[0], top, a[1])
    slat.axis = v_norm((b[0] - a[0], 0.0, b[1] - a[1]))
    # the root and tip strips ahead of the slat stay fixed
    wing.add(wing_piece(W_LE_ROOT[0], W_LE_ROOT[0] + 0.25, 0.0, SLAT_CHORD, 'wing', edges=('le',)))
    wing.add(wing_piece(W_LE_TIP[0] - 0.1, W_LE_TIP[0], 0.0, SLAT_CHORD, 'wing', caps=('tip',), edges=('le',)))
    # flaps: hinged at their leading edge
    flap = Part('flap_r', parent='wing_r')
    flap.add(wing_piece(FLAP_SPAN[0], FLAP_SPAN[1], FLAP_CHORD, 1.0, 'flap', caps=('root', 'tip'), edges=('te',)))
    a = wing_point(FLAP_SPAN[0], FLAP_CHORD)
    b = wing_point(FLAP_SPAN[1], FLAP_CHORD)
    flap.pivot = (a[0], WING_PIVOT[1], a[1])
    flap.axis = v_norm((b[0] - a[0], 0.0, b[1] - a[1]))
    # spoilers: thin plates on the upper surface, hinged at the front
    sp = Part('spoiler_r', parent='wing_r')
    x0, x1, c0, c1 = SPOILER
    corners = [wing_point(x0, c0), wing_point(x1, c0), wing_point(x1, c1), wing_point(x0, c1)]
    ys = [WING_PIVOT[1] + 0.5 * WING_THICK((c[0] - W_LE_ROOT[0]) / 7.27, 0.6) * 0.62 + 0.005 for c in corners]
    pts = [(c[0], yy, c[1]) for c, yy in zip(corners, ys)]
    sp.add([Quad([pts[0], pts[3], pts[2], pts[1]], skin, SKIN_TEXELS, {'region': 'wing', 'side': 'top'}),
            Quad([v_add(pts[0], (0, -0.012, 0)), v_add(pts[1], (0, -0.012, 0)), v_add(pts[2], (0, -0.012, 0)),
                  v_add(pts[3], (0, -0.012, 0))], airbrake_paint, SKIN_TEXELS, {'region': 'wing', 'inner': True})])
    sp.pivot = pts[0]
    sp.axis = v_norm(v_sub(pts[1], pts[0]))
    for part in (wing, slat, flap, sp):
        for q in part.quads:
            q.ctx['part'] = part.name
    return [wing, slat, flap, sp]


def build_stab():
    st = Part('stab_r', (2.0, 0.0, Z(16.2)), (1.0, 0.0, 0.0))
    ctx = {'part': 'stab_r', 'region': 'stab'}
    pl = ((1.9, Z(15.0)), (5.0, Z(15.0) + 3.1), (5.0, Z(15.0) + 4.05), (1.9, Z(18.15)))
    st.add(slab((0.0, 0.0, 0.0), (1.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.0, 1.0, 0.0), pl,
                lambda sf, cf: 0.2 - 0.13 * sf, skin, ctx, ridge=0.35))
    return st


FIN_X = 1.62
FIN_CANT = math.radians(5)


def fin_frame(side=1):
    span = v_norm((math.sin(FIN_CANT) * side, math.cos(FIN_CANT), 0.0))
    normal = v_norm((math.cos(FIN_CANT) * side, -math.sin(FIN_CANT), 0.0))
    return (FIN_X * side, 0.58, 0.0), span, normal


def fin_point(h, cf):
    le = Z(13.15) + h / 2.55 * (Z(15.9) - Z(13.15))
    te = Z(16.95) + h / 2.55 * (Z(17.1) - Z(16.95))
    return (h, le + (te - le) * cf)


RUDDER = (0.42, 2.35, 0.7)


def build_fin():
    origin, span, normal = fin_frame(1)
    ctx = {'part': 'body', 'region': 'fin'}
    ref = (fin_point(0, 0), fin_point(0, 1), fin_point(2.55, 0), fin_point(2.55, 1))
    th = lambda sf, cf: 0.22 - 0.14 * sf  # noqa: E731

    def piece(h0, h1, c0, c1, region, caps=()):
        pl = (fin_point(h0, c0), fin_point(h1, c0), fin_point(h1, c1), fin_point(h0, c1))
        return slab(origin, span, (0.0, 0.0, 1.0), normal, pl, th, skin, dict(ctx, region=region),
                    ridge=0.35, chord_ref=ref, caps=caps)
    body = piece(0.0, 2.55, 0.0, RUDDER[2], 'fin', caps=('tip',))
    body += piece(0.0, RUDDER[0], RUDDER[2], 1.0, 'fin')
    body += piece(RUDDER[1], 2.55, RUDDER[2], 1.0, 'fin', caps=('tip',))
    rud = Part('rudder_r')
    rud.add(piece(RUDDER[0], RUDDER[1], RUDDER[2], 1.0, 'rudder', caps=('root', 'tip')))
    for q in rud.quads:
        q.ctx['part'] = 'rudder_r'
    a = fin_point(RUDDER[0], RUDDER[2])
    b = fin_point(RUDDER[1], RUDDER[2])
    pa = v_add(v_add(origin, span, a[0]), (0.0, 0.0, 1.0), a[1])
    pb = v_add(v_add(origin, span, b[0]), (0.0, 0.0, 1.0), b[1])
    rud.pivot = pa
    rud.axis = v_norm(v_sub(pb, pa))
    return body, rud


def build_ventral(side=1):
    ang = math.radians(35)
    span = v_norm((math.sin(ang) * side, -math.cos(ang), 0.0))
    normal = v_norm((math.cos(ang) * side, math.sin(ang), 0.0))
    origin = (1.55 * side, -0.55, 0.0)
    pl = ((0.0, Z(15.3)), (0.55, Z(16.1)), (0.55, Z(16.9)), (0.0, Z(17.35)))
    return slab(origin, span, (0.0, 0.0, 1.0), normal, pl, lambda sf, cf: 0.08, skin,
                {'part': 'body', 'region': 'ventral'}, ridge=0.4)


def build_nozzle(side=1):
    xc = 1.40 * side
    p = Part('nozzle_r' if side > 0 else 'nozzle_l', (xc, 0.0, Z(17.1)), (0.0, 0.0, 1.0))
    ctx = {'part': p.name, 'region': 'nozzle'}
    rings = []
    for s, r in ((17.1, 0.575), (17.55, 0.56), (18.0, 0.54), (18.4, 0.52)):
        rings.append([(xc + r * math.cos(2 * math.pi * k / 18), r * math.sin(2 * math.pi * k / 18), Z(s)) for k in range(18)])
    outer = orient_out(loft(rings, skin, ctx), lambda q: (xc, 0.0, q.p[0][2]))
    inner_r = [[(xc + (pp[0] - xc) * 0.88, pp[1] * 0.88, pp[2]) for pp in ring] for ring in rings[1:]]
    inner = loft(inner_r, flat(hexc('#1d1c1b'), 0.1), {'part': p.name})
    inner = orient_out(inner, lambda q: (xc, 0.0, q.p[0][2]))
    inner = [Quad(list(reversed(q.p)), q.paint, q.density, q.ctx) for q in inner]
    lip = orient_out(loft([rings[-1], inner_r[-1]], skin, ctx), lambda q: (xc, 0.0, q.p[0][2] - 3.0))
    # the afterburner cavity back wall (flame holders)
    back = cap([(xc + (pp[0] - xc) * 0.88, pp[1] * 0.88, Z(17.35)) for pp in rings[0]],
               holder_paint, {'part': p.name}, DETAIL_TEXELS, reverse=False)
    back = orient_out(back, lambda q: (xc, 0.0, Z(16.0)))
    p.add(outer + inner + lip + back)
    return p


def holder_paint(p, n, ctx):
    r = math.hypot(abs(p[0]) - 1.40, p[1])
    ring = abs(r - 0.25) < 0.04 or abs(r - 0.4) < 0.03
    return hexc('#5a5048') if ring else hexc('#141312')


# ---- canopy -------------------------------------------------------------------------------------------------

def canopy_profile(s):
    """(half width at the sill, top height) of the glass at station s."""
    keys = [(4.44, 0.70, 0.80), (5.16, 0.74, 1.30), (5.8, 0.77, 1.50), (6.5, 0.79, 1.58), (7.2, 0.80, 1.59),
            (7.8, 0.81, 1.50), (8.30, 0.82, 1.27)]
    for i in range(len(keys) - 1):
        a, b = keys[i], keys[i + 1]
        if a[0] <= s <= b[0]:
            t = (s - a[0]) / (b[0] - a[0])
            return a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t
    return keys[-1][1], keys[-1][2]


def canopy_arc(s, grow=0.0, count=12):
    hw, top = canopy_profile(s)
    sill = SILL_Y if s < 8.2 else 0.84
    pts = []
    for k in range(count + 1):
        t = math.pi * k / count          # 0 = left sill, pi/2 = top, pi = right sill
        x = -math.cos(t) * (hw + grow)
        yy = math.sin(t)
        y = sill + (top - sill + grow) * (yy ** 0.8)
        pts.append((x, y, Z(s)))
    return pts


def build_canopy():
    glass_ws = Part('windscreen_glass', translucent=True)
    glass_c = Part('canopy_glass', (0.0, 1.02, Z(8.36)), (1.0, 0.0, 0.0), translucent=True, parent='canopy')
    frame_c = Part('canopy', (0.0, 1.02, Z(8.36)), (1.0, 0.0, 0.0))
    ws_st = [4.46, 4.8, 5.16]
    cn_st = [5.18, 5.6, 6.0, 6.5, 6.95, 7.4, 7.8, 8.05, 8.28]
    glass_ws.add(loft([canopy_arc(s) for s in ws_st], glass_paint, {'part': 'windscreen_glass'}, closed=False))
    glass_c.add(loft([canopy_arc(s) for s in cn_st], glass_paint, {'part': 'canopy_glass'}, closed=False))
    for part in (glass_ws, glass_c):
        part.quads = orient_out(part.quads, lambda q: (0.0, 0.7, q.p[0][2]))
    frame_paint = flat(hexc('#8b8f91'), 0.05, 21)
    fixed_frames = []
    for s in (4.46, 5.16):
        fixed_frames += arch(s, 0.05 if s > 5 else 0.07, frame_paint, 'body')
    for s in (5.19, 6.95, 8.26):
        frame_c.add(arch(s, 0.06 if s != 6.95 else 0.05, frame_paint, 'canopy'))
    # sill rails along the canopy's lower edges
    for side in (-1, 1):
        hw0, _ = canopy_profile(5.2)
        hw1, _ = canopy_profile(8.25)
        frame_c.add(box_between((side * hw0, SILL_Y + 0.02, Z(5.2)), (side * hw1, 0.86, Z(8.26)), 0.04, 0.04,
                                frame_paint, {'part': 'canopy'}))
    # the canopy's rear hinge fairing
    frame_c.add(box(-0.2, 0.9, Z(8.24), 0.2, 1.08, Z(8.5), frame_paint, {'part': 'canopy'}))
    return glass_ws, glass_c, frame_c, fixed_frames


def arch(s, width, paint, part):
    inner = canopy_arc(s, 0.005)
    outer = canopy_arc(s, 0.04)
    quads = []
    for grow_pts, dz in ((outer, 0.0),):
        a = [(p[0], p[1], p[2] - width / 2) for p in grow_pts]
        b = [(p[0], p[1], p[2] + width / 2) for p in grow_pts]
        quads += loft([a, b], paint, {'part': part}, closed=False)
    a = [(p[0], p[1], p[2] - width / 2) for p in inner]
    b = [(p[0], p[1], p[2] + width / 2) for p in inner]
    quads += loft([b, a], paint, {'part': part}, closed=False)
    for k in range(len(outer) - 1):
        for dz in (-width / 2, width / 2):
            q = [(outer[k][0], outer[k][1], outer[k][2] + dz), (outer[k + 1][0], outer[k + 1][1], outer[k + 1][2] + dz),
                 (inner[k + 1][0], inner[k + 1][1], inner[k + 1][2] + dz), (inner[k][0], inner[k][1], inner[k][2] + dz)]
            quads.append(Quad(q, paint, DETAIL_TEXELS, {'part': part}))
    return orient_out(quads, lambda q: (0.0, 0.8, Z(s)))


def box_between(a, b, w, h, paint, ctx):
    """A thin bar from a to b (square section w x h)."""
    d = v_norm(v_sub(b, a))
    up = (0.0, 1.0, 0.0) if abs(d[1]) < 0.9 else (0.0, 0.0, -1.0)
    side = v_norm(v_cross(d, up))
    up2 = v_cross(side, d)
    corners = []
    for p in (a, b):
        for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            corners.append(v_add(v_add(p, side, sx * w / 2), up2, sy * h / 2))
    ra, rb = corners[:4], corners[4:]
    quads = loft([ra, rb], paint, ctx, density=DETAIL_TEXELS)
    quads += [Quad(list(reversed(ra)), paint, DETAIL_TEXELS, ctx), Quad(rb, paint, DETAIL_TEXELS, ctx)]
    mid = v_lerp(a, b, 0.5)
    return orient_out(quads, lambda q: mid)


# ---- engines: the fan faces deep in the intakes ------------------------------------------------------------

def fan_paint(p, n, ctx):
    cx, cy = ctx['centre']
    dx, dy = p[0] - cx, p[1] - cy
    r = math.hypot(dx, dy)
    ang = math.atan2(dy, dx)
    if r < 0.1:
        return hexc('#2b2e31') if r > 0.075 else hexc('#5d6166')
    k = ((ang + r * 2.4) / (2 * math.pi) * 22) % 1.0
    base = mix(hexc('#6c7075'), hexc('#3c4045'), min(1.0, r / 0.5))
    if k < 0.2:
        return mix(base, hexc('#121416'), 0.75)
    if k > 0.84:
        return mix(base, hexc('#b8bcc0'), 0.35)
    return base


def build_fan(centre, side):
    cx, cy, cz = centre
    name = 'fan_r' if side > 0 else 'fan_l'
    part = Part(name, (cx, cy, cz), (0.0, 0.0, 1.0))
    ring = [(cx + 0.47 * math.cos(2 * math.pi * k / 20), cy + 0.47 * math.sin(2 * math.pi * k / 20), cz) for k in range(20)]
    quads = orient_out(cap(ring, fan_paint, {'part': name, 'centre': (cx, cy)}, DETAIL_TEXELS),
                       lambda q: (cx, cy, cz + 1.0))
    quads += cylinder((cx, cy, cz - 0.001), (cx, cy, cz - 0.17), 0.1, 0.015, flat(hexc('#3a3d40'), 0.1, 31),
                      {'part': name}, sides=10)
    part.add(quads)
    return part


# ---- cockpit ------------------------------------------------------------------------------------------------

PANEL = hexc('#30343a')
PANEL_DARK = hexc('#1c1f23')


def gauges_paint(p, n, ctx):
    """Instrument panel face: rows of round gauges with bezels and needles, the odd switch."""
    x, y = p[0], p[1]
    c = mix(PANEL, PANEL_DARK, 0.35 * noise(int(x * 64), int(y * 64), 43))
    gx, gy = (x + 5.0) % 0.13, (y + 5.0) % 0.13
    d = math.hypot(gx - 0.065, gy - 0.065)
    if d < 0.047:
        c = hexc('#0b0c0d')
        if d > 0.038:
            c = hexc('#7d8288')
        elif abs((gx - 0.065) * 0.8 - (gy - 0.065)) < 0.006 and d < 0.03:
            c = hexc('#ece6cf')
    elif noise(int(x * 90), int(y * 90), 44) > 0.93:
        c = hexc('#c9c3a7')
    return c


def console_paint(p, n, ctx):
    """Side consoles: panels of switches and knobs, a few lit legends."""
    x, z = p[0], p[2]
    c = mix(PANEL, PANEL_DARK, 0.3 * noise(int(x * 64), int(z * 64), 45))
    if n[1] > 0.5:
        k = noise(int(x * 40), int(z * 40), 46)
        if k > 0.86:
            c = hexc('#c8c2a5')
        elif k > 0.8:
            c = hexc('#b53a2e')
        elif k < 0.06:
            c = hexc('#e2c34a')
        if abs(((z * 16) % 1.0) - 0.5) < 0.06:
            c = mix(c, hexc('#101214'), 0.6)
    return c


def screen_paint(kind):
    """Green CRT displays: VDI (horizon), HSD (compass rose), TID (round radar picture)."""
    def paint(p, n, ctx):
        cx, cy = ctx['centre']
        dx, dy = p[0] - cx, p[1] - cy
        base = hexc('#08170d')
        g = hexc('#48f07a')
        if kind == 'vdi':
            if abs(dy - dx * 0.25) < 0.004 or abs(dx) < 0.003 and abs(dy) < 0.03:
                return g
            return mix(base, hexc('#12361f'), 0.6 if dy - dx * 0.25 > 0 else 0.1)
        if kind == 'hsd':
            r = math.hypot(dx, dy)
            if abs(r - 0.065) < 0.004 or (abs(dx) < 0.003 and dy > 0):
                return g
            return base
        # tid: range rings and a few contacts
        r = math.hypot(dx, dy)
        if abs(r - 0.04) < 0.003 or abs(r - 0.085) < 0.003 or abs(r - 0.125) < 0.004:
            return mix(base, g, 0.6)
        for tx, ty in ((0.05, 0.06), (-0.07, 0.03), (0.02, -0.08)):
            if abs(dx - tx) < 0.008 and abs(dy - ty) < 0.008:
                return hexc('#b8ffc8')
        if abs(dx) < 0.002 or abs(dy) < 0.002:
            return mix(base, g, 0.35)
        return base
    return paint


def hud_paint(p, n, ctx):
    """The HUD combiner: faint glass with the green reticle."""
    dx, dy = p[0], p[1] - 1.15
    r = math.hypot(dx, dy)
    if abs(r - 0.035) < 0.004 or (abs(dy) < 0.003 and 0.045 < abs(dx) < 0.09) or (abs(dx) < 0.003 and 0.045 < abs(dy) < 0.07):
        return (90, 255, 130, 220)
    return (120, 200, 150, 40)


def inward(quads, centre):
    """Flip quads so they face the given point (walls seen from inside)."""
    out = []
    for q in quads:
        mid = v_scale(v_add(v_add(q.p[0], q.p[1]), v_add(q.p[2], q.p[3])), 0.25)
        if v_dot(q.n, v_sub(centre, mid)) < 0:
            q = Quad(list(reversed(q.p)), q.paint, q.density, q.ctx, q.img.flipped_x() if q.img else None)
        out.append(q)
    return out


def build_cockpit():
    body, displays = [], []
    tub_ctx = {'part': 'body', 'region': 'cockpit'}
    tub = box(-0.66, 0.02, Z(4.5), 0.66, SILL_Y - 0.01, Z(8.25), skin, tub_ctx, SKIN_TEXELS, faces='nsewd')
    body += inward(tub, (0.0, 0.5, Z(6.3)))
    for s0, s1 in ((5.2, 6.15), (6.85, 7.95)):
        for side in (1, -1):
            x0, x1 = sorted((0.41 * side, 0.66 * side))
            body += box(x0, 0.26, Z(s0), x1, 0.5, Z(s1), console_paint, {'part': 'body'}, DETAIL_TEXELS)
    # front instrument panel, glare shield, HUD
    body += box(-0.44, 0.3, Z(4.52), 0.44, 0.93, Z(4.7), gauges_paint, {'part': 'body'}, DETAIL_TEXELS)
    body += box(-0.46, 0.92, Z(4.46), 0.46, 0.99, Z(4.86), flat(hexc('#15171a'), 0.1, 47), {'part': 'body'})
    body += box(-0.11, 0.99, Z(4.7), 0.11, 1.05, Z(4.86), flat(hexc('#15171a'), 0.1, 48), {'part': 'body'})
    for kind, cx, cy, w, h in (('vdi', 0.0, 0.74, 0.12, 0.1), ('hsd', 0.0, 0.5, 0.1, 0.1)):
        displays += box(cx - w, cy - h, Z(4.705), cx + w, cy + h, Z(4.715), screen_paint(kind),
                        {'part': 'displays', 'centre': (cx, cy)}, 64, faces='s')
    # the RIO's panel and its big round TID
    body += box(-0.44, 0.3, Z(6.28), 0.44, 0.92, Z(6.46), gauges_paint, {'part': 'body'}, DETAIL_TEXELS)
    tid = [(0.14 * math.cos(2 * math.pi * k / 16), 0.64 + 0.14 * math.sin(2 * math.pi * k / 16), Z(6.465)) for k in range(16)]
    displays += orient_out(cap(tid, screen_paint('tid'), {'part': 'displays', 'centre': (0.0, 0.64)}, 64),
                           lambda q: (0.0, 0.64, Z(6.0)))
    displays += box(-0.09, 0.8, Z(6.465), 0.09, 0.89, Z(6.47), screen_paint('vdi'), {'part': 'displays', 'centre': (0.0, 0.845)},
                    64, faces='s')
    # stick and throttles
    dark = flat(hexc('#1b1d20'), 0.1, 49)
    body += cylinder((0.0, 0.04, Z(5.62)), (0.0, 0.5, Z(5.5)), 0.022, 0.018, dark, {'part': 'body'}, sides=6)
    body += box(-0.03, 0.5, Z(5.53), 0.03, 0.62, Z(5.47), dark, {'part': 'body'})
    for dx in (-0.03, 0.03):
        body += box(-0.56 + dx, 0.5, Z(5.6), -0.53 + dx, 0.64, Z(5.64), dark, {'part': 'body'})
    for s in (5.95, 7.45):
        body += seat(s)
    hud = Part('hud_glass', translucent=True, emissive=True)
    hud.add(orient_out([Quad([(-0.1, 1.05, Z(4.86)), (0.1, 1.05, Z(4.86)), (0.1, 1.25, Z(4.9)), (-0.1, 1.25, Z(4.9))],
                             hud_paint, 64, {'part': 'hud_glass'})], lambda q: (0.0, 1.1, Z(4.0))))
    disp = Part('displays', emissive=True)
    disp.add(displays)
    return body, hud, disp


SEAT = hexc('#3b4240')
CUSHION = hexc('#5a5a3b')


def striped(c1, c2, period=0.06):
    def paint(p, n, ctx):
        return c1 if ((p[0] + p[1] + p[2]) / period) % 2 < 1 else c2
    return paint


def seat(s):
    """A Martin-Baker GRU-7A ejection seat facing forward, its back at station s."""
    q = []
    frame = flat(SEAT, 0.08, 51)
    cush = flat(CUSHION, 0.12, 52)
    q += box(-0.25, 0.08, Z(s - 0.46), 0.25, 0.3, Z(s), frame, {'part': 'body'})
    q += box(-0.21, 0.3, Z(s - 0.44), 0.21, 0.36, Z(s - 0.02), cush, {'part': 'body'})
    q += box_between((0.0, 0.3, Z(s + 0.06)), (0.0, 1.08, Z(s + 0.2)), 0.44, 0.14, frame, {'part': 'body'})
    q += box_between((0.0, 0.36, Z(s - 0.02)), (0.0, 0.98, Z(s + 0.1)), 0.36, 0.06, cush, {'part': 'body'})
    q += box(-0.2, 1.04, Z(s + 0.1), 0.2, 1.3, Z(s + 0.32), frame, {'part': 'body'})
    q += box(-0.13, 1.3, Z(s + 0.12), 0.13, 1.35, Z(s + 0.2), striped(hexc('#e7c22e'), hexc('#161616')), {'part': 'body'})
    q += box(-0.04, 0.3, Z(s - 0.5), 0.04, 0.37, Z(s - 0.44), striped(hexc('#e7c22e'), hexc('#161616'), 0.03), {'part': 'body'})
    for dx in (-0.24, 0.24):
        q += box_between((dx, 0.28, Z(s + 0.13)), (dx, 1.2, Z(s + 0.27)), 0.04, 0.05, frame, {'part': 'body'})
    return q


SUIT = hexc('#667052')
GSUIT = hexc('#8a7d57')
GLOVE = hexc('#4f3a2a')
BOOT = hexc('#1a1a1a')
HELMET = hexc('#dcdcd4')
VISOR = hexc('#20262c')


def helmet_paint(p, n, ctx):
    if abs(p[0]) < 0.022 and n[1] > 0.3:
        return hexc('#b3261e')
    return mix(HELMET, hexc('#b6b6ae'), 0.3 * noise(int(p[0] * 64), int(p[2] * 64), 53))


def crew(name, s, hands='stick'):
    """A seated crewman in flight gear, back against the seat at station s."""
    part = Part(name)
    ctx = {'part': name}
    suit = flat(SUIT, 0.12, 54)
    hips = (0.0, 0.38, Z(s - 0.08))
    shoulders = (0.0, 0.88, Z(s + 0.04))
    q = []
    q += box_between(hips, shoulders, 0.42, 0.26, suit, ctx)
    q += box_between((0.0, 0.46, Z(s - 0.1)), (0.0, 0.84, Z(s + 0.01)), 0.44, 0.2, flat(GSUIT, 0.1, 55), ctx)
    # helmet, visor, mask
    q += box(-0.15, 0.93, Z(s - 0.14), 0.15, 1.22, Z(s + 0.16), helmet_paint, ctx)
    q += box(-0.14, 1.0, Z(s - 0.155), 0.14, 1.11, Z(s - 0.139), flat(VISOR, 0.05, 56), ctx)
    q += box(-0.07, 0.93, Z(s - 0.2), 0.07, 1.01, Z(s - 0.13), flat(hexc('#55595d'), 0.1, 57), ctx)
    q += box_between((0.0, 0.94, Z(s - 0.18)), (0.1, 0.72, Z(s - 0.15)), 0.035, 0.035, flat(hexc('#3c4044'), 0.1, 58), ctx)
    for dx in (-0.12, 0.12):
        knee = (dx, 0.44, Z(s - 0.56))
        q += box_between((dx, 0.38, Z(s - 0.06)), knee, 0.17, 0.17, suit, ctx)
        q += box_between(knee, (dx, 0.1, Z(s - 0.74)), 0.14, 0.14, flat(GSUIT, 0.1, 59), ctx)
        q += box(dx - 0.07, 0.02, Z(s - 0.86), dx + 0.07, 0.14, Z(s - 0.66), flat(BOOT, 0.05, 60), ctx)
    for side in (-1, 1):
        sh = (0.25 * side, 0.84, Z(s + 0.02))
        elbow = (0.27 * side, 0.58, Z(s - 0.2))
        if hands == 'stick':
            hand = (0.03, 0.56, Z(s - 0.42)) if side > 0 else (-0.5, 0.62, Z(s - 0.34))
        else:
            hand = (0.2 * side, 0.52, Z(s - 0.46))
        q += box_between(sh, elbow, 0.12, 0.12, suit, ctx)
        q += box_between(elbow, hand, 0.1, 0.1, suit, ctx)
        q += box(hand[0] - 0.05, hand[1] - 0.05, hand[2] - 0.05, hand[0] + 0.05, hand[1] + 0.05, hand[2] + 0.05,
                 flat(GLOVE, 0.1, 61), ctx)
    part.add(q)
    return part


# ---- landing gear -------------------------------------------------------------------------------------------

STRUT = hexc('#dfe1de')
PISTON = hexc('#b9c0c7')


def tire_paint(p, n, ctx):
    if abs(n[0]) > 0.7:
        cy, cz = ctx['axle']
        r = math.hypot(p[1] - cy, p[2] - cz)
        if r < ctx['hub']:
            return hexc('#d3d6d8') if r > ctx['hub'] * 0.35 else hexc('#8d9296')
        return hexc('#202022')
    k = (math.atan2(p[1] - ctx['axle'][0], p[2] - ctx['axle'][1]) / (2 * math.pi) * 40) % 1.0
    return hexc('#18181a') if k < 0.5 else hexc('#262628')


def wheel(centre, radius, width, part, sides=14):
    x, y, z = centre
    ctx = {'part': part, 'axle': (y, z), 'hub': radius * 0.55}
    return cylinder((x - width / 2, y, z), (x + width / 2, y, z), radius, radius, tire_paint, ctx, sides=sides)


NOSE_PIVOT = (0.0, -0.42, Z(4.05))
NOSE_AXLE = (0.0, -1.52, Z(4.13))
MAIN_PIVOT = (1.98, -0.3, Z(10.6))
MAIN_AXLE = (2.42, -1.33, Z(11.3))
MAIN_STOWED = (1.72, -0.5, Z(9.3))


def build_gear():
    parts = {}
    white = flat(STRUT, 0.06, 62)
    silver = flat(PISTON, 0.08, 63)
    ng = Part('nose_gear', NOSE_PIVOT, (1.0, 0.0, 0.0))
    ctx = {'part': 'nose_gear'}
    q = cylinder(NOSE_PIVOT, (0.0, -1.12, Z(4.09)), 0.065, 0.06, white, ctx, sides=8)
    q += cylinder((0.0, -1.12, Z(4.09)), NOSE_AXLE, 0.045, 0.045, silver, ctx, sides=8)
    q += box(-0.2, -1.55, Z(4.1), 0.2, -1.49, Z(4.16), silver, ctx)
    q += box_between((0.0, -1.37, Z(4.02)), (0.0, -1.61, Z(3.5)), 0.05, 0.05, white, ctx)
    q += box_between((0.0, -0.8, Z(4.1)), (0.0, -0.47, Z(4.72)), 0.045, 0.045, white, ctx)
    q += box(-0.05, -0.78, Z(3.97), 0.05, -0.7, Z(4.0), flat(hexc('#fff8dc'), 0.02, 64), {'part': 'nose_gear', 'glow': True})
    ng.add(q)
    nw = Part('nose_wheel', NOSE_AXLE, (1.0, 0.0, 0.0), parent='nose_gear')
    for dx in (-0.135, 0.135):
        nw.add(wheel((dx, NOSE_AXLE[1], NOSE_AXLE[2]), 0.28, 0.14, 'nose_wheel'))
    parts['nose_gear'] = ng
    parts['nose_wheel'] = nw
    # nose bay doors, hinged along the bay edges
    dr = Part('nose_door_r', (0.24, -0.47, Z(4.15)), (0.0, 0.0, 1.0))
    door_paint = door_skin
    dr.add(box(0.0, -0.485, Z(3.55), 0.24, -0.465, Z(4.75), door_paint, {'part': 'nose_door_r'}, SKIN_TEXELS))
    parts['nose_door_r'] = dr
    parts['nose_door_l'] = dr.mirrored('nose_door_l')
    # main gear (right; the left one is its mirror image)
    mg = Part('main_gear_r', MAIN_PIVOT, (1.0, 0.0, 0.0))
    ctx = {'part': 'main_gear_r'}
    knee = (2.3, -1.17, Z(11.2))
    q = cylinder(MAIN_PIVOT, knee, 0.09, 0.085, white, ctx, sides=8)
    q += cylinder(knee, (MAIN_AXLE[0] - 0.13, MAIN_AXLE[1], MAIN_AXLE[2]), 0.06, 0.06, silver, ctx, sides=8)
    q += box_between((2.14, -0.8, Z(10.92)), (1.78, -0.32, Z(11.45)), 0.05, 0.05, white, ctx)
    q += box_between((2.02, -0.42, Z(10.55)), (2.27, -0.98, Z(10.95)), 0.62, 0.025, door_skin, ctx)
    mg.add(q)
    mw = Part('main_wheel_r', MAIN_AXLE, (1.0, 0.0, 0.0), parent='main_gear_r')
    mw.add(wheel(MAIN_AXLE, 0.47, 0.26, 'main_wheel_r', sides=16))
    # retraction: the one rotation about the pivot that carries the wheel from down to stowed
    u = v_sub(MAIN_AXLE, MAIN_PIVOT)
    v = v_sub(MAIN_STOWED, MAIN_PIVOT)
    axis = v_norm(v_cross(u, v))
    mg.axis = axis
    mg.retract = math.acos(max(-1.0, min(1.0, v_dot(u, v) / (v_len(u) * v_len(v)))))
    parts['main_gear_r'] = mg
    parts['main_wheel_r'] = mw
    ml = mg.mirrored('main_gear_l')
    ml.retract = mg.retract
    parts['main_gear_l'] = ml
    parts['main_wheel_l'] = mw.mirrored('main_wheel_l', 'main_gear_l')
    # main wheel well doors on the nacelle's lower flank
    wd = Part('main_door_r', (1.99, -0.2, Z(9.9)), (0.0, 0.0, 1.0))
    wd.add(box(1.95, -0.86, Z(8.8), 1.99, -0.2, Z(10.4), door_skin, {'part': 'main_door_r'}, SKIN_TEXELS))
    parts['main_door_r'] = wd
    parts['main_door_l'] = wd.mirrored('main_door_l')
    return parts


def door_skin(p, n, ctx):
    """Gear doors: white with the Navy's red edges."""
    c = mix(WHITE, hexc('#c9cac6'), 0.25 * noise(int(p[0] * 32), int(p[2] * 32), 65))
    edge = ctx.get('_edge')
    return c


# ---- weapons stations ---------------------------------------------------------------------------------------

def store(name, centre, roll_deg=0.0, part=None):
    """One of gun_models' stores placed on the jet (nose towards -z)."""
    spec = gm.AMMO[name]
    unit = spec['unit_m']
    boxes = gm.flatten(spec['parts']())
    zs = [c[2] for b in boxes for c in gm.corners(b)]
    zc = (min(zs) + max(zs)) / 2.0
    cr, sr = math.cos(math.radians(roll_deg)), math.sin(math.radians(roll_deg))

    def place(p):
        x, y, z = p[0] - 8.0, p[1] - 8.0, p[2] - zc
        x, y = x * cr - y * sr, x * sr + y * cr
        return (centre[0] + x * unit, centre[1] + y * unit, centre[2] + z * unit)
    quads = gm_boxes(boxes, place)
    for q in quads:
        q.ctx['part'] = part or name
    return quads


AIM9_POS = (2.5, -0.12, Z(9.95))
AIM54_POS = (2.15, -0.66, Z(10.1))
MK82_POS = (0.42, -0.6, Z(9.9))
LAU10 = (0.42, -0.62, 12.1, 15.1)


def build_stores():
    parts = {}
    body = []
    grey = flat(GULL, 0.05, 66)
    for side in (1, -1):
        sx = lambda x: x * side  # noqa: E731
        x0, x1 = sorted((sx(2.06), sx(2.24)))
        body += box(x0, -0.3, Z(8.3), x1, 0.38, Z(11.7), grey, {'part': 'body'})           # glove pylon
        x0, x1 = sorted((sx(2.24), sx(2.36)))
        body += box(x0, -0.17, Z(8.7), x1, -0.05, Z(11.0), grey, {'part': 'body'})         # AIM-9 shoulder rail
        x0, x1 = sorted((sx(2.1), sx(2.2)))
        body += box(x0, -0.46, Z(8.9), x1, -0.3, Z(11.3), grey, {'part': 'body'})          # Phoenix adapter
        x0, x1 = sorted((sx(0.37), sx(0.47)))
        body += box(x0, -0.45, Z(9.3), x1, -0.3, Z(10.5), grey, {'part': 'body'})          # bomb rack
        tag = 'r' if side > 0 else 'l'
        parts['aim9_' + tag] = Part('aim9_' + tag).add(
            store('aim9_sidewinder', (sx(AIM9_POS[0]), AIM9_POS[1], AIM9_POS[2]), 45.0, 'aim9_' + tag))
        parts['aim54_' + tag] = Part('aim54_' + tag).add(
            store('aim54_phoenix', (sx(AIM54_POS[0]), AIM54_POS[1], AIM54_POS[2]), 45.0, 'aim54_' + tag))
        parts['mk82_' + tag] = Part('mk82_' + tag).add(
            store('mk82_bomb', (sx(MK82_POS[0]), MK82_POS[1], MK82_POS[2]), 0.0, 'mk82_' + tag))
        # LAU-10 Zuni pod: four tubes, and a rocket nose showing in each loaded tube
        xc, yc, s0, s1 = sx(LAU10[0]), LAU10[1], LAU10[2], LAU10[3]
        body += cylinder((xc, yc, Z(s0 + 0.18)), (xc, yc, Z(s1 - 0.25)), 0.2, 0.2, pod_paint, {'part': 'body'}, sides=12)
        body += cylinder((xc, yc, Z(s0)), (xc, yc, Z(s0 + 0.18)), 0.17, 0.2, pod_front, {'part': 'body', 'pod': (xc, yc)},
                         sides=12)
        body += cylinder((xc, yc, Z(s1 - 0.25)), (xc, yc, Z(s1)), 0.2, 0.12, pod_paint, {'part': 'body'}, sides=12)
        x0, x1 = sorted((sx(0.37), sx(0.47)))
        body += box(x0, -0.42, Z(12.8), x1, -0.3, Z(14.4), grey, {'part': 'body'})
        for k, (dx, dy) in enumerate(((0.085, 0.085), (-0.085, 0.085), (0.085, -0.085), (-0.085, -0.085))):
            nm = 'zuni_%s%d' % (tag, k)
            tip = (xc + dx, yc + dy, Z(s0) - 0.12)
            quads = cylinder((xc + dx, yc + dy, Z(s0) + 0.02), tip, 0.055, 0.012, zuni_paint, {'part': nm}, sides=8)
            parts[nm] = Part(nm).add(quads)
    return body, parts


def pod_paint(p, n, ctx):
    return mix(hexc('#5f6a4a'), hexc('#4a5439'), 0.3 * noise(int(p[1] * 32), int(p[2] * 32), 67))


def pod_front(p, n, ctx):
    xc, yc = ctx['pod']
    for dx, dy in ((0.085, 0.085), (-0.085, 0.085), (0.085, -0.085), (-0.085, -0.085)):
        if n[2] < -0.5 and math.hypot(p[0] - xc - dx, p[1] - yc - dy) < 0.06:
            return hexc('#0e0f10')
    return pod_paint(p, n, ctx)


def zuni_paint(p, n, ctx):
    return hexc('#e2bd2a') if (p[2] * 16) % 2 < 0.6 else hexc('#56613f')


# ---- speed brakes, lights, details --------------------------------------------------------------------------

def airbrake_paint(p, n, ctx):
    if ctx.get('inner'):
        return hexc('#b3261e')       # speed brake wells and inner faces are red
    return skin(p, n, ctx)


def build_airbrakes():
    top = Part('airbrake_top', (0.0, 0.575, Z(16.85)), (1.0, 0.0, 0.0))
    y0, y1 = 0.575, 0.43
    outer = [(-0.46, y0, Z(16.85)), (0.46, y0, Z(16.85)), (0.4, y1, Z(18.35)), (-0.4, y1, Z(18.35))]
    inner = [(p[0], p[1] - 0.025, p[2]) for p in outer]
    top.add([Quad([outer[3], outer[2], outer[1], outer[0]], airbrake_paint, SKIN_TEXELS, {'part': 'airbrake_top', 'region': 'fuselage'}),
             Quad([inner[0], inner[1], inner[2], inner[3]], airbrake_paint, SKIN_TEXELS, {'part': 'airbrake_top', 'inner': True})])
    top.quads = orient_out(top.quads[:1], lambda q: (0.0, 0.0, Z(17.6))) + \
        inward(top.quads[1:], (0.0, 0.0, Z(17.6)))
    bot = Part('airbrake_bottom', (0.0, -0.155, Z(16.85)), (1.0, 0.0, 0.0))
    y0, y1 = -0.155, 0.02
    outer = [(-0.4, y0, Z(16.85)), (0.4, y0, Z(16.85)), (0.34, y1, Z(18.35)), (-0.34, y1, Z(18.35))]
    inner = [(p[0], p[1] + 0.025, p[2]) for p in outer]
    bot.add([Quad(outer, airbrake_paint, SKIN_TEXELS, {'part': 'airbrake_bottom', 'region': 'fuselage'}),
             Quad(list(reversed(inner)), airbrake_paint, SKIN_TEXELS, {'part': 'airbrake_bottom', 'inner': True})])
    bot.quads = orient_out(bot.quads[:1], lambda q: (0.0, 0.3, Z(17.6))) + inward(bot.quads[1:], (0.0, 0.3, Z(17.6)))
    return top, bot


def lamp(colour):
    return flat(colour, 0.02, 68)


def build_lights():
    parts = {}
    tip = wing_point(W_LE_TIP[0], 0.0)
    g = Part('light_green', emissive=True, parent='wing_r')
    g.add(box(9.62, WING_PIVOT[1] - 0.03, tip[1] + 0.08, 9.76, WING_PIVOT[1] + 0.05, tip[1] + 0.34, lamp(hexc('#3cff6e')),
              {'part': 'light_green', 'glow': True}))
    r = Part('light_red', emissive=True, parent='wing_l')
    r.add(box(-9.76, WING_PIVOT[1] - 0.03, tip[1] + 0.08, -9.62, WING_PIVOT[1] + 0.05, tip[1] + 0.34, lamp(hexc('#ff3b30')),
              {'part': 'light_red', 'glow': True}))
    w = Part('light_white', emissive=True)
    w.add(box(-0.07, 0.14, Z(19.06), 0.07, 0.24, Z(19.16), lamp(hexc('#fffbe8')), {'part': 'light_white', 'glow': True}))
    st = Part('strobe_top', emissive=True)
    st.add(box(-0.06, 0.84, Z(10.72), 0.06, 0.93, Z(10.88), lamp(hexc('#ff2d24')), {'part': 'strobe_top', 'glow': True}))
    sb = Part('strobe_bottom', emissive=True)
    sb.add(box(-0.06, -0.47, Z(10.02), 0.06, -0.37, Z(10.18), lamp(hexc('#ff2d24')), {'part': 'strobe_bottom', 'glow': True}))
    # glove leading-edge position lights (always on)
    for side, col, nm in ((1, hexc('#3cff6e'), 'light_glove_g'), (-1, hexc('#ff3b30'), 'light_glove_r')):
        p = Part(nm, emissive=True)
        x0, x1 = sorted((2.02 * side, 2.12 * side))
        p.add(box(x0, 0.43, Z(8.15), x1, 0.5, Z(8.3), lamp(col), {'part': nm, 'glow': True}))
        parts[nm] = p
    for p in (g, r, w, st, sb):
        parts[p.name] = p
    return parts


def build_details():
    q = []
    steel = flat(hexc('#8e969e'), 0.08, 69)
    q += cylinder((0.0, 0.3, Z(-0.6)), (0.0, 0.3, Z(0.04)), 0.016, 0.022, steel, {'part': 'body'}, sides=6)
    q += cylinder((0.0, -0.42, Z(2.72)), (0.0, -0.42, Z(3.6)), 0.11, 0.125, flat(GULL_DARK, 0.06, 70), {'part': 'body'}, sides=10)
    q += box(-0.08, -0.5, Z(2.7), 0.08, -0.34, Z(2.72), flat(hexc('#233447'), 0.05, 71), {'part': 'body'}, faces='n')
    for s, y0, h in ((9.2, 1.0, 0.18), (13.0, -0.28, -0.16), (7.6, -0.5, -0.14)):
        y1 = y0 + h
        q += box(-0.012, min(y0, y1), Z(s), 0.012, max(y0, y1), Z(s + 0.25), flat(hexc('#2d3033'), 0.05, 72), {'part': 'body'})
    # tail hook stowed under the beaver tail (black and white bands)
    q += box_between((0.0, 0.0, Z(16.7)), (0.0, 0.06, Z(18.95)), 0.08, 0.08, striped(hexc('#141414'), hexc('#e8e8e4'), 0.14),
                     {'part': 'body'})
    # boarding ladder on the left side, drawn deployed (the renderer slides it up into the fuselage)
    lad = Part('ladder', (-0.8, 0.0, Z(6.2)), (0.0, 1.0, 0.0))
    for dz in (6.05, 6.4):
        lad.add(box(-0.84, -1.76, Z(dz), -0.8, 0.32, Z(dz + 0.04), steel, {'part': 'ladder'}))
    for k in range(6):
        y = -1.62 + k * 0.33
        lad.add(box(-0.86, y, Z(6.05), -0.78, y + 0.04, Z(6.44), steel, {'part': 'ladder'}))
    return q, lad


# ---- markings ------------------------------------------------------------------------------------------------

def define_markings():
    DECALS.clear()
    for side in (1, -1):
        add_text('NAVY', 7.0, 0.05, side, BLACK, x_surface=1.9)
        add_text('103', 3.0, 0.52, side, BLACK, x_surface=0.64)
        add_bitmap(STAR_BAR, 12.35, 0.36, side, lambda c: c, x_surface=2.05)
        add_bitmap(RESCUE, 5.2, 0.62, side, lambda c: {'R': RED, 'Y': YELLOW}.get(c), x_surface=0.72)
    # skull and crossbones on both faces of both fins (the left fin is painted as the mirror of the right)
    rows = [[ch == 'X' for ch in row] for row in SKULL]
    sp = (math.sin(FIN_CANT), math.cos(FIN_CANT), 0.0)
    for face in (1, -1):
        normal = v_scale((math.cos(FIN_CANT), -math.sin(FIN_CANT), 0.0), face)
        h_top = 2.2
        top = v_add((FIN_X, 0.58, 0.0), sp, h_top)
        w = len(rows[0]) * texel(1)
        if face > 0:
            origin, u = (top[0] + 0.12, top[1], Z(15.05)), (0.0, 0.0, texel(1))
        else:
            origin, u = (top[0] - 0.12, top[1], Z(15.05) + w), (0.0, 0.0, -texel(1))
        DECALS.append(Decal(rows, origin, u, v_scale(sp, -texel(1)), normal,
                            lambda on, i, j: WHITE if on else None, parts=('body', 'rudder_r', 'rudder_l'), depth=0.3))


RESCUE = [
    '....R....',
    '...RRR...',
    '..RRYRR..',
    '.RRYYYRR.',
    'RRRRRRRRR',
]


# =====================================================================================================
# assembly
# =====================================================================================================

def build():
    define_markings()
    parts = {}

    body = Part('body')
    body.add(build_fuselage())
    for side in (1, -1):
        nq, fan_c = build_nacelle(side)
        body.add(nq)
        fan = build_fan(fan_c, side)
        parts[fan.name] = fan
        body.add(build_glove(side))
        body.add(build_ventral(side))
    fin_r, rud_r = build_fin()
    body.add(fin_r)
    body.add([Quad([mirror(c) for c in reversed(q.p)], q.paint, q.density, dict(q.ctx, mirrored=True)) for q in fin_r])
    parts['body'] = body

    for p in build_wing(1):
        parts[p.name] = p
        left = p.mirrored(p.name.replace('_r', '_l'), p.parent.replace('_r', '_l') if p.parent else None)
        parts[left.name] = left
    st = build_stab()
    parts['stab_r'] = st
    parts['stab_l'] = st.mirrored('stab_l')
    parts['rudder_r'] = rud_r
    parts['rudder_l'] = rud_r.mirrored('rudder_l')
    for side in (1, -1):
        nz = build_nozzle(side)
        parts[nz.name] = nz
    ws, cg, cf, fixed = build_canopy()
    parts[ws.name] = ws
    parts[cg.name] = cg
    parts[cf.name] = cf
    body.add(fixed)
    cockpit, hud, disp = build_cockpit()
    body.add(cockpit)
    parts[hud.name] = hud
    parts[disp.name] = disp
    parts['pilot'] = crew('pilot', 5.95, 'stick')
    parts['rio'] = crew('rio', 7.45, 'rio')
    parts.update(build_gear())
    store_body, stores = build_stores()
    body.add(store_body)
    parts.update(stores)
    top, bottom = build_airbrakes()
    parts[top.name] = top
    parts[bottom.name] = bottom
    parts.update(build_lights())
    details, ladder = build_details()
    body.add(details)
    parts[ladder.name] = ladder
    for p in parts.values():
        for q in p.quads:
            q.ctx.setdefault('part', p.name)
    return parts


# =====================================================================================================
# painting the atlas
# =====================================================================================================

def paint_quad(q):
    """The quad's patch image, painted exactly as it will be mapped (triangles 0-1-2 and 0-2-3)."""
    if q.img is not None:
        return q.img
    p0, p1, p2, p3 = q.p
    w = max(1, int(round(max(v_len(v_sub(p1, p0)), v_len(v_sub(p2, p3))) * q.density)))
    h = max(1, int(round(max(v_len(v_sub(p3, p0)), v_len(v_sub(p2, p1))) * q.density)))
    w, h = min(w, 512), min(h, 512)
    img = Img(w, h)
    ctx = q.ctx
    wing_like = ctx.get('region') in ('wing', 'glove', 'stab', 'fin', 'rudder', 'flap', 'slat', 'ventral')
    for j in range(h):
        tv = (j + 0.5) / h
        for i in range(w):
            tu = (i + 0.5) / w
            if tu >= tv:        # triangle 0-1-2: corners (0,0) (1,0) (1,1)
                pt = v_add(v_add(v_scale(p0, 1 - tu), v_scale(p1, tu - tv)), v_scale(p2, tv))
            else:               # triangle 0-2-3: corners (0,0) (1,1) (0,1)
                pt = v_add(v_add(v_scale(p0, 1 - tv), v_scale(p2, tu)), v_scale(p3, tv - tu))
            if ctx.get('mirrored'):
                src = mirror(pt)
                nrm = (-q.n[0], q.n[1], q.n[2])
            else:
                src = pt
                nrm = q.n
            if wing_like:
                ctx['_u'] = chord_fraction(src, ctx)
                ctx['_v'] = abs(src[0])
            img.set(i, j, q.paint(src, nrm, ctx))
    return img


def chord_fraction(p, ctx):
    region = ctx.get('region')
    if region in ('wing', 'flap', 'slat'):
        sp = abs(p[0])
        sp = max(W_LE_ROOT[0], min(W_LE_TIP[0], sp))
        le = wing_point(sp, 0.0)[1]
        te = wing_point(sp, 1.0)[1]
        return (p[2] - le) / max(1e-6, te - le)
    if region == 'glove':
        return (p[2] - Z(5.9)) / (Z(13.4) - Z(5.9))
    if region in ('fin', 'rudder'):
        h = max(0.0, min(2.55, (p[1] - 0.58) / math.cos(FIN_CANT)))
        le = fin_point(h, 0.0)[1]
        te = fin_point(h, 1.0)[1]
        return (p[2] - le) / max(1e-6, te - le)
    if region == 'stab':
        return (p[2] - Z(15.0)) / 3.1
    return 0.0


def pack(parts):
    """Paint every quad and shelf-pack the patches into one atlas.
    Returns (atlas, {id(quad): (u0, v0, u1, v1)}, {id(quad): patch image})."""
    patches = []
    for part in parts.values():
        for q in part.quads:
            patches.append((q, paint_quad(q)))
    order = sorted(range(len(patches)), key=lambda i: (-patches[i][1].h, -patches[i][1].w))
    x = y = shelf = 0
    place = {}
    for i in order:
        img = patches[i][1]
        w, h = img.w + 2, img.h + 2
        if x + w > ATLAS_W:
            x, y, shelf = 0, y + shelf, 0
        place[i] = (x + 1, y + 1)
        x += w
        shelf = max(shelf, h)
    height = 16
    while height < y + shelf:
        height *= 2
    atlas = Img(ATLAS_W, height)
    uvs = {}
    for i, (q, img) in enumerate(patches):
        px, py = place[i]
        # 1-texel border copied from the patch edge so filtering never reaches a neighbour
        for yy in range(-1, img.h + 1):
            for xx in range(-1, img.w + 1):
                c = img.get(min(img.w - 1, max(0, xx)), min(img.h - 1, max(0, yy)))
                atlas.px[py + yy][px + xx] = c
        uvs[id(q)] = (px / ATLAS_W, py / height, (px + img.w) / ATLAS_W, (py + img.h) / height)
    return atlas, uvs, {id(q): img for q, img in patches}


# =====================================================================================================
# mesh file
# =====================================================================================================

def write_mesh(parts, uvs, path, atlas):
    """Binary mesh: 'ARSM', version, atlas w/h, part count; per part: name, flags, parent, pivot, axis, quads
    (4 x [x y z u v] + normal)."""
    out = bytearray()
    out += b'ARSM'
    out += struct.pack('<iiii', 1, atlas.w, atlas.h, len(parts))
    for part in parts.values():
        name = part.name.encode()
        parent = (part.parent or '').encode()
        flags = (1 if part.translucent else 0) | (2 if part.emissive else 0)
        out += struct.pack('<H', len(name)) + name
        out += struct.pack('<H', len(parent)) + parent
        out += struct.pack('<B', flags)
        out += struct.pack('<fff', *part.pivot) + struct.pack('<fff', *part.axis)
        out += struct.pack('<f', part.retract)
        out += struct.pack('<i', len(part.quads))
        for q in part.quads:
            u0, v0, u1, v1 = uvs[id(q)]
            quv = ((u0, v0), (u1, v0), (u1, v1), (u0, v1))
            glow = 1 if (part.emissive or q.ctx.get('glow')) else 0
            out += struct.pack('<B', glow)
            for c, (u, v) in zip(q.p, quv):
                out += struct.pack('<fffff', c[0], c[1], c[2], u, v)
            out += struct.pack('<fff', *q.n)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(zlib.compress(bytes(out), 9))
    return len(out)


# =====================================================================================================
# previews (software renderer from gun_models)
# =====================================================================================================

def draw_parts(canvas, parts, uv_imgs, to_view, project, light=(0.35, 0.8, 0.5), pose=None, hidden=()):
    ln = math.sqrt(sum(c * c for c in light))
    light = [c / ln for c in light]
    for part in parts.values():
        if part.name in hidden:
            continue
        xf = pose(part) if pose else (lambda p: p)
        for q in part.quads:
            img = uv_imgs[id(q)]
            pts = [to_view(xf(c)) for c in q.p]
            n = quad_normal(pts)
            glow = part.emissive or q.ctx.get('glow')
            shade = 1.0 if glow else 0.55 + 0.45 * abs(v_dot(n, light))
            proj = [project(p) for p in pts]
            if any(p is None for p in proj):
                continue
            uv = [(0, 0), (img.w, 0), (img.w, img.h), (0, img.h)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                gm.raster(canvas, [proj[t] for t in tri], [uv[t] for t in tri], img, shade)


def view(parts, imgs, yaw, pitch, w, h, scale, centre=(0.0, 0.3, 0.0), pose=None, hidden=(), bg=(176, 196, 214, 255)):
    canvas = gm.Canvas(w, h, bg)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def to_view(p):
        x, y, z = p[0] - centre[0], p[1] - centre[1], p[2] - centre[2]
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (x, y, z)
    draw_parts(canvas, parts, imgs, to_view, gm.ortho(scale, w, h), pose=pose, hidden=hidden)
    return canvas.img


def render_icon(parts, imgs, size=64, oversample=4):
    """The item icon: the jet seen from above and ahead, rendered large and box-filtered down (transparent bg)."""
    big = size * oversample
    hidden = ('pilot', 'rio', 'ladder', 'canopy_glass', 'windscreen_glass', 'hud_glass')
    img = view(parts, imgs, 145, 48, big, big, big / 21.5, centre=(0.0, 0.0, 0.4), hidden=hidden, bg=(0, 0, 0, 0))
    out = Img(size, size)
    for y in range(size):
        for x in range(size):
            acc = [0, 0, 0]
            n = 0
            for yy in range(oversample):
                for xx in range(oversample):
                    c = img.get(x * oversample + xx, y * oversample + yy)
                    if c[3]:
                        acc[0] += c[0]
                        acc[1] += c[1]
                        acc[2] += c[2]
                        n += 1
            if n * 2 >= oversample * oversample:
                out.set(x, y, (acc[0] // n, acc[1] // n, acc[2] // n, 255))
    out.outline((24, 26, 30, 255))
    return out


def write_assets(entity_texture, mesh_path, icon_path):
    """Everything the game needs: the atlas (entity texture), the mesh and the item icon. Returns file stats."""
    parts = build()
    atlas, uvs, imgs = pack(parts)
    os.makedirs(os.path.dirname(entity_texture), exist_ok=True)
    atlas.save(entity_texture)
    size = write_mesh(parts, uvs, mesh_path, atlas)
    os.makedirs(os.path.dirname(icon_path), exist_ok=True)
    render_icon(parts, imgs).save(icon_path)
    return {'parts': len(parts), 'quads': sum(len(p.quads) for p in parts.values()),
            'atlas': (atlas.w, atlas.h), 'mesh_bytes': size}


def main():
    if 'assets' in sys.argv:
        stats = write_assets(os.path.join(ASSETS, 'textures', 'entity', 'f14.png'),
                             os.path.join(ASSETS, 'vehicle', 'f14.mesh'),
                             os.path.join(ASSETS, 'textures', 'item', 'f14_tomcat.png'))
        print(stats)
        return
    parts = build()
    imgs = {}
    for part in parts.values():
        for q in part.quads:
            imgs[id(q)] = paint_quad(q)
    os.makedirs(BUILD, exist_ok=True)
    which = [a for a in sys.argv[1:]] or ['side', 'top', 'front', 'q1', 'q2']
    views = {
        'side': lambda: view(parts, imgs, 90, 0, 1000, 330, 50),
        'top': lambda: view(parts, imgs, 0, 90, 1000, 1000, 50),
        'front': lambda: view(parts, imgs, 180, 8, 1000, 380, 50),
        'q1': lambda: view(parts, imgs, 140, 28, 1000, 650, 46),
        'q2': lambda: view(parts, imgs, -35, 22, 1000, 650, 46),
        'nose': lambda: view(parts, imgs, 125, 18, 1000, 650, 120, centre=(0.0, 0.5, Z(5.5))),
        'tail': lambda: view(parts, imgs, -30, 15, 1000, 650, 100, centre=(0.0, 0.8, Z(16.5))),
        'under': lambda: view(parts, imgs, 150, -35, 1000, 650, 46),
        'wing': lambda: view(parts, imgs, 160, 40, 1000, 650, 160, centre=(5.5, 0.5, Z(11.0))),
        'cockpit': lambda: view(parts, imgs, 150, 50, 1000, 650, 190, centre=(0.0, 0.8, Z(6.2)),
                                hidden=('canopy_glass', 'windscreen_glass', 'canopy')),
        'gear': lambda: view(parts, imgs, 120, -8, 1000, 650, 130, centre=(1.0, -0.9, Z(8.0))),
    }
    for name in which:
        views[name]().save(os.path.join(BUILD, 'f14_%s.png' % name))
    quads = sum(len(p.quads) for p in parts.values())
    print('%d parts, %d quads -> build/f14_{%s}.png' % (len(parts), quads, ','.join(which)))


if __name__ == '__main__':
    main()

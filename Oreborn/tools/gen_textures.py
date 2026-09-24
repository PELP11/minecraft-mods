#!/usr/bin/env python3
"""Generates every Oreborn texture: ores, storage blocks, crusted lava, materials, 36 gear icons, the worn
armour layers, the Frozen effect icon and the mod icon/banner.

Gear, shards and ingots reuse the silhouettes of their vanilla counterparts, repainted with each material's
palette (pixel classes are found by hue and lightness), plus hand-drawn ore veins and details.
The vanilla textures are read from the patched Minecraft jar in build/moddev/artifacts (run a Gradle build once).

Run from anywhere:  py tools/gen_textures.py      (also writes build/texture_preview.png for review)
"""
import colorsys
import glob
import json
import math
import os
import random
import sys
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from pixels import Img, CLEAR, hexc, mix, lighten, darken, decode_png, from_ascii, preview_sheet, WrapNoise  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..'))
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'oreborn', 'textures')
RESOURCES = os.path.join(ROOT, 'src', 'main', 'resources')

MATERIALS = ['cryolite', 'fulgurite', 'emberite', 'umbrium']
TOOLS = ['sword', 'pickaxe', 'axe', 'shovel', 'hoe']
ARMOR = ['helmet', 'chestplate', 'leggings', 'boots']


# =====================================================================================================
# Palettes (index 0 = outline ... 6 = brightest)
# =====================================================================================================

def ramp(*colors):
    return [hexc(c) for c in colors]


PAL = {
    # pale glacier blue, bluer and whiter than diamond
    'cryolite': dict(
        ramp=ramp('#0b2146', '#163f78', '#2968b2', '#4b98dd', '#83c6f4', '#c0e6ff', '#f4fbff'),
        handle=ramp('#27354a', '#4a5f78', '#7890a8', '#b3c7d9'),
        ore=ramp('#0b2146', '#2968b2', '#6fb8f0', '#c0e6ff', '#ffffff'),
        accent=hexc('#ffffff')),
    # crystallised lightning: lemon-white on storm-cloud slate (not gold!)
    'fulgurite': dict(
        ramp=ramp('#1f2344', '#4f5140', '#9f8e20', '#dcc22c', '#f7e24c', '#fff6a0', '#fffef0'),
        handle=ramp('#1b1e2d', '#333a55', '#535d7d', '#7c87a8'),
        ore=ramp('#1f2344', '#b8a01e', '#f7e24c', '#fff6b0', '#ffffff'),
        accent=hexc('#9ff0ff')),
    # dark forge-red metal; tools get a molten orange core (index 4)
    'emberite': dict(
        ramp=ramp('#1c0806', '#3d1109', '#5e1a0e', '#842513', '#ff7a1c', '#b23517', '#ffb347'),
        metal=ramp('#1c0806', '#3d1109', '#5e1a0e', '#842513', '#a8321a', '#cf521f', '#ff9a3c'),
        handle=ramp('#151113', '#292224', '#3e3437', '#56494b'),
        ore=ramp('#3a0c06', '#a8260e', '#ff6a14', '#ffb03a', '#fff0a0'),
        accent=hexc('#ffc24d'), glow=hexc('#ff7a1c')),
    # void-purple metal; tools get a magenta rune core (index 4)
    'umbrium': dict(
        ramp=ramp('#0d0619', '#1f0f3b', '#341a61', '#4e288f', '#e04ce0', '#6f41c2', '#a984f0'),
        metal=ramp('#0d0619', '#1f0f3b', '#341a61', '#4e288f', '#6a3dbb', '#9468dc', '#c9adff'),
        handle=ramp('#0f0a15', '#1f172a', '#322644', '#4a3a62'),
        ore=ramp('#12071f', '#3c1d6e', '#7a3fd0', '#e04ce0', '#ffb2f6'),
        accent=hexc('#ff6bf0')),
}


def metal(mat):
    """Monotonic palette for big surfaces (armour, materials, blocks)."""
    return PAL[mat].get('metal', PAL[mat]['ramp'])


# =====================================================================================================
# Vanilla textures
# =====================================================================================================

class Vanilla:
    def __init__(self):
        candidates = sorted(glob.glob(os.path.join(ROOT, 'build', 'moddev', 'artifacts', 'minecraft-patched-*-sources.jar')))
        if not candidates:
            sys.exit('No patched Minecraft jar in build/moddev/artifacts: run "gradlew build" once first.')
        self.jar = zipfile.ZipFile(candidates[-1])

    def tex(self, path):
        return decode_png(self.jar.read('assets/minecraft/textures/' + path + '.png'), path)


def seed(*parts):
    return zlib.crc32('/'.join(parts).encode())


def hls(c):
    h, l, s = colorsys.rgb_to_hls(c[0] / 255, c[1] / 255, c[2] / 255)
    return h * 360, l, s


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def rank_map(values, lo, hi):
    """Maps sorted distinct values evenly onto the integer range lo..hi."""
    levels = sorted(set(values))
    if len(levels) == 1:
        return {levels[0]: hi}
    return {v: lo + round(i * (hi - lo) / (len(levels) - 1)) for i, v in enumerate(levels)}


def is_handle(c):
    h, l, s = hls(c)
    return 15 <= h <= 65 and s > 0.2 and l < 0.5


def recolor(img, colors, lo=0, hi=None, handle=None):
    """Repaints an image by lightness rank. With `handle`, brown (wooden) pixels use that palette instead.
    Returns the image and a map (x, y) -> palette index of the non-handle pixels."""
    hi = len(colors) - 1 if hi is None else hi
    out = Img(img.w, img.h)
    main, wood = [], []
    for y in range(img.h):
        for x in range(img.w):
            c = img.px[y][x]
            if c[3] == 0:
                continue
            (wood if handle is not None and is_handle(c) else main).append((x, y, lum(c), c[3]))
    idx = {}
    mm = rank_map([v for _, _, v, _ in main], lo, hi)
    for x, y, v, a in main:
        i = mm[v]
        idx[(x, y)] = i
        c = colors[i]
        out.px[y][x] = (c[0], c[1], c[2], a)
    if wood:
        wm = rank_map([v for _, _, v, _ in wood], 0, len(handle) - 1)
        for x, y, v, a in wood:
            c = handle[wm[v]]
            out.px[y][x] = (c[0], c[1], c[2], a)
    return out, idx


# =====================================================================================================
# Gear
# =====================================================================================================

def sparkle(img, idx, mat, rnd, count):
    """A few highlight pixels in the brightest parts (frost glints / electric sparks)."""
    bright = sorted(p for p, i in idx.items() if i >= 5)
    rnd.shuffle(bright)
    for (x, y) in bright[:count]:
        img.set(x, y, PAL[mat]['accent'])


def gear_icon(vanilla, mat, piece):
    base = vanilla.tex('item/diamond_' + piece)
    pal = PAL[mat]
    armor = piece in ARMOR
    img, idx = recolor(base, metal(mat) if armor else pal['ramp'], handle=pal['handle'])
    rnd = random.Random(seed(mat, piece))
    if armor and mat == 'emberite':
        inner = {p: i for p, i in idx.items() if i >= 2}
        cracks(img, inner, rnd, 3, pal['glow'], pal['accent'], length=4)
    elif armor and mat == 'umbrium':
        for (x, y) in rnd.sample(sorted(p for p, i in idx.items() if i >= 3), 3):
            img.set(x, y, pal['accent'])
    if mat == 'cryolite':
        sparkle(img, idx, mat, rnd, 2)
    elif mat == 'fulgurite':
        sparkle(img, idx, mat, rnd, 2)
        # a copper wrap on the handle: lightning rods are copper
        if piece in TOOLS:
            for (x, y), c in list(handle_pixels(base)):
                if (x + y) % 3 == 0:
                    img.set(x, y, hexc('#d9774a') if lum(c) > 60 else hexc('#8c4a2e'))
    elif mat == 'emberite':
        # embers glow in the handle joints too
        for (x, y), c in list(handle_pixels(base)):
            if (x * 3 + y) % 7 == 0:
                img.set(x, y, hexc('#c2410f'))
    return img


def handle_pixels(img):
    for y in range(img.h):
        for x in range(img.w):
            c = img.px[y][x]
            if c[3] and is_handle(c):
                yield (x, y), c


def armor_layer(vanilla, mat, layer):
    base = vanilla.tex(f'entity/equipment/{layer}/diamond')
    pal = PAL[mat]
    # Fulgurite skips its darkest shades: dark yellow reads as muddy olive on big armour surfaces
    img, idx = recolor(base, metal(mat), lo=2 if mat == 'fulgurite' else 1, hi=6)
    rnd = random.Random(seed(mat, layer))
    opaque = sorted(idx)
    if mat == 'cryolite':
        for (x, y) in rnd.sample(opaque, len(opaque) // 40):
            img.set(x, y, pal['accent'])
    elif mat == 'fulgurite':
        # short zigzag bolts
        for _ in range(len(opaque) // 90):
            x, y = rnd.choice(opaque)
            for step in range(5):
                if (x, y) in idx:
                    img.set(x, y, hexc('#fffde4') if step % 2 == 0 else pal['accent'])
                x += 1 if step % 2 == 0 else 0
                y += 1
    elif mat == 'emberite':
        cracks(img, idx, rnd, len(opaque) // 60, pal['glow'], pal['accent'])
    elif mat == 'umbrium':
        for (x, y) in rnd.sample(opaque, len(opaque) // 45):
            img.set(x, y, pal['accent'])
    return img


def cracks(img, idx, rnd, count, color, core, length=6):
    cells = sorted(idx)
    for _ in range(count):
        x, y = rnd.choice(cells)
        dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (-1, 1)])
        for step in range(rnd.randint(3, length)):
            if (x, y) not in idx:
                break
            img.set(x, y, core if step == 1 else color)
            if rnd.random() < 0.35:
                dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (-1, 1)])
            x, y = x + dx, y + dy


# =====================================================================================================
# Materials
# =====================================================================================================

FULGURITE_CRYSTAL = [
    '................',
    '.........oooo...',
    '........o665o...',
    '.......o6654o...',
    '......o6654o....',
    '.....o665oooooo.',
    '....o66554444o..',
    '...o665444433o..',
    '...oooo54433o...',
    '......o5433o....',
    '.....o5433o.....',
    '....o4432o......',
    '....o432o.......',
    '...o432o........',
    '...o32o.........',
    '...ooo..........',
]


def material_items(vanilla):
    out = {}
    for mat in MATERIALS:
        pal = PAL[mat]
        if mat in ('cryolite', 'fulgurite'):
            shard_src = 'item/amethyst_shard' if mat == 'cryolite' else 'item/prismarine_shard'
            out[f'item/{mat}_shard'] = recolor(vanilla.tex(shard_src), pal['ramp'])[0]
        if mat == 'cryolite':
            out['item/cryolite_crystal'] = recolor(vanilla.tex('block/amethyst_cluster'), pal['ramp'])[0]
        elif mat == 'fulgurite':
            colors = {'o': pal['ramp'][0]}
            colors.update({str(i): pal['ramp'][i] for i in range(1, 7)})
            crystal = from_ascii(FULGURITE_CRYSTAL, colors)
            crystal.set(10, 2, pal['accent'])
            crystal.set(6, 7, pal['accent'])
            out['item/fulgurite_crystal'] = crystal
        else:
            rnd = random.Random(seed(mat, 'materials'))
            for name, src in ((f'raw_{mat}', 'raw_gold' if mat == 'emberite' else 'raw_iron'),
                              (f'{mat}_scrap', 'netherite_scrap'), (f'{mat}_ingot', 'netherite_ingot')):
                img, idx = recolor(vanilla.tex('item/' + src), metal(mat))
                inner = {p: i for p, i in idx.items() if i >= 2}
                if mat == 'emberite':
                    cracks(img, inner, rnd, 2, pal['glow'], pal['accent'], length=3)
                else:
                    for (x, y) in rnd.sample(sorted(inner), 2):
                        img.set(x, y, pal['accent'])
                out['item/' + name] = img
    return out


# =====================================================================================================
# Blocks
# =====================================================================================================

# ---- Ores ---------------------------------------------------------------------------------------------------------
# Every ore is two layers (see the oreborn:block/glowing_ore model): the base texture (vanilla stone / deepslate /
# netherrack with the ore worked into it) and a glow layer the model draws at full brightness (light_emission 15), so
# the veins glow in dark caves without lighting them up. The glow layers are animated strips (+ .png.mcmeta).
#   Cryolite  - ice crystals growing out of frosted stone; glints twinkle across the crystal tips
#   Fulgurite - a branching lightning scar burnt into the stone; the current flickers through its branches
#   Emberite  - smouldering coal chunks in charred netherrack; the embers breathe
#   Umbrium   - a rift torn into the void; its rim pulses and stars twinkle inside

def grid(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), 'ore art must be 16x16'
    return rows


CRYOLITE_ART = grid([   # o outline, 1-4 crystal shades, G glowing core, S crystal tip (glints), c frost crack
    '.............oo.',
    '............oS3o',
    '...........o3Go.',
    '......o...o2Go..',
    '.....oSo..o1o...',
    '....o343o.oo...c',
    '....o2G3o......c',
    '.oo.o2G3ooo.....',
    'oS3oo1G3ooSo....',
    'o2G3o1G2oG3o....',
    '.o2Go122o2o..o..',
    '..o1oooooo..oSo.',
    '.c.oo.c.....oGo.',
    'c......c....o2o.',
    '.......cc...ooo.',
    '................',
])
CRYOLITE_GLINT_ORDER = [(6, 4), (13, 1), (1, 8), (13, 11), (10, 8)]

# Emberite: molten pools and embers (o crust, d hot crust, g glowing, h white-hot) with glowing cracks running out of
# them; each pool and its cracks pulse in their own rhythm (group 1-3)
EMBER_SPRITES = {
    'pool': ['.ooo..',
             'odggo.',
             'oghhgo',
             'oghhgo',
             '.oggdo',
             '..ooo.'],
    'ember': ['.oo.',
              'ohgo',
              'oggo',
              '.oo.'],
}
EMBER_NODES = [(1, 1, 'pool', 1), (9, 8, 'pool', 2), (11, 1, 'ember', 3), (2, 10, 'ember', 3)]
EMBER_CRACKS = [([(7, 4), (8, 5), (9, 5)], 1), ([(3, 7), (2, 8), (2, 9)], 1), ([(0, 4), (0, 5)], 1),
                ([(8, 11), (7, 12), (6, 12)], 2), ([(15, 10), (15, 8)], 2), ([(13, 14), (14, 15)], 2),
                ([(10, 3), (9, 2), (8, 2)], 3), ([(1, 13), (0, 14)], 3)]

UMBRIUM_ART = grid([    # v void, * star, r side crack, + floating mote (the glowing rim is computed around the rift)
    '...v............',
    '...vv...........',
    '....v......r....',
    '....vv...rr.....',
    '.+.v*v..r.......',
    '....vvv.........',
    '.....v*v........',
    '......vvv.......',
    '......vv*v......',
    '.......vvvv.....',
    '.........vv...+.',
    '......r.v*v.....',
    '....rr...vvvv...',
    '...r.......vv...',
    '..r........v*v..',
    '............v...',
])

FULGURITE_BOLT = {   # the lightning scar: a main channel and four branches (polylines, drawn 1 px wide)
    'main': [(2, 0), (4, 3), (3, 5), (6, 8), (5, 10), (8, 13), (7, 15)],
    'a': [(4, 3), (7, 2), (9, 3), (12, 1), (14, 2)],
    'b': [(6, 8), (9, 7), (11, 9), (14, 8), (15, 9)],
    'c': [(5, 10), (3, 11), (1, 13)],
    'd': [(11, 9), (12, 12)],
}
FULGURITE_TIPS = {'a': (14, 2), 'b': (15, 9), 'c': (1, 13), 'd': (12, 12)}

# frames of each glow strip and how they play (written to <name>_glow.png.mcmeta)
ORE_ANIMATION = {
    # quiet glow, glints now and then (frame 0 = no glint, 1-5 = a glint on one crystal tip)
    'cryolite': {'frames': [{'index': 0, 'time': 14}, {'index': 1, 'time': 3}, {'index': 0, 'time': 9},
                            {'index': 2, 'time': 3}, {'index': 0, 'time': 16}, {'index': 3, 'time': 3},
                            {'index': 0, 'time': 7}, {'index': 4, 'time': 3}, {'index': 0, 'time': 12},
                            {'index': 5, 'time': 3}]},
    # steady current, broken by irregular flickers and sparks
    'fulgurite': {'frames': [{'index': 0, 'time': 24}, {'index': 1, 'time': 2}, {'index': 3, 'time': 2},
                             {'index': 1, 'time': 2}, {'index': 0, 'time': 30}, {'index': 4, 'time': 3},
                             {'index': 2, 'time': 2}, {'index': 5, 'time': 3}, {'index': 0, 'time': 18},
                             {'index': 3, 'time': 1}, {'index': 1, 'time': 2}, {'index': 0, 'time': 26}]},
    # slow breathing (8 frames, blended)
    'emberite': {'frametime': 5, 'interpolate': True},
    # pulsing rim, twinkling stars (8 frames, blended)
    'umbrium': {'frametime': 4, 'interpolate': True},
}


def chebyshev(cells):
    """Distance (in pixels, 8-neighbourhood) from every pixel to the nearest of `cells`, capped at 9."""
    dist = [[9] * 16 for _ in range(16)]
    for (cx, cy) in cells:
        for y in range(max(0, cy - 3), min(16, cy + 4)):
            for x in range(max(0, cx - 3), min(16, cx + 4)):
                dist[y][x] = min(dist[y][x], max(abs(x - cx), abs(y - cy)))
    return dist


def cells_of(art, chars):
    return [(x, y) for y in range(16) for x in range(16) if art[y][x] in chars]


def with_halo(img, feature_cells, color, strengths):
    """Tints the base around a feature (frost on stone, scorch, char, void corruption)."""
    dist = chebyshev(feature_cells)
    for y in range(16):
        for x in range(16):
            d = dist[y][x]
            if 1 <= d <= len(strengths):
                c = img.px[y][x]
                img.px[y][x] = mix(c, (color[0], color[1], color[2], c[3]), strengths[d - 1])


def bresenham(p0, p1):
    (x0, y0), (x1, y1) = p0, p1
    points = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        points.append((x0, y0))
        if (x0, y0) == (x1, y1):
            return points
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def fulgurite_channels():
    """channel name -> ordered pixel list of the lightning scar."""
    channels = {}
    for name, points in FULGURITE_BOLT.items():
        pixels = []
        for a, b in zip(points, points[1:]):
            for p in bresenham(a, b):
                if p not in pixels and 0 <= p[0] < 16 and 0 <= p[1] < 16:
                    pixels.append(p)
        channels[name] = pixels
    return channels


def pulse(frame, frames, phase=0.0):
    return 0.5 + 0.5 * math.sin(2 * math.pi * (frame / frames + phase))


def glow_strip(frames):
    strip = Img(16, 16 * len(frames))
    for i, frame in enumerate(frames):
        strip.paste(frame, 0, 16 * i)
    return strip


def cryolite_ore(base_img):
    art = CRYOLITE_ART
    colors = {'o': '#0b2146', '1': '#1f4f96', '2': '#3a7fcf', '3': '#7cc3f2', '4': '#d8f1ff',
              'G': '#a8e0ff', 'S': '#eefaff', 'c': '#b4dcf5'}
    img = base_img.copy()
    with_halo(img, cells_of(art, 'o1234GS'), hexc('#d4ecff'), (0.45, 0.2))
    with_halo(img, cells_of(art, 'c'), hexc('#d4ecff'), (0.18,))
    for (x, y) in cells_of(art, colors.keys()):
        img.px[y][x] = hexc(colors[art[y][x]])
    inner_light = {'2': '#2f66ad', '3': '#6db6ea', '4': '#c6ecff', 'G': '#d2f6ff', 'S': '#e8f9ff'}  # ice lit from within
    frames = []
    for f in range(6):
        g = Img(16, 16)
        for (x, y) in cells_of(art, inner_light.keys()):
            g.set(x, y, hexc(inner_light[art[y][x]]))
        if f > 0:  # a glint: a four-pointed star on one tip
            tx, ty = CRYOLITE_GLINT_ORDER[f - 1]
            for d, col in ((1, '#bdefff'), (2, '#79cdf0')):
                for (dx, dy) in ((d, 0), (-d, 0), (0, d), (0, -d)):
                    g.set(tx + dx, ty + dy, hexc(col))
            g.set(tx, ty, hexc('#ffffff'))
        frames.append(g)
    return img, frames


def fulgurite_ore(base_img):
    channels = fulgurite_channels()
    core = [p for pixels in channels.values() for p in pixels]
    core_set = set(core)
    edge = sorted({(x + dx, y + dy) for (x, y) in core for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1))
                   if 0 <= x + dx < 16 and 0 <= y + dy < 16 and (x + dx, y + dy) not in core_set})
    img = base_img.copy()
    with_halo(img, core + edge, hexc('#2a1f14'), (0.4, 0.18))   # scorched stone
    for (x, y) in edge:
        img.px[y][x] = mix(img.px[y][x], hexc('#4a3510'), 0.75)  # fused dark glass
    for (x, y) in core:
        img.px[y][x] = hexc('#b8921c')
    levels = {'off': '#8a6c16', 'dim': '#c49a22', 'mid': '#f2c83a', 'hi': '#fff2a4', 'max': '#fffcef'}
    # per frame: brightness of the main channel, of each branch, and which branch tips spark
    plan = [
        ('hi', {'a': 'mid', 'b': 'mid', 'c': 'mid', 'd': 'mid'}, ''),
        ('max', {'a': 'hi', 'b': 'hi', 'c': 'hi', 'd': 'hi'}, 'abcd'),
        ('mid', {'a': 'dim', 'b': 'dim', 'c': 'dim', 'd': 'dim'}, ''),
        ('dim', {'a': 'off', 'b': 'off', 'c': 'off', 'd': 'off'}, ''),
        ('max', {'a': 'hi', 'b': 'dim', 'c': 'mid', 'd': 'dim'}, 'ac'),
        ('hi', {'a': 'dim', 'b': 'hi', 'c': 'hi', 'd': 'hi'}, 'bd'),
    ]
    frames = []
    for main_level, branch_levels, sparks in plan:
        g = Img(16, 16)
        for name, pixels in channels.items():
            level = main_level if name == 'main' else branch_levels[name]
            for (x, y) in pixels:
                g.set(x, y, hexc(levels[level]))
        for name in sparks:  # a spark jumps off the branch tip
            tx, ty = FULGURITE_TIPS[name]
            g.set(tx, ty, hexc('#ffffff'))
            for (dx, dy) in ((1, -1), (-1, -1), (1, 1), (-1, 1)):
                if (tx + dx, ty + dy) not in core_set:
                    g.set(tx + dx, ty + dy, hexc('#aef6ff'))
        frames.append(g)
    return img, frames


def emberite_cells():
    """(x, y) -> (kind, group): kind o/d = crust, g = glowing, h = white-hot."""
    cells = {}
    for (x, y), group in [((px, py), grp) for points, grp in EMBER_CRACKS
                          for a, b in zip(points, points[1:]) for (px, py) in bresenham(a, b)]:
        cells[(x, y)] = ('g', group)
    for (nx, ny, sprite, group) in EMBER_NODES:
        for dy, row in enumerate(EMBER_SPRITES[sprite]):
            for dx, ch in enumerate(row):
                if ch != '.' and 0 <= nx + dx < 16 and 0 <= ny + dy < 16:
                    cells[(nx + dx, ny + dy)] = (ch, group)
    return cells


def emberite_ore(base_img):
    cells = emberite_cells()
    img = base_img.copy()
    with_halo(img, list(cells), hexc('#1a0605'), (0.35, 0.15))   # charred netherrack
    for (x, y), (kind, _) in cells.items():
        img.px[y][x] = hexc({'o': '#2a0b06', 'd': '#5a1c0c', 'g': '#a0300c', 'h': '#e06a1c'}[kind])
    frames = []
    for f in range(8):
        g = Img(16, 16)
        for (x, y), (kind, group) in cells.items():
            t = pulse(f, 8, group / 3.0)
            if kind == 'g':
                g.set(x, y, mix(hexc('#a02a0a'), hexc('#ff9a30'), t))
            elif kind == 'h':
                g.set(x, y, mix(hexc('#ff7a24'), hexc('#fff4c0'), t))
        frames.append(g)
    return img, frames


def umbrium_ore(base_img):
    art = UMBRIUM_ART
    rift = cells_of(art, 'v*')
    rift_set = set(rift)
    dist = chebyshev(rift)
    rim = [(x, y) for y in range(16) for x in range(16) if dist[y][x] == 1 and art[y][x] != 'r']
    # rim pixels touching the void edge-on burn bright; the ones only touching a corner are dimmer (gives it depth)
    edge_on = {(x, y) for (x, y) in rim if any((x + dx, y + dy) in rift_set for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    img = base_img.copy()
    with_halo(img, rift + rim, hexc('#2b1745'), (0.42, 0.2))   # corrupted stone around the rift
    for (x, y) in cells_of(art, 'r'):
        img.px[y][x] = hexc('#2a1640')
    for (x, y) in rift:
        img.px[y][x] = hexc('#0a0414')
    for (x, y) in rim:
        img.px[y][x] = hexc('#5a2290' if (x, y) in edge_on else '#3a1760')
    stars = cells_of(art, '*')
    motes = cells_of(art, '+')
    frames = []
    for f in range(8):
        g = Img(16, 16)
        t = pulse(f, 8)
        for (x, y) in rift:
            g.set(x, y, mix(hexc('#06020d'), hexc('#160830'), t))   # the void is always pitch dark
        for i, (x, y) in enumerate(stars):
            s = max(0.0, math.sin(2 * math.pi * (f / 8 + i * 0.37))) ** 2
            g.set(x, y, mix(hexc('#1a0a33'), hexc('#fbeaff'), s))
        for (x, y) in rim:
            if (x, y) in edge_on:
                g.set(x, y, mix(hexc('#7a24c0'), hexc('#ff4ff0'), t))
            else:
                g.set(x, y, mix(hexc('#3f1470'), hexc('#a834d0'), t))
        for i, (x, y) in enumerate(motes):
            s = max(0.0, math.sin(2 * math.pi * (f / 8 + 0.5 + i * 0.3))) ** 2
            g.set(x, y, mix(hexc('#3a1f5c'), hexc('#e9c2ff'), s))
        frames.append(g)
    assert all(p not in rift_set for p in rim)
    return img, frames


ORE_MAKERS = {'cryolite': cryolite_ore, 'fulgurite': fulgurite_ore, 'emberite': emberite_ore, 'umbrium': umbrium_ore}


def ore_textures(vanilla):
    """rel path -> Img for every ore base texture and glow strip, plus rel path -> mcmeta dict for the strips."""
    textures, metas, previews = {}, {}, []
    for mat in MATERIALS:
        bases = [('netherrack', f'{mat}_ore')] if mat == 'emberite' else [('stone', f'{mat}_ore'), ('deepslate', f'deepslate_{mat}_ore')]
        for base, name in bases:
            img, frames = ORE_MAKERS[mat](vanilla.tex('block/' + base))
            textures['block/' + name] = img
            previews.append((img, frames))
        textures[f'block/{mat}_ore_glow'] = glow_strip(frames)  # same art on every base: one shared strip
        metas[f'block/{mat}_ore_glow'] = {'animation': ORE_ANIMATION[mat]}
    return textures, metas, previews


def ore_preview(previews):
    """Per ore: the base, the block in daylight, then a few glow frames as seen in a dark cave."""
    rows = []
    for img, frames in previews:
        row = [img]
        lit = img.copy()
        lit.paste(frames[0], 0, 0)
        row.append(lit)
        for frame in frames[:6]:
            dark = img.copy()
            for y in range(16):
                for x in range(16):
                    c = dark.px[y][x]
                    dark.px[y][x] = (c[0] // 5, c[1] // 5, c[2] // 5, 255)
            dark.paste(frame, 0, 0)
            row.append(dark)
        while len(row) < 8:
            row.append(Img(16, 16))
        rows.extend(row)
    return preview_sheet(rows, scale=6, cols=8)


def storage_block(vanilla, mat):
    pal = PAL[mat]
    if mat in ('cryolite', 'fulgurite'):
        img, idx = recolor(vanilla.tex('block/diamond_block'), pal['ramp'], lo=2, hi=6)
    else:
        img, idx = recolor(vanilla.tex('block/netherite_block'), metal(mat), lo=1, hi=5)
    rnd = random.Random(seed(mat))
    if mat == 'cryolite':
        for (x, y) in rnd.sample(sorted(idx), 6):
            img.set(x, y, pal['accent'])
    elif mat == 'fulgurite':
        for x0 in (3, 11):
            x = x0
            for y in range(1, 15):
                img.set(x, y, hexc('#fffde4') if y % 3 else pal['accent'])
                x += 1 if (y // 2) % 2 == 0 else -1
    elif mat == 'emberite':
        cracks(img, idx, rnd, 7, pal['glow'], pal['accent'], length=7)
    elif mat == 'umbrium':
        for x, y in [(4, 4), (11, 4), (4, 11), (11, 11), (7, 7), (8, 8), (7, 8), (8, 7)]:
            img.set(x, y, pal['accent'])
    return img


def crusted_lava(age):
    """Dark cooled crust; the cracks widen and glow brighter as it (age 0..3) is about to melt."""
    noise = WrapNoise(7, 16, 4)
    fine = WrapNoise(8, 16, 2)
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            n = noise.at(x, y) * 0.6 + fine.at(x, y) * 0.4
            img.px[y][x] = mix(hexc('#1f1716'), hexc('#4a3934'), n)
    rnd = random.Random(1234)
    glow = [hexc('#7a1f0c'), hexc('#c2410f'), hexc('#ff7a1c'), hexc('#ffb347')][age]
    core = [hexc('#a8300f'), hexc('#ff6a14'), hexc('#ffb347'), hexc('#fff0a0')][age]
    for i in range(4 + age * 3):
        x, y = rnd.randrange(16), rnd.randrange(16)
        dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (1, -1)])
        for step in range(rnd.randint(3, 6)):
            img.set(x % 16, y % 16, core if step % 3 == 1 else glow)
            if rnd.random() < 0.4:
                dx, dy = rnd.choice([(1, 0), (0, 1), (1, 1), (1, -1)])
            x, y = x + dx, y + dy
    return img


STAFF_SPARKS = [   # per animation frame: (x, y, white core?) sparks jumping around the crystal
    [(15, 0, True), (11, 0, False), (15, 4, False)],
    [(9, 1, True), (14, 5, False), (12, 0, False)],
    [(8, 2, False), (13, 6, True), (15, 3, False)],
    [],
]
STAFF_ANIMATION = {'frametime': 2, 'frames': [0, 1, 3, 2, 0, 3, 1, 2, 3, 3]}


def lightning_staff():
    """A copper lightning-rod shaft (storm-cloth grip, gold bands) with a Fulgurite crystal held in copper prongs at
    the tip. Returns the animation frames (sparks jump around the crystal)."""
    copper_l, copper_m, copper_d = hexc('#ec9058'), hexc('#c4683a'), hexc('#8c4020')
    white, bright, mid, dark = hexc('#fffdea'), hexc('#ffe34a'), hexc('#e0b52a'), hexc('#8c6a10')
    base = Img(16, 16)
    # the shaft: a thick diagonal, a lit and a shaded pixel per row
    for i in range(9):
        light, shade = copper_l, copper_d
        if i in (2, 3, 4):
            light, shade = hexc('#56628a'), hexc('#2d3354')   # storm-cloth grip
        elif i in (1, 5):
            light, shade = hexc('#f4cc52'), hexc('#a8801c')   # gold bands
        elif i == 7:
            light, shade = hexc('#63b39a'), hexc('#3b8a76')   # a touch of verdigris
        base.set(1 + i, 14 - i, light)
        base.set(2 + i, 14 - i, shade)
    base.set(0, 15, bright)          # crystal pommel
    base.set(1, 15, hexc('#a8801c'))
    base.set(0, 14, hexc('#a8801c'))
    # the crystal lies along the staff's diagonal: p = x + y (14 lit side, 15 middle, 16 shaded side),
    # a = x - y from 5 (base, inside the collar) to 13 (tip)
    crystal = {}
    for a in range(5, 14):
        for p, color in ((14, white), (15, bright), (16, mid)):
            if (a + p) % 2 == 0:
                crystal[((a + p) // 2, (p - a) // 2)] = dark if a == 5 else (white if a == 13 else color)
    for (x, y), c in crystal.items():
        base.set(x, y, c)
    # the collar and two copper claws hugging the crystal's sides (p = 13 and p = 17)
    holder = {(9, 5): copper_m,
              (8, 5): copper_m, (9, 4): copper_l, (10, 3): copper_l, (11, 2): copper_l,
              (10, 7): copper_m, (11, 6): copper_d, (12, 5): copper_d, (13, 4): copper_d}
    for (x, y), c in holder.items():
        base.set(x, y, c)
    outline = hexc('#4a3410')
    for (x, y) in list(crystal):
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and base.get(nx, ny)[3] == 0:
                    base.set(nx, ny, outline)
    frames = []
    for index, sparks in enumerate(STAFF_SPARKS):
        frame = base.copy()
        if not sparks:   # a calm frame: the crystal dims for a moment
            for (x, y), c in crystal.items():
                if c == bright:
                    frame.set(x, y, mid)
        for (x, y, core) in sparks:
            if frame.get(x, y)[3] == 0:
                frame.set(x, y, hexc('#ffffff') if core else hexc('#aef6ff'))
        frames.append(frame)
    return frames


def frozen_icon():
    img = Img(18, 18)
    c = (9, 9)
    light, mid = hexc('#e6f7ff'), hexc('#7cc8f2')
    for (dx, dy) in ((0, 1), (1, 0), (1, 1), (1, -1)):
        for t in range(-6, 7):
            img.set(c[0] + dx * t, c[1] + dy * t, light if abs(t) < 5 else mid)
    for (x, y) in [(9, 3), (9, 15), (3, 9), (15, 9)]:
        for d in (-1, 1):
            if x == 9:
                img.set(x + d, y + (1 if y < 9 else -1), mid)
            else:
                img.set(x + (1 if x < 9 else -1), y + d, mid)
    img.outline(hexc('#123a66'))
    return img


# =====================================================================================================
# Output
# =====================================================================================================

def save(img, rel):
    path = os.path.join(TEX, rel + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    vanilla = Vanilla()
    ore_tex, ore_meta, ore_previews = ore_textures(vanilla)
    for rel, img in ore_tex.items():
        save(img, rel)
    for rel, meta in ore_meta.items():
        with open(os.path.join(TEX, rel + '.png.mcmeta'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(meta, f, indent=2)
            f.write('\n')
    icons = {}
    for mat in MATERIALS:
        icons[f'block/{mat}_block'] = storage_block(vanilla, mat)
    for age in range(4):
        icons[f'block/crusted_lava_{age}'] = crusted_lava(age)
    icons.update(material_items(vanilla))
    for mat in MATERIALS:
        for piece in TOOLS + ARMOR:
            icons[f'item/{mat}_{piece}'] = gear_icon(vanilla, mat, piece)
    layers = {}
    for mat in MATERIALS:
        for layer in ('humanoid', 'humanoid_leggings'):
            layers[f'entity/equipment/{layer}/{mat}'] = armor_layer(vanilla, mat, layer)

    staff = lightning_staff()
    icons['item/lightning_staff'] = staff[0]
    for rel, img in list(icons.items()) + list(layers.items()):
        save(img, rel)
    save(glow_strip(staff), 'item/lightning_staff')   # the animated strip replaces the single-frame preview copy
    with open(os.path.join(TEX, 'item', 'lightning_staff.png.mcmeta'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'animation': STAFF_ANIMATION}, f, indent=2)
        f.write('\n')
    save(frozen_icon(), 'mob_effect/frozen')

    # mod list icon (2x2 materials) and banner (a row of weapons)
    icon = Img(128, 128, hexc('#15131c'))
    for i, mat in enumerate(MATERIALS):
        main_item = f'item/{mat}_crystal' if mat in ('cryolite', 'fulgurite') else f'item/{mat}_ingot'
        icon.paste(icons[main_item].scaled(4), (i % 2) * 64, (i // 2) * 64)
    icon.save(os.path.join(RESOURCES, 'oreborn_icon.png'))
    banner = Img(320, 128, hexc('#15131c'))
    row = ['item/cryolite_sword', 'item/fulgurite_pickaxe', 'item/umbrium_pickaxe', 'item/emberite_axe', 'item/umbrium_sword']
    for i, rel in enumerate(row):
        banner.paste(icons[rel].scaled(4), i * 64, 32)
    banner.save(os.path.join(RESOURCES, 'oreborn_banner.png'))

    # review sheets
    os.makedirs(os.path.join(ROOT, 'build'), exist_ok=True)
    preview_sheet(list(icons.values()), scale=5, cols=13).save(os.path.join(ROOT, 'build', 'texture_preview.png'))
    preview_sheet(list(layers.values()), scale=4, cols=2).save(os.path.join(ROOT, 'build', 'armor_preview.png'))
    ore_preview(ore_previews).save(os.path.join(ROOT, 'build', 'ore_preview.png'))
    print(f'wrote {len(icons) + len(layers) + len(ore_tex) + 1} textures, previews in build/*_preview.png')


if __name__ == '__main__':
    main()

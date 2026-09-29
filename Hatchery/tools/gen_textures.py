"""Draws Hatchery's textures from the vanilla ones (the client jar comes from the ForgeGradle cache, so run one
Gradle build first). Run from the project folder: python3 tools/gen_textures.py (Windows: py tools/gen_textures.py).

- assets/hatchery/textures/block/broken_spawner.png: the vanilla spawner cage, dead (no soul glint), cold and
  rusty, with snapped and bent bars and a cracked frame.
- hatchery_logo.png (128x128): the broken cage with a spawn egg in front of it.
- build/texture_preview.png: vanilla vs broken, 16x, for review.
"""
import glob
import math
import os
import sys
import zipfile
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixels import CLEAR, Img, decode_png, hexc, mix, preview_sheet  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_OUT = os.path.join(ROOT, 'src/main/resources/assets/hatchery/textures/block/broken_spawner.png')
LOGO_OUT = os.path.join(ROOT, 'src/main/resources/hatchery_logo.png')
PREVIEW_OUT = os.path.join(ROOT, 'build/texture_preview.png')

# Vanilla cage colours -> dead iron (same lightness order, no blue glow).
RECOLOUR = {
    '1e1827': '#1b1a1c',
    '182c39': '#232427',
    '2a2435': '#2b292a',
    '2a4455': '#393c3f',
    '406278': '#565b5e',
    '6e0453': '#2e2a2c',  # the soul glint in the middle has gone out
}
RUST = [hexc('#4d3325'), hexc('#5e3f2b'), hexc('#6f4c31')]
CRACK = hexc('#101012')
# Snapped bars: these pixels are gone ...
HOLES = [(4, 7), (5, 7), (10, 3), (11, 3), (2, 10), (0, 5), (15, 11), (7, 15), (8, 0)]
# ... and the loose ends bend into the gaps.
BENT = [(4, 8), (11, 2), (3, 10)]
CRACKS = [(1, 0), (2, 1), (3, 0), (15, 4), (14, 5), (0, 13), (1, 14), (12, 15), (13, 14)]


def client_jar():
    hits = glob.glob(os.path.expanduser('~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/minecraft_tasks/*/client.jar'))
    if not hits:
        sys.exit('client.jar not found: run ./gradlew build once')
    return sorted(hits)[-1]


def vanilla(jar, path):
    with zipfile.ZipFile(jar) as z:
        return decode_png(z.read('assets/minecraft/textures/%s.png' % path), path)


def noise(x, y, salt):
    """Deterministic 0..1 per pixel (Python's hash() changes every run)."""
    return (zlib.crc32(('%d,%d,%s' % (x, y, salt)).encode()) & 0xFFFF) / 65535.0


def broken_spawner(cage):
    out = Img(16, 16)
    for y in range(16):
        for x in range(16):
            c = cage.get(x, y)
            if c[3] == 0:
                continue
            key = '%02x%02x%02x' % c[:3]
            base = hexc(RECOLOUR.get(key, '#2b292a'))
            # Rust creeps over the bars (more on the lighter, exposed ones).
            bright = key in ('2a4455', '406278')
            if noise(x, y, 'rust') < (0.34 if bright else 0.16):
                base = mix(base, RUST[int(noise(x, y, 'tone') * len(RUST)) % len(RUST)], 0.75)
            out.set(x, y, base)
    for x, y in CRACKS:
        if out.get(x, y)[3]:
            out.set(x, y, CRACK)
    for x, y in HOLES:
        out.set(x, y, CLEAR)
    for x, y in BENT:
        out.set(x, y, hexc('#46403c'))
    return out


def logo(jar, cage):
    size = 128
    img = Img(size, size)
    top, bottom = hexc('#56627a'), hexc('#1f232c')
    rim, rim_dark = hexc('#6b7277'), hexc('#34383b')
    radius = 18
    for y in range(size):
        row = mix(top, bottom, y / (size - 1))
        for x in range(size):
            dx = max(radius - x, 0, x - (size - 1 - radius))
            dy = max(radius - y, 0, y - (size - 1 - radius))
            d = math.hypot(dx, dy)
            if d > radius:
                continue
            c = row
            if d > radius - 3 or min(x, y, size - 1 - x, size - 1 - y) < 3:
                c = rim_dark if d > radius - 1.5 or min(x, y, size - 1 - x, size - 1 - y) < 1.5 else rim
            img.set(x, y, c)
    # The cage, 16 -> 80 px, top left of centre.
    big = cage.scaled(5)
    img.paste(big, 10, 9)
    # A soft soul-blue glow, then a spawn egg in front, bottom right.
    egg = vanilla(jar, 'item/zombie_spawn_egg').scaled(4)
    ex, ey = 58, 56
    cx, cy = ex + egg.w / 2, ey + egg.h / 2
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - cx, y - cy)
            if d < 40 and img.get(x, y)[3]:
                img.blend(x, y, (110, 230, 255, int(150 * (1 - d / 40) ** 2)))
    img.paste(egg, ex, ey)
    return img



# Spawner modules: a riveted iron chip with a coloured cage window and a symbol.
MODULE_COLOURS = {
    'swarm': ('#a8f7be', '#3ddc6e', '#16803a'),
    'haste': ('#ffe08a', '#f0a430', '#a25e10'),
    'frailty': ('#dcb8ff', '#a45ee8', '#5b2a8f'),
    'daylight': ('#fffbd0', '#f5e35a', '#b89c1c'),
    'redstone': ('#ff9a8a', '#e03a2e', '#7e140e'),
}
MODULE_SYMBOLS = {
    'swarm': [(6, 6), (9, 6), (7, 9), (8, 9), (6, 7), (9, 7), (7, 8), (8, 8)],
    'haste': [(5, 5), (6, 6), (7, 7), (6, 8), (5, 9), (8, 5), (9, 6), (10, 7), (9, 8), (8, 9)],
    'frailty': [(5, 6), (6, 5), (7, 6), (8, 6), (9, 5), (10, 6), (5, 7), (10, 7), (6, 8), (9, 8), (7, 9), (8, 9), (8, 7), (7, 8)],
    'daylight': [(7, 7), (8, 7), (7, 8), (8, 8), (7, 5), (8, 5), (7, 10), (8, 10), (5, 7), (5, 8), (10, 7), (10, 8)],
    'redstone': [(8, 4), (7, 5), (8, 5), (6, 6), (7, 6), (8, 7), (9, 7), (8, 8), (7, 9), (7, 10)],
}


def module(name):
    light, base, dark = (hexc(c) for c in MODULE_COLOURS[name])
    img = Img(16, 16)
    frame, frame_light, outline = hexc('#4b4f58'), hexc('#6d727d'), hexc('#1d1f24')
    for y in range(1, 15):
        for x in range(1, 15):
            if (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
                continue
            edge = x in (1, 14) or y in (1, 14) or (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13))
            img.set(x, y, outline if edge else (frame_light if x == 2 or y == 2 else frame))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        img.set(x, y, frame_light)
    for y in range(4, 12):
        for x in range(4, 12):
            t = (x - 4 + y - 4) / 14.0
            c = light if t < 0.25 else base if t < 0.7 else dark
            img.set(x, y, outline if x in (4, 11) or y in (4, 11) else c)
    ink = hexc('#1d1f24') if name in ('daylight', 'haste') else hexc('#ffffff')
    for x, y in MODULE_SYMBOLS[name]:
        img.set(x, y, ink)
    return img


def main():
    jar = client_jar()
    cage = vanilla(jar, 'block/spawner')
    broken = broken_spawner(cage)
    os.makedirs(os.path.dirname(BLOCK_OUT), exist_ok=True)
    broken.save(BLOCK_OUT)
    logo(jar, broken).save(LOGO_OUT)
    os.makedirs(os.path.dirname(PREVIEW_OUT), exist_ok=True)
    modules = []
    for name in MODULE_COLOURS:
        img = module(name)
        img.save(os.path.join(os.path.dirname(os.path.dirname(BLOCK_OUT)), 'item', name + '_module.png'))
        modules.append(img)
    preview_sheet([cage, broken] + modules, scale=8, cols=7).save(PREVIEW_OUT)
    print('wrote', BLOCK_OUT, LOGO_OUT, PREVIEW_OUT)


if __name__ == '__main__':
    main()

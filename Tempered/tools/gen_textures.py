"""Draws the repair kit item textures (assets/tempered/textures/item/<material>_repair_kit.png): a small toolbox
with a leather handle and an iron clasp, its body made of the kit's material. Also writes build/kit_preview.png
(16x, next to the vanilla material items) for review. Run: python3 tools/gen_textures.py (Windows: py ...).
"""
import glob
import os
import sys
import zipfile
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixels import Img, decode_png, hexc, preview_sheet  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/assets/tempered/textures/item')
PREVIEW = os.path.join(ROOT, 'build/kit_preview.png')

# name: (light, base, dark, outline, vanilla item for the preview)
MATERIALS = {
    'wooden': ('#c7a266', '#a37e4b', '#76582f', '#3b2a15', 'oak_planks'),
    'stone': ('#ababab', '#8a8a8a', '#636363', '#2c2c2c', 'cobblestone'),
    'copper': ('#f2a67e', '#d27d55', '#9a5138', '#4a2418', 'copper_ingot'),
    'iron': ('#f5f5f5', '#d4d4d4', '#9a9a9a', '#393939', 'iron_ingot'),
    'golden': ('#fff38a', '#f0cc3a', '#b5861a', '#573b09', 'gold_ingot'),
    'diamond': ('#c8fff6', '#5be4d9', '#1f9f97', '#0e4643', 'diamond'),
    'netherite': ('#7d6d75', '#4e4349', '#2f282c', '#130f11', 'netherite_ingot'),
}
LEATHER, LEATHER_DARK = hexc('#7a4f2c'), hexc('#3e2716')
CLASP, CLASP_LIGHT, CLASP_DARK = hexc('#5a5a62'), hexc('#b8b8c2'), hexc('#26262b')


def noise(x, y, salt):
    return (zlib.crc32(('%d,%d,%s' % (x, y, salt)).encode()) & 0xFFFF) / 65535.0


def kit(name):
    light, base, dark, outline = (hexc(c) for c in MATERIALS[name][:4])
    img = Img(16, 16)
    # Leather handle (an arch above the lid).
    for x in range(6, 10):
        img.set(x, 3, LEATHER)
        img.set(x, 2, LEATHER_DARK)
    for y in range(3, 6):
        img.set(5, y, LEATHER_DARK)
        img.set(10, y, LEATHER_DARK)
    img.set(5, 2, LEATHER_DARK)
    img.set(10, 2, LEATHER_DARK)
    # Box: lid rows 6-8, seam at 9, body 10-13, outline around.
    x0, x1, y0, y1 = 1, 14, 6, 14
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (y0, y1)
            if edge:
                img.set(x, y, outline)
                continue
            c = base
            if y == y0 + 1 or x == x0 + 1:
                c = light
            if y == y1 - 1 or x == x1 - 1:
                c = dark
            if y == 9:
                c = outline  # the seam between lid and body
            img.set(x, y, c)
    # Material character.
    if name == 'wooden':
        for x in range(2, 14):
            img.set(x, 11, dark)
        for x, y in ((5, 10), (10, 12), (4, 13), (8, 7)):
            img.set(x, y, dark)
    elif name == 'stone':
        for y in range(7, 14):
            for x in range(2, 14):
                if y != 9 and noise(x, y, 'stone') < 0.22:
                    img.set(x, y, dark if noise(x, y, 'tone') < 0.6 else light)
    elif name == 'copper':
        for x, y in ((3, 12), (4, 13), (12, 8)):
            img.set(x, y, hexc('#5fae93'))
    elif name == 'diamond':
        for x, y in ((3, 7), (4, 10), (11, 11)):
            img.set(x, y, hexc('#ffffff'))
    elif name == 'netherite':
        for x, y in ((4, 11), (9, 12), (11, 7)):
            img.set(x, y, hexc('#6a5a63'))
    # Iron clasp over the seam.
    for y in range(8, 12):
        for x in (7, 8):
            img.set(x, y, CLASP_DARK if y in (8, 11) else CLASP)
    img.set(7, 9, CLASP_LIGHT)
    img.set(8, 10, CLASP_DARK)
    return img


def client_jar():
    hits = glob.glob(os.path.expanduser('~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/minecraft_tasks/*/client.jar'))
    return sorted(hits)[-1] if hits else None


def main():
    os.makedirs(OUT, exist_ok=True)
    kits = []
    for name in MATERIALS:
        img = kit(name)
        img.save(os.path.join(OUT, name + '_repair_kit.png'))
        kits.append(img)
    jar = client_jar()
    if jar:
        refs = []
        with zipfile.ZipFile(jar) as z:
            for name, spec in MATERIALS.items():
                path = 'assets/minecraft/textures/%s/%s.png' % ('block' if spec[4] in ('oak_planks', 'cobblestone') else 'item', spec[4])
                refs.append(decode_png(z.read(path), path))
        os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
        preview_sheet(kits + refs, scale=8, cols=len(MATERIALS)).save(PREVIEW)
    print('wrote %d kit textures' % len(kits))


if __name__ == '__main__':
    main()

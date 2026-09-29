"""Draws Townsfolk's textures from the vanilla villager (run one Gradle build first; the client jar comes from the
ForgeGradle cache). Run: python3 tools/gen_textures.py (Windows: py tools/gen_textures.py).

- assets/townsfolk/textures/item/villager.png: a villager face, 2x.
- townsfolk_logo.png (128x128): the face with an emerald.
- build/texture_preview.png for review.
"""
import glob
import math
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixels import Img, decode_png, hexc, mix, preview_sheet  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_OUT = os.path.join(ROOT, 'src/main/resources/assets/townsfolk/textures/item/villager.png')
LOGO_OUT = os.path.join(ROOT, 'src/main/resources/townsfolk_logo.png')
PREVIEW_OUT = os.path.join(ROOT, 'build/texture_preview.png')


def vanilla(path):
    jar = sorted(glob.glob(os.path.expanduser('~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/minecraft_tasks/*/client.jar')))[-1]
    with zipfile.ZipFile(jar) as z:
        return decode_png(z.read('assets/minecraft/textures/%s.png' % path), path)


def face(skin):
    """Head front (8x10 at 8,8) with the nose (2x4 front at 26,2) laid over rows 3-6."""
    img = Img(8, 10)
    for y in range(10):
        for x in range(8):
            img.set(x, y, skin.get(8 + x, 8 + y))
    for y in range(4):
        for x in range(2):
            img.set(3 + x, 3 + y, skin.get(26 + x, 2 + y))
    return img


def icon(head):
    """Rows 1-8 of the face at 2x: brow, eyes, nose and mouth fill the 16x16 slot."""
    img = Img(16, 16)
    for y in range(8):
        for x in range(8):
            c = head.get(x, y + 1)
            for dy in range(2):
                for dx in range(2):
                    img.set(2 * x + dx, 2 * y + dy, c)
    return img


def logo(head, emerald):
    size, radius = 128, 18
    img = Img(size, size)
    top, bottom, rim = hexc('#3f6b3a'), hexc('#1b2a1a'), hexc('#8a6a3c')
    for y in range(size):
        row = mix(top, bottom, y / (size - 1))
        for x in range(size):
            dx = max(radius - x, 0, x - (size - 1 - radius))
            dy = max(radius - y, 0, y - (size - 1 - radius))
            d = math.hypot(dx, dy)
            if d <= radius:
                img.set(x, y, rim if d > radius - 3 or min(x, y, size - 1 - x, size - 1 - y) < 3 else row)
    img.paste(head.scaled(8), 18, 14)
    img.paste(emerald.scaled(3), 70, 68)
    return img


def main():
    head = face(vanilla('entity/villager/villager'))
    os.makedirs(os.path.dirname(ITEM_OUT), exist_ok=True)
    item = icon(head)
    item.save(ITEM_OUT)
    logo(head, vanilla('item/emerald')).save(LOGO_OUT)
    os.makedirs(os.path.dirname(PREVIEW_OUT), exist_ok=True)
    preview_sheet([item, head], scale=12, cols=2).save(PREVIEW_OUT)
    print('wrote', ITEM_OUT, LOGO_OUT)


if __name__ == '__main__':
    main()

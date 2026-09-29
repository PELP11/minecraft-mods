"""Draws the mod logo (src/main/resources/tempered_logo.png, 128x128): a diamond pickaxe from the vanilla
textures under five mastery stars. Needs one Gradle build first (the vanilla client jar comes from the
ForgeGradle cache). Run: python3 tools/gen_logo.py (Windows: py tools/gen_logo.py).
"""
import glob
import math
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixels import Img, decode_png, hexc, mix, star_poly, point_in_poly  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src/main/resources/tempered_logo.png')


def client_jar():
    home = os.path.expanduser('~')
    hits = glob.glob(os.path.join(home, '.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/minecraft_tasks/*/client.jar'))
    if not hits:
        sys.exit('client.jar not found: run ./gradlew build once')
    return sorted(hits)[-1]


def texture(jar, name):
    with zipfile.ZipFile(jar) as z:
        return decode_png(z.read('assets/minecraft/textures/item/%s.png' % name), name)


def main():
    jar = client_jar()
    size = 128
    img = Img(size, size)
    top, bottom = hexc('#2b2419'), hexc('#121317')
    gold, gold_dark = hexc('#ffc94a'), hexc('#a0762a')
    # Rounded panel with a vertical gradient and a gold rim.
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
            if d > radius - 3 or x < 3 or y < 3 or x > size - 4 or y > size - 4:
                c = gold_dark if d > radius - 1.5 or min(x, y, size - 1 - x, size - 1 - y) < 1.5 else gold
            img.set(x, y, c)

    # The pickaxe, 16 px -> 80 px, with a soft glow behind it.
    pick = texture(jar, 'diamond_pickaxe').scaled(5)
    ox, oy = (size - pick.w) // 2, 36
    for y in range(pick.h):
        for x in range(pick.w):
            if pick.get(x, y)[3] > 0:
                for gx, gy in ((x - 2, y), (x + 2, y), (x, y - 2), (x, y + 2)):
                    px, py = ox + gx, oy + gy
                    if 0 <= px < size and 0 <= py < size and (not pick.inside(gx, gy) or pick.get(gx, gy)[3] == 0):
                        img.blend(px, py, (255, 214, 110, 70))
    img.paste(pick, ox, oy)

    # Five mastery stars in an arc.
    for i in range(5):
        a = math.radians(-90 + (i - 2) * 24)
        cx = size / 2 + math.cos(a) * 58
        cy = 86 + math.sin(a) * 58
        poly = star_poly(cx, cy, 8.5, 3.8)
        for y in range(int(cy - 10), int(cy + 11)):
            for x in range(int(cx - 10), int(cx + 11)):
                if point_in_poly(x + 0.5, y + 0.5, poly):
                    img.set(x, y, gold if (x - cx) + (y - cy) < 2 else gold_dark)
    img.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()

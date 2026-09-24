#!/usr/bin/env python3
"""Draws every Arsenal texture: 32x32 weapon icons, 16x16 rounds, attachments, ordnance, components and the
block faces. Pure Python (no Pillow); see tools/pixels.py for the PNG writer.

Weapons are laid out horizontally in a roomy buffer, then rotated onto the sprite so they sit on the diagonal
like a vanilla tool. The buffer is drawn at double resolution and averaged back down, which keeps the rotated
edges clean instead of stair-stepped.

Run from anywhere:  py tools/gen_textures.py            (writes the textures)
                    py tools/gen_textures.py preview    (also writes build/texture_preview.png)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixels import CLEAR, Img, darken, hexc, lighten, mix, preview_sheet  # noqa: E402
import gun_models  # noqa: E402
import vehicle_models  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, '..', 'src', 'main', 'resources'))
TEX = os.path.join(RES, 'assets', 'arsenal', 'textures')

BUF_W, BUF_H = 58, 24     # weapon design grid (double the final diagonal length)
GUN_ANGLE = 33.0
SPRITE = 32

written = []

# the F-14's stores and supplies are 3D items only: their texture is the painted atlas from gun_models
JET_ITEMS = ['aim9_sidewinder', 'aim54_phoenix', 'zuni_rocket', 'mk82_bomb', 'cannon_shells_20mm', 'flare_cartridges']


def ramp(*hexes):
    return [hexc(h) for h in hexes]


STEEL = ramp('#161a1f', '#333a42', '#4f5862', '#79838f')
GUNMETAL = ramp('#0d0f12', '#1d2126', '#2e343b', '#454d57')
WOOD = ramp('#241509', '#4e3018', '#6e4622', '#8d5f33')
POLYMER = ramp('#0b0d0f', '#181b1f', '#262b31', '#373e46')
TAN = ramp('#3d2f1b', '#6b552f', '#8d7142', '#ad8e58')
OLIVE = ramp('#161c12', '#2c3622', '#3f4d31', '#566645')
BRASS = ramp('#5e4210', '#97701c', '#bf9028', '#dfb44b')
LEAD = ramp('#2f3034', '#4f5157', '#6e717a', '#90949e')
COPPER = ramp('#4a2412', '#8a4526', '#b35f33', '#cf8149')
RUST = ramp('#3a1a0d', '#6d3115', '#8e4a1f', '#ad6a30')
URANIUM = ramp('#12240f', '#2a5a1d', '#49912c', '#7ed24a')
PLUTONIUM = ramp('#062423', '#0d5a52', '#17998a', '#48e3c4')
GLASS = ramp('#16222b', '#28455a', '#3e7093', '#8fd0ee')
LASER_RED = ramp('#3a0707', '#8a1414', '#c62222', '#ff5a4a')
WARN = ramp('#4a3a05', '#9c7a0c', '#d0a716', '#f5d34a')
BLACK = ramp('#07080a', '#101215', '#1a1d21', '#282c32')
CIRCUIT = ramp('#07130c', '#0e3a20', '#17663a', '#2fa35e')

OUTLINE = hexc('#05070a')


# ---- drawing helpers -------------------------------------------------------------------------------------

def bar(img, x, y, w, h, pal, lit=True):
    """A shaded horizontal block: highlight along the top, shadow along the bottom and the ends."""
    if w <= 0 or h <= 0:
        return
    img.rect(x, y, x + w, y + h, pal[1])
    if h >= 2:
        img.hline(x, x + w, y, pal[2] if lit else pal[1])
        img.hline(x, x + w, y + h - 1, pal[0])
    if h >= 4 and lit:
        img.hline(x + 1, x + w - 1, y + 1, pal[3])
    if w >= 2:
        img.vline(x, y, y + h, pal[0])
        img.vline(x + w - 1, y, y + h, pal[0])


def slant(img, x, y, w, h, lean, pal):
    """A block that leans back as it goes down: pistol grips, magazines, stocks."""
    for row in range(h):
        offset = int(round(lean * row / max(1, h - 1)))
        img.rect(x + offset, y + row, x + offset + w, y + row + 1, pal[1])
        img.set(x + offset, y + row, pal[0])
        img.set(x + offset + w - 1, y + row, pal[0])
    img.hline(x, x + w, y, pal[2])
    img.hline(x + int(round(lean)), x + int(round(lean)) + w, y + h - 1, pal[0])


def curve_mag(img, x, y, w, h, bend, pal):
    """A banana magazine: the classic AK curve."""
    for row in range(h):
        t = row / max(1, h - 1)
        offset = int(round(bend * t * t))
        img.rect(x - offset, y + row, x - offset + w, y + row + 1, pal[1])
        img.set(x - offset, y + row, pal[0])
        img.set(x - offset + w - 1, y + row, pal[0])
    img.hline(x, x + w, y, pal[2])


def tube(img, x, y, w, h, pal):
    """A round tube seen from the side: bright band above centre, dark below."""
    img.rect(x, y, x + w, y + h, pal[1])
    img.hline(x, x + w, y, pal[0])
    img.hline(x, x + w, y + h - 1, pal[0])
    if h >= 3:
        img.hline(x, x + w, y + 1, pal[3])
    if h >= 5:
        img.hline(x, x + w, y + h - 2, pal[0])


def scope(img, x, y, w, h, pal=BLACK):
    tube(img, x, y, w, h, pal)
    img.rect(x + w - 3, y + 1, x + w - 1, y + h - 1, GLASS[3])
    img.rect(x + 1, y + 1, x + 3, y + h - 1, GLASS[2])
    # turret
    img.rect(x + w // 2 - 2, y - 2, x + w // 2 + 2, y, pal[2])


def drum(img, cx, cy, r, pal):
    for dy in range(-r, r + 1):
        for dx in range(-r, r + 1):
            d = math.hypot(dx, dy)
            if d <= r:
                img.set(cx + dx, cy + dy, pal[1] if d < r - 1 else pal[0])
    img.rect(cx - 2, cy - 2, cx + 2, cy + 2, pal[0])


def speckle(img, x0, y0, x1, y1, color, seed, density=7):
    import zlib
    n = zlib.crc32(seed.encode())
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = (n * 1103515245 + 12345) & 0x7FFFFFFF
            if n % density == 0 and img.inside(x, y) and img.get(x, y)[3]:
                img.set(x, y, color)


def rotate_into(src, deg, out_size):
    out = Img(out_size, out_size)
    rad = math.radians(deg)
    cos, sin = math.cos(rad), math.sin(rad)
    scx, scy = src.w / 2.0, src.h / 2.0
    oc = out_size / 2.0
    for y in range(out_size):
        for x in range(out_size):
            dx, dy = x - oc + 0.5, y - oc + 0.5
            sx = cos * dx + sin * dy + scx
            sy = -sin * dx + cos * dy + scy
            ix, iy = int(sx), int(sy)
            if 0 <= ix < src.w and 0 <= iy < src.h:
                c = src.px[iy][ix]
                if c[3]:
                    out.px[y][x] = c
    return out


def downsample(src, k):
    out = Img(src.w // k, src.h // k)
    for y in range(out.h):
        for x in range(out.w):
            r = g = b = n = 0
            for yy in range(k):
                row = src.px[y * k + yy]
                for xx in range(k):
                    c = row[x * k + xx]
                    if c[3]:
                        r += c[0]
                        g += c[1]
                        b += c[2]
                        n += 1
            if n * 2 >= k * k:
                out.px[y][x] = (r // n, g // n, b // n, 255)
    return out


def finish_gun(buf):
    """Buffer -> rotated, cleaned 32x32 sprite with an outline."""
    big = buf.scaled(2)
    rotated = rotate_into(big, GUN_ANGLE, SPRITE * 4)
    sprite = downsample(rotated, 4)
    sprite.outline(OUTLINE)
    return sprite


def save(img, *parts):
    path = os.path.join(TEX, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    written.append(os.path.join(*parts))


# =====================================================================================================
# weapons
# =====================================================================================================

def gun_ak47():
    b = Img(BUF_W, BUF_H)
    tube(b, 34, 9, 22, 3, STEEL)                 # barrel
    bar(b, 30, 5, 14, 3, STEEL)                  # gas tube
    bar(b, 28, 8, 12, 5, WOOD)                   # handguard
    bar(b, 16, 7, 14, 7, STEEL)                  # receiver
    bar(b, 2, 8, 15, 5, WOOD)                    # stock
    slant(b, 18, 14, 4, 7, 2, WOOD)              # grip
    curve_mag(b, 24, 14, 6, 8, 3, STEEL)
    bar(b, 48, 6, 3, 4, STEEL)                   # front sight
    bar(b, 26, 5, 3, 3, STEEL)                   # rear sight
    return finish_gun(b)


def gun_m4a1():
    b = Img(BUF_W, BUF_H)
    tube(b, 38, 9, 18, 3, GUNMETAL)
    bar(b, 28, 8, 12, 5, POLYMER)                # handguard
    bar(b, 16, 7, 13, 7, GUNMETAL)               # upper + lower
    bar(b, 18, 4, 16, 3, GUNMETAL)               # carry rail
    bar(b, 4, 8, 13, 4, POLYMER)                 # tube stock
    bar(b, 2, 7, 4, 6, POLYMER)
    slant(b, 18, 14, 4, 7, 2, POLYMER)
    bar(b, 24, 14, 5, 8, GUNMETAL)               # STANAG mag
    bar(b, 50, 5, 3, 5, GUNMETAL)
    return finish_gun(b)


def gun_scar_h():
    b = Img(BUF_W, BUF_H)
    tube(b, 38, 9, 18, 3, GUNMETAL)
    bar(b, 26, 7, 14, 6, TAN)                    # long rail handguard
    bar(b, 14, 6, 13, 8, TAN)
    bar(b, 16, 3, 18, 3, GUNMETAL)               # top rail
    bar(b, 2, 7, 13, 6, TAN)                     # folding stock
    slant(b, 16, 14, 4, 7, 2, POLYMER)
    bar(b, 22, 14, 6, 8, GUNMETAL)
    bar(b, 50, 5, 3, 5, GUNMETAL)
    return finish_gun(b)


def gun_aug():
    b = Img(BUF_W, BUF_H)
    tube(b, 40, 9, 16, 3, GUNMETAL)
    bar(b, 6, 7, 36, 8, OLIVE)                   # one-piece bullpup shell
    scope(b, 14, 2, 18, 5, OLIVE)                # integral optic
    slant(b, 20, 15, 4, 6, 1, OLIVE)             # grip, well forward
    bar(b, 28, 15, 5, 7, GUNMETAL)               # magazine behind the grip
    bar(b, 34, 6, 6, 4, OLIVE)                   # forward vertical grip
    return finish_gun(b)


def pistol(slide_pal, frame_pal, length=26, heavy=False, gold=False):
    b = Img(BUF_W, BUF_H)
    x0 = (BUF_W - length) // 2
    top = 7
    bar(b, x0, top, length, 6 if heavy else 5, slide_pal)
    tube(b, x0 + length - 2, top + 2, 4, 2, slide_pal)
    for i in range(x0 + 2, x0 + 10, 2):          # slide serrations
        b.vline(i, top + 1, top + (5 if heavy else 4), slide_pal[0])
    slant(b, x0 + 3, top + (6 if heavy else 5), 5, 9, 3, frame_pal)
    b.hline(x0 + 8, x0 + 16, top + (6 if heavy else 5), frame_pal[0])
    b.rect(x0 + 9, top + (7 if heavy else 6), x0 + 11, top + (9 if heavy else 8), frame_pal[1])
    if gold:
        b.rect(x0 + length - 8, top + 1, x0 + length - 3, top + 3, BRASS[2])
    if heavy:
        bar(b, x0 + 6, top - 2, length - 10, 2, slide_pal)   # scope rail
    return finish_gun(b)


def gun_glock17():
    return pistol(POLYMER, POLYMER)


def gun_m1911():
    return pistol(STEEL, WOOD, length=24, gold=True)


def gun_m9():
    b = pistol(GUNMETAL, GUNMETAL, length=25)
    return b


def gun_deagle():
    return pistol(STEEL, BLACK, length=30, heavy=True)


def gun_barrett():
    b = Img(BUF_W, BUF_H)
    tube(b, 30, 9, 26, 3, GUNMETAL)
    bar(b, 50, 7, 6, 6, GUNMETAL)                # arrow muzzle brake
    b.rect(52, 6, 54, 14, GUNMETAL[0])
    bar(b, 10, 6, 22, 8, OLIVE)                  # receiver
    scope(b, 16, 1, 18, 5)
    bar(b, 2, 7, 9, 6, OLIVE)                    # stock
    slant(b, 14, 14, 4, 7, 2, POLYMER)
    bar(b, 20, 14, 6, 7, GUNMETAL)
    b.line(34, 12, 30, 20, GUNMETAL[1])          # bipod
    b.line(34, 12, 38, 20, GUNMETAL[1])
    return finish_gun(b)


def gun_svd():
    b = Img(BUF_W, BUF_H)
    tube(b, 34, 9, 22, 3, STEEL)
    bar(b, 26, 8, 10, 5, WOOD)
    bar(b, 14, 7, 13, 6, STEEL)
    scope(b, 18, 2, 16, 4)
    bar(b, 2, 7, 13, 6, WOOD)                    # skeleton stock
    b.rect(6, 9, 11, 11, CLEAR)
    slant(b, 16, 13, 4, 7, 2, WOOD)
    bar(b, 22, 13, 5, 7, STEEL)
    return finish_gun(b)


def gun_awp():
    b = Img(BUF_W, BUF_H)
    tube(b, 32, 9, 24, 3, GUNMETAL)
    bar(b, 12, 6, 21, 7, OLIVE)                  # chassis
    scope(b, 16, 1, 20, 5)
    bar(b, 2, 6, 11, 8, OLIVE)                   # thumbhole stock
    b.rect(5, 9, 9, 12, CLEAR)
    slant(b, 16, 13, 4, 7, 1, OLIVE)
    bar(b, 22, 13, 5, 5, GUNMETAL)
    bar(b, 30, 4, 4, 3, GUNMETAL)                # bolt handle
    return finish_gun(b)


def gun_remington870():
    b = Img(BUF_W, BUF_H)
    tube(b, 30, 7, 26, 4, GUNMETAL)              # barrel
    tube(b, 30, 12, 22, 3, GUNMETAL)             # magazine tube
    bar(b, 32, 11, 10, 5, WOOD)                  # pump
    bar(b, 16, 7, 15, 7, GUNMETAL)
    bar(b, 2, 7, 15, 6, WOOD)
    slant(b, 16, 14, 4, 6, 3, WOOD)
    return finish_gun(b)


def gun_spas12():
    b = Img(BUF_W, BUF_H)
    tube(b, 30, 7, 26, 4, BLACK)
    tube(b, 30, 12, 22, 3, BLACK)
    bar(b, 34, 11, 10, 5, POLYMER)
    bar(b, 14, 6, 17, 8, BLACK)
    bar(b, 6, 4, 9, 4, BLACK)                    # folding stock over the top
    b.line(6, 6, 14, 9, BLACK[2])
    slant(b, 16, 14, 4, 7, 2, POLYMER)
    bar(b, 50, 4, 3, 4, BLACK)
    return finish_gun(b)


def gun_aa12():
    b = Img(BUF_W, BUF_H)
    tube(b, 36, 8, 20, 4, GUNMETAL)
    bar(b, 14, 6, 23, 9, BLACK)                  # boxy receiver
    bar(b, 16, 3, 18, 3, BLACK)                  # top rail
    bar(b, 2, 7, 13, 6, BLACK)
    slant(b, 16, 15, 4, 6, 1, POLYMER)
    drum(b, 27, 19, 5, GUNMETAL)                 # drum magazine
    bar(b, 30, 5, 5, 3, BLACK)
    return finish_gun(b)


def gun_sawed_off():
    b = Img(BUF_W, BUF_H)
    x0 = 14
    tube(b, x0 + 10, 7, 18, 4, STEEL)            # over
    tube(b, x0 + 10, 11, 18, 4, STEEL)           # under
    bar(b, x0, 6, 11, 10, STEEL)
    slant(b, x0 + 1, 15, 5, 7, 3, WOOD)
    bar(b, x0 + 4, 5, 6, 2, WOOD)
    return finish_gun(b)


def gun_rpg7():
    b = Img(BUF_W, BUF_H)
    tube(b, 4, 9, 40, 5, OLIVE)                  # launch tube
    tube(b, 20, 8, 12, 7, OLIVE)                 # flared centre
    for x in range(44, 52):                      # warhead cone
        h = 9 - (x - 44)
        b.rect(x, 12 - h // 2, x + 1, 12 + h // 2 + 1, RUST[1])
    b.rect(52, 10, 56, 14, RUST[0])
    slant(b, 16, 15, 4, 7, 2, WOOD)
    bar(b, 8, 6, 8, 3, WOOD)                     # optic mount
    return finish_gun(b)


def gun_m32():
    b = Img(BUF_W, BUF_H)
    tube(b, 38, 9, 18, 4, OLIVE)
    drum(b, 30, 12, 7, GUNMETAL)                 # six-round cylinder
    bar(b, 12, 8, 14, 7, OLIVE)
    bar(b, 2, 6, 11, 5, OLIVE)                   # collapsing stock
    slant(b, 14, 15, 4, 7, 2, POLYMER)
    bar(b, 16, 4, 14, 3, GUNMETAL)               # sight bridge
    return finish_gun(b)


def gun_railgun():
    b = Img(BUF_W, BUF_H)
    bar(b, 30, 7, 26, 4, GUNMETAL)               # upper rail
    bar(b, 30, 12, 26, 4, GUNMETAL)              # lower rail
    for x in range(32, 54, 4):                   # accelerator coils
        b.rect(x, 6, x + 2, 17, PLUTONIUM[2])
        b.rect(x, 11, x + 2, 12, PLUTONIUM[3])
    bar(b, 10, 5, 21, 11, BLACK)                 # capacitor housing
    b.rect(13, 8, 21, 13, PLUTONIUM[1])
    b.rect(14, 9, 20, 12, PLUTONIUM[3])
    bar(b, 2, 7, 9, 7, BLACK)
    slant(b, 14, 16, 4, 6, 1, BLACK)
    return finish_gun(b)


GUN_DRAWERS = {
    'ak47': gun_ak47, 'm4a1': gun_m4a1, 'scar_h': gun_scar_h, 'aug': gun_aug,
    'glock17': gun_glock17, 'm1911': gun_m1911, 'm9': gun_m9, 'deagle': gun_deagle,
    'barrett_m82': gun_barrett, 'svd_dragunov': gun_svd, 'awp': gun_awp,
    'remington_870': gun_remington870, 'spas12': gun_spas12, 'aa12': gun_aa12,
    'sawed_off': gun_sawed_off, 'rpg7': gun_rpg7, 'm32_launcher': gun_m32, 'railgun': gun_railgun,
}


# =====================================================================================================
# ammunition (16x16, standing upright)
# =====================================================================================================

def cartridge(case_h, case_w, tip_h, tip_pal, case_pal=BRASS, rim=True):
    img = Img(16, 16)
    cx = 8
    x0 = cx - case_w // 2
    top = 14 - case_h - tip_h
    # bullet
    for row in range(tip_h):
        t = row / max(1, tip_h - 1)
        w = max(1, int(round(case_w * (0.35 + 0.65 * t))))
        x = cx - w // 2
        img.rect(x, top + row, x + w, top + row + 1, tip_pal[1])
        img.set(x, top + row, tip_pal[0])
        img.set(x + w - 1, top + row, tip_pal[0])
    img.vline(cx - 1, top + 1, top + tip_h, tip_pal[3])
    # case
    bar(img, x0, top + tip_h, case_w, case_h, case_pal)
    img.vline(x0 + 1, top + tip_h, 14, case_pal[3])
    if rim:
        img.rect(x0 - 1, 13, x0 + case_w + 1, 15, case_pal[0])
        img.rect(x0, 13, x0 + case_w, 14, case_pal[2])
    img.outline(OUTLINE)
    return img


def shell(hull_pal):
    img = Img(16, 16)
    bar(img, 5, 2, 6, 9, hull_pal)
    img.hline(5, 11, 2, hull_pal[3])
    bar(img, 5, 10, 6, 5, BRASS)
    img.rect(4, 12, 12, 15, BRASS[0])
    img.rect(5, 12, 11, 14, BRASS[2])
    img.rect(6, 3, 8, 9, lighten(hull_pal[2], 0.25))
    img.outline(OUTLINE)
    return img


def ammo_grenade_40mm():
    img = Img(16, 16)
    for row in range(6):
        t = row / 5
        w = int(round(4 + 4 * t))
        x = 8 - w // 2
        img.rect(x, 1 + row, x + w, 2 + row, OLIVE[1])
        img.set(x, 1 + row, OLIVE[0])
        img.set(x + w - 1, 1 + row, OLIVE[0])
    bar(img, 4, 7, 8, 4, OLIVE)
    img.rect(4, 8, 12, 9, WARN[2])
    bar(img, 4, 10, 8, 5, BRASS)
    img.outline(OUTLINE)
    return img


def ammo_rocket():
    img = Img(16, 16)
    for row in range(5):                          # conical warhead
        w = 2 + row
        x = 8 - w // 2
        img.rect(x, 1 + row, x + w, 2 + row, RUST[1])
        img.set(x, 1 + row, RUST[0])
    bar(img, 5, 6, 6, 4, RUST)
    bar(img, 6, 9, 4, 4, OLIVE)                   # motor
    img.line(4, 13, 6, 10, OLIVE[0])              # fins
    img.line(11, 13, 9, 10, OLIVE[0])
    img.rect(4, 13, 12, 15, OLIVE[1])
    img.outline(OUTLINE)
    return img


def ammo_rail_slug():
    img = Img(16, 16)
    for row in range(5):
        w = 2 + row
        x = 8 - w // 2
        img.rect(x, 2 + row, x + w, 3 + row, LEAD[2])
        img.set(x, 2 + row, LEAD[0])
    bar(img, 5, 7, 6, 7, LEAD)
    img.vline(6, 7, 14, LEAD[3])
    img.rect(5, 9, 11, 10, PLUTONIUM[3])
    img.rect(5, 12, 11, 13, PLUTONIUM[2])
    img.outline(OUTLINE)
    return img


AMMO_DRAWERS = {
    'ammo_9mm': lambda: cartridge(6, 4, 3, LEAD),
    'ammo_45acp': lambda: cartridge(6, 5, 3, COPPER),
    'ammo_50ae': lambda: cartridge(7, 6, 4, COPPER),
    'ammo_556': lambda: cartridge(8, 4, 4, COPPER),
    'ammo_762': lambda: cartridge(8, 5, 4, LEAD),
    'ammo_338': lambda: cartridge(9, 5, 4, GUNMETAL),
    'ammo_50bmg': lambda: cartridge(9, 7, 5, GUNMETAL),
    'shell_buckshot': lambda: shell(LASER_RED),
    'shell_slug': lambda: shell(ramp('#123a1a', '#1d6b2e', '#2a9440', '#4fc468')),
    'grenade_40mm': ammo_grenade_40mm,
    'rocket_round': ammo_rocket,
    'rocket_thermobaric': ammo_rocket,           # 3D item: the sprite only feeds the preview sheet
    'rail_slug': ammo_rail_slug,
}


# =====================================================================================================
# attachments (16x16)
# =====================================================================================================

def att_suppressor():
    img = Img(16, 16)
    tube(img, 1, 6, 14, 5, BLACK)
    for x in range(3, 13, 3):
        img.vline(x, 7, 10, BLACK[0])
    img.rect(13, 7, 15, 10, GUNMETAL[1])
    img.outline(OUTLINE)
    return img


def att_muzzle_brake():
    img = Img(16, 16)
    tube(img, 2, 6, 12, 5, STEEL)
    for x in range(4, 13, 3):
        img.rect(x, 5, x + 2, 7, CLEAR)
        img.rect(x, 10, x + 2, 12, CLEAR)
    img.outline(OUTLINE)
    return img


def att_heavy_barrel():
    img = Img(16, 16)
    tube(img, 1, 7, 13, 4, STEEL)
    bar(img, 10, 5, 5, 7, STEEL)
    img.rect(1, 8, 3, 10, GUNMETAL[0])
    img.outline(OUTLINE)
    return img


def att_red_dot():
    img = Img(16, 16)
    bar(img, 3, 4, 10, 7, BLACK)
    img.rect(4, 5, 12, 10, GLASS[1])
    img.rect(7, 6, 9, 8, LASER_RED[3])
    img.rect(3, 11, 13, 13, BLACK[2])
    img.outline(OUTLINE)
    return img


def att_acog():
    img = Img(16, 16)
    tube(img, 1, 5, 14, 6, BLACK)
    img.rect(12, 6, 15, 10, GLASS[3])
    img.rect(1, 6, 4, 10, GLASS[2])
    img.rect(6, 3, 10, 5, BLACK[2])
    img.rect(2, 11, 14, 13, BLACK[1])
    img.outline(OUTLINE)
    return img


def att_thermal():
    img = Img(16, 16)
    tube(img, 1, 4, 14, 7, GUNMETAL)
    img.rect(11, 5, 15, 10, LASER_RED[2])
    img.rect(12, 6, 14, 9, WARN[3])
    img.rect(2, 5, 5, 10, PLUTONIUM[2])
    img.rect(5, 2, 9, 4, GUNMETAL[2])
    img.rect(2, 11, 14, 13, GUNMETAL[1])
    img.outline(OUTLINE)
    return img


def magazine(h, pal, curved=False, wide=False):
    img = Img(16, 16)
    w = 8 if wide else 6
    x = 8 - w // 2
    if curved:
        curve_mag(img, x + 2, 15 - h, w, h, 3, pal)
    else:
        bar(img, x, 15 - h, w, h, pal)
    img.rect(x, 15 - h, x + w, 14 - h + 2, pal[3])
    img.outline(OUTLINE)
    return img


def att_drum():
    img = Img(16, 16)
    drum(img, 8, 9, 6, GUNMETAL)
    img.rect(6, 1, 10, 5, GUNMETAL[1])
    img.rect(7, 7, 9, 11, GUNMETAL[0])
    for a in range(0, 360, 60):
        x = 8 + int(round(3.5 * math.cos(math.radians(a))))
        y = 9 + int(round(3.5 * math.sin(math.radians(a))))
        img.set(x, y, BRASS[2])
    img.outline(OUTLINE)
    return img


def att_laser_sight():
    img = Img(16, 16)
    bar(img, 3, 6, 9, 5, BLACK)
    img.rect(11, 7, 14, 10, LASER_RED[2])
    img.rect(12, 8, 14, 9, LASER_RED[3])
    img.rect(4, 11, 10, 13, GUNMETAL[1])
    img.rect(5, 7, 7, 9, LASER_RED[1])
    img.outline(OUTLINE)
    return img


def att_foregrip():
    img = Img(16, 16)
    bar(img, 3, 2, 10, 3, GUNMETAL)
    bar(img, 6, 4, 4, 10, POLYMER)
    for y in range(6, 13, 2):
        img.hline(6, 10, y, POLYMER[0])
    img.outline(OUTLINE)
    return img


def att_bipod():
    img = Img(16, 16)
    bar(img, 5, 2, 6, 3, GUNMETAL)
    img.line(7, 5, 3, 13, GUNMETAL[2])
    img.line(8, 5, 12, 13, GUNMETAL[2])
    img.line(6, 5, 2, 13, GUNMETAL[1])
    img.line(9, 5, 13, 13, GUNMETAL[1])
    img.rect(1, 13, 4, 15, GUNMETAL[0])
    img.rect(11, 13, 14, 15, GUNMETAL[0])
    img.outline(OUTLINE)
    return img


ATTACHMENT_DRAWERS = {
    'suppressor': att_suppressor,
    'muzzle_brake': att_muzzle_brake,
    'heavy_barrel': att_heavy_barrel,
    'red_dot_sight': att_red_dot,
    'acog_scope': att_acog,
    'thermal_scope': att_thermal,
    'extended_mag': lambda: magazine(12, GUNMETAL, curved=True),
    'drum_mag': att_drum,
    'quickdraw_mag': lambda: magazine(9, COPPER, wide=True),
    'laser_sight': att_laser_sight,
    'foregrip': att_foregrip,
    'bipod': att_bipod,
}


# =====================================================================================================
# grenades and weapons of mass destruction (16x16)
# =====================================================================================================

def grenade_body(pal, band=None, stripes=False):
    img = Img(16, 16)
    for y in range(4, 15):
        t = (y - 4) / 10.0
        w = int(round(8 - 2 * abs(t - 0.45) * 2))
        x = 8 - w // 2
        img.rect(x, y, x + w, y + 1, pal[1])
        img.set(x, y, pal[0])
        img.set(x + w - 1, y, pal[0])
    img.rect(6, 1, 10, 4, GUNMETAL[1])            # fuse head
    img.rect(9, 0, 13, 2, GUNMETAL[2])            # spoon
    img.rect(11, 1, 13, 4, GUNMETAL[0])
    img.vline(5, 6, 13, lighten(pal[2], 0.2))
    if band:
        img.rect(3, 7, 13, 9, band[2])
    if stripes:
        for y in range(5, 14, 3):
            img.hline(4, 12, y, pal[0])
        for x in range(4, 13, 3):
            img.vline(x, 5, 14, pal[0])
    img.outline(OUTLINE)
    return img


def canister(pal, band):
    img = Img(16, 16)
    bar(img, 4, 3, 8, 12, pal)
    img.rect(6, 1, 10, 3, GUNMETAL[1])
    img.rect(4, 5, 12, 7, band[2])
    img.rect(4, 11, 12, 13, band[1])
    img.vline(5, 4, 14, lighten(pal[2], 0.25))
    img.outline(OUTLINE)
    return img


def wmd_singularity():
    img = Img(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 6.5:
                if d < 2.5:
                    img.set(x, y, hexc('#05010a'))
                elif d < 4.2:
                    img.set(x, y, mix(hexc('#2a0b52'), hexc('#7b2ecf'), (d - 2.5) / 1.7))
                elif d < 5.6:
                    img.set(x, y, mix(hexc('#7b2ecf'), hexc('#d08bff'), (d - 4.2) / 1.4))
                else:
                    img.set(x, y, hexc('#1a0a2e'))
    for a in range(0, 360, 24):                   # accretion ring
        x = 7 + int(round(6.0 * math.cos(math.radians(a))))
        y = 7 + int(round(2.4 * math.sin(math.radians(a))))
        img.set(x, y, hexc('#f0d0ff'))
    img.outline(OUTLINE)
    return img


def wmd_thermobaric():
    img = Img(16, 16)
    for y in range(2, 14):
        t = (y - 2) / 11.0
        w = int(round(10 - 5 * abs(t - 0.5) * 2))
        x = 8 - w // 2
        img.rect(x, y, x + w, y + 1, RUST[1])
        img.set(x, y, RUST[0])
        img.set(x + w - 1, y, RUST[0])
    img.rect(3, 6, 13, 8, WARN[2])
    img.rect(6, 6, 10, 8, LASER_RED[2])
    img.line(3, 13, 6, 15, RUST[0])               # fins
    img.line(12, 13, 9, 15, RUST[0])
    img.rect(6, 0, 10, 2, GUNMETAL[1])
    img.vline(5, 4, 12, lighten(RUST[2], 0.3))
    img.outline(OUTLINE)
    return img


def wmd_ion_beacon():
    img = Img(16, 16)
    bar(img, 3, 9, 10, 6, GUNMETAL)
    img.rect(5, 11, 11, 13, CIRCUIT[2])
    for y in range(0, 10):                        # the beam it calls down
        t = y / 9.0
        w = max(1, int(round(1 + 4 * t)))
        x = 8 - w // 2
        img.rect(x, y, x + w, y + 1, mix(hexc('#bff2ff'), hexc('#2aa7d8'), t))
    img.rect(6, 7, 10, 10, GLASS[3])
    img.set(4, 10, LASER_RED[3])
    img.outline(OUTLINE)
    return img


def wmd_chemical():
    img = Img(16, 16)
    bar(img, 3, 3, 10, 12, OLIVE)
    img.rect(3, 6, 13, 8, URANIUM[2])
    img.rect(3, 11, 13, 12, URANIUM[1])
    img.rect(6, 1, 10, 3, GUNMETAL[1])
    # a crude hazard trefoil
    img.rect(7, 8, 9, 11, hexc('#0b0f08'))
    img.set(6, 9, hexc('#0b0f08'))
    img.set(9, 9, hexc('#0b0f08'))
    img.vline(5, 4, 14, lighten(OLIVE[2], 0.25))
    img.outline(OUTLINE)
    return img


ORDNANCE_DRAWERS = {
    'frag_grenade': lambda: grenade_body(OLIVE, stripes=True),
    'incendiary_grenade': lambda: canister(LASER_RED, WARN),
    'flashbang': lambda: canister(LEAD, WARN),
    'smoke_grenade': lambda: canister(OLIVE, GLASS),
    'singularity_charge': wmd_singularity,
    'thermobaric_bomb': wmd_thermobaric,
    'ion_cannon_beacon': wmd_ion_beacon,
    'chemical_warhead': wmd_chemical,
}


# =====================================================================================================
# components (16x16)
# =====================================================================================================

def ingot(pal, glow=None):
    img = Img(16, 16)
    for row in range(6):
        inset = row // 3
        img.rect(2 + inset + row // 5, 5 + row, 14 - inset - row // 5, 6 + row, pal[1])
    img.hline(3, 13, 5, pal[3])
    img.hline(3, 13, 6, pal[2])
    img.hline(3, 13, 10, pal[0])
    img.rect(3, 9, 13, 11, pal[1])
    if glow:
        for x in range(4, 12, 3):
            img.set(x, 8, glow)
    img.outline(OUTLINE)
    return img


def nugget_pile(pal):
    img = Img(16, 16)
    for (cx, cy, r) in ((5, 10, 3), (10, 11, 3), (8, 6, 3)):
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r:
                    img.set(cx + dx, cy + dy, pal[1] if dx * dx + dy * dy < (r - 1) ** 2 else pal[0])
        img.set(cx - 1, cy - 1, pal[3])
    img.outline(OUTLINE)
    return img


def comp_steel_blend():
    img = Img(16, 16)
    for (cx, cy, r, pal) in ((5, 11, 3, LEAD), (10, 11, 3, BLACK), (8, 7, 3, LEAD), (11, 6, 2, BLACK)):
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r:
                    img.set(cx + dx, cy + dy, pal[1])
        img.set(cx - 1, cy - 1, pal[3])
    img.outline(OUTLINE)
    return img


def comp_brass_casing():
    img = Img(16, 16)
    bar(img, 4, 3, 8, 11, BRASS)
    img.rect(5, 3, 11, 5, BRASS[0])               # open mouth
    img.rect(6, 4, 10, 5, hexc('#2a1c06'))
    img.vline(5, 5, 13, BRASS[3])
    img.rect(3, 12, 13, 15, BRASS[0])
    img.rect(4, 12, 12, 14, BRASS[2])
    img.outline(OUTLINE)
    return img


def comp_bullet_tip():
    img = Img(16, 16)
    for (ox, oy) in ((2, 4), (8, 7)):
        for row in range(6):
            t = row / 5.0
            w = max(1, int(round(2 + 3 * t)))
            x = ox + 3 - w // 2
            img.rect(x, oy + row, x + w, oy + row + 1, COPPER[1])
            img.set(x, oy + row, COPPER[0])
        img.vline(ox + 2, oy + 1, oy + 6, COPPER[3])
    img.outline(OUTLINE)
    return img


def comp_propellant():
    img = Img(16, 16)
    bar(img, 3, 5, 10, 9, POLYMER)
    img.rect(4, 7, 12, 12, WARN[1])
    speckle(img, 4, 7, 12, 12, WARN[3], 'propellant', 4)
    img.rect(6, 2, 10, 5, GUNMETAL[1])
    img.rect(3, 6, 13, 7, WARN[2])
    img.outline(OUTLINE)
    return img


def comp_gun_barrel():
    img = Img(16, 16)
    tube(img, 1, 6, 14, 5, STEEL)
    img.rect(1, 7, 3, 10, GUNMETAL[0])            # bore
    for x in range(5, 13, 3):
        img.vline(x, 6, 11, STEEL[0])
    img.outline(OUTLINE)
    return img


def comp_receiver():
    img = Img(16, 16)
    bar(img, 2, 4, 12, 8, GUNMETAL)
    img.rect(4, 6, 10, 8, BLACK[0])               # ejection port
    img.rect(3, 10, 13, 11, GUNMETAL[0])
    img.rect(11, 5, 13, 7, STEEL[2])
    img.outline(OUTLINE)
    return img


def comp_trigger():
    img = Img(16, 16)
    bar(img, 3, 3, 10, 5, STEEL)
    img.rect(5, 8, 7, 12, STEEL[1])               # trigger blade
    img.line(4, 8, 3, 12, STEEL[0])
    img.line(10, 8, 12, 12, STEEL[0])
    img.hline(3, 13, 12, STEEL[0])
    img.rect(9, 4, 12, 6, COPPER[2])              # spring
    img.outline(OUTLINE)
    return img


def comp_stock():
    img = Img(16, 16)
    for row in range(9):
        x0 = 2 + row // 3
        img.rect(x0, 4 + row, 14, 5 + row, WOOD[1])
    img.hline(2, 14, 4, WOOD[3])
    img.hline(3, 14, 12, WOOD[0])
    img.rect(12, 4, 14, 13, LEAD[1])              # butt plate
    speckle(img, 3, 5, 12, 12, WOOD[0], 'stock', 6)
    img.outline(OUTLINE)
    return img


def comp_precision_parts():
    img = Img(16, 16)
    for a in range(0, 360, 45):                   # gear teeth
        x = 8 + int(round(5.2 * math.cos(math.radians(a))))
        y = 8 + int(round(5.2 * math.sin(math.radians(a))))
        img.rect(x - 1, y - 1, x + 1, y + 1, STEEL[2])
    for dy in range(-4, 5):
        for dx in range(-4, 5):
            d = math.hypot(dx, dy)
            if d <= 4:
                img.set(8 + dx, 8 + dy, STEEL[1] if d > 1.6 else GUNMETAL[0])
    img.set(5, 5, STEEL[3])
    img.set(11, 4, GLASS[3])
    img.outline(OUTLINE)
    return img


def comp_lens():
    img = Img(16, 16)
    for dy in range(-5, 6):
        for dx in range(-5, 6):
            d = math.hypot(dx, dy)
            if d <= 5:
                img.set(8 + dx, 8 + dy, GLASS[1] if d > 4 else mix(GLASS[2], GLASS[3], 1 - d / 5))
    img.set(6, 5, hexc('#ffffff'))
    img.set(7, 5, hexc('#dff4ff'))
    img.outline(OUTLINE)
    return img


def comp_laser_module():
    img = Img(16, 16)
    bar(img, 2, 5, 9, 7, BLACK)
    img.rect(10, 6, 14, 10, LASER_RED[1])
    img.rect(11, 7, 14, 9, LASER_RED[3])
    img.rect(3, 6, 6, 8, COPPER[2])
    img.rect(3, 9, 9, 11, GUNMETAL[2])
    img.outline(OUTLINE)
    return img


def comp_circuit_board():
    img = Img(16, 16)
    img.rect(2, 2, 14, 14, CIRCUIT[1])
    img.frame(2, 2, 14, 14, CIRCUIT[0])
    for y in (5, 8, 11):
        img.hline(3, 13, y, CIRCUIT[3])
    for x in (5, 9):
        img.vline(x, 3, 13, CIRCUIT[3])
    img.rect(6, 6, 10, 10, GUNMETAL[1])
    img.rect(7, 7, 9, 9, BRASS[2])
    img.set(4, 4, BRASS[3])
    img.set(12, 12, BRASS[3])
    img.outline(OUTLINE)
    return img


def comp_explosive():
    img = Img(16, 16)
    bar(img, 2, 5, 12, 8, ramp('#54402c', '#8a6b48', '#ab8a5f', '#c8a878'))
    img.rect(2, 7, 14, 9, LASER_RED[1])
    img.rect(4, 3, 7, 6, GUNMETAL[1])             # detonator
    img.vline(5, 1, 4, LASER_RED[2])
    img.set(5, 0, LASER_RED[3])
    img.outline(OUTLINE)
    return img


def comp_rocket_motor():
    img = Img(16, 16)
    bar(img, 4, 2, 8, 9, GUNMETAL)
    for row in range(4):                          # nozzle
        w = 6 + row
        x = 8 - w // 2
        img.rect(x, 11 + row, x + w, 12 + row, STEEL[1])
        img.set(x, 11 + row, STEEL[0])
        img.set(x + w - 1, 11 + row, STEEL[0])
    img.rect(4, 4, 12, 6, WARN[2])
    img.vline(5, 3, 10, GUNMETAL[3])
    img.outline(OUTLINE)
    return img


def comp_warhead_casing():
    img = Img(16, 16)
    for row in range(6):
        t = row / 5.0
        w = int(round(3 + 6 * t))
        x = 8 - w // 2
        img.rect(x, 2 + row, x + w, 3 + row, STEEL[1])
        img.set(x, 2 + row, STEEL[0])
        img.set(x + w - 1, 2 + row, STEEL[0])
    bar(img, 3, 8, 10, 6, STEEL)
    img.rect(3, 10, 13, 11, WARN[2])
    img.rect(5, 13, 11, 15, GUNMETAL[0])
    img.outline(OUTLINE)
    return img


def comp_enriched_uranium():
    img = Img(16, 16)
    bar(img, 3, 4, 10, 10, GUNMETAL)
    img.rect(5, 6, 11, 12, URANIUM[1])
    img.rect(6, 7, 10, 11, URANIUM[3])
    img.rect(3, 4, 13, 5, WARN[2])
    speckle(img, 5, 6, 11, 12, URANIUM[3], 'enriched', 3)
    img.outline(OUTLINE)
    return img


def comp_plutonium_core():
    img = Img(16, 16)
    for dy in range(-6, 7):
        for dx in range(-6, 7):
            d = math.hypot(dx, dy)
            if d <= 6:
                if d < 2.0:
                    c = hexc('#ccfff2')
                elif d < 4.0:
                    c = mix(PLUTONIUM[3], PLUTONIUM[2], (d - 2.0) / 2.0)
                else:
                    c = mix(PLUTONIUM[1], PLUTONIUM[0], (d - 4.0) / 2.0)
                img.set(8 + dx, 8 + dy, c)
    for a in range(0, 360, 90):                   # containment band
        x = 8 + int(round(6.0 * math.cos(math.radians(a))))
        y = 8 + int(round(6.0 * math.sin(math.radians(a))))
        img.set(x, y, LEAD[2])
    img.outline(OUTLINE)
    return img


MATERIAL_DRAWERS = {
    'steel_blend': comp_steel_blend,
    'steel_ingot': lambda: ingot(LEAD),
    'brass_casing': comp_brass_casing,
    'bullet_tip': comp_bullet_tip,
    'propellant': comp_propellant,
    'gun_barrel': comp_gun_barrel,
    'weapon_receiver': comp_receiver,
    'trigger_assembly': comp_trigger,
    'weapon_stock': comp_stock,
    'precision_parts': comp_precision_parts,
    'optical_lens': comp_lens,
    'laser_module': comp_laser_module,
    'circuit_board': comp_circuit_board,
    'explosive_compound': comp_explosive,
    'rocket_motor': comp_rocket_motor,
    'warhead_casing': comp_warhead_casing,
    'raw_uranium': lambda: nugget_pile(URANIUM),
    'uranium_ingot': lambda: ingot(URANIUM, glow=hexc('#b6ff7a')),
    'enriched_uranium': comp_enriched_uranium,
    'plutonium_core': comp_plutonium_core,
}


# =====================================================================================================
# blocks (16x16)
# =====================================================================================================

def stone_base(dark, mid, light, seed):
    img = Img(16, 16)
    img.rect(0, 0, 16, 16, mid)
    speckle(img, 0, 0, 16, 16, light, seed + 'a', 5)
    speckle(img, 0, 0, 16, 16, dark, seed + 'b', 5)
    return img


def ore(base_seed, deepslate=False):
    if deepslate:
        img = stone_base(hexc('#0f0f12'), hexc('#1e1e22'), hexc('#2c2c31'), base_seed)
    else:
        img = stone_base(hexc('#6b6b6b'), hexc('#808080'), hexc('#949494'), base_seed)
    blobs = ((3, 3, 2), (10, 5, 2), (5, 10, 2), (11, 11, 1), (8, 7, 1))
    for (cx, cy, r) in blobs:
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r + 1:
                    d = math.hypot(dx, dy)
                    img.set(cx + dx, cy + dy, URANIUM[1] if d > r - 0.8 else URANIUM[2])
        img.set(cx, cy - 1, URANIUM[3])
    return img


def metal_block(pal, seed, rivets=True):
    img = Img(16, 16)
    img.rect(0, 0, 16, 16, pal[1])
    img.frame(0, 0, 16, 16, pal[0])
    img.hline(1, 15, 1, pal[2])
    img.vline(1, 1, 15, pal[2])
    img.hline(1, 15, 14, pal[0])
    for y in (5, 10):
        img.hline(1, 15, y, pal[0])
    if rivets:
        for (x, y) in ((3, 3), (12, 3), (3, 8), (12, 8), (3, 12), (12, 12)):
            img.set(x, y, pal[3])
            img.set(x + 1, y + 1, pal[0])
    speckle(img, 1, 1, 15, 15, pal[2], seed, 11)
    return img


def raw_block(pal, seed):
    img = Img(16, 16)
    img.rect(0, 0, 16, 16, pal[0])
    for (cx, cy, r) in ((4, 4, 3), (11, 5, 3), (5, 11, 3), (12, 12, 2), (8, 8, 2)):
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r:
                    img.set(cx + dx, cy + dy, pal[1])
        img.set(cx - 1, cy - 1, pal[2])
    speckle(img, 0, 0, 16, 16, pal[2], seed, 9)
    return img


def workbench_top():
    img = Img(16, 16)
    img.rect(0, 0, 16, 16, GUNMETAL[1])
    img.frame(0, 0, 16, 16, GUNMETAL[0])
    img.rect(1, 1, 10, 8, POLYMER[1])             # cutting mat
    img.frame(1, 1, 10, 8, POLYMER[0])
    for y in range(2, 8, 2):
        img.hline(2, 9, y, POLYMER[2])
    img.rect(11, 2, 15, 4, STEEL[2])              # a barrel lying on the bench
    img.rect(11, 5, 14, 6, BRASS[2])
    img.rect(2, 10, 7, 12, STEEL[1])              # vice
    img.rect(8, 10, 14, 14, WOOD[1])
    img.hline(8, 14, 10, WOOD[2])
    return img


def workbench_side():
    img = Img(16, 16)
    img.rect(0, 0, 16, 16, GUNMETAL[1])
    img.rect(0, 0, 16, 3, GUNMETAL[2])
    img.hline(0, 16, 2, GUNMETAL[0])
    img.frame(0, 0, 16, 16, GUNMETAL[0])
    for y in (7, 12):
        img.hline(1, 15, y, GUNMETAL[0])
    for (x, y) in ((2, 5), (13, 5), (2, 10), (13, 10)):
        img.set(x, y, GUNMETAL[3])
    img.rect(3, 4, 13, 6, POLYMER[1])
    img.rect(3, 9, 13, 11, POLYMER[1])
    return img


def workbench_front():
    img = workbench_side()
    img.rect(2, 4, 8, 7, BLACK[0])                # tool drawer
    img.rect(3, 5, 7, 6, STEEL[2])
    img.rect(9, 4, 14, 7, BLACK[0])
    img.rect(10, 5, 13, 6, BRASS[2])
    img.rect(2, 9, 14, 12, POLYMER[0])
    img.rect(3, 10, 6, 11, LASER_RED[2])          # status lights
    img.rect(7, 10, 9, 11, WARN[2])
    img.rect(10, 10, 13, 11, CIRCUIT[3])
    return img


def nuke_side(armed=False):
    img = Img(16, 16)
    img.rect(0, 0, 16, 16, LEAD[1])
    img.frame(0, 0, 16, 16, LEAD[0])
    img.hline(1, 15, 1, LEAD[2])
    # hazard stripes
    for y in (3, 11):
        img.rect(1, y, 15, y + 2, WARN[2])
        for x in range(1, 15):
            if (x + y) % 4 < 2:
                img.vline(x, y, y + 2, BLACK[1])
    img.rect(4, 6, 12, 10, LEAD[0])
    img.rect(5, 7, 11, 9, (URANIUM if not armed else LASER_RED)[2])
    img.rect(6, 7, 10, 8, (URANIUM if not armed else LASER_RED)[3])
    speckle(img, 1, 5, 15, 11, LEAD[2], 'nuke', 13)
    return img


def nuke_top():
    img = Img(16, 16)
    img.rect(0, 0, 16, 16, LEAD[1])
    img.frame(0, 0, 16, 16, LEAD[0])
    for dy in range(-5, 6):
        for dx in range(-5, 6):
            d = math.hypot(dx, dy)
            if d <= 5:
                img.set(8 + dx, 8 + dy, LEAD[0] if d > 4 else WARN[2])
    # trefoil
    for a in (90, 210, 330):
        for r in range(1, 5):
            x = 8 + int(round(r * math.cos(math.radians(a))))
            y = 8 + int(round(r * math.sin(math.radians(a))))
            img.rect(x - 1, y - 1, x + 1, y + 1, BLACK[0])
    img.rect(7, 7, 9, 9, BLACK[0])
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.set(x, y, LEAD[3])
    return img


BLOCK_DRAWERS = {
    'uranium_ore': lambda: ore('uranium'),
    'deepslate_uranium_ore': lambda: ore('deep_uranium', deepslate=True),
    'raw_uranium_block': lambda: raw_block(URANIUM, 'rawu'),
    'uranium_block': lambda: metal_block(URANIUM, 'ublock'),
    'steel_block': lambda: metal_block(LEAD, 'sblock'),
    'weapon_workbench_top': workbench_top,
    'weapon_workbench_side': workbench_side,
    'weapon_workbench_front': workbench_front,
    'tactical_nuke_top': nuke_top,
    'tactical_nuke_side': lambda: nuke_side(False),
    'tactical_nuke_side_armed': lambda: nuke_side(True),
}


# =====================================================================================================

def brand(items):
    """The square icon and wide banner the mod list shows; both live at the root of the jar."""
    root = os.path.join(RES)

    icon = Img(128, 128)
    for y in range(128):
        icon.hline(0, 128, y, mix(hexc('#20261a'), hexc('#0b0e08'), y / 127.0))
    icon.paste(items['ak47'].scaled(4), 0, 0)
    icon.frame(0, 0, 128, 128, hexc('#566645'))
    icon.frame(1, 1, 127, 127, hexc('#0b0e08'))
    icon.save(os.path.join(root, 'arsenal_icon.png'))
    written.append('arsenal_icon.png')

    banner = Img(384, 96)
    for y in range(96):
        banner.hline(0, 384, y, mix(hexc('#2a3120'), hexc('#090b07'), y / 95.0))
    for i, name in enumerate(('barrett_m82', 'ak47', 'spas12', 'railgun', 'rpg7', 'deagle')):
        banner.paste(items[name].scaled(2), 8 + i * 62, 16)
    banner.rect(0, 0, 384, 3, hexc('#8d7142'))
    banner.rect(0, 93, 384, 96, hexc('#8d7142'))
    banner.save(os.path.join(root, 'arsenal_banner.png'))
    written.append('arsenal_banner.png')


def main():
    items = {}
    for name, draw in GUN_DRAWERS.items():
        items[name] = draw()
    for name, draw in AMMO_DRAWERS.items():
        items[name] = draw()
    for name, draw in ATTACHMENT_DRAWERS.items():
        items[name] = draw()
    for name, draw in ORDNANCE_DRAWERS.items():
        items[name] = draw()
    for name, draw in MATERIAL_DRAWERS.items():
        items[name] = draw()
    for name, img in items.items():
        if name in gun_models.MODELS:
            # 3D guns: the texture is the model's painted atlas (the flat sprite is still used for the mod icon)
            gun_models.write_texture(name, os.path.join(TEX, 'item', name + '.png'))
            written.append(os.path.join('item', name + '.png'))
        else:
            save(img, 'item', name + '.png')
    for name in JET_ITEMS:
        gun_models.write_texture(name, os.path.join(TEX, 'item', name + '.png'))
        written.append(os.path.join('item', name + '.png'))
    # the F-14 itself (tools/vehicle_models.py): entity atlas, mesh and the item icon rendered from the mesh
    stats = vehicle_models.write_assets(os.path.join(TEX, 'entity', 'f14.png'),
                                        os.path.join(RES, 'assets', 'arsenal', 'vehicle', 'f14.mesh'),
                                        os.path.join(TEX, 'item', 'f14_tomcat.png'))
    written.extend([os.path.join('entity', 'f14.png'), os.path.join('item', 'f14_tomcat.png')])
    print('F-14: %(parts)d parts, %(quads)d quads, atlas %(atlas)s, mesh %(mesh_bytes)d bytes' % stats)

    blocks = {name: draw() for name, draw in BLOCK_DRAWERS.items()}
    for name, img in blocks.items():
        save(img, 'block', name + '.png')

    brand(items)
    print(f'{len(written)} textures written')

    if 'preview' in sys.argv:
        out = os.path.normpath(os.path.join(HERE, '..', 'build'))
        os.makedirs(out, exist_ok=True)
        order = (list(GUN_DRAWERS) + list(AMMO_DRAWERS) + list(ATTACHMENT_DRAWERS)
                 + list(ORDNANCE_DRAWERS) + list(MATERIAL_DRAWERS))
        sheet = preview_sheet([items[n] for n in order], scale=5, cols=9)
        sheet.save(os.path.join(out, 'texture_preview.png'))
        sheet2 = preview_sheet([blocks[n] for n in BLOCK_DRAWERS], scale=6, cols=6)
        sheet2.save(os.path.join(out, 'block_preview.png'))
        print('previews written to build/')


if __name__ == '__main__':
    main()

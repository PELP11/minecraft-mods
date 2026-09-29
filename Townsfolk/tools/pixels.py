"""Tiny dependency-free pixel-art toolkit used by the Juicer asset generators.

Only the Python standard library is used (zlib + struct for PNG encoding), so the
generators run on any Python 3.8+ install without Pillow.
"""
import math
import random
import struct
import zlib


def hexc(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(c1, c2, t):
    """Linear blend of two RGBA colours (t=0 -> c1, t=1 -> c2)."""
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def shade(c, f):
    """Multiply the RGB channels of c by f (f<1 darker, f>1 lighter)."""
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))),
            max(0, min(255, int(c[2] * f))), c[3])


def lighten(c, t):
    return mix(c, (255, 255, 255, c[3]), t)


def darken(c, t):
    return mix(c, (0, 0, 0, c[3]), t)


CLEAR = (0, 0, 0, 0)


class Img:
    def __init__(self, w, h, fill=CLEAR):
        self.w, self.h = w, h
        self.px = [[fill] * w for _ in range(h)]

    # -- basic access -------------------------------------------------------
    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def get(self, x, y):
        return self.px[y][x]

    def set(self, x, y, c):
        if c is not None and self.inside(x, y):
            self.px[y][x] = c

    def blend(self, x, y, c):
        """Alpha-composite c over the existing pixel."""
        if c is None or not self.inside(x, y):
            return
        a = c[3] / 255.0
        if a >= 1.0:
            self.px[y][x] = c
            return
        if a <= 0:
            return
        d = self.px[y][x]
        da = d[3] / 255.0
        oa = a + da * (1 - a)
        if oa <= 0:
            self.px[y][x] = CLEAR
            return
        rgb = [int(round((c[i] * a + d[i] * da * (1 - a)) / oa)) for i in range(3)]
        self.px[y][x] = (rgb[0], rgb[1], rgb[2], int(round(oa * 255)))

    def rect(self, x0, y0, x1, y1, c):
        """Fill [x0,x1) x [y0,y1)."""
        for y in range(max(0, y0), min(self.h, y1)):
            for x in range(max(0, x0), min(self.w, x1)):
                self.px[y][x] = c

    def frame(self, x0, y0, x1, y1, c):
        for x in range(x0, x1):
            self.set(x, y0, c)
            self.set(x, y1 - 1, c)
        for y in range(y0, y1):
            self.set(x0, y, c)
            self.set(x1 - 1, y, c)

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1):
            self.set(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1):
            self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        """Bresenham line (inclusive)."""
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def paste(self, other, ox, oy):
        for y in range(other.h):
            for x in range(other.w):
                c = other.px[y][x]
                if c[3]:
                    self.blend(ox + x, oy + y, c)

    def copy(self):
        n = Img(self.w, self.h)
        n.px = [row[:] for row in self.px]
        return n

    def scaled(self, k):
        n = Img(self.w * k, self.h * k)
        for y in range(self.h):
            for x in range(self.w):
                c = self.px[y][x]
                for yy in range(k):
                    row = n.px[y * k + yy]
                    for xx in range(k):
                        row[x * k + xx] = c
        return n

    def rotated90(self):
        """Clockwise rotation by 90 degrees."""
        n = Img(self.h, self.w)
        for y in range(self.h):
            for x in range(self.w):
                n.px[x][self.h - 1 - y] = self.px[y][x]
        return n

    def flipped_x(self):
        n = Img(self.w, self.h)
        for y in range(self.h):
            n.px[y] = self.px[y][::-1]
        return n

    def outline(self, c, only_if_clear=True):
        """Draws a 1px outline around all opaque pixels (4-neighbourhood)."""
        src = [row[:] for row in self.px]
        for y in range(self.h):
            for x in range(self.w):
                if src[y][x][3]:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < self.w and 0 <= ny < self.h and src[ny][nx][3]:
                        self.px[y][x] = c
                        break

    # -- output ---------------------------------------------------------------
    def save(self, path):
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for c in row:
                raw.extend(bytes((c[0], c[1], c[2], c[3])))

        def chunk(tag, data):
            body = tag + data
            return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xffffffff)

        png = b'\x89PNG\r\n\x1a\n'
        png += chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
        png += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
        png += chunk(b'IEND', b'')
        with open(path, 'wb') as f:
            f.write(png)


def from_ascii(rows, palette):
    """Builds an image from equal-length strings; '.' and ' ' are transparent."""
    h, w = len(rows), max(len(r) for r in rows)
    img = Img(w, h)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in '. ':
                continue
            img.set(x, y, palette[ch])
    return img


def vstack(frames):
    out = Img(frames[0].w, sum(f.h for f in frames))
    y = 0
    for f in frames:
        out.paste(f, 0, y)
        y += f.h
    return out


# -- procedural helpers -------------------------------------------------------

class WrapNoise:
    """Tileable value noise: random lattice values, bilinear/smoothstep interpolation."""

    def __init__(self, seed, period=16, cell=4):
        self.cell = cell
        self.n = period // cell
        rnd = random.Random(seed)
        self.grid = [[rnd.random() for _ in range(self.n)] for _ in range(self.n)]

    def at(self, x, y):
        gx, gy = x / self.cell, y / self.cell
        x0, y0 = int(math.floor(gx)), int(math.floor(gy))
        tx, ty = gx - x0, gy - y0
        tx = tx * tx * (3 - 2 * tx)
        ty = ty * ty * (3 - 2 * ty)
        n = self.n
        a = self.grid[y0 % n][x0 % n]
        b = self.grid[y0 % n][(x0 + 1) % n]
        c = self.grid[(y0 + 1) % n][x0 % n]
        d = self.grid[(y0 + 1) % n][(x0 + 1) % n]
        top = a + (b - a) * tx
        bot = c + (d - c) * tx
        return top + (bot - top) * ty


def shaded_ellipse(img, cx, cy, rx, ry, ramp, rot_deg=0.0, light=(-0.55, -0.7, 0.6),
                   outline=None, spec=None, spec_cut=0.9):
    """Draws a lit ellipsoid. `ramp` is a list of colours ordered dark -> light.

    Returns the set of pixels that were filled so callers can add details.
    """
    lx, ly, lz = light
    ll = math.sqrt(lx * lx + ly * ly + lz * lz)
    lx, ly, lz = lx / ll, ly / ll, lz / ll
    rot = math.radians(rot_deg)
    cr, sr = math.cos(rot), math.sin(rot)
    filled = set()
    for y in range(img.h):
        for x in range(img.w):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            u = (px * cr + py * sr) / rx
            v = (-px * sr + py * cr) / ry
            d = u * u + v * v
            if d > 1.0:
                continue
            nz = math.sqrt(max(0.0, 1.0 - d))
            # rotate the normal back into image space
            nx = u * cr - v * sr
            ny = u * sr + v * cr
            inten = nx * lx + ny * ly + nz * lz
            t = max(0.0, min(0.999, (inten + 0.35) / 1.35))
            col = ramp[int(t * len(ramp))]
            if spec is not None and inten > spec_cut:
                col = spec
            img.set(x, y, col)
            filled.add((x, y))
    if outline is not None:
        for (x, y) in list(filled):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in filled:
                    img.set(x, y, outline)
                    break
    return filled


def point_in_poly(x, y, poly):
    inside = False
    n = len(poly)
    j = n - 1
    for i in range(n):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi):
            inside = not inside
        j = i
    return inside


def star_poly(cx, cy, r_out, r_in, points=5, rot_deg=-90.0):
    pts = []
    for i in range(points * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(rot_deg + i * 180.0 / points)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def preview_sheet(images, scale=8, cols=8, pad=4, bg1=(200, 200, 200, 255), bg2=(170, 170, 170, 255)):
    """Composes several images on a checkerboard for quick visual review."""
    cell_w = max(i.w for i in images) * scale + pad * 2
    cell_h = max(i.h for i in images) * scale + pad * 2
    rows = (len(images) + cols - 1) // cols
    sheet = Img(cell_w * cols, cell_h * rows)
    for y in range(sheet.h):
        for x in range(sheet.w):
            sheet.px[y][x] = bg1 if ((x // 8) + (y // 8)) % 2 == 0 else bg2
    for idx, im in enumerate(images):
        ox = (idx % cols) * cell_w + pad
        oy = (idx // cols) * cell_h + pad
        sheet.paste(im.scaled(scale), ox, oy)
    return sheet


def load_png(path):
    """Decode a PNG file into an Img."""
    with open(path, 'rb') as f:
        return decode_png(f.read(), path)


def decode_png(data, path='<bytes>'):
    """Decode PNG bytes (8-bit gray/RGB/RGBA/gray-alpha, or 1/2/4/8-bit palette with tRNS) into an Img."""
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    pos, idat, palette, trns = 8, b'', None, None
    while pos < len(data):
        length, ctype = struct.unpack('>I4s', data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b'IHDR':
            w, h, depth, color, _, _, interlace = struct.unpack('>IIBBBBB', chunk)
            assert interlace == 0, 'interlaced PNG not supported: ' + path
        elif ctype == b'PLTE':
            palette = [tuple(chunk[i:i + 3]) for i in range(0, len(chunk), 3)]
        elif ctype == b'tRNS':
            trns = chunk
        elif ctype == b'IDAT':
            idat += chunk
        elif ctype == b'IEND':
            break
    raw = zlib.decompress(idat)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    bits_pp = channels * depth
    stride = (w * bits_pp + 7) // 8
    bpp = max(1, bits_pp // 8)
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(h):
        ftype = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if ftype == 1:
                line[x] = (line[x] + a) & 255
            elif ftype == 2:
                line[x] = (line[x] + b) & 255
            elif ftype == 3:
                line[x] = (line[x] + ((a + b) >> 1)) & 255
            elif ftype == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else (b if pb <= pc else c))) & 255
        rows.append(line)
        prev = line
    img = Img(w, h)
    for y, line in enumerate(rows):
        for x in range(w):
            if color == 3:
                if depth == 8:
                    idx = line[x]
                else:
                    per = 8 // depth
                    idx = (line[x // per] >> (8 - depth * (x % per + 1))) & ((1 << depth) - 1)
                r, g, b = palette[idx]
                a = trns[idx] if trns is not None and idx < len(trns) else 255
            elif color == 6:
                r, g, b, a = line[x * 4:x * 4 + 4]
            elif color == 2:
                r, g, b = line[x * 3:x * 3 + 3]
                a = 255
            elif color == 4:
                r = g = b = line[x * 2]
                a = line[x * 2 + 1]
            else:
                r = g = b = line[x]
                a = 255
            img.px[y][x] = (r, g, b, a)
    return img

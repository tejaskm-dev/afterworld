"""Item art: a small painter for modern 16x16 pixel-art sprites.

A sprite is built from shapes (polygons, ellipses, thick lines, rects, single pixels), each with a
material. A material is a five-tone ramp made from one base colour with hue-shifting: shadows lean
cool (toward blue-violet), highlights lean warm (toward yellow), the way painters light things.
Shading is automatic: the light comes from the top-left, so a pixel whose neighbour up or left is
outside its shape is lit, one whose neighbour down or right is outside is shaded, and materials
add their own detail (grain, facets, glints). Finally every shape gets a one-pixel outline in its
material's deepest tone (not black), and selected highlight pixels are added.

    s = Sprite()
    s.line((3, 12), (12, 3), 2, 'steel')        # a blade
    s.rect(2, 12, 4, 14, 'leather')
    s.dot(11, 4, 'shine')
    img = s.render()
"""
import colorsys, math
from PIL import Image


def hexrgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4))


def to8(c, a=255):
    return (int(round(max(0, min(1, c[0])) * 255)), int(round(max(0, min(1, c[1])) * 255)), int(round(max(0, min(1, c[2])) * 255)), a)


def shift(rgb, dl, dh, ds=0.0):
    """Lightness, hue (in turns) and saturation shift in HLS space."""
    h, l, s = colorsys.rgb_to_hls(*rgb)
    return colorsys.hls_to_rgb((h + dh) % 1.0, max(0, min(1, l + dl)), max(0, min(1, s + ds)))


def ramp(base, warm=0.035, contrast=1.0, sat=0.0):
    """Five tones from deep shadow to highlight, hue-shifted cool in the dark and warm in the light."""
    b = hexrgb(base) if isinstance(base, str) else base
    h, l, s = colorsys.rgb_to_hls(*b)
    # Hue moves toward blue (≈0.62) in shadow, toward yellow (≈0.15) in light.
    def toward(target, amt):
        d = ((target - h + 0.5) % 1.0) - 0.5
        return h + d * amt
    out = []
    for dl, hue_amt, ds in ((-0.30, 0.20, -0.05), (-0.15, 0.10, 0.0), (0.0, 0.0, 0.0), (0.12, -0.08, 0.02), (0.26, -0.16, -0.08)):
        hh = toward(0.66, hue_amt) if dl < 0 else toward(0.14, -hue_amt) if dl > 0 else h
        rgb = colorsys.hls_to_rgb(hh % 1.0, max(0.02, min(0.97, l + dl * contrast)), max(0, min(1, s + ds + sat)))
        out.append(rgb)
    return out


class Material:
    def __init__(self, base, kind='matte', contrast=1.0, outline=None, glow=False, sat=0.0):
        self.tones = ramp(base, contrast=contrast, sat=sat)
        self.kind = kind          # matte, metal, gem, wood, cloth, glass, glow, organic, stone, paper
        self.outline = hexrgb(outline) if outline else shift(self.tones[0], -0.12, 0.02, 0.05)
        self.glow = glow


PALETTE = {}


def mat(name, base, kind='matte', **kw):
    PALETTE[name] = Material(base, kind, **kw)
    return name


# A common palette; sprites can add their own.
for name, base, kind in (
        ('steel', '#a9b4c2', 'metal'), ('darksteel', '#5d6675', 'metal'), ('iron', '#8f9096', 'metal'),
        ('gold', '#e8b53d', 'metal'), ('brass', '#c9933a', 'metal'), ('copper', '#c9713e', 'metal'), ('silver', '#cfd6e0', 'metal'),
        ('moonsilver', '#b9c9f0', 'metal'), ('bronze', '#a8743a', 'metal'),
        ('wood', '#8a5a34', 'wood'), ('darkwood', '#5a3a26', 'wood'), ('palewood', '#c4a070', 'wood'),
        ('leather', '#7a4a2e', 'cloth'), ('darkleather', '#4a3024', 'cloth'), ('cloth_red', '#b03a3a', 'cloth'),
        ('cloth_blue', '#3a5ab0', 'cloth'), ('cloth_teal', '#2f8f88', 'cloth'), ('cloth_purple', '#6a3aa0', 'cloth'),
        ('cloth_white', '#e8e0d0', 'cloth'), ('cloth_green', '#4a8a3a', 'cloth'), ('rope', '#b89a68', 'cloth'),
        ('stone', '#8a8a8e', 'stone'), ('darkstone', '#4a4a52', 'stone'), ('sandstone', '#d8c08a', 'stone'),
        ('bone', '#e8dcc0', 'matte'), ('paper', '#efe4c8', 'paper'), ('parchment', '#d9c08a', 'paper'),
        ('glass', '#bfe8f0', 'glass'), ('ruby', '#e0304a', 'gem'), ('sapphire', '#3a6ae8', 'gem'), ('emerald', '#30c060', 'gem'),
        ('amethyst', '#a060e0', 'gem'), ('topaz', '#f0b030', 'gem'), ('aqua', '#40d8d0', 'gem'), ('pearl', '#f0e8f8', 'gem'),
        ('amber', '#e09030', 'gem'), ('crystal', '#a8e8ff', 'gem'),
        ('ember', '#ff7a2a', 'glow'), ('frost', '#8ae0ff', 'glow'), ('arcane', '#c08aff', 'glow'), ('spirit', '#7affd8', 'glow'),
        ('blood', '#d02040', 'glow'), ('sun', '#ffd060', 'glow'), ('star', '#fff0c0', 'glow'),
        ('flesh', '#c07a60', 'organic'), ('moss', '#5a8a3a', 'organic'), ('fur', '#d8cfc0', 'organic'), ('darkfur', '#6a5a4a', 'organic'),
        ('shine', '#ffffff', 'glow')):
    PALETTE[name] = Material(base, kind, glow=(kind == 'glow'))


class Sprite:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.cells = [[None] * w for _ in range(h)]       # material name per pixel
        self.layer = [[0] * w for _ in range(h)]          # which shape (for outlining between shapes)
        self.forced = {}                                 # (x, y) -> (material, tone index) overrides
        self.n = 0
        self.no_outline = set()
        self.selout = True

    # ---------------------------------------------------------------- shapes
    def _put(self, x, y, m):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.cells[y][x] = m
            self.layer[y][x] = self.n

    def poly(self, pts, m):
        self.n += 1
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        for y in range(max(0, int(min(ys)) - 1), min(self.h, int(max(ys)) + 2)):
            for x in range(max(0, int(min(xs)) - 1), min(self.w, int(max(xs)) + 2)):
                if _inside(pts, x + 0.5, y + 0.5):
                    self._put(x, y, m)
        return self

    def ellipse(self, cx, cy, rx, ry, m):
        self.n += 1
        for y in range(self.h):
            for x in range(self.w):
                if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1.0:
                    self._put(x, y, m)
        return self

    def rect(self, x0, y0, x1, y1, m):
        self.n += 1
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self._put(x, y, m)
        return self

    def line(self, a, b, width, m):
        """A thick line from a to b (pixel centres), as a capsule."""
        self.n += 1
        (x0, y0), (x1, y1) = a, b
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy or 1
        r = width / 2.0
        for y in range(self.h):
            for x in range(self.w):
                px, py = x + 0.5, y + 0.5
                t = max(0, min(1, ((px - (x0 + 0.5)) * dx + (py - (y0 + 0.5)) * dy) / L2))
                qx, qy = x0 + 0.5 + t * dx, y0 + 0.5 + t * dy
                if (px - qx) ** 2 + (py - qy) ** 2 <= r * r + 0.15:
                    self._put(x, y, m)
        return self

    def dots(self, pts, m, tone=None):
        self.n += 1
        for (x, y) in pts:
            self._put(x, y, m)
            if tone is not None:
                self.forced[(x, y)] = (m, tone)
        return self

    def dot(self, x, y, m, tone=None):
        return self.dots([(x, y)], m, tone)

    def tone(self, pts, tone):
        """Forces a tone (0 deepest .. 4 brightest) on existing pixels."""
        for (x, y) in pts:
            if 0 <= x < self.w and 0 <= y < self.h and self.cells[y][x]:
                self.forced[(x, y)] = (self.cells[y][x], tone)
        return self

    def erase(self, pts):
        for (x, y) in pts:
            if 0 <= x < self.w and 0 <= y < self.h:
                self.cells[y][x] = None
        return self

    def mirror_x(self):
        self.cells = [row[::-1] for row in self.cells]
        self.layer = [row[::-1] for row in self.layer]
        self.forced = {(self.w - 1 - x, y): v for (x, y), v in self.forced.items()}
        return self

    # ---------------------------------------------------------------- render
    def render(self, outline=True, glow_halo=False):
        img = Image.new('RGBA', (self.w, self.h), (0, 0, 0, 0))
        px = img.load()
        cells = self.cells

        def at(x, y):
            return cells[y][x] if 0 <= x < self.w and 0 <= y < self.h else None

        for y in range(self.h):
            for x in range(self.w):
                m = cells[y][x]
                if not m:
                    continue
                M = PALETTE[m]
                if (x, y) in self.forced and self.forced[(x, y)][0] == m:
                    px[x, y] = to8(M.tones[self.forced[(x, y)][1]])
                    continue
                t = 2
                up, left = at(x, y - 1) != m, at(x - 1, y) != m
                down, right = at(x, y + 1) != m, at(x + 1, y) != m
                if up or left:
                    t += 1
                if down or right:
                    t -= 1
                if up and left:
                    t += 1
                if down and right:
                    t -= 1
                k = M.kind
                if k == 'metal':
                    # A bright band along the top-left edge and a glint.
                    if (x + y) % 7 == 0 and not (down or right):
                        t += 1
                elif k == 'wood':
                    if (y * 3 + x) % 4 == 0:
                        t -= 1
                elif k == 'cloth':
                    if (x + y) % 2 == 0 and t == 2:
                        t = 2
                    elif t == 2 and (x * 2 + y) % 5 == 0:
                        t = 1
                elif k == 'gem':
                    # Facets: a diagonal split, light above it.
                    t = 3 if (x - y) % 4 in (0, 1) and not (down or right) else t
                elif k == 'stone':
                    if ((x * 7 + y * 13) % 11) == 0:
                        t -= 1
                elif k == 'glass':
                    t = max(2, t)
                elif k == 'glow':
                    t = max(2, t + 1)
                t = max(0, min(4, t))
                px[x, y] = to8(M.tones[t])
        if outline:
            out = img.copy()
            op = out.load()
            for y in range(self.h):
                for x in range(self.w):
                    if cells[y][x]:
                        continue
                    # Outline pixels take the outline colour of the material they touch (orthogonally).
                    # Selective outline: on the lit side (the shape is below/right of this pixel) the
                    # line is a touch lighter, which reads as a rim of light.
                    touch = None
                    lit = False
                    for dx, dy in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                        m = at(x + dx, y + dy)
                        if m and m not in self.no_outline:
                            touch = m
                            lit = dx > 0 or dy > 0
                            break
                    if touch:
                        M = PALETTE[touch]
                        if M.glow:
                            op[x, y] = to8(shift(M.tones[0], -0.05, 0, 0))
                        elif lit and self.selout:
                            op[x, y] = to8(shift(M.outline, 0.07, 0, 0))
                        else:
                            op[x, y] = to8(M.outline)
            img = out
        if glow_halo:
            halo = img.copy()
            hp = halo.load()
            for y in range(self.h):
                for x in range(self.w):
                    if img.getpixel((x, y))[3] == 0:
                        for dx, dy in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                            xx, yy = x + dx, y + dy
                            if 0 <= xx < self.w and 0 <= yy < self.h and cells[yy][xx] and PALETTE[cells[yy][xx]].glow:
                                c = to8(PALETTE[cells[yy][xx]].tones[3], 90)
                                hp[x, y] = c
                                break
            img = halo
        return img


def _inside(pts, x, y):
    inside = False
    n = len(pts)
    j = n - 1
    for i in range(n):
        xi, yi = pts[i]
        xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / ((yj - yi) or 1e-9) + xi:
            inside = not inside
        j = i
    return inside


def sheet(images, scale=6, cols=12, bg=(30, 30, 36, 255), labels=None):
    """A preview sheet of sprites, scaled up, on a dark background."""
    n = len(images)
    rows = (n + cols - 1) // cols
    cell = 16 * scale + 8
    out = Image.new('RGBA', (cols * cell, rows * cell), bg)
    for i, im in enumerate(images):
        big = im.resize((16 * scale, 16 * scale), Image.NEAREST)
        out.alpha_composite(big, ((i % cols) * cell + 4, (i // cols) * cell + 4))
    return out

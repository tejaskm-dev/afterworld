"""A small voxel toolkit for Afterworld's wonders.

A Grid holds palette indices (0 = leave the world as it is). Builders fill it with boxes, cylinders,
cones, lines and signed-distance sculpts; export() writes the compact, chunk-tiled .aww file the
mod places from, and preview() renders an isometric picture for checking shapes without the game.
"""
import gzip, math, struct
import numpy as np
from PIL import Image

SKIP = 0


def ns(state):
    return state if ':' in state.split('[')[0] else 'minecraft:' + state


class Grid:
    def __init__(self, sx, sy, sz):
        assert sx % 16 == 0 and sz % 16 == 0, 'wonders are tiled by chunk'
        self.sx, self.sy, self.sz = sx, sy, sz
        self.a = np.zeros((sx, sy, sz), dtype=np.uint16)
        self.palette = ['<skip>']
        self.index = {}
        self.loot = []

    # ---------------------------------------------------------------- palette

    def pid(self, state):
        state = ns(state)
        i = self.index.get(state)
        if i is None:
            i = len(self.palette)
            self.palette.append(state)
            self.index[state] = i
        return i

    def name(self, i):
        return self.palette[i]

    def inside(self, x, y, z):
        return 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz

    # ---------------------------------------------------------------- drawing

    def set(self, x, y, z, state):
        x, y, z = int(round(x)), int(round(y)), int(round(z))
        if self.inside(x, y, z):
            self.a[x, y, z] = self.pid(state)

    def get(self, x, y, z):
        if not self.inside(x, y, z):
            return None
        i = self.a[x, y, z]
        return None if i == SKIP else self.palette[i]

    def is_solid(self, x, y, z):
        s = self.get(x, y, z)
        return s is not None and not s.startswith('minecraft:air') and not s.startswith('minecraft:light') and not s.startswith('minecraft:cave_air')

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        xa, xb = sorted((int(x0), int(x1)))
        ya, yb = sorted((int(y0), int(y1)))
        za, zb = sorted((int(z0), int(z1)))
        xa, ya, za = max(xa, 0), max(ya, 0), max(za, 0)
        xb, yb, zb = min(xb, self.sx - 1), min(yb, self.sy - 1), min(zb, self.sz - 1)
        if xa > xb or ya > yb or za > zb:
            return
        self.a[xa:xb + 1, ya:yb + 1, za:zb + 1] = self.pid(state)

    def shell(self, x0, y0, z0, x1, y1, z1, state, inner='air'):
        """A hollow box: walls of `state`, the inside cleared to `inner` (None keeps it)."""
        self.fill(x0, y0, z0, x1, y1, z1, state)
        if inner is not None and x1 - x0 > 1 and z1 - z0 > 1 and y1 - y0 > 1:
            self.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, inner)

    def mask(self, m, state):
        self.a[m] = self.pid(state)

    def mask_where_empty(self, m, state):
        self.a[m & (self.a == SKIP)] = self.pid(state)

    def cyl(self, cx, cz, y0, y1, r, state, hollow=False, inner=None):
        """A vertical cylinder (or tube, with `hollow`)."""
        X, Z = self.xz()
        d = np.hypot(X - cx, Z - cz)
        ring = d <= r + 0.35
        if hollow:
            ring &= d > r - 0.85
        for y in range(max(int(y0), 0), min(int(y1), self.sy - 1) + 1):
            self.a[:, y, :][ring] = self.pid(state)
            if inner is not None and hollow:
                self.a[:, y, :][d <= r - 0.85] = self.pid(inner)

    def cone(self, cx, cz, y0, height, r0, state, r1=0.0, hollow=False):
        X, Z = self.xz()
        d = np.hypot(X - cx, Z - cz)
        for i in range(int(height)):
            y = int(y0) + i
            if not 0 <= y < self.sy:
                continue
            r = r0 + (r1 - r0) * (i / max(1, height - 1))
            m = d <= r + 0.35
            if hollow and r > 1.2:
                m &= d > r - 1.0
            self.a[:, y, :][m] = self.pid(state)

    def disc(self, cx, cz, y, r, state, ring_only=False):
        X, Z = self.xz()
        d = np.hypot(X - cx, Z - cz)
        m = d <= r + 0.35
        if ring_only:
            m &= d > r - 0.85
        if 0 <= y < self.sy:
            self.a[:, int(y), :][m] = self.pid(state)

    def line(self, p0, p1, state, r=0.0):
        """A straight line of blocks (thickened to radius r)."""
        p0, p1 = np.array(p0, float), np.array(p1, float)
        n = int(np.max(np.abs(p1 - p0))) * 2 + 1
        for t in np.linspace(0, 1, n):
            p = p0 + (p1 - p0) * t
            if r <= 0:
                self.set(p[0], p[1], p[2], state)
            else:
                ri = int(math.ceil(r))
                for dx in range(-ri, ri + 1):
                    for dy in range(-ri, ri + 1):
                        for dz in range(-ri, ri + 1):
                            if dx * dx + dy * dy + dz * dz <= r * r + 0.3:
                                self.set(p[0] + dx, p[1] + dy, p[2] + dz, state)

    def chest(self, x, y, z, facing, loot):
        self.set(x, y, z, f'chest[facing={facing}]')
        self.loot.append((int(x), int(y), int(z), loot))

    def mark(self, x, y, z, name):
        """A named spot the game acts on when the wonder is placed: 'npc:<id>:<facing>' stands a
        keeper there, 'waystone' records the waystone. Stored with the loot list, prefixed '@'."""
        self.loot.append((int(x), int(y), int(z), '@' + name))

    def xz(self):
        if not hasattr(self, '_xz'):
            self._xz = np.meshgrid(np.arange(self.sx) + 0.0, np.arange(self.sz) + 0.0, indexing='ij')
        return self._xz

    # ---------------------------------------------------------------- export

    def export(self, path):
        """Writes the .aww format: header, palette, then each 16x16 column tile run-length encoded."""
        out = bytearray()
        out += b'AWW1'
        out += struct.pack('>iii', self.sx, self.sy, self.sz)
        out += struct.pack('>i', len(self.palette))
        for s in self.palette:
            b = s.encode('utf-8')
            out += struct.pack('>H', len(b)) + b
        tx, tz = self.sx // 16, self.sz // 16
        out += struct.pack('>ii', tx, tz)
        for j in range(tz):
            for i in range(tx):
                tile = self.a[i * 16:(i + 1) * 16, :, j * 16:(j + 1) * 16]
                if not tile.any():
                    out += struct.pack('>i', 0)
                    continue
                # Order: y, then z, then x, so a layer reads like a map.
                flat = np.transpose(tile, (1, 2, 0)).reshape(-1)
                data = rle(flat)
                out += struct.pack('>i', len(data)) + data
        out += struct.pack('>i', len(self.loot))
        for x, y, z, table in self.loot:
            b = table.encode('utf-8')
            out += struct.pack('>hhhH', x, y, z, len(b)) + b
        with gzip.open(path, 'wb', compresslevel=9) as f:
            f.write(bytes(out))
        return len(out)

    def count(self):
        air = {self.index.get('minecraft:air', -1)}
        return int(np.count_nonzero((self.a != SKIP) & ~np.isin(self.a, list(air))))

    # ---------------------------------------------------------------- preview

    def preview(self, path, scale=3, yaw=0):
        """Isometric render seen from the south-east (yaw rotates the view by 90-degree steps)."""
        a = np.rot90(self.a, k=yaw, axes=(0, 2)) if yaw else self.a
        sx, sy, sz = a.shape
        solid = np.zeros(len(self.palette), bool)
        colours = np.zeros((len(self.palette), 3), float)
        for i, s in enumerate(self.palette):
            c = colour_of(s)
            solid[i] = c is not None
            colours[i] = c if c is not None else (0, 0, 0)
        occ = solid[a]
        pad = np.pad(occ, 1)
        faces = []
        # (exposed test, face offsets) for top, east (+x), south (+z).
        top = occ & ~pad[1:-1, 2:, 1:-1]
        east = occ & ~pad[2:, 1:-1, 1:-1]
        south = occ & ~pad[1:-1, 1:-1, 2:]
        s = scale
        W = (sx + sz) * 2 * s + 8
        H = (sx + sz) * s + sy * 2 * s + 8
        ox, oy = sz * 2 * s + 4, sy * 2 * s + 4
        img = np.zeros((H, W, 3), np.uint8)
        img[:] = (14, 16, 20)
        pix_all, dep_all, col_all = [], [], []
        for mask, shade, kind in ((top, 1.0, 0), (east, 0.78, 1), (south, 0.62, 2)):
            xs, ys, zs = np.nonzero(mask)
            if len(xs) == 0:
                continue
            base_x = (xs - zs) * 2 * s + ox
            base_y = (xs + zs) * s - ys * 2 * s + oy
            offs = face_offsets(kind, s)
            ids = a[xs, ys, zs]
            col = colours[ids] * shade
            # A little grain so flat walls read as stone.
            grain = ((xs * 73856093 ^ ys * 19349663 ^ zs * 83492791) % 17) / 17.0 * 0.12 + 0.94
            col = col * grain[:, None]
            depth = xs + ys + zs
            for dx, dy in offs:
                pix_all.append((base_y + dy) * W + (base_x + dx))
                dep_all.append(depth * 4 + kind)
                col_all.append(col)
        if pix_all:
            pix = np.concatenate(pix_all)
            dep = np.concatenate(dep_all)
            col = np.concatenate(col_all)
            ok = (pix >= 0) & (pix < W * H)
            pix, dep, col = pix[ok], dep[ok], col[ok]
            order = np.lexsort((dep, pix))
            ps = pix[order]
            last = np.r_[ps[1:] != ps[:-1], True]
            sel = order[last]
            flat = img.reshape(-1, 3)
            flat[pix[sel]] = np.clip(col[sel], 0, 255).astype(np.uint8)
        Image.fromarray(img).save(path)


def elevation(grid, path, view='front', scale=4):
    """Flat orthographic view with depth shading: front (from +z), side (from +x) or back (from -z)."""
    solid = np.zeros(len(grid.palette), bool)
    colours = np.zeros((len(grid.palette), 3), float)
    for i, st in enumerate(grid.palette):
        c = colour_of(st)
        solid[i] = c is not None
        colours[i] = c if c is not None else (0, 0, 0)
    a = grid.a
    if view == 'front':
        a = a[:, :, ::-1]
    elif view == 'side':
        a = np.transpose(a, (2, 1, 0))[::-1, :, ::-1]
    occ = solid[a]
    hit = occ.any(axis=2)
    first = np.argmax(occ, axis=2)
    ids = np.take_along_axis(a, first[..., None], axis=2)[..., 0]
    col = colours[ids] * (1.0 - 0.55 * first[..., None] / max(1, a.shape[2]))[..., :]
    img = np.zeros((a.shape[1], a.shape[0], 3), np.uint8)
    img[:] = (14, 16, 20)
    rgb = np.transpose(np.clip(col, 0, 255).astype(np.uint8), (1, 0, 2))[::-1]
    m = np.transpose(hit)[::-1]
    img[m] = rgb[m]
    Image.fromarray(img).resize((a.shape[0] * scale, a.shape[1] * scale), Image.NEAREST).save(path)


def face_offsets(kind, s):
    """Pixel offsets of one projected face (2:1 isometric), relative to the voxel's top corner."""
    pts = []
    if kind == 0:   # top rhombus
        for v in range(2 * s):
            half = (v + 1) * 2 if v < s else (2 * s - v) * 2
            for u in range(-half, half):
                pts.append((u, v))
    elif kind == 1:  # east (right) face
        for u in range(0, 2 * s):
            for v in range(2 * s):
                pts.append((u, s + v + u // 2))
    else:            # south (left) face
        for u in range(-2 * s, 0):
            for v in range(2 * s):
                pts.append((u, s + v + (-u - 1) // 2))
    return pts


def rle(flat):
    """Runs of (count, palette index) as varints."""
    out = bytearray()
    change = np.flatnonzero(np.diff(flat.astype(np.int64))) + 1
    starts = np.r_[0, change]
    ends = np.r_[change, len(flat)]
    for s0, e0 in zip(starts, ends):
        out += varint(int(e0 - s0)) + varint(int(flat[s0]))
    return bytes(out)


def varint(v):
    b = bytearray()
    while True:
        byte = v & 0x7F
        v >>= 7
        if v:
            b.append(byte | 0x80)
        else:
            b.append(byte)
            return bytes(b)


# -------------------------------------------------------------------- sculpting

class Sculpt:
    """Signed-distance sculpting: primitives tagged with a material, unioned (smoothly) and
    voxelised as a shell, so statues are hollow and cheap."""

    def __init__(self):
        self.prims = []

    def add(self, fn, bounds, material, k=0.0):
        self.prims.append((fn, bounds, material, k))

    def sphere(self, c, r, material, k=0.0):
        c = np.array(c, float)
        self.add(lambda X, Y, Z: np.sqrt((X - c[0]) ** 2 + (Y - c[1]) ** 2 + (Z - c[2]) ** 2) - r,
                 (c - r - 1, c + r + 1), material, k)

    def ellipsoid(self, c, radii, material, k=0.0):
        c, rr = np.array(c, float), np.array(radii, float)

        def fn(X, Y, Z):
            q = np.sqrt(((X - c[0]) / rr[0]) ** 2 + ((Y - c[1]) / rr[1]) ** 2 + ((Z - c[2]) / rr[2]) ** 2)
            return (q - 1) * rr.min()
        self.add(fn, (c - rr - 1, c + rr + 1), material, k)

    def capsule(self, a, b, r1, r2, material, k=0.0):
        """A rounded cone from a (radius r1) to b (radius r2)."""
        a, b = np.array(a, float), np.array(b, float)
        ba = b - a
        L2 = float(ba @ ba)

        def fn(X, Y, Z):
            px, py, pz = X - a[0], Y - a[1], Z - a[2]
            h = np.clip((px * ba[0] + py * ba[1] + pz * ba[2]) / L2, 0, 1)
            dx, dy, dz = px - ba[0] * h, py - ba[1] * h, pz - ba[2] * h
            return np.sqrt(dx * dx + dy * dy + dz * dz) - (r1 + (r2 - r1) * h)
        r = max(r1, r2)
        self.add(fn, (np.minimum(a, b) - r - 1, np.maximum(a, b) + r + 1), material, k)

    def box(self, c, half, material, round_=0.0, yaw=0.0, k=0.0):
        """An axis-aligned box (optionally turned about the vertical axis) with rounded edges."""
        c, hh = np.array(c, float), np.array(half, float) - round_
        cy, sy = math.cos(yaw), math.sin(yaw)

        def fn(X, Y, Z):
            px, py, pz = X - c[0], Y - c[1], Z - c[2]
            qx = np.abs(px * cy + pz * sy) - hh[0]
            qz = np.abs(-px * sy + pz * cy) - hh[2]
            qy = np.abs(py) - hh[1]
            out = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2 + np.maximum(qz, 0) ** 2)
            ins = np.minimum(np.maximum(qx, np.maximum(qy, qz)), 0)
            return out + ins - round_
        r = float(np.linalg.norm(half)) + 1
        self.add(fn, (c - r, c + r), material, k)

    def chain(self, points, radii, material, k=0.0):
        """Capsules through a list of points: horns, tails, fingers."""
        for i in range(len(points) - 1):
            self.capsule(points[i], points[i + 1], radii[i], radii[i + 1], material, k)

    def voxelise(self, grid, materials, thickness=2.2, fill_below=None):
        """Evaluates the union and writes a shell `thickness` deep. materials(name, X, Y, Z, d, up)
        returns a palette-name array for the voxels of that material."""
        lo = np.min([p[1][0] for p in self.prims], axis=0)
        hi = np.max([p[1][1] for p in self.prims], axis=0)
        lo = np.maximum(np.floor(lo).astype(int), 0)
        hi = np.minimum(np.ceil(hi).astype(int), [grid.sx - 1, grid.sy - 1, grid.sz - 1])
        X, Y, Z = np.meshgrid(np.arange(lo[0], hi[0] + 1) + 0.5, np.arange(lo[1], hi[1] + 1) + 0.5,
                              np.arange(lo[2], hi[2] + 1) + 0.5, indexing='ij')
        d = np.full(X.shape, 1e9)
        mat = np.full(X.shape, -1, int)
        names = []
        for fn, (blo, bhi), m, k in self.prims:
            # Evaluate each primitive only inside its own bounds.
            ia = np.maximum(np.floor(blo).astype(int) - lo, 0)
            ib = np.minimum(np.ceil(bhi).astype(int) - lo + 1, X.shape)
            if np.any(ib <= ia):
                continue
            sl = (slice(ia[0], ib[0]), slice(ia[1], ib[1]), slice(ia[2], ib[2]))
            di = fn(X[sl], Y[sl], Z[sl])
            cur = d[sl]
            if m not in names:
                names.append(m)
            mi = names.index(m)
            if k > 0:
                hmix = np.clip(0.5 + 0.5 * (cur - di) / k, 0, 1)
                blended = di * hmix + cur * (1 - hmix) - k * hmix * (1 - hmix)
                take = di < cur
                mat[sl] = np.where(take, mi, mat[sl])
                d[sl] = np.minimum(blended, np.minimum(cur, di))
            else:
                take = di < cur
                mat[sl] = np.where(take, mi, mat[sl])
                d[sl] = np.where(take, di, cur)
        inside = d <= 0
        shell = inside & (d > -thickness)
        # Upward-facing surface: inside here, outside above.
        above = np.ones_like(inside)
        above[:, :-1, :] = ~inside[:, 1:, :]
        for mi, m in enumerate(names):
            sel = shell & (mat == mi)
            if not sel.any():
                continue
            xs, ys, zs = np.nonzero(sel)
            states = materials(m, X[sel], Y[sel], Z[sel], d[sel], above[sel])
            for st in sorted(set(states), key=str):
                if st is None:
                    continue  # a hole: leave the world alone there
                pick = states == st
                grid.a[xs[pick] + lo[0], ys[pick] + lo[1], zs[pick] + lo[2]] = grid.pid(st)
        return inside, lo


# -------------------------------------------------------------------- noise

def hash3(x, y, z, seed=0):
    h = (np.asarray(x, np.int64) * 374761393 + np.asarray(y, np.int64) * 668265263 + np.asarray(z, np.int64) * 2147483647 + seed * 144665) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, z, scale, seed=0):
    """Smooth value noise in [0, 1]."""
    x, y, z = np.asarray(x) / scale, np.asarray(y) / scale, np.asarray(z) / scale
    xi, yi, zi = np.floor(x), np.floor(y), np.floor(z)
    xf, yf, zf = x - xi, y - yi, z - zi
    u, v, w = xf * xf * (3 - 2 * xf), yf * yf * (3 - 2 * yf), zf * zf * (3 - 2 * zf)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                wgt = (u if dx else 1 - u) * (v if dy else 1 - v) * (w if dz else 1 - w)
                out = out + wgt * hash3(xi + dx, yi + dy, zi + dz, seed)
    return out


def pick(choices, r):
    """Weighted choice per voxel. choices: [(state, weight)], r in [0, 1)."""
    names = [c[0] for c in choices]
    w = np.cumsum([c[1] for c in choices], dtype=float)
    w /= w[-1]
    idx = np.searchsorted(w, np.asarray(r) * 0.999999)
    return np.array(names, dtype=object)[idx]


# -------------------------------------------------------------------- colours (for previews only)

COLOURS = {
    'stone': (125, 125, 125), 'cobblestone': (120, 118, 118), 'mossy_cobblestone': (100, 118, 90),
    'stone_bricks': (122, 121, 122), 'mossy_stone_bricks': (104, 119, 93), 'cracked_stone_bricks': (118, 117, 118),
    'chiseled_stone_bricks': (119, 118, 119), 'smooth_stone': (158, 158, 158), 'andesite': (136, 136, 136),
    'polished_andesite': (132, 135, 134), 'tuff': (108, 109, 102), 'tuff_bricks': (98, 102, 95), 'polished_tuff': (96, 101, 95),
    'deepslate': (80, 80, 82), 'cobbled_deepslate': (77, 77, 80), 'polished_deepslate': (72, 72, 73),
    'deepslate_bricks': (70, 70, 71), 'cracked_deepslate_bricks': (64, 64, 65), 'deepslate_tiles': (54, 54, 55),
    'cracked_deepslate_tiles': (52, 52, 52), 'chiseled_deepslate': (54, 54, 55),
    'blackstone': (42, 36, 41), 'polished_blackstone': (53, 48, 56), 'polished_blackstone_bricks': (48, 42, 49),
    'cracked_polished_blackstone_bricks': (44, 37, 43), 'chiseled_polished_blackstone': (53, 48, 56),
    'gilded_blackstone': (56, 43, 38), 'basalt': (80, 81, 86), 'polished_basalt': (99, 98, 100), 'smooth_basalt': (72, 72, 78),
    'calcite': (223, 224, 220), 'diorite': (188, 188, 188), 'polished_diorite': (192, 193, 194),
    'quartz_block': (235, 229, 222), 'smooth_quartz': (235, 229, 222), 'quartz_bricks': (234, 229, 221),
    'chiseled_quartz_block': (231, 226, 218), 'quartz_pillar': (235, 230, 223), 'white_concrete': (207, 213, 214),
    'bone_block': (229, 225, 207), 'dripstone_block': (134, 107, 92),
    'magma_block': (142, 63, 31), 'shroomlight': (240, 146, 70), 'crying_obsidian': (32, 10, 60), 'obsidian': (15, 10, 24),
    'glowstone': (171, 131, 84), 'sea_lantern': (172, 199, 190), 'ochre_froglight': (250, 245, 206),
    'pearlescent_froglight': (245, 240, 239), 'verdant_froglight': (229, 244, 228),
    'moss_block': (89, 109, 45), 'moss_carpet': (89, 109, 45), 'grass_block': (95, 159, 53), 'dirt': (134, 96, 67),
    'coarse_dirt': (119, 85, 59), 'rooted_dirt': (144, 103, 76), 'podzol': (91, 63, 24), 'gravel': (131, 127, 126),
    'mud_bricks': (137, 103, 79), 'packed_mud': (142, 106, 79), 'bricks': (150, 97, 83), 'terracotta': (152, 94, 67),
    'oak_planks': (162, 130, 78), 'spruce_planks': (114, 84, 48), 'dark_oak_planks': (66, 43, 20), 'birch_planks': (192, 175, 121),
    'oak_log': (109, 85, 50), 'spruce_log': (58, 37, 16), 'dark_oak_log': (60, 46, 26), 'stripped_spruce_log': (115, 89, 52),
    'stripped_dark_oak_log': (72, 56, 36), 'oak_fence': (162, 130, 78), 'spruce_fence': (114, 84, 48), 'dark_oak_fence': (66, 43, 20),
    'copper_block': (192, 107, 79), 'cut_copper': (191, 106, 80), 'exposed_cut_copper': (154, 121, 101),
    'weathered_cut_copper': (109, 145, 107), 'oxidized_cut_copper': (79, 153, 126), 'exposed_copper': (161, 125, 103),
    'weathered_copper': (108, 153, 110), 'oxidized_copper': (82, 162, 132), 'copper_grate': (190, 105, 78),
    'waxed_copper_block': (192, 107, 79), 'waxed_cut_copper': (191, 106, 80), 'waxed_exposed_cut_copper': (154, 121, 101),
    'waxed_weathered_cut_copper': (109, 145, 107), 'waxed_oxidized_cut_copper': (79, 153, 126), 'waxed_copper_grate': (190, 105, 78),
    'waxed_exposed_copper_grate': (154, 121, 101), 'waxed_oxidized_copper': (82, 162, 132), 'waxed_weathered_copper': (108, 153, 110),
    'waxed_copper_bulb': (156, 86, 51), 'waxed_exposed_copper': (161, 125, 103), 'waxed_chiseled_copper': (184, 100, 73),
    'iron_block': (220, 220, 220), 'iron_bars': (150, 150, 150), 'iron_chain': (60, 64, 72), 'anvil': (70, 70, 70),
    'gold_block': (246, 208, 61), 'raw_gold_block': (221, 169, 46), 'lantern': (200, 160, 90), 'soul_lantern': (120, 200, 210),
    'white_wool': (234, 236, 236), 'light_gray_wool': (142, 142, 135), 'gray_wool': (63, 68, 72), 'brown_wool': (114, 72, 41),
    'red_wool': (161, 39, 35), 'black_wool': (21, 21, 26), 'orange_wool': (241, 118, 20), 'yellow_wool': (249, 198, 40),
    'white_stained_glass': (255, 255, 255), 'glass': (200, 220, 230), 'tinted_glass': (44, 38, 46),
    'purple_stained_glass': (127, 63, 178), 'blue_stained_glass': (51, 76, 178), 'red_stained_glass': (153, 51, 51),
    'yellow_stained_glass': (229, 229, 51), 'orange_stained_glass': (216, 127, 51), 'cyan_stained_glass': (76, 127, 153),
    'magenta_stained_glass': (178, 76, 216), 'light_blue_stained_glass': (102, 153, 216), 'lime_stained_glass': (127, 204, 25),
    'green_stained_glass': (102, 127, 51), 'black_stained_glass': (25, 25, 25), 'gray_stained_glass': (76, 76, 76),
    'red_stained_glass_pane': (153, 51, 51), 'water': (52, 90, 190), 'hay_block': (166, 136, 38), 'barrel': (138, 102, 57),
    'campfire': (110, 80, 50), 'smoker': (90, 80, 70), 'blast_furnace': (80, 80, 82), 'furnace': (100, 100, 100),
    'chest': (160, 110, 40), 'bookshelf': (117, 94, 59), 'lectern': (175, 140, 87), 'candle': (230, 210, 170),
    'white_candle': (230, 225, 210), 'crimson_planks': (101, 48, 70), 'warped_planks': (43, 104, 99),
    'red_nether_bricks': (69, 7, 9), 'nether_bricks': (44, 21, 26), 'end_stone_bricks': (218, 224, 162),
    'prismarine_bricks': (99, 171, 158), 'dark_prismarine': (51, 91, 75), 'amethyst_block': (133, 97, 191),
    'mud': (60, 57, 60), 'packed_ice': (141, 180, 250), 'snow_block': (249, 254, 254), 'sandstone': (216, 203, 155),
    'cut_sandstone': (217, 206, 159), 'smooth_sandstone': (223, 214, 170), 'red_sandstone': (186, 99, 29),
    'polished_granite': (154, 106, 89), 'granite': (149, 103, 85), 'resin_bricks': (206, 99, 37), 'chiseled_resin_bricks': (200, 95, 35),
    'pale_oak_planks': (227, 217, 214), 'pale_oak_log': (87, 77, 76), 'cherry_planks': (226, 178, 172), 'cherry_log': (54, 33, 44),
    'mangrove_planks': (117, 54, 48), 'bamboo_planks': (193, 173, 80), 'scaffolding': (170, 132, 73),
    'redstone_block': (175, 24, 5), 'redstone_lamp': (180, 110, 60), 'target': (226, 170, 157),
    'loom': (142, 119, 91), 'cartography_table': (104, 87, 67), 'smithing_table': (57, 58, 70), 'cauldron': (74, 73, 74),
    'jack_o_lantern': (214, 152, 52), 'pumpkin': (198, 118, 24), 'white_banner': (230, 230, 230),
}
# The sixteen dyed terracottas, concretes and wools, by their map colours.
for _dye, _t, _c, _w in (
        ('white', (210, 178, 161), (207, 213, 214), (234, 236, 236)), ('orange', (162, 84, 38), (224, 97, 1), (241, 118, 20)),
        ('magenta', (150, 88, 109), (169, 48, 159), (190, 69, 180)), ('light_blue', (113, 109, 138), (36, 137, 199), (58, 175, 217)),
        ('yellow', (186, 133, 35), (241, 175, 21), (249, 198, 40)), ('lime', (104, 118, 53), (94, 169, 25), (112, 185, 26)),
        ('pink', (162, 78, 79), (214, 101, 143), (238, 141, 172)), ('gray', (58, 42, 36), (55, 58, 62), (63, 68, 72)),
        ('light_gray', (135, 107, 98), (125, 125, 115), (142, 142, 135)), ('cyan', (87, 91, 91), (21, 119, 136), (21, 138, 145)),
        ('purple', (118, 70, 86), (100, 32, 156), (122, 42, 173)), ('blue', (74, 60, 91), (45, 47, 143), (53, 57, 157)),
        ('brown', (77, 51, 36), (96, 60, 32), (114, 72, 41)), ('green', (76, 83, 42), (73, 91, 36), (85, 110, 28)),
        ('red', (143, 61, 47), (142, 33, 33), (161, 39, 35)), ('black', (37, 23, 16), (8, 10, 15), (21, 21, 26))):
    COLOURS.setdefault(_dye + '_terracotta', _t)
    COLOURS.setdefault(_dye + '_concrete', _c)
    COLOURS.setdefault(_dye + '_wool', _w)
    COLOURS.setdefault(_dye + '_carpet', _w)


def colour_of(state):
    if state.startswith('<'):
        return None
    name = state.split('[')[0].split(':')[1]
    if name in ('air', 'cave_air', 'light', 'structure_void'):
        return None
    for suffix in ('_stairs', '_slab', '_wall', '_fence_gate', '_pane', '_button', '_pressure_plate', '_trapdoor', '_door'):
        if name.endswith(suffix):
            base = name[:-len(suffix)]
            for cand in (base, base + 's', base + '_block', base.replace('brick', 'bricks'), base + '_planks'):
                if cand in COLOURS:
                    return COLOURS[cand]
    if name in COLOURS:
        return COLOURS[name]
    if 'wall_torch' in name or name == 'torch':
        return (255, 200, 90)
    if 'banner' in name:
        return (150, 40, 40)
    if 'candle' in name:
        return (230, 210, 170)
    if 'leaves' in name:
        return (60, 110, 40)
    if 'log' in name or 'wood' in name:
        return (80, 60, 35)
    return (150, 140, 150)

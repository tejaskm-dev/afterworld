"""The Wardens of the Pass: two colossal winged angels in white stone, facing each other across a
ruined processional way, hands on swords planted at their feet, wings raised, haloes that glow
at night. Beyond them, a broken stair climbs to an arch over nothing."""
import math
import numpy as np
from vox import Grid, Sculpt, hash3, vnoise, pick

W, H, D = 96, 76, 96
G = 2
PED = 6                  # pedestal height
PATH_X = 48
S = 1.3                  # the angels are modelled at 1:1 and built 30% larger


class Frame:
    """Maps an angel's local coordinates (x right, y up, z forward) into the world."""

    def __init__(self, ox, oy, oz, facing):
        self.o = (ox, oy, oz)
        self.facing = facing  # +1: faces +x, -1: faces -x

    def p(self, x, y, z):
        ox, oy, oz = self.o
        f = self.facing
        return (ox + f * z * S, oy + y * S, oz - f * x * S)

    def r(self, rx, ry, rz):
        return (rz, ry, rx)


class Scaled(Sculpt):
    """A sculpt whose radii and half-sizes are scaled to match the frame."""

    def sphere(self, c, r, material, k=0.0):
        super().sphere(c, r * S, material, k * S)

    def ellipsoid(self, c, radii, material, k=0.0):
        super().ellipsoid(c, tuple(v * S for v in radii), material, k * S)

    def capsule(self, a, b, r1, r2, material, k=0.0):
        super().capsule(a, b, r1 * S, r2 * S, material, k * S)

    def box(self, c, half, material, round_=0.0, yaw=0.0, k=0.0):
        super().box(c, tuple(v * S for v in half), material, round_ * S, yaw, k * S)


def angel(s, fr):
    P = fr.p
    # Robe falling to the pedestal, with a few heavy folds.
    s.capsule(P(0, 0.5, -0.3), P(0, 15, 0), 6.2, 3.4, 'robe', k=1.5)
    for fx, fz in ((-4.5, 1.5), (4.5, 1.5), (-2.5, 4), (2.5, 4), (0, -4.8)):
        s.capsule(P(fx, 0.5, fz), P(fx * 0.55, 12, fz * 0.5), 1.4, 0.6, 'robe', k=1.2)
    s.ellipsoid(P(0, 19.5, 0.2), fr.r(3.7, 5.2, 2.9), 'robe', k=2)
    s.capsule(P(-3.2, 16.5, 0.4), P(3.2, 16.5, 0.4), 0.9, 0.9, 'gold')            # girdle
    for side in (-1, 1):
        s.sphere(P(side * 3.7, 23.3, 0), 2.0, 'robe', k=1)
        s.capsule(P(side * 3.8, 23, 0), P(side * 4.4, 18.2, 1.8), 1.35, 1.15, 'robe', k=0.8)
        s.capsule(P(side * 4.4, 18.2, 1.8), P(side * 1.1, 15.8, 3.6), 1.15, 0.95, 'skin', k=0.6)
        s.sphere(P(side * 0.8, 15.9, 3.7), 1.05, 'skin')
    s.capsule(P(0, 24.2, 0.2), P(0, 25.8, 0.7), 1.25, 1.15, 'skin', k=0.6)
    s.ellipsoid(P(0, 27.8, 1.1), fr.r(1.9, 2.3, 2.1), 'skin', k=0.8)                # head, a little bowed
    s.ellipsoid(P(0, 28.4, 0.3), fr.r(2.35, 2.6, 2.35), 'robe', k=0.6)              # hood
    # The sword, point down into the pedestal.
    s.box(P(0, 6.5, 3.9), fr.r(0.35, 7.6, 0.6), 'steel')
    s.box(P(0, 14.3, 3.9), fr.r(0.45, 0.4, 2.8), 'gold', round_=0.15)
    s.capsule(P(0, 14.6, 3.9), P(0, 16.6, 3.9), 0.55, 0.55, 'steel')
    s.sphere(P(0, 17.2, 3.9), 0.8, 'gold')
    # Wings: raised arms and long feathers sweeping down behind.
    for side in (-1, 1):
        root = P(side * 1.8, 22.6, -2.4)
        elbow = P(side * 8.5, 30.5, -5.5)
        wrist = P(side * 11.5, 40.5, -6.8)
        s.capsule(root, elbow, 1.6, 1.25, 'wing', k=0.8)
        s.capsule(elbow, wrist, 1.25, 0.85, 'wing', k=0.5)
        tips = []
        n = 15
        for i in range(n):
            t = i / (n - 1)
            base_pt = np.array(wrist) * (1 - t) + np.array(elbow) * t if t < 0.6 else np.array(elbow) * (1 - (t - 0.6) / 0.4) + np.array(root) * ((t - 0.6) / 0.4)
            # Feather tips fan from high and wide down to long trailing feathers near the ground.
            tip = P(side * (13.5 - 7.5 * t), 41 - 33 * t ** 0.85, -7.5 + 1.5 * t)
            tips.append(tip)
            s.capsule(tuple(base_pt), tip, 0.95, 0.35, 'wing')
        s.add(*sheet([root, elbow, wrist] + tips[::3] + [tips[-1]]))
    # Halo behind the head: a glowing ring.
    hx, hy, hz = P(0, 30.6, -1.2)
    c = np.array([hx, hy, hz], float)
    f = fr.facing

    def halo(X, Y, Z):
        # Ring in the plane facing the angel's front (world y-z plane for an angel facing along x).
        a = np.sqrt((Z - c[2]) ** 2 + (Y - c[1]) ** 2) - 3.2 * S
        return np.sqrt(a * a + (X - c[0]) ** 2) - 0.6
    s.add(halo, (c - 5, c + 5), 'halo', 0.0)


def sheet(points):
    """A thin feathered membrane through the wing's points: the fan of triangles from the root."""
    pts = [np.array(p, float) for p in points]
    root = pts[0]
    tris = [(root, pts[i], pts[i + 1]) for i in range(1, len(pts) - 1)]
    allp = np.array(pts)

    def fn(X, Y, Z):
        Pt = np.stack([X, Y, Z], axis=-1)
        d = np.full(X.shape, 1e9)
        for a, b, c in tris:
            d = np.minimum(d, tri_dist(Pt, a, b, c))
        return d - 0.7
    return fn, (allp.min(axis=0) - 2, allp.max(axis=0) + 2), 'wing', 0.0


def tri_dist(P, a, b, c):
    ba, pa = b - a, P - a
    cb, pb = c - b, P - b
    ac, pc = a - c, P - c
    nor = np.cross(ba, ac)

    def dot(u, v):
        return np.sum(u * v, axis=-1)
    outside = (np.sign(dot(np.cross(ba, nor), pa)) + np.sign(dot(np.cross(cb, nor), pb)) + np.sign(dot(np.cross(ac, nor), pc))) < 2.0

    def seg(e, p):
        t = np.clip(dot(p, e) / float(np.dot(e, e)), 0, 1)
        q = e * t[..., None] - p
        return np.sum(q * q, axis=-1)
    edge = np.minimum(np.minimum(seg(ba, pa), seg(cb, pb)), seg(ac, pc))
    plane = dot(nor, pa) ** 2 / max(1e-9, float(np.dot(nor, nor)))
    return np.sqrt(np.where(outside, edge, plane))


def materials(m, X, Y, Z, d, up):
    r = hash3(X.astype(int), Y.astype(int), Z.astype(int), 31)
    out = np.empty(X.shape, dtype=object)
    if m in ('robe', 'skin', 'wing'):
        if m == 'skin':
            out[:] = pick([('calcite', 3), ('smooth_quartz', 1)], r)
        elif m == 'wing':
            out[:] = pick([('calcite', 4), ('diorite', 1), ('polished_diorite', 2)], r)
        else:
            out[:] = pick([('calcite', 3), ('polished_diorite', 2), ('diorite', 2), ('smooth_quartz', 1)], r)
        weather = vnoise(X, Y, Z, 4.0, 77)
        out[weather > 0.68] = pick([('andesite', 1), ('tuff', 1)], r[weather > 0.68])
        moss = up & (vnoise(X, Y, Z, 3.0, 5) > 0.6)
        out[moss] = pick([('moss_block', 3), ('flowering_azalea_leaves[persistent=true]', 1)], r[moss])
    elif m == 'gold':
        out[:] = pick([('raw_gold_block', 2), ('gold_block', 1)], r)
    elif m == 'steel':
        out[:] = pick([('iron_block', 2), ('polished_andesite', 1)], r)
    elif m == 'halo':
        out[:] = 'ochre_froglight'
    else:
        out[:] = 'stone'
    return out


def pedestal(g, x, z):
    for y in range(G - 1, G + PED):
        k = max(0, (G + PED - 1 - y) // 2)
        g.fill(x - 7 - k, y, z - 7 - k, x + 7 + k, y, z + 7 + k, 'stone_bricks' if y % 3 else 'polished_andesite')
    g.fill(x - 7, G + PED - 2, z - 7, x + 7, G + PED - 2, z + 7, 'chiseled_stone_bricks')
    g.fill(x - 6, G + PED - 1, z - 6, x + 6, G + PED - 1, z + 6, 'polished_diorite')
    for dx, dz in ((-7, -7), (7, -7), (-7, 7), (7, 7)):
        g.set(x + dx, G + PED, z + dz, 'stone_brick_wall')
        g.set(x + dx, G + PED + 1, z + dz, 'lantern[hanging=false]')


def way(g, rng):
    """The processional way: worn paving, broken columns, a stair up to an arch over nothing."""
    X, Z = g.xz()
    r = hash3(X.astype(int), 4, Z.astype(int), 6)
    road = (np.abs(X - PATH_X) < 9) & (Z > 4) & (Z < D - 4)
    plaza = np.hypot(X - PATH_X, (Z - 40) * 1.2) < 34
    floor = road | plaza
    g.a[:, 0, :][floor] = g.pid('stone')
    tiles = np.where(r < 0.45, 'polished_diorite', np.where(r < 0.7, 'calcite', np.where(r < 0.85, 'stone_bricks', 'mossy_stone_bricks')))
    gaps = floor & (vnoise(X, 2, Z, 4.0, 3) > 0.62)
    for st in sorted(set(tiles[floor].tolist()), key=str):
        g.a[:, G - 1, :][floor & ~gaps & (tiles == st)] = g.pid(st)
    g.a[:, G - 1, :][gaps] = g.pid('grass_block')
    flowers = gaps & (r > 0.7)
    g.a[:, G, :][flowers] = g.pid('oxeye_daisy')
    g.a[:, G, :][floor & ~flowers] = g.pid('air')
    for y in range(G + 1, G + 4):
        g.a[:, y, :][floor] = g.pid('air')
    # Columns along the way, some broken, some fallen.
    for z in range(10, 76, 9):
        for side in (-1, 1):
            x = PATH_X + side * 11
            h = int(rng.integers(3, 15)) if rng.random() < 0.7 else 15
            g.fill(x, G, z, x, G + h, z, 'quartz_pillar[axis=y]')
            g.set(x, G - 1, z, 'chiseled_stone_bricks')
            if h == 15:
                g.set(x, G + 16, z, 'chiseled_quartz_block')
            elif rng.random() < 0.5:
                L = int(rng.integers(3, 8))
                for k in range(L):
                    g.set(x + side * (k + 1), G, z + (1 if k % 2 else 0), 'quartz_pillar[axis=x]')
    # The stair and the arch.
    for k in range(6):
        z = 70 + k
        g.fill(PATH_X - 8, G - 1, z, PATH_X + 8, G - 1 + k, z, 'polished_diorite')
        g.fill(PATH_X - 8, G + k, z, PATH_X + 8, G + k, z, 'polished_diorite_stairs[facing=south]')
    top = G + 6
    g.fill(PATH_X - 9, G - 1, 76, PATH_X + 9, top - 1, 84, 'stone_bricks')
    g.fill(PATH_X - 9, top - 1, 76, PATH_X + 9, top - 1, 84, 'polished_diorite')
    for side in (-1, 1):
        g.fill(PATH_X + side * 7, top, 80, PATH_X + side * 8, top + 16, 81, 'calcite')
    for a in np.linspace(0, math.pi, 40):
        x = PATH_X + math.cos(a) * 7.5
        y = top + 16 + math.sin(a) * 5
        g.fill(x, y, 80, x, y + 1, 81, 'calcite')
    g.set(PATH_X, top + 22, 80, 'bell[attachment=floor,facing=north]')
    g.chest(PATH_X, top, 83, 'north', 'afterworld:chests/wardens_shrine')


def build():
    g = Grid(W, H, D)
    rng = np.random.default_rng(23)
    way(g, rng)
    s = Scaled()
    for ox, facing in ((PATH_X - 22, 1), (PATH_X + 22, -1)):
        pedestal(g, ox, 40)
        angel(s, Frame(ox, G + PED, 40, facing))
    s.voxelise(g, materials, thickness=1.8)
    return g

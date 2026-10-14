"""The Bound Titan: a horned colossus kneeling on a ruined dais, hands on a sword planted in the
earth, wings folded, chained by the wrists to four rune obelisks. Sculpted from signed distances
and voxelised as a hollow shell."""
import math
import numpy as np
from vox import Grid, Sculpt, vnoise, hash3, pick

W, H, D = 112, 96, 112
CX, CZ = 56, 52
G0 = 4  # top of the dais


def body_parts(s):
    """Joints are given as offsets from the centre line; mirrored pairs use +x for the titan's left."""
    def P(x, y, z):
        return (CX + x, G0 + y, CZ + z)

    # Pelvis, belly, chest: leaning a little forward over the sword.
    s.ellipsoid(P(0, 23, -2), (11, 6.5, 8), 'body', k=3)
    s.ellipsoid(P(0, 30, 0), (10, 8, 7.5), 'body', k=4)
    s.ellipsoid(P(0, 40, 3), (15.5, 10.5, 9.5), 'armour', k=4)
    s.box(P(0, 41, 9), (9, 6, 2), 'armour', round_=1.5, k=2)
    # Shoulders (pauldrons) with spikes.
    for side in (-1, 1):
        s.sphere(P(side * 15, 46, 3), 7, 'armour', k=3)
        s.ellipsoid(P(side * 16, 50, 3), (8, 3, 7), 'armour', k=2)
        for i, (dx, dy, dz) in enumerate(((5, 6, 0), (3, 7, -4), (3, 7, 4))):
            s.capsule(P(side * 17, 51, 3 + dz * 0.4), P(side * (17 + dx), 51 + dy, 3 + dz), 1.6, 0.3, 'horn')
    # Neck and the bowed head.
    s.capsule(P(0, 48, 3), P(0, 54, 6), 4.5, 4, 'body', k=2)
    s.ellipsoid(P(0, 56, 9), (6, 6.5, 6), 'body', k=2)
    s.box(P(0, 58, 13.5), (5.2, 1.3, 1.6), 'body', round_=0.6, k=1)        # brow
    s.ellipsoid(P(0, 52.5, 12), (4.2, 2.6, 3.4), 'body', k=1.5)           # jaw
    for side in (-1, 1):
        s.sphere(P(side * 2.3, 56.6, 14.2), 0.9, 'eyes')
    # Horns sweeping out, up and forward at the tips.
    for side in (-1, 1):
        pts = [P(side * 4.5, 60, 8), P(side * 9, 63.5, 6), P(side * 14, 67, 4.5), P(side * 17.5, 72, 4),
               P(side * 18, 78, 6), P(side * 16, 82, 9.5)]
        s.chain(pts, [3.0, 2.7, 2.2, 1.7, 1.1, 0.4], 'horn')
        # A second, shorter horn behind the first.
        s.chain([P(side * 4, 61, 5), P(side * 7, 66, 1), P(side * 9, 71, -3), P(side * 8, 75, -7)], [2.0, 1.7, 1.2, 0.5], 'horn')
    # Arms down to the sword's grip.
    for side in (-1, 1):
        sh, el, wr = P(side * 15, 45, 3), P(side * 18, 33, 12), P(side * 4.5, 33, 21)
        s.capsule(sh, el, 6.2, 5.0, 'body', k=2)
        s.capsule(el, wr, 5.0, 3.8, 'body', k=2)
        s.capsule(P(side * 17.5, 34.5, 10.5), P(side * 9, 33.5, 18), 5, 4.2, 'armour', k=1)   # vambrace
        s.ellipsoid(P(side * 3, 34, 22), (3.4, 4.2, 3.6), 'body', k=1.5)                  # hand
        s.ellipsoid(P(side * 18, 33.5, 12), (6, 3, 6), 'cuff')                             # shackle
    # Kneeling leg (titan's right, -x): thigh down to a knee on the ground, shin along the ground behind.
    s.capsule(P(-8, 22, -2), P(-9, 5, 6), 6.5, 5.2, 'body', k=3)
    s.capsule(P(-9, 4, 6), P(-9, 3.5, -14), 4.8, 3.6, 'body', k=2)
    s.ellipsoid(P(-9, 2.5, -18), (3.4, 2.6, 5), 'body', k=1.5)
    s.ellipsoid(P(-9, 5, 6.5), (6, 5, 5), 'armour', k=1.5)                                # knee guard
    # Planted leg (titan's left, +x): thigh forward, shin down, foot flat.
    s.capsule(P(8, 22, -2), P(10, 22, 15), 6.5, 5.6, 'body', k=3)
    s.capsule(P(10, 22, 15), P(10.5, 3, 16), 5.2, 4.2, 'body', k=2)
    s.ellipsoid(P(10.5, 1.5, 19), (4.5, 2.6, 7), 'body', k=2)
    s.ellipsoid(P(10, 22, 16), (6, 5.5, 5.5), 'armour', k=1.5)                            # knee guard
    # A hanging tabard between the thighs.
    s.box(P(0, 15, 9), (6.5, 8, 0.9), 'cloth', round_=0.4)
    # Tail curling round behind.
    s.chain([P(0, 20, -9), P(1, 11, -17), P(6, 4, -24), P(15, 3, -28), P(22, 5, -27), P(25, 9, -23)],
            [4.0, 3.4, 2.8, 2.2, 1.4, 0.4], 'body', k=1.5)
    # Half-raised wings: dark bone spars and torn membranes, the widest part of the silhouette.
    for side in (-1, 1):
        root, wrist = P(side * 9, 45, -7), P(side * 27, 71, -17)
        s.capsule(root, P(side * 18, 60, -12), 3.2, 2.6, 'bone', k=1)
        s.capsule(P(side * 18, 60, -12), wrist, 2.6, 2.0, 'bone')
        s.capsule(wrist, P(side * 29, 76, -18), 1.6, 0.3, 'bone')          # thumb claw
        tips = [P(side * 46, 54, -21), P(side * 42, 37, -19), P(side * 32, 24, -15), P(side * 19, 25, -11)]
        for t in tips:
            s.capsule(wrist, t, 1.5, 0.5, 'bone')
        s.add(*membrane(root, wrist, tips))
    # The sword, planted point-down.
    s.box(P(0, 12, 25), (2.6, 18, 0.8), 'blade')
    s.box(P(0, 31, 25), (10, 1.2, 1.4), 'guard', round_=0.3)
    s.capsule(P(0, 32, 25), P(0, 37, 25), 1.4, 1.4, 'grip')
    s.sphere(P(0, 38.5, 25), 2.2, 'guard')


def membrane(root, wrist, tips):
    """A thin sheet spanning the wing's bones: distance to the fan of triangles, minus a thickness."""
    tris = [(wrist, tips[i], tips[i + 1]) for i in range(len(tips) - 1)] + [(root, wrist, tips[-1])]
    tris = [tuple(np.array(v, float) for v in t) for t in tris]
    allp = np.array([root, wrist] + tips, float)

    def fn(X, Y, Z):
        d = np.full(X.shape, 1e9)
        P = np.stack([X, Y, Z], axis=-1)
        for a, b, c in tris:
            d = np.minimum(d, tri_dist(P, a, b, c))
        return d - 0.7
    return fn, (allp.min(axis=0) - 2, allp.max(axis=0) + 2), 'wing', 0.0


def tri_dist(P, a, b, c):
    """Unsigned distance from points to a triangle (after Inigo Quilez)."""
    ba, pa = b - a, P - a
    cb, pb = c - b, P - b
    ac, pc = a - c, P - c
    nor = np.cross(ba, ac)

    def dot(u, v):
        return np.sum(u * v, axis=-1)

    outside = (np.sign(dot(np.cross(ba, nor), pa)) + np.sign(dot(np.cross(cb, nor), pb))
               + np.sign(dot(np.cross(ac, nor), pc))) < 2.0

    def seg(e, p):
        t = np.clip(dot(p, e) / float(np.dot(e, e)), 0, 1)
        q = e * t[..., None] - p
        return np.sum(q * q, axis=-1)
    edge = np.minimum(np.minimum(seg(ba, pa), seg(cb, pb)), seg(ac, pc))
    plane = dot(nor, pa) ** 2 / float(np.dot(nor, nor))
    return np.sqrt(np.where(outside, edge, plane))


def materials(m, X, Y, Z, d, up):
    r = hash3(X.astype(int), Y.astype(int), Z.astype(int), 11)
    n = vnoise(X, Y, Z, 6.0, 3)
    vein = np.abs(vnoise(X, Y, Z, 9.0, 21) - 0.5) < 0.022
    out = np.empty(X.shape, dtype=object)
    if m == 'body':
        out[:] = pick([('blackstone', 5), ('polished_blackstone', 2), ('basalt[axis=y]', 1), ('cobbled_deepslate', 1)], r)
        out[n > 0.62] = pick([('deepslate_tiles', 2), ('cracked_deepslate_tiles', 1)], r[n > 0.62])
        out[vein] = 'magma_block'
        moss = up & (vnoise(X, Y, Z, 4.0, 9) > 0.66) & (Y > 8)
        out[moss] = pick([('moss_block', 3), ('mossy_cobblestone', 1)], r[moss])
    elif m == 'armour':
        out[:] = pick([('polished_blackstone_bricks', 5), ('cracked_polished_blackstone_bricks', 2), ('chiseled_polished_blackstone', 1)], r)
        out[n > 0.7] = 'gilded_blackstone'
        moss = up & (vnoise(X, Y, Z, 4.0, 9) > 0.72)
        out[moss] = 'moss_block'
    elif m == 'horn':
        out[:] = pick([('bone_block[axis=y]', 4), ('calcite', 1)], r)
        root = np.hypot(X - CX, Z - (CZ + 7)) < 9.5
        out[root & (Y < G0 + 66)] = pick([('polished_blackstone', 2), ('blackstone', 1)], r[root & (Y < G0 + 66)])
    elif m == 'bone':
        out[:] = pick([('polished_basalt[axis=y]', 2), ('blackstone', 2), ('polished_blackstone', 1)], r)
    elif m == 'wing':
        out[:] = pick([('nether_bricks', 4), ('blackstone', 2), ('red_nether_bricks', 1)], r)
        tear = vnoise(X, Y, Z, 3.5, 44) > 0.68
        out[tear] = None
    elif m == 'eyes':
        out[:] = 'shroomlight'
    elif m == 'cloth':
        out[:] = pick([('red_nether_bricks', 4), ('nether_bricks', 1)], r)
    elif m == 'cuff':
        out[:] = pick([('polished_deepslate', 3), ('iron_block', 1)], r)
    elif m == 'blade':
        out[:] = pick([('polished_deepslate', 4), ('deepslate_tiles', 1)], r)
        fuller = np.abs(X - (CX + 0.5)) < 1.0
        out[fuller] = 'crying_obsidian'
    elif m == 'guard':
        out[:] = pick([('gilded_blackstone', 2), ('polished_blackstone', 1)], r)
    elif m == 'grip':
        out[:] = 'stripped_dark_oak_log[axis=y]'
    else:
        out[:] = 'stone'
    return out


def dais(g):
    """A broken round dais with steps, rune rings and a moat of rubble, sinking into the ground."""
    X, Z = g.xz()
    dist = np.hypot(X - CX, Z - CZ)
    r = hash3(X.astype(int), 7, Z.astype(int), 5)
    n = vnoise(X, 3, Z, 7.0, 2)
    for y in range(0, G0):
        rad = 42 - y * 0 - (G0 - 1 - y) * 0
        m = dist <= 46 - (G0 - y) * 1.5 + (n - 0.5) * 6
        g.a[:, y, :][m] = g.pid('deepslate_bricks')
    top = dist <= 44 + (n - 0.5) * 6
    tiles = np.where(r < 0.6, 'deepslate_tiles', np.where(r < 0.85, 'cracked_deepslate_tiles', 'cobbled_deepslate'))
    for st in sorted(set(tiles[top].tolist()), key=str):
        g.a[:, G0 - 1, :][top & (tiles == st)] = g.pid(st)
    # Rune rings inlaid in the floor.
    for rr, st in ((30, 'chiseled_deepslate'), (38, 'polished_blackstone_bricks')):
        ring = (np.abs(dist - rr) < 0.75) & top
        g.a[:, G0 - 1, :][ring] = g.pid(st)
    runes = (np.abs(dist - 34) < 1.2) & (hash3(X.astype(int) // 3, 1, Z.astype(int) // 3, 8) > 0.82) & top
    g.a[:, G0 - 1, :][runes] = g.pid('crying_obsidian')
    # Moss and grass creeping over the edge.
    edge = top & (dist > 34) & (vnoise(X, 5, Z, 5.0, 12) > 0.55)
    g.a[:, G0 - 1, :][edge] = g.pid('moss_block')
    carpet = edge & (r > 0.55)
    g.a[:, G0, :][carpet] = g.pid('moss_carpet')
    # Clear the air above the dais so the ground can't swallow the statue's feet.
    clear = dist <= 40
    for y in range(G0, G0 + 4):
        layer = g.a[:, y, :]
        layer[clear & (layer == 0)] = g.pid('air')


def obelisks(g):
    """Four rune obelisks chained to the titan's wrists and collar."""
    corners = [(-30, -26), (30, -26), (-32, 28), (32, 28)]
    tops = []
    for i, (ox, oz) in enumerate(corners):
        x, z = CX + ox, CZ + oz
        g.fill(x - 3, G0, z - 3, x + 3, G0 + 1, z + 3, 'polished_blackstone_bricks')
        g.fill(x - 2, G0 + 2, z - 2, x + 2, G0 + 20, z + 2, 'deepslate_bricks')
        for y in range(G0 + 4, G0 + 20, 4):
            g.fill(x - 2, y, z - 2, x + 2, y, z + 2, 'polished_deepslate')
        for y in range(G0 + 6, G0 + 18, 5):
            for dx, dz in ((0, -3), (0, 3), (-3, 0), (3, 0)):
                g.set(x + dx * 2 // 3, y, z + dz * 2 // 3, 'crying_obsidian')
        g.fill(x - 1, G0 + 21, z - 1, x + 1, G0 + 24, z + 1, 'chiseled_polished_blackstone')
        g.set(x, G0 + 25, z, 'polished_blackstone_wall')
        g.set(x, G0 + 26, z, 'soul_lantern')
        tops.append((x, G0 + 18, z))
    # Chains: obelisks to the shackles (front pair) and to the collar (back pair).
    wl, wr = (CX - 18, G0 + 33, CZ + 12), (CX + 18, G0 + 33, CZ + 12)
    collar = (CX, G0 + 49, CZ + 2)
    chain(g, tops[2], wl)
    chain(g, tops[3], wr)
    chain(g, tops[0], (CX - 6, G0 + 48, CZ - 2))
    chain(g, tops[1], (CX + 6, G0 + 48, CZ - 2))
    # The collar itself.
    for a in np.linspace(0, 2 * math.pi, 40, endpoint=False):
        g.set(collar[0] + math.cos(a) * 5.5, collar[1], collar[2] + math.sin(a) * 5.0, 'polished_deepslate')


def chain(g, a, b):
    """A sagging chain of iron links between two points, each link turned along its run."""
    a, b = np.array(a, float), np.array(b, float)
    n = int(np.linalg.norm(b - a) * 1.3)
    prev = None
    for i in range(n + 1):
        t = i / n
        p = a + (b - a) * t
        p[1] -= math.sin(t * math.pi) * np.linalg.norm(b - a) * 0.12
        q = tuple(int(round(v)) for v in p)
        if q == prev:
            continue
        if prev is not None:
            dx, dy, dz = (abs(q[k] - prev[k]) for k in range(3))
            axis = 'x' if dx >= max(dy, dz) else 'y' if dy >= dz else 'z'
        else:
            axis = 'y'
        if not g.is_solid(*q):
            g.set(*q, f'iron_chain[axis={axis}]')
        prev = q


def offerings(g):
    """Candles, soul lanterns and an offering chest at the titan's knee."""
    rng = np.random.default_rng(4)
    for _ in range(26):
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(16, 28)
        x, z = int(CX + math.cos(a) * rr), int(CZ + 22 + math.sin(a) * rr * 0.5)
        if g.inside(x, G0, z) and not g.is_solid(x, G0, z):
            count = int(rng.integers(1, 5))
            g.set(x, G0, z, f'black_candle[candles={count},lit=true]' if rng.random() < 0.6 else 'soul_lantern')
    g.chest(CX - 4, G0, CZ + 30, 'south', 'afterworld:chests/titan_offering')
    g.chest(CX + 4, G0, CZ + 30, 'south', 'afterworld:chests/titan_offering')


def rubble(g):
    rng = np.random.default_rng(9)
    for _ in range(60):
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(20, 44)
        x, z = int(CX + math.cos(a) * rr), int(CZ + math.sin(a) * rr)
        h = int(rng.integers(1, 4))
        for y in range(G0, G0 + h):
            if g.inside(x, y, z) and not g.is_solid(x, y, z):
                g.set(x, y, z, ['cobbled_deepslate', 'blackstone', 'cracked_deepslate_tiles', 'deepslate_tile_slab[type=bottom]'][int(rng.integers(0, 4))])


def build():
    g = Grid(W, H, D)
    dais(g)
    s = Sculpt()
    body_parts(s)
    s.voxelise(g, materials, thickness=2.4)
    obelisks(g)
    offerings(g)
    rubble(g)
    return g

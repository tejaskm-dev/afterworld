"""Part five, the second look: the lands' smaller places rebuilt wider and far more detailed (48
blocks across where they were 32), in the manner of the starter town and the lighthouse: rooms you
can walk into, furnished for whoever used them; ground worked into paths, beds and steps; light,
weathering and growth everywhere. Each keeps its old name, its seal, its loot, its lore and its
creatures, so the quests and the words that point at it still hold.
"""
import math
import numpy as np
from vox import hash3, vnoise
import kit
import bkit
import sitekit as K
from sitekit import PAL, mob, elite, clue, seal, npc

GLYPH = 'afterworld:chests/glyph_vault'
VAULT = 'afterworld:chests/site_vault'
CACHE = 'afterworld:chests/site_cache'
D = 'afterworld:'

# The sites rebuilt here; sites.py swaps these builders in for the old ones of the same name.
REBUILT = ['moonlit_clearing', 'hanged_lanterns', 'druids_ring', 'ranger_outpost', 'frozen_watch_post', 'snowbound_camp',
           'caravan_wreck', 'shattered_caravan', 'nomad_yurts',
           'wind_harp', 'aurora_shrine', 'starfall_crater',
           'cinder_watchtower', 'colossus_graveyard', 'bone_orchard',
           'beached_wreck', 'drowned_shrine']
# Sites whose rebuild sinks deeper than before (layers below the ground line).
SINK = {'starfall_crater': 10}


def fl(table, flavour):
    return f'{table}_{flavour}'


# ================================================================ helpers

def surface(g, x, z, G):
    """The first free layer above the highest solid block placed in this column (G if none)."""
    for y in range(g.sy - 1, -1, -1):
        st = g.get(x, y, z)
        if st is not None and st != 'minecraft:air' and g.is_solid(x, y, z):
            return y + 1
    return G


def dist(g, cx, cz):
    X, Z = g.xz()
    return np.hypot(X - cx, Z - cz)


def disc(g, cx, cz, y, r, state, inner=-1.0, rate=1.0, seed=0):
    """A filled circle (or a ring, from `inner`) on layer y; rate < 1 leaves noisy gaps."""
    d = dist(g, cx, cz)
    m = (d <= r) & (d > inner)
    if rate < 1.0:
        X, Z = g.xz()
        noise = np.vectorize(lambda x, z: hash3(int(x), y, int(z), seed))(X, Z)
        m &= noise < rate
    g.a[:, y, :][m] = g.pid(state)


def around(cx, cz, r, n, a0=0.0):
    """n points evenly round a circle, as whole cells."""
    out = []
    for i in range(n):
        t = math.radians(a0 + 360.0 * i / n)
        out.append((int(round(cx + math.cos(t) * r)), int(round(cz + math.sin(t) * r))))
    return out


def inside(g, x, z, margin=0):
    return margin <= x < g.sx - margin and margin <= z < g.sz - margin


def pick(choices, x, y, z, seed):
    """A weighted choice by position: choices are [(state, weight)]."""
    total = sum(w for _, w in choices)
    r = hash3(x, y, z, seed) * total
    for st, w in choices:
        r -= w
        if r <= 0:
            return st
    return choices[-1][0]


def ground(g, x0, z0, x1, z1, y, choices, rate, seed=0, keep=None):
    """Repaints the ground's top layer (y = G - 1) in broad, soft-edged patches (never speckle):
    where a slow noise falls under `rate`, a second slow noise picks which of `choices`
    [(state, weight)] the patch is made of, so each patch is mostly one thing."""
    total = sum(w for _, w in choices)
    cx, cz, half = (g.sx - 1) / 2.0, (g.sz - 1) / 2.0, min(g.sx, g.sz) / 2.0
    for x in range(max(0, x0), min(g.sx, x1 + 1)):
        for z in range(max(0, z0), min(g.sz, z1 + 1)):
            if keep is not None and keep(x, z):
                continue
            if g.get(x, y, z) is not None:
                continue
            # Thinning out toward the plot's edge (with a ragged line), so the site's ground melts
            # into the land's own instead of ending in a square.
            dn = math.hypot(x - cx, z - cz) / half + (float(vnoise(x, 5, z, 5.0, seed + 3)) - 0.5) * 0.3
            edge = min(1.0, max(0.0, (1.0 - dn) / 0.35))
            n = float(vnoise(x, 0, z, 7.0, seed)) + (hash3(x, y, z, seed + 1) - 0.5) * 0.08
            if n >= rate * edge:
                continue
            m = float(vnoise(x, 9, z, 5.0, seed + 2)) * total
            for st, w in choices:
                m -= w
                if m <= 0:
                    break
            g.set(x, y, z, st)


def plants(g, x0, z0, x1, z1, y, choices, rate, seed=0):
    """Plants on whatever ground lies at y - 1, where nothing stands: choices [(state, weight)]."""
    for x in range(max(0, x0), min(g.sx, x1 + 1)):
        for z in range(max(0, z0), min(g.sz, z1 + 1)):
            if not K.empty(g, x, y, z):
                continue
            below = g.get(x, y - 1, z)
            if below is not None and (not g.is_solid(x, y - 1, z) or any(k in below for k in ('slab', 'stairs', 'path', 'farmland', 'planks', 'bricks', 'water'))):
                continue
            if hash3(x, y, z, seed) < rate:
                g.set(x, y, z, pick(choices, x, y, z, seed + 5))


FLOWERS_WEALD = [('short_grass', 6), ('fern', 4), ('closed_eyeblossom', 2), ('lily_of_the_valley', 2), ('azure_bluet', 2),
                 ('wildflowers[facing=north,flower_amount=3]', 2), ('wildflowers[facing=east,flower_amount=2]', 2), ('firefly_bush', 1),
                 ('leaf_litter[facing=south,segment_amount=3]', 2), ('allium', 1)]


def pine(g, x, z, y0, h, seed=0, log='spruce_log', leaves='spruce_leaves', snow=False):
    """A spruce: a straight trunk under a cone of leaf tiers, each tier wider and drooping."""
    for k in range(h):
        kit.log(g, x, y0 + k, z, log)
    top = y0 + h
    tiers = max(3, h // 2)
    for i in range(tiers):
        yy = top - i * 2
        r = 1 + i * 0.75
        for dx in range(-int(r) - 1, int(r) + 2):
            for dz in range(-int(r) - 1, int(r) + 2):
                dd = math.hypot(dx, dz)
                if dd <= r + 0.2 and hash3(x + dx, yy, z + dz, seed) < 0.94:
                    kit.leaves(g, x + dx, yy, z + dz, leaves)
                    if dd > r - 0.9 and hash3(x + dx, yy - 1, z + dz, seed + 3) < 0.45:
                        kit.leaves(g, x + dx, yy - 1, z + dz, leaves)
                    if snow and dd <= r - 0.4:
                        if g.get(x + dx, yy + 1, z + dz) is None:
                            g.set(x + dx, yy + 1, z + dz, 'snow[layers=1]')
    kit.leaves(g, x, top + 1, z, leaves)
    kit.leaves(g, x, top + 2, z, leaves)


def oak(g, x, z, y0, h, r, log='dark_oak_log', leaves='dark_oak_leaves', seed=0, branches=4, wide=False):
    """A broad old tree: a trunk (two wide if `wide`) flaring at its foot, limbs reaching out, and
    a rounded crown of leaf clumps. Returns the ends of its limbs (for hanging lanterns)."""
    w = 2 if wide else 1
    for k in range(h):
        for dx in range(w):
            for dz in range(w):
                kit.log(g, x + dx, y0 + k, z + dz, log)
    # Roots spreading at the foot.
    for i, (dx, dz) in enumerate(((-1, 0), (w, 0), (0, -1), (0, w), (w, w - 1), (-1, w - 1))):
        if hash3(x, i, z, seed) < 0.75:
            kit.log(g, x + dx, y0, z + dz, log, 'x' if dz in (0, w - 1) and dx in (-1, w) else 'z')
    ends = []
    for b in range(branches):
        t = (b / branches + hash3(x, b, z, seed + 1) * 0.2) * math.pi * 2
        by = y0 + h - 2 - int(hash3(b, x, z, seed + 2) * 3)
        length = 3 + int(hash3(z, b, x, seed + 3) * 2)
        bx, bz, yy = x, z, by
        for i in range(1, length + 1):
            bx = int(round(x + w / 2 - 0.5 + math.cos(t) * i))
            bz = int(round(z + w / 2 - 0.5 + math.sin(t) * i))
            yy = by + i // 2
            kit.log(g, bx, yy, bz, log, 'x' if abs(math.cos(t)) > abs(math.sin(t)) else 'z')
        ends.append((bx, yy, bz))
        # A clump of leaves on each limb's end.
        for dx in range(-2, 3):
            for dy in range(-1, 3):
                for dz in range(-2, 3):
                    if dx * dx + dz * dz + dy * dy * 1.6 <= 5.5 and hash3(bx + dx, yy + dy, bz + dz, seed + 4) < 0.9:
                        kit.leaves(g, bx + dx, yy + dy, bz + dz, leaves)
    K.canopy(g, x + w / 2 - 0.5, z + w / 2 - 0.5, y0 + h - 1, r, leaves, seed=seed, h=4)
    return ends


def menhir(g, x, z, y0, h, mats, seed=0):
    """A standing stone: a broad foot, a tapering shaft leaning a little, moss in its cracks.
    Returns the cell on its top."""
    lean = (hash3(x, 1, z, seed) - 0.5) * 1.2
    for k in range(h):
        off = int(round(lean * k / max(1, h)))
        st = pick(mats, x, y0 + k, z, seed)
        g.set(x + off, y0 + k, z, st)
        if k < h // 2:
            for (dx, dz) in ((1, 0), (0, 1)):
                if hash3(x + dx, y0 + k, z + dz, seed + 1) < 0.7 - k * 0.15:
                    g.set(x + off + dx, y0 + k, z + dz, pick(mats, x + dx, y0 + k, z + dz, seed + 2))
    for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        if hash3(x + dx, y0, z + dz, seed + 3) < 0.5 and K.empty(g, x + dx, y0, z + dz):
            g.set(x + dx, y0, z + dz, 'mossy_cobblestone_slab[type=bottom]')
    off = int(round(lean * (h - 1) / max(1, h)))
    return (x + off, y0 + h, z)


def stepping(g, pts, y, mats, seed=0, gap=0.25, width=1):
    """Stones set into the ground (layer y) along a line, now and then a gap of earth."""
    for (a, b) in zip(pts, pts[1:]):
        n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))) + 1
        for i in range(n):
            t = i / max(1, n - 1)
            x = int(round(a[0] + (b[0] - a[0]) * t))
            z = int(round(a[1] + (b[1] - a[1]) * t))
            for w in range(-(width // 2), width - width // 2):
                for (px, pz) in ((x + w, z), (x, z + w)):
                    if hash3(px, y, pz, seed) > gap:
                        g.set(px, y, pz, pick(mats, px, y, pz, seed + 1))


def hang_moss(g, x, y_top, z, length, pale=True):
    """Moss hanging in a beard from (x, y_top) down."""
    name = 'pale_hanging_moss' if pale else 'hanging_roots'
    for k in range(length):
        y = y_top - k
        if not K.empty(g, x, y, z):
            return
        if pale:
            g.set(x, y, z, f'pale_hanging_moss[tip={"true" if k == length - 1 else "false"}]')
        else:
            g.set(x, y, z, 'hanging_roots[waterlogged=false]')


def stool(g, x, y, z, wood='spruce'):
    g.set(x, y, z, f'stripped_{wood}_log[axis=y]' if wood not in ('crimson', 'warped') else f'stripped_{wood}_stem[axis=y]')


# ================================================================ Moonlit Clearing (duskpine weald)

def moonlit_clearing():
    """A fae glade in the pines. A ring of six old standing stones, each with a cold candle on the
    offering stone at its foot, guards a round moon-dais of pale stone with an altar at its heart; mushrooms make a
    fairy ring beyond them, and the path comes in through a round moon-gate. A still pool keeps the
    moon, a leaf-roofed pavilion keeps the fae's bargain, and great dark oaks lean in with soul
    lanterns in their limbs. Light every candle and the dais gives up the gift sealed under it."""
    G = 3
    g = K.grid(48, 30, 48)
    c = 24
    d = dist(g, c, c)

    # The clearing's floor: moss, podzol and earth worked into the grass.
    ground(g, 2, 2, 45, 45, G - 1, [('moss_block', 5), ('coarse_dirt', 1), ('rooted_dirt', 1)], 0.4, seed=3,
           keep=lambda x, z: math.hypot(x - c, z - c) < 7.5)

    # The moon-dais: two round tiers of pale stone, a slab step round each.
    disc(g, c, c, G - 1, 6.7, 'stone_bricks')
    disc(g, c, c, G, 5.4, 'polished_diorite')
    disc(g, c, c, G, 5.4, 'calcite', inner=4.5)
    disc(g, c, c, G, 6.45, 'polished_diorite_slab[type=bottom]', inner=5.4)
    disc(g, c, c, G + 1, 3.4, 'quartz_bricks')
    disc(g, c, c, G + 1, 3.4, 'chiseled_quartz_block', inner=2.7)
    disc(g, c, c, G + 1, 4.45, 'quartz_slab[type=bottom]', inner=3.4)
    # Phases of the moon set into the lower tier, glowing faintly at night.
    for i, (x, z) in enumerate(around(c, c, 4.95, 8, 22.5)):
        g.set(x, G, z, 'pearlescent_froglight[axis=y]' if i % 2 == 0 else 'ochre_froglight[axis=y]')
    # The altar, with four moon-lamps round it.
    g.set(c, G + 2, c, 'chiseled_quartz_block')
    for (dx, dz) in ((2, 2), (2, -2), (-2, 2), (-2, -2)):
        g.set(c + dx, G + 2, c + dz, 'quartz_pillar[axis=y]')
        g.set(c + dx, G + 3, c + dz, 'end_rod[facing=up]')

    # Six standing stones round the dais, each with a cold candle on an offering stone at its foot,
    # in reach of a torch: the seal's lights.
    lights = []
    stones = [('mossy_stone_bricks', 3), ('mossy_cobblestone', 2), ('stone_bricks', 1), ('cracked_stone_bricks', 1), ('tuff', 1)]
    for i, (x, z) in enumerate(around(c, c, 12, 6, 15)):
        top = menhir(g, x, z, G, 5 + i % 2, stones, seed=i + 1)
        g.set(top[0], top[1] - 1, top[2], 'chiseled_stone_bricks')
        g.set(*top, 'moss_carpet')
        ux, uz = c - x, c - z
        fx, fz = (int(math.copysign(1, ux)), 0) if abs(ux) >= abs(uz) else (0, int(math.copysign(1, uz)))
        px, pz = x + 2 * fx, z + 2 * fz
        g.set(px, G, pz, 'mossy_stone_bricks')
        g.set(px, G + 1, pz, 'chiseled_stone_bricks')
        K.candles(g, px, G + 2, pz, n=1 + i % 3, lit=False, color='white')
        lights.append((px, G + 2, pz))
        # Offerings beside it.
        ox, oz = px + fz, pz + fx
        if K.empty(g, ox, G, oz):
            g.set(ox, G, oz, ['red_mushroom', 'brown_mushroom', 'potted_lily_of_the_valley', 'candle[candles=2,lit=false,waterlogged=false]', 'potted_azure_bluet', 'brown_mushroom'][i])

    # The gift, under the altar's floor: the gate stone lifts away when the seal turns.
    g.set(c, G, c + 2, 'air')
    g.chest(c, G, c + 2, 'north', GLYPH)
    g.set(c, G + 1, c + 2, 'chiseled_quartz_block')
    g.fill(c - 1, G - 1, c + 1, c + 1, G - 1, c + 3, 'stone_bricks')
    seal(g, c, G + 3, c, 'light', 'fae_candles', lights, [(c, G + 1, c + 2)])

    # The fairy ring.
    for i, (x, z) in enumerate(around(c, c, 16.5, 52)):
        if K.empty(g, x, G, z):
            g.set(x, G, z, 'red_mushroom' if i % 3 else 'brown_mushroom')

    # The moon-gate on the south path: a round arch of mossy stone, a soul lantern in its crown.
    gz = 42
    for x in range(c - 5, c + 6):
        for y in range(G, G + 9):
            r = math.hypot(x - c, (y - (G + 3)) * 1.0)
            if 3.0 <= r <= 4.1 and y >= G:
                for z in (gz, gz + 1):
                    g.set(x, y, z, pick([('mossy_stone_bricks', 3), ('stone_bricks', 2), ('cracked_stone_bricks', 1)], x, y, z, 11))
    for z in (gz, gz + 1):
        g.set(c, G + 7, z, 'chiseled_stone_bricks')
    kit.hang_lantern(g, c, G + 6, gz, 1, soul=True)
    for x in (c - 5, c + 5):
        for z in (gz, gz + 1):
            g.set(x, G, z, 'mossy_stone_brick_slab[type=bottom]')
    hang_moss(g, c - 2, G + 5, gz + 1, 2)
    hang_moss(g, c + 2, G + 5, gz, 3)
    # The path, stepping stones in from the gate, and branches to the pool and the pavilion.
    path_mats = [('mossy_stone_bricks', 3), ('polished_andesite', 2), ('stone_bricks', 2), ('mossy_cobblestone', 1)]
    stepping(g, [(c, 47), (c, gz + 2), (c, 31)], G - 1, path_mats, seed=4, gap=0.18, width=2)
    stepping(g, [(c + 6, 28), (31, 30), (34, 31)], G - 1, path_mats, seed=5, gap=0.35)
    stepping(g, [(c - 6, 21), (17, 19), (14, 17)], G - 1, path_mats, seed=6, gap=0.35)
    for x in (c - 2, c + 2):
        for z in range(31, 47, 3):
            if K.empty(g, x, G, z):
                g.set(x, G, z, 'moss_carpet')

    # The moon pool: still water two deep, a mossy rim, lilies, and pickles glowing on its bed.
    px, pz = 35, 31
    pd = dist(g, px, pz)
    g.a[:, G - 1, :][pd <= 3.6] = g.pid('water[level=0]')
    g.a[:, G - 2, :][pd <= 2.4] = g.pid('water[level=0]')
    g.a[:, G - 3, :][pd <= 2.4] = g.pid('mud')
    g.a[:, G - 2, :][(pd > 2.4) & (pd <= 3.6)] = g.pid('mud')
    for (x, z) in np.argwhere((pd > 3.6) & (pd <= 4.7)):
        g.set(x, G - 1, z, pick([('mossy_cobblestone', 3), ('stone', 2), ('moss_block', 2)], x, G - 1, z, 7))
        if hash3(x, G, z, 8) < 0.35:
            g.set(x, G, z, pick([('mossy_cobblestone_slab[type=bottom]', 2), ('moss_carpet', 3), ('fern', 1)], x, G, z, 9))
    for (x, z) in ((px - 1, pz), (px + 1, pz + 1), (px, pz - 2)):
        g.set(x, G - 3, z, 'mud')
        g.set(x, G - 2, z, 'sea_pickle[pickles=3,waterlogged=true]')
    for (x, z) in ((px + 2, pz - 1), (px - 2, pz + 2), (px + 1, pz + 2)):
        g.set(x, G, z, 'lily_pad')

    # The pavilion: four pale-oak posts under a roof of flowering leaves; a table, stools, the note.
    vx, vz = 12, 15
    g.fill(vx - 3, G - 1, vz - 3, vx + 3, G - 1, vz + 3, 'pale_oak_planks')
    for (x, z) in ((vx - 3, vz - 3), (vx + 3, vz - 3), (vx - 3, vz + 3), (vx + 3, vz + 3)):
        for k in range(4):
            kit.log(g, x, G + k, z, 'pale_oak_log')
    for x in range(vx - 3, vx + 4):
        for z in (vz - 3, vz + 3):
            kit.log(g, x, G + 4, z, 'stripped_pale_oak_log', 'x')
    for z in range(vz - 2, vz + 3):
        for x in (vx - 3, vx + 3):
            kit.log(g, x, G + 4, z, 'stripped_pale_oak_log', 'z')
    for y in range(G + 5, G + 8):
        rr = 4.6 - (y - G - 5) * 1.5
        for x in range(vx - 5, vx + 6):
            for z in range(vz - 5, vz + 6):
                if math.hypot(x - vx, z - vz) <= rr and hash3(x, y, z, 12) < 0.93:
                    kit.leaves(g, x, y, z, 'flowering_azalea_leaves' if hash3(x, y, z, 13) < 0.4 else 'azalea_leaves')
    for (x, z) in ((vx - 4, vz - 1), (vx + 4, vz + 2), (vx - 1, vz + 4), (vx + 2, vz - 4)):
        hang_moss(g, x, G + 4, z, 2)
    kit.table(g, vx, G, vz, top='pale_oak_pressure_plate', leg='pale_oak_fence')
    for (x, z) in ((vx - 1, vz), (vx + 1, vz), (vx, vz + 1)):
        stool(g, x, G, z, 'pale_oak')
    g.set(vx, G + 1, vz, 'potted_closed_eyeblossom')
    g.set(vx - 2, G, vz - 2, 'chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=false,slot_2_occupied=true,slot_3_occupied=true,slot_4_occupied=false,slot_5_occupied=true]')
    K.candles(g, vx + 2, G, vz - 2, 3, lit=True, color='white')
    clue(g, vx, G, vz - 1, 'note', 'fae_bargain')
    kit.lantern(g, vx + 2, G + 3, vz + 2, hanging=True, soul=True)

    # Old dark oaks lean in from the edges, soul lanterns hung in their limbs; pines beyond.
    for k, (x, z, h) in enumerate(((5, 6, 9), (41, 7, 10), (6, 40, 8), (42, 40, 9))):
        ends = oak(g, x, z, G, h, 5, seed=20 + k, wide=k % 2 == 0)
        for j, (ex, ey, ez) in enumerate(ends[:2]):
            if inside(g, ex, ez, 1):
                kit.hang_lantern(g, ex, ey - 1, ez, 1 + j % 2, soul=True)
        for j, (ex, ey, ez) in enumerate(ends[2:]):
            if inside(g, ex, ez, 1):
                hang_moss(g, ex, ey - 2, ez, 3 + j % 2)
    for k, (x, z) in enumerate(((2, 22), (45, 24), (16, 2), (31, 1), (13, 45), (35, 45), (45, 14), (1, 31))):
        pine(g, x, z, G, 9 + k % 3, seed=40 + k)

    # Flowers, ferns, fireflies and leaf litter in the grass; moss on the stones.
    plants(g, 1, 1, 46, 46, G, FLOWERS_WEALD, 0.24, seed=14)
    K.top_scatter(g, 0, 0, 47, 47, ['moss_carpet'], 0.2, seed=15, on={'mossy_stone_bricks', 'mossy_cobblestone', 'stone_bricks'})

    elite(g, c + 8, G, c - 9, 'dusk_hound')
    mob(g, c - 10, G, c - 6, 'kitsune')
    return K.finish(g)


def dead_limbs(g, x, z, y0, h, log, seed=0, limbs=4):
    """A dead, twisted tree: a trunk that kinks as it rises, and bare limbs that fork. Returns the
    limb tips (for lanterns and moss)."""
    tips = []
    px, pz = x, z
    for k in range(h):
        if k > 2 and hash3(x, k, z, seed) < 0.22:
            px += 1 if hash3(z, k, x, seed + 1) < 0.5 else -1
        if k > 3 and hash3(x, k, z, seed + 2) < 0.18:
            pz += 1 if hash3(k, x, z, seed + 3) < 0.5 else -1
        kit.log(g, px, y0 + k, pz, log)
    for b in range(limbs):
        t = (b / limbs + hash3(x, b, z, seed + 4) * 0.25) * math.pi * 2
        by = y0 + h - 1 - int(hash3(b, x, z, seed + 5) * (h // 2))
        length = 3 + int(hash3(z, b, x, seed + 6) * 3)
        bx, bz, yy = px, pz, by
        for i in range(1, length + 1):
            bx = int(round(px + math.cos(t) * i))
            bz = int(round(pz + math.sin(t) * i))
            yy = by + (i + 1) // 2
            kit.log(g, bx, yy, bz, log, 'x' if abs(math.cos(t)) > abs(math.sin(t)) else 'z')
        tips.append((bx, yy, bz))
        # A fork near the end.
        if hash3(b, z, x, seed + 7) < 0.6:
            fx, fz = int(round(bx + math.cos(t + 0.9))), int(round(bz + math.sin(t + 0.9)))
            kit.log(g, fx, yy + 1, fz, log)
            tips.append((fx, yy + 1, fz))
    return tips


def headstone(g, x, z, y, facing, seed=0):
    """A worn grave: a headstone with a rounded top, the mound of earth before it. Returns the
    cell on the headstone's top."""
    dx, dz = kit.FACE[facing]
    g.set(x, y, z, pick([('mossy_stone_bricks', 2), ('cracked_stone_bricks', 1), ('stone_bricks', 1)], x, y, z, seed))
    g.set(x, y + 1, z, f'mossy_stone_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]' if hash3(x, y, z, seed + 1) < 0.5
          else 'chiseled_stone_bricks')
    for k in (1, 2):
        g.set(x + dx * k, y - 1, z + dz * k, pick([('podzol[snowy=false]', 2), ('coarse_dirt', 2), ('rooted_dirt', 1)], x + dx * k, y, z + dz * k, seed + 2))
        if hash3(x + dx * k, y, z + dz * k, seed + 3) < 0.5:
            g.set(x + dx * k, y, z + dz * k, pick([('poppy', 1), ('closed_eyeblossom', 2), ('fern', 1), ('azure_bluet', 1)], x, y, z + k, seed + 4))
    return (x, y + 2, z)


# ================================================================ The Hanged Lantern Grove (gloomwald)

def hanged_lanterns():
    """A dead grove where the lantern-makers hung a light for each who never came home. Twisted
    black trees hold soul lanterns on chains in every limb; at the grove's heart an iron-fenced
    yard keeps five graves, each with a cold candle on its stone, round the greatest lantern tree;
    a ruined chapel stands at the yard's head and the lantern-maker's cart rots by the well. Light
    the five candles and the stone at the tree's foot rolls back."""
    G = 3
    g = K.grid(48, 32, 48)
    c = 24
    ground(g, 0, 0, 47, 47, G - 1, [('podzol[snowy=false]', 3), ('coarse_dirt', 2), ('rooted_dirt', 2), ('mud', 1), ('moss_block', 1)], 0.55, seed=31)

    # The grove: nine twisted trees round the yard, soul lanterns on chains in their limbs.
    for k, (x, z) in enumerate(around(c, c, 17.5, 9, 8)):
        x += int(hash3(k, 0, 1, 33) * 3) - 1
        z += int(hash3(k, 0, 2, 33) * 3) - 1
        tips = dead_limbs(g, x, z, G, 8 + k % 4, 'dark_oak_log' if k % 3 else 'pale_oak_log', seed=40 + k, limbs=4)
        for j, (tx, ty, tz) in enumerate(tips):
            if not inside(g, tx, tz, 1):
                continue
            if j % 2 == 0:
                kit.hang_lantern(g, tx, ty - 1, tz, 1 + (j + k) % 3, soul=True)
            elif hash3(tx, ty, tz, 34) < 0.7:
                hang_moss(g, tx, ty - 1, tz, 2 + (j + k) % 3)
        if hash3(x, 0, z, 35) < 0.6:
            g.set(x + 1, G + 2, z, 'cobweb')

    # The yard: an iron fence on a stone kerb, a lych-gate to the south.
    y0, y1 = 14, 34
    x0, x1 = 14, 34
    for x in range(x0, x1 + 1):
        for z in range(y0, y1 + 1):
            if x in (x0, x1) or z in (y0, y1):
                g.set(x, G - 1, z, pick([('stone_bricks', 2), ('mossy_stone_bricks', 2), ('cracked_stone_bricks', 1)], x, G, z, 36))
                if (x == x0 or x == x1) and (z - y0) % 4 == 0 or (z == y0 or z == y1) and (x - x0) % 4 == 0:
                    g.set(x, G, z, 'mossy_stone_bricks')
                    g.set(x, G + 1, z, 'mossy_stone_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
                elif not (z == y1 and c - 2 <= x <= c + 2):
                    g.set(x, G, z, 'iron_bars[north=false,south=false,east=false,west=false,waterlogged=false]')
                    if hash3(x, G + 1, z, 37) < 0.75:
                        g.set(x, G + 1, z, 'iron_bars[north=false,south=false,east=false,west=false,waterlogged=false]')
    # The lych-gate: two posts, a little gabled roof, a lantern under it.
    for x in (c - 3, c + 3):
        for k in range(4):
            kit.log(g, x, G + k, y1, 'dark_oak_log')
    for x in range(c - 4, c + 5):
        kit.stairs(g, x, G + 4, y1 - 1, 'dark_oak', 'north')
        kit.stairs(g, x, G + 4, y1 + 1, 'dark_oak', 'south')
        g.set(x, G + 4, y1, 'dark_oak_planks')
        kit.slab(g, x, G + 5, y1, 'dark_oak')
    kit.hang_lantern(g, c, G + 3, y1, 0, soul=True)
    # The path in, cracked flags between the gate and the tree.
    stepping(g, [(c, 47), (c, y1), (c, c + 4)], G - 1, [('cobblestone', 2), ('mossy_cobblestone', 2), ('gravel', 1), ('stone_bricks', 1)], seed=38, gap=0.15, width=3)

    # The five graves in an arc north of the tree, their stones facing the path.
    lights = []
    for i, (x, z) in enumerate(around(c, c - 1, 7, 5, 200)):
        top = headstone(g, x, z, G, 'south' if z < c else 'north', seed=50 + i)
        K.candles(g, *top, n=1 + i % 2, lit=False, color='gray')
        lights.append(top)
    # Older graves round the yard's edge, unlit.
    for i, (x, z) in enumerate(((x0 + 3, y0 + 4), (x0 + 3, y0 + 9), (x1 - 3, y0 + 5), (x1 - 3, y0 + 11), (x0 + 3, y1 - 5), (x1 - 3, y1 - 4))):
        headstone(g, x, z, G, 'east' if x < c else 'west', seed=60 + i)
        if i % 2 == 0:
            K.skull(g, x + (1 if x < c else -1), G, z + 2, rot=4 * i)

    # The lantern tree, its roots round a stone dais where the seal stands.
    tips = dead_limbs(g, c, c - 6, G, 13, 'dark_oak_log', seed=70, limbs=6)
    for j, (tx, ty, tz) in enumerate(tips):
        kit.hang_lantern(g, tx, ty - 1, tz, 1 + j % 3, soul=j % 3 != 1)
    disc(g, c, c + 1, G - 1, 2.5, 'mossy_stone_bricks')
    g.set(c, G, c + 1, 'mossy_stone_bricks')
    seal(g, c, G + 1, c + 1, 'light', 'grove_candles', lights, [(c, G, c + 3)])
    g.set(c, G, c + 3, 'mossy_cobblestone')
    g.fill(c - 1, G - 2, c + 2, c + 1, G - 2, c + 4, 'stone')
    g.set(c, G - 1, c + 3, 'air')
    g.chest(c, G - 1, c + 3, 'north', fl(VAULT, 'gloom'))

    # The chapel at the yard's head, its roof half fallen in.
    P = PAL['mossy']
    cx0, cz0, cx1, cz1 = c - 5, 4, c + 5, 12
    K.room(g, cx0, cz0, cx1, cz1, G, 5, P, door=(c, cz1), decay=0.25, seed=71, windows=True)
    for z in range(cz0, cz1 + 1):
        for x in (cx0, cx1):
            if (z - cz0) % 4 == 0:
                for k in range(5):
                    g.set(x, G + k, z, 'mossy_stone_bricks')
    for x in range(cx0, cx1 + 1):
        for z in range(cz0 - 1, cz1 + 2):
            off = abs(x - c)
            y = G + 5 + (5 - off)
            if hash3(x, y, z, 72) < (0.85 if z < 8 else 0.35):
                kit.stairs(g, x, y, z, 'deepslate_tile', 'east' if x < c else 'west') if off else g.set(x, y, z, 'deepslate_tiles')
    g.fill(cx0 + 1, G - 1, cz0 + 1, cx1 - 1, G - 1, cz1 - 1, 'mossy_stone_bricks')
    for z in range(cz0 + 4, cz1 - 1, 2):
        for x in (c - 3, c - 2, c + 2, c + 3):
            kit.stairs(g, x, G, z, 'dark_oak', 'south')
    g.fill(c - 1, G, cz0 + 1, c + 1, G, cz0 + 1, 'chiseled_stone_bricks')
    K.candles(g, c - 1, G + 1, cz0 + 1, 3, lit=True, color='white')
    K.candles(g, c + 1, G + 1, cz0 + 1, 2, lit=True, color='white')
    g.set(c, G + 1, cz0 + 1, 'lectern[facing=south,has_book=true,powered=false]')
    clue(g, c, G, cz0 + 3, 'note', 'lantern_grove')
    for x in (cx0 + 1, cx1 - 1):
        kit.lantern(g, x, G + 3, cz0 + 2, hanging=False, soul=True)
    for (x, z) in ((cx0 + 1, cz1 - 1), (cx1 - 1, cz1 - 2), (c + 3, cz0 + 2)):
        g.set(x, G + 2, z, 'cobweb')

    # The lantern-maker's cart and the well, outside the fence.
    K.wagon(g, 6, 30, G, 'z', wood='dark_oak', cover='gray', broken=True, seed=73)
    for (x, z) in ((8, 29), (8, 33), (5, 35)):
        kit.lantern(g, x, G, z, soul=hash3(x, 0, z, 74) < 0.5)
    K.barrels(g, 4, G, 27, seed=75, n=3)
    g.set(3, G, 31, 'iron_chain[axis=y]')
    wx, wz = 40, 26
    disc(g, wx, wz, G - 1, 1.6, 'cobblestone')
    for (x, z) in np.argwhere((dist(g, wx, wz) <= 1.6) & (dist(g, wx, wz) > 0.6)):
        g.set(x, G, z, 'mossy_cobblestone')
    g.set(wx, G - 1, wz, 'water[level=0]')
    g.set(wx, G - 2, wz, 'water[level=0]')
    for (x, z) in ((wx - 1, wz), (wx + 1, wz)):
        g.set(x, G + 1, z, 'dark_oak_fence')
        g.set(x, G + 2, z, 'dark_oak_fence')
    for x in range(wx - 1, wx + 2):
        kit.log(g, x, G + 3, wz, 'stripped_dark_oak_log', 'x')
    kit.hang_lantern(g, wx, G + 2, wz, 0, soul=False)

    # Bones, dead leaves, eyeblossoms and cobwebs in the grass.
    plants(g, 0, 0, 47, 47, G, [('leaf_litter[facing=north,segment_amount=4]', 4), ('leaf_litter[facing=east,segment_amount=2]', 3), ('dead_bush', 2),
                                  ('closed_eyeblossom', 2), ('fern', 2), ('short_grass', 2), ('brown_mushroom', 1), ('bone_block[axis=x]', 1)], 0.22, seed=39)
    mob(g, c - 8, G, c + 7, 'mourning_shade', 2)
    return K.finish(g)


# ================================================================ The Druids' Ring (mossmere wood)

def druids_ring():
    """A great henge in the mossy wood: an outer ring of sarsens bridged by lintels, a horseshoe of
    five tall trilithons inside it, and four flame-stones round the altar, each with one to four
    green flames on its brow and a stone button on its inner face. Strike them as the flames count
    and the altar gives up its gift. A sacred oak shades the ring and the druid's thatched
    roundhouse stands among its roots."""
    G = 3
    g = K.grid(48, 32, 48)
    c = 24
    ground(g, 0, 0, 47, 47, G - 1, [('moss_block', 5), ('rooted_dirt', 1), ('podzol[snowy=false]', 1)], 0.6, seed=81)
    sarsen = [('mossy_stone_bricks', 3), ('mossy_cobblestone', 2), ('stone', 2), ('andesite', 1), ('tuff', 1)]

    # The outer ring: twelve sarsens, their tops bridged by a lintel ring.
    ring = around(c, c, 17.5, 24)
    for i, (x, z) in enumerate(ring):
        if i % 2 == 0:
            for k in range(5):
                g.set(x, G + k, z, pick(sarsen, x, G + k, z, 82))
            for (dx, dz) in ((1, 0), (0, 1)):
                if hash3(x + dx, 0, z + dz, 83) < 0.5:
                    for k in range(3):
                        g.set(x + dx, G + k, z + dz, pick(sarsen, x + dx, G + k, z + dz, 84))
    for i in range(len(ring)):
        (x, z) = ring[i]
        if hash3(i, 0, 0, 85) < 0.82:
            g.set(x, G + 5, z, 'mossy_stone_bricks' if i % 3 else 'stone_bricks')
    # Gaps, and fallen stones lying in the grass.
    for (x, z, axis) in ((c + 12, c - 13, 'x'), (c - 15, c + 9, 'z')):
        for k in range(4):
            g.set(x + (k if axis == 'x' else 0), G, z + (k if axis == 'z' else 0), pick(sarsen, x + k, G, z, 86))

    # The horseshoe: five trilithons, open to the north-east where the sun rises.
    for i, a in enumerate((90, 135, 180, 225, 270)):
        t = math.radians(a + 45)
        ux, uz = math.cos(t), math.sin(t)
        px, pz = -uz, ux
        mx, mz = c + ux * 10, c + uz * 10
        for s in (-1.4, 1.4):
            x, z = int(round(mx + px * s)), int(round(mz + pz * s))
            for k in range(7):
                g.set(x, G + k, z, pick(sarsen, x, G + k, z, 87 + i))
                g.set(x + (1 if abs(ux) < 0.7 else 0), G + k, z + (1 if abs(ux) >= 0.7 else 0), pick(sarsen, x, G + k, z + 1, 88 + i))
        for s in (-2, -1, 0, 1, 2):
            x, z = int(round(mx + px * s)), int(round(mz + pz * s))
            g.set(x, G + 7, z, 'stone_bricks' if abs(s) < 2 else 'mossy_stone_bricks')

    # The four flame-stones, flames counting one to four, a button on each inner face.
    posts = {}
    counts = [3, 1, 4, 2]
    for i, a in enumerate((0, 90, 180, 270)):
        t = math.radians(a)
        x, z = int(round(c + math.cos(t) * 6.5)), int(round(c + math.sin(t) * 6.5))
        top = menhir(g, x, z, G, 4, sarsen, seed=90 + i)
        n = counts[i]
        g.set(top[0], top[1] - 1, top[2], 'chiseled_stone_bricks')
        K.candles(g, *top, n=n, lit=True, color='green')
        fx = 0 if abs(math.cos(t)) < 0.5 else (-1 if math.cos(t) > 0 else 1)
        fz = 0 if abs(math.sin(t)) < 0.5 else (-1 if math.sin(t) > 0 else 1)
        face = {(-1, 0): 'west', (1, 0): 'east', (0, -1): 'north', (0, 1): 'south'}[(fx, fz)]
        bx, bz = x + fx, z + fz
        g.set(bx - fx, G + 1, bz - fz, pick(sarsen, x, G + 1, z, 91))
        K.button(g, bx, G + 1, bz, face)
        posts[n] = (bx, G + 1, bz)
    order = [posts[n] for n in sorted(posts)]

    # The altar: a long flat stone on two feet, the seal at its head; the gift beneath its foot.
    disc(g, c, c, G - 1, 3.6, 'smooth_stone')
    disc(g, c, c, G - 1, 3.6, 'mossy_stone_bricks', inner=2.6)
    g.fill(c - 1, G, c - 2, c + 1, G, c + 2, 'mossy_stone_bricks')
    g.fill(c - 1, G + 1, c - 2, c + 1, G + 1, c + 2, 'smooth_stone_slab[type=bottom]')
    for (x, z) in ((c - 1, c - 2), (c + 1, c - 2), (c - 1, c + 2), (c + 1, c + 2)):
        g.set(x, G + 1, z, 'smooth_stone')
    # The gift lies in the altar's foot, behind a carved stone that slides away (with the slab over
    # it) when the stones are struck in order.
    seal(g, c, G + 2, c - 1, 'order', 'druid_stones', order, [(c, G, c + 2), (c, G + 1, c + 2)])
    g.set(c, G, c + 2, 'chiseled_stone_bricks')
    g.chest(c, G, c + 1, 'south', 'afterworld:chests/site_edict')
    g.fill(c - 1, G - 1, c, c + 1, G - 1, c + 2, 'stone')
    clue(g, c + 2, G, c - 3, 'glyph', 'druid_rite')
    for (x, z) in ((c - 2, c + 2), (c + 2, c + 2)):
        g.set(x, G, z, 'potted_fern')

    # The sacred oak in the north-east, its roots across the grass.
    ends = oak(g, 37, 9, G, 11, 7, log='oak_log', leaves='oak_leaves', seed=92, branches=6, wide=True)
    for j, (ex, ey, ez) in enumerate(ends):
        if j % 2 == 0 and inside(g, ex, ez, 1):
            kit.hang_lantern(g, ex, ey - 1, ez, 1, soul=False)
    for k in range(6):
        t = k * math.pi / 3 + 0.3
        for i in range(2, 6):
            x, z = int(round(37.5 + math.cos(t) * i)), int(round(9.5 + math.sin(t) * i))
            if inside(g, x, z) and K.empty(g, x, G, z):
                kit.log(g, x, G - 1 if i > 3 else G, z, 'oak_log', 'x' if abs(math.cos(t)) > 0.7 else 'z')
    mob(g, 38, G + 12, 12, 'mossmere_owl')

    # The druid's roundhouse in the south-west: wattle walls, a thatched cone, herbs and a cauldron.
    hx, hz, hr = 9, 36, 4
    hd = dist(g, hx, hz)
    wall = (hd <= hr + 0.35) & (hd > hr - 0.85)
    g.a[:, G - 1, :][hd <= hr + 0.35] = g.pid('spruce_planks')
    for y in range(G, G + 3):
        g.a[:, y, :][wall] = g.pid('stripped_spruce_log[axis=y]' if y == G else 'mud_bricks')
        g.a[:, y, :][hd <= hr - 0.85] = g.pid('air')
    for i in range(hr + 3):
        rr = hr + 1.4 - i
        band = (hd <= rr + 0.35) & (hd > rr - 0.85) if rr > 0.9 else (hd <= 0.9)
        g.a[:, G + 3 + i, :][band] = g.pid('hay_block[axis=y]')
    g.fill(hx, G, hz - hr, hx, G + 1, hz - hr, 'air')
    kit.door(g, hx, G, hz - hr, 'spruce', 'north')
    kit.hang_lantern(g, hx - 1, G + 2, hz - hr - 1, 0)
    for (x, z) in ((hx + hr, hz), (hx - hr, hz), (hx, hz + hr)):
        g.set(x, G + 1, z, 'glass_pane')
    g.set(hx, G, hz, 'cauldron')
    g.set(hx + 2, G, hz + 1, 'barrel[facing=up,open=false]')
    g.set(hx + 2, G, hz + 2, 'composter[level=4]')
    g.set(hx - 2, G, hz + 2, 'red_bed[facing=north,part=foot,occupied=false]')
    g.set(hx - 2, G, hz + 1, 'red_bed[facing=north,part=head,occupied=false]')
    for (x, z) in ((hx - 3, hz), (hx + 3, hz - 1)):
        g.set(x, G, z, 'flower_pot')
        g.set(x, G + 1, z, 'potted_red_mushroom')
    g.set(hx, G + 2, hz, 'iron_chain[axis=y]')
    kit.lantern(g, hx + 1, G + 2, hz - 2, hanging=True)
    g.set(hx - 2, G, hz - 2, 'loom[facing=east]')
    # A herb garden fenced in beside it.
    for x in range(hx + 5, hx + 11):
        for z in range(hz - 2, hz + 4):
            if x in (hx + 5, hx + 10) or z in (hz - 2, hz + 3):
                g.set(x, G, z, 'spruce_fence')
            else:
                g.set(x, G - 1, z, 'farmland[moisture=7]')
                g.set(x, G, z, pick([('carrots[age=7]', 2), ('beetroots[age=3]', 2), ('potatoes[age=7]', 1), ('sweet_berry_bush[age=3]', 1)], x, G, z, 93))
    g.set(hx + 5, G, hz + 1, 'spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]')

    # A spring in the east: water falling from a rock into a stone basin.
    sx, sz = 41, 30
    sd = dist(g, sx, sz)
    g.a[:, G - 1, :][sd <= 2.8] = g.pid('water[level=0]')
    g.a[:, G - 2, :][sd <= 2.0] = g.pid('water[level=0]')
    g.a[:, G - 3, :][sd <= 2.0] = g.pid('gravel')
    for (x, z) in np.argwhere((sd > 2.8) & (sd <= 3.8)):
        g.set(x, G - 1, z, pick([('mossy_cobblestone', 2), ('stone', 2), ('moss_block', 1)], x, G, z, 94))
        if hash3(x, G, z, 95) < 0.4:
            g.set(x, G, z, 'mossy_cobblestone_slab[type=bottom]')
    # The rock the spring runs from, its water falling into the basin.
    for k in range(3):
        g.set(sx + 3, G + k, sz - 1, 'mossy_cobblestone')
    g.set(sx + 3, G + 3, sz - 1, 'moss_block')
    g.set(sx + 2, G + 2, sz - 1, 'water[level=0]')
    for (x, z) in ((sx - 1, sz + 1), (sx + 1, sz - 1)):
        g.set(x, G, z, 'lily_pad')

    # Paths worn between the stones; moss and flowers everywhere else.
    stepping(g, [(c, 47), (c, c + 4)], G - 1, [('mossy_cobblestone', 2), ('moss_block', 2), ('mossy_stone_bricks', 1)], seed=96, gap=0.3, width=2)
    stepping(g, [(c - 4, c + 4), (hx + 2, hz - hr - 1)], G - 1, [('mossy_cobblestone', 2), ('moss_block', 2)], seed=97, gap=0.35)
    for k, (x, z) in enumerate(((3, 4), (5, 19), (43, 43), (30, 45), (45, 18), (17, 44), (2, 46))):
        oak(g, x, z, G, 6 + k % 3, 3 + k % 2, log='oak_log', leaves='oak_leaves', seed=100 + k, branches=3)
    plants(g, 0, 0, 47, 47, G, [('moss_carpet', 6), ('fern', 3), ('short_grass', 3), ('wildflowers[facing=north,flower_amount=4]', 2),
                                  ('azure_bluet', 1), ('oxeye_daisy', 1), ('red_mushroom', 1), ('firefly_bush', 1)], 0.3, seed=98)
    K.top_scatter(g, 0, 0, 47, 47, ['moss_carpet'], 0.35, seed=99, on={'mossy_stone_bricks', 'mossy_cobblestone', 'stone', 'stone_bricks'})
    mob(g, c - 6, G, c + 13, 'rootling_sentinel')
    return K.finish(g)


# ================================================================ pieces for camps and posts

def log_cabin(g, x0, z0, x1, z1, y, wood='spruce', roof='spruce', door_side='south', door_at=None, rise=1, porch=True, chimney='west',
              snow=False, seed=0):
    """A log cabin on a stone footing: walls of round logs laid crosswise (their ends showing at the
    corners), windows with shutters and sills, a steep roof hanging over, a stone chimney, a porch
    on posts before the door. Returns the room inside (x0, z0, x1, z1, y) and the door cell."""
    logs = f'{wood}_log'
    g.fill(x0, y - 1, z0, x1, y - 1, z1, 'cobblestone')
    g.fill(x0 + 1, y - 1, z0 + 1, x1 - 1, y - 1, z1 - 1, f'{wood}_planks')
    for k in range(4):
        yy = y + k
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                kit.log(g, x, yy, z, logs, 'x')
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                kit.log(g, x, yy, z, logs, 'z')
        for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            kit.log(g, x, yy, z, logs, 'y')
    g.fill(x0 + 1, y, z0 + 1, x1 - 1, y + 3, z1 - 1, 'air')
    # Ceiling joists and a loft floor.
    for x in range(x0 + 1, x1):
        if (x - x0) % 2 == 0:
            for z in range(z0 + 1, z1):
                kit.log(g, x, y + 3, z, f'stripped_{wood}_log', 'z')
    # Windows: two high, shuttered, with a sill and a flower box now and then.
    if door_at is None:
        door_at = (x0 + x1) // 2 if door_side in ('north', 'south') else (z0 + z1) // 2
    dx, dz = kit.FACE[door_side]
    door_cell = (door_at, z0 if door_side == 'north' else z1) if door_side in ('north', 'south') else (x0 if door_side == 'west' else x1, door_at)
    for face in ('north', 'south', 'east', 'west'):
        fx, fz = kit.FACE[face]
        if face in ('north', 'south'):
            zz = z0 if face == 'north' else z1
            spots = [(x, zz) for x in range(x0 + 2, x1 - 1, 3)]
        else:
            xx = x0 if face == 'west' else x1
            spots = [(xx, z) for z in range(z0 + 2, z1 - 1, 3)]
        for (wx, wz) in spots:
            if abs(wx - door_cell[0]) + abs(wz - door_cell[1]) <= 1:
                continue
            g.set(wx, y + 1, wz, 'glass_pane')
            g.set(wx, y + 2, wz, 'glass_pane')
            kit.shutters(g, wx, y + 1, wz, face, wood, 2)
            kit.stairs(g, wx + fx, y, wz + fz, wood, kit.OPP[face], 'top')
            if hash3(wx, y, wz, seed) < 0.4:
                g.set(wx + fx, y + 1, wz + fz, 'flower_pot' if snow else pick([('potted_red_tulip', 1), ('potted_fern', 1), ('potted_blue_orchid', 1)], wx, y, wz, seed))
    kit.door(g, door_cell[0], y, door_cell[1], wood, door_side)
    # The roof: steep, overhanging, the gables boarded, a ridge cap.
    axis = 'x' if (x1 - x0) >= (z1 - z0) else 'z'
    top = kit.gable(g, x0, z0, x1, z1, y + 4, roof, axis=axis, overhang=1, gable_fill=f'{wood}_planks')
    if snow:
        kit.snow(g, depth=1, rate=1.0, seed=seed + 1)
    # The chimney.
    if chimney:
        cxx = x0 if chimney == 'west' else x1
        czz = (z0 + z1) // 2 + 1
        for k in range(y - 1, top + 2):
            g.set(cxx + (-1 if chimney == 'west' else 1), k, czz, 'cobblestone' if k < top else 'stone_bricks')
        g.set(cxx + (-1 if chimney == 'west' else 1), top + 2, czz, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
        g.set(cxx, y, czz, 'campfire[lit=true,facing=' + ('east' if chimney == 'west' else 'west') + ',signal_fire=false,waterlogged=false]')
    # The porch: a plank deck on posts, a little roof.
    if porch:
        px, pz = door_cell
        along = (1, 0) if door_side in ('north', 'south') else (0, 1)
        for w in range(-2, 3):
            for d in (1, 2):
                qx, qz = px + along[0] * w + dx * d, pz + along[1] * w + dz * d
                g.set(qx, y - 1, qz, f'{wood}_planks')
                if abs(w) == 2 and d == 2:
                    for k in range(3):
                        g.set(qx, y + k, qz, f'{wood}_fence')
                elif abs(w) == 2 or d == 2:
                    if not (w == 0 and d == 2):
                        g.set(qx, y, qz, f'{wood}_fence')
                kit.stairs(g, qx, y + 3, qz, roof, kit.OPP[door_side])
        kit.hang_lantern(g, px + along[0] * 1 + dx, y + 2, pz + along[1] * 1 + dz, 0)
    return (x0 + 1, z0 + 1, x1 - 1, z1 - 1, y), door_cell


def palisade(g, pts, y, h=4, wood='spruce', seed=0, gaps=()):
    """A stockade of upright logs along the polyline, every other one a step taller and sharpened."""
    cells = []
    for (a, b) in zip(pts, pts[1:]):
        n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1]))) + 1
        for i in range(n):
            t = i / max(1, n - 1)
            cells.append((int(round(a[0] + (b[0] - a[0]) * t)), int(round(a[1] + (b[1] - a[1]) * t))))
    cells = list(dict.fromkeys(cells))
    for i, (x, z) in enumerate(cells):
        if (x, z) in gaps:
            continue
        hh = h + (i % 2)
        g.set(x, y - 1, z, 'cobblestone')
        for k in range(hh):
            kit.log(g, x, y + k, z, f'{wood}_log')
        g.set(x, y + hh, z, f'{wood}_fence')
    return cells


def sledge(g, x, z, y, axis, wood='spruce', load=True, seed=0):
    """A sledge on runners: a plank bed, a curled front, crates and barrels lashed on top."""
    ax, az = (1, 0) if axis == 'x' else (0, 1)
    sx, sz = (0, 1) if axis == 'x' else (1, 0)
    for i in range(5):
        for w in (-1, 1):
            g.set(x + ax * i + sx * w, y, z + az * i + sz * w, f'{wood}_slab[type=bottom]')
        g.set(x + ax * i, y, z + az * i, f'{wood}_slab[type=bottom]')
    for w in (-1, 1):
        kit.stairs(g, x - ax + sx * w, y, z - az + sz * w, wood, 'west' if axis == 'x' else 'north', 'bottom')
    if load:
        for i in range(1, 5):
            r = hash3(x + i, y, z, seed)
            st = 'barrel[facing=up,open=false]' if r < 0.35 else ('spruce_planks' if r < 0.55 else ('hay_block[axis=y]' if r < 0.7 else 'white_wool'))
            g.set(x + ax * i, y + 1, z + az * i, st)
            if r < 0.3:
                g.set(x + ax * i, y + 2, z + az * i, 'white_carpet')
    g.set(x - 2 * ax, y, z - 2 * az, f'{wood}_fence')


def wood_stack(g, x, z, y, length, axis, wood='spruce', h=2):
    """Firewood stacked against the weather: logs crosswise, a slab roof on top."""
    ax, az = (1, 0) if axis == 'x' else (0, 1)
    for i in range(length):
        for k in range(h):
            kit.log(g, x + ax * i, y + k, z + az * i, f'{wood}_log', 'z' if axis == 'x' else 'x')
        kit.slab(g, x + ax * i, y + h, z + az * i, wood)


def firepit(g, x, z, y, lit=True, seats=True, wood='spruce', seed=0):
    """A ring of stones round a fire, log benches round that."""
    for (dx, dz) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
        g.set(x + dx, y - 1, z + dz, 'cobblestone')
        g.set(x + dx, y, z + dz, 'cobblestone_slab[type=bottom]' if (dx + dz) % 2 else 'stone_slab[type=bottom]')
    K.campfire(g, x, y, z, lit=lit)
    if seats:
        for (bx, bz, axis) in ((x - 4, z - 1, 'z'), (x + 4, z - 1, 'z'), (x - 1, z + 4, 'x')):
            for i in range(3):
                kit.log(g, bx + (i if axis == 'x' else 0), y, bz + (i if axis == 'z' else 0), f'stripped_{wood}_log', axis)


# ================================================================ The Ranger Outpost (duskpine weald)

def ranger_outpost():
    """The wardens of the weald keep a stockaded outpost in the pines: a log lodge with bunks, a
    map table and a fire; a timber watchtower over the gate; a stable, an archery range, firewood
    stacked under cover, and a fire-pit with log benches at the heart of the yard."""
    G = 3
    g = K.grid(48, 34, 48)
    c = 24
    ground(g, 0, 0, 47, 47, G - 1, [('coarse_dirt', 3), ('podzol[snowy=false]', 2), ('rooted_dirt', 1), ('dirt_path', 2)], 0.5, seed=111,
           keep=lambda x, z: not (6 < x < 41 and 6 < z < 41))
    # The yard inside the stockade: trodden earth, gravel and paths.
    for x in range(7, 41):
        for z in range(7, 41):
            g.set(x, G - 1, z, pick([('coarse_dirt', 3), ('dirt_path', 3), ('gravel', 1), ('podzol[snowy=false]', 1)], x, G, z, 112))

    # The stockade, with a gate in the south wall and a walk over it.
    corners = [(6, 10), (10, 6), (37, 6), (41, 10), (41, 37), (37, 41), (27, 41)]
    gate = [(x, 41) for x in range(c - 2, c + 2)]
    palisade(g, corners + [(c + 1, 41)], G, 4, seed=113)
    palisade(g, [(c - 3, 41), (10, 41), (6, 37), (6, 10)], G, 4, seed=114)
    for (x, z) in gate:
        g.set(x, G - 1, z, 'gravel')
    for x in range(c - 4, c + 4):
        g.set(x, G + 4, 41, 'spruce_planks')
        g.set(x, G + 4, 40, 'spruce_planks')
        g.set(x, G + 5, 42, 'spruce_fence')
        g.set(x, G + 5, 39, 'spruce_fence')
    for x in (c - 3, c + 2):
        for k in range(5):
            kit.log(g, x, G + k, 42, 'spruce_log')
    kit.hang_lantern(g, c - 1, G + 3, 42, 0)
    K.banner(g, c - 4, G + 3, 42, 'green', 'south')
    K.banner(g, c + 3, G + 3, 42, 'green', 'south')
    for k in range(4):
        g.set(c + 3, G + k, 40, 'ladder[facing=north,waterlogged=false]')

    # The lodge along the north wall.
    room, door = log_cabin(g, 12, 9, 26, 16, G, door_side='south', door_at=19, chimney='west', seed=115)
    rx0, rz0, rx1, rz1, ry = room
    for (bx, bz) in ((rx1, rz0), (rx1 - 2, rz0), (rx1 - 4, rz0)):
        kit.bed(g, bx, ry, bz + 1, 'green', 'north')
        g.set(bx, ry + 2, bz, 'spruce_trapdoor[facing=south,half=top,open=false]')
    g.set(rx0, ry, rz0, 'cartography_table')
    g.set(rx0 + 1, ry, rz0, 'barrel[facing=up,open=false]')
    g.set(rx0 + 2, ry, rz0, 'barrel[facing=up,open=false]')
    kit.table(g, rx0 + 3, ry, rz0 + 3)
    kit.chair(g, rx0 + 3, ry, rz0 + 4, 'spruce', 'north')
    kit.chair(g, rx0 + 2, ry, rz0 + 3, 'spruce', 'east')
    g.set(rx0 + 3, ry + 1, rz0 + 3, 'lantern[hanging=false]')
    g.chest(rx1, ry, rz1 - 1, 'west', fl(CACHE, 'forest'))
    g.set(rx1, ry, rz1, 'smoker[facing=west,lit=false]')
    # Along the south wall, clear of the door (x 19).
    g.set(rx0 + 8, ry, rz1, 'crafting_table')
    g.set(rx0 + 9, ry, rz1, 'fletching_table')
    kit.lantern(g, rx0 + 6, ry + 2, rz0 + 2, hanging=True)
    for x in range(rx0 + 5, rx0 + 10):
        g.set(x, ry, rz0 + 2, 'brown_carpet')

    # The watchtower in the north-east corner: four log legs, a ladder, a roofed lookout.
    tx, tz = 34, 9
    for (x, z) in ((tx, tz), (tx + 4, tz), (tx, tz + 4), (tx + 4, tz + 4)):
        for k in range(14):
            kit.log(g, x, G + k, z, 'spruce_log')
    for k in (4, 9):
        for x in range(tx, tx + 5):
            for z in (tz, tz + 4):
                if 0 < x - tx < 4:
                    kit.log(g, x, G + k, z, 'stripped_spruce_log', 'x')
        for z in range(tz + 1, tz + 4):
            for x in (tx, tx + 4):
                kit.log(g, x, G + k, z, 'stripped_spruce_log', 'z')
    g.fill(tx - 1, G + 13, tz - 1, tx + 5, G + 13, tz + 5, 'spruce_planks')
    for x in range(tx - 1, tx + 6):
        for z in range(tz - 1, tz + 6):
            if x in (tx - 1, tx + 5) or z in (tz - 1, tz + 5):
                g.set(x, G + 14, z, 'spruce_fence')
    for (x, z) in ((tx - 1, tz - 1), (tx + 5, tz - 1), (tx - 1, tz + 5), (tx + 5, tz + 5)):
        for k in range(14, 17):
            g.set(x, G + k, z, 'spruce_fence')
    kit.hip(g, tx - 1, tz - 1, tx + 5, tz + 5, G + 17, 'spruce', overhang=1)
    for k in range(13):
        g.set(tx + 2, G + k, tz + 5, 'ladder[facing=south,waterlogged=false]')
    g.set(tx + 2, G + 13, tz + 4, 'air')
    K.campfire(g, tx + 2, G + 14, tz + 2, lit=False)
    kit.lantern(g, tx + 4, G + 16, tz, hanging=True)

    # The stable along the west wall: stalls under a lean-to roof, hay and troughs.
    for z in range(20, 34):
        for k in range(4):
            if z in (20, 33) or (z - 20) % 4 == 0:
                kit.log(g, 8, G + k, z, 'spruce_log')
                if k < 3:
                    kit.log(g, 13, G + k, z, 'spruce_log')
    for z in range(19, 35):
        for x in range(7, 15):
            yy = G + 4 - (x - 7) // 3
            kit.stairs(g, x, yy, z, 'spruce', 'east')
    for z in range(21, 33):
        if (z - 20) % 4 == 0:
            for x in range(9, 13):
                g.set(x, G, z, 'spruce_fence')
        else:
            g.set(9, G, z, 'hay_block[axis=y]' if hash3(9, G, z, 116) < 0.6 else 'cauldron')
            if hash3(10, G, z, 117) < 0.5:
                g.set(10, G, z, 'brown_carpet')
    g.set(13, G, 22, 'spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]')
    K.barrels(g, 10, G, 34, seed=118, n=3)

    # The archery range along the east wall: targets on hay, a shooting line of fence.
    for i, z in enumerate((21, 26, 31)):
        g.set(39, G, z, 'hay_block[axis=y]')
        g.set(39, G + 1, z, 'target[power=0]')
        g.set(38, G, z, 'hay_block[axis=y]')
    for z in range(20, 33):
        g.set(31, G - 1, z, 'spruce_planks')
    for z in (20, 32):
        g.set(31, G, z, 'spruce_fence')
        kit.lantern(g, 31, G + 1, z)
    g.set(32, G, 26, 'barrel[facing=up,open=false]')

    # The yard's heart: a fire-pit with benches, firewood stacked against the stockade.
    firepit(g, c, 28, G, lit=True, seed=119)
    wood_stack(g, 28, 38, G, 6, 'x')
    wood_stack(g, 15, 38, G, 4, 'x')
    for (x, z) in ((c - 6, 24), (c + 5, 32)):
        K.lantern_post(g, x, G, z, PAL['stone'], 3)
    stepping(g, [(c, 47), (c, 41), (c, 31)], G - 1, [('dirt_path', 3), ('gravel', 1)], seed=120, gap=0.05, width=3)
    stepping(g, [(c, 26), (19, door[1] + 3)], G - 1, [('dirt_path', 3), ('gravel', 1)], seed=121, gap=0.1, width=2)

    # The pines round about, and stumps where trees were felled for the stockade.
    for k, (x, z) in enumerate(((2, 3), (45, 4), (2, 23), (45, 25), (3, 45), (44, 44), (16, 46), (33, 46), (46, 13), (1, 33))):
        pine(g, x, z, G, 10 + k % 4, seed=130 + k)
    for (x, z) in ((3, 14), (44, 33), (14, 2), (30, 2), (38, 45)):
        kit.log(g, x, G, z, 'spruce_log')
        if hash3(x, 0, z, 122) < 0.5:
            g.set(x, G + 1, z, 'spruce_pressure_plate')
    plants(g, 0, 0, 47, 47, G, [('fern', 4), ('short_grass', 3), ('leaf_litter[facing=north,segment_amount=3]', 2), ('sweet_berry_bush[age=3]', 1),
                                  ('bush', 1), ('brown_mushroom', 1)], 0.2, seed=123)
    mob(g, 44, G, 40, 'duskpine_lynx')
    return K.finish(g)


def tower_stairs(g, x0, z0, x1, z1, levels, mat='spruce', rail='spruce_fence'):
    """Straight flights up the inside of a square room (x0..x1, z0..z1 inside the walls), one per
    storey, on the west and east walls in turn; each cuts the floor above its last steps, so it is
    walked, never climbed, and a rail guards the hole. levels: the layer each floor is walked on."""
    for i in range(len(levels) - 1):
        y, top = levels[i], levels[i + 1]
        rise = top - y
        west = i % 2 == 0
        x = x0 if west else x1
        cells = [(x, z0 + 1 + k) for k in range(rise)] if west else [(x, z1 - 1 - k) for k in range(rise)]
        facing = 'south' if west else 'north'
        for k, (cx, cz) in enumerate(cells):
            g.set(cx, y + k, cz, f'{mat}_stairs[facing={facing},half=bottom,shape=straight]')
            for h in range(1, 4):
                if y + k + h < top + 3:
                    g.set(cx, y + k + h, cz, 'air')
            if k > 0:
                g.set(cx, y + k - 1, cz, f'{mat}_planks')
        # The rail round the hole on the floor above.
        rx = x + (1 if west else -1)
        for (cx, cz) in cells[-4:-1]:
            g.set(rx, top, cz, rail)


# ================================================================ The Frozen Watch Post (frostmere pines)

def frozen_watch_post():
    """A border watch frozen at its post: a square stone keep four storeys high (an armoury, the
    barracks, the captain's room and the watch above) with stairs between its floors and a beacon
    on its roof, inside a walled court with a gatehouse, a barracks hall, a woodshed and a frozen
    well. The captain's last log lies by the strongbox at the top."""
    G = 3
    g = K.grid(48, 34, 48)
    c = 24
    stone = [('stone_bricks', 4), ('cracked_stone_bricks', 1), ('andesite', 1), ('mossy_stone_bricks', 1)]
    ground(g, 0, 0, 47, 47, G - 1, [('snow_block', 4), ('packed_ice', 1), ('stone', 1), ('gravel', 1)], 0.35, seed=141)

    # The curtain wall: three high with merlons, round a court 34 by 30, the gatehouse in the south.
    wx0, wz0, wx1, wz1 = 7, 7, 40, 37
    for x in range(wx0, wx1 + 1):
        for z in range(wz0, wz1 + 1):
            if x in (wx0, wx1) or z in (wz0, wz1):
                g.set(x, G - 1, z, 'stone_bricks')
                for k in range(3):
                    g.set(x, G + k, z, pick(stone, x, G + k, z, 142))
                if (x + z) % 2 == 0:
                    g.set(x, G + 3, z, pick(stone, x, G + 3, z, 143))
            else:
                g.set(x, G - 1, z, pick([('snow_block', 3), ('gravel', 2), ('stone_bricks', 1), ('cobblestone', 1)], x, G, z, 144))
    # The gatehouse: two square towers with a portcullis between.
    for tx in (c - 5, c + 2):
        g.fill(tx, G - 1, wz1 - 1, tx + 3, G + 7, wz1 + 2, 'stone_bricks')
        g.fill(tx + 1, G, wz1, tx + 2, G + 6, wz1 + 1, 'air')
        K.crenellate(g, tx, wz1 - 1, tx + 3, wz1 + 2, G + 8, 'stone_bricks')
        g.set(tx + 1, G + 3, wz1 + 2, 'iron_bars[north=false,south=false,east=true,west=true,waterlogged=false]')
    g.fill(c - 2, G, wz1 - 1, c + 1, G + 3, wz1 + 2, 'air')
    g.fill(c - 2, G + 4, wz1 - 1, c + 1, G + 5, wz1 + 2, 'stone_bricks')
    for x in range(c - 2, c + 2):
        g.set(x, G + 3, wz1 + 1, 'iron_bars[north=false,south=false,east=true,west=true,waterlogged=false]')
    K.banner(g, c - 4, G + 5, wz1 + 3, 'light_blue', 'south')
    K.banner(g, c + 3, G + 5, wz1 + 3, 'light_blue', 'south')
    for x in (c - 3, c + 2):
        kit.lantern(g, x, G + 2, wz1 + 3)

    # The keep: nine square, four storeys of five, a parapet and a beacon above.
    kx0, kz0, kx1, kz1 = 19, 10, 29, 20
    levels = [G, G + 5, G + 10, G + 15]
    roof = G + 20
    for y in range(G - 1, roof):
        for x in range(kx0, kx1 + 1):
            for z in range(kz0, kz1 + 1):
                if x in (kx0, kx1) or z in (kz0, kz1):
                    g.set(x, y, z, 'polished_andesite' if (x in (kx0, kx1) and z in (kz0, kz1)) else pick(stone, x, y, z, 145))
                elif y >= G:
                    g.set(x, y, z, 'air')
    for y in [G - 1] + [L - 1 for L in levels[1:]] + [roof - 1]:
        g.fill(kx0 + 1, y, kz0 + 1, kx1 - 1, y, kz1 - 1, 'spruce_planks' if y >= G else 'stone_bricks')
    K.crenellate(g, kx0, kz0, kx1, kz1, roof, 'stone_bricks')
    for x in range(kx0, kx1 + 1):
        for z in range(kz0, kz1 + 1):
            if (x in (kx0, kx1) or z in (kz0, kz1)) and g.get(x, roof, z) is None:
                g.set(x, roof, z, 'stone_brick_slab[type=bottom]')
    tower_stairs(g, kx0 + 1, kz0 + 1, kx1 - 1, kz1 - 1, levels + [roof], 'spruce')
    # Windows: slits below, glass above, a door into the court.
    for L in levels:
        for (x, z, face) in ((c, kz0, 'north'), (c, kz1, 'south'), (kx0, 15, 'west'), (kx1, 15, 'east')):
            g.set(x, L + 1, z, 'iron_bars[north=false,south=false,east=false,west=false,waterlogged=false]' if L == G else 'glass_pane')
            if L > G:
                g.set(x, L + 2, z, 'glass_pane')
    g.fill(c, G, kz1, c, G + 1, kz1, 'air')
    kit.door(g, c, G, kz1, 'spruce', 'south')
    K.banner(g, c - 1, G + 3, kz1 + 1, 'light_blue', 'south')
    K.banner(g, c + 1, G + 3, kz1 + 1, 'light_blue', 'south')
    # The armoury below: a grindstone, an anvil, racks of barrels.
    g.set(kx0 + 2, G, kz0 + 1, 'grindstone[face=floor,facing=north]')
    g.set(kx0 + 3, G, kz0 + 1, 'smithing_table')
    g.set(kx0 + 4, G, kz0 + 1, 'anvil[facing=east]')
    for x in range(kx1 - 4, kx1):
        g.set(x, G, kz0 + 1, 'barrel[facing=up,open=false]')
    g.set(kx1 - 2, G, kz1 - 2, 'lantern[hanging=false]')
    # The barracks: six bunks under the windows.
    for i, x in enumerate(range(kx0 + 3, kx1 - 1, 2)):
        kit.bed(g, x, G + 5, kz0 + 1, 'light_blue', 'north')
        kit.bed(g, x, G + 5, kz1 - 2, 'light_blue', 'south')
    kit.hang_lantern(g, c, G + 8, 15, 0)
    # The captain's room: a map table, a lectern, a cold hearth.
    kit.table(g, c, G + 10, 15, top='spruce_pressure_plate')
    for (x, z, f) in ((c - 1, 15, 'east'), (c + 1, 15, 'west')):
        kit.chair(g, x, G + 10, z, 'spruce', f)
    g.set(kx1 - 1, G + 10, kz0 + 1, 'lectern[facing=west,has_book=true,powered=false]')
    g.set(kx0 + 2, G + 10, kz1 - 1, 'cartography_table')
    g.set(kx1 - 2, G + 10, kz1 - 1, 'campfire[lit=false,facing=north,signal_fire=false,waterlogged=false]')
    kit.hang_lantern(g, c, G + 13, 13, 0)
    # The watch at the top: the strongbox and the captain's log, a spyglass stand, the beacon.
    g.chest(kx1 - 1, G + 15, kz0 + 2, 'west', fl(VAULT, 'frost'))
    clue(g, kx1 - 1, G + 15, kz0 + 4, 'note', 'watch_log')
    g.set(kx0 + 2, G + 15, kz0 + 1, 'barrel[facing=up,open=false]')
    g.set(c, roof, 15, 'stone_bricks')
    K.campfire(g, c, roof + 1, 15, lit=False, soul=True)
    for k in range(roof + 1, roof + 6):
        g.set(kx0 + 1, k, kz0 + 1, 'spruce_fence')
    g.set(kx0 + 2, roof + 5, kz0 + 1, 'light_blue_wool')
    g.set(kx0 + 3, roof + 5, kz0 + 1, 'light_blue_wool')
    g.set(kx0 + 2, roof + 4, kz0 + 1, 'light_blue_wool')

    # The barracks hall across the court, the woodshed, the well, a sledge, braziers.
    bkit.building(g, 10, 24, 18, 32, G, bkit.TIMBER_SLATE, storeys=1, ground='stone', axis='z', door_side='east', kind='home', seed=146)
    for x0, z0 in ((32, 24),):
        for x in range(x0, x0 + 6):
            for z in range(z0, z0 + 5):
                if (x in (x0, x0 + 5)) and (z in (z0, z0 + 4)):
                    for k in range(3):
                        kit.log(g, x, G + k, z, 'spruce_log')
        for x in range(x0 - 1, x0 + 7):
            for z in range(z0 - 1, z0 + 6):
                g.set(x, G + 3, z, 'spruce_slab[type=bottom]')
        wood_stack(g, x0 + 1, z0 + 1, G, 4, 'x', h=2)
        wood_stack(g, x0 + 1, z0 + 3, G, 4, 'x', h=1)
    wx, wz = 33, 14
    disc(g, wx, wz, G - 1, 1.5, 'cobblestone')
    for (x, z) in np.argwhere((dist(g, wx, wz) <= 1.6) & (dist(g, wx, wz) > 0.6)):
        g.set(x, G, z, 'stone_bricks')
    g.set(wx, G - 1, wz, 'ice')
    g.set(wx, G - 2, wz, 'water[level=0]')
    for (x, z) in ((wx - 1, wz), (wx + 1, wz)):
        g.set(x, G + 1, z, 'spruce_fence')
    for x in range(wx - 1, wx + 2):
        kit.log(g, x, G + 2, wz, 'stripped_spruce_log', 'x')
    sledge(g, 12, 13, G, 'x', seed=147)
    for (x, z) in ((c - 4, kz1 + 4), (c + 4, kz1 + 4)):
        K.brazier(g, x, G, z, PAL['stone'], lit=False, h=1)

    # Snowy pines outside the wall, snow over everything.
    for k, (x, z) in enumerate(((2, 3), (45, 2), (1, 24), (46, 22), (3, 44), (44, 45), (14, 45), (35, 45), (24, 1), (46, 33))):
        pine(g, x, z, G, 9 + k % 4, seed=150 + k, snow=True)
    kit.snow(g, depth=1, rate=0.92, seed=148)
    plants(g, 0, 0, 47, 47, G, [('snow[layers=2]', 5), ('snow[layers=3]', 2), ('short_grass', 1), ('sweet_berry_bush[age=1]', 1)], 0.12, seed=149)
    mob(g, 33, G, 30, 'rime_wight')
    mob(g, 4, G, 30, 'snow_hare', 2)
    return K.finish(g)


# ================================================================ The Snowbound Camp (whitewaste)

def big_tent(g, x0, z0, length, y, cloth, trim, axis='x'):
    """A great canvas tent, seven wide and five high, on a plank floor, open at its front end,
    with a ridge pole, guy ropes (fences) and a lantern hung from the ridge. Returns the floor box."""
    ax, az = (1, 0) if axis == 'x' else (0, 1)
    sx, sz = (0, 1) if axis == 'x' else (1, 0)
    profile = ((-3, 1), (-2, 2), (-1, 3), (0, 4), (1, 3), (2, 2), (3, 1))
    for i in range(length):
        for w, h in profile:
            x, z = x0 + ax * i + sx * w, z0 + az * i + sz * w
            g.set(x, y - 1, z, 'spruce_planks')
            g.set(x, y + h, z, f'{cloth}_wool' if (i % 3) else f'{trim}_wool')
            for k in range(h):
                g.set(x, y + k, z, 'air')
                if i == length - 1:
                    g.set(x, y + k, z, f'{cloth}_wool')
    for i in (0, length - 1):
        for k in range(4):
            g.set(x0 + ax * i, y + k, z0 + az * i, 'spruce_fence')
    for w in (-4, 4):
        for i in (1, length - 2):
            g.set(x0 + ax * i + sx * w, y, z0 + az * i + sz * w, 'spruce_fence')
    kit.hang_lantern(g, x0 + ax * (length // 2), y + 3, z0 + az * (length // 2), 0)
    return (x0, z0, length)


def igloo(g, cx, cz, y, r, done=1.0, seed=0):
    """A dome of snow blocks; `done` < 1 leaves the top courses unbuilt."""
    for x in range(cx - r - 1, cx + r + 2):
        for z in range(cz - r - 1, cz + r + 2):
            for k in range(r + 1):
                d = math.sqrt((x - cx) ** 2 + (z - cz) ** 2 + (k * 1.15) ** 2)
                if r - 0.6 <= d <= r + 0.4 and k <= r * done:
                    g.set(x, y + k, z, 'snow_block')
                elif d < r - 0.6:
                    g.set(x, y + k, z, 'air')
    g.fill(cx, y, cz + r, cx, y + 1, cz + r, 'air')


def snowbound_camp():
    """A trading expedition snowed in on the waste: a great command tent with the factor's table
    and his ledger, three bell tents round a fire-pit, laden sledges, crates under frozen tarps, a
    half-built igloo, the frozen bones of a pack beast, a signal pole, and a windbreak of snow
    blocks curving round the north of it all."""
    G = 3
    g = K.grid(48, 26, 48)
    c = 24
    ground(g, 0, 0, 47, 47, G - 1, [('snow_block', 6), ('packed_ice', 1), ('powder_snow', 1)], 0.5, seed=161)

    # The windbreak: a curving wall of snow blocks, broken here and there.
    for a in range(150, 395, 3):
        t = math.radians(a)
        x, z = int(round(c + math.cos(t) * 19)), int(round(c + 2 + math.sin(t) * 17))
        if not inside(g, x, z):
            continue
        h = 2 + int(vnoise(x, 0, z, 3.0, 162) * 2.4)
        for k in range(h):
            g.set(x, G + k, z, 'snow_block' if k < h - 1 or hash3(x, k, z, 163) < 0.6 else 'snow[layers=4]')
        g.set(x + (1 if math.cos(t) < 0 else -1), G, z, 'snow[layers=3]')

    # The command tent, open to the fire.
    big_tent(g, 14, 17, 10, G, 'white', 'light_gray', axis='x')
    kit.table(g, 18, G, 17)
    g.set(19, G, 17, 'cartography_table')
    kit.chair(g, 17, G, 17, 'spruce', 'east')
    g.chest(15, G, 15, 'south', fl(CACHE, 'frost'))
    g.set(16, G, 15, 'barrel[facing=up,open=false]')
    clue(g, 18, G, 18, 'note', 'trader_ledger')
    for x in (20, 21):
        g.set(x, G, 19, 'gray_carpet')
        g.set(x, G, 15, 'gray_carpet')
    kit.bed(g, 21, G, 16, 'brown', 'east')
    g.set(15, G, 19, 'lantern[hanging=false]')

    # Three bell tents and the fire-pit.
    firepit(g, c + 3, c + 4, G, lit=False, seed=164)
    for i, (x, z, axis) in enumerate(((c + 9, c - 3, 'z'), (c + 11, c + 7, 'x'), (c - 1, c + 11, 'x'))):
        K.tent(g, x, z, 5, G, ['gray', 'white', 'light_gray'][i], axis=axis)
    # Sledges, crates and barrels, half under snow and tarps.
    sledge(g, 8, 30, G, 'x', seed=165)
    sledge(g, 30, 34, G, 'x', seed=166)
    for (x, z) in ((34, 16), (36, 17), (35, 18), (33, 18)):
        g.set(x, G, z, 'barrel[facing=up,open=false]' if hash3(x, 0, z, 167) < 0.5 else 'spruce_planks')
        g.set(x, G + 1, z, 'white_carpet')
    for (x, z) in ((10, 35), (11, 36)):
        g.set(x, G, z, 'snow_block')
        g.set(x, G + 1, z, 'barrel[facing=north,open=false]')

    # The igloo, half built, blocks stacked ready beside it.
    igloo(g, 35, 30, G, 3, done=0.6, seed=168)
    for (x, z) in ((40, 29), (40, 30), (41, 30)):
        g.set(x, G, z, 'snow_block')

    # The pack beast's bones, frozen into the drift; the signal pole.
    for i in range(4):
        K.rib(g, 12 + i * 2, 41, G - 1, 4, 2 + (i % 2), axis='z', mat='bone_block')
    for k in range(4):
        g.set(10, G + (k // 3), 41, 'bone_block[axis=x]')
    K.skull(g, 9, G, 41, rot=4)
    for k in range(9):
        g.set(c - 6, G + k, c - 4, 'spruce_fence')
    for k in (5, 7):
        K.banner(g, c - 6, G + k, c - 5, 'red' if k == 7 else 'white', 'north')
    kit.lantern(g, c - 6, G + 9, c - 4)

    kit.snow(g, depth=1, rate=0.95, seed=169)
    plants(g, 0, 0, 47, 47, G, [('snow[layers=2]', 5), ('snow[layers=3]', 3), ('snow[layers=5]', 1)], 0.18, seed=170)
    mob(g, c + 14, G, c + 13, 'rime_wight', 2)
    return K.finish(g)


# ================================================================ The Caravan Wreck (hourglass dunes, ember steppe)

def dune(g, cx, cz, y, rx, rz, h, seed=0):
    """A drift of sand, highest in its middle, piled against whatever stands there."""
    for x in range(int(cx - rx - 1), int(cx + rx + 2)):
        for z in range(int(cz - rz - 1), int(cz + rz + 2)):
            if not inside(g, x, z):
                continue
            q = ((x - cx) / rx) ** 2 + ((z - cz) / rz) ** 2
            if q > 1.0:
                continue
            top = h * (1 - q) + (float(vnoise(x, 0, z, 3.0, seed)) - 0.5) * 0.8
            for k in range(int(top)):
                if K.empty(g, x, y + k, z):
                    g.set(x, y + k, z, 'sand')
            if top - int(top) > 0.5 and K.empty(g, x, y + int(top), z):
                g.set(x, y + int(top), z, 'sandstone_slab[type=bottom]' if hash3(x, 0, z, seed) < 0.3 else 'sand')


def caravan_wreck():
    """The silent caravan's last camp on the old road: four wagons (one tipped, one with a wheel cut
    away, one burnt to its frame, one still laden), its cargo spilled down the road, a torn awning
    over a cold fire, camel bones and a roadside shrine; dunes drifting in over all of it. The three
    things Rashid asked about are here: the cut wheel, the manifest, and the tracks leading away."""
    G = 3
    g = K.grid(48, 24, 48)
    P = PAL['sand']
    ground(g, 0, 0, 47, 47, G - 1, [('sand', 5), ('sandstone', 1), ('red_sand', 1)], 0.5, seed=181)
    # The old road: worn paving under the sand, a kerb here and there, a milestone.
    for x in range(0, 48):
        zc = 23 + int(round(math.sin(x / 9.0) * 2))
        for w in range(-2, 3):
            z = zc + w
            r = hash3(x, 0, z, 182)
            if r < (0.75 if abs(w) < 2 else 0.4):
                g.set(x, G - 1, z, 'smooth_sandstone' if r < 0.35 else ('cut_sandstone' if r < 0.6 else 'sandstone'))
        if x % 6 == 0:
            for z in (zc - 3, zc + 3):
                if hash3(x, 1, z, 183) < 0.6:
                    g.set(x, G, z, 'cut_sandstone_slab[type=bottom]')
    for k in range(3):
        g.set(5, G + k, 19, 'cut_sandstone' if k < 2 else 'chiseled_sandstone')
    g.set(5, G + 3, 19, 'sandstone_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')

    # The caravan: the tipped wagon (the manifest under its cover), the one with the cut wheel,
    # the burnt one, and the last, still laden, with the goods chest.
    K.wagon(g, 8, 27, G, 'x', wood='acacia', cover='orange', tipped=True, seed=184)
    clue(g, 10, G, 29, 'manifest')
    K.wagon(g, 20, 20, G, 'x', wood='acacia', cover='white', broken=True, seed=185)
    g.set(26, G, 25, 'acacia_trapdoor[facing=north,half=bottom,open=false]')
    clue(g, 27, G, 25, 'wagon')
    for i in range(5):
        for w in (-1, 1):
            if i % 2 == 0:
                g.set(30 + i, G, 31 + w, 'stripped_acacia_log[axis=x]')
        g.set(30 + i, G, 31, 'acacia_slab[type=bottom]' if i % 2 else 'coal_block')
    K.campfire(g, 32, G + 1, 31, lit=False)
    g.set(34, G + 1, 30, 'acacia_fence')
    K.wagon(g, 33, 16, G, 'x', wood='acacia', cover='red', seed=186)
    g.chest(35, G + 2, 16, 'north', 'afterworld:chests/caravan_goods')

    # The cargo, spilled down the road: barrels, pots, rolled carpets, crates.
    K.barrels(g, 14, G, 26, seed=187, n=4)
    K.barrels(g, 39, G, 21, seed=188, n=3)
    K.pots(g, [(16, G, 30), (17, G, 31), (24, G, 27), (12, G, 21)])
    for (x, z, col) in ((18, 28, 'red'), (22, 30, 'orange'), (28, 19, 'blue')):
        for i in range(3):
            g.set(x + i, G, z, f'{col}_wool' if i == 1 else f'{col}_carpet')
    for (x, z) in ((30, 25), (31, 26), (29, 27)):
        g.set(x, G, z, 'acacia_planks' if hash3(x, 0, z, 189) < 0.5 else 'barrel[facing=north,open=false]')

    # The last camp: a torn awning on poles over a cold fire, cushions, a tea-pot (a cauldron).
    ax0, az0 = 14, 10
    for (x, z) in ((ax0, az0), (ax0 + 6, az0), (ax0, az0 + 5), (ax0 + 6, az0 + 5)):
        for k in range(4):
            g.set(x, G + k, z, 'acacia_fence')
    for x in range(ax0, ax0 + 7):
        for z in range(az0, az0 + 6):
            if hash3(x, 0, z, 190) < 0.72:
                g.set(x, G + 4, z, 'orange_carpet' if (x + z) % 3 else 'white_carpet')
    K.campfire(g, ax0 + 3, G, az0 + 2, lit=False)
    g.set(ax0 + 3, G, az0 + 4, 'cauldron')
    for (x, z, col) in ((ax0 + 1, az0 + 1, 'red'), (ax0 + 5, az0 + 1, 'orange'), (ax0 + 1, az0 + 3, 'red'), (ax0 + 5, az0 + 4, 'yellow')):
        g.set(x, G, z, f'{col}_carpet')

    # Camel bones on the road, a shrine of the old road with an offering bowl.
    for i in range(3):
        K.rib(g, 40, 30 + i * 2, G - 1, 4, 2, axis='x')
    for k in range(3):
        g.set(37 - k, G, 32, 'bone_block[axis=x]')
    K.skull(g, 34, G, 32, rot=12)
    sx0, sz0 = 38, 7
    g.fill(sx0, G - 1, sz0, sx0 + 4, G - 1, sz0 + 4, 'cut_sandstone')
    for (x, z) in ((sx0, sz0), (sx0 + 4, sz0), (sx0, sz0 + 4), (sx0 + 4, sz0 + 4)):
        for k in range(4):
            g.set(x, G + k, z, 'sandstone_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]' if k < 3 else 'chiseled_sandstone')
    g.fill(sx0, G + 4, sz0, sx0 + 4, G + 4, sz0 + 4, 'cut_sandstone_slab[type=bottom]')
    g.set(sx0 + 2, G, sz0 + 2, 'chiseled_sandstone')
    g.set(sx0 + 2, G + 1, sz0 + 2, 'decorated_pot[facing=north,cracked=false,waterlogged=false]')
    K.candles(g, sx0 + 1, G, sz0 + 3, 2, lit=False, color='orange')

    # The tracks lead away north-east into the dunes.
    for i in range(10):
        x, z = 36 + i, 40 - i // 2
        if inside(g, x, z):
            g.set(x, G - 1, z, 'coarse_dirt' if i % 2 else 'sandstone')
    clue(g, 44, G, 37, 'tracks')

    # The dunes drifting in, dead bushes and cacti, the vultures' perch.
    for (x, z, rx, rz, h, s) in ((9, 31, 4, 3, 2.5, 191), (22, 23, 3, 2, 1.6, 192), (30, 12, 6, 4, 3.0, 193), (5, 6, 6, 5, 3.5, 194),
                                 (44, 44, 7, 5, 3.5, 195), (3, 42, 5, 6, 3.0, 196), (26, 42, 5, 3, 2.0, 197)):
        dune(g, x, z, G, rx, rz, h, seed=s)
    dead_limbs(g, 20, 38, G, 7, 'acacia_log', seed=198, limbs=3)
    plants(g, 0, 0, 47, 47, G, [('dead_bush', 4), ('short_dry_grass', 4), ('tall_dry_grass', 2), ('cactus', 1)], 0.06, seed=199)
    mob(g, 21, G + 7, 38, 'canyon_vulture', 2)
    mob(g, 42, G, 12, 'sand_sifter')
    return K.finish(g, P)


# ================================================================ The Shattered Caravan (shardfields)

def spire(g, x, z, y, h, lean, mats, seed=0, r=1.0):
    """A crystal spire: a tapering shaft leaning off true, a cluster on its point."""
    tx, tz = lean
    for k in range(h):
        t = k / max(1, h - 1)
        cx = x + tx * t
        cz = z + tz * t
        rr = r * (1 - t * 0.75)
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if math.hypot(dx, dz) <= rr + 0.15:
                    g.set(int(round(cx + dx)), y + k, int(round(cz + dz)), pick(mats, int(cx + dx), y + k, int(cz + dz), seed))
    px, pz = int(round(x + tx)), int(round(z + tz))
    K.cluster(g, px, y + h, pz)
    return (px, y + h, pz)


def shattered_caravan():
    """A Guild caravan split open by crystal that grew up through it in a single night: a crater of
    raw geode at the heart, spires of amethyst and quartz bursting out of it, one wagon lifted on
    their points, two more tipped and broken round the rim, the Guild's goods scattered and glowing
    shards everywhere."""
    G = 3
    g = K.grid(48, 30, 48)
    c = 24
    ground(g, 0, 0, 47, 47, G - 1, [('calcite', 3), ('smooth_basalt', 1), ('tuff', 2)], 0.35, seed=201)
    # The crater: a bowl lined like a split geode, calcite then smooth basalt then amethyst.
    K.bowl(g, c, c, G - 1, 9, 4, [('amethyst_block', 3), ('budding_amethyst', 1), ('calcite', 1)], seed=202)
    for (x, z) in np.argwhere((dist(g, c, c) > 8.2) & (dist(g, c, c) <= 10.5)):
        g.set(x, G - 1, z, pick([('calcite', 3), ('smooth_basalt', 2), ('tuff', 1)], x, G, z, 203))
        if hash3(x, G, z, 204) < 0.3:
            g.set(x, G, z, pick([('calcite', 2), ('small_amethyst_bud[facing=up,waterlogged=false]', 2), ('tuff', 1)], x, G, z, 205))
    crystal = [('amethyst_block', 5), ('calcite', 1), ('tinted_glass', 1)]
    quartz = [('quartz_block', 3), ('smooth_quartz', 2), ('calcite', 1)]
    spires = [(c, c, 17, (2, -2), crystal, 2.3), (c + 3, c + 2, 13, (5, 3), crystal, 1.6), (c - 3, c + 1, 14, (-6, 2), quartz, 1.7),
              (c + 1, c - 4, 11, (2, -6), crystal, 1.4), (c - 2, c - 3, 10, (-5, -4), quartz, 1.3), (c + 5, c - 1, 9, (6, -1), crystal, 1.1),
              (c - 5, c + 4, 9, (-4, 6), crystal, 1.1), (c + 2, c + 6, 8, (2, 6), quartz, 1.0), (c - 6, c - 2, 7, (-6, -1), crystal, 0.9)]
    tips = []
    for i, (x, z, h, lean, mats, r) in enumerate(spires):
        tips.append(spire(g, x, z, G - 3, h + 3, lean, mats, seed=206 + i, r=r))
    for (x, z) in around(c, c, 6.5, 14, 10):
        K.cluster(g, x, G - 1 + int(dist(g, c, c)[x, z] < 7), z, kind='large_amethyst_bud' if (x + z) % 2 else 'amethyst_cluster')

    # A wagon lifted on the spires' points; two more tipped and broken round the rim.
    K.wagon(g, c - 2, c - 1, G + 10, 'x', wood='spruce', cover='blue', broken=True, seed=207)
    K.wagon(g, c + 9, c + 6, G, 'z', wood='spruce', cover='light_blue', tipped=True, seed=208)
    K.wagon(g, c - 14, c - 6, G, 'x', wood='spruce', cover='blue', broken=True, seed=209)
    g.chest(c - 11, G + 2, c - 6, 'south', fl(CACHE, 'crystal'))
    K.barrels(g, c + 12, G, c - 8, seed=210, n=4)
    K.pots(g, [(c - 9, G, c + 9), (c - 8, G, c + 10), (c + 13, G, c + 12)])
    for (x, z) in ((c - 6, c + 12), (c + 14, c - 2)):
        for i in range(3):
            g.set(x + i, G, z, 'blue_carpet' if i != 1 else 'blue_wool')
    # The Guild's banner on a broken pole, a mule's bones, the road out.
    for k in range(5):
        g.set(c - 15, G + k, c + 5, 'spruce_fence')
    K.banner(g, c - 15, G + 3, c + 6, 'blue', 'south')
    for i in range(2):
        K.rib(g, c + 4 + i * 2, c - 15, G - 1, 3, 2, axis='z')
    K.skull(g, c + 3, G, c - 15, rot=8)
    stepping(g, [(0, c + 1), (c - 10, c + 1)], G - 1, [('gravel', 2), ('tuff', 1), ('coarse_dirt', 1)], seed=211, gap=0.3, width=3)
    stepping(g, [(c + 10, c - 1), (47, c - 2)], G - 1, [('gravel', 2), ('tuff', 1), ('coarse_dirt', 1)], seed=212, gap=0.3, width=3)
    # Shards strewn across the ground, small spires sprouting from the plain beyond the crater.
    for i, (x, z) in enumerate(((6, 8), (41, 9), (8, 40), (40, 41), (17, 44), (45, 26), (2, 20))):
        spire(g, x, z, G, 3 + i % 3, (i % 3 - 1, (i + 1) % 3 - 1), crystal if i % 2 else quartz, seed=213 + i, r=0.5)
    plants(g, 0, 0, 47, 47, G, [('small_amethyst_bud[facing=up,waterlogged=false]', 3), ('medium_amethyst_bud[facing=up,waterlogged=false]', 2),
                                  ('short_grass', 2), ('calcite', 1)], 0.07, seed=214)
    mob(g, c + 6, G, c + 12, 'shardling', 3)
    return K.finish(g)


# ================================================================ The Nomad Camp (ember steppe)

def nomad_camp_yurt(g, cx, cz, y, r, cloth, trim, door, seed=0):
    """A yurt dressed for living in: a felt rug, beds, a chest-bench, a hanging lamp."""
    K.yurt(g, cx, cz, y, r, cloth, trim, door=door)
    d = dist(g, cx, cz)
    rug = d <= r - 1.4
    for (x, z) in np.argwhere(rug):
        g.set(x, y, z, f'{trim}_carpet' if (x + z) % 2 else 'white_carpet')
    kit.hang_lantern(g, cx, y + 3, cz, 0)
    fx, fz = kit.FACE[door]
    bx, bz = cx - fx * (r - 2), cz - fz * (r - 2)
    g.set(bx, y, bz, 'barrel[facing=up,open=false]')
    side = (1, 0) if fz != 0 else (0, 1)
    kit.bed(g, cx + side[0] * (r - 2), y, cz + side[1] * (r - 2), trim, kit.OPP[door])


def nomad_yurts():
    """A clan of the steppe pitched for the season: the chieftain's great yurt with a hearth under
    its smoke-hole, four family yurts round a bonfire ring, a corral of striders and rams, felt
    drying on racks, a cart of rolled felts, and a prayer-post hung with banners and a skull."""
    G = 3
    g = K.grid(48, 26, 48)
    c = 24
    ground(g, 0, 0, 47, 47, G - 1, [('coarse_dirt', 3), ('dirt_path', 2), ('red_sand', 1), ('packed_mud', 1)], 0.4, seed=221)

    # The chieftain's yurt: wide, banded red and gold, a carved door frame, a hearth inside.
    hx, hz, hr = c, 13, 6
    K.yurt(g, hx, hz, G, hr, 'white', 'red', door='south')
    hd = dist(g, hx, hz)
    for (x, z) in np.argwhere(hd <= hr - 1.4):
        g.set(x, G, z, 'red_carpet' if (x + z) % 3 else 'orange_carpet')
    for (x, z) in np.argwhere((hd <= hr + 0.35) & (hd > hr - 0.85)):
        g.set(x, G + 3, z, 'yellow_wool')
    K.campfire(g, hx, G - 1, hz, lit=True)
    g.set(hx, G, hz, 'air')
    for (dx, dz) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        g.set(hx + dx, G, hz + dz, 'cobblestone_slab[type=bottom]')
    g.chest(hx - 4, G, hz - 2, 'east', fl(CACHE, 'steppe'))
    g.set(hx + 4, G, hz - 2, 'loom[facing=west]')
    g.set(hx - 4, G, hz + 2, 'barrel[facing=up,open=false]')
    kit.bed(g, hx + 3, G, hz - 3, 'red', 'north')
    for (x, z) in ((hx - 2, hz - 4), (hx + 2, hz - 4)):
        K.banner(g, x, G + 2, z, 'red', 'south')
    for x in (hx - 1, hx + 1):
        for k in range(3):
            g.set(x, G + k, hz + hr + 1, 'acacia_log[axis=y]' if k < 2 else 'chiseled_red_sandstone')
    g.set(hx, G + 2, hz + hr + 1, 'cut_red_sandstone')
    kit.lantern(g, hx - 2, G, hz + hr + 2)
    kit.lantern(g, hx + 2, G, hz + hr + 2)

    # Four family yurts round the bonfire ring.
    fams = [(10, 30, 'white', 'orange', 'east'), (38, 30, 'light_gray', 'red', 'west'), (14, 41, 'white', 'brown', 'north'), (34, 41, 'white', 'red', 'north')]
    for i, (x, z, cloth, trim, door) in enumerate(fams):
        nomad_camp_yurt(g, x, z, G, 4, cloth, trim, door, seed=222 + i)
    firepit(g, c, 30, G, lit=True, wood='acacia', seed=223)
    for (x, z) in ((c - 1, 30), (c + 1, 30)):
        g.set(x, G + 1, z, 'acacia_fence')
    kit.log(g, c - 1, G + 2, 30, 'stripped_acacia_log', 'x')
    kit.log(g, c, G + 2, 30, 'stripped_acacia_log', 'x')
    kit.log(g, c + 1, G + 2, 30, 'stripped_acacia_log', 'x')
    g.set(c, G + 1, 30, 'iron_chain[axis=y]')
    clue(g, c + 2, G, 33, 'note', 'nomad_song')

    # The corral in the east: acacia rails, hay, a trough, a shelter at its back.
    for x in range(33, 46):
        for z in range(6, 21):
            if x in (33, 45) or z in (6, 20):
                g.set(x, G, z, 'acacia_fence')
            elif hash3(x, 0, z, 224) < 0.25:
                g.set(x, G - 1, z, 'coarse_dirt')
    g.set(33, G, 14, 'acacia_fence_gate[facing=east,in_wall=false,open=false,powered=false]')
    for z in range(7, 12):
        for x in (43, 44):
            g.set(x, G, z, 'hay_block[axis=y]' if (x + z) % 3 else 'hay_block[axis=x]')
    g.set(36, G, 19, 'water_cauldron[level=3]')
    g.set(37, G, 19, 'water_cauldron[level=3]')
    for x in range(40, 46):
        for z in range(15, 21):
            g.set(x, G + 3, z, 'acacia_slab[type=bottom]')
    for (x, z) in ((40, 15), (40, 20), (45, 15), (45, 20)):
        for k in range(3):
            g.set(x, G + k, z, 'acacia_fence')

    # Felt drying on racks in the west; a cart of rolled felts.
    for i, z in enumerate((7, 10, 13)):
        for x in range(4, 12):
            g.set(x, G + 2, z, 'acacia_fence' if x in (4, 11) else ['red_carpet', 'white_carpet', 'orange_carpet'][(x + i) % 3])
        for x in (4, 11):
            g.set(x, G, z, 'acacia_fence')
            g.set(x, G + 1, z, 'acacia_fence')
    K.wagon(g, 4, 20, G, 'x', wood='acacia', cover='orange', seed=225)
    for i in range(3):
        g.set(5 + i, G + 2, 20, 'red_wool' if i % 2 else 'white_wool')

    # The prayer-post: a tall pole, banners and a ram's skull, ribbons on its stays.
    for k in range(8):
        g.set(c, G + k, 22, 'acacia_fence')
    for (k, col, f) in ((6, 'red', 'north'), (5, 'yellow', 'south'), (4, 'blue', 'east'), (7, 'white', 'west')):
        K.banner(g, c + kit.FACE[f][0], G + k, 22 + kit.FACE[f][1], col, f)
    K.skull(g, c, G + 8, 22, rot=8)

    stepping(g, [(c, 47), (c, 33)], G - 1, [('dirt_path', 3), ('coarse_dirt', 1)], seed=226, gap=0.1, width=3)
    stepping(g, [(c, 27), (c, hz + hr + 2)], G - 1, [('dirt_path', 3), ('coarse_dirt', 1)], seed=227, gap=0.1, width=2)
    plants(g, 0, 0, 47, 47, G, [('short_dry_grass', 5), ('tall_dry_grass', 2), ('short_grass', 2), ('dead_bush', 1), ('bush', 1)], 0.16, seed=228)
    mob(g, 40, G, 12, 'dune_strider', 2)
    mob(g, 4, G, 33, 'highland_ram', 2)
    return K.finish(g)


# ================================================================ more pieces

def ramp(g, cx, cz, r_in, r_out, y0, y1, a0=225.0, T=11.0, wood='spruce', slab=None, full=None):
    """A spiral ramp round the inside of a round wall (between r_in and r_out), rising half a block
    a step (slabs and full blocks, so it is walked: stair blocks on a curve would turn into corner
    pieces), `T` blocks a turn from layer y0 to y1, with three clear blocks over every step. Floors
    laid before it are cut where it passes. Returns the step cells."""
    d = dist(g, cx, cz)
    X, Z = g.xz()
    A = np.degrees(np.arctan2(Z - cz, X - cx)) % 360
    span = y1 - y0
    steps = []
    for (x, z) in np.argwhere((d > r_in) & (d <= r_out)):
        h0 = ((A[x, z] - a0) % 360) / 360 * T
        for k in range(8):
            hh = h0 + k * T
            if hh > span:
                break
            q = int(2 * hh)
            steps.append((x, z, y0 + q // 2, q % 2 == 0))
    for (x, z, y, _) in steps:
        for h in (1, 2, 3):
            g.set(x, y + h, z, 'air')
    half_block = slab or f'{wood}_slab[type=bottom]'
    whole = full or f'{wood}_planks'
    for (x, z, y, is_slab) in steps:
        g.set(x, y, z, half_block if is_slab else whole)
    return steps


def lyre_strings(g, xs, z, y_low, y_high):
    """Harp strings: iron chains from a low bar up to a curving high one."""
    for x in xs:
        kit.chain(g, x, y_low, y_high(x), z)


# ================================================================ The Wind Harp (windworn highlands)

def wind_harp():
    """A giant's harp of stone on a highland crag: two great pillars and a curved neck, strung with
    iron chains that sing in the wind, copper pegs along the top. Before it, on a terrace cut into
    the rock, five posts of different heights carry bells; strike them from the shortest to the
    tallest and the cairn below the terrace opens. A shepherd's bothy and pen shelter under the
    crag, and prayer flags snap on lines across the ridge."""
    G = 3
    g = K.grid(48, 40, 48)
    c = 24
    ground(g, 0, 0, 47, 47, G - 1, [('stone', 3), ('gravel', 1), ('coarse_dirt', 2), ('moss_block', 1)], 0.45, seed=241)
    # The crag: a long rock ridge, grassed on top, its top cut flat for the harp.
    K.mound(g, c, 18, G, 17, 5, 9, [('stone', 4), ('andesite', 2), ('tuff', 1), ('cobblestone', 1)], seed=242, top='grass_block[snowy=false]')
    T = G + 5
    for x in range(8, 41):
        for z in range(12, 25):
            if g.get(x, T - 1, z) is not None:
                for y in range(T, G + 10):
                    g.set(x, y, z, 'air')
                g.set(x, T - 1, z, 'stone_bricks' if 14 <= z <= 22 and 10 <= x <= 38 else 'grass_block[snowy=false]')
    # The harp: two pillars, a soundboard below, a neck curving between them, the strings.
    stone = [('stone_bricks', 4), ('cracked_stone_bricks', 1), ('mossy_stone_bricks', 1)]
    for px in (11, 36):
        for y in range(T, T + 19):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if abs(dx) + abs(dz) < 2 or y < T + 3 or y > T + 15:
                        g.set(px + dx, y, 18 + dz, pick(stone, px + dx, y, 18 + dz, 243))
        g.set(px, T + 19, 18, 'chiseled_stone_bricks')
        g.set(px, T + 20, 18, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    for x in range(12, 36):
        g.set(x, T + 2, 18, 'polished_andesite')
        kit.stairs(g, x, T + 2, 17, 'polished_andesite', 'south', 'top')
        kit.stairs(g, x, T + 2, 19, 'polished_andesite', 'north', 'top')
    neck = lambda x: T + 13 + int(round(4 * math.sin((x - 11) / 25 * math.pi)))
    for x in range(12, 36):
        y = neck(x)
        g.set(x, y, 18, 'stone_bricks')
        g.set(x, y + 1, 18, 'stone_brick_slab[type=bottom]')
        if x % 3 == 0:
            g.set(x, y + 1, 18, 'oxidized_copper')
            g.set(x, y + 2, 18, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    lyre_strings(g, range(13, 35, 2), 18, T + 3, lambda x: neck(x) - 1)
    # The terrace before it: five bell posts, shortest to tallest unordered, buttons facing south.
    heights = [4, 2, 6, 3, 5]
    posts = {}
    for i, h in enumerate(heights):
        x = 17 + i * 3
        for k in range(h):
            g.set(x, T + k, 22, 'stone_bricks' if k < h - 1 else 'chiseled_stone_bricks')
        g.set(x, T + h, 22, 'bell[attachment=floor,facing=south,powered=false]')
        K.button(g, x, T + 1, 23, 'south')
        posts[h] = (x, T + 1, 23)
    order = [posts[h] for h in sorted(posts)]
    clue(g, 31, T, 21, 'glyph', 'harp_carving')
    # Steps down from the terrace to the cairn and the path.
    for i in range(T - G + 1):
        for x in range(22, 27):
            kit.stairs(g, x, T - 1 - i, 25 + i, 'stone_brick', 'north')
            for h in (1, 2, 3):
                g.set(x, T - 1 - i + h, 25 + i, 'air')
    # The cairn at the crag's foot: a beehive of stones, its door sealed.
    K.mound(g, c, 36, G, 3.4, 3.2, 3.4, [('cobblestone', 2), ('mossy_cobblestone', 1), ('andesite', 1)], seed=244)
    g.fill(c - 1, G, 35, c + 1, G + 1, 37, 'air')
    # The doorway cut through the mound's outer stones; the sealed door stands a step in.
    g.fill(c - 1, G, 33, c + 1, G + 1, 33, 'air')
    g.chest(c, G, 36, 'north', fl(VAULT, 'highland'))
    door = [(x, y, 34) for x in (c - 1, c, c + 1) for y in (G, G + 1)]
    for d_ in door:
        g.set(*d_, 'mossy_cobblestone')
    seal(g, c, G, 32, 'order', 'harp_posts', order, door)
    g.set(c, G + 3, 36, 'cobblestone_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')

    # The shepherd's bothy under the crag: drystone walls, a turf roof, a pen beside it.
    bx0, bz0, bx1, bz1 = 4, 30, 11, 36
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            if x in (bx0, bx1) or z in (bz0, bz1):
                for k in range(3):
                    g.set(x, G + k, z, pick([('cobblestone', 2), ('mossy_cobblestone', 1), ('stone', 1)], x, G + k, z, 245))
    kit.gable(g, bx0, bz0, bx1, bz1, G + 3, 'spruce', axis='x', overhang=1, gable_fill='cobblestone')
    for x in range(bx0 - 1, bx1 + 2):
        for z in range(bz0 - 1, bz1 + 2):
            top = max((y for y in range(G + 3, G + 9) if g.get(x, y, z) is not None), default=None)
            if top is not None and 'spruce' in (g.get(x, top, z) or ''):
                g.set(x, top + 1, z, 'moss_carpet')
    g.fill(bx0 + 1, G - 1, bz0 + 1, bx1 - 1, G - 1, bz1 - 1, 'spruce_planks')
    g.fill(8, G, bz0, 8, G + 1, bz0, 'air')
    kit.door(g, 8, G, bz0, 'spruce', 'north')
    g.set(bx0 + 1, G, bz1 - 1, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    g.set(bx1 - 1, G, bz1 - 1, 'barrel[facing=up,open=false]')
    kit.bed(g, bx1 - 1, G, bz0 + 1, 'white', 'south')
    g.set(bx0 + 1, G, bz0 + 1, 'loom[facing=south]')
    for x in range(12, 20):
        for z in range(37, 45):
            if x in (12, 19) or z in (37, 44):
                g.set(x, G, z, 'spruce_fence')
    g.set(12, G, 40, 'spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]')
    g.set(18, G, 43, 'hay_block[axis=y]')
    g.set(17, G, 43, 'water_cauldron[level=3]')

    # Prayer flags on lines across the ridge, poles standing on whatever ground is under them.
    for (x0, x1, z) in ((6, 16, 13), (32, 42, 13)):
        tops = []
        for x in (x0, x1):
            y = surface(g, x, z, G)
            for k in range(6):
                g.set(x, y + k, z, 'spruce_fence')
            tops.append(y + 5)
        line = min(tops)
        for x in range(x0 + 1, x1):
            sag = int(round(math.sin((x - x0) / (x1 - x0) * math.pi)))
            g.set(x, line - sag, z, ['red_wool', 'yellow_wool', 'blue_wool', 'white_wool', 'green_wool'][x % 5])
    stepping(g, [(c, 47), (c, 40)], G - 1, [('gravel', 2), ('cobblestone', 1)], seed=246, gap=0.2, width=2)
    plants(g, 0, 0, 47, 47, G, [('short_grass', 5), ('fern', 2), ('cornflower', 1), ('azure_bluet', 1), ('short_dry_grass', 2)], 0.2, seed=247)
    plants(g, 0, 0, 47, 47, T, [('short_grass', 4), ('cornflower', 1)], 0.15, seed=248)
    mob(g, 15, G, 41, 'highland_ram', 2)
    return K.finish(g)


# ================================================================ The Aurora Shrine (glimmerfrost)

AURORA = ['light_blue', 'cyan', 'lime', 'magenta', 'purple']


def aurora_shrine():
    """A shrine of ice to the lights in the northern sky: a round ice floor ringed by eight pillars
    of packed ice with sea-lantern capitals, under a great dome of glass banded in the aurora's
    colours, open at its crown. Three candles stand on prismarine pedestals, cyan, lime and
    magenta, the three colours of the sky; light them and the ice at the shrine's heart melts from
    over its gift. Three tall prisms, one of each colour, keep watch round it across a frozen moat."""
    G = 3
    g = K.grid(48, 34, 48)
    c = 24
    d = dist(g, c, c)
    ground(g, 0, 0, 47, 47, G - 1, [('snow_block', 5), ('packed_ice', 2), ('blue_ice', 1)], 0.45, seed=261)
    # The moat: a ring of blue ice, a bridge of packed ice on four sides.
    g.a[:, G - 1, :][(d > 13.4) & (d <= 15.6)] = g.pid('blue_ice')
    g.a[:, G - 2, :][(d > 13.4) & (d <= 15.6)] = g.pid('water[level=0]')
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for r in range(13, 17):
            for w in (-1, 0, 1):
                x, z = c + dx * r + dz * w, c + dz * r + dx * w
                g.set(x, G - 1, z, 'packed_ice')
                if abs(w) == 1:
                    g.set(x, G, z, 'prismarine_brick_slab[type=bottom]')
    # The floor: packed ice, a prismarine ring, an inlaid star of the three colours.
    g.a[:, G - 1, :][d <= 11.6] = g.pid('packed_ice')
    g.a[:, G - 1, :][(d > 10.4) & (d <= 11.6)] = g.pid('prismarine_bricks')
    g.a[:, G, :][(d > 11.6) & (d <= 12.6)] = g.pid('prismarine_brick_slab[type=bottom]')
    for i, col in enumerate(('cyan', 'lime', 'magenta')):
        t = math.radians(90 + i * 120)
        for r in range(2, 9):
            x, z = int(round(c + math.cos(t) * r)), int(round(c + math.sin(t) * r))
            g.set(x, G - 1, z, f'{col}_stained_glass')
            g.set(x, G - 2, z, 'sea_lantern')
    # Eight pillars and the banded dome.
    for i, (x, z) in enumerate(around(c, c, 9.5, 8, 22.5)):
        for k in range(7):
            g.set(x, G + k, z, 'packed_ice' if k not in (0, 6) else 'prismarine_bricks')
        g.set(x, G + 7, z, 'sea_lantern')
    for x in range(c - 12, c + 13):
        for z in range(c - 12, c + 13):
            for y in range(G + 7, G + 18):
                k = y - (G + 7)
                rr = math.sqrt((x - c) ** 2 + (z - c) ** 2 + (k * 0.95) ** 2)
                if 9.6 <= rr <= 10.6 and math.hypot(x - c, z - c) > 1.9:
                    band = min(len(AURORA) - 1, int(k / 2.2))
                    g.set(x, y, z, f'{AURORA[band]}_stained_glass')
    # Ribs of prismarine up the dome, between the pillars.
    for i in range(8):
        t = math.radians(22.5 + i * 45)
        for k in range(11):
            rr = math.sqrt(max(0.0, 10.1 ** 2 - (k * 0.95) ** 2))
            x, z = int(round(c + math.cos(t) * rr)), int(round(c + math.sin(t) * rr))
            if math.hypot(x - c, z - c) > 1.9:
                g.set(x, G + 7 + k, z, 'prismarine_bricks')
    # The three candles on their pedestals, the seal, the gift under the ice.
    lights = []
    for i, col in enumerate(('cyan', 'lime', 'magenta')):
        t = math.radians(90 + i * 120)
        x, z = int(round(c + math.cos(t) * 5)), int(round(c + math.sin(t) * 5))
        g.set(x, G, z, 'prismarine_bricks')
        g.set(x, G + 1, z, 'prismarine_brick_slab[type=bottom]')
        K.candles(g, x, G + 2, z, 3, lit=False, color=col)
        lights.append((x, G + 2, z))
    g.set(c, G, c - 2, 'dark_prismarine')
    seal(g, c, G + 1, c - 2, 'light', 'aurora_candles', lights, [(c, G - 1, c)])
    g.set(c, G - 1, c, 'blue_ice')
    g.fill(c - 1, G - 3, c - 1, c + 1, G - 3, c + 1, 'packed_ice')
    g.chest(c, G - 2, c, 'north', 'afterworld:chests/star_vault_glimmer')
    clue(g, c + 3, G, c + 3, 'glyph', 'aurora_prayer')
    # The three prisms across the moat: tall shafts of ice and coloured glass, amethyst at the top.
    for i, col in enumerate(('cyan', 'lime', 'magenta')):
        t = math.radians(150 + i * 120)
        x, z = int(round(c + math.cos(t) * 19)), int(round(c + math.sin(t) * 19))
        h = 13 + i * 2
        for k in range(h):
            r = 1.3 * (1 - k / h) + 0.4
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if math.hypot(dx, dz) <= r:
                        g.set(x + dx, G + k, z + dz, f'{col}_stained_glass' if (k + dx + dz) % 3 == 0 else 'packed_ice')
        K.cluster(g, x, G + h, z)
        g.set(x, G - 1, z, 'sea_lantern')
    # Ice sculptures by the bridges: a kneeling figure, a bird.
    for (x, z) in ((c - 3, c + 16), (c + 3, c + 16)):
        g.set(x, G, z, 'packed_ice')
        g.set(x, G + 1, z, 'ice')
        g.set(x, G + 2, z, 'packed_ice')
    kit.snow(g, depth=1, rate=0.6, seed=262)
    plants(g, 0, 0, 47, 47, G, [('snow[layers=2]', 4), ('snow[layers=1]', 3), ('short_grass', 1)], 0.12, seed=263)
    mob(g, c + 10, G + 1, c - 16, 'rime_moth', 2)
    return K.finish(g)


# ================================================================ The Starfall Crater (ember steppe, hourglass dunes)

def starfall_crater():
    """A crater round a fallen star that has not finished glowing: the rim thrown up in a ring of
    scorched earth, rays of black rock flung out across the plain, fissures still glowing in the
    bowl, and at its heart the star itself, a split boulder of black glass with light inside. A
    stargazer's camp clings to the rim: a tent, a telescope on a tripod, scaffolding and a rope
    down into the bowl."""
    G = 10
    g = K.grid(48, 34, 48)
    c = 24
    d = dist(g, c, c)
    ground(g, 0, 0, 47, 47, G - 1, [('coarse_dirt', 3), ('gravel', 1), ('red_sand', 1)], 0.35, seed=281)
    # The bowl, and the rim thrown up round it.
    K.bowl(g, c, c, G - 1, 15, 8, [('blackstone', 3), ('basalt[axis=y]', 2), ('tuff', 2), ('magma_block', 1), ('obsidian', 1)], seed=282)
    for (x, z) in np.argwhere((d > 14.0) & (d <= 18.5)):
        h = int(round(2.2 * (1 - abs(d[x, z] - 15.6) / 2.9) + float(vnoise(x, 0, z, 3.0, 283)) - 0.4))
        for k in range(max(0, h)):
            g.set(x, G + k, z, pick([('coarse_dirt', 3), ('blackstone', 1), ('gravel', 1), ('tuff', 1)], x, G + k, z, 284))
    # Rays flung out across the plain.
    for i in range(9):
        t = math.radians(i * 40 + 13)
        for r in range(16, 24):
            x, z = int(round(c + math.cos(t) * r)), int(round(c + math.sin(t) * r))
            if inside(g, x, z) and hash3(x, 0, z, 285) < 0.8:
                g.set(x, G - 1, z, 'blackstone' if r % 3 else 'basalt[axis=y]')
                if hash3(x, 1, z, 286) < 0.25:
                    g.set(x, G, z, 'blackstone_slab[type=bottom]')
    # Fissures still glowing in the bowl.
    for i in range(6):
        t = math.radians(i * 60 + 25)
        for r in range(4, 13):
            x, z = int(round(c + math.cos(t + r * 0.07) * r)), int(round(c + math.sin(t + r * 0.07) * r))
            bottom = int(round(G - 1 - 8 * (1 - (r / 15) ** 2))) - 1
            g.set(x, bottom, z, 'magma_block' if r % 4 else 'shroomlight')
    # The star: a split boulder of black glass with light inside.
    base = G - 9
    for x in range(c - 3, c + 4):
        for z in range(c - 3, c + 4):
            for y in range(base, base + 6):
                q = ((x - c) / 3.2) ** 2 + ((y - base - 1.5) / 3.4) ** 2 + ((z - c) / 3.2) ** 2
                if q <= 1.0 + (float(vnoise(x, y, z, 2.0, 287)) - 0.5) * 0.4:
                    g.set(x, y, z, pick([('obsidian', 3), ('crying_obsidian', 2), ('blackstone', 1)], x, y, z, 288))
    for y in range(base + 1, base + 6):
        g.set(c, y, c, 'air')
        g.set(c + 1, y, c, 'air')
    g.set(c, base, c, 'shroomlight')
    g.set(c + 1, base, c, 'amethyst_block')
    for (x, y, z) in ((c, base + 1, c), (c + 1, base + 1, c)):
        K.cluster(g, x, y, z)
    for (x, y, z, f) in ((c - 1, base + 3, c, 'east'), (c + 2, base + 3, c, 'west')):
        g.set(x, y, z, 'crying_obsidian')
    g.set(c, base + 6, c, 'end_rod[facing=up]')
    g.chest(c + 4, base + 1, c, 'west', 'afterworld:chests/star_vault_steppe')
    g.set(c + 4, base, c, 'blackstone')
    g.set(c + 5, base + 1, c, 'air')
    # The stargazer's camp on the north rim: a tent, a telescope, the chart, scaffolding and rope.
    # Its ledge reaches out over the bowl to the scaffolding's head.
    ty = G + 2
    for x in range(c - 6, c + 5):
        for z in range(3, 11):
            g.set(x, ty - 1, z, 'coarse_dirt')
            for y in range(ty, ty + 6):
                g.set(x, y, z, 'air')
    K.tent(g, c - 5, 6, 5, ty, 'white', axis='x')
    kit.table(g, c - 3, ty, 6)
    clue(g, c - 4, ty, 6, 'note', 'star_chart')
    g.set(c - 2, ty, 6, 'lectern[facing=south,has_book=true,powered=false]')
    # The telescope: a tripod of fences, a tube of copper pointing at the sky.
    tx, tz = c + 2, 6
    for (dx, dz) in ((-1, -1), (1, -1), (0, 1)):
        g.set(tx + dx, ty, tz + dz, 'spruce_fence')
    g.set(tx, ty + 1, tz, 'spruce_fence')
    g.set(tx, ty + 2, tz, 'copper_block')
    g.set(tx, ty + 3, tz + 1, 'copper_block')
    g.set(tx, ty + 4, tz + 2, 'cut_copper')
    g.set(tx, ty + 5, tz + 3, 'glass')
    g.set(tx + 1, ty, tz + 1, 'barrel[facing=up,open=false]')
    # Scaffolding (climbable) down into the bowl.
    for k in range(base + 1, ty):
        g.set(c, k, 11, 'scaffolding[bottom=false,distance=0,waterlogged=false]')
    g.set(c + 1, ty - 1, 10, 'campfire[lit=true,facing=north,signal_fire=false,waterlogged=false]')
    plants(g, 0, 0, 47, 47, G, [('short_dry_grass', 4), ('dead_bush', 2), ('tall_dry_grass', 1)], 0.08, seed=289)
    mob(g, c - 6, base + 2, c - 5, 'cinder_beetle', 2)
    return K.finish(g)


# ================================================================ The Cinder Watchtower (ashen moor)

def cinder_watchtower():
    """A broken black tower on the moor, the Ashen cult camped in its fallen court: a round keep
    of blackstone with a ramp winding up inside it past a guardroom to the roofless top, where the
    cult's leader keeps their spoils; below, in a court whose walls are half down, black tents, a
    ring of soul-fire braziers round a blood altar, iron cages, a forge, and a trench of lava
    crossed by a bridge."""
    G = 3
    g = K.grid(48, 44, 48)
    tx, tz, R = 15, 14, 5.6
    ground(g, 0, 0, 47, 47, G - 1, [('coarse_dirt', 3), ('gravel', 2), ('basalt[axis=y]', 1), ('blackstone', 1)], 0.5, seed=301)
    blk = [('polished_blackstone_bricks', 4), ('cracked_polished_blackstone_bricks', 2), ('blackstone', 1), ('gilded_blackstone', 0.15)]
    # The keep: a round wall of blackstone, two floors, the ramp inside.
    d = dist(g, tx, tz)
    H = 27
    for y in range(G - 1, G + H):
        wall = (d > R - 1.15) & (d <= R + 0.35)
        for (x, z) in np.argwhere(wall):
            g.set(x, y, z, pick(blk, x, y, z, 302))
        if y >= G:
            g.a[:, y, :][d <= R - 1.15] = g.pid('air')
    g.a[:, G - 1, :][d <= R - 1.15] = g.pid('polished_blackstone')
    floors = [G + 9, G + 18]
    for f in floors:
        g.a[:, f - 1, :][d <= R - 1.15] = g.pid('dark_oak_planks')
    ramp(g, tx, tz, R - 3.2, R - 1.15, G, floors[-1], a0=200.0, T=9.0, wood='dark_oak')
    # The broken crown: the top knocked away unevenly; a few merlons still standing.
    K.erode(g, tx - 7, tz - 7, tx + 7, tz + 7, G + 20, G + H + 1, seed=303, scale=2.6)
    # Slits, a door to the court, banners.
    for f in [G] + floors:
        for a in (0, 90, 180, 270):
            t = math.radians(a + 45)
            x, z = int(round(tx + math.cos(t) * R)), int(round(tz + math.sin(t) * R))
            g.set(x, f + 1, z, 'iron_bars[north=false,south=false,east=false,west=false,waterlogged=false]')
    g.fill(tx, G, tz + 5, tx, G + 1, tz + 6, 'air')
    kit.door(g, tx, G, tz + 5, 'dark_oak', 'south')
    for x in (tx - 1, tx + 1):
        K.banner(g, x, G + 3, tz + 7, 'black', 'south')
    # The guardroom: a rack of weapons, a table, a lantern.
    g.set(tx - 2, floors[0], tz - 2, 'grindstone[face=floor,facing=north]')
    g.set(tx + 2, floors[0], tz - 2, 'barrel[facing=up,open=false]')
    kit.table(g, tx, floors[0], tz)
    kit.lantern(g, tx, floors[0] + 1, tz, soul=True)
    # The top: the leader and their spoils.
    g.chest(tx - 2, floors[1], tz - 1, 'south', fl(VAULT, 'ash'))
    elite(g, tx + 1, floors[1], tz + 1, 'ashen_husk')
    K.brazier(g, tx + 2, floors[1], tz - 2, PAL['black'], lit=True, soul=True, h=1)

    # The court: walls half down, the gate gap to the south.
    for (a, b, c2, e) in ((4, 4, 43, 4), (4, 4, 4, 43), (43, 4, 43, 43), (4, 43, 20, 43), (27, 43, 43, 43)):
        K.ruin_wall(g, a, b, c2, e, G, 4, 'polished_blackstone_bricks', seed=304 + a + b, decay=0.6)
    for x in (20, 27):
        for k in range(5):
            g.set(x, G + k, 43, 'polished_blackstone_bricks')
        K.campfire(g, x, G + 5, 43, lit=True)
    # The lava trench and its bridge.
    for x in range(5, 43):
        z0 = 27 + int(round(math.sin(x / 6.0)))
        for z in (z0, z0 + 1):
            g.set(x, G - 2, z, 'blackstone')
            g.set(x, G - 1, z, 'lava[level=0]' if not (21 <= x <= 25) else 'polished_blackstone_bricks')
        for z in (z0 - 1, z0 + 2):
            g.set(x, G - 1, z, 'blackstone')
            if not (21 <= x <= 25):
                g.set(x, G, z, 'polished_blackstone_brick_slab[type=bottom]' if hash3(x, 0, z, 305) < 0.5 else 'air')
    for x in (21, 25):
        for z in range(26, 31):
            g.set(x, G, z, 'polished_blackstone_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
    # The ritual ring: six soul-fire braziers round a blood altar.
    rx, rz = 30, 15
    for (x, z) in around(rx, rz, 5, 6, 0):
        K.brazier(g, x, G, z, PAL['black'], lit=True, soul=True, h=1)
    g.fill(rx - 1, G, rz, rx + 1, G, rz, 'polished_blackstone')
    g.set(rx, G + 1, rz, 'red_candle[candles=4,lit=true,waterlogged=false]')
    g.set(rx - 1, G + 1, rz, 'red_candle[candles=2,lit=true,waterlogged=false]')
    K.skull(g, rx + 1, G + 1, rz, rot=8, wither=True)
    disc(g, rx, rz, G - 1, 6.5, 'blackstone', inner=5.6)
    # Tents, cages, the forge, and the orders by the altar tent.
    K.tent(g, 30, 34, 6, G, 'black', axis='x', poles='dark_oak_fence')
    K.tent(g, 8, 34, 5, G, 'red', axis='x', poles='dark_oak_fence')
    K.tent(g, 36, 22, 4, G, 'gray', axis='z', poles='dark_oak_fence')
    clue(g, 34, G, 32, 'note', 'cult_orders')
    for (cx0, cz0) in ((8, 20), (11, 20)):
        for x in range(cx0, cx0 + 2):
            for z in range(cz0, cz0 + 2):
                g.set(x, G + 2, z, 'polished_blackstone_slab[type=bottom]')
        for (x, z) in ((cx0, cz0), (cx0 + 1, cz0), (cx0, cz0 + 1), (cx0 + 1, cz0 + 1)):
            g.set(x, G, z, 'iron_bars[north=false,south=false,east=false,west=false,waterlogged=false]')
            g.set(x, G + 1, z, 'iron_bars[north=false,south=false,east=false,west=false,waterlogged=false]')
    K.skull(g, 8, G, 21, rot=4)
    g.set(38, G, 9, 'anvil[facing=north]')
    g.set(39, G, 9, 'smithing_table')
    g.set(40, G, 9, 'blast_furnace[facing=south,lit=true]')
    K.barrels(g, 37, G, 11, seed=306, n=3)
    for (x, z) in ((24, 8), (35, 40), (10, 38)):
        for k in range(4):
            g.set(x, G + k, z, 'dark_oak_fence')
        K.banner(g, x, G + 3, z + 1, 'red' if x % 2 else 'black', 'south')
    # Charred trees, ash and basalt about the moor.
    for k, (x, z) in enumerate(((2, 2), (45, 3), (45, 45), (1, 46), (24, 46))):
        dead_limbs(g, x, z, G, 6 + k % 3, 'dark_oak_log' if k % 2 else 'stripped_dark_oak_log', seed=310 + k, limbs=3)
    plants(g, 0, 0, 47, 47, G, [('dead_bush', 3), ('short_dry_grass', 2), ('blackstone_slab[type=bottom]', 1), ('basalt[axis=y]', 1)], 0.08, seed=307)
    stepping(g, [(23, 47), (23, 43), (23, 31), (tx, tz + 7)], G - 1, [('gravel', 2), ('blackstone', 1)], seed=308, gap=0.2, width=2)
    mob(g, 27, G, 38, 'ashen_husk', 3)
    return K.finish(g)


# ================================================================ The Colossus Graveyard (whitewaste)

def thick_rib(g, cx, z, y0, span, height, mat='bone_block'):
    """A giant's rib: a parabola of bone two blocks thick across `span`, standing on layer y0."""
    n = span * 4
    for i in range(n + 1):
        t = i / n * 2 - 1
        a = t * span / 2
        y = y0 + height * (1 - t * t)
        for (dx, dy) in ((0, 0), (0, -1), (1 if t < 0 else -1, 0)):
            g.set(int(round(cx + a)) + dx, int(round(y)) + dy, z, f'{mat}[axis=y]')


def colossus_graveyard():
    """Where a colossus lay down to die: its ribcage stands out of the snow in arches tall as a
    hall, its spine runs on into the drift, its skull lies like a house with tusks curled before
    it. Hunters have pitched a lean-to under the ribs and raised a cairn of offerings, and the
    great tracks of something living lead off south. The Rimeback hunt starts here."""
    G = 3
    g = K.grid(48, 30, 48)
    cx = 24
    ground(g, 0, 0, 47, 47, G - 1, [('snow_block', 6), ('packed_ice', 1), ('powder_snow', 1)], 0.55, seed=321)
    # The spine: vertebrae along the middle, each with a spur.
    for z in range(7, 34):
        y = G + 1 + int(round(2 * math.sin(z / 9.0)))
        g.set(cx, y, z, 'bone_block[axis=z]')
        if z % 3 == 0:
            g.set(cx, y + 1, z, 'bone_block[axis=y]')
            g.set(cx - 1, y, z, 'bone_block[axis=x]')
            g.set(cx + 1, y, z, 'bone_block[axis=x]')
    # The ribs: nine arches, tallest at the chest.
    for k, z in enumerate(range(9, 34, 3)):
        span = 22 - abs(k - 4) * 3
        height = 13 - abs(k - 4) * 1.6
        if k in (2, 6):
            # Two ribs broken: their tops lie fallen in the snow beside them.
            for i in range(span // 2):
                g.set(cx - span // 2 + i, G, z + 1, 'bone_block[axis=x]')
            thick_rib(g, cx - span // 4, z, G - 2, span // 2, height * 0.6)
        else:
            thick_rib(g, cx, z, G - 2, span, height)
    # The skull: a great dome of bone, eye sockets black, a jaw, and tusks curling forward.
    kz = 40
    for x in range(cx - 7, cx + 8):
        for z in range(kz - 6, kz + 7):
            for y in range(G - 1, G + 8):
                q = ((x - cx) / 6.6) ** 2 + ((y - G + 1) / 7.2) ** 2 + ((z - kz) / 6.0) ** 2
                if 0.78 <= q <= 1.0:
                    g.set(x, y, z, 'bone_block[axis=y]')
                elif q < 0.78:
                    g.set(x, y, z, 'air')
    for x in (cx - 3, cx + 3):
        for y in (G + 2, G + 3):
            g.set(x, y, kz - 6, 'air')
            g.set(x, y, kz - 5, 'coal_block')
    g.fill(cx - 2, G - 1, kz - 8, cx + 2, G, kz - 6, 'bone_block[axis=z]')
    g.fill(cx - 1, G, kz - 6, cx + 1, G + 1, kz - 6, 'air')
    for s in (-1, 1):
        g.line((cx + s * 5, G, kz - 4), (cx + s * 10, G + 3, kz - 10), 'bone_block[axis=x]')
        g.line((cx + s * 10, G + 3, kz - 10), (cx + s * 9, G + 7, kz - 13), 'bone_block[axis=y]')
        g.line((cx + s * 9, G + 7, kz - 13), (cx + s * 7, G + 8, kz - 12), 'bone_block[axis=y]')
    # A calf's bones beside the great one.
    for k, z in enumerate((12, 14, 16)):
        K.rib(g, 8, z, G - 1, 6, 3, axis='x')
    kit.dome(g, 8, G - 1, 19, 2.4, 'bone_block[axis=y]', inner='air')
    # The hunters' lean-to under the ribs: hides on poles, a fire, racks.
    for z in range(17, 24):
        g.set(cx + 6, G, z, 'spruce_fence')
        g.set(cx + 6, G + 1, z, 'spruce_fence')
        for x in range(cx + 3, cx + 7):
            g.set(x, G + 2, z, 'brown_wool' if (x + z) % 3 else 'white_wool')
    K.campfire(g, cx + 3, G, 25, lit=False)
    for (x, z) in ((cx + 2, 25), (cx + 4, 25)):
        g.set(x, G, z, 'stripped_spruce_log[axis=z]')
    g.chest(cx + 5, G, 18, 'west', fl(VAULT, 'frost'))
    for (x, z) in ((cx + 4, 19), (cx + 4, 21)):
        g.set(x, G, z, 'brown_carpet')
    # The cairn of offerings: stones, candles, a horn.
    K.mound(g, 9, 30, G, 1.8, 2.0, 1.8, [('cobblestone', 2), ('stone', 1)], seed=322)
    K.candles(g, 9, G + 2, 30, 3, lit=False, color='white')
    K.candles(g, 11, G, 29, 2, lit=False, color='blue')
    K.skull(g, 8, G, 32, rot=6)
    # The tracks lead off south.
    for i in range(6):
        x, z = cx + (1 if i % 2 else -1), 34 + i * 2
        g.set(x, G - 1, z, 'packed_ice')
        g.set(x + (1 if i % 2 else -1), G - 1, z, 'packed_ice')
    clue(g, 24, G, 44, 'tracks', 'rimeback_tracks')
    clue(g, 25, G, 46, 'tracks')
    # Drifts against the bones, snow over everything.
    for (x, z, r) in ((16, 13, 3.5), (32, 26, 4), (18, 30, 3), (33, 10, 3), (14, 40, 3.5), (38, 38, 4)):
        K.mound(g, x, z, G, r, 1.6, r, [('snow_block', 1)], seed=int(x * 3 + z))
    kit.snow(g, depth=1, rate=0.9, seed=323)
    plants(g, 0, 0, 47, 47, G, [('snow[layers=2]', 4), ('snow[layers=3]', 2), ('short_grass', 1)], 0.15, seed=324)
    mob(g, 17, G, 20, 'ice_crawler', 2)
    mob(g, 36, G, 30, 'rimehorn')
    return K.finish(g)


# ================================================================ The Bone Orchard (pale reach)

def bone_tree(g, x, z, y0, h, seed=0):
    """A tree of bone: a white trunk, bone limbs, a pale canopy with skull-fruit among the leaves."""
    for k in range(h):
        g.set(x, y0 + k, z, 'bone_block[axis=y]')
    for b, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        if hash3(x, b, z, seed) < 0.75:
            yy = y0 + h - 2 + b % 2
            g.set(x + dx, yy, z + dz, f'bone_block[axis={"x" if dx else "z"}]')
            g.set(x + 2 * dx, yy + 1, z + 2 * dz, f'bone_block[axis={"x" if dx else "z"}]')
    K.canopy(g, x, z, y0 + h, 3.2, 'pale_oak_leaves', seed=seed, h=3)
    for i in range(3):
        fx = x + int(hash3(i, x, z, seed + 1) * 5) - 2
        fz = z + int(hash3(z, i, x, seed + 2) * 5) - 2
        if K.empty(g, fx, y0 + h - 1, fz):
            g.set(fx, y0 + h - 1, fz, 'pale_hanging_moss[tip=true]')


def bone_orchard():
    """A walled orchard of bone trees on the pale reach, where an old gardener tended the dead:
    rows of white trees with pale moss hanging, graves between the rows, a ruined cottage of pale
    stone at the orchard's head, a press-house, a well, a scarecrow, and a lych-gate in the wall."""
    G = 3
    g = K.grid(48, 30, 48)
    P = PAL['pale']
    ground(g, 0, 0, 47, 47, G - 1, [('coarse_dirt', 2), ('pale_moss_block', 3), ('podzol[snowy=false]', 1)], 0.45, seed=341)
    # The wall: a low wall of diorite with pale pillars, broken here and there; the lych-gate south.
    for (a, b, c2, e) in ((3, 3, 44, 3), (3, 3, 3, 44), (44, 3, 44, 44), (3, 44, 21, 44), (26, 44, 44, 44)):
        K.ruin_wall(g, a, b, c2, e, G, 3, 'polished_diorite', seed=342 + a + b, decay=0.35, holes=0.0)
    for x in range(3, 45, 6):
        for z in (3, 44):
            for k in range(4):
                g.set(x, G + k, z, 'polished_diorite' if k < 3 else 'diorite_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
    for x in (21, 26):
        for k in range(5):
            kit.log(g, x, G + k, 44, 'pale_oak_log')
    for x in range(20, 28):
        kit.stairs(g, x, G + 5, 43, 'pale_oak', 'north')
        kit.stairs(g, x, G + 5, 45, 'pale_oak', 'south')
        g.set(x, G + 5, 44, 'pale_oak_planks')
    kit.hang_lantern(g, 23, G + 4, 44, 0, soul=True)
    # The rows of trees, graves between them.
    for i, z in enumerate((10, 18, 26, 34)):
        for j, x in enumerate((9, 17, 31, 39)):
            bone_tree(g, x, z, G, 4 + (i + j) % 3, seed=343 + i * 4 + j)
            if (i + j) % 2 == 0:
                headstone(g, x + 3, z + 2, G, 'south', seed=350 + i * 4 + j)
    # The central walk: pale flags, candles on posts.
    stepping(g, [(23, 47), (23, 44), (23, 12)], G - 1, [('polished_diorite', 2), ('calcite', 2), ('diorite', 1)], seed=344, gap=0.12, width=3)
    for z in range(16, 40, 6):
        for x in (20, 26):
            g.set(x, G, z, 'pale_oak_fence')
            K.candles(g, x, G + 1, z, 1, lit=True, color='white')
    # The gardener's cottage at the head, its roof half fallen; the press-house beside it.
    room, door = log_cabin(g, 15, 4, 29, 10, G, wood='pale_oak', roof='pale_oak', door_side='south', door_at=22, chimney='east', porch=False, seed=345)
    rx0, rz0, rx1, rz1, ry = room
    K.erode(g, 14, 2, 22, 12, G + 5, G + 12, seed=346, scale=2.0)
    kit.bed(g, rx0, ry, rz0, 'white', 'north')
    kit.table(g, rx0 + 4, ry, rz0 + 2, top='pale_oak_pressure_plate', leg='pale_oak_fence')
    clue(g, rx0 + 5, ry, rz0 + 2, 'note', 'orchard_gardener')
    g.chest(rx1, ry, rz0, 'west', fl(VAULT, 'pale'))
    g.set(rx1 - 1, ry, rz0, 'composter[level=6]')
    g.set(rx1, ry, rz1, 'barrel[facing=up,open=false]')
    kit.lantern(g, rx0 + 6, ry + 2, rz0 + 3, hanging=True, soul=True)
    for x in range(32, 39):
        for z in range(5, 11):
            if x in (32, 38) and z in (5, 10):
                for k in range(4):
                    kit.log(g, x, G + k, z, 'pale_oak_log')
    for x in range(31, 40):
        for z in range(4, 12):
            g.set(x, G + 4, z, 'pale_oak_slab[type=bottom]')
    g.set(34, G, 7, 'barrel[facing=up,open=false]')
    g.set(35, G, 7, 'barrel[facing=up,open=false]')
    g.set(36, G, 8, 'cauldron')
    g.set(34, G, 8, 'piston[extended=false,facing=down]')
    # The well, the scarecrow.
    wx, wz = 12, 38
    disc(g, wx, wz, G - 1, 1.6, 'cobblestone')
    for (x, z) in np.argwhere((dist(g, wx, wz) <= 1.6) & (dist(g, wx, wz) > 0.6)):
        g.set(x, G, z, 'polished_diorite')
    g.set(wx, G - 1, wz, 'water[level=0]')
    g.set(wx, G - 2, wz, 'water[level=0]')
    for (x, z) in ((wx - 1, wz), (wx + 1, wz)):
        g.set(x, G + 1, z, 'pale_oak_fence')
    for x in range(wx - 1, wx + 2):
        kit.log(g, x, G + 2, wz, 'stripped_pale_oak_log', 'x')
    for k in range(2):
        g.set(35, G + k, 38, 'pale_oak_fence')
    g.set(35, G + 2, 38, 'hay_block[axis=y]')
    g.set(35, G + 3, 38, 'carved_pumpkin[facing=south]')
    g.set(34, G + 2, 38, 'pale_oak_fence')
    g.set(36, G + 2, 38, 'pale_oak_fence')
    plants(g, 0, 0, 47, 47, G, [('pale_moss_carpet[bottom=true,east=none,north=none,south=none,west=none]', 4), ('short_grass', 2),
                                  ('closed_eyeblossom', 2), ('dead_bush', 1), ('bone_block[axis=y]', 0.3)], 0.2, seed=347)
    mob(g, 25, G, 22, 'mourning_shade', 2)
    return K.finish(g)


# ================================================================ The Beached Wreck (bone shore, breakstone shore)

def hull(g, x0, zc, y0, length, beam, depth, heel=0.35, seed=0, broken_from=None):
    """A ship's hull lying along x from x0: planked sides curving up from the keel, heeled over
    (the deck tipped toward +z by `heel` per block of height), ribs showing where the planks are
    gone after `broken_from`. Returns a function giving the deck's z-offset at a height."""
    for i in range(length):
        x = x0 + i
        t = (i - length / 2) / (length / 2)
        w = beam * math.sqrt(max(0.0, 1 - abs(t) ** 3.2))
        broken = broken_from is not None and i >= broken_from
        for k in range(depth + 1):
            half = w * math.sqrt(k / depth) if k else 0.6
            lean = k * heel
            for side in (-1, 1):
                z = int(round(zc + side * half + lean))
                if broken and k > 1 and hash3(i, k, side, seed) < 0.6:
                    if i % 3 == 0:
                        g.set(x, y0 + k, z, 'stripped_dark_oak_log[axis=y]')
                    continue
                g.set(x, y0 + k, z, 'dark_oak_planks' if k < depth - 1 else 'spruce_planks')
            if k == 0:
                for z in range(int(round(zc - 1)), int(round(zc + 2))):
                    g.set(x, y0, z, 'dark_oak_log[axis=x]')
            if k == depth:
                for z in range(int(round(zc - half + lean)) + 1, int(round(zc + half + lean))):
                    if not (broken and hash3(i, z, 7, seed) < 0.5):
                        g.set(x, y0 + k, z, 'spruce_planks' if hash3(x, z, 8, seed) < 0.85 else 'spruce_trapdoor[facing=north,half=top,open=false]')
            elif 0 < k < depth:
                for z in range(int(round(zc - half + lean)) + 1, int(round(zc + half + lean))):
                    g.set(x, y0 + k, z, 'air')
    return lambda k: k * heel


def beached_wreck():
    """A galleon driven onto the sand and heeled over: its hull planked dark, gaping where the sea
    tore it, the ribs showing; its mast down across the beach with the sail still tangled on it; a
    captain's cabin at the stern with its windows broken. Its cargo has washed up along the tide
    line, and wreckers have pitched a camp by a fire to pick it over."""
    G = 3
    g = K.grid(48, 28, 48)
    ground(g, 0, 0, 47, 47, G - 1, [('sand', 6), ('gravel', 1), ('clay', 1)], 0.6, seed=361)
    # The sandbar it lies on.
    K.mound(g, 24, 22, G - 2, 18, 2, 9, [('sand', 5), ('gravel', 1)], seed=362)
    y0 = G - 1
    hull(g, 9, 21, y0, 28, 5.5, 7, heel=0.4, seed=363, broken_from=19)
    # The stern castle: a cabin raised on the deck, its windows broken.
    for x in range(9, 15):
        for k in range(7, 11):
            for z in range(17, 27):
                zz = z + int(round(k * 0.4))
                edge = x in (9, 14) or z in (17, 26)
                if edge:
                    g.set(x, y0 + k, zz, 'dark_oak_planks' if k < 10 else 'dark_oak_slab[type=bottom]')
                elif k < 10:
                    g.set(x, y0 + k, zz, 'air')
    for z in (19, 21, 23):
        g.set(9, y0 + 8, z + 3, 'glass_pane')
    g.set(13, y0 + 7, 23, 'lantern[hanging=false]')
    g.set(11, y0 + 7, 24, 'barrel[facing=up,open=false]')
    # The hold: the sunken cache among split crates.
    g.chest(18, y0 + 1, 21, 'north', 'afterworld:chests/sunken_cache')
    K.barrels(g, 21, y0 + 1, 20, seed=364, n=4)
    g.set(16, y0 + 1, 22, 'cobweb')
    # The mast, down across the beach, its sail tangled; the bowsprit.
    g.line((24, y0 + 7, 24), (40, G, 38), 'spruce_log[axis=x]')
    for i in range(6):
        x, z = 30 + i, 30 + i
        g.set(x, G, z + 1, 'white_carpet')
        g.set(x + 1, G, z + 2, 'white_carpet' if i % 2 else 'white_wool')
    g.line((36, y0 + 4, 24), (42, y0 + 6, 25), 'stripped_spruce_log[axis=x]')
    # Cargo along the tide line: barrels, rope, a figurehead.
    for (x, z) in ((6, 34), (7, 35), (11, 38), (27, 9), (31, 8), (38, 12)):
        g.set(x, G, z, 'barrel[facing=north,open=false]' if hash3(x, 0, z, 365) < 0.6 else 'spruce_planks')
    g.set(41, G, 22, 'spruce_planks')
    g.set(41, G + 1, 22, 'carved_pumpkin[facing=east]')
    # The wreckers' camp: a tent, a fire, a rowboat turned over, nets drying on poles.
    K.tent(g, 8, 8, 5, G, 'brown', axis='x')
    firepit(g, 16, 10, G, lit=True, seed=366)
    for x in range(30, 36):
        for z in (37, 38):
            g.set(x, G, z, 'spruce_slab[type=top]' if 30 < x < 35 else 'spruce_stairs[facing=east,half=top,shape=straight]')
    for (x, z) in ((40, 6), (44, 6)):
        for k in range(3):
            g.set(x, G + k, z, 'spruce_fence')
    for x in range(41, 44):
        g.set(x, G + 2, 6, 'cobweb')
        g.set(x, G + 1, 6, 'cobweb')
    for (x, z) in ((19, 12), (3, 18)):
        K.lantern_post(g, x, G, z, PAL['stone'], 2)
    # Shells and kelp washed up; driftwood.
    plants(g, 0, 0, 47, 47, G, [('dead_bush', 2), ('short_dry_grass', 2), ('tube_coral_fan', 1), ('brain_coral_fan', 1), ('sea_pickle[pickles=1,waterlogged=false]', 1)], 0.05, seed=367)
    for (x, z) in ((4, 26), (36, 44), (13, 44)):
        kit.log(g, x, G, z, 'stripped_spruce_log', 'x')
        kit.log(g, x + 1, G, z, 'stripped_spruce_log', 'x')
    mob(g, 26, G, 34, 'crystal_crab', 2)
    mob(g, 12, G, 30, 'drowned_revenant')
    return K.finish(g)


# ================================================================ The Drowned Shrine (bone shore, breakstone shore)

def drowned_shrine():
    """A temple the tide half took: a nave of prismarine columns standing in sea pools, its roof
    fallen in, side chapels drowned to the knee, an altar at its head with the apprentice's things
    still by it. Its bell tower stands at the shore end, the bell gone and a chain hanging where the
    rope was. A pier runs out from the porch into the water."""
    G = 3
    g = K.grid(48, 34, 48)
    P = PAL['prism']
    ground(g, 0, 0, 47, 47, G - 1, [('sand', 4), ('gravel', 2), ('mossy_cobblestone', 1)], 0.55, seed=381)
    x0, x1, z0, z1 = 14, 33, 10, 34
    # The floor: mossy flags, half of it pools of sea water with pickles glowing in them.
    g.fill(x0, G - 3, z0, x1, G - 2, z1, 'mossy_stone_bricks')
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x0 < x < x1 and z0 < z < z1 and float(vnoise(x, 0, z, 4.0, 382)) < 0.45:
                g.set(x, G - 1, z, 'water[level=0]')
                if hash3(x, 1, z, 383) < 0.18:
                    g.set(x, G - 2, z, 'sand')
                    g.set(x, G - 1, z, 'sea_pickle[pickles=3,waterlogged=true]')
            else:
                g.set(x, G - 1, z, pick([('prismarine_bricks', 3), ('mossy_stone_bricks', 2), ('dark_prismarine', 1)], x, G, z, 384))
    # The walls, half fallen; two rows of columns; the roof down but for a few spans.
    for (a, b, c2, e) in ((x0, z0, x0, z1), (x1, z0, x1, z1), (x0, z0, x1, z0)):
        K.ruin_wall(g, a, b, c2, e, G, 8, P.block, seed=385 + a, decay=0.45)
    for z in range(z0 + 3, z1, 4):
        for x in (x0 + 4, x1 - 4):
            h = 9 if hash3(x, 0, z, 386) < 0.7 else 4 + int(hash3(z, 0, x, 386) * 3)
            for k in range(h):
                g.set(x, G + k, z, 'prismarine_bricks' if k not in (0, h - 1) else 'dark_prismarine')
            if h == 9:
                for dx in (-1, 1):
                    kit.stairs(g, x + dx, G + 8, z, 'prismarine_brick', 'east' if dx < 0 else 'west', 'top')
    for z in range(z0 + 3, z0 + 12, 4):
        for x in range(x0 + 4, x1 - 3):
            g.set(x, G + 9, z, 'prismarine_brick_slab[type=bottom]')
    # The altar at the head: steps, a slab table, candles, the apprentice's note, the chest.
    g.fill(x0 + 6, G - 1, z0 + 1, x1 - 6, G, z0 + 3, 'dark_prismarine')
    g.fill(x0 + 7, G + 1, z0 + 1, x1 - 7, G + 1, z0 + 2, 'prismarine_brick_slab[type=bottom]')
    for x in (x0 + 7, x1 - 7):
        K.candles(g, x, G + 2, z0 + 1, 3, lit=True, color='light_blue')
    g.chest(23, G + 1, z0 + 1, 'south', 'afterworld:chests/drowned_shrine')
    clue(g, 21, G + 1, z0 + 3, 'note', 'apprentice_note')
    g.set(26, G + 1, z0 + 3, 'iron_chain[axis=x]')
    # Side chapels: small apses with a seat and a lantern.
    for (ax, f) in ((x0 - 3, 'east'), (x1 + 3, 'west')):
        for z in range(z0 + 9, z0 + 15):
            for x in (ax, ax + (1 if f == 'east' else -1), ax + (2 if f == 'east' else -2)):
                g.set(x, G - 1, z, 'mossy_stone_bricks')
            for k in range(4):
                g.set(ax, G + k, z, P.block)
        kit.lantern(g, ax + (1 if f == 'east' else -1), G, z0 + 12, soul=False)
    # The bell tower at the shore end, empty, a chain hanging where the rope was.
    bx0, bz0 = 20, 36
    g.fill(bx0, G - 1, bz0, bx0 + 7, G + 13, bz0 + 7, 'stone_bricks')
    g.fill(bx0 + 1, G, bz0 + 1, bx0 + 6, G + 13, bz0 + 6, 'air')
    g.fill(bx0 + 3, G, bz0, bx0 + 4, G + 2, bz0, 'air')
    for (x, z) in ((bx0, bz0), (bx0 + 7, bz0), (bx0, bz0 + 7), (bx0 + 7, bz0 + 7)):
        g.fill(x, G + 14, z, x, G + 17, z, 'stone_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
    g.fill(bx0, G + 18, bz0, bx0 + 7, G + 18, bz0 + 7, 'stone_brick_slab[type=bottom]')
    kit.chain(g, bx0 + 3, G + 4, G + 17, bz0 + 3)
    ramp(g, bx0 + 3.5, bz0 + 3.5, 1.3, 3.0, G, G + 12, a0=90.0, T=8.0, wood='spruce')
    K.erode(g, bx0, bz0, bx0 + 7, bz0 + 7, G + 12, G + 19, seed=387, scale=2.5)
    # The porch and the pier running out into the water.
    for z in range(z1 + 1, z1 + 2):
        for x in range(x0 + 5, x1 - 4):
            g.set(x, G - 1, z, 'prismarine_bricks')
    for z in range(38, 48):
        for x in (8, 9, 10):
            g.set(x, G - 1, z, 'spruce_planks' if hash3(x, 0, z, 388) < 0.85 else 'air')
        if z % 3 == 0:
            for x in (7, 11):
                for k in range(-4, 2):
                    g.set(x, G + k, z, 'spruce_log[axis=y]')
            kit.lantern(g, 11, G + 2, z, soul=False)
    # Tide pools with coral, kelp in the deeper ones.
    for (px, pz, r) in ((5, 12, 2.5), (40, 18, 3.0), (41, 41, 2.2)):
        pd = dist(g, px, pz)
        g.a[:, G - 1, :][pd <= r] = g.pid('water[level=0]')
        g.a[:, G - 2, :][pd <= r - 0.8] = g.pid('water[level=0]')
        g.a[:, G - 3, :][pd <= r] = g.pid('sand')
        for i, (dx, dz) in enumerate(((0, 0), (1, 0), (0, 1))):
            g.set(px + dx, G - 2, pz + dz, ['tube_coral[waterlogged=true]', 'brain_coral[waterlogged=true]', 'fire_coral[waterlogged=true]'][i])
    plants(g, 0, 0, 47, 47, G, [('dead_bush', 1), ('short_grass', 2), ('sea_pickle[pickles=1,waterlogged=false]', 1)], 0.04, seed=389)
    mob(g, 19, G, 21, 'drowned_revenant', 2)
    elite(g, 26, G, 18, 'drowned_revenant')
    return K.finish(g)

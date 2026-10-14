"""The Hourglass Oasis: a spring in the dunes. A clear pool rimmed with smooth sandstone, palms
leaning over it, a gold-capped obelisk telling the hours with its shadow, striped tents for the
caravans, pots, rugs, and lamps on poles."""
import math
from vox import Grid, hash3
import kit

W, H, D = 48, 34, 48
G = 3
C = 24


def palm(g, x, z, h, lean, dirx, dirz):
    """A curving jungle-log trunk with drooping frond leaves and a few coconuts (cocoa)."""
    px, pz = float(x), float(z)
    for i in range(h):
        k = i / h
        px += dirx * lean * k * 0.5
        pz += dirz * lean * k * 0.5
        kit.log(g, int(round(px)), G + i, int(round(pz)), 'jungle_log')
    tx, ty, tz = int(round(px)), G + h, int(round(pz))
    kit.leaves(g, tx, ty, tz, 'jungle_leaves')
    for a in range(8):
        ang = a / 8 * 2 * math.pi
        for s in range(1, 6):
            fx = tx + math.cos(ang) * s
            fz = tz + math.sin(ang) * s
            fy = ty - (s * s) // 7
            kit.leaves(g, int(round(fx)), fy, int(round(fz)), 'jungle_leaves')


def build():
    g = Grid(W, H, D)
    for x in range(W):
        for z in range(D):
            ang = math.atan2(z + 0.5 - C, x + 0.5 - C)
            edge = 21.5 + 2.5 * math.sin(ang * 3 + 1) + 1.5 * math.sin(ang * 7)
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if d > edge:
                continue    # leave the world's own ground beyond the oasis
            g.fill(x, 0, z, x, G - 2, z, 'sandstone')
            g.set(x, G - 1, z, 'sand' if d < edge - 2 or hash3(x, 9, z, 2) < 0.5 else 'coarse_dirt')
            g.fill(x, G, z, x, H - 1, z, 'air')
    # The pool: water two deep, a rim of smooth sandstone, grass and reeds at the edge.
    for x in range(W):
        for z in range(D):
            d = math.hypot((x + 0.5 - C) / 1.3, z + 0.5 - (C + 4))
            if d <= 6.5:
                g.fill(x, G - 3, z, x, G - 1, z, 'water')
                g.set(x, G - 4, z, 'sand')
            elif d <= 7.6:
                g.set(x, G - 1, z, 'smooth_sandstone')
                g.set(x, G, z, 'smooth_sandstone_slab[type=bottom]')
            elif d <= 10:
                g.set(x, G - 1, z, 'grass_block' if hash3(x, 0, z, 2) < 0.7 else 'sand')
                if hash3(x, 1, z, 3) < 0.3:
                    g.set(x, G, z, 'short_grass' if hash3(x, 2, z, 4) < 0.6 else 'fern')
    for i in range(8):
        x, z = int(C - 6 + hash3(i, 0, 0, 5) * 12), int(C + 1 + hash3(i, 1, 0, 5) * 6)
        if g.get(x, G - 1, z) == 'minecraft:water':
            g.set(x, G, z, 'lily_pad')
    for (x, z) in ((C - 9, C + 2), (C + 8, C + 7), (C - 2, C + 12)):
        g.set(x, G, z, 'sugar_cane')
        g.set(x, G + 1, z, 'sugar_cane')
    # Palms leaning over the water.
    palm(g, C - 10, C + 6, 11, 3.0, 0.8, -0.1)
    palm(g, C + 10, C + 1, 9, 3.0, -0.8, 0.3)
    palm(g, C + 6, C + 12, 12, 2.5, -0.3, -0.7)
    palm(g, C - 6, C - 2, 8, 2.0, 0.4, 0.6)
    # The obelisk on a stepped plinth, north of the pool.
    ox, oz = C - 1, C - 9
    for k, r in enumerate((3, 2, 1)):
        g.fill(ox - r, G + k, oz - r, ox + r + 1, G + k, oz + r + 1, 'cut_sandstone' if k else 'smooth_sandstone')
    for y in range(G + 3, G + 21):
        k = (y - G - 3) / 18
        w = 1 if k < 0.75 else 0
        for x in range(ox - w, ox + 2 + w - 1 + (1 if w == 0 else 0)):
            for z in range(oz - w, oz + 2 + w - 1 + (1 if w == 0 else 0)):
                st = 'chiseled_sandstone' if (y - G) % 5 == 0 else 'cut_sandstone'
                if (y - G) % 5 == 2 and k < 0.75:
                    st = 'lapis_block' if (x + z) % 2 else 'cut_sandstone'
                g.set(x, y, z, st)
    g.fill(ox, G + 21, oz, ox + 1, G + 21, oz + 1, 'gold_block')
    g.set(ox, G + 22, oz, 'lightning_rod[facing=up]')
    # A ring of hour-stones round the plinth: the obelisk's shadow points at them.
    for i in range(12):
        a = i / 12 * 2 * math.pi
        x = int(round(ox + 0.5 + math.cos(a) * 7))
        z = int(round(oz + 0.5 + math.sin(a) * 7))
        g.set(x, G - 1, z, 'chiseled_sandstone')
        if i % 3 == 0:
            g.set(x, G, z, 'sandstone_wall')
            g.set(x, G + 1, z, 'lantern[hanging=false]')
    # Two caravan tents: striped wool over fence frames, with rugs, pots and a chest.
    for (tx, tz, col) in ((C + 10, C - 10, 'orange'), (C - 14, C - 12, 'red')):
        for x in range(tx - 3, tx + 4):
            for k in range(4):
                for z in (tz - 2 + k, tz + 2 - k):
                    if k < 3:
                        g.set(x, G + k, z, f'{col}_wool' if (x % 2) else 'white_wool')
            g.set(x, G + 3, tz, f'{col}_wool' if (x % 2) else 'white_wool')
        for x in range(tx - 2, tx + 3):
            g.set(x, G, tz, f'{col}_carpet')
            g.set(x, G, tz - 1, 'white_carpet' if x % 2 else f'{col}_carpet')
        g.chest(tx + 2, G, tz + 1, 'north', 'afterworld:chests/ruin_cache')
        g.set(tx - 2, G, tz + 1, 'decorated_pot[facing=north]')
        g.set(tx - 4, G, tz, 'decorated_pot[facing=east]')
        g.set(tx + 4, G, tz - 1, 'barrel[facing=up]')
    # Lamp poles along a sand path.
    for z in range(10, D - 10, 6):
        g.set(C + 14, G, z, 'birch_fence')
        g.set(C + 14, G + 1, z, 'birch_fence')
        g.set(C + 14, G + 2, z, 'lantern[hanging=false]')
    kit.scatter_ground(g, G - 1, 0, 0, W - 1, D - 1, ['minecraft:dead_bush', 'minecraft:short_dry_grass', 'minecraft:cactus'], 0.03)
    kit.keeper(g, 'rashid', C + 10, C - 6, G, G, 'south')
    kit.waystone(g, C + 6, C - 9, G, G + 1)
    kit.connect(g)
    return g

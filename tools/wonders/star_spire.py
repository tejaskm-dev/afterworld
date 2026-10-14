"""The Astronomer's Spire: a tall tower of dark brick on a buttressed plinth. Each storey glows
through arched windows; a balcony rings the top beneath a pointed roof, and above it all a great
crescent of light. Round the tower runs a ribbon of glowing glass, drifting up like smoke."""
import math
from vox import Grid, hash3
import kit

W, H, D = 32, 80, 32
G = 3
C = 16
R = 6            # tower half-width (the shaft is 13 wide, octagonal)


def octagon(x, z, r):
    """Inside an octagon of 'radius' r centred on C (cell centres)."""
    dx, dz = abs(x + 0.5 - C), abs(z + 0.5 - C)
    return dx <= r and dz <= r and dx + dz <= r * 1.45


def plinth(g):
    for x in range(W):
        for z in range(D):
            if math.hypot(x + 0.5 - C, z + 0.5 - C) <= 15.4:
                g.fill(x, 0, z, x, G - 2, z, 'deepslate')
                g.set(x, G - 1, z, 'grass_block')
                g.fill(x, G, z, x, H - 1, z, 'air')
    # Stepped plinth of polished blackstone, three tiers.
    for k, r in enumerate((R + 4, R + 3, R + 2)):
        for x in range(W):
            for z in range(D):
                if octagon(x, z, r):
                    g.set(x, G + k, z, 'polished_blackstone_bricks' if k < 2 else 'chiseled_polished_blackstone')
    # Four great buttresses at the diagonals, stepping out at the base.
    for sx, sz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        for h in range(0, 18):
            reach = max(0, 3 - h // 6)
            for t in range(reach + 1):
                x = C + sx * (R - 1 + t) - (1 if sx < 0 else 0)
                z = C + sz * (R - 1 + t) - (1 if sz < 0 else 0)
                g.set(x, G + 3 + h, z, 'deepslate_bricks')
        g.set(C + sx * (R + 2) - (1 if sx < 0 else 0), G + 3, C + sz * (R + 2) - (1 if sz < 0 else 0), 'soul_lantern[hanging=false]')


def shaft(g):
    top = G + 52
    for y in range(G + 3, top):
        for x in range(W):
            for z in range(D):
                if octagon(x, z, R):
                    edge = not octagon(x, z, R - 1)
                    if edge:
                        r = hash3(x, y, z, 2)
                        st = 'deepslate_bricks' if r < 0.62 else 'cracked_deepslate_bricks' if r < 0.78 else 'deepslate_tiles' if r < 0.92 else 'polished_deepslate'
                        g.set(x, y, z, st)
                    else:
                        g.set(x, y, z, 'air')
        # Floors every 10 storeys, with a string course outside.
        if (y - (G + 3)) % 10 == 0:
            for x in range(W):
                for z in range(D):
                    if octagon(x, z, R - 1):
                        g.set(x, y, z, 'dark_oak_planks' if (x + z) % 2 else 'spruce_planks')
                    elif octagon(x, z, R + 1) and not octagon(x, z, R):
                        g.set(x, y, z, 'polished_blackstone_brick_slab[type=top]')
    # Arched windows on every face of each storey, lit from within.
    for floor in range(5):
        y0 = G + 6 + floor * 10
        for face, (fx, fz) in kit.FACE.items():
            x = (C + R - 1 if fx > 0 else C - R) if fx else C - 1
            z = (C + R - 1 if fz > 0 else C - R) if fz else C - 1
            for w in range(2):
                ax, az = (x + (w if fx == 0 else 0), z + (w if fz == 0 else 0))
                for j in range(4):
                    g.set(ax, y0 + j, az, 'orange_stained_glass_pane' if j < 3 else 'deepslate_brick_slab[type=top]')
                g.set(ax - fx, y0 + 1, az - fz, 'light[level=12]')
            # The window's hood: upside-down stairs over it.
            hx, hz = x + fx, z + fz
            for w in range(-1, 3):
                bx, bz = (hx + (w if fx == 0 else 0), hz + (w if fz == 0 else 0))
                kit.stairs(g, bx, y0 + 4, bz, 'deepslate_brick', kit.OPP[face], 'top')
    # A door at the bottom (south) with a lantern either side.
    for x in (C - 1, C):
        g.set(x, G + 3, C + R, 'air')
        g.set(x, G + 4, C + R, 'air')
    kit.door(g, C - 1, G + 3, C + R - 1, 'dark_oak', 'south', 'left')
    kit.door(g, C, G + 3, C + R - 1, 'dark_oak', 'south', 'right')
    kit.lantern(g, C - 2, G + 5, C + R + 1, hanging=False)
    kit.lantern(g, C + 1, G + 5, C + R + 1, hanging=False)
    return top


def interiors(g):
    """Ground: a hall. Then a library, a study, a bedroom and the instrument room."""
    y = G + 4
    # Spiral of stairs climbing the inside wall.
    for step in range(48):
        ang = step / 12 * math.pi * 2
        y = G + 4 + step
        x = C - 0.5 + math.cos(ang) * (R - 2)
        z = C - 0.5 + math.sin(ang) * (R - 2)
        xi, zi = int(round(x)), int(round(z))
        g.set(xi, y, zi, 'spruce_slab[type=top]')
        # Keep headroom above the step through the floors.
        for k in range(1, 4):
            if g.get(xi, y + k, zi) not in (None, 'minecraft:air'):
                g.set(xi, y + k, zi, 'air')
    lib = G + 14
    kit.bookshelf_wall(g, C - R + 1, lib, C - 2, C - R + 1, lib + 2, C + 1, seed=1)
    kit.bookshelf_wall(g, C + R - 2, lib, C - 2, C + R - 2, lib + 2, C + 1, seed=2)
    kit.table(g, C - 1, lib - 0, C, 'spruce_pressure_plate')
    g.set(C, lib, C, 'lectern[facing=north,has_book=false]')
    kit.hang_lantern(g, C - 1, lib + 7, C - 1, 2)
    study = G + 24
    g.set(C - 2, study, C - 2, 'crafting_table')
    g.set(C - 1, study, C - 2, 'cartography_table')
    g.set(C + 1, study, C + 1, 'brewing_stand')
    kit.chair(g, C - 1, study, C - 1, 'spruce', 'north')
    g.chest(C + 2, study, C - 2, 'west', 'afterworld:chests/observatory_maps')
    kit.hang_lantern(g, C, study + 7, C, 2)
    bed = G + 34
    kit.bed(g, C - 2, bed, C - 2, 'blue', 'north')
    g.set(C + 2, bed, C + 2, 'barrel[facing=up]')
    kit.potted(g, C + 2, bed + 1, C + 2, 'blue_orchid')
    kit.hang_lantern(g, C, bed + 7, C, 2)
    top = G + 44
    g.set(C - 1, top, C - 1, 'lodestone')
    g.set(C - 1, top + 1, C - 1, 'spyglass' if False else 'end_rod[facing=up]')
    g.set(C + 1, top, C + 1, 'enchanting_table')


def crown(g, top):
    """Machicolated balcony, a steep slate roof, and the crescent above it."""
    for x in range(W):
        for z in range(D):
            if octagon(x, z, R + 2) and not octagon(x, z, R - 1):
                g.set(x, top, z, 'polished_blackstone_bricks')
            if octagon(x, z, R + 2) and not octagon(x, z, R + 1):
                g.set(x, top + 1, z, 'polished_blackstone_brick_wall')
                if (x + z) % 3 == 0:
                    g.set(x, top + 2, z, 'soul_lantern[hanging=false]')
            # Corbels under the balcony.
            if octagon(x, z, R + 2) and not octagon(x, z, R) and (x + z) % 2 == 0:
                g.set(x, top - 1, z, 'polished_blackstone_brick_stairs[facing=north,half=top,shape=straight]')
    # Roof: an octagonal cone of deepslate tiles, flared at the eaves.
    for k in range(18):
        r = R + 1.2 - k * 0.42
        for x in range(W):
            for z in range(D):
                if r > 0.2 and octagon(x, z, r) and not octagon(x, z, r - 1.1):
                    g.set(x, top + 2 + k, z, 'deepslate_tiles' if k % 4 else 'deepslate_tile_slab[type=top]')
    g.set(C - 1, top + 20, C - 1, 'lightning_rod[facing=up]')
    # Crescent of light floating over the roof.
    cx, cy = C - 0.5, top + 27.5
    for x in range(C - 9, C + 9):
        for y in range(top + 19, top + 38):
            if y >= H:
                continue
            outer = math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= 7.4
            inner = math.hypot(x + 0.5 - (cx + 3.2), y + 0.5 - (cy + 1.6)) <= 6.3
            if outer and not inner:
                g.set(x, y, C - 1, 'sea_lantern')
                g.set(x, y, C, 'white_stained_glass')


def ribbon(g):
    """A ribbon of glowing glass spiralling up round the tower."""
    turns = 2.2
    n = 420
    for i in range(n):
        t = i / n
        ang = t * turns * math.pi * 2
        r = R + 3.5 + math.sin(t * 9) * 0.6
        y = G + 8 + t * 46
        x = C - 0.5 + math.cos(ang) * r
        z = C - 0.5 + math.sin(ang) * r
        for dy in (0, 1):
            st = 'sea_lantern' if i % 23 == 0 and dy == 0 else 'light_blue_stained_glass' if dy == 0 else 'white_stained_glass'
            g.set(int(round(x)), int(round(y)) + dy, int(round(z)), st)


def grounds(g):
    kit.scatter_ground(g, G - 1, 0, 0, W - 1, D - 1, ['minecraft:fern', 'minecraft:short_grass', 'minecraft:blue_orchid', 'minecraft:allium'], 0.25)
    for (x, z) in ((C - 9, C + 9), (C + 8, C + 9), (C - 9, C - 10), (C + 8, C - 10)):
        g.set(x, G, z, 'polished_blackstone_wall')
        g.set(x, G + 1, z, 'polished_blackstone_wall')
        g.set(x, G + 2, z, 'soul_lantern[hanging=false]')


def build():
    g = Grid(W, H, D)
    plinth(g)
    top = shaft(g)
    interiors(g)
    crown(g, top)
    ribbon(g)
    grounds(g)
    kit.waystone(g, C + 4, C + R + 4, G, G + 3)
    kit.connect(g)
    return g

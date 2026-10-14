"""A Waystone Circle: a round plaza with a rune-cut obelisk at its heart, four standing stones
carrying soul lanterns, benches facing the stone, flower beds and a little roofed notice board.
Travellers who touch the stone remember the place."""
import math
from vox import Grid, hash3
import kit

W, H, D = 32, 28, 32
G = 3
C = 16


def build():
    g = Grid(W, H, D)
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if d <= 15.4:
                g.fill(x, 0, z, x, G - 2, z, 'stone')
                g.set(x, G - 1, z, 'grass_block')
                g.fill(x, G, z, x, H - 1, z, 'air')
            # The plaza: polished andesite rings, a mosaic of stone bricks, a mossy edge.
            if d <= 10.4:
                ang = math.atan2(z + 0.5 - C, x + 0.5 - C)
                st = 'polished_andesite'
                if 3.5 < d <= 4.5 or 8.4 < d <= 9.4:
                    st = 'chiseled_stone_bricks' if int(ang * 8 / math.pi) % 2 else 'stone_bricks'
                elif d > 9.4:
                    st = 'mossy_stone_bricks' if hash3(x, 0, z, 3) < 0.6 else 'mossy_cobblestone'
                elif abs(math.sin(ang * 2)) < 0.18 and d > 4.5:
                    st = 'polished_tuff'
                g.set(x, G - 1, z, st)
    # The obelisk: a stepped base, a tapering shaft with rune bands, a glowing cap.
    for k, r in enumerate((2, 1, 1)):
        g.fill(C - 1 - r, G + k, C - 1 - r, C + r, G + k, C + r, 'chiseled_stone_bricks' if k == 0 else 'polished_tuff')
    for d0 in range(C - 3, C + 3):
        kit.stairs(g, d0, G, C - 4, 'stone_brick', 'south')
        kit.stairs(g, d0, G, C + 3, 'stone_brick', 'north')
        kit.stairs(g, C - 4, G, d0, 'stone_brick', 'east')
        kit.stairs(g, C + 3, G, d0, 'stone_brick', 'west')
    for y in range(G + 3, G + 14):
        band = (y - G) % 4 == 1
        for (x, z) in ((C - 1, C - 1), (C, C - 1), (C - 1, C), (C, C)):
            g.set(x, y, z, 'chiseled_tuff_bricks' if band else 'tuff_bricks')
        if band:
            for (x, z, f) in ((C - 1, C - 2, 'north'), (C, C - 2, 'north'), (C - 1, C + 1, 'south'), (C, C + 1, 'south'),
                              (C - 2, C - 1, 'west'), (C - 2, C, 'west'), (C + 1, C - 1, 'east'), (C + 1, C, 'east')):
                g.set(x, y, z, 'light[level=10]')
    g.fill(C - 1, G + 14, C - 1, C, G + 14, C, 'chiseled_tuff')
    g.fill(C - 1, G + 15, C - 1, C, G + 15, C, 'sea_lantern')
    g.set(C - 1, G + 16, C - 1, 'end_rod[facing=up]')
    g.set(C, G + 16, C, 'end_rod[facing=up]')
    # A halo of end rods floating round the crown.
    for i in range(16):
        a = i / 16 * 2 * math.pi
        x = int(round(C - 0.5 + math.cos(a) * 3.6))
        z = int(round(C - 0.5 + math.sin(a) * 3.6))
        face = 'east' if abs(math.cos(a)) > abs(math.sin(a)) else 'south'
        g.set(x, G + 17, z, f'end_rod[facing={face}]')
    # Four standing stones with soul lanterns.
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        x = int(round(C - 0.5 + math.cos(a) * 7.2))
        z = int(round(C - 0.5 + math.sin(a) * 7.2))
        for y in range(G, G + 4):
            g.set(x, y, z, 'chiseled_stone_bricks' if y == G + 2 else 'mossy_stone_bricks' if hash3(x, y, z, 2) < 0.5 else 'stone_bricks')
        g.set(x, G + 4, z, 'stone_brick_wall')
        g.set(x, G + 5, z, 'soul_lantern[hanging=false]')
    # Benches facing the stone.
    for i in range(4):
        a = i * math.pi / 2
        bx = C - 0.5 + math.cos(a) * 6
        bz = C - 0.5 + math.sin(a) * 6
        facing = ('west' if math.cos(a) > 0.5 else 'east') if abs(math.cos(a)) > 0.5 else ('north' if math.sin(a) > 0 else 'south')
        along = (0, 1) if abs(math.cos(a)) > 0.5 else (1, 0)
        for t in (-1, 0, 1):
            x = int(round(bx + along[0] * t))
            z = int(round(bz + along[1] * t))
            kit.stairs(g, x, G, z, 'spruce', facing)
    # Flower beds in the four quarters.
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        cx = C - 0.5 + math.cos(a) * 11.5
        cz = C - 0.5 + math.sin(a) * 11.5
        for x in range(int(cx) - 2, int(cx) + 3):
            for z in range(int(cz) - 2, int(cz) + 3):
                if (x - cx) ** 2 + (z - cz) ** 2 <= 4.5 and 0 <= x < W and 0 <= z < D:
                    g.set(x, G - 1, z, 'moss_block')
                    g.set(x, G, z, ['allium', 'blue_orchid', 'azure_bluet', 'cornflower', 'lily_of_the_valley'][int(hash3(x, 1, z, 7) * 5)])
    # Notice board under a little roof, at the south edge.
    nx, nz = C - 2, C + 12
    for y in range(G, G + 4):
        kit.log(g, nx - 1, y, nz, 'spruce_log')
        kit.log(g, nx + 3, y, nz, 'spruce_log')
    g.fill(nx, G + 1, nz, nx + 2, G + 2, nz, 'spruce_planks')
    for x in range(nx - 2, nx + 5):
        kit.stairs(g, x, G + 4, nz - 1, 'dark_oak', 'south')
        kit.stairs(g, x, G + 4, nz + 1, 'dark_oak', 'north')
        g.set(x, G + 4, nz, 'dark_oak_slab[type=top]')
    kit.hang_lantern(g, nx + 1, G + 3, nz - 1, 0)
    kit.scatter_ground(g, G - 1, 0, 0, W - 1, D - 1, ['minecraft:short_grass', 'minecraft:fern', 'minecraft:oxeye_daisy'], 0.22)
    kit.waystone(g, C - 1, C + 5, G, G, r=1)
    kit.connect(g)
    return g

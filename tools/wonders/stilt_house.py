"""The Fen-Keeper's Stilt House: a mud-brick and mangrove house raised on stilts over black water,
with a wraparound boardwalk, a moss-thatched roof, lanterns on poles, a little dock with a boat
post, drying nets and fireflies' favourite reeds."""
import math
from vox import Grid, hash3
import kit

W, H, D = 32, 30, 32
G = 3
X0, X1, Z0, Z1 = 10, 21, 10, 19
FLOOR = G + 3


def build():
    g = Grid(W, H, D)
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - 16, z + 0.5 - 16)
            ang = math.atan2(z + 0.5 - 16, x + 0.5 - 16)
            pond = 12.0 + 1.5 * math.sin(ang * 4 + 2) + math.sin(ang * 9)
            if d > 15.6:
                continue
            g.fill(x, 0, z, x, G - 3, z, 'mud')
            if d <= pond:
                g.fill(x, G - 2, z, x, G - 1, z, 'water')
            else:
                g.set(x, G - 1, z, 'mud' if hash3(x, 0, z, 1) < 0.5 else 'grass_block')
                g.set(x, G - 2, z, 'mud')
            g.fill(x, G, z, x, H - 1, z, 'air')
    # Stilts: mangrove logs down into the mud, with roots splaying at the waterline.
    for x in range(X0 - 2, X1 + 3, 3):
        for z in (Z0 - 2, Z1 + 2):
            for y in range(G - 3, FLOOR):
                kit.log(g, x, y, z, 'mangrove_log')
            g.set(x + 1, G - 1, z, 'mangrove_roots')
            g.set(x - 1, G - 1, z, 'mangrove_roots')
    for z in range(Z0 - 2, Z1 + 3, 3):
        for x in (X0 - 2, X1 + 2):
            for y in range(G - 3, FLOOR):
                kit.log(g, x, y, z, 'mangrove_log')
    # Platform and boardwalk.
    g.fill(X0 - 2, FLOOR, Z0 - 2, X1 + 2, FLOOR, Z1 + 2, 'mangrove_planks')
    for x in range(X0 - 2, X1 + 3):
        for z in (Z0 - 2, Z1 + 2):
            g.set(x, FLOOR + 1, z, 'mangrove_fence')
    for z in range(Z0 - 2, Z1 + 3):
        for x in (X0 - 2, X1 + 2):
            g.set(x, FLOOR + 1, z, 'mangrove_fence')
    # House walls: mud bricks between mangrove posts, with lit windows.
    for y in range(FLOOR + 1, FLOOR + 6):
        for x in range(X0, X1 + 1):
            for z in range(Z0, Z1 + 1):
                edge = x in (X0, X1) or z in (Z0, Z1)
                post = x in (X0, X1) and z in (Z0, Z1) or (edge and (x - X0) % 4 == 0 and z in (Z0, Z1)) or (edge and (z - Z0) % 4 == 0 and x in (X0, X1))
                if post:
                    kit.log(g, x, y, z, 'stripped_mangrove_log')
                elif edge:
                    g.set(x, y, z, 'mud_bricks' if y < FLOOR + 5 else 'mangrove_planks')
                else:
                    g.set(x, y, z, 'air')
    for (x, z) in ((X0 + 2, Z1), (X0 + 7, Z1), (X0 + 2, Z0), (X1, Z0 + 5), (X0, Z0 + 5)):
        g.set(x, FLOOR + 2, z, 'brown_stained_glass_pane')
        g.set(x, FLOOR + 3, z, 'brown_stained_glass_pane')
    g.set(X0 + 5, FLOOR + 1, Z1, 'air')
    kit.door(g, X0 + 5, FLOOR + 1, Z1, 'mangrove', 'south')
    # Roof: hipped, mangrove stairs under a mossy thatch.
    top = kit.hip(g, X0, Z0, X1, Z1, FLOOR + 6, 'mangrove', overhang=2)
    for x in range(X0 - 2, X1 + 3):
        for z in range(Z0 - 2, Z1 + 3):
            for y in range(FLOOR + 6, top + 1):
                s = g.get(x, y, z)
                if s and 'stairs' in s and hash3(x, y, z, 5) < 0.3:
                    g.set(x, y + 1, z, 'moss_carpet')
    for i in range(14):
        x = X0 - 2 + int(hash3(i, 2, 3, 6) * (X1 - X0 + 5))
        z = Z0 - 2 if i % 2 else Z1 + 2
        length = 1 + int(hash3(i, 4, 5, 6) * 3)
        for j in range(length):
            g.set(x, FLOOR + 5 - j, z, 'hanging_roots')
    # Inside: a stove, a hammock-bed, herbs and jars.
    g.set(X0 + 1, FLOOR + 1, Z0 + 1, 'furnace[facing=south,lit=true]')
    g.set(X0 + 2, FLOOR + 1, Z0 + 1, 'brewing_stand')
    g.set(X0 + 3, FLOOR + 1, Z0 + 1, 'cauldron')
    kit.bed(g, X1 - 2, FLOOR + 1, Z0 + 1, 'green', 'south')
    g.chest(X1 - 1, FLOOR + 1, Z1 - 1, 'west', 'afterworld:chests/sunken_cache')
    for x in range(X0 + 1, X1):
        if hash3(x, 9, 9, 7) < 0.5:
            g.set(x, FLOOR + 5, Z0 + 1, 'spore_blossom' if x % 3 == 0 else 'hanging_roots')
    kit.hang_lantern(g, X0 + 5, FLOOR + 5, Z0 + 4, 1)
    # A dock out over the water, with a mooring post and a lantern pole.
    for z in range(Z1 + 3, Z1 + 10):
        for x in (X0 + 4, X0 + 5, X0 + 6):
            g.set(x, FLOOR - 1 if z > Z1 + 4 else FLOOR, z, 'mangrove_slab[type=top]' if z > Z1 + 4 else 'mangrove_planks')
        if z % 3 == 0:
            for y in range(G - 3, FLOOR - 1):
                kit.log(g, X0 + 3, y, z, 'mangrove_log')
                kit.log(g, X0 + 7, y, z, 'mangrove_log')
    for y in range(FLOOR, FLOOR + 3):
        g.set(X0 + 7, y, Z1 + 9, 'mangrove_fence')
    g.set(X0 + 7, FLOOR + 3, Z1 + 9, 'lantern[hanging=false]')
    # Lantern poles at the corners of the walk.
    for (x, z) in ((X0 - 2, Z0 - 2), (X1 + 2, Z0 - 2), (X0 - 2, Z1 + 2), (X1 + 2, Z1 + 2)):
        g.set(x, FLOOR + 2, z, 'mangrove_fence')
        g.set(x, FLOOR + 3, z, 'lantern[hanging=false]')
    # Reeds and lily pads in the water.
    for i in range(30):
        x, z = int(hash3(i, 1, 1, 8) * W), int(hash3(i, 2, 2, 8) * D)
        if g.get(x, G - 1, z) == 'minecraft:water' and g.get(x, G, z) == 'minecraft:air':
            g.set(x, G, z, 'lily_pad' if i % 3 else 'sugar_cane')
    kit.keeper(g, 'mab', X0 + 3, Z1 + 1, FLOOR + 1, FLOOR + 1, 'south')
    kit.waystone(g, X0 + 5, Z1 + 8, FLOOR - 1, FLOOR + 1, r=2)
    kit.connect(g)
    return g

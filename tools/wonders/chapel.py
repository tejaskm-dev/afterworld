"""The Drowned Bell Chapel: a gothic chapel half fallen in. Buttressed walls with tall pointed
windows, a nave whose roof has caved at one end, a bell tower that still stands, rubble, vines and
moss, a graveyard of tilted stones and candles, and an altar where someone still lights them."""
import math
from vox import Grid, hash3
import kit

W, H, D = 32, 40, 48
G = 3
X0, X1 = 11, 21       # nave walls (x)
Z0, Z1 = 14, 38       # nave walls (z); the tower is at the south end


def build():
    g = Grid(W, H, D)
    for x in range(W):
        for z in range(D):
            g.fill(x, 0, z, x, G - 2, z, 'stone')
            g.set(x, G - 1, z, 'grass_block' if hash3(x, 0, z, 1) < 0.8 else 'podzol')
            g.fill(x, G, z, x, H - 1, z, 'air')
    stone = {'stone_bricks': ['cracked_stone_bricks', 'mossy_stone_bricks', 'mossy_stone_bricks', 'andesite']}
    # Nave: walls 13 high, floor of cobble and old tiles.
    g.fill(X0, G - 1, Z0, X1, G - 1, Z1, 'stone_bricks')
    for y in range(G, G + 13):
        for z in range(Z0, Z1 + 1):
            g.set(X0, y, z, 'stone_bricks')
            g.set(X1, y, z, 'stone_bricks')
        for x in range(X0, X1 + 1):
            g.set(x, y, Z1, 'stone_bricks')
    for x in range(X0 + 1, X1):
        for z in range(Z0 + 1, Z1):
            g.set(x, G - 1, z, 'cobblestone' if hash3(x, 1, z, 2) < 0.4 else 'polished_andesite' if (x + z) % 2 else 'stone_bricks')
    # Pointed windows: tall lancets, two panes wide, with stone tracery.
    for z in range(Z0 + 4, Z1 - 2, 5):
        for x in (X0, X1):
            for y in range(G + 3, G + 10):
                g.set(x, y, z, 'gray_stained_glass_pane' if y < G + 9 else 'stone_brick_wall')
                g.set(x, y, z + 1, 'gray_stained_glass_pane' if y < G + 9 else 'stone_brick_wall')
            g.set(x, G + 10, z, 'chiseled_stone_bricks')
            g.set(x, G + 10, z + 1, 'chiseled_stone_bricks')
            # Buttress between windows.
            out = -1 if x == X0 else 1
            for y in range(G, G + 11):
                reach = 2 if y < G + 5 else 1
                for r in range(1, reach + 1):
                    g.set(x + out * r, y, z + 3, 'stone_bricks')
            kit.stairs(g, x + out * 2, G + 5, z + 3, 'stone_brick', 'east' if out < 0 else 'west')
            kit.stairs(g, x + out, G + 11, z + 3, 'stone_brick', 'east' if out < 0 else 'west')
    # A rose window over the altar (north end).
    cx, cy = (X0 + X1) // 2, G + 8
    for x in range(X0 + 1, X1):
        for y in range(G + 4, G + 13):
            d = math.hypot(x - cx, y - cy)
            if d <= 3.6:
                ang = math.atan2(y - cy, x - cx)
                g.set(x, y, Z1, 'purple_stained_glass_pane' if d < 1.5 else ('red_stained_glass_pane' if int(ang * 4 / math.pi) % 2 else 'blue_stained_glass_pane'))
                if d > 2.8 or abs(math.sin(ang * 4)) < 0.25:
                    g.set(x, y, Z1, 'stone_bricks' if d > 2.8 else 'chiseled_stone_bricks')
    # Roof: steep gable, but caved in over the south half.
    ridge = kit.gable(g, X0, Z0, X1, Z1, G + 13, 'deepslate_tile', axis='z', overhang=1, gable_fill='stone_bricks')
    for x in range(W):
        for z in range(Z0 - 1, Z0 + 11):
            for y in range(G + 13, ridge + 1):
                h = hash3(x, y, z, 6)
                fall = (z - Z0) / 11
                if h > 0.15 + fall * 0.8:
                    g.set(x, y, z, 'air')
    # Inside: pews, an altar with candles, a broken pillar, rubble and vines.
    for z in range(Z0 + 6, Z1 - 4, 2):
        for x in range(X0 + 2, X0 + 5):
            if hash3(x, 2, z, 3) < 0.85:
                kit.stairs(g, x, G, z, 'dark_oak', 'south')
        for x in range(X1 - 4, X1 - 1):
            if hash3(x, 2, z, 4) < 0.85:
                kit.stairs(g, x, G, z, 'dark_oak', 'south')
    ax = (X0 + X1) // 2
    g.fill(ax - 2, G, Z1 - 3, ax + 2, G, Z1 - 2, 'polished_deepslate')
    g.fill(ax - 1, G + 1, Z1 - 3, ax + 1, G + 1, Z1 - 3, 'chiseled_deepslate')
    for x, n in ((ax - 1, 3), (ax, 4), (ax + 1, 2)):
        g.set(x, G + 2, Z1 - 3, f'white_candle[candles={n},lit=true]')
    g.chest(ax, G, Z1 - 4, 'south', 'afterworld:chests/shrine_cache')
    kit.hang_lantern(g, ax, G + 11, Z1 - 6, 4, soul=True)
    for (x, z, h) in ((X0 + 2, Z0 + 9, 3), (X1 - 2, Z0 + 3, 2), (ax, Z0 + 7, 2)):
        for y in range(G, G + h):
            g.set(x, y, z, 'cobblestone' if hash3(x, y, z, 9) < 0.5 else 'mossy_cobblestone')
    kit.weather(g, stone, rate=0.4, seed=7)
    # Vines down the walls.
    for x in (X0 - 1, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if hash3(x, 3, z, 5) < 0.3:
                length = 3 + int(hash3(x, 4, z, 5) * 6)
                face = 'east' if x < X0 else 'west'
                for y in range(G + 11 - length, G + 11):
                    g.set(x, y, z, f'vine[{face}=true]')
    # Bell tower at the south end: square, taller, open belfry with a bell.
    tx0, tx1, tz0, tz1 = X0 + 1, X1 - 1, Z0 - 7, Z0
    for y in range(G - 1, G + 24):
        for x in range(tx0, tx1 + 1):
            for z in range(tz0, tz1 + 1):
                edge = x in (tx0, tx1) or z in (tz0, tz1)
                belfry = G + 16 <= y <= G + 20 and not (x in (tx0, tx1) and z in (tz0, tz1))
                if edge and not belfry:
                    g.set(x, y, z, 'stone_bricks')
                elif not edge and y in (G - 1, G + 15):
                    g.set(x, y, z, 'spruce_planks')
                else:
                    g.set(x, y, z, 'air')
    tcx, tcz = (tx0 + tx1) // 2, (tz0 + tz1) // 2
    g.set(tcx, G + 20, tcz, 'oak_log[axis=y]')
    g.set(tcx, G + 19, tcz, 'bell[attachment=ceiling,facing=north]')
    kit.hip(g, tx0, tz0, tx1, tz1, G + 24, 'deepslate_tile', overhang=1)
    g.set(tcx, G + 29, tcz, 'lightning_rod[facing=up]')
    # Doorway through the tower.
    for y in range(G, G + 4):
        g.set(tcx, y, tz0, 'air')
        g.set(tcx + 1, y, tz0, 'air')
        g.set(tcx, y, tz1, 'air')
        g.set(tcx + 1, y, tz1, 'air')
    kit.stairs(g, tcx, G + 4, tz0, 'stone_brick', 'east', 'top')
    kit.stairs(g, tcx + 1, G + 4, tz0, 'stone_brick', 'west', 'top')
    kit.weather(g, stone, rate=0.4, seed=8, region=(tx0, G - 1, tz0, tx1, G + 30, tz1))
    # Graveyard to the east: tilted stones, candles and a low wall.
    for i in range(10):
        x = 25 + (i % 2) * 3
        z = Z0 + 2 + (i // 2) * 4
        g.set(x, G, z, 'cobblestone_wall' if i % 3 else 'stone_brick_wall')
        g.set(x, G + 1, z, 'stone_brick_slab[type=bottom]' if i % 3 else 'skeleton_skull[rotation=4]')
        if hash3(i, 1, 1, 3) < 0.5:
            g.set(x + 1, G, z, 'candle[candles=2,lit=true]')
    for z in range(Z0, Z1):
        g.set(30, G, z, 'mossy_cobblestone_wall' if z % 3 else 'cobblestone_wall')
    kit.scatter_ground(g, G - 1, 0, 0, W - 1, D - 1, ['minecraft:short_grass', 'minecraft:fern', 'minecraft:dead_bush', 'minecraft:lily_of_the_valley'], 0.25)
    kit.keeper(g, 'ines', ax + 2, Z1 - 6, G, G, 'south')
    kit.waystone(g, 26, Z0 - 4, G, G + 1)
    kit.connect(g)
    return g

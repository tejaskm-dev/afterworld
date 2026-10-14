"""The Waystone Camp: where every traveller wakes. A Warden outpost round a carved waystone on a
stepped dais: Isolde's pavilion to the north, Bram's supply stall to the east, Marta's forge to the
west, Wren's map tent to the south-west, sleeping tents, a well, a firepit with log benches, a
training yard, a little garden, lantern posts along the paths and a palisade with a gate facing
south. Markers stand the keepers in their places and record the waystone and the arrival point."""
import math
from vox import Grid, hash3
import kit

W, H, D = 48, 28, 48
G = 4             # ground layers
C = 24            # centre (x and z)
R = 22.5          # the camp's ground radius


def ground(g):
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            ang = math.atan2(z + 0.5 - C, x + 0.5 - C)
            edge = R - 1.2 + 1.2 * math.sin(ang * 5 + 1) + 0.8 * math.sin(ang * 11)
            if d > edge:
                continue
            g.fill(x, 0, z, x, G - 2, z, 'dirt')
            top = 'grass_block'
            h = hash3(x, 0, z, 1)
            if h < 0.06:
                top = 'coarse_dirt'
            elif h < 0.10:
                top = 'rooted_dirt'
            g.set(x, G - 1, z, top)
            g.fill(x, G, z, x, H - 1, z, 'air')


def paths(g):
    """Packed paths from the gate and round the plaza, edged with gravel and the odd stone."""
    def tread(x, z):
        if 0 <= x < W and 0 <= z < D and g.get(x, G - 1, z) not in (None, 'minecraft:air'):
            h = hash3(x, 3, z, 2)
            g.set(x, G - 1, z, 'dirt_path' if h < 0.78 else 'coarse_dirt' if h < 0.9 else 'gravel')
    # Gate road (south) to the plaza.
    for z in range(C + 6, D):
        for x in range(C - 2, C + 2):
            tread(x, z)
    # Plaza ring.
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if 6.5 <= d <= 9.0:
                tread(x, z)
    # Spokes to the stalls.
    for x in range(C + 8, C + 16):
        for z in (C - 1, C):
            tread(x, z)
    for x in range(C - 16, C - 8):
        for z in (C - 1, C):
            tread(x, z)
    for z in range(C - 16, C - 8):
        for x in (C - 1, C):
            tread(x, z)
    for i in range(9):
        tread(C - 8 - i, C + 6 + i // 2)
        tread(C - 9 - i, C + 6 + i // 2)


def plaza(g):
    """A mosaic disc, a stepped dais and the waystone, with lantern posts and Warden banners."""
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if d <= 6.4:
                ang = math.atan2(z + 0.5 - C, x + 0.5 - C)
                if d > 5.5:
                    st = 'polished_andesite'
                elif d > 4.6:
                    st = 'chiseled_stone_bricks' if int((ang + math.pi) / (math.pi / 8)) % 2 else 'stone_bricks'
                else:
                    st = 'mossy_stone_bricks' if hash3(x, 5, z, 3) < 0.25 else 'stone_bricks'
                    if int(d + 0.5) == 3:
                        st = 'polished_diorite'
                g.set(x, G - 1, z, st)
    # The dais: a 5x5 step then a 3x3 top, stairs on every side.
    for x in range(C - 3, C + 3):
        for z in range(C - 3, C + 3):
            g.set(x, G, z, 'polished_deepslate')
    for i in range(C - 3, C + 3):
        kit.stairs(g, i, G, C - 4, 'polished_deepslate', 'south')
        kit.stairs(g, i, G, C + 3, 'polished_deepslate', 'north')
        kit.stairs(g, C - 4, G, i, 'polished_deepslate', 'east')
        kit.stairs(g, C + 3, G, i, 'polished_deepslate', 'west')
    for (x, z) in ((C - 4, C - 4), (C + 3, C - 4), (C - 4, C + 3), (C + 3, C + 3)):
        g.set(x, G, z, 'polished_deepslate_slab[type=bottom]')
    for x in range(C - 2, C + 2):
        for z in range(C - 2, C + 2):
            g.set(x, G + 1, z, 'chiseled_deepslate' if (x + z) % 2 else 'deepslate_tiles')
    # The waystone in the middle of the dais.
    g.set(C, G + 2, C, 'afterworld:waystone[natural=true]')
    g.mark(C, G + 2, C, 'waystone')
    for (x, z) in ((C - 1, C - 1), (C - 1, C), (C, C - 1)):
        g.set(x, G + 2, z, 'air')
    # Lantern posts at the dais corners: deepslate walls with lanterns, banners between.
    for (x, z) in ((C - 3, C - 3), (C + 2, C - 3), (C - 3, C + 2), (C + 2, C + 2)):
        g.set(x, G + 1, z, 'polished_deepslate_wall')
        g.set(x, G + 2, z, 'polished_deepslate_wall')
        g.set(x, G + 3, z, 'lantern[hanging=false]')
    # A ring of low standing stones with candles where the mosaic meets the path.
    for i in range(8):
        a = i / 8 * 2 * math.pi + math.pi / 8
        x = int(round(C - 0.5 + math.cos(a) * 6.0))
        z = int(round(C - 0.5 + math.sin(a) * 6.0))
        g.set(x, G, z, 'stone_brick_wall')
        g.set(x, G + 1, z, f'white_candle[candles={1 + i % 3},lit=true]')


def pavilion(g):
    """Isolde's pavilion (north): an open striped tent on spruce posts with a long map table."""
    x0, x1, z0, z1 = C - 6, C + 5, C - 18, C - 11
    for x in (x0, x1):
        for z in (z0, z1):
            for y in range(G, G + 4):
                kit.log(g, x, y, z, 'stripped_spruce_log')
    for x in (x0 + 4, x1 - 4):
        for y in range(G, G + 4):
            kit.log(g, x, y, z1, 'stripped_spruce_log')
    # Canvas roof: a gable along x, wool in Warden teal and white stripes.
    for z in range(z0 - 1, z1 + 2):
        k = abs(z + 0.5 - (z0 + z1 + 1) / 2)
        y = G + 4 + int(3.2 - k * 0.75)
        for x in range(x0 - 1, x1 + 2):
            col = 'cyan_wool' if (x - x0) % 4 < 2 else 'white_wool'
            g.set(x, y, z, col)
    # Scalloped valance: carpets hanging off the front edge (trapdoors read better).
    for x in range(x0 - 1, x1 + 2):
        g.set(x, G + 3, z1 + 1, 'spruce_trapdoor[facing=south,half=top,open=true]' if x % 2 else 'air')
    # Floor: a rug, the map table, chairs, a chest and banner poles.
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            g.set(x, G - 1, z, 'spruce_planks')
            if 1 <= x - x0 <= 9 and 2 <= z - z0 <= 5:
                g.set(x, G, z, 'cyan_carpet' if (x + z) % 2 else 'light_blue_carpet')
    for x in range(x0 + 3, x1 - 2):
        g.set(x, G, z0 + 3, 'spruce_fence')
        g.set(x, G + 1, z0 + 3, 'spruce_slab[type=bottom]')
    g.set(x0 + 5, G, z0 + 3, 'cartography_table')
    g.set(x0 + 5, G + 1, z0 + 3, 'air')
    kit.chair(g, x0 + 4, G, z0 + 2, 'spruce', 'south')
    kit.chair(g, x0 + 7, G, z0 + 2, 'spruce', 'south')
    g.set(x0 + 1, G, z0 + 1, 'lectern[facing=south]')
    g.chest(x1 - 1, G, z0 + 1, 'south', 'afterworld:chests/ruin_cache')
    g.set(x1 - 2, G, z0 + 1, 'barrel[facing=up]')
    g.set(x1 - 2, G + 1, z0 + 1, 'potted_fern')
    kit.hang_lantern(g, C, G + 5, z0 + 3, 1)
    g.mark(C, G, z1 + 3, 'npc:isolde:south')


def stall(g):
    """Bram's supply stall (east): a counter of barrels and slabs under a striped awning, with
    crates, sacks and a handcart."""
    x0, x1, z0, z1 = C + 12, C + 19, C - 5, C + 4
    # Posts and awning.
    for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        for y in range(G, G + 4):
            g.set(x, y, z, 'spruce_fence')
    for x in range(x0 - 1, x1 + 2):
        k = (x - x0) / (x1 - x0)
        y = G + 4 + (1 if k > 0.5 else 0)
        for z in range(z0 - 1, z1 + 2):
            g.set(x, y, z, 'orange_wool' if (z - z0) % 2 == 0 else 'white_wool')
    for z in range(z0 - 1, z1 + 2):
        g.set(x0 - 1, G + 3, z, 'spruce_trapdoor[facing=west,half=top,open=true]' if z % 2 == 0 else 'air')
    # The counter along the west side, with a gap to walk behind.
    for z in range(z0 + 1, z1):
        g.set(x0 + 1, G, z, 'barrel[facing=west]' if z % 3 else 'spruce_planks')
        g.set(x0 + 1, G + 1, z, 'spruce_slab[type=bottom]')
    g.set(x0 + 1, G, z1 - 1, 'air')
    g.set(x0 + 1, G + 1, z1 - 1, 'air')
    # Things on the counter.
    g.set(x0 + 1, G + 2, z0 + 2, 'lantern[hanging=false]')
    g.set(x0 + 1, G + 2, z1 - 2, 'decorated_pot[facing=west]')
    # Back shelves: barrels stacked, hay sacks, a crate wall.
    for z in range(z0 + 1, z1):
        g.set(x1 - 1, G, z, 'barrel[facing=up]' if hash3(z, 1, 2, 3) < 0.5 else 'hay_block[axis=y]')
        g.set(x1 - 1, G + 1, z, 'barrel[facing=west]' if z % 2 else 'barrel[facing=up]')
        if z % 3 == 0:
            g.set(x1 - 1, G + 2, z, 'composter[level=0]')
    kit.hang_lantern(g, x0 + 3, G + 3, C, 0)
    kit.hang_lantern(g, x0 + 3, G + 3, z0 + 1, 0)
    # A handcart outside: a slab bed on two "wheels".
    cx, cz = x0 - 3, z1 + 3
    for dx in range(3):
        g.set(cx + dx, G + 1, cz, 'spruce_slab[type=bottom]')
        g.set(cx + dx, G + 1, cz + 1, 'spruce_slab[type=bottom]')
    g.set(cx, G, cz, 'spruce_trapdoor[facing=north,half=bottom,open=true]')
    g.set(cx + 2, G, cz, 'spruce_trapdoor[facing=north,half=bottom,open=true]')
    g.set(cx + 1, G + 2, cz, 'pumpkin')
    g.set(cx + 2, G + 2, cz + 1, 'melon')
    g.set(cx - 1, G + 1, cz, 'spruce_fence')
    for (x, z) in ((x0 - 2, z0 - 2), (x0 - 2, z0 - 1), (x0 - 1, z0 - 2)):
        g.set(x, G, z, 'barrel[facing=up]')
    g.set(x0 - 2, G + 1, z0 - 2, 'hay_block[axis=x]')
    g.mark(x0 + 3, G, C - 1, 'npc:bram:west')


def forge(g):
    """Marta's forge (west): a stone hearth with a chimney, anvil, grindstone, quenching trough,
    smithing table and a rack of tools under a lean-to roof."""
    x0, x1, z0, z1 = C - 20, C - 12, C - 5, C + 4
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g.set(x, G - 1, z, 'cobblestone' if hash3(x, 7, z, 3) < 0.6 else 'stone_bricks')
    # Hearth along the west wall.
    for z in range(z0, z1 + 1):
        for y in range(G, G + 4):
            g.set(x0, y, z, 'stone_bricks' if hash3(x0, y, z, 4) < 0.7 else 'cracked_stone_bricks')
    for z in (C - 2, C - 1, C):
        g.set(x0 + 1, G, z, 'stone_bricks')
        g.set(x0 + 1, G + 1, z, 'stone_bricks' if z != C - 1 else 'blast_furnace[facing=east,lit=true]')
    g.set(x0 + 1, G, C - 1, 'magma_block')
    g.set(x0 + 1, G + 2, C - 2, 'stone_brick_stairs[facing=east,half=bottom]')
    g.set(x0 + 1, G + 2, C, 'stone_brick_stairs[facing=east,half=bottom]')
    g.set(x0 + 1, G + 2, C - 1, 'stone_bricks')
    # Chimney.
    for y in range(G + 4, G + 11):
        for z in (C - 2, C - 1, C):
            g.set(x0, y, z, 'bricks' if y < G + 10 else 'stone_brick_wall')
    g.set(x0, G + 10, C - 1, 'campfire[lit=true,signal_fire=false]')
    # Lean-to roof sloping west to east.
    for x in range(x0, x1 + 2):
        y = G + 5 - (x - x0) // 3
        for z in range(z0 - 1, z1 + 2):
            if (x - x0) % 3 == 0:
                kit.stairs(g, x, y, z, 'dark_oak', 'west')
            else:
                g.set(x, y, z, 'dark_oak_slab[type=bottom]' if (x - x0) % 3 == 2 else 'dark_oak_slab[type=top]')
    for (x, z) in ((x1, z0), (x1, z1)):
        for y in range(G, G + 4):
            kit.log(g, x, y, z, 'stripped_dark_oak_log')
    # Tools of the trade.
    g.set(x0 + 3, G, C - 1, 'anvil[facing=north]')
    g.set(x0 + 3, G, z0 + 1, 'grindstone[face=floor,facing=north]')
    g.set(x0 + 5, G, z0 + 1, 'smithing_table')
    g.set(x0 + 3, G, z1 - 1, 'water_cauldron[level=3]')
    g.set(x0 + 4, G, z1 - 1, 'lava_cauldron')
    g.set(x0 + 5, G, z1 - 1, 'water_cauldron[level=2]')
    for z in range(z0 + 2, z1 - 1):
        g.set(x1 - 1, G, z, 'spruce_fence')
    g.set(x1 - 1, G + 1, z0 + 2, 'lightning_rod[facing=up]')
    g.set(x1 - 1, G + 1, z0 + 4, 'lightning_rod[facing=up]')
    g.set(x0 + 1, G, z1, 'iron_block')
    g.set(x0 + 1, G, z0, 'coal_block')
    g.set(x0 + 2, G, z0, 'barrel[facing=up]')
    kit.hang_lantern(g, x0 + 4, G + 3, C - 1, 0)
    g.mark(x0 + 4, G, C + 1, 'npc:marta:east')


def map_tent(g):
    """Wren's map tent (south-west): an A-frame of blue canvas, a map board and a cartography desk."""
    x0, z0 = C - 18, C + 9
    L = 7
    for z in range(z0, z0 + L):
        for k in range(4):
            for x in (x0 + k, x0 + 7 - k):
                g.set(x, G + k, z, 'blue_wool' if (z - z0) % 3 else 'light_blue_wool')
        g.set(x0 + 3, G + 4, z, 'spruce_planks')
        g.set(x0 + 4, G + 4, z, 'spruce_planks')
    # Open the north end as the doorway: canvas flaps folded back.
    for x in range(x0 + 1, x0 + 7):
        for y in range(G, G + 3):
            g.set(x, y, z0, 'air')
    # A map board inside: a wall of terracotta painted as a map.
    for x in range(x0 + 2, x0 + 6):
        for y in range(G, G + 3):
            h = hash3(x, y, 1, 9)
            g.set(x, y, z0 + L - 1, 'light_blue_terracotta' if h < 0.3 else 'lime_terracotta' if h < 0.6 else 'white_terracotta' if h < 0.8 else 'yellow_terracotta')
    for x in range(x0 + 1, x0 + 7):
        for z in range(z0 + 1, z0 + L - 1):
            g.set(x, G - 1, z, 'spruce_planks')
    g.set(x0 + 2, G, z0 + 2, 'cartography_table')
    g.set(x0 + 5, G, z0 + 2, 'lectern[facing=north]')
    g.set(x0 + 2, G, z0 + 4, 'barrel[facing=up]')
    g.set(x0 + 5, G, z0 + 4, 'chest[facing=west]')
    kit.hang_lantern(g, x0 + 3, G + 3, z0 + 3, 0)
    g.mark(x0 + 4, G, z0 - 2, 'npc:wren:north')


def sleeping_tents(g):
    """Three small tents with bedrolls along the north-east, round a firepit with log benches."""
    for i, (tx, tz, col) in enumerate(((C + 9, C - 18, 'red'), (C + 15, C - 15, 'green'), (C + 16, C - 9, 'brown'))):
        for z in range(tz, tz + 4):
            for k in range(3):
                g.set(tx + k, G + k, z, f'{col}_wool')
                g.set(tx + 4 - k, G + k, z, f'{col}_wool')
            g.set(tx + 2, G + 3, z, 'spruce_fence')
        for x in range(tx + 1, tx + 4):
            for y in range(G, G + 2):
                g.set(x, y, tz, 'air')
        g.set(tx + 2, G + 2, tz, 'air')
        kit.bed(g, tx + 2, G, tz + 2, 'white' if i % 2 else 'light_gray', 'north')
    # Firepit with log benches.
    fx, fz = C + 11, C - 9
    g.set(fx, G - 1, fz, 'campfire[lit=true]')
    g.set(fx, G - 1, fz, 'stone')
    g.set(fx, G, fz, 'campfire[lit=true]')
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        g.set(fx + dx, G - 1, fz + dz, 'cobblestone')
    for z in range(fz - 1, fz + 2):
        kit.log(g, fx - 3, G, z, 'stripped_oak_log', 'z')
        kit.log(g, fx + 3, G, z, 'stripped_oak_log', 'z')
    for x in range(fx - 1, fx + 2):
        kit.log(g, x, G, fz + 3, 'stripped_oak_log', 'x')


def well(g):
    """A stone well with a little roof and a bucket on a chain."""
    wx, wz = C - 10, C - 12
    for x in range(wx - 1, wx + 2):
        for z in range(wz - 1, wz + 2):
            g.set(x, G, z, 'cobblestone_wall' if (x, z) != (wx, wz) else 'water')
            g.set(x, G - 1, z, 'cobblestone')
    g.set(wx, G - 1, wz, 'water')
    g.set(wx, G - 2, wz, 'water')
    for (x, z) in ((wx - 1, wz - 1), (wx + 1, wz + 1)):
        for y in range(G + 1, G + 3):
            g.set(x, y, z, 'spruce_fence')
    for x in range(wx - 2, wx + 3):
        kit.stairs(g, x, G + 3, wz - 1, 'spruce', 'south')
        kit.stairs(g, x, G + 3, wz + 1, 'spruce', 'north')
        g.set(x, G + 3, wz, 'spruce_slab[type=top]')
    g.set(wx, G + 2, wz, 'iron_chain[axis=y]')
    g.set(wx, G + 1, wz, 'lantern[hanging=true]')


def yard(g):
    """A training yard (south-east): straw dummies, an archery target, a weapon rack."""
    x0, z0 = C + 9, C + 9
    for x in range(x0, x0 + 9):
        for z in range(z0, z0 + 7):
            if hash3(x, 9, z, 2) < 0.8:
                g.set(x, G - 1, z, 'coarse_dirt' if hash3(x, 1, z, 7) < 0.5 else 'dirt_path')
    for x in range(x0 - 1, x0 + 10):
        g.set(x, G, z0 - 1, 'spruce_fence')
        g.set(x, G, z0 + 7, 'spruce_fence')
    for z in range(z0 - 1, z0 + 8):
        g.set(x0 + 9, G, z, 'spruce_fence')
    for z in range(z0 + 2, z0 + 5):
        g.set(x0 - 1, G, z, 'air')
    for (dx, dz) in ((2, 2), (5, 4)):
        g.set(x0 + dx, G, z0 + dz, 'spruce_fence')
        g.set(x0 + dx, G + 1, z0 + dz, 'hay_block[axis=y]')
        g.set(x0 + dx, G + 2, z0 + dz, 'carved_pumpkin[facing=north]')
    g.set(x0 + 7, G, z0 + 1, 'spruce_fence')
    g.set(x0 + 7, G + 1, z0 + 1, 'target')
    g.set(x0 + 7, G + 2, z0 + 1, 'target')
    g.set(x0 + 8, G + 1, z0 + 1, 'spruce_fence')
    g.set(x0 + 6, G + 1, z0 + 1, 'spruce_fence')
    g.set(x0 + 1, G, z0 + 6, 'barrel[facing=up]')
    g.set(x0 + 2, G, z0 + 6, 'spruce_fence')
    g.set(x0 + 3, G, z0 + 6, 'spruce_fence')


def garden(g):
    """A herb garden in raised beds by the pavilion."""
    x0, z0 = C + 6, C - 20
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 3):
            edge = x in (x0, x0 + 4) or z in (z0, z0 + 2)
            if edge:
                g.set(x, G, z, 'stripped_spruce_wood[axis=y]')
            else:
                g.set(x, G, z, 'farmland[moisture=7]')
                g.set(x, G + 1, z, ['wheat[age=7]', 'carrots[age=7]', 'potatoes[age=7]', 'beetroots[age=3]'][int(hash3(x, 2, z, 3) * 4)])
    g.set(x0 + 2, G - 1, z0 + 1, 'water')


def palisade(g):
    """A palisade of spruce logs round most of the camp, with a gate (south) and gaps."""
    for i in range(200):
        a = i / 200 * 2 * math.pi
        x = int(round(C - 0.5 + math.cos(a) * 21.0))
        z = int(round(C - 0.5 + math.sin(a) * 21.0))
        deg = math.degrees(a) % 360
        if 80 <= deg <= 100:      # the south gate
            continue
        if 350 <= deg or deg <= 10 or 170 <= deg <= 190 or 260 <= deg <= 280:
            continue            # gaps east, west and north
        if g.get(x, G - 1, z) in (None, 'minecraft:air'):
            continue
        h = 3 + (1 if hash3(x, 0, z, 9) < 0.5 else 0)
        for y in range(G, G + h):
            kit.log(g, x, y, z, 'spruce_log')
        g.set(x, G + h, z, 'spruce_fence' if hash3(x, 1, z, 9) < 0.7 else 'air')
    # The gate: two tall posts, a beam with a hanging sign-board and lanterns.
    gz = int(round(C - 0.5 + 21.0))
    for x in (C - 3, C + 2):
        for y in range(G, G + 6):
            kit.log(g, x, y, gz, 'stripped_spruce_log')
        g.set(x, G + 6, gz, 'lantern[hanging=false]')
    for x in range(C - 3, C + 3):
        kit.log(g, x, G + 5, gz, 'spruce_log', 'x')
    for x in range(C - 2, C + 2):
        g.set(x, G + 4, gz, 'spruce_trapdoor[facing=south,half=top,open=true]' if x in (C - 2, C + 1) else 'cyan_wool')
    kit.hang_lantern(g, C - 2, G + 4, gz + 1, 0)
    kit.hang_lantern(g, C + 1, G + 4, gz + 1, 0)
    g.mark(C, G, gz - 3, 'spawn')


def lamps(g):
    """Lantern posts along the paths."""
    for (x, z) in ((C - 3, C + 12), (C + 2, C + 15), (C - 3, C + 18), (C + 10, C - 3), (C - 11, C + 2), (C - 3, C - 10), (C + 2, C - 10),
                   (C + 7, C + 7), (C - 8, C - 8)):
        g.set(x, G, z, 'spruce_fence')
        g.set(x, G + 1, z, 'spruce_fence')
        g.set(x, G + 2, z, 'lantern[hanging=false]')


def clutter(g):
    """Hay, crates, banners on poles, flowers in the corners."""
    for (x, z) in ((C - 14, C - 14), (C - 15, C - 13), (C + 14, C + 15)):
        g.set(x, G, z, 'hay_block[axis=y]')
    g.set(C - 14, G + 1, C - 14, 'hay_block[axis=x]')
    for (x, z) in ((C + 5, C + 17), (C - 6, C + 17)):
        for y in range(G, G + 3):
            g.set(x, y, z, 'spruce_fence')
        g.set(x, G + 3, z, 'cyan_wool')
        g.set(x, G + 2, z, 'spruce_fence')
    kit.scatter_ground(g, G - 1, 0, 0, W - 1, D - 1,
                       ['minecraft:short_grass', 'minecraft:short_grass', 'minecraft:fern', 'minecraft:dandelion', 'minecraft:poppy',
                        'minecraft:cornflower', 'minecraft:azure_bluet'], 0.18)


def board(g):
    """The Warden's Board beside the path in from the gate, facing travellers as they arrive."""
    g.set(C + 3, G, C + 8, 'afterworld:wardens_board[facing=south]')
    g.set(C + 4, G, C + 8, 'barrel[facing=up]')
    g.set(C + 2, G, C + 9, 'lantern[hanging=false]')


def build():
    g = Grid(W, H, D)
    ground(g)
    paths(g)
    plaza(g)
    pavilion(g)
    stall(g)
    forge(g)
    map_tent(g)
    sleeping_tents(g)
    well(g)
    yard(g)
    garden(g)
    palisade(g)
    lamps(g)
    clutter(g)
    board(g)
    kit.connect(g)
    return g

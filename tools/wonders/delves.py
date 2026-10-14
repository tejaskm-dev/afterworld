"""Part six, the underground: the delves. Each has a way in on the surface (a ruined forge-house,
a hollow stump, a sinkhole, a mausoleum) and below it, thirty blocks down, a place built or grown
in the dark: halls, caverns, a geode, catacombs. Each keeps a hoard behind a seal and something
that guards it.

The rooms are dug as shells (a skin of the delve's own stone round every space, so caves and
water around them stay outside) and then hollowed, all shells before any hollowing, so rooms that
touch open into each other. The ground is levelled round the way in like any site's: the piece's
box starts at the ground line, so the levelling reaches only a dozen blocks down, and the halls
below are placed whatever the rock there.
"""
import math
import numpy as np
from vox import hash3, vnoise
import kit
import sitekit as K
from sitekit import mob, elite, clue, seal
from sites5 import dist, disc, around, pick, plants, stepping, ramp, surface

D = 'afterworld:'
G = 38
# The grids reach thirty-odd layers above the ground line, as high as the ground is cleared of
# trees round a site's heart (sitekit.clear_core), so no tree is cut off halfway up.
H = G + 32


def blob(choices, x, y, z, seed, scale=4.0):
    """A weighted choice by slow noise, so each material comes in patches rather than speckle."""
    total = sum(w for _, w in choices)
    r = float(vnoise(x, y, z, scale, seed)) * total
    for st, w in choices:
        r -= w
        if r <= 0:
            return st
    return choices[-1][0]


class Dig:
    """Rooms dug out of the rock: their shells first, then their hollows. With `patches`, the
    shells' stone comes in broad patches (a cave's walls) instead of block by block (masonry)."""

    def __init__(self, g, walls, seed=0, patches=None):
        self.g = g
        self.walls = walls
        self.seed = seed
        self.patches = patches
        self.boxes = []
        self.cylinders = []

    def wall(self, x, y, z):
        if self.patches:
            return blob(self.walls, x, y, z, self.seed, self.patches)
        return pick(self.walls, x, y, z, self.seed)

    def room(self, x0, y0, z0, x1, y1, z1, floor=None, ceiling=None):
        """A box of air (x0..x1, y0..y1, z0..z1): y0 is the layer walked on."""
        self.boxes.append((x0, y0, z0, x1, y1, z1, floor, ceiling))

    def shaft(self, cx, cz, r_in, r_out, y0, y1, floor=True):
        """A round shaft from layer y0 up to y1, hollow between r_in and r_out (a solid newel
        inside r_in; none if r_in is negative), lined to r_out + 1 and open at the top, so a shaft
        dug up to the ground line opens to the sky. Without `floor` it opens below too."""
        self.cylinders.append((cx, cz, r_in, r_out, y0, y1, floor))

    def done(self):
        g = self.g
        for (x0, y0, z0, x1, y1, z1, floor, ceiling) in self.boxes:
            for x in range(x0 - 1, x1 + 2):
                for y in range(y0 - 1, y1 + 2):
                    for z in range(z0 - 1, z1 + 2):
                        if 0 <= x < g.sx and 0 <= y < g.sy and 0 <= z < g.sz:
                            g.set(x, y, z, self.wall(x, y, z))
        d_cache = {}
        for (cx, cz, r_in, r_out, y0, y1, floor) in self.cylinders:
            d = d_cache.setdefault((cx, cz), dist(g, cx, cz))
            for y in range(y0 - 1 if floor else y0, y1 + 1):
                for (x, z) in np.argwhere(d <= r_out + 1.0):
                    g.set(x, y, z, self.wall(x, y, z))
        for (x0, y0, z0, x1, y1, z1, floor, ceiling) in self.boxes:
            g.fill(x0, y0, z0, x1, y1, z1, 'air')
            if floor:
                for x in range(x0, x1 + 1):
                    for z in range(z0, z1 + 1):
                        g.set(x, y0 - 1, z, pick(floor, x, y0 - 1, z, self.seed + 1) if isinstance(floor, list) else floor)
            if ceiling:
                for x in range(x0, x1 + 1):
                    for z in range(z0, z1 + 1):
                        g.set(x, y1 + 1, z, pick(ceiling, x, y1 + 1, z, self.seed + 2) if isinstance(ceiling, list) else ceiling)
        for (cx, cz, r_in, r_out, y0, y1, _) in self.cylinders:
            d = d_cache[(cx, cz)]
            for y in range(y0, y1 + 1):
                g.a[:, y, :][(d > r_in) & (d <= r_out)] = g.pid('air')


def pillar(g, x, z, y0, y1, body, cap, base=None):
    for y in range(y0, y1 + 1):
        g.set(x, y, z, body)
    if base:
        g.set(x, y0, z, base)
    g.set(x, y1, z, cap)


def bars(g, x0, z0, x1, z1, y0, h):
    """A wall of iron bars (a seal's gate): returns the cells."""
    cells = []
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(y0, y0 + h):
                g.set(x, y, z, 'iron_bars[north=false,south=false,east=false,west=false,waterlogged=false]')
                cells.append((x, y, z))
    return cells


DEEP = [('deepslate_bricks', 5), ('cracked_deepslate_bricks', 2), ('deepslate_tiles', 1), ('cobbled_deepslate', 1)]


# ================================================================ The Sunken Forge

def sunken_forge():
    """A forge of the old smiths sunk under the hills: on the surface only a ruined forge-house
    and its chimney, an anvil rusting in the grass, a cart of ore; inside the house a round shaft
    winds down thirty blocks to a vaulted hall split by a channel of lava, with the smelting rooms
    and the smiths' quarters off it and at its end the great forge. Light its four fires together
    and the bars of the vault at the hall's far end lift from over the smiths' hoard."""
    g = K.grid(48, H, 48)
    L = 8                        # the hall's floor layer
    sx, sz = 24, 12              # the shaft
    dig = Dig(g, DEEP, seed=401)
    dig.shaft(sx, sz, 1.6, 4.4, L, G - 1)
    dig.room(6, L, 20, 41, L + 9, 34, floor=[('polished_deepslate', 4), ('deepslate_tiles', 2), ('cracked_deepslate_tiles', 1)], ceiling='deepslate_tiles')
    dig.room(23, L, 16, 25, L + 3, 19, floor='polished_deepslate')
    dig.room(2, L, 23, 4, L + 4, 31, floor='polished_blackstone_bricks')
    dig.room(6, L, 11, 16, L + 4, 18, floor=[('deepslate_tiles', 3), ('cobbled_deepslate', 1)])
    dig.room(32, L, 11, 41, L + 4, 18, floor='spruce_planks')
    dig.done()
    # Doors from the hall into the side rooms.
    for (x, z) in ((11, 19), (36, 19)):
        g.fill(x, L, z, x + 1, L + 2, z, 'air')
        g.set(x, L + 3, z, 'chiseled_deepslate')
        g.set(x + 1, L + 3, z, 'chiseled_deepslate')

    # The shaft: a ramp winding round a newel up to the forge-house floor, lights set into the
    # newel's faces, a curb round the opening with a gap where the ramp arrives.
    ramp(g, sx, sz, 1.6, 4.4, L, G - 1, a0=100.0, T=10.0, wood='dark_oak')
    for y in range(L + 2, G - 1, 5):
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            g.set(sx + dx, y, sz + dz, 'shroomlight')
    kit.lantern(g, sx, G, sz)
    # The well's curb: a low ring of stone with a rail on it, open where the ramp arrives.
    top = (100.0 + (G - 1 - L) / 10.0 * 360) % 360
    for (x, z) in np.argwhere((dist(g, sx, sz) > 4.4) & (dist(g, sx, sz) <= 5.4)):
        a = math.degrees(math.atan2(z - sz, x - sx)) % 360
        if abs(((a - top) + 180) % 360 - 180) >= 30:
            g.set(x, G, z, 'polished_deepslate')
            g.set(x, G + 1, z, 'dark_oak_fence')

    # The hall: two rows of pillars with arches between, the lava channel down the middle crossed
    # by two bridges, soul lanterns hung on chains over the channel.
    for x in range(9, 40, 5):
        for z in (22, 32):
            pillar(g, x, z, L, L + 9, 'polished_deepslate', 'chiseled_deepslate', base='deepslate_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
            g.set(x, L, z, 'polished_deepslate')
    for z in (22, 32):
        for x in range(9, 40):
            if (x - 9) % 5:
                kit.stairs(g, x, L + 9, z, 'deepslate_brick', 'east' if (x - 9) % 5 < 3 else 'west', 'top')
    for x in range(10, 36):
        for z in (26, 27, 28):
            g.set(x, L - 2, z, 'blackstone')
            g.set(x, L - 1, z, 'lava[level=0]' if z == 27 else 'magma_block')
        for z in (25, 29):
            g.set(x, L - 1, z, 'polished_blackstone_bricks')
            g.set(x, L, z, 'polished_blackstone_brick_slab[type=bottom]' if x % 2 else 'polished_blackstone_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
    for bx in (16, 28):
        for z in range(25, 30):
            for x in (bx, bx + 1, bx + 2):
                g.set(x, L - 1, z, 'polished_blackstone_bricks')
                g.set(x, L, z, 'air')
            g.set(bx - 1, L, z, 'polished_blackstone_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
            g.set(bx + 3, L, z, 'polished_blackstone_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
    for x in range(12, 36, 6):
        kit.hang_lantern(g, x, L + 9, 27, 3, soul=True)

    # The four fires of the forge: cold braziers on pedestals round the bridges.
    fires = []
    for (x, z) in ((14, 23), (14, 31), (32, 23), (32, 31)):
        g.set(x, L, z, 'polished_deepslate')
        g.set(x, L + 1, z, 'deepslate_brick_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
        K.campfire(g, x, L + 2, z, lit=False)
        fires.append((x, L + 2, z))

    # The great forge at the east end: a raised floor, the hearth, a flue, anvils and a quench.
    g.fill(36, L, 21, 41, L, 33, 'polished_blackstone')
    for z in range(21, 34):
        kit.stairs(g, 35, L, z, 'polished_blackstone', 'east')
    g.fill(39, L + 1, 24, 41, L + 3, 30, 'bricks')
    for z in range(25, 30):
        g.set(39, L + 1, z, 'blast_furnace[facing=west,lit=true]' if z % 2 else 'furnace[facing=west,lit=true]')
    g.set(40, L + 1, 27, 'lava[level=0]')
    g.set(39, L + 2, 27, 'iron_bars[north=true,south=true,east=false,west=false,waterlogged=false]')
    for y in range(L + 4, L + 10):
        g.fill(39, y, 25, 41, y, 29, 'bricks' if y < L + 9 else 'deepslate_tiles')
    g.set(37, L + 1, 23, 'anvil[facing=north]')
    g.set(37, L + 1, 31, 'chipped_anvil[facing=north]')
    g.set(38, L + 1, 23, 'smithing_table')
    g.set(38, L + 1, 31, 'grindstone[face=floor,facing=north]')
    g.set(37, L + 1, 27, 'lava_cauldron')
    g.set(37, L + 1, 26, 'water_cauldron[level=3]')
    # The vault beyond the west end, behind bars that lift when the fires burn.
    g.fill(5, L, 25, 5, L + 2, 29, 'air')
    gate = bars(g, 5, 25, 5, 29, L, 3)
    g.chest(3, L, 27, 'east', 'afterworld:chests/delve_hoard_forge')
    for (x, z) in ((2, 24), (2, 30), (4, 24), (4, 30)):
        g.set(x, L, z, 'raw_gold_block' if (x + z) % 2 else 'gold_block')
    g.set(2, L + 1, 24, 'raw_gold_block')
    kit.lantern(g, 4, L, 23)
    seal(g, 36, L + 1, 27, 'light', 'forge_fires', fires, gate)
    elite(g, 33, L, 27, 'clockwork_sentry')

    # The smelting room: cold furnaces in a row, ore heaped, coal, a cart.
    for x in range(7, 16):
        g.set(x, L, 11, 'furnace[facing=south,lit=false]' if x % 3 else 'blast_furnace[facing=south,lit=false]')
    for (x, z, st) in ((8, 16, 'raw_iron_block'), (9, 16, 'raw_iron_block'), (8, 17, 'raw_copper_block'), (14, 17, 'coal_block'), (15, 17, 'coal_block'), (15, 16, 'raw_gold_block')):
        g.set(x, L, z, st)
    g.set(9, L + 1, 16, 'raw_iron_block')
    for x in range(10, 14):
        g.set(x, L, 15, 'rail[shape=east_west,waterlogged=false]')
    kit.hang_lantern(g, 11, L + 4, 14, 1)
    mob(g, 12, L, 17, 'rust_golemite')

    # The smiths' quarters: bunks, a table, lockers, the forge-master's ledger.
    for x in (33, 35, 37):
        kit.bed(g, x, L, 12, 'gray', 'north')
    g.set(39, L, 11, 'barrel[facing=up,open=false]')
    g.set(40, L, 11, 'barrel[facing=up,open=false]')
    kit.table(g, 37, L, 16)
    kit.chair(g, 36, L, 16, 'spruce', 'east')
    clue(g, 38, L, 16, 'note', 'forge_ledger')
    kit.hang_lantern(g, 36, L + 4, 15, 1)
    g.set(41, L, 18, 'cobweb')

    # The surface: the forge-house round the shaft's head, roofless and half fallen.
    hx0, hz0, hx1, hz1 = 18, 6, 30, 18
    for x in range(hx0, hx1 + 1):
        for z in range(hz0, hz1 + 1):
            if math.hypot(x - sx, z - sz) <= 4.4:
                continue
            g.set(x, G - 1, z, pick([('cobblestone', 3), ('stone_bricks', 2), ('gravel', 1)], x, G - 1, z, 403))
            if x in (hx0, hx1) or z in (hz0, hz1):
                h = 4 - int(vnoise(x, 0, z, 3.0, 404) * 4.5)
                for k in range(max(0, h)):
                    g.set(x, G + k, z, pick([('cobblestone', 2), ('stone_bricks', 2), ('mossy_cobblestone', 1)], x, G + k, z, 405))
    g.fill(24, G, hz1, 25, G + 1, hz1, 'air')
    for k in range(-2, 11):
        g.set(hx0, G + k, hz0, 'bricks')
        g.set(hx0 + 1, G + k, hz0, 'bricks')
    K.campfire(g, hx0, G + 11, hz0, lit=True)
    g.set(31, G, 21, 'anvil[facing=east]')
    K.wagon(g, 34, 22, G, 'x', wood='spruce', cover='gray', broken=True, seed=406)
    g.set(35, G + 2, 22, 'raw_iron_block')
    stepping(g, [(24, 47), (24, hz1 + 1)], G - 1, [('gravel', 2), ('cobblestone', 1)], seed=407, gap=0.2, width=2)
    plants(g, 0, 0, 47, 47, G, [('short_grass', 4), ('fern', 1), ('dead_bush', 1)], 0.12, seed=408)
    mob(g, 20, L, 27, 'gearhound', 3)
    return K.finish(g)


# ================================================================ The Glowcap Hollow

# A cave's earth, in patches (see blob: the weights are spans of the noise, whose middle is
# commonest): moss, then stone, tuff, rooted dirt and a little mossy cobble.
EARTH = [('moss_block', 40), ('stone', 20), ('tuff', 14), ('rooted_dirt', 11), ('mossy_cobblestone', 15)]


def big_shroom(g, x, z, y0, h, cap, r, seed=0):
    """A giant mushroom: a pale stem, a broad cap, shroomlight glowing in the gills under it."""
    for k in range(h):
        g.set(x, y0 + k, z, 'mushroom_stem[down=true,east=true,north=true,south=true,up=true,west=true]')
    top = y0 + h
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            d = math.hypot(dx, dz)
            if d > r + 0.3:
                continue
            y = top - (1 if d > r - 1.2 else 0) + (1 if d < r * 0.45 else 0)
            g.set(x + dx, y, z + dz, f'{cap}[down=false,east=true,north=true,south=true,up=true,west=true]')
            if d <= r - 1.5 and hash3(x + dx, y, z + dz, seed) < 0.35:
                g.set(x + dx, top - 1, z + dz, 'shroomlight')


def glowcap_hollow():
    """Under a hollow stump in the deep wood, a ramp winds down through the roots into a cavern
    of giant glowing mushrooms: spore blossoms hang from its roof, a pool glows with sea pickles,
    a hermit's mushroom hut stands by the pool, and at the cavern's end the Mycelium Heart pulses
    over a cage of stems. Strike the heart and it wakes the cavern's creatures; hold against them
    and the cage opens."""
    g = K.grid(48, H, 48)
    c = 24
    F = 9                        # the cavern floor's layer
    sx, sz = 24, 9               # the stump and its shaft
    dig = Dig(g, EARTH, seed=421, patches=4.0)
    dig.shaft(sx, sz, 1.4, 3.4, F, G - 1)
    dig.done()
    # The cavern: a broad, low dome, its edge ragged, a floor of moss laid level across its bottom.
    cy = F + 2
    for x in range(1, 47):
        for z in range(10, 47):
            for y in range(F - 3, F + 16):
                q = ((x - c) / 21.5) ** 2 + (max(0, y - cy) / 13.0) ** 2 + ((z - 28) / 17.5) ** 2
                q += (float(vnoise(x, y, z, 5.0, 422)) - 0.5) * 0.35
                if q <= 1.0:
                    g.set(x, y, z, 'air' if y >= F else blob([('moss_block', 55), ('rooted_dirt', 13), ('mud', 32)], x, y, z, 423, 3.0))
                elif q <= 1.22:
                    g.set(x, y, z, blob(EARTH, x, y, z, 424, 4.5))
    for x in range(1, 47):
        for z in range(10, 47):
            if g.get(x, F, z) == 'minecraft:air' and g.get(x, F - 1, z) is None:
                g.set(x, F - 1, z, 'moss_block')
    # The shaft's foot opens into the cavern.
    g.fill(sx - 2, F, sz + 3, sx + 2, F + 3, sz + 6, 'air')
    g.fill(sx - 2, F - 1, sz + 3, sx + 2, F - 1, sz + 6, 'moss_block')
    # The ramp's top step comes round to the stump's door (south): a0 + (28 mod 9) / 9 turns.
    ramp(g, sx, sz, 1.4, 3.4, F, G - 1, a0=50.0, T=9.0, wood='mangrove')
    for y in range(F + 2, G - 1, 5):
        g.set(sx, y, sz, 'shroomlight')
    g.set(sx, G - 1, sz, 'shroomlight')
    # Giant mushrooms, red and brown, glowing underneath.
    for i, (x, z, h, r) in enumerate(((10, 22, 9, 4), (37, 20, 11, 5), (14, 38, 8, 4), (33, 36, 10, 4), (23, 30, 7, 3), (41, 31, 6, 3), (6, 31, 6, 3))):
        big_shroom(g, x, z, F, h, 'red_mushroom_block' if i % 2 == 0 else 'brown_mushroom_block', r, seed=425 + i)
    # The roof: spore blossoms and hanging roots wherever it overhangs open air.
    for x in range(2, 46):
        for z in range(11, 46):
            for y in range(F + 15, F + 3, -1):
                above = g.get(x, y + 1, z)
                if g.get(x, y, z) == 'minecraft:air' and above is not None and above != 'minecraft:air' and 'mushroom' not in above and 'shroomlight' not in above:
                    r = hash3(x, y, z, 426)
                    if r < 0.05:
                        g.set(x, y, z, 'spore_blossom')
                    elif r < 0.16:
                        g.set(x, y, z, 'hanging_roots[waterlogged=false]')
                    elif r < 0.22:
                        g.set(x, y, z, 'glow_lichen[down=false,east=false,north=false,south=false,up=true,waterlogged=false,west=false]')
                    break
    # The spore pool, glowing with pickles.
    pd = dist(g, 31, 27)
    for (x, z) in np.argwhere(pd <= 3.8):
        g.set(x, F - 1, z, 'water[level=0]')
        g.set(x, F - 2, z, 'mud')
    for (x, z) in ((30, 26), (32, 28), (31, 29), (29, 28)):
        g.set(x, F - 2, z, 'sea_pickle[pickles=4,waterlogged=true]')
    for (x, z) in ((33, 25), (28, 26)):
        g.set(x, F, z, 'lily_pad')
    # The hermit's hut: a dome of brown mushroom block, a door, a bed, his brews and his notes.
    hx, hz = 20, 41
    for x in range(hx - 3, hx + 4):
        for z in range(hz - 3, hz + 4):
            for k in range(0, 5):
                d = math.sqrt((x - hx) ** 2 + (z - hz) ** 2 + (k * 1.3) ** 2)
                if 2.6 <= d <= 3.6:
                    g.set(x, F + k, z, 'brown_mushroom_block[down=false,east=true,north=true,south=true,up=true,west=true]')
                elif d < 2.6:
                    g.set(x, F + k, z, 'air')
    g.fill(hx, F, hz - 3, hx, F + 1, hz - 3, 'air')
    kit.door(g, hx, F, hz - 3, 'mangrove', 'north')
    kit.bed(g, hx + 1, F, hz + 1, 'brown', 'south')
    g.set(hx - 1, F, hz + 1, 'brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]')
    g.set(hx - 1, F, hz, 'cauldron')
    clue(g, hx, F, hz + 1, 'note', 'hermit_spores')
    kit.lantern(g, hx + 1, F, hz - 1)
    # The Mycelium Heart: a pulsing mass at the cavern's far end, grown over a cage of stems with
    # the hoard shut inside it.
    mx, mz = 34, 43
    for x in range(mx - 3, mx + 4):
        for z in range(mz - 2, mz + 3):
            for k in range(0, 7):
                d = math.sqrt((x - mx) ** 2 + ((k - 3) * 0.9) ** 2 + ((z - mz) * 1.3) ** 2)
                if d <= 3.2 + (float(vnoise(x, k, z, 2.0, 427)) - 0.5) * 0.8:
                    g.set(x, F + 3 + k, z, pick([('red_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=true]', 3), ('shroomlight', 2), ('crying_obsidian', 1)], x, k, z, 428))
    cage = []
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            for k in (0, 1, 2):
                if dx == 0 and dz == 0 and k < 2:
                    continue
                g.set(mx + dx, F + k, mz - 2 + dz, 'mushroom_stem[down=true,east=true,north=true,south=true,up=true,west=true]')
                cage.append((mx + dx, F + k, mz - 2 + dz))
    g.set(mx, F + 1, mz - 2, 'air')
    g.chest(mx, F, mz - 2, 'north', 'afterworld:chests/delve_hoard_glowcap')
    seal(g, mx, F, mz - 6, 'waves-3', 'mycelium_heart', [], cage)
    elite(g, 28, F, 36, 'fungal_brute')
    mob(g, 16, F, 28, 'sporeling', 3)
    plants(g, 1, 11, 46, 46, F, [('moss_carpet', 5), ('red_mushroom', 2), ('brown_mushroom', 2), ('short_grass', 1), ('glow_lichen[down=true,east=false,north=false,south=false,up=false,waterlogged=false,west=false]', 2)], 0.3, seed=429)

    # The surface: the hollow stump, roots spreading, bracket fungi on its sides.
    sd = dist(g, sx, sz)
    for y in range(G - 1, G + 8):
        ring = (sd > 3.4) & (sd <= 5.6 - max(0, y - G - 2) * 0.12)
        for (x, z) in np.argwhere(ring):
            if y < G + 4 + int(float(vnoise(x, 0, z, 2.0, 430)) * 5):
                kit.log(g, x, y, z, 'dark_oak_log')
    for k in range(8):
        t = k * math.pi / 4 + 0.2
        for i in range(5, 11):
            x, z = int(round(sx + math.cos(t) * i)), int(round(sz + math.sin(t) * i))
            if 0 <= x < 48 and 0 <= z < 48:
                kit.log(g, x, G - 1 if i > 7 else G, z, 'dark_oak_log', 'x' if abs(math.cos(t)) > 0.7 else 'z')
    for (x, y, z) in ((sx + 6, G + 2, sz), (sx - 6, G + 3, sz + 1), (sx + 1, G + 1, sz + 6), (sx - 2, G + 4, sz - 6)):
        g.set(x, y, z, 'brown_mushroom_block[down=true,east=true,north=true,south=true,up=true,west=true]')
    # The door: a gap in the stump's wall, its sill the root wood itself.
    g.fill(sx - 1, G, sz + 3, sx + 1, G + 1, sz + 6, 'air')
    plants(g, 0, 0, 47, 47, G, [('fern', 3), ('moss_carpet', 3), ('red_mushroom', 1), ('brown_mushroom', 1), ('short_grass', 2)], 0.2, seed=431)
    return K.finish(g)


# ================================================================ The Geode Vault

def geode_vault():
    """A sinkhole lined with crystal drops into a geode as wide as a hall: its walls banded basalt,
    calcite and amethyst, crystals growing everywhere, and hanging in its middle a spiral of quartz
    winding down to a platform on a pillar of amethyst. Four pylons round the platform carry one
    to four crystals; strike them from the fewest to the most and the crystal case on the
    platform opens. A surveyor's camp and winch stand at the sinkhole's lip."""
    g = K.grid(48, H, 48)
    c = 24
    cy, R = 15, 13.5
    X, Z = g.xz()
    # The geode: shells of smooth basalt, calcite and amethyst round a hollow.
    for y in range(max(0, int(cy - R - 3)), int(cy + R + 3)):
        dd = np.sqrt((X - c) ** 2 + ((y - cy) * 1.0) ** 2 + (Z - c) ** 2)
        layer = g.a[:, y, :]
        layer[(dd > R + 1.6) & (dd <= R + 2.6)] = g.pid('smooth_basalt')
        layer[(dd > R + 0.6) & (dd <= R + 1.6)] = g.pid('calcite')
        layer[(dd > R - 0.4) & (dd <= R + 0.6)] = g.pid('amethyst_block')
        layer[dd <= R - 0.4] = g.pid('air')
    for (x, y, z) in np.argwhere(g.a == g.pid('amethyst_block')):
        r = hash3(int(x), int(y), int(z), 441)
        if r < 0.12:
            g.set(int(x), int(y), int(z), 'budding_amethyst')
        # Crystals grow inward from the shell.
        dx, dy, dz = c - x, cy - y, c - z
        n = math.sqrt(dx * dx + dy * dy + dz * dz) or 1
        ix, iy, iz = int(round(x + dx / n)), int(round(y + dy / n)), int(round(z + dz / n))
        if r > 0.82 and g.get(ix, iy, iz) == 'minecraft:air':
            face = 'up' if abs(dy) >= max(abs(dx), abs(dz)) and dy > 0 else 'down' if abs(dy) >= max(abs(dx), abs(dz)) else \
                ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north')
            K.cluster(g, ix, iy, iz, face, 'amethyst_cluster' if r > 0.92 else 'large_amethyst_bud')
    # Lights set into the shell here and there.
    for i, (a, e) in enumerate(((0, 20), (90, -30), (180, 35), (270, -10), (45, 60), (225, -55), (135, 10), (315, 40))):
        t, p = math.radians(a), math.radians(e)
        x = int(round(c + math.cos(t) * math.cos(p) * (R + 0.2)))
        y = int(round(cy + math.sin(p) * (R + 0.2)))
        z = int(round(c + math.sin(t) * math.cos(p) * (R + 0.2)))
        g.set(x, y, z, 'pearlescent_froglight[axis=y]' if i % 2 else 'sea_lantern')
    # The sinkhole from the surface down to the geode's crown (open beneath, where the crown is
    # as wide as the shaft), a ramp round its wall that carries on down into the crown itself.
    Y = 23                       # the bridge's layer, low enough in the geode to stand up on
    P = cy - 1                   # the platform's
    dig = Dig(g, [('calcite', 3), ('tuff', 2), ('smooth_basalt', 1)], seed=442)
    dig.shaft(c, c, -1.0, 4.6, int(cy + R - 1.5), G - 1, floor=False)
    dig.done()
    # The spiral of quartz hanging in the geode comes up from the platform to the bridge, its top
    # at 180 + (9 mod 10) / 10 turns = 144 degrees; the sinkhole's ramp starts down there too.
    ramp(g, c, c, 7.0, 9.0, P, Y, a0=180.0, T=10.0, slab='smooth_quartz_slab[type=bottom]', full='quartz_bricks')
    ramp(g, c, c, 2.6, 4.6, Y + 1, G - 1, a0=144.0, T=8.0, slab='smooth_quartz_slab[type=bottom]', full='smooth_quartz')
    # The bridge between them, across the open well in the middle of the geode.
    t = math.radians(144.0)
    for (x, z) in np.argwhere(dist(g, c, c) <= 9.4):
        along = (x - c) * math.cos(t) + (z - c) * math.sin(t)
        across = abs(-(x - c) * math.sin(t) + (z - c) * math.cos(t))
        if 2.4 <= along <= 9.4 and across <= 1.2 and g.get(x, Y, z) == 'minecraft:air':
            g.set(x, Y, z, 'chiseled_quartz_block' if across > 0.7 else 'quartz_bricks')
    # The platform on its pillar.
    disc(g, c, c, P - 1, 5.6, 'quartz_bricks')
    disc(g, c, c, P - 1, 5.6, 'chiseled_quartz_block', inner=4.6)
    for y in range(0, P - 1):
        disc(g, c, c, y, 2.2 if y < P - 4 else 3.2, 'amethyst_block')
    # A causeway from the spiral's foot to the platform.
    for i in range(4, 8):
        g.set(c - i, P - 1, c, 'quartz_bricks')
    # The four pylons: one to four crystals each, a button facing the middle.
    counts = [2, 4, 1, 3]
    posts = {}
    for i, a in enumerate((45, 135, 225, 315)):
        t = math.radians(a)
        x, z = int(round(c + math.cos(t) * 4)), int(round(c + math.sin(t) * 4))
        for k in range(3):
            g.set(x, P + k, z, 'amethyst_block' if k < 2 else 'calcite')
        n = counts[i]
        for j, (dx, dz) in enumerate(((0, 0), (1, 0), (0, 1), (-1, 0))[:n]):
            if j == 0:
                K.cluster(g, x, P + 3, z)
            else:
                g.set(x + dx, P + 2, z + dz, 'calcite')
                K.cluster(g, x + dx, P + 3, z + dz)
        fx = -1 if math.cos(t) > 0 else 1
        face = 'west' if fx < 0 else 'east'
        K.button(g, x + fx, P + 1, z, face, mat='polished_blackstone')
        posts[n] = (x + fx, P + 1, z)
    order = [posts[n] for n in sorted(posts)]
    # The crystal case in the middle, the hoard inside.
    case = []
    for (x, z) in ((c - 1, c - 1), (c, c - 1), (c + 1, c - 1), (c - 1, c), (c + 1, c), (c - 1, c + 1), (c, c + 1), (c + 1, c + 1)):
        for k in (0, 1):
            g.set(x, P + k, z, 'tinted_glass')
            case.append((x, P + k, z))
    g.set(c, P + 2, c, 'tinted_glass')
    case.append((c, P + 2, c))
    g.chest(c, P, c, 'south', 'afterworld:chests/delve_hoard_geode')
    seal(g, c + 3, P, c + 2, 'order', 'geode_pylons', order, case)
    elite(g, c - 2, P, c + 3, 'prism_golem')
    mob(g, c + 6, P, c - 5, 'shardling', 2)
    # The surface: crystal spires round the sinkhole's lip, the surveyor's camp and winch.
    for i, (x, z) in enumerate(around(c, c, 8.5, 7, 40)):
        h = 3 + i % 3
        for k in range(h):
            g.set(x, G + k, z, 'amethyst_block' if k < h - 1 else 'calcite')
        K.cluster(g, x, G + h, z)
    for (x, z) in np.argwhere((dist(g, c, c) > 4.6) & (dist(g, c, c) <= 6.5)):
        g.set(x, G - 1, z, pick([('calcite', 3), ('tuff', 2), ('amethyst_block', 1)], x, G, z, 443))
    K.tent(g, 33, 10, 5, G, 'white', axis='x')
    clue(g, 35, G, 10, 'note', 'geode_survey')
    # The surveyors' winch: a beam on two posts over the sinkhole, a chain down the open well
    # with a bucket of a barrel on its end.
    for z in (c - 6, c + 6):
        for k in range(4):
            kit.log(g, c, G + k, z, 'stripped_spruce_log')
    for z in range(c - 6, c + 7):
        kit.log(g, c, G + 4, z, 'stripped_spruce_log', 'z')
    kit.chain(g, c, G - 8, G + 3, c)
    g.set(c, G - 9, c, 'barrel[facing=up,open=false]')
    plants(g, 0, 0, 47, 47, G, [('short_grass', 3), ('small_amethyst_bud[facing=up,waterlogged=false]', 1)], 0.1, seed=444)
    return K.finish(g)


# ================================================================ The Drowned Catacombs

def niche(g, x, y, z, face, seed=0):
    """A burial niche cut into a corridor wall: a skull, bones or a candle in it."""
    r = hash3(x, y, z, seed)
    g.set(x, y, z, 'air')
    g.set(x, y + 1, z, 'air')
    if r < 0.4:
        K.skull(g, x, y, z, rot=int(r * 40) % 16)
    elif r < 0.7:
        g.set(x, y, z, 'bone_block[axis=y]')
        if r < 0.55:
            K.skull(g, x, y + 1, z, rot=int(r * 70) % 16)
    else:
        K.candles(g, x, y, z, 1 + int(r * 10) % 3, lit=r > 0.85, color='white')


def drowned_catacombs():
    """Under a mausoleum of pale stone, a stair runs down to the catacombs: corridors walled with
    burial niches, an ossuary chapel of bone at their crossing, a wing the groundwater has taken
    knee-deep, and four old tombs with cold candles. Light them all and the bars of the Crypt
    Lord's tomb at the south end lift."""
    g = K.grid(48, H, 48)
    c = 24
    L = 10
    pale = [('polished_diorite', 3), ('calcite', 2), ('diorite', 1), ('mossy_stone_bricks', 1)]
    walls = [('stone_bricks', 3), ('mossy_stone_bricks', 2), ('cracked_stone_bricks', 2), ('cobblestone', 1)]
    dig = Dig(g, walls, seed=461)
    # The way down from the mausoleum: a round stairwell under its floor, opening at its foot into
    # the north crossing.
    sx, sz = c, 9
    dig.shaft(sx, sz, 1.4, 3.4, L, G - 1)
    # The corridors: north-south down the middle, three crossings, the chapel, the tomb.
    dig.room(c - 1, L, 13, c + 1, L + 4, 42, floor=pale)
    for z in (14, 24, 34):
        dig.room(5, L, z - 1, 43, L + 4, z + 1, floor=pale)
    dig.room(18, L, 18, 30, L + 7, 30, floor='polished_diorite')
    dig.room(19, L, 40, 29, L + 5, 46, floor='polished_blackstone_bricks')
    dig.done()
    # A ramp of stone round the stairwell's newel, three whole turns (27 layers at 9 a turn), so it
    # starts and ends to the south: at the foot by the crossing, at the head by the mausoleum's
    # door. Soul lanterns are set into the newel's faces on the way down.
    ramp(g, sx, sz, 1.4, 3.4, L, G - 1, a0=90.0, T=9.0, slab='stone_brick_slab[type=bottom]', full='stone_bricks')
    for y in range(L + 3, G - 2, 6):
        for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            kit.lantern(g, sx + dx, y, sz + dz, soul=True)
    g.fill(c - 1, L, 39, c + 1, L + 3, 39, 'air')
    # Niches along the corridor walls, two high, every other block.
    for z in range(7, 42, 2):
        for (x, f) in ((c - 2, 'east'), (c + 2, 'west')):
            if z < 16 or z > 37 or 17 <= z <= 31 or z in (33, 34, 35):
                continue
            niche(g, x, L + 1, z, f, seed=462)
    for zc in (14, 24, 34):
        for x in range(6, 43, 2):
            if c - 7 <= x <= c + 7:
                continue
            for z in (zc - 2, zc + 2):
                niche(g, x, L + 1, z, 'south', seed=463 + zc)
    # The ossuary chapel: bone pillars, a skull altar, candles.
    for (x, z) in ((20, 20), (28, 20), (20, 28), (28, 28)):
        pillar(g, x, z, L, L + 7, 'bone_block[axis=y]', 'chiseled_stone_bricks')
    g.fill(c - 1, L, 19, c + 1, L, 19, 'polished_diorite')
    for x in (c - 1, c + 1):
        K.skull(g, x, L + 1, 19, rot=8)
    K.candles(g, c, L + 1, 19, 4, lit=True, color='white')
    for (x, z) in ((19, 24), (29, 24)):
        kit.hang_lantern(g, x, L + 7, z, 2, soul=True)
    # The flooded wing: the east arm of the south crossing, its floor gone under knee-deep water
    # (sunk a step below the corridor's, so it stays in the wing).
    for x in range(c + 2, 44):
        for z in range(33, 36):
            g.set(x, L - 1, z, 'water[level=0]')
    # The four tombs at the crossings' ends, a cold candle on each.
    lights = []
    for (x, z) in ((6, 14), (42, 14), (6, 34), (42, 24)):
        g.set(x, L, z, 'polished_diorite')
        g.set(x, L + 1, z, 'polished_diorite_slab[type=bottom]')
        K.candles(g, x, L + 2, z, 3, lit=False, color='white')
        lights.append((x, L + 2, z))
    # The Crypt Lord's tomb: bars across its door, a great sarcophagus, the hoard.
    gate = bars(g, c - 1, 39, c + 1, 39, L, 3)
    g.fill(c - 2, L, 43, c + 2, L, 45, 'polished_blackstone')
    g.fill(c - 1, L + 1, 43, c + 1, L + 1, 45, 'polished_blackstone_slab[type=bottom]')
    g.chest(c, L, 42, 'north', 'afterworld:chests/delve_hoard_catacomb')
    for (x, z) in ((20, 41), (28, 41), (20, 45), (28, 45)):
        kit.lantern(g, x, L, z, soul=True)
    seal(g, c, L, 31, 'light', 'catacomb_candles', lights, gate)
    clue(g, c + 1, L, 37, 'note', 'catacomb_epitaph')
    elite(g, c, L, 44, 'mourning_shade')
    mob(g, 36, L, 34, 'drowned_revenant', 2)
    mob(g, 10, L, 24, 'echo_stalker')

    # The mausoleum on the surface: pale stone, columns at its front, a pediment, the stair inside.
    mx0, mz0, mx1, mz1 = c - 5, 4, c + 5, 16
    for x in range(mx0, mx1 + 1):
        for z in range(mz0, mz1 + 1):
            if math.hypot(x - sx, z - sz) > 3.4:
                g.set(x, G - 1, z, 'polished_diorite')
            if x in (mx0, mx1) or z in (mz0, mz1):
                for k in range(6):
                    g.set(x, G + k, z, pick(pale, x, G + k, z, 464))
    g.fill(mx0 + 1, G, mz0 + 1, mx1 - 1, G + 5, mz1 - 1, 'air')
    # A low wall round the stairwell's mouth, open to the south where the stair arrives, and a
    # soul lantern on the newel's head.
    for (x, z) in np.argwhere((dist(g, sx, sz) > 3.4) & (dist(g, sx, sz) <= 4.4)):
        a = math.degrees(math.atan2(z - sz, x - sx)) % 360
        if abs(a - 90.0) > 38 and mx0 < x < mx1 and mz0 < z < mz1:
            g.set(x, G, z, 'diorite_wall[up=true,north=none,south=none,east=none,west=none,waterlogged=false]')
    kit.lantern(g, sx, G, sz, soul=True)
    g.fill(c - 1, G, mz1, c + 1, G + 3, mz1, 'air')
    for x in (c - 3, c + 3):
        for k in range(6):
            g.set(x, G + k, mz1 + 2, 'quartz_pillar[axis=y]' if 0 < k < 5 else 'chiseled_quartz_block')
    for x in range(mx0 - 1, mx1 + 2):
        for z in range(mz0 - 1, mz1 + 4):
            g.set(x, G + 6, z, 'polished_diorite_slab[type=bottom]')
    for i in range(4):
        for x in range(c - 4 + i, c + 5 - i):
            g.set(x, G + 7 + i, mz1 + 3, 'polished_diorite' if i < 3 else 'chiseled_quartz_block')
    for (x, z) in ((c - 2, mz1 + 3), (c + 2, mz1 + 3)):
        kit.lantern(g, x, G, z, soul=True)
    for z in range(mz0 + 2, mz1 - 1, 3):
        for x in (mx0, mx1):
            g.set(x, G + 2, z, 'iron_bars[north=false,south=false,east=false,west=false,waterlogged=false]')
    stepping(g, [(c, 47), (c, mz1 + 3)], G - 1, [('gravel', 2), ('polished_diorite', 1)], seed=465, gap=0.15, width=3)
    for i, (x, z) in enumerate(((10, 30), (14, 34), (34, 30), (38, 36), (12, 40), (36, 42))):
        g.set(x, G, z, pick(pale, x, G, z, 466))
        g.set(x, G + 1, z, 'polished_diorite_slab[type=bottom]')
    plants(g, 0, 0, 47, 47, G, [('short_grass', 3), ('closed_eyeblossom', 1), ('dead_bush', 1), ('fern', 1)], 0.12, seed=467)
    return K.finish(g)


META = {
    'sunken_forge': dict(sink=G, relief=10, weight=3, tags=['delves'],
                         biomes=[D + 'vesper_downs', D + 'ashen_moor', D + 'ember_steppe', D + 'windworn_highlands', D + 'rustscar_canyons']),
    'glowcap_hollow': dict(sink=G, relief=10, weight=3, tags=['delves'],
                           biomes=[D + 'sporeveil', D + 'hollowwood', D + 'gloomwald', D + 'mossmere_wood', D + 'verdant_tangle', D + 'duskpine_weald']),
    'geode_vault': dict(sink=G, relief=10, weight=3, tags=['delves'],
                        biomes=[D + 'shardfields', D + 'rimeglass_spires', D + 'glimmerfrost', D + 'whitewaste', D + 'skyspire_peaks']),
    'drowned_catacombs': dict(sink=G, relief=10, weight=3, tags=['delves'],
                              biomes=[D + 'pale_reach', D + 'gloamfen', D + 'gloomwald', D + 'vesper_downs']),
}

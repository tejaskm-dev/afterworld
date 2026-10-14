"""Afterworld's biome sites: the places that make every land worth walking into. Each builder
returns a Grid; META (at the end) says where each one stands, how deep it sinks and which quest
tags it carries. Words for them live in site_text.py.
"""
import math
from vox import hash3, vnoise
import kit
import sitekit as K
from sitekit import PAL, mob, elite, clue, seal, npc

CACHE = 'afterworld:chests/site_cache'
VAULT = 'afterworld:chests/site_vault'
GLYPH = 'afterworld:chests/glyph_vault'
CRYPT = 'afterworld:chests/mausoleum_crypt'


def flavoured(table, flavour):
    return f'{table}_{flavour}' if flavour else table


# ================================================================ the caravan quest

def caravan_wreck():
    """Two wagons of the silent caravan on the old road: one tipped, one with a cut wheel. Cargo
    left lying, a cold fire, camel bones, vultures. The three clues Rashid needs are here."""
    G = 3
    g = K.grid(32, 20, 32)
    P = PAL['sand']
    K.path(g, [(0, 15), (12, 14), (20, 16), (31, 15)], G - 1, 'smooth_sandstone', width=3, rate=0.45, seed=2)
    K.wagon(g, 6, 10, G, 'x', wood='acacia', cover='orange', tipped=True, seed=1)
    K.wagon(g, 17, 16, G, 'x', wood='acacia', cover='white', broken=True, seed=2)
    g.chest(19, G + 2, 16, 'north', 'afterworld:chests/caravan_goods')
    # The cut wheel lies apart from its wagon.
    g.set(23, G, 19, 'acacia_trapdoor[facing=north,half=bottom,open=false]')
    clue(g, 22, G, 19, 'wagon')
    # Cargo spilled down the road; the manifest under the tipped wagon's cover.
    K.barrels(g, 11, G, 7, seed=3, n=3)
    K.barrels(g, 25, G, 12, seed=4, n=2)
    K.pots(g, [(13, G, 12), (14, G, 8)])
    clue(g, 10, G, 12, 'manifest')
    # Camel bones and a cold fire.
    for (x, z, axis) in ((4, 21, 'x'), (5, 21, 'x'), (6, 22, 'x'), (26, 22, 'z'), (26, 23, 'z')):
        g.set(x, G, z, f'bone_block[axis={axis}]')
    K.skull(g, 7, G, 22, 4)
    K.campfire(g, 14, G, 23, lit=False)
    for (x, z) in ((12, 23), (16, 23)):
        g.set(x, G, z, 'stripped_acacia_log[axis=z]')
    # Tracks leading off into the dunes.
    clue(g, 28, G, 25, 'tracks')
    # A third wagon, burnt to its frame.
    for i in range(5):
        for w in (-1, 1):
            g.set(5 + i, G, 26 + w, 'stripped_acacia_log[axis=x]' if i % 2 == 0 else 'air')
        g.set(5 + i, G, 26, 'acacia_slab[type=bottom]' if i % 2 else 'coal_block')
    K.campfire(g, 7, G + 1, 26, lit=False)
    # A camel's ribs, sand drifted against the wagons, the old road's marker post.
    for k, z in enumerate((5, 7, 9)):
        K.rib(g, 26, z, G, 4, 2 - (k == 2), axis='x')
    K.skull(g, 29, G, 7, 12)
    for (x, z, r) in ((8, 8, 2.2), (20, 20, 1.8), (13, 17, 1.5)):
        K.mound(g, x, z, G, r, 1, r, [('sand', 1)], seed=int(x * 7 + z))
    g.fill(2, G, 13, 2, G + 2, 13, 'acacia_fence')
    g.set(2, G + 3, 13, 'acacia_fence')
    g.set(3, G + 3, 13, 'acacia_trapdoor[facing=east,half=top,open=true]')
    K.debris(g, 0, 0, 31, 31, G, ['sand', 'dead_bush', 'sandstone_slab[type=bottom]', 'short_dry_grass'], 0.05, seed=6)
    mob(g, 16, G + 1, 5, 'canyon_vulture', 2)
    mob(g, 27, G, 6, 'sand_sifter')
    return K.finish(g, P)


def buried_obelisk():
    """A gilded obelisk drowning in sand. Under it, the storm-eaters' hall; off to the side, the
    room where Leyla and the drivers hid; behind the hall, the sanctum with a Glyph Tablet."""
    G = 12
    g = K.grid(32, 36, 32)
    P = PAL['sand']
    # Dunes heaped around the base.
    K.mound(g, 16, 16, G, 9, 3, 9, [('sand', 5), ('sandstone', 1)], seed=4)
    K.obelisk(g, 16, 16, G - 1, 18, P, cap='gold_block', glyph='chiseled_sandstone')
    for (x, z) in ((16, 17), (17, 16), (15, 16), (16, 15)):
        g.set(x, G + 2, z, 'gold_block')
    # A ring of broken sun-dial stones around it.
    for a in range(0, 360, 30):
        t = math.radians(a)
        x, z = int(round(16 + math.cos(t) * 11)), int(round(16 + math.sin(t) * 11))
        h = 1 + int(hash3(x, 0, z, 3) * 3)
        for k in range(h):
            g.set(x, G + k, z, P.block if k < h - 1 else 'cut_sandstone_slab[type=bottom]')
    # Below: the hall, the hideout and the sanctum.
    K.chamber(g, 8, G - 9, 10, 24, G - 4, 20, P)
    K.chamber(g, 2, G - 9, 12, 6, G - 6, 18, P)
    K.chamber(g, 12, G - 9, 23, 20, G - 5, 29, P)
    K.stair_down(g, 16, 1, G - 1, 9, 'south', P, width=3)
    # Doors: a spruce door into the hideout (the storm-eaters never learned doors), an arch south.
    kit.door(g, 7, G - 9, 15, 'acacia', 'west')
    g.fill(15, G - 9, 21, 17, G - 7, 22, 'air')
    kit.stairs(g, 15, G - 7, 21, P.stair, 'east', 'top')
    kit.stairs(g, 17, G - 7, 21, P.stair, 'west', 'top')
    # The hall: pillars, fallen stones, a dry well of sand, glyph bands.
    for x in (10, 22):
        for z in (12, 18):
            g.fill(x, G - 9, z, x, G - 4, z, 'cut_sandstone')
            g.set(x, G - 5, z, 'chiseled_sandstone')
    for (x, z) in ((12, 14), (20, 16), (14, 19)):
        g.set(x, G - 9, z, 'sand')
    for x in range(9, 24, 3):
        if x not in (15, 16, 17):
            g.set(x, G - 6, 9, 'chiseled_sandstone')
            g.set(x, G - 6, 21, 'chiseled_sandstone')
    kit.hang_lantern(g, 16, G - 4, 15, 0, soul=True)
    elite(g, 16, G - 9, 14, 'sand_sifter')
    mob(g, 12, G - 9, 17, 'glyph_scarab', 2)
    # The hideout: bedrolls, water, spice, a soul lantern, Leyla.
    for z in (13, 15, 17):
        g.set(3, G - 9, z, 'orange_carpet')
        g.set(4, G - 9, z, 'orange_carpet')
    g.set(6, G - 9, 13, 'water_cauldron[level=3]')
    K.barrels(g, 5, G - 9, 17, seed=8, n=2)
    kit.lantern(g, 2, G - 9, 18, soul=True)
    clue(g, 5, G - 9, 12, 'note', 'obelisk_hideout')
    npc(g, 4, G - 9, 15, 'leyla', 'east')
    # The sanctum: an altar, two gilt pillars, the tablet's chest.
    for x in (13, 19):
        g.fill(x, G - 9, 27, x, G - 5, 27, 'cut_sandstone')
        g.set(x, G - 5, 27, 'gold_block')
    g.set(16, G - 9, 28, 'chiseled_sandstone')
    g.chest(16, G - 8, 28, 'north', GLYPH)
    K.candles(g, 15, G - 9, 28, 3, True, 'yellow')
    K.candles(g, 17, G - 9, 28, 3, True, 'yellow')
    mob(g, 16, G - 9, 25, 'glyph_scarab', 2)
    return K.finish(g, P, weather_rate=0.18)


# ================================================================ the glyph tablets

def sunken_bog_temple():
    """An old Warden temple sinking into the fen: flooded courts, broken pillars, walkways, and a
    raised sanctum ringed by four cold braziers. Light them all and the tablet's niche opens."""
    G = 4
    g = K.grid(48, 24, 48)
    P = PAL['mossy']
    M = PAL['mud']
    x0, x1, z0, z1 = 8, 39, 8, 39
    # The flooded court: water one and two deep, then the outer walls standing in it.
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            g.set(x, G - 2, z, 'mud')
            g.set(x, G - 1, z, 'water')
    for (a, b, c, d) in ((x0, z0, x1, z0), (x0, z1, x1, z1), (x0, z0, x0, z1), (x1, z0, x1, z1)):
        g.fill(a, G - 2, b, c, G - 1, d, M.block)
        K.ruin_wall(g, a, b, c, d, G, 7, P.block, seed=11, decay=0.6)
    g.fill(23, G, z1, 24, G + 2, z1, 'air')
    # Walkways in a cross, and broken pillars along them.
    for i in range(x0 + 1, x1):
        for w in (23, 24):
            g.set(i, G - 1, w, f'{P.slab}_slab[type=top]')
            g.set(w, G - 1, i, f'{P.slab}_slab[type=top]')
    for k, (x, z) in enumerate(((13, 19), (13, 28), (34, 19), (34, 28), (19, 13), (28, 13), (19, 34), (28, 34))):
        h = 3 + int(hash3(x, k, z, 5) * 5)
        g.fill(x, G - 1, z, x, G + h - 1, z, P.block)
        g.set(x, G + h, z, P.accent if h > 5 else f'{P.slab}_slab[type=bottom]')
    # The sanctum: raised floor, mud-brick walls, a doorway south.
    g.fill(17, G - 1, 17, 30, G - 1, 30, P.block)
    K.room(g, 17, 17, 30, 30, G, 6, M, floor=False, door=(23, 30))
    g.set(24, G, 30, 'air')
    g.set(24, G + 1, 30, 'air')
    g.fill(18, G + 6, 18, 29, G + 6, 29, P.block)
    K.erode(g, 17, 17, 30, 30, G + 5, G + 8, seed=4, scale=3.0)
    fires = [K.brazier(g, x, G, z, P, lit=False) for (x, z) in ((19, 19), (28, 19), (19, 28), (28, 28))]
    # The niche at the back with the tablet, barred until the seal turns.
    g.fill(22, G, 17, 25, G + 2, 17, 'air')
    g.fill(22, G, 16, 25, G + 3, 16, M.block)
    g.fill(22, G - 1, 16, 25, G - 1, 17, P.accent)
    bars = [(x, y, 18) for x in range(22, 26) for y in (G, G + 1, G + 2)]
    for b in bars:
        g.set(*b, 'iron_bars')
    g.chest(23, G, 17, 'south', GLYPH)
    g.chest(24, G, 17, 'south', flavoured(VAULT, 'swamp'))
    seal(g, 23, G, 23, 'light', 'temple_braziers', fires, bars)
    clue(g, 24, G, 33, 'glyph', 'temple_keeper')
    # Lily pads, roots, moss.
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if g.get(x, G - 1, z) == 'minecraft:water' and hash3(x, 9, z, 2) < 0.12:
                g.set(x, G, z, 'lily_pad')
    K.debris(g, x0, z0, x1, z1, G + 7, ['moss_carpet', 'mangrove_roots'], 0.2, seed=8)
    K.debris(g, 0, 0, 47, 47, G, ['mangrove_roots', 'moss_carpet', 'brown_mushroom'], 0.03, seed=9)
    mob(g, 14, G, 24, 'mire_shambler', 2)
    mob(g, 33, G, 23, 'fen_toad', 2)
    mob(g, 14, G - 1, 14, 'bog_lurker', clear=False)
    elite(g, 23, G, 26, 'mire_shambler')
    return K.finish(g, P)


def frozen_mage_tower():
    """A mage's tower frozen from the cellar up. A cold hearth on each of three floors; warm all
    three and the ice case at the top, round the tablet, thaws."""
    G = 3
    g = K.grid(32, 60, 32)
    P = PAL['frost']
    cx = cz = 16
    r = 6
    floors = (G + 10, G + 20, G + 30)
    K.mound(g, cx, cz, G - 1, 12, 3, 12, [('packed_ice', 2), ('snow_block', 3), ('stone', 1)], seed=2)
    K.round_tower(g, cx, cz, G, 40, r, P, floors=floors, door='south', crenel=False, seed=3)
    # Bands of ice in the stone, and a pointed roof of blue ice over an overhang.
    for y in range(G + 4, G + 40, 9):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                if g.get(x, y, z) == 'minecraft:stone_bricks':
                    g.set(x, y, z, 'packed_ice')
    g.disc(cx, cz, G + 40, r + 1, 'stone_bricks')
    kit.spire(g, cx, cz, G + 41, 12, r + 1, 'blue_ice', tip='end_rod[facing=up]')
    g.cone(cx, cz, G + 41, 11, r - 0.5, 'air', 0.0)
    # Hearths: one cold soul fire per floor, on a stone plinth near the middle.
    hearths = []
    for fy in (G, floors[0] + 1, floors[1] + 1):
        g.set(cx + 2, fy, cz, 'polished_andesite')
        K.campfire(g, cx + 2, fy + 1, cz, lit=False, soul=True)
        hearths.append((cx + 2, fy + 1, cz))
    # Frozen library on the second floor; benches and a cauldron on the first.
    for (x, z) in ((cx - 4, cz - 2), (cx - 4, cz - 1), (cx - 4, cz), (cx - 4, cz + 1), (cx - 4, cz + 2)):
        for y in range(floors[1] + 1, floors[1] + 4):
            g.set(x, y, z, 'bookshelf' if hash3(x, y, z, 1) < 0.7 else 'packed_ice')
    g.set(cx - 2, floors[0] + 1, cz - 3, 'cauldron')
    g.set(cx - 1, floors[0] + 1, cz - 3, 'brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]')
    g.set(cx - 3, floors[0] + 1, cz + 3, 'enchanting_table')
    clue(g, cx - 2, floors[1] + 1, cz + 2, 'note', 'mage_journal')
    # The top room: the seal, and the tablet sealed in ice.
    top = floors[2] + 1
    case = []
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            for dy in (0, 1):
                if dx == 0 and dz == 0 and dy == 0:
                    continue
                case.append((cx - 2 + dx, top + dy, cz - 2 + dz))
    for c in case:
        g.set(*c, 'blue_ice')
    g.chest(cx - 2, top, cz - 2, 'south', GLYPH)
    seal(g, cx, top, cz + 2, 'light', 'tower_hearths', hearths, case)
    for a in range(0, 360, 90):
        t = math.radians(a + 45)
        kit.hang_lantern(g, int(round(cx + math.cos(t) * 3)), floors[2] + 9, int(round(cz + math.sin(t) * 3)), 1, soul=True)
    # Ice spikes round the foot.
    for k, (x, z) in enumerate(((5, 7), (26, 9), (8, 26), (25, 25), (3, 17))):
        h = 4 + int(hash3(x, k, z, 9) * 6)
        for y in range(G - 1, G + h):
            g.set(x, y, z, 'packed_ice')
        g.set(x, G + h, z, 'ice')
    elite(g, cx, G, cz + r + 3, 'rime_brute')
    mob(g, cx - 2, floors[0] + 1, cz + 2, 'ice_crawler', 2)
    mob(g, cx + 1, floors[1] + 1, cz - 3, 'rime_wight')
    mob(g, cx + 2, top, cz + 1, 'rime_moth', 2)
    return K.finish(g, P, weather_rate=0.15)


def overgrown_ziggurat():
    """A stepped pyramid swallowed by the jungle. Four braziers on the summit; light them all and
    the summit floor opens onto a shaft down to the heart chamber and its tablet."""
    G = 3
    g = K.grid(48, 36, 48)
    P = PAL['mossy']
    c = 24
    tiers = 4
    for t in range(tiers):
        hs = 15 - t * 3
        y0 = G + t * 4
        g.fill(c - hs, y0 - 1, c - hs, c + hs, y0 + 3, c + hs, P.block)
        K.crenellate(g, c - hs, c - hs, c + hs, c + hs, y0 + 4, f'{P.slab}_slab[type=bottom]')
    top_y = G + tiers * 4
    # The great stair: a ramp standing out from the south face, straight up to the summit.
    rise = tiers * 4
    for i in range(rise):
        z = c + 6 + rise - i
        y = G - 1 + i
        for x in range(c - 2, c + 3):
            g.fill(x, G - 1, z, x, y - 1, z, P.block)
            kit.stairs(g, x, y, z, P.stair, 'north')
            g.fill(x, y + 1, z, x, y + 3, z, 'air')
        for x in (c - 3, c + 3):
            g.fill(x, G - 1, z, x, y, z, P.block)
    # The heart chamber inside, and the shaft above it.
    g.fill(c - 3, G + 4, c - 3, c + 3, G + 8, c + 3, 'air')
    g.fill(c - 3, G + 3, c - 3, c + 3, G + 3, c + 3, P.accent)
    g.fill(c - 1, G + 9, c - 1, c + 1, top_y - 1, c + 1, 'air')
    g.fill(c, G + 4, c + 2, c, G + 8, c + 2, P.block)
    K.ladder(g, c, c + 1, G + 4, top_y - 2, 'north')
    hatch = [(x, top_y - 1, z) for x in (c - 1, c, c + 1) for z in (c - 1, c, c + 1)]
    for h in hatch:
        g.set(*h, P.accent)
    g.chest(c, G + 4, c - 2, 'south', GLYPH)
    g.chest(c + 2, G + 4, c - 2, 'south', flavoured(VAULT, 'jungle'))
    K.pots(g, [(c - 2, G + 4, c - 2), (c - 3, G + 4, c + 2)])
    kit.hang_lantern(g, c - 2, G + 8, c + 1, 0)
    # The summit shrine: four braziers at the corners, the seal before the hatch.
    fires = [K.brazier(g, x, top_y, z, P, lit=False) for (x, z) in ((c - 3, c - 3), (c + 3, c - 3), (c - 3, c + 3), (c + 3, c + 3))]
    seal(g, c, top_y, c + 3, 'light', 'ziggurat_braziers', fires, hatch)
    clue(g, c + 1, top_y, c + 3, 'note', 'ziggurat_explorer')
    # Jungle over everything.
    K.debris(g, 0, 0, 47, 47, G, ['jungle_leaves[persistent=true]', 'fern', 'moss_carpet'], 0.10, seed=3)
    for t in range(tiers):
        hs = 15 - t * 3
        K.debris(g, c - hs, c - hs, c + hs, c + hs, G + t * 4 + 4, ['jungle_leaves[persistent=true]', 'moss_carpet', 'moss_block'], 0.18, seed=10 + t)
    mob(g, c - 10, G + 4, c + 8, 'vine_strangler', 2)
    elite(g, c, G + 4, c + 1, 'vine_strangler')
    return K.finish(g, P, weather_rate=0.3)


def moonlit_clearing():
    """A fairy ring in the weald: mushrooms in a circle, mossy stones with cold candles, a moon
    altar. Light every candle and the altar's buried gift is uncovered."""
    G = 3
    g = K.grid(32, 20, 32)
    P = PAL['pale']
    c = 16
    for a in range(0, 360, 12):
        t = math.radians(a)
        x, z = int(round(c + math.cos(t) * 10)), int(round(c + math.sin(t) * 10))
        g.set(x, G, z, 'red_mushroom' if a % 24 else 'brown_mushroom')
    lights = []
    for i in range(6):
        t = math.radians(i * 60 + 15)
        x, z = int(round(c + math.cos(t) * 7)), int(round(c + math.sin(t) * 7))
        h = 2 + i % 2
        K.standing_stone(g, x, z, G, h, 'mossy_cobblestone', seed=i)
        K.candles(g, x, G + h, z, 1 + i % 3, lit=False, color='white')
        lights.append((x, G + h, z))
    # The moon altar.
    g.fill(c - 1, G - 1, c - 1, c + 1, G - 1, c + 1, 'calcite')
    g.set(c, G, c, 'chiseled_quartz_block')
    kit.slab(g, c - 1, G, c, 'quartz')
    kit.slab(g, c + 1, G, c, 'quartz')
    seal(g, c, G + 1, c, 'light', 'fae_candles', lights, [(c, G - 1, c + 3)])
    g.set(c, G - 1, c + 3, 'mossy_cobblestone')
    g.fill(c - 1, G - 3, c + 2, c + 1, G - 3, c + 4, 'stone')
    g.chest(c, G - 2, c + 3, 'north', GLYPH)
    clue(g, c - 3, G, c + 2, 'note', 'fae_bargain')
    # Old trees at the edge, flowers in the clearing.
    for k, (x, z) in enumerate(((3, 4), (28, 6), (4, 27), (27, 27))):
        K.dead_tree(g, x, z, G, 6 + k % 3, 'dark_oak_log', seed=k)
        kit.hang_lantern(g, x + 1, G + 5, z, 0, soul=True)
    K.debris(g, 0, 0, 31, 31, G, ['lily_of_the_valley', 'azure_bluet', 'short_grass', 'fern', 'oxeye_daisy'], 0.18, seed=4)
    elite(g, c + 5, G, c - 5, 'dusk_hound')
    mob(g, c - 6, G, c - 4, 'kitsune')
    return K.finish(g)


# ================================================================ the Lich's crypts

def barrow_mound():
    """A grassy barrow on the Downs with a stone door. Down the steps: the burial hall with its
    sarcophagi; beyond, the inner room where the Lich keeps a phylactery."""
    G = 10
    g = K.grid(32, 24, 32)
    P = PAL['mossy']
    K.mound(g, 16, 15, G - 1, 12, 6, 12, [('dirt', 5), ('coarse_dirt', 1), ('stone', 1)], seed=3, top='grass_block')
    K.chamber(g, 10, G - 7, 9, 22, G - 3, 20, P)
    K.chamber(g, 12, G - 7, 3, 20, G - 3, 7, PAL['deep'])
    K.stair_down(g, 16, 28, G - 1, 7, 'north', P, width=3)
    # The dolmen at the door: two stones and a lintel.
    for x in (14, 18):
        g.fill(x, G - 1, 29, x, G + 2, 29, 'mossy_cobblestone')
    g.fill(14, G + 3, 29, 18, G + 3, 29, 'stone_brick_slab[type=bottom]')
    g.fill(13, G - 1, 30, 19, G - 1, 31, 'mossy_cobblestone')
    # The burial hall.
    for z in (11, 14, 17):
        K.sarcophagus(g, 11, G - 7, z, 'x', P)
        K.sarcophagus(g, 20, G - 7, z, 'x', P)
    for (x, z) in ((10, 9), (22, 9), (10, 20), (22, 20)):
        g.fill(x, G - 7, z, x, G - 3, z, 'mossy_stone_bricks')
    kit.hang_lantern(g, 16, G - 3, 14, 0, soul=True)
    g.chest(21, G - 7, 10, 'west', CRYPT)
    for (x, z) in ((13, 19), (19, 12)):
        g.set(x, G - 7, z, 'bone_block[axis=x]')
    K.skull(g, 12, G - 7, 18, 3)
    # The inner door and room: the phylactery on its altar.
    g.fill(16, G - 7, 8, 16, G - 6, 8, 'air')
    clue(g, 16, G - 7, 9, 'glyph', 'barrow_warning')
    g.set(16, G - 7, 4, 'chiseled_deepslate')
    g.set(16, G - 6, 4, 'afterworld:phylactery')
    for x in (13, 19):
        K.candles(g, x, G - 7, 4, 4, True, 'red')
        g.fill(x, G - 7, 3, x, G - 3, 3, 'polished_deepslate')
    kit.hang_lantern(g, 16, G - 3, 6, 0, soul=True)
    mob(g, 13, G - 7, 15, 'minecraft:skeleton', 2)
    mob(g, 19, G - 7, 16, 'minecraft:zombie')
    elite(g, 16, G - 7, 6, 'minecraft:skeleton')
    K.top_scatter(g, 0, 0, 31, 31, ['short_grass', 'poppy', 'short_grass', 'fern'], 0.22, seed=4, on={'grass_block'})
    return K.finish(g, P, weather_rate=0.25)


def ossuary_gate():
    """A gate of bone in a white hillside. Behind it, a passage down to the ossuary: skulls in
    their niches, bone pillars, candles, and the Lich's second phylactery."""
    G = 8
    g = K.grid(32, 30, 32)
    P = PAL['pale']
    K.mound(g, 16, 10, G - 1, 14, 9, 11, [('calcite', 3), ('stone', 3), ('tuff', 1), ('diorite', 1)], seed=5)
    K.chamber(g, 9, G - 5, 3, 23, G - 1, 14, P)
    K.stair_down(g, 16, 20, G - 1, 5, 'north', P, width=3)
    g.fill(15, G, 17, 17, G + 2, 21, 'air')
    # The gate: bone pillars, a diorite arch, skulls along the top.
    for x in (13, 19):
        g.fill(x, G - 1, 21, x, G + 4, 21, 'bone_block[axis=y]')
        g.fill(x, G - 1, 20, x, G + 4, 20, 'bone_block[axis=y]')
    kit.arch(g, 13, 19, G + 2, 2, P.stair, axis='x', at=21, fill_above='polished_diorite', top=G + 5)
    g.fill(13, G + 5, 21, 19, G + 5, 21, 'polished_diorite')
    for x in range(13, 20):
        K.skull(g, x, G + 6, 21, 0)
    g.fill(12, G - 1, 22, 20, G - 1, 25, 'calcite')
    # The ossuary: skulls in the walls, bone pillars, the altar.
    for z in range(4, 13, 2):
        for y in (G - 4, G - 2):
            K.skull(g, 8, y, z, 12)
            K.skull(g, 24, y, z, 4)
    for x in range(10, 23, 2):
        for y in (G - 4, G - 2):
            K.skull(g, x, y, 2, 0)
    for (x, z) in ((12, 6), (20, 6), (12, 10), (20, 10)):
        g.fill(x, G - 5, z, x, G - 1, z, 'bone_block[axis=y]')
    g.set(16, G - 5, 4, 'chiseled_quartz_block')
    g.set(16, G - 4, 4, 'afterworld:phylactery')
    for x in (14, 18):
        K.candles(g, x, G - 5, 4, 3, True, 'red')
    kit.hang_lantern(g, 16, G - 1, 8, 1, soul=True)
    g.chest(22, G - 5, 12, 'west', CRYPT)
    clue(g, 10, G - 5, 12, 'note', 'ossuary_ledger')
    mob(g, 13, G - 5, 9, 'minecraft:skeleton', 3)
    elite(g, 16, G - 5, 7, 'mourning_shade')
    mob(g, 8, G, 27, 'bone_gull', 2)
    return K.finish(g, P, weather_rate=0.2)


# ================================================================ where they stand

D = 'afterworld:'
META = {
    'caravan_wreck': dict(sink=3, relief=8, weight=4, biomes=[D + 'hourglass_dunes', D + 'ember_steppe'], tags=['caravan_wreck']),
    'buried_obelisk': dict(sink=12, relief=8, weight=4, biomes=[D + 'hourglass_dunes'], tags=['buried_obelisk', 'glyph_sites']),
    'sunken_bog_temple': dict(sink=4, relief=6, weight=4, biomes=[D + 'gloamfen'], tags=['glyph_sites']),
    'frozen_mage_tower': dict(sink=3, relief=14, weight=4, biomes=[D + 'rimeglass_spires'], tags=['glyph_sites']),
    'overgrown_ziggurat': dict(sink=3, relief=12, weight=4, biomes=[D + 'verdant_tangle'], tags=['glyph_sites']),
    'moonlit_clearing': dict(sink=3, relief=8, weight=4, biomes=[D + 'duskpine_weald'], tags=['glyph_sites']),
    'barrow_mound': dict(sink=10, relief=8, weight=4, biomes=[D + 'vesper_downs'], tags=['crypts']),
    'ossuary_gate': dict(sink=8, relief=12, weight=4, biomes=[D + 'pale_reach'], tags=['crypts']),
}

# Part two: every land's own places; part three: the Wardens' lairs; four: the gate of the Warden's Ladder.
import sites2 as _part2
import sites3 as _part3
import ladder as _part4
for _part in (_part2, _part3, _part4):
    META.update(_part.META)
    for _n in _part.META:
        globals()[_n] = getattr(_part, _n)

# Part five, the second look: sites rebuilt wider and more detailed. They keep their names, lands
# and tags (META above); only their builders change.
import sites5 as _part5
# Part six, the underground: the delves, each a way in on the surface and halls far below.
import delves as _part6
META.update(_part6.META)
for _n in _part6.META:
    globals()[_n] = getattr(_part6, _n)
for _n in _part5.REBUILT:
    globals()[_n] = getattr(_part5, _n)
    if _n in _part5.SINK:
        META[_n]['sink'] = _part5.SINK[_n]

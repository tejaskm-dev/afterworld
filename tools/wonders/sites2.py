"""Afterworld's biome sites, part two: every land's own places (kilns and cult towers on the moor,
the bone orchard, the weaver's cave, the lantern grove, the crystal observatory, the tea house and
the Lantern Dojo, watch posts, giant bones and snowbound camps, the monastery and the great nest,
the wind harp, nomad yurts, the starfall crater, mines and canyon bridges, canopy huts, the
alchemist's mushroom, the glowcap grotto, the rangers' palisade, the druids' ring, the aurora
shrine and the hermit's treehouse)."""
import math
import numpy as np
from vox import hash3
import kit
import sitekit as K
from sitekit import PAL, mob, elite, clue, seal, npc, boss

CACHE = 'afterworld:chests/site_cache'
VAULT = 'afterworld:chests/site_vault'


def fl(table, flavour):
    return f'{table}_{flavour}'


# ================================================================ Ashen Moor

def ember_kilns():
    """Four brick kilns round a blackstone vault. Light all four at once and the vault's bars fall."""
    G = 3
    g = K.grid(32, 24, 32)
    P = PAL['black']
    K.floor(g, 4, 4, 27, 27, G - 1, 'blackstone', rate=0.45, seed=1)
    # The vault.
    g.fill(12, G - 1, 12, 19, G - 1, 19, P.floor)
    for y in range(G, G + 5):
        for x in range(12, 20):
            for z in range(12, 20):
                if x in (12, 19) or z in (12, 19):
                    g.set(x, y, z, P.block)
    g.fill(12, G + 5, 12, 19, G + 5, 19, P.block)
    K.crenellate(g, 12, 12, 19, 19, G + 6, f'{P.slab}_slab[type=bottom]')
    bars = [(x, y, 19) for x in (14, 15, 16, 17) for y in (G, G + 1, G + 2)]
    for b in bars:
        g.set(*b, 'iron_bars')
    g.chest(14, G, 13, 'south', fl(VAULT, 'ash'))
    g.chest(17, G, 13, 'south', fl(CACHE, 'ash'))
    kit.hang_lantern(g, 15, G + 4, 15, 0)
    # The kilns: brick domes with a cold fire inside and a chimney.
    fires = []
    for (cx, cz, mouth) in ((6, 6, 'south'), (25, 6, 'south'), (6, 25, 'north'), (25, 25, 'north')):
        g.fill(cx - 3, G - 1, cz - 3, cx + 3, G - 1, cz + 3, 'bricks')
        kit.dome(g, cx, G, cz, 3.4, 'bricks', inner='air')
        dx, dz = K.FACE[mouth]
        g.fill(cx + dx * 3, G, cz + dz * 3, cx + dx * 3, G + 1, cz + dz * 3, 'air')
        K.campfire(g, cx, G, cz, lit=False)
        fires.append((cx, G, cz))
        g.fill(cx, G + 3, cz, cx, G + 6, cz, 'brick_wall')
    seal(g, 15, G, 22, 'light', 'kiln_fires', fires, bars)
    # Slag heaps, the foreman's table and his log.
    K.mound(g, 27, 16, G, 3, 2, 3, [('basalt[axis=y]', 2), ('blackstone', 2), ('magma_block', 1)], seed=5)
    K.mound(g, 4, 16, G, 2.5, 2, 3, [('basalt[axis=y]', 2), ('gravel', 2), ('magma_block', 1)], seed=6)
    kit.table(g, 20, G, 24)
    kit.chair(g, 21, G, 24, 'dark_oak', 'west')
    clue(g, 19, G, 24, 'note', 'kiln_foreman')
    K.debris(g, 0, 0, 31, 31, G, ['basalt[axis=y]', 'gravel', 'coarse_dirt', 'blackstone_slab[type=bottom]'], 0.05, seed=2)
    mob(g, 10, G, 25, 'cinder_beetle', 3)
    mob(g, 22, G, 9, 'ember_salamander')
    return K.finish(g, PAL['brick'], weather_rate=0.2)


def cinder_watchtower():
    """A broken black tower with a cult camped at its foot; their leader holds the top floor."""
    G = 3
    g = K.grid(32, 36, 32)
    P = PAL['black']
    K.round_tower(g, 16, 10, G, 22, 4, P, floors=(G + 7, G + 14), door='south', decay=0.3, seed=7)
    g.chest(14, G + 15, 9, 'south', fl(VAULT, 'ash'))
    elite(g, 17, G + 15, 11, 'ashen_husk')
    # The camp.
    K.tent(g, 5, 22, 5, G, 'black', axis='x', poles='dark_oak_fence')
    K.tent(g, 21, 25, 5, G, 'gray', axis='x', poles='dark_oak_fence')
    K.campfire(g, 16, G, 24, lit=True)
    for (x, z) in ((14, 24), (18, 24), (16, 26)):
        g.set(x, G, z, f'{P.slab}_slab[type=bottom]')
    for (x, z) in ((11, 18), (21, 18)):
        g.fill(x, G, z, x, G + 2, z, 'dark_oak_fence')
        g.set(x, G + 3, z, 'black_banner[rotation=0]')
    g.set(24, G, 19, 'grindstone[face=floor,facing=north]')
    g.set(25, G, 19, 'smithing_table')
    g.set(26, G, 19, 'barrel[facing=up,open=false]')
    clue(g, 13, G, 27, 'note', 'cult_orders')
    K.path(g, [(16, 15), (16, 31)], G - 1, 'gravel', width=2, rate=0.6, seed=3)
    K.debris(g, 0, 0, 31, 31, G, ['basalt[axis=y]', 'gravel', 'blackstone'], 0.04, seed=4)
    mob(g, 9, G, 27, 'ashen_husk', 3)
    return K.finish(g, P)


# ================================================================ Pale Reach and Gloomwald

def bone_orchard():
    """A walled garden of graves under bone-white trees, with a ruined chapel at its head."""
    G = 3
    g = K.grid(32, 24, 32)
    P = PAL['pale']
    for (a, b, c, d) in ((3, 3, 28, 3), (3, 28, 28, 28), (3, 3, 3, 28), (28, 3, 28, 28)):
        K.ruin_wall(g, a, b, c, d, G, 2, 'diorite_wall', seed=2, decay=0.45, holes=0.0)
    g.fill(15, G, 28, 16, G + 1, 28, 'air')
    for x in (14, 17):
        g.fill(x, G, 28, x, G + 2, 28, 'polished_diorite')
        kit.lantern(g, x, G + 3, 28, soul=True)
    for k, (x, z) in enumerate(((8, 15), (23, 14), (9, 23), (22, 22), (15, 19))):
        h = 4 + k % 2
        for y in range(G, G + h):
            g.set(x, y, z, 'bone_block[axis=y]')
        for (bx, bz, ax) in ((1, 0, 'x'), (-1, 0, 'x'), (0, 1, 'z'), (0, -1, 'z')):
            if hash3(x, k, z, bx * 3 + bz) < 0.7:
                g.set(x + bx, G + h - 1, z + bz, f'bone_block[axis={ax}]')
                g.set(x + 2 * bx, G + h, z + 2 * bz, f'bone_block[axis={ax}]')
        K.canopy(g, x, z, G + h, 2.6, 'pale_oak_leaves', seed=k, h=3)
    for x in (6, 11, 20, 25):
        for z in (13, 18, 24):
            if (x, z) in ((11, 18), (20, 18)):
                continue
            g.set(x, G, z, 'polished_diorite')
            kit.slab(g, x, G + 1, z, 'polished_diorite')
            g.set(x, G - 1, z + 1, 'coarse_dirt')
            g.set(x, G - 1, z + 2, 'coarse_dirt')
            if hash3(x, 1, z, 4) < 0.5:
                K.candles(g, x, G, z + 1, 1 + int(hash3(x, 2, z, 4) * 3), True)
    # The chapel.
    K.room(g, 11, 4, 20, 10, G, 5, P, floor=True, door=(15, 10), decay=0.45, seed=3)
    g.set(16, G, 10, 'air')
    g.set(16, G + 1, 10, 'air')
    g.set(15, G, 5, 'chiseled_quartz_block')
    K.candles(g, 14, G, 5, 3, True)
    K.candles(g, 16, G, 5, 2, True)
    g.chest(18, G, 5, 'south', fl(VAULT, 'pale'))
    clue(g, 13, G, 7, 'note', 'orchard_gardener')
    K.debris(g, 4, 4, 27, 27, G, ['bone_block[axis=x]', 'lily_of_the_valley', 'short_grass', 'pale_moss_carpet'], 0.06, seed=5)
    mob(g, 15, G, 16, 'mourning_shade', 2)
    return K.finish(g, P)


def weavers_hollow():
    """A cave in a rock knoll, choked with webs and cocoons. Something large spins at the back."""
    G = 3
    g = K.grid(32, 24, 32)
    K.mound(g, 16, 13, G - 1, 11, 9, 10, [('stone', 4), ('tuff', 2), ('deepslate', 1), ('andesite', 1)], seed=4, shell=2.5)
    # The cave mouth, south, and the floor inside.
    for z in range(17, 25):
        w = 2 if z > 20 else 3
        g.fill(16 - w, G, z, 16 + w, G + 3, z, 'air')
    g.fill(8, G - 1, 6, 24, G - 1, 22, 'coarse_dirt')
    for (x, y, z) in ((12, G + 3, 9), (19, G + 4, 11), (15, G + 5, 7), (21, G + 2, 15), (10, G + 1, 14), (17, G + 2, 19), (14, G + 4, 16),
                      (13, G, 18), (20, G + 5, 8), (11, G + 2, 6), (18, G, 13), (22, G + 3, 12)):
        g.set(x, y, z, 'cobweb')
    for (x, z) in ((10, 9), (21, 9), (12, 5), (20, 15)):
        g.set(x, G, z, 'white_wool')
        g.set(x, G + 1, z, 'mushroom_stem')
        K.skull(g, x + 1, G, z, int(hash3(x, 0, z, 1) * 16))
    g.chest(16, G, 7, 'south', fl(VAULT, 'gloom'))
    clue(g, 15, G, 21, 'note', 'weaver_victim')
    for k, (x, z) in enumerate(((4, 26), (27, 25), (3, 6), (28, 5))):
        K.dead_tree(g, x, z, G, 5 + k % 3, 'dark_oak_log', seed=k)
    K.debris(g, 0, 0, 31, 31, G, ['cobweb', 'dead_bush', 'bone_block[axis=y]'], 0.03, seed=7)
    elite(g, 16, G, 8, 'gloomweaver')
    mob(g, 13, G, 12, 'gloomweaver', 2)
    return K.finish(g)


def hanged_lanterns():
    """Dead trees hung with soul lanterns over five cold candles on stumps. Light all five and the
    stone at the centre rolls back."""
    G = 3
    g = K.grid(32, 24, 32)
    P = PAL['mossy']
    c = 16
    for k in range(7):
        t = math.radians(k * (360 / 7) + 10)
        x, z = int(round(c + math.cos(t) * 11)), int(round(c + math.sin(t) * 11))
        top = K.dead_tree(g, x, z, G, 7 + k % 3, 'dark_oak_log', seed=k, branches=3)
        for b in range(2):
            lx = x + (1 if b else -1)
            ly = G + 5 + k % 3
            kit.log(g, lx, ly + 1, z, 'dark_oak_log', 'x')
            kit.hang_lantern(g, lx, ly, z, 1 + b, soul=True)
    lights = []
    for i in range(5):
        t = math.radians(i * 72 + 36)
        x, z = int(round(c + math.cos(t) * 5)), int(round(c + math.sin(t) * 5))
        kit.log(g, x, G, z, 'dark_oak_log')
        K.candles(g, x, G + 1, z, 1 + i % 2, lit=False, color='gray')
        lights.append((x, G + 1, z))
    g.fill(c - 1, G - 1, c - 1, c + 1, G - 1, c + 1, P.block)
    seal(g, c, G, c, 'light', 'grove_candles', lights, [(c, G - 1, c + 3)])
    g.set(c, G - 1, c + 3, 'mossy_cobblestone')
    g.fill(c - 1, G - 3, c + 2, c + 1, G - 3, c + 4, 'stone')
    g.chest(c, G - 2, c + 3, 'north', fl(VAULT, 'gloom'))
    clue(g, c + 2, G, c - 3, 'note', 'lantern_grove')
    K.debris(g, 0, 0, 31, 31, G, ['dead_bush', 'fern', 'brown_mushroom', 'short_grass'], 0.07, seed=3)
    mob(g, c - 6, G, c + 6, 'mourning_shade', 2)
    return K.finish(g)


# ================================================================ Shardfields

def crystal_observatory():
    """A domed observatory of calcite and tinted glass. Four plinths carry one to four crystals;
    strike their buttons from fewest crystals to most and the floor lens opens."""
    G = 3
    g = K.grid(32, 30, 32)
    P = PAL['pale']
    c = 16
    X, Z = g.xz()
    import numpy as np
    d = np.hypot(X - c, Z - c)
    g.a[:, G - 1, :][d <= 8.4] = g.pid('polished_deepslate')
    for y in range(G, G + 5):
        g.a[:, y, :][(d <= 8.35) & (d > 7.15)] = g.pid('calcite')
    for a in range(0, 360, 45):
        t = math.radians(a + 22)
        g.set(int(round(c + math.cos(t) * 8)), G + 2, int(round(c + math.sin(t) * 8)), 'tinted_glass')
    g.fill(c, G, c + 8, c, G + 1, c + 8, 'air')
    kit.dome(g, c, G + 5, c, 8, 'tinted_glass', inner=None, ribs='calcite', ribs_n=8, oculus=2)
    # A star map in the floor.
    for (x, z) in ((c - 4, c - 2), (c - 2, c - 5), (c + 1, c - 3), (c + 3, c + 1), (c + 5, c - 1), (c - 3, c + 4), (c + 2, c + 5)):
        g.set(x, G - 1, z, 'amethyst_block')
    # The telescope: a copper tube aimed at the oculus, on a plinth.
    g.fill(c, G, c, c, G + 1, c, 'polished_deepslate')
    g.line((c, G + 2, c), (c - 3, G + 10, c - 3), 'cut_copper')
    g.set(c - 3, G + 11, c - 3, 'lightning_rod[facing=up,powered=false,waterlogged=false]')
    # Four plinths with their crystals and buttons.
    plinths = {}
    counts = {'north': 3, 'east': 1, 'south': 4, 'west': 2}
    for face, n in counts.items():
        dx, dz = K.FACE[face]
        x, z = c + dx * 5, c + dz * 5
        g.fill(x, G, z, x, G + 1, z, 'calcite')
        tops = [(x, G + 2, z), (x + dz, G + 1, z + dx), (x - dz, G + 1, z - dx), (x + dx, G + 1, z + dz)]
        for i in range(n):
            if i > 0:
                g.set(tops[i][0], tops[i][1] - 1, tops[i][2], 'calcite')
            K.cluster(g, *tops[i])
        bx, bz = x - dx, z - dz
        K.button(g, bx, G + 1, bz, K.OPP[face])
        plinths[n] = (bx, G + 1, bz)
    order = [plinths[n] for n in sorted(plinths)]
    lens = [(c + 2, G - 1, c + 2)]
    g.set(*lens[0], 'tinted_glass')
    g.fill(c + 1, G - 3, c + 1, c + 3, G - 3, c + 3, 'calcite')
    g.chest(c + 2, G - 2, c + 2, 'north', 'afterworld:chests/star_vault_crystal')
    seal(g, c - 2, G, c + 2, 'order', 'observatory_lenses', order, lens)
    clue(g, c + 2, G, c - 2, 'note', 'astronomer')
    # Crystals outside.
    for k, (x, z) in enumerate(((3, 4), (28, 6), (4, 27), (27, 26), (2, 16))):
        h = 2 + k % 3
        g.fill(x, G - 1, z, x, G + h - 1, z, 'amethyst_block')
        K.cluster(g, x, G + h, z)
        g.set(x + 1, G - 1, z, 'budding_amethyst')
        K.cluster(g, x + 1, G, z)
    K.debris(g, 0, 0, 31, 31, G, ['calcite', 'small_amethyst_bud[facing=up,waterlogged=false]', 'amethyst_cluster[facing=up,waterlogged=false]'], 0.04, seed=6)
    mob(g, c, G, c + 11, 'prism_golem')
    mob(g, 5, G, 12, 'crystal_crab', 2)
    return K.finish(g)


def shattered_caravan():
    """A Guild wagon split open by crystals that grew through it in a night."""
    G = 3
    g = K.grid(32, 22, 32)
    K.wagon(g, 13, 13, G, 'x', wood='spruce', cover='blue', tipped=True, seed=3)
    K.wagon(g, 9, 21, G, 'z', wood='spruce', cover='light_blue', broken=True, seed=4)
    for k, (x, z, h) in enumerate(((15, 15, 6), (17, 12, 4), (12, 18, 5), (20, 17, 3), (7, 11, 4), (23, 22, 5))):
        g.line((x, G - 1, z), (x + (k % 3) - 1, G + h, z + ((k + 1) % 3) - 1), 'amethyst_block', r=0.6 if h > 4 else 0)
        K.cluster(g, x + (k % 3) - 1, G + h + 1, z + ((k + 1) % 3) - 1)
    g.chest(18, G, 15, 'west', fl(CACHE, 'crystal'))
    K.barrels(g, 21, G, 11, seed=2, n=3)
    K.pots(g, [(10, G, 15), (11, G, 16)])
    K.debris(g, 0, 0, 31, 31, G, ['calcite', 'small_amethyst_bud[facing=up,waterlogged=false]', 'blue_carpet', 'medium_amethyst_bud[facing=up,waterlogged=false]'], 0.05, seed=4)
    mob(g, 16, G, 21, 'shardling', 3)
    return K.finish(g)


# ================================================================ Petalfall

def tea_house():
    """A tea house of cherry wood and paper screens on a raised floor, a koi pond and stone lanterns."""
    G = 3
    g = K.grid(32, 22, 32)
    x0, x1, z0, z1 = 9, 21, 6, 15
    g.fill(x0, G - 1, z0, x1, G - 1, z1, 'stone_bricks')
    g.fill(x0, G, z0, x1, G, z1, 'cherry_planks')
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            post = x in (x0, x1, (x0 + x1) // 2) and z in (z0, z1) or z in (z0, z1, (z0 + z1) // 2) and x in (x0, x1)
            if post:
                g.fill(x, G + 1, z, x, G + 4, z, 'stripped_cherry_log[axis=y]')
            elif edge:
                for y in range(G + 1, G + 4):
                    g.set(x, y, z, 'white_wool' if y < G + 3 else 'cherry_planks')
                g.set(x, G + 4, z, 'cherry_planks')
    g.fill(14, G + 1, z1, 16, G + 2, z1, 'air')
    kit.hip(g, x0, z0, x1, z1, G + 5, 'cherry', overhang=2)
    g.fill(x0 + 1, G + 4, z0 + 1, x1 - 1, G + 4, z1 - 1, 'cherry_planks')
    # Tatami, a low table, cushions, the kettle.
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            g.set(x, G + 1, z, 'yellow_carpet' if (x + z) % 4 else 'lime_carpet')
    g.set(15, G + 1, 10, 'cherry_slab[type=bottom]')
    g.set(16, G + 1, 10, 'cherry_slab[type=bottom]')
    for (x, z) in ((15, 9), (16, 9), (15, 11), (16, 11)):
        g.set(x, G + 1, z, 'red_carpet')
    g.set(11, G + 1, 7, 'decorated_pot[facing=south,cracked=false,waterlogged=false]')
    g.chest(19, G + 1, 7, 'south', fl(CACHE, 'petal'))
    kit.hang_lantern(g, 15, G + 3, 10, 0)
    clue(g, 13, G + 1, 12, 'note', 'tea_poem')
    # The garden.
    for x in range(8, 16):
        for z in range(19, 26):
            if (x - 11.5) ** 2 / 14 + (z - 22) ** 2 / 9 <= 1:
                g.set(x, G - 1, z, 'water')
                g.set(x, G - 2, z, 'clay')
                if hash3(x, 0, z, 6) < 0.15:
                    g.set(x, G, z, 'lily_pad')
    for (x, z) in ((19, 20), (6, 13), (24, 13)):
        g.set(x, G, z, 'stone_brick_wall')
        g.set(x, G + 1, z, 'stone_bricks')
        kit.lantern(g, x, G + 2, z)
    for (x, z) in ((25, 20), (26, 22), (24, 23)):
        for y in range(G, G + 4 + int(hash3(x, 0, z, 2) * 3)):
            g.set(x, y, z, 'bamboo[age=1,leaves=large,stage=0]')
    K.tree(g, 4, 23, G, 5, 'cherry_log', 'cherry_leaves', r=3, seed=2)
    K.path(g, [(15, 16), (15, 20), (19, 28)], G - 1, 'gravel', width=2, rate=0.9, seed=1)
    K.debris(g, 0, 0, 31, 31, G, ['pink_petals[facing=north,flower_amount=4]', 'short_grass'], 0.12, seed=5)
    mob(g, 20, G, 24, 'blossom_crane', 2)
    return K.finish(g)


def lantern_dojo():
    """The Ashen Shogun's house: a great hall of dark wood and paper, lanterns by the hundred, a
    torii gate and a courtyard. His last disciple, Akane, keeps it."""
    G = 3
    g = K.grid(48, 30, 48)
    x0, x1, z0, z1 = 12, 35, 10, 27
    g.fill(x0 - 1, G - 1, z0 - 1, x1 + 1, G, z1 + 1, 'stone_bricks')
    g.fill(x0, G, z0, x1, G, z1, 'dark_oak_planks')
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z in (z0, z1)):
                continue
            post = (x - x0) % 4 == 0 and z in (z0, z1) or (z - z0) % 4 == 0 and x in (x0, x1)
            for y in range(G + 1, G + 7):
                g.set(x, y, z, 'stripped_dark_oak_log[axis=y]' if post else ('white_wool' if y < G + 5 else 'dark_oak_planks'))
    g.fill(22, G + 1, z1, 25, G + 3, z1, 'air')
    kit.hip(g, x0, z0, x1, z1, G + 7, 'dark_oak', overhang=2)
    g.fill(x0 + 1, G + 6, z0 + 1, x1 - 1, G + 6, z1 - 1, 'dark_oak_planks')
    # The hundred lanterns: rows hanging from the beams, and more along the walls.
    for x in range(x0 + 2, x1 - 1, 2):
        for z in range(z0 + 2, z1 - 1, 3):
            kit.hang_lantern(g, x, G + 5, z, int(hash3(x, 1, z, 2) * 2))
    # The dojo's heart: a ring of red at the middle of the floor.
    for x in range(21, 27):
        for z in range(16, 22):
            if x in (21, 26) or z in (16, 21):
                g.set(x, G + 1, z, 'red_carpet')
    # The heart: light the challenge lantern here and the Shogun wakes at the head of the hall.
    seal(g, 23, G, 18, 'offer-afterworld.challenge_lantern-1', 'dojo_heart', [], [])
    g.set(24, G, 19, 'chiseled_stone_bricks')
    boss(g, 23, G + 1, 12, 'ashen_shogun', dormant=True)
    for x in (x0 + 2, x1 - 2):
        for z in range(z0 + 2, z1 - 1, 2):
            g.set(x, G + 1, z, 'dark_oak_slab[type=bottom]')
    clue(g, 20, G + 1, z1 - 1, 'note', 'dojo_rule')
    npc(g, 23, G + 1, z1 - 3, 'akane', 'south')
    # The courtyard, the path, the torii.
    K.path(g, [(23, z1 + 2), (23, 46)], G - 1, 'gravel', width=4, rate=0.95, seed=2)
    for z in range(z1 + 4, 46, 4):
        for x in (19, 28):
            g.set(x, G, z, 'stone_brick_wall')
            g.set(x, G + 1, z, 'stone_bricks')
            kit.lantern(g, x, G + 2, z)
    for x in (19, 28):
        g.fill(x, G, 41, x, G + 6, 41, 'red_terracotta')
    g.fill(18, G + 6, 41, 29, G + 6, 41, 'dark_oak_planks')
    g.fill(19, G + 4, 41, 28, G + 4, 41, 'red_terracotta')
    for x in (18, 29):
        kit.stairs(g, x, G + 7, 41, 'dark_oak', 'east' if x == 18 else 'west', 'bottom')
    for (x, z) in ((6, 8), (41, 9), (5, 35), (42, 36), (8, 22), (40, 22)):
        K.tree(g, x, z, G, 5, 'cherry_log', 'cherry_leaves', r=3, seed=x)
    K.debris(g, 0, 0, 47, 47, G, ['pink_petals[facing=east,flower_amount=3]', 'short_grass'], 0.14, seed=3)
    return K.finish(g)


# ================================================================ Frostmere Pines and Whitewaste

def frozen_watch_post():
    """A spruce lookout on a stone footing. The watch left in a hurry, and left their lockbox."""
    G = 3
    g = K.grid(32, 30, 32)
    c = 16
    g.fill(c - 2, G - 1, c - 2, c + 2, G + 1, c + 2, 'cobblestone')
    for (x, z) in ((c - 2, c - 2), (c + 2, c - 2), (c - 2, c + 2), (c + 2, c + 2)):
        g.fill(x, G + 2, z, x, G + 13, z, 'spruce_log[axis=y]')
    for y in (G + 5, G + 9):
        for i in range(-2, 3):
            for (x, z) in ((c + i, c - 2), (c + i, c + 2), (c - 2, c + i), (c + 2, c + i)):
                if g.get(x, y, z) is None:
                    g.set(x, y, z, 'stripped_spruce_log[axis=x]' if z in (c - 2, c + 2) else 'stripped_spruce_log[axis=z]')
    g.fill(c - 3, G + 10, c - 3, c + 3, G + 10, c + 3, 'spruce_planks')
    for x in range(c - 3, c + 4):
        for z in range(c - 3, c + 4):
            if x in (c - 3, c + 3) or z in (c - 3, c + 3):
                g.set(x, G + 11, z, 'spruce_fence')
    g.set(c, G + 10, c + 1, 'air')
    K.ladder(g, c, c + 1, G + 2, G + 10, 'north')
    kit.stairs(g, c, G, c + 4, 'cobblestone', 'north')
    kit.stairs(g, c, G + 1, c + 3, 'cobblestone', 'north')
    g.set(c, G, c + 3, 'cobblestone')
    g.fill(c, G + 2, c + 2, c, G + 9, c + 2, 'spruce_planks')
    kit.hip(g, c - 3, c - 3, c + 3, c + 3, G + 14, 'spruce', overhang=1)
    for (x, z) in ((c - 3, c - 3), (c + 3, c - 3), (c - 3, c + 3), (c + 3, c + 3)):
        g.fill(x, G + 11, z, x, G + 13, z, 'spruce_fence')
    g.chest(c - 2, G + 11, c - 2, 'south', fl(VAULT, 'frost'))
    K.campfire(g, c + 1, G + 11, c - 1, lit=False)
    clue(g, c - 1, G + 11, c + 1, 'note', 'watch_log')
    g.set(c + 2, G + 11, c + 1, 'barrel[facing=up,open=false]')
    K.barrels(g, c + 4, G, c - 3, seed=3, n=2)
    for k, (x, z) in enumerate(((4, 5), (27, 4), (5, 26), (26, 27))):
        K.tree(g, x, z, G, 7, 'spruce_log', 'spruce_leaves', r=2, seed=k)
    mob(g, c + 6, G, c + 5, 'rime_wight')
    mob(g, c - 7, G, c + 2, 'snow_hare', 2)
    return K.finish(g)


def colossus_graveyard():
    """A giant's ribs standing out of the snow like arches, its skull like a house, and tracks
    pressed deep, heading away. The Rimeback hunt starts here."""
    G = 3
    g = K.grid(48, 26, 48)
    cx = 24
    for k, z in enumerate(range(10, 31, 3)):
        span = 14 - abs(k - 3) * 2
        K.rib(g, cx, z, G - 2, span, 9 - abs(k - 3), axis='x')
    for z in range(10, 29):
        k = (z - 10) / 3.0
        g.set(cx, G - 2 + int(round(9 - abs(k - 3))), z, 'bone_block[axis=z]')
    # The skull: a dome of bone with eye sockets, and two tusks.
    kit.dome(g, cx, G - 1, 38, 5.5, 'bone_block[axis=y]', inner='air')
    for x in (cx - 2, cx + 2):
        g.fill(x, G + 1, 33, x, G + 2, 34, 'air')
        g.set(x, G + 1, 35, 'coal_block')
    g.fill(cx - 1, G - 1, 32, cx + 1, G - 1, 33, 'bone_block[axis=z]')
    for s in (-1, 1):
        g.line((cx + s * 4, G, 34), (cx + s * 9, G + 3, 29), 'bone_block[axis=x]')
        g.line((cx + s * 9, G + 3, 29), (cx + s * 10, G + 6, 31), 'bone_block[axis=y]')
    # Snow drifts over the bones, a chest half-buried between the ribs.
    for (x, z, r) in ((18, 14, 3), (30, 24, 3.5), (20, 28, 2.5), (31, 12, 2.5), (14, 36, 3)):
        K.mound(g, x, z, G, r, 1.5, r, [('snow_block', 1)], seed=int(x + z))
    g.chest(26, G, 21, 'west', fl(VAULT, 'frost'))
    clue(g, 24, G, 44, 'tracks', 'rimeback_tracks')
    clue(g, 25, G, 46, 'tracks')
    mob(g, 17, G, 20, 'ice_crawler', 2)
    mob(g, 33, G, 30, 'rimehorn')
    return K.finish(g)


def snowbound_camp():
    """Three tents round a cold fire, a loaded sled, and a ledger with a last entry."""
    G = 3
    g = K.grid(32, 20, 32)
    K.tent(g, 6, 9, 5, G, 'white', axis='x')
    K.tent(g, 21, 9, 5, G, 'light_gray', axis='x')
    K.tent(g, 14, 22, 5, G, 'white', axis='x')
    K.campfire(g, 16, G, 14, lit=False)
    for (x, z) in ((14, 14), (18, 14)):
        g.set(x, G, z, 'stripped_spruce_log[axis=z]')
    # The sled: runners, a slab deck, barrels and a handle.
    for x in range(8, 13):
        g.set(x, G, 17, 'spruce_trapdoor[facing=north,half=bottom,open=true]')
        g.set(x, G, 19, 'spruce_trapdoor[facing=south,half=bottom,open=true]')
        g.set(x, G, 18, 'spruce_slab[type=top]')
    K.barrels(g, 9, G + 1, 18, seed=2, n=2)
    g.set(13, G, 18, 'spruce_fence')
    g.set(13, G + 1, 18, 'spruce_fence')
    g.chest(11, G + 1, 18, 'north', fl(CACHE, 'frost'))
    clue(g, 17, G, 16, 'note', 'trader_ledger')
    K.debris(g, 0, 0, 31, 31, G, ['snow[layers=2]', 'snow[layers=3]', 'snow_block'], 0.08, seed=4)
    mob(g, 25, G, 18, 'rime_wight', 2)
    return K.finish(g)


# ================================================================ Rimeglass Spires and Glimmerfrost

def rime_cavern():
    """A cave of blue ice; rime pearls grow in the dark, and something big sleeps at the back."""
    G = 3
    g = K.grid(32, 28, 32)
    K.mound(g, 16, 13, G - 1, 12, 11, 11, [('packed_ice', 4), ('blue_ice', 1), ('snow_block', 2), ('stone', 1)], seed=8, shell=3)
    for z in range(17, 26):
        w = 2 if z > 21 else 3
        g.fill(16 - w, G, z, 16 + w, G + 4, z, 'air')
    g.fill(7, G - 1, 4, 25, G - 1, 22, 'packed_ice')
    for k, (x, z) in enumerate(((11, 8), (21, 9), (16, 12), (12, 15), (20, 16))):
        g.fill(x, G, z, x, G + 7, z, 'blue_ice')
        g.set(x, G + 3, z, 'sea_lantern') if k % 2 == 0 else None
    for (x, y, z) in ((14, G + 6, 6), (18, G + 7, 10), (10, G + 5, 12), (22, G + 6, 13), (16, G + 8, 8)):
        g.fill(x, y, z, x, y + 2, z, 'ice')
    g.chest(16, G, 7, 'south', fl(VAULT, 'frost'))
    g.chest(11, G, 9, 'east', fl(CACHE, 'frost'))
    elite(g, 16, G, 9, 'rime_brute')
    mob(g, 13, G + 1, 14, 'rime_moth', 3)
    return K.finish(g)


def aurora_shrine():
    """A little shrine of ice and sea-lantern light. Three candles for the three colours of the sky."""
    G = 3
    g = K.grid(32, 22, 32)
    P = PAL['prism']
    c = 16
    import numpy as np
    X, Z = g.xz()
    d = np.hypot(X - c, Z - c)
    g.a[:, G - 1, :][d <= 6.4] = g.pid('packed_ice')
    g.a[:, G - 1, :][(d <= 6.4) & (d > 5.2)] = g.pid('prismarine_bricks')
    for a in range(0, 360, 60):
        t = math.radians(a)
        x, z = int(round(c + math.cos(t) * 5)), int(round(c + math.sin(t) * 5))
        g.fill(x, G, z, x, G + 4, z, 'packed_ice')
        g.set(x, G + 5, z, 'sea_lantern')
    kit.dome(g, c, G + 5, c, 5.5, 'light_blue_stained_glass', inner=None, oculus=1.5)
    lights = []
    for (dx, dz, col) in ((-2, -1, 'cyan'), (2, -1, 'lime'), (0, 2, 'magenta')):
        g.set(c + dx, G, c + dz, 'prismarine_bricks')
        K.candles(g, c + dx, G + 1, c + dz, 3, lit=False, color=col)
        lights.append((c + dx, G + 1, c + dz))
    seal(g, c, G, c - 3, 'light', 'aurora_candles', lights, [(c, G - 1, c)])
    g.set(c, G - 1, c, 'blue_ice')
    g.fill(c - 1, G - 3, c - 1, c + 1, G - 3, c + 1, 'packed_ice')
    g.chest(c, G - 2, c, 'north', 'afterworld:chests/star_vault_glimmer')
    clue(g, c + 3, G, c + 3, 'glyph', 'aurora_prayer')
    mob(g, c + 8, G + 1, c - 6, 'rime_moth', 2)
    return K.finish(g)


# ================================================================ Skyspire Peaks and Windworn Highlands

def peak_monastery():
    """Terraces climbing to a pagoda with a bell; past it, stepping stones over the drop to a high
    shrine. The climb is the prayer."""
    G = 3
    g = K.grid(48, 40, 48)
    P = PAL['andesite']
    for (x0, z0, x1, z1, y) in ((6, 8, 32, 37, G - 1), (9, 12, 29, 31, G + 3), (12, 16, 26, 25, G + 7)):
        g.fill(x0, G - 1, z0, x1, y, z1, P.block)
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                g.set(x, y + 1, z, 'andesite_wall')
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                g.set(x, y + 1, z, 'andesite_wall')
    # Stairs: ground to the first terrace's gate, then up to the second and the third.
    g.fill(18, G, 37, 20, G, 37, 'air')
    for i in range(4):
        for x in range(18, 21):
            g.fill(x, G - 1, 35 - i, x, G + i - 1, 35 - i, P.block)
            kit.stairs(g, x, G + i, 35 - i, P.stair, 'north')
            kit.stairs(g, x, G + 4 + i, 29 - i, P.stair, 'north')
    g.fill(18, G + 4, 31, 20, G + 4, 31, 'air')
    g.fill(18, G + 8, 25, 20, G + 8, 25, 'air')
    # The pagoda.
    px0, px1, pz0, pz1, py = 15, 23, 17, 23, G + 8
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            if x in (px0, px1) or z in (pz0, pz1):
                post = x in (px0, px1) and z in (pz0, pz1)
                for y in range(py, py + 4):
                    g.set(x, y, z, 'stripped_spruce_log[axis=y]' if post else ('white_wool' if y < py + 3 else 'spruce_planks'))
    g.fill(18, py, pz1, 20, py + 1, pz1, 'air')
    top = kit.hip(g, px0, pz0, px1, pz1, py + 4, 'mangrove', overhang=2)
    g.fill(px0 + 1, py + 3, pz0 + 1, px1 - 1, py + 3, pz1 - 1, 'spruce_planks')
    g.set(19, py + 2, 22, 'bell[attachment=ceiling,facing=north,powered=false]')
    g.set(19, py, 20, 'chiseled_stone_bricks')
    K.candles(g, 18, py, 20, 3, True)
    K.candles(g, 20, py, 20, 3, True)
    clue(g, 19, py, 24, 'note', 'monk_koan')
    # Prayer flags between poles.
    for (x0, x1, z) in ((8, 30, 33), (8, 30, 12)):
        for x in (x0, x1):
            g.fill(x, G, z, x, G + 4, z, 'spruce_fence')
        for x in range(x0 + 1, x1):
            g.set(x, G + 4, z, ['red_carpet', 'yellow_carpet', 'blue_carpet', 'white_carpet', 'lime_carpet'][x % 5])
    # The trial: stepping stones climbing east to the high shrine.
    sz = 20
    g.fill(26, G + 8, sz, 26, G + 8, sz, 'air')
    for i in range(7):
        x = 28 + i * 2 + (i % 2)
        z = sz + [0, 2, 1, -1, 0, 2, 1][i]
        y = G + 8 + i
        g.fill(x, G - 1, z, x, y, z, P.block if i % 3 else 'stone_bricks')
    hx, hz, hy = 45, 21, G + 15
    g.fill(hx - 2, G - 1, hz - 2, hx + 1, hy, hz + 2, 'stone_bricks')
    g.chest(hx - 1, hy + 1, hz, 'west', fl(VAULT, 'sky'))
    kit.lantern(g, hx, hy + 1, hz + 1)
    kit.lantern(g, hx, hy + 1, hz - 1)
    mob(g, 30, G + 12, 30, 'windrider_eagle')
    return K.finish(g, P)


def great_nest():
    """A nest of whole trees on a pillar of rock high over the peaks; eggs the size of barrels."""
    G = 3
    g = K.grid(32, 44, 32)
    c = 16
    for y in range(G - 2, G + 26):
        r = 4.2 - 1.2 * math.sin(y / 5.0) * 0.5
        for x in range(c - 6, c + 7):
            for z in range(c - 6, c + 7):
                if math.hypot(x - c, z - c) <= r + 0.8 * hash3(x, y // 3, z, 2):
                    g.set(x, y, z, 'stone' if hash3(x, y, z, 1) < 0.6 else ('andesite' if hash3(x, y, z, 3) < 0.6 else 'tuff'))
    top = G + 26
    # The nest: a ring of logs heaped round a hay floor.
    for x in range(c - 8, c + 9):
        for z in range(c - 8, c + 9):
            d = math.hypot(x - c, z - c)
            if d <= 5.5:
                g.set(x, top, z, 'hay_block[axis=y]')
            elif d <= 8.2:
                for y in range(top, top + 2 + int(hash3(x, 0, z, 4) * 2)):
                    ax = 'x' if hash3(x, y, z, 5) < 0.5 else 'z'
                    g.set(x, y, z, f'{"spruce" if hash3(x, y, z, 6) < 0.5 else "oak"}_log[axis={ax}]')
                if hash3(x, 9, z, 7) < 0.3:
                    kit.leaves(g, x, top + 3, z, 'oak_leaves')
    for (x, z) in ((c - 1, c), (c + 2, c - 1), (c, c + 2)):
        g.set(x, top + 1, z, 'sniffer_egg[hatch=0]')
    g.chest(c + 3, top + 1, c + 2, 'west', fl(VAULT, 'sky'))
    K.debris(g, c - 6, c - 6, c + 6, c + 6, top + 1, ['white_carpet', 'bone_block[axis=x]'], 0.06, seed=3)
    # A climber's ladder up one face, and fires at the foot (their updraft carries gliders up).
    for y in range(G, top):
        for x in range(c + 7, c - 1, -1):
            if g.get(x, y, c) is not None:
                g.set(x + 1, y, c, 'ladder[facing=east,waterlogged=false]')
                break
    for (x, z) in ((c - 7, c + 3), (c + 6, c - 7), (c - 3, c - 8)):
        K.campfire(g, x, G, z, lit=True)
    elite(g, c, top + 1, c - 2, 'windrider_eagle')
    mob(g, c + 2, top + 4, c + 2, 'windrider_eagle')
    return K.finish(g)


def wind_harp():
    """A giant's harp of stone on the highlands. Five posts of different heights carry bells; strike
    them from the shortest to the tallest and the cairn opens."""
    G = 3
    g = K.grid(32, 28, 32)
    P = PAL['stone']
    for x in (7, 25):
        g.fill(x - 1, G - 1, 14, x + 1, G + 12, 16, P.block)
        g.set(x, G + 13, 15, P.accent)
    kit.arch(g, 8, 24, G + 10, 4, P.stair, axis='x', at=15, fill_above=P.block, top=G + 14)
    g.fill(8, G + 14, 15, 24, G + 15, 15, P.block)
    for x in range(10, 23, 2):
        kit.chain(g, x, G + 3, G + 9 + int(3 * math.sin((x - 8) / 16 * math.pi)), 15)
    heights = [4, 2, 6, 3, 5]
    posts = {}
    for i, h in enumerate(heights):
        x = 10 + i * 3
        g.fill(x, G, 19, x, G + h - 1, 19, 'stone_bricks')
        g.set(x, G + h, 19, 'bell[attachment=floor,facing=south,powered=false]')
        K.button(g, x, G + 1, 20, 'south')
        posts[h] = (x, G + 1, 20)
    order = [posts[h] for h in sorted(posts)]
    # The cairn.
    K.mound(g, 16, 26, G, 2.6, 2.2, 2.6, [('cobblestone', 2), ('mossy_cobblestone', 1), ('andesite', 1)], seed=4)
    g.fill(15, G, 25, 17, G + 1, 27, 'air')
    g.chest(16, G, 26, 'north', fl(VAULT, 'highland'))
    door = [(x, y, 24) for x in (15, 16, 17) for y in (G, G + 1)]
    for d_ in door:
        g.set(*d_, 'mossy_cobblestone')
    seal(g, 16, G, 22, 'order', 'harp_posts', order, door)
    clue(g, 18, G, 23, 'glyph', 'harp_carving')
    K.debris(g, 0, 0, 31, 31, G, ['short_grass', 'fern', 'cobblestone', 'cornflower'], 0.1, seed=6)
    mob(g, 5, G, 25, 'highland_ram', 2)
    return K.finish(g, P)


# ================================================================ Ember Steppe

def nomad_yurts():
    """Three felt yurts, a horse pen with hay and a trough, a cooking fire."""
    G = 3
    g = K.grid(32, 20, 32)
    K.yurt(g, 9, 9, G, 4, 'white', 'red', door='south')
    K.yurt(g, 23, 9, G, 4, 'white', 'orange', door='south')
    K.yurt(g, 15, 23, G, 4, 'light_gray', 'red', door='north')
    for (x, z, col) in ((9, 9, 'red'), (23, 9, 'orange'), (15, 23, 'brown')):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                g.set(x + dx, G, z + dz, f'{col}_carpet')
    g.chest(8, G, 7, 'south', fl(CACHE, 'steppe'))
    g.set(24, G, 7, 'loom[facing=south]')
    kit.bed(g, 21, G, 8, 'red', 'north')
    clue(g, 16, G, 21, 'note', 'nomad_song')
    for x in range(24, 31):
        for z in range(18, 27):
            if x in (24, 30) or z in (18, 26):
                g.set(x, G, z, 'acacia_fence')
    g.set(24, G, 22, 'acacia_fence_gate[facing=east,in_wall=false,open=false,powered=false]')
    g.set(29, G, 19, 'hay_block[axis=y]')
    g.set(28, G, 19, 'hay_block[axis=x]')
    g.set(29, G, 25, 'water_cauldron[level=3]')
    K.campfire(g, 16, G, 15, lit=True)
    for (x, z) in ((15, 15), (17, 15)):
        g.set(x, G, z, 'acacia_fence')
        g.set(x, G + 1, z, 'acacia_fence')
    g.set(16, G + 2, 15, 'acacia_trapdoor[facing=north,half=bottom,open=false]')
    mob(g, 27, G, 22, 'dune_strider', 2)
    mob(g, 4, G, 20, 'highland_ram', 2)
    return K.finish(g)


def starfall_crater():
    """A crater round a fallen star that has not finished glowing."""
    G = 3
    g = K.grid(32, 22, 32)
    c = 16
    K.bowl(g, c, c, G - 1, 13, 6, [('blackstone', 3), ('basalt[axis=y]', 2), ('magma_block', 1), ('obsidian', 1), ('tuff', 2)], seed=2)
    base = G - 7
    g.fill(c - 2, base, c - 2, c + 2, base + 2, c + 2, 'end_stone')
    g.fill(c - 1, base + 1, c - 1, c + 1, base + 3, c + 1, 'amethyst_block')
    g.set(c, base + 2, c, 'shroomlight')
    g.set(c, base + 4, c, 'crying_obsidian')
    for (dx, dz, f) in ((2, 0, 'east'), (-2, 0, 'west'), (0, 2, 'south'), (0, -2, 'north')):
        K.cluster(g, c + dx * 1.5, base + 3, c + dz * 1.5, 'up')
    K.cluster(g, c, base + 5, c, 'up')
    g.chest(c + 4, base + 1, c, 'west', 'afterworld:chests/star_vault_steppe')
    clue(g, c - 4, base + 1, c + 2, 'note', 'star_chart')
    import numpy as np
    for x in range(g.sx):
        for z in range(g.sz):
            d = math.hypot(x - c, z - c)
            if 13 < d < 15.5 and hash3(x, 0, z, 3) < 0.5:
                g.set(x, G - 1, z, 'coarse_dirt' if hash3(x, 1, z, 3) < 0.6 else 'gravel')
    mob(g, c - 6, G - 4, c - 5, 'cinder_beetle', 2)
    return K.finish(g)


# ================================================================ Rustscar Canyons

def abandoned_mine():
    """A timbered mine in a banded canyon wall, its tunnel running down to a gallery of ore."""
    G = 6
    g = K.grid(32, 30, 32)
    bands = ['terracotta', 'orange_terracotta', 'brown_terracotta', 'red_terracotta', 'yellow_terracotta', 'white_terracotta']
    for x in range(g.sx):
        for z in range(0, 20):
            # A cliff face at the front, falling away to the sides and the back so it meets the land.
            fx = 1 - (abs(x - 15.5) / 16.5) ** 3
            fz = min(1.0, (z + 1) / 7.0)
            top = int(G - 1 + (15 + 2 * math.sin(x / 4.0) - (z - 2) * 0.3) * fx * fz)
            for y in range(G - 6, min(g.sy, top)):
                g.set(x, y, z, bands[(y // 2) % len(bands)])
    R = K.palette(block='stone', floor='cobblestone', stair='cobblestone')
    K.chamber(g, 9, G - 5, 3, 22, G - 2, 10, R)
    K.chamber(g, 15, G - 5, 10, 17, G - 3, 15, R)
    K.stair_down(g, 16, 20, G - 1, 5, 'north', R, width=3)
    g.fill(15, G, 16, 17, G + 2, 19, 'air')
    # Timbers: the portal, the tunnel, the gallery.
    for x in (14, 18):
        g.fill(x, G - 1, 20, x, G + 3, 20, 'spruce_log[axis=y]')
    g.fill(14, G + 4, 20, 18, G + 4, 20, 'spruce_planks')
    for z in (11, 14):
        for x in (15, 17):
            g.fill(x, G - 5, z, x, G - 3, z, 'spruce_fence')
        g.fill(15, G - 3, z, 17, G - 3, z, 'spruce_planks')
    for x in (9, 13, 18, 22):
        g.fill(x, G - 5, 3, x, G - 2, 3, 'spruce_log[axis=y]')
        g.fill(x, G - 5, 10, x, G - 2, 10, 'spruce_log[axis=y]')
    # Ore in the walls, rails on the floor, a chest at the end.
    for (x, y, z) in ((8, G - 4, 5), (8, G - 3, 7), (23, G - 4, 6), (23, G - 2, 8), (12, G - 1, 2), (19, G - 1, 2), (15, G - 3, 2)):
        g.set(x, y, z, ['iron_ore', 'copper_ore', 'gold_ore', 'raw_iron_block', 'coal_ore'][(x + z) % 5])
    for x in range(10, 22):
        g.set(x, G - 5, 7, 'rail[shape=east_west,waterlogged=false]')
    g.chest(21, G - 5, 5, 'west', fl(VAULT, 'canyon'))
    kit.hang_lantern(g, 12, G - 2, 6, 0)
    kit.hang_lantern(g, 19, G - 2, 6, 0)
    clue(g, 10, G - 5, 9, 'note', 'miner_tally')
    # Outside: a spoil heap, a broken cart, a winch.
    K.mound(g, 6, 25, G, 3, 2, 3, [('gravel', 3), ('cobblestone', 1), ('raw_copper_block', 1)], seed=3)
    K.wagon(g, 22, 25, G, 'x', wood='spruce', cover='brown', broken=True, seed=2)
    g.fill(25, G, 18, 25, G + 3, 18, 'spruce_fence')
    g.set(25, G + 4, 18, 'spruce_planks')
    g.set(24, G + 4, 18, 'iron_chain[axis=x]')
    elite(g, 16, G - 5, 5, 'rust_golemite')
    mob(g, 11, G - 5, 8, 'gearhound', 2)
    mob(g, 20, G, 27, 'rust_golemite')
    return K.finish(g)


def canyon_outpost():
    """A stilted hut on one rock pillar, a rope bridge to another, and a long way down."""
    G = 3
    g = K.grid(32, 32, 32)
    cols = ['red_terracotta', 'orange_terracotta', 'terracotta', 'brown_terracotta']
    tops = {}
    for (cx, cz, h, r) in ((8, 16, 15, 3.5), (24, 15, 13, 3.2)):
        for y in range(G - 2, G + h):
            rr = r + 0.6 * math.sin(y * 0.7 + cx)
            for x in range(int(cx - rr - 1), int(cx + rr + 2)):
                for z in range(int(cz - rr - 1), int(cz + rr + 2)):
                    if math.hypot(x - cx, z - cz) <= rr:
                        g.set(x, y, z, cols[(y // 2) % len(cols)])
        tops[cx] = G + h
    # The hut on the western pillar.
    hy = tops[8]
    g.fill(5, hy, 13, 11, hy, 20, 'spruce_planks')
    for x in range(6, 11):
        for z in range(14, 19):
            if x in (6, 10) or z in (14, 18):
                for y in range(hy + 1, hy + 4):
                    g.set(x, y, z, 'spruce_planks' if (x + z) % 4 else 'stripped_spruce_log[axis=y]')
    g.fill(10, hy + 1, 16, 10, hy + 2, 16, 'air')
    kit.gable(g, 6, 14, 10, 18, hy + 4, 'spruce', axis='x', overhang=1, gable_fill='spruce_planks')
    g.chest(7, hy + 1, 15, 'south', fl(CACHE, 'canyon'))
    kit.bed(g, 7, hy + 1, 17, 'brown', 'north')
    kit.lantern(g, 9, hy + 1, 15)
    # The rope bridge.
    by = tops[24]
    for x in range(11, 22):
        t = (x - 11) / 10
        y = int(round(hy + (by - hy) * t - 1.5 * math.sin(t * math.pi)))
        g.set(x, y, 16, 'spruce_slab[type=bottom]')
        g.set(x, y, 15, 'spruce_slab[type=bottom]')
        g.set(x, y + 1, 14, 'spruce_fence')
        g.set(x, y + 1, 17, 'spruce_fence')
    g.fill(21, by, 13, 27, by, 18, 'spruce_planks')
    K.lantern_post(g, 22, by + 1, 13, K.palette(wood='spruce'), h=2)
    # Ladders up the pillars.
    K.ladder(g, 8, 21, G, hy, 'south')
    for y in range(G, hy):
        if g.get(8, y, 20) is None:
            g.set(8, y, 20, 'spruce_planks')
    mob(g, 16, G + 12, 8, 'canyon_vulture', 2)
    return K.finish(g)


# ================================================================ Verdant Tangle and Sporeveil

def canopy_outpost():
    """Huts in the crowns of two jungle giants, joined by a hanging bridge."""
    G = 3
    g = K.grid(32, 40, 32)
    huts = []
    for (cx, cz, h) in ((9, 11, 22), (23, 21, 24)):
        K.big_trunk(g, cx, cz, G, h, 1.8, 'jungle_log', seed=cx)
        K.canopy(g, cx, cz, G + h - 1, 7, 'jungle_leaves', seed=cx, h=5)
        py = G + h - 7
        for x in range(cx - 4, cx + 5):
            for z in range(cz - 4, cz + 5):
                if math.hypot(x - cx, z - cz) <= 4.6 and g.get(x, py, z) is None:
                    g.set(x, py, z, 'jungle_planks')
                elif 4.6 < math.hypot(x - cx, z - cz) <= 5.2:
                    g.set(x, py + 1, z, 'jungle_fence')
        for x in range(cx + 2, cx + 5):
            for z in range(cz - 2, cz + 1):
                if x in (cx + 2, cx + 4) or z in (cz - 2, cz):
                    g.fill(x, py + 1, z, x, py + 2, z, 'bamboo_block[axis=y]')
        g.fill(cx + 2, py + 3, cz - 2, cx + 4, py + 3, cz, 'bamboo_mosaic_slab[type=bottom]')
        K.ladder(g, cx, cz + 3, G, py, 'south')
        for y in range(G, py):
            if g.get(cx, y, cz + 2) is None:
                g.set(cx, y, cz + 2, 'jungle_log[axis=y]')
        huts.append((cx, cz, py))
    g.chest(12, huts[0][2] + 1, 10, 'west', fl(CACHE, 'jungle'))
    g.chest(26, huts[1][2] + 1, 20, 'west', fl(VAULT, 'jungle'))
    (ax, az, ay), (bx, bz, by) = huts
    n = 12
    for i in range(n + 1):
        t = i / n
        x = int(round(ax + 4 + (bx - 4 - ax - 4) * t))
        z = int(round(az + (bz - az) * t))
        y = int(round(ay + (by - ay) * t - 1.5 * math.sin(t * math.pi)))
        g.set(x, y, z, 'jungle_slab[type=bottom]')
        g.set(x + 1, y, z, 'jungle_slab[type=bottom]')
    K.debris(g, 0, 0, 31, 31, G, ['fern', 'jungle_leaves[persistent=true]', 'short_grass', 'large_fern[half=lower]'], 0.15, seed=4)
    mob(g, 16, G, 4, 'vine_strangler')
    return K.finish(g)


def spore_lab():
    """A giant mushroom hollowed into an alchemist's laboratory, with a loft under the cap."""
    G = 3
    g = K.grid(32, 32, 32)
    c = 16
    import numpy as np
    X, Z = g.xz()
    d = np.hypot(X - c, Z - c)
    for y in range(G, G + 13):
        g.a[:, y, :][(d <= 4.4) & (d > 3.2)] = g.pid('mushroom_stem')
        g.a[:, y, :][d <= 3.2] = g.pid('air')
    g.a[:, G - 1, :][d <= 4.4] = g.pid('mycelium')
    g.a[:, G + 7, :][d <= 3.2] = g.pid('mushroom_stem')
    for y in range(G + 12, G + 18):
        rr = 9.5 - (y - G - 12) * 1.2
        ring = (d <= rr) & (d > rr - 1.5 if y < G + 17 else d >= 0)
        g.a[:, y, :][ring] = g.pid('red_mushroom_block')
    g.a[:, G + 12, :][d <= 8] = g.pid('red_mushroom_block')
    for (x, z) in ((c - 5, c + 3), (c + 4, c - 5), (c + 6, c + 4), (c - 6, c - 4)):
        g.set(x, G + 13, z, 'shroomlight')
    for (x, z) in ((c - 3, c - 6), (c + 5, c - 2), (c - 2, c + 6), (c + 2, c + 3)):
        g.set(x, G + 16, z, 'mushroom_stem')
    g.fill(c - 1, G, c + 4, c + 1, G + 1, c + 4, 'air')
    for (x, z) in ((c - 4, c), (c + 4, c), (c, c - 4)):
        g.set(x, G + 2, z, 'air')
        g.set(x, G + 9, z, 'air')
    # The laboratory.
    g.set(c - 2, G, c - 2, 'brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]')
    g.set(c - 1, G, c - 2, 'cauldron')
    g.set(c + 2, G, c - 2, 'water_cauldron[level=2]')
    for x in (c - 3, c + 3):
        g.fill(x, G, c - 1, x, G + 1, c + 1, 'bookshelf')
    g.set(c + 1, G, c - 2, 'barrel[facing=up,open=false]')
    kit.table(g, c, G, c + 1)
    clue(g, c - 1, G, c + 1, 'note', 'alchemist_notes')
    kit.hang_lantern(g, c, G + 6, c, 1)
    K.ladder(g, c + 2, c + 2, G, G + 7, 'north')
    g.chest(c - 2, G + 8, c + 1, 'east', fl(VAULT, 'spore'))
    kit.bed(g, c, G + 8, c - 2, 'red', 'north')
    for (x, z) in ((5, 6), (26, 8), (6, 25), (25, 26), (3, 15)):
        g.set(x, G, z, 'red_mushroom' if (x + z) % 2 else 'brown_mushroom')
    K.debris(g, 0, 0, 31, 31, G, ['brown_mushroom', 'red_mushroom', 'short_grass'], 0.06, seed=3)
    mob(g, c + 7, G, c - 6, 'sporeling', 2)
    mob(g, c - 8, G, c + 7, 'fungal_brute')
    return K.finish(g)


def glowcap_grotto():
    """A grotto lit by glowing mushrooms round a still spring."""
    G = 3
    g = K.grid(32, 24, 32)
    K.mound(g, 16, 13, G - 1, 11, 8, 10, [('stone', 3), ('mossy_cobblestone', 1), ('tuff', 1)], seed=9, shell=2.5)
    for z in range(17, 25):
        w = 2 if z > 20 else 3
        g.fill(16 - w, G, z, 16 + w, G + 3, z, 'air')
    g.fill(8, G - 1, 6, 24, G - 1, 20, 'moss_block')
    for x in range(12, 20):
        for z in range(8, 13):
            if (x - 15.5) ** 2 / 12 + (z - 10) ** 2 / 5 <= 1:
                g.set(x, G - 1, z, 'water')
                g.set(x, G - 2, z, 'clay')
    for k, (x, z) in enumerate(((9, 9), (22, 10), (10, 16), (21, 16), (16, 5))):
        h = 2 + k % 2
        g.fill(x, G, z, x, G + h - 1, z, 'mushroom_stem')
        g.set(x, G + h, z, 'shroomlight')
    for (x, y, z) in ((12, G + 5, 7), (18, G + 6, 9), (14, G + 5, 14), (20, G + 4, 12), (11, G + 4, 11)):
        g.set(x, y, z, 'cave_vines[age=0,berries=true]')
    g.chest(16, G, 15, 'north', fl(CACHE, 'spore'))
    K.debris(g, 8, 6, 24, 20, G, ['red_mushroom', 'brown_mushroom', 'moss_carpet', 'short_grass'], 0.15, seed=6)
    mob(g, 14, G, 17, 'sporeling', 3)
    return K.finish(g)


# ================================================================ Duskpine Weald, Mossmere Wood and Hollowwood

def ranger_outpost():
    """A palisade camp of the weald rangers: a watch platform, a cabin, an archery range."""
    G = 3
    g = K.grid(32, 24, 32)
    x0, x1, z0, z1 = 4, 27, 6, 25
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                g.fill(x, G - 1, z, x, G + 3, z, 'spruce_log[axis=y]')
                g.set(x, G + 4, z, 'spruce_fence')
    g.fill(15, G, z1, 17, G + 3, z1, 'air')
    # Watch platform in the north-east corner.
    for (x, z) in ((22, 8), (26, 8), (22, 12), (26, 12)):
        g.fill(x, G, z, x, G + 6, z, 'spruce_log[axis=y]')
    g.fill(22, G + 6, 8, 26, G + 6, 12, 'spruce_planks')
    for x in range(22, 27):
        for z in range(8, 13):
            if x in (22, 26) or z in (8, 12):
                g.set(x, G + 7, z, 'spruce_fence')
    g.fill(24, G, 12, 24, G + 5, 12, 'spruce_planks')
    K.ladder(g, 24, 13, G, G + 6, 'south')
    g.set(24, G + 7, 12, 'air')
    # The cabin.
    K.room(g, 6, 8, 12, 13, G, 4, K.palette(block='spruce_planks', floor='spruce_planks'), door=(9, 13))
    kit.gable(g, 6, 8, 12, 13, G + 4, 'spruce', axis='x', overhang=1, gable_fill='spruce_planks')
    kit.bed(g, 7, G, 9, 'green', 'north')
    g.chest(11, G, 9, 'south', fl(CACHE, 'forest'))
    g.set(11, G, 12, 'crafting_table')
    g.set(10, G, 9, 'barrel[facing=up,open=false]')
    kit.hang_lantern(g, 9, G + 3, 11, 0)
    # The archery range: a firing line and three targets on hay.
    for x in range(8, 22):
        g.set(x, G - 1, 22, 'spruce_slab[type=top]')
    for x in (10, 15, 20):
        g.set(x, G, 16, 'hay_block[axis=y]')
        g.set(x, G + 1, 16, 'target')
    K.campfire(g, 16, G, 19, lit=True)
    K.debris(g, x0 + 1, z0 + 1, x1 - 1, z1 - 1, G, ['short_grass', 'fern'], 0.08, seed=3)
    mob(g, 29, G, 28, 'duskpine_lynx')
    return K.finish(g)


def druids_ring():
    """Standing stones round a flat altar. Four carry flames, one to four; strike them as the flames
    count and the altar gives up its gift."""
    G = 3
    g = K.grid(32, 24, 32)
    c = 16
    posts = {}
    counts = [3, 1, 4, 2]
    for i in range(8):
        t = math.radians(i * 45)
        x, z = int(round(c + math.cos(t) * 8)), int(round(c + math.sin(t) * 8))
        h = 3 + (i % 3)
        K.standing_stone(g, x, z, G, h, 'mossy_stone_bricks' if i % 2 == 0 else 'mossy_cobblestone', seed=i)
        if i % 2 == 0:
            n = counts[i // 2]
            K.candles(g, x, G + h, z, n, lit=True, color='green')
            # The button on the stone's inner face, toward the altar.
            fx = 0 if abs(math.cos(t)) < 0.5 else (-1 if math.cos(t) > 0 else 1)
            fz = 0 if abs(math.sin(t)) < 0.5 else (-1 if math.sin(t) > 0 else 1)
            face = {(-1, 0): 'west', (1, 0): 'east', (0, -1): 'north', (0, 1): 'south'}[(fx, fz)]
            K.button(g, x + fx, G + 1, z + fz, face)
            posts[n] = (x + fx, G + 1, z + fz)
    order = [posts[n] for n in sorted(posts)]
    g.fill(c - 1, G - 1, c - 1, c + 1, G - 1, c + 1, 'smooth_stone')
    g.set(c, G, c, 'mossy_stone_bricks')
    seal(g, c, G + 1, c, 'order', 'druid_stones', order, [(c, G - 1, c + 2)])
    g.set(c, G - 1, c + 2, 'mossy_cobblestone')
    g.fill(c - 1, G - 3, c + 1, c + 1, G - 3, c + 3, 'stone')
    g.chest(c, G - 2, c + 2, 'north', 'afterworld:chests/site_edict')
    clue(g, c + 2, G, c - 1, 'glyph', 'druid_rite')
    for k, (x, z) in enumerate(((3, 4), (28, 3), (2, 27), (29, 28), (15, 29))):
        K.tree(g, x, z, G, 6, 'oak_log', 'oak_leaves', r=3, seed=k)
    K.debris(g, 0, 0, 31, 31, G, ['moss_carpet', 'fern', 'short_grass', 'red_mushroom'], 0.15, seed=4)
    mob(g, c - 5, G, c + 11, 'rootling_sentinel')
    mob(g, 28, G + 7, 3, 'mossmere_owl')
    return K.finish(g)


def hermits_bough():
    """A treehouse high in a Hollowwood giant: a platform, a hut, a pulley, the hermit's diary."""
    G = 3
    g = K.grid(32, 40, 32)
    c = 16
    K.big_trunk(g, c, c, G, 24, 2.6, 'dark_oak_log', seed=4)
    K.canopy(g, c, c, G + 22, 10, 'dark_oak_leaves', seed=2, h=6)
    py = G + 14
    for x in range(c - 7, c + 8):
        for z in range(c - 7, c + 8):
            d = math.hypot(x - c, z - c)
            if d <= 6.6 and g.get(x, py, z) is None:
                g.set(x, py, z, 'oak_planks')
            elif 6.6 < d <= 7.3:
                g.set(x, py + 1, z, 'oak_fence')
    for (x, z) in ((c - 5, c - 5), (c + 5, c - 5), (c - 5, c + 5), (c + 5, c + 5)):
        g.fill(x, G, z, x, py - 1, z, 'dark_oak_fence')
    # The hut on the platform's south side.
    hx0, hx1, hz0, hz1 = c - 3, c + 3, c + 3, c + 6
    K.room(g, hx0, hz0, hx1, hz1, py + 1, 3, K.palette(block='oak_planks', floor='oak_planks'), floor=False, door=(c, hz0))
    kit.gable(g, hx0, hz0, hx1, hz1, py + 4, 'dark_oak', axis='x', overhang=1, gable_fill='oak_planks')
    g.set(hx1, py + 2, c + 4, 'glass_pane')
    kit.bed(g, hx0 + 1, py + 1, hz1 - 1, 'green', 'west')
    g.chest(hx1 - 1, py + 1, hz1 - 1, 'west', fl(VAULT, 'forest'))
    g.set(hx1 - 1, py + 1, hz0 + 1, 'bookshelf')
    kit.potted(g, hx1 - 1, py + 2, hz0 + 1, 'red_mushroom')
    clue(g, c + 1, py + 1, hz0 + 1, 'note', 'hermit_diary')
    # Ladder up the trunk, a pulley with a bucket barrel.
    lz = c - 4
    for y in range(G, py + 1):
        for z in range(c - 6, c):
            if g.get(c, y, z) is not None and 'ladder' not in g.get(c, y, z) and 'planks' not in g.get(c, y, z):
                lz = z - 1
                break
        g.set(c, y, lz, 'ladder[facing=north,waterlogged=false]')
    kit.chain(g, c - 6, G + 2, py + 3, c)
    g.set(c - 6, G + 1, c, 'barrel[facing=up,open=false]')
    g.set(c - 6, py + 4, c, 'oak_planks')
    kit.hang_lantern(g, c + 3, py + 4, c - 3, 1)
    K.debris(g, 0, 0, 31, 31, G, ['fern', 'short_grass', 'brown_mushroom'], 0.12, seed=5)
    mob(g, c + 6, py + 8, c - 4, 'mossmere_owl')
    return K.finish(g)


# ================================================================ the coasts

def drowned_shrine():
    """A shrine the tide half took: flooded floor, broken columns, a bell tower with no bell. The
    apprentice kept the bell's rope; it lies by the altar still."""
    G = 3
    g = K.grid(32, 30, 32)
    P = PAL['prism']
    x0, x1, z0, z1 = 9, 22, 8, 21
    g.fill(x0, G - 2, z0, x1, G - 1, z1, 'mossy_stone_bricks')
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if hash3(x, 0, z, 3) < 0.45:
                g.set(x, G - 1, z, 'water')
                if hash3(x, 1, z, 3) < 0.25:
                    g.set(x, G - 1, z, 'sea_pickle[pickles=3,waterlogged=true]')
    for (a, b, c, d) in ((x0, z0, x1, z0), (x0, z0, x0, z1), (x1, z0, x1, z1)):
        K.ruin_wall(g, a, b, c, d, G, 5, P.block, seed=5, decay=0.6)
    for (x, z) in ((x0 + 3, z1), (x1 - 3, z1)):
        g.fill(x, G - 1, z, x, G + 4, z, 'prismarine_bricks')
        g.set(x, G + 5, z, 'dark_prismarine')
    kit.arch(g, x0 + 3, x1 - 3, G + 3, 2, P.stair, axis='x', at=z1, fill_above='prismarine_bricks', top=G + 5)
    # The bell tower, empty, a chain hanging where the rope was.
    g.fill(13, G - 1, 2, 18, G + 11, 7, 'stone_bricks')
    g.fill(14, G, 3, 17, G + 11, 6, 'air')
    g.fill(15, G, 7, 16, G + 1, 7, 'air')
    for (x, z) in ((13, 2), (18, 2), (13, 7), (18, 7)):
        g.fill(x, G + 12, z, x, G + 14, z, 'stone_brick_wall')
    g.fill(13, G + 15, 2, 18, G + 15, 7, 'stone_brick_slab[type=bottom]')
    kit.chain(g, 15, G + 4, G + 14, 4)
    K.erode(g, 13, 2, 18, 7, G + 9, G + 16, seed=3, scale=2.5)
    # The altar and the apprentice's chest.
    g.set(15, G - 1, 10, 'chiseled_stone_bricks')
    g.set(15, G, 10, 'prismarine_bricks')
    K.candles(g, 14, G, 10, 2, False, 'light_blue')
    K.candles(g, 16, G, 10, 3, False, 'light_blue')
    g.chest(17, G, 10, 'west', 'afterworld:chests/drowned_shrine')
    clue(g, 13, G, 11, 'note', 'apprentice_note')
    K.debris(g, 0, 0, 31, 31, G, ['sand', 'bone_block[axis=x]', 'prismarine'], 0.04, seed=4)
    mob(g, 12, G, 15, 'drowned_revenant', 2)
    elite(g, 18, G, 17, 'drowned_revenant')
    return K.finish(g, P)


def lighthouse():
    """The Dark Lighthouse: a broad red-and-white tower on the shore rocks, thick-walled at its foot.
    A stair two blocks wide winds up the inside of the wall past the entry hall, the keeper's
    quarters and the oil store to the watch room. Above them is the lamp room, its great lamp
    sooted dark, ringed by a gallery open to the sea. Light the four braziers round the lamp and
    the soot falls away, the lamp burns again and the keeper's strongbox opens. His cottage
    stands at the tower's foot."""
    G = 3
    g = K.grid(48, 64, 48)
    cx, cz = 19, 24
    X, Z = g.xz()
    D = np.hypot(X - cx, Z - cz)
    A = np.degrees(np.arctan2(Z - cz, X - cx)) % 360
    air = g.pid('air')

    def at(a, d):
        t = math.radians(a)
        return int(round(cx + math.cos(t) * d)), int(round(cz + math.sin(t) * d))

    def facing_of(dx, dz):
        return ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north')

    # The shore rocks, and a round stone plinth on them with a step all round.
    K.mound(g, 25, 24, G - 1, 19, 2, 15, [('stone', 3), ('tuff', 1), ('cobblestone', 1), ('andesite', 1)], seed=6)
    top = G + 1
    for y in range(top, top + 10):
        g.a[:, y, :][D <= 12.6] = air
    for y in (G - 1, G):
        g.a[:, y, :][D <= 11.4] = g.pid('stone_bricks')
    g.a[:, G, :][(D > 11.4) & (D <= 12.6)] = g.pid('stone_brick_slab[type=bottom]')

    # The tower: the inside the same width all the way up, the outside narrowing as it rises, in
    # bands of red and white with stone on the inner face.
    H = 36
    LF = top + H                      # the lamp room's floor, and the gallery round it
    RI = 5.6                          # inside radius
    def ro(y):
        t = min(1.0, max(0.0, (y - top) / (H - 1)))
        return 6.6 + 1.9 * (1 - t) ** 1.6
    white, red = g.pid('white_terracotta'), g.pid('red_terracotta')
    for y in range(top, LF):
        r = ro(y)
        wall = (D > RI) & (D <= r + 0.35)
        g.a[:, y, :][wall] = g.pid('stone_bricks')
        g.a[:, y, :][wall & (D > r - 0.75)] = red if (y - top) // 6 % 2 == 1 else white
        g.a[:, y, :][D <= RI] = air
    for y in (top, top + 1):
        g.a[:, y, :][(D > ro(y) + 0.35) & (D <= ro(y) + 1.35)] = g.pid('stone_bricks')
    g.a[:, top + 2, :][(D > ro(top + 2) + 0.35) & (D <= ro(top + 1) + 1.35)] = g.pid('stone_brick_slab[type=bottom]')

    # Floors: a compass rose in the entry hall, planks above.
    g.a[:, top - 1, :][D <= RI] = g.pid('polished_andesite')
    g.a[:, top - 1, :][(D <= RI) & (D > RI - 1.0)] = g.pid('stone_bricks')
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for k in range(1, 5):
            g.set(cx + dx * k, top - 1, cz + dz * k, 'red_terracotta' if dz == -1 else 'polished_diorite')
    g.set(cx, top - 1, cz, 'chiseled_stone_bricks')
    FLOORS = [top + 9, top + 18, top + 27]
    for f in FLOORS:
        g.a[:, f, :][D <= RI] = g.pid('spruce_planks')

    # The gallery: a stone ring standing out on corbels, behind a parapet you can't fall over.
    g.a[:, LF, :][D <= 9.4] = g.pid('polished_andesite')
    for y, (r0, r1) in ((LF - 1, (ro(LF - 1) + 0.35, 9.4)), (LF - 2, (ro(LF - 2) + 0.35, ro(LF - 2) + 1.6))):
        for (x, z) in np.argwhere((D > r0) & (D <= r1)):
            kit.stairs(g, x, y, z, 'stone_brick', facing_of(cx - x, cz - z), 'top')
    par = (D > 8.45) & (D <= 9.4)
    # Close the diagonal gaps, so the ring is joined edge to edge.
    for (x, z) in np.argwhere(par.copy()):
        for (dx, dz) in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            nx, nz = x + dx, z + dz
            if 0 <= nx < g.sx and 0 <= nz < g.sz and par[nx, nz] and not par[nx, z] and not par[x, nz]:
                if D[nx, z] >= D[x, nz]:
                    par[nx, z] = True
                else:
                    par[x, nz] = True
    g.a[:, LF, :][par] = g.pid('polished_andesite')
    g.a[:, LF + 1, :][par] = red
    g.a[:, LF + 2, :][par] = g.pid('stone_brick_wall')

    # The lamp room: tall panes between white piers, doors out east and west, a copper roof.
    glass = (D > RI) & (D <= RI + 1.0)
    for y in range(LF + 1, LF + 6):
        g.a[:, y, :][glass] = g.pid('glass_pane')
        g.a[:, y, :][D <= RI] = air
    for k in range(8):
        pa = 22.5 + 45 * k
        for (x, z) in np.argwhere(glass & (np.abs((A - pa + 180) % 360 - 180) < 7)):
            for y in range(LF + 1, LF + 6):
                g.set(x, y, z, 'white_terracotta')
    for da in (0, 180):  # clear of the stairwell, which reaches the lamp room at about 323 degrees
        for (x, z) in np.argwhere(glass & (np.abs((A - da + 180) % 360 - 180) < 9)):
            g.set(x, LF + 1, z, 'air')
            g.set(x, LF + 2, z, 'air')
    g.a[:, LF + 6, :][D <= RI + 1.8] = g.pid('oxidized_cut_copper')
    g.cone(cx, cz, LF + 7, 5, RI + 0.8, 'oxidized_cut_copper', 1.0, hollow=True)
    g.set(cx, LF + 12, cz, 'oxidized_copper')
    g.set(cx, LF + 13, cz, 'lightning_rod[facing=up,powered=false,waterlogged=false]')

    # The stair: two blocks wide against the inside of the wall, a ramp of slabs and planks that
    # rises half a block a step (walked, never climbed: stair blocks would turn into corner pieces
    # on a curve). It rises 11 blocks a turn, cuts through each floor it meets and ends in the lamp
    # room. Floors are furnished away from where it passes.
    T = 11.0
    a0 = 225.0

    def layers(a):
        """The stair's step layers at angle a, and whether each is a slab (else a full block)."""
        out = []
        h0 = ((a - a0) % 360) / 360 * T
        for k in range(6):
            q = int(2 * (h0 + k * T))
            if q > 2 * H + 1:
                break
            out.append((top + q // 2, q % 2 == 0))
        return out

    def stair_between(a, lo, hi):
        return any(lo <= L <= hi for (L, _) in layers(a))

    def crossing(f):
        """The angle where the stair arrives on the floor at layer f."""
        return (a0 + (f - top) / T * 360) % 360

    def free(a, f):
        """Nothing of the stair at the floor's level, or just above or below it."""
        return not stair_between(a, f - 3, f + 2)

    ring = np.argwhere((D > RI - 2.05) & (D <= RI))
    steps = [(x, z, L, slab) for (x, z) in ring for (L, slab) in layers(A[x, z])]
    # Three clear blocks over every step: stepping up, the body still overlaps the step below.
    for (x, z, L, slab) in steps:
        for y in (L + 1, L + 2, L + 3):
            g.set(x, y, z, 'air')
    for (x, z, L, slab) in steps:
        g.set(x, L, z, 'spruce_slab[type=bottom]' if slab else 'spruce_planks')
    # Rails round each stairwell, on the floor's open side.
    for f in FLOORS + [LF]:
        for (x, z) in np.argwhere((D > RI - 3.05) & (D <= RI - 2.05)):
            if stair_between(A[x, z], f - 3, f - 1):
                g.set(x, f + 1, z, 'spruce_fence')
    # Torches on the wall all the way up.
    for k in range(0, 360, 45):
        a = (a0 + 25 + k) % 360
        x, z = at(a, RI - 0.3)
        out = facing_of(math.cos(math.radians(a)), math.sin(math.radians(a)))
        wx, wz = x + K.FACE[out][0], z + K.FACE[out][1]
        if D[x, z] > RI or D[wx, wz] <= RI:
            continue
        for (L, slab) in layers(A[x, z]):
            if L + 2 < LF and g.get(x, L + 2, z) == 'minecraft:air':
                g.set(x, L + 2, z, f'wall_torch[facing={K.OPP[out]}]')

    def place(a, d, f, state=None):
        """A spot on floor f at angle a, distance d; it must be clear of the stair."""
        x, z = at(a, d)
        if not free(A[x, z], f):
            raise ValueError(f'lighthouse: {a} degrees on floor {f} is on the stair')
        if state:
            g.set(x, f + 1, z, state)
        return x, z

    # The door, south, under a round arch, with lamps either side.
    for x in range(cx - 1, cx + 2):
        for z in range(cz + 4, cz + 12):
            if D[x, z] > RI - 0.5:
                for y in range(top, top + 3 + (x == cx)):
                    g.set(x, y, z, 'air')
    g.set(cx, top + 4, cz + int(round(ro(top + 4))), 'chiseled_stone_bricks')
    for x in (cx - 2, cx + 2):
        z = cz + 10
        g.set(x, top, z, 'stone_brick_wall')
        g.set(x, top + 1, z, 'lantern[hanging=false]')

    # Windows: deep embrasures through the thick wall, glass on the outer face.
    def window(a, y0, h=2):
        t = math.radians(a)
        for dd in np.arange(RI - 0.4, 10.0, 0.3):
            x, z = int(round(cx + math.cos(t) * dd)), int(round(cz + math.sin(t) * dd))
            if D[x, z] <= RI:
                continue
            for y in range(y0, y0 + h):
                s = g.get(x, y, z)
                if s is None or s == 'minecraft:air' or s.endswith('glass_pane'):
                    continue
                g.set(x, y, z, 'glass_pane' if D[x, z] > ro(y) - 0.65 else 'air')
    for a in (0, 180, 270):
        window(a, top + 2)
    for f, angles, h in ((FLOORS[0], (0, 90, 270), 2), (FLOORS[1], (90, 180, 270), 2), (FLOORS[2], (0, 90, 180, 270), 3)):
        for a in angles:
            window(a, f + 2, h)

    # The entry hall (the stair rises from it at a0): stores by the wall, a chart table, a bench.
    f = top - 1
    for a in (335, 347, 359):
        x, z = place(a, 4.6, f)
        K.barrels(g, x, top, z, seed=int(a), n=1)
    place(10, 4.4, f, 'cartography_table')
    for a in (40, 160):
        place(a, 5.0, f, 'lantern[hanging=false]')
    x, z = place(130, 4.8, f)
    kit.stairs(g, x, top, z, 'spruce', 'north')
    kit.hang_lantern(g, cx, FLOORS[0] - 1, cz, 1)

    # The keeper's quarters: bed, rug, table, and his chest.
    f = FLOORS[0]
    c = crossing(f)
    g.a[:, f + 1, :][D <= 2.3] = g.pid('blue_carpet')
    g.a[:, f + 1, :][D <= 1.2] = g.pid('white_carpet')
    a = c + 125
    out = facing_of(math.cos(math.radians(a)), math.sin(math.radians(a)))
    hx, hz = place(a, 5.0, f)
    place(a, 4.0, f)
    kit.bed(g, hx - K.FACE[out][0], f + 1, hz - K.FACE[out][1], 'red', out)
    x, z = place(c + 145, 4.9, f, 'barrel[facing=up,open=false]')
    K.candles(g, x, f + 2, z, 2, lit=False)
    x, z = place(c + 170, 4.7, f, 'bookshelf')
    g.set(x, f + 2, z, 'bookshelf')
    x, z = place(c + 200, 4.8, f)
    g.chest(x, f + 1, z, K.OPP[facing_of(x - cx, z - cz)], fl(CACHE, 'coast'))
    x, z = place(c + 245, 3.9, f)
    kit.table(g, x, f + 1, z)
    kit.chair(g, x + 1, f + 1, z, 'spruce', 'west')
    place(c + 225, 5.0, f, 'lantern[hanging=false]')
    kit.hang_lantern(g, cx, FLOORS[1] - 1, cz, 1)

    # The oil store: barrels and crates, a cauldron of lamp oil, the anvil, and something that came
    # up from the sea.
    f = FLOORS[1]
    c = crossing(f)
    for k in range(5):
        x, z = place(c + 110 + 14 * k, 4.7, f)
        K.barrels(g, x, f + 1, z, seed=k + 3, n=1)
    place(c + 190, 4.6, f, 'cauldron')
    place(c + 215, 4.6, f, 'anvil[facing=east]')
    place(c + 232, 5.0, f, 'lantern[hanging=false]')
    x, z = place(c + 250, 4.6, f)
    g.chest(x, f + 1, z, K.OPP[facing_of(x - cx, z - cz)], fl(CACHE, 'coast'))
    mob(g, cx, f + 1, cz, 'drowned_revenant')
    kit.hang_lantern(g, cx + 1, FLOORS[2] - 1, cz, 1)

    # The watch room: the keeper's desk and log, a telescope at the north window, and the
    # strongbox in the west wall, barred until the lamp is lit.
    f = FLOORS[2]
    place(180, 5.0, f)
    place(180, 4.0, f)
    g.chest(cx - 5, f + 1, cz, 'east', fl(VAULT, 'coast'))
    g.set(cx - 5, f + 1, cz - 1, 'chiseled_stone_bricks')
    g.set(cx - 5, f + 1, cz + 1, 'chiseled_stone_bricks')
    g.set(cx - 5, f + 2, cz, 'stone_brick_slab[type=bottom]')
    g.set(cx - 4, f + 1, cz, 'iron_bars')
    strongbox = (cx - 4, f + 1, cz)
    x, z = place(135, 4.2, f)
    kit.table(g, x, f + 1, z)
    clue(g, x, f + 2, z, 'note', 'keeper_log')
    kit.chair(g, x + 1, f + 1, z, 'spruce', 'west')
    place(270, 4.0, f, 'spruce_fence')
    g.set(cx, f + 2, cz - 4, 'end_rod[facing=north]')
    place(225, 4.4, f, 'cartography_table')
    place(250, 5.0, f, 'lantern[hanging=false]')
    kit.hang_lantern(g, cx - 1, LF - 1, cz + 1, 1)

    # The great lamp: a heart of light caked in soot, under a copper hood, with four cold braziers
    # round it, and the seal before it. Lit, the soot falls away.
    g.fill(cx - 1, LF + 1, cz - 1, cx + 1, LF + 1, cz + 1, 'chiseled_stone_bricks')
    g.fill(cx - 1, LF + 2, cz - 1, cx + 1, LF + 3, cz + 1, 'shroomlight')
    soot = []
    for y in (LF + 2, LF + 3):
        for i in (-1, 0, 1):
            soot += [(cx + 2, y, cz + i), (cx - 2, y, cz + i), (cx + i, y, cz + 2), (cx + i, y, cz - 2)]
    soot += [(cx + i, LF + 4, cz + j) for i in (-1, 0, 1) for j in (-1, 0, 1)]
    for p in soot:
        g.set(*p, 'coal_block')
    g.fill(cx - 1, LF + 5, cz - 1, cx + 1, LF + 5, cz + 1, 'oxidized_cut_copper')
    fires = []
    for (dx, dz) in ((2, 2), (-2, 2), (-2, -2), (2, -2)):
        g.set(cx + dx, LF, cz + dz, 'stone_bricks')
        K.campfire(g, cx + dx, LF + 1, cz + dz, lit=False)
        fires.append((cx + dx, LF + 1, cz + dz))
    for a in (60, 120):
        place(a, 5.0, LF, 'lantern[hanging=false]')
    place(0, 3.0, LF)
    seal(g, cx + 3, LF + 1, cz, 'light', 'lamp_braziers', fires, soot + [strongbox])

    # The keeper's cottage, at the tower's foot, facing it.
    x0, z0, x1, z1 = 32, 18, 41, 27
    g.fill(x0, G - 2, z0, x1, G - 1, z1, 'cobblestone')
    g.fill(x0, G, z0, x1, top + 4, z1, 'air')
    CP = K.palette(block='spruce_planks', floor='spruce_planks')
    K.room(g, x0, z0, x1, z1, G + 1, 4, CP, door=(x0, 23))
    g.fill(x0, G, z0, x1, G, z1, 'spruce_planks')
    for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        g.fill(x, G, z, x, G + 4, z, 'stripped_spruce_log[axis=y]')
    for (x, z) in ((35, z0), (38, z0), (35, z1), (38, z1)):
        g.set(x, G + 2, z, 'glass_pane')
        g.set(x, G + 3, z, 'glass_pane')
    kit.door(g, x0, G + 1, 23, 'spruce', 'east')
    kit.gable(g, x0, z0, x1, z1, G + 5, 'spruce', axis='x', overhang=1, gable_fill='spruce_planks')
    g.fill(x1, G, 21, x1, G + 10, 22, 'bricks')
    K.campfire(g, x1 - 1, G + 1, 21, lit=False)
    K.campfire(g, x1 - 1, G + 1, 22, lit=False)
    kit.bed(g, x0 + 2, G + 1, z1 - 1, 'blue', 'west')
    g.chest(x1 - 1, G + 1, z1 - 1, 'west', fl(CACHE, 'coast'))
    kit.table(g, 36, G + 1, 22)
    kit.chair(g, 36, G + 1, 23, 'spruce', 'north')
    kit.chair(g, 37, G + 1, 22, 'spruce', 'west')
    kit.bookshelf_wall(g, x0 + 1, G + 1, z0 + 1, x0 + 3, G + 2, z0 + 1, seed=5)
    K.barrels(g, x1 - 1, G + 1, z0 + 1, seed=8, n=2)
    g.set(x1 - 1, G + 2, z0 + 1, 'lantern[hanging=false]')

    mob(g, 6, G + 9, 40, 'bone_gull', 2)
    mob(g, cx + 2, LF + 1, cz + 7, 'bone_gull')
    mob(g, 19 + 11, G + 1, 35, 'crystal_crab', 2)
    return K.finish(g)


def beached_wreck():
    """A ship on its side in the shallows, ribs to the sky, its mast across the sand."""
    G = 3
    g = K.grid(32, 22, 32)
    L = 20
    for i in range(L):
        x = 6 + i
        t = (i - L / 2) / (L / 2)
        w = 4.0 * math.sqrt(max(0.0, 1 - t ** 4))
        broken = i > 14 and hash3(i, 0, 0, 9) < 0.5
        for y in range(0, 7):
            half = int(round(w * math.sqrt(y / 6.0)))
            lean = int(round(y * 0.6))
            for dz in range(-half, half + 1):
                side = abs(dz) == half
                if y == 0 or side:
                    if broken and y > 2:
                        continue
                    if side and y > 3 and hash3(x, y, dz, 4) < 0.2:
                        continue
                    g.set(x, G - 2 + y, 15 + dz + lean, 'dark_oak_planks' if y < 5 or i % 3 else 'spruce_log[axis=y]')
            if y == 3 and half > 1:
                for dz in range(-half + 1, half):
                    g.set(x, G - 2 + y, 15 + dz + lean, 'spruce_planks' if hash3(x, 3, dz, 6) < 0.6 else 'air')
    g.line((14, G, 6), (28, G + 1, 4), 'spruce_log[axis=x]')
    for x in range(16, 26, 2):
        g.set(x, G + 1, 5, 'white_carpet')
    K.barrels(g, 11, G, 14, seed=3, n=3)
    g.chest(18, G - 1, 15, 'north', 'afterworld:chests/sunken_cache')
    K.debris(g, 0, 0, 31, 31, G, ['sand', 'bone_block[axis=z]', 'white_carpet'], 0.05, seed=5)
    mob(g, 22, G, 22, 'crystal_crab', 2)
    mob(g, 9, G, 22, 'drowned_revenant')
    return K.finish(g)


D = 'afterworld:'
META = {
    'drowned_shrine': dict(sink=3, relief=10, weight=4, biomes=[D + 'bone_shore', D + 'breakstone_shore'], shore=True, tags=['drowned_shrine']),
    'lighthouse': dict(sink=3, relief=12, weight=3, biomes=[D + 'breakstone_shore', D + 'bone_shore'], shore=True),
    'beached_wreck': dict(sink=3, relief=8, weight=3, biomes=[D + 'bone_shore', D + 'breakstone_shore'], shore=True),
    'ember_kilns': dict(sink=3, relief=8, weight=3, biomes=[D + 'ashen_moor']),
    'cinder_watchtower': dict(sink=3, relief=10, weight=3, biomes=[D + 'ashen_moor']),
    'bone_orchard': dict(sink=3, relief=8, weight=3, biomes=[D + 'pale_reach']),
    'weavers_hollow': dict(sink=3, relief=12, weight=3, biomes=[D + 'gloomwald']),
    'hanged_lanterns': dict(sink=3, relief=8, weight=3, biomes=[D + 'gloomwald']),
    'crystal_observatory': dict(sink=3, relief=8, weight=3, biomes=[D + 'shardfields']),
    'shattered_caravan': dict(sink=3, relief=8, weight=3, biomes=[D + 'shardfields']),
    'tea_house': dict(sink=3, relief=8, weight=3, biomes=[D + 'petalfall']),
    'lantern_dojo': dict(atlas=True, sink=3, relief=8, weight=2, biomes=[D + 'petalfall'], tags=['lair/shogun']),
    'frozen_watch_post': dict(sink=3, relief=10, weight=3, biomes=[D + 'frostmere_pines']),
    'colossus_graveyard': dict(sink=3, relief=8, weight=3, biomes=[D + 'whitewaste'], tags=['rimeback_trail']),
    'snowbound_camp': dict(sink=3, relief=8, weight=3, biomes=[D + 'whitewaste']),
    'rime_cavern': dict(sink=3, relief=16, weight=3, biomes=[D + 'rimeglass_spires']),
    'aurora_shrine': dict(sink=3, relief=8, weight=3, biomes=[D + 'glimmerfrost']),
    'peak_monastery': dict(sink=3, relief=16, weight=3, biomes=[D + 'skyspire_peaks']),
    'great_nest': dict(sink=3, relief=18, weight=3, biomes=[D + 'skyspire_peaks', D + 'windworn_highlands']),
    'wind_harp': dict(sink=3, relief=10, weight=3, biomes=[D + 'windworn_highlands']),
    'nomad_yurts': dict(sink=3, relief=8, weight=3, biomes=[D + 'ember_steppe']),
    'starfall_crater': dict(sink=3, relief=8, weight=3, biomes=[D + 'ember_steppe', D + 'hourglass_dunes']),
    'abandoned_mine': dict(sink=6, relief=14, weight=3, biomes=[D + 'rustscar_canyons']),
    'canyon_outpost': dict(sink=3, relief=14, weight=3, biomes=[D + 'rustscar_canyons']),
    'canopy_outpost': dict(sink=3, relief=12, weight=3, biomes=[D + 'verdant_tangle']),
    'spore_lab': dict(sink=3, relief=8, weight=3, biomes=[D + 'sporeveil']),
    'glowcap_grotto': dict(sink=3, relief=12, weight=3, biomes=[D + 'sporeveil']),
    'ranger_outpost': dict(sink=3, relief=8, weight=3, biomes=[D + 'duskpine_weald']),
    'druids_ring': dict(sink=3, relief=8, weight=3, biomes=[D + 'mossmere_wood']),
    'hermits_bough': dict(sink=3, relief=8, weight=3, biomes=[D + 'hollowwood']),
}

"""The Wardens' lairs: the Crimson Necropolis (the Lich on his throne, and his third phylactery),
the Rimeback's Den (a hollow in the ice where the colossus waits), the Sun Temple (a sun-dial
court of mirrors that wakes the Sentinel) and the Gilded Vault (five glyph tablets open its gate,
and the Warden behind it). The Lantern Dojo, in sites2, is the Shogun's."""
import math
from vox import hash3
import kit
import sitekit as K
from sitekit import PAL, mob, elite, clue, seal, boss

VAULT = 'afterworld:chests/site_vault'


def crimson_necropolis():
    """A walled city of the dead in red stone. Mausoleums line its streets; one keeps the Lich's
    third phylactery. At the centre, an open throne court where the Lich waits."""
    G = 3
    g = K.grid(48, 30, 48)
    P = K.palette(block='red_nether_bricks', rough='nether_bricks', accent='chiseled_nether_bricks', stair='red_nether_brick',
                  slab='red_nether_brick', wall='red_nether_brick_wall', floor='polished_blackstone_bricks', weather={'red_nether_bricks': ['nether_bricks', 'cracked_nether_bricks']})
    for (a, b, c, d) in ((2, 2, 45, 2), (2, 45, 45, 45), (2, 2, 2, 45), (45, 2, 45, 45)):
        K.ruin_wall(g, a, b, c, d, G, 6, P.block, seed=3, decay=0.35)
    g.fill(22, G, 45, 25, G + 4, 45, 'air')
    for x in (21, 26):
        g.fill(x, G - 1, 45, x, G + 7, 45, 'polished_blackstone_bricks')
        K.skull(g, x, G + 8, 45, 0, wither=True)
    K.path(g, [(23.5, 46), (23.5, 32)], G - 1, 'polished_blackstone_bricks', width=4, rate=0.9, seed=2)
    # The throne court: a sunken floor, braziers of soul fire, the throne.
    g.fill(14, G - 1, 12, 33, G - 1, 31, 'polished_blackstone')
    for x in range(14, 34):
        for z in range(12, 32):
            if (x + z) % 6 == 0:
                g.set(x, G - 1, z, 'chiseled_polished_blackstone')
    for (x, z) in ((15, 13), (32, 13), (15, 30), (32, 30), (15, 21), (32, 21)):
        g.fill(x, G, z, x, G + 4, z, P.block)
        K.campfire(g, x, G + 5, z, lit=True, soul=True)
    g.fill(21, G, 13, 26, G, 15, 'polished_blackstone_bricks')
    g.fill(22, G + 1, 13, 25, G + 1, 14, 'polished_blackstone_bricks')
    kit.stairs(g, 23, G + 2, 13, 'polished_blackstone_brick', 'south')
    kit.stairs(g, 24, G + 2, 13, 'polished_blackstone_brick', 'south')
    g.fill(22, G + 2, 12, 25, G + 6, 12, 'red_nether_bricks')
    for x in (22, 25):
        g.set(x, G + 7, 12, 'wither_skeleton_skull[rotation=0]')
    boss(g, 23, G, 18, 'crimson_lich')
    # Mausoleums along the streets; the north-east one keeps the third phylactery.
    for k, (x0, z0) in enumerate(((5, 6), (5, 20), (5, 34), (37, 6), (37, 20), (37, 34))):
        K.room(g, x0, z0, x0 + 6, z0 + 6, G, 5, P, door=(x0 + 3, z0 + 6 if k < 3 else z0 + 6))
        kit.gable(g, x0, z0, x0 + 6, z0 + 6, G + 5, 'red_nether_brick', axis='z', overhang=1, gable_fill=P.block)
        K.sarcophagus(g, x0 + 2, G, z0 + 2, 'x', P)
        if (x0, z0) == (37, 6):
            g.set(x0 + 3, G, z0 + 4, 'chiseled_nether_bricks')
            g.set(x0 + 3, G + 1, z0 + 4, 'afterworld:phylactery')
            elite(g, x0 + 4, G, z0 + 5, 'minecraft:wither_skeleton')
        else:
            g.chest(x0 + 5, G, z0 + 2, 'west', 'afterworld:chests/mausoleum_crypt' if k % 2 else f'{VAULT}_pale')
    clue(g, 23, G, 40, 'note', 'necropolis_decree')
    mob(g, 12, G, 40, 'minecraft:skeleton', 3)
    mob(g, 35, G, 40, 'mourning_shade', 2)
    K.debris(g, 3, 3, 44, 44, G, ['bone_block[axis=x]', 'soul_sand', 'crimson_roots'], 0.03, seed=5)
    return K.finish(g, P)


def rimeback_den():
    """A hollow in the ice, half-open to the sky, its floor strewn with what the colossus ate."""
    G = 3
    g = K.grid(48, 32, 48)
    K.mound(g, 24, 24, G - 1, 21, 13, 21, [('packed_ice', 4), ('snow_block', 3), ('blue_ice', 1), ('stone', 1)], seed=12, shell=4)
    K.erode(g, 12, 12, 36, 36, G + 10, G + 14, seed=4, scale=5.0)
    for z in range(38, 48):
        w = 3 if z > 42 else 4
        g.fill(24 - w, G, z, 24 + w, G + 5, z, 'air')
    g.fill(6, G - 1, 6, 42, G - 1, 42, 'packed_ice')
    for (x, z) in ((14, 14), (34, 16), (13, 33), (35, 32)):
        g.fill(x, G, z, x, G + 9, z, 'blue_ice')
    for k, z in enumerate((28, 31)):
        K.rib(g, 16, z, G - 1, 6, 4 - k, axis='x')
    for (x, z) in ((30, 12), (31, 13), (18, 36), (19, 36)):
        g.set(x, G, z, 'bone_block[axis=z]')
    K.skull(g, 32, G, 14, 6)
    g.chest(24, G, 8, 'south', f'{VAULT}_frost')
    clue(g, 24, G, 44, 'tracks', 'rimeback_tracks')
    boss(g, 24, G, 22, 'rimeback_colossus')
    mob(g, 15, G, 24, 'ice_crawler', 2)
    return K.finish(g)


def sun_temple():
    """A sandstone temple round a sun-dial court. Four mirrors stand in the court, each carved with
    one to four suns; strike them in that order and the light wakes what sleeps in the sanctum."""
    G = 3
    g = K.grid(48, 30, 48)
    P = PAL['sand']
    # The court and its sun-dial.
    g.fill(8, G - 1, 18, 39, G - 1, 45, 'smooth_sandstone')
    for a in range(0, 360, 15):
        t = math.radians(a)
        g.set(int(round(24 + math.cos(t) * 8)), G - 1, int(round(32 + math.sin(t) * 8)), 'chiseled_sandstone')
    g.fill(24, G, 32, 24, G + 6, 32, 'cut_sandstone')
    g.set(24, G + 7, 32, 'gold_block')
    for (a, b, c, d) in ((7, 17, 40, 17), (7, 46, 40, 46), (7, 17, 7, 46), (40, 17, 40, 46)):
        K.ruin_wall(g, a, b, c, d, G, 4, P.block, seed=8, decay=0.4)
    g.fill(22, G, 46, 25, G + 3, 46, 'air')
    mirrors = {}
    counts = {(14, 26): 2, (34, 26): 4, (14, 38): 1, (34, 38): 3}
    for (x, z), n in counts.items():
        g.fill(x, G, z, x, G + 2, z, 'cut_sandstone')
        g.set(x, G + 3, z, 'glass')
        for i in range(n):
            g.set(x - 1 + i, G - 1, z + 2, 'gold_block')
        K.button(g, x, G + 1, z + 1, 'south', 'birch')
        mirrors[n] = (x, G + 1, z + 1)
    order = [mirrors[n] for n in sorted(mirrors)]
    # The temple: a hall to the north, its gate barred until the court is solved.
    x0, x1, z0, z1 = 12, 35, 2, 16
    g.fill(x0, G - 1, z0, x1, G - 1, z1, 'smooth_sandstone')
    for (a, b, c, d) in ((x0, z0, x1, z0), (x0, z0, x0, z1), (x1, z0, x1, z1), (x0, z1, x1, z1)):
        g.fill(a, G, b, c, G + 9, d, P.block)
    for x in range(x0 + 2, x1 - 1, 4):
        g.fill(x, G, z0 + 1, x, G + 8, z0 + 1, 'chiseled_sandstone')
    g.fill(x0, G + 10, z0, x1, G + 10, z1, 'smooth_sandstone')
    g.fill(x0 + 4, G + 10, z0 + 4, x1 - 4, G + 10, z1 - 4, 'air')
    gate = [(x, y, z1) for x in range(21, 27) for y in range(G, G + 5)]
    for q in gate:
        g.set(*q, 'iron_bars' if q[1] < G + 4 else 'cut_sandstone')
    seal(g, 24, G, 22, 'order', 'sun_mirrors', order, gate)
    boss(g, 24, G, 8, 'dune_sentinel', dormant=True)
    g.chest(14, G, 4, 'south', f'{VAULT}_desert')
    g.chest(33, G, 4, 'south', f'{VAULT}_desert')
    clue(g, 20, G, 24, 'glyph', 'sun_dial')
    mob(g, 10, G, 42, 'glyph_scarab', 3)
    return K.finish(g, P, weather_rate=0.15)


def gilded_vault():
    """The vault of the last Warden, cut into a highland cliff behind a golden gate. Five glyph
    tablets set into the gate open it; the Warden wakes when they do."""
    G = 3
    g = K.grid(48, 34, 48)
    P = PAL['stone']
    for x in range(g.sx):
        for z in range(0, 34):
            fx = 1 - (abs(x - 23.5) / 24.5) ** 4
            top = int(G - 1 + (22 - z * 0.15) * fx * min(1.0, (z + 1) / 6.0))
            for y in range(G - 2, min(g.sy, top)):
                g.set(x, y, z, 'stone' if hash3(x, y, z, 2) < 0.7 else ('andesite' if hash3(x, y, z, 3) < 0.6 else 'tuff'))
    # The vault hall carved inside.
    K.chamber(g, 10, G, 6, 37, G + 10, 30, K.palette(block='polished_andesite', floor='polished_deepslate'))
    for x in range(12, 37, 4):
        for z in (8, 28):
            g.fill(x, G, z, x, G + 10, z, 'gold_block' if x % 8 == 0 else 'chiseled_stone_bricks')
    for x in range(10, 38):
        for z in range(6, 31):
            if (x * 7 + z * 3) % 11 == 0:
                g.set(x, G - 1, z, 'gilded_blackstone')
    # The facade and the gate.
    g.fill(16, G - 1, 33, 31, G + 14, 33, 'polished_andesite')
    for x in (16, 31):
        g.fill(x, G - 1, 34, x, G + 14, 34, 'gold_block')
    g.fill(16, G + 15, 33, 31, G + 15, 34, 'chiseled_stone_bricks')
    g.fill(18, G - 1, 34, 29, G - 1, 40, 'polished_andesite')
    g.fill(20, G, 31, 27, G + 6, 32, 'air')
    gate = [(x, y, 33) for x in range(20, 28) for y in range(G, G + 7)]
    for q in gate:
        g.set(*q, 'gold_block' if (q[0] + q[1]) % 2 else 'raw_gold_block')
    for (x, y) in ((19, G + 8), (21, G + 9), (24, G + 10), (26, G + 9), (28, G + 8)):
        g.set(x, y, 33, 'chiseled_stone_bricks')
    seal(g, 24, G - 1, 36, 'offer-afterworld.glyph_tablet-5', 'vault_gate', [], gate)
    boss(g, 24, G, 18, 'gilded_warden', dormant=True)
    g.chest(12, G, 26, 'east', f'{VAULT}_highland')
    g.chest(35, G, 26, 'west', f'{VAULT}_highland')
    clue(g, 22, G, 38, 'glyph', 'vault_inscription')
    return K.finish(g, P)


D = 'afterworld:'
META = {
    'crimson_necropolis': dict(atlas=True, sink=3, relief=10, weight=2, biomes=[D + 'pale_reach', D + 'gloomwald'], tags=['lair/lich', 'crypts']),
    'rimeback_den': dict(atlas=True, sink=3, relief=14, weight=2, biomes=[D + 'whitewaste'], tags=['lair/colossus']),
    'sun_temple': dict(atlas=True, sink=3, relief=8, weight=2, biomes=[D + 'hourglass_dunes'], tags=['lair/sentinel']),
    'gilded_vault': dict(atlas=True, sink=3, relief=16, weight=2, biomes=[D + 'windworn_highlands'], tags=['lair/warden']),
}

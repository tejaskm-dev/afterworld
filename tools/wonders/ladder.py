"""The Warden's Ladder: the floors of the fifty-floor climb (dev.afterworld.dungeon.Ladder) and its
gate in the world.

Every floor is an island hanging in the dark, built on layer G (the floor's walking level; its
paving is G - 1, and the Ladder sets G on its fixed height). Five tiers, each remembering one
Warden's land: the overgrown blossom country of the Shogun, the Lich's crimson necropolis, the
Colossus's rime, the Sentinel's sun-baked sandstone and the Gilded Warden's vault. Each tier has
two arenas, an echo's arena (every tenth floor) and they share the landing (every fifth floor).

Markers the Ladder reads: ladder:start (where climbers arrive), ladder:spawn (where creatures come
from), ladder:chest (where the reward stands once the floor is won), ladder:boss (the echo),
ladder:up and ladder:out (the rungs, which are Ladder stones set into the floor).
"""
import math
from vox import hash3, vnoise
import kit
import sitekit as K
from sitekit import mob, clue, npc

G = 12

TIERS = {
    'overgrown': dict(floor='mossy_stone_bricks', floor2='stone_bricks', inlay='ochre_froglight', rim='mossy_stone_brick_wall',
                      pillar='mossy_stone_bricks', cap='chiseled_stone_bricks', stair='mossy_stone_brick', slab='mossy_stone_brick',
                      under=[('stone', 4), ('moss_block', 3), ('mossy_cobblestone', 2), ('dirt', 1)], top='moss_block', accent='moss_block',
                      lantern='lantern', banner='pink', leaves='cherry_leaves', log='cherry_log', petals=True,
                      hang='cave_vines_plant[berries=true]'),
    'crimson': dict(floor='red_nether_bricks', floor2='polished_blackstone_bricks', inlay='shroomlight', rim='red_nether_brick_wall',
                    pillar='polished_blackstone_bricks', cap='chiseled_polished_blackstone', stair='polished_blackstone_brick',
                    slab='polished_blackstone_brick', under=[('blackstone', 4), ('basalt[axis=y]', 2), ('crimson_nylium', 1), ('netherrack', 2)],
                    top='crimson_nylium', accent='bone_block[axis=y]', lantern='soul_lantern', banner='red', leaves='nether_wart_block',
                    log='crimson_stem', petals=False, hang='weeping_vines_plant'),
    'rime': dict(floor='packed_ice', floor2='polished_diorite', inlay='sea_lantern', rim='diorite_wall', pillar='polished_diorite',
                 cap='quartz_block', stair='polished_diorite', slab='polished_diorite', under=[('packed_ice', 4), ('snow_block', 3), ('stone', 2),
                                                                                               ('blue_ice', 1)],
                 top='snow_block', accent='blue_ice', lantern='soul_lantern', banner='light_blue', leaves='spruce_leaves', log='spruce_log',
                 petals=False, hang='packed_ice'),
    'sun': dict(floor='smooth_sandstone', floor2='cut_sandstone', inlay='glowstone', rim='sandstone_wall', pillar='cut_sandstone',
                cap='chiseled_sandstone', stair='smooth_sandstone', slab='smooth_sandstone', under=[('sandstone', 4), ('sand', 2),
                                                                                                      ('terracotta', 2), ('orange_terracotta', 1)],
                top='sand', accent='orange_terracotta', lantern='lantern', banner='orange', leaves='jungle_leaves', log='jungle_log', petals=False,
                hang='pointed_dripstone[thickness=middle,vertical_direction=down,waterlogged=false]'),
    'gilded': dict(floor='polished_blackstone_bricks', floor2='polished_blackstone', inlay='glowstone', rim='polished_blackstone_brick_wall',
                   pillar='quartz_pillar[axis=y]', cap='chiseled_quartz_block', stair='polished_blackstone_brick',
                   slab='polished_blackstone_brick', under=[('blackstone', 4), ('gilded_blackstone', 2), ('polished_blackstone', 2), ('obsidian', 1)],
                   top='polished_blackstone', accent='gold_block', lantern='lantern', banner='yellow', leaves=None, log=None, petals=False,
                   hang='iron_chain[axis=y]'),
}


def island(g, cx, cz, r, T, depth=12, seed=1):
    """The rock the floor stands on: a rounded cone hanging under the paving, roughened."""
    for y in range(G - 1 - depth, G - 1):
        t = (G - 1 - y) / depth
        rr = r * (1 - t ** 1.6) + 0.5
        for x in range(int(cx - rr - 2), int(cx + rr + 3)):
            for z in range(int(cz - rr - 2), int(cz + rr + 3)):
                n = float(vnoise(x, y, z, 3.0, seed)) - 0.5
                if math.hypot(x - cx, z - cz) <= rr + n * 2.2:
                    w = hash3(x, y, z, seed + 3)
                    acc = 0
                    total = sum(wt for _, wt in T['under'])
                    for state, wt in T['under']:
                        acc += wt / total
                        if w <= acc:
                            g.set(x, y, z, state)
                            break
    # Hanging roots, chains or icicles under the rim.
    for k in range(14):
        a = hash3(k, 0, 0, seed) * math.pi * 2
        d = r * (0.3 + 0.6 * hash3(0, k, 0, seed))
        x, z = int(round(cx + math.cos(a) * d)), int(round(cz + math.sin(a) * d))
        bottom = None
        for y in range(0, G - 1):
            if g.get(x, y, z) not in (None, 'minecraft:air'):
                bottom = y
                break
        if bottom is None or bottom < 2:
            continue
        hang = 2 + int(hash3(k, 1, 1, seed) * 4)
        for y in range(bottom - hang, bottom):
            if y > 0:
                g.set(x, y, z, T['hang'])


def paving(g, cx, cz, r, T, seed=0, inner=None):
    """The floor: rings of the tier's paving with lit inlays every few blocks, so the dark can't win."""
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            d = math.hypot(x - cx, z - cz)
            if d > r + 0.35:
                continue
            ring = int(d) % 6
            state = T['floor2'] if ring in (0, 3) else T['floor']
            a = math.degrees(math.atan2(z - cz, x - cx)) % 360
            if ring == 3 and int(a) % 30 < 4:
                state = T['inlay']
            if d < 1.6:
                state = T['inlay']
            g.set(x, G - 1, z, state)
            for y in range(G, G + 18):
                g.set(x, y, z, 'air')
    if T['petals']:
        for x in range(int(cx - r), int(cx + r + 1)):
            for z in range(int(cz - r), int(cz + r + 1)):
                if math.hypot(x - cx, z - cz) < r - 1 and hash3(x, 5, z, seed) < 0.12:
                    g.set(x, G, z, f'pink_petals[facing=north,flower_amount={1 + int(hash3(x, 6, z, seed) * 4)}]')


def rim(g, cx, cz, r, T, gaps=()):
    """A low wall round the edge, open where the start and the rungs are."""
    for x in range(int(cx - r - 2), int(cx + r + 3)):
        for z in range(int(cz - r - 2), int(cz + r + 3)):
            d = math.hypot(x - cx, z - cz)
            if r - 0.65 < d <= r + 0.35:
                a = math.degrees(math.atan2(z - cz, x - cx)) % 360
                if any(abs((a - ga + 180) % 360 - 180) < gw for ga, gw in gaps):
                    continue
                g.set(x, G, z, T['rim'])


def pillar(g, x, z, h, T, broken=0.0, seed=0):
    """Cover: a pillar with a capital, sometimes snapped off."""
    top = h if broken == 0 or hash3(x, h, z, seed) > broken else max(2, int(h * 0.5))
    for y in range(G, G + top):
        g.set(x, y, z, T['pillar'])
    if top == h:
        g.set(x, G + h, z, T['cap'])
        lantern = T['lantern']
        g.set(x, G + h + 1, z, f'{lantern}[hanging=false]')
    else:
        g.set(x, G + top, z, f'{T["slab"]}_slab[type=bottom]')
    for face, (dx, dz) in K.FACE.items():
        kit.stairs(g, x + dx, G, z + dz, T['stair'], K.OPP[face])


def gate_arch(g, x, z, T, axis='x', h=6):
    """An arch over a rung: two pillars and a lintel, with banners."""
    for s in (-2, 2):
        px, pz = (x + s, z) if axis == 'x' else (x, z + s)
        for y in range(G, G + h):
            g.set(px, y, pz, T['pillar'])
        g.set(px, G + h, pz, T['cap'])
    for s in range(-2, 3):
        px, pz = (x + s, z) if axis == 'x' else (x, z + s)
        g.set(px, G + h + 1, pz, T['cap'] if s == 0 else T['floor2'])
    kit.hang_lantern(g, x, G + h, z, 1, soul=T['lantern'] == 'soul_lantern')


def rung(g, x, z, kind, T, facing='south', arch=True):
    """A Ladder stone set in the floor (cold until the Ladder lights it), framed and arched."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            g.set(x + dx, G - 1, z + dz, T['cap'])
    g.set(x, G - 1, z, f'afterworld:ladder_stone[kind={kind},active=false]')
    g.mark(x, G - 1, z, f'ladder:{kind}')
    if arch:
        gate_arch(g, x, z, T, axis='x' if facing in ('north', 'south') else 'z')


def decor_tree(g, x, z, T, seed=0):
    if T['log'] is None:
        # The vault has no trees: a gilded obelisk instead.
        K.obelisk(g, x, z, G, 5, K.palette(block='polished_blackstone_bricks', wall='polished_blackstone_brick_wall', slab='polished_blackstone_brick'),
                  cap='gold_block')
        return
    if T is TIERS['crimson']:
        K.dead_tree(g, x, z, G, 6, T['log'], seed=seed)
        for dx, dz in ((1, 0), (0, 1)):
            g.set(x + dx, G + 6, z + dz, T['leaves'])
        return
    if T is TIERS['rime']:
        # An ice spire.
        g.cone(x, z, G, 7, 1.6, 'packed_ice', 0.2)
        return
    K.tree(g, x, z, G, 5, T['log'], T['leaves'], r=3, seed=seed)


def spawns(g, cx, cz, r, start_angle=90, n=8):
    """Where creatures come from: a ring, keeping clear of where climbers arrive."""
    for k in range(n):
        a = 22.5 + k * (360 / n)
        if abs((a - start_angle + 180) % 360 - 180) < 50:
            continue
        x, z = int(round(cx + math.cos(math.radians(a)) * r)), int(round(cz + math.sin(math.radians(a)) * r))
        g.mark(x, G, z, 'ladder:spawn')


def arena(tier, variant):
    """An ordinary floor: an island with cover, a central dais for the reward and the rung up north."""
    T = TIERS[tier]
    g = K.grid(48, 40, 48)
    cx = cz = 23
    R = 19
    island(g, cx, cz, R + 1, T, seed=variant * 7 + len(tier))
    paving(g, cx, cz, R, T, seed=variant)
    rim(g, cx, cz, R, T, gaps=((90, 8), (270, 8)))
    # The start: a balcony on the south edge.
    for x in range(cx - 2, cx + 3):
        for z in range(cz + R - 1, cz + R + 3):
            g.set(x, G - 1, z, T['floor2'])
            for y in range(G, G + 4):
                g.set(x, y, z, 'air')
    for x in (cx - 3, cx + 3):
        for z in range(cz + R - 1, cz + R + 3):
            g.set(x, G, z, T['rim'])
    g.mark(cx, G, cz + R + 1, 'ladder:start')
    K.banner(g, cx - 3, G + 1, cz + R + 2, T['banner'], 'north')
    K.banner(g, cx + 3, G + 1, cz + R + 2, T['banner'], 'north')
    # The rung up, north, under its arch.
    rung(g, cx, cz - R + 2, 'up', T, 'north')
    # The dais in the middle, where the chest will stand.
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            if abs(x - cx) + abs(z - cz) <= 3:
                g.set(x, G, z, T['floor2'] if (x + z) % 2 else T['cap'])
    for face, (dx, dz) in K.FACE.items():
        for s in (-1, 0, 1):
            px, pz = (cx + dx * 3 + (s if dx == 0 else 0), cz + dz * 3 + (s if dz == 0 else 0))
            kit.stairs(g, px, G, pz, T['stair'], K.OPP[face])
    g.mark(cx, G + 1, cz, 'ladder:chest')
    if variant == 1:
        # Six pillars of cover in a ring, two of them broken.
        for k in range(6):
            a = math.radians(30 + k * 60)
            pillar(g, int(round(cx + math.cos(a) * 9)), int(round(cz + math.sin(a) * 9)), 5, T, broken=0.35, seed=k)
        for k in range(4):
            a = math.radians(45 + k * 90)
            decor_tree(g, int(round(cx + math.cos(a) * 15.5)), int(round(cz + math.sin(a) * 15.5)), T, seed=k)
    else:
        # Raised terraces east and west with steps, a broken wall across the middle.
        for side in (-1, 1):
            for x in range(cx + side * 9, cx + side * 15 + side, side):
                for z in range(cz - 6, cz + 7):
                    if math.hypot(x - cx, z - cz) < R - 1.5:
                        g.set(x, G, z, T['floor2'] if (x + z) % 3 else T['inlay'])
                        g.set(x, G + 1, z, 'air')
            for z in range(cz - 6, cz + 7):
                kit.stairs(g, cx + side * 8, G, z, T['stair'], 'east' if side > 0 else 'west')
            pillar(g, cx + side * 12, cz - 4, 4, T, seed=side + 5)
            pillar(g, cx + side * 12, cz + 4, 4, T, broken=0.9, seed=side + 9)
        K.ruin_wall(g, cx - 5, cz - 7, cx + 5, cz - 7, G, 3, T['pillar'].split('[')[0], seed=3, decay=0.5)
        K.ruin_wall(g, cx - 5, cz + 8, cx + 5, cz + 8, G, 2, T['pillar'].split('[')[0], seed=4, decay=0.55)
        for k in range(2):
            decor_tree(g, cx - 13 + k * 26, cz + 13, T, seed=k + 20)
    # Braziers round the edge keep it bright.
    for k in range(8):
        a = math.radians(k * 45)
        x, z = int(round(cx + math.cos(a) * (R - 1.6))), int(round(cz + math.sin(a) * (R - 1.6)))
        if abs(k * 45 - 90) < 20 or abs(k * 45 - 270) < 20:
            continue
        if g.get(x, G, z) in (None, 'minecraft:air'):
            K.campfire(g, x, G, z, lit=True, soul=T['lantern'] == 'soul_lantern')
    spawns(g, cx, cz, 13)
    return K.finish(g)


def echo_arena(tier):
    """An echo's floor: a wide, open ring with the Warden's sigil in the floor, cover only at the edge."""
    T = TIERS[tier]
    g = K.grid(64, 40, 64)
    cx = cz = 31
    R = 26
    island(g, cx, cz, R + 1, T, depth=16, seed=len(tier) * 3)
    paving(g, cx, cz, R, T, seed=11)
    rim(g, cx, cz, R, T, gaps=((90, 7), (270, 12)))
    # The sigil: a ring and a star of inlay round the middle.
    for x in range(cx - 12, cx + 13):
        for z in range(cz - 12, cz + 13):
            d = math.hypot(x - cx, z - cz)
            a = math.degrees(math.atan2(z - cz, x - cx)) % 72
            if 10.6 < d <= 11.4 or (d < 10.5 and (abs(a - 36) < 3.5 * (11 - d) / 11 + 0.6)):
                g.set(x, G - 1, z, T['inlay'] if d > 10.5 else T['floor2'])
    for x in range(cx - 2, cx + 3):
        for z in range(cz + R - 1, cz + R + 3):
            g.set(x, G - 1, z, T['floor2'])
            for y in range(G, G + 4):
                g.set(x, y, z, 'air')
    g.mark(cx, G, cz + R + 1, 'ladder:start')
    g.mark(cx, G, cz, 'ladder:boss')
    rung(g, cx - 4, cz - R + 2, 'up', T, 'north')
    rung(g, cx + 4, cz - R + 2, 'out', T, 'north', arch=False)
    g.mark(cx, G, cz - R + 6, 'ladder:chest')
    for k in range(10):
        a = math.radians(18 + k * 36)
        if abs(math.degrees(a) - 90) < 25 or abs(math.degrees(a) - 270) < 25:
            continue
        pillar(g, int(round(cx + math.cos(a) * (R - 4))), int(round(cz + math.sin(a) * (R - 4))), 7, T, broken=0.3, seed=k + 40)
    for k in range(12):
        a = math.radians(k * 30 + 15)
        x, z = int(round(cx + math.cos(a) * (R - 1.6))), int(round(cz + math.sin(a) * (R - 1.6)))
        if g.get(x, G, z) in (None, 'minecraft:air'):
            K.campfire(g, x, G, z, lit=True, soul=T['lantern'] == 'soul_lantern')
    return K.finish(g)


def landing():
    """The landing every fifth floor: a camp in the dark with a fire, Rook's stall, benches, and the
    rungs up and out."""
    T = dict(TIERS['overgrown'])
    T['inlay'] = 'shroomlight'
    g = K.grid(48, 36, 48)
    cx = cz = 23
    R = 16
    island(g, cx, cz, R + 1, T, seed=77)
    for x in range(cx - R - 1, cx + R + 2):
        for z in range(cz - R - 1, cz + R + 2):
            d = math.hypot(x - cx, z - cz)
            if d <= R + 0.35:
                g.set(x, G - 1, z, 'grass_block[snowy=false]' if d > 5 else ('spruce_planks' if d > 2 else 'cobblestone'))
                for y in range(G, G + 16):
                    g.set(x, y, z, 'air')
    # Grass and flowers, a path of planks to each rung.
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            d = math.hypot(x - cx, z - cz)
            if 5.5 < d < R - 0.5:
                r = hash3(x, 3, z, 9)
                if r < 0.18:
                    g.set(x, G, z, 'short_grass')
                elif r < 0.24:
                    g.set(x, G, z, ['dandelion', 'poppy', 'cornflower', 'oxeye_daisy', 'allium'][int(hash3(z, 4, x, 9) * 5)])
    for z in range(cz - R + 2, cz - 1):
        for x in (cx - 1, cx, cx + 1):
            g.set(x, G - 1, z, 'spruce_planks')
            g.set(x, G, z, 'air')
    for x in range(cx - R + 2, cx - 1):
        for z in (cz - 1, cz, cz + 1):
            g.set(x, G - 1, z, 'spruce_planks')
            g.set(x, G, z, 'air')
    rim(g, cx, cz, R, T, gaps=((90, 9),))
    # The fire, logs to sit on.
    K.campfire(g, cx, G, cz, lit=True)
    for (x, z, axis) in ((cx - 3, cz, 'z'), (cx + 3, cz, 'z'), (cx, cz + 3, 'x')):
        for s in (-1, 0, 1):
            g.set(x + (s if axis == 'x' else 0), G, z + (s if axis == 'z' else 0), f'stripped_spruce_log[axis={axis}]')
    # Rook's stall on the east side, his tent behind it.
    import bkit
    bkit.stall(g, cx + 9, G, cz, 'west', 'purple', seed=3, goods=['barrel[facing=up]', 'decorated_pot[facing=north]', 'brewing_stand', 'lantern[hanging=false]'])
    npc(g, cx + 10, G, cz, 'rook', 'west')
    K.tent(g, cx + 11, cz - 6, 5, G, 'purple', axis='z')
    # Lamp posts.
    for (x, z) in ((cx - 7, cz - 7), (cx + 7, cz + 7), (cx - 7, cz + 7), (cx + 6, cz - 9)):
        bkit.lamp_post(g, x, G, z)
    # The rungs: up to the north, out to the west.
    rung(g, cx, cz - R + 2, 'up', T, 'north')
    rung(g, cx - R + 2, cz, 'out', T, 'west')
    for x in range(cx - 2, cx + 3):
        for z in range(cz + R - 1, cz + R + 3):
            g.set(x, G - 1, z, 'spruce_planks')
            for y in range(G, G + 4):
                g.set(x, y, z, 'air')
    g.mark(cx, G, cz + R + 1, 'ladder:start')
    clue(g, cx - 4, G, cz + 4, 'note', 'ladder_landing')
    return K.finish(g)


def gate():
    """The Ladder's gate in the world: a broken arch of old dark stone over a lit stone, the first
    rung, with the Edict-writers' words on a fallen tablet."""
    g = K.grid(32, 24, 32)
    Gw = 3
    P = K.PAL['black'] if 'black' in K.PAL else K.palette(block='polished_blackstone_bricks', wall='polished_blackstone_brick_wall',
                                                           stair='polished_blackstone_brick', slab='polished_blackstone_brick')
    cx, cz = 15, 15
    K.floor(g, 6, 6, 25, 25, Gw - 1, 'polished_blackstone_bricks', rate=0.55, seed=2)
    K.floor(g, 9, 9, 22, 22, Gw - 1, 'cracked_polished_blackstone_bricks', rate=0.35, seed=4)
    # The dais and the gate stone.
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            g.set(x, Gw - 1, z, 'polished_blackstone')
            g.set(x, Gw, z, 'chiseled_polished_blackstone' if (x + z) % 2 else 'polished_blackstone')
    g.set(cx, Gw + 1, cz, 'afterworld:ladder_stone[kind=gate,active=false]')
    for face, (dx, dz) in K.FACE.items():
        for s in (-1, 0, 1):
            kit.stairs(g, cx + dx * 3 + (s if dx == 0 else 0), Gw, cz + dz * 3 + (s if dz == 0 else 0), 'polished_blackstone_brick', K.OPP[face])
    # The arch behind it: two great pillars and a broken lintel, climbing with rungs carved up its face.
    for s in (-4, 4):
        for y in range(Gw, Gw + 11):
            for dx in (0, 1 if s > 0 else -1):
                g.set(cx + s + dx, y, cz - 3, 'polished_blackstone_bricks' if y % 4 else 'gilded_blackstone')
                g.set(cx + s + dx, y, cz - 4, 'polished_blackstone_bricks')
    for x in range(cx - 5, cx + 4):
        if hash3(x, 1, 1, 5) < 0.85:
            g.set(x, Gw + 11, cz - 3, 'polished_blackstone_brick_slab[type=bottom]' if x in (cx - 5, cx + 3) else 'chiseled_polished_blackstone')
    for y in range(Gw + 1, Gw + 10, 2):
        g.set(cx - 3, y, cz - 3, 'gold_block')
        g.set(cx + 3, y, cz - 3, 'gold_block')
    for (x, z) in ((cx - 4, cz - 2), (cx + 5, cz - 2), (cx - 4, cz + 4), (cx + 5, cz + 4)):
        K.campfire(g, x, Gw, z, lit=True, soul=True)
    clue(g, cx + 3, Gw, cz + 3, 'note', 'ladder_gate')
    K.debris(g, 4, 4, 27, 27, Gw, ['polished_blackstone_brick_slab[type=bottom]', 'blackstone', 'gilded_blackstone'], 0.05, seed=8)
    return K.finish(g)


def proving_ring():
    """A proving ring in the world: standing stones round a trodden arena, a horn-stone on a dais at
    its heart (strike it and three waves of the land's creatures come), and a barred cist at the
    north edge that opens when the ring is held."""
    g = K.grid(48, 24, 48)
    Gw = 3
    cx = cz = 23
    R = 13
    for x in range(cx - R - 3, cx + R + 4):
        for z in range(cz - R - 3, cz + R + 4):
            d = math.hypot(x - cx, z - cz)
            if d <= R + 0.4:
                r = hash3(x, 1, z, 4)
                g.set(x, Gw - 1, z, 'coarse_dirt' if r < 0.55 else 'packed_mud' if r < 0.8 else 'gravel')
                for y in range(Gw, Gw + 8):
                    g.set(x, y, z, 'air')
            elif d <= R + 2.4 and hash3(x, 2, z, 4) < 0.75:
                g.set(x, Gw - 1, z, 'cobblestone' if hash3(x, 3, z, 4) < 0.6 else 'mossy_cobblestone')
    # The standing stones, a few crowned with lanterns.
    for k in range(12):
        a = math.radians(k * 30 + 15)
        x, z = int(round(cx + math.cos(a) * (R + 2))), int(round(cz + math.sin(a) * (R + 2)))
        h = 3 + int(hash3(k, 0, 0, 6) * 3)
        K.standing_stone(g, x, z, Gw, h, 'stone_bricks' if k % 3 else 'mossy_stone_bricks', seed=k, lean=0)
        if k % 3 == 0:
            g.set(x, Gw + h, z, 'lantern[hanging=false]')
    # The dais and the horn-stone.
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            g.set(x, Gw - 1, z, 'polished_andesite')
            if abs(x - cx) <= 1 and abs(z - cz) <= 1:
                g.set(x, Gw, z, 'chiseled_stone_bricks' if (x + z) % 2 == 0 else 'polished_andesite')
    for face, (dx, dz) in K.FACE.items():
        for s_ in (-1, 0, 1):
            kit.stairs(g, cx + dx * 2 + (s_ if dx == 0 else 0), Gw, cz + dz * 2 + (s_ if dz == 0 else 0), 'stone_brick', K.OPP[face])
    for (dx, dz) in ((3, 3), (-3, 3), (-3, -3), (3, -3)):
        K.brazier(g, cx + dx, Gw, cz + dz, K.PAL['stone'], lit=True)
    # The cist at the north edge: a stone box, its front barred, a chest inside.
    vx, vz = cx, cz - R - 1
    for x in range(vx - 2, vx + 3):
        for z in range(vz - 2, vz + 1):
            for y in range(Gw - 1, Gw + 3):
                g.set(x, y, z, 'mossy_stone_bricks' if hash3(x, y, z, 9) < 0.3 else 'stone_bricks')
    for x in range(vx - 1, vx + 2):
        g.set(x, Gw, vz - 1, 'air')
        g.set(x, Gw + 1, vz - 1, 'air')
    g.chest(vx, Gw, vz - 1, 'south', 'afterworld:chests/proving_ring')
    bars = []
    for x in range(vx - 1, vx + 2):
        for y in (Gw, Gw + 1):
            g.set(x, y, vz, 'iron_bars')
            bars.append((x, y, vz))
    for x in range(vx - 2, vx + 3):
        g.set(x, Gw + 3, vz - 1, 'stone_brick_slab[type=bottom]')
    K.seal(g, cx, Gw + 1, cz, 'waves-3', 'proving_ring', [], bars)
    K.banner(g, vx - 2, Gw + 3, vz + 1, 'red', 'south')
    K.banner(g, vx + 2, Gw + 3, vz + 1, 'red', 'south')
    clue(g, cx + 4, Gw, cz + 6, 'note', 'proving_ring')
    return K.finish(g, K.PAL['stone'])


def climbers_needle():
    """The Climber's Needle: a spire of rock forty blocks high, ledges spiralling up its sides for
    a climber's hands and feet, and on the very top a little platform with a cache, a flag and a
    lantern you can see for miles."""
    g = K.grid(32, 60, 32)
    Gw = 3
    cx = cz = 15.5
    H = 42
    mats = ['stone', 'andesite', 'tuff', 'stone', 'cobblestone']
    for y in range(Gw - 3, Gw + H):
        t = max(0.0, (y - Gw) / H)
        r = 6.2 - 3.2 * t + (1.6 if y < Gw + 3 else 0) * (1 - (y - Gw + 3) / 6)
        for x in range(32):
            for z in range(32):
                d = math.hypot(x - cx, z - cz) + (vnoise(x, y, z, 3.0, 5) - 0.5) * 1.8
                if d <= r:
                    m = mats[int(hash3(x, y // 3, z, 7) * len(mats))]
                    g.set(x, y, z, m)
    # Ledges: a spiral of footholds and handholds round the spire, every couple of blocks up.
    for k in range(0, H - 2):
        y = Gw + k
        t = k / H
        r = 6.2 - 3.2 * t
        a = k * 0.62
        x = int(round(cx + math.cos(a) * (r + 0.8)))
        z = int(round(cz + math.sin(a) * (r + 0.8)))
        if k % 2 == 0:
            g.set(x, y, z, 'stone_brick_slab[type=top]' if k % 4 else 'mossy_stone_brick_slab[type=top]')
    # The top: a stone platform, a flag on a pole, a lantern, a cache and a note.
    top = Gw + H
    for x in range(12, 20):
        for z in range(12, 20):
            if math.hypot(x - cx, z - cz) <= 3.9:
                g.set(x, top - 1, z, 'stone_bricks' if hash3(x, 1, z, 3) < 0.7 else 'cracked_stone_bricks')
                for y in range(top, top + 4):
                    g.set(x, y, z, 'air')
    for y in range(top, top + 5):
        g.set(13, y, 13, 'spruce_fence')
    for y in range(top + 3, top + 5):
        g.set(14, y, 13, 'red_wool')
        g.set(15, y, 13, 'red_wool' if y == top + 4 else 'white_wool')
    g.set(13, top + 5, 13, 'lantern[hanging=false]')
    g.chest(17, top, 17, 'west', 'afterworld:chests/needle_cache')
    clue(g, 16, top, 13, 'note', 'needle_top')
    clue(g, 15, Gw, 23, 'note', 'needle_base')
    return K.finish(g)


# Each floor's builder by name, for build.py: ladder_<tier>_<n>, ladder_<tier>_echo, ladder_landing.
for _t in TIERS:
    for _v in (1, 2):
        globals()[f'ladder_{_t}_{_v}'] = (lambda t, v: lambda: arena(t, v))(_t, _v)
    globals()[f'ladder_{_t}_echo'] = (lambda t: lambda: echo_arena(t))(_t)
ladder_landing = landing
ladder_gate = gate
ladder_gate_first = gate
FLOORS = [f'ladder_{t}_{v}' for t in TIERS for v in (1, 2)] + [f'ladder_{t}_echo' for t in TIERS] + ['ladder_landing']

D = 'afterworld:'
# The gate also stands in the world as a site (one is always placed near the town by the Atlas).
GATE_BIOMES = [D + b for b in ('vesper_downs', 'mossmere_wood', 'pale_reach', 'ashen_moor', 'hollowwood', 'duskpine_weald',
                               'windworn_highlands', 'ember_steppe', 'petalfall', 'shardfields', 'whitewaste', 'hourglass_dunes',
                               'gloomwald', 'rustscar_canyons', 'frostmere_pines', 'glimmerfrost')]
META = {
    # The first gate: always there, a short walk from the town (placed by the Atlas).
    'ladder_gate_first': dict(sink=3, relief=8, weight=1, tags=['ladder_gates'], atlas=True, biomes=GATE_BIOMES),
    'proving_ring': dict(sink=3, relief=6, weight=3, tags=['proving_rings'], biomes=GATE_BIOMES + [D + 'gloamfen', D + 'verdant_tangle']),
    'climbers_needle': dict(sink=3, relief=10, weight=3, tags=['needles'],
                            biomes=[D + b for b in ('windworn_highlands', 'rustscar_canyons', 'ember_steppe', 'vesper_downs', 'skyspire_peaks',
                                                    'pale_reach', 'hourglass_dunes', 'whitewaste')]),
    'ladder_gate': dict(sink=3, relief=8, weight=2, tags=['ladder_gates'],
                        biomes=[D + b for b in ('vesper_downs', 'mossmere_wood', 'pale_reach', 'ashen_moor', 'hollowwood', 'duskpine_weald',
                                                'windworn_highlands', 'ember_steppe', 'petalfall', 'shardfields', 'whitewaste', 'hourglass_dunes',
                                                'gloomwald', 'rustscar_canyons', 'frostmere_pines', 'glimmerfrost')]),
}

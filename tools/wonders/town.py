"""The Waystone Camp, grown into a walled Warden town: where every traveller wakes.

Laid out the way good game hubs are: one landmark that can be seen from everywhere (the Great
Waystone on its dais in the plaza, with its lantern crown), a main street from the gate that frames
it, and districts you can tell apart at a glance:
- north: the Warden Hall (Isolde) with its bell tower, banners and a court before it;
- west: craft row, Marta's forge with its great chimney, a workshop and a storehouse;
- east: the market, Bram's provisions, striped stalls and the Warden's Board;
- south-west: the Last Lantern inn and its stable yard;
- south-east: the Wardens' barracks, a training yard and the old camp's tents;
- north-west: Wren's map tower; north-east: a chapel and its quiet yard;
- houses, gardens, trees and a pond between them, lamp posts along every street.
A curtain wall with round towers rings it all, with a gatehouse to the south and a postern east.
"""
import math

import bkit
import kit
from vox import Grid, hash3

W, H, D = 160, 72, 160
G = 8            # ground layers under the town (its plateau)
C = 80           # centre
R_WALL = 66      # curtain wall radius


def pal(g, x, y, z, palette, t, seed=0):
    g.set(x, y, z, bkit.pick(palette, x, y, z, seed, t))


# ---------------------------------------------------------------- ground and streets

def ground(g):
    """The plateau: earth under everything inside the outer ring, grass on top, air above."""
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            ang = math.atan2(z + 0.5 - C, x + 0.5 - C)
            edge = 78.5 + 1.2 * math.sin(ang * 7 + 0.5)
            if d > edge:
                continue
            g.fill(x, 0, z, x, G - 3, z, 'stone')
            g.fill(x, G - 2, z, x, G - 2, z, 'dirt')
            h = hash3(x, 0, z, 1)
            top = 'grass_block' if h > 0.07 else 'coarse_dirt' if h > 0.035 else 'rooted_dirt'
            g.set(x, G - 1, z, top)
            g.fill(x, G, z, x, H - 1, z, 'air')


ROAD = ['cobblestone', 'stone_bricks', 'andesite', 'cobblestone', 'gravel', 'mossy_cobblestone', 'coarse_dirt']
LANE = ['dirt_path', 'dirt_path', 'coarse_dirt', 'gravel', 'cobblestone']


def street(g, x0, z0, x1, z1, width, palette=ROAD, seed=0, edge=True):
    """A paved street between two points: worn in the middle, ragged and grassy at the edges."""
    steps = int(max(abs(x1 - x0), abs(z1 - z0))) + 1
    for s in range(steps + 1):
        t = s / steps
        cx, cz = x0 + (x1 - x0) * t, z0 + (z1 - z0) * t
        for dx in range(-width, width + 1):
            for dz in range(-width, width + 1):
                d = math.hypot(dx, dz)
                if d > width + 0.3:
                    continue
                x, z = int(round(cx + dx)), int(round(cz + dz))
                if not (0 <= x < W and 0 <= z < D):
                    continue
                k = d / (width + 0.3)
                if edge and k > 0.8 and hash3(x, 1, z, seed + 7) < 0.45:
                    continue
                pal(g, x, G - 1, z, palette, 0.15 + 0.6 * k, seed)


def plaza(g):
    """The plaza: a paved disc with a mosaic ring, benches and trees in planters at the rim."""
    for x in range(C - 24, C + 25):
        for z in range(C - 24, C + 25):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if d > 22.5:
                continue
            ang = math.atan2(z + 0.5 - C, x + 0.5 - C)
            if 15.5 < d <= 17:
                st = 'polished_andesite' if int((ang + math.pi) / (math.pi / 12)) % 2 else 'stone_bricks'
            elif 17 < d <= 17.8:
                st = 'chiseled_stone_bricks'
            elif d > 21.2:
                st = bkit.pick(ROAD, x, 0, z, 3, 0.55)
            else:
                # Rings of paving, worn a little toward the rim.
                ring = int(d) % 4 == 0
                st = 'polished_andesite' if ring else bkit.pick(['stone_bricks', 'stone_bricks', 'stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks'],
                                                                 x, 0, z, 5, 0.1 + 0.35 * d / 22)
            g.set(x, G - 1, z, st)
    # Benches facing the dais, and four trees in round planters.
    for i in range(8):
        a = i / 8 * 2 * math.pi + math.pi / 8
        bx, bz = C + math.cos(a) * 19.5, C + math.sin(a) * 19.5
        face = _facing_toward(bx, bz, C, C)
        for k in (-1, 0, 1):
            ox, oz = _along(face)
            x, z = int(round(bx + ox * k)), int(round(bz + oz * k))
            kit.stairs(g, x, G, z, 'spruce', face)
    for a in (math.pi / 4, 3 * math.pi / 4, 5 * math.pi / 4, 7 * math.pi / 4):
        tx, tz = int(round(C + math.cos(a) * 13)), int(round(C + math.sin(a) * 13))
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if abs(dx) == 2 or abs(dz) == 2:
                    if abs(dx) + abs(dz) < 4:
                        g.set(tx + dx, G, tz + dz, 'stone_brick_wall')
                else:
                    g.set(tx + dx, G - 1, tz + dz, 'grass_block')
                    if hash3(tx + dx, 2, tz + dz, 9) < 0.5:
                        g.set(tx + dx, G, tz + dz, ['poppy', 'cornflower', 'oxeye_daisy', 'azure_bluet'][int(hash3(tx + dx, 3, tz + dz, 9) * 4) % 4])
        bkit.tree(g, tx, G, tz, 'oak', 6, 3, seed=int(a * 10))
    # A well to the south-west of the dais.
    well(g, C - 9, C + 8)


def _facing_toward(x, z, tx, tz):
    dx, dz = tx - x, tz - z
    if abs(dx) > abs(dz):
        return 'east' if dx > 0 else 'west'
    return 'south' if dz > 0 else 'north'


def _along(face):
    return (1, 0) if face in ('north', 'south') else (0, 1)


def well(g, x, z):
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if dx == 0 and dz == 0:
                g.fill(x, G - 6, z, x, G - 1, z, 'water')
            else:
                g.set(x + dx, G, z + dz, 'mossy_cobblestone' if hash3(x + dx, 0, z + dz, 4) < 0.4 else 'cobblestone')
    for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        g.set(x + dx, G + 1, z + dz, 'spruce_fence')
        g.set(x + dx, G + 2, z + dz, 'spruce_fence')
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            g.set(x + dx, G + 3, z + dz, 'spruce_slab[type=bottom]')
    g.set(x, G + 2, z, 'iron_chain[axis=y]')
    g.set(x, G + 1, z, 'iron_chain[axis=y]')


# ---------------------------------------------------------------- the Great Waystone

def great_waystone(g):
    """A three-tier dais with the waystone at the front of its top, and behind it a carved monolith
    ringed by four lantern pillars, crowned with a cage of light that can be seen from the gate."""
    for tier, (r, h) in enumerate(((8.5, 1), (6.5, 2), (4.5, 3))):
        for x in range(C - 10, C + 11):
            for z in range(C - 10, C + 11):
                d = math.hypot(x + 0.5 - C, z + 0.5 - C)
                if d <= r:
                    mat = 'polished_deepslate' if d > r - 1 else ('deepslate_tiles' if (x + z) % 2 else 'chiseled_deepslate') if tier == 2 else 'deepslate_bricks'
                    g.set(x, G - 1 + h, z, mat)
                    for y in range(G, G - 1 + h):
                        g.set(x, y, z, 'polished_deepslate')
    # Steps on the four sides, so it can be climbed from every street.
    for face, (dx, dz) in kit.FACE.items():
        for k, (r, h) in enumerate(((9, 0), (7, 1), (5, 2))):
            for w in (-1, 0, 1):
                ox, oz = _along(face)
                x = C + dx * r + ox * w - (1 if dx > 0 else 0) * 0
                z = C + dz * r + oz * w
                kit.stairs(g, x, G + h, z, 'polished_deepslate', kit.OPP[face])
    top = G + 3
    # The waystone itself at the south edge of the top tier, where Isolde points.
    g.set(C, top, C + 3, 'afterworld:waystone[natural=true]')
    g.mark(C, top, C + 3, 'waystone')
    # The monolith: a tapering carved column behind the waystone.
    for y in range(top, top + 22):
        k = (y - top) / 22
        r = 1.6 - 0.9 * k
        for x in range(C - 2, C + 3):
            for z in range(C - 3, C + 2):
                if math.hypot(x + 0.5 - C - 0.0, z + 0.5 - (C - 0.5)) <= r:
                    band = (y - top) % 6 == 5
                    g.set(x, y, z, 'chiseled_deepslate' if band else ('polished_blackstone_bricks' if hash3(x, y, z, 21) < 0.3 else 'polished_deepslate'))
        if (y - top) % 6 == 2:
            for (x, z) in ((C, C - 2), (C, C + 1), (C - 1, C - 1), (C + 1, C - 1)):
                if g.get(x, y, z) and 'deepslate' in g.get(x, y, z):
                    g.set(x, y, z, 'gilded_blackstone')
    # The crown: a cage of chains and lanterns round a lit core.
    cy = top + 22
    g.set(C, cy, C - 1, 'sea_lantern')
    g.set(C, cy + 1, C - 1, 'sea_lantern')
    for (x, z) in ((C - 1, C - 2), (C + 1, C - 2), (C - 1, C), (C + 1, C)):
        g.set(x, cy, z, 'polished_blackstone_wall')
        g.set(x, cy + 1, z, 'polished_blackstone_wall')
        g.set(x, cy + 2, z, 'gold_block')
    g.set(C, cy + 2, C - 1, 'gold_block')
    g.set(C, cy + 3, C - 1, 'lightning_rod[facing=up]')
    # Four lantern pillars at the corners of the top tier.
    for (x, z) in ((C - 3, C - 4), (C + 3, C - 4), (C - 3, C + 2), (C + 3, C + 2)):
        for y in range(top, top + 6):
            g.set(x, y, z, 'polished_deepslate_wall' if y < top + 5 else 'chiseled_polished_blackstone')
        g.set(x, top + 6, z, 'lantern[hanging=false]')
    # Braziers at the foot of the steps: a fire on one side of each stair, votive candles on the other.
    for face, (dx, dz) in kit.FACE.items():
        for s in (-1, 1):
            ox, oz = _along(face)
            x, z = C + dx * 10 + ox * 3 * s, C + dz * 10 + oz * 3 * s
            g.set(x, G, z, 'polished_deepslate')
            g.set(x, G + 1, z, 'campfire[lit=true,signal_fire=false,facing=north]' if s > 0 else 'candle[candles=4,lit=true,waterlogged=false]')
    kit.keeper(g, 'isolde', C + 2, C + 7, G, G + 1, 'south', 3)


# ---------------------------------------------------------------- the wall

def wall_points():
    pts = []
    n = 12
    for i in range(n):
        a = i / n * 2 * math.pi + math.pi / 12
        r = R_WALL + 1.5 * math.sin(i * 2.3)
        pts.append((C + math.cos(a) * r, C + math.sin(a) * r))
    return pts


def walls(g):
    pts = wall_points()
    wallpal = ['stone_bricks', 'stone_bricks', 'andesite', 'cracked_stone_bricks', 'stone_bricks', 'cobblestone', 'mossy_stone_bricks', 'mossy_cobblestone']
    bkit.curtain(g, pts, G, 9, 3, wallpal, seed=31)
    for i, (x, z) in enumerate(pts):
        # The gatehouse and the postern have towers of their own.
        if math.hypot(x - C, z - (C + R_WALL)) < 18 or math.hypot(x - (C + R_WALL), z - C) < 10:
            continue
        bkit.round_tower(g, int(round(x)), int(round(z)), G, 4, 14, wallpal, roof_mat='deepslate_tile', seed=40 + i)
    gatehouse(g)
    postern(g)


def gatehouse(g):
    """The south gate: two great towers, an arched passage with a raised portcullis, banners."""
    gz = C + R_WALL
    wallpal = ['stone_bricks', 'stone_bricks', 'polished_andesite', 'cracked_stone_bricks', 'stone_bricks', 'mossy_stone_bricks']
    # Clear the wall where the passage runs.
    for x in range(C - 3, C + 4):
        for z in range(gz - 5, gz + 5):
            for y in range(G, G + 7):
                g.set(x, y, z, 'air')
    # The gate block over the passage.
    for x in range(C - 6, C + 7):
        for z in range(gz - 3, gz + 4):
            for y in range(G - 3, G + 13):
                inside = abs(x - C) <= 3 and y < G + 6
                arch = abs(x - C) == 3 and y == G + 5
                if inside or arch:
                    continue
                pal(g, x, y, z, wallpal, 0.2 + 0.4 * max(0, 1 - (y - G + 3) / 6), 51)
    for x in range(C - 2, C + 3):
        for z in range(gz - 3, gz + 4):
            g.set(x, G + 5, z, 'air') if abs(x - C) < 2 else None
    for x in range(C - 3, C + 4):
        g.set(x, G + 6, gz - 3, 'stone_brick_stairs[facing=north,half=top,shape=straight]')
        g.set(x, G + 6, gz + 3, 'stone_brick_stairs[facing=south,half=top,shape=straight]')
    # The raised portcullis.
    for x in range(C - 2, C + 3):
        g.set(x, G + 5, gz, 'iron_bars')
    for x in range(C - 6, C + 7, 2):
        g.set(x, G + 13, gz + 3, 'stone_brick_wall')
        g.set(x, G + 13, gz - 3, 'stone_brick_wall')
    for s in (-1, 1):
        bkit.round_tower(g, C + s * 8, gz, G, 5, 17, wallpal, roof_mat='deepslate_tile', seed=60 + s, door_face='north', cone=False)
        for y in (G + 7, G + 8):
            g.set(C + s * 4, y, gz + 4, f'{"blue" if s < 0 else "light_blue"}_wall_banner[facing=south]')
    # Braziers outside the gate and lanterns inside.
    for s in (-1, 1):
        g.set(C + s * 4, G, gz + 6, 'polished_andesite')
        g.set(C + s * 4, G + 1, gz + 6, 'campfire[lit=true,signal_fire=false,facing=south]')
        bkit.lamp_post(g, C + s * 5, G, gz - 6)
    g.mark(C, G, gz - 8, 'spawn')


def postern(g):
    """A small east gate onto the stables and the fields."""
    gx = C + R_WALL
    for z in range(C - 1, C + 2):
        for x in range(gx - 3, gx + 4):
            for y in range(G, G + 4):
                g.set(x, y, z, 'air')
        g.set(gx - 2, G + 4, z, 'stone_brick_stairs[facing=west,half=top,shape=straight]')
        g.set(gx + 2, G + 4, z, 'stone_brick_stairs[facing=east,half=top,shape=straight]')
    bkit.lamp_post(g, gx - 4, G, C - 2)


# ---------------------------------------------------------------- districts

def north(g):
    """The Warden Hall with a bell tower, its court and banners."""
    x0, z0, x1, z1 = C - 14, C - 58, C + 14, C - 40
    info = bkit.building(g, x0, z0, x1, z1, G, bkit.HALL, storeys=2, axis='x', door_side='south', kind='hall', seed=101, bay=4, double_door=True,
                         chimney_at='east', overhang=1)
    # The bell tower at the west end.
    tx, tz = x0 - 4, (z0 + z1) // 2
    for y in range(G - 3, G + 24):
        for x in range(tx - 3, tx + 4):
            for z in range(tz - 3, tz + 4):
                edge = abs(x - tx) == 3 or abs(z - tz) == 3
                if edge:
                    pal(g, x, y, z, bkit.HALL.wall, 0.2 + 0.4 * max(0, 1 - (y - G + 3) / 6), 103)
                else:
                    g.set(x, y, z, 'air')
    for y in range(G + 17, G + 22):
        for (x, z) in ((tx, tz - 3), (tx, tz + 3), (tx - 3, tz), (tx + 3, tz)):
            g.set(x, y, z, 'air')
    g.set(tx, G + 21, tz, 'bell[attachment=ceiling,facing=north,powered=false]')
    g.set(tx, G + 22, tz, 'dark_oak_planks')
    kit.hip(g, tx - 3, tz - 3, tx + 3, tz + 3, G + 24, 'deepslate_tile', overhang=1)
    g.set(tx, G + 29, tz, 'lightning_rod[facing=up]')
    for y in range(G, G + 21):
        g.set(tx + 2, y, tz, 'ladder[facing=west]')
    g.set(tx + 3, G, tz, 'air')
    g.set(tx + 3, G + 1, tz, 'air')
    # The court: paving, banners on poles, two planters.
    for x in range(x0, x1 + 1):
        for z in range(z1 + 2, C - 24):
            pal(g, x, G - 1, z, ['stone_bricks', 'polished_andesite', 'stone_bricks', 'cracked_stone_bricks', 'mossy_stone_bricks'], 0.3, 105)
    for s in (-1, 1):
        for k in range(3):
            px = C + s * (6 + k * 4)
            for y in range(G, G + 6):
                g.set(px, y, z1 + 4, 'dark_oak_fence')
            g.set(px, G + 5, z1 + 5, 'blue_wall_banner[facing=south]')
            g.set(px, G + 4, z1 + 5, 'blue_wall_banner[facing=south]')
    return info


def west(g):
    """Craft row: Marta's forge, a workshop and a storehouse along the west street."""
    # The forge: open-fronted, stone, a great chimney.
    x0, z0, x1, z1 = C - 46, C - 16, C - 32, C - 5
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g.set(x, G - 1, z, 'stone_bricks' if hash3(x, 0, z, 7) < 0.6 else 'cobblestone')
    bkit.footing(g, x0, z0, x1, z1, G, 3, bkit.STONE_FOUND, 201)
    for y in range(G, G + 5):
        for x in range(x0, x1 + 1):
            pal(g, x, y, z0, bkit.HALL.wall, 0.3, 203)
        for z in range(z0, z1 + 1):
            pal(g, x0, y, z, bkit.HALL.wall, 0.3, 203)
        for (x, z) in ((x1, z0), (x1, z1), (x0 + 5, z1), (x0 + 10, z1)):
            bkit._log(g, x, y, z, 'dark_oak_log', 'y')
    for x in range(x0, x1 + 1):
        bkit._log(g, x, G + 5, z1, 'dark_oak_log', 'x')
    bkit.roof(g, x0, z0, x1, z1, G + 6, bkit.TIMBER_SLATE, axis='x', overhang=2, rise=1, gable_fill=bkit.TIMBER_SLATE.plaster)
    # Hearth and chimney in the north-west corner.
    for x in range(x0 + 1, x0 + 4):
        for z in range(z0 + 1, z0 + 4):
            for y in range(G, G + 3):
                g.set(x, y, z, 'bricks')
    # The fire glows in the hearth's mouth behind a grate: the lava is held in on every side.
    g.set(x0 + 2, G + 1, z0 + 2, 'lava')
    g.set(x0 + 2, G + 1, z0 + 3, 'iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]')
    g.set(x0 + 2, G, z0 + 3, 'blast_furnace[facing=south,lit=true]')
    for y in range(G + 3, G + 20):
        for x in range(x0 + 1, x0 + 4):
            for z in range(z0 + 1, z0 + 4):
                g.set(x, y, z, 'bricks' if (x in (x0 + 1, x0 + 3) or z in (z0 + 1, z0 + 3)) else 'air')
    g.set(x0 + 2, G + 20, z0 + 2, 'campfire[lit=true,signal_fire=false,facing=north]')
    g.set(x0 + 6, G, z0 + 5, 'anvil[facing=east]')
    g.set(x0 + 8, G, z0 + 2, 'grindstone[face=floor,facing=south]')
    g.set(x0 + 9, G, z0 + 2, 'smithing_table')
    g.set(x0 + 11, G, z0 + 2, 'cauldron')
    g.set(x0 + 12, G, z0 + 2, 'barrel[facing=up]')
    for x in range(x0 + 4, x1):
        g.set(x, G + 3, z0 + 1, 'iron_chain[axis=y]') if x % 3 == 0 else None
    # Quench tubs outside the open front (cauldrons: no loose water to run into the street).
    for x in range(x0 + 2, x0 + 5):
        g.set(x, G, z1 + 2, 'water_cauldron[level=3]')
    kit.keeper(g, 'marta', x0 + 7, z0 + 6, G, G + 1, 'south', 3)
    # The workshop and the storehouse.
    bkit.building(g, C - 48, C + 6, C - 38, C + 15, G, bkit.TIMBER, storeys=2, axis='z', door_side='east', kind='home', seed=211)
    bkit.building(g, C - 34, C + 8, C - 24, C + 17, G, bkit.TIMBER_SLATE, storeys=2, axis='z', door_side='north', door_at=C - 29, kind='shop', seed=213)


def east(g):
    """The market: Bram's provisions, stalls along the street, the Warden's Board."""
    info = bkit.building(g, C + 30, C - 20, C + 44, C - 8, G, bkit.TIMBER_RED, storeys=2, axis='x', door_side='south', kind='shop', seed=301)
    kit.keeper(g, 'bram', C + 37, C - 12, G, G + 1, 'south', 3)
    # Dyes a market town could actually make: madder, woad, weld, walnut, verdigris.
    colours = ['red', 'blue', 'yellow', 'brown', 'green', 'orange']
    for i, x in enumerate(range(C + 28, C + 58, 7)):
        bkit.stall(g, x, G, C + 6, 'north', colours[i % len(colours)], seed=310 + i)
    for i, x in enumerate(range(C + 31, C + 55, 8)):
        bkit.stall(g, x, G, C - 5, 'south', colours[(i + 3) % len(colours)], seed=320 + i)
    notice_board(g, C + 21, C + 3)
    # Houses behind the market.
    bkit.building(g, C + 48, C - 22, C + 58, C - 12, G, bkit.TIMBER, storeys=2, axis='z', door_side='west', kind='home', seed=331)
    bkit.building(g, C + 28, C + 18, C + 38, C + 28, G, bkit.TIMBER_SLATE, storeys=2, axis='x', door_side='north', kind='home', seed=333)
    return info


def notice_board(g, bx, z0):
    """The Warden's Board, where the market meets the plaza and facing it: a great notice board you
    can see from the waystone. Nine panels of notices (each one opens the board) on a plank wall
    between two tall posts, under a little gabled roof with a bell on its ridge, lanterns at its
    eaves and the Wardens' banners on its posts; barrels of the day's post at its feet."""
    z1 = z0 + 6
    for z in range(z0 - 1, z1 + 2):
        for x in range(bx - 2, bx + 3):
            for y in range(G, G + 9):
                g.set(x, y, z, 'air')
    for x in range(bx - 2, bx + 2):
        for z in range(z0, z1 + 1):
            g.set(x, G - 1, z, 'stone_bricks' if (x + z) % 3 else 'polished_andesite')
    for z in (z0 + 1, z1 - 1):
        for y in range(G, G + 5):
            kit.log(g, bx, y, z, 'dark_oak_log')
        g.set(bx - 1, G + 3, z, f'{"red" if z == z0 + 1 else "blue"}_wall_banner[facing=west]')
    for z in range(z0 + 2, z1 - 1):
        g.set(bx, G, z, 'spruce_slab[type=top]')
        for y in range(G + 1, G + 4):
            g.set(bx + 1, y, z, 'spruce_planks')
            g.set(bx, y, z, 'afterworld:wardens_board[facing=west,panel=true]')
        g.set(bx + 1, G, z, 'spruce_planks')
    for z in range(z0 + 1, z1):
        kit.log(g, bx, G + 4, z, 'dark_oak_log', 'z')
        g.set(bx + 1, G + 4, z, 'dark_oak_planks')
    # The roof: eaves out over the panels, a ridge of planks, the bell on top.
    for z in range(z0, z1 + 1):
        kit.stairs(g, bx - 1, G + 5, z, 'dark_oak', 'east')
        kit.stairs(g, bx + 2, G + 5, z, 'dark_oak', 'west')
        g.set(bx, G + 5, z, 'dark_oak_planks')
        g.set(bx + 1, G + 5, z, 'dark_oak_planks')
        g.set(bx, G + 6, z, 'dark_oak_slab[type=bottom]')
        g.set(bx + 1, G + 6, z, 'dark_oak_slab[type=bottom]')
    g.set(bx, G + 6, z0 + 3, 'dark_oak_planks')
    g.set(bx, G + 7, z0 + 3, 'bell[attachment=floor,facing=west,powered=false]')
    for z in (z0, z1):
        kit.hang_lantern(g, bx - 1, G + 4, z, 1)
    for z in (z0, z1):
        g.set(bx, G, z, 'barrel[facing=up]')
    g.set(bx - 1, G, z0, 'barrel[facing=west]')


def south_west(g):
    """The Last Lantern inn and its stable yard."""
    info = bkit.building(g, C - 30, C + 26, C - 14, C + 40, G, bkit.TIMBER_RED, storeys=3, axis='z', door_side='east', kind='inn', seed=401)
    # Its sign-lantern on a bracket over the street.
    for k in range(3):
        g.set(C - 13 + k, G + 5, C + 30, 'dark_oak_fence')
    g.set(C - 11, G + 4, C + 30, 'lantern[hanging=true]')
    # The stable yard behind it.
    for x in range(C - 46, C - 33):
        for z in range(C + 26, C + 40):
            if x in (C - 46, C - 34) or z in (C + 26, C + 39):
                g.set(x, G, z, 'spruce_fence')
            elif hash3(x, 0, z, 9) < 0.3:
                g.set(x, G - 1, z, 'coarse_dirt')
    for z in range(C + 27, C + 39, 4):
        g.set(C - 45, G, z, 'hay_block[axis=x]')
        g.set(C - 45, G + 1, z, 'hay_block[axis=y]')
    bkit.building(g, C - 30, C + 46, C - 20, C + 54, G, bkit.TIMBER, storeys=2, axis='x', door_side='north', kind='home', seed=411)
    return info


def south_east(g):
    """The barracks, the training yard and the old camp's tents."""
    bkit.building(g, C + 14, C + 34, C + 36, C + 44, G, bkit.HALL, storeys=1, axis='x', door_side='west', kind='home', seed=501, bay=4)
    # Training yard: dummies, targets, a weapon rack.
    for x in range(C + 14, C + 37):
        for z in range(C + 48, C + 58):
            g.set(x, G - 1, z, 'coarse_dirt' if hash3(x, 0, z, 5) < 0.5 else 'dirt_path')
    for i, x in enumerate(range(C + 17, C + 34, 5)):
        g.set(x, G, C + 52, 'spruce_fence')
        g.set(x, G + 1, C + 52, 'hay_block[axis=y]')
        g.set(x, G + 2, C + 52, 'carved_pumpkin[facing=north]')
        g.set(x, G, C + 56, 'target')
    # Tents of the old camp.
    for i, (tx, tz) in enumerate(((C + 44, C + 30), (C + 50, C + 38), (C + 44, C + 46))):
        tent(g, tx, tz, ['white', 'light_gray', 'cyan'][i])


def tent(g, x, z, colour):
    for k in range(4):
        for dz in range(-2, 3):
            for s in (-1, 1):
                g.set(x + s * (3 - k), G + k, z + dz, f'{colour}_wool')
    for dz in range(-2, 3):
        g.set(x, G + 3, z + dz, f'{colour}_wool')
    for dz in (-3, 3):
        g.set(x, G, z + dz, 'spruce_fence')
        g.set(x, G + 1, z + dz, 'spruce_fence')
        g.set(x, G + 2, z + dz, 'spruce_fence')
    kit.bed(g, x - 1, G, z - 1, colour, 'north')


def north_west(g):
    """Wren's map tower and the Cartographers' house."""
    tx, tz = C - 38, C - 34
    bkit.round_tower(g, tx, tz, G, 5, 24, bkit.HALL.wall, roof_mat='deepslate_tile', seed=601, door_face='south')
    # A wooden gallery round the top room.
    for a in range(0, 360, 10):
        x = tx + int(round(math.cos(math.radians(a)) * 7))
        z = tz + int(round(math.sin(math.radians(a)) * 7))
        g.set(x, G + 18, z, 'spruce_planks')
        g.set(x, G + 19, z, 'spruce_fence')
    for a in range(0, 360, 45):
        x = tx + int(round(math.cos(math.radians(a)) * 6))
        z = tz + int(round(math.sin(math.radians(a)) * 6))
        kit.stairs(g, x, G + 17, z, 'spruce', _facing_toward(x, z, tx, tz), 'top')
    g.set(tx, G, tz, 'cartography_table')
    g.set(tx + 1, G, tz - 2, 'lectern[facing=south]')
    kit.keeper(g, 'wren', tx, tz + 8, G, G + 1, 'south', 3)
    bkit.building(g, C - 54, C - 26, C - 45, C - 16, G, bkit.TIMBER_SLATE, storeys=2, axis='z', door_side='east', kind='maps', seed=611)


def north_east(g):
    """A chapel with a rose window, and its quiet yard."""
    x0, z0, x1, z1 = C + 26, C - 50, C + 38, C - 34
    bkit.building(g, x0, z0, x1, z1, G, bkit.HALL, storeys=1, axis='z', door_side='south', kind='chapel', seed=701, bay=4, dormers=False,
                  chimney_at=None)
    # A small spire over the door end.
    bkit.round_tower(g, (x0 + x1) // 2, z1 - 1, G + 10, 2, 6, bkit.HALL.wall, roof_mat='deepslate_tile', seed=703)
    for i in range(6):
        x = x1 + 4 + (i % 3) * 3
        z = z0 + 2 + (i // 3) * 5
        g.set(x, G, z, 'stone_brick_wall')
        g.set(x, G + 1, z, 'mossy_stone_brick_wall')
        g.set(x, G - 1, z + 1, 'podzol')
        g.set(x, G, z + 1, 'white_tulip' if i % 2 else 'lily_of_the_valley')


def houses(g):
    """Homes filling the quarters between the districts."""
    spots = [
        (C - 30, C - 30, C - 20, C - 22, 'south', 'x', bkit.TIMBER),
        (C + 46, C + 8, C + 56, C + 16, 'west', 'z', bkit.TIMBER_RED),
        (C - 12, C + 44, C - 4, C + 54, 'east', 'z', bkit.TIMBER_SLATE),
        (C + 6, C + 46, C + 12, C + 56, 'west', 'z', bkit.TIMBER),
        (C - 56, C - 6, C - 48, C + 2, 'east', 'z', bkit.TIMBER_RED),
        (C + 14, C - 34, C + 22, C - 26, 'south', 'x', bkit.TIMBER),
        (C - 14, C - 34, C - 6, C - 24, 'east', 'z', bkit.TIMBER_RED),
        (C + 44, C - 34, C + 54, C - 26, 'south', 'x', bkit.TIMBER_SLATE),
        (C - 58, C + 30, C - 50, C + 40, 'east', 'z', bkit.TIMBER),
        (C + 18, C + 18, C + 26, C + 28, 'west', 'z', bkit.TIMBER_RED),
        (C - 6, C - 64, C + 4, C - 60, 'south', 'x', bkit.TIMBER),
    ]
    for i, (x0, z0, x1, z1, door, axis, style) in enumerate(spots):
        bkit.building(g, x0, z0, x1, z1, G, style, storeys=2, axis=axis, door_side=door, kind='home', seed=800 + i)


def gardens(g):
    """Kitchen gardens behind the houses: tilled rows of crops with a water channel."""
    for (x0, z0, x1, z1) in ((C - 22, C - 22, C - 14, C - 16), (C + 40, C + 16, C + 46, C + 26), (C - 46, C + 44, C - 36, C + 52),
                             (C + 2, C + 26, C + 10, C + 32)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    g.set(x, G, z, 'spruce_fence' if not (x == x0 and z == (z0 + z1) // 2) else 'spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]')
                elif x == (x0 + x1) // 2:
                    g.set(x, G - 1, z, 'water')
                else:
                    g.set(x, G - 1, z, 'farmland[moisture=7]')
                    crop = ['wheat[age=7]', 'carrots[age=7]', 'potatoes[age=7]', 'beetroots[age=3]'][(x * 7 + z0) % 4]
                    g.set(x, G, z, crop)


def greenery(g):
    """Trees, gardens, a pond and flowers where nothing is built."""
    trees = [(C - 22, C - 12), (C + 22, C - 30), (C - 52, C + 22), (C + 52, C + 26), (C - 8, C + 60), (C + 58, C - 4), (C - 60, C - 18),
             (C + 6, C - 62), (C - 42, C - 50), (C + 46, C - 44), (C - 24, C + 58), (C + 40, C + 56)]
    for i, (x, z) in enumerate(trees):
        if g.get(x, G, z) in (None, 'minecraft:air'):
            bkit.tree(g, x, G, z, ['oak', 'birch', 'oak', 'dark_oak'][i % 4], 6 + i % 3, 3, seed=900 + i)
    # A pond with lilies in the south-east quarter.
    px, pz = C + 50, C + 48
    for x in range(px - 6, px + 7):
        for z in range(pz - 4, pz + 5):
            d = math.hypot((x - px) / 6, (z - pz) / 4)
            if d < 1:
                g.set(x, G - 1, z, 'water')
                g.set(x, G - 2, z, 'water' if d < 0.6 else 'clay')
                if hash3(x, 0, z, 3) < 0.08:
                    g.set(x, G, z, 'lily_pad')
            elif d < 1.25 and hash3(x, 1, z, 4) < 0.5:
                g.set(x, G, z, 'short_grass' if hash3(x, 2, z, 5) < 0.6 else 'blue_orchid')
    # Flowers and grass on open ground.
    for x in range(W):
        for z in range(D):
            if g.get(x, G - 1, z) == 'minecraft:grass_block' and g.get(x, G, z) in ('minecraft:air',):
                r = hash3(x, 5, z, 11)
                if r < 0.06:
                    g.set(x, G, z, 'short_grass')
                elif r < 0.075:
                    g.set(x, G, z, ['dandelion', 'poppy', 'cornflower', 'oxeye_daisy'][int(r * 1000) % 4])


def lamps(g):
    """Lamp posts along every street."""
    for z in range(C + 24, C + R_WALL - 6, 9):
        bkit.lamp_post(g, C - 5, G, z)
        bkit.lamp_post(g, C + 5, G, z)
    for x in list(range(C - 60, C - 24, 9)) + list(range(C + 26, C + 62, 9)):
        bkit.lamp_post(g, x, G, C - 4 if x < C else C + 12)
    for z in range(C - 36, C - 22, 7):
        bkit.lamp_post(g, C - 4, G, z)
        bkit.lamp_post(g, C + 4, G, z)


def build():
    g = Grid(W, H, D)
    ground(g)
    street(g, C, C + R_WALL + 6, C, C + 18, 3, seed=1)                 # the main street from the gate
    street(g, C - R_WALL + 2, C, C - 18, C, 2, seed=2)                 # west street
    street(g, C + 18, C, C + R_WALL + 4, C, 3, seed=3)                 # east street (the market)
    street(g, C, C - 18, C, C - 38, 2, seed=4)                         # north to the hall
    street(g, C - 36, C - 18, C - 36, C - 4, 1, LANE, seed=5)          # lanes
    street(g, C - 22, C + 33, C - 4, C + 33, 1, LANE, seed=6)
    street(g, C + 25, C + 39, C + 8, C + 39, 1, LANE, seed=7)
    street(g, C - 38, C - 26, C - 18, C - 26, 1, LANE, seed=8)
    street(g, C + 22, C - 30, C + 32, C - 42, 1, LANE, seed=9)
    plaza(g)
    great_waystone(g)
    north(g)
    west(g)
    east(g)
    south_west(g)
    south_east(g)
    north_west(g)
    north_east(g)
    houses(g)
    walls(g)
    gardens(g)
    greenery(g)
    lamps(g)
    kit.connect(g)
    return g


if __name__ == '__main__':
    import sys, os, time
    t = time.time()
    g = build()
    print('built in', round(time.time() - t, 1), 's;', g.count(), 'blocks')

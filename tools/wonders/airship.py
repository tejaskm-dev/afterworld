"""The Wayfarer's Airship: a Guild sky-barge riding at anchor. A striped balloon on spruce ribs
with tail fins, a boat-shaped gondola slung beneath on rigging, a cabin, rails, lanterns and a
stern propeller; chains run down to a timber mooring tower and stakes in the ground."""
import math
from vox import Grid, hash3
import kit

W, H, D = 64, 64, 32
G = 3
CZ = 16            # the ship's centre line (z)
HULL_Y = G + 24    # keel height
BX0, BX1 = 10, 54  # balloon length


def ground(g):
    for x in range(W):
        for z in range(D):
            g.set(x, G - 1, z, 'grass_block')
            g.fill(x, 0, z, x, G - 2, z, 'dirt')


def balloon(g):
    cx, cy = (BX0 + BX1) / 2, HULL_Y + 15
    a, r = (BX1 - BX0) / 2, 8.2
    for x in range(BX0 - 1, BX1 + 2):
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for z in range(CZ - 10, CZ + 10):
                nx = (x + 0.5 - cx) / a
                rr = math.hypot(y + 0.5 - cy, z + 0.5 - CZ) / r
                # A blunt bow and a pointed stern: the radius shrinks faster aft.
                prof = math.sqrt(max(0.0, 1 - nx * nx)) * (1 - 0.15 * max(0, nx))
                if rr <= prof and rr > prof - 0.17:
                    band = int((x - BX0) / 3) % 4
                    st = 'white_wool'
                    if band == 1:
                        st = 'cyan_wool'
                    elif band == 3 and y > cy:
                        st = 'light_gray_wool'
                    # Spruce ribs every six blocks and a keel line.
                    if (x - BX0) % 6 == 0 or abs(z + 0.5 - CZ) < 0.6 and y > cy:
                        st = 'stripped_spruce_wood[axis=y]'
                    g.set(x, y, z, st)
    # Netting: a criss-cross of chains over the top and down the sides.
    for x in range(BX0 + 3, BX1 - 2, 4):
        for z in (CZ - 9, CZ + 8):
            g.set(x, int(cy), z, 'iron_chain[axis=y]')
    # Tail fins: vertical and horizontal, planks edged in teal.
    for k in range(7):
        x = BX0 - 1 + k
        h = 7 - k
        for j in range(h):
            g.set(x, int(cy) + 6 + j, CZ - 1, 'spruce_planks' if j < h - 1 else 'cyan_wool')
            g.set(x, int(cy) + 6 + j, CZ, 'spruce_planks' if j < h - 1 else 'cyan_wool')
        for side in (-1, 1):
            for j in range(h):
                g.set(x, int(cy), CZ + side * (8 + j), 'spruce_planks' if j < h - 1 else 'cyan_wool')
    # A bow cap and a lantern at the nose.
    g.set(BX1 + 1, int(cy), CZ, 'lightning_rod[facing=east]')
    g.set(BX1 + 1, int(cy) - 1, CZ, 'lantern[hanging=true]')
    return int(cy)


def hull(g):
    """A boat: dark oak keel, spruce sides curving in, a planked deck with rails."""
    x0, x1 = 16, 48
    for x in range(x0, x1 + 1):
        t = (x - x0) / (x1 - x0)
        beam = 3.6 * math.sin(math.pi * min(1, t * 1.15)) + 0.4
        for y in range(HULL_Y, HULL_Y + 5):
            k = (y - HULL_Y) / 4
            half = beam * (0.55 + 0.45 * k)
            for z in range(CZ - 6, CZ + 6):
                dz = abs(z + 0.5 - CZ)
                if dz <= half + 0.3:
                    edge = dz > half - 0.8 or y == HULL_Y
                    if edge:
                        st = 'dark_oak_planks' if y <= HULL_Y + 1 else 'spruce_planks'
                        if y == HULL_Y + 3:
                            st = 'stripped_dark_oak_log[axis=x]'
                        g.set(x, y, z, st)
                    elif y == HULL_Y + 4:
                        g.set(x, y, z, 'spruce_planks' if (x % 2) else 'stripped_spruce_wood[axis=x]')
                    else:
                        g.set(x, y, z, 'air')
        # Rails along the deck edge.
        for side in (-1, 1):
            half = beam
            z = int(round(CZ - 0.5 + side * (half - 0.2)))
            g.set(x, HULL_Y + 5, z, 'spruce_fence')
    # Prow: a carved figurehead post.
    g.set(x1 + 1, HULL_Y + 3, CZ - 1, 'stripped_dark_oak_log[axis=x]')
    g.set(x1 + 2, HULL_Y + 4, CZ - 1, 'stripped_dark_oak_log[axis=x]')
    g.set(x1 + 3, HULL_Y + 5, CZ - 1, 'gold_block')
    # Cabin at the stern half: dark oak frame, glass, a pitched roof.
    cx0, cx1 = x0 + 4, x0 + 13
    for x in range(cx0, cx1 + 1):
        for z in range(CZ - 3, CZ + 3):
            for y in range(HULL_Y + 5, HULL_Y + 9):
                wall = x in (cx0, cx1) or z in (CZ - 3, CZ + 2)
                corner = x in (cx0, cx1) and z in (CZ - 3, CZ + 2)
                if corner:
                    g.set(x, y, z, 'stripped_dark_oak_log[axis=y]')
                elif wall:
                    g.set(x, y, z, 'glass_pane' if y in (HULL_Y + 6, HULL_Y + 7) and x % 2 == 0 else 'spruce_planks')
                else:
                    g.set(x, y, z, 'air')
    kit.gable(g, cx0, CZ - 3, cx1, CZ + 2, HULL_Y + 9, 'dark_oak', axis='x', overhang=1, gable_fill='spruce_planks')
    g.set(cx0 + 2, HULL_Y + 5, CZ - 1, 'cartography_table')
    g.chest(cx0 + 3, HULL_Y + 5, CZ - 2, 'south', 'afterworld:chests/brassmere_airship')
    kit.hang_lantern(g, cx0 + 5, HULL_Y + 8, CZ - 1, 0)
    g.set((cx0 + cx1) // 2, HULL_Y + 5, CZ + 2, 'air')
    g.set((cx0 + cx1) // 2, HULL_Y + 6, CZ + 2, 'air')
    # Deck furniture: a ship's wheel post, barrels, a funnel with smoke.
    g.set(cx1 + 2, HULL_Y + 5, CZ - 1, 'spruce_fence')
    g.set(cx1 + 2, HULL_Y + 6, CZ - 1, 'oak_trapdoor[facing=east,half=bottom,open=true]')
    for i in range(3):
        g.set(cx1 + 6 + i, HULL_Y + 5, CZ - 3 + (i % 2), 'barrel[facing=up]')
    for y in range(HULL_Y + 5, HULL_Y + 9):
        g.set(cx1 + 10, y, CZ - 1, 'polished_deepslate')
    g.set(cx1 + 10, HULL_Y + 9, CZ - 1, 'campfire[lit=true,signal_fire=true]')
    for (x, z) in ((cx1 + 4, CZ + 1), (x1 - 4, CZ - 2), (x1 - 4, CZ + 1)):
        g.set(x, HULL_Y + 5, z, 'lantern[hanging=false]')
    # Stern propeller: hub and four spruce blades.
    px = x0 - 1
    py = HULL_Y + 3
    g.set(px, py, CZ - 1, 'oak_log[axis=x]')
    g.set(px - 1, py, CZ - 1, 'iron_block')
    for (dy, dz) in ((1, 0), (2, 0), (-1, 0), (-2, 0), (0, 1), (0, 2), (0, -1), (0, -2)):
        g.set(px - 1, py + dy, CZ - 1 + dz, 'spruce_planks' if abs(dy) + abs(dz) == 2 else 'spruce_slab[type=top]')
    return HULL_Y + 5


def rigging(g, cy):
    """Chains from the balloon's belly to the gondola rails."""
    for x in (20, 30, 40):
        for side in (-1, 1):
            z = CZ - 1 + side * 3 if side > 0 else CZ + side * 3
            top = cy - 6
            for y in range(HULL_Y + 6, top + 1):
                g.set(x, y, z, 'iron_chain[axis=y]')


def mooring(g):
    """A timber tower at the bow with a platform, and chains down to it and to ground stakes."""
    tx, tz = W - 7, CZ - 1
    for y in range(G, HULL_Y + 4):
        for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            kit.log(g, tx + dx, y, tz + dz, 'stripped_spruce_log')
        if (y - G) % 5 == 0:
            for d in range(-1, 2):
                g.set(tx + d, y, tz - 1, 'spruce_planks')
                g.set(tx + d, y, tz + 1, 'spruce_planks')
                g.set(tx - 1, y, tz + d, 'spruce_planks')
                g.set(tx + 1, y, tz + d, 'spruce_planks')
    for y in range(G, HULL_Y + 4):
        g.set(tx, y, tz, 'ladder[facing=south]')
        g.set(tx, y, tz - 1, 'spruce_planks')
    for x in range(tx - 2, tx + 3):
        for z in range(tz - 2, tz + 3):
            g.set(x, HULL_Y + 4, z, 'spruce_planks')
    for x in range(tx - 2, tx + 3):
        g.set(x, HULL_Y + 5, tz - 2, 'spruce_fence')
        g.set(x, HULL_Y + 5, tz + 2, 'spruce_fence')
    g.set(tx + 2, HULL_Y + 5, tz, 'spruce_fence')
    g.set(tx + 2, HULL_Y + 6, tz, 'lantern[hanging=false]')
    g.set(tx, HULL_Y + 4, tz, 'ladder[facing=south]')
    # Mooring line from the prow to the tower.
    g.line((50, HULL_Y + 4, CZ - 1), (tx - 2, HULL_Y + 5, tz), 'spruce_fence')
    # Ground stakes with chains up to the balloon's flanks.
    for (sx, sz) in ((14, 2), (14, D - 3), (42, 2), (42, D - 3)):
        g.set(sx, G, sz, 'polished_andesite')
        g.set(sx, G + 1, sz, 'andesite_wall')
        top = (sx, HULL_Y + 12, CZ - 8 if sz < CZ else CZ + 7)
        g.line((sx, G + 2, sz), top, 'iron_chain[axis=y]')


def build():
    g = Grid(W, H, D)
    ground(g)
    cy = balloon(g)
    hull(g)
    rigging(g, cy)
    mooring(g)
    kit.scatter_ground(g, G - 1, 0, 0, W - 1, D - 1, ['minecraft:short_grass', 'minecraft:dandelion', 'minecraft:poppy', 'minecraft:fern'], 0.15)
    kit.keeper(g, 'odo', W - 11, CZ + 4, G, G + 1, 'west')
    kit.waystone(g, W - 14, D - 5, G, G + 1)
    kit.connect(g)
    return g

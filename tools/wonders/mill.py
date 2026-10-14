"""Brassmere Works: the Guild's mill and skyport. A brick-and-copper mill turned by a great
waterwheel on its millpond, smoking chimneys, a timber crane, copper pipes and gears, and a
mooring tower with an airship tied up at the top."""
import math
import numpy as np
from vox import Grid, Sculpt, hash3, vnoise, pick

W, H, D = 128, 92, 128
G = 5               # the pond is dug four deep below the ground line
POND_Z0, POND_Z1 = 30, 44
MILL_X0, MILL_X1, MILL_Z0, MILL_Z1 = 18, 52, 47, 67


def ground(g):
    X, Z = g.xz()
    r = hash3(X.astype(int), 3, Z.astype(int), 2)
    ang = np.arctan2(Z - 64, X - 64)
    edge = 54 + (vnoise(np.cos(ang) * 9 + 20, 1, np.sin(ang) * 9 + 20, 3.0, 6) - 0.5) * 16
    dist = np.hypot(X - 64, Z - 64)
    yard = dist < edge
    verge = (dist >= edge) & (dist < edge + 5)
    g.a[:, G - 1, :][verge] = g.pid('grass_block')
    pave = np.where(r < 0.35, 'gravel', np.where(r < 0.6, 'cobblestone', np.where(r < 0.8, 'packed_mud', 'coarse_dirt')))
    pave = np.where((r > 0.8) & (dist > edge - 8), 'grass_block', pave)
    for st in sorted(set(pave[yard].tolist()), key=str):
        g.a[:, G - 1, :][yard & (pave == st)] = g.pid(st)
    for y in range(G, G + 6):
        g.a[:, y, :][yard] = g.pid('air')
    # The millpond: a stone basin three deep, closed at both ends.
    g.fill(13, G - 4, POND_Z0 - 1, W - 14, G - 1, POND_Z1 + 1, 'stone_bricks')
    g.fill(14, G - 3, POND_Z0, W - 15, G - 1, POND_Z1, 'water')
    for x in range(13, W - 13):
        for z in (POND_Z0 - 1, POND_Z1 + 1):
            g.set(x, G, z, 'stone_brick_slab[type=bottom]')
    # Lily pads and reeds at the quiet end.
    rng = np.random.default_rng(5)
    for _ in range(30):
        x, z = int(rng.integers(70, W - 16)), int(rng.integers(POND_Z0 + 1, POND_Z1))
        g.set(x, G, z, 'lily_pad')


def mill(g):
    x0, x1, z0, z1 = MILL_X0, MILL_X1, MILL_Z0, MILL_Z1
    g.shell(x0, G - 1, z0, x1, G + 10, z1, 'bricks')
    g.shell(x0, G + 11, z0, x1, G + 20, z1, 'spruce_planks')
    for x in range(x0, x1 + 1, 4):
        for z in (z0, z1):
            g.fill(x, G + 11, z, x, G + 20, z, 'stripped_dark_oak_log[axis=y]')
    for z in range(z0, z1 + 1, 4):
        for x in (x0, x1):
            g.fill(x, G + 11, z, x, G + 20, z, 'stripped_dark_oak_log[axis=y]')
    for y in (G + 10, G + 20):
        g.fill(x0, y, z0, x1, y, z0, 'stripped_dark_oak_log[axis=x]')
        g.fill(x0, y, z1, x1, y, z1, 'stripped_dark_oak_log[axis=x]')
        g.fill(x0, y, z0, x0, y, z1, 'stripped_dark_oak_log[axis=z]')
        g.fill(x1, y, z0, x1, y, z1, 'stripped_dark_oak_log[axis=z]')
        g.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, 'spruce_planks')
    g.fill(x0 + 1, G - 1, z0 + 1, x1 - 1, G - 1, z1 - 1, 'polished_andesite')
    # Arched windows, glowing warm.
    for x in range(x0 + 3, x1 - 1, 4):
        for z, inward in ((z0, 1), (z1, -1)):
            for y in (G + 3, G + 14):
                g.fill(x, y, z, x, y + 3, z, 'glass_pane[east=true,west=true]')
                g.set(x, y + 1, z + inward, 'light[level=13]')
    # Roof: copper sheet over a steep gable, ridge along x, with dormers.
    half = (z1 - z0) // 2 + 1
    for k in range(half + 1):
        y = G + 21 + k
        mat = 'waxed_weathered_cut_copper' if k % 3 else 'waxed_exposed_cut_copper'
        g.fill(x0 - 1, y, z0 - 1 + k, x1 + 1, y, z0 - 1 + k, f'{mat}_stairs[facing=south]')
        g.fill(x0 - 1, y, z1 + 1 - k, x1 + 1, y, z1 + 1 - k, f'{mat}_stairs[facing=north]')
        if z0 + k <= z1 - k:
            g.fill(x0, y, z0 + k, x0, y, z1 - k, 'spruce_planks')
            g.fill(x1, y, z0 + k, x1, y, z1 - k, 'spruce_planks')
    g.fill(x0 - 1, G + 21 + half, (z0 + z1) // 2, x1 + 1, G + 21 + half, (z0 + z1) // 2, 'waxed_cut_copper_slab[type=bottom]')
    for x in range(x0 + 4, x1 - 3, 8):
        g.fill(x, G + 22, z1 - 2, x + 2, G + 25, z1 + 1, 'spruce_planks')
        g.fill(x + 1, G + 23, z1 + 1, x + 1, G + 24, z1 + 1, 'glass_pane[east=true,west=true]')
        g.fill(x - 1, G + 26, z1 - 2, x + 3, G + 26, z1 + 2, 'waxed_weathered_cut_copper_slab[type=bottom]')
    # Two tall chimneys with signal smoke.
    for cx in (x0 + 4, x1 - 4):
        g.fill(cx - 1, G - 1, z0 + 3, cx + 1, G + 40, z0 + 5, 'bricks')
        g.fill(cx - 2, G + 38, z0 + 2, cx + 2, G + 39, z0 + 6, 'bricks')
        g.set(cx, G + 40, z0 + 4, 'hay_block[axis=y]')
        g.set(cx, G + 41, z0 + 4, 'campfire[lit=true,signal_fire=true]')
    # Gears on the yard-facing wall.
    gear(g, x0 + 9, G + 15, z1 + 1, 4.5, 'z')
    gear(g, x0 + 18, G + 13, z1 + 1, 3, 'z')
    gear(g, x1 - 8, G + 15, z1 + 1, 4.5, 'z')
    # Doors and a loading bay.
    g.fill((x0 + x1) // 2 - 2, G, z1, (x0 + x1) // 2 + 2, G + 4, z1, 'air')
    g.set((x0 + x1) // 2 - 3, G + 3, z1 + 1, 'waxed_copper_bulb[lit=true]')
    g.set((x0 + x1) // 2 + 3, G + 3, z1 + 1, 'waxed_copper_bulb[lit=true]')
    # Inside: millstones, sacks, barrels.
    for mx in (x0 + 8, x0 + 20):
        g.cyl(mx, (z0 + z1) // 2, G, G + 1, 2.5, 'smooth_stone')
        g.set(mx, G + 2, (z0 + z1) // 2, 'grindstone[face=floor,facing=north]')
    for i in range(10):
        g.set(x1 - 2 - (i % 5), G, z0 + 2 + i // 5, 'barrel[facing=up]')
    g.chest(x1 - 2, G, z1 - 2, 'west', 'afterworld:chests/brassmere_storeroom')
    g.chest(x0 + 2, G, z1 - 2, 'east', 'afterworld:chests/brassmere_storeroom')


def gear(g, cx, cy, z, r, plane):
    """A copper gear on a wall: rim, teeth, spokes and a hub."""
    for a in np.linspace(0, 2 * math.pi, int(r * 14), endpoint=False):
        tooth = int(a / (2 * math.pi) * 10) % 2 == 0 and abs((a * 10 / (2 * math.pi)) % 1 - 0.5) < 0.3
        rr = r + (1 if tooth else 0)
        for rad in (rr, r - 0.6):
            g.set(cx + math.cos(a) * rad, cy + math.sin(a) * rad, z, 'waxed_cut_copper' if tooth else 'waxed_copper_block')
    for a in np.linspace(0, 2 * math.pi, 4, endpoint=False):
        g.line((cx, cy, z), (cx + math.cos(a) * (r - 0.5), cy + math.sin(a) * (r - 0.5), z), 'waxed_exposed_copper')
    g.set(cx, cy, z, 'waxed_copper_bulb[lit=true]')


def waterwheel(g):
    """A great wheel in the pond against the mill's north wall, axle into the building."""
    cx, cy, cz = (MILL_X0 + MILL_X1) // 2, G + 6, MILL_Z0 - 3
    R = 9
    for zz in (cz - 1, cz + 1):
        for a in np.linspace(0, 2 * math.pi, 120, endpoint=False):
            g.set(cx + math.cos(a) * R, cy + math.sin(a) * R, zz, 'stripped_spruce_log[axis=z]')
            g.set(cx + math.cos(a) * (R - 1), cy + math.sin(a) * (R - 1), zz, 'spruce_planks')
        for a in np.linspace(0, 2 * math.pi, 8, endpoint=False):
            g.line((cx, cy, zz), (cx + math.cos(a) * (R - 1), cy + math.sin(a) * (R - 1), zz), 'spruce_fence')
    # Paddles across the rim.
    for a in np.linspace(0, 2 * math.pi, 16, endpoint=False):
        for rr in (R, R + 1):
            g.fill(cx + math.cos(a) * rr, cy + math.sin(a) * rr, cz - 1, cx + math.cos(a) * rr, cy + math.sin(a) * rr, cz + 1, 'spruce_planks')
    g.fill(cx, cy, cz - 2, cx, cy, MILL_Z0, 'stripped_dark_oak_log[axis=z]')
    g.fill(cx - 1, cy - 1, MILL_Z0, cx + 1, cy + 1, MILL_Z0, 'waxed_copper_block')
    # The wheel's lower part sits in the water (level with the pond, so it does not spill over it);
    # clear the bank so it shows.
    g.fill(cx - R - 2, G - 1, POND_Z1 + 1, cx + R + 2, G - 1, MILL_Z0 - 1, 'water')
    g.fill(cx - R - 2, G, POND_Z1 + 1, cx + R + 2, G, MILL_Z0 - 1, 'air')
    g.fill(cx - R - 3, G - 4, POND_Z1 + 1, cx + R + 3, G - 2, MILL_Z0 - 1, 'stone_bricks')
    g.fill(cx - R - 3, G - 1, POND_Z1 + 1, cx - R - 3, G + 1, MILL_Z0 - 1, 'stone_bricks')
    g.fill(cx + R + 3, G - 1, POND_Z1 + 1, cx + R + 3, G + 1, MILL_Z0 - 1, 'stone_bricks')
    # Redraw the wheel's lowest arc where the water fill covered it.
    for a in np.linspace(0, 2 * math.pi, 120, endpoint=False):
        y = cy + math.sin(a) * R
        if y <= G + 1:
            for zz in (cz - 1, cz + 1):
                g.set(cx + math.cos(a) * R, y, zz, 'stripped_spruce_log[axis=z]')


def crane(g):
    """A timber crane on the pond's edge, a crate swinging over the water."""
    x, z = 72, POND_Z1 + 3
    for dx, dz in ((0, 0), (3, 0), (0, 3), (3, 3)):
        g.fill(x + dx, G, z + dz, x + dx, G + 22, z + dz, 'spruce_log[axis=y]')
    for y in range(G + 4, G + 22, 6):
        g.line((x, y, z), (x + 3, y + 5, z), 'spruce_fence')
        g.line((x, y, z + 3), (x + 3, y + 5, z + 3), 'spruce_fence')
    g.fill(x - 1, G + 23, z - 1, x + 4, G + 23, z + 4, 'spruce_planks')
    # The boom, out over the pond.
    for k in range(0, 16):
        g.set(x + 1, G + 24 + k // 6, z - k, 'stripped_spruce_log[axis=z]')
        g.set(x + 2, G + 24 + k // 6, z - k, 'stripped_spruce_log[axis=z]')
    g.fill(x + 1, G + 26, z + 4, x + 2, G + 28, z + 6, 'cobblestone')
    hz = z - 14
    g.fill(x + 1, G + 9, hz, x + 1, G + 25, hz, 'iron_chain[axis=y]')
    g.fill(x, G + 5, hz - 1, x + 2, G + 8, hz + 1, 'barrel[facing=up]')
    g.set(x + 1, G + 9, hz, 'iron_chain[axis=y]')


def pipes(g):
    """Copper pipes from the mill to the tower, with lit bulbs at the joints."""
    path = [(MILL_X1 + 1, G + 8, MILL_Z0 + 6), (MILL_X1 + 12, G + 8, MILL_Z0 + 6), (MILL_X1 + 12, G + 8, MILL_Z1 + 8),
            (96, G + 8, MILL_Z1 + 8), (96, G + 8, 80)]
    for a, b in zip(path, path[1:]):
        g.line(a, b, 'waxed_copper_grate')
        g.set(*b, 'waxed_copper_bulb[lit=true]')
    for (x, y, z) in path[1:-1]:
        g.fill(x, G, z, x, y - 1, z, 'waxed_cut_copper')
    path2 = [(MILL_X0 - 1, G + 12, MILL_Z0 + 10), (8, G + 12, MILL_Z0 + 10), (8, G, MILL_Z0 + 10)]
    for a, b in zip(path2, path2[1:]):
        g.line(a, b, 'waxed_copper_grate')
    g.fill(6, G, MILL_Z0 + 8, 10, G + 4, MILL_Z0 + 12, 'waxed_copper_block')
    g.set(8, G + 5, MILL_Z0 + 10, 'waxed_copper_bulb[lit=true]')


TOWER_X, TOWER_Z = 100, 84
DECK = G + 46


def mooring_tower(g):
    """A tall lattice tower of iron and copper, a platform and a gangway to the airship."""
    s = 4
    corners = [(-s, -s), (s, -s), (-s, s), (s, s)]
    for (dx, dz) in corners:
        for y in range(G - 1, DECK):
            t = (y - G) / (DECK - G)
            k = 1 - t * 0.45
            g.set(TOWER_X + dx * k, y, TOWER_Z + dz * k, 'waxed_copper_block' if y % 8 == 0 else 'iron_block' if y < G + 3 else 'waxed_cut_copper')
    for y in range(G + 6, DECK, 6):
        t = (y - G) / (DECK - G)
        k = s * (1 - t * 0.45)
        for (a, b) in ((0, 1), (1, 3), (3, 2), (2, 0)):
            (ax, az), (bx, bz) = corners[a], corners[b]
            g.line((TOWER_X + ax * k / s, y, TOWER_Z + az * k / s), (TOWER_X + bx * k / s, y, TOWER_Z + bz * k / s), 'iron_bars')
    for y in range(G, DECK):
        g.set(TOWER_X, y, TOWER_Z, 'iron_chain[axis=y]')
    g.fill(TOWER_X - 5, DECK, TOWER_Z - 5, TOWER_X + 5, DECK, TOWER_Z + 5, 'spruce_planks')
    for x in range(TOWER_X - 5, TOWER_X + 6):
        for z in (TOWER_Z - 5, TOWER_Z + 5):
            g.set(x, DECK + 1, z, 'spruce_fence')
    for z in range(TOWER_Z - 5, TOWER_Z + 6):
        for x in (TOWER_X - 5, TOWER_X + 5):
            g.set(x, DECK + 1, z, 'spruce_fence')
    g.fill(TOWER_X - 1, DECK + 1, TOWER_Z - 5, TOWER_X + 1, DECK + 1, TOWER_Z - 5, 'air')
    g.fill(TOWER_X, DECK + 1, TOWER_Z, TOWER_X, DECK + 6, TOWER_Z, 'iron_bars')
    g.set(TOWER_X, DECK + 7, TOWER_Z, 'waxed_copper_bulb[lit=true]')
    g.set(TOWER_X, DECK + 8, TOWER_Z, 'lightning_rod')
    for (dx, dz) in ((-4, -4), (4, -4), (-4, 4), (4, 4)):
        g.set(TOWER_X + dx, DECK + 2, TOWER_Z + dz, 'lantern[hanging=false]')
    # Gangway to the gondola, north of the tower.
    g.fill(TOWER_X - 1, DECK, TOWER_Z - 13, TOWER_X + 1, DECK, TOWER_Z - 6, 'spruce_planks')
    for z in range(TOWER_Z - 13, TOWER_Z - 5):
        g.set(TOWER_X - 2, DECK + 1, z, 'spruce_fence')
        g.set(TOWER_X + 2, DECK + 1, z, 'spruce_fence')


def airship(g):
    """The airship: a banded envelope, fins, a planked gondola with a deck, rigging and props."""
    ax, az = TOWER_X - 18, TOWER_Z - 16          # gondola centre (the ship lies along x)
    gy = DECK - 1                                # gondola keel line
    ey = gy + 18                                 # envelope centre height
    s = Sculpt()
    s.ellipsoid((ax, ey, az), (27, 10, 10), 'envelope')
    s.capsule((ax - 22, ey, az), (ax - 36, ey + 0.5, az), 7.5, 1.5, 'envelope', k=3)
    s.capsule((ax + 22, ey, az), (ax + 30, ey, az), 7.5, 2.5, 'envelope', k=3)

    def mats(m, X, Y, Z, d, up):
        out = np.empty(X.shape, dtype=object)
        band = (np.round(X) % 7 == 0)
        out[:] = pick([('white_wool', 6), ('light_gray_wool', 1)], hash3(X.astype(int), Y.astype(int), Z.astype(int), 2))
        out[band] = 'brown_wool'
        out[Y > ey + 8.6] = 'light_gray_wool'
        return out
    s.voxelise(g, mats, thickness=1.3)
    # Tail fins.
    for k in range(11):
        x = ax - 27 - k
        h = 11 - k
        g.fill(x, ey + 7, az, x, ey + 7 + h, az, 'spruce_planks')
        g.fill(x, ey - 7 - h, az, x, ey - 7, az, 'spruce_planks')
        g.fill(x, ey, az - 7 - h, x, ey, az - 7, 'spruce_planks')
        g.fill(x, ey, az + 7, x, ey, az + 7 + h, 'spruce_planks')
    # Gondola: a long boat-shaped hull.
    L, Wd = 16, 5
    for x in range(ax - L, ax + L + 1):
        t = abs(x - ax) / L
        half = max(1, int(round(Wd * (1 - t ** 3))))
        for z in range(az - half, az + half + 1):
            edge = abs(z - az) == half
            g.set(x, gy, z, 'dark_oak_planks')
            for y in range(gy + 1, gy + 4):
                if edge or abs(x - ax) == L:
                    g.set(x, y, z, 'spruce_planks' if y < gy + 3 else 'stripped_spruce_log[axis=x]')
                else:
                    g.set(x, y, z, 'air')
            g.set(x, gy + 4, z, 'spruce_planks' if not edge else 'spruce_fence')
        for z in (az - half, az + half):
            if x % 3 == 0 and abs(x - ax) < L - 2:
                g.set(x, gy + 2, z, 'glass_pane[east=true,west=true]')
    g.fill(ax - L + 2, gy - 1, az, ax + L - 2, gy - 1, az, 'waxed_cut_copper')
    for x in range(ax - 8, ax + 9, 4):
        g.set(x, gy + 1, az, 'light[level=14]')
    # Deckhouse and wheel.
    g.shell(ax + 3, gy + 4, az - 2, ax + 8, gy + 8, az + 2, 'spruce_planks', inner='air')
    g.fill(ax + 4, gy + 6, az - 2, ax + 7, gy + 6, az - 2, 'glass_pane[east=true,west=true]')
    g.fill(ax + 4, gy + 6, az + 2, ax + 7, gy + 6, az + 2, 'glass_pane[east=true,west=true]')
    g.fill(ax + 2, gy + 9, az - 3, ax + 9, gy + 9, az + 3, 'waxed_cut_copper_slab[type=bottom]')
    g.set(ax + 5, gy + 5, az, 'lectern[facing=east]')
    g.chest(ax - 6, gy + 5, az, 'east', 'afterworld:chests/brassmere_airship')
    # Rigging: chains from the gondola rails up to the envelope.
    for x in range(ax - 12, ax + 13, 6):
        for side in (-1, 1):
            g.line((x, gy + 5, az + side * 4), (x, ey - 8, az + side * 6), 'iron_chain[axis=y]')
    # Propellers at the stern and engine pods.
    for side in (-1, 1):
        px, pz = ax - L - 1, az + side * 6
        g.fill(ax - L + 2, gy + 2, az + side * 4, ax - L + 2, gy + 2, pz, 'waxed_cut_copper')
        g.fill(px, gy + 1, pz, px + 3, gy + 3, pz, 'waxed_copper_block')
        g.set(px - 1, gy + 2, pz, 'waxed_copper_bulb[lit=true]')
        for k in range(1, 4):
            g.set(px - 1, gy + 2 + k, pz, 'spruce_planks')
            g.set(px - 1, gy + 2 - k, pz, 'spruce_planks')
            g.set(px - 1, gy + 2, pz + k, 'spruce_planks')
            g.set(px - 1, gy + 2, pz - k, 'spruce_planks')
    # Mooring lines to the tower deck.
    g.line((ax + L, gy + 4, az), (TOWER_X - 5, DECK + 1, TOWER_Z - 5), 'iron_chain[axis=x]')


def sheds(g):
    """A warehouse and a workshop round the yard, rails to the pond."""
    for (x0, z0, w, d) in ((60, 70, 14, 10), (60, 96, 10, 12), (24, 82, 16, 10)):
        g.shell(x0, G - 1, z0, x0 + w, G + 6, z0 + d, 'spruce_planks')
        for x in range(x0, x0 + w + 1, 3):
            g.fill(x, G, z0, x, G + 6, z0, 'stripped_spruce_log[axis=y]')
            g.fill(x, G, z0 + d, x, G + 6, z0 + d, 'stripped_spruce_log[axis=y]')
        for k in range(d // 2 + 2):
            g.fill(x0 - 1, G + 7 + k, z0 - 1 + k, x0 + w + 1, G + 7 + k, z0 - 1 + k, 'mud_brick_stairs[facing=south]')
            g.fill(x0 - 1, G + 7 + k, z0 + d + 1 - k, x0 + w + 1, G + 7 + k, z0 + d + 1 - k, 'mud_brick_stairs[facing=north]')
            if z0 + k <= z0 + d - k:
                g.fill(x0, G + 7 + k, z0 + k, x0, G + 7 + k, z0 + d - k, 'spruce_planks')
                g.fill(x0 + w, G + 7 + k, z0 + k, x0 + w, G + 7 + k, z0 + d - k, 'spruce_planks')
        g.fill(x0 + w // 2 - 1, G, z0, x0 + w // 2 + 1, G + 3, z0, 'air')
        g.set(x0 + w // 2 - 2, G + 3, z0 - 1, 'lantern[hanging=false]')
        for i in range(4):
            g.set(x0 + 2 + i, G, z0 + d - 2, 'barrel[facing=up]')
        g.set(x0 + 2, G, z0 + 2, 'smithing_table')
        g.set(x0 + 3, G, z0 + 2, 'blast_furnace[facing=south,lit=true]')
    for z in range(POND_Z1 + 2, 70):
        g.set(66, G, z, 'rail[shape=north_south]')
    g.set(66, G, 70, 'rail[shape=north_south]')


def industry(g):
    """A tall round factory chimney and a pair of copper boilers by the sheds."""
    cx, cz = 44, 104
    g.cyl(cx, cz, G - 1, G + 52, 3.2, 'bricks', hollow=True, inner='air')
    g.cyl(cx, cz, G - 1, G + 3, 4.2, 'stone_bricks')
    for y in range(G + 10, G + 52, 14):
        g.cyl(cx, cz, y, y, 3.6, 'waxed_cut_copper', hollow=True)
    g.cyl(cx, cz, G + 50, G + 52, 4, 'waxed_copper_block', hollow=True)
    g.set(cx, G + 51, cz, 'hay_block[axis=y]')
    g.set(cx, G + 52, cz, 'campfire[lit=true,signal_fire=true]')
    for bz in (112, 119):
        for x in range(64, 82):
            for a in np.linspace(0, 2 * math.pi, 26, endpoint=False):
                y = G + 3 + math.sin(a) * 2.6
                z = bz + math.cos(a) * 2.6
                g.set(x, y, z, 'waxed_copper_block' if x % 6 else 'waxed_exposed_copper')
        g.fill(64, G + 1, bz, 64, G + 5, bz, 'waxed_cut_copper')
        g.fill(81, G + 1, bz, 81, G + 5, bz, 'waxed_cut_copper')
        g.fill(70, G - 1, bz - 2, 70, G, bz + 2, 'stone_bricks')
        g.fill(76, G - 1, bz - 2, 76, G, bz + 2, 'stone_bricks')
        g.set(73, G + 6, bz, 'waxed_copper_bulb[lit=true]')
        g.line((82, G + 3, bz), (88, G + 3, bz), 'waxed_copper_grate')
        g.line((88, G + 3, bz), (88, G + 9, bz), 'waxed_copper_grate')


def lamps(g):
    for (x, z) in ((10, 52), (10, 76), (58, 50), (86, 50), (86, 76), (56, 86), (40, 100), (90, 104)):
        g.fill(x, G, z, x, G + 3, z, 'iron_bars')
        g.set(x, G + 4, z, 'waxed_copper_bulb[lit=true]')


def build():
    g = Grid(W, H, D)
    ground(g)
    mill(g)
    waterwheel(g)
    crane(g)
    sheds(g)
    pipes(g)
    industry(g)
    mooring_tower(g)
    airship(g)
    lamps(g)
    return g

"""Vesperhold: a walled cathedral city. A cruciform gothic cathedral with twin west spires, a
crossing spire, flying buttresses and a rose window, its stained glass lit from inside; around it,
lantern-lit streets of tall timber-framed houses whose windows glow at night, inside a curtain
wall with round towers and a west gate."""
import math
import numpy as np
from vox import Grid, hash3, vnoise, pick

W, H, D = 160, 104, 160
G = 2            # first layer above ground: streets are paved at G - 1
CX, CZ = 80, 80
WALL_R = 74

# Cathedral plan (x runs west to east, the altar at the east).
NAVE_X0, NAVE_X1 = 48, 118
NAVE_Z0, NAVE_Z1 = 70, 90          # nave walls (inclusive)
AISLE = 6                          # side aisles outside the nave walls
NAVE_TOP = G + 28
CROSS_X0, CROSS_X1 = 94, 110       # transept
TRANS_Z0, TRANS_Z1 = 52, 108
APSE_R = 10
GLASS = ['red_stained_glass', 'blue_stained_glass', 'yellow_stained_glass', 'purple_stained_glass', 'orange_stained_glass']


def stair(material, facing, half='bottom', shape='straight'):
    return f'{material}_stairs[facing={facing},half={half},shape={shape}]'


# ------------------------------------------------------------------ ground and walls

def ground(g):
    X, Z = g.xz()
    d = np.hypot(X - CX, Z - CZ)
    r = hash3(X.astype(int), 1, Z.astype(int), 3)
    inside = d <= WALL_R + 1
    g.a[:, 0, :][inside] = g.pid('stone')
    pave = np.where(r < 0.45, 'stone_bricks', np.where(r < 0.7, 'polished_andesite', np.where(r < 0.85, 'cobblestone', 'mossy_stone_bricks')))
    for st in sorted(set(pave[inside].tolist()), key=str):
        g.a[:, G - 1, :][inside & (pave == st)] = g.pid(st)
    # Keep the streets clear of any hillside the levelling missed.
    for y in range(G, G + 5):
        g.a[:, y, :][inside] = g.pid('air')
    # A grassy verge just outside the wall.
    verge = (d > WALL_R + 1) & (d <= WALL_R + 5)
    g.a[:, G - 1, :][verge] = g.pid('grass_block')


def walls(g):
    X, Z = g.xz()
    d = np.hypot(X - CX, Z - CZ)
    ring = (d <= WALL_R + 0.5) & (d > WALL_R - 2.5)
    for y in range(G, G + 12):
        g.a[:, y, :][ring] = g.pid('stone_bricks' if y % 4 else 'cracked_stone_bricks')
    # Walkway and crenellations.
    walk = (d <= WALL_R - 0.5) & (d > WALL_R - 3.5)
    g.a[:, G + 11, :][walk] = g.pid('stone_brick_slab[type=top]')
    outer = (d <= WALL_R + 0.5) & (d > WALL_R - 0.5)
    ang = np.arctan2(Z - CZ, X - CX)
    teeth = outer & (np.floor((ang + math.pi) * WALL_R / 2) % 2 == 0)
    g.a[:, G + 12, :][teeth] = g.pid('stone_brick_wall')
    # Mossy, ivy-hung lower courses.
    r = hash3(X.astype(int), 5, Z.astype(int), 9)
    for y in range(G, G + 5):
        g.a[:, y, :][ring & (r > 0.82 - y * 0.0)] = g.pid('mossy_stone_bricks')
    # Round towers.
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        tx, tz = CX + math.cos(a) * WALL_R, CZ + math.sin(a) * WALL_R
        tower(g, tx, tz, 5.5, G + 18)
    gate(g)


def tower(g, x, z, r, top):
    g.cyl(x, z, G - 1, top, r, 'stone_bricks', hollow=True, inner='air')
    g.disc(x, z, G - 1, r - 1, 'spruce_planks')
    for y in range(G + 4, top - 2, 5):
        g.disc(x, z, y, r - 1, 'spruce_planks')
    g.disc(x, z, top, r + 1, 'stone_bricks')
    g.disc(x, z, top + 1, r + 1, 'stone_brick_wall', ring_only=True)
    g.cone(x, z, top + 1, int(r * 2.6), r + 0.5, 'deepslate_tiles')
    g.set(x, top + 1 + int(r * 2.6), z, 'lightning_rod')
    # Arrow slits with a lantern glow behind.
    for y in range(G + 5, top - 2, 6):
        for a in np.linspace(0, 2 * math.pi, 4, endpoint=False):
            g.set(x + math.cos(a) * r, y, z + math.sin(a) * r, 'air')
            g.set(x + math.cos(a) * (r - 1), y, z + math.sin(a) * (r - 1), 'light[level=12]')


def gate(g):
    """The west gatehouse: two square towers and a portcullis over the main street."""
    x = CX - WALL_R
    for zz in (CZ - 9, CZ + 4):
        g.shell(x - 4, G - 1, zz, x + 4, G + 20, zz + 5, 'stone_bricks')
        g.fill(x - 5, G + 20, zz - 1, x + 5, G + 20, zz + 6, 'stone_bricks')
        g.fill(x - 4, G + 21, zz, x + 4, G + 21, zz + 5, 'stone_brick_wall')
        g.fill(x - 3, G + 21, zz + 1, x + 3, G + 21, zz + 4, 'air')
        for y in (G + 6, G + 12):
            g.set(x - 4, y, zz + 2, 'air')
            g.set(x - 4, y, zz + 3, 'air')
        g.set(x - 3, G + 1, zz + 2, 'light[level=13]')
    g.fill(x - 4, G - 1, CZ - 3, x + 4, G + 14, CZ + 3, 'stone_bricks')
    g.fill(x - 4, G, CZ - 3, x + 4, G + 8, CZ + 3, 'air')
    for y in range(G + 6, G + 9):
        span = 3 - (y - (G + 6))
        g.fill(x - 4, y, CZ - 3, x + 4, y, CZ - span - 1, 'stone_bricks')
        g.fill(x - 4, y, CZ + span + 1, x + 4, y, CZ + 3, 'stone_bricks')
    for z in range(CZ - 3, CZ + 4):
        g.set(x - 4, G + 8, z, 'iron_bars')
        g.set(x - 4, G + 7, z, 'iron_bars')
    for zz in (CZ - 4, CZ + 4):
        g.set(x - 5, G + 4, zz, 'lantern[hanging=false]')
        g.set(x - 5, G + 3, zz, 'stone_brick_wall')
    for zz in (CZ - 5, CZ + 5):
        g.set(x - 5, G + 10, zz, 'red_wall_banner[facing=west]')


# ------------------------------------------------------------------ the cathedral

def pointed_window(g, x0, y0, z0, w, h, axis, state, frame='chiseled_stone_bricks'):
    """A lancet window in a wall: axis 'x' means the wall runs along x (window faces z)."""
    for i in range(w):
        for j in range(h):
            # Narrow toward the point over the top third.
            top = h - 1 - j
            inset = max(0, (w // 2) - top) if top < w // 2 + 1 else 0
            if abs(i - (w - 1) / 2) > (w - 1) / 2 - inset + 0.01:
                continue
            if axis == 'x':
                g.set(x0 + i, y0 + j, z0, state)
            else:
                g.set(x0, y0 + j, z0 + i, state)
    sill = f'{frame}'
    if axis == 'x':
        g.fill(x0 - 1, y0 - 1, z0, x0 + w, y0 - 1, z0, sill)
    else:
        g.fill(x0, y0 - 1, z0 - 1, x0, y0 - 1, z0 + w, sill)


def gable_roof(g, x0, x1, z0, z1, y0, material, along='x', ridge_cap='deepslate_tile_slab[type=bottom]', gable='stone_bricks'):
    """A steep pitched roof of stairs over x0..x1, z0..z1, ridge along `along`."""
    if along == 'x':
        half = (z1 - z0) // 2
        for k in range(half + 1):
            y = y0 + k
            g.fill(x0, y, z0 + k, x1, y, z0 + k, stair(material, 'south'))
            g.fill(x0, y, z1 - k, x1, y, z1 - k, stair(material, 'north'))
            if z0 + k + 1 <= z1 - k - 1:
                g.fill(x0, y, z0 + k + 1, x1, y, z1 - k - 1, 'air')
        if (z1 - z0) % 2 == 0:
            g.fill(x0, y0 + half + 1, z0 + half, x1, y0 + half + 1, z0 + half, ridge_cap)
        # Gable ends.
        for k in range(half):
            g.fill(x0, y0 + k, z0 + k + 1, x0, y0 + k, z1 - k - 1, gable)
            g.fill(x1, y0 + k, z0 + k + 1, x1, y0 + k, z1 - k - 1, gable)
    else:
        half = (x1 - x0) // 2
        for k in range(half + 1):
            y = y0 + k
            g.fill(x0 + k, y, z0, x0 + k, y, z1, stair(material, 'east'))
            g.fill(x1 - k, y, z0, x1 - k, y, z1, stair(material, 'west'))
            if x0 + k + 1 <= x1 - k - 1:
                g.fill(x0 + k + 1, y, z0, x1 - k - 1, y, z1, 'air')
        if (x1 - x0) % 2 == 0:
            g.fill(x0 + half, y0 + half + 1, z0, x0 + half, y0 + half + 1, z1, ridge_cap)
        for k in range(half):
            g.fill(x0 + k + 1, y0 + k, z0, x1 - k - 1, y0 + k, z0, gable)
            g.fill(x0 + k + 1, y0 + k, z1, x1 - k - 1, y0 + k, z1, gable)


def spire(g, x, z, y0, base, height, cap='deepslate_tiles'):
    """A tall round spire with a lit lantern-stage at its foot and pinnacles at the corners."""
    g.cone(x, z, y0, height, base + 0.5, cap, r1=0.2, hollow=True)
    g.set(x, y0 + height, z, 'end_rod[facing=up]')
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        px, pz = x + math.cos(a) * (base + 1.5), z + math.sin(a) * (base + 1.5)
        g.fill(px, y0 - 2, pz, px, y0 + 3, pz, 'stone_bricks')
        g.set(px, y0 + 4, pz, 'stone_brick_wall')
        g.set(px, y0 + 5, pz, 'lightning_rod')
    g.set(x, y0 + 2, z, 'light[level=15]')


def cathedral(g, rng):
    mat = 'stone_bricks'
    # Foundations and floor.
    g.fill(NAVE_X0 - 8, G - 1, NAVE_Z0 - AISLE - 1, NAVE_X1 + APSE_R + 1, G - 1, NAVE_Z1 + AISLE + 1, 'polished_andesite')
    # Side aisles: lower walls with their own windows and lean-to roofs.
    for zz0, zz1, face in ((NAVE_Z0 - AISLE, NAVE_Z0, 'north'), (NAVE_Z1, NAVE_Z1 + AISLE, 'south')):
        g.shell(NAVE_X0, G - 1, zz0, NAVE_X1, G + 12, zz1, mat)
        outer_z = zz0 if face == 'north' else zz1
        for bx in range(NAVE_X0 + 4, NAVE_X1 - 3, 7):
            pointed_window(g, bx, G + 3, outer_z, 3, 7, 'x', GLASS[(bx // 7) % len(GLASS)])
            g.set(bx + 1, G + 1, outer_z + (1 if face == 'north' else -1), 'light[level=15]')
            g.set(bx + 1, G + 7, outer_z + (1 if face == 'north' else -1), 'light[level=15]')
        # Lean-to roof sloping away from the nave.
        for k in range(AISLE + 1):
            z = (NAVE_Z0 - k) if face == 'north' else (NAVE_Z1 + k)
            g.fill(NAVE_X0, G + 13 + (AISLE - k) // 2, z, NAVE_X1, G + 13 + (AISLE - k) // 2, z,
                   stair('deepslate_tile', 'south' if face == 'north' else 'north'))
    # The nave: tall walls, clerestory lancets, the roof.
    g.shell(NAVE_X0, G - 1, NAVE_Z0, NAVE_X1, NAVE_TOP, NAVE_Z1, mat)
    g.fill(NAVE_X0 + 1, G - 1, NAVE_Z0 + 1, NAVE_X1 - 1, G - 1, NAVE_Z1 - 1, 'polished_deepslate')
    # Arcade between nave and aisles.
    for zw in (NAVE_Z0, NAVE_Z1):
        for bx in range(NAVE_X0 + 3, NAVE_X1 - 2, 7):
            g.fill(bx, G, zw, bx + 4, G + 8, zw, 'air')
            g.fill(bx + 1, G + 9, zw, bx + 3, G + 9, zw, 'air')
            g.set(bx + 2, G + 10, zw, 'air')
    for zw, face in ((NAVE_Z0, 'north'), (NAVE_Z1, 'south')):
        for bx in range(NAVE_X0 + 4, NAVE_X1 - 3, 7):
            pointed_window(g, bx, G + 15, zw, 3, 11, 'x', GLASS[(bx // 7 + 2) % len(GLASS)])
            g.set(bx + 1, G + 15, zw + (1 if face == 'north' else -1), 'light[level=15]')
            g.set(bx + 1, G + 22, zw + (1 if face == 'north' else -1), 'light[level=15]')
    # Buttresses with flying arches over the aisles.
    for bx in range(NAVE_X0 + 2, NAVE_X1, 7):
        for side in (-1, 1):
            zp = (NAVE_Z0 - AISLE - 3) if side < 0 else (NAVE_Z1 + AISLE + 3)
            g.fill(bx, G - 1, min(zp, zp - side), bx + 1, G + 18, max(zp, zp - side), mat)
            g.set(bx, G + 19, zp, 'stone_brick_wall')
            g.set(bx + 1, G + 19, zp, 'stone_brick_wall')
            g.set(bx, G + 20, zp, 'lightning_rod')
            # The flying arch: a quarter-curve from the pier top to the nave wall.
            zw = NAVE_Z0 if side < 0 else NAVE_Z1
            span = abs(zw - zp)
            for t in range(1, span):
                z = zp - side * t
                y = G + 18 + int(round(math.sin(t / span * math.pi / 2) * 8))
                g.set(bx, y, z, f'stone_brick_slab[type=top]')
                g.set(bx + 1, y, z, f'stone_brick_slab[type=top]')
                g.set(bx, y + 1, z, 'stone_brick_wall' if t % 2 else 'air')
    gable_roof(g, NAVE_X0, NAVE_X1, NAVE_Z0 - 1, NAVE_Z1 + 1, NAVE_TOP + 1, 'deepslate_tile')
    # Transept.
    g.shell(CROSS_X0, G - 1, TRANS_Z0, CROSS_X1, NAVE_TOP, TRANS_Z1, mat)
    g.fill(CROSS_X0 + 1, G, NAVE_Z0, CROSS_X1 - 1, NAVE_TOP - 1, NAVE_Z1, 'air')
    g.fill(CROSS_X0 + 1, G - 1, TRANS_Z0 + 1, CROSS_X1 - 1, G - 1, TRANS_Z1 - 1, 'polished_deepslate')
    gable_roof(g, CROSS_X0 - 1, CROSS_X1 + 1, TRANS_Z0, TRANS_Z1, NAVE_TOP + 1, 'deepslate_tile', along='z')
    for zt, face in ((TRANS_Z0, 'north'), (TRANS_Z1, 'south')):
        rose(g, (CROSS_X0 + CROSS_X1) // 2, G + 18, zt, 6, 'z_face', face)
        g.fill(CROSS_X0 + 4, G, zt, CROSS_X1 - 4, G + 6, zt, 'air')
    # Crossing tower and spire.
    tx, tz = (CROSS_X0 + CROSS_X1) // 2, (NAVE_Z0 + NAVE_Z1) // 2
    g.shell(tx - 7, NAVE_TOP, tz - 7, tx + 7, NAVE_TOP + 14, tz + 7, mat)
    for y in (NAVE_TOP + 4,):
        for k in range(-4, 5, 4):
            for (sx, sz) in ((tx + k, tz - 7), (tx + k, tz + 7), (tx - 7, tz + k), (tx + 7, tz + k)):
                g.fill(sx, y, sz, sx, y + 6, sz, 'yellow_stained_glass')
    g.fill(tx - 6, NAVE_TOP + 3, tz - 6, tx + 6, NAVE_TOP + 3, tz + 6, 'light[level=15]')
    g.fill(tx - 5, NAVE_TOP + 3, tz - 5, tx + 5, NAVE_TOP + 3, tz + 5, 'air')
    g.fill(tx - 8, NAVE_TOP + 15, tz - 8, tx + 8, NAVE_TOP + 15, tz + 8, 'stone_bricks')
    spire(g, tx, tz, NAVE_TOP + 16, 7, 42)
    # Apse: a half-round east end with tall windows.
    ax, az = NAVE_X1, (NAVE_Z0 + NAVE_Z1) // 2
    X, Z = g.xz()
    d = np.hypot(X - ax, Z - az)
    half = (X >= ax)
    for y in range(G - 1, NAVE_TOP + 1):
        g.a[:, y, :][half & (d <= APSE_R + 0.4) & (d > APSE_R - 0.8)] = g.pid(mat)
        if y >= G:
            g.a[:, y, :][half & (d <= APSE_R - 0.8) & (X > ax)] = g.pid('air')
    g.a[:, G - 1, :][half & (d <= APSE_R)] = g.pid('polished_deepslate')
    for k in range(5):
        a = -math.pi / 2 + (k + 0.5) * math.pi / 5
        wx, wz = ax + math.cos(a) * APSE_R, az + math.sin(a) * APSE_R
        for y in range(G + 4, G + 22):
            g.set(wx, y, wz, GLASS[k % len(GLASS)])
        g.set(ax + math.cos(a) * (APSE_R - 1.5), G + 8, az + math.sin(a) * (APSE_R - 1.5), 'light[level=15]')
        g.set(ax + math.cos(a) * (APSE_R - 1.5), G + 17, az + math.sin(a) * (APSE_R - 1.5), 'light[level=15]')
    g.cone(ax, az, NAVE_TOP + 1, 9, APSE_R + 1, 'deepslate_tiles')
    # West front: twin towers, a great door and a rose window.
    fz0, fz1 = NAVE_Z0 - AISLE - 1, NAVE_Z1 + AISLE + 1
    fx0, fx1 = NAVE_X0 - 9, NAVE_X0
    for zz0, zz1 in ((fz0, fz0 + 9), (fz1 - 9, fz1)):
        g.shell(fx0, G - 1, zz0, fx1, G + 44, zz1, mat)
        for y in range(G + 8, G + 42, 9):
            pointed_window(g, fx0, y, zz0 + 3, 4, 7, 'z', 'black_stained_glass')
        g.fill(fx0 - 1, G + 44, zz0 - 1, fx1 + 1, G + 44, zz1 + 1, 'stone_bricks')
        g.fill(fx0 - 1, G + 45, zz0 - 1, fx1 + 1, G + 45, zz1 + 1, 'stone_brick_wall')
        g.fill(fx0, G + 45, zz0, fx1, G + 45, zz1, 'air')
        spire((g), (fx0 + fx1) // 2, (zz0 + zz1) // 2, G + 46, 4, 34)
        for y in range(G + 8, G + 42, 9):
            g.set((fx0 + fx1) // 2, y, (zz0 + zz1) // 2, 'light[level=14]')
    # The front wall between the towers.
    g.fill(fx1 - 2, G - 1, fz0 + 9, fx1, NAVE_TOP + 2, fz1 - 9, mat)
    g.fill(fx1 - 2, G, CZ - 3, fx1, G + 10, CZ + 3, 'air')
    for y in range(G + 8, G + 12):
        span = 3 - (y - (G + 8))
        g.fill(fx1 - 2, y, CZ - 3, fx1, y, CZ - span - 1, mat)
        g.fill(fx1 - 2, y, CZ + span + 1, fx1, y, CZ + 3, mat)
    g.fill(fx1 - 1, G, CZ - 2, fx1 - 1, G + 1, CZ - 2, 'dark_oak_door[facing=west,half=lower,hinge=left]')
    g.set(fx1 - 1, G + 1, CZ - 2, 'dark_oak_door[facing=west,half=upper,hinge=left]')
    g.set(fx1 - 1, G, CZ + 2, 'dark_oak_door[facing=west,half=lower,hinge=right]')
    g.set(fx1 - 1, G + 1, CZ + 2, 'dark_oak_door[facing=west,half=upper,hinge=right]')
    rose(g, fx1 - 2, G + 20, CZ, 7, 'x_face', 'west')
    g.fill(fx1 - 2, NAVE_TOP + 3, CZ - 8, fx1 - 2, NAVE_TOP + 9, CZ + 8, mat)
    for k in range(-8, 9):
        g.set(fx1 - 2, NAVE_TOP + 10 - abs(k) // 2 + 2, CZ + k, 'stone_brick_wall' if k % 2 == 0 else 'air')
    interior(g, rng)


def rose(g, x, y, z, r, plane, face):
    """A round window of radiating glass with stone tracery, lit from behind."""
    colours = ['red_stained_glass', 'blue_stained_glass', 'purple_stained_glass', 'yellow_stained_glass']
    for a in range(-r, r + 1):
        for b in range(-r, r + 1):
            d = math.hypot(a, b)
            if d > r + 0.4:
                continue
            ang = math.atan2(b, a)
            spoke = abs(math.sin(ang * 4)) < 0.16 and d > 1.5
            ring = abs(d - r * 0.55) < 0.5 or d > r - 0.6
            state = 'stone_bricks' if (spoke or ring) else colours[int((ang + math.pi) / (2 * math.pi) * 8) % 4] if d > 1.5 else 'yellow_stained_glass'
            if plane == 'x_face':
                g.set(x, y + b, z + a, state)
                g.set(x + 1, y + b, z + a, 'air')
            else:
                g.set(x + a, y + b, z, state)
    behind = 1 if face in ('west', 'north') else -1
    if plane == 'x_face':
        g.set(x + 2 * behind, y, z, 'light[level=15]')
        g.set(x + 2 * behind, y + r // 2, z, 'light[level=15]')
        g.set(x + 2 * behind, y - r // 2, z, 'light[level=15]')
    else:
        g.set(x, y, z + 2 * behind, 'light[level=15]')


def interior(g, rng):
    # Columns along the arcade, pews, an altar, candles and hanging lanterns.
    for zw in (NAVE_Z0 + 2, NAVE_Z1 - 2):
        for bx in range(NAVE_X0 + 2, NAVE_X1 - 1, 7):
            g.fill(bx, G, zw, bx, NAVE_TOP - 4, zw, 'polished_andesite')
            g.set(bx, NAVE_TOP - 3, zw, 'chiseled_stone_bricks')
    for x in range(NAVE_X0 + 6, CROSS_X0 - 2, 2):
        for z in list(range(NAVE_Z0 + 4, CZ - 1)) + list(range(CZ + 2, NAVE_Z1 - 3)):
            g.set(x, G, z, 'dark_oak_stairs[facing=east]')
    g.fill(NAVE_X1 + 2, G, CZ - 3, NAVE_X1 + 5, G, CZ + 3, 'polished_blackstone_bricks')
    g.fill(NAVE_X1 + 3, G + 1, CZ - 2, NAVE_X1 + 4, G + 1, CZ + 2, 'chiseled_quartz_block')
    for z in (CZ - 2, CZ, CZ + 2):
        g.set(NAVE_X1 + 3, G + 2, z, 'white_candle[candles=4,lit=true]')
    g.set(NAVE_X1 + 4, G + 2, CZ, 'lectern[facing=west]')
    for x in range(NAVE_X0 + 6, NAVE_X1, 10):
        g.fill(x, NAVE_TOP - 1, CZ, x, NAVE_TOP - 6, CZ, 'iron_chain[axis=y]')
        g.set(x, NAVE_TOP - 7, CZ, 'lantern[hanging=true]')
    g.chest(NAVE_X1 + 5, G + 1, CZ - 3, 'west', 'afterworld:chests/vesperhold_reliquary')


# ------------------------------------------------------------------ the town

def house(g, x0, z0, w, d, floors, facing, rng):
    """A tall, narrow townhouse: stone ground floor, timber-framed upper floors, steep roof."""
    plaster = ['calcite', 'white_terracotta', 'smooth_sandstone'][int(rng.integers(0, 3))]
    beam = 'stripped_dark_oak_log' if rng.random() < 0.6 else 'stripped_spruce_log'
    roof = ['deepslate_tile', 'dark_oak', 'spruce', 'mud_brick'][int(rng.integers(0, 4))]
    x1, z1 = x0 + w - 1, z0 + d - 1
    top = G + floors * 4
    g.shell(x0, G - 1, z0, x1, G + 3, z1, 'stone_bricks')
    g.shell(x0, G + 4, z0, x1, top, z1, plaster)
    for y in range(G + 4, top + 1, 4):
        g.fill(x0, y, z0, x1, y, z0, f'{beam}[axis=x]')
        g.fill(x0, y, z1, x1, y, z1, f'{beam}[axis=x]')
        g.fill(x0, y, z0, x0, y, z1, f'{beam}[axis=z]')
        g.fill(x1, y, z0, x1, y, z1, f'{beam}[axis=z]')
        g.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, 'spruce_planks')
    for (cx, cz) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        g.fill(cx, G + 4, cz, cx, top, cz, f'{beam}[axis=y]')
    # Windows on every floor, a lantern glow behind each so they shine at night.
    for f in range(floors):
        y = G + 1 + f * 4 + (1 if f else 0)
        for x in range(x0 + 2, x1 - 1, 3):
            for z, step in ((z0, 1), (z1, -1)):
                g.set(x, y, z, 'glass_pane[east=true,west=true]')
                g.set(x, y + 1, z, 'glass_pane[east=true,west=true]')
                if rng.random() < 0.75:
                    g.set(x, y, z + step, 'light[level=13]')
        for z in range(z0 + 2, z1 - 1, 3):
            for x, step in ((x0, 1), (x1, -1)):
                g.set(x, y, z, 'glass_pane[north=true,south=true]')
                g.set(x, y + 1, z, 'glass_pane[north=true,south=true]')
    # Door on the street side.
    if facing in ('north', 'south'):
        dz = z0 if facing == 'north' else z1
        dx = x0 + w // 2
        g.set(dx, G, dz, f'spruce_door[facing={facing},half=lower]')
        g.set(dx, G + 1, dz, f'spruce_door[facing={facing},half=upper]')
        g.set(dx + 1, G + 2, dz + (-1 if facing == 'north' else 1), f'wall_torch[facing={facing}]')
    else:
        dx = x0 if facing == 'west' else x1
        dz = z0 + d // 2
        g.set(dx, G, dz, f'spruce_door[facing={facing},half=lower]')
        g.set(dx, G + 1, dz, f'spruce_door[facing={facing},half=upper]')
    # Roof: ridge along the longer side, with a chimney.
    gable_roof(g, x0 - 1, x1 + 1, z0 - 1, z1 + 1, top + 1, roof, along='x' if w >= d else 'z',
               ridge_cap=f'{roof}_slab[type=bottom]', gable=plaster)
    ch_x, ch_z = x0 + 1, z0 + 1
    g.fill(ch_x, top + 1, ch_z, ch_x, top + max(w, d) // 2 + 3, ch_z, 'bricks')
    g.set(ch_x, top + max(w, d) // 2 + 4, ch_z, 'campfire[lit=true,signal_fire=false]' if rng.random() < 0.4 else 'brick_wall')
    if rng.random() < 0.3:
        g.chest(x1 - 1, G, z1 - 1, 'west', 'afterworld:chests/vesperhold_house')


def town(g, rng):
    """Lots on a grid of streets aligned with the cathedral; most get a house, a few a garden."""
    reserved = np.zeros((W, D), bool)
    reserved[NAVE_X0 - 12:NAVE_X1 + APSE_R + 5, NAVE_Z0 - AISLE - 6:NAVE_Z1 + AISLE + 7] = True   # nave, aisles, buttresses
    reserved[CROSS_X0 - 4:CROSS_X1 + 5, TRANS_Z0 - 4:TRANS_Z1 + 5] = True                       # transept
    reserved[NAVE_X0 - 26:NAVE_X0, CZ - 14:CZ + 15] = True                                     # the square
    reserved[:NAVE_X0, CZ - 6:CZ + 7] = True                                                   # the main street
    X, Z = g.xz()
    outside = np.hypot(X - CX, Z - CZ) > WALL_R - 5
    lot, street = 10, 3
    placed = 0
    for lx in range(4, W - lot, lot + street):
        for lz in range(4, D - lot, lot + street):
            if reserved[lx:lx + lot, lz:lz + lot].any() or outside[lx:lx + lot, lz:lz + lot].any():
                continue
            roll = rng.random()
            if roll < 0.12:
                garden(g, lx, lz, lot, rng)
                continue
            if roll < 0.18:
                stalls(g, lx, lz, lot, rng)
                continue
            w = int(rng.integers(7, lot + 1))
            d = int(rng.integers(7, lot + 1))
            x0 = lx + int(rng.integers(0, lot - w + 1))
            z0 = lz + int(rng.integers(0, lot - d + 1))
            if abs(z0 + d / 2 - CZ) > abs(x0 + w / 2 - CX) * 0.6:
                facing = 'north' if z0 > CZ else 'south'
            else:
                facing = 'west' if x0 > CX else 'east'
            house(g, x0, z0, w, d, int(rng.integers(2, 5)), facing, rng)
            placed += 1
    return placed


def tree(g, x, z, rng, height=None):
    h = height or int(rng.integers(5, 8))
    g.fill(x, G, z, x, G + h - 1, z, 'oak_log[axis=y]')
    for dy in range(-2, 3):
        r = 2.6 - abs(dy) * 0.6
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if dx * dx + dz * dz <= r * r and not (dx == 0 and dz == 0 and dy < 1):
                    g.set(x + dx, G + h - 1 + dy, z + dz, 'oak_leaves[persistent=true]')


def garden(g, lx, lz, lot, rng):
    """A walled garden: grass, a tree, flowers and a bench."""
    g.fill(lx, G - 1, lz, lx + lot - 1, G - 1, lz + lot - 1, 'grass_block')
    for x in range(lx, lx + lot):
        for z in (lz, lz + lot - 1):
            g.set(x, G, z, 'mossy_stone_brick_wall')
    for z in range(lz, lz + lot):
        for x in (lx, lx + lot - 1):
            g.set(x, G, z, 'mossy_stone_brick_wall')
    g.set(lx + lot // 2, G, lz, 'air')
    tree(g, lx + lot // 2, lz + lot // 2, rng)
    flowers = ['poppy', 'cornflower', 'allium', 'oxeye_daisy', 'azure_bluet', 'lily_of_the_valley']
    for _ in range(10):
        x, z = lx + int(rng.integers(1, lot - 1)), lz + int(rng.integers(1, lot - 1))
        if g.get(x, G, z) is None or g.get(x, G, z) == 'minecraft:air':
            g.set(x, G, z, flowers[int(rng.integers(0, len(flowers)))])
    g.set(lx + 2, G, lz + lot - 2, 'spruce_stairs[facing=north]')
    g.set(lx + 3, G, lz + lot - 2, 'spruce_stairs[facing=north]')


def stalls(g, lx, lz, lot, rng):
    """Market stalls: posts, striped awnings, barrels and crates."""
    colours = ['red', 'yellow', 'blue', 'green', 'orange', 'white']
    for i in range(2):
        x0 = lx + 1 + i * 5
        c1, c2 = colours[int(rng.integers(0, 6))], colours[int(rng.integers(0, 6))]
        for (px, pz) in ((x0, lz + 2), (x0 + 3, lz + 2), (x0, lz + 6), (x0 + 3, lz + 6)):
            g.fill(px, G, pz, px, G + 2, pz, 'spruce_fence')
        for x in range(x0 - 1, x0 + 5):
            for z in range(lz + 1, lz + 8):
                g.set(x, G + 3, z, f'{c1 if (x + z) % 2 else c2}_wool')
        g.fill(x0 + 1, G, lz + 3, x0 + 2, G, lz + 5, 'barrel[facing=up]')
        g.set(x0 + 1, G + 1, lz + 4, 'lantern[hanging=false]')


def lamps(g, rng):
    """Lamp posts along the main street and round the square, and a fountain before the doors."""
    for x in range(CX - WALL_R + 8, NAVE_X0 - 10, 8):
        for z in (CZ - 5, CZ + 5):
            post(g, x, z)
    fx, fz = NAVE_X0 - 16, CZ
    g.cyl(fx, fz, G - 1, G, 4, 'stone_bricks')
    g.cyl(fx, fz, G, G, 3, 'water')
    g.fill(fx, G, fz, fx, G + 3, fz, 'stone_bricks')
    g.set(fx, G + 4, fz, 'water')
    for dx, dz in ((-6, -6), (-6, 6), (6, -6), (6, 6)):
        post(g, fx + dx, fz + dz)


def post(g, x, z):
    g.fill(x, G, z, x, G + 3, z, 'dark_oak_fence')
    g.set(x, G + 4, z, 'lantern[hanging=false]')


def build():
    g = Grid(W, H, D)
    rng = np.random.default_rng(31)
    ground(g)
    walls(g)
    print('  houses:', town(g, rng))
    cathedral(g, rng)
    lamps(g, rng)
    return g

"""The Black Citadel: a fortress of needle spires on a sheer crag. A hexagonal curtain wall with
spired towers, a central keep crowned by the tallest spire in the world, a ramp up to the gate,
and windows lit by soul fire."""
import math
import numpy as np
from vox import Grid, hash3, vnoise

W, H, D = 144, 128, 144
CX, CZ = 72, 70
G = 2                   # first layer above the ground line
CRAG = 20               # height of the crag top above the ground line
TOP = G + CRAG          # first layer above the crag top
WALL_R = 40


def crag(g):
    """A mesa of dark rock with cliff faces, ledges and a cap of gravel and ash."""
    X, Z = g.xz()
    ang = np.arctan2(Z - CZ, X - CX)
    wobble = (vnoise(np.cos(ang) * 20 + 50, 3, np.sin(ang) * 20 + 50, 6.0, 4) - 0.5) * 14
    r = np.hypot(X - CX, Z - CZ)
    R = 54 + wobble
    height = np.where(r < R - 5, CRAG, np.clip((R - r) / 5.0, 0, 1) * CRAG * 0.9)
    height = height + (vnoise(X, 1, Z, 5.0, 7) - 0.5) * 3 * (r < R - 5)
    rnd = hash3(X.astype(int), 2, Z.astype(int), 5)
    for y in range(0, G + CRAG + 2):
        layer = g.a[:, y, :]
        lvl = y - G + 1
        solid = height >= lvl
        if y < G:
            solid = r < R + 2
        n = vnoise(X, y * 1.0, Z, 7.0, 13)
        layer[solid] = g.pid('deepslate')
        layer[solid & (n > 0.55)] = g.pid('blackstone')
        layer[solid & (n < 0.32)] = g.pid('tuff')
        layer[solid & (rnd > 0.88)] = g.pid('cobbled_deepslate')
        layer[solid & (rnd < 0.05)] = g.pid('basalt[axis=y]')
    # Cap: gravel, ash and coarse dirt where the crag is flat.
    for y in range(G, G + CRAG + 2):
        top = (height >= y - G + 1) & (height < y - G + 2)
        cap = np.where(rnd < 0.3, 'gravel', np.where(rnd < 0.5, 'tuff', np.where(rnd < 0.68, 'coarse_dirt', np.where(rnd < 0.84, 'blackstone', 'podzol'))))
        for st in sorted(set(cap[top].tolist()), key=str):
            g.a[:, y, :][top & (cap == st)] = g.pid(st)
    # Clear the air on top so no hill pokes through the courtyard.
    inner = r < WALL_R - 1
    for y in range(TOP, TOP + 6):
        g.a[:, y, :][inner] = g.pid('air')


def ramp(g):
    """A walled stair climbing the south face of the crag to the gate."""
    x0, x1 = CX - 3, CX + 3
    for i in range(CRAG + 1):
        z = CZ + WALL_R + 5 + (CRAG - i)
        y = G - 1 + i
        g.fill(x0, G - 1, z, x1, y - 1, z, 'polished_blackstone_bricks')
        g.fill(x0 + 1, y, z, x1 - 1, y, z, 'polished_blackstone_brick_stairs[facing=north]')
        g.fill(x0 + 1, y + 1, z, x1 - 1, y + 5, z, 'air')
        g.fill(x0, G - 1, z, x0, y + 1, z, 'polished_blackstone_bricks')
        g.fill(x1, G - 1, z, x1, y + 1, z, 'polished_blackstone_bricks')
        g.set(x0, y + 2, z, 'polished_blackstone_brick_wall')
        g.set(x1, y + 2, z, 'polished_blackstone_brick_wall')
        if i % 5 == 0:
            g.set(x0, y + 3, z, 'soul_lantern')
            g.set(x1, y + 3, z, 'soul_lantern')
    # Landing before the gate.
    g.fill(x0, TOP - 1, CZ + WALL_R - 2, x1, TOP - 1, CZ + WALL_R + 4, 'polished_blackstone_bricks')
    g.fill(x0 + 1, TOP, CZ + WALL_R - 2, x1 - 1, TOP + 4, CZ + WALL_R + 4, 'air')


def hexagon(i, r):
    a = math.pi / 6 + i * math.pi / 3
    return CX + math.cos(a) * r, CZ + math.sin(a) * r


def curtain(g):
    corners = [hexagon(i, WALL_R) for i in range(6)]
    for i in range(6):
        (ax, az), (bx, bz) = corners[i], corners[(i + 1) % 6]
        n = int(math.hypot(bx - ax, bz - az) * 2)
        for k in range(n + 1):
            t = k / n
            x, z = ax + (bx - ax) * t, az + (bz - az) * t
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    g.fill(x + dx, TOP - 1, z + dz, x + dx, TOP + 14, z + dz, 'polished_blackstone_bricks')
            if k % 4 < 2:
                g.set(x, TOP + 15, z, 'polished_blackstone_brick_wall')
            if k % 9 == 4:
                g.set(x, TOP + 8, z, 'crying_obsidian')
        for k in range(1, n):
            t = k / n
            x, z = ax + (bx - ax) * t, az + (bz - az) * t
            # Walkway inside the battlements.
            ix, iz = x + (CX - x) * 0.05, z + (CZ - z) * 0.05
            g.set(ix, TOP + 14, iz, 'polished_blackstone_brick_slab[type=top]')
    for i, (x, z) in enumerate(corners):
        tower(g, x, z, 5, TOP + 26 + (i % 2) * 8, 16 + (i % 3) * 4)
    gatehouse(g)


def tower(g, x, z, r, top, spire_h):
    g.cyl(x, z, TOP - 1, top, r, 'polished_blackstone_bricks', hollow=True, inner='air')
    g.cyl(x, z, top - 4, top, r + 1, 'polished_blackstone_bricks', hollow=True)
    for y in range(TOP + 3, top - 4, 5):
        g.disc(x, z, y, r - 1, 'dark_oak_planks')
        for a in np.linspace(0, 2 * math.pi, 6, endpoint=False):
            g.set(x + math.cos(a) * r, y + 2, z + math.sin(a) * r, 'purple_stained_glass')
            g.set(x + math.cos(a) * (r - 1), y + 2, z + math.sin(a) * (r - 1), 'light[level=11]')
    g.disc(x, z, top + 1, r + 1, 'polished_blackstone_brick_wall', ring_only=True)
    g.cone(x, z, top + 1, spire_h, r + 0.5, 'deepslate_tiles', r1=0.1, hollow=True)
    g.set(x, top + 1 + spire_h, z, 'end_rod[facing=up]')
    g.set(x, top + 2, z, 'soul_lantern')


def gatehouse(g):
    gz = CZ + WALL_R - 1
    g.fill(CX - 7, TOP - 1, gz - 5, CX + 7, TOP + 20, gz + 2, 'polished_blackstone_bricks')
    g.fill(CX - 3, TOP, gz - 5, CX + 3, TOP + 7, gz + 2, 'air')
    for y in range(TOP + 5, TOP + 8):
        span = 3 - (y - (TOP + 5))
        g.fill(CX - 3, y, gz - 5, CX - span - 1, y, gz + 2, 'polished_blackstone_bricks')
        g.fill(CX + span + 1, y, gz - 5, CX + 3, y, gz + 2, 'polished_blackstone_bricks')
    for x in range(CX - 3, CX + 4):
        g.set(x, TOP + 7, gz + 2, 'iron_bars')
        g.set(x, TOP + 6, gz + 2, 'iron_bars')
    for k in range(-7, 8, 2):
        g.set(CX + k, TOP + 21, gz + 2, 'polished_blackstone_brick_wall')
    for dx in (-8, 8):
        g.fill(CX + dx, TOP + 18, gz - 3, CX + dx, TOP + 18, gz, 'polished_blackstone_bricks')
        tower(g, CX + dx, gz - 1, 3, TOP + 24, 14)
    for dx in (-4, 4):
        g.set(CX + dx, TOP + 3, gz + 3, 'soul_lantern')
        g.set(CX + dx, TOP + 2, gz + 3, 'polished_blackstone_brick_wall')
    g.set(CX, TOP + 12, gz + 3, 'purple_wall_banner[facing=south]')


def keep(g, rng):
    """A great square keep with an octagonal crown and a needle spire; turrets on its shoulders."""
    k0x, k0z = CX - 11, CZ - 13
    k1x, k1z = CX + 11, CZ + 9
    ktop = TOP + 44
    g.shell(k0x, TOP - 1, k0z, k1x, ktop, k1z, 'polished_blackstone_bricks')
    # Buttressed corners and string courses.
    for (bx, bz) in ((k0x, k0z), (k1x, k0z), (k0x, k1z), (k1x, k1z)):
        g.fill(bx - 1, TOP - 1, bz - 1, bx + 1, ktop + 2, bz + 1, 'deepslate_bricks')
    for y in range(TOP + 10, ktop, 11):
        g.fill(k0x - 1, y, k0z - 1, k1x + 1, y, k0z - 1, 'polished_deepslate')
        g.fill(k0x - 1, y, k1z + 1, k1x + 1, y, k1z + 1, 'polished_deepslate')
        g.fill(k0x - 1, y, k0z - 1, k0x - 1, y, k1z + 1, 'polished_deepslate')
        g.fill(k1x + 1, y, k0z - 1, k1x + 1, y, k1z + 1, 'polished_deepslate')
    # Floors inside and tall lancet windows glowing violet.
    for y in range(TOP + 10, ktop, 11):
        g.fill(k0x + 1, y, k0z + 1, k1x - 1, y, k1z - 1, 'dark_oak_planks')
    for y in range(TOP + 3, ktop - 4, 11):
        for x in range(k0x + 4, k1x - 2, 5):
            for z, inward in ((k0z, 1), (k1z, -1)):
                g.fill(x, y, z, x + 1, y + 6, z, 'purple_stained_glass')
                g.set(x, y + 7, z, 'purple_stained_glass')
                g.set(x, y + 3, z + inward, 'light[level=15]')
        for z in range(k0z + 4, k1z - 2, 5):
            for x, inward in ((k0x, 1), (k1x, -1)):
                g.fill(x, y, z, x, y + 6, z + 1, 'purple_stained_glass')
                g.set(x + inward, y + 3, z, 'light[level=15]')
    # Great door.
    g.fill(CX - 2, TOP, k1z, CX + 2, TOP + 6, k1z, 'air')
    g.fill(CX - 1, TOP + 7, k1z, CX + 1, TOP + 7, k1z, 'air')
    # Crown: an octagonal drum with a ring of turrets, then the needle.
    kcx, kcz = CX, (k0z + k1z) // 2
    g.fill(k0x - 1, ktop + 1, k0z - 1, k1x + 1, ktop + 1, k1z + 1, 'polished_blackstone_bricks')
    for x in range(k0x - 1, k1x + 2, 2):
        g.set(x, ktop + 2, k0z - 1, 'polished_blackstone_brick_wall')
        g.set(x, ktop + 2, k1z + 1, 'polished_blackstone_brick_wall')
    for z in range(k0z - 1, k1z + 2, 2):
        g.set(k0x - 1, ktop + 2, z, 'polished_blackstone_brick_wall')
        g.set(k1x + 1, ktop + 2, z, 'polished_blackstone_brick_wall')
    g.cyl(kcx, kcz, ktop + 1, ktop + 14, 8, 'deepslate_tiles', hollow=True, inner='air')
    for a in np.linspace(0, 2 * math.pi, 8, endpoint=False):
        for y in range(ktop + 4, ktop + 11):
            g.set(kcx + math.cos(a) * 8, y, kcz + math.sin(a) * 8, 'magenta_stained_glass')
        g.set(kcx + math.cos(a) * 7, ktop + 7, kcz + math.sin(a) * 7, 'light[level=15]')
    g.disc(kcx, kcz, ktop + 15, 9, 'polished_blackstone_bricks')
    g.cone(kcx, kcz, ktop + 16, H - (ktop + 16) - 3, 8.5, 'deepslate_tiles', r1=0.1, hollow=True)
    g.set(kcx, H - 3, kcz, 'end_rod[facing=up]')
    g.set(kcx, H - 2, kcz, 'end_rod[facing=up]')
    for (tx, tz, h) in ((k0x, k0z, 30), (k1x, k0z, 24), (k0x, k1z, 26), (k1x, k1z, 32)):
        g.cyl(tx, tz, ktop - 6, ktop + 6, 3, 'polished_blackstone_bricks')
        g.cone(tx, tz, ktop + 7, h, 3.5, 'deepslate_tiles', r1=0.1)
        g.set(tx, ktop + 7 + h, tz, 'end_rod[facing=up]')
    # Treasures in the undercroft.
    g.chest(k0x + 2, TOP, k0z + 2, 'south', 'afterworld:chests/citadel_vault')
    g.chest(k1x - 2, TOP, k0z + 2, 'south', 'afterworld:chests/citadel_vault')
    g.fill(CX - 1, TOP, kcz - 1, CX + 1, TOP, kcz + 1, 'crying_obsidian')
    g.set(CX, TOP + 1, kcz, 'enchanting_table')


def courtyard(g, rng):
    """Soul-fire braziers, dead trees and statues of hooded figures about the keep."""
    for i in range(6):
        a = i * math.pi / 3
        x, z = CX + math.cos(a) * 26, CZ + math.sin(a) * 26
        g.fill(x, TOP, z, x, TOP + 1, z, 'polished_blackstone_bricks')
        g.set(x, TOP + 2, z, 'soul_campfire[lit=true]')
    for i in range(10):
        a = rng.uniform(0, 2 * math.pi)
        rr = rng.uniform(16, 33)
        x, z = int(CX + math.cos(a) * rr), int(CZ + math.sin(a) * rr)
        if abs(x - CX) < 14 and abs(z - CZ) < 16:
            continue
        h = int(rng.integers(4, 8))
        g.fill(x, TOP, z, x, TOP + h, z, 'dark_oak_log[axis=y]')
        for k in range(3):
            dx, dz = int(rng.integers(-2, 3)), int(rng.integers(-2, 3))
            g.line((x, TOP + h - k, z), (x + dx, TOP + h + 2 - k, z + dz), 'dark_oak_fence')


def build():
    g = Grid(W, H, D)
    rng = np.random.default_rng(17)
    crag(g)
    ramp(g)
    curtain(g)
    keep(g, rng)
    courtyard(g, rng)
    return g

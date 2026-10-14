"""The Icyshroom Lodge: someone made a home in the stem of a giant frost mushroom. A blue cap
spotted with white and crowned with fins of ice; icicles and soul lanterns under the rim; a lit
door and windows in the pale stem; a little blue mushroom and snowy pines keeping it company."""
import math
from vox import Grid, hash3
import kit

W, H, D = 32, 44, 32
G = 3
C = 16


def ground(g):
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if d <= 15.4:
                g.fill(x, 0, z, x, G - 2, z, 'stone')
                g.set(x, G - 1, z, 'snow_block' if hash3(x, 0, z, 3) < 0.8 else 'packed_ice')
                g.fill(x, G, z, x, H - 1, z, 'air')
    # A path of stones to the door.
    for z in range(C + 5, D):
        for x in (C - 1, C):
            if hash3(x, 1, z, 4) < 0.8:
                g.set(x, G - 1, z, 'cobblestone' if hash3(x, 2, z, 5) < 0.5 else 'stone_bricks')


def stem(g):
    top = G + 13
    for y in range(G - 1, top):
        k = (y - G) / (top - G)
        r = 5.2 - 1.6 * min(1, k * 1.4) + (1.6 if y < G + 2 else 0)
        for x in range(W):
            for z in range(D):
                d = math.hypot(x + 0.5 - C, z + 0.5 - C)
                if d <= r + 0.3:
                    if d > r - 1.1:
                        h = hash3(x, y, z, 7)
                        st = 'mushroom_stem' if h < 0.55 else 'calcite' if h < 0.8 else 'smooth_stone' if h < 0.92 else 'diorite'
                        if y < G + 1:
                            st = 'mossy_cobblestone' if h < 0.5 else 'cobblestone'
                        g.set(x, y, z, st)
                    elif y >= G:
                        g.set(x, y, z, 'air')
    # Floors inside.
    for (y, mat) in ((G - 1, 'spruce_planks'), (G + 6, 'spruce_planks')):
        for x in range(W):
            for z in range(D):
                if math.hypot(x + 0.5 - C, z + 0.5 - C) <= 3.6:
                    g.set(x, y, z, mat)
    # Door, porch roof and windows.
    dz = C + 4
    g.set(C - 1, G, dz, 'air')
    g.set(C - 1, G + 1, dz, 'air')
    kit.door(g, C - 1, G, dz, 'spruce', 'south')
    for x in range(C - 3, C + 2):
        kit.stairs(g, x, G + 3, dz + 1, 'blue_terracotta' if False else 'spruce', 'north', 'bottom')
    g.set(C - 3, G + 2, dz + 1, 'spruce_fence')
    g.set(C + 1, G + 2, dz + 1, 'spruce_fence')
    g.set(C - 3, G + 1, dz + 1, 'spruce_fence')
    g.set(C + 1, G + 1, dz + 1, 'spruce_fence')
    g.set(C - 3, G, dz + 1, 'spruce_fence')
    g.set(C + 1, G, dz + 1, 'spruce_fence')
    kit.hang_lantern(g, C - 2, G + 2, dz + 1, 0, soul=True)
    for (x, y, z, face) in ((C + 3, G + 2, C - 1, 'east'), (C - 5, G + 2, C, 'west'), (C + 1, G + 8, C - 4, 'north'), (C - 4, G + 8, C + 1, 'west')):
        g.set(x, y, z, 'light_blue_stained_glass_pane')
        g.set(x, y + 1, z, 'light_blue_stained_glass_pane')
    # A little furniture.
    g.set(C - 2, G, C - 2, 'furnace[facing=south,lit=true]')
    g.set(C + 1, G, C - 2, 'crafting_table')
    g.chest(C + 2, G, C, 'west', 'afterworld:chests/ruin_cache')
    kit.bed(g, C - 1, G + 7, C - 2, 'light_blue', 'north')
    g.set(C + 1, G + 7, C + 1, 'blue_candle[candles=3,lit=true]')
    g.set(C + 2, G + 1, C - 1, 'ladder[facing=west]')
    for y in range(G, G + 7):
        g.set(C + 2, y, C - 1, 'ladder[facing=west]')
    return top


def cap(g, base):
    """A flattened dome of blues with white spots, a gill ring underneath and icicles on the rim."""
    cy = base + 1
    a, b = 13.4, 8.6
    spots = []
    for i in range(16):
        ang = hash3(i, 1, 2, 9) * math.pi * 2
        el = 0.15 + hash3(i, 3, 4, 9) * 0.75
        spots.append((math.cos(ang) * math.cos(el), math.sin(el), math.sin(ang) * math.cos(el), 1.2 + hash3(i, 5, 6, 9) * 1.4))
    for x in range(W):
        for z in range(D):
            for y in range(cy - 1, min(H, cy + 10)):
                nx, ny, nz = (x + 0.5 - C) / a, (y + 0.5 - cy) / b, (z + 0.5 - C) / a
                d = math.sqrt(nx * nx + ny * ny + nz * nz)
                if d > 1.0 or y < cy - 1:
                    continue
                shell = d > 1.0 - 1.6 / a or y == cy - 1
                if not shell:
                    g.set(x, y, z, 'blue_concrete' if y == cy else 'air')
                    continue
                # Colour by height on the cap, with a little noise.
                k = (y - cy) / b
                h = hash3(x, y, z, 12)
                if k < 0.15:
                    st = 'blue_terracotta' if h < 0.4 else 'blue_concrete'
                elif k < 0.55:
                    st = 'blue_concrete' if h < 0.55 else 'light_blue_concrete'
                else:
                    st = 'light_blue_concrete' if h < 0.7 else 'light_blue_terracotta'
                # White spots on the dome.
                px, py, pz = x + 0.5 - C, y + 0.5 - cy, z + 0.5 - C
                ln = math.sqrt(px * px + py * py + pz * pz) or 1
                for (sx, sy2, sz, sr) in spots:
                    if math.acos(max(-1, min(1, (px * sx + py * sy2 + pz * sz) / ln))) * 11 < sr:
                        st = 'white_concrete' if h < 0.7 else 'snow_block'
                g.set(x, y, z, st)
    # Gills: a ring of pale blue under the cap, outside the stem.
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if 4.2 < d <= a - 0.6:
                g.set(x, cy - 1, z, 'light_blue_terracotta' if (int(math.atan2(z - C, x - C) * 12) % 2) else 'packed_ice')
    # Icicles hanging from the rim, and soul lanterns between them.
    for i in range(46):
        ang = i / 46 * math.pi * 2
        x = int(round(C - 0.5 + math.cos(ang) * (a - 0.8)))
        z = int(round(C - 0.5 + math.sin(ang) * (a - 0.8)))
        h = hash3(i, 7, 8, 10)
        if i % 6 == 0:
            kit.hang_lantern(g, x, cy - 2, z, 1, soul=True)
            continue
        length = 1 + int(h * 4)
        for j in range(length):
            g.set(x, cy - 2 - j, z, 'blue_ice' if j == 0 else 'packed_ice' if j < length - 1 else 'ice')
    # Fins of ice standing up from the crown.
    for i, ang in enumerate((0.3, 1.9, 3.6, 5.0)):
        base_x = C - 0.5 + math.cos(ang) * 5.5
        base_z = C - 0.5 + math.sin(ang) * 5.5
        tip = (base_x + math.cos(ang) * 4, cy + b + 6 + i % 2 * 2, base_z + math.sin(ang) * 4)
        for t in range(14):
            f = t / 13
            px = base_x + (tip[0] - base_x) * f
            py = cy + b - 2 + (tip[1] - (cy + b - 2)) * f
            pz = base_z + (tip[2] - base_z) * f
            r = 1.6 * (1 - f) + 0.3
            st = 'packed_ice' if f < 0.5 else 'blue_ice' if f < 0.85 else 'white_stained_glass'
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if dx * dx + dz * dz <= r * r + 0.2:
                        g.set(px + dx, py, pz + dz, st)


def garden(g):
    """A small blue mushroom, snowy pines, a woodpile and a lantern post."""
    mx, mz = C + 10, C + 6
    for y in range(G, G + 4):
        g.set(mx, y, mz, 'mushroom_stem')
    for x in range(mx - 2, mx + 3):
        for z in range(mz - 2, mz + 3):
            if (x - mx) ** 2 + (z - mz) ** 2 <= 5:
                g.set(x, G + 4, z, 'light_blue_concrete' if (x + z) % 3 else 'white_concrete')
    for (tx, tz, h) in ((C - 11, C - 6, 9), (C - 9, C + 9, 7), (C + 9, C - 10, 8)):
        for y in range(G, G + h):
            kit.log(g, tx, y, tz, 'spruce_log')
        for k in range(h - 2):
            r = max(0, (h - 2 - k) * 0.45)
            y = G + 2 + k
            for x in range(tx - 3, tx + 4):
                for z in range(tz - 3, tz + 4):
                    if (x - tx) ** 2 + (z - tz) ** 2 <= r * r + 0.5 and (x, z) != (tx, tz):
                        kit.leaves(g, x, y, z, 'spruce_leaves')
        kit.leaves(g, tx, G + h, tz, 'spruce_leaves')
    for z in range(C + 6, C + 9):
        kit.log(g, C + 4, G, z, 'spruce_log', 'z')
        kit.log(g, C + 5, G, z, 'spruce_log', 'z')
    kit.log(g, C + 4, G + 1, C + 7, 'spruce_log', 'z')
    g.set(C - 5, G, C + 7, 'spruce_fence')
    g.set(C - 5, G + 1, C + 7, 'spruce_fence')
    g.set(C - 5, G + 2, C + 7, 'soul_lantern[hanging=false]')


def build():
    g = Grid(W, H, D)
    ground(g)
    top = stem(g)
    cap(g, top)
    garden(g)
    kit.snow(g, depth=1, rate=0.9)
    kit.waystone(g, C + 5, D - 5, G, G + 2)
    kit.connect(g)
    return g

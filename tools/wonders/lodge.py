"""The Hunter's Lodge: a log cabin on a cobbled footing, with a deep porch, shuttered windows, a
stone chimney breathing smoke, and inside, a hearth, a bed, a table set for one and trophies on
the walls. Outside, a woodpile, a drying rack, hay and a lantern post. Snowed-in in the cold."""
import math
from vox import Grid, hash3
import kit

W, H, D = 32, 30, 32
G = 3
X0, X1, Z0, Z1 = 9, 22, 11, 20     # cabin walls


def build(snow=False):
    g = Grid(W, H, D)
    for x in range(W):
        for z in range(D):
            if math.hypot(x + 0.5 - 16, z + 0.5 - 16) <= 15.4:
                g.fill(x, 0, z, x, G - 2, z, 'stone')
                g.set(x, G - 1, z, 'snow_block' if snow else ('grass_block' if hash3(x, 0, z, 1) < 0.85 else 'coarse_dirt'))
                g.fill(x, G, z, x, H - 1, z, 'air')
    # Footing.
    g.fill(X0 - 1, G - 1, Z0 - 1, X1 + 1, G, Z1 + 1, 'cobblestone')
    kit.weather(g, {'cobblestone': ['mossy_cobblestone']}, rate=0.35, seed=4)
    g.fill(X0, G, Z0, X1, G, Z1, 'spruce_planks')
    # Log walls: horizontal logs, alternating axis at the corners like a real notched cabin.
    for y in range(G + 1, G + 6):
        for x in range(X0, X1 + 1):
            kit.log(g, x, y, Z0, 'spruce_log', 'x')
            kit.log(g, x, y, Z1, 'spruce_log', 'x')
        for z in range(Z0 + 1, Z1):
            kit.log(g, X0, y, z, 'spruce_log', 'z')
            kit.log(g, X1, y, z, 'spruce_log', 'z')
        for (x, z) in ((X0, Z0), (X1, Z0), (X0, Z1), (X1, Z1)):
            kit.log(g, x, y, z, 'stripped_spruce_log', 'y')
        g.fill(X0 + 1, y, Z0 + 1, X1 - 1, y, Z1 - 1, 'air')
    # Windows with shutters, and the door.
    for x in (X0 + 3, X0 + 9):
        g.set(x, G + 2, Z1, 'glass_pane')
        g.set(x, G + 3, Z1, 'glass_pane')
        kit.shutters(g, x, G + 2, Z1, 'south')
        g.set(x, G + 2, Z0, 'glass_pane')
        g.set(x, G + 3, Z0, 'glass_pane')
        kit.shutters(g, x, G + 2, Z0, 'north')
    g.set(X0, G + 2, Z0 + 4, 'glass_pane')
    g.set(X0, G + 3, Z0 + 4, 'glass_pane')
    kit.door(g, X0 + 6, G + 1, Z1, 'spruce', 'south')
    # Gable roof, ridge along x, dark oak, with plank gables and a deep overhang.
    ridge = kit.gable(g, X0, Z0, X1, Z1, G + 6, 'dark_oak', axis='x', overhang=2, gable_fill='spruce_planks')
    # Porch along the front: slab floor, posts, rail and its own lean-to roof.
    for x in range(X0 - 1, X1 + 2):
        for z in range(Z1 + 1, Z1 + 4):
            g.set(x, G, z, 'spruce_slab[type=top]')
    for x in range(X0 - 1, X1 + 2, 4):
        for y in range(G + 1, G + 4):
            kit.log(g, x, y, Z1 + 3, 'stripped_spruce_log')
    for x in range(X0 - 1, X1 + 2):
        if (x - (X0 - 1)) % 4:
            g.set(x, G + 1, Z1 + 3, 'spruce_fence')
        kit.stairs(g, x, G + 4, Z1 + 3, 'spruce', 'north')
        g.set(x, G + 4, Z1 + 2, 'spruce_planks')
        g.set(x, G + 4, Z1 + 1, 'spruce_planks')
    for x in (X0 + 6,):
        g.set(x, G + 1, Z1 + 3, 'air')
    kit.hang_lantern(g, X0 + 4, G + 3, Z1 + 2, 0)
    kit.hang_lantern(g, X0 + 9, G + 3, Z1 + 2, 0)
    g.set(X0 + 2, G + 1, Z1 + 2, 'barrel[facing=up]')
    kit.chair(g, X0 + 10, G + 1, Z1 + 2, 'spruce', 'south')
    # Stone chimney on the west gable with smoke.
    cx = X0 - 1
    for y in range(G, ridge + 3):
        for z in range(Z0 + 3, Z0 + 6):
            g.set(cx, y, z, 'cobblestone' if hash3(cx, y, z, 5) < 0.6 else 'mossy_cobblestone')
    g.set(cx, ridge + 3, Z0 + 4, 'campfire[lit=true,signal_fire=false]')
    # Hearth inside.
    g.set(X0 + 1, G + 1, Z0 + 4, 'campfire[lit=true]')
    g.set(X0 + 1, G + 2, Z0 + 4, 'cobblestone')
    for z in (Z0 + 3, Z0 + 5):
        g.set(X0 + 1, G + 1, z, 'cobblestone_wall')
    # Furniture.
    kit.bed(g, X1 - 2, G + 1, Z0 + 1, 'brown', 'north')
    g.set(X1 - 1, G + 1, Z0 + 1, 'chest[facing=west]')
    g.loot.append((X1 - 1, G + 1, Z0 + 1, 'afterworld:chests/ruin_cache'))
    kit.table(g, X0 + 6, G + 1, Z0 + 4)
    kit.chair(g, X0 + 6, G + 1, Z0 + 3, 'spruce', 'south')
    kit.chair(g, X0 + 6, G + 1, Z0 + 5, 'spruce', 'north')
    g.set(X0 + 3, G + 1, Z0 + 1, 'furnace[facing=south,lit=false]')
    g.set(X0 + 4, G + 1, Z0 + 1, 'smoker[facing=south,lit=true]')
    g.set(X0 + 5, G + 1, Z0 + 1, 'crafting_table')
    g.set(X0 + 2, G + 1, Z1 - 1, 'barrel[facing=up]')
    g.set(X0 + 2, G + 2, Z1 - 1, 'potted_fern')
    for x in range(X0 + 4, X0 + 9):
        for z in range(Z0 + 3, Z0 + 6):
            g.set(x, G + 1, z, 'brown_carpet') if g.get(x, G + 1, z) in (None, 'minecraft:air') else None
    # Trophies: bone antlers on the north wall.
    for x in (X0 + 8, X0 + 11):
        g.set(x, G + 4, Z0 + 1, 'bone_block[axis=y]')
        g.set(x - 1, G + 5, Z0 + 1, 'bone_block[axis=x]')
        g.set(x + 1, G + 5, Z0 + 1, 'bone_block[axis=x]')
    kit.hang_lantern(g, X0 + 6, G + 5, Z0 + 4, 0)
    # Yard: woodpile, drying rack, hay, lantern post.
    for z in range(Z0 + 1, Z0 + 5):
        for y in range(G, G + 2):
            kit.log(g, X1 + 3, y, z, 'spruce_log', 'x')
            kit.log(g, X1 + 4, y, z, 'oak_log', 'x')
    for x in range(X1 + 2, X1 + 6):
        kit.stairs(g, x, G + 2, Z0 + 3, 'spruce', 'south')
    for x in (X1 + 2, X1 + 5):
        g.set(x, G, Z0 - 4, 'spruce_fence')
        g.set(x, G + 1, Z0 - 4, 'spruce_fence')
    for x in range(X1 + 2, X1 + 6):
        g.set(x, G + 2, Z0 - 4, 'spruce_fence')
        g.set(x, G + 1, Z0 - 3 if x % 2 else Z0 - 5, 'spruce_trapdoor[facing=north,half=top,open=true]')
    g.set(X0 - 4, G, Z1 + 5, 'hay_block[axis=y]')
    g.set(X0 - 5, G, Z1 + 5, 'hay_block[axis=x]')
    g.set(X0 - 4, G + 1, Z1 + 5, 'hay_block[axis=z]')
    for y in range(G, G + 3):
        g.set(X0 + 6, y, Z1 + 8, 'spruce_fence')
    g.set(X0 + 6, G + 3, Z1 + 8, 'lantern[hanging=false]')
    for z in range(Z1 + 4, D):
        g.set(X0 + 6, G - 1, z, 'dirt_path')
        g.set(X0 + 7, G - 1, z, 'dirt_path')
    plants = ['minecraft:fern', 'minecraft:short_grass', 'minecraft:sweet_berry_bush[age=3]'] if not snow else ['minecraft:fern']
    kit.scatter_ground(g, G - 1, 0, 0, W - 1, D - 1, plants, 0.2 if not snow else 0.05)
    if snow:
        kit.snow(g, depth=2, rate=0.95)
    kit.keeper(g, 'torvald', X0 + 8, Z1 + 2, G + 1, G + 1, 'south')
    kit.waystone(g, X0 + 10, Z1 + 8, G, G + 2)
    kit.connect(g)
    return g


def build_snow():
    return build(snow=True)

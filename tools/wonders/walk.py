"""walk.py <module> <fn> <G> <start x,y,z> [name:x,y,z ...] [--open] [--static]

Can a player walk this build? Breadth-first over feet cells from `start`, with the rules of an
ordinary player: walk on anything with a top, step or jump up one block, fall at most FALL blocks,
swim in water, climb ladders and vines, go through doors. Cells the build leaves empty count as
open air at or above the ground line G and as rock below it. Every seal, each of its inputs and
every chest are targets, besides any named ones; a target counts as reached from a feet cell
within reach of it with a clear line of sight. With --open, the seals' gates count as opened.
For each target it also checks the way back to the start, so the player can climb out again.
Every door must have standing room on both sides, and every fluid must be held in. With --static,
only those two (no walk; the start is ignored).

    python3 tools/wonders/walk.py delves sunken_forge 38 24,38,46 --open
"""
import sys
from collections import deque

import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import importlib

mod = importlib.import_module(sys.argv[1])
g = getattr(mod, sys.argv[2])()
G = int(sys.argv[3])
start = tuple(int(v) for v in sys.argv[4].split(','))
opened = '--open' in sys.argv
targets = []
for a in sys.argv[5:]:
    if a.startswith('--'):
        continue
    name, at = a.split(':')
    targets.append((name, tuple(int(v) for v in at.split(','))))

FALL = 3
REACH = 4.0
gates = set()
seals = []
for (mx, my, mz, text) in g.loot:
    # Seal marks: '@seal:kind:hint:inputs:gates', the cells as offsets from the seal.
    if text.startswith('@seal:'):
        parts = text.split(':')
        rel = lambda s: [tuple(int(v) for v in c.split(',')) for c in s.split('/') if c]
        seals.append((parts[2], (mx, my, mz), [(mx + a, my + b, mz + c) for (a, b, c) in rel(parts[3])]))
        if opened:
            gates.update((mx + a, my + b, mz + c) for (a, b, c) in rel(parts[4]))

PASS = ('air', 'grass', 'fern', 'bush', 'flower', 'tulip', 'poppy', 'dandelion', 'orchid', 'allium', 'bluet', 'daisy', 'cornflower',
        'lily_of_the_valley', 'eyeblossom', 'torch', 'rail', 'button', 'pressure_plate', 'sign', 'banner', 'cobweb', 'vine', 'ladder',
        'lichen', 'roots', 'spore_blossom', 'lily_pad', 'sea_pickle', 'candle', 'carpet', 'sapling', 'kelp', 'seagrass', 'light',
        'litter', 'petals', 'wildflowers', 'hanging_moss', 'cave_vines', 'sprouts', 'door', 'trapdoor', 'fence_gate', 'tripwire',
        'redstone_wire', 'snow[layers=1', 'dead_bush', 'firefly', 'cactus_flower', 'leaf_litter', 'pink_petals', 'short_dry_grass',
        'tall_dry_grass', 'water', 'scaffolding')
SMALL_MUSH = ('red_mushroom', 'brown_mushroom', 'crimson_fungus', 'warped_fungus')
TALL = ('fence', 'wall', 'iron_bars', 'pane', 'chain')


def state(x, y, z):
    if not (0 <= x < g.sx and 0 <= z < g.sz) or y < 0:
        return 'stone'
    if y >= g.sy:
        return 'air'
    s = g.get(x, y, z)
    if s is None:
        return 'air' if y >= G else 'stone'
    return s.replace('minecraft:', '')


def passable(x, y, z):
    if (x, y, z) in gates:
        return True
    s = state(x, y, z)
    base = s.split('[')[0]
    if base in SMALL_MUSH:
        return True
    if base in ('mangrove_roots', 'muddy_mangrove_roots'):
        return False
    if any(k in base for k in TALL) and 'gate' not in base:
        return False
    if base.endswith('door') or base.endswith('trapdoor'):
        return True
    return any(k in s for k in PASS) and 'block' not in base and 'mushroom_stem' not in base


def water(x, y, z):
    return state(x, y, z).startswith('water')


def climbable(x, y, z):
    s = state(x, y, z)
    return s.startswith('ladder') or 'vine' in s or s.startswith('water') or s.startswith('scaffolding')


def standable(x, y, z):
    """Has a top to stand on (with the feet in the cell above)."""
    if (x, y, z) in gates:
        return False
    s = state(x, y, z)
    base = s.split('[')[0]
    if passable(x, y, z) or base.startswith('lava') or any(k in base for k in TALL):
        return False
    return True


def free(x, y, z):
    return passable(x, y, z) and passable(x, y + 1, z) and not state(x, y, z).startswith('lava')


def supported(x, y, z):
    return standable(x, y - 1, z) or water(x, y, z) or climbable(x, y, z)


def land(x, y, z):
    """Falling from (x, y, z): where the feet come to rest, or None if too far or into lava."""
    for k in range(0, FALL + 1):
        yy = y - k
        if not free(x, yy, z):
            return None
        if supported(x, yy, z):
            return (x, yy, z)
        if state(x, yy - 1, z).startswith('lava'):
            return None
    return None


def moves(p):
    x, y, z = p
    out = []
    head = passable(x, y + 2, z)
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, nz = x + dx, z + dz
        if free(nx, y, nz):
            q = land(nx, y, nz)
            if q:
                out.append(q)
        elif head and free(nx, y + 1, nz) and standable(nx, y, nz):
            out.append((nx, y + 1, nz))
    if climbable(x, y, z) and free(x, y + 1, z):
        out.append((x, y + 1, z))
    if water(x, y, z) and free(x, y - 1, z):
        out.append((x, y - 1, z))
    return out


def reach(src):
    seen = {src}
    todo = deque([src])
    while todo:
        p = todo.popleft()
        for q in moves(p):
            if q not in seen:
                seen.add(q)
                todo.append(q)
    return seen


static = '--static' in sys.argv
ok = True
if static:
    # Doors and fluids only, wherever the build stands: no walk from a start.
    out = None
    targets = []
    seals = []
elif not free(*start) or not supported(*start):
    print('start', start, 'is not a place to stand:', state(*start), '/', state(start[0], start[1] - 1, start[2]))
    sys.exit(1)
else:
    out = reach(start)
    print(f'{sys.argv[2]}: {len(out)} feet cells reachable from {start}' + (' (gates open)' if opened else ''))
# The seals and every one of their inputs are targets too, and every chest.
for (hint, at, inputs) in seals:
    targets.append((f'seal {hint}', at))
    targets += [(f'  input {i + 1}', c) for i, c in enumerate(inputs)]
for (mx, my, mz, text) in ([] if static else g.loot):
    if not text.startswith('@'):
        targets.append((f'chest {text.split("/")[-1]}', (mx, my, mz)))
def sight(p, t):
    """A clear line from the eye at feet cell p to the middle of cell t, or to the middle of its top (a
    chest sunk in an altar is opened from above); t itself excepted."""
    ex, ey, ez = p[0] + 0.5, p[1] + 1.62, p[2] + 0.5
    for top in (0.5, 0.85):
        tx, ty, tz = t[0] + 0.5, t[1] + top, t[2] + 0.5
        n = int(max(abs(tx - ex), abs(ty - ey), abs(tz - ez)) * 10) + 1
        for i in range(n):
            f = i / n
            c = (int((ex + (tx - ex) * f) // 1), int((ey + (ty - ey) * f) // 1), int((ez + (tz - ez) * f) // 1))
            if c != tuple(t) and not passable(*c):
                break
        else:
            return True
    return False


for name, t in targets:
    near = [p for p in out if ((p[0] - t[0]) ** 2 + (p[1] + 1 - t[1]) ** 2 + (p[2] - t[2]) ** 2) ** 0.5 <= REACH and sight(p, t)]
    if not near:
        best = min(out, key=lambda p: (p[0] - t[0]) ** 2 + (p[1] - t[1]) ** 2 + (p[2] - t[2]) ** 2)
        print(f'  {name} at {t}: NOT reached (closest feet {best})')
        ok = False
        continue
    back = reach(near[0])
    print(f'  {name} at {t}: reached from {near[0]}; ' + ('way back out' if start in back else 'NO WAY BACK'))
    ok &= start in back


def cells(test):
    """Every cell whose state passes test(state)."""
    import numpy as np
    for i, st in enumerate(g.palette):
        if st and test(st.replace('minecraft:', '')):
            for (x, y, z) in np.argwhere(g.a == i):
                yield int(x), int(y), int(z), st.replace('minecraft:', '')


def prop(st, key):
    if '[' not in st:
        return None
    for kv in st[st.index('[') + 1:-1].split(','):
        k, _, v = kv.partition('=')
        if k == key:
            return v
    return None


# Doors: each one walked through, both of its sides open to stand in.
STEP = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
doors = 0
for (x, y, z, st) in cells(lambda s: s.split('[')[0].endswith('_door') and 'trapdoor' not in s and 'half=lower' in s):
    doors += 1
    dx, dz = STEP.get(prop(st, 'facing'), (0, 1))
    blocked = []
    for sx, sz in ((x + dx, z + dz), (x - dx, z - dz)):
        # Standing room on that side: level with the door, or a step down; no shutter shut across it.
        if not any(free(sx, yy, sz) and supported(sx, yy, sz) for yy in (y, y - 1, y + 1)):
            blocked.append(f'{state(sx, y, sz).split("[")[0]} at {sx},{y},{sz} (head {state(sx, y + 1, sz).split("[")[0]})')
        for yy in (y, y + 1):
            if 'trapdoor' in state(sx, yy, sz) and 'open=false' in state(sx, yy, sz):
                blocked.append(f'closed trapdoor at {sx},{yy},{sz}')
    if blocked:
        print(f'  door at {x},{y},{z} ({st.split("[")[0]}): blocked by ' + '; '.join(blocked))
        ok = False
    elif out is not None and (x, y, z) not in out:
        print(f'  door at {x},{y},{z} ({st.split("[")[0]}): never reached from the start')
        ok = False


# Fluids: every water or lava cell (and every waterlogged block) held in on all four sides and below.
def fluid(st):
    base = st.split('[')[0]
    if base in ('water', 'bubble_column') or 'waterlogged=true' in st or base in ('kelp', 'kelp_plant', 'seagrass', 'tall_seagrass'):
        return 'water'
    if base == 'lava':
        return 'lava'
    return None


def flowable(x, y, z, n):
    """Whether a fluid runs into this cell: open air, or something it washes away (a plant, a carpet, a
    torch). Never a waterloggable block, a door, a sign or a ladder, as in the game's own rules."""
    base = n.split('[')[0]
    if base in ('air', 'cave_air'):
        return True
    if 'waterlogged=' in n or base.endswith(('door', 'sign', 'fence_gate', 'banner')) or base in ('ladder', 'sugar_cane'):
        return False
    return passable(x, y, z)


def lands(x, y, z, kind):
    """Where water (or lava) falling from (x, y, z) comes to rest: True if into its own kind."""
    while y > 0 and fluid(state(x, y, z)) is None and flowable(x, y, z, state(x, y, z)):
        y -= 1
    return fluid(state(x, y, z)) == kind


leaks = 0
for (x, y, z, st) in cells(lambda s: fluid(s) is not None):
    kind = fluid(st)

    def leak(where, what):
        global leaks, ok
        if leaks < 40:
            print(f'  {kind} at {x},{y},{z} {what} at {where}')
        ok = False
        leaks += 1

    below = state(x, y - 1, z)
    if fluid(below) not in (None, kind):
        leak(f'{x},{y - 1},{z}', f'meets {fluid(below)}')
        continue
    if fluid(below) is None and flowable(x, y - 1, z, below):
        # With room beneath, it falls (and only spreads once it lands): fine if it lands in its own pool.
        if not lands(x, y - 1, z, kind):
            leak(f'{x},{y - 1},{z}', 'falls out onto the ground')
        continue
    for (dx, dz) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = state(x + dx, y, z + dz)
        other = fluid(n)
        if other == kind:
            continue
        if other is not None:
            leak(f'{x + dx},{y},{z + dz}', f'meets {other}')
        elif flowable(x + dx, y, z + dz, n) and not lands(x + dx, y - 1, z + dz, kind):
            leak(f'{x + dx},{y},{z + dz}', f'runs out into {n.split("[")[0]}')
print(f'{doors} doors checked; {leaks} fluid leaks')
print('ALL OK' if ok else 'PROBLEMS')

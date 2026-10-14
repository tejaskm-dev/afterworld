"""Afterworld's architecture kit: the parts that make a build look built rather than piled.

Columns with bases and capitals, round arches, overhanging roofs, domes, framed windows, lanterns
on chains, furniture, weathering and snow. Everything works on a vox.Grid. Block states are written
in full (placed structures do not update their neighbours), and connect() finishes fences, panes,
walls and bars so they join the way they would if a player had placed them.
"""
import math
import numpy as np
from vox import hash3

FACE = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
OPP = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
CW = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}


def stairs(g, x, y, z, mat, facing, half='bottom', shape='straight'):
    g.set(x, y, z, f'{mat}_stairs[facing={facing},half={half},shape={shape}]')


def slab(g, x, y, z, mat, typ='bottom'):
    g.set(x, y, z, f'{mat}_slab[type={typ}]')


def lantern(g, x, y, z, hanging=False, soul=False):
    g.set(x, y, z, f'{"soul_lantern" if soul else "lantern"}[hanging={"true" if hanging else "false"}]')


def chain(g, x, y0, y1, z):
    for y in range(min(y0, y1), max(y0, y1) + 1):
        g.set(x, y, z, 'iron_chain[axis=y]')


def hang_lantern(g, x, y_top, z, length, soul=False):
    """A lantern hanging on a chain of `length` links below y_top."""
    if length > 0:
        chain(g, x, y_top - length + 1, y_top, z)
    lantern(g, x, y_top - length, z, hanging=True, soul=soul)


def log(g, x, y, z, mat, axis='y'):
    g.set(x, y, z, f'{mat}[axis={axis}]')


def leaves(g, x, y, z, mat='oak_leaves'):
    if g.get(x, y, z) in (None, 'minecraft:air'):
        g.set(x, y, z, f'{mat}[persistent=true]')


# ---------------------------------------------------------------- columns and arches

def column(g, cx, cz, y0, height, body, base=None, cap=None, wide=False, top_slab=None):
    """A column standing on y0. `wide` makes it 3x3; base and capital are rings of stairs."""
    r = 1 if wide else 0
    g.fill(cx - r, y0, cz - r, cx + r, y0 + height - 1, cz + r, body)
    if base:
        _ring_stairs(g, cx, cz, y0, r + 1, base, 'bottom')
    if cap:
        _ring_stairs(g, cx, cz, y0 + height - 1, r + 1, cap, 'top')
    if top_slab:
        g.fill(cx - r - 1, y0 + height, cz - r - 1, cx + r + 1, y0 + height, cz + r + 1, top_slab)


def _ring_stairs(g, cx, cz, y, r, mat, half):
    """Stairs all round a square of radius r, facing inward (so they read as a moulding)."""
    for d in range(-r, r + 1):
        stairs(g, cx + d, y, cz - r, mat, 'south', half)
        stairs(g, cx + d, y, cz + r, mat, 'north', half)
        stairs(g, cx - r, y, cz + d, mat, 'east', half)
        stairs(g, cx + r, y, cz + d, mat, 'west', half)
    for (sx, sz), (f1, sh) in {(-1, -1): ('south', 'outer_left'), (1, -1): ('south', 'outer_right'),
                                (-1, 1): ('north', 'outer_right'), (1, 1): ('north', 'outer_left')}.items():
        stairs(g, cx + sx * r, y, cz + sz * r, mat, f1, half, sh)


def arch(g, a, b, y_spring, rise, mat, axis='x', at=0, fill_above=None, top=None, depth=1):
    """A round arch spanning from a to b (exclusive pillar faces) along `axis`, in the plane
    axis-coordinate `at` (z for an x-arch). Stairs follow the curve; above it, `fill_above` fills
    the spandrels up to `top`."""
    span = b - a - 1
    if span <= 0:
        return
    mid = (a + b) / 2.0
    half_w = span / 2.0
    for k in range(depth):
        plane = at + k
        for i in range(a + 1, b):
            t = (i - mid) / (half_w + 0.5)
            h = rise * math.sqrt(max(0.0, 1 - t * t))
            yy = y_spring + int(round(h))
            # Underside: an upside-down stair facing away from the centre makes the curve.
            facing = ('west' if i < mid else 'east') if axis == 'x' else ('north' if i < mid else 'south')
            if abs(i - mid) < 0.6:
                xyz = (i, yy, plane) if axis == 'x' else (plane, yy, i)
                g.set(*xyz, f'{mat}_slab[type=top]')
            else:
                xyz = (i, yy, plane) if axis == 'x' else (plane, yy, i)
                g.set(*xyz, f'{mat}_stairs[facing={facing},half=top,shape=straight]')
            if fill_above and top is not None:
                for y in range(yy + 1, top + 1):
                    xyz = (i, y, plane) if axis == 'x' else (plane, y, i)
                    g.set(*xyz, fill_above)


# ---------------------------------------------------------------- roofs

def gable(g, x0, z0, x1, z1, y, mat, axis='x', overhang=1, gable_fill=None, ridge=True, slab_mat=None):
    """A pitched roof over the box; the ridge runs along `axis`. Stairs make each slope; the gable
    ends are filled with `gable_fill`. Returns the ridge height."""
    slab_mat = slab_mat or mat
    if axis == 'x':
        w = z1 - z0
        steps = (w + 2) // 2 + overhang
        for k in range(steps):
            yy = y + k
            za, zb = z0 - overhang + k, z1 + overhang - k
            if za > zb:
                break
            for x in range(x0 - overhang, x1 + overhang + 1):
                if za == zb:
                    g.set(x, yy, za, f'{slab_mat}_slab[type=bottom]')
                    continue
                stairs(g, x, yy, za, mat, 'south')
                stairs(g, x, yy, zb, mat, 'north')
            if gable_fill and za + 1 <= zb - 1:
                for x in (x0, x1):
                    g.fill(x, yy, za + 1, x, yy, zb - 1, gable_fill)
        return y + steps
    else:
        w = x1 - x0
        steps = (w + 2) // 2 + overhang
        for k in range(steps):
            yy = y + k
            xa, xb = x0 - overhang + k, x1 + overhang - k
            if xa > xb:
                break
            for z in range(z0 - overhang, z1 + overhang + 1):
                if xa == xb:
                    g.set(xa, yy, z, f'{slab_mat}_slab[type=bottom]')
                    continue
                stairs(g, xa, yy, z, mat, 'east')
                stairs(g, xb, yy, z, mat, 'west')
            if gable_fill and xa + 1 <= xb - 1:
                for z in (z0, z1):
                    g.fill(xa + 1, yy, z, xb - 1, yy, z, gable_fill)
        return y + steps


def hip(g, x0, z0, x1, z1, y, mat, overhang=1, cap=None):
    """A hipped roof: every side slopes in. Corners get outer stairs."""
    k = 0
    while True:
        xa, xb = x0 - overhang + k, x1 + overhang - k
        za, zb = z0 - overhang + k, z1 + overhang - k
        if xa > xb or za > zb:
            break
        yy = y + k
        if xa == xb or za == zb:
            g.fill(xa, yy, za, xb, yy, zb, f'{cap or mat}_slab[type=bottom]')
            break
        for x in range(xa + 1, xb):
            stairs(g, x, yy, za, mat, 'south')
            stairs(g, x, yy, zb, mat, 'north')
        for z in range(za + 1, zb):
            stairs(g, xa, yy, z, mat, 'east')
            stairs(g, xb, yy, z, mat, 'west')
        stairs(g, xa, yy, za, mat, 'south', 'bottom', 'outer_left')
        stairs(g, xb, yy, za, mat, 'south', 'bottom', 'outer_right')
        stairs(g, xa, yy, zb, mat, 'north', 'bottom', 'outer_right')
        stairs(g, xb, yy, zb, mat, 'north', 'bottom', 'outer_left')
        k += 1
    return y + k


def dome(g, cx, cy, cz, r, mat, inner='air', ribs=None, ribs_n=8, oculus=0):
    """A hemisphere shell (with optional stone ribs and an open oculus at the top)."""
    ri = int(math.ceil(r))
    for x in range(int(cx) - ri - 1, int(cx) + ri + 2):
        for z in range(int(cz) - ri - 1, int(cz) + ri + 2):
            for y in range(int(cy), int(cy) + ri + 1):
                d = math.sqrt((x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2)
                if d <= r + 0.35:
                    if d > r - 0.9:
                        if oculus and math.hypot(x - cx, z - cz) < oculus:
                            continue
                        st = mat
                        if ribs:
                            ang = (math.atan2(z - cz, x - cx) / (2 * math.pi) * ribs_n) % 1
                            if ang < 0.12 or ang > 0.88:
                                st = ribs
                        g.set(x, y, z, st)
                    elif inner is not None:
                        g.set(x, y, z, inner)


def spire(g, cx, cz, y0, height, r0, mat, tip=None):
    """A tapering spire (cone) with an optional finial."""
    g.cone(cx, cz, y0, height, r0, mat, 0.25)
    if tip:
        g.set(cx, y0 + height, cz, tip)


# ---------------------------------------------------------------- walls, windows, doors

def window(g, x, y, z, face, w=1, h=2, glass='glass_pane', sill=None, lintel=None, light=None):
    """A window of w x h in the wall at (x..x+w-1 or z.., y..), opening toward `face`.
    A sill (stair/slab) outside and a lintel above; a light block just inside if asked."""
    dx, dz = FACE[face]
    along = (1, 0) if dx == 0 else (0, 1)
    for i in range(w):
        for j in range(h):
            g.set(x + along[0] * i, y + j, z + along[1] * i, glass)
        if sill:
            stairs(g, x + along[0] * i + dx, y - 1, z + along[1] * i + dz, sill, OPP[face], 'top')
        if lintel:
            g.set(x + along[0] * i, y + h, z + along[1] * i, lintel)
        if light:
            g.set(x + along[0] * i - dx, y, z + along[1] * i - dz, light)


def door(g, x, y, z, mat, facing, hinge='left'):
    g.set(x, y, z, f'{mat}_door[facing={facing},half=lower,hinge={hinge},open=false]')
    g.set(x, y + 1, z, f'{mat}_door[facing={facing},half=upper,hinge={hinge},open=false]')


def shutters(g, x, y, z, face, mat='spruce', h=2):
    """Open trapdoors either side of a one-wide window on a wall facing `face`."""
    dx, dz = FACE[face]
    along = (1, 0) if dx == 0 else (0, 1)
    for s, side in ((-1, 'a'), (1, 'b')):
        for j in range(h):
            fx, fz = x + along[0] * s + dx, z + along[1] * s + dz
            g.set(fx, y + j, fz, f'{mat}_trapdoor[facing={face},half=bottom,open=true]')


# ---------------------------------------------------------------- furniture

def table(g, x, y, z, top='spruce_pressure_plate', leg='spruce_fence'):
    g.set(x, y, z, leg)
    g.set(x, y + 1, z, top)


def chair(g, x, y, z, mat, facing):
    stairs(g, x, y, z, mat, OPP[facing])


def bed(g, x, y, z, color, facing):
    """facing: the direction the head points."""
    dx, dz = FACE[facing]
    g.set(x, y, z, f'{color}_bed[facing={facing},part=foot,occupied=false]')
    g.set(x + dx, y, z + dz, f'{color}_bed[facing={facing},part=head,occupied=false]')


def bookshelf_wall(g, x0, y0, z0, x1, y1, z1, seed=0):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                r = hash3(x, y, z, seed)
                g.set(x, y, z, 'bookshelf' if r < 0.78 else ('chiseled_bookshelf[facing=north]' if r < 0.9 else 'barrel[facing=up]'))


def potted(g, x, y, z, plant='fern'):
    g.set(x, y, z, f'potted_{plant}')


# ---------------------------------------------------------------- finishing passes

def weather(g, mapping, rate=0.25, seed=3, region=None):
    """Swaps a share of blocks for weathered variants: mapping = {'stone_bricks': ['cracked_stone_bricks', ...]}."""
    for base, variants in mapping.items():
        pid = g.index.get('minecraft:' + base)
        if pid is None:
            continue
        idx = np.argwhere(g.a == pid)
        for (x, y, z) in idx:
            if region and not (region[0] <= x <= region[3] and region[1] <= y <= region[4] and region[2] <= z <= region[5]):
                continue
            r = hash3(int(x), int(y), int(z), seed)
            if r < rate:
                g.a[x, y, z] = g.pid(variants[int(r / rate * len(variants)) % len(variants)])


def snow(g, depth=1, rate=1.0, seed=11):
    """Snow layers on every exposed, solid, full-topped surface."""
    sx, sy, sz = g.a.shape
    full = _full_top_ids(g)
    for x in range(sx):
        for z in range(sz):
            col = g.a[x, :, z]
            ys = np.nonzero(col)[0]
            if len(ys) == 0:
                continue
            y = int(ys.max())
            if y + 1 >= sy or col[y] not in full:
                continue
            if hash3(x, y, z, seed) <= rate:
                g.set(x, y + 1, z, f'snow[layers={depth}]')


def _full_top_ids(g):
    out = set()
    for i, s in enumerate(g.palette):
        if i == 0 or not s.startswith('minecraft:'):
            continue
        n = s[10:].split('[')[0]
        if n in ('air', 'light', 'water', 'lava') or any(k in n for k in ('stairs', 'slab', 'fence', 'pane', 'wall', 'lantern', 'chain', 'door',
                'torch', 'candle', 'carpet', 'button', 'plate', 'leaves', 'glass', 'bars', 'flower', 'grass', 'fern', 'snow', 'sign', 'banner',
                'trapdoor', 'rod', 'campfire', 'bed', 'pot', 'head', 'skull', 'vine', 'roots', 'lichen', 'bell', 'cluster')):
            continue
        out.add(i)
    return out


CONNECTS = ('fence', 'pane', 'wall', 'iron_bars')


# Small things that must never stand in a doorway: posts and rails, shutters, lamps, pots and
# workbenches. (Chests stay: their loot is marked on them.)
DOORWAY_CLUTTER = ('fence', '_wall', 'trapdoor', 'lantern', 'chain', 'pane', 'iron_bars', 'potted_', 'flower_pot', 'barrel', 'leaves',
                   'campfire', 'candle', 'lectern', 'composter', 'hay_block', 'cauldron', 'anvil', 'grindstone', 'bell', 'crafting_table',
                   'fletching_table', 'smithing_table', 'cartography_table', 'loom', 'stonecutter', 'brewing_stand', 'banner')


def clear_doorways(g):
    """Nothing blocks a door: whatever clutter stands in the cell just outside or just inside it, at
    foot or head height, is taken away (a lamp post or fence with everything stacked on it)."""
    for i, st in enumerate(g.palette):
        if not st or '_door[' not in st or 'trapdoor' in st or 'half=lower' not in st:
            continue
        facing = st[st.index('facing=') + 7:].split(',')[0].split(']')[0]
        dx, dz = FACE[facing]
        for (x, y, z) in np.argwhere(g.a == i):
            for sx, sz in ((x + dx, z + dz), (x - dx, z - dz)):
                if not (0 <= sx < g.sx and 0 <= sz < g.sz):
                    continue
                for yy in (y, y + 1):
                    here = g.get(sx, yy, sz)
                    if not here or 'fence_gate' in here or not any(k in here for k in DOORWAY_CLUTTER):
                        continue
                    k = yy
                    while k < g.sy:
                        above = g.get(sx, k, sz)
                        if k > yy and (not above or not any(j in above for j in ('fence', '_wall', 'lantern', 'chain', 'candle'))):
                            break
                        g.set(sx, k, sz, 'air')
                        k += 1


def connect(g):
    """Fences, panes, walls and bars join their neighbours (placed structures do not update). Every
    build's last pass, so it first clears its doorways (see clear_doorways)."""
    clear_doorways(g)
    families = {}
    for i, s in enumerate(g.palette):
        if i == 0:
            continue
        name = s.split('[')[0][10:] if s.startswith('minecraft:') else s.split('[')[0]
        if any(name.endswith(k) for k in ('_fence', '_pane', '_wall')) or name in ('iron_bars', 'glass_pane'):
            if 'gate' in name:
                continue
            families[i] = name
    if not families:
        return
    solid = np.zeros(len(g.palette), bool)
    full = _full_top_ids(g)
    for i in full:
        solid[i] = True
    for i in families:
        solid[i] = True
    n0 = len(solid)

    def joins(v):
        # Variants added during this pass are connected fences/panes/walls: they join too.
        return True if v >= n0 else bool(solid[v])

    for i, name in list(families.items()):
        cells = np.argwhere(g.a == i)
        kind = 'wall' if name.endswith('_wall') else 'fence' if name.endswith('_fence') else 'pane'
        for (x, y, z) in cells:
            props = {}
            for face, (dx, dz) in FACE.items():
                nx, nz = x + dx, z + dz
                ok = 0 <= nx < g.sx and 0 <= nz < g.sz and joins(int(g.a[nx, y, nz]))
                if kind == 'wall':
                    props[face] = 'low' if ok else 'none'
                else:
                    props[face] = 'true' if ok else 'false'
            if kind == 'wall':
                above = y + 1 < g.sy and g.a[x, y + 1, z] != 0 and joins(int(g.a[x, y + 1, z]))
                straight = (props['north'] != 'none' and props['south'] != 'none' and props['east'] == 'none' and props['west'] == 'none') or \
                           (props['east'] != 'none' and props['west'] != 'none' and props['north'] == 'none' and props['south'] == 'none')
                props['up'] = 'false' if straight and not above else 'true'
            state = 'minecraft:' + name + '[' + ','.join(f'{k}={v}' for k, v in sorted(props.items())) + ']'
            g.a[x, y, z] = g.pid(state)


def scatter_ground(g, y, x0, z0, x1, z1, choices, rate, seed=5):
    """Plants and pebbles on top of solid ground at layer y (only where the cell above is empty)."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if g.get(x, y + 1, z) not in (None, 'minecraft:air'):
                continue
            if not g.is_solid(x, y, z):
                continue
            r = hash3(x, y, z, seed)
            if r < rate:
                g.set(x, y + 1, z, choices[int(r / rate * len(choices)) % len(choices)])


# ---------------------------------------------------------------- keepers and waystones

_SOFT = ('air', 'cave_air', 'short_grass', 'tall_grass', 'fern', 'large_fern', 'dandelion', 'poppy', 'cornflower', 'azure_bluet',
         'oxeye_daisy', 'lily_of_the_valley', 'white_tulip', 'allium', 'blue_orchid', 'snow', 'dead_bush', 'short_dry_grass',
         'sweet_berry_bush', 'moss_carpet')


def _soft(state):
    if state is None:
        return True
    name = state.split('[')[0].replace('minecraft:', '')
    return name in _SOFT


def standable(g, x, y, z, height=2):
    if not (0 <= x < g.sx and 0 <= z < g.sz and 1 <= y < g.sy - height):
        return False
    below = g.get(x, y - 1, z)
    if _soft(below) or 'slab[type=bottom]' in below or 'stairs' in below or 'fence' in below or 'wall' in below or 'water' in below \
            or 'carpet' in below or 'lantern' in below or 'leaves' in below:
        return False
    return all(_soft(g.get(x, y + k, z)) for k in range(height))


def spot(g, x, z, y0, y1, r=5, height=2):
    """The standable cell nearest (x, z), at the lowest free height between y0 and y1."""
    best = None
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for y in range(y0, y1 + 1):
                if standable(g, x + dx, y, z + dz, height):
                    d = dx * dx + dz * dz + 0.01 * (y - y0)
                    if best is None or d < best[0]:
                        best = (d, x + dx, y, z + dz)
                    break
    if best is None:
        raise ValueError(f'no room to stand near {x},{z} between y {y0} and {y1}')
    return best[1], best[2], best[3]


def keeper(g, npc, x, z, y0, y1, facing, r=5):
    """Stands a named keeper (an Npc id) on the nearest free spot."""
    x, y, z = spot(g, x, z, y0, y1, r)
    g.set(x, y, z, 'air')
    g.set(x, y + 1, z, 'air')
    g.mark(x, y, z, f'npc:{npc}:{facing}')
    return x, y, z


def waystone(g, x, z, y0, y1, r=5):
    """Sets a working waystone on the nearest free spot, with room above for its crystal."""
    x, y, z = spot(g, x, z, y0, y1, r, height=3)
    g.set(x, y, z, 'afterworld:waystone[natural=true]')
    g.set(x, y + 1, z, 'air')
    g.set(x, y + 2, z, 'air')
    return x, y, z

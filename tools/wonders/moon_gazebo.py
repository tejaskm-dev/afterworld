"""The Moonwell Gazebo: a white marble pavilion on a round, stepped podium. Four great columns
carry round arches and a corniced, stepped roof crowned by a lantern cupola; under it, a crescent
moon of light hangs on chains over a little altar. Lanterns everywhere, candles on the altar, and
in the cold lands a soft cap of snow on every ledge."""
import math
from vox import Grid, hash3
import kit

W, H, D = 32, 48, 32
G = 3            # first layer above the ground line
C = 16           # centre


def ring_steps(g, y, r, mat, inner_fill=None):
    """A round tier: every cell inside r gets `inner_fill`; the rim gets stairs facing the centre."""
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if d <= r - 0.6 and inner_fill:
                g.set(x, y, z, inner_fill)
            elif d <= r + 0.4:
                dx, dz = C - (x + 0.5), C - (z + 0.5)
                facing = ('east' if dx > 0 else 'west') if abs(dx) > abs(dz) else ('south' if dz > 0 else 'north')
                kit.stairs(g, x, y, z, mat, facing)


def podium(g, snow):
    # Clear the site and lay a foundation under it.
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if d <= 15.4:
                g.fill(x, 0, z, x, G - 2, z, 'stone')
                g.set(x, G - 1, z, 'snow_block' if snow else 'grass_block')
                g.fill(x, G, z, x, H - 1, z, 'air')
    ring_steps(g, G, 14.2, 'polished_diorite', 'polished_diorite')
    ring_steps(g, G + 1, 12.2, 'quartz', 'calcite')
    # Floor: smooth quartz with a ring of chiseled quartz and a diorite compass star.
    for x in range(W):
        for z in range(D):
            d = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if d <= 11.4:
                st = 'smooth_quartz'
                if 5.2 < d <= 6.2:
                    st = 'chiseled_quartz_block'
                ang = math.atan2(z + 0.5 - C, x + 0.5 - C)
                if d <= 5.2 and abs(math.sin(ang * 4)) < 0.22:
                    st = 'polished_diorite'
                g.set(x, G + 1, z, st)


def columns_and_arches(g):
    ys = G + 2
    height = 16
    posts = [(C - 6, C - 6), (C - 6, C + 5), (C + 5, C - 6), (C + 5, C + 5)]
    for cx, cz in posts:
        kit.column(g, cx, cz, ys, height, 'quartz_pillar[axis=y]', base='quartz', cap='quartz', wide=True)
        g.fill(cx - 1, ys + height, cz - 1, cx + 1, ys + height, cz + 1, 'chiseled_quartz_block')
    top = ys + height          # the beam layer
    spring = ys + 9
    # Arches between the columns on all four sides, three blocks deep like the columns.
    lo, hi = C - 6 + 1, C + 5 - 1
    for plane in (C - 7, C + 4):
        kit.arch(g, lo, hi, spring, 4, 'quartz', axis='x', at=plane, fill_above='smooth_quartz', top=top, depth=3)
        kit.arch(g, lo, hi, spring, 4, 'quartz', axis='z', at=plane, fill_above='smooth_quartz', top=top, depth=3)
    # A second, inner arch of calcite one step in, so each opening has a moulded double frame.
    for plane, inward in ((C - 7, 1), (C + 4, -1)):
        inner_plane = plane + (3 if inward > 0 else -1)
        kit.arch(g, lo, hi, spring - 1, 4, 'diorite', axis='x', at=inner_plane, depth=1)
        kit.arch(g, lo, hi, spring - 1, 4, 'diorite', axis='z', at=inner_plane, depth=1)
    # Lanterns hanging from each arch's crown.
    for (x, z) in ((C - 0.5, C - 6), (C - 0.5, C + 5), (C - 6, C - 0.5), (C + 5, C - 0.5)):
        xi, zi = int(round(x)), int(round(z))
        kit.hang_lantern(g, xi, spring + 3, zi, 1)
    # The beam all round, and a cornice of upside-down stairs one block out.
    for x in range(C - 7, C + 7):
        for z in range(C - 7, C + 7):
            edge = x in (C - 7, C + 6) or z in (C - 7, C + 6)
            inner = (C - 4 <= x <= C + 3) and (C - 4 <= z <= C + 3)
            if not inner:
                g.set(x, top, z, 'smooth_quartz')
                g.set(x, top + 1, z, 'quartz_block' if edge else 'smooth_quartz')
    for d in range(C - 8, C + 8):
        kit.stairs(g, d, top + 1, C - 8, 'quartz', 'south', 'top')
        kit.stairs(g, d, top + 1, C + 7, 'quartz', 'north', 'top')
        kit.stairs(g, C - 8, top + 1, d, 'quartz', 'east', 'top')
        kit.stairs(g, C + 7, top + 1, d, 'quartz', 'west', 'top')
        # Dentils: a row of little blocks under the cornice, every other block.
        if d % 2 == 0:
            for (x, z) in ((d, C - 7), (d, C + 6), (C - 7, d), (C + 6, d)):
                if C - 7 <= x <= C + 6 and C - 7 <= z <= C + 6:
                    g.set(x, top - 1, z, 'chiseled_quartz_block' if (x + z) % 4 == 0 else 'quartz_bricks')
    # Lanterns on chains all round the cornice.
    for (x, z) in ((C - 8, C - 8), (C + 7, C - 8), (C - 8, C + 7), (C + 7, C + 7), (C - 0, C - 8), (C - 1, C + 7), (C - 8, C - 1), (C + 7, C)):
        kit.hang_lantern(g, x, top, z, 2)
    return top + 1


def roof(g, base):
    """Two stepped tiers, then a ribbed dome, then a lantern cupola."""
    y = base + 1
    for inset, mat in ((1, 'smooth_quartz'), (3, 'quartz_block')):
        x0, x1 = C - 8 + inset, C + 7 - inset
        g.fill(x0, y, x0, x1, y, x1, mat)
        for d in range(x0, x1 + 1):
            kit.stairs(g, d, y, x0 - 1, 'quartz', 'south')
            kit.stairs(g, d, y, x1 + 1, 'quartz', 'north')
            kit.stairs(g, x0 - 1, y, d, 'quartz', 'east')
            kit.stairs(g, x1 + 1, y, d, 'quartz', 'west')
        y += 1
    kit.dome(g, C - 0.5, y, C - 0.5, 5.6, 'smooth_quartz', inner='air', ribs='quartz_bricks', ribs_n=8)
    y += 5
    # Cupola: four slender posts, a slab cap and a lantern finial.
    for (px, pz) in ((C - 3, C - 3), (C - 3, C + 2), (C + 2, C - 3), (C + 2, C + 2)):
        g.fill(px, y, pz, px, y + 2, pz, 'quartz_pillar[axis=y]')
    g.fill(C - 3, y + 3, C - 3, C + 2, y + 3, C + 2, 'smooth_quartz_slab[type=bottom]')
    kit.hang_lantern(g, C - 1, y + 2, C - 1, 0)
    g.set(C - 1, y + 4, C - 1, 'end_rod[facing=up]')
    g.set(C, y + 4, C, 'end_rod[facing=up]')
    g.set(C - 1, y + 5, C, 'lantern[hanging=false]')
    # Ceiling inside: smooth quartz under the beam.
    g.fill(C - 4, base, C - 4, C + 3, base, C + 3, 'smooth_quartz')


def crescent(g, ceiling):
    """A crescent moon of light in the x-y plane, hung on two chains."""
    cx, cy = C - 0.5, G + 11
    pts = []
    for x in range(C - 7, C + 7):
        for y in range(G + 4, G + 19):
            outer = math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= 5.4
            inner = math.hypot(x + 0.5 - (cx + 2.3), y + 0.5 - (cy + 1.0)) <= 4.5
            if outer and not inner:
                pts.append((x, y))
    for (x, y) in pts:
        g.set(x, y, C - 1, 'sea_lantern')
        g.set(x, y, C, 'sea_lantern')
    # Chains from the ceiling to the two horns.
    top = max(y for _, y in pts)
    horns = sorted([p for p in pts if p[1] >= top - 1], key=lambda p: p[0])
    for (x, y) in (horns[0], horns[-1]):
        kit.chain(g, x, y + 1, ceiling - 1, C - 1)


def altar(g):
    y = G + 2
    g.fill(C - 2, y, C - 2, C + 1, y, C + 1, 'polished_diorite')
    for d in range(C - 2, C + 2):
        kit.stairs(g, d, y, C - 3, 'polished_diorite', 'south')
        kit.stairs(g, d, y, C + 2, 'polished_diorite', 'north')
        kit.stairs(g, C - 3, y, d, 'polished_diorite', 'east')
        kit.stairs(g, C + 2, y, d, 'polished_diorite', 'west')
    g.set(C - 1, y + 1, C - 1, 'chiseled_quartz_block')
    g.set(C, y + 1, C, 'chiseled_quartz_block')
    g.set(C - 1, y + 1, C, 'white_candle[candles=3,lit=true]')
    g.set(C, y + 1, C - 1, 'white_candle[candles=2,lit=true]')
    g.set(C - 1, y + 2, C - 1, 'amethyst_cluster[facing=up]')
    g.chest(C + 1, y + 1, C - 2, 'west', 'afterworld:chests/shrine_cache')


def grounds(g, snow):
    """Lantern posts round the podium, a path to the stair, and the garden."""
    for k in range(8):
        a = k / 8 * 2 * math.pi + math.pi / 8
        x = int(round(C - 0.5 + math.cos(a) * 13.3))
        z = int(round(C - 0.5 + math.sin(a) * 13.3))
        g.set(x, G + 1, z, 'quartz_pillar[axis=y]')
        g.set(x, G + 2, z, 'lantern[hanging=false]')
    for z in range(D - 1, D):
        for x in range(C - 2, C + 2):
            g.set(x, G - 1, z, 'gravel' if hash3(x, 1, z, 4) < 0.5 else 'polished_diorite')
    plants = (['short_grass', 'fern', 'lily_of_the_valley', 'oxeye_daisy', 'white_tulip'] if not snow else ['fern'])
    kit.scatter_ground(g, G - 1, 0, 0, W - 1, D - 1, ['minecraft:' + p for p in plants], 0.22 if not snow else 0.05)


def build(snow=False):
    g = Grid(W, H, D)
    podium(g, snow)
    beam = columns_and_arches(g)
    roof(g, beam)
    crescent(g, beam)
    altar(g)
    grounds(g, snow)
    kit.weather(g, {'calcite': ['diorite', 'calcite'], 'polished_diorite': ['diorite']}, rate=0.12)
    if snow:
        kit.snow(g, depth=1, rate=0.92)
    kit.keeper(g, 'selene', C - 1, C + 5, G + 2, G + 3, 'south')
    kit.waystone(g, C + 4, D - 3, G, G + 1)
    kit.connect(g)
    return g


def build_snow():
    return build(snow=True)

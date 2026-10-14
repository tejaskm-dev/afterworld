"""The second bestiary: 38 creatures, merged into gen_mobs.py like mobs_more.

Each spec builds a Part tree whose part names drive the shared animated model on the client
(GenericCreatureModel): head, jaw, neck, body, leg_fl/fr/bl/br (four legs), leg_l/leg_r and
arm_l/arm_r (two legs and arms), leg_l1..4/leg_r1..4 (many legs), wing_l/wing_r, tail/tail2/tail3,
tent1..n (tentacles, vines, tatters), claw_l/claw_r, ear_l/ear_r, fin_l/fin_r, lure, cap.
Coordinates are model pixels, y down, root pivot at the feet (0, 24, 0).
"""
import math
import os

from gen_models import Box, Part

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(HERE, '..', 'src', 'client', 'java', 'dev', 'afterworld', 'client', 'model', 'gen')


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def jitter(rng, c, amt):
    d = rng.randint(-amt, amt)
    return (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), c[3])


def mixc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


# ---------------------------------------------------------------- materials

def C(v):
    return hexc(v) if isinstance(v, str) else v


def paint(base, belly=None, top=None, rows=0, noise=6, spots=None, density=0, stripes=None, every=4, mottle=None):
    """A fur/skin painter: base colour, a belly on the underside (and its lowest rows), an optional
    top colour, scattered spots, stripes across the sides, and soft mottling."""
    base, belly, top, spots, stripes, mottle = (C(v) if v else None for v in (base, belly, top, spots, stripes, mottle))

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    c = base
                    if face == 'down' and belly:
                        c = belly
                    elif face == 'up' and top:
                        c = top
                    elif belly and rows and face not in ('up', 'down') and y >= h - rows:
                        c = belly
                    if stripes and face not in ('down',) and (x + y // 2) % every == 0 and h > 2:
                        c = stripes
                    if mottle and rng.random() < 0.18:
                        c = mottle
                    F.put(x0 + x, y0 + y, jitter(rng, c, noise))
            if spots and density:
                for _ in range(max(1, w * h * density // 100)):
                    F.put(x0 + rng.randrange(w), y0 + rng.randrange(h), jitter(rng, spots, 4))
    return f


def eyes(F, face, color, glow=None, row=1, inset=1, size=1, wide=False):
    x0, y0, w, h = F.rect[face]
    if w < 2 or h < 1:
        return
    xs = (x0 + inset, x0 + w - 1 - inset - (size - 1))
    for ex in xs:
        for dx in range(size):
            for dy in range(size):
                if 0 <= row + dy < h:
                    F.put(ex + dx, y0 + row + dy, C(color))
                    if glow:
                        F.lit(ex + dx, y0 + row + dy, C(glow))


def head(base, eye='#100C0A', glow=None, row=1, inset=1, size=1, side=False, **kw):
    """A head: painted like the body, with eyes on its front (or its sides, for prey animals)."""
    p = paint(base, **kw)

    def f(F, rng):
        p(F, rng)
        if side:
            for face in ('east', 'west'):
                x0, y0, w, h = F.rect[face]
                ex = x0 + (1 if face == 'east' else w - 2)
                F.put(ex, y0 + row, C(eye))
                if glow:
                    F.lit(ex, y0 + row, C(glow))
        else:
            eyes(F, 'north', eye, glow, row, inset, size)
    return f


def glowing(base, light, noise=6, every=0, alpha=255):
    """Something that shines: lit all over (every=0) or in a scatter of bright points."""
    base, light = C(base), C(light)

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    F.put(x0 + x, y0 + y, jitter(rng, base, noise))
                    if every == 0 or rng.random() < 1.0 / every:
                        F.lit(x0 + x, y0 + y, (light[0], light[1], light[2], alpha))
    return f


def shell(base, line, crack=None, plates=4):
    """Armoured plates: dark seams in a grid, and (optionally) glowing cracks."""
    base, line, crack = C(base), C(line), C(crack) if crack else None

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    seam = (x % plates == 0 or y % plates == 0) and face != 'down'
                    c = line if seam else base
                    if face == 'up' and (x + y) % 7 == 0:
                        c = shade(base, 1.18)
                    F.put(x0 + x, y0 + y, jitter(rng, c, 5))
                    if crack and seam and rng.random() < 0.45:
                        F.put(x0 + x, y0 + y, crack)
                        F.lit(x0 + x, y0 + y, (crack[0], crack[1], crack[2], 230))
    return f


def crystal(base, light, glow_every=5):
    base, light = C(base), C(light)

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    band = ((x + y) // 2) % 3
                    c = shade(base, (0.85, 1.0, 1.2)[band])
                    F.put(x0 + x, y0 + y, jitter(rng, c, 4))
                    if rng.random() < 1.0 / glow_every:
                        F.put(x0 + x, y0 + y, light)
                        F.lit(x0 + x, y0 + y, (light[0], light[1], light[2], 200))
    return f


def feathers(base, tip=None, bar=None, belly=None):
    base, tip, bar, belly = (C(v) if v else None for v in (base, tip, bar, belly))

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    c = belly if (face == 'down' and belly) else base
                    if bar and (y + (x // 2) % 2) % 3 == 0 and face != 'down':
                        c = bar
                    if tip and y >= h - 1 and h > 2 and face not in ('up', 'down'):
                        c = tip
                    F.put(x0 + x, y0 + y, jitter(rng, c, 6))
    return f


def membrane(base, vein, glow=None):
    base, vein = C(base), C(vein)

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    c = vein if (x % 4 == 0 or y == 0) else base
                    F.put(x0 + x, y0 + y, jitter(rng, c, 5))
                    if glow and x % 4 == 0 and rng.random() < 0.3:
                        F.lit(x0 + x, y0 + y, C(glow))
    return f


def bark(base, moss=None, grain=None):
    base, moss = C(base), C(moss) if moss else None
    grain = C(grain) if grain else shade(base, 0.7)

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    c = grain if (x % 3 == 0 and face not in ('up', 'down')) else base
                    if moss and (face == 'up' or y < 1) and rng.random() < 0.55:
                        c = moss
                    F.put(x0 + x, y0 + y, jitter(rng, c, 6))
    return f


def metal(base, accent=None, rivet='#2A2A2A'):
    base, accent, rivet = C(base), C(accent) if accent else None, C(rivet)

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    c = base
                    if accent and rng.random() < 0.22:
                        c = accent
                    if (x == 1 or x == w - 2) and (y == 1 or y == h - 2) and w > 4 and h > 4:
                        c = rivet
                    F.put(x0 + x, y0 + y, jitter(rng, c, 5))
    return f


def teeth(white='#E8E0D0', gum='#7A2A30'):
    white, gum = C(white), C(gum)

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    F.put(x0 + x, y0 + y, white if (x % 2 == 0 and face in ('north', 'east', 'west', 'south')) else gum)
    return f


def solid(c, noise=5):
    c = C(c)

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    F.put(x0 + x, y0 + y, jitter(rng, c, noise))
    return f


def ghostly(base, light):
    """A shade: pale, fading toward its hem (alpha), with a faint glow."""
    base, light = C(base), C(light)

    def f(F, rng):
        for face, x0, y0, w, h in F.each():
            for y in range(h):
                for x in range(w):
                    a = int(230 - 120 * (y / max(1, h - 1))) if face not in ('up', 'down') else 210
                    c = jitter(rng, base, 6)
                    F.put(x0 + x, y0 + y, (c[0], c[1], c[2], a))
                    if rng.random() < 0.08:
                        F.lit(x0 + x, y0 + y, (light[0], light[1], light[2], 120))
    return f


MATERIALS = {}


def mat(name, fn):
    MATERIALS[name] = fn
    return name


# ---------------------------------------------------------------- building helpers

def P(name, pivot=(0, 0, 0), rot=(0, 0, 0), boxes=(), children=()):
    return Part(name, pivot=pivot, rot=rot, boxes=list(boxes), children=list(children))


def B(o, s, m, inflate=0.0):
    return Box(o, s, m, inflate)


def legs4(m, w, h, dx, zf, zb, hip=None, d=None):
    d = d or w
    hip = -h if hip is None else hip
    return [P(n, pivot=(sx * dx, hip, z), boxes=[B((-w / 2, 0, -d / 2), (w, h, d), m)])
            for n, sx, z in (('leg_fl', -1, zf), ('leg_fr', 1, zf), ('leg_bl', -1, zb), ('leg_br', 1, zb))]


def legs2(m, w, h, dx, hip=None, d=None):
    d = d or w
    hip = -h if hip is None else hip
    return [P('leg_l', pivot=(-dx, hip, 0), boxes=[B((-w / 2, 0, -d / 2), (w, h, d), m)]),
            P('leg_r', pivot=(dx, hip, 0), boxes=[B((-w / 2, 0, -d / 2), (w, h, d), m)])]


def arms2(m, w, h, dx, y, d=None, extra_r=()):
    d = d or w
    return [P('arm_l', pivot=(-dx, y, 0), boxes=[B((-w, -1, -d / 2), (w, h, d), m)]),
            P('arm_r', pivot=(dx, y, 0), boxes=[B((0, -1, -d / 2), (w, h, d), m)] + list(extra_r))]


def bug_legs(m, n, length, w, y, zs, spread=0.7):
    """n legs a side: thin, splayed outward and down (insects, spiders, crabs)."""
    out = []
    for i in range(n):
        z = zs[i]
        out.append(P(f'leg_l{i + 1}', pivot=(-w, y, z), rot=(0, 0, spread), boxes=[B((-length, -0.5, -0.5), (length, 1, 1), m)]))
        out.append(P(f'leg_r{i + 1}', pivot=(w, y, z), rot=(0, 0, -spread), boxes=[B((0, -0.5, -0.5), (length, 1, 1), m)]))
    return out


def wings(m, span, depth, y, z, thick=1, x=0):
    return [P('wing_l', pivot=(-x, y, z), boxes=[B((-span, 0, 0), (span, thick, depth), m)]),
            P('wing_r', pivot=(x, y, z), boxes=[B((0, 0, 0), (span, thick, depth), m)])]


def root(*children):
    return P('root', pivot=(0, 24, 0), children=list(children))


# ---------------------------------------------------------------- wildlife and beasts

def dune_strider():
    fur = mat('strider_fur', paint('#C8A46A', belly='#E8D2A0', rows=2, mottle='#B8925A'))
    face = mat('strider_head', head('#C8A46A', side=True, row=1, belly='#E8D2A0'))
    hoof = mat('strider_leg', paint('#B8925A', belly='#3A2A1A', rows=3))
    rug = mat('strider_rug', paint('#8A2A2A', stripes='#E8C060', every=3))
    hd = P('head', pivot=(0, -14, 0), rot=(-0.3, 0, 0), boxes=[B((-2.5, -4, -6), (5, 5, 8), face), B((-2, -2, -9), (4, 3, 3), fur)], children=[
        P('ear_l', pivot=(-2, -4, -1), rot=(0, 0, -0.3), boxes=[B((-1, -3, -0.5), (1, 3, 1), fur)]),
        P('ear_r', pivot=(2, -4, -1), rot=(0, 0, 0.3), boxes=[B((0, -3, -0.5), (1, 3, 1), fur)])])
    neck = P('neck', pivot=(0, -6, -8), rot=(0.3, 0, 0), boxes=[B((-2, -14, -2), (4, 15, 4), fur)], children=[hd])
    body = P('body', pivot=(0, -18, 0), boxes=[B((-5, -9, -9), (10, 9, 18), fur), B((-3.5, -13, -3), (7, 4, 7), fur), B((-5.5, -10, -2), (11, 4, 6), rug)],
             children=[neck, P('tail', pivot=(0, -7, 9), rot=(0.6, 0, 0), boxes=[B((-1, 0, 0), (2, 6, 1), fur)])])
    return root(body, *legs4(hoof, 3, 18, 3.5, -6, 6))


def snow_hare():
    fur = mat('hare_fur', paint('#F0F0F4', belly='#FFFFFF', mottle='#D8DCE4', noise=4))
    face = mat('hare_head', head('#F0F0F4', eye='#C83A5A', side=True, row=1))
    ear = mat('hare_ear', paint('#F0F0F4', top='#3A3A44', rows=0))
    hd = P('head', pivot=(0, -4, -3), boxes=[B((-1.5, -3, -3), (3, 3, 3), face)], children=[
        P('ear_l', pivot=(-1, -3, -1), rot=(-0.2, 0, -0.15), boxes=[B((-0.5, -4, -0.5), (1, 4, 1), ear)]),
        P('ear_r', pivot=(1, -3, -1), rot=(-0.2, 0, 0.15), boxes=[B((-0.5, -4, -0.5), (1, 4, 1), ear)])])
    body = P('body', pivot=(0, -3, 0), boxes=[B((-2, -4, -3), (4, 4, 6), fur)], children=[hd, P('tail', pivot=(0, -3, 3), boxes=[B((-1, -1, 0), (2, 2, 1), fur)])])
    return root(body, *legs4(fur, 1, 3, 1.2, -2, 2.5))


def thornback_boar():
    hide = mat('boar_hide', paint('#4A3428', belly='#6A5040', rows=1, mottle='#3A2820'))
    face = mat('boar_head', head('#4A3428', eye='#E8A33D', row=2, inset=1))
    thorn = mat('boar_thorn', paint('#5A6A3A', top='#8A9A5A', noise=8))
    tusk = mat('boar_tusk', solid('#E8DCC0', 3))
    leg = mat('boar_leg', paint('#3A2820', belly='#1E1612', rows=1))
    hd = P('head', pivot=(0, -5, -6), boxes=[B((-3, -3, -6), (6, 6, 6), face), B((-2, 0, -8), (4, 3, 2), hide),
                                             B((-3, 1, -8), (1, 3, 1), tusk), B((2, 1, -8), (1, 3, 1), tusk)])
    spines = [B((-0.5, -9 - (i % 2), -5 + i * 2.5), (1, 2 + i % 2, 1), thorn) for i in range(5)]
    body = P('body', pivot=(0, -6, 0), boxes=[B((-4, -7, -6), (8, 7, 12), hide)] + spines, children=[hd, P('tail', pivot=(0, -6, 6), rot=(0.5, 0, 0), boxes=[B((-0.5, 0, 0), (1, 3, 1), hide)])])
    return root(body, *legs4(leg, 3, 5, 2.5, -4, 4))


def highland_ram():
    wool = mat('ram_wool', paint('#E8E0D0', belly='#C8C0B0', mottle='#D8D0C0', noise=8))
    face = mat('ram_head', head('#3A3530', eye='#E8C060', row=2, inset=1))
    horn = mat('ram_horn', paint('#8A7A5A', stripes='#5A4A30', every=2))
    leg = mat('ram_leg', paint('#3A3530', belly='#1E1A18', rows=1))
    def curl(side):
        if side == 'l':
            return P('horn_l', pivot=(-3, -5, -2), boxes=[B((-2, -2, -1), (2, 2, 4), horn), B((-3, 0, 1), (2, 3, 2), horn), B((-2, 2, -1), (2, 2, 2), horn)])
        return P('horn_r', pivot=(3, -5, -2), boxes=[B((0, -2, -1), (2, 2, 4), horn), B((1, 0, 1), (2, 3, 2), horn), B((0, 2, -1), (2, 2, 2), horn)])
    hd = P('head', pivot=(0, -9, -6), boxes=[B((-2.5, -4, -6), (5, 6, 6), face)], children=[curl('l'), curl('r')])
    body = P('body', pivot=(0, -8, 0), boxes=[B((-4, -8, -6), (8, 8, 12), wool, 0.5)], children=[hd])
    return root(body, *legs4(leg, 2.5, 7, 2.5, -4, 4))


def duskpine_lynx():
    fur = mat('lynx_fur', paint('#8A7A6A', belly='#E8E0D0', rows=1, spots='#3A3028', density=8))
    face = mat('lynx_head', head('#8A7A6A', eye='#B8E060', row=2, inset=1, belly='#E8E0D0'))
    tuft = mat('lynx_tuft', paint('#2A2420', noise=3))
    hd = P('head', pivot=(0, -7, -6), boxes=[B((-3, -3, -5), (6, 5, 5), face), B((-1.5, 0, -6), (3, 2, 1), fur)], children=[
        P('ear_l', pivot=(-2, -3, -2), rot=(0, 0, -0.2), boxes=[B((-1, -3, -0.5), (2, 3, 1), fur), B((-0.5, -5, -0.5), (1, 2, 1), tuft)]),
        P('ear_r', pivot=(2, -3, -2), rot=(0, 0, 0.2), boxes=[B((-1, -3, -0.5), (2, 3, 1), fur), B((-0.5, -5, -0.5), (1, 2, 1), tuft)])])
    body = P('body', pivot=(0, -6, 0), boxes=[B((-3, -6, -6), (6, 6, 12), fur)], children=[hd, P('tail', pivot=(0, -5, 6), rot=(0.4, 0, 0), boxes=[B((-1, 0, 0), (2, 4, 2), fur)])])
    return root(body, *legs4(fur, 2, 6, 2, -4, 4))


def fen_toad():
    skin = mat('toad_skin', paint('#4A6A3A', belly='#C8C08A', rows=1, spots='#2A3A1E', density=10))
    eye = mat('toad_eye', glowing('#E8A33D', '#FFC860', every=0, alpha=160))
    jawm = mat('toad_jaw', paint('#4A6A3A', belly='#C8C08A', top='#D86A7A'))
    body = P('body', pivot=(0, -3, 0), boxes=[B((-6, -7, -5), (12, 7, 10), skin)], children=[
        P('head', pivot=(0, -4, -5), boxes=[B((-5, -3, -3), (10, 4, 3), skin), B((-5, -5, -2), (3, 3, 3), eye), B((2, -5, -2), (3, 3, 3), eye)],
          children=[P('jaw', pivot=(0, 1, -3), boxes=[B((-5, 0, 0), (10, 2, 3), jawm)])])])
    legs = [P('leg_fl', pivot=(-4, -3, -4), boxes=[B((-1, 0, -1), (2, 3, 2), skin)]), P('leg_fr', pivot=(4, -3, -4), boxes=[B((-1, 0, -1), (2, 3, 2), skin)]),
            P('leg_bl', pivot=(-6, -3, 3), boxes=[B((-2, 0, -3), (3, 3, 6), skin)]), P('leg_br', pivot=(6, -3, 3), boxes=[B((-1, 0, -3), (3, 3, 6), skin)])]
    return root(body, *legs)


def crystal_crab():
    sh = mat('crab_shell', shell('#2A6A8A', '#1A3A4A'))
    cr = mat('crab_crystal', crystal('#7FE3D2', '#B8FFF0', 3))
    leg = mat('crab_leg', paint('#1E4A5A', noise=4))
    claw = mat('crab_claw', paint('#2A6A8A', top='#5AA8C8'))
    eye = mat('crab_eye', glowing('#101418', '#7FE3D2', every=0, alpha=200))
    body = P('body', pivot=(0, -4, 0), boxes=[B((-5, -4, -4), (10, 4, 8), sh), B((-3, -7, -2), (2, 3, 2), cr), B((1, -6, 0), (2, 2, 2), cr),
                                              B((-1, -8, 1), (2, 4, 2), cr), B((-2.5, -6, -5), (1, 2, 1), eye), B((1.5, -6, -5), (1, 2, 1), eye)],
             children=[P('claw_l', pivot=(-5, -2, -3), rot=(0, 0.4, 0), boxes=[B((-3, -2, -5), (3, 3, 5), claw)]),
                       P('claw_r', pivot=(5, -2, -3), rot=(0, -0.4, 0), boxes=[B((0, -2, -5), (3, 3, 5), claw)])] + bug_legs(leg, 3, 5, 5, -1, (-1.5, 1, 3.5), 0.6))
    return root(body)


def ember_salamander():
    skin = mat('sala_skin', shell('#2A1A14', '#1A100C', crack='#FF8A3A', plates=3))
    face = mat('sala_head', head('#2A1A14', eye='#FFC860', glow='#FFD890', row=1, inset=0))
    tail2 = mat('sala_tail', glowing('#3A1E14', '#FF6A2A', every=3))
    body = P('body', pivot=(0, -2, 0), boxes=[B((-2.5, -3, -6), (5, 3, 12), skin)], children=[
        P('head', pivot=(0, -1.5, -6), boxes=[B((-2.5, -1.5, -5), (5, 3, 5), face)]),
        P('tail', pivot=(0, -1.5, 6), boxes=[B((-1.5, -1, 0), (3, 2, 7), skin)], children=[P('tail2', pivot=(0, 0, 7), boxes=[B((-1, -0.5, 0), (2, 1.5, 6), tail2)])])])
    return root(body, *legs4(skin, 2, 2, 3, -4, 4))


def silverrun_otter():
    fur = mat('otter_fur', paint('#6A4A30', belly='#A88A68', rows=1, noise=5))
    face = mat('otter_head', head('#6A4A30', eye='#100C0A', row=1, inset=0, belly='#D8C8A8'))
    body = P('body', pivot=(0, -2, 0), boxes=[B((-2, -4, -5), (4, 4, 10), fur)], children=[
        P('head', pivot=(0, -2, -5), boxes=[B((-2, -2, -4), (4, 4, 4), face)]),
        P('tail', pivot=(0, -2, 5), rot=(0.2, 0, 0), boxes=[B((-1, -1, 0), (2, 2, 4), fur)], children=[P('tail2', pivot=(0, 0, 4), boxes=[B((-0.5, -0.5, 0), (1, 1, 4), fur)])])])
    return root(body, *legs4(fur, 1.5, 2, 1.5, -3.5, 3.5))


def kitsune():
    fur = mat('kit_fur', paint('#F0EAE0', belly='#FFFFFF', rows=1, noise=4))
    face = mat('kit_head', head('#F0EAE0', eye='#C83A3A', glow='#FF8AA8', row=1, inset=0, stripes='#C83A3A', every=6))
    tailm = mat('kit_tail', glowing('#F0EAE0', '#FFB8D8', every=4))
    ear = mat('kit_ear', paint('#F0EAE0', top='#C83A3A'))
    def tail(name, yaw):
        return P(name, pivot=(0, -4, 5), rot=(0.7, yaw, 0), boxes=[B((-1.5, -1.5, 0), (3, 3, 9), tailm)])
    hd = P('head', pivot=(0, -6, -5), boxes=[B((-2.5, -2, -4), (5, 4, 4), face), B((-1, 0, -6), (2, 2, 2), fur)], children=[
        P('ear_l', pivot=(-2, -2, -2), rot=(0, 0, -0.15), boxes=[B((-1, -3, -0.5), (2, 3, 1), ear)]),
        P('ear_r', pivot=(2, -2, -2), rot=(0, 0, 0.15), boxes=[B((-1, -3, -0.5), (2, 3, 1), ear)])])
    body = P('body', pivot=(0, -6, 0), boxes=[B((-2.5, -5, -5), (5, 5, 10), fur)], children=[hd, tail('tail', 0), tail('tail_l', -0.6), tail('tail_r', 0.6)])
    return root(body, *legs4(fur, 2, 6, 1.6, -3.5, 3.5))


def sporeling():
    cap = mat('spore_cap', glowing('#6A4AA8', '#7FE3D2', every=7))
    stem = mat('spore_stem', head('#E8DCC8', eye='#2A1A30', glow='#B8FFF0', row=2, inset=1))
    foot = mat('spore_foot', paint('#C8BCA8'))
    body = P('body', pivot=(0, -2, 0), boxes=[B((-2, -5, -2), (4, 5, 4), stem)], children=[P('cap', pivot=(0, -5, 0), boxes=[B((-4, -3, -4), (8, 3, 8), cap)])])
    return root(body, *legs2(foot, 1.5, 2, 1))


def floe_seal():
    skin = mat('seal_skin', paint('#8A8A92', belly='#C8C8D0', rows=1, spots='#4A4A52', density=6))
    face = mat('seal_head', head('#8A8A92', eye='#0A0A0E', row=1, inset=1))
    body = P('body', pivot=(0, -3, 0), boxes=[B((-4, -6, -6), (8, 6, 12), skin), B((-3, -4, 6), (6, 4, 4), skin)], children=[
        P('head', pivot=(0, -4, -6), boxes=[B((-3, -3, -5), (6, 5, 5), face), B((-1.5, 0, -6), (3, 2, 1), skin)]),
        P('fin_l', pivot=(-4, -1, -3), rot=(0, 0.3, 0.5), boxes=[B((-5, 0, -1), (5, 1, 3), skin)]),
        P('fin_r', pivot=(4, -1, -3), rot=(0, -0.3, -0.5), boxes=[B((0, 0, -1), (5, 1, 3), skin)]),
        P('tail', pivot=(0, -2, 10), boxes=[B((-3, 0, 0), (6, 1, 4), skin)])])
    return root(body)


def blossom_crane():
    plume = mat('crane_plume', feathers('#F4F0F0', tip='#1E1E22', belly='#FFFFFF'))
    petal = mat('crane_wing', feathers('#F4F0F0', tip='#F0A8C0', bar='#E8D8E0'))
    face = mat('crane_head', head('#F4F0F0', eye='#100C0A', side=True, row=1, top='#C83A3A'))
    beak = mat('crane_beak', solid('#3A3A30', 3))
    leg = mat('crane_leg', solid('#2A2A28', 3))
    hd = P('head', pivot=(0, -10, 0), boxes=[B((-1, -3, -2), (2, 3, 4), face), B((-0.5, -2, -7), (1, 1, 5), beak)])
    neck = P('neck', pivot=(0, -4, -4), rot=(0.15, 0, 0), boxes=[B((-1, -10, -1), (2, 10, 2), plume)], children=[hd])
    body = P('body', pivot=(0, -14, 0), boxes=[B((-2.5, -6, -4), (5, 6, 9), plume)], children=[neck] + wings(petal, 1, 8, -5, -3, thick=5, x=2.5) +
             [P('tail', pivot=(0, -4, 5), rot=(0.4, 0, 0), boxes=[B((-2, 0, 0), (4, 1, 4), plume)])])
    return root(body, *legs2(leg, 1, 14, 1.5))


# ---------------------------------------------------------------- monsters

def cinder_beetle():
    sh = mat('cinder_shell', shell('#241A16', '#120C0A', crack='#FF8A3A', plates=3))
    face = mat('cinder_head', head('#1A1210', eye='#FF6A2A', glow='#FFB060', row=1, inset=0))
    leg = mat('cinder_leg', solid('#1A1210', 4))
    horn = mat('cinder_horn', glowing('#3A1E14', '#FF8A3A', every=2))
    body = P('body', pivot=(0, -3, 0), boxes=[B((-4, -5, -4), (8, 5, 10), sh, 0.25)], children=[
        P('head', pivot=(0, -2, -4), boxes=[B((-2.5, -2, -3), (5, 3, 3), face), B((-0.5, -5, -4), (1, 4, 1), horn)])] + bug_legs(leg, 3, 4, 4, -1, (-2, 1, 4), 0.7))
    return root(body)


def sand_sifter():
    sh = mat('sifter_shell', shell('#C8A86A', '#8A6A3A', plates=4))
    face = mat('sifter_head', head('#B8945A', eye='#1A1208', glow='#E8C060', row=1, inset=1, size=1))
    mand = mat('sifter_mandible', solid('#3A2A1A', 4))
    leg = mat('sifter_leg', paint('#8A6A3A', belly='#5A4020', rows=1))
    body = P('body', pivot=(0, -4, 0), boxes=[B((-6, -5, -4), (12, 5, 10), sh), B((-5, -4, 6), (10, 4, 6), sh)], children=[
        P('head', pivot=(0, -3, -4), boxes=[B((-4, -3, -4), (8, 4, 4), face)], children=[
            P('jaw', pivot=(0, 0, -4), boxes=[B((-4, -1, -5), (2, 2, 5), mand), B((2, -1, -5), (2, 2, 5), mand)])])] +
             bug_legs(leg, 3, 6, 6, -1, (-2, 1.5, 5), 0.5))
    return root(body)


def glyph_scarab():
    sh = mat('scarab_shell', shell('#C8962E', '#6A4A14', crack='#FFE08A', plates=2))
    leg = mat('scarab_leg', solid('#3A2A10', 3))
    face = mat('scarab_head', head('#6A4A14', eye='#FFE08A', glow='#FFF0B0', row=0, inset=0))
    body = P('body', pivot=(0, -2, 0), boxes=[B((-2.5, -3, -3), (5, 3, 6), sh, 0.2)], children=[
        P('head', pivot=(0, -1.5, -3), boxes=[B((-1.5, -1, -2), (3, 2, 2), face)])] + bug_legs(leg, 3, 2.5, 2.5, -0.5, (-1.5, 0.5, 2), 0.7))
    return root(body)


def rust_golemite():
    iron = mat('golemite_iron', metal('#8A4A2A', accent='#6A6A6A'))
    face = mat('golemite_head', head('#6A6A6A', eye='#FFB040', glow='#FFD080', row=2, inset=2, size=2))
    core = mat('golemite_core', glowing('#5A2A14', '#FF9A3A', every=0))
    body = P('body', pivot=(0, -12, 0), boxes=[B((-6, -12, -4), (12, 12, 8), iron), B((-2, -9, -4.5), (4, 4, 1), core)], children=[
        P('head', pivot=(0, -12, 0), boxes=[B((-3, -6, -3), (6, 6, 6), face)])] + arms2(iron, 4, 15, 6, -10))
    return root(body, *legs2(iron, 4, 12, 3))


def ice_crawler():
    shell_ = mat('crawler_shell', paint('#C8E0F0', belly='#8AA8C0', mottle='#E8F4FF', noise=5))
    cr = mat('crawler_crystal', crystal('#8AE0FF', '#D0F8FF', 3))
    face = mat('crawler_head', head('#A8C8E0', eye='#4AE0FF', glow='#A8F8FF', row=1, inset=1, size=1))
    leg = mat('crawler_leg', paint('#A8C8E0', belly='#6A8AA0', rows=1))
    body = P('body', pivot=(0, -5, 0), boxes=[B((-4, -4, -4), (8, 4, 8), shell_), B((-5, -6, 4), (10, 7, 10), shell_),
                                              B((-2, -9, 6), (2, 4, 2), cr), B((1, -8, 9), (2, 3, 2), cr), B((-1, -10, 10), (2, 5, 2), cr)], children=[
        P('head', pivot=(0, -2, -4), boxes=[B((-3, -2, -4), (6, 4, 4), face)])] + bug_legs(leg, 4, 8, 4, -2, (-3, -1, 1, 3), 0.55))
    return root(body)


def rime_brute():
    fur = mat('brute_fur', paint('#E8F0F4', belly='#C8D4DC', mottle='#D8E4EA', noise=6))
    face = mat('brute_head', head('#6A7A8A', eye='#4AC8FF', glow='#A8E8FF', row=3, inset=2, size=1, top='#E8F0F4'))
    ice = mat('brute_ice', crystal('#A8E0FF', '#E0F8FF', 4))
    skin = mat('brute_skin', paint('#6A7A8A'))
    body = P('body', pivot=(0, -14, 1), rot=(0.25, 0, 0), boxes=[B((-7, -12, -5), (14, 12, 10), fur), B((-6, -14, -3), (3, 3, 3), ice), B((3, -14, -2), (3, 4, 3), ice)],
             children=[P('head', pivot=(0, -11, -3), rot=(-0.25, 0, 0), boxes=[B((-4, -7, -6), (8, 7, 7), face)]),
                       P('arm_l', pivot=(-7, -10, 0), boxes=[B((-5, -1, -2.5), (5, 17, 5), fur), B((-5, 16, -2.5), (5, 3, 5), skin)]),
                       P('arm_r', pivot=(7, -10, 0), boxes=[B((0, -1, -2.5), (5, 17, 5), fur), B((0, 16, -2.5), (5, 3, 5), skin)])])
    return root(body, *legs2(fur, 5, 10, 3.5))


def drowned_revenant():
    skin = mat('rev_skin', paint('#5A7A6A', mottle='#4A6A5A'))
    face = mat('rev_head', head('#5A7A6A', eye='#4AE8D0', glow='#A8FFF0', row=3, inset=1, size=2, spots='#C8C0A8', density=10))
    coat = mat('rev_coat', paint('#2A3A5A', belly='#1E2A40', rows=2, stripes='#C8A048', every=9))
    hat = mat('rev_hat', paint('#1E1E28', top='#2A2A36'))
    pole = mat('rev_pole', solid('#5A4A30', 3))
    tip = mat('rev_tip', metal('#9AA8B0'))
    harpoon = [B((1, 10, -8), (1, 1, 18), pole), B((0.5, 9.5, -11), (2, 2, 3), tip)]
    body = P('body', pivot=(0, -12, 0), boxes=[B((-4, -12, -2), (8, 12, 4), coat)], children=[
        P('head', pivot=(0, -12, 0), boxes=[B((-4, -8, -4), (8, 8, 8), face), B((-5, -9, -5), (10, 1, 10), hat), B((-3, -11, -3), (6, 2, 6), hat)])] +
             arms2(skin, 4, 12, 4, -10, extra_r=harpoon))
    return root(body, *legs2(coat, 4, 12, 2))


def mire_shambler():
    mud = mat('mire_mud', paint('#4A3A2A', mottle='#4A6A2A', spots='#2A3A1A', density=8))
    face = mat('mire_head', head('#3A4A2A', eye='#E8E040', glow='#FFFF90', row=3, inset=2, size=1, mottle='#5A7A3A'))
    shroom = mat('mire_shroom', paint('#C8A048', top='#E8C060'))
    body = P('body', pivot=(0, -12, 0), rot=(0.2, 0, 0), boxes=[B((-4.5, -12, -3), (9, 12, 6), mud)], children=[
        P('head', pivot=(0, -12, -1), rot=(-0.2, 0, 0), boxes=[B((-4, -8, -4), (8, 8, 8), face), B((1, -10, -2), (2, 2, 2), shroom), B((-3, -9, 1), (2, 1, 2), shroom)])] +
             arms2(mud, 4, 15, 4.5, -10))
    tatters = [P(f'tent{i + 1}', pivot=(-3 + i * 2, -12, 2), boxes=[B((-0.5, 0, -0.5), (1, 5, 1), mud)]) for i in range(4)]
    return root(body, *legs2(mud, 4, 12, 2), *tatters)


def vine_strangler():
    stem = mat('vine_stem', bark('#2A5A2A', moss='#4A8A3A', grain='#1E4A1E'))
    bulb = mat('vine_bulb', paint('#2A6A2A', top='#3A7A2A', spots='#C83A4A', density=6))
    mouth = mat('vine_mouth', teeth('#E8E0C8', '#A83A4A'))
    pot = mat('vine_roots', bark('#4A3A28', moss='#3A5A2A'))
    vine = mat('vine_vine', paint('#3A6A2A', spots='#6A9A3A', density=20))
    head_ = P('head', pivot=(0, -16, 0), boxes=[B((-5, -4, -5), (10, 4, 10), bulb), B((-4.5, 0, -4.5), (9, 1, 9), mouth)], children=[
        P('jaw', pivot=(0, 1, 0), boxes=[B((-5, 0, -5), (10, 4, 10), bulb)])] +
          [P(f'tent{i + 1}', pivot=(math.cos(i * 1.57) * 5, -2, math.sin(i * 1.57) * 5), boxes=[B((-0.5, 0, -0.5), (1, 10, 1), vine)]) for i in range(4)])
    neck = P('neck', pivot=(0, -4, 0), boxes=[B((-2, -16, -2), (4, 16, 4), stem)], children=[head_])
    body = P('body', pivot=(0, 0, 0), boxes=[B((-5, -4, -5), (10, 4, 10), pot)], children=[neck])
    return root(body)


def fungal_brute():
    cap = mat('brute_cap', glowing('#7A3AA8', '#7FE3D2', every=6))
    gills = mat('brute_gills', paint('#C8A8C8', stripes='#8A6A8A', every=2))
    stem = mat('fungal_stem', head('#E8DCC8', eye='#2A1A30', glow='#B8FFF0', row=4, inset=2, size=2, mottle='#D8C8B8'))
    limb = mat('fungal_limb', paint('#D8CCB8', mottle='#B8A898'))
    body = P('body', pivot=(0, -12, 0), boxes=[B((-5, -12, -4), (10, 12, 8), stem)], children=[
        P('cap', pivot=(0, -12, 0), boxes=[B((-8, -6, -8), (16, 6, 16), cap), B((-7.5, 0, -7.5), (15, 1, 15), gills)])] + arms2(limb, 5, 14, 5, -10))
    return root(body, *legs2(limb, 5, 12, 2.5))


def rootling_sentinel():
    wood = mat('root_bark', bark('#5A4030', moss='#4A6A2A'))
    face = mat('root_head', head('#4A3428', eye='#90FF60', glow='#C0FF90', row=3, inset=1, size=1))
    twig = mat('root_twig', bark('#6A5040'))
    hd = P('head', pivot=(0, -14, 0), boxes=[B((-3, -8, -3), (6, 8, 6), face), B((-4, -12, -0.5), (1, 5, 1), twig), B((3, -11, -0.5), (1, 4, 1), twig),
                                              B((-5, -12, -0.5), (2, 1, 1), twig), B((3, -12, -0.5), (2, 1, 1), twig)])
    body = P('body', pivot=(0, -12, 0), boxes=[B((-4, -14, -3), (8, 14, 6), wood)], children=[hd] + arms2(wood, 3, 17, 4, -12))
    return root(body, *legs2(wood, 4, 12, 2))


def gloomweaver():
    chit = mat('weaver_body', paint('#1E1A28', mottle='#2A2438'))
    abdo = mat('weaver_abdomen', paint('#1E1A28', stripes='#B8A8D8', every=5, spots='#D8C8F0', density=4))
    face = mat('weaver_head', head('#1E1A28', eye='#B060FF', glow='#D8A8FF', row=1, inset=1, size=1))
    leg = mat('weaver_leg', paint('#1E1A28', belly='#2A2438', rows=1, stripes='#4A3A60', every=3))
    body = P('body', pivot=(0, -6, 0), boxes=[B((-4, -4, -4), (8, 5, 8), chit), B((-6, -8, 4), (12, 10, 12), abdo)], children=[
        P('head', pivot=(0, -2, -4), boxes=[B((-3, -2, -5), (6, 5, 5), face)])] + bug_legs(leg, 4, 10, 4, -2, (-3, -1, 1, 3), 0.5))
    return root(body)


def prism_golem():
    cr = mat('prism_crystal', crystal('#B8A8F0', '#E8E0FF', 5))
    stone = mat('prism_stone', paint('#6A6A7A', mottle='#5A5A6A'))
    face = mat('prism_head', head('#9A8AD8', eye='#FFFFFF', glow='#E8E0FF', row=3, inset=2, size=2))
    body = P('body', pivot=(0, -12, 0), boxes=[B((-7, -14, -5), (14, 14, 10), stone), B((-5, -17, -3), (3, 4, 3), cr), B((2, -16, -2), (4, 5, 4), cr),
                                               B((-2, -18, 1), (3, 5, 3), cr)], children=[
        P('head', pivot=(0, -14, -1), boxes=[B((-4, -8, -4), (8, 8, 8), face)]),
        P('arm_l', pivot=(-7, -12, 0), boxes=[B((-6, -1, -3), (6, 16, 6), stone), B((-7, 10, -4), (8, 6, 8), cr)]),
        P('arm_r', pivot=(7, -12, 0), boxes=[B((0, -1, -3), (6, 16, 6), stone), B((-1, 10, -4), (8, 6, 8), cr)])])
    return root(body, *legs2(stone, 6, 12, 3.5))


def dusk_hound():
    fur = mat('hound_fur', paint('#2A2A3A', belly='#3A3A4C', rows=1, mottle='#22222E'))
    face = mat('hound_head', head('#2A2A3A', eye='#FF6A3A', glow='#FFA060', row=1, inset=1))
    mane = mat('hound_mane', paint('#4A3A5A', noise=8))
    hd = P('head', pivot=(0, -9, -6), boxes=[B((-3, -3, -5), (6, 6, 5), face), B((-1.5, 0, -8), (3, 3, 3), fur)], children=[
        P('ear_l', pivot=(-2, -3, -2), boxes=[B((-1, -3, -0.5), (2, 3, 1), fur)]), P('ear_r', pivot=(2, -3, -2), boxes=[B((-1, -3, -0.5), (2, 3, 1), fur)])])
    body = P('body', pivot=(0, -8, 0), boxes=[B((-3, -6, -6), (6, 6, 12), fur), B((-3.5, -8, -6), (7, 3, 6), mane)], children=[
        hd, P('tail', pivot=(0, -5, 6), rot=(0.7, 0, 0), boxes=[B((-1, 0, 0), (2, 2, 8), fur)])])
    return root(body, *legs4(fur, 2, 8, 2, -4, 4))


# ---------------------------------------------------------------- flyers

def ash_wyrmling():
    sc = mat('wyrm_scale', shell('#2A2222', '#1A1414', crack='#E86A2A', plates=3))
    belly = mat('wyrm_belly', glowing('#E86A2A', '#FFB060', every=3))
    face = mat('wyrm_head', head('#2A2222', eye='#FFC040', glow='#FFE090', row=1, inset=0))
    wingm = mat('wyrm_wing', membrane('#6A1E1A', '#2A1414'))
    horn = mat('wyrm_horn', solid('#C8B898', 3))
    hd = P('head', pivot=(0, -1, -5), boxes=[B((-2, -2, -6), (4, 4, 6), face), B((-2, -4, -1), (1, 2, 1), horn), B((1, -4, -1), (1, 2, 1), horn)])
    neck = P('neck', pivot=(0, -3, -5), rot=(-0.3, 0, 0), boxes=[B((-1.5, -1.5, -5), (3, 3, 5), sc)], children=[hd])
    tail = P('tail', pivot=(0, -2, 5), boxes=[B((-1.5, -1.5, 0), (3, 3, 7), sc)], children=[P('tail2', pivot=(0, 0, 7), boxes=[B((-1, -1, 0), (2, 2, 7), sc)])])
    body = P('body', pivot=(0, -6, 0), boxes=[B((-3, -5, -5), (6, 5, 10), sc), B((-2.5, 0, -4), (5, 1, 8), belly)], children=[neck, tail] + wings(wingm, 12, 9, -5, -3, x=3) +
             [P('leg_l', pivot=(-2, 0, 2), boxes=[B((-1, 0, -1), (2, 4, 2), sc)]), P('leg_r', pivot=(2, 0, 2), boxes=[B((-1, 0, -1), (2, 4, 2), sc)])])
    return root(body)


def canyon_vulture():
    fe = mat('vulture_feather', feathers('#3A2A20', tip='#1E1612', bar='#4A3828'))
    face = mat('vulture_head', head('#C86A5A', eye='#100C0A', side=True, row=1))
    ruff = mat('vulture_ruff', paint('#E8E0D0', noise=6))
    beak = mat('vulture_beak', solid('#D8C890', 3))
    hd = P('head', pivot=(0, -5, 0), boxes=[B((-1.5, -3, -3), (3, 3, 4), face), B((-0.5, -2, -5), (1, 2, 2), beak)])
    neck = P('neck', pivot=(0, -4, -4), rot=(0.3, 0, 0), boxes=[B((-1, -5, -1), (2, 5, 2), face), B((-2, -1, -2), (4, 2, 4), ruff)], children=[hd])
    body = P('body', pivot=(0, -4, 0), boxes=[B((-3, -6, -4), (6, 6, 9), fe)], children=[neck] + wings(fe, 14, 8, -5, -3, x=3) +
             [P('tail', pivot=(0, -3, 5), rot=(0.2, 0, 0), boxes=[B((-3, 0, 0), (6, 1, 5), fe)])] + legs2(beak, 1, 3, 1.5, hip=0))
    return root(body)


def mossmere_owl():
    fe = mat('owl_feather', feathers('#6A5A3A', tip='#4A6A2A', bar='#4A3E28', belly='#B8A880'))
    face = mat('owl_head', head('#7A6A4A', eye='#FFC020', row=2, inset=1, size=1, top='#5A4A30'))
    tuft = mat('owl_tuft', paint('#4A3E28'))
    talon = mat('owl_talon', solid('#3A3020', 2))
    hd = P('head', pivot=(0, -7, 0), boxes=[B((-3, -5, -3), (6, 5, 6), face)], children=[
        P('ear_l', pivot=(-2, -5, 0), rot=(0, 0, -0.3), boxes=[B((-1, -2, -0.5), (1, 2, 1), tuft)]),
        P('ear_r', pivot=(2, -5, 0), rot=(0, 0, 0.3), boxes=[B((0, -2, -0.5), (1, 2, 1), tuft)])])
    body = P('body', pivot=(0, -2, 0), boxes=[B((-3, -7, -3), (6, 7, 6), fe)], children=[hd] + wings(fe, 1, 6, -7, -2, thick=6, x=3) +
             [P('tail', pivot=(0, -1, 3), rot=(0.6, 0, 0), boxes=[B((-2, 0, 0), (4, 1, 3), fe)])])
    return root(body, *legs2(talon, 1, 2, 1.5))


def echo_bat():
    fur = mat('bat_fur', paint('#2A1A30', mottle='#3A2440'))
    face = mat('bat_head', head('#2A1A30', eye='#F0F0FF', glow='#FFFFFF', row=1, inset=0))
    wingm = mat('bat_wing', membrane('#3A2448', '#1E1228', glow='#B8A8FF'))
    hd = P('head', pivot=(0, -5, 0), boxes=[B((-2, -4, -2), (4, 4, 4), face)], children=[
        P('ear_l', pivot=(-1.5, -4, 0), rot=(0, 0, -0.2), boxes=[B((-1, -3, -0.5), (2, 3, 1), fur)]),
        P('ear_r', pivot=(1.5, -4, 0), rot=(0, 0, 0.2), boxes=[B((-1, -3, -0.5), (2, 3, 1), fur)])])
    body = P('body', pivot=(0, -6, 0), boxes=[B((-2, -5, -1.5), (4, 5, 3), fur)], children=[hd] + wings(wingm, 10, 7, -5, -1, x=2) +
             legs2(fur, 1, 2, 1, hip=0))
    return root(body)


def windrider_eagle():
    fe = mat('eagle_feather', feathers('#4A3020', tip='#E8D8B0', bar='#5A3E28', belly='#6A4A30'))
    face = mat('eagle_head', head('#F0F0E8', eye='#E8A020', side=True, row=1))
    beak = mat('eagle_beak', solid('#F0C030', 3))
    talon = mat('eagle_talon', solid('#E8B020', 3))
    hd = P('head', pivot=(0, -6, -5), boxes=[B((-2.5, -4, -5), (5, 5, 6), face), B((-1, -2, -7), (2, 3, 2), beak)])
    body = P('body', pivot=(0, -6, 0), boxes=[B((-4, -7, -6), (8, 7, 12), fe)], children=[hd] + wings(fe, 20, 11, -6, -4, x=4) +
             [P('tail', pivot=(0, -4, 6), boxes=[B((-4, 0, 0), (8, 1, 7), fe)])] + legs2(talon, 2, 3, 2, hip=0))
    return root(body)


def mourning_shade():
    robe = mat('shade_robe', ghostly('#8A9AB0', '#C8E8FF'))
    face = mat('shade_head', head('#1A1E28', eye='#7FE8FF', glow='#C8F8FF', row=4, inset=2, size=1))
    hood = mat('shade_hood', ghostly('#9AA8C0', '#C8E8FF'))
    claw = mat('shade_claw', ghostly('#C8D4E8', '#E8F8FF'))
    hd = P('head', pivot=(0, -12, 0), boxes=[B((-3, -7, -3), (6, 7, 6), face), B((-4, -9, -4), (8, 9, 8), hood, 0.2)])
    body = P('body', pivot=(0, -10, 0), boxes=[B((-4, -12, -3), (8, 12, 6), robe)], children=[hd] + arms2(claw, 3, 13, 4, -10))
    tatters = [P(f'tent{i + 1}', pivot=(-3 + i * 2, -10, -1 + (i % 2) * 2), boxes=[B((-1, 0, -1), (2, 9, 2), robe)]) for i in range(4)]
    return root(body, *tatters)


def bone_gull():
    fe = mat('gull_feather', feathers('#F0F0F0', tip='#3A3A40', bar='#C8C8D0'))
    ribs = mat('gull_ribs', paint('#E8E4D8', stripes='#5A5A60', every=2))
    face = mat('gull_head', head('#F0F0F0', eye='#100C0A', side=True, row=1))
    beak = mat('gull_beak', solid('#E8C040', 3))
    hd = P('head', pivot=(0, -4, -3), boxes=[B((-1.5, -3, -3), (3, 3, 4), face), B((-0.5, -2, -6), (1, 1, 3), beak)])
    body = P('body', pivot=(0, -3, 0), boxes=[B((-2, -4, -3), (4, 4, 7), ribs)], children=[hd] + wings(fe, 9, 5, -4, -2, x=2) +
             [P('tail', pivot=(0, -3, 4), boxes=[B((-2, 0, 0), (4, 1, 3), fe)])] + legs2(beak, 0.5, 2, 1, hip=0))
    return root(body)


def rime_moth():
    fur = mat('rmoth_fur', paint('#D0E8F8', noise=4))
    wingm = mat('rmoth_wing', glowing('#C8E0F8', '#E8FFFF', every=6))
    feel = mat('rmoth_antenna', solid('#A8C8E0', 3))
    body = P('body', pivot=(0, -6, 0), boxes=[B((-1.5, -3, -2.5), (3, 3, 5), fur)], children=[
        P('head', pivot=(0, -2, -2.5), boxes=[B((-1, -1, -2), (2, 2, 2), fur), B((-1.5, -4, -2), (1, 3, 1), feel), B((0.5, -4, -2), (1, 3, 1), feel)])] +
             wings(wingm, 8, 7, -3, -3, x=1.5))
    return root(body)


# ---------------------------------------------------------------- swimmers

def abyssal_angler():
    skin = mat('angler_skin', paint('#14182A', belly='#2A3048', rows=1, spots='#2A4A6A', density=4))
    face = mat('angler_head', head('#14182A', eye='#E8F8FF', glow='#FFFFFF', row=1, inset=1))
    mouth = mat('angler_teeth', teeth('#E8E8F0', '#3A1420'))
    lure = mat('angler_lure', glowing('#3A4A6A', '#B8FFE8', every=0))
    fin = mat('angler_fin', membrane('#1E2440', '#0A0C18', glow='#4AA8C8'))
    body = P('body', pivot=(0, -4, 0), boxes=[B((-5, -8, -6), (10, 8, 12), face)], children=[
        P('jaw', pivot=(0, 0, -6), boxes=[B((-5, 0, -1), (10, 3, 6), skin), B((-4.5, -1, -0.5), (9, 1, 5), mouth)]),
        P('lure', pivot=(0, -8, -4), rot=(-0.6, 0, 0), boxes=[B((-0.5, -8, -0.5), (1, 8, 1), skin), B((-1, -10, -1), (2, 2, 2), lure)]),
        P('fin_l', pivot=(-5, -3, 0), boxes=[B((-1, -2, 0), (1, 4, 4), fin)]), P('fin_r', pivot=(5, -3, 0), boxes=[B((0, -2, 0), (1, 4, 4), fin)]),
        P('tail', pivot=(0, -4, 6), boxes=[B((-0.5, -3, 0), (1, 6, 5), fin)])])
    return root(body)


def tidecaller_jelly():
    bell = mat('jelly_bell', glowing('#58C8C0', '#B8FFF8', every=3, alpha=170))
    core = mat('jelly_core', glowing('#E87AB8', '#FFB8E0', every=0))
    tent = mat('jelly_tentacle', glowing('#8AE8E0', '#C8FFF8', every=4, alpha=150))
    body = P('body', pivot=(0, -12, 0), boxes=[B((-5, -7, -5), (10, 7, 10), bell), B((-2, -4, -2), (4, 3, 4), core)])
    tents = [P(f'tent{i + 1}', pivot=(math.cos(i * 1.047) * 3.5, -12, math.sin(i * 1.047) * 3.5), boxes=[B((-0.5, 0, -0.5), (1, 11, 1), tent)]) for i in range(6)]
    return root(body, *tents)


def tusked_narwhal():
    skin = mat('narwhal_skin', paint('#8A9098', belly='#C8CCD0', rows=1, spots='#4A5058', density=7))
    face = mat('narwhal_head', head('#8A9098', eye='#0A0A0E', side=True, row=2))
    tusk = mat('narwhal_tusk', paint('#E8DCC0', stripes='#B8AC90', every=2))
    body = P('body', pivot=(0, -4, 0), boxes=[B((-4, -7, -9), (8, 7, 18), skin)], children=[
        P('head', pivot=(0, -3, -9), boxes=[B((-3.5, -3.5, -4), (7, 6, 4), face), B((-0.5, -1.5, -18), (1, 1, 14), tusk)]),
        P('fin_l', pivot=(-4, -1, -4), rot=(0, 0.2, 0.4), boxes=[B((-4, 0, -1), (4, 1, 3), skin)]),
        P('fin_r', pivot=(4, -1, -4), rot=(0, -0.2, -0.4), boxes=[B((0, 0, -1), (4, 1, 3), skin)]),
        P('tail', pivot=(0, -4, 9), boxes=[B((-2, -2, 0), (4, 3, 5), skin)], children=[P('tail2', pivot=(0, 0, 5), boxes=[B((-5, -0.5, 0), (10, 1, 4), skin)])])])
    return root(body)


# ---------------------------------------------------------------- not creatures, but built the same way

def training_dummy():
    straw = mat('dummy_straw', paint('#C8A858', mottle='#A88838', stripes='#8A6A2A', every=5))
    sack = mat('dummy_sack', head('#B89868', eye='#3A2A1A', row=2, inset=1, size=1, mottle='#A88858'))
    wood = mat('dummy_wood', bark('#6A4A2A'))
    club = mat('dummy_club', paint('#5A3A20', top='#8A6A4A', noise=4))

    def target(F, rng):
        straw_fn = MATERIALS['dummy_straw']
        straw_fn(F, rng)
        x0, y0, w, h = F.rect['north']
        cx, cy = x0 + w / 2 - 0.5, y0 + h / 2 - 0.5
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if d < 1.2 or 2.4 < d < 3.4:
                    F.put(x, y, hexc('#B83A2E'))
    mat('dummy_target', target)
    base = P('base', boxes=[B((-4, -2, -4), (8, 2, 8), wood), B((-1, -16, -1), (2, 14, 2), wood)])
    hd = P('head', pivot=(0, -10, 0), boxes=[B((-3, -6, -3), (6, 6, 6), sack)])
    body = P('body', pivot=(0, -16, 0), boxes=[B((-4, -10, -2.5), (8, 10, 5), 'dummy_target')], children=[
        hd, P('arm_l', pivot=(-4, -8, 0), boxes=[B((-7, -1, -0.5), (7, 1, 1), wood)]),
        P('arm_r', pivot=(4, -8, 0), boxes=[B((0, -1, -0.5), (6, 1, 1), wood), B((5, -2, -1.5), (3, 3, 3), club)])])
    return root(base, body)


EXTRAS = [('training_dummy', training_dummy, 64)]


# ---------------------------------------------------------------- the list

def _pascal(s):
    return ''.join(w.capitalize() for w in s.split('_'))


SPECIES = [
    ('dune_strider', dune_strider, 128, '#C8A46A', '#8A2A2A'), ('snow_hare', snow_hare, 32, '#F0F0F4', '#C83A5A'),
    ('thornback_boar', thornback_boar, 64, '#4A3428', '#5A6A3A'), ('highland_ram', highland_ram, 64, '#E8E0D0', '#8A7A5A'),
    ('duskpine_lynx', duskpine_lynx, 64, '#8A7A6A', '#2A2420'), ('fen_toad', fen_toad, 64, '#4A6A3A', '#E8A33D'),
    ('crystal_crab', crystal_crab, 64, '#2A6A8A', '#7FE3D2'), ('ember_salamander', ember_salamander, 64, '#2A1A14', '#FF8A3A'),
    ('silverrun_otter', silverrun_otter, 64, '#6A4A30', '#D8C8A8'), ('kitsune', kitsune, 64, '#F0EAE0', '#C83A3A'),
    ('sporeling', sporeling, 32, '#6A4AA8', '#7FE3D2'), ('floe_seal', floe_seal, 64, '#8A8A92', '#4A4A52'),
    ('blossom_crane', blossom_crane, 64, '#F4F0F0', '#F0A8C0'),
    ('cinder_beetle', cinder_beetle, 64, '#241A16', '#FF8A3A'), ('sand_sifter', sand_sifter, 64, '#C8A86A', '#3A2A1A'),
    ('glyph_scarab', glyph_scarab, 32, '#C8962E', '#FFE08A'), ('rust_golemite', rust_golemite, 128, '#8A4A2A', '#FFB040'),
    ('ice_crawler', ice_crawler, 64, '#C8E0F0', '#4AE0FF'), ('rime_brute', rime_brute, 128, '#E8F0F4', '#4AC8FF'),
    ('drowned_revenant', drowned_revenant, 64, '#2A3A5A', '#4AE8D0'), ('mire_shambler', mire_shambler, 64, '#4A3A2A', '#E8E040'),
    ('vine_strangler', vine_strangler, 64, '#2A5A2A', '#C83A4A'), ('fungal_brute', fungal_brute, 128, '#7A3AA8', '#7FE3D2'),
    ('rootling_sentinel', rootling_sentinel, 64, '#5A4030', '#90FF60'), ('gloomweaver', gloomweaver, 64, '#1E1A28', '#B060FF'),
    ('prism_golem', prism_golem, 128, '#B8A8F0', '#6A6A7A'), ('dusk_hound', dusk_hound, 64, '#2A2A3A', '#FF6A3A'),
    ('ash_wyrmling', ash_wyrmling, 64, '#2A2222', '#E86A2A'), ('canyon_vulture', canyon_vulture, 64, '#3A2A20', '#C86A5A'),
    ('mossmere_owl', mossmere_owl, 64, '#6A5A3A', '#FFC020'), ('echo_bat', echo_bat, 64, '#2A1A30', '#B8A8FF'),
    ('windrider_eagle', windrider_eagle, 128, '#4A3020', '#F0F0E8'), ('mourning_shade', mourning_shade, 64, '#8A9AB0', '#7FE8FF'),
    ('bone_gull', bone_gull, 32, '#F0F0F0', '#3A3A40'), ('rime_moth', rime_moth, 32, '#D0E8F8', '#E8FFFF'),
    ('abyssal_angler', abyssal_angler, 64, '#14182A', '#B8FFE8'), ('tidecaller_jelly', tidecaller_jelly, 64, '#58C8C0', '#E87AB8'),
    ('tusked_narwhal', tusked_narwhal, 64, '#8A9098', '#E8DCC0'),
]

V2_SPECS = [(_pascal(i) + 'Layer', i, fn, w, 100 + k) for k, (i, fn, w, egg_a, egg_b) in enumerate(SPECIES)]
V2_SPECS += [(_pascal(i) + 'Layer', i, fn, w, 300 + k) for k, (i, fn, w) in enumerate(EXTRAS)]
V2_MATERIALS = MATERIALS


def part_paths(root_part):
    """Every part's path from the model root, for the client model to find them by name."""
    out = []

    def walk(p, prefix):
        path = prefix + '/' + p.name if prefix else p.name
        out.append(path)
        for c in p.children:
            walk(c, path)
    walk(root_part, '')
    return out


def write_registry():
    """WildLayers.java: each species' layer and part paths, so one renderer serves them all."""
    lines = ['package dev.afterworld.client.model.gen;', '',
             'import net.minecraft.client.model.geom.builders.LayerDefinition;', '',
             '/** Generated by tools/mobs_v2.py. Do not edit by hand. */',
             'public final class WildLayers {', '\tprivate WildLayers() {', '\t}', '',
             '\tpublic static LayerDefinition create(String id) {', '\t\treturn switch (id) {']
    for i, fn, w, *_ in SPECIES + EXTRAS:
        lines.append(f'\t\t\tcase "{i}" -> {_pascal(i)}Layer.create();')
    lines += ['\t\t\tdefault -> throw new IllegalArgumentException(id);', '\t\t};', '\t}', '',
              '\tpublic static String[] parts(String id) {', '\t\treturn switch (id) {']
    for i, fn, w, *_ in SPECIES + EXTRAS:
        paths = part_paths(fn())
        lines.append(f'\t\t\tcase "{i}" -> new String[] {{' + ', '.join(f'"{p}"' for p in paths) + '};')
    lines += ['\t\t\tdefault -> new String[0];', '\t\t};', '\t}', '}', '']
    os.makedirs(JAVA, exist_ok=True)
    with open(os.path.join(JAVA, 'WildLayers.java'), 'w') as f:
        f.write('\n'.join(lines))


def eggs():
    """Spawn eggs for every species, in its two colours, with their item model and definition."""
    import json
    import gen_assets
    assets = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld')
    for i, fn, w, a, b in SPECIES:
        name = i + '_spawn_egg'
        gen_assets.egg(name, a, b)
        with open(os.path.join(assets, 'models', 'item', name + '.json'), 'w') as f:
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'afterworld:item/{name}'}}, f, indent=2)
        with open(os.path.join(assets, 'items', name + '.json'), 'w') as f:
            json.dump({'model': {'type': 'minecraft:model', 'model': f'afterworld:item/{name}'}}, f, indent=2)


# Build every spec once so their materials are registered before gen_mobs merges them.
for _i, _fn, *_rest in SPECIES + EXTRAS:
    _fn()

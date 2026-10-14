#!/usr/bin/env python3
"""Afterworld's creatures: one spec per mob gives the Java LayerDefinition, a painted texture and
a glow texture (drawn full-bright) for the parts that shine."""
import math, os, random, sys
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from gen_models import Box, Part, all_boxes, pack, write_java

HERE = os.path.dirname(os.path.abspath(__file__))
TEX_OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures', 'entity')
HALF_PI = math.pi / 2


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f, a=None):
    r, g, b = (max(0, min(255, int(v * f))) for v in c[:3])
    return (r, g, b, c[3] if a is None else a)


def jitter(rng, c, amt):
    d = rng.randint(-amt, amt)
    return (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), c[3])


# ---------------------------------------------------------------- painting

class Faces:
    """Paint context for one box: base and glow pixel access plus each face's rectangle."""
    def __init__(self, base, glow, box):
        self.base, self.glow, self.box = base, glow, box
        w, h, d = (int(math.ceil(s)) for s in box.size)
        u, v = box.u, box.v
        self.w, self.h, self.d = w, h, d
        self.rect = {
            'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
            'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h),
        }

    def each(self, faces=None):
        for f, (x0, y0, w, h) in self.rect.items():
            if (faces is None or f in faces) and w > 0 and h > 0:
                yield f, x0, y0, w, h

    def fill(self, rng, color, amt=8, faces=None):
        for f, x0, y0, w, h in self.each(faces):
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    self.base[x, y] = jitter(rng, color, amt)

    def put(self, x, y, c):
        self.base[x, y] = c

    def lit(self, x, y, c):
        self.glow[x, y] = c


def m_moth_fur(F, rng):
    F.fill(rng, hexc('#8A7458'), 10)
    for f, x0, y0, w, h in F.each():
        for _ in range(w * h // 4):
            F.put(x0 + rng.randrange(w), y0 + rng.randrange(h), hexc('#B8A07A'))


def m_moth_head(F, rng):
    F.fill(rng, hexc('#5A4632'), 6)
    x0, y0, w, h = F.rect['north']
    F.put(x0, y0, hexc('#14100C'))
    F.put(x0 + w - 1, y0, hexc('#14100C'))


def m_antenna(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                F.put(x, y, hexc('#C8A878') if (y - y0) % 2 == 0 else hexc('#9A7C52'))


def m_moth_lantern(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                k = (x - x0) if f in ('east', 'west', 'up', 'down') else 0
                stripe = f in ('east', 'west', 'up', 'down') and k % 2 == 1 or f == 'south'
                if stripe:
                    F.put(x, y, jitter(rng, hexc('#F2C14E'), 8))
                    F.lit(x, y, hexc('#FFD27A', 230))
                else:
                    F.put(x, y, jitter(rng, hexc('#5A4028'), 6))


def wing(F, rng, mirror):
    for f, x0, y0, w, h in F.each(('up', 'down')):
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for y in range(h):
            for x in range(w):
                xx = w - 1 - x if mirror else x
                # Rounded outer edge: trim the far corners.
                if (xx >= w - 2 and (y == 0 or y == h - 1)) or (xx == w - 1 and (y <= 1 or y >= h - 2)):
                    continue
                base = hexc('#C9B48A') if f == 'up' else hexc('#DCCCA6')
                c = jitter(rng, base, 6)
                if xx == w - 1 or y in (0, h - 1):
                    c = shade(c, 0.72)
                if (xx + y) % 3 == 0 and f == 'up':
                    c = shade(c, 0.9)
                d = math.hypot(xx - (w * 0.62), y - cy)
                if f == 'up' and d < 1.6:
                    c = hexc('#F2C14E') if d < 0.8 else hexc('#3A2A1A')
                    if d < 0.8:
                        F.lit(x0 + x, y0 + y, hexc('#FFD27A', 140))
                F.put(x0 + x, y0 + y, c)


def m_moth_wing(F, rng):
    wing(F, rng, False)


def m_moth_wing_r(F, rng):
    wing(F, rng, True)


def m_crow(color):
    def f(F, rng):
        F.fill(rng, hexc(color), 5)
        for face, x0, y0, w, h in F.each(('up', 'east', 'west')):
            for _ in range(max(1, w * h // 6)):
                F.put(x0 + rng.randrange(w), y0 + rng.randrange(h), hexc('#3A3E5A'))
    return f


def m_crow_head(F, rng):
    m_crow('#1E1E26')(F, rng)
    for face in ('east', 'west'):
        x0, y0, w, h = F.rect[face]
        ex = x0 + (0 if face == 'east' else w - 1)
        F.put(ex, y0 + 1, hexc('#08080C'))
        F.put(ex, y0, hexc('#C8C8D0'))


def m_beak(F, rng):
    F.fill(rng, hexc('#3A3A40'), 4)
    x0, y0, w, h = F.rect['north']
    F.put(x0, y0, hexc('#6A6A72'))


def m_crow_leg(F, rng):
    F.fill(rng, hexc('#2A2A30'), 3)


def m_shell(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                gx, gy = (x - x0) % 5, (y - y0 + ((x - x0) // 5) * 2) % 5
                edge = gx == 0 or gy == 0
                c = hexc('#4E5A3A') if not edge else hexc('#2E3624')
                if gx == 2 and gy == 2:
                    c = hexc('#6E7A4A')
                F.put(x, y, jitter(rng, c, 6))


def m_belly(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = hexc('#B8A878') if (x - x0) % 4 else hexc('#8A7C58')
                F.put(x, y, jitter(rng, c, 5))


def m_moss(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                F.put(x, y, rng.choice((hexc('#4E6A2A'), hexc('#5E7A32'), hexc('#6E8A3A'), hexc('#7A9A44'))))


def m_mushroom(F, rng):
    F.fill(rng, hexc('#B83A2E'), 6, ('up', 'north', 'south', 'east', 'west'))
    F.fill(rng, hexc('#E8DCC0'), 4, ('down',))
    x0, y0, w, h = F.rect['up']
    F.put(x0, y0, hexc('#F0E8D8'))
    F.put(x0 + w - 1, y0 + h - 1, hexc('#F0E8D8'))


def m_sprout(F, rng):
    F.fill(rng, hexc('#6AA040'), 10)


def m_mossback_skin(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = hexc('#6A7058')
                if (x + y * 2) % 5 == 0:
                    c = hexc('#545A44')
                F.put(x, y, jitter(rng, c, 5))


def m_mossback_head(F, rng):
    m_mossback_skin(F, rng)
    for face in ('east', 'west'):
        x0, y0, w, h = F.rect[face]
        ex = x0 + (1 if face == 'east' else w - 2)
        F.put(ex, y0 + 1, hexc('#14140E'))
        F.put(ex, y0, hexc('#C8C8A0'))
    x0, y0, w, h = F.rect['north']
    F.put(x0 + 1, y0 + h - 2, hexc('#2A2A20'))
    F.put(x0 + w - 2, y0 + h - 2, hexc('#2A2A20'))


def m_horn(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                F.put(x, y, hexc('#8A7C5A') if y == y0 and f != 'down' else jitter(rng, hexc('#D8CCA8'), 6))


def m_stag_fur(F, rng):
    for f, x0, y0, w, h in F.each():
        base = hexc('#C8B090') if f == 'down' else hexc('#7A5434')
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = jitter(rng, base, 7)
                if f != 'down' and y >= y0 + h - 2 and f != 'up':
                    c = jitter(rng, hexc('#A8865E'), 6)
                F.put(x, y, c)
    if F.box.size[2] >= 12:
        for f in ('east', 'west', 'up'):
            x0, y0, w, h = F.rect[f]
            for _ in range(5):
                F.put(x0 + rng.randrange(w), y0 + rng.randrange(max(1, h - 2)), hexc('#E8DCC0'))


def m_stag_head(F, rng):
    m_stag_fur(F, rng)
    for face in ('east', 'west'):
        x0, y0, w, h = F.rect[face]
        ex = x0 + (2 if face == 'east' else w - 3)
        F.put(ex, y0 + 1, hexc('#120C08'))
        F.put(ex, y0, hexc('#E8E0D0'))


def m_stag_muzzle(F, rng):
    F.fill(rng, hexc('#4A3426'), 5)
    x0, y0, w, h = F.rect['north']
    F.put(x0, y0, hexc('#14100C'))
    F.put(x0 + w - 1, y0, hexc('#14100C'))


def m_stag_leg(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                F.put(x, y, jitter(rng, hexc('#2A2018') if y >= y0 + h - 2 else hexc('#5A3E28'), 5))


def m_antler(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                F.put(x, y, jitter(rng, hexc('#E8E0C8'), 6))
                F.lit(x, y, hexc('#9FE8D0', 200 if (x + y) % 3 else 255))


DUSK = hexc('#3A3060')
DUSK_DARK = hexc('#241C40')
DUSK_LIGHT = hexc('#8A7AB8')
DUSK_GLOW = hexc('#FFC870')


def m_dusk_fur(F, rng):
    for f, x0, y0, w, h in F.each():
        base = DUSK_LIGHT if f == 'down' else DUSK
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = jitter(rng, base, 6)
                if f not in ('down', 'up') and y >= y0 + h - 2:
                    c = jitter(rng, shade(DUSK_LIGHT, 0.85), 5)
                F.put(x, y, c)


def m_dusk_body(F, rng):
    m_dusk_fur(F, rng)
    # Silver dapples along the flanks.
    for f in ('east', 'west'):
        x0, y0, w, h = F.rect[f]
        for _ in range(9):
            F.put(x0 + rng.randrange(w), y0 + rng.randrange(max(1, h - 2)), hexc('#B8B0D8'))
    # The saddle: leather on the back, a gold-trimmed blanket over each flank.
    x0, y0, w, h = F.rect['up']
    mid = h // 2
    for y in range(y0 + mid - 4, y0 + mid + 4):
        for x in range(x0, x0 + w):
            edge = y in (y0 + mid - 4, y0 + mid + 3)
            F.put(x, y, hexc('#D8B45A') if edge else jitter(rng, hexc('#6A4226'), 5))
    F.put(x0 + w // 2, y0 + mid - 3, hexc('#2A1A10'))
    F.put(x0 + w // 2, y0 + mid + 2, hexc('#2A1A10'))
    for f in ('east', 'west'):
        x0, y0, w, h = F.rect[f]
        c0 = w // 2
        for x in range(x0 + c0 - 4, x0 + c0 + 4):
            for y in range(y0, y0 + 4):
                trim = y == y0 + 3 or x in (x0 + c0 - 4, x0 + c0 + 3)
                F.put(x, y, hexc('#D8B45A') if trim else jitter(rng, hexc('#7A2A3A'), 5))
        F.put(x0 + c0, y0 + 4, hexc('#8A8A92'))
        F.put(x0 + c0, y0 + 5, hexc('#5A5A62'))


def m_dusk_head(F, rng):
    m_dusk_fur(F, rng)
    for face in ('east', 'west'):
        x0, y0, w, h = F.rect[face]
        ex = x0 + (2 if face == 'east' else w - 3)
        F.put(ex, y0 + 1, DUSK_GLOW)
        F.lit(ex, y0 + 1, hexc('#FFD890', 255))
        F.put(ex, y0 + 2, hexc('#14101C'))


def m_dusk_muzzle(F, rng):
    F.fill(rng, hexc('#1E1830'), 4)
    x0, y0, w, h = F.rect['north']
    F.put(x0, y0, hexc('#0A0810'))
    F.put(x0 + w - 1, y0, hexc('#0A0810'))


def m_dusk_leg(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if y >= y0 + h - 2:
                    c = hexc('#14101C')
                elif y == y0 + h - 3:
                    c = DUSK_GLOW
                    F.lit(x, y, hexc('#FFC870', 150))
                else:
                    c = jitter(rng, DUSK_DARK, 4)
                F.put(x, y, c)


def m_dusk_antler(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                F.put(x, y, jitter(rng, hexc('#E8D49A'), 6))
                F.lit(x, y, hexc('#FFC870', 210 if (x + y) % 3 else 255))


def m_dusk_tail(F, rng):
    F.fill(rng, hexc('#C8B8F0'), 6)


def m_white(F, rng):
    F.fill(rng, hexc('#EDE6DA'), 5)


def m_brass(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                c = hexc('#6A4E1A') if edge else hexc('#B8892E')
                if not edge and (x - x0) % 4 == 1 and (y - y0) % 4 == 1:
                    c = hexc('#E8C060')
                F.put(x, y, jitter(rng, c, 6))


def m_sentry_head(F, rng):
    F.fill(rng, hexc('#4A4E56'), 5)
    x0, y0, w, h = F.rect['north']
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy)
            if d <= min(w, h) / 2:
                c = hexc('#E8DCC0')
                if d > min(w, h) / 2 - 0.9:
                    c = hexc('#B8892E')
                F.put(x0 + x, y0 + y, c)
    ex, ey = x0 + int(cx), y0 + int(cy)
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
        if ex + dx < x0 + w and ey + dy < y0 + h:
            F.put(ex + dx, ey + dy, hexc('#FF5A3A'))
            F.lit(ex + dx, ey + dy, hexc('#FF7A4A', 255))


def m_gear(F, rng):
    for f, x0, y0, w, h in F.each(('north', 'south')):
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for y in range(h):
            for x in range(w):
                d = math.hypot(x - cx, y - cy)
                ang = math.atan2(y - cy, x - cx)
                tooth = math.cos(ang * 8) > 0.2
                if d <= w / 2 - (0 if tooth else 1) and d >= 1.0:
                    F.put(x0 + x, y0 + y, jitter(rng, hexc('#C8962E') if d < w / 2 - 1.5 else hexc('#8A6420'), 5))
    F.fill(rng, hexc('#8A6420'), 4, ('up', 'down', 'east', 'west'))


def m_iron_leg(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = hexc('#3A3E46')
                if (y - y0) in (0, h - 1):
                    c = hexc('#B8892E')
                F.put(x, y, jitter(rng, c, 4))


def m_wraith_robe(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                c = hexc('#2A2440')
                if (x + y) % 7 == 0:
                    c = hexc('#3A3258')
                F.put(x, y, jitter(rng, c, 5))
        if f == 'north' and w >= 6:
            for i in range(1, h - 1, 3):
                F.lit(x0 + w // 2, y0 + i, hexc('#B79CE8', 150))


def m_wraith_hood(F, rng):
    m_wraith_robe(F, rng)
    x0, y0, w, h = F.rect['north']
    for y in range(y0 + 1, y0 + h):
        for x in range(x0 + 1, x0 + w - 1):
            F.put(x, y, hexc('#0A0810'))
    for ex in (x0 + 2, x0 + w - 3):
        F.put(ex, y0 + 4, hexc('#E8D0FF'))
        F.lit(ex, y0 + 4, hexc('#F0E0FF', 255))


def m_claw(F, rng):
    F.fill(rng, hexc('#C8C0D8'), 6)


def m_tatter(F, rng):
    for f, x0, y0, w, h in F.each(('north', 'south')):
        cuts = [rng.randint(0, 4) for _ in range(w)]
        for x in range(w):
            for y in range(h - cuts[x]):
                F.put(x0 + x, y0 + y, jitter(rng, hexc('#2A2440') if y < h - cuts[x] - 1 else hexc('#463C66'), 5))


def m_rune_ring(F, rng):
    for f, x0, y0, w, h in F.each(('up', 'down')):
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for y in range(h):
            for x in range(w):
                d = math.hypot(x - cx, y - cy)
                if w / 2 - 1.6 < d <= w / 2 - 0.4 and (x * 3 + y) % 4 != 0:
                    F.put(x0 + x, y0 + y, hexc('#C8A8FF', 220))
                    F.lit(x0 + x, y0 + y, hexc('#D8C0FF', 255))


def m_wisp_core(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                F.put(x, y, hexc('#FFF6D8'))
                F.lit(x, y, hexc('#FFE8A0', 255))


def m_wisp_shell(F, rng):
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                F.put(x, y, hexc('#9FE8FF', 70))
                F.lit(x, y, hexc('#BFF4FF', 60))


MATERIALS = {
    'dusk_fur': m_dusk_fur, 'dusk_body': m_dusk_body, 'dusk_head': m_dusk_head, 'dusk_muzzle': m_dusk_muzzle,
    'dusk_leg': m_dusk_leg, 'dusk_antler': m_dusk_antler, 'dusk_tail': m_dusk_tail,
    'moth_fur': m_moth_fur, 'moth_head': m_moth_head, 'antenna': m_antenna, 'moth_lantern': m_moth_lantern,
    'moth_wing': m_moth_wing, 'moth_wing_r': m_moth_wing_r,
    'crow_body': m_crow('#1E1E26'), 'crow_wing': m_crow('#16161E'), 'crow_tail': m_crow('#14141A'),
    'crow_head': m_crow_head, 'beak': m_beak, 'crow_leg': m_crow_leg,
    'shell': m_shell, 'belly': m_belly, 'moss': m_moss, 'mushroom': m_mushroom, 'sprout': m_sprout,
    'mossback_skin': m_mossback_skin, 'mossback_head': m_mossback_head, 'horn': m_horn,
    'stag_fur': m_stag_fur, 'stag_head': m_stag_head, 'stag_muzzle': m_stag_muzzle, 'stag_leg': m_stag_leg,
    'antler': m_antler, 'white': m_white,
    'brass': m_brass, 'sentry_head': m_sentry_head, 'gear': m_gear, 'iron_leg': m_iron_leg,
    'wraith_robe': m_wraith_robe, 'wraith_hood': m_wraith_hood, 'claw': m_claw, 'tatter': m_tatter, 'rune_ring': m_rune_ring,
    'wisp_core': m_wisp_core, 'wisp_shell': m_wisp_shell,
}


# ---------------------------------------------------------------- specs (root at the feet, y down)

def moth():
    head = Part('head', pivot=(0, -0.5, -2), boxes=[Box((-1, -1, -2), (2, 2, 2), 'moth_head')], children=[
        Part('antenna_l', pivot=(0.5, -1, -3), rot=(-0.5, 0, 0.35), boxes=[Box((0, -4, 0), (1, 4, 0), 'antenna')]),
        Part('antenna_r', pivot=(-0.5, -1, -3), rot=(-0.5, 0, -0.35), boxes=[Box((-1, -4, 0), (1, 4, 0), 'antenna')])])
    abdomen = Part('abdomen', pivot=(0, 0, 2), rot=(-0.15, 0, 0), boxes=[Box((-1.5, -1.5, 0), (3, 3, 4), 'moth_lantern')])
    wing_l = Part('wing_l', pivot=(1.5, -1.5, -0.5), boxes=[Box((0, 0, -3), (7, 0, 7), 'moth_wing')])
    wing_r = Part('wing_r', pivot=(-1.5, -1.5, -0.5), boxes=[Box((-7, 0, -3), (7, 0, 7), 'moth_wing_r')])
    body = Part('body', pivot=(0, -5, 0), boxes=[Box((-1.5, -1.5, -2), (3, 3, 4), 'moth_fur')], children=[head, abdomen, wing_l, wing_r])
    return Part('root', pivot=(0, 24, 0), children=[body])


def crow():
    head = Part('head', pivot=(0, -2, -3), rot=(-0.35, 0, 0), boxes=[
        Box((-1.5, -3, -2.5), (3, 3, 3), 'crow_head'), Box((-0.5, -2, -4.5), (1, 1, 2), 'beak')])
    body = Part('body', pivot=(0, -4.5, 0), rot=(0.35, 0, 0), boxes=[Box((-1.5, -2, -3), (3, 4, 6), 'crow_body')], children=[
        head,
        Part('wing_l', pivot=(1.5, -1.5, -2), boxes=[Box((0, 0, 0), (1, 3, 6), 'crow_wing')]),
        Part('wing_r', pivot=(-1.5, -1.5, -2), boxes=[Box((-1, 0, 0), (1, 3, 6), 'crow_wing')]),
        Part('tail', pivot=(0, -1, 3), rot=(-0.5, 0, 0), boxes=[Box((-1.5, 0, 0), (3, 1, 4), 'crow_tail')])])
    legs = [Part('leg_l', pivot=(1, -2.5, 0.5), boxes=[Box((-0.5, 0, 0), (1, 2.5, 0), 'crow_leg')]),
            Part('leg_r', pivot=(-1, -2.5, 0.5), boxes=[Box((-0.5, 0, 0), (1, 2.5, 0), 'crow_leg')])]
    return Part('root', pivot=(0, 24, 0), children=[body] + legs)


def mossback():
    moss = Part('moss', boxes=[Box((-6.5, -10.8, -8.5), (13, 1, 17), 'moss'), Box((2, -13, -2), (2, 2, 2), 'mushroom'),
                               Box((-4, -12.5, 3), (1, 2, 1), 'sprout'), Box((-5.5, -14.5, 2), (3, 2, 3), 'moss'),
                               Box((3.5, -12, 4), (1, 1, 1), 'mushroom')])
    head = Part('head', pivot=(0, -1, -10), boxes=[Box((-3, -3, -6), (6, 5, 6), 'mossback_head'),
                                                   Box((-4, -5, -4), (1, 3, 1), 'horn'), Box((3, -5, -4), (1, 3, 1), 'horn')])
    tail = Part('tail', pivot=(0, -1, 10), rot=(-0.3, 0, 0), boxes=[Box((-1, -1, 0), (2, 2, 4), 'mossback_skin')])
    body = Part('body', pivot=(0, -11, 0), boxes=[
        Box((-8, -7, -10), (16, 7, 20), 'shell'), Box((-6.5, -10, -8), (13, 3, 16), 'shell'), Box((-7, 0, -9), (14, 2, 18), 'belly')],
        children=[moss, head, tail])
    legs = [Part(n, pivot=(x, -9, z), boxes=[Box((-2.5, 0, -2.5), (5, 9, 5), 'mossback_skin')])
            for n, x, z in (('leg_fl', -6, -6), ('leg_fr', 6, -6), ('leg_bl', -6, 7), ('leg_br', 6, 7))]
    return Part('root', pivot=(0, 24, 0), children=[body] + legs)


def stag(m=None):
    m = m or {'antler': 'antler', 'head': 'stag_head', 'muzzle': 'stag_muzzle', 'fur': 'stag_fur', 'body': 'stag_fur',
              'tail': 'white', 'leg': 'stag_leg'}

    def antler(side):
        s = 1 if side > 0 else -1
        x = lambda v, w: v if s > 0 else -v - w
        return Part('antler_l' if s > 0 else 'antler_r', pivot=(s * 1.5, -3, -1), rot=(0, 0, s * 0.25), boxes=[
            Box((x(0, 1), -8, 0), (1, 8, 1), m['antler']),
            Box((x(1, 2), -4, 0), (2, 1, 1), m['antler']),
            Box((x(2, 1), -7, 0), (1, 3, 1), m['antler']),
            Box((x(0, 1), -11, -1), (1, 3, 1), m['antler']),
            Box((x(-1, 1), -10, 0), (1, 1, 1), m['antler'])])
    head = Part('head', pivot=(0, -8, 0), rot=(-0.55, 0, 0), boxes=[
        Box((-2.5, -3, -6), (5, 5, 7), m['head']), Box((-1.5, -1, -8), (3, 3, 2), m['muzzle'])], children=[
        Part('ear_l', pivot=(2.5, -2.5, -1), rot=(0, 0, -0.5), boxes=[Box((0, -1, -0.5), (3, 2, 1), m['fur'])]),
        Part('ear_r', pivot=(-2.5, -2.5, -1), rot=(0, 0, 0.5), boxes=[Box((-3, -1, -0.5), (3, 2, 1), m['fur'])]),
        antler(1), antler(-1)])
    neck = Part('neck', pivot=(0, -3, -6), rot=(0.55, 0, 0), boxes=[Box((-2, -8, -2), (4, 9, 4), m['fur'])], children=[head])
    tail = Part('tail', pivot=(0, -3, 8), rot=(0.3, 0, 0), boxes=[Box((-1, 0, 0), (2, 3, 1), m['tail'])])
    body = Part('body', pivot=(0, -13, 0), boxes=[Box((-3.5, -4, -8), (7, 7, 16), m['body'])], children=[neck, tail])
    legs = [Part(n, pivot=(x, -10, z), boxes=[Box((-1, 0, -1), (2, 10, 2), m['leg'])])
            for n, x, z in (('leg_fl', -2.2, -6), ('leg_fr', 2.2, -6), ('leg_bl', -2.2, 6), ('leg_br', 2.2, 6))]
    return Part('root', pivot=(0, 24, 0), children=[body] + legs)


def duskstrider():
    return stag({'antler': 'dusk_antler', 'head': 'dusk_head', 'muzzle': 'dusk_muzzle', 'fur': 'dusk_fur', 'body': 'dusk_body',
                 'tail': 'dusk_tail', 'leg': 'dusk_leg'})


def sentry():
    body = Part('body', pivot=(0, -12, 0), boxes=[Box((-5, -4, -5), (10, 7, 10), 'brass')], children=[
        Part('head', pivot=(0, -4, -2), boxes=[Box((-3, -5, -3), (6, 5, 6), 'sentry_head')]),
        Part('gear', pivot=(0, -1, 5), boxes=[Box((-4, -4, 0), (8, 8, 1), 'gear')])])
    legs = []
    for n, sx, sz in (('leg_fl', -1, -1), ('leg_fr', 1, -1), ('leg_bl', -1, 1), ('leg_br', 1, 1)):
        lower = Part(n + '_lower', pivot=(sx * 6, 0, 0), boxes=[Box((-1, 0, -1), (2, 11, 2), 'iron_leg')])
        legs.append(Part(n, pivot=(sx * 4.5, -12, sz * 3.5), rot=(0, sz * sx * -0.5, 0), boxes=[
            Box((0 if sx > 0 else -7, -1, -1), (7, 2, 2), 'iron_leg')], children=[lower]))
    return Part('root', pivot=(0, 24, 0), children=[body] + legs)


def wraith():
    body = Part('body', pivot=(0, -13, 0), boxes=[Box((-4, -11, -2.5), (8, 11, 5), 'wraith_robe')], children=[
        Part('hood', pivot=(0, -11, 0), boxes=[Box((-4, -8, -4), (8, 8, 8), 'wraith_hood')]),
        Part('arm_l', pivot=(5, -10, 0), boxes=[Box((-1.5, 0, -1.5), (3, 11, 3), 'wraith_robe'), Box((-1.5, 11, -1.5), (3, 3, 3), 'claw')]),
        Part('arm_r', pivot=(-5, -10, 0), boxes=[Box((-1.5, 0, -1.5), (3, 11, 3), 'wraith_robe'), Box((-1.5, 11, -1.5), (3, 3, 3), 'claw')]),
        Part('tatter_f', pivot=(0, 0, -2.5), boxes=[Box((-4, 0, 0), (8, 9, 0), 'tatter')]),
        Part('tatter_b', pivot=(0, 0, 2.5), boxes=[Box((-4, 0, 0), (8, 10, 0), 'tatter')]),
        Part('tatter_l', pivot=(4, 0, 0), rot=(0, HALF_PI, 0), boxes=[Box((-2.5, 0, 0), (5, 8, 0), 'tatter')]),
        Part('tatter_r', pivot=(-4, 0, 0), rot=(0, HALF_PI, 0), boxes=[Box((-2.5, 0, 0), (5, 8, 0), 'tatter')]),
        Part('halo', pivot=(0, -21, 0), boxes=[Box((-6, 0, -6), (12, 0, 12), 'rune_ring')])])
    return Part('root', pivot=(0, 24, 0), children=[body])


def wisp():
    core = Part('core', pivot=(0, -6, 0), boxes=[Box((-1.5, -1.5, -1.5), (3, 3, 3), 'wisp_core'), Box((-2.5, -2.5, -2.5), (5, 5, 5), 'wisp_shell')], children=[
        Part('orbit_a', boxes=[Box((3, -0.5, -0.5), (1, 1, 1), 'wisp_core')]),
        Part('orbit_b', rot=(0.6, 0, 0), boxes=[Box((-4, -0.5, -0.5), (1, 1, 1), 'wisp_core')]),
        Part('orbit_c', rot=(0, 0, 0.7), boxes=[Box((-0.5, -0.5, 3.5), (1, 1, 1), 'wisp_core')])])
    return Part('root', pivot=(0, 24, 0), children=[core])


SPECS = [
    ('LanternMothLayer', 'lantern_moth', moth, 64, 11),
    ('CrowLayer', 'crow', crow, 64, 12),
    ('MossbackLayer', 'mossback', mossback, 128, 13),
    ('HollowStagLayer', 'hollow_stag', stag, 64, 14),
    ('DuskstriderLayer', 'duskstrider', duskstrider, 64, 31),
    ('ClockworkSentryLayer', 'clockwork_sentry', sentry, 64, 15),
    ('RuleWraithLayer', 'rule_wraith', wraith, 64, 16),
    ('WispLayer', 'wisp', wisp, 32, 17),
]

# The second wave lives in its own module.
from mobs_more import MORE_MATERIALS, MORE_SPECS  # noqa: E402
MATERIALS.update(MORE_MATERIALS)
SPECS += MORE_SPECS

# The second bestiary.
import mobs_v2  # noqa: E402
MATERIALS.update(mobs_v2.V2_MATERIALS)
SPECS += mobs_v2.V2_SPECS

# The Wardens of the Lands (registers its materials into the same table).
import bosses_v2  # noqa: E402
SPECS += bosses_v2.BOSS_SPECS
MATERIALS.update(mobs_v2.V2_MATERIALS)


SHADOW = (26, 30, 52)
WARM = (255, 246, 226)


def light(bp, gp, box):
    """Modern shading over a painted box: lit tops, shaded undersides, a soft gradient down every
    side with cool shadows and warm highlights, and darkened edges where faces meet."""
    F = Faces(bp, gp, box)
    for f, x0, y0, w, h in F.each():
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                r, g, b, a = bp[x, y]
                if a == 0 or gp[x, y][3] > 0:
                    continue
                lum = 0.299 * r + 0.587 * g + 0.114 * b
                if lum < 22:
                    continue
                t = (y - y0) / max(1, h - 1)
                if f == 'up':
                    k = 1.10
                elif f == 'down':
                    k = 0.70
                else:
                    k = 1.06 - 0.20 * t if h > 2 else 0.98
                    if f in ('east', 'west'):
                        k *= 0.94
                # Edges darken (ambient occlusion), the top rim of each side catches the light.
                if f not in ('up', 'down') and h > 3:
                    if y == y0 + h - 1:
                        k *= 0.86
                    elif y == y0:
                        k *= 1.05
                    if w > 3 and (x == x0 or x == x0 + w - 1):
                        k *= 0.93
                elif f == 'up' and w > 3 and h > 3 and (x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)):
                    k *= 0.95
                nr, ng, nb = r * k, g * k, b * k
                if k < 1:
                    m = min(0.35, (1 - k) * 0.9)
                    nr, ng, nb = nr * (1 - m) + SHADOW[0] * m * k, ng * (1 - m) + SHADOW[1] * m * k, nb * (1 - m) + SHADOW[2] * m * k
                else:
                    m = min(0.2, (k - 1) * 1.2)
                    nr, ng, nb = nr * (1 - m) + WARM[0] * m * k * 0.6, ng * (1 - m) + WARM[1] * m * k * 0.6, nb * (1 - m) + WARM[2] * m * k * 0.6
                bp[x, y] = (max(0, min(255, int(nr))), max(0, min(255, int(ng))), max(0, min(255, int(nb))), a)


def main():
    os.makedirs(TEX_OUT, exist_ok=True)
    for cls, tex, fn, width, seed in SPECS:
        root = fn()
        th = pack(root, width)
        write_java(cls, root, width, th)
        base = Image.new('RGBA', (width, th), (0, 0, 0, 0))
        glow = Image.new('RGBA', (width, th), (0, 0, 0, 0))
        bp, gp = base.load(), glow.load()
        rng = random.Random(seed)
        for b in all_boxes(root):
            MATERIALS[b.mat](Faces(bp, gp, b), rng)
        for b in all_boxes(root):
            light(bp, gp, b)
        if tex == 'rule_wraith':
            # Spectral: everything but the eyes and runes is partly see-through.
            for y in range(th):
                for x in range(width):
                    r, g, b, a = bp[x, y]
                    if a and not gp[x, y][3]:
                        bp[x, y] = (r, g, b, int(a * 0.78))
        base.save(os.path.join(TEX_OUT, tex + '.png'))
        if glow.getbbox():
            glow.save(os.path.join(TEX_OUT, tex + '_glow.png'))
        print(cls, width, 'x', th, '(glow)' if glow.getbbox() else '')
    mobs_v2.write_registry()
    mobs_v2.eggs()
    bosses_v2.write_registry()


if __name__ == '__main__':
    main()

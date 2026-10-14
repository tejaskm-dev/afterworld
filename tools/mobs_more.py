"""The second wave of Afterworld's creatures: materials and model specs, merged into gen_mobs.py.

Monsters: ashen husk, shardling, bog lurker, echo stalker, gearhound, rime wight.
Wildlife: skywhale, glimmerfin, emberfox, rimehorn, petal sprite, mirror ray.
Coordinates are model pixels with y pointing down and the root pivot at the feet (0, 24, 0).
"""
import math

from gen_models import Box, Part

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


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def each_px(F, faces=None):
    for f, x0, y0, w, h in F.each(faces):
        for y in range(h):
            for x in range(w):
                yield f, x0 + x, y0 + y, x, y, w, h


def eyes(F, face, color, glow, row=1, inset=1, size=1):
    """Two eyes on a face, inset from the sides, lit on the glow layer."""
    x0, y0, w, h = F.rect[face]
    for ex in (x0 + inset, x0 + w - 1 - inset - (size - 1)):
        for dx in range(size):
            for dy in range(size):
                F.put(ex + dx, y0 + row + dy, color)
                if glow:
                    F.lit(ex + dx, y0 + row + dy, glow)


# ---------------------------------------------------------------- ashen husk

def m_husk_skin(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        c = jitter(rng, hexc('#2E2A28'), 6)
        if rng.random() < 0.18:
            c = jitter(rng, hexc('#45403C'), 5)
        F.put(x, y, c)
    # Ember cracks: short glowing veins.
    for f, x0, y0, w, h in F.each():
        for _ in range(max(1, w * h // 18)):
            cx, cy = rng.randrange(w), rng.randrange(h)
            for _ in range(rng.randint(2, 4)):
                if 0 <= cx < w and 0 <= cy < h:
                    F.put(x0 + cx, y0 + cy, hexc('#E8641E'))
                    F.lit(x0 + cx, y0 + cy, hexc('#FF8A3A', 230))
                cx += rng.choice((-1, 0, 1))
                cy += 1


def m_husk_head(F, rng):
    m_husk_skin(F, rng)
    x0, y0, w, h = F.rect['north']
    for x in range(x0 + 1, x0 + w - 1):
        F.put(x, y0 + 5, hexc('#120E0C'))
    for x in range(x0 + 2, x0 + w - 2, 2):
        F.put(x, y0 + 6, hexc('#E8641E'))
        F.lit(x, y0 + 6, hexc('#FF8A3A', 200))
    eyes(F, 'north', hexc('#FFC24A'), hexc('#FFD27A', 255), row=3, inset=1, size=2)


def m_husk_rags(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        c = jitter(rng, hexc('#4A3A2C'), 6)
        if (lx + ly * 3) % 7 == 0:
            c = hexc('#2A2018')
        if ly >= h - 2 and rng.random() < 0.4:
            continue
        F.put(x, y, c)


# ---------------------------------------------------------------- shardling

def m_shard_body(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        c = jitter(rng, hexc('#3A3446'), 6)
        if (lx * 2 + ly) % 5 == 0:
            c = jitter(rng, hexc('#4E4660'), 5)
        F.put(x, y, c)


def m_crystal(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        t = ly / max(1, h - 1)
        c = mix(hexc('#E0C8FF'), hexc('#8A5CD0'), t)
        if lx == 0 or (f in ('east', 'north') and lx == w - 1):
            c = shade(c, 0.8)
        F.put(x, y, c)
        if ly < h // 2 or (lx + ly) % 3 == 0:
            F.lit(x, y, hexc('#C8A8FF', 150 if ly else 220))


def m_shard_leg(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        F.put(x, y, jitter(rng, hexc('#2A2634') if lx % 3 else hexc('#8A5CD0'), 4))


def m_shard_eyes(F, rng):
    F.fill(rng, hexc('#2A2634'), 3)
    x0, y0, w, h = F.rect['north']
    for i, ex in enumerate(range(x0, x0 + w, 2)):
        F.put(ex, y0 + (i % 2), hexc('#F0E0FF'))
        F.lit(ex, y0 + (i % 2), hexc('#E8D8FF', 255))


# ---------------------------------------------------------------- bog lurker

def m_lurker_skin(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        base = hexc('#4A5A2E') if f != 'down' else hexc('#A89A6A')
        c = jitter(rng, base, 7)
        if f != 'down' and rng.random() < 0.12:
            c = jitter(rng, hexc('#2E3A1C'), 5)   # warts
        if f != 'down' and rng.random() < 0.05:
            c = jitter(rng, hexc('#7A8A3A'), 5)
        F.put(x, y, c)


def m_lurker_jaw(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        c = jitter(rng, hexc('#B8A87A'), 6) if f in ('down', 'north', 'east', 'west', 'south') else jitter(rng, hexc('#6A2A2A'), 6)
        F.put(x, y, c)
    x0, y0, w, h = F.rect['up']
    for x in range(x0, x0 + w, 2):
        F.put(x, y0, hexc('#F0E8D0'))
        F.put(x, y0 + h - 1, hexc('#F0E8D0'))


def m_lurker_head(F, rng):
    m_lurker_skin(F, rng)
    x0, y0, w, h = F.rect['down']
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            F.put(x, y, jitter(rng, hexc('#6A2A2A'), 5))
    for x in range(x0, x0 + w, 2):
        F.put(x, y0 + h - 1, hexc('#F0E8D0'))
    x0, y0, w, h = F.rect['north']
    for x in range(x0, x0 + w, 2):
        F.put(x, y0 + h - 1, hexc('#F0E8D0'))


def m_lurker_eye(F, rng):
    F.fill(rng, hexc('#4A5A2E'), 4)
    for f in ('north', 'up', 'east', 'west'):
        x0, y0, w, h = F.rect[f]
        F.put(x0 + w // 2, y0, hexc('#F2D24A'))
        F.lit(x0 + w // 2, y0, hexc('#FFE070', 230))
        if w > 1:
            F.put(x0 + w // 2 - 1, y0, hexc('#100C08'))


# ---------------------------------------------------------------- echo stalker

def m_stalker_body(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        c = jitter(rng, hexc('#141220'), 4)
        if (lx + ly * 2) % 9 == 0:
            c = hexc('#2A2244')
            F.lit(x, y, hexc('#6A4AB0', 90))
        F.put(x, y, c)


def m_stalker_mask(F, rng):
    F.fill(rng, hexc('#141220'), 3)
    x0, y0, w, h = F.rect['north']
    for y in range(h):
        for x in range(w):
            if 1 <= x < w - 1 and y < h - 1:
                F.put(x0 + x, y0 + y, jitter(rng, hexc('#E8E0D4'), 4))
    for ex in (x0 + 1, x0 + w - 3):
        for dx in range(2):
            F.put(ex + dx, y0 + 3, hexc('#0A0810'))
            F.put(ex + dx, y0 + 2, hexc('#B79CE8'))
            F.lit(ex + dx, y0 + 2, hexc('#D8C0FF', 255))
    F.put(x0 + w // 2, y0 + 5, hexc('#0A0810'))


def m_stalker_tatter(F, rng):
    for f, x0, y0, w, h in F.each(('north', 'south')):
        cuts = [rng.randint(0, 6) for _ in range(w)]
        for x in range(w):
            for y in range(h - cuts[x]):
                F.put(x0 + x, y0 + y, jitter(rng, hexc('#141220') if y < h - cuts[x] - 2 else hexc('#2A2244'), 3))


# ---------------------------------------------------------------- gearhound

def m_hound_brass(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        edge = lx in (0, w - 1) or ly in (0, h - 1)
        c = hexc('#6A4E1A') if edge else hexc('#B8892E')
        if not edge and (lx * 3 + ly) % 6 == 0:
            c = hexc('#E8C060')
        if not edge and f in ('east', 'west') and ly == h // 2:
            c = hexc('#4A3410')   # rib seam
        F.put(x, y, jitter(rng, c, 5))


def m_hound_head(F, rng):
    m_hound_brass(F, rng)
    eyes(F, 'north', hexc('#FF4A2A'), hexc('#FF6A3A', 255), row=1, inset=0)
    x0, y0, w, h = F.rect['east']
    F.put(x0 + 1, y0 + 1, hexc('#FF4A2A'))
    F.lit(x0 + 1, y0 + 1, hexc('#FF6A3A', 255))
    x0, y0, w, h = F.rect['west']
    F.put(x0 + w - 2, y0 + 1, hexc('#FF4A2A'))
    F.lit(x0 + w - 2, y0 + 1, hexc('#FF6A3A', 255))


def m_iron(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        F.put(x, y, jitter(rng, hexc('#3A3E46') if (lx + ly) % 4 else hexc('#565C66'), 4))


def m_hound_teeth(F, rng):
    F.fill(rng, hexc('#2A2E34'), 3)
    x0, y0, w, h = F.rect['north']
    for x in range(x0, x0 + w):
        F.put(x, y0 + h - 1, hexc('#E8E4DC') if (x - x0) % 2 == 0 else hexc('#2A2E34'))


# ---------------------------------------------------------------- rime wight

def m_rime_robe(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        t = ly / max(1, h - 1)
        c = mix(hexc('#DCEEFF', 230), hexc('#6A9AC8', 160), t)
        if (lx + ly * 3) % 8 == 0:
            c = hexc('#FFFFFF', 240)
        F.put(x, y, jitter(rng, c, 5))
        if (lx * 5 + ly) % 11 == 0:
            F.lit(x, y, hexc('#BFE8FF', 120))


def m_rime_face(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        F.put(x, y, jitter(rng, hexc('#EAF4FF'), 4))
    x0, y0, w, h = F.rect['north']
    for x in range(x0 + 1, x0 + w - 1):
        F.put(x, y0 + h - 2, hexc('#9AB8D8'))
    eyes(F, 'north', hexc('#4AC8FF'), hexc('#8AE0FF', 255), row=2, inset=1, size=2)


def m_icicle(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        c = mix(hexc('#FFFFFF'), hexc('#7AC8F0'), ly / max(1, h - 1))
        F.put(x, y, c)
        F.lit(x, y, hexc('#BFF0FF', 150))


def m_rime_mist(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        a = int(170 * (1 - ly / max(1, h)))
        if a > 20 and rng.random() > ly / max(1, h) * 0.7:
            F.put(x, y, hexc('#C8E4FF', a))


# ---------------------------------------------------------------- skywhale

def m_whale_back(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        base = hexc('#2C3E5C')
        if f in ('east', 'west'):
            base = mix(hexc('#2C3E5C'), hexc('#7A92B0'), ly / max(1, h - 1))
        c = jitter(rng, base, 5)
        if rng.random() < 0.035:
            c = hexc('#C8D8E8')   # pale spots
        if rng.random() < 0.01:
            c = hexc('#5A5A4A')   # barnacles
        F.put(x, y, c)
    # Bioluminescent lines along the flanks.
    for f in ('east', 'west'):
        x0, y0, w, h = F.rect[f]
        for x in range(w):
            yy = y0 + h * 2 // 3 + int(math.sin(x * 0.4) * 1.5)
            if (x // 3) % 2 == 0 and y0 <= yy < y0 + h:
                F.put(x0 + x, yy, hexc('#8AF0E8'))
                F.lit(x0 + x, yy, hexc('#9AFFF0', 220))


def m_whale_belly(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        c = jitter(rng, hexc('#C8D4DC'), 5)
        if f in ('down', 'up') and lx % 3 == 0:
            c = hexc('#9AA8B4')   # throat grooves
            if (ly // 4) % 2 == 0:
                F.lit(x, y, hexc('#7AE8FF', 140))
        F.put(x, y, c)


def m_whale_head(F, rng):
    m_whale_back(F, rng)
    for face in ('east', 'west'):
        x0, y0, w, h = F.rect[face]
        ex = x0 + (2 if face == 'west' else w - 3)
        F.put(ex, y0 + h - 4, hexc('#0A0E14'))
        F.put(ex, y0 + h - 5, hexc('#E8F4FF'))
        F.lit(ex, y0 + h - 5, hexc('#BFF4FF', 200))


def m_whale_fin(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        c = jitter(rng, hexc('#33476A') if f == 'up' else hexc('#A8B8C8'), 5)
        if lx == w - 1 or ly in (0, h - 1):
            c = shade(c, 0.8)
        if f == 'down' and (lx + ly) % 5 == 0:
            F.lit(x, y, hexc('#8AF0E8', 120))
        F.put(x, y, c)


# ---------------------------------------------------------------- glimmerfin

def m_fin_body(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        c = mix(hexc('#5AC8C0'), hexc('#D8F0F0'), ly / max(1, h - 1))
        if f in ('east', 'west') and ly == 1:
            c = hexc('#FFF0A0')
            F.lit(x, y, hexc('#FFF4B0', 255))
        F.put(x, y, jitter(rng, c, 4))
    for face in ('east', 'west'):
        x0, y0, w, h = F.rect[face]
        ex = x0 + (w - 1 if face == 'east' else 0)
        F.put(ex, y0, hexc('#101418'))


def m_fin_tail(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        F.put(x, y, hexc('#8AE0D8', 200))
        F.lit(x, y, hexc('#AFFFF0', 120))


# ---------------------------------------------------------------- emberfox

def m_fox_fur(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        base = hexc('#B8441E') if f != 'down' else hexc('#E8D0B0')
        c = jitter(rng, base, 7)
        if f != 'down' and rng.random() < 0.1:
            c = jitter(rng, hexc('#8A2E14'), 5)
        F.put(x, y, c)


def m_fox_head(F, rng):
    m_fox_fur(F, rng)
    x0, y0, w, h = F.rect['north']
    for y in range(y0 + h // 2, y0 + h):
        for x in range(x0 + 1, x0 + w - 1):
            F.put(x, y, jitter(rng, hexc('#F0E0C8'), 4))
    eyes(F, 'north', hexc('#FFD24A'), hexc('#FFE070', 200), row=2, inset=1)


def m_fox_snout(F, rng):
    F.fill(rng, hexc('#F0E0C8'), 4)
    x0, y0, w, h = F.rect['north']
    F.put(x0 + w // 2, y0, hexc('#14100C'))


def m_fox_leg(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        F.put(x, y, jitter(rng, hexc('#2A1A12') if ly >= h - 3 else hexc('#9A3A1A'), 5))


def m_ember_tail(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        t = ly / max(1, h - 1)
        if t < 0.55:
            c = jitter(rng, hexc('#B8441E'), 6)
        else:
            c = mix(hexc('#FF8A2A'), hexc('#FFE070'), (t - 0.55) / 0.45)
            F.lit(x, y, hexc('#FFB04A', int(140 + 110 * t)))
        F.put(x, y, c)


# ---------------------------------------------------------------- rimehorn

def m_shag(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        c = jitter(rng, hexc('#D8D4CC'), 8)
        if lx % 2 == 0 and rng.random() < 0.4:
            c = jitter(rng, hexc('#B8B2A6'), 5)
        if f == 'up' and rng.random() < 0.08:
            c = hexc('#FFFFFF')   # frost
        F.put(x, y, c)


def m_shag_fringe(F, rng):
    for f, x0, y0, w, h in F.each(('north', 'south', 'east', 'west')):
        cuts = [rng.randint(0, 2) for _ in range(w)]
        for x in range(w):
            for y in range(h - cuts[x]):
                F.put(x0 + x, y0 + y, jitter(rng, hexc('#C8C2B4'), 6))


def m_yak_head(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        F.put(x, y, jitter(rng, hexc('#4A4038'), 5))
    x0, y0, w, h = F.rect['north']
    for y in range(y0, y0 + 3):
        for x in range(x0, x0 + w):
            F.put(x, y, jitter(rng, hexc('#D8D4CC'), 6))
    eyes(F, 'north', hexc('#14100C'), None, row=3, inset=1)
    F.put(x0 + 2, y0 + h - 2, hexc('#1A1410'))
    F.put(x0 + w - 3, y0 + h - 2, hexc('#1A1410'))


def m_yak_horn(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        F.put(x, y, jitter(rng, hexc('#8AC8E8') if (lx + ly) % 3 else hexc('#D8F0FF'), 4))
        F.lit(x, y, hexc('#AFE8FF', 90))


def m_shag_leg(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        F.put(x, y, jitter(rng, hexc('#2A2420') if ly >= h - 2 else hexc('#C8C2B4'), 6))


# ---------------------------------------------------------------- petal sprite

def m_sprite_body(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        F.put(x, y, jitter(rng, hexc('#F8E8F0'), 4))
        F.lit(x, y, hexc('#FFE0F0', 200))


def m_sprite_head(F, rng):
    m_sprite_body(F, rng)
    x0, y0, w, h = F.rect['north']
    F.put(x0, y0 + 1, hexc('#5A3A6A'))
    F.put(x0 + w - 1, y0 + 1, hexc('#5A3A6A'))


def m_petal(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F, ('up', 'down')):
        cx, cy = (w - 1) / 2, (h - 1) / 2
        if ((lx - cx) / (w / 2)) ** 2 + ((ly - cy) / (h / 2)) ** 2 > 1.05:
            continue
        c = mix(hexc('#F8B8D8', 220), hexc('#E87AB0', 220), abs(lx - cx) / max(1, w / 2))
        F.put(x, y, c)
        F.lit(x, y, hexc('#FFC8E8', 110))


def m_flower(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        F.put(x, y, hexc('#FFE070') if (lx + ly) % 2 == 0 else hexc('#F88AB8'))
        F.lit(x, y, hexc('#FFE8A0', 160))


# ---------------------------------------------------------------- mirror ray

def m_ray_back(F, rng):
    for f, x, y, lx, ly, w, h in each_px(F):
        if f == 'down':
            c = jitter(rng, hexc('#E8EEF4'), 4)
        else:
            c = mix(hexc('#3A5A7A'), hexc('#AFC8E0'), ((lx + ly) % 6) / 6.0)   # mirror sheen
            c = jitter(rng, c, 4)
            if (lx * 3 + ly * 5) % 13 == 0:
                c = hexc('#F0F8FF')
                F.lit(x, y, hexc('#D8F0FF', 160))
        F.put(x, y, c)


def m_ray_tail(F, rng):
    F.fill(rng, hexc('#2A3A4A'), 4)


# ---------------------------------------------------------------- specs

def humanoid(prefix, skin, head, rags, arm_skin=None):
    """A husk: zombie-like stance, arms raised toward its prey."""
    arm_skin = arm_skin or skin
    body = Part('body', pivot=(0, -24, 0), boxes=[Box((-4, 0, -2), (8, 12, 4), skin), Box((-4, 6, -2), (8, 6, 4), rags, inflate=0.25)])
    hd = Part('head', pivot=(0, -24, 0), boxes=[Box((-4, -8, -4), (8, 8, 8), head)])
    arm_l = Part('arm_l', pivot=(5, -22, 0), boxes=[Box((-1, -2, -2), (4, 12, 4), arm_skin)])
    arm_r = Part('arm_r', pivot=(-5, -22, 0), boxes=[Box((-3, -2, -2), (4, 12, 4), arm_skin)])
    leg_l = Part('leg_l', pivot=(1.9, -12, 0), boxes=[Box((-2, 0, -2), (4, 12, 4), skin)])
    leg_r = Part('leg_r', pivot=(-1.9, -12, 0), boxes=[Box((-2, 0, -2), (4, 12, 4), rags)])
    return Part('root', pivot=(0, 24, 0), children=[body, hd, arm_l, arm_r, leg_l, leg_r])


def ashen_husk():
    return humanoid('husk', 'husk_skin', 'husk_head', 'husk_rags')


def shardling():
    body = Part('body', pivot=(0, -6, 0), boxes=[
        Box((-3, -3, -4), (6, 5, 8), 'shard_body'),
        Box((-2, -2.5, -6), (4, 3, 2), 'shard_eyes')], children=[
        Part('crystal_a', pivot=(0, -3, 0), rot=(0.15, 0, 0.1), boxes=[Box((-1, -7, -1), (2, 7, 2), 'crystal')]),
        Part('crystal_b', pivot=(2, -3, 2), rot=(0.4, 0, -0.5), boxes=[Box((-0.75, -5, -0.75), (1.5, 5, 1.5), 'crystal')]),
        Part('crystal_c', pivot=(-2, -3, -1), rot=(-0.3, 0, 0.5), boxes=[Box((-0.75, -4, -0.75), (1.5, 4, 1.5), 'crystal')]),
        Part('crystal_d', pivot=(0.5, -3, 3), rot=(0.7, 0, 0.2), boxes=[Box((-0.5, -3, -0.5), (1, 3, 1), 'crystal')])])
    legs = []
    for i, z in enumerate((-2.5, 0, 2.5)):
        for side in (1, -1):
            name = f'leg_{"l" if side > 0 else "r"}{i}'
            yaw = (i - 1) * 0.45 * side
            legs.append(Part(name, pivot=(side * 3, -6, z), rot=(0, yaw, side * 0.6), boxes=[
                Box((0 if side > 0 else -8, -0.5, -0.5), (8, 1, 1), 'shard_leg')]))
    return Part('root', pivot=(0, 24, 0), children=[body] + legs)


def bog_lurker():
    jaw = Part('jaw', pivot=(0, -1, -7), boxes=[Box((-4.5, 0, -9), (9, 2, 9), 'lurker_jaw')])
    head = Part('head', pivot=(0, -4, -7), boxes=[Box((-5, -3, -10), (10, 3, 10), 'lurker_head')], children=[
        Part('eye_l', pivot=(3, -3, -5), boxes=[Box((-1, -2, -1), (2, 2, 2), 'lurker_eye')]),
        Part('eye_r', pivot=(-3, -3, -5), boxes=[Box((-1, -2, -1), (2, 2, 2), 'lurker_eye')])])
    tail = Part('tail', pivot=(0, -4, 7), boxes=[Box((-2.5, -1.5, 0), (5, 3, 8), 'lurker_skin')], children=[
        Part('tail_tip', pivot=(0, 0, 8), boxes=[Box((-1.5, -1, 0), (3, 2, 7), 'lurker_skin')])])
    body = Part('body', pivot=(0, -5, 0), boxes=[Box((-5.5, -4, -7), (11, 6, 15), 'lurker_skin')], children=[jaw, head, tail])
    legs = [Part(n, pivot=(x, -3, z), rot=(0, 0, s * 0.5), boxes=[Box((-1.5, 0, -1.5), (3, 4, 3), 'lurker_skin')])
            for n, x, z, s in (('leg_fl', 5, -5, -1), ('leg_fr', -5, -5, 1), ('leg_bl', 5, 5, -1), ('leg_br', -5, 5, 1))]
    return Part('root', pivot=(0, 24, 0), children=[body] + legs)


def echo_stalker():
    body = Part('body', pivot=(0, -20, 0), boxes=[Box((-3, -14, -1.5), (6, 14, 3), 'stalker_body')], children=[
        Part('head', pivot=(0, -14, 0), boxes=[Box((-3, -7, -3), (6, 7, 6), 'stalker_mask')]),
        Part('arm_l', pivot=(4, -13, 0), boxes=[Box((-1, 0, -1), (2, 21, 2), 'stalker_body')]),
        Part('arm_r', pivot=(-4, -13, 0), boxes=[Box((-1, 0, -1), (2, 21, 2), 'stalker_body')]),
        Part('cloak', pivot=(0, -13, 1.5), rot=(0.12, 0, 0), boxes=[Box((-3.5, 0, 0), (7, 18, 0), 'stalker_tatter')])])
    legs = [Part('leg_l', pivot=(1.6, -20, 0), boxes=[Box((-1, 0, -1), (2, 20, 2), 'stalker_body')]),
            Part('leg_r', pivot=(-1.6, -20, 0), boxes=[Box((-1, 0, -1), (2, 20, 2), 'stalker_body')])]
    return Part('root', pivot=(0, 24, 0), children=[body] + legs)


def gearhound():
    head = Part('head', pivot=(0, -11, -6), boxes=[
        Box((-3, -3, -5), (6, 6, 5), 'hound_head'), Box((-1.5, 0, -8.5), (3, 3, 4), 'hound_teeth')], children=[
        Part('ear_l', pivot=(2, -3, -2), rot=(0, 0, 0.3), boxes=[Box((-1, -3, -0.5), (2, 3, 1), 'iron')]),
        Part('ear_r', pivot=(-2, -3, -2), rot=(0, 0, -0.3), boxes=[Box((-1, -3, -0.5), (2, 3, 1), 'iron')])])
    tail = Part('tail', pivot=(0, -11, 6), rot=(0.7, 0, 0), boxes=[Box((-0.5, -0.5, 0), (1, 1, 7), 'iron')])
    gear = Part('gear', pivot=(3.1, -10, 0), boxes=[Box((0, -3, -3), (1, 6, 6), 'gear')])
    body = Part('body', pivot=(0, 0, 0), boxes=[Box((-3, -13, -6), (6, 6, 12), 'hound_brass')], children=[head, tail, gear])
    legs = [Part(n, pivot=(x, -8, z), boxes=[Box((-1, 0, -1), (2, 8, 2), 'iron_leg')])
            for n, x, z in (('leg_fl', -2, -4.5), ('leg_fr', 2, -4.5), ('leg_bl', -2, 4.5), ('leg_br', 2, 4.5))]
    return Part('root', pivot=(0, 24, 0), children=[body] + legs)


def rime_wight():
    crown = [Part(f'icicle_{i}', pivot=(x, -7, z), rot=(rx, 0, rz), boxes=[Box((-0.5, -h, -0.5), (1, h, 1), 'icicle')])
             for i, (x, z, h, rx, rz) in enumerate(((-2.5, -2, 5, -0.2, -0.3), (0, -2.5, 7, -0.25, 0), (2.5, -2, 5, -0.2, 0.3),
                                                     (-2.5, 1.5, 4, 0.2, -0.3), (2.5, 1.5, 4, 0.2, 0.3)))]
    head = Part('head', pivot=(0, -12, 0), boxes=[Box((-3.5, -7, -3.5), (7, 7, 7), 'rime_face')], children=crown)
    body = Part('body', pivot=(0, -12, 0), boxes=[Box((-4, -12, -2), (8, 12, 4), 'rime_robe')], children=[
        head,
        Part('arm_l', pivot=(5, -11, 0), boxes=[Box((-1, 0, -1), (2, 12, 2), 'rime_robe'), Box((-1.5, 12, -1.5), (3, 3, 3), 'icicle')]),
        Part('arm_r', pivot=(-5, -11, 0), boxes=[Box((-1, 0, -1), (2, 12, 2), 'rime_robe'), Box((-1.5, 12, -1.5), (3, 3, 3), 'icicle')]),
        Part('mist', pivot=(0, 0, 0), boxes=[Box((-3.5, 0, -1.5), (7, 9, 3), 'rime_mist')])])
    return Part('root', pivot=(0, 24, 0), children=[body])


def skywhale():
    fluke = Part('flukes', pivot=(0, 0, 11), boxes=[Box((-14, -0.5, 0), (28, 1, 9), 'whale_fin')])
    tail2 = Part('tail2', pivot=(0, 0, 12), boxes=[Box((-3, -3, 0), (6, 6, 12), 'whale_back')], children=[fluke])
    tail = Part('tail', pivot=(0, -1, 16), boxes=[Box((-5.5, -5, 0), (11, 9, 13), 'whale_back')], children=[tail2])
    jaw = Part('jaw', pivot=(0, 4, -24), boxes=[Box((-6.5, 0, -12), (13, 3, 12), 'whale_belly')])
    head = Part('head', pivot=(0, 0, -24), boxes=[Box((-7, -7, -13), (14, 11, 13), 'whale_head')])
    fin_l = Part('fin_l', pivot=(8, 3, -12), rot=(0, 0, 0.35), boxes=[Box((0, -0.5, -4), (18, 1, 9), 'whale_fin')])
    fin_r = Part('fin_r', pivot=(-8, 3, -12), rot=(0, 0, -0.35), boxes=[Box((-18, -0.5, -4), (18, 1, 9), 'whale_fin')])
    dorsal = Part('dorsal', pivot=(0, -8, 6), rot=(-0.5, 0, 0), boxes=[Box((-0.5, -5, 0), (1, 5, 7), 'whale_fin')])
    body = Part('body', pivot=(0, -12, 0), boxes=[
        Box((-8, -8, -24), (16, 12, 40), 'whale_back'), Box((-7, 4, -23), (14, 3, 36), 'whale_belly')],
        children=[head, jaw, tail, fin_l, fin_r, dorsal])
    return Part('root', pivot=(0, 24, 0), children=[body])


def glimmerfin():
    body = Part('body', pivot=(0, -2, 0), boxes=[Box((-1, -2, -3), (2, 3, 6), 'fin_body')], children=[
        Part('tail', pivot=(0, 0, 3), boxes=[Box((0, -2, 0), (0, 3, 4), 'fin_tail')]),
        Part('top_fin', pivot=(0, -2, -1), boxes=[Box((0, -2, 0), (0, 2, 3), 'fin_tail')])])
    return Part('root', pivot=(0, 24, 0), children=[body])


def emberfox():
    head = Part('head', pivot=(0, -9, -5), boxes=[Box((-3.5, -3, -5), (7, 6, 5), 'fox_head'), Box((-1.5, 0, -8), (3, 2, 3), 'fox_snout')], children=[
        Part('ear_l', pivot=(2.5, -3, -2), boxes=[Box((-1, -3, -0.5), (2, 3, 1), 'fox_fur')]),
        Part('ear_r', pivot=(-2.5, -3, -2), boxes=[Box((-1, -3, -0.5), (2, 3, 1), 'fox_fur')])])
    tail = Part('tail', pivot=(0, -9, 5), rot=(0.9, 0, 0), boxes=[Box((-2, 0, -2), (4, 10, 4), 'ember_tail')])
    body = Part('body', pivot=(0, 0, 0), boxes=[Box((-3, -11, -5), (6, 6, 10), 'fox_fur')], children=[head, tail])
    legs = [Part(n, pivot=(x, -6, z), boxes=[Box((-1, 0, -1), (2, 6, 2), 'fox_leg')])
            for n, x, z in (('leg_fl', -1.8, -3.5), ('leg_fr', 1.8, -3.5), ('leg_bl', -1.8, 3.5), ('leg_br', 1.8, 3.5))]
    return Part('root', pivot=(0, 24, 0), children=[body] + legs)


def rimehorn():
    def horn(side):
        s = 1 if side > 0 else -1
        return Part('horn_l' if s > 0 else 'horn_r', pivot=(s * 3.5, -3, -2), rot=(0, 0, s * -0.2), boxes=[
            Box((0 if s > 0 else -4, -1, -1), (4, 2, 2), 'yak_horn'),
            Box((3 if s > 0 else -4, -4, -1), (1, 3, 2), 'yak_horn'),
            Box((2 if s > 0 else -3, -5, -2), (1, 2, 1), 'yak_horn')])
    head = Part('head', pivot=(0, -13, -9), boxes=[Box((-4, -4, -7), (8, 8, 7), 'yak_head')], children=[horn(1), horn(-1)])
    body = Part('body', pivot=(0, 0, 0), boxes=[
        Box((-6, -21, -9), (12, 11, 18), 'shag'), Box((-3, -24, -7), (6, 3, 8), 'shag'),
        Box((-6.5, -13, -9.5), (13, 5, 19), 'shag_fringe')], children=[head])
    legs = [Part(n, pivot=(x, -10, z), boxes=[Box((-2, 0, -2), (4, 10, 4), 'shag_leg')])
            for n, x, z in (('leg_fl', -3.5, -6), ('leg_fr', 3.5, -6), ('leg_bl', -3.5, 6), ('leg_br', 3.5, 6))]
    return Part('root', pivot=(0, 24, 0), children=[body] + legs)


def petal_sprite():
    body = Part('body', pivot=(0, -6, 0), boxes=[Box((-1, -1, -1), (2, 3, 2), 'sprite_body')], children=[
        Part('head', pivot=(0, -1, 0), boxes=[Box((-1.5, -3, -1.5), (3, 3, 3), 'sprite_head'), Box((-1.5, -4, -1.5), (3, 1, 3), 'flower')]),
        Part('wing_l', pivot=(0.5, -0.5, 0.8), rot=(0, -0.4, 0), boxes=[Box((0, 0, 0), (5, 0, 4), 'petal')]),
        Part('wing_r', pivot=(-0.5, -0.5, 0.8), rot=(0, 0.4, 0), boxes=[Box((-5, 0, 0), (5, 0, 4), 'petal')])])
    return Part('root', pivot=(0, 24, 0), children=[body])


def mirror_ray():
    body = Part('body', pivot=(0, -3, 0), boxes=[Box((-4, -1.5, -6), (8, 3, 12), 'ray_back'),
                                                 Box((-2.5, -1, -8), (1.5, 2, 3), 'ray_back'), Box((1, -1, -8), (1.5, 2, 3), 'ray_back')], children=[
        Part('wing_l', pivot=(4, 0, 0), boxes=[Box((0, -0.5, -5), (12, 1, 10), 'ray_back')]),
        Part('wing_r', pivot=(-4, 0, 0), boxes=[Box((-12, -0.5, -5), (12, 1, 10), 'ray_back')]),
        Part('tail', pivot=(0, 0, 6), boxes=[Box((-0.5, -0.5, 0), (1, 1, 14), 'ray_tail')])])
    return Part('root', pivot=(0, 24, 0), children=[body])


MORE_MATERIALS = {
    'husk_skin': m_husk_skin, 'husk_head': m_husk_head, 'husk_rags': m_husk_rags,
    'shard_body': m_shard_body, 'crystal': m_crystal, 'shard_leg': m_shard_leg, 'shard_eyes': m_shard_eyes,
    'lurker_skin': m_lurker_skin, 'lurker_jaw': m_lurker_jaw, 'lurker_head': m_lurker_head, 'lurker_eye': m_lurker_eye,
    'stalker_body': m_stalker_body, 'stalker_mask': m_stalker_mask, 'stalker_tatter': m_stalker_tatter,
    'hound_brass': m_hound_brass, 'hound_head': m_hound_head, 'iron': m_iron, 'hound_teeth': m_hound_teeth,
    'rime_robe': m_rime_robe, 'rime_face': m_rime_face, 'icicle': m_icicle, 'rime_mist': m_rime_mist,
    'whale_back': m_whale_back, 'whale_belly': m_whale_belly, 'whale_head': m_whale_head, 'whale_fin': m_whale_fin,
    'fin_body': m_fin_body, 'fin_tail': m_fin_tail,
    'fox_fur': m_fox_fur, 'fox_head': m_fox_head, 'fox_snout': m_fox_snout, 'fox_leg': m_fox_leg, 'ember_tail': m_ember_tail,
    'shag': m_shag, 'shag_fringe': m_shag_fringe, 'yak_head': m_yak_head, 'yak_horn': m_yak_horn, 'shag_leg': m_shag_leg,
    'sprite_body': m_sprite_body, 'sprite_head': m_sprite_head, 'petal': m_petal, 'flower': m_flower,
    'ray_back': m_ray_back, 'ray_tail': m_ray_tail,
}

# (Java layer class, texture name, spec, texture width, seed)
MORE_SPECS = [
    ('AshenHuskLayer', 'ashen_husk', ashen_husk, 64, 21),
    ('ShardlingLayer', 'shardling', shardling, 64, 22),
    ('BogLurkerLayer', 'bog_lurker', bog_lurker, 128, 23),
    ('EchoStalkerLayer', 'echo_stalker', echo_stalker, 64, 24),
    ('GearhoundLayer', 'gearhound', gearhound, 64, 25),
    ('RimeWightLayer', 'rime_wight', rime_wight, 64, 26),
    ('SkywhaleLayer', 'skywhale', skywhale, 256, 27),
    ('GlimmerfinLayer', 'glimmerfin', glimmerfin, 32, 28),
    ('EmberfoxLayer', 'emberfox', emberfox, 64, 29),
    ('RimehornLayer', 'rimehorn', rimehorn, 128, 30),
    ('PetalSpriteLayer', 'petal_sprite', petal_sprite, 32, 31),
    ('MirrorRayLayer', 'mirror_ray', mirror_ray, 64, 32),
]

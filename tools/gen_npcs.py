#!/usr/bin/env python3
"""Painted skins for Afterworld's people: the Waystone Camp and the folk who keep the landmarks.

Each character is a small spec (skin, hair, eyes, outfit layers, accessories). The painter works
part by part on the standard 64x64 player layout, wrapping patterns round each box so belts, hems
and sashes meet at the seams, and shades every face from a light above and in front: tops are
lit, sides and backs fall into shadow, hems and folds get a darker line, worn edges a highlight.
Outer-layer pixels (hat, jacket, sleeves, trousers) carry anything that should stand proud of the
body: fringes, collars, coat fronts, cuffs, boot tops.

Writes textures/entity/npc/<id>.png, and with --preview a sheet of front and back views.
"""
import math, os, random, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures', 'entity', 'npc')


def hx(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def tone(c, k):
    """k < 0 darkens toward a cool shadow, k > 0 lightens toward a warm highlight."""
    if k < 0:
        shadow = (int(c[0] * 0.42), int(c[1] * 0.40), int(c[2] * 0.52))
        return lerp(c, shadow, min(1.0, -k))
    light = (min(255, int(c[0] * 1.18 + 28)), min(255, int(c[1] * 1.15 + 24)), min(255, int(c[2] * 1.08 + 16)))
    return lerp(c, light, min(1.0, k))


def h01(*a):
    """Deterministic hash of integers to [0, 1)."""
    n = 0x9E3779B9
    for v in a:
        n = (n ^ (int(v) & 0xFFFFFFFF)) * 0x01000193 & 0xFFFFFFFF
        n ^= n >> 13
    n = (n * 0x5BD1E995) & 0xFFFFFFFF
    n ^= n >> 15
    return (n & 0xFFFFFF) / float(0x1000000)


# ------------------------------------------------------------------ the layout

# name: (u, v, w, h, d) for the inner box, and the outer-layer (u, v)
PARTS = {
    'head': ((0, 0, 8, 8, 8), (32, 0)),
    'body': ((16, 16, 8, 12, 4), (16, 32)),
    'rarm': ((40, 16, 4, 12, 4), (40, 32)),
    'larm': ((32, 48, 4, 12, 4), (48, 48)),
    'rleg': ((0, 16, 4, 12, 4), (0, 32)),
    'lleg': ((16, 48, 4, 12, 4), (0, 48)),
}
FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')
# How each face catches the light.
FACE_LIGHT = {'top': 0.22, 'front': 0.0, 'right': -0.16, 'left': -0.16, 'back': -0.26, 'bottom': -0.42}


def face_rects(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h),
        'back': (u + d + w + d, v + d, w, h),
    }


class Pix:
    """What a painter function returns for one pixel: a colour and how it sits in the light."""
    __slots__ = ('c', 'k')

    def __init__(self, c, k=0.0):
        self.c, self.k = c, k


class Ctx:
    """Where a pixel is: the part, the face, its coordinates on that face, and a wrapped
    coordinate (u) that runs once round the box (front, left, back, right) for patterns."""

    def __init__(self, part, face, x, y, w, h, d, outer):
        self.part, self.face, self.x, self.y, self.w, self.h, self.d, self.outer = part, face, x, y, w, h, d, outer
        if face == 'front':
            self.u = x
        elif face == 'left':
            self.u = w + x
        elif face == 'back':
            self.u = w + d + x
        elif face == 'right':
            self.u = w + d + w + x
        else:
            self.u = x
        self.round = 2 * (w + d)
        # Mirrored parts: the left arm and leg read their patterns the other way round.
        self.side = 'l' if part in ('larm', 'lleg') else 'r'


def paint(img, part, fn, outer=False):
    (u, v, w, h, d), (ou, ov) = PARTS[part]
    if outer:
        u, v = ou, ov
    px = img.load()
    for face, (fx, fy, fw, fh) in face_rects(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                c = Ctx(part, face, x, y, w, h, d, outer)
                r = fn(c)
                if r is None:
                    continue
                k = r.k + FACE_LIGHT[face]
                if face in ('front', 'right', 'left', 'back'):
                    # A little light from above: the top of a face is brighter than its bottom.
                    k += 0.10 - 0.20 * (y / max(1, fh - 1))
                col = tone(r.c, k)
                px[fx + x, fy + y] = col + (255,)


# ------------------------------------------------------------------ materials

def cloth(c, ctx, seed, grain=0.10, folds=True):
    """Woven cloth: a fine checker grain and, on long faces, soft vertical folds."""
    k = (h01(ctx.u, ctx.y, seed) - 0.5) * grain
    if (ctx.u + ctx.y) % 2 == 0:
        k += grain * 0.3
    if folds and ctx.face in ('front', 'back') and ctx.h >= 12:
        f = math.sin((ctx.x + 0.5) * 1.7 + seed) * 0.5 + math.sin((ctx.x + 1.3) * 0.9 + seed * 2) * 0.5
        k += f * 0.07 * (ctx.y / ctx.h + 0.4)
    return Pix(c, k)


def leather(c, ctx, seed):
    k = (h01(ctx.u, ctx.y, seed, 7) - 0.5) * 0.18
    if h01(ctx.u, ctx.y, seed, 9) < 0.06:
        k += 0.25   # scuffs
    return Pix(c, k)


def metal(c, ctx, seed):
    k = 0.25 if (ctx.y == 0 or ctx.x == 0) else -0.15 if ctx.y >= ctx.h - 1 else 0.0
    k += 0.35 if (ctx.u + ctx.y) % 5 == 0 else 0.0
    return Pix(c, k)


# ------------------------------------------------------------------ the head

def head_fn(s):
    skin, hair, eye = hx(s['skin']), hx(s['hair']), hx(s['eyes'])
    style = s.get('hair_style', 'short')
    beard = s.get('beard')
    brow = tone(hair, -0.35)
    lip = lerp(skin, (150, 70, 70), 0.35)
    blush = lerp(skin, (220, 110, 110), 0.18)
    age = s.get('age', 0)

    def hair_pix(ctx, strand_seed=3):
        # Strands: alternating light and dark runs down the head.
        k = 0.12 if (ctx.u * 3 + ctx.y // 2 + strand_seed) % 4 == 0 else -0.08 if (ctx.u + strand_seed) % 3 == 0 else 0.0
        k += (h01(ctx.u, ctx.y, 11) - 0.5) * 0.12
        if age and h01(ctx.u, ctx.y, 13) < 0.25 * age:
            return Pix(lerp(hair, (200, 200, 205), 0.7), k)
        return Pix(hair, k)

    def fn(ctx):
        f, x, y = ctx.face, ctx.x, ctx.y
        if f == 'top':
            if style == 'bald':
                return Pix(skin, 0.05)
            return hair_pix(ctx)
        if f == 'bottom':
            return Pix(skin, -0.2) if not beard else hair_pix(ctx)
        # Hairline: how far the hair comes down each face.
        line = {'short': 1, 'crop': 1, 'long': 1, 'tied': 1, 'braids': 1, 'bald': -1, 'bun': 1, 'wild': 2}[style]
        side_len = {'short': 3, 'crop': 2, 'long': 8, 'tied': 4, 'braids': 8, 'bald': 2, 'bun': 4, 'wild': 5}[style]
        back_len = {'short': 5, 'crop': 4, 'long': 8, 'tied': 7, 'braids': 8, 'bald': 3, 'bun': 6, 'wild': 7}[style]
        if f == 'front':
            if y <= line and style != 'bald':
                # A parting and a fringe that dips at one side.
                if y == line and (x in (0, 7) or (s.get('fringe') and x < 3)):
                    return hair_pix(ctx)
                if y < line:
                    return hair_pix(ctx)
                return Pix(skin, -0.12)       # shadow under the fringe
            if style == 'bald' and y == 0:
                return Pix(skin, 0.15)
            if style in ('long', 'braids', 'wild') and x in (0, 7) and y <= 6:
                return hair_pix(ctx)
            if style == 'braids' and x in (0, 7):
                return Pix(hair, -0.1 if y % 2 else 0.1)
            # brows
            if y == 3 and x in (1, 2, 5, 6):
                return Pix(brow, 0.0)
            # eyes: white and iris, iris toward the nose, a highlight on the iris
            if y == 4:
                if x in (1, 6):
                    return Pix((236, 232, 226), -0.05)
                if x in (2, 5):
                    return Pix(eye, 0.0)
            if y == 5 and x in (2, 5) and s.get('lashes'):
                return Pix(skin, -0.2)
            # beard
            if beard and y >= 5:
                if beard == 'full' and not (y == 6 and x in (3, 4)):
                    return hair_pix(ctx, 5)
                if beard == 'moustache' and y == 5 and 2 <= x <= 5:
                    return hair_pix(ctx, 5)
                if beard == 'goatee' and y >= 6 and 3 <= x <= 4 and not (y == 6):
                    return hair_pix(ctx, 5)
                if beard == 'stubble' and h01(x, y, 17) < 0.5:
                    return Pix(lerp(skin, hair, 0.35), -0.05)
            if y == 5 and x in (3, 4):
                return Pix(skin, -0.14 if x == 4 else -0.06)    # nose
            if y == 6 and x in (3, 4):
                return Pix(lip, 0.0)
            if y == 5 and x in (1, 6) and not beard:
                return Pix(blush, 0.0)
            if age and y == 3 and x in (0, 7):
                return Pix(skin, -0.1)
            return Pix(skin, 0.02 if 2 <= x <= 5 else -0.03)
        if f in ('right', 'left'):
            if style == 'bald':
                return Pix(skin, 0.0) if y > 1 else Pix(hair, 0.0) if y > 0 and h01(x, y, 3) < 0.4 else Pix(skin, 0.0)
            # ear
            ex = 3 if f == 'right' else 4
            if y in (4, 5) and x == ex and side_len < 5:
                return Pix(skin, -0.12)
            if y < side_len or (y < side_len + 2 and (x > 4 if f == 'right' else x < 3)):
                return hair_pix(ctx)
            if beard == 'full' and y >= 5 and (x <= 2 if f == 'right' else x >= 5):
                return hair_pix(ctx, 5)
            return Pix(skin, 0.0)
        if f == 'back':
            if style == 'bald':
                return Pix(skin, 0.0)
            if y < back_len:
                if style == 'tied' and y == 5 and 3 <= x <= 4:
                    return Pix(hx(s.get('tie', '#8E2430')), 0.1)
                return hair_pix(ctx)
            return Pix(skin, -0.05)
        return None
    return fn


def hat_fn(s):
    """The outer head layer: hair volume, fringes, braids, buns and headwear."""
    hair = hx(s['hair'])
    style = s.get('hair_style', 'short')
    gear = s.get('headwear')

    def hp(ctx, k=0.0):
        k += 0.14 if (ctx.u * 3 + ctx.y) % 4 == 0 else -0.06
        return Pix(hair, k)

    def fn(ctx):
        f, x, y = ctx.face, ctx.x, ctx.y
        if gear:
            col = hx(gear['color'])
            kind = gear['kind']
            if kind == 'band' and y == gear.get('row', 2) and f in ('front', 'left', 'right', 'back'):
                if f == 'front' and x in (3, 4) and gear.get('gem'):
                    return Pix(hx(gear['gem']), 0.3 if x == 3 else 0.0)
                return Pix(col, 0.05)
            if kind == 'goggles' and f == 'front' and y in (2, 3) and x in (1, 2, 5, 6):
                return Pix(hx(gear.get('lens', '#7FD4E8')), 0.35 if y == 2 and x in (1, 5) else -0.1)
            if kind == 'goggles' and y in (2, 3) and f != 'top' and f != 'bottom':
                return Pix(col, -0.05 if y == 3 else 0.05)
            if kind == 'scarf' and f == 'front' and y >= 6:
                return Pix(col, 0.05 if (x + y) % 3 else -0.15)
            if kind == 'scarf' and f in ('left', 'right', 'back') and y >= 6:
                return Pix(col, -0.05 if (ctx.u + y) % 3 else -0.2)
            if kind == 'cap' and (f == 'top' or (f != 'bottom' and y <= 1)):
                return Pix(col, 0.0 if f == 'top' else -0.06 + (0.15 if y == 1 else 0))
        if style in ('bald', 'crop'):
            return None
        if f == 'top':
            return hp(ctx, 0.08) if h01(x, y, 21) < 0.45 else None
        if f == 'front':
            if y == 0:
                return hp(ctx, 0.1)
            if y == 1 and (x in (0, 1, 6, 7) or (s.get('fringe') and x < 4)):
                return hp(ctx, 0.0)
            if style in ('long', 'braids', 'wild') and x in (0, 7) and y <= 7:
                return hp(ctx, -0.05)
            return None
        if f in ('left', 'right'):
            length = {'short': 3, 'long': 8, 'tied': 4, 'braids': 8, 'bun': 3, 'wild': 6}[style]
            if y < length:
                return hp(ctx, -0.02)
            return None
        if f == 'back':
            length = {'short': 5, 'long': 8, 'tied': 6, 'braids': 8, 'bun': 4, 'wild': 8}[style]
            if style == 'bun' and 1 <= y <= 3 and 2 <= x <= 5:
                return hp(ctx, 0.2 if y == 1 else 0.0)
            if style == 'tied' and y >= 5 and 3 <= x <= 4:
                return hp(ctx, 0.05)
            if style == 'braids' and y >= 5 and x in (1, 6):
                return Pix(hair, 0.15 if y % 2 else -0.1)
            if y < length:
                return hp(ctx, 0.0) if h01(x, y, 23) < 0.85 or y < 3 else None
            return None
        return None
    return fn


# ------------------------------------------------------------------ the body and limbs

def body_fn(s, outer=False):
    o = s['outfit']
    shirt = hx(o['shirt'])
    seed = s.get('seed', 1)
    skin = hx(s['skin'])

    def fn(ctx):
        f, x, y = ctx.face, ctx.x, ctx.y
        part = ctx.part
        if part == 'body':
            return torso(ctx)
        if part in ('rarm', 'larm'):
            return arm(ctx)
        return leg(ctx)

    def torso(ctx):
        f, x, y = ctx.face, ctx.x, ctx.y
        coat = o.get('coat')
        if outer:
            if coat:
                cc = hx(coat['color'])
                trim = hx(coat.get('trim', coat['color']))
                open_front = coat.get('open', True)
                length = coat.get('length', 12)
                if f == 'top' or f == 'bottom':
                    return Pix(cc, -0.1) if f == 'top' else None
                if y >= length:
                    return None
                if f == 'front' and open_front and 2 < x < 5:
                    # Open front: the shirt shows between the lapels, but the collar closes.
                    return None if y > 1 else Pix(trim, 0.1)
                if f == 'front' and (x in (2, 5)) and open_front:
                    return Pix(trim, 0.15 if y < 2 else 0.0)     # lapel trim
                if y == length - 1:
                    return Pix(trim, -0.1)                         # hem
                if y == 0 and f == 'front':
                    return Pix(trim, 0.1)
                if coat.get('belted') and y == 7:
                    return None
                return cloth(cc, ctx, seed + 3, folds=True)
            if o.get('mantle') and y <= 2 and f != 'bottom':
                return Pix(hx(o['mantle']), 0.1 if y == 0 else -0.05 if y == 2 else 0.0)
            return None
        if f == 'top':
            return Pix(shirt, 0.0)
        if f == 'bottom':
            return Pix(hx(o.get('trousers', o['shirt'])), -0.1)
        # The belt.
        belt = o.get('belt')
        if belt and y == belt.get('row', 7):
            if f == 'front' and x in (3, 4):
                return Pix(hx(belt.get('buckle', '#D8B45A')), 0.3 if x == 3 else 0.0)
            return leather(hx(belt['color']), ctx, seed)
        if belt and y == belt.get('row', 7) + 1 and o.get('skirt'):
            return Pix(hx(o['skirt']), -0.2)
        if o.get('skirt') and y > belt.get('row', 7):
            sk = hx(o['skirt'])
            if y == 11:
                return Pix(sk, -0.18)
            return cloth(sk, ctx, seed + 5)
        # A sash from the right shoulder to the left hip (front), round the back the other way.
        sash = o.get('sash')
        if sash:
            if f == 'front' and abs((7 - x) - y * 0.65) < 0.9 and y < 8:
                return Pix(hx(sash), 0.05 if (x + y) % 2 else -0.05)
            if f == 'back' and abs(x - y * 0.65) < 0.9 and y < 8:
                return Pix(hx(sash), -0.05)
        apron = o.get('apron')
        if apron and f == 'front' and y >= 3 and 1 <= x <= 6:
            ap = hx(apron)
            if y == 3 and x in (1, 6):
                return None if False else Pix(ap, 0.1)
            k = -0.1 if x in (1, 6) else 0.0
            if y == 11:
                k -= 0.15
            if y in (8, 9) and x in (2, 3):
                return Pix(ap, -0.25)   # pocket
            return cloth(ap, ctx, seed + 9, grain=0.14)
        if apron and f in ('left', 'right') and y == 3:
            return Pix(hx(apron), -0.05)
        # Collar and neckline.
        neck = o.get('neck', 'v')
        if f == 'front' and y == 0 and 3 <= x <= 4:
            return Pix(skin, -0.1)
        if f == 'front' and neck == 'v' and y == 1 and 3 <= x <= 4:
            return Pix(skin, -0.18)
        if f == 'front' and neck == 'laced' and 1 <= y <= 3 and x in (3, 4):
            return Pix(hx(o.get('lace', '#E8DCC0')), 0.1) if (y + x) % 2 == 0 else Pix(shirt, -0.25)
        if f == 'front' and o.get('buttons') and x == 4 and y in (2, 4, 6) and y < 7:
            return Pix(hx(o['buttons']), 0.35)
        if o.get('emblem') and f == 'front' and 1 <= y <= 3 and 1 <= x <= 2:
            return Pix(hx(o['emblem']), 0.3 if (x, y) == (1, 1) else 0.0)
        k = 0
        if f == 'front' and y == 10:
            k -= 0.08
        if y == 11:
            k -= 0.15
        if o.get('vest') and f in ('front', 'back') and (x <= 1 or x >= 6) and y < 7:
            return cloth(hx(o['vest']), ctx, seed + 13)
        if o.get('vest') and f in ('left', 'right') and y < 7:
            return cloth(hx(o['vest']), ctx, seed + 13)
        p = cloth(shirt, ctx, seed)
        p.k += k
        return p

    def arm(ctx):
        f, x, y = ctx.face, ctx.x, ctx.y
        sleeve_len = o.get('sleeves', 8)      # rows of sleeve from the shoulder
        glove = o.get('gloves')
        coat = o.get('coat')
        if outer:
            if coat and coat.get('sleeves', True):
                cc = hx(coat['color'])
                trim = hx(coat.get('trim', coat['color']))
                cl = coat.get('sleeve_len', 9)
                if f == 'top':
                    return Pix(cc, 0.0)
                if f == 'bottom':
                    return None
                if y < cl - 1:
                    return cloth(cc, ctx, seed + 3, folds=False)
                if y == cl - 1:
                    return Pix(trim, 0.0)      # cuff
                return None
            if o.get('pauldron') and ctx.part == ('rarm' if o.get('pauldron_side', 'r') == 'r' else 'larm') and y <= 2 and f != 'bottom':
                return metal(hx(o['pauldron']), ctx, seed)
            if glove and y in (8,) and f != 'top':
                return Pix(hx(glove), 0.1)     # glove cuff stands proud
            return None
        if f == 'top':
            return Pix(shirt, 0.05)
        if f == 'bottom':
            return Pix(hx(glove), -0.1) if glove else Pix(skin, -0.1)
        if y < sleeve_len:
            p = cloth(shirt, ctx, seed + 1, folds=False)
            if y == sleeve_len - 1:
                p.k -= 0.12
            if o.get('armband') and y == 3:
                return Pix(hx(o['armband']), 0.05)
            return p
        if glove and y >= 9:
            return leather(hx(glove), ctx, seed + 2)
        if glove and y == sleeve_len and sleeve_len < 9:
            return Pix(skin, -0.05)
        if o.get('bracers') and 6 <= y <= 9:
            return leather(hx(o['bracers']), ctx, seed + 4)
        k = -0.05 if y == 11 else 0.0
        return Pix(skin, k)

    def leg(ctx):
        f, x, y = ctx.face, ctx.x, ctx.y
        tr = hx(o.get('trousers', '#4A4038'))
        boots = o.get('boots')
        boot_top = o.get('boot_top', 8)
        if outer:
            if o.get('skirt_long'):
                sk = hx(o['skirt_long'])
                if f in ('top', 'bottom'):
                    return None
                if y < o.get('skirt_long_len', 10):
                    k = -0.18 if y == o.get('skirt_long_len', 10) - 1 else 0.0
                    p = cloth(sk, ctx, seed + 5)
                    p.k += k
                    return p
                return None
            coat = o.get('coat')
            if coat and coat.get('length', 12) > 12 and f not in ('top', 'bottom'):
                cl = coat['length'] - 12
                if y < cl:
                    if f == 'front' and coat.get('open', True) and ((ctx.side == 'r' and x >= 3) or (ctx.side == 'l' and x <= 0)):
                        return None
                    if y == cl - 1:
                        return Pix(hx(coat.get('trim', coat['color'])), -0.1)
                    return cloth(hx(coat['color']), ctx, seed + 3)
            if boots and y == boot_top and f not in ('top', 'bottom'):
                return Pix(hx(o.get('boot_cuff', boots)), 0.12)   # the boot top folds over
            return None
        if f == 'top':
            return Pix(tr, 0.0)
        if f == 'bottom':
            return Pix(hx(boots), -0.3) if boots else Pix(tr, -0.2)
        if boots and y >= boot_top:
            b = hx(boots)
            if y == 11:
                return Pix(tone(b, -0.45), 0.0)     # sole
            if f == 'front' and y == 10:
                return Pix(b, 0.18)                 # toe cap
            if o.get('boot_laces') and f == 'front' and x in (1, 2) and (y % 2 == 0):
                return Pix(hx(o['boot_laces']), 0.0)
            return leather(b, ctx, seed + 6)
        if o.get('skirt') and y < 2:
            return Pix(hx(o['skirt']), -0.15)
        k = 0.0
        if f == 'front' and y == 4:
            k += 0.06   # knee
        if o.get('patch') and f == 'front' and y in (4, 5) and x in (1, 2) and ctx.side == 'l':
            return Pix(hx(o['patch']), 0.0)
        if y == boot_top - 1:
            k -= 0.12
        if f in ('right', 'left') and o.get('stripe') and x == 1:
            return Pix(hx(o['stripe']), 0.0)
        p = cloth(tr, ctx, seed + 7, folds=False)
        p.k += k
        return p

    return fn


# ------------------------------------------------------------------ the people

PEOPLE = {
    # The Waystone Camp.
    'isolde': {
        'name': 'Warden Isolde', 'model': 'slim', 'seed': 3,
        'skin': '#E7B996', 'hair': '#B8462E', 'hair_style': 'braids', 'eyes': '#3F7F6E', 'lashes': True, 'fringe': True,
        'headwear': {'kind': 'band', 'color': '#C9A45A', 'row': 1, 'gem': '#7FE3D2'},
        'outfit': {
            'shirt': '#E2D7BF', 'trousers': '#3E4A3A', 'boots': '#5A3C28', 'boot_cuff': '#7A5638', 'boot_top': 7,
            'belt': {'color': '#5C3B26', 'buckle': '#D8B45A', 'row': 7}, 'neck': 'laced', 'lace': '#5C3B26',
            'coat': {'color': '#2F5E5A', 'trim': '#C9A45A', 'open': True, 'length': 16, 'belted': True, 'sleeve_len': 10},
            'gloves': '#5A3C28', 'sleeves': 9,
        },
    },
    'bram': {
        'name': 'Quartermaster Bram', 'model': 'wide', 'seed': 7,
        'skin': '#C98E68', 'hair': '#5A4030', 'hair_style': 'crop', 'eyes': '#4A3626', 'beard': 'full', 'age': 0.4,
        'outfit': {
            'shirt': '#8E6E4E', 'trousers': '#3D3A36', 'boots': '#3A2A20', 'boot_top': 8, 'boot_laces': '#8E7A5E',
            'belt': {'color': '#3A2A20', 'buckle': '#9EA2A6', 'row': 7}, 'neck': 'v',
            'apron': '#6E4A2E', 'sleeves': 5, 'armband': '#A5482A', 'bracers': '#4A3424',
        },
    },
    'wren': {
        'name': 'Wren Halloway', 'model': 'slim', 'seed': 11,
        'skin': '#F1CCA8', 'hair': '#D9B25E', 'hair_style': 'tied', 'tie': '#3F6E8C', 'eyes': '#3F6E8C', 'lashes': True,
        'headwear': {'kind': 'goggles', 'color': '#6B4A2E', 'lens': '#9FE0EE'},
        'outfit': {
            'shirt': '#F0E8D6', 'trousers': '#6B5A44', 'boots': '#4A3424', 'boot_top': 7,
            'belt': {'color': '#6B4A2E', 'buckle': '#D8B45A', 'row': 7}, 'neck': 'v',
            'vest': '#3F6E8C', 'buttons': '#D8B45A', 'sash': '#C08A2E', 'sleeves': 7, 'patch': '#8E7A5E',
        },
    },
    'marta': {
        'name': 'Marta Ironvein', 'model': 'wide', 'seed': 13,
        'skin': '#A86E4E', 'hair': '#2A2224', 'hair_style': 'bun', 'eyes': '#5E3A24', 'lashes': True,
        'headwear': {'kind': 'band', 'color': '#A5482A', 'row': 2},
        'outfit': {
            'shirt': '#B8A68A', 'trousers': '#3A3430', 'boots': '#2E2622', 'boot_top': 7,
            'belt': {'color': '#2E2622', 'buckle': '#C9A45A', 'row': 7},
            'apron': '#4A3A30', 'sleeves': 3, 'gloves': '#5E4434', 'pauldron': '#8C8E94', 'pauldron_side': 'l',
        },
    },
    # The landmark keepers.
    'selene': {
        'name': 'Stargazer Selene', 'model': 'slim', 'seed': 17,
        'skin': '#E9D2C4', 'hair': '#D8DCEA', 'hair_style': 'long', 'eyes': '#6A5ACD', 'lashes': True,
        'headwear': {'kind': 'band', 'color': '#C9D2E8', 'row': 1, 'gem': '#B9A4FF'},
        'outfit': {
            'shirt': '#2B3550', 'trousers': '#232A40', 'boots': '#1E2234', 'boot_top': 9,
            'belt': {'color': '#C9D2E8', 'buckle': '#B9A4FF', 'row': 6}, 'neck': 'v', 'emblem': '#E8E4F8',
            'skirt_long': '#2B3550', 'skirt_long_len': 11, 'sleeves': 11,
            'mantle': '#5E3A6E',
        },
    },
    'odo': {
        'name': 'Captain Odo Brassmere', 'model': 'wide', 'seed': 19,
        'skin': '#E0A882', 'hair': '#8E8A86', 'hair_style': 'short', 'eyes': '#3F5E7E', 'beard': 'moustache', 'age': 0.6,
        'headwear': {'kind': 'goggles', 'color': '#7A5638', 'lens': '#E8C060'},
        'outfit': {
            'shirt': '#E8DCC0', 'trousers': '#4A4038', 'boots': '#2E2622', 'boot_top': 6, 'boot_cuff': '#5A4030',
            'belt': {'color': '#3A2A20', 'buckle': '#D8B45A', 'row': 8},
            'coat': {'color': '#7A2E2A', 'trim': '#D8B45A', 'open': True, 'length': 15, 'sleeve_len': 11},
            'buttons': '#D8B45A', 'gloves': '#3A2A20', 'sleeves': 10,
        },
    },
    'mab': {
        'name': 'Fen-Keeper Mab', 'model': 'slim', 'seed': 23,
        'skin': '#D8B49A', 'hair': '#B4B8AC', 'hair_style': 'wild', 'eyes': '#6E8C3A', 'age': 0.2,
        'headwear': {'kind': 'scarf', 'color': '#5E7A3A'},
        'outfit': {
            'shirt': '#5E6E44', 'trousers': '#3E4436', 'boots': '#3A3426', 'boot_top': 7,
            'belt': {'color': '#4A3A2A', 'buckle': '#8E8A70', 'row': 7}, 'neck': 'laced', 'lace': '#B4A27A',
            'skirt': '#4A5A36', 'sleeves': 10, 'bracers': '#4A3A2A',
            'coat': {'color': '#3E4A36', 'trim': '#6E7A4A', 'open': True, 'length': 10, 'sleeve_len': 8},
        },
    },
    'torvald': {
        'name': 'Torvald the Hunter', 'model': 'wide', 'seed': 29,
        'skin': '#E6B48E', 'hair': '#7A4426', 'hair_style': 'long', 'eyes': '#5E7E9E', 'beard': 'full',
        'outfit': {
            'shirt': '#7A4E34', 'trousers': '#4A3E30', 'boots': '#3A2C20', 'boot_top': 6, 'boot_cuff': '#D8CCB4',
            'belt': {'color': '#2E2218', 'buckle': '#9EA2A6', 'row': 7},
            'mantle': '#D8CCB4', 'sash': '#5A3C28', 'gloves': '#4A3424', 'sleeves': 9, 'bracers': '#5A3C28',
        },
    },
    'ines': {
        'name': 'Sister Ines', 'model': 'slim', 'seed': 31,
        'skin': '#EAC8B0', 'hair': '#2E2A2E', 'hair_style': 'short', 'eyes': '#5A4A3A', 'lashes': True,
        'headwear': {'kind': 'cap', 'color': '#E8E4DC'},
        'outfit': {
            'shirt': '#3A3A44', 'trousers': '#2E2E36', 'boots': '#22222A', 'boot_top': 10,
            'belt': {'color': '#8E2430', 'buckle': '#C9A45A', 'row': 6}, 'neck': 'v', 'emblem': '#C9A45A',
            'skirt_long': '#3A3A44', 'skirt_long_len': 12, 'sleeves': 11, 'mantle': '#E8E4DC',
        },
    },
    'rashid': {
        'name': 'Rashid of the Sands', 'model': 'wide', 'seed': 37,
        'skin': '#9E6A48', 'hair': '#1E1A1A', 'hair_style': 'short', 'eyes': '#3A2A1E', 'beard': 'goatee',
        'headwear': {'kind': 'scarf', 'color': '#E8A33D'},
        'outfit': {
            'shirt': '#E8DCC0', 'trousers': '#C0A27A', 'boots': '#6B4A2E', 'boot_top': 9,
            'belt': {'color': '#A5482A', 'buckle': '#D8B45A', 'row': 7}, 'neck': 'laced', 'lace': '#A5482A',
            'vest': '#2F7F78', 'sash': '#A5482A', 'sleeves': 10, 'stripe': '#A5482A',
        },
    },
    'akane': {
        'name': 'Akane', 'model': 'slim', 'seed': 43,
        'skin': '#F0D2B8', 'hair': '#141018', 'hair_style': 'long', 'eyes': '#2A1E1A', 'lashes': True,
        'headwear': {'kind': 'band', 'color': '#C8323C', 'row': 1},
        'outfit': {
            'shirt': '#F2EEE6', 'trousers': '#2A2E4A', 'boots': '#1E1A1A', 'boot_top': 6,
            'belt': {'color': '#C8323C', 'buckle': '#C8323C', 'row': 7}, 'neck': 'v',
            'vest': '#F2EEE6', 'sash': '#C8323C', 'sleeves': 8, 'skirt_long': '#2A2E4A', 'skirt_long_len': 10,
        },
    },
    'rook': {
        'name': 'Rook, Keeper of the Landings', 'model': 'wide', 'seed': 47,
        'skin': '#D8A888', 'hair': '#ECE8E0', 'hair_style': 'wild', 'eyes': '#4E9EAE', 'beard': 'full', 'age': 0.85,
        'headwear': {'kind': 'goggles', 'color': '#5A3E2A', 'lens': '#7FE3D2'},
        'outfit': {
            'shirt': '#C9B48A', 'trousers': '#4A3E5A', 'boots': '#3A2C22', 'boot_top': 7, 'boot_cuff': '#8E6A48',
            'belt': {'color': '#5A3E2A', 'buckle': '#E8C060', 'row': 8},
            'coat': {'color': '#5E3A6E', 'trim': '#E8C060', 'open': True, 'length': 14, 'sleeve_len': 10},
            'sash': '#B8A47A', 'gloves': '#6B4A2E', 'sleeves': 10, 'buttons': '#E8C060',
        },
    },
    'leyla': {
        'name': 'Leyla of the Sands', 'model': 'slim', 'seed': 41,
        'skin': '#9A6646', 'hair': '#1E1A1A', 'hair_style': 'long', 'eyes': '#3A2A1E', 'lashes': True,
        'headwear': {'kind': 'scarf', 'color': '#2F7F78'},
        'outfit': {
            'shirt': '#E2D2B0', 'trousers': '#8E6A48', 'boots': '#5A3E26', 'boot_top': 9,
            'belt': {'color': '#E8A33D', 'buckle': '#D8B45A', 'row': 7}, 'neck': 'laced', 'lace': '#2F7F78',
            'vest': '#A5482A', 'sash': '#E8A33D', 'sleeves': 9, 'stripe': '#2F7F78', 'gloves': '#6B4A2E',
        },
    },
}


def skin_for(s):
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    paint(img, 'head', head_fn(s))
    paint(img, 'head', hat_fn(s), outer=True)
    for part in ('body', 'rarm', 'larm', 'rleg', 'lleg'):
        paint(img, part, body_fn(s))
        paint(img, part, body_fn(s, outer=True), outer=True)
    if s.get('model') == 'slim':
        slimify(img)
    return img


def slimify(img):
    """Slim (three-pixel) arms: squeeze each arm's front, back and top faces to three columns."""
    px = img.load()
    for (u, v, w, h, d), (ou, ov) in (PARTS['rarm'], PARTS['larm']):
        for bu, bv in ((u, v), (ou, ov)):
            src = img.crop((bu, bv, bu + 16, bv + 16)).copy()
            sp = src.load()
            for y in range(16):
                for x in range(16):
                    px[bu + x, bv + y] = (0, 0, 0, 0)
            # Slim layout: top (d..d+3), bottom (d+3..d+6) on the first 4 rows; then right (0..4),
            # front (4..7), left (7..11), back (11..14).
            def copy(sx0, sy0, sw, sh, dx0, dy0, dw):
                for y in range(sh):
                    for x in range(dw):
                        tx = sx0 + min(sw - 1, int(x * sw / dw + 0.5)) if dw != sw else sx0 + x
                        px[bu + dx0 + x, bv + dy0 + y] = sp[tx, sy0 + y]
            copy(4, 0, 4, 4, 4, 0, 3)      # top
            copy(8, 0, 4, 4, 7, 0, 3)      # bottom
            copy(0, 4, 4, 12, 0, 4, 4)     # right
            copy(4, 4, 4, 12, 4, 4, 3)     # front
            copy(8, 4, 4, 12, 7, 4, 4)     # left
            copy(12, 4, 4, 12, 11, 4, 3)   # back


def front_view(img, slim, back=False, scale=6):
    """A flat paper-doll of the skin, for checking."""
    view = Image.new('RGBA', (16 * scale, 32 * scale), (40, 44, 52, 255))

    def blit(rect, dx, dy, outer_rect=None):
        x, y, w, h = rect
        tile = img.crop((x, y, x + w, y + h))
        if outer_rect:
            ox, oy, ow, oh = outer_rect
            tile = Image.alpha_composite(tile, img.crop((ox, oy, ox + ow, oy + oh)))
        view.alpha_composite(tile.resize((w * scale, h * scale), Image.NEAREST), (dx * scale, dy * scale))

    f = 'back' if back else 'front'
    aw = 3 if slim else 4

    def rect(part, outer=False):
        (u, v, w, h, d), (ou, ov) = PARTS[part]
        if outer:
            u, v = ou, ov
        if part in ('rarm', 'larm') and slim:
            r = {'front': (u + 4, v + 4, 3, 12), 'back': (u + 11, v + 4, 3, 12)}
            return r[f]
        return face_rects(u, v, w, h, d)[f]

    blit(rect('head'), 4, 0, rect('head', True))
    blit(rect('body'), 4, 8, rect('body', True))
    ra, la = ('rarm', 'larm') if not back else ('larm', 'rarm')
    blit(rect(ra), 4 - aw, 8, rect(ra, True))
    blit(rect(la), 12, 8, rect(la, True))
    rl, ll = ('rleg', 'lleg') if not back else ('lleg', 'rleg')
    blit(rect(rl), 4, 20, rect(rl, True))
    blit(rect(ll), 8, 20, rect(ll, True))
    return view


def main():
    os.makedirs(OUT, exist_ok=True)
    previews = []
    for pid, s in PEOPLE.items():
        img = skin_for(s)
        img.save(os.path.join(OUT, pid + '.png'))
        previews.append((front_view(img, s.get('model') == 'slim'), front_view(img, s.get('model') == 'slim', back=True)))
    if '--preview' in sys.argv:
        path = sys.argv[sys.argv.index('--preview') + 1]
        w, h = previews[0][0].size
        sheet = Image.new('RGBA', ((w * 2 + 12) * 5, (h + 12) * 2), (24, 26, 32, 255))
        for i, (a, b) in enumerate(previews):
            x = (i % 5) * (w * 2 + 12)
            y = (i // 5) * (h + 12)
            sheet.alpha_composite(a, (x, y))
            sheet.alpha_composite(b, (x + w, y))
        sheet.save(path)
    print(f'{len(PEOPLE)} skins')


if __name__ == '__main__':
    main()

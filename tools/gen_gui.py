#!/usr/bin/env python3
"""Afterworld's window kit: the frames, buttons, tabs, plaques and bars every screen is built from,
painted to sit beside the Allure resource pack (soft lavender stone, rounded bevelled corners, dark
violet insets) and a few things drawn as objects: a parchment scroll, a stall's awning, the Guild's
ledger and a carved stone tablet. All are nine-slice sprites, so they stretch to any size.

Writes assets/afterworld/textures/gui/sprites/frame/*.png (+ .mcmeta), and with --preview a sheet.
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures', 'gui', 'sprites', 'frame')


def hx(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


CLEAR = (0, 0, 0, 0)
# Lavender stone, from light to deep, and the violet ink it is outlined in.
STONE = [hx('#e9ecf3'), hx('#cccfd9'), hx('#b2b6c7'), hx('#9398b0'), hx('#7b8098'), hx('#666a83'), hx('#545570')]
OUTLINE = hx('#261c33')
BODY = hx('#1c1628', 246)
BODY_EDGE = hx('#2e2440')
INSET = hx('#140f1e', 235)
INSET_EDGE = hx('#3a2f50')
GOLD = [hx('#7a4a14'), hx('#c08a2a'), hx('#f0c050'), hx('#fff0a8')]
PARCH = [hx('#7a5a3a'), hx('#b9966a'), hx('#dcc49a'), hx('#ecdcb4'), hx('#f6ecd0')]
CLOTH = {'red': [hx('#6e1e24'), hx('#a8323a'), hx('#d4505a')], 'cream': [hx('#b8a888'), hx('#e2d6bc'), hx('#f6efde')]}
LEATHER = [hx('#2c3a24'), hx('#3e5232'), hx('#55703f'), hx('#6f8c50')]
SLATE = [hx('#2a2a34'), hx('#3e3e4c'), hx('#565668'), hx('#727286'), hx('#9090a4')]


def save(img, name, border, stretch=True):
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + '.png'))
    meta = {'gui': {'scaling': {'type': 'nine_slice', 'width': img.width, 'height': img.height, 'border': border}}}
    if not stretch:
        meta['gui']['scaling']['stretch_inner'] = False
    with open(os.path.join(OUT, name + '.png.mcmeta'), 'w') as f:
        json.dump(meta, f)


def rounded(w, h, x, y, r):
    """Whether (x, y) is inside a w x h rectangle with corners cut to radius r (pixel-art round)."""
    for cx, cy in ((r - 1, r - 1), (w - r, r - 1), (r - 1, h - r), (w - r, h - r)):
        inx = x < r if cx == r - 1 else x > w - 1 - r
        iny = y < r if cy == r - 1 else y > h - 1 - r
        if inx and iny:
            dx = (r - 1 - x) if cx == r - 1 else (x - (w - r))
            dy = (r - 1 - y) if cy == r - 1 else (y - (h - r))
            return dx * dx + dy * dy <= (r - 0.5) ** 2
    return 0 <= x < w and 0 <= y < h


def edge_distance(w, h, x, y, r):
    """How many pixels in from the rounded rectangle's edge (0 on the outline)."""
    for d in range(0, 12):
        if not rounded(w - 2 * d, h - 2 * d, x - d, y - d, max(1, r - d)):
            return d - 1
    return 12


def bevel(img, w, h, r, ring, lit, shade, base, outline=OUTLINE):
    """A bevelled stone band `ring` pixels wide: outline, then lit top-left and shaded bottom-right."""
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not rounded(w, h, x, y, r):
                continue
            d = edge_distance(w, h, x, y, r)
            if d == 0:
                px[x, y] = outline
            elif d <= ring:
                # Which side: top-left catches the light, bottom-right falls into shade.
                top_left = (x + y) < (w + h) / 2 and (x < d + 2 or y < d + 2 or (x - d) + (y - d) < r + 1)
                bottom_right = x > w - d - 3 or y > h - d - 3
                if d == 1:
                    c = lit[0] if top_left and not bottom_right else shade[1] if bottom_right else base
                elif d == ring:
                    c = shade[0] if not bottom_right else lit[1]
                else:
                    c = lit[1] if top_left and not bottom_right else shade[0] if bottom_right else base
                px[x, y] = c


def panel():
    """The standard window: a lavender stone frame with a violet body, studs at the corners."""
    w = h = 32
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    bevel(img, w, h, 4, 4, (STONE[0], STONE[1]), (STONE[4], STONE[5]), STONE[2])
    for y in range(h):
        for x in range(w):
            if rounded(w, h, x, y, 4) and edge_distance(w, h, x, y, 4) > 4:
                px[x, y] = BODY_EDGE if edge_distance(w, h, x, y, 4) == 5 else BODY
    # Studs: a gold rivet in each corner of the band.
    for cx, cy in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
        px[cx, cy] = GOLD[2]
        px[cx + (1 if cx < w // 2 else -1), cy] = GOLD[1]
        px[cx, cy + (1 if cy < h // 2 else -1)] = GOLD[1]
    save(img, 'panel', 7)


def inset():
    """A sunken area inside a panel: a list, a card, a portrait's backing."""
    w = h = 16
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not rounded(w, h, x, y, 2):
                continue
            d = edge_distance(w, h, x, y, 2)
            if d == 0:
                px[x, y] = OUTLINE
            elif d == 1:
                px[x, y] = INSET_EDGE if (x > w - 3 or y > h - 3) else hx('#0c0814')
            else:
                px[x, y] = INSET
    save(img, 'inset', 3)


def button(state):
    """A lavender stone button: raised (idle), lifted and lit (hover), pressed flat (down), grey (off)."""
    w, h = 32, 16
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    if state == 'off':
        lit, shade, base = (hx('#8a8c98'), hx('#7a7c88')), (hx('#5a5c68'), hx('#4a4c58')), hx('#6a6c78')
    elif state == 'hover':
        lit, shade, base = (hx('#ffffff'), STONE[0]), (STONE[3], STONE[4]), STONE[1]
    elif state == 'down':
        lit, shade, base = (STONE[4], STONE[3]), (STONE[2], STONE[1]), STONE[3]
    else:
        lit, shade, base = (STONE[0], STONE[1]), (STONE[4], STONE[5]), STONE[2]
    for y in range(h):
        for x in range(w):
            if not rounded(w, h, x, y, 3):
                continue
            d = edge_distance(w, h, x, y, 3)
            if d == 0:
                px[x, y] = OUTLINE
            elif y >= h - 3 and state != 'down':
                px[x, y] = shade[1] if y == h - 2 else shade[0]
            elif d == 1 and (y < 3 or x < 3):
                px[x, y] = lit[0]
            elif d == 1:
                px[x, y] = shade[0]
            else:
                px[x, y] = base if y > 4 else lit[1]
    if state == 'hover':
        # A gold glint on the top edge.
        for x in range(4, w - 4, 6):
            px[x, 1] = GOLD[3]
    save(img, f'button_{state}' if state != 'idle' else 'button', 4)


def tab(active):
    """A tab on a panel's edge: the active one stands proud and joins the panel."""
    w, h = 24, 24
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not rounded(w, h + 6, x, y, 4):
                continue
            d = edge_distance(w, h + 6, x, y, 4)
            if d == 0:
                px[x, y] = OUTLINE
            elif d == 1:
                px[x, y] = (STONE[0] if active else STONE[2]) if (x < 3 or y < 3) else (STONE[4] if active else STONE[5])
            else:
                px[x, y] = (STONE[1] if active else STONE[3]) if y < 6 else (STONE[2] if active else STONE[4])
    save(img, 'tab_active' if active else 'tab', 5)


def plaque():
    """A title plaque: a gold-rimmed stone bar with ends cut like a ribbon's."""
    w, h = 48, 20
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    for y in range(h):
        for x in range(w):
            # Notched ends.
            notch = abs(y - h / 2 + 0.5) * 0.6
            if x < 4 - notch or x > w - 5 + notch:
                continue
            top, bot = y == 0, y == h - 1
            if top or bot or x < 5 - notch or x > w - 6 + notch:
                px[x, y] = OUTLINE
            elif y == 1 or y == h - 2:
                px[x, y] = GOLD[2] if y == 1 else GOLD[1]
            elif y == 2:
                px[x, y] = GOLD[0]
            else:
                px[x, y] = STONE[1] if y < h / 2 else STONE[2]
    save(img, 'plaque', 7)


def divider():
    """A line to part a panel's sections (stretched), and the gem set in its middle (drawn over it)."""
    w, h = 16, 5
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    for x in range(0, w):
        px[x, 2] = STONE[3]
        px[x, 3] = OUTLINE
    for x in (0, w - 1):
        px[x, 2] = CLEAR
        px[x, 3] = CLEAR
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, 'divider.png'))
    with open(os.path.join(OUT, 'divider.png.mcmeta'), 'w') as f:
        json.dump({'gui': {'scaling': {'type': 'nine_slice', 'width': w, 'height': h, 'border': {'left': 2, 'right': 2, 'top': 0, 'bottom': 0}}}}, f)
    gem = Image.new('RGBA', (6, 5), CLEAR)
    gp = gem.load()
    for (x, y, c) in ((2, 0, GOLD[3]), (3, 0, GOLD[2]), (1, 1, GOLD[2]), (2, 1, GOLD[3]), (3, 1, GOLD[2]), (4, 1, GOLD[1]),
                      (1, 2, GOLD[1]), (2, 2, GOLD[2]), (3, 2, GOLD[1]), (4, 2, GOLD[0]), (2, 3, GOLD[1]), (3, 3, GOLD[0]), (2, 4, OUTLINE), (3, 4, OUTLINE),
                      (0, 2, OUTLINE), (5, 2, OUTLINE)):
        gp[x, y] = c
    gem.save(os.path.join(OUT, 'gem.png'))


def bar(fill):
    w, h = 16, 6
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not rounded(w, h, x, y, 2):
                continue
            d = edge_distance(w, h, x, y, 2)
            if d == 0:
                px[x, y] = OUTLINE
            elif fill:
                px[x, y] = (255, 255, 255, 255) if y == 1 else (235, 235, 235, 255) if y < 4 else (190, 190, 190, 255)
            else:
                px[x, y] = INSET
    save(img, 'bar_fill' if fill else 'bar', 2)


# ------------------------------------------------------------------ the object windows

def parchment():
    """A scroll of parchment: cream paper, darker at its worn edges, rolled at top and bottom."""
    w, h = 48, 48
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    rnd = random.Random(7)
    for y in range(h):
        for x in range(w):
            # Rolls: the top and bottom 5 rows are a rounded tube.
            if y < 6 or y >= h - 6:
                yy = y if y < 6 else h - 1 - y
                if x < 1 or x > w - 2:
                    continue
                c = [OUTLINE, PARCH[1], PARCH[3], PARCH[4], PARCH[2], PARCH[1]][yy]
                if x in (1, w - 2) and yy > 0:
                    c = PARCH[0]
                px[x, y] = c
                continue
            if x < 3 or x > w - 4:
                px[x, y] = OUTLINE if x in (3, w - 4) else CLEAR
                if x in (3, w - 4):
                    px[x, y] = PARCH[0]
                continue
            edge = min(x - 3, w - 4 - x)
            c = PARCH[2] if edge < 2 else PARCH[3]
            if edge >= 2 and rnd.random() < 0.06:
                c = PARCH[2]
            px[x, y] = c
    save(img, 'parchment', 8)


def awning():
    """A market stall's striped awning, scalloped along its lower edge (for the shop's header)."""
    w, h = 32, 14
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    for y in range(h):
        for x in range(w):
            stripe = (x // 4) % 2
            cloth = CLOTH['red'] if stripe == 0 else CLOTH['cream']
            # Scallops: each stripe ends in a half-round tongue.
            local = x % 4
            tongue = h - 3 + (1 if local in (1, 2) else 0)
            if y > tongue:
                continue
            if y == 0:
                c = OUTLINE
            elif y == 1:
                c = GOLD[1]
            elif y == tongue:
                c = OUTLINE
            elif y == tongue - 1:
                c = cloth[0]
            else:
                c = cloth[2] if y < 4 else cloth[1]
            px[x, y] = c
    save(img, 'awning', 4, stretch=False)


def ledger():
    """The Guild's ledger: green leather boards round a cream page, a ribbon marking the place."""
    w, h = 48, 32
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not rounded(w, h, x, y, 3):
                continue
            d = edge_distance(w, h, x, y, 3)
            if d == 0:
                px[x, y] = OUTLINE
            elif d <= 3:
                px[x, y] = LEATHER[3] if d == 1 and (x < 3 or y < 3) else LEATHER[1] if d == 3 else LEATHER[2]
            elif d == 4:
                px[x, y] = PARCH[1]
            else:
                px[x, y] = PARCH[4] if d > 5 else PARCH[3]
    # A red ribbon bookmark hanging from the top edge, near the right (in the corner, so it never stretches).
    for y in range(0, 9):
        px[w - 9, y] = hx('#8a2430') if y < 8 else OUTLINE
        px[w - 8, y] = hx('#b83a46') if y < 7 else hx('#8a2430')
    for cx, cy in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
        px[cx, cy] = GOLD[2]
    save(img, 'ledger', 10)


def tablet():
    """A carved slab of dark stone, chipped at its corners, with a lit rune-line inside its border."""
    w = h = 32
    img = Image.new('RGBA', (w, h), CLEAR)
    px = img.load()
    rnd = random.Random(3)
    for y in range(h):
        for x in range(w):
            if not rounded(w, h, x, y, 3):
                continue
            d = edge_distance(w, h, x, y, 3)
            if d == 0:
                px[x, y] = OUTLINE
            elif d <= 3:
                px[x, y] = SLATE[3] if (x < 4 or y < 4) and d < 3 else SLATE[1] if (x > w - 5 or y > h - 5) else SLATE[2]
            elif d == 4:
                px[x, y] = hx('#9c86c8')
            elif d == 5:
                px[x, y] = SLATE[0]
            else:
                px[x, y] = hx('#1a1824', 246) if rnd.random() > 0.05 else hx('#22202e', 246)
    save(img, 'tablet', 7)


def hud_arrow():
    """The way-finding arrow (white, drawn tinted and turned by the HUD): a pointer with a notch in
    its heel and a dark rim, so it reads on sky and on stone alike."""
    s = 8
    big = Image.new('L', (16 * s, 16 * s), 0)
    pts = [(8 * s, 0.6 * s), (15.2 * s, 15.0 * s), (8 * s, 10.6 * s), (0.8 * s, 15.0 * s)]
    ImageDraw.Draw(big).polygon(pts, fill=255)
    mask = big.resize((16, 16), Image.LANCZOS)
    rim = mask.filter(ImageFilter.MaxFilter(3))
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            a, r = mask.getpixel((x, y)), rim.getpixel((x, y))
            if r > 0:
                img.putpixel((x, y), (38, 28, 51, r))
            if a > 0:
                k = a / 255.0
                img.putpixel((x, y), (int(255 * k + 38 * (1 - k)), int(255 * k + 28 * (1 - k)), int(255 * k + 51 * (1 - k)), max(a, r)))
    out = os.path.join(OUT, '..', 'hud')
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, 'arrow.png'))


def preview(path):
    names = ['panel', 'inset', 'button', 'button_hover', 'button_down', 'button_off', 'tab', 'tab_active', 'plaque', 'divider', 'bar', 'bar_fill',
             'parchment', 'awning', 'ledger', 'tablet']
    s = 6
    out = Image.new('RGBA', (900, 620), (60, 64, 80, 255))
    x, y, rowh = 10, 10, 0
    for n in names:
        im = Image.open(os.path.join(OUT, n + '.png'))
        im = im.resize((im.width * s, im.height * s), Image.NEAREST)
        if x + im.width > 890:
            x, y = 10, y + rowh + 10
            rowh = 0
        out.alpha_composite(im, (x, y))
        x += im.width + 10
        rowh = max(rowh, im.height)
    out.save(path)


def main():
    panel()
    inset()
    for s in ('idle', 'hover', 'down', 'off'):
        button(s)
    tab(False)
    tab(True)
    plaque()
    divider()
    bar(False)
    bar(True)
    parchment()
    awning()
    ledger()
    tablet()
    hud_arrow()
    if '--preview' in sys.argv:
        preview(sys.argv[sys.argv.index('--preview') + 1])
    print('window kit written')


if __name__ == '__main__':
    main()

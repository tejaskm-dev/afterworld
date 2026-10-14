#!/usr/bin/env python3
"""Generates every Afterworld texture. Run from the project root: python3 tools/gen_assets.py"""
import math, os, random, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(__file__))
from pixel import hexc, mix, shade, ramp, sprite, noise_tile, outline, save

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'textures')
ITEM = os.path.join(ROOT, 'item')
BLOCK = os.path.join(ROOT, 'block')
GUI = os.path.join(ROOT, 'gui', 'sprites')
INK = hexc('#1A161E')

EDICTS = {
    'return': '#E8A33D', 'echoes': '#B79CE8', 'borrowed_time': '#4FB3A9', 'weightlessness': '#9BD08A', 'hunger': '#C4553B',
    'hospitality': '#EBD9A6', 'trespass': '#6E8FB8', 'stillness': '#A9D8E8', 'witness': '#F2D25C', 'forgotten': '#8A7FA0',
}

# ---------------------------------------------------------------- edict fragments

SHARD = [
    "................",
    "........kk......",
    ".......kWLk.....",
    "......kLHHk.....",
    ".....kLHHMMk....",
    "....kLHHMMMDk...",
    "...kLHHMMMDDk...",
    "...kHHMMMMDDDk..",
    "..kLHMMMMDDDDk..",
    "..kHHMMMDDDDKk..",
    "...kHMMMDDDKk...",
    "...kMMMDDDKk....",
    "....kMMDDKk.....",
    ".....kMDKk......",
    "......kkk.......",
    "................",
]

def edict_palette(color):
    c = hexc(color)
    return {'k': shade(c, 0.22), 'K': shade(c, 0.4), 'D': shade(c, 0.62), 'M': c, 'H': shade(c, 1.28), 'L': shade(c, 1.55), 'W': (255, 255, 250, 255)}

def fragments():
    for eid, col in EDICTS.items():
        save(sprite(SHARD, edict_palette(col)), os.path.join(ITEM, f'edict_fragment_{eid}.png'))

# ---------------------------------------------------------------- symbol icons (GUI)

def canvas():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)

def finish(img, color, hl=True):
    """Outline the symbol and add a light top edge for a little depth."""
    c = hexc(color) if isinstance(color, str) else color
    if hl:
        px = img.load()
        lite = shade(c, 1.35)
        for y in range(16):
            for x in range(16):
                if px[x, y][3] > 0 and (y == 0 or px[x, y - 1][3] == 0):
                    px[x, y] = lite
    return outline(img, INK)

def icon_return(c):
    img, d = canvas()
    d.arc((2, 2, 13, 13), 60, 330, fill=c, width=2)
    d.polygon([(13, 4), (13, 9), (9, 7)], fill=c)
    return img

def icon_echoes(c):
    img, d = canvas()
    d.ellipse((2, 6, 5, 9), fill=c)
    d.arc((1, 3, 9, 12), -55, 55, fill=c, width=1)
    d.arc((0, 1, 12, 14), -55, 55, fill=c, width=1)
    d.arc((-1, -1, 15, 16), -50, 50, fill=c, width=1)
    return img

def icon_borrowed_time(c):
    img, d = canvas()
    d.line((3, 1, 12, 1), fill=c, width=2)
    d.line((3, 14, 12, 14), fill=c, width=2)
    d.polygon([(4, 3), (11, 3), (8, 7), (7, 7)], fill=c)
    d.polygon([(7, 8), (8, 8), (11, 12), (4, 12)], outline=c)
    d.line((5, 12, 10, 12), fill=c)
    d.point((7, 10), fill=c)
    return img

def icon_weightlessness(c):
    img, d = canvas()
    for i, y in enumerate((2, 6, 10)):
        d.line((3, y + 4, 7, y), fill=c, width=2)
        d.line((8, y, 12, y + 4), fill=c, width=2)
    return img

def icon_hunger(c):
    img, d = canvas()
    d.arc((1, 1, 14, 14), 200, 340, fill=c, width=2)
    d.arc((1, 1, 14, 14), 20, 160, fill=c, width=2)
    for x in (4, 7, 10):
        d.polygon([(x, 4), (x + 2, 4), (x + 1, 7)], fill=c)
        d.polygon([(x, 11), (x + 2, 11), (x + 1, 8)], fill=c)
    return img

def icon_hospitality(c):
    img, d = canvas()
    d.polygon([(2, 2), (13, 2), (11, 7), (4, 7)], fill=c)
    d.rectangle((7, 8, 8, 11), fill=c)
    d.rectangle((4, 12, 11, 13), fill=c)
    return img

def icon_trespass(c):
    img, d = canvas()
    stone = shade(hexc('#8A8478'), 1.0)
    d.rectangle((6, 1, 9, 14), fill=stone)
    d.line((6, 5, 9, 5), fill=shade(stone, 0.6))
    d.line((6, 10, 9, 10), fill=shade(stone, 0.6))
    d.line((1, 7, 12, 7), fill=c, width=2)
    d.polygon([(11, 4), (15, 7), (15, 8), (11, 11)], fill=c)
    return img

def icon_stillness(c):
    img, d = canvas()
    d.line((7, 1, 7, 14), fill=c, width=2)
    d.line((2, 4, 13, 11), fill=c, width=2)
    d.line((2, 11, 13, 4), fill=c, width=2)
    d.rectangle((6, 6, 9, 9), fill=shade(c, 1.4) if isinstance(c, tuple) else c)
    return img

def icon_witness(c):
    img, d = canvas()
    d.chord((1, 3, 14, 15), 200, 340, fill=c)
    d.chord((1, 0, 14, 12), 20, 160, fill=c)
    d.ellipse((5, 5, 10, 10), fill=INK)
    d.point((6, 6), fill=(255, 255, 255, 255))
    return img

def icon_forgotten(c):
    img, d = canvas()
    d.ellipse((3, 1, 12, 10), fill=c)
    d.rectangle((3, 6, 12, 12), fill=c)
    for x in (3, 7, 11):
        d.polygon([(x, 12), (x + 2, 12), (x + 1, 14)], fill=c)
    return img

def icon_unknown(c):
    rows = [
        "................",
        "................",
        "......XXXX......",
        ".....XX..XX.....",
        ".....XX..XX.....",
        ".........XX.....",
        "........XX......",
        ".......XX.......",
        ".......XX.......",
        "................",
        ".......XX.......",
        ".......XX.......",
        "................",
        "................",
        "................",
        "................",
    ]
    return sprite(rows, {'X': c})

ICONS = {'return': icon_return, 'echoes': icon_echoes, 'borrowed_time': icon_borrowed_time, 'weightlessness': icon_weightlessness,
         'hunger': icon_hunger, 'hospitality': icon_hospitality, 'trespass': icon_trespass, 'stillness': icon_stillness,
         'witness': icon_witness, 'forgotten': icon_forgotten}

def edict_icons():
    for eid, col in EDICTS.items():
        img = ICONS[eid](hexc(col))
        save(finish(img, col), os.path.join(GUI, 'edict', f'{eid}.png'))
    save(finish(icon_unknown(hexc('#8C8170')), '#8C8170', hl=False), os.path.join(GUI, 'edict', 'unknown.png'))

# ---------------------------------------------------------------- disciplines, factions, bonds, tabs

def draw_icon(fn, color, path, hl=True):
    img, d = canvas()
    fn(d, hexc(color))
    save(finish(img, color, hl), path)

def shield(d, c):
    d.polygon([(2, 2), (13, 2), (13, 8), (7, 14), (2, 8)], fill=c)
    d.line((7, 3, 7, 12), fill=shade(c, 0.6))
    d.line((3, 6, 12, 6), fill=shade(c, 0.6))

def compass_rose(d, c):
    d.polygon([(7, 0), (9, 6), (15, 7), (9, 9), (7, 15), (6, 9), (0, 7), (6, 6)], fill=c)
    d.ellipse((5, 5, 10, 10), fill=shade(c, 0.6))

def collision(d, c):
    d.polygon([(5, 1), (10, 6), (5, 11), (0, 6)], fill=c)
    d.polygon([(10, 4), (15, 9), (10, 14), (5, 9)], fill=shade(c, 1.3))

def key(d, c):
    d.ellipse((1, 1, 7, 7), fill=c)
    d.ellipse((3, 3, 5, 5), fill=(0, 0, 0, 0))
    d.line((6, 6, 13, 13), fill=c, width=2)
    d.line((10, 12, 12, 10), fill=c, width=2)

def dot(d, c):
    d.ellipse((5, 5, 10, 10), fill=c)

def crown(d, c):
    d.polygon([(1, 12), (1, 4), (4, 8), (7, 2), (10, 8), (14, 4), (14, 12)], fill=c)
    d.rectangle((1, 12, 14, 14), fill=shade(c, 0.75))
    d.point((7, 9), fill=hexc('#C4553B'))

def scroll_map(d, c):
    d.rectangle((2, 3, 13, 12), fill=c)
    d.line((2, 3, 13, 3), fill=shade(c, 0.7))
    d.line((5, 6, 10, 9), fill=hexc('#C4553B'))
    d.line((10, 6, 5, 9), fill=hexc('#C4553B'))
    d.line((3, 11, 7, 5), fill=shade(c, 0.6))

def moth(d, c):
    d.polygon([(7, 4), (1, 1), (0, 8), (7, 9)], fill=c)
    d.polygon([(8, 4), (14, 1), (15, 8), (8, 9)], fill=c)
    d.polygon([(7, 9), (3, 14), (7, 12)], fill=shade(c, 0.8))
    d.polygon([(8, 9), (12, 14), (8, 12)], fill=shade(c, 0.8))
    d.rectangle((7, 3, 8, 12), fill=shade(c, 0.5))

def sealed_eye(d, c):
    d.rectangle((1, 2, 14, 13), fill=shade(c, 0.6))
    d.chord((3, 4, 12, 12), 20, 160, fill=c)
    d.chord((3, 3, 12, 11), 200, 340, fill=c)
    d.ellipse((6, 6, 9, 9), fill=INK)

def top_hat(d, c):
    d.rectangle((4, 2, 11, 10), fill=c)
    d.rectangle((1, 11, 14, 12), fill=c)
    d.rectangle((4, 8, 11, 9), fill=hexc('#C4553B'))

def footprints(d, c):
    for (x, y) in ((2, 8), (8, 2)):
        d.ellipse((x, y, x + 4, y + 6), fill=c)
        d.ellipse((x + 1, y + 7, x + 3, y + 9), fill=c)

def bubble(d, c):
    d.rounded_rectangle((1, 2, 14, 11), radius=3, fill=c)
    d.polygon([(4, 11), (4, 15), (8, 11)], fill=c)
    for x in (4, 7, 10):
        d.point((x, 6), fill=INK)
        d.point((x + 1, 6), fill=INK)

def scroll(d, c):
    d.rectangle((3, 2, 12, 13), fill=c)
    d.rectangle((2, 1, 13, 2), fill=shade(c, 0.75))
    d.rectangle((2, 13, 13, 14), fill=shade(c, 0.75))
    for y in (5, 7, 9, 11):
        d.line((5, y, 10, y), fill=shade(c, 0.55))

def globe(d, c):
    d.ellipse((1, 1, 14, 14), fill=c)
    d.arc((4, 1, 11, 14), 90, 270, fill=shade(c, 0.6))
    d.arc((4, 1, 11, 14), 270, 90, fill=shade(c, 0.6))
    d.line((1, 7, 14, 7), fill=shade(c, 0.6))

def magnifier(d, c):
    d.ellipse((1, 1, 10, 10), outline=c, width=2)
    d.ellipse((3, 3, 8, 8), fill=hexc('#A9D8E8', 200))
    d.line((9, 9, 14, 14), fill=shade(c, 0.7), width=3)

def shard_small(d, c):
    d.polygon([(8, 1), (13, 6), (10, 14), (5, 14), (3, 7)], fill=c)
    d.line((8, 2, 7, 13), fill=shade(c, 1.3))

def bell_small(d, c):
    d.polygon([(3, 1), (4, 0), (5, 1), (6, 5), (2, 5)], fill=c)
    d.point((4, 6), fill=c)

def icons():
    disc = {'vanguard': ('#C9714A', shield), 'wayfarer': ('#7FB069', compass_rose), 'arcanist': ('#9E86D9', collision), 'keeper': ('#D9B45A', key),
            'none': ('#9AA3A8', dot)}
    for k, (col, fn) in disc.items():
        draw_icon(fn, col, os.path.join(GUI, 'discipline', f'{k}.png'))
    fac = {'crown': ('#C9A227', crown), 'guild': ('#D8C9A3', scroll_map), 'court': ('#8E7CC3', moth), 'bureau': ('#4FA89B', sealed_eye)}
    for k, (col, fn) in fac.items():
        draw_icon(fn, col, os.path.join(GUI, 'faction', f'{k}.png'))
    bond = {'landlord': ('#9FB7C9', top_hat), 'moth': ('#E7C46A', moth), 'pilgrim': ('#B5AE98', footprints)}
    for k, (col, fn) in bond.items():
        draw_icon(fn, col, os.path.join(GUI, 'bond', f'{k}.png'))
    tabs = {'edicts': ('#B79CE8', shard_small), 'rumours': ('#E8DCC0', bubble), 'chronicle': ('#E8DCC0', scroll),
            'world': ('#4FB3A9', globe), 'codex': ('#C9A227', magnifier)}
    for k, (col, fn) in tabs.items():
        draw_icon(fn, col, os.path.join(GUI, 'journal', f'tab_{k}.png'))
    img = Image.new('RGBA', (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bell_small(d, hexc('#E8A33D'))
    save(outline(img, INK), os.path.join(GUI, 'hud', 'suppressed.png'))

# ---------------------------------------------------------------- HUD frames and journal paper

def frame(path, border, fill, accent=None):
    img = Image.new('RGBA', (22, 22), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, 21, 21), radius=3, fill=hexc(border))
    d.rounded_rectangle((1, 1, 20, 20), radius=2, fill=hexc(fill, 215))
    d.line((2, 1, 19, 1), fill=shade(hexc(fill), 1.6))
    if accent:
        a = hexc(accent)
        for (x, y) in ((1, 1), (20, 1), (1, 20), (20, 20)):
            d.point((x, y), fill=a)
        d.line((3, 20, 18, 20), fill=shade(a, 0.8))
    save(img, path)

def paper():
    w = h = 64
    base = noise_tile(w, h, '#E6D9BB', var=5, seed=7, speck='#CDBB93', speck_rate=0.02)
    px = base.load()
    r = random.Random(11)
    for _ in range(40):
        x, y = r.randrange(w), r.randrange(h)
        c = px[x, y]
        px[x, y] = shade(c, 0.93)
    for x in range(w):
        for y in range(h):
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge < 4:
                px[x, y] = shade(px[x, y], 0.86 + edge * 0.035)
    save(base, os.path.join(GUI, 'journal', 'paper.png'))
    with open(os.path.join(GUI, 'journal', 'paper.png.mcmeta'), 'w') as f:
        f.write('{"gui": {"scaling": {"type": "nine_slice", "width": 64, "height": 64, "border": 4}}}\n')

# ---------------------------------------------------------------- items

def item(name, rows, pal):
    save(sprite(rows, pal), os.path.join(ITEM, f'{name}.png'))

def items():
    item('expedition_journal', [
        "................",
        "...kkkkkkkkkk...",
        "..kBBBBBBBBBBk..",
        "..kBbbbbbbbbBpk.",
        "..kBbGGGGGGbBpk.",
        "..kBbGAAAAGbBpk.",
        "..kBbGAmmAGbBpk.",
        "..kBbGAmAAGbBpk.",
        "..kBbGAAAAGbBpk.",
        "..kBbGGGGGGbBpk.",
        "..kBbbbbbbbbBpk.",
        "..kYbbbbbbbbYpk.",
        "..kBBBBBBBBBBpk.",
        "...kkkkkkkkrkk..",
        "...........r....",
        "................",
    ], {'k': '#1A1A22', 'B': '#5A3A24', 'b': '#47301F', 'p': '#E8DCC0', 'G': '#9C7A3C', 'A': '#2E3A42', 'm': '#E8DCC0', 'Y': '#C9A227', 'r': '#C4553B'})
    item('chronicle_page', [
        "................",
        "...kkkkkkkkk....",
        "...kPPPPPPPkk...",
        "...kPllllPPPk...",
        "...kPPPPPPPPk...",
        "...kPlllllPPk...",
        "...kPPPPPPPPk...",
        "...kPllllllPk...",
        "...kPPPPPPPPk...",
        "...kPlllPPPPk...",
        "...kPPPPPPPPk...",
        "...kPllllllPk...",
        "...kPPPPPPPdk...",
        "...kkkkkkkkkk...",
        "................",
        "................",
    ], {'k': '#3B3226', 'P': '#E8DCC0', 'l': '#B5A27C', 'd': '#CDBB93'})
    item('torn_map', [
        "................",
        "..kkkkkkk.kk....",
        "..kPPPgPPkPPk...",
        "..kPgggPPPPPPk..",
        "..kPPgPPPrPrPk..",
        "..kPPPPPPPrPPk..",
        "..kPbbPPPrPrPk..",
        "...kPbbPPPPPPk..",
        "...kPPbPPgPPk...",
        "..kPPPPPggggPk..",
        "..kPPgPPPPgPPk..",
        "..kPgggPPPPPk...",
        "..kPPPPPPPPPk...",
        "...kk.kkkkkk....",
        "................",
        "................",
    ], {'k': '#3B3226', 'P': '#DCCBA5', 'g': '#7FA068', 'b': '#5B8DB8', 'r': '#C4553B'})
    item('pilgrim_offering', [
        "................",
        ".......f........",
        "......fFf.......",
        ".......w........",
        "......kwk...gg..",
        "......kwk..gGGg.",
        "......kwk...gyg.",
        "....kkkwkkk..g..",
        "...kssssssskg...",
        "...ksSSSSSsk....",
        "...kssrrrssk....",
        "...ksSSSSSsk....",
        "....ksssssk.....",
        ".....kkkkk......",
        "................",
        "................",
    ], {'k': '#2A2420', 'f': '#E8A33D', 'F': '#FFF2B0', 'w': '#EDE3CC', 's': '#8A8478', 'S': '#A8A296', 'r': '#C4553B', 'g': '#5E7F3A', 'G': '#E8DCC0', 'y': '#F2D25C'})
    item('anomaly_residue', [
        "................",
        "................",
        "........w.......",
        "......p.........",
        "...........w....",
        ".....kkkk.......",
        "....kvVVvk..p...",
        "...kvVwVVvk.....",
        "..kvVVVVvVvk....",
        "..kvVvVVVVvVk...",
        ".kvvVVvVVwVvvk..",
        ".kvvvvVVvvVvvk..",
        "..kkvvvvvvvvk...",
        "....kkkkkkkk....",
        "................",
        "................",
    ], {'k': '#24182E', 'v': '#6A4C8C', 'V': '#9E7FD0', 'w': '#F4ECFF', 'p': '#C9B2F0'})
    item('clockwork_fragment', [
        "................",
        "......kkkk......",
        "...kk.kYYk.kk...",
        "...kYkYYYYkYk...",
        "....kYYyyYYk....",
        "..kkYyykkyyYkk..",
        "..kYYyk..kyYYk..",
        "..kYYyk..kyYYk..",
        "..kkYyykkyyYkk..",
        "....kYYyyYYk....",
        "...kYkYYYYkYk...",
        "...kk.kYYk.kk...",
        "......kkkk..ss..",
        "...........sk.s.",
        "............ss..",
        "................",
    ], {'k': '#2A1E12', 'Y': '#C9973A', 'y': '#8C6420', 's': '#A8A296'})
    item('moth_dust', [
        "................",
        "...y......y.....",
        ".........w......",
        "....w...........",
        "..........y.....",
        ".....kkkkk......",
        "....kYYwYYk.....",
        "...kYwYYYwYk....",
        "..kYYYYwYYYYk...",
        "..kYwYYYYYwYk...",
        ".kyYYYwYYYYYyk..",
        ".kyyYYYYYwYyyk..",
        "..kkyyyyyyyyk...",
        "....kkkkkkkk....",
        "................",
        "................",
    ], {'k': '#3A2E14', 'Y': '#E7D48A', 'y': '#B8A35A', 'w': '#FFF8D8'})
    item('grave_wax', [
        "................",
        "................",
        "................",
        ".......f........",
        "......fFf.......",
        ".......t........",
        "......kkk.......",
        ".....kWWWk......",
        ".....kWwWWk.....",
        "....kWWWWwWk....",
        "....kWWwWWWk....",
        "...kWWWWWWwWk...",
        "...kwWWWWWWwk...",
        "..kkkkkkkkkkkk..",
        "..kssssssssssk..",
        "...kkkkkkkkkk...",
    ], {'k': '#3B3226', 'W': '#E8E2D0', 'w': '#C8C0AA', 'f': '#E8A33D', 'F': '#FFF2B0', 't': '#2A2420', 's': '#6E6458'})
    item('bell_fragment', [
        "................",
        "................",
        "......kkk.......",
        ".....kYYYk......",
        "....kYyYYYk.....",
        "...kYyYYYYk.....",
        "...kYyYYYk......",
        "..kYyYYYYk......",
        "..kYyYYYYYk.....",
        ".kYyYYYYYYYk....",
        ".kyyyyYYYYYk....",
        "..kkkkyyyYk.....",
        "......kkkk......",
        "................",
        "................",
        "................",
    ], {'k': '#2A1E12', 'Y': '#C9973A', 'y': '#8C6420'})
    item('hungry_lantern', [
        "......kkkk......",
        ".....k....k.....",
        "......kkkk......",
        ".....kIIIIk.....",
        "....kIkkkkIk....",
        "....kIkGGkIk....",
        "....kIGggGIk....",
        "....kIGggGIk....",
        "....kIGGGGIk....",
        "....kIkGGkIk....",
        "....kIkkkkIk....",
        ".....kIIIIk.....",
        "......kkkk......",
        "................",
        "................",
        "................",
    ], {'k': '#1A1A22', 'I': '#4A4E56', 'G': '#3A3226', 'g': '#5A4A30'})
    item('hungry_lantern_lit', [
        "......kkkk......",
        ".....k....k.....",
        "......kkkk......",
        ".....kIIIIk.....",
        "....kIkkkkIk....",
        "....kIkGGkIk....",
        "....kIGgWGIk....",
        "....kIGWgGIk....",
        "....kIGGGGIk....",
        "....kIkGGkIk....",
        "....kIkkkkIk....",
        ".....kIIIIk.....",
        "......kkkk......",
        "................",
        "................",
        "................",
    ], {'k': '#1A1A22', 'I': '#4A4E56', 'G': '#E8A33D', 'g': '#FFD27A', 'W': '#FFF6D6'})
    item('clockwork_heart', [
        "................",
        "...kkk....kkk...",
        "..kRRRk..kRRRk..",
        ".kRrRRRkkRRRRRk.",
        ".kRrRRRRRRRRRRk.",
        ".kRRRkkkkkRRRRk.",
        ".kRRkYYYYYkRRRk.",
        "..kRkYyYyYkRRk..",
        "..kRkYYkYYkRRk..",
        "...kRkYYYkRRk...",
        "....kRkkkRRk....",
        ".....kRRRRk.....",
        "......kRRk......",
        ".......kk.......",
        "................",
        "................",
    ], {'k': '#2A1612', 'R': '#B0503A', 'r': '#E08A6A', 'Y': '#C9973A', 'y': '#FFE08A'})
    item('thimble_of_borrowed_rain', [
        ".....ccccc......",
        "...ccCCCCCcc....",
        "..cCCCCCCCCCc...",
        "...cccccccccc...",
        "....b..b..b.....",
        "...b..b..b......",
        "................",
        "......kkkk......",
        ".....kSsSSk.....",
        "....kSsSSSSk....",
        "....kSSsSsSk....",
        "....kSsSSSSk....",
        "....kSSSsSSk....",
        "...kssssssssk...",
        "...kkkkkkkkkk...",
        "................",
    ], {'c': '#8C9AA8', 'C': '#C8D2DC', 'b': '#5B8DB8', 'k': '#2A2E36', 'S': '#C0C6CC', 's': '#8A9098'})
    item('apologetic_blade', [
        "..............kk",
        ".............kWk",
        "............kWSk",
        "...........kWSk.",
        "..........kWSk..",
        ".........kWSk...",
        "........kWSk....",
        ".......kWSk.....",
        "..kk..kWSk......",
        "..kGkkWSk.......",
        "...kGpSk........",
        "....kpGk........",
        "...khkkGk.......",
        "..khk..kk.......",
        ".khk............",
        ".kk.............",
    ], {'k': '#1A1A22', 'W': '#F2F2EE', 'S': '#B8BCC4', 'G': '#C9A227', 'p': '#E89AA8', 'h': '#5A3A24'})

# What each creature's egg wears, so it reads as that creature at a glance (as the vanilla eggs do).
EGG_FEATURES = {
    'crow': 'beak wings', 'bone_gull': 'beak wings', 'canyon_vulture': 'beak wings', 'windrider_eagle': 'beak wings', 'blossom_crane': 'beak',
    'mossmere_owl': 'ears eyes wings', 'echo_bat': 'ears wings', 'rime_moth': 'wings', 'lantern_moth': 'wings', 'ash_wyrmling': 'horns wings',
    'mourning_shade': 'ghost eyes', 'petal_sprite': 'wings leaf', 'wisp': 'ghost eyes', 'skywhale': 'fins',
    'duskpine_lynx': 'ears', 'kitsune': 'ears', 'emberfox': 'ears flame', 'snow_hare': 'ears', 'silverrun_otter': 'ears', 'dune_strider': 'ears',
    'dusk_hound': 'ears eyes', 'highland_ram': 'horns', 'rimehorn': 'horns', 'hollow_stag': 'horns', 'thornback_boar': 'horns',
    'tusked_narwhal': 'fins', 'glimmerfin': 'fins', 'mirror_ray': 'fins', 'abyssal_angler': 'fins eyes', 'floe_seal': 'fins',
    'tidecaller_jelly': 'tentacles', 'crystal_crab': 'shell crystal', 'drowned_revenant': 'eyes', 'cinder_beetle': 'shell flame',
    'sand_sifter': 'shell', 'glyph_scarab': 'shell', 'ice_crawler': 'spikes eyes', 'gloomweaver': 'eyes', 'shardling': 'crystal',
    'sporeling': 'leaf', 'vine_strangler': 'leaf eyes', 'fungal_brute': 'leaf', 'rootling_sentinel': 'leaf eyes', 'mire_shambler': 'leaf eyes',
    'mossback': 'leaf', 'rust_golemite': 'gear', 'clockwork_sentry': 'gear eyes', 'gearhound': 'gear ears', 'prism_golem': 'crystal',
    'rule_wraith': 'ghost eyes', 'echo_stalker': 'ghost eyes', 'ashen_husk': 'eyes flame', 'rime_wight': 'spikes eyes',
    'ember_salamander': 'flame', 'pilgrim': 'eyes', 'bell_ringer': 'eyes', 'king_of_seconds': 'crown', 'fen_toad': 'eyes', 'bog_lurker': 'eyes',
}


def egg(name, base, spots):
    """A spawn egg: a softly lit egg in the creature's colours, its spots rounded, with something of the
    creature about it (ears, wings, horns, fins, eyes, a shell, leaves, a gear, a crystal, a ghostly
    hem, a flame, ice spikes, a crown)."""
    import colorsys
    from PIL import Image
    who = name[:-len('_spawn_egg')] if name.endswith('_spawn_egg') else name
    feats = EGG_FEATURES.get(who, '').split()
    b = hexc(base)
    sp = hexc(spots)

    def tone(c, k):
        r, g, bb = (v / 255 for v in c[:3])
        h, l, s_ = colorsys.rgb_to_hls(r, g, bb)
        target = 0.72 if k < 0 else 0.14
        dh = ((target - h + 0.5) % 1.0) - 0.5
        h2 = (h + dh * min(1, abs(k)) * (0.10 if k < 0 else 0.05)) % 1.0
        l2 = min(0.96, max(0.05, l + k * (0.32 if k < 0 else 0.26)))
        s2 = min(1.0, s_ * (1.05 if abs(k) < 0.6 else 0.95) + 0.04)
        rr, gg, b2 = colorsys.hls_to_rgb(h2, l2, s2)
        return (int(rr * 255), int(gg * 255), int(b2 * 255), 255)

    outline = tone(b, -1.6)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 7.5, 8.6

    def half_w(y):
        t = (y + 0.5 - cy) / 6.3
        if abs(t) >= 1:
            return 0
        return 5.6 * (1 - t * t) ** 0.5 * (0.84 + 0.16 * min(1, max(0, (y - 2) / 10)))

    inside = [[abs(x + 0.5 - cx) <= half_w(y) for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            if not inside[y][x]:
                continue
            nx = (x + 0.5 - cx) / 5.6
            ny = (y + 0.5 - cy) / 6.3
            light = -0.62 * nx - 0.72 * ny
            k = 0.85 if light > 0.55 else 0.4 if light > 0.15 else 0.0 if light > -0.35 else -0.45
            px[x, y] = tone(b, k)
    # Rounded spots in the creature's second colour.
    for (sx, sy, w, h) in ((5, 5, 2, 2), (9, 7, 2, 2), (6, 10, 3, 2), (10, 11, 2, 1)):
        for yy in range(sy, sy + h):
            for xx in range(sx, sx + w):
                if inside[yy][xx]:
                    px[xx, yy] = tone(sp, 0.35 if yy == sy else 0.0)
    # The outline, round the egg (drawn after, so features overlap it cleanly).
    for y in range(16):
        for x in range(16):
            if inside[y][x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if 0 <= xx < 16 and 0 <= yy < 16 and inside[yy][xx]:
                    px[x, y] = outline
                    break
    hi = tone(b, 1.0)
    px[5, 4] = hi
    px[4, 6] = hi

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c

    s_hi, s_mid, s_lo = tone(sp, 0.6), tone(sp, 0.0), tone(sp, -0.7)
    for f in feats:
        if f == 'ears':
            for ex in (5, 10):
                put(ex, 1, outline)
                put(ex - (1 if ex < 8 else -1), 2, outline)
                put(ex, 2, tone(b, 0.4))
                put(ex + (1 if ex < 8 else -1), 2, s_mid)
        elif f == 'horns':
            for (x, y) in ((4, 2), (3, 1), (3, 0), (11, 2), (12, 1), (12, 0)):
                put(x, y, hexc('#E8DCC0'))
            put(4, 3, outline)
            put(11, 3, outline)
        elif f == 'wings':
            for (x, y, c) in ((1, 7, s_mid), (0, 6, s_hi), (1, 8, s_lo), (0, 8, outline), (1, 6, outline),
                              (14, 7, s_mid), (15, 6, s_hi), (14, 8, s_lo), (15, 8, outline), (14, 6, outline)):
                put(x, y, c)
        elif f == 'beak':
            put(13, 6, hexc('#F0B040'))
            put(14, 6, hexc('#C88020'))
            put(13, 7, hexc('#C88020'))
        elif f == 'eyes':
            glow = tone(sp, 0.9)
            put(6, 6, glow)
            put(9, 6, glow)
            put(6, 7, outline)
            put(9, 7, outline)
        elif f == 'fins':
            for (x, y, c) in ((7, 1, s_hi), (8, 1, s_mid), (7, 2, s_mid), (2, 10, s_mid), (13, 10, s_mid), (1, 11, s_lo), (14, 11, s_lo)):
                put(x, y, c)
        elif f == 'shell':
            for x in range(3, 13):
                if inside[9][x]:
                    px[x, 9] = tone(b, -0.6)
            for y in range(4, 14):
                if inside[y][7]:
                    px[7, y] = tone(b, -0.6)
        elif f == 'crystal':
            for (x, y, c) in ((11, 1, hexc('#E8F8FF')), (11, 2, s_hi), (12, 2, s_mid), (10, 2, s_hi), (12, 3, s_lo), (10, 3, s_mid), (11, 3, s_mid)):
                put(x, y, c)
        elif f == 'leaf':
            for (x, y, c) in ((8, 0, hexc('#9AE060')), (9, 0, hexc('#6AB040')), (8, 1, hexc('#6AB040')), (7, 1, hexc('#4A8A30')), (7, 2, outline)):
                put(x, y, c)
        elif f == 'gear':
            for (x, y) in ((11, 9), (12, 9), (13, 10), (13, 11), (12, 12), (11, 12), (10, 11), (10, 10)):
                put(x, y, s_mid)
            put(11, 10, outline)
            put(12, 11, outline)
        elif f == 'ghost':
            for x in range(16):
                for y in range(13, 16):
                    if (x + y) % 3 == 0 and px[x, y][3]:
                        px[x, y] = (0, 0, 0, 0)
        elif f == 'flame':
            for (x, y, c) in ((8, 0, hexc('#FFF0A0')), (8, 1, hexc('#FFB040')), (7, 1, hexc('#FF7020')), (9, 1, hexc('#FF7020')), (8, 2, hexc('#E85020'))):
                put(x, y, c)
        elif f == 'spikes':
            for (x, y) in ((5, 2), (8, 1), (11, 2)):
                put(x, y, hexc('#C8F0FF'))
                put(x, y + 1, hexc('#8AD0F0'))
        elif f == 'tentacles':
            for x in (5, 7, 9, 11):
                put(x, 15, s_mid)
                put(x, 14, s_lo)
        elif f == 'crown':
            for (x, y, c) in ((6, 1, hexc('#FFE080')), (8, 0, hexc('#FFE080')), (10, 1, hexc('#FFE080')), (6, 2, hexc('#E0A020')), (7, 2, hexc('#E0A020')),
                              (8, 2, hexc('#FF4040')), (9, 2, hexc('#E0A020')), (10, 2, hexc('#E0A020'))):
                put(x, y, c)
    save(img, os.path.join(ITEM, f'{name}.png'))


def more_items():
    egg('pilgrim_spawn_egg', '#7C7A72', '#4E6A3A')
    egg('bell_ringer_spawn_egg', '#8A9AA0', '#9C6A2A')
    egg('king_of_seconds_spawn_egg', '#B8892E', '#3A3E46')
    egg('lantern_moth_spawn_egg', '#8A7458', '#F2C14E')
    egg('crow_spawn_egg', '#1E1E26', '#3A3E5A')
    egg('mossback_spawn_egg', '#4E5A3A', '#7A9A44')
    egg('hollow_stag_spawn_egg', '#7A5434', '#9FE8D0')
    egg('clockwork_sentry_spawn_egg', '#B8892E', '#FF5A3A')
    egg('rule_wraith_spawn_egg', '#2A2440', '#B79CE8')
    egg('wisp_spawn_egg', '#2F4A3E', '#FFF6D8')
    # The second wave.
    for name, base, spots in (('ashen_husk', '#2E2A28', '#E8641E'), ('shardling', '#3A3446', '#C8A8FF'), ('bog_lurker', '#4A5A2E', '#F2D24A'),
                              ('echo_stalker', '#141220', '#B79CE8'), ('gearhound', '#B8892E', '#FF4A2A'), ('rime_wight', '#DCEEFF', '#4AC8FF'),
                              ('skywhale', '#2C3E5C', '#8AF0E8'), ('glimmerfin', '#5AC8C0', '#FFF0A0'), ('emberfox', '#B8441E', '#FFB04A'),
                              ('rimehorn', '#D8D4CC', '#8AC8E8'), ('petal_sprite', '#F8E8F0', '#E87AB0'), ('mirror_ray', '#3A5A7A', '#F0F8FF')):
        egg(name + '_spawn_egg', base, spots)
    item('ember_core', [
        "................",
        "......kkkk......",
        "....kkDooDkk....",
        "...kDoYYooDDk...",
        "...kooYWYooDk...",
        "..kDoYYYYoooDk..",
        "..kooooYoooDDk..",
        "..kDooooooDDDk..",
        "..kDDooooDDDDk..",
        "...kDDDooDDDk...",
        "...kDDDDDDDDk...",
        "....kkDDDDkk....",
        "......kkkk......",
        "................",
        "................",
        "................",
    ], {'k': '#1A1210', 'D': '#3A2A24', 'o': '#E8641E', 'Y': '#FFC24A', 'W': '#FFF0C0'})
    item('living_crystal', [
        "................",
        "........k.......",
        ".......kLk......",
        "......kLWLk.....",
        "......kLLMk.....",
        ".....kLWLMMk....",
        ".....kLLMMMk.k..",
        "....kLLMMMDk.kL.",
        "....kLMMMDDkkLM.",
        "...kLLMMMDk.kMMk",
        "...kLMMDDDk.kMDk",
        "..kLMMDDDk...kk.",
        "..kkMDDDkk......",
        "....kkkk........",
        "................",
        "................",
    ], {'k': '#2A1E3A', 'L': '#E0C8FF', 'W': '#FFFFFF', 'M': '#A87ADA', 'D': '#6A44A0'})
    item('lurker_fang', [
        "................",
        "..........kkk...",
        ".........kWWWk..",
        "........kWWBBk..",
        ".......kWWBBk...",
        "......kWWBBk....",
        ".....kWWBBk.....",
        "....kWWBBk......",
        "...kWWBBk.......",
        "..kWWBBk........",
        "..kWBBk.........",
        "...kBk..........",
        "....k...........",
        "................",
        "................",
        "................",
    ], {'k': '#2E2A1C', 'W': '#F0E8D0', 'B': '#B8A87A'})
    item('echo_essence', [
        "................",
        "......kkkk......",
        "......kCCk......",
        ".....kkGGkk.....",
        "....kGGGGGGk....",
        "...kGVVGGVVGk...",
        "...kGVPVVPVGk...",
        "...kGVVPPVVGk...",
        "...kGGVVVVGGk...",
        "...kGGGVVGGGk...",
        "....kGGGGGGk....",
        ".....kkkkkk.....",
        "................",
        "................",
        "................",
        "................",
    ], {'k': '#141220', 'C': '#8A6A4A', 'G': '#3A3460', 'V': '#6A4AB0', 'P': '#D8C0FF'})
    item('gear_spring', [
        "................",
        ".....kkkkkk.....",
        "....kBBBBBBk....",
        "...kBkkkkkkBk...",
        "...kBk.kk.kBk...",
        "...kBk.kB.kBk...",
        "...kBk.kkBkBk...",
        "...kBkkkkBkBk...",
        "...kBBBBBBkBk...",
        "....kkkkkkkBk...",
        "....kBBBBBBBk...",
        ".....kkkkkkk....",
        "................",
        "................",
        "................",
        "................",
    ], {'k': '#4A3410', 'B': '#E8C060'})
    item('rime_core', [
        "................",
        ".......kk.......",
        "......kWWk......",
        ".....kWIIWk.....",
        "...kkWICCIWkk...",
        "..kWWICBBCIWWk..",
        "...kkWICCIWkk...",
        ".....kWIIWk.....",
        "......kWWk......",
        ".......kk.......",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], {'k': '#2A4A6A', 'W': '#FFFFFF', 'I': '#BFE8FF', 'C': '#6AC8F0', 'B': '#2A8AD0'})
    item('glimmerfin_bucket', [
        "................",
        "................",
        "....kkkkkkkk....",
        "...kGWWWWWWGk...",
        "...kWwwFFwwWk...",
        "...kWwFYFFwWk...",
        "...kGwwFFwwGk...",
        "...kGGwwwwGGk...",
        "....kGGGGGGk....",
        "....kGHGGGGk....",
        "....kGHGGGGk....",
        "....kGGGGGGk....",
        ".....kGGGGk.....",
        ".....kkkkkk.....",
        "................",
        "................",
    ], {'k': '#2A2E34', 'G': '#8A9098', 'H': '#C8CDD4', 'W': '#3A6AA8', 'w': '#4A86C8', 'F': '#5AC8C0', 'Y': '#FFF0A0'})
    item('torn_census', [
        "................",
        "..kkkkkkkkk.....",
        "..kPPPPPPPkk....",
        "..kPlllPPPPkk...",
        "..kPPPPPPPPPk...",
        "..kPlllllPPPk...",
        "..kPPPPPrPPPk...",
        "..kPllllrllPk...",
        "..kPPPPPrPPPk...",
        "..kPlllPPPPk....",
        "..kPPPPPPPPkk...",
        "..kPllllllPPk...",
        "..kPPPPPPkPPk...",
        "..kkkkkkk.kk....",
        "................",
        "................",
    ], {'k': '#3B3226', 'P': '#D8CBA8', 'l': '#8A7A5C', 'r': '#A8432E'})
    item('ringers_ledger', [
        "................",
        "...kkkkkkkkkk...",
        "..kRRRRRRRRRRk..",
        "..kRrrrrrrrrRpk.",
        "..kRrYYYYYYrRpk.",
        "..kRrYyyyyYrRpk.",
        "..kRrYYYYYYrRpk.",
        "..kRrrrrrrrrRpk.",
        "..kRrrrBrrrrRpk.",
        "..kRrrBBBrrrRpk.",
        "..kRrrrBrrrrRpk.",
        "..kRRRRRRRRRRpk.",
        "...kkkkkkkkkkk..",
        "................",
        "................",
        "................",
    ], {'k': '#1A1A22', 'R': '#4A2E2A', 'r': '#3A2420', 'p': '#D8CBA8', 'Y': '#9C7A3C', 'y': '#5A4420', 'B': '#C9973A'})

def compass_frames():
    os.makedirs(ITEM, exist_ok=True)
    rim = hexc('#9C7A3C')
    rim_d = hexc('#5A4420')
    dial = hexc('#E8DCC0')
    for i in range(32):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.ellipse((1, 1, 14, 14), fill=rim_d)
        d.ellipse((2, 2, 13, 13), fill=rim)
        d.ellipse((3, 3, 12, 12), fill=dial)
        # the unfinished part of the rim
        d.pieslice((1, 1, 14, 14), 300, 340, fill=(0, 0, 0, 0))
        d.pieslice((3, 3, 12, 12), 300, 340, fill=shade(dial, 0.8))
        a = (i / 32.0) * math.pi * 2
        cx, cy = 7.5, 7.5
        tip = (cx + math.sin(a) * 4.2, cy - math.cos(a) * 4.2)
        tail = (cx - math.sin(a) * 2.6, cy + math.cos(a) * 2.6)
        d.line((cx, cy, tip[0], tip[1]), fill=hexc('#C4553B'), width=1)
        d.line((cx, cy, tail[0], tail[1]), fill=hexc('#3B3226'), width=1)
        d.point((7, 7), fill=hexc('#1A1A22'))
        save(outline(img, INK), os.path.join(ITEM, f'unfinished_compass_{i:02d}.png'))

# ---------------------------------------------------------------- blocks

def bricks(base, mortar, seed, rivet=None):
    img = noise_tile(16, 16, base, var=8, seed=seed)
    px = img.load()
    m = hexc(mortar)
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 4 if row % 2 else 0
            if y % 4 == 3 or (x + off) % 8 == 7:
                px[x, y] = m
            elif y % 4 == 0:
                px[x, y] = shade(px[x, y], 1.12)
    if rivet:
        for (x, y) in ((1, 1), (9, 5), (5, 9), (13, 13)):
            px[x, y] = hexc(rivet)
    return img

def gear(base, light, dark, seed, ward=None):
    img = noise_tile(16, 16, base, var=6, seed=seed)
    d = ImageDraw.Draw(img)
    cx, cy = 7.5, 7.5
    for k in range(8):
        a = k * math.pi / 4
        x, y = cx + math.cos(a) * 6, cy + math.sin(a) * 6
        d.rectangle((round(x) - 1, round(y) - 1, round(x), round(y)), fill=hexc(light))
    d.ellipse((2, 2, 13, 13), fill=hexc(light))
    d.ellipse((4, 4, 11, 11), fill=hexc(dark))
    d.ellipse((6, 6, 9, 9), fill=hexc(light))
    if ward:
        d.line((0, 7, 15, 7), fill=hexc(ward))
        d.line((7, 0, 7, 15), fill=hexc(ward))
    return img

def blocks():
    save(bricks('#7A5A3C', '#3A2A1C', 3, rivet='#C9973A'), os.path.join(BLOCK, 'clockwork_bricks.png'))
    save(gear('#4A3A2A', '#B07A3A', '#5A3E22', 5), os.path.join(BLOCK, 'gearwork.png'))
    save(gear('#24443E', '#4FA89B', '#1E3A36', 9, ward='#B8F0E8'), os.path.join(BLOCK, 'warded_gearwork.png'))
    # Escapement: a brass anchor over dark gearwork.
    img = gear('#3A2E1E', '#8C6420', '#2A1E12', 13)
    d = ImageDraw.Draw(img)
    d.arc((3, 2, 12, 11), 200, 340, fill=hexc('#FFD27A'), width=2)
    d.line((7, 3, 7, 13), fill=hexc('#FFD27A'), width=2)
    d.rectangle((5, 12, 10, 14), fill=hexc('#E8A33D'))
    save(img, os.path.join(BLOCK, 'escapement.png'))
    # Counterweight: iron plate with a hook and rivets.
    img = noise_tile(16, 16, '#4A4E56', var=6, seed=17)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 15, 15), outline=hexc('#2A2E36'))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        d.point((x, y), fill=hexc('#9AA0A8'))
    d.arc((4, 6, 11, 13), 0, 200, fill=hexc('#C8CCD2'), width=2)
    d.line((10, 2, 10, 9), fill=hexc('#C8CCD2'), width=2)
    save(img, os.path.join(BLOCK, 'counterweight.png'))
    # Shrine stone, cracked and repaired.
    for repaired in (False, True):
        img = noise_tile(16, 16, '#5A5A5E', var=9, seed=23)
        d = ImageDraw.Draw(img)
        d.rectangle((0, 0, 15, 15), outline=hexc('#3A3A40'))
        glow = hexc('#FFC860') if repaired else hexc('#3A3A40')
        d.ellipse((3, 3, 12, 12), outline=glow)
        d.line((7, 4, 7, 11), fill=glow)
        d.line((5, 7, 10, 7), fill=glow)
        if not repaired:
            d.line((1, 2, 5, 6), fill=hexc('#1E1E22'))
            d.line((5, 6, 4, 10), fill=hexc('#1E1E22'))
            d.line((4, 10, 8, 15), fill=hexc('#1E1E22'))
            d.line((10, 0, 12, 5), fill=hexc('#1E1E22'))
        save(img, os.path.join(BLOCK, 'shrine_stone_repaired.png' if repaired else 'shrine_stone.png'))
    # Anomaly core: an animated swirl of loose rule.
    frames = 16
    strip = Image.new('RGBA', (16, 16 * frames))
    for f in range(frames):
        t = f / frames * math.pi * 2
        for y in range(16):
            for x in range(16):
                dx, dy = x - 7.5, y - 7.5
                r = math.hypot(dx, dy)
                a = math.atan2(dy, dx)
                v = math.sin(a * 3 + r * 0.9 - t) * 0.5 + 0.5
                v2 = math.sin(r * 1.3 - t * 2) * 0.5 + 0.5
                c = mix(hexc('#2A1844'), hexc('#B79CE8'), v * 0.75 + v2 * 0.25)
                if r < 2.2:
                    c = mix(c, hexc('#FFFFFF'), 0.6)
                strip.putpixel((x, y + f * 16), c)
    save(strip, os.path.join(BLOCK, 'anomaly_core.png'))
    with open(os.path.join(BLOCK, 'anomaly_core.png.mcmeta'), 'w') as fh:
        fh.write('{"animation": {"frametime": 2, "interpolate": true}}\n')

# ---------------------------------------------------------------- mod icon

def mod_icon():
    s = 64
    img = Image.new('RGBA', (s, s), hexc('#141A20'))
    d = ImageDraw.Draw(img)
    r = random.Random(3)
    for _ in range(40):
        x, y = r.randrange(s), r.randrange(s // 2)
        d.point((x, y), fill=hexc('#E8DCC0', r.randint(120, 255)))
    d.ellipse((30, 6, 58, 34), fill=hexc('#1E2A32'))
    d.ellipse((36, 12, 52, 28), fill=hexc('#E8D7A0'))
    d.polygon([(0, 44), (14, 36), (26, 42), (40, 34), (64, 40), (64, 64), (0, 64)], fill=hexc('#22303A'))
    d.polygon([(0, 52), (20, 46), (44, 50), (64, 46), (64, 64), (0, 64)], fill=hexc('#34483A'))
    d.rectangle((38, 30, 52, 52), fill=hexc('#0E1216'))
    d.rectangle((42, 24, 48, 30), fill=hexc('#0E1216'))
    d.polygon([(52, 30), (56, 30), (54, 22)], fill=hexc('#0E1216'))
    for (x, y) in ((44, 33), (44, 39), (40, 46), (49, 46)):
        d.rectangle((x, y, x + 1, y + 2), fill=hexc('#E8A33D'))
    d.rectangle((18, 44, 19, 50), fill=hexc('#0E1216'))
    d.ellipse((17, 42, 20, 45), fill=hexc('#0E1216'))
    d.point((21, 46), fill=hexc('#E8A33D'))
    for i in range(8):
        d.point((6 + i * 3, 58 - (i % 3)), fill=hexc('#C98A2E'))
    for (x, y, col) in ((10, 18, '#E8A33D'), (16, 14, '#4FB3A9'), (22, 19, '#9BD08A'), (28, 15, '#C4553B')):
        d.polygon([(x, y - 2), (x + 2, y), (x, y + 2), (x - 2, y)], fill=hexc(col))
    img = img.resize((128, 128), Image.NEAREST)
    save(img, os.path.join(os.path.dirname(ROOT), 'icon.png'))

if __name__ == '__main__':
    # Item sprites (fragments, relics, weapons, materials, the compass) are painted by
    # tools/gen_items.py now; this script keeps the GUI, icons, blocks and the mod icon.
    edict_icons()
    icons()
    frame(os.path.join(GUI, 'hud', 'slot.png'), '#0E1216', '#24303A')
    frame(os.path.join(GUI, 'hud', 'slot_signature.png'), '#0E1216', '#2A2620', accent='#E8A33D')
    paper()
    blocks()
    mod_icon()
    print('textures written to', os.path.abspath(ROOT))

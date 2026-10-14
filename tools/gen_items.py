#!/usr/bin/env python3
"""Every Afterworld item sprite, painted with tools/itemart.py.

Weapons lean the classic way (grip bottom-left, point top-right). Growing weapons get three
looks: tier II adds a gem and richer metal, tier III a glowing edge and ornament. Writes
textures/item/<id>.png (and <id>_t2/_t3), item models and item definitions (tiered weapons select
their look on custom model data), and with --preview DIR, contact sheets for checking.
"""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from itemart import Sprite, mat, sheet, PALETTE  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld')
TEX = os.path.join(A, 'textures', 'item')
SPRITES = {}
TIERED = {}          # id -> number of tiers


def sprite(name, tiers=1):
    def wrap(fn):
        if tiers == 1:
            SPRITES[name] = fn
        else:
            TIERED[name] = tiers
            for t in range(1, tiers + 1):
                SPRITES[name if t == 1 else f'{name}_t{t}'] = (lambda t=t: fn(t))
        return fn
    return wrap


# Extra materials.
mat('emberblade', '#d8603a', 'metal')
mat('rimeblade', '#9ad8f0', 'metal')
mat('echoblade', '#7a8ad0', 'metal')
mat('crystalblade', '#9ae8e0', 'gem')
mat('sandsteel', '#c8a870', 'metal')
mat('lichbone', '#d8c8b0', 'matte')
mat('crimson', '#a8203a', 'cloth')
mat('ashsteel', '#5a5660', 'metal')
mat('glyphgold', '#f0c040', 'metal')
mat('jade', '#4ab890', 'gem')
mat('bogiron', '#6a7458', 'metal')
mat('moss_cloth', '#5e7a3a', 'cloth')
mat('violet', '#7a4ac0', 'cloth')
mat('starcloth', '#2b3570', 'cloth')
mat('teal_metal', '#3a9a90', 'metal')
mat('paperlantern', '#f0d8a0', 'paper')
mat('ink', '#2a2a40', 'matte')
mat('spore', '#c0e070', 'glow')
mat('sporestem', '#8a6a9a', 'organic')
mat('rimefur', '#e0ecf4', 'organic')
mat('soot', '#3a3436', 'matte')


def glow_trail(s, pts, m='spirit'):
    for (x, y) in pts:
        if 0 <= x < 16 and 0 <= y < 16 and s.cells[y][x] is None:
            s.dot(x, y, m, 2)


# ================================================================== weapons

def bezier(p0, p1, p2, n=24):
    """Pixels along a quadratic curve, without repeats."""
    out = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        q = (int(round(x)), int(round(y)))
        if not out or out[-1] != q:
            out.append(q)
    return out



def sword(blade, fuller, guard, grip, gem, tier, glow=None, curve=False):
    s = Sprite()
    w = 2.2 if tier < 3 else 2.6
    s.line((4, 11), (13, 2), w, blade)
    s.line((5, 10), (12, 3), 0.9, fuller)
    if tier >= 2:
        s.line((1, 8), (7, 14), 1.6, guard)
        s.dots([(1, 8), (7, 14)], guard, 4)
    else:
        s.line((2, 9), (6, 13), 1.4, guard)
    s.line((1, 14), (4, 11), 1.4, grip)
    s.dot(0, 15, guard)
    if tier >= 2:
        s.dot(4, 11, gem, 3)
    if tier >= 3 and glow:
        glow_trail(s, [(14, 0), (15, 1), (14, 3), (11, 1)], glow)
        s.dot(13, 2, 'shine')
    return s


@sprite('emberbrand', 3)
def emberbrand(t):
    s = sword('emberblade', 'ember' if t >= 2 else 'copper', 'darksteel', 'leather', 'ruby', t, 'ember')
    if t >= 2:
        s.dots([(7, 6), (9, 4)], 'ember', 3)
    return s.render(glow_halo=t >= 3)


@sprite('echo_edge', 3)
def echo_edge(t):
    s = sword('echoblade', 'silver', 'moonsilver', 'darkleather', 'amethyst', t, 'arcane')
    # The afterimage: a faint second blade.
    for (x, y) in [(6, 12), (8, 10), (10, 8), (12, 6), (14, 4)]:
        if s.cells[y][x] is None:
            s.dot(x, y, 'arcane', 1 if t < 3 else 2)
    return s.render(glow_halo=t >= 3)


@sprite('moonsilver_rapier', 3)
def rapier(t):
    s = Sprite()
    s.line((5, 10), (14, 1), 1.2, 'moonsilver')
    s.dot(14, 1, 'shine')
    # Swept hilt: a ring of silver round the grip.
    for (x, y) in [(2, 9), (2, 10), (3, 8), (4, 8), (5, 9), (6, 11), (6, 12), (5, 13)]:
        s.dot(x, y, 'silver')
    s.line((1, 14), (4, 11), 1.3, 'starcloth')
    s.dot(0, 15, 'silver')
    if t >= 2:
        s.dot(4, 11, 'sapphire', 3)
    if t >= 3:
        glow_trail(s, [(15, 0), (12, 2), (10, 4)], 'frost')
    return s.render(glow_halo=t >= 3)


@sprite('shardsong_glaive', 3)
def glaive(t):
    s = Sprite()
    s.line((1, 14), (10, 5), 1.3, 'darkwood' if t < 3 else 'moonsilver')
    s.poly([(9, 7), (12, 1), (15, 0), (14, 4), (11, 8)], 'crystalblade')
    s.dots([(12, 3), (13, 2)], 'crystal', 4)
    s.line((8, 7), (10, 9), 1.2, 'silver')
    if t >= 2:
        s.dots([(6, 9), (4, 11)], 'aqua', 3)
    if t >= 3:
        glow_trail(s, [(15, 2), (12, 0), (10, 2)], 'frost')
    return s.render(glow_halo=t >= 3)


@sprite('lurkers_hook', 3)
def hook(t):
    s = Sprite()
    s.line((1, 14), (7, 8), 1.5, 'darkwood')
    s.dots([(2, 13), (4, 11), (6, 9)], 'rope')
    # A broad sickle blade curling back over the grip, with a fang set in it.
    blade = [(7, 7), (8, 6), (8, 5), (9, 4), (10, 3), (11, 2), (12, 2), (13, 2), (14, 3), (14, 4), (14, 5), (13, 6)]
    for (x, y) in blade:
        s.dot(x, y, 'bogiron')
    for (x, y) in [(9, 5), (10, 4), (11, 3), (12, 3), (13, 3), (13, 4)]:
        s.dot(x, y, 'bogiron')
    s.dot(12, 7, 'lichbone', 4)
    s.dot(13, 7, 'lichbone')
    s.tone([(11, 2), (12, 2)], 4)
    if t >= 2:
        s.dot(8, 7, 'emerald', 3)
    if t >= 3:
        glow_trail(s, [(15, 4), (15, 6), (12, 1), (10, 2)], 'spore')
    return s.render(glow_halo=t >= 3)


@sprite('gearspring_maul', 3)
def maul(t):
    s = Sprite()
    s.line((1, 14), (8, 7), 1.5, 'darkwood')
    s.poly([(6, 6), (10, 2), (15, 7), (11, 11)], 'brass')
    s.poly([(8, 6), (10, 4), (13, 7), (11, 9)], 'darksteel')
    s.dot(10, 6, 'aqua' if t >= 2 else 'copper', 3)
    for (x, y) in [(7, 3), (14, 10), (5, 8)]:
        s.dot(x, y, 'brass')
    if t >= 3:
        glow_trail(s, [(15, 4), (12, 0), (15, 9)], 'spirit')
    return s.render(glow_halo=t >= 3)


@sprite('rimefang_spear', 3)
def spear(t):
    s = Sprite()
    s.line((1, 14), (11, 4), 1.2, 'palewood' if t < 3 else 'moonsilver')
    s.poly([(10, 5), (13, 0), (15, 0), (15, 2), (11, 6)], 'rimeblade')
    s.dots([(13, 1), (14, 1)], 'shine')
    s.dots([(9, 4), (11, 7)], 'rimefur')
    if t >= 2:
        s.dot(10, 6, 'aqua', 3)
        s.dots([(7, 8), (8, 9)], 'cloth_blue')
    if t >= 3:
        glow_trail(s, [(12, 0), (15, 3), (9, 2)], 'frost')
    return s.render(glow_halo=t >= 3)


@sprite('duskpine_longbow', 3)
def longbow(t):
    s = Sprite()
    limb = 'darkwood' if t < 3 else 'moonsilver'
    # The limbs bow out toward the top-right; the string runs straight from tip to tip.
    curve = bezier((1, 2), (14, 1), (13, 14), 30)
    for (x, y) in curve:
        s.dot(x, y, limb)
    for (x, y) in bezier((2, 3), (13, 2), (12, 13), 30):
        if s.cells[y][x] is None:
            s.dot(x, y, limb)
    s.line((1, 2), (13, 14), 0.35, 'cloth_white')
    s.rect(10, 4, 11, 5, 'cloth_green')
    s.dots([(1, 2), (13, 14)], 'gold' if t >= 2 else 'palewood')
    if t >= 2:
        s.dot(11, 4, 'emerald', 3)
    if t >= 3:
        glow_trail(s, [(0, 1), (14, 15), (15, 6), (7, 0)], 'star')
    return s.render(glow_halo=t >= 3)


@sprite('bogiron_crossbow', 2)
def crossbow(t):
    s = Sprite()
    s.line((2, 13), (10, 5), 2.2, 'darkwood')
    s.line((4, 11), (9, 6), 0.6, 'wood')
    # A short heavy bow across the head, bowed forward.
    for (x, y) in bezier((5, 2), (13, 1), (14, 10), 24):
        s.dot(x, y, 'bogiron')
    for (x, y) in bezier((5, 3), (12, 2), (13, 10), 24):
        if s.cells[y][x] is None:
            s.dot(x, y, 'bogiron')
    s.line((5, 2), (9, 6), 0.3, 'rope')
    s.line((14, 10), (9, 6), 0.3, 'rope')
    s.line((9, 6), (13, 2), 0.7, 'palewood')
    s.dot(13, 2, 'emerald' if t >= 2 else 'iron', 3)
    s.dots([(3, 13), (2, 12)], 'leather')
    return s.render()


@sprite('wayfarers_fang', 2)
def dagger(t):
    s = Sprite()
    s.line((6, 9), (12, 3), 1.8, 'steel')
    s.dot(12, 3, 'shine')
    s.line((7, 8), (11, 4), 0.6, 'silver')
    s.line((4, 7), (8, 11), 1.2, 'brass')
    s.line((3, 12), (6, 9), 1.3, 'leather')
    s.dot(2, 13, 'brass')
    if t >= 2:
        s.dot(6, 9, 'topaz', 3)
        glow_trail(s, [(13, 2), (14, 4)], 'sun')
    return s.render()


@sprite('mirrorglass_chakram', 2)
def chakram(t):
    s = Sprite()
    s.ellipse(8, 8, 6, 6, 'silver')
    s.erase([(x, y) for x in range(16) for y in range(16) if (x + 0.5 - 8) ** 2 + (y + 0.5 - 8) ** 2 < 10])
    # Six blades swept the same way round.
    for a in range(6):
        ang = a / 6 * 2 * math.pi
        x0, y0 = 8 + math.cos(ang) * 5.5, 8 + math.sin(ang) * 5.5
        x1, y1 = 8 + math.cos(ang + 0.45) * 7.6, 8 + math.sin(ang + 0.45) * 7.6
        s.line((int(x0), int(y0)), (int(round(x1)), int(round(y1))), 1.0, 'silver')
    s.dots([(5, 4), (4, 5)], 'shine')
    s.dots([(8, 3), (13, 8)], 'glass', 4)
    if t >= 2:
        s.dots([(3, 8), (8, 13)], 'aqua', 3)
    return s.render()


@sprite('starweavers_wand', 3)
def wand(t):
    s = Sprite()
    s.line((2, 13), (10, 5), 1.0, 'darkwood' if t == 1 else 'moonsilver')
    s.dots([(4, 11), (6, 9)], 'gold')
    star = [(12, 1), (11, 3), (13, 3), (10, 4), (14, 4), (12, 4), (12, 5), (11, 5), (13, 5), (12, 6)]
    s.dots(star, 'star' if t >= 2 else 'topaz')
    s.dot(12, 4, 'shine')
    if t >= 3:
        glow_trail(s, [(9, 1), (15, 2), (15, 7), (9, 7)], 'arcane')
    return s.render(glow_halo=t >= 2)


@sprite('ashen_warbanner', 2)
def banner(t):
    s = Sprite()
    s.line((1, 14), (12, 3), 1.1, 'darkwood')
    s.dot(13, 2, 'gold', 4)
    s.poly([(8, 3), (13, 3), (13, 10), (11, 8), (8, 10)], 'cloth_red' if t < 2 else 'crimson')
    s.dots([(10, 5), (11, 6), (10, 6)], 'gold')
    return s.render()


@sprite('sporelash_whip', 2)
def whip(t):
    s = Sprite()
    s.line((1, 14), (4, 11), 1.4, 'sporestem')
    loops = [(5, 10), (7, 9), (9, 9), (11, 10), (12, 12), (11, 13), (9, 13), (8, 12), (8, 10), (9, 8), (11, 7), (13, 6), (14, 4), (13, 3)]
    s.dots(loops, 'moss_cloth')
    s.dots([(13, 3), (12, 2)], 'spore', 3)
    if t >= 2:
        s.dots([(14, 2), (10, 6)], 'spore', 4)
    return s.render(glow_halo=True)


@sprite('brassmere_repeater', 2)
def repeater(t):
    s = Sprite()
    s.rect(4, 5, 14, 7, 'brass')
    s.rect(5, 4, 13, 4, 'darksteel')
    s.poly([(4, 7), (8, 7), (6, 13), (2, 13)], 'darkwood')
    s.ellipse(9, 7.5, 1.8, 1.8, 'darksteel')
    s.dots([(14, 5), (14, 6)], 'iron')
    s.dot(6, 8, 'darksteel')
    if t >= 2:
        s.dot(9, 7, 'ruby', 3)
        s.dots([(3, 12), (5, 10)], 'gold')
    return s.render()


@sprite('skyhook_harpoon', 2)
def harpoon(t):
    s = Sprite()
    s.line((1, 14), (11, 4), 1.2, 'palewood')
    s.poly([(10, 5), (14, 1), (15, 3), (12, 6)], 'iron')
    s.dots([(11, 3), (13, 6)], 'iron')
    rope = [(3, 9), (2, 8), (1, 7), (1, 6), (2, 5), (3, 5)]
    s.dots(rope, 'rope')
    if t >= 2:
        s.dot(12, 4, 'aqua', 3)
    return s.render()


@sprite('thunderdrum_gauntlets', 2)
def gauntlets(t):
    s = Sprite()
    # A clenched armoured fist, knuckles up, cuff below.
    s.poly([(3, 6), (12, 5), (13, 9), (12, 11), (4, 11), (3, 9)], 'steel')
    for x in (4, 6, 8, 10):
        s.rect(x, 3, x + 1, 5, 'darksteel')
        s.dot(x, 3, 'brass', 4)
    s.rect(12, 7, 13, 10, 'darksteel')
    s.rect(4, 11, 11, 14, 'leather')
    s.rect(4, 11, 11, 11, 'brass')
    s.line((4, 8), (11, 8), 0.4, 'darksteel')
    s.dot(8, 13, 'sapphire' if t >= 2 else 'iron', 3)
    if t >= 2:
        glow_trail(s, [(14, 4), (15, 8), (2, 3), (1, 7)], 'frost')
    return s.render(glow_halo=t >= 2)


# The great powers' weapons.
@sprite('bellhammer')
def bellhammer():
    s = Sprite()
    s.line((1, 14), (8, 7), 1.5, 'darkwood')
    s.dots([(3, 12), (5, 10)], 'rope')
    # The head is a little bell turned on its side.
    s.poly([(7, 7), (9, 3), (12, 1), (15, 2), (15, 5), (12, 8), (9, 9)], 'bronze')
    s.poly([(12, 1), (15, 2), (15, 5), (13, 4)], 'bronze')
    s.tone([(13, 2), (14, 3), (14, 4)], 1)
    s.dots([(9, 4), (10, 3)], 'shine')
    s.dot(15, 6, 'darksteel')
    s.dots([(11, 6), (10, 7)], 'gold', 3)
    return s.render()


@sprite('second_hand')
def second_hand():
    s = Sprite()
    s.line((4, 11), (14, 1), 1.4, 'gold')
    s.poly([(12, 1), (15, 0), (14, 3)], 'gold')
    s.ellipse(4, 11, 2.5, 2.5, 'darksteel')
    s.dot(4, 11, 'topaz', 3)
    s.line((1, 14), (3, 12), 1.2, 'darkleather')
    s.dots([(8, 6), (11, 3)], 'brass')
    return s.render()


@sprite('pilgrims_staff')
def pilgrims_staff():
    s = Sprite()
    s.line((1, 15), (11, 5), 1.3, 'palewood')
    s.ellipse(12.5, 3.5, 2.6, 2.6, 'stone')
    s.dot(12, 3, 'sun', 4)
    s.dots([(10, 7), (9, 8)], 'rope')
    glow_trail(s, [(15, 1), (14, 6), (10, 1)], 'sun')
    return s.render(glow_halo=True)


@sprite('apologetic_blade')
def apologetic_blade():
    s = sword('steel', 'silver', 'brass', 'cloth_blue', 'sapphire', 2)
    s.dots([(9, 4), (10, 6)], 'cloth_white')
    return s.render()


@sprite('hitodama')
def hitodama():
    s = Sprite()
    curve = [(4, 11), (5, 10), (6, 9), (7, 8), (8, 7), (9, 5), (10, 4), (11, 3), (12, 2), (13, 1)]
    for (x, y) in curve:
        s.dot(x, y, 'ashsteel')
        s.dot(x + 1, y, 'ashsteel')
    s.dots([(14, 0), (14, 1)], 'silver')
    s.ellipse(4, 11.5, 1.8, 1.8, 'gold')
    s.line((1, 14), (3, 12), 1.3, 'cloth_white')
    s.dots([(2, 13)], 'darkleather')
    # The ghost lantern charm.
    s.dots([(1, 9), (1, 10)], 'paperlantern')
    s.dot(1, 9, 'spirit', 3)
    glow_trail(s, [(15, 2), (12, 4), (8, 9)], 'spirit')
    return s.render(glow_halo=True)


@sprite('crimson_requiem')
def scythe():
    s = Sprite()
    s.line((2, 15), (9, 2), 1.2, 'darkwood')
    blade = [(9, 2), (10, 1), (11, 1), (12, 1), (13, 2), (14, 3), (14, 4), (15, 6), (15, 7)]
    for (x, y) in blade:
        s.dot(x, y, 'lichbone')
        s.dot(x, y + 1, 'lichbone')
    s.dots([(11, 2), (12, 2), (13, 3)], 'crimson', 3)
    s.dot(8, 4, 'ruby', 3)
    glow_trail(s, [(15, 8), (10, 4)], 'blood')
    return s.render(glow_halo=True)


@sprite('rimeback_cleaver')
def cleaver():
    s = Sprite()
    s.line((1, 14), (8, 7), 1.6, 'darkwood')
    s.poly([(6, 6), (9, 1), (15, 1), (15, 7), (10, 9)], 'rimeblade')
    s.dots([(10, 2), (12, 2), (14, 2)], 'shine')
    s.dots([(7, 8), (8, 9)], 'rimefur')
    s.dot(11, 6, 'aqua', 3)
    return s.render()


@sprite('dunewalkers_chain')
def chainblade():
    s = Sprite()
    s.line((6, 9), (13, 2), 2.0, 'sandsteel')
    s.dot(13, 2, 'shine')
    links = [(5, 10), (4, 11), (4, 13), (3, 14), (2, 13), (1, 12), (1, 10), (2, 9)]
    s.dots(links, 'iron')
    s.dot(8, 7, 'topaz', 3)
    return s.render()


@sprite('glyphwarden_aegis')
def aegis():
    s = Sprite()
    s.poly([(2, 2), (14, 2), (14, 8), (8, 15), (2, 8)], 'darkstone')
    s.poly([(3, 3), (13, 3), (13, 8), (8, 13), (3, 8)], 'stone')
    s.dots([(8, 4), (8, 5), (8, 6), (8, 7), (8, 8), (6, 6), (10, 6), (7, 9), (9, 9)], 'glyphgold')
    s.dot(8, 6, 'sun', 4)
    return s.render(glow_halo=True)


# ================================================================== armour (6 sets x 4)

SETS = {
    'wardens_regalia': ('teal_metal', 'gold', 'aqua'),
    'duskpine_ranger': ('moss_cloth', 'leather', 'emerald'),
    'emberforged': ('darksteel', 'copper', 'ember'),
    'rimeguard': ('rimefur', 'rimeblade', 'frost'),
    'starweave': ('starcloth', 'silver', 'arcane'),
    'gilded_glyphmail': ('glyphgold', 'darkstone', 'sun'),
}


def helmet(body, trim, gem):
    s = Sprite()
    s.poly([(3, 9), (3, 6), (5, 3), (8, 2), (11, 3), (13, 6), (13, 9)], body)
    s.rect(3, 9, 13, 10, trim)
    s.rect(5, 11, 6, 13, body)
    s.rect(10, 11, 11, 13, body)
    s.dots([(7, 7), (8, 7), (9, 7)], 'soot')
    s.dot(8, 4, gem, 3)
    return s


def chest(body, trim, gem):
    s = Sprite()
    s.poly([(2, 3), (6, 2), (8, 4), (10, 2), (14, 3), (13, 8), (12, 14), (4, 14), (3, 8)], body)
    s.rect(4, 13, 12, 14, trim)
    s.line((5, 3), (8, 6), 0.8, trim)
    s.line((11, 3), (8, 6), 0.8, trim)
    s.dot(8, 8, gem, 3)
    return s


def legs(body, trim, gem):
    s = Sprite()
    s.rect(4, 2, 12, 5, body)
    s.poly([(4, 5), (8, 5), (7, 14), (4, 14)], body)
    s.poly([(8, 5), (12, 5), (12, 14), (9, 14)], body)
    s.rect(4, 2, 12, 2, trim)
    s.dot(8, 3, gem, 3)
    s.dots([(5, 9), (11, 9)], trim)
    return s


def boots(body, trim, gem):
    s = Sprite()
    s.poly([(2, 4), (6, 4), (6, 11), (8, 13), (2, 13)], body)
    s.poly([(9, 4), (13, 4), (13, 11), (15, 13), (9, 13)], body)
    s.rect(2, 4, 6, 5, trim)
    s.rect(9, 4, 13, 5, trim)
    s.dots([(4, 8), (11, 8)], gem, 3)
    return s


for _set, (_b, _t, _g) in SETS.items():
    for _piece, _fn in (('helmet', helmet), ('chestplate', chest), ('leggings', legs), ('boots', boots)):
        SPRITES[f'{_set}_{_piece}'] = (lambda fn=_fn, b=_b, t=_t, g=_g: fn(b, t, g).render(glow_halo=PALETTE[g].glow))


# ================================================================== relics

def ring(band, gem):
    s = Sprite()
    s.ellipse(8, 9.5, 5, 4.5, band)
    s.erase([(x, y) for x in range(16) for y in range(16) if ((x + 0.5 - 8) / 2.8) ** 2 + ((y + 0.5 - 9.5) / 2.4) ** 2 < 1])
    s.ellipse(8, 4.5, 2.2, 2.2, gem)
    s.dot(7, 4, 'shine')
    return s


@sprite('ring_of_second_breath')
def r1():
    return ring('gold', 'emerald').render()


@sprite('warden_glyph_ring')
def r2():
    s = ring('glyphgold', 'aqua')
    s.dots([(4, 10), (12, 10)], 'sun', 4)
    return s.render(glow_halo=True)


def amulet(chain, stone, extra=None):
    s = Sprite()
    for i in range(6):
        s.dot(3 + i, 1 + i, chain)
        s.dot(12 - i, 1 + i, chain)
    s.ellipse(8, 10.5, 3.6, 4.2, 'gold')
    s.ellipse(8, 10.5, 2.4, 3.0, stone)
    s.dot(7, 9, 'shine')
    if extra:
        extra(s)
    return s


@sprite('amulet_of_quiet_steps')
def a1():
    return amulet('silver', 'darkstone', lambda s: s.dots([(8, 10), (8, 11)], 'cloth_white')).render()


@sprite('emberheart_pendant')
def a2():
    return amulet('gold', 'ember').render(glow_halo=True)


@sprite('mothlantern_locket')
def a3():
    return amulet('brass', 'sun', lambda s: s.dots([(6, 10), (10, 10)], 'paperlantern')).render(glow_halo=True)


@sprite('rimecore_charm')
def c1():
    s = Sprite()
    s.poly([(8, 1), (12, 5), (12, 11), (8, 15), (4, 11), (4, 5)], 'rimeblade')
    s.poly([(8, 4), (10, 7), (8, 12), (6, 7)], 'frost')
    s.dot(7, 5, 'shine')
    return s.render(glow_halo=True)


@sprite('glimmerfin_scale')
def c2():
    s = Sprite()
    s.poly([(8, 2), (13, 6), (12, 12), (8, 14), (4, 12), (3, 6)], 'aqua')
    for y in (6, 9, 12):
        for x in range(4, 13):
            if s.cells[y][x] and (x + y) % 2 == 0:
                s.dot(x, y, 'sapphire', 2)
    s.dots([(6, 4), (7, 3)], 'shine')
    return s.render()


@sprite('pilgrims_bead')
def c3():
    s = Sprite()
    beads = [(4, 3), (6, 2), (8, 2), (10, 2), (12, 3), (13, 5), (12, 7), (4, 7), (3, 5)]
    for (x, y) in beads:
        s.ellipse(x + 0.5, y + 0.5, 1.2, 1.2, 'palewood')
    s.line((8, 8), (8, 12), 0.8, 'rope')
    s.ellipse(8, 13, 1.8, 1.8, 'sun')
    return s.render(glow_halo=True)


@sprite('lucky_sextant')
def c4():
    s = Sprite()
    s.poly([(2, 12), (8, 2), (14, 12)], 'brass')
    s.poly([(4, 11), (8, 5), (12, 11)], None or 'darkwood')
    s.line((8, 3), (8, 12), 0.7, 'brass')
    s.ellipse(8, 12, 6, 1.5, 'brass')
    s.dot(8, 7, 'glass', 4)
    return s.render()


@sprite('bram_purse_knot')
def c5():
    s = Sprite()
    s.ellipse(8, 9, 4.5, 4.5, 'rope')
    s.ellipse(8, 9, 2, 2, 'leather')
    s.line((4, 13), (2, 15), 0.8, 'rope')
    s.line((12, 13), (14, 15), 0.8, 'rope')
    s.dots([(8, 3), (7, 4), (9, 4)], 'gold', 4)
    return s.render()


@sprite('gearsprung_anklet')
def c6():
    s = Sprite()
    s.ellipse(8, 8, 6, 3.5, 'brass')
    s.erase([(x, y) for x in range(16) for y in range(16) if ((x + 0.5 - 8) / 4.2) ** 2 + ((y + 0.5 - 8) / 1.8) ** 2 < 1])
    for x in (3, 6, 9, 12):
        s.dot(x, 4, 'brass', 4)
    s.dot(8, 11, 'aqua', 3)
    return s.render()


@sprite('echo_shell')
def c7():
    s = Sprite()
    s.ellipse(8, 9, 6, 5, 'pearl')
    spiral = [(8, 9), (9, 8), (10, 9), (10, 10), (9, 11), (7, 11), (6, 10), (6, 8), (7, 6), (9, 6), (11, 7)]
    s.dots(spiral, 'echoblade')
    s.dot(5, 6, 'shine')
    return s.render()


@sprite('lich_finger_bone')
def c8():
    s = Sprite()
    s.line((3, 13), (11, 5), 1.6, 'lichbone')
    s.dots([(6, 10), (9, 7)], 'bone', 1)
    s.ellipse(12, 4, 2, 2, 'lichbone')
    s.dot(12, 4, 'ruby', 3)
    s.ellipse(5, 11, 3.5, 1.2, 'gold')
    return s.render()


@sprite('shogun_tsuba')
def c9():
    s = Sprite()
    s.ellipse(8, 8, 6.5, 6.5, 'ashsteel')
    s.ellipse(8, 8, 2, 3, 'soot')
    for (x, y) in [(4, 4), (11, 4), (4, 11), (11, 11)]:
        s.dot(x, y, 'gold', 4)
    s.dots([(8, 2), (8, 13)], 'spirit', 3)
    return s.render(glow_halo=True)


@sprite('sentinel_sandglass')
def c10():
    s = Sprite()
    s.rect(4, 1, 11, 2, 'sandsteel')
    s.rect(4, 13, 11, 14, 'sandsteel')
    s.poly([(5, 3), (10, 3), (8, 8), (7, 8)], 'glass')
    s.poly([(7, 8), (8, 8), (10, 12), (5, 12)], 'glass')
    s.poly([(6, 10), (9, 10), (10, 12), (5, 12)], 'sandstone')
    s.dots([(7, 6), (7, 7), (7, 9)], 'sandstone')
    return s.render()


@sprite('starseed')
def c11():
    s = Sprite()
    s.ellipse(8, 9, 4, 5, 'darkwood')
    s.ellipse(8, 9, 2.4, 3.2, 'star')
    s.dots([(8, 3), (7, 2), (9, 2), (8, 1)], 'moss')
    return s.render(glow_halo=True)


@sprite('caravan_seal')
def c12():
    s = Sprite()
    s.ellipse(8, 8, 6, 6, 'crimson')
    s.ellipse(8, 8, 4, 4, 'cloth_red')
    s.dots([(7, 6), (8, 6), (9, 7), (8, 8), (7, 9), (8, 10), (9, 10)], 'gold')
    s.dots([(3, 12), (2, 14), (13, 12), (14, 14)], 'crimson')
    return s.render()


# Existing relics, redrawn.
def r_compass():
    s = Sprite()
    s.ellipse(8, 8, 6.5, 6.5, 'brass')
    s.ellipse(8, 8, 5, 5, 'paper')
    s.line((8, 8), (11, 4), 0.8, 'cloth_red')
    s.line((8, 8), (5, 12), 0.8, 'darksteel')
    s.erase([(12, 9), (13, 10), (12, 11)])
    s.dot(8, 8, 'gold', 4)
    return s.render()


def r_lantern():
    s = Sprite()
    s.rect(7, 1, 8, 2, 'darksteel')
    s.rect(4, 3, 11, 4, 'darksteel')
    s.rect(4, 13, 11, 14, 'darksteel')
    s.rect(5, 5, 10, 12, 'glass')
    s.ellipse(7.5, 9, 2, 2.6, 'spirit')
    s.dots([(4, 5), (11, 5), (4, 12), (11, 12)], 'darksteel')
    s.dots([(6, 7), (9, 7)], 'soot')
    return s.render(glow_halo=True)


@sprite('clockwork_heart')
def r_heart():
    s = Sprite()
    s.ellipse(5.5, 6, 3.5, 3.5, 'copper')
    s.ellipse(10.5, 6, 3.5, 3.5, 'copper')
    s.poly([(2, 7), (14, 7), (8, 14)], 'copper')
    s.ellipse(8, 8, 2.2, 2.2, 'brass')
    s.dot(8, 8, 'ruby', 3)
    s.dots([(4, 4), (5, 3)], 'shine')
    return s.render()


@sprite('thimble_of_borrowed_rain')
def r_thimble():
    s = Sprite()
    s.poly([(4, 13), (5, 5), (8, 3), (11, 5), (12, 13)], 'silver')
    for y in range(6, 13, 2):
        for x in range(5, 12, 2):
            if s.cells[y][x]:
                s.dot(x, y, 'iron', 1)
    s.dots([(8, 0), (7, 1), (9, 1)], 'frost', 3)
    return s.render(glow_halo=True)


# ================================================================== gifts and gadgets

@sprite('moth_silk_glider')
def glider():
    s = Sprite()
    s.poly([(8, 7), (1, 2), (0, 7), (3, 11)], 'cloth_white')
    s.poly([(8, 7), (15, 2), (16, 7), (13, 11)], 'cloth_white')
    s.dots([(3, 5), (12, 5)], 'frost', 3)
    s.dots([(2, 8), (13, 8)], 'paperlantern')
    s.line((8, 4), (8, 13), 0.8, 'darkwood')
    s.dot(8, 5, 'moonsilver', 4)
    return s.render()


@sprite('prototype_wing')
def proto_wing():
    s = Sprite()
    s.poly([(2, 12), (6, 3), (14, 1), (11, 7), (5, 13)], 'brass')
    s.poly([(4, 11), (7, 5), (12, 3), (10, 7)], 'cloth_white')
    s.dots([(6, 9), (9, 6)], 'darksteel')
    return s.render()


@sprite('wayfarers_hook')
def grapple():
    s = Sprite()
    s.line((1, 14), (6, 9), 1.6, 'darkleather')
    s.rect(5, 8, 8, 10, 'brass')
    s.line((8, 8), (12, 4), 1.0, 'iron')
    s.dots([(12, 2), (13, 3), (14, 4), (11, 3), (13, 5), (10, 4)], 'darksteel')
    s.dots([(12, 1), (15, 4)], 'darksteel')
    s.dot(6, 9, 'topaz', 3)
    return s.render()


@sprite('featherstep_boots')
def featherboots():
    s = boots('moonsilver', 'starcloth', 'star')
    s.dots([(0, 4), (1, 3), (1, 5), (14, 3), (15, 4), (15, 2)], 'cloth_white')
    return s.render(glow_halo=True)


@sprite('climbing_claws')
def claws():
    s = Sprite()
    # A leather bracer with three curved steel hooks.
    s.poly([(3, 8), (11, 7), (12, 13), (4, 14)], 'darkleather')
    s.rect(3, 12, 12, 13, 'rimefur')
    for x in (4, 7, 10):
        s.dots([(x, 7), (x + 1, 6), (x + 1, 5), (x + 2, 4), (x + 2, 3)], 'steel')
        s.dot(x + 3, 3, 'shine')
    s.dots([(6, 9), (9, 9)], 'brass')
    return s.render()


@sprite('mirewalk_charm')
def mirewalk():
    s = Sprite()
    s.ellipse(8, 9, 6, 4.5, 'moss')
    s.erase([(8, 9), (9, 8), (10, 7)])
    s.ellipse(8, 9, 2, 1.6, 'aqua')
    s.line((8, 2), (8, 5), 0.8, 'rope')
    s.dot(8, 1, 'spore', 3)
    return s.render(glow_halo=True)


@sprite('skyward_bell')
def skybell():
    s = Sprite()
    s.poly([(8, 2), (11, 4), (12, 10), (13, 12), (3, 12), (4, 10), (5, 4)], 'silver')
    s.rect(3, 12, 13, 13, 'gold')
    s.dot(8, 14, 'gold')
    s.dots([(6, 5), (6, 6)], 'shine')
    s.dots([(1, 3), (14, 2), (15, 6)], 'star', 3)
    return s.render(glow_halo=True)


@sprite('wayfarers_whistle')
def whistle():
    s = Sprite()
    s.line((3, 12), (11, 4), 2.2, 'bone')
    s.dot(5, 10, 'soot')
    s.dot(8, 7, 'soot')
    s.line((11, 4), (14, 1), 0.8, 'rope')
    s.dot(12, 6, 'emerald', 3)
    return s.render()


@sprite('survey_sextant')
def sextant():
    s = Sprite()
    s.poly([(2, 13), (8, 2), (14, 13)], 'brass')
    s.erase([(x, y) for x in range(5, 12) for y in range(7, 12) if abs(x - 8) < (y - 4) * 0.55])
    s.line((8, 3), (5, 13), 0.7, 'darksteel')
    s.ellipse(8, 13, 6, 1.4, 'brass')
    s.dot(8, 3, 'glass', 4)
    s.dot(5, 12, 'ruby', 3)
    return s.render()


# ================================================================== consumables

def bottle(liquid, shape='round', cork='darkwood'):
    s = Sprite()
    if shape == 'round':
        s.rect(7, 2, 8, 4, 'glass')
        s.rect(6, 1, 9, 1, cork)
        s.ellipse(7.5, 10, 4.5, 4.6, 'glass')
        s.ellipse(7.5, 11, 3.5, 3.0, liquid)
        s.dot(5, 8, 'shine')
    elif shape == 'tall':
        s.rect(6, 1, 9, 2, cork)
        s.rect(6, 3, 9, 13, 'glass')
        s.rect(7, 6, 8, 12, liquid)
        s.dot(6, 4, 'shine')
    else:   # flask
        s.rect(7, 1, 8, 4, 'glass')
        s.rect(6, 0, 9, 0, cork)
        s.poly([(6, 5), (9, 5), (13, 14), (2, 14)], 'glass')
        s.poly([(5, 10), (10, 10), (12, 13), (3, 13)], liquid)
        s.dot(6, 6, 'shine')
    return s


@sprite('elixir_of_stamina')
def p1():
    return bottle('spore', 'round').render(glow_halo=True)


@sprite('draught_of_resonance')
def p2():
    return bottle('arcane', 'tall', 'gold').render(glow_halo=True)


@sprite('phial_of_second_wind')
def p3():
    return bottle('ruby', 'flask').render()


def oil(color):
    s = bottle(color, 'flask', 'rope')
    s.dots([(13, 10), (13, 12), (14, 14)], color)
    return s


@sprite('ember_oil')
def o1():
    return oil('ember').render(glow_halo=True)


@sprite('frostbite_oil')
def o2():
    return oil('frost').render(glow_halo=True)


@sprite('venom_oil')
def o3():
    return oil('spore').render(glow_halo=True)


def bomb(body, fuse_spark, band):
    s = Sprite()
    s.ellipse(8, 9.5, 5.5, 5.5, body)
    s.rect(7, 3, 9, 4, band)
    s.line((9, 3), (11, 1), 0.6, 'rope')
    s.dot(12, 0, fuse_spark, 4)
    s.dots([(6, 7), (5, 8)], 'shine')
    return s


@sprite('smoke_bomb')
def b1():
    s = bomb('soot', 'ember', 'iron')
    s.dots([(1, 2), (2, 1), (3, 2)], 'cloth_white')
    return s.render()


@sprite('frost_bomb')
def b2():
    return bomb('rimeblade', 'frost', 'silver').render(glow_halo=True)


@sprite('thunder_bomb')
def b3():
    s = bomb('darksteel', 'sun', 'brass')
    s.dots([(8, 9), (9, 10), (8, 11), (9, 12)], 'sun', 3)
    return s.render(glow_halo=True)


@sprite('waystone_shard')
def ws():
    s = Sprite()
    s.poly([(8, 1), (12, 6), (10, 14), (6, 14), (4, 6)], 'darkstone')
    s.dots([(8, 5), (7, 7), (9, 7), (8, 9), (8, 11)], 'aqua', 3)
    s.dot(7, 3, 'shine')
    return s.render(glow_halo=True)


def scroll(paper, ribbon, mark=None):
    s = Sprite()
    s.rect(3, 4, 12, 11, paper)
    s.ellipse(3, 7.5, 1.6, 4, paper)
    s.ellipse(12.5, 7.5, 1.6, 4, paper)
    s.rect(7, 4, 8, 11, ribbon)
    if mark:
        s.dots(mark, 'ink')
    return s


@sprite('survey_scroll')
def sc1():
    return scroll('parchment', 'cloth_blue', [(5, 6), (6, 8), (10, 6), (10, 9)]).render()


@sprite('lockpick')
def lockpick():
    s = Sprite()
    s.line((2, 13), (12, 3), 0.7, 'iron')
    s.dots([(12, 2), (13, 2), (13, 3)], 'iron')
    s.line((1, 14), (4, 11), 1.4, 'darkleather')
    s.line((5, 13), (13, 5), 0.6, 'steel')
    return s.render()


@sprite('hearty_stew')
def f1():
    s = Sprite()
    s.ellipse(8, 9, 6.5, 3, 'wood')
    s.poly([(2, 9), (14, 9), (12, 13), (4, 13)], 'wood')
    s.ellipse(8, 8.5, 5.5, 2.2, 'flesh')
    s.dots([(6, 8), (9, 8), (10, 9)], 'copper')
    s.dots([(7, 9), (11, 8)], 'moss')
    s.dots([(6, 4), (8, 3), (10, 4)], 'cloth_white', 2)
    return s.render()


@sprite('moonberry_tart')
def f2():
    s = Sprite()
    s.poly([(2, 12), (14, 12), (13, 7), (3, 7)], 'palewood')
    s.ellipse(8, 7, 5.5, 2, 'violet')
    s.dots([(5, 6), (8, 6), (11, 6), (7, 7)], 'arcane', 3)
    s.dot(9, 5, 'shine')
    return s.render()


@sprite('spiced_kebab')
def f3():
    s = Sprite()
    s.line((1, 15), (14, 2), 0.6, 'palewood')
    for (x, y, m) in [(4, 11, 'flesh'), (6, 9, 'copper'), (8, 7, 'flesh'), (10, 5, 'moss'), (12, 3, 'flesh')]:
        s.ellipse(x + 0.5, y + 0.5, 1.8, 1.8, m)
    return s.render()


@sprite('glimmerfin_sushi')
def f4():
    s = Sprite()
    s.ellipse(8, 9, 5.5, 4.5, 'soot')
    s.ellipse(8, 9, 4, 3.2, 'cloth_white')
    s.ellipse(8, 9, 2, 1.6, 'aqua')
    s.dot(7, 8, 'shine')
    return s.render()


@sprite('ember_pepper')
def f5():
    s = Sprite()
    s.poly([(5, 4), (9, 3), (11, 6), (10, 12), (7, 15), (6, 11)], 'emberblade')
    s.line((7, 3), (7, 0), 0.8, 'moss')
    s.dot(6, 6, 'shine')
    s.dots([(12, 4), (13, 7)], 'ember', 3)
    return s.render(glow_halo=True)


@sprite('starlight_tea')
def f6():
    s = Sprite()
    s.poly([(3, 7), (12, 7), (11, 13), (4, 13)], 'pearl')
    s.ellipse(7.5, 7, 4.5, 1.4, 'arcane')
    s.rect(12, 8, 14, 11, 'pearl')
    s.erase([(13, 9), (13, 10)])
    s.dots([(6, 4), (8, 3), (7, 2)], 'star', 3)
    return s.render(glow_halo=True)


# ================================================================== materials

@sprite('raw_moonsilver')
def m1():
    s = Sprite()
    s.poly([(3, 10), (5, 5), (10, 3), (13, 7), (12, 12), (6, 13)], 'darkstone')
    s.dots([(6, 7), (7, 6), (9, 8), (10, 7), (8, 10)], 'moonsilver', 3)
    s.dot(9, 5, 'shine')
    return s.render()


@sprite('moonsilver_ingot')
def m2():
    s = Sprite()
    s.poly([(1, 10), (5, 6), (15, 6), (11, 10)], 'moonsilver')
    s.poly([(1, 10), (11, 10), (11, 12), (1, 12)], 'moonsilver')
    s.poly([(11, 10), (15, 6), (15, 8), (11, 12)], 'moonsilver')
    s.tone([(x, 10) for x in range(1, 11)], 3)
    s.tone([(x, 11) for x in range(1, 11)], 1)
    s.dots([(6, 7), (7, 7)], 'shine')
    return s.render()


@sprite('glimmer_silk')
def m3():
    s = Sprite()
    s.ellipse(8, 8, 5.5, 5.5, 'pearl')
    for (x, y) in [(5, 5), (10, 6), (6, 10), (11, 10), (8, 3)]:
        s.dot(x, y, 'frost', 3)
    s.ellipse(8, 8, 1.5, 1.5, 'palewood')
    return s.render(glow_halo=True)


@sprite('glyph_gold')
def m4():
    s = Sprite()
    s.poly([(3, 8), (6, 3), (11, 4), (13, 9), (9, 13), (4, 12)], 'glyphgold')
    s.dots([(7, 6), (8, 7), (9, 8), (7, 9)], 'sun', 4)
    s.dot(6, 5, 'shine')
    return s.render(glow_halo=True)


@sprite('rime_pearl')
def m5():
    s = Sprite()
    s.ellipse(8, 8, 4.5, 4.5, 'pearl')
    s.dots([(6, 6), (7, 5)], 'shine')
    s.dots([(11, 11), (10, 12)], 'frost', 2)
    return s.render(glow_halo=True)


@sprite('sandsteel_ingot')
def m6():
    s = Sprite()
    s.poly([(1, 10), (5, 6), (15, 6), (11, 10)], 'sandsteel')
    s.poly([(1, 10), (11, 10), (11, 12), (1, 12)], 'sandsteel')
    s.poly([(11, 10), (15, 6), (15, 8), (11, 12)], 'sandsteel')
    s.tone([(x, 11) for x in range(1, 11)], 1)
    s.dots([(5, 7), (6, 7)], 'shine')
    return s.render()


@sprite('lich_bone')
def m7():
    s = Sprite()
    s.line((3, 12), (12, 3), 1.8, 'lichbone')
    s.ellipse(3, 12.5, 1.8, 1.8, 'lichbone')
    s.ellipse(12.5, 3, 1.8, 1.8, 'lichbone')
    s.dots([(7, 8), (8, 7)], 'blood', 3)
    return s.render(glow_halo=True)


@sprite('shogun_steel')
def m8():
    s = Sprite()
    s.poly([(2, 13), (11, 3), (14, 2), (13, 5), (4, 14)], 'ashsteel')
    s.line((4, 12), (12, 4), 0.6, 'silver')
    s.dots([(14, 1), (12, 2)], 'spirit', 3)
    return s.render(glow_halo=True)


@sprite('edict_shard')
def m9():
    s = Sprite()
    s.poly([(8, 1), (11, 6), (9, 14), (6, 14), (5, 6)], 'arcane')
    s.poly([(8, 3), (9, 6), (8, 11), (7, 6)], 'star')
    s.dots([(3, 3), (13, 5), (12, 12)], 'arcane', 2)
    return s.render(glow_halo=True)


@sprite('star_fragment')
def m10():
    s = Sprite()
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = 6.5 if i % 2 == 0 else 2.8
        pts.append((8 + math.cos(a) * r, 8.5 + math.sin(a) * r))
    s.poly(pts, 'star')
    s.dot(8, 8, 'shine')
    return s.render(glow_halo=True)


# Existing materials, redrawn.
@sprite('ember_core')
def e1():
    s = Sprite()
    s.ellipse(8, 8.5, 5, 5, 'soot')
    s.ellipse(8, 8.5, 3, 3, 'ember')
    s.dots([(4, 5), (11, 4), (12, 11), (4, 12)], 'emberblade')
    s.dot(7, 7, 'sun', 4)
    return s.render(glow_halo=True)


@sprite('living_crystal')
def e2():
    s = Sprite()
    s.poly([(5, 14), (4, 6), (7, 2), (9, 6), (8, 14)], 'crystal')
    s.poly([(8, 14), (10, 5), (13, 4), (12, 14)], 'aqua')
    s.dots([(6, 4), (11, 6)], 'shine')
    return s.render(glow_halo=True)


@sprite('lurker_fang')
def e3():
    s = Sprite()
    s.poly([(4, 2), (10, 2), (12, 6), (8, 14), (6, 10)], 'bone')
    s.tone([(5, 3), (6, 4), (6, 5)], 4)
    s.dots([(5, 2), (6, 2), (7, 2), (8, 2), (9, 2)], 'moss', 2)
    return s.render()


@sprite('echo_essence')
def e4():
    s = Sprite()
    s.ellipse(8, 8, 4.5, 4.5, 'echoblade')
    s.ellipse(8, 8, 2.4, 2.4, 'arcane')
    s.dots([(2, 3), (13, 4), (3, 12), (12, 13)], 'arcane', 2)
    s.dot(7, 7, 'shine')
    return s.render(glow_halo=True)


@sprite('gear_spring')
def e5():
    s = Sprite()
    coil = [(4, 12), (11, 12), (11, 10), (4, 10), (4, 8), (11, 8), (11, 6), (4, 6), (4, 4), (11, 4)]
    for a, b in zip(coil, coil[1:]):
        s.line(a, b, 1.0, 'brass')
    s.dots([(12, 4), (13, 3)], 'darksteel')
    return s.render()


@sprite('rime_core')
def e6():
    s = Sprite()
    s.poly([(8, 1), (13, 5), (13, 11), (8, 15), (3, 11), (3, 5)], 'rimeblade')
    s.ellipse(8, 8, 2.5, 2.5, 'frost')
    s.dot(6, 4, 'shine')
    return s.render(glow_halo=True)


@sprite('anomaly_residue')
def e7():
    s = Sprite()
    s.ellipse(8, 9, 5.5, 4, 'violet')
    s.dots([(6, 8), (9, 10), (11, 8), (7, 11)], 'arcane', 4)
    s.dots([(3, 3), (12, 2), (14, 6)], 'arcane', 2)
    return s.render(glow_halo=True)


@sprite('clockwork_fragment')
def e8():
    s = Sprite()
    s.poly([(2, 9), (8, 3), (14, 9)], 'brass')
    s.ellipse(8, 8, 2, 2, 'darksteel')
    for x in (3, 6, 10, 13):
        s.dot(x, 10, 'brass')
    s.dot(8, 8, 'topaz', 3)
    return s.render()


@sprite('moth_dust')
def e9():
    s = Sprite()
    s.ellipse(8, 10, 5, 3.5, 'paperlantern')
    for (x, y) in [(4, 5), (7, 3), (11, 4), (13, 7), (2, 8)]:
        s.dot(x, y, 'sun', 3)
    return s.render(glow_halo=True)


@sprite('grave_wax')
def e10():
    s = Sprite()
    s.poly([(4, 13), (5, 6), (7, 4), (10, 5), (12, 13)], 'cloth_white')
    s.dots([(5, 8), (5, 9), (11, 9), (11, 10)], 'bone', 1)
    s.line((8, 4), (8, 1), 0.6, 'soot')
    return s.render()


@sprite('bell_fragment')
def e11():
    s = Sprite()
    s.poly([(3, 12), (5, 4), (11, 2), (13, 10), (8, 14)], 'bronze')
    s.dots([(6, 5), (7, 4)], 'shine')
    s.dots([(9, 8), (10, 10)], 'copper', 1)
    return s.render()


# ================================================================== quest things and lore

@sprite('star_chart_fragment')
def q1():
    s = Sprite()
    s.poly([(2, 3), (13, 2), (14, 9), (10, 14), (3, 12)], 'starcloth')
    for (x, y) in [(5, 5), (8, 4), (10, 7), (6, 9), (11, 11)]:
        s.dot(x, y, 'star', 4)
    s.line((5, 5), (8, 4), 0.3, 'silver')
    return s.render(glow_halo=True)


@sprite('glyph_tablet')
def q2():
    s = Sprite()
    s.rect(3, 2, 12, 14, 'stone')
    s.rect(4, 3, 11, 13, 'darkstone')
    s.dots([(7, 5), (8, 5), (7, 6), (6, 7), (9, 7), (7, 9), (8, 9), (8, 10), (6, 11), (9, 11)], 'glyphgold')
    return s.render(glow_halo=True)


@sprite('phylactery_shard')
def q3():
    s = Sprite()
    s.poly([(7, 1), (11, 5), (10, 13), (6, 14), (4, 6)], 'ruby')
    s.dots([(7, 4), (7, 5)], 'shine')
    s.dots([(8, 8), (8, 9)], 'blood', 4)
    return s.render(glow_halo=True)


@sprite('challenge_lantern')
def q4():
    s = Sprite()
    s.line((8, 0), (8, 2), 0.6, 'darkwood')
    s.ellipse(8, 8, 4.5, 5.5, 'paperlantern')
    s.rect(5, 2, 10, 3, 'darkwood')
    s.rect(5, 13, 10, 14, 'darkwood')
    s.dots([(8, 6), (8, 7), (8, 8), (7, 9), (9, 9)], 'cloth_red')
    s.ellipse(8, 8, 1.6, 2, 'spirit')
    return s.render(glow_halo=True)


@sprite('sun_dial_key')
def q5():
    s = Sprite()
    s.ellipse(5.5, 5.5, 4, 4, 'gold')
    s.ellipse(5.5, 5.5, 1.6, 1.6, 'sun')
    s.line((8, 8), (14, 14), 1.2, 'gold')
    s.dots([(12, 14), (14, 12), (11, 13)], 'gold')
    return s.render(glow_halo=True)


@sprite('crypt_key')
def q6():
    s = Sprite()
    s.ellipse(5, 5, 3.5, 3.5, 'iron')
    s.erase([(5, 5), (4, 5), (5, 4)])
    s.line((7, 7), (14, 14), 1.0, 'iron')
    s.dots([(12, 14), (11, 13), (13, 12)], 'iron')
    s.dot(4, 3, 'blood', 3)
    return s.render()


@sprite('bounty_writ')
def q7():
    s = scroll('paper', 'cloth_red', [(5, 6), (6, 6), (9, 6), (5, 9), (10, 9)])
    s.ellipse(8, 12, 1.8, 1.8, 'crimson')
    return s.render()


@sprite('bell_rope')
def q8():
    s = Sprite()
    s.ellipse(7, 8, 5.5, 5.5, 'rope')
    s.ellipse(7, 8, 3, 3, 'rope')
    s.erase([(x, y) for x in range(16) for y in range(16) if (x + 0.5 - 7) ** 2 + (y + 0.5 - 8) ** 2 < 2.5])
    s.line((11, 11), (14, 15), 1, 'cloth_red')
    s.dot(14, 15, 'gold')
    return s.render()


@sprite('expedition_journal')
def journal():
    s = Sprite()
    s.rect(3, 2, 13, 14, 'leather')
    s.rect(4, 3, 12, 13, 'darkleather')
    s.rect(13, 3, 14, 13, 'paper')
    s.rect(2, 2, 3, 14, 'darkleather')
    s.dots([(7, 6), (8, 5), (9, 6), (8, 7), (8, 8)], 'gold')
    s.line((10, 14), (11, 15), 0.6, 'cloth_red')
    return s.render()


@sprite('chronicle_page')
def lore1():
    s = Sprite()
    s.poly([(3, 2), (12, 2), (13, 13), (4, 14)], 'parchment')
    for y in (5, 7, 9, 11):
        s.line((5, y), (11, y), 0.3, 'ink')
    s.dot(12, 3, 'gold', 3)
    return s.render()


@sprite('pilgrim_offering')
def lore2():
    s = Sprite()
    s.ellipse(8, 11, 5.5, 2.5, 'stone')
    s.ellipse(8, 8, 3, 3, 'sun')
    s.dots([(8, 3), (7, 4), (9, 4)], 'moss')
    return s.render(glow_halo=True)


@sprite('torn_map')
def lore3():
    s = Sprite()
    s.poly([(2, 3), (13, 2), (14, 8), (11, 9), (13, 13), (3, 13)], 'parchment')
    s.dots([(5, 5), (6, 6), (7, 6), (8, 7)], 'cloth_red')
    s.dots([(9, 10), (5, 11)], 'emerald')
    s.dot(10, 4, 'ink')
    return s.render()


@sprite('torn_census')
def lore4():
    s = Sprite()
    s.poly([(3, 2), (13, 2), (12, 14), (9, 12), (4, 14)], 'paper')
    for y in (4, 6, 8, 10):
        s.line((5, y), (11, y), 0.3, 'ink')
    s.dot(6, 6, 'cloth_red', 3)
    return s.render()


@sprite('ringers_ledger')
def lore5():
    s = Sprite()
    s.rect(3, 3, 13, 13, 'darkleather')
    s.rect(4, 4, 12, 12, 'crimson')
    s.dots([(8, 6), (7, 7), (9, 7), (6, 9), (10, 9), (8, 10)], 'bronze')
    return s.render()


@sprite('glimmerfin_bucket')
def bucket():
    s = Sprite()
    s.poly([(3, 5), (13, 5), (11, 14), (5, 14)], 'iron')
    s.ellipse(8, 5.5, 5, 1.5, 'aqua')
    s.dots([(7, 5), (8, 4), (9, 5), (10, 4)], 'frost', 4)
    s.line((3, 5), (8, 1), 0.5, 'darksteel')
    s.line((13, 5), (8, 1), 0.5, 'darksteel')
    return s.render(glow_halo=True)


# ================================================================== Aspect hearts

ASPECTS = {
    'heart_of_the_bell': ('bronze', 'spirit'),
    'heart_of_seconds': ('gold', 'topaz'),
    'heart_of_stone': ('sandstone', 'sun'),
    'heart_of_lanterns': ('ashsteel', 'spirit'),
    'heart_of_crimson': ('crimson', 'blood'),
    'heart_of_winter': ('rimeblade', 'frost'),
    'heart_of_the_dune': ('sandsteel', 'sun'),
    'heart_of_judgment': ('glyphgold', 'sun'),
}


def heart(shell, core):
    s = Sprite()
    s.ellipse(5.5, 6, 4, 4, shell)
    s.ellipse(10.5, 6, 4, 4, shell)
    s.poly([(1.5, 7), (14.5, 7), (8, 15)], shell)
    s.ellipse(8, 8, 3, 3, core)
    s.dot(8, 8, 'shine')
    s.dots([(4, 4), (5, 3)], 'shine')
    return s


for _id, (_sh, _co) in ASPECTS.items():
    SPRITES[_id] = (lambda sh=_sh, co=_co: heart(sh, co).render(glow_halo=True))


# ================================================================== the economy

def coins(metal, n, mark):
    """A small stack: each coin an oval face over a darker rim, the top one bright, offset a little."""
    s = Sprite()
    for i in range(n):
        y = 12 - i * 2
        x = 8 + (1 if i % 2 else 0)
        s.ellipse(x, y + 0.5, 5.5, 2.0, metal)
        s.tone([(xx, y + 1) for xx in range(x - 5, x + 5)], 0)
        s.tone([(xx, y) for xx in range(x - 4, x + 4)], 2)
    top = 12 - (n - 1) * 2
    tx = 8 + (1 if (n - 1) % 2 else 0)
    s.ellipse(tx, top - 0.3, 4.5, 1.6, metal)
    s.tone([(xx, top - 1) for xx in range(tx - 3, tx + 3)], 3)
    s.dot(tx, top - 1, mark, 4)
    s.dot(tx - 3, top - 1, 'shine')
    return s


@sprite('copper_mark')
def coin1():
    return coins('copper', 2, 'bronze').render()


@sprite('silver_mark')
def coin2():
    return coins('silver', 3, 'moonsilver').render()


@sprite('gold_crown')
def coin3():
    return coins('gold', 4, 'sun').render()


@sprite('lost_purse')
def purse():
    s = Sprite()
    s.ellipse(8, 10, 5.5, 4.5, 'leather')
    s.poly([(5, 6), (11, 6), (10, 3), (6, 3)], 'leather')
    s.rect(5, 5, 10, 5, 'rope')
    s.dots([(8, 9), (8, 10), (7, 10), (9, 10)], 'gold')
    s.dots([(3, 2), (13, 3), (2, 12)], 'spirit', 2)
    return s.render(glow_halo=True)


@sprite('spice_crate')
def g1():
    s = Sprite()
    s.rect(2, 4, 13, 13, 'palewood')
    for x in range(2, 14):
        s.dot(x, 8, 'wood', 1)
    s.rect(2, 4, 2, 13, 'wood')
    s.rect(13, 4, 13, 13, 'wood')
    s.rect(2, 4, 13, 4, 'wood')
    s.rect(2, 13, 13, 13, 'wood')
    s.dots([(5, 6), (10, 11), (7, 10), (9, 6)], 'emberblade')
    s.dots([(3, 5), (12, 5), (3, 12), (12, 12)], 'iron')
    return s.render()


@sprite('frost_silk')
def g2():
    s = Sprite()
    s.rect(2, 5, 13, 11, 'rimefur')
    s.ellipse(2, 8, 1.6, 3.5, 'pearl')
    s.ellipse(13.5, 8, 1.6, 3.5, 'pearl')
    for (x, y) in [(5, 6), (8, 8), (11, 6), (6, 10), (10, 10)]:
        s.dot(x, y, 'frost', 3)
    s.rect(7, 5, 8, 11, 'cloth_blue')
    return s.render(glow_halo=True)


@sprite('ember_glass')
def g3():
    s = Sprite()
    s.rect(7, 1, 8, 3, 'glass')
    s.ellipse(7.5, 9, 5, 5.5, 'glass')
    s.ellipse(7.5, 9.5, 3.8, 4.2, 'ember')
    s.dots([(5, 6), (5, 7)], 'shine')
    return s.render(glow_halo=True)


@sprite('bog_amber')
def g4():
    s = Sprite()
    s.poly([(3, 10), (6, 4), (11, 3), (13, 8), (11, 13), (5, 13)], 'amber')
    s.dots([(7, 8), (8, 8), (8, 9), (9, 7)], 'moss')
    s.dot(6, 6, 'shine')
    return s.render()


@sprite('starlight_ink')
def g5():
    s = Sprite()
    s.rect(4, 6, 11, 13, 'darkstone')
    s.rect(6, 3, 9, 5, 'darkstone')
    s.rect(5, 7, 10, 12, 'arcane')
    s.line((10, 1), (14, 6), 1, 'cloth_white')
    s.dots([(6, 8), (9, 10)], 'star', 4)
    return s.render(glow_halo=True)


@sprite('sky_ambergris')
def g6():
    s = Sprite()
    s.ellipse(8, 9, 6, 4.5, 'fur')
    s.ellipse(6, 7, 3, 2.5, 'fur')
    s.dots([(9, 9), (11, 10), (6, 11)], 'darkfur')
    s.dots([(12, 4), (13, 3)], 'frost', 2)
    s.dot(5, 6, 'shine')
    return s.render()


@sprite('clockwork_curio')
def g7():
    s = Sprite()
    s.ellipse(8, 8, 5.5, 5.5, 'brass')
    for (x, y) in [(7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8), (3, 3), (12, 3), (3, 12), (12, 12)]:
        s.dot(x, y, 'brass')
    s.ellipse(8, 8, 2.2, 2.2, 'darksteel')
    s.dot(8, 8, 'aqua', 4)
    return s.render()


@sprite('mirror_salt')
def g8():
    s = Sprite()
    s.ellipse(8, 10, 5.5, 4.5, 'cloth_white')
    s.poly([(6, 6), (10, 6), (9, 3), (7, 3)], 'cloth_white')
    s.rect(6, 5, 9, 5, 'cloth_blue')
    s.dots([(6, 9), (9, 11), (10, 8)], 'crystal', 4)
    return s.render()


# ================================================================== Edict fragments, the compass, the lantern

EDICTS = {
    'return': ('#e8a33d', [(7, 6), (8, 6), (9, 7), (8, 8), (7, 9)]),
    'echoes': ('#b79ce8', [(6, 7), (7, 6), (9, 6), (10, 7), (8, 9)]),
    'borrowed_time': ('#4fb3a9', [(8, 5), (8, 6), (8, 7), (9, 7), (10, 7)]),
    'weightlessness': ('#9bd08a', [(8, 5), (7, 6), (9, 6), (8, 8), (8, 9)]),
    'hunger': ('#c4553b', [(7, 6), (9, 6), (8, 7), (7, 8), (9, 8)]),
    'hospitality': ('#ebd9a6', [(7, 6), (8, 6), (9, 6), (8, 7), (8, 8)]),
    'trespass': ('#6e8fb8', [(6, 6), (7, 7), (8, 8), (9, 7), (10, 6)]),
    'stillness': ('#a9d8e8', [(7, 6), (9, 6), (7, 8), (9, 8), (8, 7)]),
    'witness': ('#f2d25c', [(7, 7), (8, 6), (9, 7), (8, 8), (8, 7)]),
    'forgotten': ('#8a7fa0', [(7, 6), (8, 7), (9, 8), (7, 8), (9, 6)]),
}
for _e, (_col, _glyph) in EDICTS.items():
    mat('edict_' + _e, _col, 'gem')

    def _frag(e=_e, glyph=_glyph):
        s = Sprite()
        s.poly([(8, 1), (12, 5), (11, 13), (8, 15), (4, 12), (4, 5)], 'edict_' + e)
        s.dots(glyph, 'shine')
        s.dots([(6, 3), (5, 5)], 'shine')
        return s.render(glow_halo=True)
    SPRITES['edict_fragment_' + _e] = _frag


def compass_frame(i):
    s = Sprite()
    s.ellipse(8, 8, 6.5, 6.5, 'brass')
    s.ellipse(8, 8, 5, 5, 'paper')
    a = i / 32 * 2 * math.pi - math.pi / 2
    tip = (8 + math.cos(a) * 4.2, 8 + math.sin(a) * 4.2)
    tail = (8 - math.cos(a) * 3.0, 8 - math.sin(a) * 3.0)
    s.line((8, 8), (int(round(tip[0])), int(round(tip[1]))), 0.8, 'cloth_red')
    s.line((8, 8), (int(round(tail[0])), int(round(tail[1]))), 0.8, 'darksteel')
    s.erase([(12, 9), (13, 10), (12, 11)])
    s.dot(8, 8, 'gold', 4)
    return s.render()


for _i in range(32):
    SPRITES[f'unfinished_compass_{_i:02d}'] = (lambda i=_i: compass_frame(i))


def lantern(lit):
    s = Sprite()
    s.rect(7, 1, 8, 2, 'darksteel')
    s.rect(4, 3, 11, 4, 'darksteel')
    s.rect(4, 13, 11, 14, 'darksteel')
    s.rect(5, 5, 10, 12, 'glass')
    s.ellipse(7.5, 9, 2.2 if lit else 1.4, 2.8 if lit else 1.6, 'spirit' if lit else 'echoblade')
    s.dots([(4, 5), (11, 5), (4, 12), (11, 12)], 'darksteel')
    s.dots([(6, 7), (9, 7)], 'soot')
    return s.render(glow_halo=lit)


SPRITES['hungry_lantern'] = lambda: lantern(False)
SPRITES['hungry_lantern_lit'] = lambda: lantern(True)
# Items whose item definitions are written elsewhere (animated or conditional) keep them.
KEEP_DEFS = {'unfinished_compass', 'hungry_lantern'}


# ================================================================== output

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


HANDHELD = {'emberbrand', 'echo_edge', 'moonsilver_rapier', 'shardsong_glaive', 'lurkers_hook', 'gearspring_maul', 'rimefang_spear',
            'wayfarers_fang', 'starweavers_wand', 'ashen_warbanner', 'sporelash_whip', 'skyhook_harpoon', 'bellhammer', 'second_hand',
            'pilgrims_staff', 'apologetic_blade', 'hitodama', 'crimson_requiem', 'rimeback_cleaver', 'dunewalkers_chain'}



# ---------------------------------------------------------------- the second bestiary's spoils

mat('tusk', '#ece2c8', 'matte')
mat('hornbrown', '#8a6a48', 'wood')
mat('lynxfur', '#9a8a74', 'organic')
mat('toadgreen', '#7a9a3a', 'glass')
mat('crabshell', '#2a7a98', 'stone')
mat('kitsunefur', '#f4eee6', 'organic')
mat('chitin', '#2e2420', 'stone')
mat('sandchitin', '#c8a86a', 'stone')
mat('heartwood', '#6a4a30', 'wood')
mat('wyrmscale', '#3a2a2a', 'stone')
mat('ecto', '#b8d8f0', 'glass')
mat('anglerskin', '#1e2438', 'organic')


@sprite('boar_tusk')
def spoil1():
    s = Sprite()
    s.line((3, 13), (8, 9), 2.2, 'tusk')
    s.line((8, 9), (11, 4), 1.6, 'tusk')
    s.line((11, 4), (12, 2), 1.0, 'tusk')
    s.ellipse(3, 13, 2, 2, 'hornbrown')
    s.dots([(9, 7), (10, 5)], 'shine')
    return s.render()


@sprite('ram_horn')
def spoil2():
    s = Sprite()
    s.ellipse(8, 8, 6, 6, 'hornbrown')
    s.ellipse(8.5, 8.5, 3.5, 3.5, 'darkwood')
    s.ellipse(9, 9, 1.8, 1.8, 'hornbrown')
    for x, y in ((3, 6), (5, 3), (9, 2), (13, 5), (13, 10)):
        s.tone([(x, y)], 1)
    s.erase([(x, y) for x in range(9, 12) for y in range(12, 15)])
    return s.render()


@sprite('lynx_pelt')
def spoil3():
    s = Sprite()
    s.poly([(2, 4), (6, 2), (11, 3), (14, 6), (13, 12), (9, 14), (4, 13), (2, 9)], 'lynxfur')
    s.dots([(5, 5), (8, 7), (11, 6), (6, 10), (10, 11), (12, 9)], 'darkfur')
    s.dots([(5, 1), (11, 2)], 'soot')
    return s.render()


@sprite('toad_oil')
def spoil4():
    s = Sprite()
    s.poly([(5, 6), (11, 6), (12, 13), (4, 13)], 'glass')
    s.poly([(5, 8), (11, 8), (12, 13), (4, 13)], 'toadgreen')
    s.rect(6, 3, 10, 6, 'glass')
    s.rect(6, 2, 10, 3, 'wood')
    s.dots([(6, 9), (7, 10)], 'shine')
    return s.render(glow_halo=True)


@sprite('crab_shell')
def spoil5():
    s = Sprite()
    s.ellipse(8, 9, 6.5, 4.5, 'crabshell')
    s.tone([(x, 7) for x in range(3, 14)], 3)
    s.dots([(6, 6), (9, 5), (11, 7)], 'aqua')
    s.dots([(8, 4)], 'shine')
    return s.render(glow_halo=True)


@sprite('kitsune_tail')
def spoil6():
    s = Sprite()
    s.line((3, 13), (8, 8), 3.0, 'kitsunefur')
    s.line((8, 8), (11, 4), 2.6, 'kitsunefur')
    s.ellipse(12, 3, 2.2, 2.2, 'blood')
    s.dots([(12, 2), (13, 3)], 'shine')
    return s.render(glow_halo=True)


@sprite('ember_chitin')
def spoil7():
    s = Sprite()
    s.poly([(3, 4), (12, 2), (14, 9), (9, 14), (3, 12)], 'chitin')
    s.line((5, 6), (8, 9), 0.6, 'ember')
    s.line((8, 9), (12, 7), 0.6, 'ember')
    s.line((8, 9), (7, 12), 0.6, 'ember')
    return s.render(glow_halo=True)


@sprite('sifter_mandible')
def spoil8():
    s = Sprite()
    for (a, b, c) in (((3, 13), (5, 6), (9, 3)), ((7, 14), (11, 9), (14, 4))):
        s.line(a, b, 1.8, 'sandchitin')
        s.line(b, c, 1.2, 'sandchitin')
    s.dots([(9, 3), (14, 4)], 'darkwood')
    return s.render()


@sprite('root_heartwood')
def spoil9():
    s = Sprite()
    s.poly([(3, 5), (12, 3), (14, 10), (5, 13), (2, 10)], 'heartwood')
    s.ellipse(8, 8, 2.5, 2.2, 'moss')
    s.dots([(8, 8)], 'spirit')
    s.line((12, 3), (15, 1), 0.6, 'darkwood')
    s.line((5, 13), (3, 15), 0.6, 'darkwood')
    return s.render(glow_halo=True)


@sprite('dusk_fang')
def spoil10():
    s = Sprite()
    s.poly([(4, 3), (9, 2), (8, 7), (5, 14), (4, 8)], 'bone')
    s.line((5, 4), (5, 12), 0.5, 'arcane')
    return s.render(glow_halo=True)


@sprite('wyrm_scale')
def spoil11():
    s = Sprite()
    s.poly([(8, 2), (13, 6), (11, 13), (5, 13), (3, 6)], 'wyrmscale')
    s.line((8, 3), (8, 12), 0.5, 'ember')
    s.tone([(4, 6), (5, 5), (12, 6), (11, 5)], 3)
    return s.render(glow_halo=True)


@sprite('windrider_feather')
def spoil12():
    s = Sprite()
    s.line((3, 14), (12, 3), 0.6, 'bone')
    s.poly([(5, 11), (11, 3), (13, 4), (7, 12)], 'bronze')
    s.poly([(11, 3), (13, 2), (13, 4)], 'cloth_white')
    s.tone([(7, 10), (9, 7), (11, 5)], 1)
    return s.render()


@sprite('pale_ectoplasm')
def spoil13():
    s = Sprite()
    s.ellipse(8, 9, 5, 4, 'ecto')
    s.ellipse(6, 6, 2.5, 2, 'ecto')
    s.ellipse(11, 5, 2, 2, 'ecto')
    s.dots([(7, 8), (10, 9)], 'frost')
    s.dots([(6, 5)], 'shine')
    return s.render(glow_halo=True)


@sprite('angler_lure')
def spoil14():
    s = Sprite()
    s.line((3, 14), (9, 7), 0.8, 'anglerskin')
    s.line((9, 7), (10, 5), 0.6, 'anglerskin')
    s.ellipse(11, 4, 2.6, 2.6, 'spirit')
    s.dots([(10, 3)], 'shine')
    return s.render(glow_halo=True)


@sprite('narwhal_tusk')
def spoil15():
    s = Sprite()
    s.line((2, 14), (14, 2), 1.6, 'tusk')
    for i in range(3, 13, 2):
        s.tone([(i, 15 - i), (i + 1, 14 - i)], 1)
    s.dots([(13, 3)], 'shine')
    return s.render()


def main():
    os.makedirs(TEX, exist_ok=True)
    images = []
    for name, fn in SPRITES.items():
        img = fn()
        img.save(os.path.join(TEX, name + '.png'))
        images.append(img)
    # Models and item definitions (vanilla handheld pose for weapons; tiers by custom model data).
    for name in SPRITES:
        base = name.split('_t')[0] if name[-3:] in ('_t2', '_t3') and name[:-3] in TIERED else name
        parent = 'minecraft:item/handheld' if base in HANDHELD else 'minecraft:item/generated'
        write_json(os.path.join(A, 'models', 'item', name + '.json'), {'parent': parent, 'textures': {'layer0': f'afterworld:item/{name}'}})
    for name in SPRITES:
        if name[-3:] in ('_t2', '_t3') and name[:-3] in TIERED:
            continue
        if name in KEEP_DEFS or name.startswith('unfinished_compass_') or name == 'hungry_lantern_lit':
            continue
        if name in TIERED:
            cases = [{'when': str(t), 'model': {'type': 'minecraft:model', 'model': f'afterworld:item/{name}_t{t}'}} for t in range(2, TIERED[name] + 1)]
            write_json(os.path.join(A, 'items', name + '.json'), {'model': {
                'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0,
                'cases': cases, 'fallback': {'type': 'minecraft:model', 'model': f'afterworld:item/{name}'}}})
        else:
            write_json(os.path.join(A, 'items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': f'afterworld:item/{name}'}})
    if '--preview' in sys.argv:
        out = sys.argv[sys.argv.index('--preview') + 1]
        os.makedirs(out, exist_ok=True)
        names = list(SPRITES)
        for i in range(0, len(names), 48):
            sheet([SPRITES[n]() for n in names[i:i + 48]], scale=5, cols=12).save(os.path.join(out, f'items_{i // 48}.png'))
    print(f'{len(SPRITES)} sprites ({len(TIERED)} tiered items)')


if __name__ == '__main__':
    main()

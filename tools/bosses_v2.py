"""The five Wardens of the Lands, as box models: the Ashen Shogun (lacquered armour, a horned
kabuto, a naginata hung with a ghost lantern), the Crimson Lich (horned skull, crimson cape,
tattered robe, a staff with a blood gem), the Rimeback Colossus (a horned yeti with ice on its
back), the Dune Sentinel (a sandstone golem whose fists float on chains) and the Gilded Warden
(a stone turtle-golem whose shell is written in gold). Part names drive the animation."""
import os
from mobs_v2 import mat, P, B, root, legs2, legs4, paint, head, glowing, metal, crystal, shell, solid, ghostly, teeth, MATERIALS
import mobs_v2

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.join(HERE, '..', 'src', 'client', 'java', 'dev', 'afterworld', 'client', 'model', 'gen')


def ashen_shogun():
    armour = mat('shogun_armour', paint('#2E2A2E', belly='#3A3438', stripes='#A8202A', every=3, noise=5))
    plate = mat('shogun_plate', metal('#3A3640', accent='#A8202A', rivet='#C8A048'))
    mask = mat('shogun_mask', head('#1E1A1E', eye='#7AFFD8', glow='#C8FFF0', row=3, inset=2, size=1, top='#2E2A2E'))
    hakama = mat('shogun_hakama', paint('#5A1E24', belly='#4A181E', stripes='#3A1418', every=2, noise=5))
    gold = mat('shogun_gold', metal('#C8A048', accent='#FFE08A'))
    wood = mat('shogun_wood', solid('#2A1E18', 4))
    blade = mat('shogun_blade', metal('#C8D0D8', accent='#FFFFFF'))
    paper = mat('shogun_lantern', glowing('#E8F4EC', '#7AFFD8', noise=4, every=2))
    naginata = P('weapon', pivot=(2, 11, 0), boxes=[B((-0.5, -22, -0.5), (1, 36, 1), wood), B((-0.5, -30, -1), (1, 8, 2), blade)],
                 children=[P('lantern', pivot=(0, -21, 0), boxes=[B((-0.5, 0, -0.5), (1, 3, 1), gold), B((-2, 3, -2), (4, 5, 4), paper), B((-2.5, 2.5, -2.5), (5, 1, 5), gold)])])
    body = P('body', pivot=(0, -12, 0), boxes=[B((-5, -13, -3), (10, 13, 6), armour), B((-5.5, -1, -3.5), (11, 5, 7), plate),
                                                B((-1, -12, -3.5), (2, 10, 1), gold)], children=[
        P('head', pivot=(0, -13, 0), boxes=[B((-4, -8, -4), (8, 8, 8), mask), B((-5.5, -5, -4), (11, 4, 9), plate), B((-4.5, -10, -4.5), (9, 2, 9), plate)],
          children=[P('horn_l', pivot=(-2, -9, -4), rot=(0.3, 0, -0.5), boxes=[B((-0.5, -8, -0.5), (1, 8, 1), gold)]),
                    P('horn_r', pivot=(2, -9, -4), rot=(0.3, 0, 0.5), boxes=[B((-0.5, -8, -0.5), (1, 8, 1), gold)])]),
        P('arm_l', pivot=(-5, -11, 0), boxes=[B((-4, -1, -2), (4, 13, 4), armour), B((-6, -3, -3), (6, 5, 6), plate)]),
        P('arm_r', pivot=(5, -11, 0), boxes=[B((0, -1, -2), (4, 13, 4), armour), B((0, -3, -3), (6, 5, 6), plate)], children=[naginata]),
    ])
    return root(body, *legs2(hakama, 5, 12, 2.6))


def crimson_lich():
    robe = mat('lich_robe', paint('#4A0E18', belly='#36090F', stripes='#C8A048', every=7, noise=6))
    cape = mat('lich_cape', paint('#8A1222', belly='#6A0E1A', mottle='#A01828', noise=5))
    skull = mat('lich_skull', head('#E8E0CC', eye='#FF2A3A', glow='#FF8A90', row=3, inset=1, size=2, top='#D8D0BC'))
    horn = mat('lich_horn', solid('#2A2024', 4))
    gold = mat('lich_gold', metal('#C8A048', accent='#FFE08A'))
    bone = mat('lich_bone', solid('#D8D0BC', 4))
    gem = mat('lich_gem', glowing('#A0101C', '#FF4A5A', noise=3, every=1))
    staff = P('weapon', pivot=(2, 11, 0), boxes=[B((-0.5, -24, -0.5), (1, 34, 1), horn), B((-1.5, -28, -1.5), (3, 4, 3), gold)],
              children=[P('gem', pivot=(0, -31, 0), boxes=[B((-1.5, -1.5, -1.5), (3, 3, 3), gem)])])
    tatters = [P(f'tent{i + 1}', pivot=(x, 0, z), boxes=[B((-1.5, 0, -0.5), (3, 12, 1), robe)]) for i, (x, z) in enumerate(((-4, -3), (0, -3.2), (4, -3), (-4, 3), (0, 3.2), (4, 3)))]
    body = P('body', pivot=(0, -16, 0), boxes=[B((-5, -14, -3), (10, 14, 6), robe), B((-6, -15, -3.5), (12, 3, 7), gold)], children=[
        P('head', pivot=(0, -14, 0), boxes=[B((-4, -8, -4), (8, 8, 8), skull), B((-3, -10, -3), (6, 2, 6), gold)],
          children=[P('horn_l', pivot=(-3.5, -6, 0), rot=(0.5, 0, -0.9), boxes=[B((-1, -9, -1), (2, 9, 2), horn)]),
                    P('horn_r', pivot=(3.5, -6, 0), rot=(0.5, 0, 0.9), boxes=[B((-1, -9, -1), (2, 9, 2), horn)])]),
        P('cape', pivot=(0, -13, 3), rot=(0.12, 0, 0), boxes=[B((-6.5, 0, 0), (13, 26, 1), cape)]),
        P('arm_l', pivot=(-5, -12, 0), boxes=[B((-3, -1, -2), (3, 12, 4), robe), B((-2.5, 11, -1), (2, 4, 2), bone)]),
        P('arm_r', pivot=(5, -12, 0), boxes=[B((0, -1, -2), (3, 12, 4), robe), B((0.5, 11, -1), (2, 4, 2), bone)], children=[staff]),
        P('skirt', pivot=(0, 0, 0), boxes=[B((-5.5, 0, -3.5), (11, 4, 7), robe)], children=tatters),
    ])
    return root(body)


def rimeback_colossus():
    fur = mat('rimeback_fur', paint('#DCE8F0', belly='#B8C8D8', mottle='#C8D8E4', noise=7))
    face = mat('rimeback_face', head('#4A5A78', eye='#4AE0FF', glow='#B8F4FF', row=4, inset=2, size=2, top='#DCE8F0'))
    skin = mat('rimeback_skin', paint('#4A5A78', noise=5))
    horn = mat('rimeback_horn', paint('#3A3A48', stripes='#5A5A6A', every=2, noise=4))
    ice = mat('rimeback_ice', crystal('#8AD0FF', '#E0F8FF', 3))
    fang = mat('rimeback_fang', teeth('#F0F4F8', '#4A5A78'))
    spikes = [B((x, -21, z), (3, 6, 3), ice) for (x, z) in ((-7, -4), (-2, -6), (3, -3), (-5, 1), (1, 0), (5, 2))]
    body = P('body', pivot=(0, -18, 2), rot=(0.3, 0, 0), boxes=[B((-10, -16, -7), (20, 16, 14), fur)] + spikes, children=[
        P('head', pivot=(0, -14, -6), rot=(-0.3, 0, 0), boxes=[B((-6, -9, -9), (12, 10, 10), face), B((-4, -2, -10), (8, 3, 2), fang)],
          children=[P('horn_l', pivot=(-6, -8, -4), rot=(0.2, 0.3, -0.6), boxes=[B((-8, -2, -2), (8, 4, 4), horn), B((-10, -8, -1.5), (3, 7, 3), horn)]),
                    P('horn_r', pivot=(6, -8, -4), rot=(0.2, -0.3, 0.6), boxes=[B((0, -2, -2), (8, 4, 4), horn), B((7, -8, -1.5), (3, 7, 3), horn)]),
                    P('jaw', pivot=(0, 1, -5), boxes=[B((-5, 0, -4), (10, 3, 5), skin)])]),
        P('arm_l', pivot=(-10, -13, -1), boxes=[B((-8, -2, -4), (8, 22, 8), fur), B((-8.5, 20, -4.5), (9, 6, 9), skin), B((-7, 4, -5), (4, 4, 2), ice)]),
        P('arm_r', pivot=(10, -13, -1), boxes=[B((0, -2, -4), (8, 22, 8), fur), B((-0.5, 20, -4.5), (9, 6, 9), skin), B((3, 4, -5), (4, 4, 2), ice)]),
    ])
    return root(body, *legs2(fur, 8, 12, 5))


def dune_sentinel():
    stone = mat('sentinel_stone', shell('#C8A46A', '#8A6A3A', crack='#6A4A2A', plates=3))
    face = mat('sentinel_face', head('#B8945A', eye='#FFD060', glow='#FFF0A0', row=4, inset=1, size=1, top='#C8A46A'))
    gold = mat('sentinel_gold', metal('#C8A048', accent='#FFE08A'))
    sun = mat('sentinel_sun', glowing('#E8A030', '#FFE070', noise=3, every=1))
    chain = mat('sentinel_chain', metal('#5A5A62', accent='#8A8A92'))
    fist_box = [B((-4.5, -4.5, -4.5), (9, 9, 9), stone), B((-5, -1, -5), (10, 2, 10), gold)]
    body = P('body', pivot=(0, -14, 0), boxes=[B((-9, -18, -6), (18, 18, 12), stone), B((-3, -14, -6.5), (6, 6, 1), sun), B((-9.5, -2, -6.5), (19, 3, 13), gold)], children=[
        P('head', pivot=(0, -18, 0), boxes=[B((-5, -9, -5), (10, 9, 10), face), B((-6, -10, -6), (12, 2, 12), gold)]),
        P('shoulder_l', pivot=(-13, -16, 0), boxes=[B((-3, -3, -3), (6, 6, 6), stone)]),
        P('shoulder_r', pivot=(13, -16, 0), boxes=[B((-3, -3, -3), (6, 6, 6), stone)]),
        P('chain_l', pivot=(-13, -13, 0), boxes=[B((-0.5, 0, -0.5), (1, 10, 1), chain)]),
        P('chain_r', pivot=(13, -13, 0), boxes=[B((-0.5, 0, -0.5), (1, 10, 1), chain)]),
        P('fist_l', pivot=(-17, -2, -3), boxes=fist_box),
        P('fist_r', pivot=(17, -2, -3), boxes=fist_box),
    ])
    return root(body, *legs2(stone, 7, 10, 4.5))


def gilded_warden():
    shell_mat = mat('warden_shell', shell('#7A7468', '#C8A048', crack='#5A5448', plates=5))
    dome = mat('warden_dome', shell('#8A8478', '#FFD060', plates=3))
    skin = mat('warden_skin', paint('#6A6458', belly='#8A8070', noise=6))
    face = mat('warden_face', head('#6A6458', eye='#FFD060', glow='#FFF4B0', row=3, inset=2, size=1, top='#7A7468'))
    gold = mat('warden_gold', metal('#C8A048', accent='#FFE08A'))
    glyph = mat('warden_glyph', glowing('#C8A048', '#FFE890', noise=3, every=2))
    body = P('body', pivot=(0, -10, 0), boxes=[B((-14, -14, -16), (28, 14, 32), shell_mat), B((-11, -18, -12), (22, 4, 24), dome),
                                                B((-7, -21, -8), (14, 3, 16), dome), B((-15, -2, -17), (30, 3, 34), gold),
                                                B((-2, -22, -2), (4, 1, 4), glyph)], children=[
        P('head', pivot=(0, -6, -16), boxes=[B((-5, -6, -10), (10, 8, 10), face), B((-2, -1, -12), (4, 3, 2), gold)]),
        P('tail', pivot=(0, -4, 16), boxes=[B((-2, -2, 0), (4, 4, 6), skin)]),
    ])
    return root(body, *legs4(skin, 8, 10, 10, -10, 10, hip=-10))


BOSSES = [
    ('ashen_shogun', ashen_shogun, 128),
    ('crimson_lich', crimson_lich, 128),
    ('rimeback_colossus', rimeback_colossus, 128),
    ('dune_sentinel', dune_sentinel, 128),
    ('gilded_warden', gilded_warden, 256),
]
BOSS_SPECS = [(mobs_v2._pascal(i) + 'Layer', i, fn, w, 500 + k) for k, (i, fn, w) in enumerate(BOSSES)]


def write_registry():
    lines = ['package dev.afterworld.client.model.gen;', '',
             'import net.minecraft.client.model.geom.builders.LayerDefinition;', '',
             '/** Generated by tools/bosses_v2.py. Do not edit by hand. */',
             'public final class BossLayers {', '\tprivate BossLayers() {', '\t}', '',
             '\tpublic static LayerDefinition create(String id) {', '\t\treturn switch (id) {']
    for i, fn, w in BOSSES:
        lines.append(f'\t\t\tcase "{i}" -> {mobs_v2._pascal(i)}Layer.create();')
    lines += ['\t\t\tdefault -> throw new IllegalArgumentException(id);', '\t\t};', '\t}', '',
              '\tpublic static String[] parts(String id) {', '\t\treturn switch (id) {']
    for i, fn, w in BOSSES:
        lines.append(f'\t\t\tcase "{i}" -> new String[] {{' + ', '.join(f'"{p}"' for p in mobs_v2.part_paths(fn())) + '};')
    lines += ['\t\t\tdefault -> new String[0];', '\t\t};', '\t}', '}', '']
    os.makedirs(JAVA, exist_ok=True)
    with open(os.path.join(JAVA, 'BossLayers.java'), 'w') as f:
        f.write('\n'.join(lines))


for _i, _fn, _w in BOSSES:
    _fn()

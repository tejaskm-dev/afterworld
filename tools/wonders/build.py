#!/usr/bin/env python3
"""Builds Afterworld's wonders: the blocks (data/afterworld/wonder/*.aww), where they generate
(worldgen/structure, worldgen/structure_set, biome tags) and, if asked, preview pictures.

    <python with numpy + Pillow> tools/wonders/build.py [name ...] [--preview DIR]
"""
import importlib, json, os, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
DATA = os.path.join(HERE, '..', '..', 'src', 'main', 'resources', 'data')
OUT = os.path.join(DATA, 'afterworld', 'wonder')

# sink: layers of foundation below the ground line. relief: how uneven the ground may be.
# spacing/separation in chunks. Biomes are where the centre may stand.
META = {
    'titan': dict(sink=2, relief=16, spacing=72, separation=30, salt=7101, adapt='beard_box',
                  biomes=['afterworld:ashen_moor', 'afterworld:ember_steppe', 'afterworld:rustscar_canyons', 'afterworld:hourglass_dunes', 'afterworld:whitewaste', 'afterworld:vesper_downs']),
    'cathedral': dict(sink=2, relief=14, spacing=80, separation=36, salt=7102, adapt='beard_box',
                      biomes=['afterworld:vesper_downs', 'afterworld:ember_steppe', 'afterworld:pale_reach', 'afterworld:windworn_highlands']),
    'citadel': dict(sink=2, relief=22, spacing=80, separation=36, salt=7103, adapt='beard_box',
                    biomes=['afterworld:ashen_moor', 'afterworld:gloamfen', 'afterworld:gloomwald', 'afterworld:rustscar_canyons', 'afterworld:windworn_highlands', 'afterworld:frostmere_pines', 'afterworld:whitewaste']),
    'mill': dict(sink=5, relief=12, spacing=64, separation=28, salt=7104, adapt='beard_box',
                 biomes=['afterworld:vesper_downs', 'afterworld:ember_steppe', 'afterworld:petalfall']),
    'angels': dict(sink=2, relief=14, spacing=60, separation=26, salt=7105, adapt='beard_box',
                   biomes=['afterworld:shardfields', 'afterworld:petalfall', 'afterworld:vesper_downs', 'afterworld:windworn_highlands', 'afterworld:whitewaste', 'afterworld:skyspire_peaks']),
}

# Landmarks: smaller, highly detailed builds met every few hundred blocks. They share one placement
# grid (LANDMARK_SET); each one only stands in its own biomes. module/fn say where the builder is.
COLD = ['afterworld:whitewaste', 'afterworld:glimmerfrost', 'afterworld:frostmere_pines', 'afterworld:skyspire_peaks', 'afterworld:rimeglass_spires']
LANDMARKS = {
    'moon_gazebo': dict(module='moon_gazebo', fn='build', sink=3, relief=8, weight=3,
                        biomes=['afterworld:vesper_downs', 'afterworld:pale_reach', 'afterworld:petalfall', 'afterworld:shardfields',
                                'afterworld:mossmere_wood', 'afterworld:duskpine_weald', 'afterworld:windworn_highlands']),
    'moon_gazebo_snow': dict(module='moon_gazebo', fn='build_snow', sink=3, relief=8, weight=3, biomes=COLD),
    'star_spire': dict(sink=3, relief=10, weight=3,
                       biomes=['afterworld:gloomwald', 'afterworld:duskpine_weald', 'afterworld:hollowwood', 'afterworld:pale_reach',
                               'afterworld:mossmere_wood', 'afterworld:ashen_moor', 'afterworld:windworn_highlands']),
    'icyshroom': dict(sink=3, relief=8, weight=4,
                      biomes=['afterworld:glimmerfrost', 'afterworld:whitewaste', 'afterworld:frostmere_pines', 'afterworld:rimeglass_spires']),
    'waystone': dict(sink=3, relief=8, weight=5,
                     biomes=['afterworld:vesper_downs', 'afterworld:mossmere_wood', 'afterworld:pale_reach', 'afterworld:ashen_moor', 'afterworld:hollowwood',
                             'afterworld:duskpine_weald', 'afterworld:windworn_highlands', 'afterworld:ember_steppe', 'afterworld:petalfall',
                             'afterworld:shardfields', 'afterworld:glimmerfrost', 'afterworld:whitewaste', 'afterworld:frostmere_pines']),
    'lodge': dict(sink=3, relief=8, weight=3,
                  biomes=['afterworld:duskpine_weald', 'afterworld:mossmere_wood', 'afterworld:hollowwood', 'afterworld:windworn_highlands', 'afterworld:pale_reach']),
    'lodge_snow': dict(module='lodge', fn='build_snow', sink=3, relief=8, weight=3, biomes=COLD),
    'chapel': dict(sink=3, relief=8, weight=3,
                   biomes=['afterworld:gloomwald', 'afterworld:hollowwood', 'afterworld:mossmere_wood', 'afterworld:pale_reach', 'afterworld:ashen_moor',
                           'afterworld:vesper_downs', 'afterworld:gloamfen']),
    'oasis': dict(sink=4, relief=6, weight=5, biomes=['afterworld:hourglass_dunes', 'afterworld:ember_steppe', 'afterworld:rustscar_canyons']),
    'stilt_house': dict(sink=4, relief=6, weight=5, biomes=['afterworld:gloamfen', 'afterworld:verdant_tangle', 'afterworld:sporeveil']),
    'airship': dict(sink=3, relief=8, weight=3,
                    biomes=['afterworld:vesper_downs', 'afterworld:ember_steppe', 'afterworld:windworn_highlands', 'afterworld:hourglass_dunes',
                            'afterworld:rustscar_canyons', 'afterworld:ashen_moor']),
}
LANDMARK_SET = dict(spacing=20, separation=7, salt=7201)
META.update({k: dict(v, kind='landmark') for k, v in LANDMARKS.items()})
# Placed by the game itself (the camp at spawn), never by world generation.
META['waystone_camp'] = dict(module='town', fn='build', kind='placed')
# The floors of the Warden's Ladder: stamped by the game, one by one, as the climb goes up.
import ladder as _ladder
META.update({n: dict(module='ladder', fn=n, kind='placed') for n in _ladder.FLOORS})

# Sites: every land's own places (ruins, camps, crypts, shrines), on a denser grid of their own.
# Each stands only in its lands; some carry quest tags (glyph_sites, caravan_wreck, crypts...).
import sites as _sites
SITES = _sites.META
SITE_SET = dict(spacing=12, separation=4, salt=7301)
META.update({k: dict(v, kind='site', module='sites', fn=k) for k, v in SITES.items()})


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


# The Wardens' lairs: one each per world, where the game's Atlas puts them (see world/atlas/Atlas.java).
# Their has_structure tag is the country the Atlas looks for; the structure itself may stand anywhere.
ATLAS_SALT = 7400


def data(name):
    m = META[name]
    if m.get('kind') == 'placed':
        return
    atlas = m.get('atlas', False)
    write_json(os.path.join(DATA, 'afterworld', 'worldgen', 'structure', name + '.json'), {
        'type': 'afterworld:wonder',
        'wonder': name,
        'sink': m['sink'],
        'max_relief': m['relief'],
        'biomes': '#afterworld:atlas_any' if atlas else '#afterworld:has_structure/' + name,
        **({'atlas': True} if atlas else {}),
        # Sites go down last, after trees and ice spikes, so nothing grows through their walls.
        'step': 'top_layer_modification' if m.get('kind') == 'site' else 'surface_structures',
        'terrain_adaptation': m.get('adapt', 'beard_box'),
        'spawn_overrides': m.get('spawns', {}),
        **({'shore': True} if m.get('shore') else {}),
    })
    if atlas:
        write_json(os.path.join(DATA, 'afterworld', 'worldgen', 'structure_set', 'atlas_' + name + '.json'), {
            'placement': {'type': 'afterworld:atlas', 'site': name, 'salt': ATLAS_SALT + sorted(n for n in META if META[n].get('atlas')).index(name)},
            'structures': [{'structure': 'afterworld:' + name, 'weight': 1}],
        })
    elif m.get('kind') not in ('landmark', 'site'):
        write_json(os.path.join(DATA, 'afterworld', 'worldgen', 'structure_set', name + '.json'), {
            'placement': {'type': 'minecraft:random_spread', 'salt': m['salt'], 'separation': m['separation'], 'spacing': m['spacing']},
            'structures': [{'structure': 'afterworld:' + name, 'weight': 1}],
        })
    write_json(os.path.join(DATA, 'afterworld', 'tags', 'worldgen', 'biome', 'has_structure', name + '.json'),
               {'values': m['biomes']})


def main():
    args = sys.argv[1:]
    preview = None
    if '--preview' in args:
        i = args.index('--preview')
        preview = args[i + 1]
        del args[i:i + 2]
    names = args or list(META)
    os.makedirs(OUT, exist_ok=True)
    for name in names:
        t = time.time()
        m = META[name]
        g = getattr(importlib.import_module(m.get('module', name)), m.get('fn', 'build'))()
        if m.get('kind') == 'site' and not m.get('shore') and m.get('clear', True):
            import sitekit
            sitekit.clear_core(g, m['sink'])
        size = g.export(os.path.join(OUT, name + '.aww'))
        packed = os.path.getsize(os.path.join(OUT, name + '.aww'))
        data(name)
        print(f'{name}: {g.sx}x{g.sy}x{g.sz}, {g.count()} blocks, {len(g.palette) - 1} states, '
              f'{size // 1024} KB raw, {packed // 1024} KB packed, {time.time() - t:.1f}s')
        if preview:
            import vox
            os.makedirs(preview, exist_ok=True)
            for yaw in (0, 1):
                g.preview(os.path.join(preview, f'{name}_iso{yaw}.png'), scale=3, yaw=yaw)
            for view in ('front', 'side'):
                vox.elevation(g, os.path.join(preview, f'{name}_{view}.png'), view, scale=4)
    wonders = [n for n in META if META[n].get('kind') not in ('landmark', 'placed', 'site')]
    write_json(os.path.join(DATA, 'afterworld', 'tags', 'worldgen', 'structure', 'wonders.json'),
               {'values': ['afterworld:' + n for n in wonders]})
    write_json(os.path.join(DATA, 'afterworld', 'tags', 'worldgen', 'structure', 'landmarks.json'),
               {'values': ['afterworld:' + n for n in LANDMARKS]})
    write_json(os.path.join(DATA, 'afterworld', 'worldgen', 'structure_set', 'landmarks.json'), {
        'placement': {'type': 'minecraft:random_spread', 'salt': LANDMARK_SET['salt'], 'separation': LANDMARK_SET['separation'],
                      'spacing': LANDMARK_SET['spacing']},
        'structures': [{'structure': 'afterworld:' + n, 'weight': LANDMARKS[n].get('weight', 1)} for n in LANDMARKS],
    })
    write_json(os.path.join(DATA, 'afterworld', 'worldgen', 'structure_set', 'sites.json'), {
        'placement': {'type': 'minecraft:random_spread', 'salt': SITE_SET['salt'], 'separation': SITE_SET['separation'], 'spacing': SITE_SET['spacing']},
        'structures': [{'structure': 'afterworld:' + n, 'weight': SITES[n].get('weight', 1)} for n in SITES if not SITES[n].get('atlas')],
    })
    write_json(os.path.join(DATA, 'afterworld', 'tags', 'worldgen', 'biome', 'atlas_any.json'), {'values': ['#minecraft:is_overworld']})
    groups = {'sites': list(SITES)}
    for n, m in SITES.items():
        for t in m.get('tags', []):
            groups.setdefault(t, []).append(n)
    for t, names in groups.items():
        write_json(os.path.join(DATA, 'afterworld', 'tags', 'worldgen', 'structure', t + '.json'), {'values': ['afterworld:' + n for n in names]})


if __name__ == '__main__':
    main()

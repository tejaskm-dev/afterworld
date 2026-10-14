#!/usr/bin/env python3
"""Afterworld's terrain data: its biomes (built on vanilla hosts), their features, and biome tags.

Almost every vanilla overworld biome is replaced by an Afterworld one (see AwBiomes#remap), so the
world keeps vanilla's layout while looking, sounding and growing differently. Each biome has a
ground palette, its own trees, rocks, spires and ruins, and its own cave dressing underground.

Vanilla features reused in a biome are sorted by a global order derived from every vanilla biome,
and Afterworld's own features always come first in their step in one fixed order, so feature order
never cycles. A biome inherits the vanilla tags of the biomes it replaces (structures, spawns...).

Usage: gen_worldgen.py <vanilla data/minecraft/worldgen> [<vanilla data/minecraft/tags/worldgen/biome>] [<block list>]
"""
import json, os, sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources', 'data')
VANILLA = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..', 'build', 'vanilla-worldgen')
VTAGS = sys.argv[2] if len(sys.argv) > 2 else os.path.join(VANILLA, '..', 'tags', 'worldgen', 'biome')
BLOCKS = set(open(sys.argv[3]).read().split()) if len(sys.argv) > 3 else None
OUR = 'afterworld'
USED_BLOCKS = set()


# ---------------------------------------------------------------- block helpers

def b(block, **props):
    """A block state for BlockState.CODEC fields (a bare id, or id plus properties)."""
    USED_BLOCKS.add(block)
    if not props:
        return 'minecraft:' + block
    return {'id': 'minecraft:' + block, 'properties': {k: str(v).lower() for k, v in props.items()}}


def s(block, **props):
    """A block state provider holding one state."""
    USED_BLOCKS.add(block)
    out = {'id': 'minecraft:' + block}
    if props:
        out['properties'] = {k: str(v).lower() for k, v in props.items()}
    return out


def w(*entries):
    """A weighted block state provider: (block or (block, props), weight)..."""
    out = []
    for e, weight in entries:
        if isinstance(e, tuple):
            out.append({'data': b(e[0], **e[1]), 'weight': weight})
        else:
            out.append({'data': b(e), 'weight': weight})
    return {'type': 'minecraft:weighted', 'entries': out}


def layer(block, noise=0, lo=0.0, hi=1.01, **props):
    return {'state': b(block, **props), 'noise': noise, 'min': lo, 'max': hi}


def plant(block, chance, **props):
    return {'state': b(block, **props), 'chance': chance}


# ---------------------------------------------------------------- placement helpers

def scatter(first, heightmap='WORLD_SURFACE_WG'):
    return [first, {'type': 'minecraft:in_square'}, {'type': 'minecraft:heightmap', 'heightmap': heightmap}, {'type': 'minecraft:biome'}]


def rarity(n):
    return {'type': 'minecraft:rarity_filter', 'chance': n}


def count(lo, hi=None):
    if hi is None:
        return {'type': 'minecraft:count', 'count': lo}
    return {'type': 'minecraft:count', 'count': {'type': 'minecraft:uniform', 'min_inclusive': lo, 'max_inclusive': hi}}


def underground(n):
    return [count(n), {'type': 'minecraft:in_square'}, {'type': 'minecraft:biome'}]


# ---------------------------------------------------------------- our features

CONFIGURED = {}
PLACED = {}
# Placed features that reuse another's configuration (identical configurations may not be registered twice).
ALIAS = {}
# One global order for every feature of ours in each step (see the module docstring).
ORDER = {7: [], 9: []}


def feature(name, cfg, placement, step=9):
    # A configuration identical to an earlier one is shared rather than registered twice.
    key = json.dumps(cfg, sort_keys=True)
    twin = next((n for n, c in CONFIGURED.items() if json.dumps(c, sort_keys=True) == key), None)
    if twin:
        ALIAS[name] = twin
    else:
        CONFIGURED[name] = cfg
    PLACED[name] = placement
    if step in ORDER and name not in ORDER[step]:
        ORDER[step].append(name)
    return name


# Existing hand-written features.
feature('hollow_giant', {'type': 'afterworld:hollow_giant'}, scatter(rarity(2)))
feature('floating_island', {'type': 'afterworld:floating_island'}, scatter(rarity(4)))
feature('crystal_spire', {'type': 'afterworld:crystal_spire'}, scatter(count(0, 2)))
feature('moor_arch', {'type': 'afterworld:stone_arch'}, scatter(rarity(30)))
feature('standing_stones', {'type': 'afterworld:standing_stones'}, scatter(rarity(26)))
PLACED['standing_stones_rare'] = scatter(rarity(60))
ALIAS['standing_stones_rare'] = 'standing_stones'
ORDER[9].append('standing_stones_rare')
feature('ash_vent', {'type': 'afterworld:ash_vent'}, scatter(rarity(7)))
feature('ashen_tree', {'type': 'afterworld:gnarled_tree', 'fen': False}, scatter(count(0, 2)))
feature('fen_causeway', {'type': 'afterworld:causeway'}, scatter(rarity(9)))
feature('fen_tree', {'type': 'afterworld:gnarled_tree', 'fen': True}, scatter(count(0, 2)))
for p in ('ashen', 'hollow', 'shard', 'fen'):
    CONFIGURED[p + '_surface'] = {'type': 'afterworld:surface_paint', 'palette': p}
    PLACED[p + '_surface'] = []


def tree(name, log, leaves, shape, lo, hi, placement, hang=None, hang_chance=0.0, extra=None, extra_chance=0.0, snow=False):
    if isinstance(leaves, str):
        leaves = {'id': leaves}     # a single leaves block, as a provider
    cfg = {'type': 'afterworld:tree', 'log': log, 'leaves': leaves, 'shape': shape, 'min_height': lo, 'max_height': hi, 'snow': snow}
    if hang:
        cfg['hang'] = hang
        cfg['hang_chance'] = hang_chance
    if extra:
        cfg['extra'] = extra
        cfg['extra_chance'] = extra_chance
    return feature(name, cfg, placement)


def outcrop(name, body, placement, cap=None, cap_chance=0.6, lo=1.6, hi=3.2, lumps=3):
    cfg = {'type': 'afterworld:outcrop', 'body': body, 'cap_chance': cap_chance, 'min_size': lo, 'max_size': hi, 'lumps': lumps}
    if cap:
        cfg['cap'] = cap
    return feature(name, cfg, placement)


def spire(name, body, placement, style='taper', lo=6, hi=12, rlo=1.0, rhi=2.5, tip=None, glow=None, glow_chance=0.0):
    cfg = {'type': 'afterworld:spire', 'body': body, 'style': style, 'min_height': lo, 'max_height': hi, 'min_radius': rlo, 'max_radius': rhi,
           'glow_chance': glow_chance}
    if tip:
        cfg['tip'] = tip
    if glow:
        cfg['glow'] = glow
    return feature(name, cfg, placement)


def ruin(name, wall, floor, style, placement, accent=None, loot='afterworld:chests/ruin_cache', loot_chance=0.4, underwater=False):
    cfg = {'type': 'afterworld:ruin', 'wall': wall, 'floor': floor, 'style': style, 'loot_chance': loot_chance, 'underwater': underwater}
    if accent:
        cfg['accent'] = accent
    if loot:
        cfg['loot'] = loot
    return feature(name, cfg, placement)


def mushroom(name, stem, cap, placement, shape='dome', lo=6, hi=12, clo=3, chi=5, spots=None, spot_chance=0.12):
    cfg = {'type': 'afterworld:giant_mushroom', 'stem': stem, 'cap': cap, 'shape': shape, 'min_height': lo, 'max_height': hi,
           'min_cap': clo, 'max_cap': chi, 'spot_chance': spot_chance}
    if spots:
        cfg['spots'] = spots
    return feature(name, cfg, placement)


def bones(name, placement, bone=None, lo=10, hi=18):
    return feature(name, {'type': 'afterworld:bones', 'bone': bone or b('bone_block', axis='y'), 'min_length': lo, 'max_length': hi}, placement)


def caves(name, n, floor_paint=None, floor=None, floor_chance=0.3, ceiling_paint=None, ceiling=None, ceiling_chance=0.25, radius=4, lo=-48, hi=50):
    cfg = {'type': 'afterworld:cave_decor', 'tries': 3, 'min_y': lo, 'max_y': hi, 'radius': radius,
           'floor_chance': floor_chance, 'ceiling_chance': ceiling_chance}
    for k, v in (('floor_paint', floor_paint), ('floor', floor), ('ceiling_paint', ceiling_paint), ('ceiling', ceiling)):
        if v:
            cfg[k] = v
    return feature(name, cfg, underground(n), step=7)


# Others came down before you: camps, shrines and lost explorers on cave floors (tried in one chunk of four).
feature('cave_camp', {'type': 'afterworld:cave_camp', 'tries': 4, 'min_y': -48, 'max_y': 40, 'loot': 'afterworld:chests/cave_cache'},
        [rarity(4), {'type': 'minecraft:in_square'}, {'type': 'minecraft:biome'}], step=7)


# ---- shared palettes
STONE_RUIN = w(('stone_bricks', 4), ('mossy_stone_bricks', 3), ('cracked_stone_bricks', 2), ('cobblestone', 1))
STONE_FLOOR = w(('stone_bricks', 3), ('cobblestone', 2), ('gravel', 1), ('mossy_cobblestone', 1))
MOSSY_BOULDER = w(('cobblestone', 3), ('mossy_cobblestone', 3), ('andesite', 2), ('stone', 2))
LANTERN = s('lantern', hanging=False)
CANDLES = w((('white_candle', {'candles': 3, 'lit': True}), 2), (('candle', {'candles': 2, 'lit': True}), 1), (('lantern', {'hanging': False}), 1))

# ---- caves: one style per family of biomes
caves('cave_glimmer', 4, floor_paint=w(('calcite', 3), ('tuff', 2), ('smooth_basalt', 1)),
      floor=w((('small_amethyst_bud', {'facing': 'up'}), 3), (('medium_amethyst_bud', {'facing': 'up'}), 2), (('amethyst_cluster', {'facing': 'up'}), 1)),
      ceiling_paint=w(('calcite', 2), ('tuff', 1)), ceiling=w((('amethyst_cluster', {'facing': 'down'}), 1), (('large_amethyst_bud', {'facing': 'down'}), 2)),
      floor_chance=0.22, ceiling_chance=0.12)
caves('cave_rootways', 5, floor_paint=w(('moss_block', 4), ('rooted_dirt', 1)),
      floor=w(('moss_carpet', 5), ('azalea', 1), ('flowering_azalea', 1), ('short_grass', 2)),
      ceiling_paint=w(('rooted_dirt', 3), ('moss_block', 1)), ceiling=w(('hanging_roots', 3), (('cave_vines', {'berries': True}), 1)),
      floor_chance=0.45, ceiling_chance=0.3)
caves('cave_ember', 4, floor_paint=w(('blackstone', 3), (('basalt', {'axis': 'y'}), 2), ('magma_block', 1)),
      ceiling_paint=w((('basalt', {'axis': 'y'}), 2), ('blackstone', 2), ('shroomlight', 1)), floor_chance=0.0, ceiling_chance=0.0)
caves('cave_frost', 4, floor_paint=w(('packed_ice', 3), ('snow_block', 2), ('blue_ice', 1)),
      floor=w((('snow', {'layers': 1}), 3), (('snow', {'layers': 2}), 1)), ceiling_paint=w(('packed_ice', 2), ('ice', 1)),
      floor_chance=0.4, ceiling_chance=0.0)
caves('cave_spore', 5, floor_paint=w(('mycelium', 2), ('moss_block', 1)), floor=w(('red_mushroom', 2), ('brown_mushroom', 2)),
      ceiling_paint=w(('moss_block', 3), ('shroomlight', 1)), ceiling=w(('hanging_roots', 1)), floor_chance=0.35, ceiling_chance=0.2)
caves('cave_sculpt', 3, floor_paint=w(('tuff', 2), ('polished_tuff', 1), ('calcite', 1)), floor=w((('pointed_dripstone', {'vertical_direction': 'up', 'thickness': 'tip'}), 1)),
      ceiling_paint=w(('dripstone_block', 2), ('tuff', 1)), ceiling=w((('pointed_dripstone', {'vertical_direction': 'down', 'thickness': 'tip'}), 1)),
      floor_chance=0.15, ceiling_chance=0.3)

# ---- trees, rocks, spires, ruins for each biome (the biome table below picks from these)
tree('downs_oak', b('oak_log'), b('oak_leaves'), 'round', 5, 8, scatter(rarity(3)))
outcrop('downs_boulder', MOSSY_BOULDER, scatter(rarity(5)), cap=s('moss_block'))
ruin('downs_ruin', STONE_RUIN, STONE_FLOOR, 'walls', scatter(rarity(40)), accent=LANTERN)
ruin('downs_well', STONE_RUIN, STONE_FLOOR, 'well', scatter(rarity(90)), accent=LANTERN, loot=None)
spire('rune_menhir', w(('stone_bricks', 3), ('mossy_stone_bricks', 2), ('chiseled_stone_bricks', 1)), scatter(rarity(28)),
      lo=4, hi=8, rlo=0.6, rhi=1.0, glow=s('crying_obsidian'), glow_chance=0.04)

tree('mossy_giant', b('oak_log'), b('oak_leaves'), 'giant', 14, 22, scatter(rarity(3)), hang=s('pale_hanging_moss'), hang_chance=0.12)
tree('mossy_oak', b('oak_log'), b('oak_leaves'), 'round', 6, 9, scatter(count(2, 4)), hang=s('pale_hanging_moss'), hang_chance=0.08)
tree('mossy_twist', b('oak_log'), b('oak_leaves'), 'twisted', 5, 8, scatter(count(0, 2)))
outcrop('mossy_boulder', MOSSY_BOULDER, scatter(rarity(3)), cap=s('moss_block'), cap_chance=0.85)
ruin('mossy_ruin', w(('mossy_stone_bricks', 4), ('mossy_cobblestone', 2), ('stone_bricks', 1)), STONE_FLOOR, 'tower', scatter(rarity(55)), accent=LANTERN)

tree('pale_tree', b('birch_log'), b('oak_leaves'), 'round', 6, 9, scatter(count(2, 4)), hang=s('pale_hanging_moss'), hang_chance=0.1)
tree('pale_spire_tree', b('pale_oak_log'), b('oak_leaves'), 'column', 9, 14, scatter(count(0, 2)), hang=s('pale_hanging_moss'), hang_chance=0.06)
spire('pale_headstone', w(('calcite', 2), ('polished_diorite', 2), ('diorite', 1)), scatter(rarity(5)), lo=2, hi=3, rlo=0.3, rhi=0.5)
ruin('pale_shrine', w(('calcite', 3), ('polished_diorite', 2), ('diorite_wall', 1)), w(('polished_diorite', 2), ('calcite', 1)), 'shrine',
     scatter(rarity(35)), accent=CANDLES, loot='afterworld:chests/shrine_cache')

tree('gloom_tree', b('dark_oak_log'), b('oak_leaves'), 'droop', 7, 11, scatter(count(3, 5)), hang=w((('cave_vines', {'berries': True}), 1), ('pale_hanging_moss', 1)), hang_chance=0.07)
mushroom('gloomcap', b('mushroom_stem'), w(('amethyst_block', 5), ('purple_terracotta', 1)), scatter(rarity(2)), shape='bell', lo=7, hi=14, clo=3, chi=6,
         spots=s('pearlescent_froglight'), spot_chance=0.1)
ruin('gloom_ruin', w(('deepslate_bricks', 3), ('cracked_deepslate_bricks', 2), ('cobbled_deepslate', 1)), w(('deepslate_tiles', 2), ('cobbled_deepslate', 1)),
     'walls', scatter(rarity(45)), accent=s('soul_lantern', hanging=False))

tree('petal_tree', b('cherry_log'), b('cherry_leaves'), 'round', 5, 8, scatter(count(2, 4)), extra=s('flowering_azalea_leaves'), extra_chance=0.06)
tree('petal_weeper', b('cherry_log'), b('cherry_leaves'), 'droop', 6, 9, scatter(rarity(2)))
outcrop('petal_boulder', MOSSY_BOULDER, scatter(rarity(6)), cap=s('moss_block'), cap_chance=0.8)
ruin('petal_shrine', w(('stone_bricks', 2), ('mossy_stone_bricks', 1)), w(('cherry_planks', 2), ('stone_bricks', 1)), 'shrine', scatter(rarity(45)), accent=LANTERN)

tree('frost_pine', b('spruce_log'), b('spruce_leaves'), 'cone', 9, 16, scatter(count(4, 7)), snow=True)
tree('frost_tall', b('spruce_log'), b('spruce_leaves'), 'cone', 18, 26, scatter(count(0, 1)), snow=True)
outcrop('frost_boulder', w(('stone', 2), ('packed_ice', 2), ('cobblestone', 1)), scatter(rarity(4)), cap=s('snow_block'), cap_chance=0.9)
ruin('frost_tower', w(('stone_bricks', 3), ('cracked_stone_bricks', 1), ('packed_ice', 1)), w(('spruce_planks', 2), ('stone_bricks', 1)), 'tower',
     scatter(rarity(50)), accent=LANTERN)

spire('ice_spire', w(('packed_ice', 4), ('blue_ice', 1), ('ice', 1)), scatter(rarity(3)), style='shard', lo=10, hi=24, rlo=1.6, rhi=3.0, tip=s('snow_block'))
spire('ice_spire_huge', w(('packed_ice', 4), ('blue_ice', 2)), scatter(count(1, 2)), style='shard', lo=22, hi=38, rlo=2.6, rhi=4.0, tip=s('snow_block'))
bones('frost_bones', scatter(rarity(12)))
# Duskpine Weald: dark, snowless conifers, moss and amber waylights.
tree('duskpine', b('spruce_log'), w(('spruce_leaves', 4), ('azalea_leaves', 1)), 'cone', 11, 19, scatter(count(5, 8)), hang=s('pale_hanging_moss'), hang_chance=0.05)
tree('duskpine_elder', b('spruce_log'), b('spruce_leaves'), 'giant', 22, 32, scatter(rarity(3)), hang=s('pale_hanging_moss'), hang_chance=0.08)
outcrop('duskpine_boulder', w(('mossy_cobblestone', 3), ('stone', 2), ('moss_block', 1)), scatter(rarity(3)), cap=s('moss_carpet'), cap_chance=0.7)
ruin('duskpine_waylight', w(('mossy_stone_bricks', 3), ('stone_bricks', 1)), w(('mossy_cobblestone', 1), ('podzol', 1)), 'shrine', scatter(rarity(28)),
     accent=LANTERN, loot='afterworld:chests/ruin_cache')
# Glimmerfrost: blue crystal clusters that glow under the snow.
spire('glimmer_crystal', w(('blue_ice', 3), ('packed_ice', 2), ('light_blue_stained_glass', 1)), scatter(count(0, 2)), style='shard', lo=5, hi=12,
      rlo=1.0, rhi=2.2, tip=s('sea_lantern'), glow=s('sea_lantern'), glow_chance=0.1)
outcrop('glimmer_boulder', w(('snow_block', 2), ('packed_ice', 2), ('blue_ice', 1)), scatter(rarity(3)), cap=s('snow_block'), cap_chance=0.8)
ruin('glimmer_shrine', w(('polished_diorite', 3), ('calcite', 2), ('packed_ice', 1)), w(('snow_block', 1), ('calcite', 1)), 'colonnade', scatter(rarity(30)),
     accent=s('soul_lantern', hanging=False))
outcrop('ice_boulder', w(('packed_ice', 3), ('snow_block', 1), ('blue_ice', 1)), scatter(rarity(5)), cap=s('snow_block'))
ruin('frost_ruin', w(('stone_bricks', 3), ('packed_ice', 1)), w(('snow_block', 1), ('stone_bricks', 1)), 'walls', scatter(rarity(45)), accent=LANTERN)

spire('crystal_shard', w(('amethyst_block', 3), ('calcite', 1)), scatter(rarity(3)), style='shard', lo=6, hi=14, rlo=1.0, rhi=2.0)
tree('peak_pine', b('spruce_log'), b('spruce_leaves'), 'cone', 6, 10, scatter(count(0, 2)), snow=True)
spire('sky_cairn', w(('cobblestone', 2), ('stone', 1), ('mossy_cobblestone', 1)), scatter(rarity(7)), lo=2, hi=4, rlo=0.8, rhi=1.2)
ruin('peak_tower', w(('stone_bricks', 3), ('cracked_stone_bricks', 2)), STONE_FLOOR, 'tower', scatter(rarity(80)), accent=LANTERN)

tree('windworn_tree', b('oak_log'), b('oak_leaves'), 'twisted', 4, 7, scatter(count(0, 2)))
outcrop('highland_boulder', w(('stone', 3), ('andesite', 2), ('granite', 1), ('cobblestone', 1)), scatter(rarity(2)), cap=s('moss_block'), cap_chance=0.3, lo=2.0, hi=4.0)
ruin('highland_tower', w(('cobblestone', 2), ('stone_bricks', 2), ('mossy_cobblestone', 1)), STONE_FLOOR, 'tower', scatter(rarity(45)), accent=LANTERN)

tree('ember_tree', b('acacia_log'), b('acacia_leaves'), 'flat', 6, 9, scatter(count(0, 2)))
tree('ember_poplar', b('poplar_log'), w(('orange_poplar_leaves', 3), ('red_poplar_leaves', 1), ('yellow_poplar_leaves', 1)), 'column', 7, 11, scatter(rarity(2)))
spire('termite_mound', w(('packed_mud', 2), ('mud_bricks', 1), ('terracotta', 1), ('brown_terracotta', 1)), scatter(rarity(3)), lo=4, hi=9, rlo=1.4, rhi=2.4)
outcrop('steppe_rock', w(('granite', 2), ('terracotta', 1), ('coarse_dirt', 1)), scatter(rarity(6)), lo=1.4, hi=2.6)
ruin('steppe_ruin', w(('mud_bricks', 3), ('packed_mud', 2), ('terracotta', 1)), w(('packed_mud', 2), ('coarse_dirt', 1)), 'walls', scatter(rarity(45)), accent=LANTERN)

spire('dune_obelisk', w(('cut_sandstone', 3), ('smooth_sandstone', 2), ('chiseled_sandstone', 1)), scatter(rarity(7)), lo=8, hi=16, rlo=0.9, rhi=1.4,
      tip=s('waxed_cut_copper'))
bones('dune_bones', scatter(rarity(10)))
outcrop('dune_rock', w(('sandstone', 3), ('smooth_sandstone', 1), ('cut_sandstone', 1)), scatter(rarity(5)), cap=s('sand'), cap_chance=0.5)
ruin('dune_colonnade', w(('cut_sandstone', 3), ('chiseled_sandstone', 1), ('smooth_sandstone', 1)), w(('smooth_sandstone', 2), ('sandstone', 1)),
     'colonnade', scatter(rarity(25)), accent=s('waxed_copper_lantern', hanging=False))
ruin('dune_clockwork', w(('cut_sandstone', 3), ('waxed_cut_copper', 1), ('waxed_exposed_cut_copper', 1)), w(('smooth_sandstone', 1),),
     'walls', scatter(rarity(35)), accent=s('waxed_copper_bulb', lit=True), loot='afterworld:chests/clockwork_cache', loot_chance=0.5)

tree('petrified_tree', b('stripped_acacia_log'), b('acacia_leaves'), 'dead', 5, 9, scatter(rarity(4)))
spire('rust_spire', w(('terracotta', 2), ('red_terracotta', 2), ('orange_terracotta', 1), ('red_sandstone', 2)), scatter(rarity(5)), style='columns',
      lo=6, hi=14, rlo=2.0, rhi=3.5, glow=s('raw_copper_block'), glow_chance=0.05)
ruin('rust_ruin', w(('cut_red_sandstone', 3), ('red_sandstone', 2), ('waxed_exposed_cut_copper', 1)), w(('red_sandstone', 1),), 'walls',
     scatter(rarity(40)), accent=s('waxed_exposed_copper_lantern', hanging=False))
bones('rust_bones', scatter(rarity(20)))

tree('tangle_giant', b('jungle_log'), b('jungle_leaves'), 'giant', 18, 28, scatter(count(1, 2)), extra=s('flowering_azalea_leaves'), extra_chance=0.08)
tree('tangle_tree', b('jungle_log'), b('jungle_leaves'), 'round', 6, 10, scatter(count(3, 5)), extra=s('azalea_leaves'), extra_chance=0.1)
ruin('tangle_colonnade', w(('mossy_stone_bricks', 3), ('mossy_cobblestone', 2), ('cracked_stone_bricks', 1)), w(('mossy_cobblestone', 2), ('moss_block', 1)),
     'colonnade', scatter(rarity(30)), accent=s('verdant_froglight'))
mushroom('tangle_lamp', b('mushroom_stem'), w(('red_mushroom_block', 1),), scatter(rarity(9)), lo=4, hi=7, clo=2, chi=3, spots=s('verdant_froglight'), spot_chance=0.25)

mushroom('glowcap', b('mushroom_stem'), w(('warped_wart_block', 4), ('mushroom_stem', 1)), scatter(count(1, 2)), lo=6, hi=13, clo=3, chi=6,
         spots=s('shroomlight'), spot_chance=0.14)
mushroom('redcap', b('mushroom_stem'), w(('red_mushroom_block', 1),), scatter(rarity(2)), shape='flat', lo=5, hi=9, clo=3, chi=4)
spire('sporelamp', w(('mushroom_stem', 1),), scatter(rarity(3)), lo=3, hi=6, rlo=0.4, rhi=0.6, tip=s('shroomlight'))

ruin('sunken_colonnade', w(('prismarine_bricks', 2), ('stone_bricks', 2), ('mossy_stone_bricks', 1)), w(('prismarine', 1), ('stone_bricks', 1)),
     'colonnade', scatter(rarity(14), 'OCEAN_FLOOR_WG'), accent=s('sea_lantern'), loot='afterworld:chests/sunken_cache', loot_chance=0.45, underwater=True)
spire('sea_spire', w(('prismarine', 3), ('dark_prismarine', 1), ('prismarine_bricks', 1)), scatter(rarity(5), 'OCEAN_FLOOR_WG'), style='shard',
      lo=8, hi=18, rlo=1.0, rhi=2.0, glow=s('sea_lantern'), glow_chance=0.08)
spire('coral_spire', w(('brain_coral_block', 2), ('horn_coral_block', 2), ('fire_coral_block', 1), ('tube_coral_block', 2), ('bubble_coral_block', 1)),
      scatter(rarity(2), 'OCEAN_FLOOR_WG'), style='shard', lo=5, hi=11, rlo=1.0, rhi=1.8)
spire('rime_spire', w(('packed_ice', 4), ('blue_ice', 1)), scatter(rarity(6), 'OCEAN_FLOOR_WG'), style='shard', lo=14, hi=26, rlo=1.6, rhi=2.8)
outcrop('river_stones', w(('stone', 2), ('mossy_cobblestone', 1), ('gravel', 1)), scatter(rarity(7)), lo=1.0, hi=1.8, lumps=2)
bones('shore_bones', scatter(rarity(7)), lo=12, hi=20)
tree('driftwood', b('stripped_birch_log'), b('oak_leaves'), 'dead', 3, 5, scatter(rarity(6)))
outcrop('shore_rock', w(('stone', 2), ('tuff', 1), ('cobblestone', 1)), scatter(rarity(5)), lo=1.2, hi=2.2)
spire('basalt_columns', w((('basalt', {'axis': 'y'}), 3), (('polished_basalt', {'axis': 'y'}), 1), ('smooth_basalt', 1)), scatter(rarity(2)), style='columns',
      lo=4, hi=12, rlo=2.0, rhi=4.0)

# The four first biomes get ruins and caves too.
ruin('moor_ruin', w(('cobblestone', 2), ('mossy_cobblestone', 1), ('tuff_bricks', 1)), w(('gravel', 1), ('cobblestone', 1)), 'walls', scatter(rarity(40)), accent=LANTERN)
ruin('fen_shrine', w(('mud_bricks', 2), ('mossy_cobblestone', 1)), w(('mud_bricks', 1),), 'shrine', scatter(rarity(40)), accent=CANDLES, loot='afterworld:chests/shrine_cache')

WILDS = {'default': {'max_delay': 24000, 'min_delay': 12000, 'sound': 'afterworld:music.wilds'}}


# ---------------------------------------------------------------- who lives where
# Afterworld's natives by biome: (entity, weight, min, max) per spawn category. Vanilla's own are
# kept but thinned (VANILLA_SCALE), so most of what you meet belongs to this world. Monsters marked
# "roaming" in AwEntities also walk by day, thinly; the rest keep to the dark.
VANILLA_SCALE = {'monster': 0.3, 'creature': 0.45, 'ambient': 0.5}
LAND_HERDS = 0.14   # chance a fresh chunk starts with a group of wildlife (vanilla: 0.1)
M, C, A, WA, WC = 'monster', 'creature', 'ambient', 'water_ambient', 'water_creature'
NATIVE_SPAWNS = {
    'ashen_moor': {M: [('ashen_husk', 60, 1, 3), ('clockwork_sentry', 30, 1, 2), ('gearhound', 16, 2, 3), ('rule_wraith', 10, 1, 1)],
                   C: [('crow', 18, 2, 4), ('emberfox', 10, 1, 2)], A: [('lantern_moth', 4, 1, 2)]},
    'hollowwood': {M: [('echo_stalker', 35, 1, 2), ('rule_wraith', 12, 1, 1)],
                   C: [('hollow_stag', 14, 1, 3), ('mossback', 8, 1, 2), ('crow', 4, 1, 2)], A: [('lantern_moth', 14, 1, 3), ('wisp', 6, 1, 1)]},
    'shardfields': {M: [('shardling', 60, 1, 3), ('rule_wraith', 30, 1, 2), ('clockwork_sentry', 10, 1, 1)],
                    C: [('hollow_stag', 8, 1, 2), ('crow', 4, 1, 2)], A: [('lantern_moth', 8, 1, 2)]},
    'gloamfen': {M: [('bog_lurker', 45, 1, 2), ('rule_wraith', 20, 1, 2), ('echo_stalker', 10, 1, 1)],
                 C: [('mossback', 6, 1, 2)], A: [('wisp', 12, 1, 2), ('lantern_moth', 12, 1, 3)]},
    'vesper_downs': {M: [('ashen_husk', 15, 1, 2), ('gearhound', 10, 2, 3), ('rule_wraith', 8, 1, 1)],
                     C: [('crow', 10, 2, 3), ('hollow_stag', 6, 1, 2), ('emberfox', 4, 1, 2)], A: [('lantern_moth', 4, 1, 2)]},
    'mossmere_wood': {M: [('echo_stalker', 15, 1, 2), ('bog_lurker', 10, 1, 1)],
                      C: [('mossback', 8, 1, 2), ('hollow_stag', 8, 1, 2), ('crow', 4, 1, 2)], A: [('lantern_moth', 6, 1, 2)]},
    'pale_reach': {M: [('echo_stalker', 20, 1, 2), ('rule_wraith', 15, 1, 1)],
                   C: [('hollow_stag', 8, 1, 2)], A: [('petal_sprite', 8, 1, 3), ('wisp', 4, 1, 1)]},
    'gloomwald': {M: [('echo_stalker', 40, 1, 2), ('rule_wraith', 15, 1, 1)],
                  C: [('crow', 4, 1, 2)], A: [('lantern_moth', 12, 1, 3), ('wisp', 6, 1, 1)]},
    'petalfall': {M: [('echo_stalker', 10, 1, 1), ('shardling', 8, 1, 2)],
                  C: [('hollow_stag', 6, 1, 2)], A: [('petal_sprite', 20, 2, 4)]},
    'frostmere_pines': {M: [('rime_wight', 40, 1, 2), ('echo_stalker', 10, 1, 1)],
                        C: [('rimehorn', 12, 2, 4), ('hollow_stag', 6, 1, 2)]},
    'whitewaste': {M: [('rime_wight', 50, 1, 2)], C: [('rimehorn', 16, 2, 5)]},
    'rimeglass_spires': {M: [('rime_wight', 40, 1, 2), ('shardling', 30, 1, 3)], C: [('rimehorn', 6, 1, 3)]},
    'skyspire_peaks': {M: [('rime_wight', 30, 1, 2)], C: [('rimehorn', 8, 2, 3), ('hollow_stag', 4, 1, 2)]},
    'windworn_highlands': {M: [('gearhound', 15, 2, 3), ('rule_wraith', 10, 1, 1), ('rime_wight', 8, 1, 1)],
                           C: [('hollow_stag', 8, 1, 3), ('crow', 6, 2, 3), ('rimehorn', 4, 1, 3)]},
    'ember_steppe': {M: [('ashen_husk', 50, 1, 3), ('gearhound', 20, 2, 3)], C: [('emberfox', 16, 1, 3), ('crow', 8, 2, 3)]},
    'hourglass_dunes': {M: [('gearhound', 40, 2, 4), ('clockwork_sentry', 25, 1, 2)], C: [('emberfox', 4, 1, 2)]},
    'rustscar_canyons': {M: [('gearhound', 45, 2, 4), ('clockwork_sentry', 30, 1, 2), ('ashen_husk', 10, 1, 2)],
                         C: [('emberfox', 6, 1, 2), ('crow', 6, 2, 3)]},
    'verdant_tangle': {M: [('bog_lurker', 30, 1, 2), ('echo_stalker', 15, 1, 1)],
                       C: [('mossback', 6, 1, 2)], A: [('petal_sprite', 6, 1, 3), ('wisp', 4, 1, 1)]},
    'sporeveil': {A: [('wisp', 10, 1, 2), ('lantern_moth', 10, 1, 3)]},
    'mirrorsea': {WA: [('glimmerfin', 15, 4, 8)], WC: [('mirror_ray', 4, 1, 2)]},
    'mirrordeep': {WC: [('mirror_ray', 6, 1, 2)]},
    'ember_shallows': {WA: [('glimmerfin', 20, 4, 8)], WC: [('mirror_ray', 4, 1, 1)]},
    'silverrun': {WA: [('glimmerfin', 25, 4, 8)]},
    'bone_shore': {C: [('crow', 6, 1, 3)]},
    'breakstone_shore': {C: [('crow', 4, 1, 2)]},
    'echo_hollows': {M: [('echo_stalker', 40, 1, 2), ('shardling', 30, 1, 3), ('clockwork_sentry', 10, 1, 1)], A: [('wisp', 4, 1, 1)]},
    'rootways': {M: [('bog_lurker', 15, 1, 1), ('echo_stalker', 15, 1, 1)], A: [('wisp', 8, 1, 2)]},
    'duskpine_weald': {M: [('echo_stalker', 20, 1, 1), ('rule_wraith', 10, 1, 1)],
                       C: [('hollow_stag', 10, 1, 3), ('emberfox', 4, 1, 2), ('mossback', 4, 1, 2)], A: [('lantern_moth', 10, 1, 3), ('wisp', 3, 1, 1)]},
    'glimmerfrost': {M: [('rime_wight', 40, 1, 2), ('shardling', 15, 1, 2)], C: [('rimehorn', 14, 2, 4)], A: [('wisp', 4, 1, 1)]},
    'rimefloe': {M: [('rime_wight', 10, 1, 1)]},
}


# The second bestiary, by biome: every land gets its own wildlife and something to fear.
V2_SPAWNS = {
    'ashen_moor': {M: [('cinder_beetle', 40, 1, 3), ('ash_wyrmling', 12, 1, 1)]},
    'ember_steppe': {M: [('cinder_beetle', 40, 1, 3), ('ash_wyrmling', 10, 1, 1)], C: [('ember_salamander', 10, 1, 2), ('canyon_vulture', 6, 1, 2)]},
    'ember_shallows': {C: [('ember_salamander', 12, 1, 2)], WA: [('tidecaller_jelly', 10, 1, 3)]},
    'hourglass_dunes': {M: [('sand_sifter', 30, 1, 1), ('glyph_scarab', 25, 2, 4)], C: [('dune_strider', 14, 2, 4), ('canyon_vulture', 6, 1, 2)]},
    'rustscar_canyons': {M: [('rust_golemite', 25, 1, 1), ('sand_sifter', 15, 1, 1)], C: [('canyon_vulture', 10, 1, 2)]},
    'frostmere_pines': {C: [('snow_hare', 12, 2, 3), ('duskpine_lynx', 6, 1, 1)], A: [('rime_moth', 8, 1, 3)]},
    'whitewaste': {M: [('rime_brute', 25, 1, 1)], C: [('snow_hare', 10, 2, 3)]},
    'glimmerfrost': {M: [('ice_crawler', 30, 1, 2)], A: [('rime_moth', 10, 2, 4)]},
    'rimeglass_spires': {M: [('ice_crawler', 30, 1, 2), ('prism_golem', 10, 1, 1)]},
    'skyspire_peaks': {C: [('windrider_eagle', 8, 1, 1), ('highland_ram', 8, 2, 3), ('snow_hare', 6, 1, 2)]},
    'windworn_highlands': {M: [('dusk_hound', 15, 2, 4)], C: [('highland_ram', 12, 2, 4), ('windrider_eagle', 5, 1, 1)]},
    'rimefloe': {C: [('floe_seal', 14, 2, 4)]},
    'rimesea': {WC: [('tusked_narwhal', 6, 1, 3)], WA: [('tidecaller_jelly', 4, 1, 2)]},
    'rimedeep': {WC: [('tusked_narwhal', 5, 1, 2), ('abyssal_angler', 4, 1, 1)]},
    'mirrorsea': {WA: [('tidecaller_jelly', 10, 2, 4)]},
    'mirrordeep': {WC: [('abyssal_angler', 6, 1, 1)]},
    'silverrun': {C: [('silverrun_otter', 10, 1, 3), ('blossom_crane', 6, 1, 2)]},
    'bone_shore': {M: [('drowned_revenant', 25, 1, 2)], C: [('crystal_crab', 10, 1, 3), ('bone_gull', 10, 2, 4)]},
    'breakstone_shore': {M: [('drowned_revenant', 15, 1, 1)], C: [('crystal_crab', 12, 1, 3), ('bone_gull', 8, 2, 3)]},
    'gloamfen': {M: [('mire_shambler', 30, 1, 2)], C: [('fen_toad', 12, 1, 2)]},
    'verdant_tangle': {M: [('vine_strangler', 20, 1, 1)], C: [('thornback_boar', 10, 2, 3), ('fen_toad', 6, 1, 2)]},
    'mossmere_wood': {C: [('thornback_boar', 8, 1, 3)], A: [('mossmere_owl', 10, 1, 2)]},
    'duskpine_weald': {C: [('duskpine_lynx', 8, 1, 2), ('thornback_boar', 6, 1, 2)], A: [('mossmere_owl', 8, 1, 2)]},
    'hollowwood': {M: [('rootling_sentinel', 15, 1, 1)], A: [('mossmere_owl', 8, 1, 1)]},
    'rootways': {M: [('rootling_sentinel', 25, 1, 1), ('echo_bat', 15, 1, 2)], C: [('sporeling', 8, 1, 3)]},
    'echo_hollows': {M: [('echo_bat', 30, 2, 3)]},
    'gloomwald': {M: [('gloomweaver', 30, 1, 2), ('mourning_shade', 10, 1, 1)]},
    'pale_reach': {M: [('mourning_shade', 25, 1, 1)]},
    'petalfall': {C: [('kitsune', 8, 1, 1), ('blossom_crane', 10, 1, 3)]},
    'sporeveil': {M: [('fungal_brute', 25, 1, 1)], C: [('sporeling', 16, 2, 4)]},
    'shardfields': {M: [('prism_golem', 15, 1, 1)]},
    'vesper_downs': {M: [('dusk_hound', 25, 2, 4)]},
}
for _b, _cats in V2_SPAWNS.items():
    for _cat, _entries in _cats.items():
        NATIVE_SPAWNS.setdefault(_b, {}).setdefault(_cat, []).extend(_entries)


def spawns(name, host):
    """The host biome's spawns, thinned, with this biome's natives added."""
    arg = json.loads(json.dumps(host['argument'])) if host else {'spawn_costs': {}, 'spawns_by_category': {}}
    cats = arg.setdefault('spawns_by_category', {})
    for cat, scale in VANILLA_SCALE.items():
        for e in cats.get(cat, []):
            e['weight'] = max(1, round(e['weight'] * scale))
    for cat, entries in NATIVE_SPAWNS.get(name, {}).items():
        out = cats.setdefault(cat, [])
        for ent, weight, lo, hi in entries:
            count = lo if lo == hi else {'type': 'minecraft:uniform', 'min_inclusive': lo, 'max_inclusive': hi}
            out.append({'type': f'{OUR}:{ent}', 'count': count, 'weight': weight})
    return {'argument': arg, 'modifier': host.get('modifier', 'overlay') if host else 'overlay'}


def clear_water(hexcol):
    """Water fog bright enough to see through: same hue, but never murky."""
    import colorsys
    r, g, b = (int(hexcol[i:i + 2], 16) / 255 for i in (1, 3, 5))
    h, sat, v = colorsys.rgb_to_hsv(r, g, b)
    v = max(v, 0.48)
    sat = min(max(sat, 0.35), 0.7)
    r, g, b = colorsys.hsv_to_rgb(h, sat, v)
    return '#%02x%02x%02x' % (int(r * 255), int(g * 255), int(b * 255))


def particles(*entries):
    return {'argument': [{'particle': p, 'probability': pr} for p, pr in entries], 'modifier': 'append'}


def mote(color):
    return {'type': 'afterworld:mote', 'color': color}


def ember(color=-30150):
    return {'type': 'afterworld:ember', 'color': color}


def argb(rgb):
    v = 0xFF000000 | rgb
    return v - (1 << 32)


# ---------------------------------------------------------------- the biomes
# sources: the vanilla biomes it replaces (for tags). ground: Ground config (None for the four
# first biomes, which use their hand-written painters). feats: our step-9 features. caves: step 7.

BIOMES = {
    # ---- the first four
    'ashen_moor': dict(host='plains', sources=['plains', 'savanna'], temperature=0.7, downfall=0.25,
        effects={'grass_color': '#8c8a62', 'foliage_color': '#7d7752', 'dry_foliage_color': '#8a6f4a', 'water_color': '#55707a'},
        sky='#9eaab0', fog='#c4b8a2', water_fog='#3a4a50',
        particles=particles(({'type': 'minecraft:white_ash'}, 0.0018), (ember(), 0.0006)),
        surface='ashen_surface', feats=['moor_arch', 'standing_stones', 'ash_vent', 'ashen_tree', 'moor_ruin', 'basalt_columns'], caves=['cave_ember'],
        vanilla=['minecraft:glow_lichen', 'minecraft:patch_grass_plain', 'minecraft:patch_dead_bush', 'minecraft:forest_rock',
                 'minecraft:patch_bush', 'minecraft:brown_mushroom_normal', 'minecraft:patch_sugar_cane']),
    'hollowwood': dict(host='dark_forest', sources=['forest', 'dark_forest'], temperature=0.6, downfall=0.9,
        effects={'grass_color': '#3f6b3c', 'foliage_color': '#2f5a35', 'dry_foliage_color': '#5a4630', 'water_color': '#2e5e5a'},
        sky='#6c9ad0', fog='#8fb3a6', water_fog='#1f3d3a',
        particles=particles(({'type': 'minecraft:spore_blossom_air'}, 0.006)),
        surface='hollow_surface', feats=['hollow_giant', 'mossy_boulder'], caves=['cave_rootways'],
        vanilla=['minecraft:glow_lichen', 'minecraft:dark_forest_vegetation', 'minecraft:patch_grass_forest', 'minecraft:patch_large_fern',
                 'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal', 'minecraft:patch_leaf_litter', 'minecraft:fallen_oak_tree',
                 'minecraft:patch_firefly_bush_near_water']),
    'shardfields': dict(host='meadow', sources=['meadow'], temperature=0.7, downfall=0.6,
        effects={'grass_color': '#8fd39b', 'foliage_color': '#7cc49a', 'water_color': '#4fb3c9'},
        sky='#8ec6f2', fog='#d6e6ee', water_fog='#2a6070',
        particles=particles((mote(-3607297), 0.004)),
        surface='shard_surface', feats=['floating_island', 'crystal_spire', 'crystal_shard'], caves=['cave_glimmer'],
        vanilla=['minecraft:glow_lichen', 'minecraft:patch_tall_grass_2', 'minecraft:patch_grass_meadow', 'minecraft:flower_meadow',
                 'minecraft:trees_meadow', 'minecraft:wildflowers_meadow']),
    'gloamfen': dict(host='swamp', sources=['swamp', 'mangrove_swamp'], temperature=0.8, downfall=0.9,
        effects={'grass_color': '#56663a', 'foliage_color': '#4a5a30', 'dry_foliage_color': '#5a4a30', 'water_color': '#2f4a3e'},
        sky='#5f6f80', fog='#66766a', water_fog='#1e2c26', extra_attrs={'minecraft:visual/water_fog_end_distance': {'argument': 0.6, 'modifier': 'multiply'}},
        particles=particles(({'type': 'minecraft:firefly'}, 0.0025), (mote(-6291574), 0.0012)),
        surface='fen_surface', feats=['fen_causeway', 'fen_tree', 'fen_shrine'], caves=['cave_rootways'],
        vanilla=['minecraft:glow_lichen', 'minecraft:trees_swamp', 'minecraft:patch_grass_normal', 'minecraft:patch_waterlily',
                 'minecraft:brown_mushroom_swamp', 'minecraft:red_mushroom_swamp', 'minecraft:patch_sugar_cane_swamp',
                 'minecraft:patch_firefly_bush_swamp', 'minecraft:patch_firefly_bush_near_water_swamp', 'minecraft:seagrass_swamp']),

    # ---- the rest of the land
    'vesper_downs': dict(host='plains', sources=['plains', 'sunflower_plains'], temperature=0.75, downfall=0.5,
        effects={'grass_color': '#a7b77c', 'foliage_color': '#8fa36a', 'water_color': '#4c7f9a'},
        sky='#a9c4e0', fog='#e7d9c6', water_fog='#2b4f60',
        particles=particles(({'type': 'minecraft:firefly'}, 0.0012), (mote(argb(0xFFE6B0)), 0.0008)),
        ground=dict(surface=[layer('coarse_dirt', 0, 0.8), layer('moss_block', 1, 0.0, 0.07), layer('gravel', 1, 0.94)],
                    plants=[plant('short_grass', 0.26), plant('tall_grass', 0.04, half='lower'), plant('wildflowers', 0.03), plant('lilac', 0.006, half='lower'),
                            plant('cornflower', 0.007), plant('oxeye_daisy', 0.006), plant('bush', 0.012), plant('firefly_bush', 0.003)]),
        feats=['downs_oak', 'downs_boulder', 'downs_ruin', 'downs_well', 'rune_menhir', 'standing_stones_rare'], caves=['cave_glimmer'],
        vanilla=['minecraft:patch_sugar_cane', 'minecraft:patch_pumpkin']),
    'mossmere_wood': dict(host='forest', sources=['forest'], temperature=0.7, downfall=0.8,
        effects={'grass_color': '#5a8a3f', 'foliage_color': '#4c7d33', 'water_color': '#3d6e66'},
        sky='#8fb8d8', fog='#b9d0c0', water_fog='#21403b',
        particles=particles(({'type': 'minecraft:spore_blossom_air'}, 0.002), ({'type': 'minecraft:firefly'}, 0.0006)),
        ground=dict(surface=[layer('moss_block', 0, 0.7), layer('podzol', 0, 0.0, 0.2), layer('coarse_dirt', 1, 0.93)],
                    plants=[plant('fern', 0.08), plant('short_grass', 0.12), plant('moss_carpet', 0.05), plant('leaf_litter', 0.06),
                            plant('brown_mushroom', 0.006), plant('red_mushroom', 0.004), plant('large_fern', 0.015, half='lower'), plant('firefly_bush', 0.003)]),
        feats=['mossy_giant', 'mossy_oak', 'mossy_twist', 'mossy_boulder', 'mossy_ruin'], caves=['cave_rootways'],
        vanilla=['minecraft:fallen_oak_tree', 'minecraft:patch_leaf_litter', 'minecraft:brown_mushroom_normal']),
    'pale_reach': dict(host='birch_forest', sources=['birch_forest', 'old_growth_birch_forest', 'pale_garden'], temperature=0.6, downfall=0.6,
        effects={'grass_color': '#b9c8b4', 'foliage_color': '#e3ebe6', 'water_color': '#6a8f9a'},
        sky='#c9d4dc', fog='#e2e6e8', water_fog='#33494f',
        particles=particles((mote(argb(0xF2F2F2)), 0.0015), ({'type': 'minecraft:white_ash'}, 0.0004)),
        ground=dict(surface=[layer('pale_moss_block', 0, 0.74), layer('calcite', 1, 0.96)],
                    plants=[plant('short_grass', 0.18), plant('pale_moss_carpet', 0.06), plant('lily_of_the_valley', 0.01), plant('white_tulip', 0.006),
                            plant('azure_bluet', 0.008), plant('open_eyeblossom', 0.004)]),
        feats=['pale_tree', 'pale_spire_tree', 'pale_headstone', 'pale_shrine'], caves=['cave_glimmer'],
        vanilla=['minecraft:fallen_birch_tree']),
    'gloomwald': dict(host='dark_forest', sources=['dark_forest'], temperature=0.6, downfall=0.9,
        effects={'grass_color': '#4a5a3c', 'foliage_color': '#6a4c8c', 'water_color': '#3a3f66'},
        sky='#6f6f9a', fog='#4f4a66', water_fog='#1f1f33',
        particles=particles((mote(argb(0xB48CFF)), 0.002), ({'type': 'minecraft:spore_blossom_air'}, 0.001)),
        ground=dict(surface=[layer('podzol', 0, 0.0, 0.35), layer('moss_block', 0, 0.75), layer('mycelium', 1, 0.9)],
                    plants=[plant('fern', 0.06), plant('red_mushroom', 0.01), plant('brown_mushroom', 0.01), plant('large_fern', 0.01, half='lower'),
                            plant('short_grass', 0.05)]),
        feats=['gloom_tree', 'gloomcap', 'gloom_ruin'], caves=['cave_spore'],
        vanilla=['minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal']),
    'petalfall': dict(host='cherry_grove', sources=['cherry_grove', 'flower_forest'], temperature=0.5, downfall=0.8,
        effects={'grass_color': '#8fc27a', 'foliage_color': '#e6a6c8', 'water_color': '#5db7d0'},
        sky='#b4d2f0', fog='#f2d6e4', water_fog='#2a5868',
        particles=particles(({'type': 'minecraft:cherry_leaves'}, 0.004)),
        ground=dict(surface=[layer('moss_block', 1, 0.0, 0.05)],
                    plants=[plant('pink_petals', 0.12, flower_amount=4), plant('short_grass', 0.15), plant('allium', 0.01), plant('peony', 0.006, half='lower'),
                            plant('lilac', 0.006, half='lower'), plant('wildflowers', 0.03)]),
        feats=['petal_tree', 'petal_weeper', 'petal_boulder', 'petal_shrine'], caves=['cave_rootways'],
        vanilla=['minecraft:flower_cherry']),
    'frostmere_pines': dict(host='snowy_taiga', sources=['snowy_taiga'], temperature=0.05, downfall=0.6,
        effects={'grass_color': '#5f8a7a', 'foliage_color': '#3f6e66', 'water_color': '#3f6a8a'},
        sky='#8fb0d0', fog='#cfdce6', water_fog='#1f3550',
        particles=particles(({'type': 'minecraft:snowflake'}, 0.002), (mote(argb(0x8FF0FF)), 0.0005)),
        ground=dict(surface=[layer('podzol', 0, 0.0, 0.3), layer('coarse_dirt', 1, 0.9), layer('snow_block', 0, 0.85)],
                    plants=[plant('fern', 0.08), plant('large_fern', 0.02, half='lower'), plant('sweet_berry_bush', 0.01, age=3), plant('short_grass', 0.05)]),
        feats=['frost_pine', 'frost_tall', 'frost_boulder', 'frost_tower'], caves=['cave_frost'],
        vanilla=['minecraft:fallen_spruce_tree']),
    'duskpine_weald': dict(host='taiga', sources=['taiga', 'old_growth_pine_taiga', 'old_growth_spruce_taiga'], temperature=0.3, downfall=0.8,
        effects={'grass_color': '#5f8a4a', 'foliage_color': '#3f6a3a', 'water_color': '#3f7a8a'},
        sky='#8fb8c8', fog='#c8d8c8', water_fog='#1f4a50',
        particles=particles(({'type': 'minecraft:firefly'}, 0.0008), (mote(argb(0xFFC860)), 0.0004)),
        ground=dict(surface=[layer('podzol', 0, 0.0, 0.45), layer('moss_block', 1, 0.82), layer('coarse_dirt', 2, 0.9)],
                    plants=[plant('fern', 0.12), plant('large_fern', 0.03, half='lower'), plant('sweet_berry_bush', 0.012, age=3),
                            plant('brown_mushroom', 0.01), plant('short_grass', 0.06)]),
        feats=['duskpine', 'duskpine_elder', 'duskpine_boulder', 'duskpine_waylight'], caves=['cave_glimmer'],
        vanilla=['minecraft:fallen_spruce_tree']),
    'glimmerfrost': dict(host='snowy_plains', sources=['snowy_plains'], temperature=-0.2, downfall=0.5,
        effects={'grass_color': '#a0b8b0', 'foliage_color': '#8aa49c', 'water_color': '#4a7ad6'},
        sky='#b8d0ee', fog='#e8f0fa', water_fog='#2a4a80',
        particles=particles(({'type': 'minecraft:snowflake'}, 0.003), (mote(argb(0x8FE8FF)), 0.0012)),
        ground=dict(surface=[layer('snow_block', 0, 0.55), layer('packed_ice', 1, 0.9), layer('blue_ice', 2, 0.97)], plants=[plant('short_grass', 0.015)]),
        feats=['glimmer_crystal', 'glimmer_boulder', 'glimmer_shrine', 'frost_bones'], caves=['cave_frost'], vanilla=[]),
    'whitewaste': dict(host='snowy_plains', sources=['snowy_plains'], temperature=-0.3, downfall=0.5,
        effects={'grass_color': '#a0b8b0', 'foliage_color': '#8aa49c', 'water_color': '#3d57d6'},
        sky='#c0d4ea', fog='#f0f4f8', water_fog='#1f2f60',
        particles=particles(({'type': 'minecraft:snowflake'}, 0.004)),
        ground=dict(surface=[layer('snow_block', 0, 0.6), layer('packed_ice', 1, 0.95)], plants=[plant('short_grass', 0.02)]),
        feats=['ice_spire', 'frost_bones', 'ice_boulder', 'frost_ruin'], caves=['cave_frost'], vanilla=[]),
    'rimeglass_spires': dict(host='ice_spikes', sources=['ice_spikes'], temperature=-0.5, downfall=0.5,
        effects={'grass_color': '#a0b8b0', 'foliage_color': '#8aa49c', 'water_color': '#3d57d6'},
        sky='#b8d8f0', fog='#e8f2fa', water_fog='#1f2f60',
        particles=particles(({'type': 'minecraft:snowflake'}, 0.006), ({'type': 'minecraft:end_rod'}, 0.0003)),
        ground=dict(surface=[layer('snow_block', 0, 0.3), layer('blue_ice', 1, 0.96)], plants=[]),
        feats=['ice_spire_huge', 'ice_spire'], caves=['cave_frost'], vanilla=[]),
    'skyspire_peaks': dict(host='grove', sources=['grove', 'snowy_slopes', 'jagged_peaks', 'frozen_peaks', 'stony_peaks'], temperature=-0.5, downfall=0.6,
        effects={'grass_color': '#8aa8a0', 'foliage_color': '#6f9a8f', 'water_color': '#3d5a8a'},
        sky='#7fa8e8', fog='#dfe8f4', water_fog='#1f3050',
        particles=particles(({'type': 'minecraft:snowflake'}, 0.003), (mote(argb(0xD8C8FF)), 0.0008)),
        ground=dict(surface=[layer('snow_block', 0, 0.5), layer('calcite', 1, 0.93), layer('stone', 1, 0.0, 0.06)], plants=[]),
        feats=['crystal_shard', 'peak_pine', 'sky_cairn', 'peak_tower'], caves=['cave_glimmer'], vanilla=[]),
    'windworn_highlands': dict(host='windswept_hills', sources=['windswept_hills', 'windswept_gravelly_hills', 'windswept_forest'], temperature=0.3, downfall=0.4,
        effects={'grass_color': '#7f9a6a', 'foliage_color': '#6f8a5a', 'water_color': '#4a6a8a'},
        sky='#9ab4cc', fog='#c8d0d8', water_fog='#22374a',
        particles=particles((mote(argb(0xC8D8C8)), 0.0005)),
        ground=dict(surface=[layer('gravel', 0, 0.82), layer('coarse_dirt', 1, 0.9), layer('stone', 0, 0.0, 0.08)],
                    plants=[plant('short_grass', 0.2), plant('allium', 0.02), plant('lilac', 0.005, half='lower'), plant('bush', 0.01), plant('fern', 0.02)]),
        feats=['windworn_tree', 'highland_boulder', 'rune_menhir', 'standing_stones', 'highland_tower'], caves=['cave_glimmer'], vanilla=[]),
    'ember_steppe': dict(host='savanna', sources=['savanna', 'savanna_plateau', 'windswept_savanna'], temperature=1.4, downfall=0.0,
        effects={'grass_color': '#c9a24a', 'foliage_color': '#d27a2a', 'water_color': '#4a7f8a'},
        sky='#d8c49a', fog='#e8cfa0', water_fog='#2a4a50',
        particles=particles((ember(), 0.0012), ({'type': 'minecraft:white_ash'}, 0.0003)),
        ground=dict(surface=[layer('coarse_dirt', 0, 0.72), layer('packed_mud', 1, 0.92)],
                    plants=[plant('short_dry_grass', 0.18), plant('tall_dry_grass', 0.06), plant('short_grass', 0.08), plant('bush', 0.01),
                            plant('dead_bush', 0.005), plant('golden_dandelion', 0.004)]),
        feats=['ember_tree', 'ember_poplar', 'termite_mound', 'steppe_rock', 'steppe_ruin'], caves=['cave_ember'], vanilla=[]),
    'hourglass_dunes': dict(host='desert', sources=['desert'], temperature=2.0, downfall=0.0,
        effects={'grass_color': '#bfb76f', 'foliage_color': '#aea463', 'water_color': '#3a9aa8'},
        sky='#ecd6a8', fog='#f0dcb0', water_fog='#1f4a50',
        particles=particles(({'type': 'minecraft:falling_dust', 'block_state': 'minecraft:sand'}, 0.002), (mote(argb(0xFFD27A)), 0.0006)),
        ground=dict(surface=[layer('smooth_sandstone', 1, 0.97)], plants=[plant('dead_bush', 0.01), plant('short_dry_grass', 0.02)], sand=True),
        feats=['dune_obelisk', 'dune_bones', 'dune_rock', 'dune_colonnade', 'dune_clockwork'], caves=['cave_ember'],
        vanilla=['minecraft:patch_cactus_desert', 'minecraft:patch_dead_bush_2']),
    'rustscar_canyons': dict(host='badlands', sources=['badlands', 'eroded_badlands', 'wooded_badlands'], temperature=2.0, downfall=0.0,
        effects={'grass_color': '#a07a4a', 'foliage_color': '#8f6a3a', 'water_color': '#3a7a8a'},
        sky='#e0b090', fog='#e0a878', water_fog='#2a4040',
        particles=particles((ember(), 0.0004), ({'type': 'minecraft:falling_dust', 'block_state': 'minecraft:red_sand'}, 0.001)),
        ground=dict(surface=[layer('coarse_dirt', 0, 0.86)], plants=[plant('dead_bush', 0.015), plant('short_dry_grass', 0.02)], sand=True),
        feats=['petrified_tree', 'rust_spire', 'rust_ruin', 'rust_bones'], caves=['cave_ember'], vanilla=['minecraft:patch_dead_bush_badlands']),
    'verdant_tangle': dict(host='jungle', sources=['jungle', 'sparse_jungle', 'bamboo_jungle'], temperature=0.95, downfall=0.9,
        effects={'grass_color': '#4fb83a', 'foliage_color': '#3a9a2a', 'water_color': '#2f9a8a'},
        sky='#8fd0d0', fog='#a8e0c0', water_fog='#1a4a40',
        particles=particles(({'type': 'minecraft:spore_blossom_air'}, 0.004), ({'type': 'minecraft:firefly'}, 0.0008)),
        ground=dict(surface=[layer('moss_block', 0, 0.65), layer('podzol', 0, 0.0, 0.2), layer('rooted_dirt', 1, 0.92)],
                    plants=[plant('fern', 0.12), plant('large_fern', 0.04, half='lower'), plant('short_grass', 0.1), plant('azalea', 0.01),
                            plant('flowering_azalea', 0.008)]),
        feats=['tangle_giant', 'tangle_tree', 'tangle_colonnade', 'tangle_lamp'], caves=['cave_rootways'],
        vanilla=['minecraft:bamboo_vegetation', 'minecraft:vines', 'minecraft:patch_melon']),
    'sporeveil': dict(host='mushroom_fields', sources=['mushroom_fields'], temperature=0.9, downfall=1.0,
        effects={'grass_color': '#6fb06a', 'foliage_color': '#5aa060', 'water_color': '#8a6ad0'},
        sky='#9a8ad0', fog='#b8a8e0', water_fog='#2a1f50',
        particles=particles(({'type': 'minecraft:spore_blossom_air'}, 0.006), (mote(argb(0xFF9AD8)), 0.002)),
        ground=dict(surface=[layer('moss_block', 0, 0.8)], plants=[plant('red_mushroom', 0.02), plant('brown_mushroom', 0.02)]),
        feats=['glowcap', 'redcap', 'sporelamp'], caves=['cave_spore'], vanilla=[]),

    # ---- water
    'mirrorsea': dict(host='ocean', sources=['ocean'], temperature=0.5, downfall=0.5,
        effects={'grass_color': '#8fb87a', 'foliage_color': '#7aa86a', 'water_color': '#5f86c8'},
        sky='#9fc0e8', fog='#d8e4f0', water_fog='#1f3550',
        ground=dict(surface=[layer('gravel', 0, 0.65), layer('clay', 1, 0.9), layer('calcite', 1, 0.0, 0.03)], plants=[], sand=True),
        feats=['sunken_colonnade', 'sea_spire'], caves=['cave_glimmer'], vanilla=[]),
    'mirrordeep': dict(host='deep_ocean', sources=['deep_ocean', 'deep_lukewarm_ocean'], temperature=0.5, downfall=0.5,
        effects={'grass_color': '#8fb87a', 'foliage_color': '#7aa86a', 'water_color': '#4a6ab8'},
        sky='#9fc0e8', fog='#d8e4f0', water_fog='#152a45',
        ground=dict(surface=[layer('gravel', 0, 0.6), layer('clay', 1, 0.92)], plants=[], sand=True),
        feats=['sunken_colonnade', 'sea_spire'], caves=['cave_glimmer'], vanilla=[]),
    'ember_shallows': dict(host='warm_ocean', sources=['warm_ocean', 'lukewarm_ocean'], temperature=0.9, downfall=0.5,
        effects={'grass_color': '#8fb87a', 'foliage_color': '#7aa86a', 'water_color': '#3ad0c0'},
        sky='#a0d8f0', fog='#d0eef0', water_fog='#0f4a4a',
        ground=dict(surface=[layer('sand', 0, 0.0, 1.01)], plants=[], sand=True),
        feats=['coral_spire', 'sunken_colonnade'], caves=['cave_glimmer'], vanilla=[]),
    'rimesea': dict(host='cold_ocean', sources=['cold_ocean'], temperature=0.3, downfall=0.5,
        effects={'grass_color': '#8fb87a', 'foliage_color': '#7aa86a', 'water_color': '#3d5a8a'},
        sky='#a8c0dc', fog='#dce6f0', water_fog='#14233f',
        ground=dict(surface=[layer('gravel', 0, 0.55)], plants=[], sand=True),
        feats=['rime_spire'], caves=['cave_frost'], vanilla=[]),
    'rimedeep': dict(host='deep_cold_ocean', sources=['deep_cold_ocean'], temperature=0.3, downfall=0.5,
        effects={'grass_color': '#8fb87a', 'foliage_color': '#7aa86a', 'water_color': '#2f4a7a'},
        sky='#a8c0dc', fog='#dce6f0', water_fog='#0f1c35',
        ground=dict(surface=[layer('gravel', 0, 0.5)], plants=[], sand=True),
        feats=['rime_spire'], caves=['cave_frost'], vanilla=[]),
    'rimefloe': dict(host='frozen_ocean', sources=['frozen_ocean', 'deep_frozen_ocean'], temperature=0.0, downfall=0.5, frozen=True,
        effects={'grass_color': '#8fb87a', 'foliage_color': '#7aa86a', 'water_color': '#3d5a8a'},
        sky='#a8c0dc', fog='#dce6f0', water_fog='#2a4a7a',
        particles=particles(({'type': 'minecraft:snowflake'}, 0.003)),
        ground=dict(surface=[layer('gravel', 0, 0.55)], plants=[], sand=True),
        feats=['rime_spire'], caves=['cave_frost'], vanilla=None),
    'silverrun': dict(host='river', sources=['river'], temperature=0.5, downfall=0.5,
        effects={'grass_color': '#9fbf7f', 'foliage_color': '#8aac6a', 'water_color': '#8ac0e0'},
        sky='#a8c8e8', fog='#e0ecf2', water_fog='#2a5068',
        particles=particles(({'type': 'minecraft:firefly'}, 0.0005)),
        ground=dict(surface=[layer('gravel', 0, 0.6), layer('clay', 1, 0.9)], plants=[], sand=True),
        feats=['river_stones'], caves=['cave_glimmer'], vanilla=[]),
    'bone_shore': dict(host='beach', sources=['beach'], temperature=0.6, downfall=0.4,
        effects={'grass_color': '#a7b77c', 'foliage_color': '#8fa36a', 'water_color': '#5f9ac8'},
        sky='#a8c8e8', fog='#eee6d8', water_fog='#22445f',
        ground=dict(surface=[layer('sand', 0, 0.0, 1.01)], plants=[], sand=True),
        feats=['shore_bones', 'driftwood', 'shore_rock'], caves=['cave_glimmer'], vanilla=[]),
    'breakstone_shore': dict(host='stony_shore', sources=['stony_shore'], temperature=0.3, downfall=0.3,
        effects={'grass_color': '#8a9a7a', 'foliage_color': '#7a8a6a', 'water_color': '#4a6a8a'},
        sky='#9ab4cc', fog='#d0d8e0', water_fog='#22374a',
        ground=None, feats=['basalt_columns', 'shore_rock'], caves=['cave_sculpt'], vanilla=[]),

    # ---- underground
    'echo_hollows': dict(host='dripstone_caves', sources=['dripstone_caves'], temperature=0.8, downfall=0.4,
        effects={'grass_color': '#8a9a7a', 'foliage_color': '#7a8a6a', 'water_color': '#3d8ab0'},
        sky='#7ba4ff', fog='#c0d8ff', water_fog='#1f3a50',
        particles=particles((mote(argb(0xB8A8FF)), 0.0025)),
        ground=None, feats=[], caves=['cave_sculpt', 'cave_glimmer'], vanilla=None),
    'rootways': dict(host='lush_caves', sources=['lush_caves'], temperature=0.5, downfall=0.5,
        effects={'grass_color': '#5fbf4a', 'foliage_color': '#4aa83a', 'water_color': '#3a9a8a'},
        sky='#7ba4ff', fog='#c0d8ff', water_fog='#1a4a40',
        particles=particles((mote(argb(0xC8FF8A)), 0.002), ({'type': 'minecraft:spore_blossom_air'}, 0.002)),
        ground=None, feats=[], caves=['cave_rootways'], vanilla=None),
}

# Ground painters for the biomes that use the data-driven one.
for name, bm in BIOMES.items():
    g = bm.get('ground')
    if g is not None:
        cfg = {'type': 'afterworld:ground', 'biome': f'{OUR}:{name}', 'surface': g.get('surface', []), 'plants': g.get('plants', [])}
        if g.get('sand'):
            cfg['sand'] = True
        CONFIGURED[f'ground_{name}'] = cfg
        PLACED[f'ground_{name}'] = []
        bm['surface'] = f'ground_{name}'


def global_order(step):
    """Topological order of vanilla features in one generation step, across every vanilla biome."""
    edges = defaultdict(set)
    nodes = []
    for f in sorted(os.listdir(os.path.join(VANILLA, 'biome'))):
        feats = json.load(open(os.path.join(VANILLA, 'biome', f)))['features']
        if step >= len(feats):
            continue
        lst = feats[step]
        for a, c in zip(lst, lst[1:]):
            edges[a].add(c)
        for x in lst:
            if x not in nodes:
                nodes.append(x)
    indeg = {n: 0 for n in nodes}
    for a in edges:
        for c in edges[a]:
            indeg[c] += 1
    order, ready = [], [n for n in nodes if indeg[n] == 0]
    while ready:
        n = ready.pop(0)
        order.append(n)
        for c in sorted(edges[n], key=nodes.index):
            indeg[c] -= 1
            if indeg[c] == 0:
                ready.append(c)
    assert len(order) == len(nodes), 'vanilla feature order has a cycle?'
    return order


def vanilla_tags():
    """Every vanilla biome tag, resolved to plain biome ids."""
    raw = {}
    for root, _, files in os.walk(VTAGS):
        for f in files:
            if f.endswith('.json'):
                rel = os.path.relpath(os.path.join(root, f), VTAGS)[:-5].replace(os.sep, '/')
                raw[rel] = json.load(open(os.path.join(root, f)))['values']
    resolved = {}

    def res(tag, seen=()):
        if tag in resolved:
            return resolved[tag]
        out = set()
        for v in raw.get(tag, []):
            v = v['id'] if isinstance(v, dict) else v
            if v.startswith('#'):
                inner = v[1:].split(':')[-1]
                if inner not in seen:
                    out |= res(inner, seen + (tag,))
            else:
                out.add(v.split(':')[-1])
        resolved[tag] = out
        return out
    for t in raw:
        res(t)
    return resolved


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def main():
    import shutil
    # The vanilla worldgen this reads (data/minecraft/worldgen and tags/worldgen/biome from the game's
    # jar, unpacked under build/) must be there before anything is cleared.
    if not os.path.isdir(os.path.join(VANILLA, 'placed_feature')) or not os.path.isdir(VTAGS):
        sys.exit(f'No vanilla worldgen at {VANILLA}: unpack data/minecraft/worldgen and tags/worldgen/biome from the game jar first.')
    # Everything below is generated: start clean so renamed or merged features leave nothing behind
    # (but keep biomes written by hand, such as the Ladder's).
    for sub in ('feature', 'placed_feature'):
        shutil.rmtree(os.path.join(RES, OUR, 'worldgen', sub), ignore_errors=True)
    for name in BIOMES:
        path = os.path.join(RES, OUR, 'worldgen', 'biome', name + '.json')
        if os.path.exists(path):
            os.remove(path)
    shutil.rmtree(os.path.join(RES, 'minecraft', 'tags', 'worldgen', 'biome'), ignore_errors=True)
    placed_vanilla = set(x[:-5] for x in os.listdir(os.path.join(VANILLA, 'placed_feature')))
    seen = {}
    for name, cfg in CONFIGURED.items():
        key = json.dumps(cfg, sort_keys=True)
        assert key not in seen, f'{name} has the same configuration as {seen[key]}'
        seen[key] = name
        write(os.path.join(RES, OUR, 'worldgen', 'feature', name + '.json'), cfg)
        write(os.path.join(RES, OUR, 'worldgen', 'placed_feature', name + '.json'), {'feature': f'{OUR}:{name}', 'placement': PLACED[name]})
    for name, base in ALIAS.items():
        write(os.path.join(RES, OUR, 'worldgen', 'placed_feature', name + '.json'), {'feature': f'{OUR}:{base}', 'placement': PLACED[name]})
    orders = {step: global_order(step) for step in range(11)}
    for name, bm in BIOMES.items():
        host = json.load(open(os.path.join(VANILLA, 'biome', bm['host'] + '.json')))
        out = dict(host)
        out['temperature'] = bm['temperature']
        out['downfall'] = bm['downfall']
        out['has_precipitation'] = host.get('has_precipitation', True)
        if bm.get('frozen'):
            out['temperature_modifier'] = 'frozen'
        out['effects'] = dict(host.get('effects', {}), **bm['effects'])
        attrs = dict(host.get('attributes', {}))
        attrs['minecraft:visual/sky_color'] = bm['sky']
        attrs['minecraft:visual/fog_color'] = bm['fog']
        attrs['minecraft:visual/water_fog_color'] = clear_water(bm['water_fog'])
        attrs['minecraft:visual/cloud_height'] = 300.0
        if bm.get('particles'):
            attrs['minecraft:visual/ambient_particles'] = bm['particles']
        attrs.update(bm.get('extra_attrs', {}))
        attrs['minecraft:gameplay/natural_mob_spawns'] = spawns(name, attrs.get('minecraft:gameplay/natural_mob_spawns'))
        if C in NATIVE_SPAWNS.get(name, {}):
            attrs['minecraft:gameplay/creature_world_gen_spawn_probability'] = max(LAND_HERDS, attrs.get('minecraft:gameplay/creature_world_gen_spawn_probability', 0.1))
        # Caves keep their own music; everywhere else plays Afterworld's.
        if bm['host'] not in ('dripstone_caves', 'lush_caves'):
            attrs['minecraft:audio/background_music'] = WILDS
            attrs.pop('minecraft:audio/music_volume', None)
        out['attributes'] = dict(sorted(attrs.items()))
        feats = [list(x) for x in host['features']]
        while len(feats) < 11:
            feats.append([])
        if bm.get('surface'):
            feats[0] = [f'{OUR}:{bm["surface"]}'] + feats[0]
        # Step 7: our cave dressing first, then the host's own.
        # Every land's caves (not the seas' or the rivers') may hold the camps of those who came before.
        caves = bm.get('caves', []) + ([] if any(k in bm['host'] for k in ('ocean', 'river')) else ['cave_camp'])
        ours7 = [f'{OUR}:{x}' for x in ORDER[7] if x in caves]
        feats[7] = ours7 + feats[7]
        # Step 9: our features, then the chosen vanilla ones (or the host's, if none were chosen).
        vanilla = bm.get('vanilla')
        if vanilla is None:
            vanilla = host['features'][9] if len(host['features']) > 9 else []
        for v in vanilla:
            assert v.split(':')[1] in placed_vanilla, v
        vanilla = sorted(vanilla, key=lambda v: orders[9].index(v) if v in orders[9] else len(orders[9]))
        ours9 = [f'{OUR}:{x}' for x in ORDER[9] if x in bm.get('feats', [])]
        missing = [x for x in bm.get('feats', []) if x not in ORDER[9]]
        assert not missing, (name, missing)
        feats[9] = ours9 + vanilla
        out['features'] = feats
        write(os.path.join(RES, OUR, 'worldgen', 'biome', name + '.json'), out)
    # Tags: each biome joins every vanilla tag its source biomes are in.
    vt = vanilla_tags()
    tags = defaultdict(list)
    for name, bm in BIOMES.items():
        for tag, members in vt.items():
            if any(src in members for src in bm['sources']):
                tags[tag].append(name)
    for tag, biomes in tags.items():
        write(os.path.join(RES, 'minecraft', 'tags', 'worldgen', 'biome', tag + '.json'),
              {'replace': False, 'values': [f'{OUR}:{x}' for x in sorted(set(biomes))]})
    # Afterworld's own tag of every biome it adds.
    write(os.path.join(RES, OUR, 'tags', 'worldgen', 'biome', 'all.json'), {'values': [f'{OUR}:{x}' for x in BIOMES]})
    if BLOCKS is not None:
        bad = sorted(x for x in USED_BLOCKS if x not in BLOCKS)
        assert not bad, f'unknown blocks: {bad}'
    print(len(CONFIGURED), 'features,', len(BIOMES), 'biomes,', len(tags), 'tags')


if __name__ == '__main__':
    main()

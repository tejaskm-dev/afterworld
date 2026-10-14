#!/usr/bin/env python3
"""Writes item/block model JSON, blockstates, loot tables, recipes, tags and recipe advancements."""
import json, os
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
A = os.path.join(RES, 'assets', 'afterworld')
D = os.path.join(RES, 'data', 'afterworld')
NS = 'afterworld'
EDICTS = ['return', 'echoes', 'borrowed_time', 'weightlessness', 'hunger', 'hospitality', 'trespass', 'stillness', 'witness', 'forgotten']

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')

def simple_item(name, parent='minecraft:item/generated', texture=None):
    write(os.path.join(A, 'items', f'{name}.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{name}'}})
    write(os.path.join(A, 'models', 'item', f'{name}.json'), {'parent': parent, 'textures': {'layer0': f'{NS}:item/{texture or name}'}})

GENERATED = ['expedition_journal', 'anomaly_residue', 'clockwork_fragment', 'moth_dust', 'grave_wax', 'bell_fragment',
             'chronicle_page', 'pilgrim_offering', 'torn_map', 'clockwork_heart', 'thimble_of_borrowed_rain', 'torn_census', 'ringers_ledger',
             'pilgrim_spawn_egg', 'bell_ringer_spawn_egg', 'king_of_seconds_spawn_egg',
             'lantern_moth_spawn_egg', 'crow_spawn_egg', 'mossback_spawn_egg', 'hollow_stag_spawn_egg',
             'clockwork_sentry_spawn_egg', 'rule_wraith_spawn_egg', 'wisp_spawn_egg',
             'ashen_husk_spawn_egg', 'shardling_spawn_egg', 'bog_lurker_spawn_egg', 'echo_stalker_spawn_egg', 'gearhound_spawn_egg',
             'rime_wight_spawn_egg', 'skywhale_spawn_egg', 'glimmerfin_spawn_egg', 'emberfox_spawn_egg', 'rimehorn_spawn_egg',
             'petal_sprite_spawn_egg', 'mirror_ray_spawn_egg', 'glimmerfin_bucket',
             'ember_core', 'living_crystal', 'lurker_fang', 'echo_essence', 'gear_spring', 'rime_core']

def items():
    for n in GENERATED:
        simple_item(n)
    for e in EDICTS:
        simple_item(f'edict_fragment_{e}')
    simple_item('apologetic_blade', parent='minecraft:item/handheld')
    for w in ('emberbrand', 'shardsong_glaive', 'lurkers_hook', 'echo_edge', 'gearspring_maul', 'rimefang_spear',
              'bellhammer', 'second_hand', 'pilgrims_staff'):
        simple_item(w, parent='minecraft:item/handheld')
    # Hungry lantern: lit and unlit.
    write(os.path.join(A, 'models', 'item', 'hungry_lantern.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/hungry_lantern'}})
    write(os.path.join(A, 'models', 'item', 'hungry_lantern_lit.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/hungry_lantern_lit'}})
    write(os.path.join(A, 'items', 'hungry_lantern.json'), {'model': {
        'type': 'minecraft:condition', 'property': 'minecraft:has_component', 'component': f'{NS}:lit',
        'on_true': {'type': 'minecraft:model', 'model': f'{NS}:item/hungry_lantern_lit'},
        'on_false': {'type': 'minecraft:model', 'model': f'{NS}:item/hungry_lantern'}}})
    # Unfinished compass: 32 needle frames, aimed by the lodestone tracker the item sets on itself.
    for i in range(32):
        write(os.path.join(A, 'models', 'item', f'unfinished_compass_{i:02d}.json'),
              {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/unfinished_compass_{i:02d}'}})
    def dispatch(target):
        entries = []
        for k in range(33):
            entries.append({'threshold': 0.0 if k == 0 else k - 0.5, 'model': {'type': 'minecraft:model', 'model': f'{NS}:item/unfinished_compass_{k % 32:02d}'}})
        return {'type': 'minecraft:range_dispatch', 'property': 'minecraft:compass', 'target': target, 'scale': 32.0, 'entries': entries}
    write(os.path.join(A, 'items', 'unfinished_compass.json'), {'model': {
        'type': 'minecraft:condition', 'property': 'minecraft:has_component', 'component': 'minecraft:lodestone_tracker',
        'on_true': dispatch('lodestone'), 'on_false': dispatch('none')}})

CUBES = ['clockwork_bricks', 'gearwork', 'warded_gearwork', 'escapement', 'counterweight', 'anomaly_core']

def blocks():
    for b in CUBES:
        write(os.path.join(A, 'blockstates', f'{b}.json'), {'variants': {'': {'model': f'{NS}:block/{b}'}}})
        write(os.path.join(A, 'models', 'block', f'{b}.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{b}'}})
        write(os.path.join(A, 'items', f'{b}.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{b}'}})
    write(os.path.join(A, 'blockstates', 'shrine_stone.json'), {'variants': {
        'repaired=false': {'model': f'{NS}:block/shrine_stone'}, 'repaired=true': {'model': f'{NS}:block/shrine_stone_repaired'}}})
    write(os.path.join(A, 'models', 'block', 'shrine_stone.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/shrine_stone'}})
    write(os.path.join(A, 'models', 'block', 'shrine_stone_repaired.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/shrine_stone_repaired'}})
    write(os.path.join(A, 'items', 'shrine_stone.json'), {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/shrine_stone'}})
    for b in ('clockwork_bricks', 'gearwork'):
        write(os.path.join(D, 'loot_table', 'blocks', f'{b}.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{b}'}],
                                                 'condition': {'type': 'minecraft:survives_explosion'}}]})

def item_entry(name, weight=1, lo=1, hi=1):
    e = {'type': 'minecraft:item', 'name': name, 'weight': weight}
    if hi > 1:
        e['modifier'] = {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}
    return e

def fragment_entries(weight):
    return [item_entry(f'{NS}:edict_fragment_{e}', weight) for e in EDICTS if e not in ('borrowed_time', 'trespass')]

def chest(name, pools):
    write(os.path.join(D, 'loot_table', 'chests', f'{name}.json'), {'type': 'minecraft:chest', 'pools': pools})

def loot():
    chest('shrine_cache', [
        {'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}, 'entries': [
            item_entry('minecraft:candle', 4, 1, 3), item_entry('minecraft:emerald', 4, 1, 4), item_entry(f'{NS}:pilgrim_offering', 3, 1, 2),
            item_entry('minecraft:amethyst_shard', 3, 1, 4), item_entry(f'{NS}:chronicle_page', 2, 1, 2), item_entry('minecraft:golden_apple', 1)]},
        {'rolls': 1, 'entries': [{'type': 'minecraft:empty', 'weight': 3}] + fragment_entries(1)}])
    chest('lair_hoard', [
        {'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 6}, 'entries': [
            item_entry('minecraft:bone', 6, 1, 5), item_entry('minecraft:leather', 5, 1, 4), item_entry('minecraft:mutton', 3, 1, 3),
            item_entry('minecraft:emerald', 3, 1, 3), item_entry('minecraft:iron_ingot', 2, 1, 3), item_entry('minecraft:saddle', 1), item_entry(f'{NS}:torn_map', 2)]}])
    chest('old_cache', [
        {'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 6}, 'entries': [
            item_entry('minecraft:paper', 5, 2, 6), item_entry('minecraft:compass', 2), item_entry('minecraft:emerald', 4, 2, 6),
            item_entry('minecraft:gold_ingot', 3, 1, 3), item_entry(f'{NS}:chronicle_page', 4, 1, 3), item_entry(f'{NS}:torn_map', 3),
            item_entry('minecraft:spyglass', 1), item_entry(f'{NS}:anomaly_residue', 2, 1, 3)]},
        {'rolls': 1, 'entries': [{'type': 'minecraft:empty', 'weight': 5}] + fragment_entries(1)}])
    chest('caravan_goods', [
        {'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 6}, 'entries': [
            item_entry('minecraft:wheat', 5, 2, 8), item_entry('minecraft:bread', 4, 1, 4), item_entry('minecraft:emerald', 4, 1, 4),
            item_entry('minecraft:white_wool', 3, 1, 4), item_entry('minecraft:lantern', 2), item_entry(f'{NS}:pilgrim_offering', 1)]}])
    chest('cathedral_archive', [
        {'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 5}, 'entries': [
            item_entry('minecraft:book', 5, 1, 3), item_entry('minecraft:candle', 4, 1, 4), item_entry(f'{NS}:chronicle_page', 5, 2, 4),
            item_entry(f'{NS}:grave_wax', 4, 1, 3), item_entry('minecraft:gold_nugget', 3, 2, 9), item_entry(f'{NS}:bell_fragment', 2)]}])
    chest('unremembered_house', [
        {'rolls': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}, 'entries': [
            item_entry('minecraft:bread', 3, 1, 2), item_entry('minecraft:paper', 3, 1, 4), item_entry(f'{NS}:grave_wax', 3, 1, 2),
            item_entry('minecraft:flower_pot', 1), item_entry('minecraft:emerald', 2, 1, 2)]}])
    chest('vault_reward', [
        {'rolls': {'type': 'minecraft:uniform', 'min': 4, 'max': 7}, 'entries': [
            item_entry(f'{NS}:clockwork_fragment', 6, 2, 5), item_entry('minecraft:gold_ingot', 4, 2, 5), item_entry('minecraft:diamond', 2, 1, 2),
            item_entry('minecraft:clock', 2), item_entry('minecraft:emerald', 4, 3, 8), item_entry(f'{NS}:anomaly_residue', 3, 2, 5)]},
        {'rolls': 1, 'entries': fragment_entries(1)}])

def shapeless(name, ingredients, result, count=1):
    write(os.path.join(D, 'recipe', f'{name}.json'), {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': ingredients,
                                                     'result': {'id': result, 'count': count}})
    advancement(name, ingredients[0] if isinstance(ingredients[0], str) else None)

def shaped(name, pattern, key, result, count=1, unlock=None):
    write(os.path.join(D, 'recipe', f'{name}.json'), {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key,
                                                     'result': {'id': result, 'count': count}})
    advancement(name, unlock)

def advancement(name, item):
    if item is None or item.startswith('#'):
        return
    write(os.path.join(D, 'advancement', 'recipes', f'{name}.json'), {
        'parent': 'minecraft:recipes/root',
        'criteria': {'has_item': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': item}]}},
                     'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipes': f'{NS}:{name}'}}},
        'requirements': [['has_item', 'has_the_recipe']],
        'rewards': {'recipes': [f'{NS}:{name}']}})

def recipes():
    shapeless('expedition_journal', ['minecraft:book', 'minecraft:compass'], f'{NS}:expedition_journal')
    shapeless('pilgrim_offering', ['minecraft:candle', '#minecraft:small_flowers', 'minecraft:cobblestone'], f'{NS}:pilgrim_offering', 2)
    advancement('pilgrim_offering', 'minecraft:candle')
    shapeless('chronicle_page', ['minecraft:paper', 'minecraft:paper', 'minecraft:paper', 'minecraft:ink_sac'], f'{NS}:chronicle_page', 3)
    shapeless('torn_map', ['minecraft:paper', f'{NS}:anomaly_residue'], f'{NS}:torn_map')
    advancement('torn_map', f'{NS}:anomaly_residue')
    shapeless('grave_wax', ['minecraft:candle', 'minecraft:bone_meal', 'minecraft:honeycomb'], f'{NS}:grave_wax', 2)
    # Arms: a plain weapon, two of a monster's forging material, and something to bind them.
    for name, base, mat, binder in (('emberbrand', 'diamond_sword', 'ember_core', 'blaze_rod'),
                                    ('shardsong_glaive', 'iron_sword', 'living_crystal', 'amethyst_shard'),
                                    ('lurkers_hook', 'iron_sword', 'lurker_fang', 'string'),
                                    ('echo_edge', 'diamond_sword', 'echo_essence', 'ender_pearl'),
                                    ('gearspring_maul', 'iron_axe', 'gear_spring', 'iron_block'),
                                    ('rimefang_spear', 'diamond_sword', 'rime_core', 'packed_ice')):
        shapeless(name, [f'minecraft:{base}', f'{NS}:{mat}', f'{NS}:{mat}', f'minecraft:{binder}'], f'{NS}:{name}')
    shapeless('clockwork_fragment', ['minecraft:copper_ingot', 'minecraft:gold_nugget', 'minecraft:redstone'], f'{NS}:clockwork_fragment')
    advancement('clockwork_fragment', 'minecraft:copper_ingot')
    shaped('clockwork_bricks', ['BB', 'BB'], {'B': 'minecraft:bricks'}, f'{NS}:clockwork_bricks', 4, unlock=f'{NS}:clockwork_fragment')
    shaped('gearwork', ['CFC', 'FIF', 'CFC'], {'C': 'minecraft:copper_ingot', 'F': f'{NS}:clockwork_fragment', 'I': 'minecraft:iron_ingot'},
           f'{NS}:gearwork', 4, unlock=f'{NS}:clockwork_fragment')
    shaped('unfinished_compass', [' R ', 'RCR', ' P '], {'R': f'{NS}:anomaly_residue', 'C': 'minecraft:compass', 'P': f'{NS}:chronicle_page'},
           f'{NS}:unfinished_compass', unlock=f'{NS}:anomaly_residue')
    shaped('hungry_lantern', ['DDD', 'DLD', 'DGD'], {'D': f'{NS}:moth_dust', 'L': 'minecraft:lantern', 'G': 'minecraft:glowstone_dust'},
           f'{NS}:hungry_lantern', unlock=f'{NS}:moth_dust')
    shaped('clockwork_heart', ['F F', 'FCF', ' R '], {'F': f'{NS}:clockwork_fragment', 'C': 'minecraft:clock', 'R': 'minecraft:redstone_block'},
           f'{NS}:clockwork_heart', unlock=f'{NS}:clockwork_fragment')
    shaped('thimble_of_borrowed_rain', [' R ', 'NWN', ' N '], {'R': f'{NS}:anomaly_residue', 'N': 'minecraft:iron_nugget', 'W': 'minecraft:water_bucket'},
           f'{NS}:thimble_of_borrowed_rain', unlock=f'{NS}:anomaly_residue')
    shaped('apologetic_blade', [' B ', 'WSW', ' A '], {'B': f'{NS}:bell_fragment', 'W': f'{NS}:grave_wax', 'S': 'minecraft:iron_sword', 'A': 'minecraft:amethyst_shard'},
           f'{NS}:apologetic_blade', unlock=f'{NS}:bell_fragment')

def tags():
    write(os.path.join(D, 'tags', 'block', 'warded.json'), {'values': [
        f'{NS}:warded_gearwork', f'{NS}:escapement', f'{NS}:counterweight', 'minecraft:bedrock', 'minecraft:barrier', 'minecraft:reinforced_deepslate',
        'minecraft:end_portal_frame', 'minecraft:command_block', 'minecraft:chain_command_block', 'minecraft:repeating_command_block',
        'minecraft:structure_block', 'minecraft:jigsaw', 'minecraft:light', {'id': 'minecraft:vault', 'required': False},
        {'id': 'minecraft:trial_spawner', 'required': False}]})
    write(os.path.join(D, 'tags', 'block', 'pilgrim_tramples.json'), {'values': [
        'minecraft:grass_block', 'minecraft:dirt', 'minecraft:podzol', 'minecraft:mycelium', 'minecraft:coarse_dirt', 'minecraft:rooted_dirt']})
    mc = os.path.join(RES, 'data', 'minecraft', 'tags', 'block')
    write(os.path.join(mc, 'mineable', 'pickaxe.json'), {'values': [f'{NS}:clockwork_bricks', f'{NS}:gearwork', f'{NS}:escapement', f'{NS}:anomaly_core', f'{NS}:waystone']})
    write(os.path.join(mc, 'needs_iron_tool.json'), {'values': [f'{NS}:escapement']})

if __name__ == '__main__':
    items()
    blocks()
    loot()
    recipes()
    tags()
    print('data written')

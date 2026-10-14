#!/usr/bin/env python3
"""Loot for Afterworld's creatures and grand structures, in 26.x loot-table format
(entries carry a "modifier" list; pools and modifiers take a single "condition")."""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'data', 'afterworld', 'loot_table')


def count(lo, hi):
    return {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}


LOOTING = {'type': 'minecraft:enchanted_count_increase', 'count': {'type': 'minecraft:uniform', 'min': 0.0, 'max': 1.0}, 'enchantment': 'minecraft:looting'}


def drop(item, lo, hi, looting=True, chance=None, player=False):
    entry = {'type': 'minecraft:item', 'name': item, 'modifier': [count(lo, hi)] + ([LOOTING] if looting else [])}
    pool = {'rolls': 1, 'entries': [entry]}
    conds = []
    if player:
        conds.append({'type': 'minecraft:killed_by_player'})
    if chance:
        conds.append({'type': 'minecraft:random_chance', 'chance': chance})
    if len(conds) == 1:
        pool['condition'] = conds[0]
    elif conds:
        pool['condition'] = {'type': 'minecraft:all_of', 'terms': conds}
    return pool


def item(name, weight, lo=1, hi=1, extra=None):
    e = {'type': 'minecraft:item', 'name': name, 'weight': weight}
    mods = []
    if hi > 1 or lo != 1:
        mods.append(count(lo, hi))
    if extra:
        mods.append(extra)
    if mods:
        e['modifier'] = mods
    return e


ENCHANT = {'type': 'minecraft:enchant_randomly'}
ENCHANT_LEVELS = {'type': 'minecraft:enchant_with_levels', 'levels': {'type': 'minecraft:uniform', 'min': 10, 'max': 30}}


def chest(rolls, entries, bonus=None):
    pools = [{'rolls': {'type': 'minecraft:uniform', 'min': rolls[0], 'max': rolls[1]}, 'entries': entries}]
    if bonus:
        pools.append({'rolls': 1, 'entries': bonus})
    return {'type': 'minecraft:chest', 'pools': pools}


CREATURES = {
    'lantern_moth': [drop('afterworld:moth_dust', 0, 1)],
    'crow': [drop('minecraft:feather', 0, 2)],
    'mossback': [drop('minecraft:moss_block', 1, 2), drop('minecraft:leather', 0, 1)],
    'hollow_stag': [drop('minecraft:leather', 1, 2), drop('minecraft:amethyst_shard', 0, 1, chance=0.35)],
    'clockwork_sentry': [drop('minecraft:copper_ingot', 1, 3), drop('minecraft:redstone', 0, 2),
                         drop('afterworld:clockwork_fragment', 1, 1, looting=False, chance=0.25, player=True)],
    'rule_wraith': [drop('minecraft:lapis_lazuli', 1, 2), drop('afterworld:anomaly_residue', 1, 1, looting=False, chance=0.3, player=True)],
    'wisp': [drop('minecraft:glowstone_dust', 1, 2)],
    # The second wave: each monster can give up the material its weapon is forged from.
    'ashen_husk': [drop('minecraft:charcoal', 0, 2), drop('afterworld:ember_core', 1, 1, looting=False, chance=0.22, player=True)],
    'shardling': [drop('minecraft:amethyst_shard', 0, 2), drop('afterworld:living_crystal', 1, 1, looting=False, chance=0.25, player=True)],
    'bog_lurker': [drop('minecraft:slime_ball', 0, 2), drop('minecraft:leather', 0, 1), drop('afterworld:lurker_fang', 1, 1, looting=False, chance=0.3, player=True)],
    'echo_stalker': [drop('minecraft:ender_pearl', 0, 1, chance=0.5), drop('afterworld:echo_essence', 1, 1, looting=False, chance=0.25, player=True)],
    'gearhound': [drop('minecraft:copper_ingot', 0, 2), drop('afterworld:gear_spring', 1, 1, looting=False, chance=0.25, player=True)],
    'rime_wight': [drop('minecraft:snowball', 1, 3), drop('minecraft:packed_ice', 0, 1), drop('afterworld:rime_core', 1, 1, looting=False, chance=0.25, player=True)],
    'emberfox': [drop('minecraft:leather', 0, 1)],
    'rimehorn': [drop('minecraft:beef', 1, 3), drop('minecraft:leather', 0, 2), drop('minecraft:white_wool', 1, 2)],
    'glimmerfin': [drop('minecraft:cod', 1, 1, looting=False), drop('minecraft:glowstone_dust', 0, 1, chance=0.3)],
    'petal_sprite': [drop('minecraft:pink_petals', 1, 2), drop('minecraft:glowstone_dust', 0, 1)],
    'mirror_ray': [drop('minecraft:prismarine_crystals', 1, 2), drop('minecraft:prismarine_shard', 0, 2)],
}

CHESTS = {
    'bastion_armory': chest((4, 7), [
        item('minecraft:iron_ingot', 10, 2, 6), item('minecraft:gold_ingot', 5, 1, 4), item('minecraft:arrow', 8, 6, 18),
        item('minecraft:iron_sword', 3, extra=ENCHANT), item('minecraft:iron_chestplate', 2, extra=ENCHANT), item('minecraft:shield', 3),
        item('minecraft:bread', 8, 2, 6), item('minecraft:cooked_beef', 6, 2, 5), item('minecraft:emerald', 5, 2, 6),
        item('minecraft:crossbow', 2, extra=ENCHANT)],
        bonus=[item('minecraft:golden_apple', 2), item('minecraft:diamond', 1), item('minecraft:enchanted_book', 2, extra=ENCHANT_LEVELS)]),
    'observatory_maps': chest((4, 7), [
        item('minecraft:map', 8, 1, 3), item('minecraft:compass', 5), item('minecraft:spyglass', 4), item('minecraft:paper', 10, 3, 9),
        item('minecraft:amethyst_shard', 6, 2, 6), item('minecraft:copper_ingot', 7, 2, 8), item('afterworld:torn_map', 5),
        item('minecraft:clock', 3), item('minecraft:glow_ink_sac', 4, 1, 3), item('minecraft:book', 6, 1, 3)],
        bonus=[item('minecraft:enchanted_book', 3, extra=ENCHANT_LEVELS), item('minecraft:recovery_compass', 1)]),
    'mausoleum_crypt': chest((4, 7), [
        item('afterworld:grave_wax', 8, 1, 3), item('minecraft:candle', 8, 2, 5), item('minecraft:soul_lantern', 4),
        item('minecraft:gold_ingot', 6, 2, 5), item('minecraft:bone', 10, 3, 9), item('minecraft:emerald', 4, 2, 5),
        item('minecraft:golden_carrot', 5, 2, 5), item('minecraft:experience_bottle', 4, 1, 4)],
        bonus=[item('minecraft:enchanted_book', 3, extra=ENCHANT_LEVELS), item('minecraft:totem_of_undying', 1), item('minecraft:diamond', 1, 1, 2)]),
    'containment_archive': chest((4, 7), [
        item('afterworld:clockwork_fragment', 6, 1, 2), item('afterworld:anomaly_residue', 6, 1, 3), item('minecraft:redstone', 10, 4, 12),
        item('minecraft:copper_ingot', 8, 3, 9), item('minecraft:iron_ingot', 6, 2, 5), item('minecraft:paper', 8, 4, 10),
        item('minecraft:observer', 3, 1, 2), item('minecraft:quartz', 5, 3, 8)],
        bonus=[item('afterworld:clockwork_heart', 1), item('minecraft:diamond', 2, 1, 2), item('minecraft:enchanted_book', 2, extra=ENCHANT_LEVELS)]),
    'archive_almost': chest((4, 7), [
        item('afterworld:chronicle_page', 8, 1, 3), item('minecraft:book', 8, 2, 5), item('minecraft:paper', 8, 3, 9),
        item('minecraft:experience_bottle', 6, 2, 6), item('minecraft:lapis_lazuli', 6, 4, 10), item('minecraft:amethyst_shard', 5, 2, 5),
        item('minecraft:feather', 5, 2, 5), item('minecraft:enchanted_book', 4, extra=ENCHANT_LEVELS)],
        bonus=[item('minecraft:enchanted_book', 3, extra=ENCHANT_LEVELS), item('minecraft:diamond', 1, 1, 2), item('afterworld:chronicle_page', 3, 2, 4)]),
    # Ruins scattered through the biomes.
    'ruin_cache': chest((3, 6), [
        item('minecraft:bread', 8, 1, 4), item('minecraft:coal', 8, 2, 6), item('minecraft:iron_nugget', 8, 3, 9), item('minecraft:gold_nugget', 6, 2, 7),
        item('minecraft:emerald', 4, 1, 3), item('minecraft:candle', 5, 1, 3), item('afterworld:torn_map', 3), item('minecraft:arrow', 6, 3, 10),
        item('minecraft:book', 4), item('afterworld:chronicle_page', 2)],
        bonus=[item('minecraft:enchanted_book', 2, extra=ENCHANT), item('minecraft:golden_apple', 1), item('minecraft:iron_ingot', 4, 1, 3)]),
    'clockwork_cache': chest((3, 6), [
        item('minecraft:copper_ingot', 10, 3, 9), item('minecraft:clock', 4), item('minecraft:redstone', 8, 2, 8), item('minecraft:gold_nugget', 6, 3, 9),
        item('afterworld:clockwork_fragment', 3), item('minecraft:compass', 3), item('minecraft:sand', 4, 2, 6)],
        bonus=[item('minecraft:gold_ingot', 3, 1, 3), item('minecraft:enchanted_book', 2, extra=ENCHANT), item('minecraft:spyglass', 1)]),
    'sunken_cache': chest((3, 6), [
        item('minecraft:prismarine_shard', 8, 2, 6), item('minecraft:prismarine_crystals', 6, 1, 4), item('minecraft:gold_nugget', 8, 3, 9),
        item('minecraft:nautilus_shell', 3), item('minecraft:emerald', 4, 1, 3), item('minecraft:kelp', 6, 2, 8), item('minecraft:iron_ingot', 4, 1, 3)],
        bonus=[item('minecraft:heart_of_the_sea', 1), item('minecraft:trident', 1, extra=ENCHANT), item('minecraft:enchanted_book', 2, extra=ENCHANT)]),
    # The wonders.
    'titan_offering': chest((4, 7), [
        item('minecraft:gold_ingot', 8, 2, 6), item('minecraft:gold_nugget', 8, 6, 18), item('minecraft:candle', 6, 2, 6),
        item('minecraft:crying_obsidian', 5, 1, 3), item('minecraft:magma_cream', 5, 1, 4), item('minecraft:blaze_powder', 4, 1, 3),
        item('minecraft:bone', 8, 3, 9), item('afterworld:anomaly_residue', 4, 1, 2), item('minecraft:obsidian', 4, 1, 3)],
        bonus=[item('minecraft:netherite_scrap', 1), item('minecraft:enchanted_golden_apple', 1),
               item('minecraft:diamond', 3, 1, 3), item('minecraft:enchanted_book', 4, extra=ENCHANT_LEVELS)]),
    'vesperhold_reliquary': chest((5, 8), [
        item('minecraft:gold_ingot', 8, 2, 6), item('minecraft:emerald', 8, 3, 9), item('minecraft:candle', 6, 2, 6),
        item('minecraft:book', 6, 2, 5), item('afterworld:chronicle_page', 6, 1, 3), item('minecraft:experience_bottle', 6, 2, 6),
        item('minecraft:golden_apple', 3), item('minecraft:bell', 2)],
        bonus=[item('minecraft:totem_of_undying', 1), item('minecraft:enchanted_book', 4, extra=ENCHANT_LEVELS),
               item('minecraft:diamond', 2, 1, 3)]),
    'vesperhold_house': chest((3, 6), [
        item('minecraft:bread', 10, 2, 6), item('minecraft:apple', 8, 2, 5), item('minecraft:candle', 6, 1, 3),
        item('minecraft:white_wool', 6, 1, 4), item('minecraft:emerald', 5, 1, 3), item('minecraft:paper', 6, 2, 6),
        item('minecraft:iron_ingot', 4, 1, 3), item('minecraft:cake', 2), item('minecraft:music_disc_strad', 1)],
        bonus=[item('afterworld:torn_map', 2), item('minecraft:name_tag', 1), item('minecraft:emerald', 2, 2, 5)]),
    'citadel_vault': chest((5, 8), [
        item('minecraft:diamond', 4, 1, 3), item('minecraft:gold_ingot', 8, 3, 7), item('minecraft:obsidian', 6, 2, 5),
        item('minecraft:crying_obsidian', 6, 1, 4), item('minecraft:ender_pearl', 6, 1, 3), item('minecraft:amethyst_shard', 6, 2, 6),
        item('minecraft:netherite_scrap', 2), item('afterworld:anomaly_residue', 5, 1, 3), item('minecraft:diamond_sword', 2, extra=ENCHANT_LEVELS)],
        bonus=[item('minecraft:enchanted_golden_apple', 1), item('minecraft:enchanted_book', 4, extra=ENCHANT_LEVELS),
               item('minecraft:netherite_upgrade_smithing_template', 1)]),
    'brassmere_storeroom': chest((4, 7), [
        item('minecraft:copper_ingot', 10, 4, 12), item('minecraft:iron_ingot', 6, 2, 6), item('minecraft:redstone', 6, 3, 9),
        item('minecraft:bread', 8, 2, 6), item('minecraft:wheat', 8, 4, 12), item('minecraft:coal', 8, 3, 9),
        item('minecraft:piston', 3, 1, 2), item('minecraft:comparator', 2), item('afterworld:clockwork_fragment', 3)],
        bonus=[item('minecraft:spyglass', 2), item('minecraft:emerald', 3, 2, 6), item('minecraft:diamond', 1)]),
    'brassmere_airship': chest((4, 7), [
        item('minecraft:map', 6, 1, 2), item('afterworld:torn_map', 5), item('minecraft:compass', 5), item('minecraft:spyglass', 4),
        item('minecraft:firework_rocket', 6, 3, 8), item('minecraft:phantom_membrane', 4, 1, 3), item('minecraft:feather', 6, 2, 6),
        item('minecraft:copper_ingot', 6, 3, 8), item('minecraft:cooked_salmon', 6, 2, 5)],
        bonus=[item('minecraft:elytra', 1), item('minecraft:diamond', 2, 1, 2), item('minecraft:enchanted_book', 3, extra=ENCHANT_LEVELS)]),
    'wardens_shrine': chest((4, 7), [
        item('minecraft:gold_ingot', 8, 2, 5), item('minecraft:quartz', 8, 4, 12), item('minecraft:amethyst_shard', 6, 2, 6),
        item('minecraft:feather', 6, 2, 6), item('minecraft:golden_carrot', 6, 2, 5), item('minecraft:experience_bottle', 6, 2, 5),
        item('minecraft:glow_berries', 6, 2, 6), item('minecraft:white_candle', 5, 1, 4)],
        bonus=[item('minecraft:totem_of_undying', 1), item('minecraft:enchanted_book', 4, extra=ENCHANT_LEVELS),
               item('minecraft:golden_apple', 2, 1, 2)]),
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


if __name__ == '__main__':
    for k, pools in CREATURES.items():
        write(os.path.join(OUT, 'entities', k + '.json'), {'type': 'minecraft:entity', 'pools': pools, 'random_sequence': f'afterworld:entities/{k}'})
    for k, table in CHESTS.items():
        write(os.path.join(OUT, 'chests', k + '.json'), table)
    print(len(CREATURES), 'creature tables,', len(CHESTS), 'chest tables')

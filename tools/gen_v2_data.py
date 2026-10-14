#!/usr/bin/env python3
"""Afterworld 2.0's data: what the second bestiary drops, and what can be made from it (armour
sets, oils, bombs, elixirs and a few weapons)."""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
D = os.path.join(HERE, '..', 'src', 'main', 'resources', 'data', 'afterworld')


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def aw(x):
    return x if ':' in x else 'afterworld:' + x


def drop(item, lo, hi, chance=1.0, looting=True):
    """A pool: lo..hi of an item (a chance below one makes it rare); looting adds up to one more."""
    entry = {'type': 'minecraft:item', 'name': aw(item) if not item.startswith('minecraft:') else item,
             'modifier': [{'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]}
    if looting:
        entry['modifier'].append({'type': 'minecraft:enchanted_count_increase', 'enchantment': 'minecraft:looting',
                                  'count': {'type': 'minecraft:uniform', 'min': 0.0, 'max': 1.0}})
    pool = {'rolls': 1, 'entries': [entry]}
    if chance < 1.0:
        pool['condition'] = {'type': 'minecraft:all_of', 'terms': [
            {'type': 'minecraft:killed_by_player'},
            {'type': 'minecraft:random_chance_with_enchanted_bonus', 'enchantment': 'minecraft:looting', 'unenchanted_chance': chance,
             'enchanted_chance': {'type': 'minecraft:linear', 'base': chance + 0.05, 'per_level_above_first': 0.05}}]}
    return pool


MC = 'minecraft:'
LOOT = {
    'dune_strider': [drop(MC + 'leather', 1, 2), drop(MC + 'mutton', 1, 2)],
    'snow_hare': [drop(MC + 'rabbit', 0, 1), drop(MC + 'rabbit_hide', 0, 1), drop(MC + 'rabbit_foot', 1, 1, 0.1, False)],
    'thornback_boar': [drop(MC + 'porkchop', 1, 3), drop('boar_tusk', 1, 1, 0.3)],
    'highland_ram': [drop(MC + 'mutton', 1, 2), drop(MC + 'white_wool', 1, 2), drop('ram_horn', 1, 1, 0.3)],
    'duskpine_lynx': [drop(MC + 'leather', 0, 1), drop('lynx_pelt', 1, 1, 0.4)],
    'fen_toad': [drop(MC + 'slime_ball', 0, 2), drop('toad_oil', 1, 1, 0.35)],
    'crystal_crab': [drop('crab_shell', 1, 1, 0.4), drop(MC + 'prismarine_shard', 0, 1)],
    'ember_salamander': [drop(MC + 'magma_cream', 0, 1), drop(MC + 'blaze_powder', 1, 1, 0.25)],
    'silverrun_otter': [drop(MC + 'cod', 0, 1), drop(MC + 'leather', 0, 1)],
    'kitsune': [drop('kitsune_tail', 1, 1, 0.25), drop(MC + 'pink_petals', 1, 2)],
    'sporeling': [drop(MC + 'brown_mushroom', 0, 1), drop(MC + 'red_mushroom', 0, 1), drop(MC + 'glow_berries', 1, 1, 0.2)],
    'floe_seal': [drop(MC + 'cod', 1, 2), drop(MC + 'salmon', 0, 1)],
    'blossom_crane': [drop(MC + 'feather', 1, 2), drop(MC + 'pink_petals', 0, 1)],
    'cinder_beetle': [drop(MC + 'gunpowder', 0, 1), drop('ember_chitin', 1, 1, 0.35), drop('ember_core', 1, 1, 0.1)],
    'sand_sifter': [drop('sifter_mandible', 1, 1, 0.4), drop('glyph_gold', 1, 1, 0.05)],
    'glyph_scarab': [drop(MC + 'gold_nugget', 1, 2), drop('glyph_gold', 1, 1, 0.08)],
    'rust_golemite': [drop(MC + 'iron_nugget', 2, 5), drop('gear_spring', 1, 1, 0.3), drop('clockwork_fragment', 1, 1, 0.15)],
    'ice_crawler': [drop(MC + 'string', 0, 2), drop('rime_pearl', 1, 1, 0.1), drop('rime_core', 1, 1, 0.15)],
    'rime_brute': [drop(MC + 'white_wool', 0, 2), drop('rime_core', 1, 1, 0.4), drop('rime_pearl', 1, 1, 0.15)],
    'drowned_revenant': [drop(MC + 'rotten_flesh', 0, 2), drop(MC + 'copper_ingot', 0, 1), drop(MC + 'nautilus_shell', 1, 1, 0.08)],
    'mire_shambler': [drop(MC + 'slime_ball', 0, 1), drop(MC + 'vine', 0, 2), drop('bog_amber', 1, 1, 0.12)],
    'vine_strangler': [drop(MC + 'vine', 1, 3), drop(MC + 'glow_berries', 0, 2)],
    'fungal_brute': [drop(MC + 'red_mushroom', 1, 2), drop(MC + 'glowstone_dust', 0, 2)],
    'rootling_sentinel': [drop(MC + 'stick', 1, 3), drop('root_heartwood', 1, 1, 0.35)],
    'gloomweaver': [drop(MC + 'string', 1, 3), drop(MC + 'spider_eye', 0, 1)],
    'prism_golem': [drop(MC + 'amethyst_shard', 2, 4), drop('living_crystal', 1, 1, 0.3)],
    'dusk_hound': [drop(MC + 'bone', 0, 2), drop('dusk_fang', 1, 1, 0.35)],
    'ash_wyrmling': [drop('wyrm_scale', 1, 1, 0.4), drop(MC + 'blaze_powder', 0, 1)],
    'canyon_vulture': [drop(MC + 'feather', 1, 2), drop(MC + 'bone', 0, 1)],
    'mossmere_owl': [drop(MC + 'feather', 0, 2)],
    'echo_bat': [drop('echo_essence', 1, 1, 0.3), drop(MC + 'phantom_membrane', 1, 1, 0.15)],
    'windrider_eagle': [drop(MC + 'feather', 1, 3), drop('windrider_feather', 1, 1, 0.3)],
    'mourning_shade': [drop('pale_ectoplasm', 1, 1, 0.4), drop(MC + 'ghast_tear', 1, 1, 0.05)],
    'bone_gull': [drop(MC + 'feather', 0, 1), drop(MC + 'bone_meal', 0, 2)],
    'rime_moth': [drop('moth_dust', 0, 1), drop('frost_silk', 1, 1, 0.08)],
    'abyssal_angler': [drop(MC + 'cod', 1, 2), drop('angler_lure', 1, 1, 0.3), drop(MC + 'glow_ink_sac', 0, 1)],
    'tidecaller_jelly': [drop(MC + 'slime_ball', 0, 1), drop(MC + 'glow_ink_sac', 0, 1)],
    'tusked_narwhal': [drop(MC + 'cod', 1, 2), drop('narwhal_tusk', 1, 1, 0.3)],
}

ARMOUR = {
    # set: (main material, secondary, trim)
    'duskpine_ranger': ('lynx_pelt', 'minecraft:leather', 'minecraft:string'),
    'emberforged': ('ember_chitin', 'wyrm_scale', 'minecraft:iron_ingot'),
    'rimeguard': ('rime_pearl', 'narwhal_tusk', 'minecraft:iron_ingot'),
    'starweave': ('glimmer_silk', 'pale_ectoplasm', 'star_fragment'),
    'gilded_glyphmail': ('glyph_gold', 'crab_shell', 'minecraft:gold_ingot'),
    'wardens_regalia': ('edict_shard', 'glyph_gold', 'minecraft:diamond'),
}
SHAPES = {
    'helmet': ['ABA', 'A A'],
    'chestplate': ['A A', 'ABA', 'CAC'],
    'leggings': ['ABA', 'A A', 'C C'],
    'boots': ['A A', 'B B'],
}


def shaped(name, pattern, key, result, count=1):
    keys = {k: (v if ':' in v else 'afterworld:' + v) for k, v in key.items() if any(k in row for row in pattern)}
    write(os.path.join(D, 'recipe', f'{name}.json'), {'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern, 'key': keys,
                                                       'result': {'id': aw(result), 'count': count}})


def shapeless(name, items, result, count=1):
    write(os.path.join(D, 'recipe', f'{name}.json'), {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
                                                       'ingredients': [aw(i) if not i.startswith('minecraft:') else i for i in items],
                                                       'result': {'id': aw(result), 'count': count}})


def item(name, weight, lo=1, hi=1):
    e = {'type': 'minecraft:item', 'name': aw(name) if not name.startswith('minecraft:') else name, 'weight': weight}
    if hi > 1 or lo != 1:
        e['modifier'] = [{'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
    return e


def pool(lo, hi, entries, chance=None):
    p = {'rolls': {'type': 'minecraft:uniform', 'min': lo, 'max': hi} if hi != lo else lo, 'entries': entries}
    if chance is not None:
        p['condition'] = {'type': 'minecraft:random_chance', 'chance': chance}
    return p


# What each land's sites keep: (materials, gear)
FLAVOURS = {
    'desert': (['spice_crate', 'sandsteel_ingot', 'glyph_gold', 'sifter_mandible', 'spiced_kebab', 'minecraft:gold_ingot'], ['dunewalkers_chain']),
    'swamp': (['bog_amber', 'toad_oil', 'venom_oil', 'lurker_fang', 'minecraft:slime_ball', 'mirewalk_charm'], ['bogiron_crossbow', 'lurkers_hook']),
    'jungle': (['minecraft:cocoa_beans', 'root_heartwood', 'minecraft:emerald', 'venom_oil', 'minecraft:melon_slice'], ['sporelash_whip', 'wayfarers_fang']),
    'ash': (['ember_chitin', 'ember_core', 'ember_glass', 'ember_oil', 'ember_pepper', 'wyrm_scale'], ['emberbrand', 'ashen_warbanner']),
    'pale': (['pale_ectoplasm', 'grave_wax', 'minecraft:bone', 'minecraft:candle', 'lich_bone'], ['moonsilver_rapier']),
    'gloom': (['minecraft:string', 'minecraft:spider_eye', 'dusk_fang', 'pale_ectoplasm', 'venom_oil'], ['echo_edge']),
    'crystal': (['living_crystal', 'mirror_salt', 'crab_shell', 'minecraft:amethyst_shard', 'echo_shell'], ['shardsong_glaive', 'mirrorglass_chakram']),
    'petal': (['kitsune_tail', 'starlight_tea', 'minecraft:pink_petals', 'moonberry_tart', 'minecraft:cherry_sapling'], ['moonsilver_rapier']),
    'frost': (['rime_pearl', 'rime_core', 'frost_silk', 'frost_bomb', 'frostbite_oil', 'narwhal_tusk'], ['rimefang_spear']),
    'glimmer': (['glimmerfin_scale', 'rime_pearl', 'star_fragment', 'starlight_ink', 'glimmer_silk'], ['starweavers_wand']),
    'sky': (['windrider_feather', 'sky_ambergris', 'moth_dust', 'glimmer_silk', 'minecraft:phantom_membrane'], ['skyhook_harpoon']),
    'highland': (['ram_horn', 'minecraft:emerald', 'windrider_feather', 'survey_scroll', 'hearty_stew'], ['thunderdrum_gauntlets']),
    'steppe': (['ram_horn', 'boar_tusk', 'star_fragment', 'starseed', 'ember_pepper', 'spiced_kebab'], ['thunderdrum_gauntlets', 'duskpine_longbow']),
    'canyon': (['gear_spring', 'clockwork_fragment', 'minecraft:iron_ingot', 'minecraft:raw_gold', 'sandsteel_ingot'], ['gearspring_maul', 'brassmere_repeater']),
    'spore': (['moth_dust', 'echo_essence', 'minecraft:glow_berries', 'draught_of_resonance', 'minecraft:brown_mushroom'], ['sporelash_whip']),
    'coast': (['minecraft:prismarine_shard', 'minecraft:nautilus_shell', 'crab_shell', 'narwhal_tusk', 'minecraft:glow_ink_sac'], ['skyhook_harpoon']),
    'forest': (['lynx_pelt', 'dusk_fang', 'root_heartwood', 'moonberry_tart', 'minecraft:apple', 'hearty_stew'], ['duskpine_longbow', 'wayfarers_fang']),
}
RELICS = ['amulet_of_quiet_steps', 'mothlantern_locket', 'emberheart_pendant', 'rimecore_charm', 'gearsprung_anklet', 'warden_glyph_ring']


def supplies():
    return [item('minecraft:bread', 8, 1, 3), item('minecraft:cooked_mutton', 5, 1, 2), item('minecraft:arrow', 6, 4, 10), item('minecraft:torch', 6, 3, 8),
            item('copper_mark', 7, 2, 6), item('silver_mark', 4, 1, 2), item('minecraft:iron_nugget', 5, 3, 8), item('minecraft:string', 4, 1, 3),
            item('minecraft:leather', 4, 1, 2), item('elixir_of_stamina', 2), item('smoke_bomb', 2, 1, 2), item('survey_scroll', 1), item('waystone_shard', 1),
            item('lockpick', 2, 1, 2), item('minecraft:flint_and_steel', 2)]


def treasure():
    return [item('gold_crown', 5, 1, 2), item('silver_mark', 8, 3, 6), item('edict_shard', 2), item('moonsilver_ingot', 4, 1, 2), item('minecraft:diamond', 2),
            item('minecraft:emerald', 3, 1, 3), item('star_chart_fragment', 2), item('phial_of_second_wind', 2), item('draught_of_resonance', 1),
            item('thunder_bomb', 2, 1, 2), item('frost_bomb', 2, 1, 2), item('ember_oil', 2, 1, 2)]


def chest(name, pools):
    write(os.path.join(D, 'loot_table', 'chests', f'{name}.json'), {'type': 'minecraft:chest', 'pools': pools})


def site_loot():
    for f, (mats, gear) in FLAVOURS.items():
        local = [item(m, 3, 1, 2) for m in mats]
        chest(f'site_cache_{f}', [pool(3, 5, supplies()), pool(1, 2, local)])
        chest(f'site_vault_{f}', [pool(3, 5, treasure()), pool(2, 3, local), pool(1, 1, [item(x, 1) for x in gear], 0.35),
                                  pool(1, 1, [item(r, 1) for r in RELICS], 0.07)])
    for f in ('crystal', 'glimmer', 'steppe'):
        mats, gear = FLAVOURS[f]
        chest(f'star_vault_{f}', [pool(1, 1, [item('star_chart_fragment', 1)]), pool(3, 4, treasure()), pool(2, 3, [item(m, 3, 1, 2) for m in mats])])
    chest('drowned_shrine', [pool(1, 1, [item('bell_rope', 1)]), pool(1, 1, [item('silver_mark', 1, 2, 5)]),
                             pool(2, 3, [item('minecraft:prismarine_shard', 4, 2, 5), item('minecraft:nautilus_shell', 1), item('pale_ectoplasm', 3),
                                         item('minecraft:prismarine_crystals', 3, 1, 4), item('narwhal_tusk', 1)])])
    chest('glyph_vault', [pool(1, 1, [item('glyph_tablet', 1)]), pool(1, 1, [item('glyph_gold', 1, 1, 3)]), pool(3, 4, treasure()),
                          pool(1, 1, [item(r, 1) for r in RELICS], 0.12)])
    chest('site_edict', [pool(1, 1, [item('edict_shard', 1)]), pool(1, 1, [item('silver_mark', 1, 2, 4)]),
                         pool(1, 1, [item('edict_fragment_' + e, 1) for e in ('return', 'echoes', 'borrowed_time', 'weightlessness', 'hunger',
                                                                              'hospitality', 'trespass', 'stillness', 'witness', 'forgotten')], 0.5)])
    print(len(FLAVOURS) * 2 + 2, 'site chests written')


def delve_loot():
    """Underground: what the camps in ordinary caves were left with, and each delve's hoard."""
    chest('cave_cache', [pool(3, 5, [item('minecraft:torch', 8, 4, 10), item('minecraft:bread', 6, 1, 3), item('minecraft:raw_iron', 6, 2, 5),
                                     item('minecraft:raw_copper', 6, 3, 7), item('minecraft:raw_gold', 3, 1, 3), item('minecraft:coal', 6, 3, 8),
                                     item('minecraft:lantern', 3), item('minecraft:rail', 4, 4, 10), item('copper_mark', 6, 2, 6), item('lockpick', 3, 1, 2),
                                     item('survey_scroll', 2), item('elixir_of_stamina', 2), item('minecraft:iron_pickaxe', 1), item('minecraft:glow_berries', 3, 2, 5)]),
                         pool(1, 1, [item(r, 1) for r in RELICS], 0.03)])
    hoards = {'forge': ('canyon', ['gearspring_maul', 'emberbrand', 'brassmere_repeater']), 'glowcap': ('spore', ['sporelash_whip', 'hitodama']),
              'geode': ('crystal', ['shardsong_glaive', 'mirrorglass_chakram']), 'catacomb': ('pale', ['moonsilver_rapier', 'crimson_requiem', 'echo_edge'])}
    for name, (flavour, gear) in hoards.items():
        mats = FLAVOURS[flavour][0]
        chest(f'delve_hoard_{name}', [pool(1, 1, [item('edict_shard', 1)]), pool(4, 6, treasure()), pool(2, 4, [item(m, 3, 1, 3) for m in mats]),
                                      pool(1, 1, [item(x, 1) for x in gear], 0.6), pool(1, 1, [item(r, 1) for r in RELICS], 0.2)])


def boss_loot():
    """What each Warden leaves besides its heart: its own spoils, a purse, an Edict Shard."""
    def drops(*pools):
        return {'type': 'minecraft:entity', 'pools': list(pools)}
    spoils = {
        'ashen_shogun': [pool(1, 1, [item('shogun_steel', 1, 2, 3)]), pool(1, 1, [item('shogun_tsuba', 1)], 0.5)],
        'crimson_lich': [pool(1, 1, [item('lich_bone', 1, 2, 4)]), pool(1, 1, [item('lich_finger_bone', 1)], 0.5), pool(1, 1, [item('crimson_requiem', 1)], 0.35)],
        'rimeback_colossus': [pool(1, 1, [item('rime_core', 1, 3, 5)]), pool(1, 1, [item('rime_pearl', 1, 2, 3)]), pool(1, 1, [item('rimeback_cleaver', 1)], 0.35)],
        'dune_sentinel': [pool(1, 1, [item('sandsteel_ingot', 1, 3, 5)]), pool(1, 1, [item('sentinel_sandglass', 1)], 0.5), pool(1, 1, [item('dunewalkers_chain', 1)], 0.35)],
        'gilded_warden': [pool(1, 1, [item('glyph_gold', 1, 4, 6)]), pool(1, 1, [item('glyphwarden_aegis', 1)], 0.4), pool(1, 1, [item('warden_glyph_ring', 1)], 0.3)],
    }
    for k, extra in spoils.items():
        write(os.path.join(D, 'loot_table', 'entities', f'{k}.json'),
              drops(pool(1, 1, [item('gold_crown', 1, 3, 5)]), pool(1, 1, [item('edict_shard', 1)]), *extra))


def ladder_loot():
    """The Warden's Ladder: a chest for each floor won (richer each tier), a better one for each
    echo, and the summit's hoard."""
    tiers = [
        (FLAVOURS['petal'][0] + FLAVOURS['forest'][0], ['moonsilver_rapier', 'duskpine_longbow', 'wayfarers_fang'], ['shogun_steel', 'shogun_tsuba']),
        (FLAVOURS['pale'][0] + FLAVOURS['ash'][0], ['emberbrand', 'ashen_warbanner', 'echo_edge'], ['lich_bone', 'lich_finger_bone', 'crimson_requiem']),
        (FLAVOURS['frost'][0] + FLAVOURS['glimmer'][0], ['rimefang_spear', 'starweavers_wand'], ['rime_core', 'rime_pearl', 'rimeback_cleaver']),
        (FLAVOURS['desert'][0] + FLAVOURS['canyon'][0], ['dunewalkers_chain', 'gearspring_maul', 'brassmere_repeater'], ['sandsteel_ingot', 'sentinel_sandglass']),
        (FLAVOURS['crystal'][0] + ['glyph_gold', 'clockwork_fragment'], ['shardsong_glaive', 'mirrorglass_chakram', 'thunderdrum_gauntlets'],
         ['glyph_gold', 'glyphwarden_aegis', 'warden_glyph_ring']),
    ]
    tonics = [item('elixir_of_stamina', 3), item('phial_of_second_wind', 3), item('minecraft:golden_apple', 1), item('minecraft:cooked_beef', 4, 2, 4),
              item('thunder_bomb', 2, 1, 2), item('frost_bomb', 2, 1, 2), item('smoke_bomb', 2, 1, 2), item('minecraft:arrow', 3, 8, 16)]
    for n, (mats, gear, spoils) in enumerate(tiers, start=1):
        coins = [item('silver_mark', 6, 2 * n, 4 * n), item('gold_crown', n, 1, n), item('copper_mark', 4, 5, 12)]
        local = [item(m, 3, 1, 1 + n // 2) for m in mats]
        chest(f'ladder_{n}', [pool(2, 3, coins), pool(1, 2, tonics), pool(1, 2, local), pool(1, 1, [item(x, 1) for x in gear], 0.06 * n),
                              pool(1, 1, [item(r, 1) for r in RELICS], 0.015 * n), pool(1, 1, [item('edict_shard', 1)], 0.05 * n)])
        chest(f'ladder_echo_{n}', [pool(1, 1, [item('gold_crown', 1, 2 * n, 3 * n)]), pool(1, 1, [item('edict_shard', 1, 1, 1 + n // 2)]),
                                   pool(1, 1, [item(x, 1) for x in gear]), pool(1, 2, [item(x, 1, 1, 2) for x in spoils]), pool(2, 3, local),
                                   pool(1, 1, [item(r, 1) for r in RELICS], 0.15 + 0.05 * n), pool(1, 2, tonics)])
    chest('ladder_summit', [pool(1, 1, [item('gold_crown', 1, 12, 18)]), pool(1, 1, [item('edict_shard', 1, 3, 4)]),
                            pool(2, 2, [item(r, 1) for r in RELICS]), pool(2, 3, [item(x, 1) for t in tiers for x in t[1]]),
                            pool(1, 1, [item('minecraft:enchanted_golden_apple', 1)]), pool(1, 1, [item('minecraft:netherite_ingot', 1)])])
    chest('proving_ring', [pool(2, 3, [item('silver_mark', 6, 3, 6), item('gold_crown', 2, 1, 2)]), pool(1, 2, tonics),
                           pool(1, 1, [item(x, 1) for t in tiers[:2] for x in t[1]], 0.3), pool(1, 1, [item(r, 1) for r in RELICS], 0.06),
                           pool(1, 1, [item('edict_shard', 1)], 0.2)])
    chest('needle_cache', [pool(2, 3, [item('silver_mark', 6, 2, 5), item('gold_crown', 1, 1, 2)]), pool(1, 1, [item('windrider_feather', 1, 2, 4)]),
                           pool(1, 2, tonics), pool(1, 1, [item('skyhook_harpoon', 1), item('duskpine_longbow', 1)], 0.25),
                           pool(1, 1, [item(r, 1) for r in RELICS], 0.05)])
    print('ladder chests written')


# What each creature is made of, for the sound and the chips of a hit (dev.afterworld.combat.Impacts).
# Anything in none of these is flesh. Vanilla entries are optional so a missing mob never breaks a tag.
MATERIALS = {
    'metal': ['rust_golemite', 'clockwork_sentry', 'gearhound', 'ashen_shogun', 'gilded_warden', 'king_of_seconds', 'bell_ringer', MC + 'iron_golem'],
    'stone': ['dune_sentinel', 'pilgrim', 'mossback', 'ashen_husk'],
    'crystal': ['crystal_crab', 'prism_golem', 'shardling'],
    'wood': ['vine_strangler', 'rootling_sentinel', 'training_dummy', MC + 'creaking'],
    'bone': ['bone_gull', 'crimson_lich', MC + 'skeleton', MC + 'stray', MC + 'wither_skeleton', MC + 'bogged', MC + 'skeleton_horse', MC + 'wither'],
    'chitin': ['cinder_beetle', 'sand_sifter', 'glyph_scarab', 'gloomweaver', MC + 'spider', MC + 'cave_spider', MC + 'silverfish', MC + 'endermite',
               MC + 'bee', MC + 'shulker'],
    'spirit': ['rule_wraith', 'wisp', 'mourning_shade', 'petal_sprite', 'echo_stalker', 'lantern_moth', 'rime_moth', MC + 'vex', MC + 'allay',
               MC + 'ghast', MC + 'breeze', MC + 'blaze'],
    'slime': ['fen_toad', 'sporeling', 'mire_shambler', 'fungal_brute', 'tidecaller_jelly', 'bog_lurker', MC + 'slime', MC + 'magma_cube', MC + 'frog'],
    'ice': ['ice_crawler', 'rime_wight', 'rimeback_colossus', MC + 'snow_golem'],
    'scale': ['ember_salamander', 'ash_wyrmling', 'abyssal_angler', 'glimmerfin', 'mirror_ray', MC + 'guardian', MC + 'elder_guardian',
              MC + 'axolotl', MC + 'armadillo', MC + 'turtle', MC + 'cod', MC + 'salmon', MC + 'tropical_fish', MC + 'pufferfish'],
    'hide': ['dune_strider', 'thornback_boar', 'highland_ram', 'floe_seal', 'tusked_narwhal', 'rimehorn', 'rime_brute', 'skywhale', 'duskstrider',
             'hollow_stag', MC + 'cow', MC + 'mooshroom', MC + 'horse', MC + 'donkey', MC + 'mule', MC + 'llama', MC + 'trader_llama', MC + 'camel',
             MC + 'hoglin', MC + 'zoglin', MC + 'ravager', MC + 'sniffer', MC + 'polar_bear', MC + 'goat', MC + 'pig', MC + 'sheep', MC + 'panda'],
}


def materials():
    for name, ids in MATERIALS.items():
        values = [i if not i.startswith(MC) else {'id': i, 'required': False} for i in (aw(x) for x in ids)]
        write(os.path.join(D, 'tags', 'entity_type', 'material', name + '.json'), {'values': values})


def main():
    materials()
    boss_loot()
    site_loot()
    ladder_loot()
    delve_loot()
    for mob, pools in LOOT.items():
        write(os.path.join(D, 'loot_table', 'entities', f'{mob}.json'), {'type': 'minecraft:entity', 'pools': pools})
    for s, (a, b, c) in ARMOUR.items():
        for piece, pattern in SHAPES.items():
            shaped(f'{s}_{piece}', pattern, {'A': a, 'B': b, 'C': c}, f'{s}_{piece}')
    # Oils, bombs and elixirs from what the wilds give.
    shapeless('ember_oil', ['toad_oil', 'minecraft:blaze_powder'], 'ember_oil', 2)
    shapeless('frostbite_oil', ['toad_oil', 'rime_pearl'], 'frostbite_oil', 2)
    shapeless('venom_oil', ['toad_oil', 'minecraft:spider_eye'], 'venom_oil', 2)
    shapeless('elixir_of_stamina', ['toad_oil', 'minecraft:sugar', 'minecraft:glow_berries'], 'elixir_of_stamina')
    shapeless('phial_of_second_wind', ['minecraft:glass_bottle', 'minecraft:golden_carrot', 'minecraft:glow_berries'], 'phial_of_second_wind')
    shapeless('draught_of_resonance', ['minecraft:glass_bottle', 'angler_lure', 'kitsune_tail'], 'draught_of_resonance', 2)
    shapeless('smoke_bomb', ['minecraft:gunpowder', 'minecraft:charcoal', 'minecraft:paper'], 'smoke_bomb', 2)
    shapeless('frost_bomb', ['minecraft:gunpowder', 'rime_pearl', 'minecraft:snowball'], 'frost_bomb', 2)
    shapeless('thunder_bomb', ['minecraft:gunpowder', 'minecraft:copper_ingot', 'echo_essence'], 'thunder_bomb', 2)
    shapeless('waystone_shard', ['minecraft:amethyst_shard', 'root_heartwood'], 'waystone_shard', 2)
    shapeless('lockpick', ['minecraft:iron_nugget', 'minecraft:iron_nugget'], 'lockpick', 2)
    # A few weapons from the beasts that inspired them.
    shaped('skyhook_harpoon', ['  T', ' S ', 'S  '], {'T': 'narwhal_tusk', 'S': 'minecraft:stick'}, 'skyhook_harpoon')
    shaped('duskpine_longbow', [' SL', 'S R', ' SL'], {'S': 'minecraft:stick', 'L': 'lynx_pelt', 'R': 'minecraft:string'}, 'duskpine_longbow')
    shaped('mirrorglass_chakram', [' C ', 'CIC', ' C '], {'C': 'crab_shell', 'I': 'minecraft:iron_ingot'}, 'mirrorglass_chakram')
    shaped('sporelash_whip', ['  V', ' V ', 'H  '], {'V': 'minecraft:vine', 'H': 'root_heartwood'}, 'sporelash_whip')
    shaped('wayfarers_fang', ['  F', ' F ', 'S  '], {'F': 'dusk_fang', 'S': 'minecraft:stick'}, 'wayfarers_fang')
    shaped('thunderdrum_gauntlets', ['H H', 'ICI'], {'H': 'ram_horn', 'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot'}, 'thunderdrum_gauntlets')
    print(len(LOOT), 'loot tables, recipes written')


if __name__ == '__main__':
    main()

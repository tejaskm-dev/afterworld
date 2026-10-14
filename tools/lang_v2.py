"""Afterworld 2.0's English: the economy, the second armoury, Aspects, Gifts, skills and the
Character screen. Imported by gen_lang.py, which calls add(put)."""


def add(put):
    # ------------------------------------------------------------------ creative tabs
    put('itemGroup.afterworld.armor', 'Afterworld: Armour')
    put('itemGroup.afterworld.supplies', 'Afterworld: Supplies')
    put('itemGroup.afterworld.trade', 'Afterworld: Trade')

    # ------------------------------------------------------------------ keys
    put('key.afterworld.dodge', 'Dodge (moving) / Parry (still)')
    put('key.afterworld.ultimate', 'Unleash Aspect')
    put('key.afterworld.aspect', 'Next Aspect')
    put('key.afterworld.character', 'Character')

    # ------------------------------------------------------------------ the economy
    coins = {
        'copper_mark': ('Copper Mark', 'The Guild\'s smallest coin. Worth one Mark.'),
        'silver_mark': ('Silver Mark', 'Ten Marks, stamped with a waystone.'),
        'gold_crown': ('Gold Crown', 'A hundred Marks. Merchants bite it anyway.'),
    }
    for k, (n, d) in coins.items():
        put(f'item.afterworld.{k}', n)
        put(f'item.afterworld.{k}.lore', d)
    put('item.afterworld.lost_purse', 'Lost Purse')
    put('tooltip.afterworld.lost_purse', 'Holds %s Marks. Pick it up to take them back.')
    goods = {
        'spice_crate': ('Spice Crate', 'Desert spice from the Hourglass Oasis. The cold peaks pay well for it.'),
        'frost_silk': ('Frost Silk', 'Spun by rime moths. Desert tailors would trade water for it.'),
        'ember_glass': ('Ember Glass', 'Glass that never quite cools. Bog folk want it for their lamps.'),
        'bog_amber': ('Bog Amber', 'Fen resin with old things caught inside. Smiths use it as flux.'),
        'starlight_ink': ('Starlight Ink', 'Ink that shows only by night. Cartographers fight over it.'),
        'sky_ambergris': ('Sky Ambergris', 'Shed by skywhales. Perfumers anywhere will buy it.'),
        'clockwork_curio': ('Clockwork Curio', 'A small machine that does one useless thing beautifully.'),
        'mirror_salt': ('Mirror Salt', 'Salt from the mirror flats. It keeps meat and secrets.'),
    }
    for k, (n, d) in goods.items():
        put(f'item.afterworld.{k}', n)
        put(f'item.afterworld.{k}.lore', d)
    put('message.afterworld.purse_dropped', 'You dropped a purse of %s Marks where you fell.')
    put('message.afterworld.shop_refused', '%s will not trade with you right now.')
    put('notice.afterworld.interest', 'The Guild Ledger paid %s Marks in interest.')
    put('shop.afterworld.locked', 'Needs Renown %s.')
    put('shop.afterworld.sold_out', 'Sold out until tomorrow.')
    put('shop.afterworld.too_poor', 'You can\'t afford that.')
    put('shop.afterworld.bought', 'Bought %s × %s for %s Marks.')
    put('shop.afterworld.not_wanted', 'They don\'t want that.')
    put('shop.afterworld.sold', 'Sold %s × %s for %s Marks.')
    put('shop.afterworld.bought_back', 'Bought back %s.')
    put('shop.afterworld.haggled_already', 'You\'ve already haggled with them today.')
    put('shop.afterworld.hint', 'Prices move with demand. Sell what they lack, buy what\'s plentiful.')
    put('shop.afterworld.needs_renown', 'Renown %s')
    put('shop.afterworld.qty', 'Qty')
    put('shop.afterworld.max', 'Max')
    # The shop screen itself.
    put('notice.afterworld.climb_help', 'Climbing: WASD along the wall, Jump to leap, Back+Jump to kick off, Sneak to let go')
    put('shop.afterworld.tab.buy', 'Buy')
    put('shop.afterworld.tab.sell', 'Sell')
    put('shop.afterworld.tab.buyback', 'Buy back')
    put('shop.afterworld.buy', 'Buy')
    put('shop.afterworld.sell', 'Sell')
    put('shop.afterworld.buy_back', 'Buy back')
    put('shop.afterworld.you_pay', 'You pay %s')
    put('shop.afterworld.you_get', 'You get %s')
    put('shop.afterworld.stock', '%s in stock')
    put('shop.afterworld.sold_out_short', 'Sold out')
    put('shop.afterworld.empty', 'Nothing for sale right now.')
    put('shop.afterworld.nothing_to_sell', 'You carry nothing they want.')
    put('shop.afterworld.no_buyback', 'Nothing you sold is waiting here.')
    put('shop.afterworld.pick', 'Pick something to see it up close.')
    put('shop.afterworld.pick_sell', 'Pick something from your pack to sell.')
    put('shop.afterworld.deal_good', 'Haggled: %s%% off today')
    put('shop.afterworld.deal_bad', 'Offended: +%s%% today')
    put('shop.afterworld.haggle', 'Haggle')
    put('shop.afterworld.haggled', 'Haggled today')
    put('shop.afterworld.haggle_win', '(%s%% off today)')
    put('shop.afterworld.haggle_lose', '(+%s%% today)')
    put('shop.afterworld.tactic.flatter', 'Flatter')
    put('shop.afterworld.tactic.bargain', 'Bargain hard')
    put('shop.afterworld.tactic.rival', 'Name a rival')
    # How each temperament takes each tactic: flatter (0), bargain (1), rival (2).
    haggles = {
        'vain': [('They preen. "Well. For someone with taste..."', 'They see straight through it.'),
                 ('They sigh, but give way.', '"Haggling? How common."'),
                 ('"Them? Please. Fine, fine."', '"Then go and buy from them."')],
        'shrewd': [('They laugh. "Smooth. All right."', '"Flattery\'s free. Prices aren\'t."'),
                   ('They respect a hard bargain. Hands shake.', 'They don\'t budge an inch.'),
                   ('"Their stock\'s rubbish. But fine."', '"Then enjoy their rusty nails."')],
        'jealous': [('They warm a little.', 'They narrow their eyes.'),
                    ('They grumble, but give way.', '"Take it or leave it."'),
                    ('"Them? Over my dead body. Here."', '"Go, then. See if I care." They clearly care.')],
    }
    for t, lines in haggles.items():
        for i, (win, lose) in enumerate(lines):
            put(f'shop.afterworld.haggle.{t}.{i}.win', win)
            put(f'shop.afterworld.haggle.{t}.{i}.lose', lose)
    put('shop.afterworld.cat.all', 'All')
    for c, n in {'supplies': 'Supplies', 'gear': 'Gear', 'arms': 'Arms', 'potions': 'Potions', 'materials': 'Materials',
                 'goods': 'Trade Goods', 'curios': 'Curios'}.items():
        put(f'shop.afterworld.cat.{c}', n)
    for i, n in enumerate(['Despised', 'Distrusted', 'Neutral', 'Trusted', 'Honoured']):
        put(f'shop.afterworld.rep.{i}', n)
    put('screen.afterworld.bank', 'The Guild Ledger')
    put('screen.afterworld.bank.sub', 'Banked Marks are safe from death and earn 1%% a day.')
    put('screen.afterworld.bank.carried', 'Carried')
    put('screen.afterworld.bank.banked', 'Banked')
    put('screen.afterworld.bank.interest', 'Interest today: %s Marks')
    put('screen.afterworld.bank.deposit', 'Deposit %s')
    put('screen.afterworld.bank.deposit_all', 'Deposit all')
    put('screen.afterworld.bank.withdraw', 'Withdraw %s')
    put('screen.afterworld.bank.withdraw_all', 'Withdraw all')
    put('screen.afterworld.waystones.charter', 'Charter flight: %s Marks a leg')
    put('message.afterworld.travel_no_camp', 'There is no camp to return to in this world.')

    # ------------------------------------------------------------------ progression
    put('renown.afterworld.level', 'Renown %s')
    put('renown.afterworld.insight', '%s Insight to spend (press K)')
    put('notice.afterworld.renown', '+%s Renown: %s')
    put('notice.afterworld.surveyed', 'Surveyed: %s')
    put('notice.afterworld.weapon_hums', '%s hums. Marta can wake it.')
    put('growth.afterworld.line', '%s  ·  %s / %s renown')
    put('growth.afterworld.ready', 'It hums: Marta can wake it at her anvil.')
    put('growth.afterworld.max', '%s  ·  fully awake')
    put('growth.afterworld.tier.1', 'Dormant')
    put('growth.afterworld.tier.2', 'Awakened')
    put('growth.afterworld.tier.3', 'Ascendant')
    put('message.afterworld.minigame_missing', 'That trial isn\'t ready yet (%s).')

    # Side questlines (tracker text per stage).
    SIDE = {
        'stars': ('The Falling Stars', ['Find three star chart fragments for Selene.', 'Align the star dials with Selene at night.']),
        'wayfarer': ('Mending the Wayfarer', ['Bring Odo six gear springs and two clockwork fragments.', 'Race the Wayfarer\'s rings for Odo.']),
        'brew': ('The Fen-Keeper\'s Brew', ['Bring Mab three lurker fangs, bog amber and two pinches of moth dust.', 'Brew the draught at Mab\'s cauldron.',
                                           'Drink the draught and cross the black water.']),
        'hunt': ('Something Taller than Trees', ['Follow the trail Torvald showed you.', 'Tell Torvald the beast is dead.', 'Best Torvald at the archery butts.']),
        'bell': ('The Drowned Bell', ['Find the drowned chapel\'s bell rope.', 'Listen to the bell\'s song and play it back.']),
        'caravan': ('The Silent Caravan', ['Search the wrecked caravan for clues.', 'Find the caravan folk where they hid.']),
        'edges': ('The Edges of the Map', ['Survey three landmarks with Wren\'s sextant.']),
        'meal': ('A Proper Meal', ['Bring Bram two beef, three carrots, two potatoes and an ember core.', 'Cook the stew at Bram\'s pot.', 'Deliver the stew to the camp.']),
        'forge': ('Waking Steel', ['Fight with a growing weapon until it hums.']),
        'duel': ('The Lantern Duel', ['Face Akane in the duel of lanterns.']),
    }
    for k, (n, stages) in SIDE.items():
        put(f'side.afterworld.{k}', n)
        for i, s in enumerate(stages):
            put(f'side.afterworld.{k}.{i}', s)
    put('notice.afterworld.side_started', 'New questline: %s')
    put('notice.afterworld.side_advanced', 'Questline advanced: %s')
    put('notice.afterworld.side_done', 'Questline complete: %s')

    # ------------------------------------------------------------------ the second armoury: weapons
    WEAPONS = {
        'moonsilver_rapier': ('Moonsilver Rapier', 'A duellist\'s blade, cold as a winter moon.'),
        'duskpine_longbow': ('Duskpine Longbow', 'Strung with moth silk. It sings when drawn.'),
        'bogiron_crossbow': ('Bog-iron Crossbow', 'Its bolts are soaked in lurker venom.'),
        'wayfarers_fang': ('Wayfarer\'s Fang', 'A long knife for short arguments.'),
        'mirrorglass_chakram': ('Mirrorglass Chakram', 'Thrown, it finds every face in the room.'),
        'starweavers_wand': ('Starweaver\'s Wand', 'Tipped with a star that refused to fall.'),
        'ashen_warbanner': ('Ashen Warbanner', 'Carried at the last stand of the Ashen Guard. It still smoulders.'),
        'sporelash_whip': ('Sporelash Whip', 'Braided from fen roots. It leaves spores in every wound.'),
        'brassmere_repeater': ('Brassmere Repeater', 'Captain Odo\'s pride: three shots and a great deal of noise.'),
        'skyhook_harpoon': ('Skyhook Harpoon', 'Made for skywhales. Works on walls.'),
        'thunderdrum_gauntlets': ('Thunderdrum Gauntlets', 'Clap them, and the sky answers.'),
        'hitodama': ('Hitodama', 'The Ashen Shogun\'s blade. Its cuts burn with ghost-light.'),
        'crimson_requiem': ('Crimson Requiem', 'The Crimson Lich\'s scythe. It remembers everyone it has reaped.'),
        'rimeback_cleaver': ('Rimeback Cleaver', 'Cut from the Rimeback Colossus\'s horn.'),
        'dunewalkers_chain': ('Dunewalker\'s Chain', 'A link of the Dune Sentinel\'s chains. It still wants to drag.'),
        'glyphwarden_aegis': ('Glyphwarden Aegis', 'A shell-plate of the Gilded Warden, carved with judgment.'),
    }
    for k, (n, d) in WEAPONS.items():
        put(f'item.afterworld.{k}', n)
        put(f'item.afterworld.{k}.lore', d)
    put('item.afterworld.duskpine_longbow.desc', 'Fully drawn arrows become Moonshots: they glow and curve toward foes. Hits harder as it grows.')
    put('item.afterworld.bogiron_crossbow.desc', 'Bolts poison what they hit, and the poison spreads to anything standing beside it.')
    put('item.afterworld.starweavers_wand.desc', 'Fires star bolts that lean toward foes. %s / %s charges; one returns every three seconds. Ascendant: bolts splinter.')
    put('item.afterworld.brassmere_repeater.desc', 'Three thunderous rounds (%s / %s loaded), then a reload that costs one gunpowder.')

    ARTS = {
        'riposte': ('Riposte', 'Taught in the Moonsilver Court, where duels end in one touch.',
                    'Take a stance for a heartbeat. Anything that strikes you is countered for triple damage and stunned; if nothing does, you lunge.',
                    'Every third hit in a row on the same foe flourishes into a second cut.'),
        'fang_flurry': ('Fang Flurry', 'The wayfarer\'s answer to being cornered.',
                        'Dart forward and open whatever you reach with a flurry of quick cuts (more as the Fang grows).',
                        'Strikes from behind bite deeper.'),
        'chakram': ('Ricochet', 'It always comes home.',
                    'Throw the chakram: it leaps from foe to foe (three, then five) and returns to your hand.',
                    'Hits sometimes send a shard into a second foe.'),
        'rally': ('Rally', 'Plant it, and the line holds.',
                  'Plant the banner for ten seconds: allies near it gain strength, resistance and regeneration; foes near it weaken and slow.',
                  'Each hit wraps you in a little ashen courage (absorption, up to two hearts).'),
        'spore_lash': ('Spore Lash', 'The fen bites back.',
                       'Crack the whip in a line: everything it reaches is poisoned and dragged toward you, and a spore cloud lingers where it ends.',
                       'Hits sometimes poison.'),
        'harpoon': ('Skyhook', 'Point at where you want to be.',
                    'Fire the harpoon up to 24 blocks (32 when Awakened): spear a creature and haul it in, or spear a wall and haul yourself there. Sneak to go to the creature instead.',
                    'Hits pull foes toward you.'),
        'thunderclap': ('Thunderclap', 'The drum of the storm-giants.',
                        'Clap: a cone of thunder strikes, stuns and hurls back everything in front of you, and lightning finds the closest.',
                        'Every fourth hit in a row arcs lightning to another foe.'),
        'lantern_cuts': ('Lantern Cuts', 'Three draws too quick to see.',
                         'Blink behind up to three foes in front of you and cut each; each cut leaves a ghost lantern that bursts in soul fire a heartbeat later.',
                         'Hits set foes alight with ghost-fire.'),
        'crimson_reap': ('Crimson Reap', 'The scythe drinks.',
                         'One wide crimson sweep: you heal for part of the damage, and anything it kills rises as a thrall to fight for you.',
                         'Each hit mends you a little.'),
        'avalanche': ('Avalanche', 'The Colossus\'s descent.',
                      'Leap and come down as an avalanche: ice erupts in a line ahead of you, freezing and throwing everything it touches.',
                      'Hits chill; frozen foes take half again as much damage.'),
        'chain_pull': ('Dune Chains', 'Nothing escapes the dune.',
                       'Cast the chain in a sweeping arc: every foe in front of you is dragged into one heap, then blinded by a burst of sand.',
                       'Hits slow foes.'),
        'glyph_beam': ('Judgment', 'The Warden\'s verdict.',
                       'Brace behind the aegis and pour out a beam of judgment for two seconds. You take half damage while it burns.',
                       'Hits sometimes brand a foe for extra radiant damage. It blocks like a shield.'),
    }
    for k, (n, lore, desc, passive) in ARTS.items():
        put(f'art.afterworld.{k}', n)
        put(f'art.afterworld.{k}.lore', lore)
        put(f'art.afterworld.{k}.desc', desc)
        put(f'art.afterworld.{k}.passive', passive)
    put('art.afterworld.crimson_reap.thrall', 'Crimson Thrall')

    # ------------------------------------------------------------------ armour sets
    SETS = {
        'wardens_regalia': ('Warden\'s Regalia', 'Set: Resonance builds a quarter faster, and you are hard to knock back.'),
        'duskpine_ranger': ('Duskpine Ranger', 'Set: projectiles hit 30%% harder, you move 10%% faster and have 20 more stamina.'),
        'emberforged': ('Emberforged', 'Set: fire and lava can\'t hurt you, and whatever strikes you catches fire.'),
        'rimeguard': ('Rimeguard', 'Set: cold can\'t touch you, attackers are slowed and chilled, +2 armour toughness.'),
        'starweave': ('Starweave', 'Set: Edicts recover a fifth faster, and you see in the dark.'),
        'gilded_glyphmail': ('Gilded Glyphmail', 'Set: a quarter of all arrows and spells are turned aside, and +2 hearts.'),
    }
    pieces = {'helmet': 'Helm', 'chestplate': 'Cuirass', 'leggings': 'Greaves', 'boots': 'Boots'}
    for k, (n, bonus) in SETS.items():
        put(f'set.afterworld.{k}', n)
        put(f'set.afterworld.{k}.bonus', bonus)
        for p, pn in pieces.items():
            put(f'item.afterworld.{k}_{p}', f'{n} {pn}')
    put('set.afterworld.label', '%s (%s/4)')

    # ------------------------------------------------------------------ relics
    RELICS = {
        'ring_of_second_breath': ('Ring of Second Breath', 'Once every ten minutes, a killing blow leaves you standing instead.'),
        'amulet_of_quiet_steps': ('Amulet of Quiet Steps', 'While crouched, creatures more than eight blocks away lose track of you.'),
        'emberheart_pendant': ('Emberheart Pendant', 'Your blows set foes alight, and fire hurts you half as much.'),
        'rimecore_charm': ('Rimecore Charm', 'You never freeze, and your blows slow what they strike.'),
        'glimmerfin_scale': ('Glimmerfin Scale', 'In water you breathe freely and swim like a glimmerfin.'),
        'mothlantern_locket': ('Mothlantern Locket', 'A moth\'s light lets you see in the dark.'),
        'pilgrims_bead': ('Pilgrim\'s Bead', 'Every move costs a fifth less stamina.'),
        'lucky_sextant': ('Lucky Sextant', 'More Marks from every purse, and the Survey Sextant recovers twice as fast.'),
        'bram_purse_knot': ('Bram\'s Purse Knot', 'Creatures drop 15%% more Marks.'),
        'gearsprung_anklet': ('Gearsprung Anklet', 'A spring in your step: you jump higher, fall further unhurt, and the hook recovers faster.'),
        'echo_shell': ('Echo Shell', 'Weapon Arts recover a fifth faster.'),
        'lich_finger_bone': ('Lich\'s Finger Bone', 'Every kill mends two hearts.'),
        'shogun_tsuba': ('Shogun\'s Tsuba', 'Your parry window lasts much longer.'),
        'sentinel_sandglass': ('Sentinel\'s Sandglass', 'Your dodge stays untouchable for almost twice as long.'),
        'warden_glyph_ring': ('Warden\'s Glyph Ring', 'Resonance builds faster.'),
        'starseed': ('Starseed', 'You are always Starlit: Resonance and stamina return faster.'),
        'caravan_seal': ('Rashid\'s Caravan Seal', 'Guild merchants give you fifteen in the hundred off.'),
    }
    for k, (n, d) in RELICS.items():
        put(f'item.afterworld.{k}', n)
        put(f'item.afterworld.{k}.desc', d)
    put('relic.afterworld.label', 'Relic: use to wear it (three slots; manage them on the Character screen).')
    put('message.afterworld.relics_full', 'All three relic slots are full. Take one off on the Character screen (K).')
    put('message.afterworld.relic_worn', 'You wear %s.')

    # ------------------------------------------------------------------ gifts and gadgets
    GIFTS = {
        'glide': ('Moth-silk Glider', 'Glide', 'Hold jump while falling to glide. Drains stamina slowly.', 'Hold jump while falling.',
                  'Hold jump while falling to glide; look down to dive faster.'),
        'double_jump': ('Featherstep Boots', 'Double Jump', 'Jump again in mid-air.', 'Jump again in mid-air.',
                        'Press jump in mid-air to jump again.'),
        'climb': ('Climbing Claws', 'Sure Grip', 'Climb faster and for longer: the claws take half the strain.', 'Climb as usual; the claws do the rest.',
                  'Anyone can climb: jump at a wall and keep pushing into it. WASD moves along the wall, jump leaps up it, back and jump kicks off, sneak lets go. With the claws it is faster and costs half the stamina.'),
        'water_walk': ('Mirewalk Charm', 'Mirewalk', 'Sprint across water as if it were stone. Drains stamina.', 'Sprint onto water.',
                       'Sprint onto water to run across it.'),
    }
    item_for = {'glide': 'moth_silk_glider', 'double_jump': 'featherstep_boots', 'climb': 'climbing_claws', 'water_walk': 'mirewalk_charm'}
    for k, (item, gift, desc, short, how) in GIFTS.items():
        put(f'item.afterworld.{item_for[k]}', item)
        put(f'gift.afterworld.{k}', gift)
        put(f'gift.afterworld.{k}.desc', desc)
        put(f'gift.afterworld.{k}.short', short)
        put(f'gift.afterworld.{k}.how', how)
    put('gift.afterworld.label', 'Gift: use it once to learn its ability for good.')
    put('gift.afterworld.learned', 'Gift learned')
    put('message.afterworld.gift_known', 'You already have that gift.')
    GADGETS = {
        'grapple': ('wayfarers_hook', 'Wayfarer\'s Hook', 'Fire at any surface within 32 blocks and be hauled there. No fall damage on the way.'),
        'skyward': ('skyward_bell', 'Skyward Bell', 'Ring it and the air throws you high into the sky; you drift down slowly after.'),
        'whistle': ('wayfarers_whistle', 'Wayfarer\'s Whistle', 'Calls your Duskstrider, a stag of the dusk roads that only you may ride.'),
        'sextant': ('survey_sextant', 'Survey Sextant', 'Sight the nearest landmark and mark it on your map.'),
        'wing': ('prototype_wing', 'Prototype Wing', 'Use in mid-air for a few seconds of clumsy flight.'),
    }
    for k, (item, n, d) in GADGETS.items():
        put(f'item.afterworld.{item}', n)
        put(f'gadget.afterworld.{k}.desc', d)
    put('message.afterworld.hook_miss', 'Nothing within reach to hook.')
    put('message.afterworld.wing_ground', 'Jump first: the wing only works in the air.')
    put('message.afterworld.whistle_riding', 'You\'re already riding.')
    put('entity.afterworld.duskstrider', 'Duskstrider')
    put('entity.afterworld.magic_projectile', 'Star Bolt')

    # ------------------------------------------------------------------ consumables and meals
    CONS = {
        'elixir_of_stamina': ('stamina_elixir', 'Elixir of Stamina', 'Fills your stamina and enlarges it by half for three minutes.'),
        'draught_of_resonance': ('resonance_draught', 'Draught of Resonance', 'Half a charge of Resonance for your Aspect.'),
        'phial_of_second_wind': ('second_wind', 'Phial of Second Wind', 'Strong regeneration for six seconds.'),
        'ember_oil': ('ember_oil', 'Ember Oil', 'Hold in your off hand and use: your weapon burns what it strikes for 60 hits.'),
        'frostbite_oil': ('frost_oil', 'Frostbite Oil', 'Hold in your off hand and use: your weapon chills and slows for 60 hits.'),
        'venom_oil': ('venom_oil', 'Venom Oil', 'Hold in your off hand and use: your weapon poisons for 60 hits.'),
        'smoke_bomb': ('smoke_bomb', 'Smoke Bomb', 'Throw: blinds foes, makes them lose you, and hides you for five seconds.'),
        'frost_bomb': ('frost_bomb', 'Frost Bomb', 'Throw: freezes everything it bursts on.'),
        'thunder_bomb': ('thunder_bomb', 'Thunder Bomb', 'Throw: a lightning strike that stuns.'),
        'waystone_shard': ('waystone_shard', 'Waystone Shard', 'Crush it to return to the Wayfarers\' camp.'),
        'survey_scroll': ('survey_scroll', 'Survey Scroll', 'Read it to mark the nearest landmark on your map.'),
        'lockpick': ('lockpick', 'Lockpick', 'Opens locked chests in ruins and crypts (a small trial).'),
    }
    for item, (kind, n, d) in CONS.items():
        put(f'item.afterworld.{item}', n)
        put(f'consumable.afterworld.{kind}', d)
    put('message.afterworld.oil_how', 'Hold a weapon in your main hand and the oil in your off hand.')
    put('message.afterworld.oil_coated', '%s is coated.')
    put('message.afterworld.oil_gone', 'The oil has worn off your weapon.')
    put('message.afterworld.repeater_powder', 'The Repeater needs gunpowder to reload.')
    MEALS = {
        'hearty_stew': ('Hearty Stew', 'Bram\'s recipe. Two extra hearts for five minutes.'),
        'moonberry_tart': ('Moonberry Tart', 'You see in the dark and walk quicker.'),
        'spiced_kebab': ('Spiced Kebab', 'Rashid\'s caravan fare. Quick hands, and the heat can\'t touch you.'),
        'glimmerfin_sushi': ('Glimmerfin Sushi', 'Breathe and swim like a fish.'),
        'ember_pepper': ('Ember Pepper', 'Strength, and a mouth full of fire.'),
        'starlight_tea': ('Starlight Tea', 'Selene\'s blend: you are Starlit for five minutes.'),
    }
    for k, (n, d) in MEALS.items():
        put(f'item.afterworld.{k}', n)
        put(f'item.afterworld.{k}.lore', d)
    put('effect.afterworld.starlit', 'Starlit')

    MATS = {
        'raw_moonsilver': ('Raw Moonsilver', 'Found in moonlit peaks. Cold to the touch even in the sun.'),
        'moonsilver_ingot': ('Moonsilver Ingot', 'Smiths say it remembers the shape it was given.'),
        'glimmer_silk': ('Glimmer Silk', 'Woven by lantern moths.'),
        'glyph_gold': ('Glyph Gold', 'Gold that carries a carved rule in its grain.'),
        'rime_pearl': ('Rime Pearl', 'Grown inside rimehorn horns.'),
        'sandsteel_ingot': ('Sandsteel Ingot', 'Steel folded with desert glass.'),
        'lich_bone': ('Lich Bone', 'It is never quite still.'),
        'shogun_steel': ('Shogun Steel', 'Folded a thousand times in ghost-fire.'),
        'edict_shard': ('Edict Shard', 'A splinter of a rule. Raises an Edict\'s rank (Character screen, K).'),
        'star_fragment': ('Star Fragment', 'Still warm from its fall.'),
    }
    for k, (n, d) in MATS.items():
        put(f'item.afterworld.{k}', n)
        put(f'item.afterworld.{k}.lore', d)
    QUEST = {
        'star_chart_fragment': ('Star Chart Fragment', 'Part of Selene\'s lost chart. Three make the whole.'),
        'glyph_tablet': ('Glyph Tablet', 'Carved with a rule of the Gilded Warden\'s court. Five of them open its vault.'),
        'phylactery_shard': ('Phylactery Shard', 'A sliver of the Crimson Lich\'s vessel. It beats.'),
        'challenge_lantern': ('Challenge Lantern', 'Akane\'s token. Set it on the heart of the Lantern Dojo to wake the Ashen Shogun.'),
        'sun_dial_key': ('Sun-dial Key', 'Opens the buried obelisk at noon.'),
        'crypt_key': ('Crypt Key', 'Fits one door in the Bonewood crypts.'),
        'bounty_writ': ('Bounty Writ', 'A Warden\'s contract, signed and sealed.'),
        'bell_rope': ('Bell Rope', 'Rotted at one end. It hums when held.'),
    }
    for k, (n, d) in QUEST.items():
        put(f'item.afterworld.{k}', n)
        put(f'item.afterworld.{k}.lore', d)

    # ------------------------------------------------------------------ Aspects
    ASPECTS = {
        'requiem_toll': ('Requiem Toll', 'heart_of_the_bell', 'Heart of the Bell',
                         'A spectral bell falls where you look: everything under it is struck hard, stunned and sickened.',
                         'Taken from the Bell-Ringer of the Hollow Cathedral.'),
        'stopped_hour': ('The Stopped Hour', 'heart_of_seconds', 'Heart of Seconds',
                         'Time stops for everything around you but you: creatures freeze and arrows hang in the air for five seconds.',
                         'Taken from the King of Seconds in his vault.'),
        'footsteps_of_stone': ('Footsteps of Stone', 'heart_of_stone', 'Heart of Stone',
                               'Become a stone colossus for twelve seconds: huge, armoured and strong, and every step a quake.',
                               'Taken from the Pilgrim who walks where there are no roads.'),
        'hundred_lanterns': ('A Hundred Lanterns', 'heart_of_lanterns', 'Heart of Lanterns',
                             'Dash through up to eight foes in ghost-fire, cutting each, then return to where you stood.',
                             'Win it from the Ashen Shogun, who waits in the Lantern Dojo among the blossoms.'),
        'red_dominion': ('Red Dominion', 'heart_of_crimson', 'Heart of Crimson',
                         'Four crimson knights rise to fight for you, and every wound you deal mends you.',
                         'Break the Crimson Lich\'s three phylacteries, then face him in his necropolis.'),
        'white_winter': ('White Winter', 'heart_of_winter', 'Heart of Winter',
                         'A blizzard dome: everything inside slows, freezes and bleeds cold for ten seconds.',
                         'Find the Rimeback Colossus\'s den in the Whitewaste.'),
        'chains_of_the_dune': ('Chains of the Dune', 'heart_of_the_dune', 'Heart of the Dune',
                               'A sand vortex drags foes to its eye while stone fists fall on it.',
                               'Wake the Dune Sentinel under the Sun Temple in the Hourglass Dunes.'),
        'glyph_of_judgment': ('Glyph of Judgment', 'heart_of_judgment', 'Heart of Judgment',
                              'A golden dome turns arrows and spells back for eight seconds; then judgment strikes every foe in sight.',
                              'Set five Glyph Tablets into the Gilded Vault\'s gate and stand before its Warden.'),
    }
    for k, (n, heart, heart_name, desc, hint) in ASPECTS.items():
        put(f'aspect.afterworld.{k}', n)
        put(f'aspect.afterworld.{k}.desc', desc)
        put(f'aspect.afterworld.{k}.hint', hint)
        put(f'item.afterworld.{heart}', heart_name)
    put('aspect.afterworld.label', 'Aspect: %s')
    put('aspect.afterworld.use', 'Use to bind this Aspect. Fill its Resonance in battle, then unleash it (C).')
    put('aspect.afterworld.bound', 'Aspect bound')
    put('aspect.afterworld.red_knight', 'Crimson Knight')
    put('message.afterworld.aspect_known', 'You have already bound that Aspect.')
    put('message.afterworld.no_aspect', 'You have bound no Aspect yet. The great powers carry them.')
    put('message.afterworld.resonance_low', 'Resonance %s / 100. Fight well to fill it.')
    put('message.afterworld.aspect_equipped', 'Aspect: %s')
    put('message.afterworld.second_breath', 'The Ring of Second Breath holds you up.')
    put('cinematic.afterworld.unleashed', 'Aspect unleashed')
    put('cinematic.afterworld.unleashed_by', 'unleashed by %s')
    for i, t in enumerate(['It stirs.', 'It rises in fury.', 'Its last stand.', 'It changes.']):
        put(f'cinematic.afterworld.phase.{i}', t)
    put('subtitles.afterworld.combat.parry', 'Blade clangs')
    put('subtitles.afterworld.aspect.unleash', 'An Aspect is unleashed')
    put('dialogue.afterworld.hint', 'Space: continue · A: auto (%s) · L: log · Esc: leave')
    put('dialogue.afterworld.auto_on', 'on')
    put('dialogue.afterworld.auto_off', 'off')
    put('dialogue.afterworld.log', 'What has been said')
    put('hud.afterworld.parry', 'PARRY')
    put('hud.afterworld.dodge', 'DODGE')

    # ------------------------------------------------------------------ skills
    put('skill.afterworld.branch.blade', 'Blade')
    put('skill.afterworld.branch.wayfarer', 'Wayfarer')
    put('skill.afterworld.branch.arcanum', 'Arcanum')
    SKILLS = {
        'blade.edge': ('Keen Edge', 'Melee hits deal 8%% more damage.'),
        'blade.flow': ('Flowing Strikes', 'Each hit in a row on the same foe deals 4%% more, up to 20%%.'),
        'blade.riposte': ('Iron Wrist', 'Your parry window lasts longer, and a perfect parry cuts the attacker.'),
        'blade.execution': ('Execution', 'Hits on foes below a quarter of their health deal 40%% more.'),
        'wayfarer.endurance': ('Long Lungs', '+25 stamina.'),
        'wayfarer.featherfall': ('Featherfall', 'Falls hurt you half as much.'),
        'wayfarer.ghoststep': ('Ghost Step', 'Dodges cost 30%% less stamina and leave you quick for a second.'),
        'wayfarer.pathfinder': ('Pathfinder', 'Move 15%% faster when nothing hunts you, and waystones recover twice as fast.'),
        'arcanum.attune': ('Attunement', 'Resonance builds 15%% faster.'),
        'arcanum.recall': ('Quick Recall', 'Weapon Arts recover 15%% faster.'),
        'arcanum.overflow': ('Overflow', 'Unleashing an Aspect leaves a quarter of its Resonance.'),
        'arcanum.edictweaver': ('Edictweaver', 'Edicts recover 15%% faster.'),
        # The tree's root, its small nodes, its keystones and its bridges.
        'root': ('The Waking', 'Where every traveller begins. The tree grows from here.'),
        'blade.might1': ('Whetted Edge', 'Melee hits deal 3%% more damage.'),
        'blade.quick1': ('Quick Hands', 'Attack 5%% faster.'),
        'blade.might2': ('Honed Edge', 'Melee hits deal 3%% more damage.'),
        'blade.duelist': ("Duelist's Oath", 'Hits on a foe that has chosen you deal 15%% more.'),
        'blade.guard1': ('Mail Sense', '+1 armour.'),
        'blade.hale1': ('Hale', '+2 health (a heart).'),
        'blade.stance1': ('Rooted Stance', '10%% less knockback.'),
        'blade.bloodthirst': ('Bloodthirst', 'Killing blows struck by hand mend 2 health.'),
        'arcanum.res1': ('Resonant Mind', 'Resonance builds 5%% faster.'),
        'arcanum.res2': ('Deep Resonance', 'Resonance builds 5%% faster.'),
        'arcanum.art1': ('Ready Hands', 'Weapon Arts recover 5%% faster.'),
        'arcanum.edict1': ('Steady Voice', 'Edicts recover 5%% faster.'),
        'arcanum.echo': ('Echoing Will', 'Every Edict you speak gives your Aspect 5 Resonance.'),
        'wayfarer.swift1': ('Light Step', 'Move 3%% faster.'),
        'wayfarer.swift2': ('Long Stride', 'Move 3%% faster.'),
        'wayfarer.lungs1': ('Deep Breath', '+10 stamina.'),
        'wayfarer.lungs2': ('Mountain Lungs', '+10 stamina.'),
        'wayfarer.secondwind': ('Second Wind', 'When your stamina runs dry, half of it comes back at once (once in 30 seconds).'),
        'wayfarer.hardy1': ('Hardy', '+2 health (a heart).'),
        'wayfarer.luck1': ("Fortune's Friend", '+1 luck: better finds in chests.'),
        'wayfarer.scavenger': ('Scavenger', 'Foes you slay drop a quarter more Marks.'),
        'bridge.spellblade': ('Spellblade', 'Melee hits deal 3%% more damage. Joins Blade to Arcanum.'),
        'bridge.pilgrim': ("Pilgrim's Patience", 'Edicts recover 3%% faster. Joins Wayfarer to Arcanum.'),
    }
    for k, (n, d) in SKILLS.items():
        put(f'skill.afterworld.{k}', n)
        put(f'skill.afterworld.{k}.desc', d)
    put('skill.afterworld.learned', 'Learned: %s')
    put('skill.afterworld.branch.root', 'The Root')
    put('skill.afterworld.branch.bridge', 'Bridge')
    put('notice.afterworld.insight', '+%s Insight (%s to spend on the skill tree)')
    put('skilltree.afterworld.title', 'Skill Tree')
    put('skilltree.afterworld.insight', 'Insight: %s')
    put('skilltree.afterworld.learned_count', '%s of %s learned')
    put('skilltree.afterworld.hint', 'Drag to look around  ·  Scroll to zoom  ·  Click a glowing node to learn it  ·  R to centre')
    put('skilltree.afterworld.kind.root', 'Where the tree begins')
    put('skilltree.afterworld.kind.minor', 'Small node')
    put('skilltree.afterworld.kind.notable', 'Skill')
    put('skilltree.afterworld.kind.keystone', 'Keystone')
    put('skilltree.afterworld.learn', 'Click to learn: %s Insight')
    put('skilltree.afterworld.need_insight', 'Needs %s Insight')
    put('skilltree.afterworld.need_path', 'Learn a node next to it first')
    put('skilltree.afterworld.back', 'Character')

    # ------------------------------------------------------------------ the second bestiary
    BEASTS = {
        'dune_strider': ('Dune Strider', 'Long-legged herd beasts of the Hourglass, striped like the caravans that follow them.', 'Walks the dunes in herds.'),
        'snow_hare': ('Snow Hare', 'White as the drifts it hides in, until it moves.', 'Bolts across the snowfields.'),
        'thornback_boar': ('Thornback Boar', 'Brambles grow along its spine. Leave it be and it leaves you be; strike it and it charges.', 'Roots in the tangled woods.'),
        'highland_ram': ('Highland Ram', 'Its horns have settled a thousand arguments. Do not be the thousand-and-first.', 'Grazes the windworn highlands.'),
        'duskpine_lynx': ('Duskpine Lynx', 'A grey ghost of the pines. It pounces once, then decides whether you are worth a second.', 'Hunts quietly in the duskpines.'),
        'fen_toad': ('Fen Toad', 'Its tongue reaches further than you think. Mab brews its oil.', 'Croaks in the black water of the fen.'),
        'crystal_crab': ('Crystal Crab', 'Grows a little garden of crystal on its shell. Wren covets them.', 'Scuttles along the stony shores.'),
        'ember_salamander': ('Ember Salamander', 'Cool to look at, hot to touch. Its bite carries embers.', 'Basks in the ember shallows.'),
        'silverrun_otter': ('Silverrun Otter', 'Plays in the river, steals your fish, regrets nothing.', 'Swims the Silverrun.'),
        'kitsune': ('Kitsune', 'A fox of three tails that walks between one place and the next. It throws foxfire when cornered.', 'Glimpsed among the Petalfall blossoms.'),
        'sporeling': ('Sporeling', 'A walking mushroom, shy and glowing. It hops when happy.', 'Wanders the Sporeveil.'),
        'floe_seal': ('Floe Seal', 'Barks at boats, sleeps on ice, ignores everything else.', 'Lounges on the rimefloe.'),
        'blossom_crane': ('Blossom Crane', 'Wades in the pink shallows on legs like reeds.', 'Stands in Petalfall\'s streams.'),
        'cinder_beetle': ('Cinder Beetle', 'A beetle with a forge for a belly. It bursts into embers when it dies.', 'Crawls over the ashen moors.'),
        'sand_sifter': ('Sand Sifter', 'It waits beneath the sand. You will not see it until it decides you are close enough.', 'Hides under the dunes and canyons.'),
        'glyph_scarab': ('Glyph Scarab', 'Gold-backed beetles that guard the old glyphs in swarms.', 'Swarms near the desert ruins.'),
        'rust_golemite': ('Rust Golemite', 'A small cousin of the clockwork sentries, slow and patient, with fists like anvils.', 'Wanders the rustscar canyons.'),
        'ice_crawler': ('Ice Crawler', 'Many-legged and crystal-backed. It climbs walls and its bite freezes.', 'Lurks among the rimeglass spires.'),
        'rime_brute': ('Rime Brute', 'Kin of the Rimeback Colossus, smaller only by comparison. It throws ice like stones.', 'Roams the whitewaste.'),
        'drowned_revenant': ('Drowned Revenant', 'A sailor the sea gave back. It still carries its harpoon.', 'Walks the shores by night.'),
        'mire_shambler': ('Mire Shambler', 'Mud and roots in the shape of a man, breathing spores.', 'Rises from the fen at night.'),
        'vine_strangler': ('Vine Strangler', 'A plant that has learned to want. Its vines pull; its bulb bites.', 'Waits in the verdant tangle.'),
        'fungal_brute': ('Fungal Brute', 'A mushroom grown too big and too angry, breathing dizzying spores.', 'Stalks the Sporeveil at night.'),
        'rootling_sentinel': ('Rootling Sentinel', 'Old wood that stands up when you trespass, and drags you down when you run.', 'Guards the rootways and hollow woods.'),
        'gloomweaver': ('Gloomweaver', 'A spider the size of a cart, spinning in the gloom. Its webs slow you; its bite poisons.', 'Weaves in the Gloomwald.'),
        'prism_golem': ('Prism Golem', 'Living crystal in a golem\'s shape. Arrows and spells glance off it and fly back.', 'Stands among the shardfields.'),
        'dusk_hound': ('Dusk Hound', 'Hounds that run in packs when the light goes. When one finds you, they all do.', 'Hunts the vesper downs at night.'),
        'ash_wyrmling': ('Ash Wyrmling', 'A young dragon of ash and ember. It spits fire and dives.', 'Circles over the ashen moors.'),
        'canyon_vulture': ('Canyon Vulture', 'It waits. When you are hurt, it stops waiting.', 'Wheels over the canyons.'),
        'mossmere_owl': ('Mossmere Owl', 'Moss grows on its feathers. It watches everything, says little.', 'Perches in the mossy woods.'),
        'echo_bat': ('Echo Bat', 'Its cry is a weapon: it blinds and staggers.', 'Swarms in the echo hollows.'),
        'windrider_eagle': ('Windrider Eagle', 'The great eagle of the peaks, with feathers prized by every fletcher.', 'Soars over the high peaks.'),
        'mourning_shade': ('Mourning Shade', 'A grief that never ended. It drifts, weeps cold, and blinks from place to place.', 'Haunts the pale reach.'),
        'bone_gull': ('Bone Gull', 'A gull thin enough to show its bones. It screams anyway.', 'Wheels over the bone shore.'),
        'rime_moth': ('Rime Moth', 'Its wings are frost. Rime silk comes from its cocoons.', 'Flutters in the glimmerfrost.'),
        'abyssal_angler': ('Abyssal Angler', 'The light in the deep is a lure. The rest of the angler is teeth.', 'Lurks in the deepest water.'),
        'tidecaller_jelly': ('Tidecaller Jelly', 'A drifting lantern of the warm seas. Do not touch.', 'Drifts in the mirror sea.'),
        'tusked_narwhal': ('Tusked Narwhal', 'Unicorn of the cold seas. It charges anything that threatens its pod.', 'Swims the rime sea.'),
    }
    for k, (n, d, hint) in BEASTS.items():
        put(f'entity.afterworld.{k}', n)
        put(f'item.afterworld.{k}_spawn_egg', n + ' Spawn Egg')
        put(f'codex.afterworld.creature.{k}', n)
        put(f'codex.afterworld.creature.{k}.desc', d)
        put(f'codex.afterworld.creature.{k}.hint', hint)
    SPOILS = {
        'boar_tusk': ('Boar Tusk', 'Curved and heavy. Marta makes gauntlets of them.'),
        'ram_horn': ('Ram Horn', 'Ridged with years. Drums and gauntlets are made from them.'),
        'lynx_pelt': ('Lynx Pelt', 'Soft, grey and silent. The rangers\' leathers are made of it.'),
        'toad_oil': ('Toad Oil', 'The base of every oil and half of Mab\'s brews.'),
        'crab_shell': ('Crab Shell', 'Crystal grows on it. Glyphmail is backed with it.'),
        'kitsune_tail': ('Kitsune Tail', 'It is still warm. Foxfire flickers at its tip.'),
        'ember_chitin': ('Ember Chitin', 'A plate of beetle shell that never quite cools.'),
        'sifter_mandible': ('Sifter Mandible', 'Sharp enough to cut glass. Desert smiths prize them.'),
        'root_heartwood': ('Root Heartwood', 'The living core of a rootling. It hums near waystones.'),
        'dusk_fang': ('Dusk Fang', 'A hound\'s fang, faintly violet. Wayfarers carve knives from them.'),
        'wyrm_scale': ('Wyrm Scale', 'A wyrmling\'s scale, glowing at the edge.'),
        'windrider_feather': ('Windrider Feather', 'The great eagle\'s flight feather. Odo would give a lot for one.'),
        'pale_ectoplasm': ('Pale Ectoplasm', 'What remains of a shade. It is cold and remembers something.'),
        'angler_lure': ('Angler Lure', 'It still glows. Selene thinks it is a fallen star the sea kept.'),
        'narwhal_tusk': ('Narwhal Tusk', 'A spiral of ivory as long as a spear.'),
    }
    for k, (n, d) in SPOILS.items():
        put(f'item.afterworld.{k}', n)
        put(f'item.afterworld.{k}.lore', d)
    put('entity.afterworld.training_dummy', 'Training Dummy')
    put('creature.afterworld.elite', 'Elite %s')
    put('creature.afterworld.elite_slain', 'an elite %s')
    put('block.afterworld.clue', 'Clue')
    CLUES = {
        'wagon': ('A wagon wheel, snapped clean through. Not by a storm: the spokes were cut. Whoever stopped this caravan meant to.', 'Found the caravan\'s cut wheel.'),
        'manifest': ('The caravan\'s manifest. Spice, silk, water... and a last line in a hurried hand: "The obelisk. Hide."', 'Found the caravan\'s manifest.'),
        'tracks': ('Footprints, many, heading south and east into the dunes, toward the old obelisk. Something larger followed.', 'Read the tracks in the sand.'),
        'glyph': ('A glyph carved into the stone, still warm. The same mark is on every glyph tablet.', 'Found a warm glyph.'),
        'note': ('A note, half washed out: "...the key turns at noon..."', 'Found a weathered note.'),
    }
    for k, (long, short) in CLUES.items():
        put(f'clue.afterworld.{k}', long)
        put(f'clue.afterworld.{k}.short', short)
    put('message.afterworld.dummy_hit', 'Too slow! Roll (Left Alt while moving) or parry (Left Alt standing still) as it swings.')
    for k, n in {'owl': 'Owl hoots', 'eagle': 'Eagle cries', 'crane': 'Crane calls', 'seal': 'Seal barks', 'hound': 'Hound growls'}.items():
        put(f'subtitles.afterworld.mob.{k}', n)

    # ------------------------------------------------------------------ the Disciplines' signatures, rebuilt
    put('discipline.afterworld.vanguard.signature', 'Unbroken')
    put('discipline.afterworld.vanguard.signature.desc', 'Leap and slam the ground: everything near is thrown into the air and staggered (more with Renown). For five seconds after, you take half damage and every foe nearby wants only you. Last Word is now passive: once every three minutes, a killing blow leaves you standing.')
    put('discipline.afterworld.wayfarer.signature', 'Wayfarer\'s Gambit')
    put('discipline.afterworld.wayfarer.signature.desc', 'Blink from foe to foe (up to five within twelve blocks), striking each for 160%% of your weapon, then step back to where you began. Hold the key instead for Wrong Turn: mark this spot, and hold again within thirty seconds to return to it.')
    put('discipline.afterworld.arcanist.signature', 'Rule Collision')
    put('discipline.afterworld.arcanist.signature.desc', 'Fuse your first two Edicts into one cast at double power, with a fusion of their kinds (Overload, Bulwark, Comet, Accord or Revelation). Half of both cooldowns comes straight back.')
    put('discipline.afterworld.keeper.signature', 'Call the Pack')
    put('discipline.afterworld.keeper.signature.desc', 'Three spectral wolves run with you for thirty seconds. They draw foes onto themselves, and every bite mends you. Your summons last half again as long.')
    put('discipline.afterworld.keeper.wolf', 'Spectral Wolf')
    put('message.afterworld.last_word', 'Last Word: not yet.')
    put('message.afterworld.fail.gambit', 'No foe within reach for the Gambit.')
    put('codex.afterworld.discipline.vanguard.unbroken', 'Unbroken')
    put('codex.afterworld.discipline.vanguard.unbroken.desc', 'The Vanguard leaps, lands like a falling wall, and stands there daring the world to try again.')
    put('codex.afterworld.discipline.vanguard.unbroken.hint', 'A Vanguard\'s signature.')
    put('codex.afterworld.discipline.keeper.pack', 'Call the Pack')
    put('codex.afterworld.discipline.keeper.pack.desc', 'Spectral wolves answer a Keeper\'s call. They bite for their Keeper\'s sake and mend them with each wound.')
    put('codex.afterworld.discipline.keeper.pack.hint', 'A Keeper\'s signature.')
    for k, n in {'overload': 'Overload: the Edicts burst together.', 'bulwark': 'Bulwark: you stand behind the rule.',
                 'comet': 'Comet: you move like a rule unbound.', 'accord': 'Accord: hostility falters around you.',
                 'revelation': 'Revelation: every foe is shown to you.'}.items():
        put(f'edict.afterworld.fusion.{k}', n)

    # ------------------------------------------------------------------ the Character screen
    put('screen.afterworld.character', 'Character')
    for k, n in {'aspects': 'Aspects', 'skills': 'Skills', 'edicts': 'Edicts', 'relics': 'Relics & Gear'}.items():
        put(f'character.afterworld.tab.{k}', n)
    put('character.afterworld.renown', 'Renown %s')
    put('character.afterworld.insight', 'Insight: %s')
    put('character.afterworld.marks', 'Marks: %s')
    put('character.afterworld.unknown', 'Unknown')
    put('character.afterworld.aspects_intro', 'Aspects are the hearts of the old powers. Defeat a great power to take its heart, bind it, fill its Resonance by fighting well, then unleash it (C).')
    put('character.afterworld.equip', 'Equip')
    put('character.afterworld.equipped', 'Equipped')
    put('character.afterworld.equip_cost', 'Changing Aspects scatters half your Resonance.')
    put('character.afterworld.learned', 'Learned')
    put('character.afterworld.cost_insight', 'Costs %s Insight')
    put('character.afterworld.needs_previous', 'Learn the skill above it first.')
    put('character.afterworld.edict_intro', 'Raise an Edict with Edict Shards and Marks. You carry %s shards.')
    put('character.afterworld.rank_cost', '%s shards · %s Marks')
    put('character.afterworld.raise', 'Raise')
    put('character.afterworld.rank_max', 'Mastered')
    put('character.afterworld.rank_effect', 'Rank %s: each rank makes it recover 15%% faster and bend its rule 30%% harder.')
    put('character.afterworld.no_edicts', 'You have learned no Edicts yet. Anomalies and shrines hold them.')
    put('character.afterworld.need_shards', 'You need %s Edict Shards.')
    put('character.afterworld.ranked', '%s is now rank %s.')
    put('character.afterworld.relics', 'Relics')
    put('character.afterworld.relics_how', 'Use a relic to wear it. Click a worn relic to take it off.')
    put('character.afterworld.relic_remove', 'Click to take it off.')
    put('character.afterworld.gifts', 'Gifts')
    put('character.afterworld.gift_unknown', 'Not yet learned.')
    put('character.afterworld.armour', 'Armour sets')
    put('character.afterworld.no_set', 'Wear all four pieces of a set to wake its bonus.')
    places(put)
    ladder(put)
    trials(put)
    wardens(put)
    creature_sounds(put)
    capstones(put)
    board(put)


def wardens(put):
    """The five Wardens of the Lands: names, epithets, acts, waking, and their Codex pages."""
    W = {
        'ashen_shogun': ('The Ashen Shogun', 'Every lantern is a life he took.', 'The lanterns stir', 'A hundred lanterns',
                         'The Ashen Shogun opens his eyes. The lanterns of the dojo flare blue.',
                         'Once the gentlest of men, he swore to guard the blossom country forever, and the world took him at his word. He cuts, dashes in lines of ghostfire and throws lanterns that fall where you stand. Parry his iaido.',
                         'Akane keeps his challenge lantern.'),
        'crimson_lich': ('The Crimson Lich', 'He hid his heart in three places.', 'The obelisks drink', 'Rise, all of you',
                         'The Crimson Lich turns his skull toward you.',
                         'A theatrical old king who hid his life in three phylacteries. Blood beams, a red nova, the dead raised to fight for him, a drain that feeds on you. Break all three phylacteries, or he rises again.',
                         'Sister Ines knows where he hid his heart.'),
        'rimeback_colossus': ('The Rimeback Colossus', 'The snow, standing up.', 'The cold deepens', 'White winter',
                              'The snow stands up.',
                              'A horned giant of the Whitewaste with ice growing from its back. It swipes, slams the ground into rolling waves (jump them), throws boulders of ice and breathes frost. At the end, it brings a blizzard.',
                              'Torvald has seen tracks as wide as a cart.'),
        'dune_sentinel': ('The Dune Sentinel', 'A thousand years asleep.', 'The sand turns', 'Chains of the dune',
                          'Sand pours from the Sentinel\'s shoulders as it wakes. WHO WAKES THE SUN?',
                          'A sand golem asleep under the Sun Temple, its fists floating on chains. They fall where you stand; its chains drag you in; then the sand itself turns round it.',
                          'Something sleeps under the Sun Temple.'),
        'gilded_warden': ('The Gilded Warden', 'It judges.', 'The shell remembers', 'Judgment',
                          'The gate swings wide. Gold light runs along the Warden\'s shell as it rises to judge you.',
                          'The last guardian, a turtle of stone whose shell is written in gold. Beams of glyph-light, a rolling shell, a golden dome that turns arrows back, and at the end, judgment on the whole vault.',
                          'Five glyph tablets open the Gilded Vault.'),
    }
    for k, (name, epithet, p2, p3, wakes, desc, hint) in W.items():
        put(f'entity.afterworld.{k}', name)
        put(f'boss.afterworld.{k}.epithet', epithet)
        put(f'boss.afterworld.{k}.phase2', p2)
        put(f'boss.afterworld.{k}.phase3', p3)
        put(f'boss.afterworld.{k}.wakes', wakes)
        put(f'codex.afterworld.boss.{k}', name)
        put(f'codex.afterworld.boss.{k}.desc', desc)
        put(f'codex.afterworld.boss.{k}.hint', hint)
    put('boss.afterworld.crimson_lich.revive', 'The Lich rises again. His phylacteries still bind him to the world.')
    put('boss.afterworld.felled', 'Felled %s')
    put('seal.afterworld.offer_more', 'The seal wants %s of %s.')


def trials(put):
    """The minigames: titles, how to play, counters, verdicts."""
    T = {
        'smithing': ('The Anvil', 'Space or click: strike while the marker sits in the gold.'),
        'cooking': ('The Stew Pot', 'Hold Space to stir and keep the heat in the green. Press 1-4 to add what the broth asks for.'),
        'brewing': ('The Cauldron', 'Space: drop the next ingredient in when the brew turns its colour. A / D: stir with the bubbles.'),
        'stars': ('The Star Dials', 'A / D to turn a star, W / S or 1-5 to choose one. Bring each home to its mark.'),
        'melody': ('The Apprentice\'s Song', 'Listen, then ring the bells back in the same order with 1-5.'),
        'race': ('The Glide Race', 'Hold Jump to glide; steer with your view. Through every ring before the sand runs out.'),
        'waterwalk': ('The Walk on Water', 'Sprint across the water to the marker and back. Don\'t get wet.'),
        'tracking': ('The Hunt', 'Follow the fresh tracks. Lose them, and you lose the alpha.'),
        'archery': ('The Archery Contest', 'Shoot each target before it falls. Six of eight.'),
        'duel': ('The Duel of Lanterns', 'Only a perfect parry counts. Parry the instant her blade falls.'),
        'sighting': ('A Sighting', 'Hold still. Let the needle settle.'),
    }
    for k, (title, hint) in T.items():
        put(f'trial.afterworld.{k}', title)
        put(f'trial.afterworld.{k}.hint', hint)
    put('trial.afterworld.quit', 'Esc to give up')
    put('trial.afterworld.legend', 'Passed %s')
    put('trial.afterworld.banner.won', '%s: passed!')
    put('trial.afterworld.banner.lost', '%s: not this time')
    lose = {
        'score': 'Not quite. Talk to them again to try once more.',
        'time': 'The sand ran out. Talk to them again to try once more.',
        'fell': 'The trial is over.',
        'wet': 'Splash. You\'re soaked through. Mab will let you try again.',
        'lost': 'The snow filled the tracks. You\'ve lost the trail. Torvald can show you again.',
        'escaped': 'The alpha got away. Torvald can show you the trail again.',
        'missed': 'Not enough hits. Torvald will set the targets up again.',
        'forfeit': 'You walked away from the duel. Akane will wait.',
        'struck': 'Her blade found you three times. Akane bows: again, when you are ready.',
        'wandered': 'You moved away from the waystone. Raise the sextant again beside it.',
        'quit': 'You set the trial aside.',
    }
    for k, v in lose.items():
        put(f'trial.afterworld.lose.{k}', v)
    put('trial.afterworld.smithing.not_ready', 'Marta squints at your weapon. "It isn\'t humming yet. Use it more."')
    put('trial.afterworld.smithing.perfect', 'Perfect strike!')
    put('trial.afterworld.smithing.good', 'Good strike.')
    put('trial.afterworld.smithing.miss', 'Clang. Off the gold.')
    put('trial.afterworld.smithing.sings', 'The metal sings.')
    put('trial.afterworld.smithing.cooled', 'It cooled unevenly...')
    put('trial.afterworld.smithing.score', 'Score: %s (6 to pass)')
    put('trial.afterworld.smithing.woke', '%s wakes, heavier in the hand and keener in the edge.')
    put('trial.afterworld.cooking.missing', 'Bram counts on his fingers: two beef, three carrots, two potatoes, one ember core.')
    put('trial.afterworld.cooking.scorched', 'Scorched! Stir!')
    put('trial.afterworld.cooking.late', 'Too slow. The moment passed.')
    put('trial.afterworld.cooking.tasty', 'It smells like somebody\'s grandmother.')
    put('trial.afterworld.cooking.bland', 'Edible. Mostly.')
    put('trial.afterworld.cooking.right', 'Just right.')
    put('trial.afterworld.cooking.wrong', 'Not that one!')
    put('trial.afterworld.cooking.stir', 'Stirring (hold Space)')
    put('trial.afterworld.brewing.unstirred', 'The bubbles settled on their own.')
    put('trial.afterworld.brewing.good', 'It glows a satisfied green.')
    put('trial.afterworld.brewing.murky', 'Murky. Mab will not be impressed.')
    put('trial.afterworld.brewing.drop_good', 'In it goes. The brew drinks it up.')
    put('trial.afterworld.brewing.drop_bad', 'Wrong colour. It sputters.')
    put('trial.afterworld.brewing.stir_good', 'With the bubbles. Good.')
    put('trial.afterworld.brewing.stir_bad', 'Against the bubbles!')
    put('trial.afterworld.brewing.clockwise', 'Stir clockwise (D)')
    put('trial.afterworld.brewing.counter', 'Stir counter-clockwise (A)')
    put('trial.afterworld.stars.home', 'The Lost Moth is home.')
    put('trial.afterworld.melody.listen', 'Listen...')
    put('trial.afterworld.melody.your_turn', 'Your turn.')
    put('trial.afterworld.melody.verse', 'Yes. She hums the next verse.')
    put('trial.afterworld.melody.again', 'Not quite. She hums it again.')
    put('trial.afterworld.melody.finished', 'The song is finished. She can rest.')
    put('trial.afterworld.race.count', 'Rings %s / %s')
    put('trial.afterworld.waterwalk.out', 'Out to the marker')
    put('trial.afterworld.waterwalk.back', 'Now back to Mab!')
    put('trial.afterworld.tracking.count', 'Tracks followed: %s / %s')
    put('trial.afterworld.tracking.alpha_hint', 'The alpha! Bring it down.')
    put('trial.afterworld.tracking.alpha', 'Rimehorn Alpha')
    put('trial.afterworld.tracking.found', 'The tracks end. Something huge lifts its head.')
    put('trial.afterworld.archery.count', 'Hits %s   Target %s / %s   (need %s)')
    put('trial.afterworld.duel.count', 'Parries %s / %s   Wounds %s / %s')
    put('trial.afterworld.duel.warn', 'PARRY!')
    put('trial.afterworld.duel.struck', 'Struck! %s more and the duel is lost.')
    put('trial.afterworld.duel.parried', 'Turned aside! %s / %s')
    put('trial.afterworld.sighting.hold', 'Hold still...')
    put('trial.afterworld.sighting.count', 'Sighting taken. %s of 3.')
    put('trial.afterworld.sighting.done', 'You\'ve already taken a sighting at this waystone.')
    put('message.afterworld.minigame_missing', 'That trial isn\'t ready yet (%s).')


def places(put):
    """Landmarks and sites: title cards, Codex entries, notes found there and seal riddles."""
    import os
    import sys
    sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'wonders'))
    import site_text as T
    for group in (T.LANDMARKS, T.SITES):
        for k, (title, sub, desc, hint) in group.items():
            put(f'wonder.afterworld.{k}', title)
            put(f'wonder.afterworld.{k}.sub', sub)
            put(f'codex.afterworld.wonder.{k}', title)
            put(f'codex.afterworld.wonder.{k}.desc', desc)
            put(f'codex.afterworld.wonder.{k}.hint', hint)
    put('journal.afterworld.codex.wonders', 'Wonders')
    put('journal.afterworld.codex.landmarks', 'Landmarks')
    put('journal.afterworld.codex.sites', 'Places of the Lands')
    for k, (long, short) in T.LORE.items():
        put(f'lore.afterworld.{k}', long)
        put(f'lore.afterworld.{k}.short', short)
    for k, text in T.SEAL_HINTS.items():
        put(f'seal.afterworld.hint.{k}', text)
    put('block.afterworld.seal', 'Seal Stone')
    put('block.afterworld.phylactery', 'Phylactery')
    put('seal.afterworld.hint', 'A seal stone, carved shut. Something nearby must be set right first.')
    put('seal.afterworld.progress', ' (%s of %s)')
    put('seal.afterworld.solved', 'The seal has turned. Whatever it kept is open.')
    put('seal.afterworld.opened', 'Stone grinds on stone. The seal turns, and something opens.')
    put('seal.afterworld.legend', 'Solved a seal\'s riddle')
    put('phylactery.afterworld.broken', 'The phylactery shatters with a sound like a held breath let go. (%s of 3)')
    put('phylactery.afterworld.legend', 'Shattered one of the Crimson Lich\'s phylacteries')
    put('codex.afterworld.boss.lich.phylactery', 'The Lich\'s Phylacteries')
    put('codex.afterworld.boss.lich.phylactery.desc', 'The Crimson Lich hid his life in three phylacteries: a barrow on the Vesper Downs, an ossuary in the Pale Reach, and his own necropolis. Break all three, or he will not stay down.')
    put('codex.afterworld.boss.lich.phylactery.hint', 'Sister Ines knows where the Lich hid his heart.')
    put('codex.afterworld.people.leyla', 'Leyla of the Sands')
    put('codex.afterworld.people.leyla.desc', 'Rashid\'s elder sister, master of the caravan that went silent. Hid eight drivers under the Buried Obelisk, and counted the spice.')
    put('codex.afterworld.people.leyla.hint', 'Someone is hiding in the dunes.')
    put('codex.afterworld.people.akane', 'Akane')
    put('codex.afterworld.people.akane.desc', 'The last disciple of the Lantern Dojo. Her master became the Ashen Shogun; she keeps his hundred lanterns, and his challenge.')
    put('codex.afterworld.people.akane.hint', 'Someone keeps the lanterns lit in the blossom country.')


def ladder(put):
    """The Warden's Ladder: its stones, floors, waves, gate window and panel."""
    put('block.afterworld.ladder_stone', 'Ladder Stone')
    put('ladder.afterworld.floor', 'Floor %s')
    put('ladder.afterworld.landing', 'A landing: rest, trade, or go home')
    tiers = {'overgrown': 'The Overgrown Rungs', 'crimson': 'The Crimson Rungs', 'rime': 'The Rime Rungs', 'sun': 'The Sun Rungs',
             'gilded': 'The Gilded Rungs'}
    for k, v in tiers.items():
        put(f'ladder.afterworld.tier.{k}', v)
    for k, v in {'ashen_shogun': 'The Ashen Shogun', 'crimson_lich': 'The Crimson Lich', 'rimeback_colossus': 'The Rimeback Colossus',
                 'dune_sentinel': 'The Dune Sentinel', 'gilded_warden': 'The Gilded Warden'}.items():
        put(f'ladder.afterworld.echo.{k}', f'An echo of {v} bars the way')
    put('ladder.afterworld.echo_name', 'Echo of %s')
    put('ladder.afterworld.wave', 'Wave %s of %s')
    put('ladder.afterworld.wave_done', 'Wave %s of %s beaten. Catch your breath.')
    put('ladder.afterworld.floor_won', 'Floor %s is won. The rung is lit.')
    put('ladder.afterworld.echo_down', 'The echo fades. Floor %s is yours, and the way home is open.')
    put('ladder.afterworld.rung_cold', 'The rung is cold. Win the floor first.')
    put('ladder.afterworld.caught', 'The Ladder catches you and sets you back on the floor.')
    put('ladder.afterworld.thrown', 'Thrown from the Ladder')
    put('ladder.afterworld.thrown.sub', 'You reached floor %s. The Ladder let you go; it keeps no one.')
    put('ladder.afterworld.renown', 'Floor %s of the Warden\'s Ladder')
    put('ladder.afterworld.legend_echo', 'Bested the echo of %s on floor %s of the Warden\'s Ladder')
    put('ladder.afterworld.legend_summit', 'Climbed all fifty floors of the Warden\'s Ladder')
    put('ladder.afterworld.summit', 'The Ladder Is Climbed')
    put('ladder.afterworld.summit.sub', 'Fifty floors. The Edict-writers would have made you a Warden.')
    # The gate's window.
    put('ladder.afterworld.screen.title', 'The Warden\'s Ladder')
    put('ladder.afterworld.screen.subtitle', 'Fifty floors where the Wardens were proven')
    put('ladder.afterworld.screen.best', 'Highest floor: %s of %s')
    put('ladder.afterworld.screen.best_none', 'You have not climbed it yet.')
    put('ladder.afterworld.screen.begin', 'Begin at Floor 1')
    put('ladder.afterworld.screen.resume', 'Resume at Floor %s')
    put('ladder.afterworld.screen.party', 'Climbing with %s companions')
    put('ladder.afterworld.screen.party_one', 'Climbing with 1 companion')
    put('ladder.afterworld.screen.alone', 'Climbing alone')
    put('ladder.afterworld.screen.rule.waves', 'Every floor sends waves of the land\'s creatures, stronger the higher you go.')
    put('ladder.afterworld.screen.rule.landings', 'Every fifth floor is a landing: rest, Rook\'s wares, and a way home.')
    put('ladder.afterworld.screen.rule.echoes', 'Every tenth floor, an echo of one of the five Wardens holds the way.')
    put('ladder.afterworld.screen.rule.fall', 'Fall in the Ladder and it throws you out at its gate, alive.')
    put('ladder.afterworld.screen.tiers', 'Floors 1-10: %s · 11-20: %s · 21-30: %s · 31-40: %s · 41-50: %s')
    for k, v in {'overgrown': 'Overgrown', 'crimson': 'Crimson', 'rime': 'Rime', 'sun': 'Sun', 'gilded': 'Gilded'}.items():
        put(f'ladder.afterworld.tier_short.{k}', v)
    put('ladder.afterworld.screen.card.best', 'Highest floor')
    put('ladder.afterworld.screen.card.landing', 'Next landing')
    put('ladder.afterworld.screen.card.echo', 'Next echo')
    put('ladder.afterworld.screen.gate_note', 'Everyone standing at the gate climbs together.')
    # The panel at the top of the screen.
    put('ladder.afterworld.hud.title', 'Warden\'s Ladder')
    put('ladder.afterworld.hud.floor', 'Floor %s')
    put('ladder.afterworld.hud.wave', 'Wave %s / %s')
    put('ladder.afterworld.hud.foes', '%s left')
    put('ladder.afterworld.hud.arriving', 'Steel yourself...')
    put('ladder.afterworld.hud.breather', 'Next wave coming...')
    put('ladder.afterworld.hud.cleared', 'Floor won: step onto the lit rung')
    put('ladder.afterworld.hud.resting', 'A landing: rest and trade')
    put('ladder.afterworld.hud.echo', 'The echo')
    put('seal.afterworld.ring.wakes', 'The ring wakes! Hold it through %s waves.')
    put('seal.afterworld.ring.wave', 'Wave %s of %s')
    put('seal.afterworld.ring.underway', 'Wave %s of %s: %s left')


def creature_sounds(put):
    """Subtitles for the creature voices remixed by tools/gen_sfx.py."""
    V = {
        'whale': ('Sky whale calls', 'Sky whale groans', 'Sky whale dies'),
        'wyrmling': ('Wyrmling roars', 'Wyrmling hurts', 'Wyrmling dies'),
        'brute': ('Brute grunts', 'Brute hurts', 'Brute dies'),
        'shambler': ('Shambler gurgles', 'Shambler hurts', 'Shambler dies'),
        'weaver': ('Gloomweaver hisses', 'Gloomweaver hurts', 'Gloomweaver dies'),
        'shade': ('Shade wails', 'Shade hurts', 'Shade fades'),
        'hound': ('Hound growls', 'Hound yelps', 'Hound howls its last'),
        'angler': ('Angler rumbles', 'Angler hurts', 'Angler dies'),
        'fungal': ('Fungal brute coughs', 'Fungal brute hurts', 'Fungal brute dies'),
        'chitter': ('Insect chitters', 'Insect hurts', 'Insect dies'),
        'moth': ('Moth flutters', 'Moth hurts', 'Moth dies'),
        'sprite': ('Sprite chimes', 'Sprite hurts', 'Sprite fades'),
        'stalker': ('Something breathes nearby', 'Stalker hurts', 'Stalker dies'),
        'lurker': ('Lurker croaks', 'Lurker hurts', 'Lurker dies'),
        'husk': ('Husk rasps', 'Husk hurts', 'Husk crumbles'),
        'sporeling': ('Sporeling squeaks', 'Sporeling hurts', 'Sporeling bursts'),
        'treant': ('Wood creaks', 'Wood groans', 'Wood splinters'),
        'wight': ('Wight keens', 'Wight cracks', 'Wight shatters'),
    }
    for k, (ambient, hurt, death) in V.items():
        put(f'subtitles.afterworld.creature.{k}.ambient', ambient)
        put(f'subtitles.afterworld.creature.{k}.hurt', hurt)
        put(f'subtitles.afterworld.creature.{k}.death', death)


def capstones(put):
    """What each Edict gains at Rank III (shown with its rank on the Character screen)."""
    C = {
        'return': 'Rank III: it leaps between up to six foes, then lands home with a shockwave.',
        'echoes': 'Rank III: the afterimage stays ten seconds, strikes at 75% and repeats your next two Edicts.',
        'borrowed_time': 'Rank III: the moment you leave explodes a second later, and every cooldown comes back.',
        'weightlessness': 'Rank III: the rift drags foes into its eye, then slams them down for double fall damage.',
        'hunger': 'Rank III: up to five echoes. They strike at half your strength and burst when they fade.',
        'hospitality': 'Rank III: your guests fight for you for twenty seconds. Only striking a guest breaks faith.',
        'trespass': 'Rank III: step through floors, ceilings and open air, leaving a rift that cuts whatever follows.',
        'stillness': 'Rank III: creatures freeze for three seconds. What you deal them lands at the end, half again as hard.',
        'witness': 'Rank III: witnessed foes take 25% more harm for 15 seconds, and chests and ores within 48 blocks shine.',
        'forgotten': 'Rank III: your first strike from hiding deals triple damage and keeps you hidden a second longer.',
    }
    for k, v in C.items():
        put(f'edict.afterworld.{k}.capstone', v)
    put('message.afterworld.witness_shine', '%s hidden things shine through the stone.')


def board(put):
    """The Warden's Board and its contracts."""
    put('block.afterworld.wardens_board', "Warden's Board")
    put('board.afterworld.title', "The Warden's Board")
    put('board.afterworld.empty', 'Nothing is pinned up today. Come back tomorrow.')
    for k, v in {'bounty': 'Bounty', 'delivery': 'Delivery', 'survey': 'Survey', 'gathering': 'Gathering', 'escort': 'Escort'}.items():
        put(f'board.afterworld.kind.{k}', v)
    put('board.afterworld.head.delivery', '%s × %s for %s')
    put('board.afterworld.head.gathering', '%s × %s')
    put('board.afterworld.head.survey', 'Three survey points')
    put('board.afterworld.head.escort', 'A caravan in trouble')
    put('board.afterworld.desc.bounty', 'Last seen %s, %s. It does not hunt alone.')
    put('board.afterworld.desc.delivery', '%s waits %s, %s.')
    put('board.afterworld.desc.survey', 'Three sightings for the Guild. The first: %s, %s.')
    put('board.afterworld.desc.gathering', 'Bring them to any board.')
    put('board.afterworld.desc.escort', 'Raiders have a caravan pinned down on a road nearby. See it through.')
    put('board.afterworld.pay', '%s Marks · %s Renown')
    put('board.afterworld.take', 'Take it')
    put('board.afterworld.full', 'Hands full')
    put('board.afterworld.taken', 'TAKEN')
    put('board.afterworld.carried', 'Carried: %s of %s')
    put('board.afterworld.none_carried', 'Take a notice from the board. You can carry three at once.')
    put('board.afterworld.hand_in', 'Hand in')
    put('board.afterworld.abandon', 'Give up')
    put('board.afterworld.status.go', 'Go %s.')
    put('board.afterworld.status.bounty_found', 'Found. Bring it down.')
    put('board.afterworld.status.survey', 'Point %s of %s. Next: %s.')
    put('board.afterworld.status.gathering', '%s of %s gathered.')
    put('board.afterworld.status.delivery_ready', 'Carried. Deliver it %s.')
    put('board.afterworld.status.delivery_need', 'Carrying %s of %s. Buy them where they are cheap.')
    put('board.afterworld.track.bounty', 'Bounty: %s')
    put('board.afterworld.track.delivery', 'Delivery to %s')
    put('board.afterworld.track.survey', 'Survey for the Guild')
    put('board.afterworld.track.gathering', 'Gather %s')
    put('board.afterworld.track.escort', 'Escort the caravan')
    put('message.afterworld.contract_taken', 'Contract taken: %s. It is in your tracker.')
    put('message.afterworld.contract_full', 'You can carry %s contracts at once.')
    put('message.afterworld.contract_short', 'You do not have enough yet.')
    put('message.afterworld.contract_abandoned', 'Contract given up.')
    put('message.afterworld.contract_none_here', 'No caravan needs you on these roads right now.')
    put('message.afterworld.contract_done', '%s fulfilled. You are paid %s Marks.')
    put('message.afterworld.contract_failed', '%s failed.')
    put('message.afterworld.bounty_found', '%s is here, and not alone.')
    put('message.afterworld.survey_point', 'Survey point %s of %s marked.')

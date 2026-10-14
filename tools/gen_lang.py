#!/usr/bin/env python3
"""Writes assets/afterworld/lang/en_us.json. All player-facing English lives here."""
import json, os
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'afterworld', 'lang', 'en_us.json')
L = {}

def put(k, v):
    L[k] = v

EDICTS = {
    'return': ("Return", "A marked object returns to its owner.",
               "Hurl your held weapon. It pierces up to four foes and flies back to the slot it left. With nothing to throw, it recalls your dropped items and fired arrows instead.",
               "Weapons and tools only. Blocked by walls; while in flight, the weapon is not in your hand.",
               "Somewhere, whatever you throw comes back to you."),
    'echoes': ("Echoes", "A recent action repeats as a spectral afterimage.",
               "For six seconds, every strike you land is repeated a second later by your afterimage at half strength. The next Edict you cast in that window is echoed too.",
               "Echoes are weaker and delayed; a target that moves may not be there for them.",
               "There are places where every sound happens twice."),
    'borrowed_time': ("Borrowed Time", "You may return to a recent version of yourself.",
               "Step back to where you were about five seconds ago, with the health, hunger and burns you had then. Edicts you spent in those seconds come back to you.",
               "A short window, within one dimension. Items, the world and other people do not rewind.",
               "The King of Seconds keeps it close to his chest."),
    'weightlessness': ("Weightlessness", "Gravity forgets a small place for a while.",
               "Open a gravity rift where you look. Creatures inside drift helplessly upward, arrows fly straight and you fall softly. When it closes, everything comes down.",
               "Bounded radius and duration. Heavy creatures resist being lifted.",
               "Fields where stones float as if they forgot which way is down."),
    'hunger': ("Hunger", "Defeated foes leave a hunting echo.",
               "For twenty seconds, foes you defeat rise again as translucent echoes that hunt for you, up to three at a time.",
               "Echoes fade quickly, and an unbound echo may turn on the nearest gentle creature instead.",
               "Barrows where the bones are always fresh."),
    'hospitality': ("Hospitality", "An honoured bargain forbids hostility.",
               "Offer a bargain to the creature you look at, or to every hostile creature close by. For forty-five seconds they will not treat you as a threat.",
               "Terms: you may not strike anything while it holds. Break it and they are enraged, and you are marked Oathbreaker.",
               "A table is set for guests who never quite arrive."),
    'trespass': ("Trespass", "A wall is only a suggestion.",
               "Step through up to five blocks of solid wall in the direction you face, if there is room on the other side.",
               "Warded stone, bedrock and machinery cannot be crossed.",
               "The Pilgrim walks where there are no roads."),
    'stillness': ("Stillness", "Time thickens around you.",
               "For four seconds, projectiles near you hang in the air and creatures move as if through syrup. Your own arrows keep their speed for when time resumes.",
               "Short and bounded. Things that were thrown at you simply fall.",
               "A stream that stopped moving last spring, and is still wet."),
    'witness': ("Witness", "Nothing near you may go unseen.",
               "Every creature within thirty-two blocks glows, chests and spawners pulse with light, and your nearest lead flares in its direction.",
               "A glimpse, not a map: ten seconds of sight.",
               "Clearings where you feel watched by something taking notes."),
    'forgotten': ("Forgotten", "Creatures forget you exist.",
               "Every creature within sixteen blocks loses track of you and cannot target you for eight seconds.",
               "Ends the moment you strike.",
               "Places people visit and cannot remember why."),
}
for k, (name, rule, desc, limit, hint) in EDICTS.items():
    put(f'edict.afterworld.{k}', name)
    put(f'edict.afterworld.{k}.rule', rule)
    put(f'edict.afterworld.{k}.desc', desc)
    put(f'edict.afterworld.{k}.limit', limit)
    put(f'edict.afterworld.{k}.hint', hint)
    put(f'item.afterworld.edict_fragment_{k}', f'Edict Fragment: {name}')
    put(f'codex.afterworld.edict.{k}', name)
    put(f'codex.afterworld.edict.{k}.desc', f'{rule} {desc} Limit: {limit}')
    put(f'codex.afterworld.edict.{k}.hint', hint)
for kind, name in {'offensive': 'Offensive Edict', 'defensive': 'Defensive Edict', 'traversal': 'Traversal Edict', 'social': 'Social Edict', 'exploratory': 'Exploratory Edict'}.items():
    put(f'edict.afterworld.kind.{kind}', name)
put('item.afterworld.edict_fragment.hint', 'Use to bind this rule to yourself.')

DISC = {
    'vanguard': ("Vanguard", "Close-range control, defence and posture. Built to hold a line and punish mistakes.", "Last Word",
                 "Only near defeat (below 40% health): a shockwave that hits harder the more you have lost, followed by a moment of resistance.",
                 "+2 hearts. Raising a shield just before a blow lands staggers the attacker."),
    'wayfarer': ("Wayfarer", "Mobility, precision and exploration. Quick on the road and hard to pin down.", "Wrong Turn",
                 "Press once to mark where you stand. Press again within thirty seconds to return to the mark.",
                 "+10% speed and four extra blocks of safe falling."),
    'arcanist': ("Arcanist", "Edict combinations and tactical control. Carries more rules, and turns them faster.", "Rule Collision",
                 "Cast the Edicts in your first two slots at once, both at greater strength.",
                 "A fourth Edict slot, and Edict cooldowns 25% shorter."),
    'keeper': ("Keeper", "Contracts, summons and bargains. Makes friends of things that should not have friends.", "Unpaid Favour",
               "Summon two spectral hounds for thirty seconds. Afterwards you owe the Favour six hostile kills within ten minutes, or it takes experience as interest.",
               "Echoes and pacts last 50% longer, and bonds allow a day of grace."),
}
put('discipline.afterworld.none', 'No Discipline')
put('discipline.afterworld.none.signature', 'None')
for k, (name, style, sig, sigdesc, passive) in DISC.items():
    put(f'discipline.afterworld.{k}', name)
    put(f'discipline.afterworld.{k}.style', style)
    put(f'discipline.afterworld.{k}.signature', sig)
    put(f'discipline.afterworld.{k}.signature.desc', sigdesc)
    put(f'discipline.afterworld.{k}.passive', 'Passive: ' + passive)
    put(f'codex.afterworld.discipline.{k}', f'The {name}')
    put(f'codex.afterworld.discipline.{k}.desc', f'{style} Signature, {sig}: {sigdesc} Passive: {passive}')
    put(f'codex.afterworld.discipline.{k}.hint', 'Choose a Discipline in the Expedition Journal.')

FACTIONS = {'crown': "The Crown", 'guild': "Cartographers' Guild", 'court': "Morrow Court", 'bureau': "Bureau of Impossible Things"}
for k, v in FACTIONS.items():
    put(f'faction.afterworld.{k}', v)
for i, t in enumerate(['Hostile', 'Distrusted', 'Unknown', 'Acknowledged', 'Trusted', 'Honoured']):
    put(f'faction.afterworld.tier.{i}', t)
put('journal.afterworld.contract_tip.crown', 'The Crown pays for marked beasts and safe roads.')
put('journal.afterworld.contract_tip.guild', 'The Guild pays for places charted before anyone else finds them.')
put('journal.afterworld.contract_tip.court', 'The Court asks that the forgotten be remembered.')
put('journal.afterworld.contract_tip.bureau', 'The Bureau pays, quietly, for anomalies sealed.')

BONDS = {
    'landlord': ("The Ghost Landlord",
                 "Service: once per rent period he will not let you die on his premises. Obligation: 3 emeralds every 3 days.",
                 "Tenancy: once per rent period, a killing blow leaves you standing with three hearts.",
                 "Rent: 3 emeralds every 3 days, taken at dawn if you carry them.",
                 "Mr. Hollowell, late of the Morrow Court, would like a tenant. He has a room. He would very much prefer you didn't die in it.",
                 "Rent paid. Mr. Hollowell tips a hat that is not entirely there.",
                 "Your rent is overdue. Something has started knocking on the walls at night.",
                 "You give notice. A house you never saw groans as you leave it.",
                 "You need 3 emeralds for the rent."),
    'moth': ("The Lantern Moth",
             "Service: sight in the dark, and the invisible revealed. Obligation: 2 glowstone dust or 4 torches every day.",
             "Night sight in darkness; invisible creatures nearby glow.",
             "Feed it 2 glowstone dust or 4 torches each day.",
             "Something soft lands on your lantern and does not leave. It is hungry, and it has decided you are where food comes from.",
             "The moth eats, and the dark softens.",
             "The moth is hungry. It has started eating the light around you.",
             "The moth leaves, and takes a little light with it.",
             "The moth wants 2 glowstone dust or 4 torches."),
    'pilgrim': ("The Pilgrim's Road",
                "Service: sure feet and swift paths. Obligation: visit a mended shrine every 5 days.",
                "You step up whole blocks and walk faster on paths.",
                "Visit a mended shrine every 5 days.",
                "The stone pilgrim kneels. Its footsteps, it seems, would like to walk with you.",
                "Your feet remember the road.",
                "It has been too long since you visited a shrine. Your feet feel heavy.",
                "The road lets go of you.",
                "Stand at a mended shrine to keep this bond."),
}
for k, (name, terms, service, obligation, offer, paid, overdue, released, cannot) in BONDS.items():
    put(f'bond.afterworld.{k}', name)
    put(f'bond.afterworld.{k}.terms', terms)
    put(f'bond.afterworld.{k}.service', service)
    put(f'bond.afterworld.{k}.obligation', obligation)
    put(f'message.afterworld.bond_offer.{k}', offer)
    put(f'message.afterworld.bond_paid.{k}', paid)
    put(f'message.afterworld.bond_overdue.{k}', overdue)
    put(f'message.afterworld.bond_released.{k}', released)
    put(f'message.afterworld.bond_cannot_pay.{k}', cannot)
    put(f'codex.afterworld.bond.{k}', name)
    put(f'codex.afterworld.bond.{k}.desc', f'{service} {obligation} Neglect brings a complication, never a silent penalty.')
put('codex.afterworld.bond.landlord.hint', 'A principled ghost, behind on his rent.')
put('codex.afterworld.bond.moth.hint', 'Keep a hungry lantern lit long enough.')
put('codex.afterworld.bond.pilgrim.hint', 'Help the Pilgrim finish its walk.')
put('codex.afterworld.bond.landlord.tenancy', 'Tenancy')
put('codex.afterworld.bond.landlord.tenancy.desc', 'The Ghost Landlord kept you alive. He will not do it again until the rent is paid.')
put('codex.afterworld.bond.landlord.tenancy.hint', 'A landlord who cares about his premises.')

ITEMS = {
    'expedition_journal': ('Expedition Journal', ['Edicts, rumours, the Chronicle and the Codex.', 'Use it, or press J.']),
    'apologetic_blade': ('The Apologetic Blade', None),
    'anomaly_residue': ('Anomaly Residue', ['Glittering dust left where a rule came loose.']),
    'clockwork_fragment': ('Clockwork Fragment', ['Still ticking. Not obviously connected to anything.']),
    'moth_dust': ('Moth Dust', ['Scales from wings that prefer the dark.']),
    'grave_wax': ('Grave Wax', ['The Morrow Court seals its records with it.']),
    'bell_fragment': ('Bell Fragment', ['It still hums when nobody is listening.']),
    'chronicle_page': ('Chronicle Page', ['Blank, for now. The world will write on it.']),
    'pilgrim_offering': ('Pilgrim Offering', ['A candle, a flower and a stone, tied with string. Mends a pilgrim shrine.']),
    'torn_map': ('Torn Map', ['Someone else’s lead. Read it to make it yours.']),
    'unfinished_compass': ('The Unfinished Compass', ['Points toward the nearest unresolved rumour or hidden place.', 'Sometimes it points to a clue, not a destination.']),
    'hungry_lantern': ('The Hungry Lantern', ['Lit, it reveals invisible creatures and old traces.', 'It feeds on your experience while it burns.']),
    'clockwork_heart': ('Clockwork Heart', ['Winds itself on every blow you take. Release a charge to slow everything thrown at you.', 'It overheats if wound and released too quickly.']),
    'torn_census': ('Torn Census', ['A village census with pages missing.']),
    'ringers_ledger': ("Ringer's Ledger", ['Candle accounts from the Hollow Cathedral.']),
    'pilgrim_spawn_egg': ('Pilgrim Spawn Egg', None),
    'bell_ringer_spawn_egg': ('Bell-Ringer Spawn Egg', None),
    'king_of_seconds_spawn_egg': ('King of Seconds Spawn Egg', None),
    'lantern_moth_spawn_egg': ('Lantern Moth Spawn Egg', None),
    'crow_spawn_egg': ('Crow Spawn Egg', None),
    'mossback_spawn_egg': ('Mossback Spawn Egg', None),
    'hollow_stag_spawn_egg': ('Hollow Stag Spawn Egg', None),
    'clockwork_sentry_spawn_egg': ('Clockwork Sentry Spawn Egg', None),
    'rule_wraith_spawn_egg': ('Rule-Wraith Spawn Egg', None),
    'wisp_spawn_egg': ('Wisp Spawn Egg', None),
    'ashen_husk_spawn_egg': ('Ashen Husk Spawn Egg', None),
    'shardling_spawn_egg': ('Shardling Spawn Egg', None),
    'bog_lurker_spawn_egg': ('Bog Lurker Spawn Egg', None),
    'echo_stalker_spawn_egg': ('Echo Stalker Spawn Egg', None),
    'gearhound_spawn_egg': ('Gearhound Spawn Egg', None),
    'rime_wight_spawn_egg': ('Rime Wight Spawn Egg', None),
    'skywhale_spawn_egg': ('Skywhale Spawn Egg', None),
    'glimmerfin_spawn_egg': ('Glimmerfin Spawn Egg', None),
    'emberfox_spawn_egg': ('Emberfox Spawn Egg', None),
    'rimehorn_spawn_egg': ('Rimehorn Spawn Egg', None),
    'petal_sprite_spawn_egg': ('Petal Sprite Spawn Egg', None),
    'mirror_ray_spawn_egg': ('Mirror Ray Spawn Egg', None),
    'glimmerfin_bucket': ('Bucket of Glimmerfin', ['A little school of light, carried home.']),
    'ember_core': ('Ember Core', ['The coal at the heart of an Ashen Husk. It is still warm.']),
    'living_crystal': ('Living Crystal', ['A shard of a Shardling that has not yet noticed it is broken.']),
    'lurker_fang': ('Lurker Fang', ['Hooked backward, so nothing it holds slips free.']),
    'echo_essence': ('Echo Essence', ['A held breath of the half-step behind the world.']),
    'gear_spring': ('Gear Spring', ['The mainspring of a Gearhound, still trying to unwind.']),
    'rime_core': ('Rime Core', ['The cold that a Rime Wight was built around. It frosts your fingers.']),
    'thimble_of_borrowed_rain': ('Thimble of Borrowed Rain', ['A tiny local rainstorm: waters crops, puts out fires, uncovers tracks.', 'Things that follow rain sometimes follow it.']),
}
for k, (name, lore) in ITEMS.items():
    put(f'item.afterworld.{k}', name)
    if lore:
        put(f'item.afterworld.{k}.lore', lore[0])
        if len(lore) > 1:
            put(f'item.afterworld.{k}.lore2', lore[1])
put('item.afterworld.apologetic_blade.lore', 'Its spirit is very sorry about all of this.')
put('item.afterworld.apologetic_blade.effect', 'Counterattacks after a perfect shield parry.')
BLOCKS = {'shrine_stone': 'Shrine Stone', 'anomaly_core': 'Anomaly Core', 'clockwork_bricks': 'Clockwork Bricks', 'gearwork': 'Gearwork',
          'warded_gearwork': 'Warded Gearwork', 'escapement': 'Escapement', 'counterweight': 'Counterweight Release'}
for k, v in BLOCKS.items():
    put(f'block.afterworld.{k}', v)
ENTITIES = {'thrown_weapon': 'Returning Weapon', 'heavy_projectile': 'Thrown Weight', 'pilgrim': 'The Pilgrim Without Feet', 'bell_ringer': 'The Bell-Ringer',
            'king_of_seconds': 'The King of Seconds', 'second_hand': 'Second Hand', 'vault_warden': 'Vault Warden', 'mirrored_warden': 'Mirrored Warden',
            'spiked_warden': 'Spiked Warden', 'anchored_warden': 'Anchored Warden', 'vault_marksman': 'Vault Marksman'}
for k, v in ENTITIES.items():
    put(f'entity.afterworld.{k}', v)
put('entity.afterworld.echo_of', 'Echo of %s')
put('entity.afterworld.rule_touched', 'Rule-touched %s')
put('itemGroup.afterworld', 'Afterworld')
for k, v in {'relics': 'Relics & Journal', 'arms': 'Arms', 'curios': 'Curios & Materials', 'blocks': 'Blocks',
             'bestiary': 'Bestiary', 'bosses': 'Bosses & Summons'}.items():
    put(f'itemGroup.afterworld.{k}', f'Afterworld ✦ {v}')
ARTS = {
    'emberbrand': ('Emberbrand', 'cinder_cleave', 'Cinder Cleave', 'Leap forward and come down in a ring of fire that scorches and throws everything near.',
                   'Hits set foes alight.', 'A greatsword with an ember for a heart. It is never quite cool to the touch.'),
    'shardsong_glaive': ('Shardsong Glaive', 'crystal_fan', 'Crystal Fan', 'Fling a fan of five living-crystal shards.',
                         'Long reach. Hits sometimes burst, hurting foes close by.', 'It hums when the wind crosses its blade.'),
    'lurkers_hook': ("Lurker's Hook", 'reel', 'Reel', 'Hook what you look at and haul it to you. Sneak to haul yourself to it instead.',
                     'Hits slow the hooked.', 'Barbed backward, so nothing it holds slips free.'),
    'echo_edge': ('Echo Edge', 'step_behind', 'Step Behind', 'Step out of an echo behind what you look at. Your next blow on it lands twice.',
                  'Every hit echoes a moment later.', 'A dagger that is always a half-step behind the world.'),
    'gearspring_maul': ('Gearspring Maul', 'coil_slam', 'Coil Slam', 'Let the mainspring go: everything around you is thrown into the air and slowed.',
                        'Hits knock foes back hard.', 'Wound tight. Do not stand too close when it unwinds.'),
    'rimefang_spear': ('Rimefang Spear', 'frost_lance', 'Frost Lance', 'Lunge forward through everything in your way, freezing what you pass.',
                       'Long reach. Hits frost the target.', 'Its point is colder than the north wind.'),
    'bellhammer': ('Bellhammer', 'toll', 'Toll', 'Ring one great toll: everything nearby is struck, thrown back and stopped for a moment.',
                   'Hits sometimes stop a foe in its tracks.', 'Struck from the Bell-Ringer\'s own bell.'),
    'second_hand': ('The Second Hand', 'stopped_moment', 'Stopped Moment', 'Stop the moment around you: every foe nearby freezes, and you move quickly.',
                    'Blows on stopped foes land half again as hard.', 'The King of Seconds kept time with it.'),
    'pilgrims_staff': ("Pilgrim's Staff", 'way_of_light', 'Way of Light', 'Glide forward along a road of light, scattering foes; it mends you and your allies when it ends.',
                       'Every hit mends you a little.', 'It has walked further than any road.'),
}
for item_id, (name, art, art_name, desc, passive, lore) in ARTS.items():
    put(f'item.afterworld.{item_id}', name)
    put(f'art.afterworld.{art}', art_name)
    put(f'art.afterworld.{art}.desc', desc)
    put(f'art.afterworld.{art}.passive', passive)
    put(f'art.afterworld.{art}.lore', lore)
put('art.afterworld.label', 'Art: %s  (right-click, %ss)')
put('effect.afterworld.stopped', 'Stopped')
put('entity.afterworld.crystal_shard', 'Crystal Shard')
put('tooltip.afterworld.soulbound', '✦ Soulbound: stays with you through death')
put('screen.afterworld.journal', 'Expedition Journal')
put('key.category.afterworld.afterworld', 'Afterworld')
for i, n in enumerate(['I', 'II', 'III', 'IV']):
    put(f'key.afterworld.edict_{i + 1}', f'Cast Edict {n}')
put('key.afterworld.signature', 'Discipline Signature')
put('key.afterworld.journal', 'Open Expedition Journal')

MSG = {
    'learned': 'You bind the Edict of %s.',
    'slots_full': 'Your Edict slots are full. Rearrange them in the Expedition Journal.',
    'fragment_shatter': 'You already hold %s. The fragment shatters into residue and insight.',
    'discipline_chosen': 'You take up the way of the %s.',
    'discipline_cost': 'Changing your Discipline costs 10 levels.',
    'oathbreaker': 'You broke the bargain. %s creatures remember, and you will not be trusted with another for a while.',
    'pact': 'Bargain struck with %s creatures for %s seconds. Do not strike anything.',
    'rewind_refund': '%s Edicts return with the time you borrowed.',
    'witness': '%s creatures revealed, %s caches marked.',
    'forgotten': '%s creatures lose track of you.',
    'favour_owed': 'You owe the Favour %s hostile kills within ten minutes.',
    'favour_paid': 'Your debt to the Favour is settled.',
    'favour_collected': 'The Favour collects its interest: %s levels.',
    'wrong_turn_marked': 'Wrong Turn marked. Press your signature again to return here.',
    'intro.1': 'The world has noticed you.',
    'intro.2': 'Open your Expedition Journal (J) to choose a Discipline. Sneak and right-click a villager to ask what they have heard.',
    'intro.2b': 'Open your Expedition Journal (J) to see your Edicts and leads. Sneak and right-click a villager to ask what they have heard.',
    'settlement_harm': '%s will remember that.',
    'settlement_welcome': '%s welcomes you like an old friend.',
    'settlement_guard': '%s has hired another guard.',
    'settlement_raid': 'Something is moving at the edge of %s tonight.',
    'anomaly_near': 'The air near %s obeys a different rule.',
    'anomaly_inert': 'The core is quiet. Whatever rule it held has been settled.',
    'anomaly_hint': 'A loose rule hums inside: %s. Break the core to seal it, or tap it with a glass bottle.',
    'anomaly_spill': 'The anomaly spills over. Its Edict tears free, and the strangeness spreads.',
    'beast_near': '%s has caught your scent.',
    'caravan_attack': 'The caravan is under attack! Keep the caravan master alive.',
    'caravan_lost': 'The caravan master is dead. The road will stay silent.',
    'roadwright': 'The Road That Moves has stopped here for a while. The Roadwright is open for business.',
    'shrine_cracked': 'The shrine stone is cracked. A Pilgrim Offering would mend it.',
    'shrine_whole': 'The shrine is whole, and warm to the touch.',
    'rumours_full': 'Your journal is full of unresolved leads. Settle or abandon some first.',
    'contract_active': 'You already hold a contract from them.',
    'contract_refused': '%s will not give you work.',
    'contract_none': '%s has no work for you right now.',
    'contract_paid': '%s has paid for your contract.',
    'boon': '%s sends a gift in recognition of your service.',
    'bond_formed': 'A Bond is formed: %s.',
    'bond_full': 'You cannot hold more than two Bonds.',
    'bond_offer_hint': 'Open your Expedition Journal (World tab) to accept or decline.',
    'tenancy': '"Not on my premises," says a voice like a door in a draught. You are still standing.',
    'lantern_hungry': 'The lantern is hungry, and you have nothing to feed it.',
    'lantern_starved': 'The lantern gutters out. It has eaten everything you had.',
    'heart_empty': 'The heart is unwound.',
    'heart_overheat': 'The Clockwork Heart overheats!',
    'thimble_follower': 'Something that follows the rain has found you.',
    'pilgrim_peace': 'The Pilgrim does not fight on a finished road.',
    'pilgrim_wakes': 'The Pilgrim stops walking, and turns toward you.',
    'pilgrim_opens': 'The Pilgrim pauses at %s. Something beneath the shrine opens.',
    'shrines_mended': 'Pilgrim shrines mended: %s of 4.',
    'ringer_appears': 'Something heavy drags a bell across the nave.',
    'ringer_topples': 'Weightless, the Bell-Ringer loses its footing!',
    'bell_silences': "The bell's toll silences %s.",
    'ringer_bell_lost': 'The Bell-Ringer has lost its bell. Without it, it is only angry.',
    'counterweight_spent': 'The counterweight has already been released.',
    'chandelier_hit': 'The great chandelier comes down on the Bell-Ringer!',
    'chandelier_miss': 'The great chandelier crashes down onto the empty nave.',
    'ringer_wrong_name': 'The Bell-Ringer does not answer to "%s". It answers with the bell.',
    'ringer_remembers': '"%s," it says, as if tasting the word. "I rang for the dead, so they would be remembered. I forgot myself instead." It sets the bell down.',
    'king_wakes': 'The King of Seconds rises from his throne. "You are late. Everyone always is."',
    'king_rewound': 'The King of Seconds is on his throne again. "Did you think I could not rewind my own ending?"',
    'king_offer': '"Enough." The King kneels. "I can spare you a second of mine, for my life." Hold a clock and sneak-use him to accept.',
    'king_refused': '"So be it," says the King, and stands.',
    'king_bargained': 'The King of Seconds presses a fragment of himself into your hand, and is gone between one tick and the next.',
    'king_loop': 'Something marks where you stand. Be here when it comes due.',
    'king_loop_held': 'You held your ground in time.',
    'king_staggers': 'An escapement shatters. The King stumbles, out of time.',
    'vault_bell': 'A bell rings as you pass. The Vault knows where you are.',
    'vault_gate': 'Both clocks are wound. The warded gate grinds open.',
    'vault_adapt.mirrored': 'The Vault has noticed you favour the bow. Its wardens now carry mirrored shields.',
    'vault_adapt.spiked': 'The Vault has noticed you favour the blade. Its wardens are now covered in spikes.',
    'vault_adapt.anchored': 'The Vault has noticed you lift its wardens. They are now chained to the floor.',
    'vault_adapt.warded': 'The Vault has noticed you walk through its walls. Some of them are now warded.',
    'vault_adapt.listening': 'The Vault has noticed you creep. It has hung bells.',
    'vault_adapt.expected': 'The Vault remembers which way you came last time. It is expecting you.',
    'unremembered_appear': 'Lights come on in %s, in houses that have no roofs.',
    'need_census': 'You need the Torn Census.',
    'need_pages': 'Restoring their history needs 3 Chronicle Pages.',
}
for k, v in MSG.items():
    put(f'message.afterworld.{k}', v)
FAIL = {
    'return_nothing': 'Nothing of yours is close enough to return.',
    'borrowed_time_none': 'There is no time to borrow yet.',
    'oathbreaker': 'No creature will bargain with an Oathbreaker.',
    'hospitality_none': 'There is no one to bargain with.',
    'trespass_up': 'Trespass only works sideways.',
    'trespass_warded': 'The wall is warded.',
    'trespass_none': 'There is no wall to pass through, or no room beyond it.',
    'empty_slot': 'No Edict is bound to that slot.',
    'suppressed': 'The bell’s toll still silences that Edict.',
    'collision_slots': 'Rule Collision needs Edicts in both of your first two slots.',
    'collision_cooldown': 'Both Edicts must be ready to collide.',
    'cooldown': '%s is not ready yet.',
    'last_word': 'Last Word can only be spoken near defeat.',
    'no_discipline': 'Choose a Discipline in your Expedition Journal first.',
    'wrong_turn_far': 'Your mark is too far away.',
}
for k, v in FAIL.items():
    put(f'message.afterworld.fail.{k}', v)
NOTICE = {
    'codex': 'Codex: %s', 'chronicle': 'Chronicle: %s', 'page_restored': 'A missing page restored: %s', 'rep_tier': '%s now regards you as %s',
    'rumour': 'Rumour heard: %s', 'rumour_found': 'You found what the rumour described: %s', 'rumour_outdated': 'Outdated: %s',
    'rumour_resolved': 'Rumour settled: %s', 'bond_offer': 'A Bond is offered: %s', 'adapt': 'The Vault adapts: %s',
}
for k, v in NOTICE.items():
    put(f'notice.afterworld.{k}', v)
EVENTS = {'silent_caravan': 'The Silent Caravan', 'roads_last_stop': "The Road's Last Stop", 'crown_of_crows': 'A Crown of Crows', 'anomaly_bloom': 'Anomaly Bloom'}
for k, v in EVENTS.items():
    put(f'event.afterworld.{k}', v)

J = {
    'tab.edicts': 'Edicts', 'tab.rumours': 'Rumours', 'tab.chronicle': 'Chronicle', 'tab.world': 'World', 'tab.codex': 'Field Codex',
    'loading': 'The pages are still turning…',
    'signature': 'Signature: %s  [%s]', 'signature_short': 'Signature: %s', 'change_discipline': 'Change Discipline',
    'change_cost': 'Changing your Discipline costs 10 levels.', 'slots': 'Bound Edicts', 'empty_slot': '(empty)',
    'library': 'Library', 'library_hint': 'Select a slot, then click a rule to bind it there.', 'click_equip': 'Click to bind into the selected slot.',
    'cooldown': 'Cooldown: %ss', 'unknown_edict': 'An Edict you have not found.', 'clear_slot': 'Clear Slot',
    'choose_intro': 'Every traveller fights in their own way. Choose a Discipline: it grants a signature technique, a passive gift, and your first Edict. You can change it later, at a cost.',
    'change_intro': 'Choose a new Discipline. The change costs 10 levels, and your signature will start cold.',
    'current': 'current', 'starts_with': 'First Edict: %s', 'take_up': 'Take up the way of the %s', 'pick_one': 'Choose a Discipline',
    'no_rumours': 'No rumours yet. Sneak and right-click a villager to ask what they have heard, read a torn map, or ask a faction for work below.',
    'heard_from': 'Heard from %s', 'bearing': 'It lies to the %s, %s from here.', 'other_world': 'It lies in another world.',
    'confidence': 'Confidence: %s', 'conf_high': 'High', 'conf_fair': 'Fair', 'conf_low': 'Low', 'arrived': 'You have reached the place it describes.',
    'track': 'Track', 'tracked': 'Tracked', 'track_tip': 'Witness, the Unfinished Compass and the Hungry Lantern point to the tracked rumour.',
    'abandon': 'Abandon', 'contracts': 'Ask for work', 'ask_work': 'Ask %s', 'contract_taken': '%s: contract held',
    'contract_refused_short': '%s: refused',
    'reliability.0': 'The rumour was true.', 'reliability.1': 'The rumour left things out.', 'reliability.2': 'The rumour was out of date.', 'reliability.3': 'The rumour was a lie.',
    'chronicle.public': 'Public Record', 'chronicle.legend': 'Personal Legend', 'chronicle.missing': 'Missing Pages',
    'legend_empty': 'Your legend has not begun.', 'chronicle_empty': 'The Chronicle is empty.', 'missing_empty': 'No pages are missing. That you know of.',
    'page_torn': '[the rest of this page is missing]', 'day': 'Day %s',
    'certainty.0': 'Confirmed', 'certainty.1': 'Rumoured', 'certainty.2': 'Disputed', 'certainty.3': 'Falsified', 'certainty.4': 'Erased',
    'bonds': 'Bonds', 'no_bonds': 'You have no Bonds. Some things in the world would like one.', 'bond_offered': 'Offered: %s',
    'accept': 'Accept', 'decline': 'Decline', 'bond_due': 'Obligation due in %s days', 'bond_complication': 'Overdue: a complication follows you',
    'pay': 'Fulfil now', 'release': 'End Bond', 'release_tip': 'Ending a Bond has a small, lingering consequence.',
    'factions': 'Factions', 'standing': '%s (%s)', 'influence': 'Influence', 'operation': 'Now: %s', 'claim_boon': 'Claim a boon',
    'nearby': 'Nearest settlement', 'kept_by': 'kept by %s, under %s', 'events': 'Events in progress',
    'stat.safety': 'Safety', 'stat.prosperity': 'Prosperity', 'stat.legitimacy': 'Legitimacy', 'stat.anomaly': 'Strangeness',
    'discoveries': '%s discoveries recorded', 'undiscovered': 'Not yet discovered.',
    'codex.edicts': 'Edicts', 'codex.combos': 'Rule Collisions', 'codex.disciplines': 'Disciplines', 'codex.places': 'Places',
    'codex.anomalies': 'Anomalies', 'codex.relics': 'Relics', 'codex.bonds': 'Bonds', 'codex.actors': 'Actors of the World',
    'codex.vault': 'The Vault Remembers', 'codex.rules': 'Rules of the World',
}
for k, v in J.items():
    put(f'journal.afterworld.{k}', v)

CODEX = {
    'combo.echoed_edict': ('Echoed Edict', 'Casting any Edict during an Echoes window repeats it a moment later at reduced strength.', 'What happens when an echo meets a rule?'),
    'combo.returning_echo': ('Returning Echo', 'A weapon hurled under Echoes is followed by its own spectral double.', 'Throw something while your afterimage is listening.'),
    'combo.gravity_orbit': ('Gravity Orbit', 'A weapon thrown into a Weightlessness rift orbits inside it, striking everything caught there, before it returns.', 'Throw a returning weapon into a place without gravity.'),
    'combo.frozen_volley': ('Frozen Volley', 'Your own arrows hang in Stillness and resume their flight all at once when time returns.', 'Loose arrows while time is thick.'),
    'combo.suspended_sentence': ('Suspended Sentence', 'Creatures held by both Weightlessness and Stillness are helpless and take half again as much damage.', 'Two rules about motion, at once.'),
    'combo.rewind_refund': ('Rewind Refund', 'Borrowed Time returns the Edicts you spent in the seconds it takes back.', 'Borrow time right after spending it.'),
    'combo.kept_promise': ('Kept Promise', 'Hunting echoes born while a Hospitality bargain holds are bound: they never turn, and last twice as long.', 'Hunt under a promise.'),
    'combo.ambush': ('Ambush', 'While Forgotten and Witnessing, your strikes on revealed creatures deal double damage.', 'Be unseen by what you can see.'),
    'combo.rule_collision': ('Rule Collision', 'The Arcanist’s signature: two Edicts cast as one, each stronger than alone.', 'An Arcanist technique.'),
    'discipline.vanguard.parry': ('Perfect Parry', 'Raise a shield a heartbeat before a blow lands and the attacker staggers, slowed and weakened.', 'Timing matters more than the shield.'),
    'discipline.vanguard.last_word': ('Last Word', 'Near defeat, a Vanguard’s counter hits hardest.', 'A Vanguard technique.'),
    'discipline.wayfarer.wrong_turn': ('Wrong Turn', 'Mark a place, then return to it from up to a hundred and twenty-eight blocks away.', 'A Wayfarer technique.'),
    'discipline.keeper.favour': ('Unpaid Favour', 'Favours are repaid in hostile kills, or the debt collects experience as interest.', 'A Keeper technique.'),
    'place.settlement': ('Settlements', 'Villages remember. Each tracks its safety, prosperity, legitimacy and strangeness, which change with what happens there. Sneak and right-click a villager with an empty hand to ask what they have heard.', 'Visit a village.'),
    'place.shrine': ('Pilgrim Shrines', 'Old shrines along the Pilgrim’s road. A Pilgrim Offering mends one, and the Pilgrim remembers who mended them.', 'Follow the worn path.'),
    'place.anomaly': ('Anomalies', 'Places where one of the world’s rules has come loose. Break the core to seal it and keep its Edict, or tap it with a glass bottle for residue. Tapped anomalies grow.', 'Rumours speak of places where the rules run thin.'),
    'place.lair': ('Lairs', 'A Marked Beast that troubles a settlement. Killing it restores the village’s safety.', 'Villages that feel unsafe know where the tracks lead.'),
    'place.cache': ('Caches', 'Hidden stores, often mentioned in rumours. Not every rumour about them is true.', 'Somebody always knows somebody who buried something.'),
    'place.unremembered': ('The Unremembered', 'A village that appears on stormy nights. Its people forget a part of their lives every night.', 'A merchant swears it appears in thunder.'),
    'place.cathedral': ('The Hollow Cathedral', 'Home of the Bell-Ringer, whose bell silences Edicts.', 'Nobody goes there.'),
    'place.vault': ('The Vault of Seconds', 'An adaptive dungeon beneath a clocktower. It studies how it is fought and changes its wardens to match.', 'The Guild lost surveyors there.'),
    'anomaly.choice': ('Seal or Exploit', 'Breaking an anomaly’s core seals it for good, and the Bureau approves. Tapping it with a glass bottle yields residue; after enough taps it spills its Edict and spreads.', 'Stand before an anomaly core.'),
    'anomaly.sealed': ('Sealed', 'You sealed an anomaly. Its rule is yours now, and the land around it is quieter.', 'Break an anomaly core.'),
    'anomaly.tapped': ('Tapped', 'You drew residue from a living anomaly. It noticed.', 'Bring a glass bottle to an anomaly core.'),
    'anomaly.weightlessness': ('Local Rule: Weightlessness', 'Inside this anomaly you fall slowly and leap high.', 'Stand inside a weightless anomaly.'),
    'anomaly.echoes': ('Local Rule: Echoes', 'Inside this anomaly, every strike you land is echoed.', 'Stand inside an echoing anomaly.'),
    'anomaly.stillness': ('Local Rule: Stillness', 'Inside this anomaly, hostile creatures move slowly.', 'Stand inside a still anomaly.'),
    'anomaly.witness': ('Local Rule: Witness', 'Inside this anomaly, every creature nearby is revealed.', 'Stand inside a watching anomaly.'),
    'anomaly.forgotten': ('Local Rule: Forgotten', 'Inside this anomaly, creatures forget you are there.', 'Stand inside a forgetting anomaly.'),
    'anomaly.hunger': ('Local Rule: Hunger', 'Inside this anomaly, the foes you defeat rise as echoes.', 'Stand inside a hungry anomaly.'),
    'anomaly.return': ('Local Rule: Return', 'Inside this anomaly, whatever you drop comes back to you.', 'Stand inside a returning anomaly.'),
    'anomaly.hospitality': ('Local Rule: Hospitality', 'Inside this anomaly, creatures treat you as a guest.', 'Stand inside a courteous anomaly.'),
    'relic.unfinished_compass': ('The Unfinished Compass', 'Points toward your tracked rumour, or the nearest one. Far away, it points at a clue on the way rather than the destination.', 'Granted by the Guild, or made from residue and a compass.'),
    'relic.hungry_lantern': ('The Hungry Lantern', 'While lit in your hand it reveals invisible creatures and traces of your leads, and eats your experience. Keep it lit long enough and something may move in.', 'Granted by the Court, or made with moth dust.'),
    'relic.clockwork_heart': ('Clockwork Heart', 'Winds itself when you are hurt. Release a charge to open a bubble of slowed time around you.', 'Granted by the Bureau, or made with clockwork.'),
    'relic.clockwork_heart.overheat': ('Overheating', 'Released twice in quick succession, the Clockwork Heart overheats and burns you.', 'Impatience has a cost.'),
    'relic.apologetic_blade': ('The Apologetic Blade', 'After a perfect parry, the blade answers with a counterattack of its own.', 'Granted by the Crown, or forged with a bell fragment.'),
    'relic.apologetic_blade.conscience': ('A Conscience in Steel', 'The blade apologises, at length, whenever it is turned on something that meant no harm.', 'Use it on something gentle.'),
    'relic.thimble': ('Thimble of Borrowed Rain', 'A tiny rainstorm that waters crops, puts out fires and uncovers tracks toward your leads. Things that follow rain may follow it.', 'Granted by the Bureau, or made with residue.'),
    'boss.pilgrim': ('The Pilgrim Without Feet', 'A colossal stone pilgrim that walks between four shrines without touching the ground. It rarely attacks first. Its footsteps wear roads into the grass.', 'Shepherds speak of a walking statue.'),
    'pilgrim.shrine_repaired': ('A Mended Shrine', 'You mended a pilgrim shrine. If all four are whole, the Pilgrim can finish its walk.', 'Bring an offering to a cracked shrine.'),
    'boss.pilgrim.peace': ('The Walk Completed', 'With its shrines mended, the Pilgrim finished its walk and knelt. Its road now walks with whoever helped it.', 'What does a pilgrim want?'),
    'boss.bell_ringer': ('The Bell-Ringer', 'A hunched giant that drags a great bell through the Hollow Cathedral. Each ring silences one of your Edicts.', 'Something drags a bell in the cathedral.'),
    'boss.bell_ringer.name': ('A Forgotten Name', 'The Bell-Ringer once had a name. Written on a name tag and offered to it, a name could end the fight without a death.', 'Ledgers in the cathedral archive remember part of it.'),
    'boss.bell_ringer.counterweight': ('The Counterweight', 'A release in the cathedral loft drops the great chandelier on whatever stands beneath it.', 'Look up.'),
    'boss.king': ('The King of Seconds', 'A clockwork monarch who locks his challengers into loops of their own movement. Every escapement left standing keeps him on time.', 'He waits at the bottom of the Vault of Seconds.'),
    'boss.king.bargain': ('A Bargain with Time', 'Brought low, the King may offer a fragment of his power in exchange for his life. Hold out a clock and sneak-use him to accept.', 'Kings always have something to trade.'),
    'npc.roadwright': ('The Roadwright', 'Merchant of the Road That Moves, which links places that should not connect. Trades rare rules for rare materials.', 'The road stops near places that have been found.'),
    'npc.archivist': ('The Archivist', 'An echo who keeps the Unremembered village’s census, and forgets a page of it every night.', 'Visit the village that appears in thunder.'),
    'npc.landlord': ('Mr. Hollowell', 'A ghost landlord with a strong sense of duty and a weak grasp of how long ago he died.', 'He owns property in the Unremembered village.'),
    'vault.entered': ('The Vault Watches', 'The Vault of Seconds counts how you fight: blades or bows, which Edicts, how quietly. Return, and it will have changed to meet you.', 'Enter the Vault of Seconds.'),
    'vault.adapt.mirrored': ('Mirrored Wardens', 'You favoured the bow, so the Vault armed its wardens with mirrored shields that turn arrows aside. Close the distance.', 'Fight the Vault from a distance.'),
    'vault.adapt.spiked': ('Spiked Wardens', 'You favoured the blade, so the Vault covered its wardens in spikes that punish every strike. Keep your distance.', 'Fight the Vault up close.'),
    'vault.adapt.anchored': ('Anchored Wardens', 'You leaned on Weightlessness, so the Vault chained its wardens down. Other rules still apply.', 'Lift the Vault’s wardens too often.'),
    'vault.adapt.warded': ('Warded Walls', 'You walked through the Vault’s walls, so it warded them. Look for the door it forgot.', 'Trespass in the Vault.'),
    'vault.adapt.listening': ('Listening Bells', 'You crept through the Vault, so it hung bells that ring when you pass. Move quickly, or break them.', 'Sneak through the Vault.'),
    'vault.adapt.expected': ('Expected', 'The Vault remembered which wing you took first, and sent more wardens there.', 'Return to the Vault by the same route.'),
    'boss.bell_ringer.rift': ('Off Balance', 'Caught in a Weightlessness rift, the Bell-Ringer topples and takes half again as much damage while it struggles up.', 'Heavy things hate losing their footing.'),
    'boss.bell_ringer.stolen': ('The Stolen Bell', 'When the Bell-Ringer throws its bell, Return can take it. Without the bell it cannot silence you, but it grows angrier.', 'What returns to whoever calls it?'),
    'rule.oathbreaker': ('Oathbreaker', 'Break a Hospitality bargain and the creatures you deceived are enraged, while you are weakened and cannot bargain for five minutes.', 'Strike something under a bargain.'),
    'rumour.first': ('Rumourcraft', 'Rumours come from real things in the world. They tell you which way and how far, never exactly where. Some leave things out, some are out of date, and some are lies.', 'Ask a villager what they have heard.'),
    'rumour.outdated': ('Outdated', 'Someone settled a rumour before you could. The Chronicle remembers who.', 'Arrive second.'),
    'rumour.false': ('A Lie, Followed', 'You followed a false rumour, and found out who wanted it told.', 'Some rumours are bait.'),
    'event.silent_caravan': ('The Silent Caravan', 'Unsafe roads stop caravans. Defend one, and the settlement it was bound for recovers.', 'Struggling villages wait for caravans.'),
    'quest.unremembered': ('The Village That Appears at Night', 'Restore the missing history, sell it, or let the village begin anew. Each choice changes what grows there.', 'Visit in a thunderstorm.'),
}
for k, (title, desc, hint) in CODEX.items():
    put(f'codex.afterworld.{k}', title)
    put(f'codex.afterworld.{k}.desc', desc)
    put(f'codex.afterworld.{k}.hint', hint)

# Title and loading screens.
put('title.afterworld.subtitle', 'The world that plays back')
put('title.afterworld.version', 'Afterworld %s')
TIPS = [
    "Rumours give a bearing and a distance, never an exact place. Follow the bearing, then look around.",
    "Every Edict has a rule and a limit. Learn the limit and the rule becomes a weapon.",
    "Press J to open your Journal. The Chronicle remembers what happened while you were away.",
    "Settlements remember who helped them, and who did not.",
    "A Hospitality bargain holds only while you keep your hands to yourself.",
    "Borrowed Time rewinds your health and hunger, never your items.",
    "Struggling settlements wait for caravans. Unsafe roads stop them.",
    "The Pilgrim rarely strikes first. Ask what it wants before you ask how to kill it.",
    "Each ring of the Bell-Ringer's bell silences one of your Edicts. Count the rings.",
    "The Vault of Seconds learns how you fight. Change your habits and it has to change too.",
    "Some rumours are lies, and someone wanted them told.",
    "In a thunderstorm, look for lights where no village stood yesterday.",
]
for i, tip in enumerate(TIPS):
    put(f'loading.afterworld.tip.{i}', tip)
put('loading.afterworld.tip_label', 'Did you know')

SUBTITLES = {
    'ui.discovery': 'Discovery', 'ui.rumour': 'Rumour heard', 'ui.chronicle': 'Chronicle written',
    'edict.ready': 'Edict ready', 'edict.fail': 'Edict fails',
    'edict.return': 'Weapon flies', 'edict.echoes': 'Echo repeats', 'edict.borrowed_time': 'Time rewinds',
    'edict.weightlessness': 'Gravity loosens', 'edict.hunger': 'Hunger stirs', 'edict.hospitality': 'Bargain offered',
    'edict.trespass': 'Something passes through stone', 'edict.stillness': 'Time thickens', 'edict.witness': 'Everything is seen',
    'edict.forgotten': 'Memory fades',
    'boss.bell': 'Great bell tolls', 'boss.stomp': 'Heavy footstep', 'boss.tick': 'Clockwork ticks',
    'boss.loop': 'Time loops', 'boss.roar': 'Something roars',
    'world.anomaly': 'Anomaly hums', 'world.whisper': 'Whispers', 'world.shard': 'Shard chimes',
    'mob.moth': 'Moth flutters', 'mob.crow': 'Crow caws', 'mob.sentry': 'Sentry whirs',
    'mob.wraith': 'Wraith moans', 'mob.stag': 'Stag calls', 'mob.mossback': 'Mossback grumbles',
    'mob.whale': 'Skywhale sings', 'mob.sprite': 'Petal sprite chimes', 'mob.rimehorn': 'Rimehorn lows', 'mob.emberfox': 'Emberfox yips',
}
for k, v in SUBTITLES.items():
    put(f'subtitles.afterworld.{k}', v)

# Arrival (character creation) and the wardrobe.
ARRIVAL = {
    'title': "Traveller's Arrival", 'wardrobe': 'Wardrobe',
    'q.intro': '...A traveller wakes at the edge of the world.',
    'q.origin': 'Where do you come from?',
    'q.discipline': 'How do you meet trouble?',
    'q.affinity': 'What lies within you?',
    'q.name': 'What is your name?',
    'q.garb': 'How do you travel?',
    'q.confirm': 'Is this you?',
    'q.outro': '...and the world begins to remember you.',
    'press_any_key': 'Press any key',
    'step': '%s of %s',
    'later': 'Decide later',
    'later_help': 'You will arrive without a discipline and can choose one from your Journal whenever you are ready. Your first Edict will have to be found.',
    'standing': 'Standing', 'standing.one': '%s  +%s', 'standing.all': 'Every faction  +%s',
    'carries': 'Carries', 'first_edict': 'First Edict', 'signature': 'Signature: %s',
    'affinity_gift': '%s comes back to you a fifth sooner, and its shard glows upon your chest.',
    'name_help': 'Type a name, or press Enter to keep the one you came with.',
    'name_keep': 'Press Enter when it sounds right.',
    'garb.head': 'Head', 'garb.cloak': 'Cloak', 'garb.accent': 'Waist', 'garb.arms': 'Arms', 'garb.feet': 'Feet',
    'garb.cloth': 'Cloth', 'garb.trim': 'Trim', 'garb.emblem': 'Emblem', 'garb.wear': 'Wear garb', 'garb.random': 'Surprise me',
    'garb.random_value': 'Anything at all', 'garb.wear_yes': 'Yes', 'garb.wear_no': 'Plain clothes',
    'garb_help.head': 'Helmets hide whatever you wear on your head.',
    'garb_help.cloak': 'Cloaks catch the wind as you move. A cloak replaces any cape you own.',
    'garb_help.accent': 'The lantern belt glows faintly in the dark.',
    'garb_help.arms': 'Chestplates cover bracers and wraps.',
    'garb_help.feet': 'Boots cover cuffs; leggings cover wraps.',
    'garb_help.cloth': 'The main colour of everything you wear.',
    'garb_help.trim': 'Hems, bands, bracers and boots.',
    'garb_help.emblem': 'Embroidered on your cloak, and pinned to your chest.',
    'garb_help.wear': 'Plain clothes show only your skin, and hide your shard.',
    'garb_help.random': 'Press Enter until something feels like you.',
    'summary.name': 'Called %s.', 'summary.origin': 'Once a %s.', 'summary.discipline': 'Walks the way of the %s.',
    'summary.undecided': 'Has not yet decided how to meet trouble.', 'summary.affinity': '%s lies within.',
    'yes': 'Yes, this is me', 'not_yet': 'Not yet',
    'hint.choose': 'Choose', 'hint.change': 'Change', 'hint.confirm': 'Answer', 'hint.done': 'Done',
    'hint.cancel': 'Cancel', 'hint.back': 'Back', 'hint.turn': 'Drag to turn', 'hint.erase': 'Erase',
}
for k, v in ARRIVAL.items():
    put(f'arrival.afterworld.{k}', v)
put('message.afterworld.intro.named', 'The world has noticed you, %s.')
put('journal.afterworld.within', '%s within')

# The wonders: title cards and Codex entries.
WONDERS = {
    'titan': ('The Bound Titan', 'It knelt when the first rules were written.',
              'A horned colossus chained to four obelisks, kneeling over a sword it has not drawn in an age. Whatever it was, the world decided it should not stand.',
              'Something vast kneels where the ash lies thickest.'),
    'angels': ('The Wardens of the Pass', 'Told to watch, and never told to stop.',
               'Two winged guardians of white stone face each other across a ruined way. Their haloes still glow at night, though nothing passes.',
               'Two figures keep watch over a road to nowhere.'),
    'cathedral': ('Vesperhold', 'A city that still keeps its evening bells.',
                  'A walled city around a great cathedral of glass and spires. Its windows burn every night for a congregation no one has seen arrive.',
                  'Look for spires above the plains at dusk.'),
    'citadel': ('The Black Citadel', 'Its spires are older than the dark around them.',
                'A fortress of needle spires on a sheer crag, its windows lit by soul fire. The Bureau files it under things that were here first.',
                'Violet lights high on a crag, in the dark lands.'),
    'mill': ('Brassmere Works', "Where the Guild's airships come to drink.",
             "The Cartographers' Guild mill and skyport: a great waterwheel, copper pipes, smoking chimneys and an airship moored at the tower.",
             'Follow the smoke and the sound of the wheel.'),
}
for k, (title, sub, desc, hint) in WONDERS.items():
    put(f'wonder.afterworld.{k}', title)
    put(f'wonder.afterworld.{k}.sub', sub)
    put(f'codex.afterworld.wonder.{k}', title)
    put(f'codex.afterworld.wonder.{k}.desc', desc)
    put(f'codex.afterworld.wonder.{k}.hint', hint)

ORIGINS = {
    'crown_squire': ('Crown Squire', 'Sworn to a crown that may not remember you.',
                     'You carried a banner for the Crown until the road carried you off. Crown folk still greet you as one of their own.'),
    'guild_cartographer': ('Guild Cartographer', 'Every map is a promise someone else will break.',
                           "The Cartographers' Guild taught you to measure the world, and the world refuses to hold still. Guildsfolk trade with you on better terms."),
    'court_mourner': ('Court Mourner', 'You remember the dead, so that they need not.',
                      'The Morrow Court taught you the names of the departed. Its keepers welcome you, and the forgotten sometimes do too.'),
    'bureau_clerk': ('Bureau Clerk', 'Somewhere, there is a form for this.',
                     'You filed anomalies for the Bureau of Impossible Things until one of them filed you. Bureau agents trust your paperwork.'),
    'wanderer': ('Wanderer', 'No banner, no debts, no map.',
                 'Nobody in particular. Every faction treats you as a curiosity rather than a rival, and you came well supplied.'),
    'none': ('Unknown', '', ''),
}
for k, short in (('crown_squire', 'Squire'), ('guild_cartographer', 'Cartographer'), ('court_mourner', 'Mourner'),
                 ('bureau_clerk', 'Clerk'), ('wanderer', 'Wanderer')):
    put(f'origin.afterworld.{k}.short', short)
for k, (name, tag, desc) in ORIGINS.items():
    put(f'origin.afterworld.{k}', name)
    if tag:
        put(f'origin.afterworld.{k}.tagline', tag)
        put(f'origin.afterworld.{k}.desc', desc)
        put(f'codex.afterworld.origin.{k}', name)
        put(f'codex.afterworld.origin.{k}.desc', desc)
        put(f'codex.afterworld.origin.{k}.hint', 'Chosen on arrival.')

GARB = {
    'cat.head': 'Head', 'cat.cloak': 'Cloak', 'cat.accent': 'Waist', 'cat.arms': 'Arms', 'cat.feet': 'Feet', 'cat.dye': 'Dye', 'cat.emblem': 'Emblem',
    'head.0': 'Bare', 'head.1': 'Hood', 'head.2': 'Wide hat', 'head.3': 'Cowl', 'head.4': 'Circlet',
    'cloak.0': 'None', 'cloak.1': 'Short cape', 'cloak.2': 'Long cloak', 'cloak.3': 'Tattered cloak', 'cloak.4': 'Mantle', 'cloak.5': 'Mantle and cloak',
    'accent.0': 'Nothing', 'accent.1': 'Belt and satchel', 'accent.2': 'Sash', 'accent.3': 'Bandolier', 'accent.4': 'Lantern belt',
    'arms.0': 'Bare', 'arms.1': 'Bracers', 'arms.2': 'Wraps',
    'feet.0': 'Bare', 'feet.1': 'Boot cuffs', 'feet.2': 'Leg wraps',
    'emblem.0': 'None', 'emblem.1': 'Crown', 'emblem.2': 'Compass', 'emblem.3': 'Moth', 'emblem.4': 'Gear', 'emblem.5': 'Shard', 'emblem.6': 'Eye',
    'cloth': 'Cloth', 'trim': 'Trim',
}
for k, v in GARB.items():
    put(f'garb.afterworld.{k}', v)
for name in ('bone', 'ash', 'charcoal', 'midnight', 'lake', 'teal', 'moss', 'forest', 'ochre', 'amber', 'rust', 'crimson', 'plum', 'lavender', 'umber', 'rose'):
    put(f'garb.afterworld.colour.{name}', name.capitalize())

# Biomes: name, and the line under their title card.
BIOME_TEXT = {
    'ashen_moor': ('Ashen Moor', 'Where the old fires never quite went out'),
    'hollowwood': ('Hollowwood', 'The trees here are older than the roads'),
    'shardfields': ('Shardfields', 'Here, stones forget which way is down'),
    'gloamfen': ('Gloamfen', 'Follow the lights at your own risk'),
    'vesper_downs': ('Vesper Downs', 'Where the evening is always just beginning'),
    'mossmere_wood': ('Mossmere Wood', 'Old trees, older moss, and nobody keeping count'),
    'pale_reach': ('Pale Reach', 'The Court buries names here, and the trees remember them'),
    'gloomwald': ('Gloomwald', 'Even the mushrooms have stopped waiting for morning'),
    'petalfall': ('Petalfall', 'It has been spring here for a very long time'),
    'frostmere_pines': ('Frostmere Pines', 'The snow keeps what the pines forget'),
    'whitewaste': ('Whitewaste', 'Something enormous died here, and the cold kept it'),
    'rimeglass_spires': ('Rimeglass Spires', 'The ice grows upward, as if it were reaching'),
    'skyspire_peaks': ('Skyspire Peaks', 'Above the clouds, the rules thin out'),
    'windworn_highlands': ('Windworn Highlands', 'The stones stand where the wind told them to'),
    'ember_steppe': ('Ember Steppe', 'The grass remembers a fire that has not happened yet'),
    'hourglass_dunes': ('Hourglass Dunes', 'Every grain is a second somebody borrowed'),
    'rustscar_canyons': ('Rustscar Canyons', 'The rock bleeds copper where the rules were cut'),
    'verdant_tangle': ('Verdant Tangle', 'The forest grew over the Archive, and kept reading'),
    'sporeveil': ('Sporeveil', 'Breathe lightly; the island is listening'),
    'mirrorsea': ('Mirrorsea', 'Calm enough to show you the sky twice'),
    'mirrordeep': ('Mirrordeep', 'Somewhere below, the reflections keep going'),
    'ember_shallows': ('Ember Shallows', 'Warm water over sunken colonnades'),
    'rimesea': ('Rimesea', 'Cold, clear water, and icebergs drifting like ships'),
    'rimefloe': ('Rimefloe', 'The sea froze here, and never thawed'),
    'duskpine_weald': ('Duskpine Weald', 'Dark pines, soft moss, and lights left burning by no one'),
    'glimmerfrost': ('Glimmerfrost', 'Under the snow, something is still glowing'),
    'rimedeep': ('Rimedeep', 'The cold goes all the way down'),
    'silverrun': ('Silverrun', 'Rivers that remember where they came from'),
    'bone_shore': ('Bone Shore', 'The tide leaves bones; the bones stay'),
    'breakstone_shore': ('Breakstone Shore', 'Black columns, white water'),
    'echo_hollows': ('Echo Hollows', 'Every sound down here happens twice'),
    'rootways': ('Rootways', 'The roots of the Hollowwood go deeper than you think'),
}
for k, (name, line) in BIOME_TEXT.items():
    put(f'biome.afterworld.{k}', name)
    put(f'title.afterworld.region.{k}', line)

# Creatures.
CREATURES = {
    'lantern_moth': ('Lantern Moth', 'A night moth with a lantern for an abdomen. It drifts toward any light it can find, and leaves glowing dust when it dies.', 'Look near torches after dark.'),
    'crow': ('Crow', 'Crows know things. Feed one seeds and it will tell you what it has seen. Approach quietly, or it will be gone.', 'Sneak up on a crow with seeds.'),
    'mossback': ('Mossback', 'A patient beast with a garden on its shell. Shear the moss and it grows back, given time and green ground.', 'Look in Hollowwood.'),
    'hollow_stag': ('Hollow Stag', 'A stag with antlers like moonlit glass. It bolts from anyone who walks; it lets the patient come close.', 'Creep through Hollowwood.'),
    'clockwork_sentry': ('Clockwork Sentry', 'A Bureau construct still guarding whatever it was wound to guard. It ticks before it fires.', 'Cross the Ashen Moor at night.'),
    'rule_wraith': ('Rule-Wraith', 'It feeds on rules. Its touch steals one of your equipped Edicts for a while. Dawn burns it away.', 'Walk the Shardfields after dark.'),
    'wisp': ('Wisp', 'A fen light. Some only flee. Some lead you to something left behind, if you follow.', 'Follow a light in the Gloamfen.'),
    'ashen_husk': ('Ashen Husk', 'What the moor\'s old fires left standing. It still smoulders: its touch catches, and it bursts into embers when it falls. Water hurts it.', 'Cross the Ashen Moor or the Ember Steppe.'),
    'shardling': ('Shardling', 'A crawling cluster of living crystal. It leaps, and when it breaks it breaks into smaller ones that keep coming.', 'Walk the Shardfields after dark, or the spires.'),
    'bog_lurker': ('Bog Lurker', 'The fen\'s patient mouth. It lies still with only its eyes above the water, then lunges and drags its catch back toward the mud.', 'Watch the shallows of the Gloamfen.'),
    'echo_stalker': ('Echo Stalker', 'Something that walks a step behind the world. It steps out of its own echo at your back, and its touch leaves you slow. Daylight thins it.', 'Walk the Gloomwald by night.'),
    'gearhound': ('Gearhound', 'A Bureau tracking-engine shaped like a hound. They hunt in packs, by day as well as night; hurt one and the pack turns on you.', 'Cross the canyons and the dunes.'),
    'rime_wight': ('Rime Wight', 'A spirit of the white wastes, crowned in icicles. It keeps its distance and breathes a line of frost that freezes.', 'Brave the Whitewaste.'),
    'skywhale': ('Skywhale', 'Whales that swim the sky, glowing faintly along the belly. They sing to each other across the valleys. Nothing can hurt them.', 'Look up, from high ground.'),
    'glimmerfin': ('Glimmerfin', 'Little fish with a lit stripe down each side. Their schools turn rivers into slow stars. They fit in a bucket.', 'Look into the Silverrun at night.'),
    'emberfox': ('Emberfox', 'A fox whose brush never quite stops smouldering. Shy of people, fond of sweet berries.', 'Crouch near the Ember Steppe\'s grass.'),
    'rimehorn': ('Rimehorn', 'The shaggy, ice-horned herd beast of the Whitewaste. Milk it, or shear it for a thick white fleece that grows back.', 'Follow the herds across the snow.'),
    'petal_sprite': ('Petal Sprite', 'A spark of the blossom groves no bigger than a hand, with petals for wings. They chime to each other as they drift.', 'Sit quietly in Petalfall.'),
    'mirror_ray': ('Mirror Ray', 'A great flat ray whose back holds the sky like a mirror. Now and then it leaps clear of the sea.', 'Sail the Mirror Sea.'),
}
for k, (name, desc, hint) in CREATURES.items():
    put(f'entity.afterworld.{k}', name)
    put(f'codex.afterworld.creature.{k}', name)
    put(f'codex.afterworld.creature.{k}.desc', desc)
    put(f'codex.afterworld.creature.{k}.hint', hint)
put('message.afterworld.crow_full', 'The crow has nothing more to tell you today.')
put('message.afterworld.wraith_steals', 'The Rule-Wraith steals %s for a while.')

# Title cards.
put('title.afterworld.settlement', 'A settlement under %s')
for k, v in (('bastion', 'Seat of the Crown'), ('observatory', "The Cartographers' Guild measures the sky"), ('mausoleum', 'Where the Morrow Court keeps its names'),
             ('containment', 'Property of the Bureau of Impossible Things'), ('archive', 'Of things that almost happened'),
             ('cathedral', 'Somewhere inside, a bell is still rung'), ('vault', 'Beneath the throne of seconds'),
             ('unremembered', 'A village no one remembers'), ('anomaly', 'Here, a rule has come loose'), ('lair', 'Something has made its den here'),
             ('shrine', 'A waypoint on the Pilgrim\'s road'), ('cache', 'Something was left here'), ('caravan', 'A caravan in trouble'), ('roadstop', 'The road pauses here')):
    put(f'title.afterworld.site.{k}', v)
for k, v in (('pilgrim', 'Who Walks Without Feet'), ('bell_ringer', 'Keeper of the Hollow Cathedral'), ('king_of_seconds', 'Sovereign of the Vault')):
    put(f'boss.afterworld.{k}.epithet', v)
# The great actors' acts: each threshold of health begins the next one.
for k, (p2, p3) in {'bell_ringer': ('The Second Toll', 'The Last Toll'), 'king_of_seconds': ('The Hour Turns', 'Stopped Time'),
                    'pilgrim': ('The Road Remembers', 'Ascension')}.items():
    put(f'boss.afterworld.{k}.phase2', p2)
    put(f'boss.afterworld.{k}.phase3', p3)
put('message.afterworld.pilgrim_road', 'Find the road of light!')

# Journal facing pages.
for k, v in (('rumours', 'Every rumour points somewhere. Not every rumour points somewhere true.'),
             ('chronicle', 'The world keeps its own account of what happened. So should you.'),
             ('world', 'Four powers, one road, and a great many debts.'),
             ('codex', 'What you have learned about how this world bends.')):
    put(f'journal.afterworld.quote.{k}', v)
put('journal.afterworld.stat.leads', '%s open leads')
put('journal.afterworld.stat.heard', '%s rumours heard')
put('journal.afterworld.stat.pages', '%s pages of public record')
put('journal.afterworld.stat.legend', '%s lines of your legend')
put('journal.afterworld.stat.missing', '%s pages missing')
put('journal.afterworld.stat.codex', '%s entries')
put('journal.afterworld.wardrobe', 'Wardrobe')
put('journal.afterworld.wardrobe_tip', 'Change your garb and its colours.')

# The Edict wheel.
put('key.afterworld.wheel', 'Edict Wheel (hold)')
put('screen.afterworld.wheel', 'Edict Wheel')
put('wheel.afterworld.empty', 'No Edicts bound yet.')
put('wheel.afterworld.hint', 'Point at a rule and let go')
put('wheel.afterworld.ready', 'Ready')
put('wheel.afterworld.cooldown', 'Recovering: %ss')
put('wheel.afterworld.silenced', 'Silenced')

# Restoring the Chronicle.
put('journal.afterworld.restore', 'Restore with 3 pages (you carry %s)')
put('journal.afterworld.restore_tip', 'Chronicle Pages are found in old archives. Three restore a torn page, and anything torn with it.')
put('message.afterworld.restore_need', 'You need %s Chronicle Pages to restore this.')

# The Path: chapters, objectives, the tracker and its marker.
put('quest.afterworld.prologue', 'Prologue')
put('quest.afterworld.chapter.n', 'Chapter %s')
for i, n in enumerate(['I', 'II', 'III', 'IV', 'V', 'VI'], 1):
    put(f'quest.afterworld.numeral.{i}', n)
put('quest.afterworld.chapter.prologue', 'The Waystone Camp')
put('quest.afterworld.chapter.listening', 'The Listening World')
put('quest.afterworld.chapter.three', 'The Three Who Wait')
put('quest.afterworld.chapter.wardens', 'The Five Wardens')
put('quest.afterworld.finished', 'The Path Walked')
put('quest.afterworld.finished.sub', 'The world is moving again.')
for k, v in (
        ('meet_isolde', 'Speak with Warden Isolde'),
        ('attune_camp', "Touch the camp's waystone"),
        ('cast', 'Cast the Edict within you'),
        ('meet_marta', 'Find Marta at the forge'),
        ('dodge', 'Roll clear of a blow (Left Alt)'),
        ('parry', 'Parry a blow as it lands'),
        ('journal', 'Open your Expedition Journal'),
        ('meet_bram', 'Visit Quartermaster Bram'),
        ('trade', 'Trade at a stall: sell, then buy'),
        ('report_prologue', 'Return to Warden Isolde'),
        ('hunt', 'Thin the wilds: defeat hostile creatures'),
        ('anomaly', 'Bring a loose Edict to heel'),
        ('shrine', "Find a shrine on the Pilgrim's road"),
        ('waystone', "Touch a landmark's waystone"),
        ('contract', "Finish a notice from the Warden's Board"),
        ('report_listening', 'Tell Isolde what you found'),
        ('bell', 'Silence the Bell-Ringer of the Hollow Cathedral'),
        ('king', 'Face the King of Seconds in his vault'),
        ('pilgrim', "Bring the Pilgrim's walk to an end"),
        ('report_three', 'Return to Warden Isolde'),
        ('shogun', 'Lay the Ashen Shogun to rest'),
        ('lich', 'End the Lich who will not stay dead'),
        ('colossus', 'Bring down the Colossus of the white waste'),
        ('sentinel', 'Wake, and best, the Dune Sentinel'),
        ('warden', 'Stand before the Gilded Warden'),
        ('report_wardens', 'Return to Warden Isolde')):
    put(f'quest.afterworld.obj.{k}', v)
put('notice.afterworld.objective_done', 'Done: %s')
put('notice.afterworld.objective_already', 'Already done: %s')
put('notice.afterworld.marked', '%s marked your map: %s')
put('notice.afterworld.mark_reached', 'You found it: %s')
put('notice.afterworld.attuned', 'Waystone touched: %s')
put('message.afterworld.mark_none', 'Nowhere like that is close enough to mark.')
put('message.afterworld.travel_wait', 'The waystone is still humming. Try again in %ss.')
put('message.afterworld.travel_dimension', 'A waystone only reaches across its own world.')
put('message.afterworld.travel_gone', 'The waystone at %s is gone.')
put('hud.afterworld.talk', '[%s]  Talk to %s')
put('hud.afterworld.quest.hint', '%s  Journal     %s  Next target')
put('hud.afterworld.quest.here', 'Here')
put('hud.afterworld.quest.elsewhere', 'In another world')
put('dialogue.afterworld.continue', '▸ Continue')
put('dialogue.afterworld.farewell', '▸ Farewell')
put('screen.afterworld.waystones', 'Waystones')
put('screen.afterworld.waystones.hint', 'Choose a waystone to travel to.')
put('screen.afterworld.waystones.wait', 'Still humming: %ss')
put('screen.afterworld.waystones.none', "You haven't touched any other waystones yet.")
put('screen.afterworld.waystones.here', 'You are here')
put('screen.afterworld.waystones.elsewhere', 'In another world')
put('key.afterworld.focus', 'Next Objective Marker')
put('block.afterworld.waystone', 'Waystone')
put('block.afterworld.waystone.desc', 'Touch it to remember it. Use any waystone you know to travel to another.')

# The people.
PEOPLE = {
    'isolde': ('Warden Isolde', 'Keeps the Waystone Camp, and the travellers who wake there. She hears the waystones hum.'),
    'bram': ('Quartermaster Bram', 'Feeds, lights and stitches the camp together, and buys the strange parts beasts leave behind.'),
    'wren': ('Wren Halloway', "A Guild cartographer who maps the places that won't sit still. She will mark places on your map."),
    'marta': ('Marta Ironvein', 'Smith to the Wardens. Brings beasts’ parts and emeralds to her forge, and she makes weapons of them.'),
    'selene': ('Stargazer Selene', 'Keeps the Moon Gazebo and counts the stars that fall. One fewer, every night.'),
    'odo': ('Captain Odo Brassmere', 'Master of the Wayfarer, the only airship in the sky, moored for repairs.'),
    'mab': ('Fen-Keeper Mab', 'The witch of the black water. Brews before fights, never after.'),
    'torvald': ('Torvald the Hunter', 'Hunts what hunts the valley, and warns of something tall in the frozen peaks.'),
    'ines': ('Sister Ines', 'Of the Morrow Court. Tends the drowned chapel, and the wounds of those who pass it.'),
    'rashid': ('Rashid of the Sands', 'Master of the caravan at the Hourglass Oasis, where the water is free.'),
}
put('journal.afterworld.codex.people', 'People')
for k, (n, d) in PEOPLE.items():
    put(f'codex.afterworld.people.{k}', n)
    put(f'codex.afterworld.people.{k}.desc', d)
    put(f'codex.afterworld.people.{k}.hint', 'Someone worth meeting.')

import sys
sys.path.insert(0, HERE)
import lang_v2
lang_v2.add(put)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, 'w', encoding='utf-8') as f:
    json.dump(dict(sorted(L.items())), f, indent=2, ensure_ascii=False)
    f.write('\n')
print(len(L), 'lang entries')

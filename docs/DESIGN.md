# Afterworld 2.0 — Design

Afterworld is an open-world action RPG that uses Minecraft as its engine. The world still
feels like Minecraft: you build, mine and wander. Over that it adds a living economy, a story
with real mechanics, powers that change how you fight and move, and a reason to explore every biome.

Design pillars:

1. **Every biome is worth entering.** Each has structures, people, puzzles and loot of its own.
2. **Power you can feel.** There are four layers of power, each obtained differently:
   - Edicts: tactical skills.
   - Disciplines: your class, with a signature move and a skill tree.
   - Aspects: ultimates taken from the old powers.
   - Gifts: traversal abilities.
3. **Quests are things you do, not chores.** Every questline has at least one mechanic
   that isn't fighting: a puzzle, a minigame, an investigation, a race or a choice.
4. **Money moves.** Prices respond to supply, distance and reputation. Trade routes,
   contracts, a bank and haggling make Marks worth chasing.
5. **Cinematic but playable.** Cutscenes are short. Camera work, VFX and sound carry the weight.

---

## 1. Progression

### Renown (character level 1–30)
- **Earned from:** quests (the most), discoveries (the first visit to a structure or landmark),
  bosses, elite kills and contracts.
- **Each level gives:**
  - 1 Insight point, spent in your Discipline's skill tree.
  - Every 5 levels, +1 heart and +2% damage.
  - Some levels unlock shop tiers, Edict ranks and harder contracts.
- **Shown in:** the Journal header, and a gold banner on the HUD when you level up.

### Edicts (tactical skills — the "runes")
There are ten Edicts, as before. All are stronger now, and each has three Ranks.
- **Ranking up:** costs Edict Shards plus Marks, at any waystone or in the Journal.
  - Rank II: cooldown −15% and power ×1.3.
  - Rank III: cooldown −30%, power ×1.6, and a capstone behaviour.
- **Capstones (Rank III):**
  - **Return:** the thrown weapon ricochets between up to 6 targets and bursts in a shockwave when it comes back.
  - **Echoes:** the afterimage lingers 10 s, mirrors your attacks at 75%, and repeats your next two Edicts.
  - **Borrowed Time:** the moment you leave becomes a time-bomb (heavy damage), and every cooldown you spent is refunded.
  - **Weightlessness:** the rift drags enemies to its eye, then slams them down for double fall damage.
  - **Hunger:** up to 5 echoes. Echoes hit at half their host's strength and explode when they fade.
  - **Hospitality:** guests fight for you for 20 s instead of only standing down.
  - **Trespass:** works in mid-air (up and down too), and leaves a rift wake that cuts whatever follows.
  - **Stillness:** creatures freeze completely for 3 s. Damage dealt in that window lands at the end ×1.5.
  - **Witness:** marked creatures take +25% damage for 15 s. Hidden chests and ores within 48 blocks shine.
  - **Forgotten:** your first strike from hiding deals ×3 and keeps you hidden for one more second.

### Disciplines (class) — signature moves rebuilt, plus a skill tree
| Discipline | Resource | Signature (new) | Passive |
|---|---|---|---|
| Vanguard | Grit (taking or dealing melee hits) | **Unbroken**: a leap and slam (AoE knock-up, 10 + 1.5×Renown damage), then 5 s of −50% damage taken and taunt | +2 hearts. Parry staggers. **Last Word** is now passive: once every 3 min, a killing blow leaves you at 1 HP with a shockwave |
| Wayfarer | Momentum (sprinting, dodging) | **Wayfarer's Gambit**: blink-strike up to 5 enemies within 12 blocks (×1.6 weapon damage each), then return to the start. Hold the key instead for **Wrong Turn** (mark and recall) | +10% speed, softer falls, +1 extra dodge |
| Arcanist | Resonance gain ×1.25 | **Rule Collision**: fuse the first two Edicts into one cast at ×2 power, with a fusion effect set by their kinds. Refunds half their cooldowns | +1 Edict slot. Cooldowns −25% |
| Keeper | Favour (kills while summons live) | **Call the Pack**: 3 spectral wolves for 30 s (strong, they taunt, they heal you when they bite). There is no longer a debt | Summons last 50% longer. Pacts can't break by accident |

**Skill trees:** three branches of four nodes per Discipline, bought with Insight. Each branch
deepens one fantasy. For Vanguard, for example:
- **Bulwark:** shield, parry and armour.
- **Breaker:** heavy weapons and stagger.
- **Warlord:** banners, taunts and team buffs.

### Aspects (ultimates)
These are powers taken from the old powers. You equip one at a time. They charge a Resonance
meter from 0 to 100:
- +1 per 2 damage dealt
- +2 per Edict cast
- +3 per kill
- +6 per perfect parry or perfect dodge

Activating one plays a short camera kick, a burst of slow motion and a title. Each Aspect is
obtained in its own way:

| Aspect | From | Obtained by | Effect |
|---|---|---|---|
| Requiem Toll | The Bell-Ringer | Speaking its true name (from the ledger investigation), or defeating it | A spectral bell falls: 18 damage in 8 blocks; silences and stuns for 4 s |
| The Stopped Hour | The King of Seconds | Accepting his bargain, or breaking every escapement and defeating him | Time stops for everything but you for 5 s |
| Footsteps of Stone | The Pilgrim | Only by bringing it peace (mending all four shrines) | You become a stone colossus for 12 s: scale ×1.6, armour +12, quake stomps |
| Hundred Lanterns | The Ashen Shogun | Winning the Duel of Lanterns (a parry minigame) to earn the right to challenge him, then defeating him | Dash through up to 8 enemies in ghostfire cuts, then they burn |
| Red Dominion | The Crimson Lich | Breaking its three phylacteries in three crypts, then defeating it | Raise 4 crimson knights for 25 s, with a lifesteal aura |
| White Winter | The Rimeback Colossus | Tracking it through the Whitewaste (a tracking minigame) and defeating it | A 12-block blizzard dome for 10 s: slows, freezes and chips |
| Chains of the Dune | The Dune Sentinel | Waking it with the Sun-Dial (a light-mirror puzzle) and defeating it | A sand vortex pulls enemies in while floating stone fists crush them |
| Glyph of Judgment | The Gilded Warden | Setting the five Glyph Tablets (one in each of five biome structures) into its gate, then defeating it | A golden dome reflects projectiles for 8 s, then judgment beams strike every enemy |

### Gifts (traversal) and baseline movement
- **Baseline movement, for everyone:**
  - **Stamina:** a wheel near the crosshair. It drains on dodge, slide, mantle, climb and glide.
  - **Dodge roll:** default key Left Alt. 6 ticks of invulnerability.
  - **Slide:** crouch while sprinting.
  - **Mantle:** automatic, on ledges up to 2.5 blocks.
  - **Wall-jump:** jump against a wall while in the air.
- **Gifts, earned through quests:**
  - **Moth-silk Glider:** from Wren's *Uncharted Edges*. Hold jump while falling; vents and campfires give lift.
  - **Wayfarer's Hook:** from Odo's *Repairs for the Wayfarer*. A grapple to blocks within 32; it also yanks small creatures.
  - **Featherstep Boots:** from Selene's *The Missing Stars*. A double jump.
  - **Climbing Claws:** from Torvald's *The White Hunt*. Climb any wall, using stamina.
  - **Mire-walk Charm:** from Mab's *A Brew for the Bog*. Walk on water and on cooling lava for 8 s.
  - **Skyward Bell:** from Ines' *The Drowned Bell*. Launches you 15 blocks up, with a 20 s cooldown.
  - **Wayfarer's Whistle:** from Torvald after the hunt. Summons your tamed Duskstrider stag mount.

---

## 2. Economy

- **Currency:** Marks, kept in a wallet rather than the inventory.
  - Coins drop as items worth 1, 10 or 100 (Copper Mark, Silver Mark and Gold Crown) and go straight into the wallet when picked up.
  - The wallet shows on the HUD whenever it changes, and in the inventory.
- **Death:** you drop 10% of the Marks you carry as a *Lost Purse* where you fell, and can recover it.
  Banked Marks are safe.
- **The bank (Guild Ledger,** at Wren's map tent): deposit and withdraw. It pays 1% a day, up to 500 a day.
- **Item values:** every item has a base value. Mod items are hand-set; vanilla items come from a table.
- **Shops** use a custom screen with Buy, Sell and Buyback tabs, categories, item details, and
  quantity steps of 1, 8 and a stack. Prices react:
  - Buy price = value × merchant markup × regional demand × scarcity × reputation × events.
  - Sell price = value × merchant rate × regional demand × saturation × reputation.
  - Saturation: selling the same thing again lowers its price. It recovers by half each day.
  - Scarcity: low stock raises prices. Stock restocks daily, and a caravan's arrival restocks instantly.
  - Reputation: Trusted −10%, Honoured −20%, Distrusted +15%. A Hostile merchant won't trade.
- **Haggling:** you get one try per item a day. You pick a tactic:
  - Flatter, Hard Bargain or Name a Rival.
  - Each merchant has a temperament that favours one tactic.
  - Success gives −15 to −30%. Failure gives +10% for the day.
- **Trade goods:** eight goods that are cheap where they're made and dear far away.
  - Each landmark keeper produces one good and wants others.
  - Carrying a crate from the Oasis to the Lodge can triple your money.

  | Good | Cheap at | Valued at |
  |---|---|---|
  | Spice Crate | Oasis | Lodge, Duskpine |
  | Frost Silk | Icyshroom, Glimmerfrost | Oasis, Ember Steppe |
  | Ember Glass | Ember Steppe | Whitewaste, Lodge |
  | Bog Amber | Fen-Keeper's House | Moon Gazebo, Pale Reach |
  | Starlight Ink | Moon Gazebo | Airship, Rustscar |
  | Sky Ambergris | Airship | Chapel, Oasis |
  | Clockwork Curio | Vault region | Petalfall, Fen |
  | Mirror Salt | Coasts | Ashen Moor, Hollowwood |

- **The Warden's Board** (at the camp and in settlements): three to five rotating contracts.
  - **Bounty:** a named elite, at a place.
  - **Delivery:** a trade good, to a keeper.
  - **Survey:** stand at three survey stones.
  - **Gathering:** bring N of something.
  - **Escort:** see a caravan through.
  - Contracts pay Marks, Renown and faction reputation.

---

## 3. Items (100+)

Every item does something of its own. Leveled items have at most three tiers (I, II and III); each
tier has its own texture and a stronger art.

### Weapons (26)
Tiers come from weapon renown (kills and boss hits). Each evolution needs Marta's forge and
the smithing minigame.

| Weapon | Kind | Art (right-click or charged) | Tiers |
|---|---|---|---|
| Emberbrand | Sword | Flame arc; III leaves a burning trail | 3 |
| Shardsong Glaive | Glaive | Crystal shards fan out | 3 |
| Lurker's Hook | Sickle-hook | Hooks and drags | 3 |
| Echo Edge | Sword | Strikes repeat a beat later | 3 |
| Gearspring Maul | Hammer | Spring-loaded launch slam | 3 |
| Rimefang Spear | Spear | Thrust that freezes; III throws an ice javelin | 3 |
| Moonsilver Rapier | Rapier | Riposte: parry, then a ×2 counter | 3 |
| Duskpine Longbow | Bow | Moonshot: a homing charged arrow | 3 |
| Bog-iron Crossbow | Crossbow | Venom bolts that spread | 2 |
| Wayfarer's Fang | Dagger | Backstab ×2.5, fast | 2 |
| Mirrorglass Chakram | Thrown | Boomerangs, hits on the way back | 2 |
| Starweaver's Wand | Wand | Star bolts with 6 recharging charges | 3 |
| Ashen Warbanner | Polearm | Plants a banner (an ally buff zone) | 2 |
| Sporelash Whip | Whip | 6-block reach, stun spores | 2 |
| Brassmere Repeater | Flintlock | Three shots, then a reload; uses powder | 2 |
| Skyhook Harpoon | Harpoon | Spears a target and reels it or you in | 2 |
| Thunderdrum Gauntlets | Fists | 3-hit combo; the third is a shockwave | 2 |
| Bellhammer | Boss (Bell-Ringer) | Toll shockwave that silences | 1 |
| Second Hand | Boss (King) | Rewinds your last hit's damage onto the target | 1 |
| Pilgrim's Staff | Boss (Pilgrim) | Stone road of light | 1 |
| Hitodama | Boss (Shogun) | Ghost-lantern slashes | 1 |
| Crimson Requiem | Boss (Lich) | Scythe sweep with lifesteal | 1 |
| Rimeback Cleaver | Boss (Colossus) | Avalanche cleave | 1 |
| Dunewalker's Chain | Boss (Sentinel) | Chain-blade whip that pulls | 1 |
| Glyphwarden Aegis | Boss (Warden) | A shield whose bash fires a glyph beam | 1 |
| Apologetic Blade | Relic weapon | (existing) | 1 |

### Armour (6 sets × 4 pieces = 24)
| Set | Look | Set bonus (4 pieces) |
|---|---|---|
| Warden's Regalia | Teal and gold | Aspects charge 25% faster |
| Duskpine Ranger | Green leather | +20% bow damage; sprinting while crouched hides you |
| Emberforged Plate | Iron and glowing seams | Fire immunity; attackers burn |
| Rimeguard | Fur and ice | Immune to freezing; attackers slow |
| Starweave Robes | Violet and stars | Edict cooldowns −20% |
| Gilded Glyphmail | Gold glyphs | Reflects 20% of melee; +2 toughness |

### Relics (20)
These go in three relic slots, opened from the Journal.
1. Unfinished Compass *(existing)*
2. Hungry Lantern *(existing)*
3. Clockwork Heart *(existing)*
4. Thimble of Borrowed Rain *(existing)*
5. Ring of Second Breath: survive a lethal hit at 30% health; 5 min cooldown.
6. Amulet of Quiet Steps: creatures notice you at half range while you crouch.
7. Emberheart Pendant: attacks have a 15% chance to ignite.
8. Rimecore Charm: crits freeze for 1 s.
9. Glimmerfin Scale: water breathing; +40% swim speed.
10. Moth-lantern Locket: night vision; moths lead you to the nearest unopened chest.
11. Pilgrim's Bead: stamina costs −20%.
12. Wren's Lucky Sextant: undiscovered structures within 160 blocks show on the tracker.
13. Bram's Purse Knot: +15% Marks from creatures.
14. Gear-sprung Anklet: +1 step height; jump +30%.
15. Echo Shell: every 4th arrow fires twice.
16. Lich's Finger Bone: 6% lifesteal.
17. Shogun's Tsuba: the perfect-dodge and parry window is 50% longer.
18. Sentinel's Sandglass: below 25% health, slows the world around you for 3 s; 2 min cooldown.
19. Warden's Glyph Ring: +2 armour; +15% Resonance.
20. Starseed: one Edict can hold two charges.

### Gifts and gadgets (7)
Glider, Hook, Featherstep Boots, Climbing Claws, Mire-walk Charm, Skyward Bell, Wayfarer's Whistle.

### Consumables (18)
- **Elixirs:**
  - Elixir of Stamina: +50% max stamina for 3 min.
  - Draught of Resonance: +50 Resonance.
  - Phial of Second Wind: regeneration III for 6 s.
- **Weapon oils:** Ember Oil, Frostbite Oil, Lurker Venom. Coat the held weapon for 60 hits.
- **Bombs (thrown):** Smoke Bomb (break aggro and go invisible 4 s), Frost Bomb, Thunder Bomb.
- **Scrolls:**
  - Waystone Shard: return to camp, single use.
  - Survey Scroll: marks the nearest unvisited structure.
- **Lockpick:** for locked chests (the lockpicking minigame). It can break.
- **Meals (the cooking minigame):**
  - Hearty Stew: max health +4 for 5 min.
  - Moonberry Tart: night vision and speed.
  - Spiced Dune Kebab: haste and fire resistance.
  - Glimmerfin Sushi: water breathing and +stamina regeneration.
  - Ember Pepper: warmth (cold immunity) and strength I.
  - Starlight Tea: Edict cooldowns −10% for 5 min.

### Materials (22)
- **Existing:** Ember Core, Living Crystal, Lurker Fang, Echo Essence, Gear Spring, Rime Core,
  Anomaly Residue, Clockwork Fragment, Moth Dust, Grave Wax, Bell Fragment.
- **New:** Moonsilver Ore, Moonsilver Ingot, Glimmer Silk, Bog Amber, Glyph Gold, Rime Pearl,
  Sandsteel Ingot, Lich Bone, Shogun Steel, Edict Shard, Star Fragment.

### Trade goods (8), currency (3), quest items (10) and Aspect hearts (8)
- **Trade goods:** see the table under Economy.
- **Currency:** Copper Mark, Silver Mark, Gold Crown.
- **Quest items:** Star Chart Fragment, Glyph Tablet, Phylactery Shard, Challenge Lantern,
  Sun-Dial Key, Crypt Key, Bounty Writ, Bell Rope, Survey Sextant, Caravan Seal.
- **Aspect hearts:** one per Aspect. Using one binds that Aspect.

**Total: about 150 items.**

---

## 4. Places

Every land biome gets at least two structures of its own, on top of the shared ones (wayside
shrines, camps and ruins in that biome's materials).
- **Landmarks** keep their keepers and waystones.
- **Structures** carry loot, a lock (lockpicking), a puzzle or a person.

| Biome | Structures |
|---|---|
| Ashen Moor | Cinder Watchtower (cultist camp, elite); Ember Kiln Ruins (relight four kilns to open the vault) |
| Hollowwood | Hollow Giant *(existing)*; Hermit's Bough (a treehouse, with a hermit's quest) |
| Shardfields | Crystal Observatory (light-mirror puzzle); Shattered Caravan (wreck, shardlings, lockbox) |
| Gloamfen | Stilt House *(landmark)*; Sunken Bog Temple (flooded halls, lurkers, a Glyph Tablet) |
| Vesper Downs | Windmill Farmstead (farmer NPC, harvest contract); Barrow Mound (Crypt I, Phylactery I) |
| Mossmere Wood | Druid's Ring (rune-trace puzzle, Edict Shard); Woodcutter's Lodge (NPC) |
| Pale Reach | Ossuary Gate (Crypt II, Phylactery II); Bone Orchard (a chapel garden) |
| Gloomwald | Weaver's Hollow (a web-choked cave mouth, an elite spider-thing); Hanged Lanterns grove (spirit puzzle) |
| Petalfall | Lantern Dojo (the Shogun's trial); Tea House (tea-master NPC, the tea ceremony minigame) |
| Frostmere Pines | Trapper's Cabin (NPC); Frozen Watch Post (lockbox, a view) |
| Whitewaste | Colossus Graveyard (bones; the Rimeback hunt starts here); Snowbound Camp (NPC traders) |
| Rimeglass Spires | Rime Cavern (crystal halls, Rime Pearls); Frozen Mage Tower (Glyph Tablet) |
| Skyspire Peaks | Peak Monastery (parkour trial, monks); Great Nest (gliding updraft, loot) |
| Windworn Highlands | Wind Harp (music puzzle); Gilded Vault *(Warden lair)* |
| Ember Steppe | Nomad Yurts (trader camp); Starfall Crater (Star Fragments) |
| Hourglass Dunes | Oasis *(landmark)*; Sun Temple *(Sentinel lair, the Sun-Dial)*; Buried Obelisk (Glyph Tablet) |
| Rustscar Canyons | Abandoned Mine (minecarts, ore, elite); Canyon Bridge Outpost |
| Verdant Tangle | Overgrown Ziggurat (traps, Glyph Tablet); Canopy Village (treetop NPC huts) |
| Sporeveil | Alchemist's Spore Lab (NPC, a brewing contract); Glowcap Grotto |
| Duskpine Weald | Ranger Outpost (NPC, archery contest); Moonlit Clearing (fairy ring, Glyph Tablet) |
| Glimmerfrost | Icyshroom *(landmark)*; Aurora Shrine (constellation shard) |
| Coasts and seas | Shipwrecks (three kinds), Lighthouse, Drowned Shrine |
| Caves | Echo Shrine (echo_hollows), Root Throne (rootways) |
| Pale Reach / Gloomwald | Crimson Necropolis *(Lich lair, Crypt III)* |

---

## 5. Quests

### The main story (the Path)
- **Prologue — The Waystone Camp:**
  1. Meet Isolde.
  2. Touch the waystone.
  3. Cast your Edict.
  4. The training yard: perform a dodge and a parry against the dummy.
  5. Open the Journal.
  6. Bram: sell something and buy something (introduces the wallet).
  7. Report to Isolde.
- **Chapter I — The Listening World:**
  - Take and finish a contract from the Warden's Board.
  - Bind a loose Edict. The anomaly now demands a rune-binding sequence.
  - Find a Pilgrim's shrine.
  - Touch a landmark's waystone.
  - Report.
- **Chapter II — The Three Who Wait:** the Bell-Ringer (its name, from the ledger), the King
  (the escapements, or a bargain) and the Pilgrim (mend the shrines). Then report.
- **Chapter III — Wardens of the Five Lands:** the five new powers, each with its own trial
  (above). Then report.
- **Epilogue:** the world moves again. The Wardens' last gift comes with a cutscene at the camp.

### Side questlines (one per keeper)
| Keeper | Questline | Mechanic | Reward |
|---|---|---|---|
| Selene | The Missing Stars | Find three Star Chart fragments, then at night turn the gazebo's star dials to match the constellation | Featherstep Boots, Moonsilver |
| Odo | Repairs for the Wayfarer | Gather parts, then a glider ring race round the airship against the clock | Wayfarer's Hook; a flight to a far landmark (cutscene) |
| Mab | A Brew for the Bog | Brewing: add ingredients at the right colour and stir in rhythm | Mire-walk Charm, recipes |
| Torvald | The White Hunt | Tracking footprints through snow, then an archery contest | Climbing Claws, Duskstrider stag and whistle |
| Ines | The Drowned Bell | Bell-melody puzzle: ring the chapel bells in the order the ghost hums | Skyward Bell |
| Rashid | The Silent Caravan | Escort with choices (the route, the sandstorm, a bribe or a fight) | Caravan Seal (−15% at all Guild merchants), the spice route |
| Wren | Uncharted Edges | Sextant surveys: align the sight on a peak from three survey stones | Moth-silk Glider |
| Bram | A Proper Meal | The cooking minigame, and foraging | Bram's Purse Knot, recipes |
| Marta | Forged in Strange Fire | The smithing rhythm minigame | Weapon evolution unlocked |

### Minigames
- Lockpicking
- Rune-binding (a Simon sequence on pillars)
- Star dials
- Bell melody
- Light mirrors
- Brewing and cooking
- Smithing rhythm
- Fishing (keep the catch in the bar)
- Ring race
- Tracking
- Duel of parries
- Archery contest
- Sextant survey
- Haggling
- Tea ceremony (a pour-timing variant of brewing)

---

## 6. Bosses

Bosses are original designs inspired by the references. Each gets a lair, a cutscene intro,
three phases, its own mechanics, an Aspect, a weapon and voice lines where it speaks.

- **The Ashen Shogun:** a skeletal samurai in a horned kabuto, with a naginata-staff and a hanging paper lantern.
  - Phase 1: iaido draw cuts and ghostfire orbs.
  - Phase 2: splits into three ash-copies. Only the real one casts a lantern shadow.
  - Phase 3: the Hundred Lanterns detonate in sequence. A final parry exchange staggers him.
- **The Crimson Lich:** a horned skeletal king in a red cape, crowned, with a bone staff.
  - Phase 1: blood bolts and skeleton risings.
  - Phase 2: he teleports between three obelisks that heal him; break them.
  - Phase 3: Red Dominion. Blood mist fills the hall and crimson knights rise.
- **The Rimeback Colossus:** a towering blue yeti with curled horns and crystals on its back.
  - Phase 1: slams and thrown boulders.
  - Phase 2: a blizzard (short sight) and ice spikes.
  - Phase 3: rage charges. The back crystals glow, and shattering them with shots staggers it.
- **The Dune Sentinel:** a sandstone golem whose limbs float, tied to its body by chains, round a glyph core.
  - Phase 1: its fists fly.
  - Phase 2: a sandstorm and quicksand.
  - Phase 3: chain whips across the arena. The core is exposed when the limbs return.
- **The Gilded Warden:** a stone turtle-golem with gold glyph inlay.
  - Phase 1: shell-spin charges and glyph beams.
  - Phase 2: a shell dome reflects projectiles. Strike the glyph pillars.
  - Phase 3: the shell cracks; gold core and beam sweeps.

---

## 7. Cinematics
- **The cutscene system:** the server sends a script; the client plays it with an overridden camera.
  - Each script has camera keyframes (position, look, FOV), easing, letterbox, titles and voice cues.
  - The player is frozen and invulnerable while it plays. Esc skips after the first second.
- **Where cutscenes are used:**
  - Boss intros (6–10 s).
  - Phase changes (2–3 s pull-backs).
  - Aspects (a camera kick about 1 s long, no lock).
  - Story beats: the airship flight and the epilogue.

## 8. Look and feel
- **The Afterworld shader pack** (Iris):
  - Soft shadows, god rays, bloom on emissives, a colour grade per biome.
  - Clear water with a visible floor, high clouds that never clip, warm sunsets and cold moons.
  - Low, Medium and High presets.
- **The Afterworld resource pack:** retextures key blocks, water, the sky and the GUI to match the mod.
- **Item and mob textures** are redone in a modern, outlined, hue-shifted pixel style.

## 9. Order of work
1. Economy, then powers, then movement.
2. Items and textures.
3. Minigames and quests, with the voice script.
4. Structures.
5. Bosses and cinematics.
6. Mob textures.
7. Shaders and the resource pack.
8. Full testing.
9. The modpack (2.0.0).

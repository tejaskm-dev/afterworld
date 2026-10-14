# Afterworld

*The world that plays back.* A Fabric mod and modpack for **Minecraft 26.3** that turns the sandbox
into an action RPG:
- a story in three chapters;
- five Wardens to wake and defeat;
- keepers with questlines and trials;
- a living economy;
- 58 creatures;
- dozens of places to find in every land.

It comes two ways:

- **`Afterworld-Modpack-2.4.0.mrpack`**: the full experience. Afterworld plus its own shader pack
  and texture pack, distant terrain, ambient sound and performance mods, set up to work together.
- **`afterworld-2.4.0.jar`**: just the mod. One file with Fabric API bundled inside it.

## New in 2.4

**From your notes on 2.3:**
- **Voices no longer talk over each other.** One voice at a time. When a conversation opens (as when
  the prologue ends with Warden Isolde), the narrator and the great powers wait; a line a
  conversation cut off is heard again after it, and a chapter's title card waits for the
  conversation to end instead of playing unseen behind it. Lines that arrive together are spoken
  one after the other.
- **The town, fixed and easier to read:**
  - Marta's forge no longer spills lava (its fire glows behind an iron grate), and her quench trough
    no longer floods the street (it is a row of water tubs now).
  - Every door opens onto open ground: no lamp post, fence, shutter or workbench stands in a
    doorway, in the town or anywhere else. Every building in the game is now checked for blocked
    doors and for water or lava that could run loose (three more were fixed: the mill race, the
    Druids' spring and the catacombs' flooded wing).
  - **The Warden's Board is a great notice board** at the edge of the plaza, facing the waystone:
    nine panels of notices between two tall posts, under a gabled roof with a bell on its ridge,
    lanterns and the Wardens' banners. Any panel opens the board.
  - One house in three has a smoking chimney instead of every one.
- **A new quest tracker.** Top right: the chapter on one quiet line, then the objective in hand
  written large with an arrow that turns to point the way and how far it is, then the chapter's
  other objectives, smaller. New objectives slide in with a glowing marker; finished ones tick,
  glow and fade away. The Journal and next-target keys show for a few seconds whenever something
  changes. In the world, the marker floats above a keeper's name instead of on top of it, and when
  the place is behind you an arrow circling the crosshair shows which way to turn.
- **A real skill tree**, opened from the Character screen's Skills tab: 35 skills growing from a root
  in three limbs that fork into branches, with two bridges between them.
  - **Blade** (the duelist and the breaker), **Arcanum** (Arts, Edicts and the Aspect) and
    **Wayfarer** (the runner and the pathfinder).
  - Small nodes give a few percent of damage, attack speed, armour, health, footing, speed, luck,
    stamina, cooldowns or Resonance. The twelve skills you knew are its named nodes.
  - Five keystones at the tips: Duelist's Oath, Bloodthirst, Echoing Will, Second Wind and Scavenger.
  - Learn any node next to one you have. Drag to look around, scroll to zoom, R to centre.
  - More Insight to spend: one for each chapter you finish and two for each Warden's first fall,
    besides one for each Renown level.
- **The Ladder's gate, carved anew:** the Ladder drawn as a tower of its five lands, with its landings
  and echoes marked; cards for your highest floor, the next landing and the next echo (and whose);
  its rules each with its sign; who climbs with you; and bigger buttons.
- **Water that looks like water** with Afterworld Shaders: it takes the biome's colour and darkens
  with depth (clear shallows, deep blue sea), falls and streams no longer flash white, and from
  under the surface it is a teal ceiling with the sky bright through it.
- **Faster.** In the town, the heaviest place in the game, frames went up about 15% and the worst
  frames about 35% in our tests:
  - Afterworld Shaders draws shadows out to 64 blocks by default (Full still reaches 128), and its
    sun rays cost a fraction of what they did.
  - LambDynamicLights has left the pack: it re-lit the world around every moving light. With the
    shaders on, a torch in your hand still lights the way.
  - Fewer smoke plumes over the town.

Towns, buildings and their fixes are made when a world is: the new board and the fixed doors and
fluids appear in new worlds.

## Earlier: 2.3

- Ability effects: blade arcs, shockwaves, beams, sigils and flashes for every Art, Edict, Aspect and
  technique, and the body acts each power out (`abilityEffects` and `swingTrails` in
  `config/afterworld.json`).
- The underground built: four delves (the Sunken Forge, the Glowcap Hollow, the Geode Vault and the
  Drowned Catacombs), each a way in on the surface and a place thirty blocks below, and camps in
  every land's caves.
- Seventeen places rebuilt wider and far more detailed.
- Every line voiced; the character creator's hover sound fixed.

## Earlier: 2.2

- A much bigger world: the Warden lairs stand 4.5 to 8.5 km out, the story's places step outward
  as the story does, and waystones carry you back along roads you have walked.
- The Warden's Ladder: a fifty-floor dungeon of island floors in a starry dark, with landings,
  Rook's stall and an echo of a Warden every tenth floor. One of its gates is always a short walk
  from the town.
- Proving Rings and the Climber's Needle; climbing hand over hand and pulling up over ledges
  (`climbing` and `mantle` in `config/afterworld.json`).
- The Dark Lighthouse rebuilt as a broad tower with a ramp inside and a great lamp to light again.
- Poses for climbing, slides, dodges and glides, and livelier creatures.
- Allure textures in place of Patrix, and screens drawn to match.

## Earlier: 2.1

- Screens and HUD fit every window and GUI scale (set `uiSize` or `uiFollowsGuiScale` in
  `config/afterworld.json`).
- The Waystone Camp became a walled town 160 blocks across, with its keepers spread out where they work.
- The story never gets stuck: anything you did before it was asked for counts at once.
- No two buildings overlap, and nothing is built on the town, the lairs or the great powers' seats.
- Hits sound like what you hit; the Warden's Board; Edict Rank III capstones; Leyla is voiced.

## Installing the modpack with SKLauncher

1. In SKLauncher, choose **Import Modpack** (or "Install from a .mrpack or .zip").
2. Pick `Afterworld-Modpack-2.4.0.mrpack`.
3. SKLauncher creates a Fabric 26.3 instance named Afterworld. It downloads the other mods from
   Modrinth, checking each file's hash, and sets them up.
4. Give the instance at least **6 GB** of memory in its settings.
5. Play. The pack opens straight onto Afterworld's title screen.

**Afterworld Shaders** start switched on.
- They are a light, fast pack made for Afterworld: soft sun shadows, a warm torch glow (the torch in
  your hand lights the way too), a painted sky, swaying plants, living water, fog that melts into
  the sky, sun rays, bloom and a gentle filmic grade.
- Open *Video Settings → Shader Packs → Afterworld Shaders → Settings* to pick a profile: **Lite**
  (no shadows, for slower machines), **Balanced** or **Full**. You can also tune each option there.
- Complementary Reimagined is still in the pack if you prefer it. Choose it from the same Shader
  Packs screen.

**Allure** paints the world's blocks, items, mobs and menus at 16 pixels, bright and cute; the pack
downloads it from Modrinth. **Afterworld Resources** sits under it and fills in what Allure doesn't
paint. Both are on by default.

## Installing just the mod

1. In SKLauncher, add an installation for **Fabric**, Minecraft **26.3**, Fabric Loader **0.19.5 or
   newer**. Start it once and close the game.
2. Open the installation's folder, then its `mods` folder.
3. Copy `afterworld-2.4.0.jar` into `mods` and launch.

Minecraft 26.3 needs Java 25 or newer. SKLauncher normally picks the right Java by itself.

**Dedicated server:** install the Fabric server for 26.3 and put the jar in its `mods` folder.
Players need the mod too.

## What's in it

### The story

- **The awakening:** a cinematic character creator. Choose an origin, a discipline and an Edict,
  then a name and your garb.
- **Chapter One: the Listening World.** A loose rule hums somewhere in the land.
- **Chapter Two: the Three Who Wait.** The Bell-Ringer, the King of Seconds and the Pilgrim.
- **Chapter Three: the Wardens of the Five Lands.** Each Warden keeps a lair, has three acts with a
  boss bar, voice lines and a wind-up you can read before every blow, and gives up an Aspect (an
  ultimate power) the first time it falls:

  | Warden | Lair | How to face it |
  |---|---|---|
  | The Ashen Shogun | The Lantern Dojo (Petalfall) | Win Akane's Duel of Lanterns, then set her challenge lantern on the dojo's heart. |
  | The Crimson Lich | The Crimson Necropolis (Pale Reach, Gloomwald) | Break his three phylacteries (a barrow on the Vesper Downs, an ossuary gate in the Pale Reach, his necropolis), or he rises again. |
  | The Rimeback Colossus | The Rimeback's Den (Whitewaste) | Waiting in the ice. |
  | The Dune Sentinel | The Sun Temple (Hourglass Dunes) | Wake it by striking the court's mirrors in the right order. |
  | The Gilded Warden | The Gilded Vault (Windworn Highlands) | Set the five Glyph Tablets into its golden gate. |

- **The Warden's Ladder:** fifty floors of waves, landings and Warden echoes in a world of their
  own. Its gates stand in the world (one is always near the town).

### Keepers, questlines and trials

The people of the Afterworld have voices (all 239 lines recorded) and their own questlines. Many
questlines end in a trial that plays like a small game:
- **Marta's anvil:** strike while the marker sits in the gold, and your humming weapon wakes to its next form.
- **Bram's stew pot:** keep the heat in the green and add what the broth asks for.
- **Mab's cauldron:** drop each ingredient in on its colour and stir with the bubbles.
- **Mab's next test:** walk across the water and back without a splash.
- **Selene's star dials:** turn the Lost Moth's stars home.
- **Sister Ines's bells:** ring back the drowned apprentice's song.
- **Captain Odo's glide race:** eight rings before the sand runs out.
- **Torvald's hunt:** follow fresh tracks in the snow to the rimehorn alpha, then win his archery contest.
- **Akane's duel:** only a perfect parry counts.
- **Wren's survey:** take steady sextant sightings at three waystones.
- **Rashid's caravan:** find the clues on the old road, deduce where the drivers went, and bring his sister Leyla home from the Buried Obelisk.

### The lands

Thirty-two biomes. Every land has places of its own: about forty sites in all, besides the five
wonders and the landmarks.
- **Ruins and temples:**
  - the Sunken Temple;
  - the Frozen Tower;
  - the Green Ziggurat;
  - the Sun Temple;
  - the Crystal Observatory.
- **Camps and huts:**
  - nomad yurts;
  - a snowbound traders' camp;
  - a ranger palisade;
  - canopy huts;
  - a hermit's treehouse.
- **Crypts:** the Barrow Mound, the Ossuary Gate and the Crimson Necropolis.
- **Natural wonders:**
  - a starfall crater;
  - a colossus graveyard;
  - the Great Nest;
  - a rime cavern;
  - the Glowcap Grotto.
- **Coasts:** the Drowned Shrine, the Dark Lighthouse and a beached wreck.
- **Trials of nerve:** Proving Rings, the Climber's Needle and the Ladder's gates.

Places hold:
- chests of supplies and vaults of treasure, with loot to match their land;
- creatures, and glowing **elite** guardians that carry better spoils;
- notes and carvings that tell their story;
- **seal stones** whose riddles (light the braziers, strike the stones in order, make an offering)
  open their vaults.

A torch in hand now lights any cold campfire or candle. Each place has a title card as you arrive,
and the Codex keeps a page for it.

### Fighting and moving

- **Dodge and parry:** Left Alt dodges while moving and parries while standing still. A perfect
  dodge or parry slows the attacker.
- **Weapon arts and combos:** each weapon has its own art.
- **Aspects:** ultimate powers that charge as you fight. Press C to unleash one and N to switch.
- **Disciplines:** each has a signature technique (tap H) and a second one (hold H).
- **Skills and sets:** a skill tree of 35 skills in three limbs (Blade, Arcanum, Wayfarer), six armour sets with set bonuses, and relics.
- **Moving through the world:** stamina, a slide, real climbing, pulling up over ledges (press
  jump again in mid-air) and wall jumps (back and jump against a wall).
- **Gifts:** glide, double jump, climbing and running on water.
- **Gadgets:** the Wayfarer's Hook, the Skyward Bell, a mount whistle, the sextant and the
  prototype wing.

### The economy

- Merchants buy and sell at prices that move with scarcity.
- Each region values goods differently.
- The Guild Ledger keeps your Marks safe.

## Controls

| Key | Action |
| --- | --- |
| Left Alt | Dodge (moving) / Parry (standing still) |
| C | Unleash your Aspect |
| N | Next Aspect |
| K | Character (Aspects, skills, Edicts, relics) |
| R, V, Z, B | Cast the Edict in slot 1-4 |
| H (tap / hold) | Discipline signature / its second technique |
| X (hold) | Edict wheel |
| J | Expedition Journal |
| G | Next objective marker |

All keys can be changed in Options → Controls → Key Binds. In the modpack, Iris's "reload shaders"
and "toggle shaders" keys are unbound because they share R and K with Afterworld.

## Voices

The keepers and the great powers speak with voices generated for this pack: all 239 lines. The
recordings are in the mod at `assets/afterworld/sounds/va/` (the raw takes, `voice/incoming` and
`voice/split`, stay out of the repository). To replace a line:
1. Drop a new recording named as in `voice/VOICE_SCRIPT.md` into `voice/incoming`.
2. Run `python tools/split_voices.py` (for whole takes), then `python tools/import_voices.py voice/split`.

The script for each line is in `voice/VOICE_SCRIPT.md`. The voices belong to whoever generated
them, under the terms of the service used.

## Performance

- **Afterworld Shaders** are built for speed:
  - one shadow map, sized by profile;
  - no screen-space reflections or volumetric raymarching;
  - bloom from mipmaps.
  Pick **Lite** to drop shadows entirely.
- **Wonders and sites** are stored as compact tiled files (a few KB each). World generation places
  only the part in the chunk being built.
- **World simulation** runs once per in-game day.
- **Per-player checks** run every one to two seconds rather than every tick.

## Building from source

- **The mod:** `./gradlew build`. The jar appears in `build/libs/`.
- **The modpack:** after building the mod, `python3 tools/build_pack.py`. The `.mrpack` appears in `dist/`.
- **Wonders and sites:** `tools/wonders/build.py` (Python 3, numpy and Pillow). Sites live in
  `sites.py`, `sites2.py` and `sites3.py` (the rebuilt ones in `sites5.py`), the delves in
  `delves.py`, and the Ladder's floors and gates in `ladder.py`; `--preview DIR` renders pictures.
  `tools/wonders/walk.py` checks that a player can walk a build from its way in to every seal,
  input and chest and back out, that every door has room on both sides, and that no water or lava
  can run loose (`--static` for those two alone).
- **Window art:** `python3 tools/gen_gui.py` paints the frames, buttons and object windows.
- **Shaders:** the GLSL is in `pack/shaderpack/shaders/{lib,program}`. `python3 tools/gen_shaders.py`
  writes the wrappers and zips the pack.
- **Textures:** `python3 tools/gen_resourcepack.py <vanilla textures dir>` draws the resource pack.
  The mod's own art comes from `gen_items.py`, `gen_mobs.py` (with `mobs_v2.py` and `bosses_v2.py`)
  and `gen_clues.py`.
- **Text and voices:** `gen_lang.py` (with `lang_v2.py`), `dialogue.py`, `split_voices.py` and
  `import_voices.py`.

## Credits

- **Textures:** Allure by p4ncake, downloaded from Modrinth by the modpack and used unchanged. It is
  not part of Afterworld.
- **Sounds:** Kenney's Impact Sounds, RPG Audio and Interface Sounds, and rubberduck's 80 CC0
  Creature SFX (all CC0 1.0), reworked by `tools/gen_sfx.py`. See `sources/sfx/README.md`.

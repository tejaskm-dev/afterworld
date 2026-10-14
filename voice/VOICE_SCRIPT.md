# Afterworld — Voice Script

239 lines, about 22,188 characters of speech, across 20 voices.

## How to record and bring lines in

1. Pick the voice listed for a character: the ElevenLabs library voice, or search Fish Audio with the hint.
2. Generate each line. Keep the **[cues]**: ElevenLabs v3 reads them as audio tags. For Fish Audio, write them in round brackets instead, so `[softly]` becomes `(softly)`.
3. Name each file exactly as given, e.g. `isolde__welcome_0.mp3` (mp3, wav, ogg or flac all work).
4. Put the files in `voice/incoming/` and run `python3 tools/import_voices.py`. It trims silence, levels the volume, applies the character's treatment (the great powers are pitched down and given halls, metal or sand), converts to Ogg and wires the line into the game. Lines you haven't recorded keep their subtitles.
5. `voice/lines.csv` has every line in one table, for batch tools.

Model: **Eleven v3** for anything with cues (most lines); Multilingual v2 is steadier for long calm lines. Generate two or three takes of the important lines and keep the best.

---

## Warden Isolde, keeper of the Waystone Camp

**Who they are:** A warm, wise Irish woman in her forties. Gentle authority; a guide who has seen many travellers come and go. Never rushed.

- **ElevenLabs:** Colleen – Warm Southern Irish Woman — `1OYA2kgM85gF2eGN8HEp`
- **Second choice:** Louise – Neutral Irish – Sincere — `UwtFVYnvYG6hxAbc4I6T`
- **Fish Audio search:** "Irish woman warm storyteller", "gentle Irish narrator"
- **Settings:** Stability 50 · Similarity 80 · Style 15
- **Treatment on import:** room 0.12

| File | Line |
|---|---|
| `isolde__welcome_0` | [softly] There you are. I felt the waystone hum the moment you woke. |
| `isolde__welcome_1` | Easy, now. Everyone wakes a little lost. The Afterworld is like that. It listens before it speaks. |
| `isolde__welcome_2` | I'm Isolde, Warden of this camp. We keep the waystones lit, and we keep travellers like you on your feet. |
| `isolde__where_0` | A world still deciding what it wants to be. Old rules, half forgotten, written into stone and storm. |
| `isolde__where_1` | We call those rules Edicts. A few folk carry one inside them, the way you do. That's why the world noticed you. |
| `isolde__listens_0` | Walk far enough and you'll see. Stones that forget to fall. A bell that rings and takes the memory with it. A king who keeps time in a vault. |
| `isolde__listens_1` | None of it's cruel, exactly. But it remembers what you do. Every kindness, and every grudge. |
| `isolde__first_0` | First things first. Lay your hand on the waystone, there on the dais. Once it knows you, it will always bring you home. |
| `isolde__first_1` | Then let out the Edict that lives in you, and go and see Marta at the forge. She'll teach you how not to die. [dryly] Mostly. |
| `isolde__hint_attune_0` | The waystone first. Touch it, and it'll learn your name. |
| `isolde__hint_cast_0` | Go on, let the Edict out. Whatever it does, it's yours. The camp has seen worse. |
| `isolde__hint_marta_0` | Marta's at the forge, west of the dais. Follow the sound of hammering. [amused] And the swearing. |
| `isolde__hint_training_0` | Marta will have you rolling in the dirt by now. Good. Dodge first, parry second, and pride last of all. |
| `isolde__hint_journal_0` | Open your journal. It writes down everything the world tells you, so you don't have to. |
| `isolde__hint_bram_0` | Bram's at the supply stall, east of the dais. He'll teach you what a Mark is worth. To the copper. |
| `isolde__chapter_one_0` | You're ready, then. Good. The camp can only hold you so long, and the world won't wait. |
| `isolde__chapter_one_1` | Take work from the Warden's Board, there by the dais. Coin, reputation, and a reason to see the wilds. |
| `isolde__chapter_one_2` | Then follow the tremors. Where the ground hums, an Edict has slipped loose. Bind it before it binds something else. |
| `isolde__chapter_one_3` | Find a shrine on the Pilgrim's road, and touch the waystone at one of the old landmarks. Then come back and tell me everything. |
| `isolde__the_light_0` | The waystones answer the ones they know. While you're on the road, a pillar of light stands over the place you're bound for. |
| `isolde__the_light_1` | Press G to change which place it marks, if you're chasing more than one thing at once. |
| `isolde__hint_listening_0` | The Board, the tremor, the shrine, the landmark. One at a time, or all at once. Your mark will show you. |
| `isolde__chapter_two_0` | So it's true. An Edict came to your hand, and the shrine didn't turn you away. |
| `isolde__chapter_two_1` | Then I'll tell you what the Wardens don't tell everyone. Three old powers wait out there. The Bell-Ringer in the Hollow Cathedral. The King of Seconds, deep in his vault. And the Pilgrim, who walks and never arrives. |
| `isolde__chapter_two_2` | They're why the world is stuck. Free them, or face them. Either way, it starts to move again. [quietly] And whatever they leave behind is yours to carry. |
| `isolde__find_three_0` | The cathedral lies south and west, under a bell nobody remembers hearing. The vault goes down a long way, past the old roads. |
| `isolde__find_three_1` | The Pilgrim you'll hear before you see. Its footsteps wear roads into the grass. Wren can mark any of them for you. |
| `isolde__hint_three_0` | The Bell-Ringer, the King and the Pilgrim. Rest when you need to. The camp will always be here. |
| `isolde__chapter_three_0` | [quietly] Listen. The bell is quiet. The clocks have stopped arguing. And somewhere, a stone pilgrim is finally sitting down. |
| `isolde__chapter_three_1` | But the world has five more locks on it. Five Wardens, set to guard the lands when the old powers fell asleep. They never stopped guarding. |
| `isolde__chapter_three_2` | A samurai of ash in the blossom country. A lich who will not stay dead. A colossus in the white waste. A sentinel under the sun. And a gilded warden who judges everyone who passes. |
| `isolde__chapter_three_3` | Each one has a trial before it will even face you. The keepers know the ways in. Ask them. |
| `isolde__shogun_hint_0` | Go to the Lantern Dojo in Petalfall. His last disciple still keeps it. Win the Duel of Lanterns and you'll earn the right to challenge him. |
| `isolde__lich_hint_0` | Sister Ines has been reading the crypt records. Three crypts, three phylacteries. Break them all, or he'll simply get up again. |
| `isolde__colossus_hint_0` | Torvald has seen its tracks. If you've hunted with him, you know how to follow them. Don't lose the trail in the snow. |
| `isolde__sentinel_hint_0` | Rashid's people tell of a temple under the dunes that only opens to the sun. You'll need to bend the light. |
| `isolde__warden_hint_0` | The Gilded Vault won't open without five glyph tablets. They're scattered through the old places. Wren has a list. [amused] Of course she does. |
| `isolde__chapter_three_menu_0` | Ask me anything. I'll tell you what I know. |
| `isolde__hint_wardens_0` | Five Wardens. Take them one at a time. And come home between them, will you? [softly] I worry. |
| `isolde__epilogue_0` | [softly] You did it. All of it. Do you feel that? The wind's changed. The waystones are singing a new song. |
| `isolde__epilogue_1` | When you first woke here, the world was holding its breath. Now it's breathing again, and it's because of you. |
| `isolde__epilogue_2` | Whatever you do next, build, wander, rest, it'll remember you kindly. [warmly] And so will I. |
| `isolde__stew_0` | [surprised] Is that Bram's stew? You made it? [laughs softly] Thank you. I'll pretend I wasn't hungry. |
| `isolde__after_0` | The waystones sing different songs now. Happier ones, I think. Safe roads, traveller. |
| `isolde__idle_0` | Safe roads, traveller. The waystones remember you. |

---

## Marta Ironvein, smith to the Wardens

**Who they are:** A strong, blunt blacksmith with a low alto voice. Deadpan, impatient with fools, secretly proud of every student.

- **ElevenLabs:** Cass — `gOZRcEzY40chlRuMDmLV`
- **Second choice:** Kyra Katerina Kerosna — `eKfc89CM18tC2Gn79djN`
- **Fish Audio search:** "deep female voice blacksmith", "tough woman warrior"
- **Settings:** Stability 45 · Similarity 80 · Style 25
- **Treatment on import:** room 0.12

| File | Line |
|---|---|
| `marta__training_0` | So Isolde sent me another one. Good. Mind the sparks. Marta Ironvein, smith to the Wardens. |
| `marta__training_1` | Out there, things hit hard and they don't take turns. So you'll learn two things before you leave my yard. |
| `marta__training_2` | First, the roll. Tap the dodge key, Left Alt unless you've changed it, and you'll roll clear. For a breath, nothing can touch you. |
| `marta__training_3` | Second, the parry. Raise a shield, or your blade, the instant before a blow lands, and it'll bounce right back at them. |
| `marta__training_4` | The dummy in the yard swings slow and honest. Roll under one swing, then parry the next. [impatient] Go on. |
| `marta__hint_dodge_0` | Roll. When the dummy swings, tap dodge and get out from under it. Don't think. Roll. |
| `marta__hint_parry_0` | Now the parry. Block the instant before it hits. Too early and you're just holding a shield. [dryly] Too late and you're holding your nose. |
| `marta__meet_0` | Mind the sparks. Marta Ironvein, smith to the Wardens. |
| `marta__meet_1` | The beasts out here grow strange parts. Bring them to me with a few Marks, and I'll make you something worth swinging. |
| `marta__greet_0` | Back for steel? Good. Steel's always the answer. [beat] Usually. |
| `marta__parts_0` | Ember cores from the ashen husks make a blade that burns. Crystal from the shardlings sings when you swing it. |
| `marta__parts_1` | Lurker fangs make a hook that drags, gear springs a maul that kicks, rime cores a spear that bites cold. Bring me the pieces. |
| `marta__forge_offer_0` | That blade of yours has some miles on it. Weapons remember, out here. Every fight. Every kill. Some of them start to want more. |
| `marta__forge_offer_1` | Bring me a weapon that's ready to wake, and the parts it asks for, and we'll see what it wants to become. You'll swing the hammer, mind. I don't do all the work. |
| `marta__forge_ready_0` | When it's ready, it hums. You'll see it in the journal, and on the blade. Then bring it here with what it asks for. |
| `marta__forge_progress_0` | No hum yet. Keep fighting. Weapons are like people. They grow when they're used. |
| `marta__forge_smith_0` | It's humming. I can hear it from here. To the anvil, then. |
| `marta__forge_smith_1` | Strike when the metal glows gold. Not before. Not after. [grins] And keep your fingers where I can see them. |
| `marta__forge_done_0` | There. Feel the weight change? That's not the steel. That's the weapon deciding it likes you. |
| `marta__forge_done_1` | Bring me any weapon that hums, any time. The anvil's yours. |

---

## Quartermaster Bram

**Who they are:** A gruff, big-hearted Glaswegian quartermaster in his fifties. Dry humour, warm underneath, talks about money like weather.

- **ElevenLabs:** Pete Gates – Warm & Friendly Glaswegian — `eVKQybPTL0poBPxBa8L6`
- **Second choice:** Adam – Classic Scottish Storyteller — `U5UjeJMsOvyhYhXfZdvZ`
- **Fish Audio search:** "Scottish man gruff friendly", "Glasgow accent"
- **Settings:** Stability 40 · Similarity 80 · Style 30
- **Treatment on import:** room 0.1

| File | Line |
|---|---|
| `bram__meet_0` | Isolde sent you? Then you're welcome at my stall. Name's Bram. I keep this camp fed, lit and stitched together. |
| `bram__meet_1` | Here's twenty-five Marks to start you off. Nobody walks out of my camp with an empty purse. [gruff] That's not charity, mind. It's an investment. |
| `bram__meet_2` | Now. Prices out here move like weather. Sell me the same thing ten times and I'll pay you less each time. Bring me something rare from far away and I'll pay you more. |
| `bram__meet_3` | Go on, sell me something. Anything. Then buy something you need. That's the whole trick of it. |
| `bram__hint_trade_0` | Sell me something, then buy something. Open my stall and have a look. Go on. |
| `bram__meet_early_0` | A new face. Name's Bram. Quartermaster. Isolde will want to see you first, mind. She's by the waystone. |
| `bram__greet_0` | Back again? Good. Let's see what you need. |
| `bram__prices_0` | Every merchant pays more for what's scarce where they stand. Ember glass is cheap where it's made, and dear in the snow. |
| `bram__prices_1` | Carry goods between the keepers and you'll make a fortune. Or lose your boots to a lurker. [chuckles] That's trade. |
| `bram__prices_2` | And don't sell all your fangs to one stall in one day. I'll stop wanting them. So will everyone. |
| `bram__meal_offer_0` | You know what this camp's missing? A proper meal. Not jerky. Not bread. A stew that remembers its grandmother. |
| `bram__meal_offer_1` | Bring me two raw beef, three carrots, two potatoes, and an ember core for the warmth. I'll teach you to cook it yourself. |
| `bram__meal_progress_0` | Two beef, three carrots, two potatoes and an ember core. [gruff] Mind you don't eat them on the way. |
| `bram__meal_cook_0` | You've got it all? Good. To the pot. |
| `bram__meal_cook_1` | Add each thing when the broth tells you, and keep it stirring. A stew's like a camp. Neglect it and it burns. |
| `bram__meal_cook_again_0` | The pot's still warm. Give it another go. |
| `bram__meal_deliver_0` | Now take a bowl to Isolde. She forgets to eat when she's worrying, and she's always worrying. |
| `bram__meal_done_0` | She ate it? All of it? [laughs] Ha! Here. Take this purse knot. Coins like it. They stay. |

---

## Wren Halloway, Cartographers’ Guild

**Who they are:** A bright, quick young woman with a Celtic lilt. Curious, talks fast when excited, endlessly enthusiastic about maps.

- **ElevenLabs:** Zoey – Upbeat and Refreshing — `f4EpLLfw2IURnw62TRtz`
- **Second choice:** Jordan – Young Irish Woman — `cMnMwhoOlFYOQDBWBwU1`
- **Fish Audio search:** "young Irish woman energetic", "cheerful explorer girl"
- **Settings:** Stability 35 · Similarity 75 · Style 40
- **Treatment on import:** room 0.1

| File | Line |
|---|---|
| `wren__meet_0` | Oh! A new face. Wren Halloway, Cartographers' Guild. I map the places that won't sit still. |
| `wren__meet_1` | If you're looking for something out there, I can probably mark it on your map. Probably. And I keep the Guild Ledger, so your Marks can sleep somewhere safer than your pockets. |
| `wren__greet_0` | Back from the edges? Tell me everything. [excited] Or at least, tell me where. |
| `wren__marks_0` | Of course. What are you after? |
| `wren__ladder_0` | [lowers voice] The Warden's Ladder. An old gate, dark stone, a stone that glows. Step through and you're somewhere else entirely. |
| `wren__ladder_1` | Fifty floors, they say, each one meaner than the last. The Guild has never mapped past the fifth. [brightly] I've marked the nearest gate for you! |
| `wren__marked_0` | There. It's on your map now. Follow the light, and mind the edges. The edges are where maps get hungry. |
| `wren__landmarks_0` | There's a gazebo where the moon comes down to rest. A tower crowned with a crescent. A mushroom house frozen into the ice. And an airship that never quite lands. |
| `wren__landmarks_1` | Each has a keeper, and a waystone. Touch it, and you can come back whenever you like. It's the closest thing to cheating the Guild allows. |
| `wren__edges_offer_0` | You've got good boots and a curious face. How'd you like to do some real surveying? The Guild pays, and I pay better. |
| `wren__edges_offer_1` | Take this sextant. Find three landmarks, stand by their waystones and take a sighting. The trick is the wind. Hold your breath, and wait for the needle to settle. |
| `wren__edges_offer_2` | Bring me three good sightings and I'll give you something that makes the whole world smaller. [laughs] Well. Easier to fall off. |
| `wren__edges_progress_0` | Three sightings, three landmarks. The sextant counts them for you. Mind the needle. |
| `wren__edges_done_0` | Three! [delighted] Beautiful. These are the best sightings I've seen since, well, since mine. |
| `wren__edges_done_1` | Here. Moth-silk. Hold your jump while you fall and it'll catch the wind. The moths spun it, so be nice to moths. |
| `wren__tablets_0` | The five glyph tablets? I've a list. Of course I have a list. |
| `wren__tablets_1` | One in the sunken bog temple. One in the frozen mage tower. One in the buried obelisk, out in the dunes. One in the overgrown ziggurat. And one in a moonlit clearing in the Duskpine. I'll mark whichever is nearest. |

---

## Rook, keeper of the landings in the Warden's Ladder

**Who they are:** A wiry, ancient, delighted old man who has lived in the Ladder for centuries. Crackly, chirpy and quick, a shopkeeper's patter, laughs at his own jokes; a little mad, entirely kind.

- **ElevenLabs:** (search the library: "old man cheerful eccentric")
- **Second choice:** (any crackly, playful elderly male voice)
- **Fish Audio search:** "old man funny shopkeeper", "eccentric grandpa"
- **Settings:** Stability 35 · Similarity 75 · Style 45
- **Treatment on import:** room 0.1

| File | Line |
|---|---|
| `rook__meet_0` | [cheerfully] Ah! A climber! Welcome to Rook's Landing. Warm fire, fair prices, no refunds. |
| `rook__meet_1` | I've been on this Ladder longer than your grandmother's grandmother. Never reached the top. Never fell off, either. [chuckles] I take the back stairs. |
| `rook__greet_0` | [warmly] Back again! Sit, sit. The fire doesn't charge. |
| `rook__ahead_0` | Five floors to the next landing. Every tenth, a Warden's echo waits. Not the Warden, mind. Just the memory of one. [whispers] Memories hit just as hard. |
| `rook__ahead_1` | Stock up. Drink something red. And if the Ladder throws you out, come back. It always lets you back in. |
| `rook__who_0` | The Edict-writers built this place to prove their Wardens. I swept the floors. [laughs] Then they left, and I stayed. |
| `rook__who_1` | Somebody has to keep the fires lit. And somebody has to sell you potions at a very reasonable markup. |

---

## Stargazer Selene, keeper of the Moon Gazebo

**Who they are:** Dreamy, soft-spoken and other-worldly. Speaks slowly, as if listening to the sky between words.

- **ElevenLabs:** Serena – melodic and poetic — `mqyRCI8OeJTogXjYUGZ5`
- **Second choice:** Ella – angelic — `5BTfD9GV7eMTyvzofs0V`
- **Fish Audio search:** "ethereal soft female", "dreamy whisper narrator"
- **Settings:** Stability 55 · Similarity 75 · Style 20
- **Treatment on import:** hall 0.28, air

| File | Line |
|---|---|
| `selene__meet_0` | [whispers] Hush. The moon is listening. I'm Selene. I keep the gazebo, and I count the stars that fall. |
| `selene__meet_1` | Every night there is one fewer. I think someone is collecting them. |
| `selene__greet_0` | The sky is kind tonight. Or it is pretending to be. |
| `selene__lore_0` | Light remembers. A fallen star is a memory the sky let go of. |
| `selene__lore_1` | The Bureau keeps some of them in jars, I'm told. I hope they whisper to them. [sadly] Stars get lonely. |
| `selene__stars_offer_0` | There was a constellation here once. The Lost Moth. Its stars fell one by one, and with them went the gazebo's song. |
| `selene__stars_offer_1` | The old star chart was torn into three. One piece lies in the crystal observatory in the Shardfields. One at the aurora shrine in the Glimmerfrost. And one in the crater where a star once fell, out on the Ember Steppe. |
| `selene__stars_offer_2` | Bring the pieces to me, and we will put the Moth back in the sky. |
| `selene__stars_progress_0` | Three pieces. The observatory, the aurora shrine, the crater. The sky can wait. It is very patient. |
| `selene__stars_assemble_0` | [breathless] You found them. All three. Let me... yes. There. The Lost Moth. |
| `selene__stars_assemble_1` | Now the hard part. The dials on the gazebo's floor turn the old stars. Turn them until the sky above matches the chart. But only at night. The stars are shy. |
| `selene__stars_wait_0` | Wait for the night. Then turn the dials. [softly] I will keep watch with you. |
| `selene__stars_dials_0` | The dials are waiting. Turn each one until its star sits where the chart says. You will feel it settle. |
| `selene__stars_done_0` | [softly] Do you hear it? The gazebo is singing again. The Moth is home. |
| `selene__stars_done_1` | These were made from the last of the fallen starlight. Featherstep boots. Jump, and then jump again, as if the air were a stair. |

---

## Captain Odo Brassmere, master of the Wayfarer

**Who they are:** A booming, jolly old sky-captain. Theatrical, laughs at his own jokes, a little gravelly from shouting into wind.

- **ElevenLabs:** Silver – Gritty Pirate Voice — `6VgigPFWF0sNZy1BthVg`
- **Second choice:** Anthony Brownstone – Older English male — `06Te1sUCUc98TG97R9be`
- **Fish Audio search:** "jolly pirate captain", "old sea captain British"
- **Settings:** Stability 30 · Similarity 80 · Style 45
- **Treatment on import:** room 0.08

| File | Line |
|---|---|
| `odo__meet_0` | [booming] Ahoy, groundling! Captain Odo Brassmere, of the Wayfarer. Best ship in the sky. [beat] Only ship in the sky, come to think of it. |
| `odo__meet_1` | She's moored for repairs. Bring me gear springs and clockwork, and I'll make it worth your while. |
| `odo__greet_0` | Wind's from the west today. Good sailing weather, if one had a sail. |
| `odo__sky_0` | There are whales up there, you know. Great gentle things. They sing at dawn. |
| `odo__sky_1` | Lovely creatures. Just don't let one sit on your balloon. [laughs] Ask me how I know. |
| `odo__wayfarer_offer_0` | Here's the trouble. The Wayfarer's rudder springs are shot, and her clock-heart is skipping. I need six gear springs and two clockwork fragments. |
| `odo__wayfarer_offer_1` | Gearhounds carry springs in their bellies, poor things. Clockwork you'll find wherever the King's toys wander. |
| `odo__wayfarer_progress_0` | Six springs and two fragments, groundling. The sky won't wait forever. [beat] Actually, it will. But I won't. |
| `odo__wayfarer_parts_0` | [laughs] Look at that! Springs, clockwork, and not a single finger missing. Splendid. |
| `odo__wayfarer_parts_1` | Now, before I trust her with passengers, someone has to test the wings. Climb the tower, take this practice wing, and fly through every ring before the sand runs out. |
| `odo__wayfarer_race_0` | The rings, groundling! Top of the mooring tower, jump, and steer for the lights. Sixty seconds, all eight rings. |
| `odo__wayfarer_done_0` | [delighted] Ha! You fly like you were hatched up there! Here. My old hook. Point it at anything and it'll pull you there, or pull it to you. [beat] Don't point it at me. |
| `odo__wayfarer_done_1` | And any time you want to go somewhere far, come and see me. For a few Marks, the Wayfarer will take you. |
| `odo__charter_0` | Where to, groundling? Thirty Marks a leg, and I throw in the view for free. |

---

## Fen-Keeper Mab, witch of the black water

**Who they are:** An ancient swamp witch. Raspy, cackling, sly; drags out words she enjoys. Half menace, half grandmother.

- **ElevenLabs:** The Ancient Evil — `HH3kybY6uEJ2ebSa9Vy3`
- **Second choice:** Enchantress – mysterious, witchy — `sssn4wp3AspuK2kvy3Ym`
- **Fish Audio search:** "old witch cackle", "crone raspy"
- **Settings:** Stability 35 · Similarity 75 · Style 50
- **Treatment on import:** room 0.1

| File | Line |
|---|---|
| `mab__meet_0` | [cackles] Heh. Another one wading in. I'm Mab. The fen is mine, and I'm the fen's. |
| `mab__meet_1` | Mind the lurkers. They look like logs, right up until they don't. |
| `mab__greet_0` | Still breathing? The fen likes you, then. [sly] Or it's saving you for later. |
| `mab__advice_0` | Brew before you fight, not after. And never answer the bog when it calls your name. |
| `mab__brew_offer_0` | Want to cross my fen without wet boots? Heh. There's a charm for that. But a charm needs a brew, and a brew needs a fool to fetch for it. |
| `mab__brew_offer_1` | Three lurker fangs, one lump of bog amber, and two pinches of moth dust. Bring them, and mind the logs. |
| `mab__brew_progress_0` | Fangs, amber, dust. I'll not repeat myself a third time. [cackles] I will, actually. Fangs. Amber. Dust. |
| `mab__brew_cook_0` | Good, good. Now, the cauldron. Drop each one in when the brew turns the right colour, and stir with the bubbles, not against them. |
| `mab__brew_cook_again_0` | The cauldron's still hot. Again, and gently this time. |
| `mab__brew_test_0` | Drink it, and walk out to the dead tree in the middle of the water and back. If you come back dry, the charm is yours. |
| `mab__brew_test_1` | If you come back wet, well. [cackles] It's a learning experience. |
| `mab__brew_done_0` | [cackles] Dry as a bone! Take the charm. It'll hold you up on water, and on lava that's had a good long think about cooling. |
| `mab__brew_done_1` | Don't make a habit of the lava. |

---

## Torvald the Hunter

**Who they are:** A deep, slow, gravelly northern hunter. Few words, each one weighed. Speaks like snow falling.

- **ElevenLabs:** Bjorn The Viking — `ljo9gAlSqKOvF6D8sOsX`
- **Second choice:** Adam – Classic Scottish Storyteller — `U5UjeJMsOvyhYhXfZdvZ`
- **Fish Audio search:** "viking deep voice", "nordic warrior"
- **Settings:** Stability 55 · Similarity 80 · Style 15
- **Treatment on import:** room 0.14

| File | Line |
|---|---|
| `torvald__meet_0` | Stranger. Sit by the fire if you like. Torvald. I hunt what hunts the valley. |
| `torvald__meet_1` | The rimehorns are restless this year. And something walks the frozen peaks. Taller than the trees. |
| `torvald__greet_0` | The fire's warm. The meat's fresh. Sit. |
| `torvald__big_0` | Ice and white fur, and horns like a cathedral's ribs. The old songs call it the Rimeback. |
| `torvald__big_1` | If you ever see the snow stand up, run. [beat] Then run some more. |
| `torvald__hunt_offer_0` | The rimehorn alpha has been taking the young ones from the valley. I am old, and my knees are older. You are neither. |
| `torvald__hunt_offer_1` | Follow its tracks from the lodge. They're deep and wide, and the snow fills them fast. Lose them, and you lose it. |
| `torvald__hunt_progress_0` | Its tracks start at the lodge. Watch the snow. The fresh ones still have their edges. |
| `torvald__hunt_killed_0` | You brought down the alpha? [laughs] Then you've earned a contest. |
| `torvald__hunt_killed_1` | Every hunter at this lodge has shot the targets. Hit six of eight, and I'll call you hunter too. |
| `torvald__hunt_contest_0` | The targets are set. Six of eight. Breathe out before you loose. |
| `torvald__hunt_done_0` | Six of eight. Good. Better than I did, my first winter. |
| `torvald__hunt_done_1` | Take these claws. They'll let you climb where the rimehorns climb. And the stag, he's taken a liking to you. Whistle, and he'll come. |
| `torvald__hunt_done_2` | One more thing. The big tracks, the ones as wide as a cart. Those aren't rimehorn. If you ever follow them, go well armed. |

---

## Sister Ines, of the Morrow Court

**Who they are:** A calm, kind nun with a soft Spanish accent. Solemn, unhurried, consoling; a voice used to speaking in quiet chapels.

- **ElevenLabs:** Laura – Warm Bilingual Latina Voice — `FGLJyeekUzxl8M3CTG9M`
- **Second choice:** ladypatty1 – warm and latin — `k6aNMn2EN3T8vpJSBhQw`
- **Fish Audio search:** "calm Spanish accent woman", "gentle nun"
- **Settings:** Stability 60 · Similarity 80 · Style 10
- **Treatment on import:** hall 0.25

| File | Line |
|---|---|
| `ines__meet_0` | Peace, traveller. I am Sister Ines, of the Morrow Court. We remember the dead, so that they may rest. |
| `ines__meet_1` | The bell of this chapel drowned long ago. Still, some nights, I hear it ring. |
| `ines__greet_0` | Welcome back. The candles burned steady while you were gone. |
| `ines__heal_0` | There. Rest a moment. The road will wait for you. It always does. |
| `ines__bell_offer_0` | Some nights a voice hums near the altar. A child's voice, I think. The bell-ringer's apprentice, who drowned with the bell. |
| `ines__bell_offer_1` | The bell's rope was carried off by the tide. It lies in the drowned shrine on the coast, if the old records are right. Bring it back, and perhaps she can finish her song. |
| `ines__bell_progress_0` | The drowned shrine, on the coast. Go carefully. The sea keeps what it takes. |
| `ines__bell_rope_0` | The rope! It's still whole. Bring it to the altar bells, and listen. |
| `ines__bell_rope_1` | When the humming starts, ring the small bells in the same order. [softly] Gently. |
| `ines__bell_listen_0` | Ring them as she hums. If you lose the tune, she will hum it again. |
| `ines__bell_done_0` | [softly] She finished it. Did you hear? The whole song. |
| `ines__bell_done_1` | This bell is hers. Ring it, and it will lift you, the way a song lifts a heavy heart. Up, and up. |
| `ines__lich_0` | The records speak of a lich who hid his heart in three places. A barrow on the Vesper Downs. An ossuary gate in the Pale Reach. And his own necropolis. |
| `ines__lich_1` | Break all three, or he will not stay down. [quietly] The Court will pray for you. It helps more than you'd think. |

---

## Rashid of the Sands, master of the caravan

**Who they are:** A charismatic Middle-Eastern merchant. Warm, playful, generous with flattery, sharp about money.

- **ElevenLabs:** Abduljalil — `Vk9ZqhRb88Uz2VkJR43M`
- **Second choice:** Ahmed — `bpVVDsfiNGD1Fs2TZP84`
- **Fish Audio search:** "Arabic accent man warm", "merchant storyteller"
- **Settings:** Stability 40 · Similarity 80 · Style 35
- **Treatment on import:** room 0.08

| File | Line |
|---|---|
| `rashid__meet_0` | Welcome to the Hourglass, friend. Rashid, master of this caravan. The water is free. [playful] Everything else, we can discuss. |
| `rashid__greet_0` | Ah, my friend returns. The sand remembers you, and so do I. |
| `rashid__obelisk_0` | It tells the hours with its shadow. But at noon, sometimes, it tells the wrong one. |
| `rashid__obelisk_1` | On those days the sand remembers things it shouldn't. We pack up early. |
| `rashid__caravan_offer_0` | Three days ago my sister's caravan left for the nomad camps of the Ember Steppe. Twelve camels, eight drivers, a cargo of spice. It never arrived. |
| `rashid__caravan_offer_1` | No sandstorm. No bandits seen. Just silence. Find out what happened to them, and you will have a friend in every market I trade in. |
| `rashid__caravan_progress_0` | Start where they were last seen, the old road east of here. Look for anything out of place. Sand hides things, but it is a poor liar. |
| `rashid__caravan_deduce_0` | You found the wagon, the manifest, and the tracks. So. Where did they go? You saw what I did not. |
| `rashid__caravan_wrong_0` | [doubtful] Hm. That does not fit the tracks, my friend. Look at what you found again. Sand is a poor liar, remember. |
| `rashid__caravan_right_0` | The obelisk! Of course. They hid from the storm-eaters. Go, quickly. If they hid there, something chased them in. |
| `rashid__caravan_rescue_0` | The buried obelisk, east and south. Bring them home, friend. |
| `rashid__caravan_done_0` | My sister. Alive. And most of the spice, which she says I care about more. [laughs] She is wrong. Mostly. |
| `rashid__caravan_done_1` | Take this seal. Show it to any Guild merchant, and they will treat you as family. Which means slightly less robbery. |

---

## Leyla of the Sands, Rashid's sister

**Who they are:** A tough, dry-humoured Middle-Eastern caravan master. Hoarse from the sand, tired, proud, the elder sibling; warm underneath.

- **ElevenLabs:** (search the library: "Middle Eastern female warm mature")
- **Second choice:** (any warm, slightly husky female voice)
- **Fish Audio search:** "Arabic accent woman warm", "desert trader woman"
- **Settings:** Stability 45 · Similarity 80 · Style 30
- **Treatment on import:** room 0.1

| File | Line |
|---|---|
| `leyla__stranger_0` | [whispering] Shh. Keep your voice down. The storm-eaters hunt by sound. |
| `leyla__stranger_1` | If you're going back to the Hourglass, find a merchant called Rashid. Tell him his sister is alive, and very, very annoyed. |
| `leyla__found_0` | You're not a storm-eater. [relieved] Rashid sent you? Of course he did. He'd send the whole Guild if it didn't cost him a coin. |
| `leyla__found_1` | We hid down here when the sand opened and those things came out. Eight drivers, all alive. Mostly whole. |
| `leyla__freed_0` | Then we walk at dusk. Tell my brother we're coming. [smiles] And that I counted the spice. |
| `leyla__waiting_0` | We leave at dusk. Go on ahead, and tell Rashid to put the kettle on. |
| `leyla__home_0` | The road's quiet again. The sand owes you one, traveller. So do I. |

---

## Akane, last disciple of the Lantern Dojo

**Who they are:** A young Japanese swordswoman. Disciplined, formal, grieving her master; softens once you prove yourself.

- **ElevenLabs:** (search the library: "Japanese female calm")
- **Second choice:** Hideo – Japanese Asian Accent, pitched up, if no female voice suits — `FMgBdHe1YV2Xi0B9anXW`
- **Fish Audio search:** "Japanese woman calm English", "samurai girl"
- **Settings:** Stability 55 · Similarity 80 · Style 20
- **Treatment on import:** room 0.12

| File | Line |
|---|---|
| `akane__meet_0` | You walk into the Lantern Dojo with your boots on. Bold. I am Akane, the last disciple of this house. |
| `akane__meet_1` | My master became the Ashen Shogun. Every lantern on these walls is a life he took. If you have come to challenge him, you must first face me. |
| `akane__greet_0` | The lanterns flicker when you enter. They remember you. |
| `akane__not_yet_0` | [quietly] Not yet. You still smell of the camp's woodsmoke. He has burned a hundred who were braver than you look. |
| `akane__not_yet_1` | When the Warden of the waystones speaks to you of the five Wardens, come back. The lanterns will still be burning. They always are. |
| `akane__master_0` | He was the gentlest man I knew. He swore to guard the blossom country forever, and the world took him at his word. |
| `akane__master_1` | Forever is too long for a promise. It burned him to ash, and left the oath walking. |
| `akane__duel_rules_0` | The Duel of Lanterns has one rule. Only a perfect parry may strike. Any other blow, and the lanterns go dark. |
| `akane__duel_rules_1` | Turn my blade aside five times, and I will give you his challenge lantern. [quietly] And perhaps, peace. |
| `akane__duel_won_0` | [breathing hard] Five. You turned my blade five times. Take the lantern. |
| `akane__duel_won_1` | Light it at the dojo's heart, and he will come. Do not let him reach the hundredth lantern. |

---

## The Ashen Shogun

**Who they are:** A dead samurai lord. Quiet, precise, utterly calm, with a Japanese accent; every line a blade drawn slowly. Never shouts.

- **ElevenLabs:** Hideo – Japanese Asian Accent — `FMgBdHe1YV2Xi0B9anXW`
- **Second choice:** Tokyo Executive — `Gr7mLjPA3HhuWxZidxPW`
- **Fish Audio search:** "samurai deep calm Japanese accent"
- **Settings:** Stability 60 · Similarity 75 · Style 20
- **Treatment on import:** hall 0.22

| File | Line |
|---|---|
| `shogun__intro_0` | You came for the lanterns. Every one is a life I took. |
| `shogun__intro_1` | [slowly] Add yours. |
| `shogun__phase2_0` | Which of us is the ash? Choose. |
| `shogun__phase3_0` | A hundred lanterns. [quietly] Light the last one. |
| `shogun__defeat_0` | The wind is clean again. |
| `shogun__defeat_1` | Take my lantern. Walk where I could not. |

---

## The Crimson Lich

**Who they are:** An ancient, theatrical undead king. Velvet menace; amused by the living. Gravelly, slow, savours every threat.

- **ElevenLabs:** Branok – Evil and Villainous — `Vs5CmVCVJwW4odQS2pVf`
- **Second choice:** Malakor – Grim Antagonist — `dqoktGAat2XIYpd4sTb7`
- **Fish Audio search:** "lich undead king", "evil sorcerer deep"
- **Settings:** Stability 40 · Similarity 75 · Style 45
- **Treatment on import:** pitch -1.5, cathedral 0.32

| File | Line |
|---|---|
| `lich__intro_0` | Ah. A visitor who still has a heartbeat. [amused] How quaint. |
| `lich__intro_1` | Kneel, and I shall make it quick. Stand, and I shall make it last. |
| `lich__phase2_0` | My obelisks drink. You will tire long before they do. |
| `lich__phase3_0` | [roaring] Rise. All of you. RISE. |
| `lich__revive_0` | [laughs] Did you think death was something I had not already survived? |
| `lich__defeat_0` | The phylacteries. You found them. [weakly] Clever. |
| `lich__defeat_1` | Then let me, at last, be still. |

---

## The King of Seconds

**Who they are:** A clockwork monarch. Posh, cunning, impatient; speaks in tidy little phrases like a ticking watch.

- **ElevenLabs:** Victor – The Cunning Villain – Posh British — `ttNi9wVM8M97tsxE7PFZ`
- **Second choice:** Monty – Theatrical Aristocratic Villain — `tUVMjP56ZNYXlKLsRXbo`
- **Fish Audio search:** "posh British villain"
- **Settings:** Stability 45 · Similarity 80 · Style 40
- **Treatment on import:** metal 0.2, room 0.18

| File | Line |
|---|---|
| `king__intro_0` | Late. Everyone is always late. Shall we see how late you are? |
| `king__bargain_0` | A trade, then. A second of my power for every second you spare me. Tick, tock. Decide. |
| `king__defeat_0` | [slowing] Tick. Tock. Tick. |

---

## The Bell-Ringer

**Who they are:** A huge, broken creature who has forgotten itself. Only whispers, hoarse and childlike, as if from inside a bell.

- **ElevenLabs:** Malakor – Grim Antagonist (whispering) — `dqoktGAat2XIYpd4sTb7`
- **Second choice:** The Ancient Evil (whispering) — `HH3kybY6uEJ2ebSa9Vy3`
- **Fish Audio search:** "monster whisper", "giant creature whisper"
- **Settings:** Stability 30 · Similarity 70 · Style 30
- **Treatment on import:** pitch -3, cathedral 0.42

| File | Line |
|---|---|
| `bell__intro_0` | [whispers] Ring. Must ring. Must... |
| `bell__name_0` | [whispers] That was... my name. Yes. That was me. |
| `bell__defeat_0` | [whispers] Quiet. So quiet now. |

---

## The Gilded Warden

**Who they are:** An ancient guardian construct of stone and gold. Measured and formal, like a judge reading a verdict; resonant and slow.

- **ElevenLabs:** Wolfgang – Deep Resonant Narrator — `Rsizh9fHHZ0ZE0geoMbe`
- **Second choice:** Elariel X – Epic Ethereal Queen — `ksryVoNAGZT8GxWCTiVm`
- **Fish Audio search:** "ancient golem deep voice", "god voice"
- **Settings:** Stability 65 · Similarity 75 · Style 15
- **Treatment on import:** pitch -2, metal 0.15, cathedral 0.32

| File | Line |
|---|---|
| `warden__intro_0` | None pass the Gilded Vault without the five glyphs. You carry them. |
| `warden__intro_1` | [resonant] Then be judged. |
| `warden__phase2_0` | The shell remembers every blow. So do I. |
| `warden__phase3_0` | Judgment is not cruelty. It is accounting. |
| `warden__defeat_0` | The account is settled. Go, traveller. You are owed nothing, and owe nothing. |

---

## The Dune Sentinel

**Who they are:** A sand golem woken from a thousand years of sleep. Very few words, enormous, grinding, slow.

- **ElevenLabs:** Evil Villain Voice – Deep, Menacing — `pHD4qotPFeOAuU1YsFjv`
- **Second choice:** Branok – Evil and Villainous — `Vs5CmVCVJwW4odQS2pVf`
- **Fish Audio search:** "giant golem rumble"
- **Settings:** Stability 50 · Similarity 70 · Style 25
- **Treatment on import:** pitch -4, sand 0.2, cathedral 0.28, giant

| File | Line |
|---|---|
| `sentinel__intro_0` | [booming] WHO... WAKES... THE SUN? |
| `sentinel__defeat_0` | [grinding] Sleep... again... |

---

## The Afterworld (chapter openings)

**Who they are:** The voice of the world itself. Mythic, regal, intimate; a storyteller beside a fire that is also the sky.

- **ElevenLabs:** Elariel X – Epic Ethereal Queen — `ksryVoNAGZT8GxWCTiVm`
- **Second choice:** Wolfgang – Deep Resonant Narrator — `Rsizh9fHHZ0ZE0geoMbe`
- **Fish Audio search:** "epic fantasy narrator female"
- **Settings:** Stability 60 · Similarity 80 · Style 20
- **Treatment on import:** hall 0.2

| File | Line |
|---|---|
| `narrator__prologue_0` | The world listens. It always has. Today, it heard you wake. |
| `narrator__chapter1_0` | Chapter One. The Listening World. Where the ground hums, a rule has slipped its leash. |
| `narrator__chapter2_0` | Chapter Two. The Three Who Wait. A bell, a clock, and a pilgrim. Each one stuck. Each one waiting for you. |
| `narrator__chapter3_0` | Chapter Three. Wardens of the Five Lands. The old guardians never stopped guarding. Not even when they should have. |
| `narrator__epilogue_0` | And the world, at last, let out its breath. |


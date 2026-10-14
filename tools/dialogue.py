#!/usr/bin/env python3
"""Every conversation in the Afterworld, and who should voice it.

This file is the single source for the game's words. Running it writes:

  src/main/resources/data/afterworld/dialogue/<npc>.json   the conversation graphs the game reads
  src/main/resources/data/afterworld/dialogue/barks.json   the great powers' lines (cutscenes, fights)
  voice/VOICE_SCRIPT.md                                     the voice script: casting, direction, every line
  voice/lines.csv                                           the same lines as a table, for batch generation

Voice lines are recorded elsewhere (ElevenLabs, Fish Audio, a real actor) and brought in with
tools/import_voices.py: drop files named <who>__<line id>.<mp3|wav|ogg|flac> into voice/incoming/.
Lines without a recording simply show their subtitle.

Text may carry delivery cues in square brackets, e.g. "[softly] There you are." The cues go to
the voice script (ElevenLabs v3 reads them as audio tags; for Fish Audio write them as
(softly)), and are stripped from the subtitles.

Node fields: lines (spoken in order), options [(text, go, if, do)], do (actions on arrival).
go: a node, or '#end', '#shop', '#travel', '#bank', '#charter'. Conditions and actions are listed
in npc/Dialogue.java.
"""
import csv, json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
DATA = os.path.join(RES, 'data', 'afterworld', 'dialogue')
VOICE_DIR = os.path.join(ROOT, 'voice')
SOUNDS = os.path.join(RES, 'assets', 'afterworld', 'sounds', 'va')
TIMING = os.path.join(VOICE_DIR, 'timing.json')
BEATS = os.path.join(VOICE_DIR, 'beats.json')

# ------------------------------------------------------------------ casting
# el: an ElevenLabs library voice (name, id). alt: a second choice. fish: what to search for on
# Fish Audio. fx: what tools/import_voices.py does to the recording (pitch in semitones, room,
# hall, cathedral, metal, sand, rasp, air, giant).
CAST = {
    'isolde': dict(role='Warden Isolde, keeper of the Waystone Camp',
                   who='A warm, wise Irish woman in her forties. Gentle authority; a guide who has seen many travellers come and go. Never rushed.',
                   el=('Colleen – Warm Southern Irish Woman', '1OYA2kgM85gF2eGN8HEp'), alt=('Louise – Neutral Irish – Sincere', 'UwtFVYnvYG6hxAbc4I6T'),
                   fish='"Irish woman warm storyteller", "gentle Irish narrator"', settings='Stability 50 · Similarity 80 · Style 15', fx=dict(room=0.12)),
    'bram': dict(role='Quartermaster Bram',
                 who='A gruff, big-hearted Glaswegian quartermaster in his fifties. Dry humour, warm underneath, talks about money like weather.',
                 el=('Pete Gates – Warm & Friendly Glaswegian', 'eVKQybPTL0poBPxBa8L6'), alt=('Adam – Classic Scottish Storyteller', 'U5UjeJMsOvyhYhXfZdvZ'),
                 fish='"Scottish man gruff friendly", "Glasgow accent"', settings='Stability 40 · Similarity 80 · Style 30', fx=dict(room=0.10)),
    'wren': dict(role='Wren Halloway, Cartographers’ Guild',
                 who='A bright, quick young woman with a Celtic lilt. Curious, talks fast when excited, endlessly enthusiastic about maps.',
                 el=('Zoey – Upbeat and Refreshing', 'f4EpLLfw2IURnw62TRtz'), alt=('Jordan – Young Irish Woman', 'cMnMwhoOlFYOQDBWBwU1'),
                 fish='"young Irish woman energetic", "cheerful explorer girl"', settings='Stability 35 · Similarity 75 · Style 40', fx=dict(room=0.10)),
    'marta': dict(role='Marta Ironvein, smith to the Wardens',
                  who='A strong, blunt blacksmith with a low alto voice. Deadpan, impatient with fools, secretly proud of every student.',
                  el=('Cass', 'gOZRcEzY40chlRuMDmLV'), alt=('Kyra Katerina Kerosna', 'eKfc89CM18tC2Gn79djN'),
                  fish='"deep female voice blacksmith", "tough woman warrior"', settings='Stability 45 · Similarity 80 · Style 25', fx=dict(room=0.12)),
    'selene': dict(role='Stargazer Selene, keeper of the Moon Gazebo',
                   who='Dreamy, soft-spoken and other-worldly. Speaks slowly, as if listening to the sky between words.',
                   el=('Serena – melodic and poetic', 'mqyRCI8OeJTogXjYUGZ5'), alt=('Ella – angelic', '5BTfD9GV7eMTyvzofs0V'),
                   fish='"ethereal soft female", "dreamy whisper narrator"', settings='Stability 55 · Similarity 75 · Style 20', fx=dict(hall=0.28, air=True)),
    'odo': dict(role='Captain Odo Brassmere, master of the Wayfarer',
                who='A booming, jolly old sky-captain. Theatrical, laughs at his own jokes, a little gravelly from shouting into wind.',
                el=('Silver – Gritty Pirate Voice', '6VgigPFWF0sNZy1BthVg'), alt=('Anthony Brownstone – Older English male', '06Te1sUCUc98TG97R9be'),
                fish='"jolly pirate captain", "old sea captain British"', settings='Stability 30 · Similarity 80 · Style 45', fx=dict(room=0.08)),
    'mab': dict(role='Fen-Keeper Mab, witch of the black water',
                who='An ancient swamp witch. Raspy, cackling, sly; drags out words she enjoys. Half menace, half grandmother.',
                el=('The Ancient Evil', 'HH3kybY6uEJ2ebSa9Vy3'), alt=('Enchantress – mysterious, witchy', 'sssn4wp3AspuK2kvy3Ym'),
                fish='"old witch cackle", "crone raspy"', settings='Stability 35 · Similarity 75 · Style 50', fx=dict(room=0.10)),
    'torvald': dict(role='Torvald the Hunter',
                    who='A deep, slow, gravelly northern hunter. Few words, each one weighed. Speaks like snow falling.',
                    el=('Bjorn The Viking', 'ljo9gAlSqKOvF6D8sOsX'), alt=('Adam – Classic Scottish Storyteller', 'U5UjeJMsOvyhYhXfZdvZ'),
                    fish='"viking deep voice", "nordic warrior"', settings='Stability 55 · Similarity 80 · Style 15', fx=dict(room=0.14)),
    'ines': dict(role='Sister Ines, of the Morrow Court',
                 who='A calm, kind nun with a soft Spanish accent. Solemn, unhurried, consoling; a voice used to speaking in quiet chapels.',
                 el=('Laura – Warm Bilingual Latina Voice', 'FGLJyeekUzxl8M3CTG9M'), alt=('ladypatty1 – warm and latin', 'k6aNMn2EN3T8vpJSBhQw'),
                 fish='"calm Spanish accent woman", "gentle nun"', settings='Stability 60 · Similarity 80 · Style 10', fx=dict(hall=0.25)),
    'rashid': dict(role='Rashid of the Sands, master of the caravan',
                   who='A charismatic Middle-Eastern merchant. Warm, playful, generous with flattery, sharp about money.',
                   el=('Abduljalil', 'Vk9ZqhRb88Uz2VkJR43M'), alt=('Ahmed', 'bpVVDsfiNGD1Fs2TZP84'),
                   fish='"Arabic accent man warm", "merchant storyteller"', settings='Stability 40 · Similarity 80 · Style 35', fx=dict(room=0.08)),
    'leyla': dict(role="Leyla of the Sands, Rashid's sister",
                  who='A tough, dry-humoured Middle-Eastern caravan master. Hoarse from the sand, tired, proud, the elder sibling; warm underneath.',
                  el=('(search the library: "Middle Eastern female warm mature")', ''), alt=('(any warm, slightly husky female voice)', ''),
                  fish='"Arabic accent woman warm", "desert trader woman"', settings='Stability 45 · Similarity 80 · Style 30', fx=dict(room=0.10)),
    'rook': dict(role='Rook, keeper of the landings in the Warden\'s Ladder',
                 who='A wiry, ancient, delighted old man who has lived in the Ladder for centuries. Crackly, chirpy and quick, a shopkeeper\'s patter, laughs at his own jokes; a little mad, entirely kind.',
                 el=('(search the library: "old man cheerful eccentric")', ''), alt=('(any crackly, playful elderly male voice)', ''),
                 fish='"old man funny shopkeeper", "eccentric grandpa"', settings='Stability 35 · Similarity 75 · Style 45', fx=dict(room=0.10)),
    'akane': dict(role='Akane, last disciple of the Lantern Dojo',
                  who='A young Japanese swordswoman. Disciplined, formal, grieving her master; softens once you prove yourself.',
                  el=('(search the library: "Japanese female calm")', ''), alt=('Hideo – Japanese Asian Accent, pitched up, if no female voice suits', 'FMgBdHe1YV2Xi0B9anXW'),
                  fish='"Japanese woman calm English", "samurai girl"', settings='Stability 55 · Similarity 80 · Style 20', fx=dict(room=0.12)),
    # The great powers. Their recordings are processed into something less than human.
    'shogun': dict(role='The Ashen Shogun',
                   who='A dead samurai lord. Quiet, precise, utterly calm, with a Japanese accent; every line a blade drawn slowly. Never shouts.',
                   el=('Hideo – Japanese Asian Accent', 'FMgBdHe1YV2Xi0B9anXW'), alt=('Tokyo Executive', 'Gr7mLjPA3HhuWxZidxPW'),
                   fish='"samurai deep calm Japanese accent"', settings='Stability 60 · Similarity 75 · Style 20', fx=dict(hall=0.22)),
    'lich': dict(role='The Crimson Lich',
                 who='An ancient, theatrical undead king. Velvet menace; amused by the living. Gravelly, slow, savours every threat.',
                 el=('Branok – Evil and Villainous', 'Vs5CmVCVJwW4odQS2pVf'), alt=('Malakor – Grim Antagonist', 'dqoktGAat2XIYpd4sTb7'),
                 fish='"lich undead king", "evil sorcerer deep"', settings='Stability 40 · Similarity 75 · Style 45', fx=dict(pitch=-1.5, cathedral=0.32)),
    'king': dict(role='The King of Seconds',
                 who='A clockwork monarch. Posh, cunning, impatient; speaks in tidy little phrases like a ticking watch.',
                 el=('Victor – The Cunning Villain – Posh British', 'ttNi9wVM8M97tsxE7PFZ'), alt=('Monty – Theatrical Aristocratic Villain', 'tUVMjP56ZNYXlKLsRXbo'),
                 fish='"posh British villain"', settings='Stability 45 · Similarity 80 · Style 40', fx=dict(metal=0.2, room=0.18)),
    'bell': dict(role='The Bell-Ringer',
                 who='A huge, broken creature who has forgotten itself. Only whispers, hoarse and childlike, as if from inside a bell.',
                 el=('Malakor – Grim Antagonist (whispering)', 'dqoktGAat2XIYpd4sTb7'), alt=('The Ancient Evil (whispering)', 'HH3kybY6uEJ2ebSa9Vy3'),
                 fish='"monster whisper", "giant creature whisper"', settings='Stability 30 · Similarity 70 · Style 30', fx=dict(pitch=-3, cathedral=0.42)),
    'warden': dict(role='The Gilded Warden',
                   who='An ancient guardian construct of stone and gold. Measured and formal, like a judge reading a verdict; resonant and slow.',
                   el=('Wolfgang – Deep Resonant Narrator', 'Rsizh9fHHZ0ZE0geoMbe'), alt=('Elariel X – Epic Ethereal Queen', 'ksryVoNAGZT8GxWCTiVm'),
                   fish='"ancient golem deep voice", "god voice"', settings='Stability 65 · Similarity 75 · Style 15', fx=dict(pitch=-2, metal=0.15, cathedral=0.32)),
    'sentinel': dict(role='The Dune Sentinel',
                     who='A sand golem woken from a thousand years of sleep. Very few words, enormous, grinding, slow.',
                     el=('Evil Villain Voice – Deep, Menacing', 'pHD4qotPFeOAuU1YsFjv'), alt=('Branok – Evil and Villainous', 'Vs5CmVCVJwW4odQS2pVf'),
                     fish='"giant golem rumble"', settings='Stability 50 · Similarity 70 · Style 25', fx=dict(pitch=-4, sand=0.2, cathedral=0.28, giant=True)),
    'narrator': dict(role='The Afterworld (chapter openings)',
                     who='The voice of the world itself. Mythic, regal, intimate; a storyteller beside a fire that is also the sky.',
                     el=('Elariel X – Epic Ethereal Queen', 'ksryVoNAGZT8GxWCTiVm'), alt=('Wolfgang – Deep Resonant Narrator', 'Rsizh9fHHZ0ZE0geoMbe'),
                     fish='"epic fantasy narrator female"', settings='Stability 60 · Similarity 80 · Style 20', fx=dict(hall=0.20)),
}


def opt(text, go, when=None, do=None):
    return (text, go, when or [], do or [])


END = opt('Farewell.', '#end')
TRAVEL = opt('Send me down the road.', '#travel')

# ------------------------------------------------------------------ the people
SCRIPT = {
    'isolde': {
        'entry': [
            (['obj=meet_isolde'], 'welcome'),
            (['obj=report_prologue'], 'chapter_one'),
            (['obj=report_listening'], 'chapter_two'),
            (['obj=report_three'], 'chapter_three'),
            (['obj=report_wardens'], 'epilogue'),
            (['side:meal=2'], 'stew'),
            (['obj=attune_camp'], 'hint_attune'),
            (['obj=cast'], 'hint_cast'),
            (['obj=meet_marta'], 'hint_marta'),
            (['obj=dodge'], 'hint_training'),
            (['obj=parry'], 'hint_training'),
            (['obj=journal'], 'hint_journal'),
            (['obj=meet_bram'], 'hint_bram'),
            (['obj=trade'], 'hint_bram'),
            (['chapter=1'], 'hint_listening'),
            (['chapter=2'], 'hint_three'),
            (['chapter=3'], 'hint_wardens'),
            (['finished'], 'after'),
            ([], 'idle'),
        ],
        'nodes': {
            'welcome': dict(lines=[
                "[softly] There you are. I felt the waystone hum the moment you woke.",
                "Easy, now. Everyone wakes a little lost. The Afterworld is like that. It listens before it speaks.",
                "I'm Isolde, Warden of this camp. We keep the waystones lit, and we keep travellers like you on your feet.",
            ], options=[opt("Where am I?", 'where'), opt("What do you mean, it listens?", 'listens'), opt("What should I do first?", 'first')]),
            'where': dict(lines=[
                "A world still deciding what it wants to be. Old rules, half forgotten, written into stone and storm.",
                "We call those rules Edicts. A few folk carry one inside them, the way you do. That's why the world noticed you.",
            ], options=[opt("What do you mean, it listens?", 'listens'), opt("What should I do first?", 'first')]),
            'listens': dict(lines=[
                "Walk far enough and you'll see. Stones that forget to fall. A bell that rings and takes the memory with it. A king who keeps time in a vault.",
                "None of it's cruel, exactly. But it remembers what you do. Every kindness, and every grudge.",
            ], options=[opt("Where am I?", 'where'), opt("What should I do first?", 'first')]),
            'first': dict(do=['complete:meet_isolde'], lines=[
                "First things first. Lay your hand on the waystone, there on the dais. Once it knows you, it will always bring you home.",
                "Then let out the Edict that lives in you, and go and see Marta at the forge. She'll teach you how not to die. [dryly] Mostly.",
            ], options=[opt("I'll do that.", '#end')]),
            'hint_attune': dict(lines=["The waystone first. Touch it, and it'll learn your name."], options=[END]),
            'hint_cast': dict(lines=["Go on, let the Edict out. Whatever it does, it's yours. The camp has seen worse."], options=[END]),
            'hint_marta': dict(lines=["Marta's at the forge, west of the dais. Follow the sound of hammering. [amused] And the swearing."], options=[END]),
            'hint_training': dict(lines=["Marta will have you rolling in the dirt by now. Good. Dodge first, parry second, and pride last of all."], options=[END]),
            'hint_journal': dict(lines=["Open your journal. It writes down everything the world tells you, so you don't have to."], options=[END]),
            'hint_bram': dict(lines=["Bram's at the supply stall, east of the dais. He'll teach you what a Mark is worth. To the copper."], options=[END]),
            'chapter_one': dict(do=['complete:report_prologue'], lines=[
                "You're ready, then. Good. The camp can only hold you so long, and the world won't wait.",
                "Take work from the Warden's Board, there by the dais. Coin, reputation, and a reason to see the wilds.",
                "Then follow the tremors. Where the ground hums, an Edict has slipped loose. Bind it before it binds something else.",
                "Find a shrine on the Pilgrim's road, and touch the waystone at one of the old landmarks. Then come back and tell me everything.",
            ], options=[opt("What's the light in the sky?", 'the_light'), opt("I'll go.", '#end')]),
            'the_light': dict(lines=[
                "The waystones answer the ones they know. While you're on the road, a pillar of light stands over the place you're bound for.",
                "Press G to change which place it marks, if you're chasing more than one thing at once.",
            ], options=[opt("Thank you, Isolde.", '#end')]),
            'hint_listening': dict(lines=["The Board, the tremor, the shrine, the landmark. One at a time, or all at once. Your mark will show you."],
                                   options=[TRAVEL, END]),
            'chapter_two': dict(do=['complete:report_listening'], lines=[
                "So it's true. An Edict came to your hand, and the shrine didn't turn you away.",
                "Then I'll tell you what the Wardens don't tell everyone. Three old powers wait out there. The Bell-Ringer in the Hollow Cathedral. The King of Seconds, deep in his vault. And the Pilgrim, who walks and never arrives.",
                "They're why the world is stuck. Free them, or face them. Either way, it starts to move again. [quietly] And whatever they leave behind is yours to carry.",
            ], options=[opt("Where do I find them?", 'find_three'), opt("I'll find them.", '#end')]),
            'find_three': dict(lines=[
                "The cathedral lies south and west, under a bell nobody remembers hearing. The vault goes down a long way, past the old roads.",
                "The Pilgrim you'll hear before you see. Its footsteps wear roads into the grass. Wren can mark any of them for you.",
            ], options=[opt("Thank you.", '#end')]),
            'hint_three': dict(lines=["The Bell-Ringer, the King and the Pilgrim. Rest when you need to. The camp will always be here."],
                               options=[TRAVEL, END]),
            'chapter_three': dict(do=['complete:report_three'], lines=[
                "[quietly] Listen. The bell is quiet. The clocks have stopped arguing. And somewhere, a stone pilgrim is finally sitting down.",
                "But the world has five more locks on it. Five Wardens, set to guard the lands when the old powers fell asleep. They never stopped guarding.",
                "A samurai of ash in the blossom country. A lich who will not stay dead. A colossus in the white waste. A sentinel under the sun. And a gilded warden who judges everyone who passes.",
                "Each one has a trial before it will even face you. The keepers know the ways in. Ask them.",
            ], options=[opt("The samurai?", 'shogun_hint'), opt("The lich?", 'lich_hint'), opt("The colossus?", 'colossus_hint'),
                        opt("The sentinel?", 'sentinel_hint'), opt("The gilded warden?", 'warden_hint'), opt("I'll begin.", '#end')]),
            'shogun_hint': dict(lines=["Go to the Lantern Dojo in Petalfall. His last disciple still keeps it. Win the Duel of Lanterns and you'll earn the right to challenge him."],
                                options=[opt("And the others?", 'chapter_three_menu'), END]),
            'lich_hint': dict(lines=["Sister Ines has been reading the crypt records. Three crypts, three phylacteries. Break them all, or he'll simply get up again."],
                              options=[opt("And the others?", 'chapter_three_menu'), END]),
            'colossus_hint': dict(lines=["Torvald has seen its tracks. If you've hunted with him, you know how to follow them. Don't lose the trail in the snow."],
                                  options=[opt("And the others?", 'chapter_three_menu'), END]),
            'sentinel_hint': dict(lines=["Rashid's people tell of a temple under the dunes that only opens to the sun. You'll need to bend the light."],
                                  options=[opt("And the others?", 'chapter_three_menu'), END]),
            'warden_hint': dict(lines=["The Gilded Vault won't open without five glyph tablets. They're scattered through the old places. Wren has a list. [amused] Of course she does."],
                                options=[opt("And the others?", 'chapter_three_menu'), END]),
            'chapter_three_menu': dict(lines=["Ask me anything. I'll tell you what I know."],
                                       options=[opt("The samurai?", 'shogun_hint'), opt("The lich?", 'lich_hint'), opt("The colossus?", 'colossus_hint'),
                                                opt("The sentinel?", 'sentinel_hint'), opt("The gilded warden?", 'warden_hint'), END]),
            'hint_wardens': dict(lines=["Five Wardens. Take them one at a time. And come home between them, will you? [softly] I worry."],
                                 options=[opt("Remind me where they are.", 'chapter_three_menu'), TRAVEL, END]),
            'epilogue': dict(do=['complete:report_wardens'], lines=[
                "[softly] You did it. All of it. Do you feel that? The wind's changed. The waystones are singing a new song.",
                "When you first woke here, the world was holding its breath. Now it's breathing again, and it's because of you.",
                "Whatever you do next, build, wander, rest, it'll remember you kindly. [warmly] And so will I.",
            ], options=[opt("It was an honour, Warden.", '#end')]),
            'stew': dict(do=['side:meal:advance'], lines=[
                "[surprised] Is that Bram's stew? You made it? [laughs softly] Thank you. I'll pretend I wasn't hungry.",
            ], options=[opt("Enjoy it.", '#end')]),
            'after': dict(lines=["The waystones sing different songs now. Happier ones, I think. Safe roads, traveller."], options=[TRAVEL, END]),
            'idle': dict(lines=["Safe roads, traveller. The waystones remember you."], options=[TRAVEL, END]),
        },
    },
    'marta': {
        'entry': [
            (['obj=meet_marta'], 'training'),
            (['done:meet_isolde', '!done:meet_marta'], 'training'),
            (['obj=dodge'], 'hint_dodge'),
            (['obj=parry'], 'hint_parry'),
            (['side:forge=0', 'weapon:ready'], 'forge_smith'),
            (['side:forge=0'], 'forge_progress'),
            (['side:forge=1'], 'forge_done'),
            (['!flag:met:marta'], 'meet'),
            ([], 'greet'),
        ],
        'nodes': {
            'training': dict(do=['complete:meet_marta'], lines=[
                "So Isolde sent me another one. Good. Mind the sparks. Marta Ironvein, smith to the Wardens.",
                "Out there, things hit hard and they don't take turns. So you'll learn two things before you leave my yard.",
                "First, the roll. Tap the dodge key, Left Alt unless you've changed it, and you'll roll clear. For a breath, nothing can touch you.",
                "Second, the parry. Raise a shield, or your blade, the instant before a blow lands, and it'll bounce right back at them.",
                "The dummy in the yard swings slow and honest. Roll under one swing, then parry the next. [impatient] Go on.",
            ], options=[opt("Right.", '#end')]),
            'hint_dodge': dict(lines=["Roll. When the dummy swings, tap dodge and get out from under it. Don't think. Roll."], options=[END]),
            'hint_parry': dict(lines=["Now the parry. Block the instant before it hits. Too early and you're just holding a shield. [dryly] Too late and you're holding your nose."],
                               options=[END]),
            'meet': dict(lines=[
                "Mind the sparks. Marta Ironvein, smith to the Wardens.",
                "The beasts out here grow strange parts. Bring them to me with a few Marks, and I'll make you something worth swinging.",
            ], options=[opt("Show me what you make.", '#shop'), opt("What can those parts become?", 'parts'), END]),
            'greet': dict(lines=["Back for steel? Good. Steel's always the answer. [beat] Usually."],
                          options=[opt("Show me what you make.", '#shop'), opt("What can those parts become?", 'parts'),
                                   opt("Can a weapon grow stronger?", 'forge_offer', ['!side:forge:started', 'chapter>=1']), END]),
            'parts': dict(lines=[
                "Ember cores from the ashen husks make a blade that burns. Crystal from the shardlings sings when you swing it.",
                "Lurker fangs make a hook that drags, gear springs a maul that kicks, rime cores a spear that bites cold. Bring me the pieces.",
            ], options=[opt("Show me what you make.", '#shop'), END]),
            'forge_offer': dict(lines=[
                "That blade of yours has some miles on it. Weapons remember, out here. Every fight. Every kill. Some of them start to want more.",
                "Bring me a weapon that's ready to wake, and the parts it asks for, and we'll see what it wants to become. You'll swing the hammer, mind. I don't do all the work.",
            ], options=[opt("How will I know it's ready?", 'forge_ready', do=['side:forge:start']), opt("I'll be back.", '#end', do=['side:forge:start'])]),
            'forge_ready': dict(lines=["When it's ready, it hums. You'll see it in the journal, and on the blade. Then bring it here with what it asks for."],
                                options=[END]),
            'forge_progress': dict(lines=["No hum yet. Keep fighting. Weapons are like people. They grow when they're used."],
                                   options=[opt("Show me what you make.", '#shop'), END]),
            'forge_smith': dict(lines=[
                "It's humming. I can hear it from here. To the anvil, then.",
                "Strike when the metal glows gold. Not before. Not after. [grins] And keep your fingers where I can see them.",
            ], options=[opt("To the anvil.", '#end', do=['minigame:smithing']), opt("Not yet.", '#end')]),
            'forge_done': dict(do=['side:forge:finish'], lines=[
                "There. Feel the weight change? That's not the steel. That's the weapon deciding it likes you.",
                "Bring me any weapon that hums, any time. The anvil's yours.",
            ], options=[opt("Wake another weapon.", '#end', ['weapon:ready'], ['minigame:smithing']), opt("Show me what you make.", '#shop'), END]),
        },
    },
    'bram': {
        'entry': [
            (['obj=meet_bram'], 'meet'),
            (['obj=trade'], 'hint_trade'),
            (['done:meet_isolde', '!done:meet_bram'], 'meet'),
            (['!done:meet_isolde', '!flag:met:bram'], 'meet_early'),
            (['side:meal=0', 'has:minecraft:beef*2', 'has:minecraft:carrot*3', 'has:minecraft:potato*2', 'has:afterworld:ember_core'], 'meal_cook'),
            (['side:meal=0'], 'meal_progress'),
            (['side:meal=1'], 'meal_cook_again'),
            (['side:meal=2'], 'meal_deliver'),
            (['side:meal=3'], 'meal_done'),
            ([], 'greet'),
        ],
        'nodes': {
            'meet': dict(do=['complete:meet_bram', 'wallet:25'], lines=[
                "Isolde sent you? Then you're welcome at my stall. Name's Bram. I keep this camp fed, lit and stitched together.",
                "Here's twenty-five Marks to start you off. Nobody walks out of my camp with an empty purse. [gruff] That's not charity, mind. It's an investment.",
                "Now. Prices out here move like weather. Sell me the same thing ten times and I'll pay you less each time. Bring me something rare from far away and I'll pay you more.",
                "Go on, sell me something. Anything. Then buy something you need. That's the whole trick of it.",
            ], options=[opt("Show me your wares.", '#shop'), opt("Thanks, Bram.", '#end')]),
            'hint_trade': dict(lines=["Sell me something, then buy something. Open my stall and have a look. Go on."], options=[opt("Show me your wares.", '#shop'), END]),
            'meet_early': dict(lines=["A new face. Name's Bram. Quartermaster. Isolde will want to see you first, mind. She's by the waystone."],
                               options=[opt("Show me your wares.", '#shop'), END]),
            'greet': dict(lines=["Back again? Good. Let's see what you need."],
                          options=[opt("Show me your wares.", '#shop'), opt("How do prices work?", 'prices'),
                                   opt("Got any work?", 'meal_offer', ['!side:meal:started']), END]),
            'prices': dict(lines=[
                "Every merchant pays more for what's scarce where they stand. Ember glass is cheap where it's made, and dear in the snow.",
                "Carry goods between the keepers and you'll make a fortune. Or lose your boots to a lurker. [chuckles] That's trade.",
                "And don't sell all your fangs to one stall in one day. I'll stop wanting them. So will everyone.",
            ], options=[opt("Show me your wares.", '#shop'), END]),
            'meal_offer': dict(lines=[
                "You know what this camp's missing? A proper meal. Not jerky. Not bread. A stew that remembers its grandmother.",
                "Bring me two raw beef, three carrots, two potatoes, and an ember core for the warmth. I'll teach you to cook it yourself.",
            ], options=[opt("I'll bring them.", '#end', do=['side:meal:start']), opt("Not now.", '#end')]),
            'meal_progress': dict(lines=["Two beef, three carrots, two potatoes and an ember core. [gruff] Mind you don't eat them on the way."],
                                  options=[opt("Show me your wares.", '#shop'), END]),
            'meal_cook': dict(lines=[
                "You've got it all? Good. To the pot.",
                "Add each thing when the broth tells you, and keep it stirring. A stew's like a camp. Neglect it and it burns.",
            ], options=[opt("Let's cook.", '#end', do=['minigame:cooking']), opt("In a moment.", '#end')]),
            'meal_cook_again': dict(lines=["The pot's still warm. Give it another go."], options=[opt("Let's cook.", '#end', do=['minigame:cooking']), END]),
            'meal_deliver': dict(lines=["Now take a bowl to Isolde. She forgets to eat when she's worrying, and she's always worrying."], options=[END]),
            'meal_done': dict(do=['side:meal:finish', 'give:afterworld:bram_purse_knot'], lines=[
                "She ate it? All of it? [laughs] Ha! Here. Take this purse knot. Coins like it. They stay.",
            ], options=[opt("Thanks, Bram.", '#end')]),
        },
    },
    'wren': {
        'entry': [
            (['!flag:met:wren'], 'meet'),
            (['side:edges=1'], 'edges_done'),
            (['side:edges=0'], 'edges_progress'),
            ([], 'greet'),
        ],
        'nodes': {
            'meet': dict(lines=[
                "Oh! A new face. Wren Halloway, Cartographers' Guild. I map the places that won't sit still.",
                "If you're looking for something out there, I can probably mark it on your map. Probably. And I keep the Guild Ledger, so your Marks can sleep somewhere safer than your pockets.",
            ], options=[opt("Mark something for me.", 'marks'), opt("The Guild Ledger.", '#bank'), opt("Tell me about the landmarks.", 'landmarks'),
                        opt("What do you sell?", '#shop'), END]),
            'greet': dict(lines=["Back from the edges? Tell me everything. [excited] Or at least, tell me where."],
                          options=[opt("Mark something for me.", 'marks'), opt("The Guild Ledger.", '#bank'),
                                   opt("Any surveying work?", 'edges_offer', ['!side:edges:started', 'chapter>=1']),
                                   opt("Where are the glyph tablets?", 'tablets', ['chapter>=3']),
                                   opt("What do you sell?", '#shop'), END]),
            'marks': dict(lines=["Of course. What are you after?"], options=[
                opt("The nearest landmark.", 'marked', do=['mark:landmark']),
                opt("A loose Edict.", 'marked', do=['mark:anomaly']),
                opt("A shrine on the Pilgrim's road.", 'marked', do=['mark:shrine']),
                opt("The Hollow Cathedral.", 'marked', ['chapter>=2'], ['mark:cathedral']),
                opt("The Vault of Seconds.", 'marked', ['chapter>=2'], ['mark:vault']),
                opt("A village.", 'marked', do=['mark:village']),
                opt("Somewhere to test my blade.", 'ladder', do=['mark:ladder'])]),
            'ladder': dict(lines=[
                "[lowers voice] The Warden's Ladder. An old gate, dark stone, a stone that glows. Step through and you're somewhere else entirely.",
                "Fifty floors, they say, each one meaner than the last. The Guild has never mapped past the fifth. [brightly] I've marked the nearest gate for you!",
            ], options=[opt("Mark something else.", 'marks'), opt("Thanks, Wren.", '#end')]),
            'marked': dict(lines=["There. It's on your map now. Follow the light, and mind the edges. The edges are where maps get hungry."],
                           options=[opt("Mark something else.", 'marks'), opt("Thanks, Wren.", '#end')]),
            'landmarks': dict(lines=[
                "There's a gazebo where the moon comes down to rest. A tower crowned with a crescent. A mushroom house frozen into the ice. And an airship that never quite lands.",
                "Each has a keeper, and a waystone. Touch it, and you can come back whenever you like. It's the closest thing to cheating the Guild allows.",
            ], options=[opt("Mark the nearest one.", 'marked', do=['mark:landmark']), END]),
            'edges_offer': dict(lines=[
                "You've got good boots and a curious face. How'd you like to do some real surveying? The Guild pays, and I pay better.",
                "Take this sextant. Find three landmarks, stand by their waystones and take a sighting. The trick is the wind. Hold your breath, and wait for the needle to settle.",
                "Bring me three good sightings and I'll give you something that makes the whole world smaller. [laughs] Well. Easier to fall off.",
            ], options=[opt("I'll take it.", '#end', do=['side:edges:start', 'give:afterworld:survey_sextant']), opt("Maybe later.", '#end')]),
            'edges_progress': dict(lines=["Three sightings, three landmarks. The sextant counts them for you. Mind the needle."],
                                   options=[opt("Mark the nearest landmark.", 'marked', do=['mark:landmark']), opt("The Guild Ledger.", '#bank'), END]),
            'edges_done': dict(do=['side:edges:finish', 'give:afterworld:moth_silk_glider'], lines=[
                "Three! [delighted] Beautiful. These are the best sightings I've seen since, well, since mine.",
                "Here. Moth-silk. Hold your jump while you fall and it'll catch the wind. The moths spun it, so be nice to moths.",
            ], options=[opt("Thank you, Wren.", '#end')]),
            'tablets': dict(lines=[
                "The five glyph tablets? I've a list. Of course I have a list.",
                "One in the sunken bog temple. One in the frozen mage tower. One in the buried obelisk, out in the dunes. One in the overgrown ziggurat. And one in a moonlit clearing in the Duskpine. I'll mark whichever is nearest.",
            ], options=[opt("Mark the nearest one.", 'marked', do=['mark:tablet']), END]),
        },
    },
    'rook': {
        'entry': [
            (['!flag:met:rook'], 'meet'),
            ([], 'greet'),
        ],
        'nodes': {
            'meet': dict(lines=[
                "[cheerfully] Ah! A climber! Welcome to Rook's Landing. Warm fire, fair prices, no refunds.",
                "I've been on this Ladder longer than your grandmother's grandmother. Never reached the top. Never fell off, either. [chuckles] I take the back stairs.",
            ], options=[opt("Show me your wares.", '#shop'), opt("What's ahead?", 'ahead'), opt("Who are you, really?", 'who'), END]),
            'greet': dict(lines=["[warmly] Back again! Sit, sit. The fire doesn't charge."],
                          options=[opt("Show me your wares.", '#shop'), opt("What's ahead?", 'ahead'), opt("Who are you, really?", 'who'), END]),
            'ahead': dict(lines=[
                "Five floors to the next landing. Every tenth, a Warden's echo waits. Not the Warden, mind. Just the memory of one. [whispers] Memories hit just as hard.",
                "Stock up. Drink something red. And if the Ladder throws you out, come back. It always lets you back in.",
            ], options=[opt("Show me your wares.", '#shop'), END]),
            'who': dict(lines=[
                "The Edict-writers built this place to prove their Wardens. I swept the floors. [laughs] Then they left, and I stayed.",
                "Somebody has to keep the fires lit. And somebody has to sell you potions at a very reasonable markup.",
            ], options=[opt("Show me your wares.", '#shop'), END]),
        },
    },
    'selene': {
        'entry': [
            (['!flag:met:selene'], 'meet'),
            (['side:stars=0', 'has:afterworld:star_chart_fragment*3'], 'stars_assemble'),
            (['side:stars=0'], 'stars_progress'),
            (['side:stars=1', 'time:day'], 'stars_wait'),
            (['side:stars=1'], 'stars_dials'),
            (['side:stars=2'], 'stars_done'),
            ([], 'greet'),
        ],
        'nodes': {
            'meet': dict(lines=[
                "[whispers] Hush. The moon is listening. I'm Selene. I keep the gazebo, and I count the stars that fall.",
                "Every night there is one fewer. I think someone is collecting them.",
            ], options=[opt("What happens to a fallen star?", 'lore'), opt("Can I help?", 'stars_offer', ['!side:stars:started']),
                        opt("May I trade?", '#shop'), END]),
            'greet': dict(lines=["The sky is kind tonight. Or it is pretending to be."],
                          options=[opt("What happens to a fallen star?", 'lore'), opt("Can I help?", 'stars_offer', ['!side:stars:started']),
                                   opt("May I trade?", '#shop'), END]),
            'lore': dict(lines=[
                "Light remembers. A fallen star is a memory the sky let go of.",
                "The Bureau keeps some of them in jars, I'm told. I hope they whisper to them. [sadly] Stars get lonely.",
            ], options=[opt("May I trade?", '#shop'), END]),
            'stars_offer': dict(lines=[
                "There was a constellation here once. The Lost Moth. Its stars fell one by one, and with them went the gazebo's song.",
                "The old star chart was torn into three. One piece lies in the crystal observatory in the Shardfields. One at the aurora shrine in the Glimmerfrost. And one in the crater where a star once fell, out on the Ember Steppe.",
                "Bring the pieces to me, and we will put the Moth back in the sky.",
            ], options=[opt("I'll find them.", '#end', do=['side:stars:start']), opt("Not now.", '#end')]),
            'stars_progress': dict(lines=["Three pieces. The observatory, the aurora shrine, the crater. The sky can wait. It is very patient."],
                                   options=[opt("May I trade?", '#shop'), END]),
            'stars_assemble': dict(do=['take:afterworld:star_chart_fragment*3', 'side:stars:advance'], lines=[
                "[breathless] You found them. All three. Let me... yes. There. The Lost Moth.",
                "Now the hard part. The dials on the gazebo's floor turn the old stars. Turn them until the sky above matches the chart. But only at night. The stars are shy.",
            ], options=[opt("I'll wait for nightfall.", '#end')]),
            'stars_wait': dict(lines=["Wait for the night. Then turn the dials. [softly] I will keep watch with you."], options=[END]),
            'stars_dials': dict(lines=["The dials are waiting. Turn each one until its star sits where the chart says. You will feel it settle."],
                                options=[opt("Begin.", '#end', do=['minigame:stars']), END]),
            'stars_done': dict(do=['side:stars:finish', 'give:afterworld:featherstep_boots', 'give:afterworld:moonsilver_ingot*4'], lines=[
                "[softly] Do you hear it? The gazebo is singing again. The Moth is home.",
                "These were made from the last of the fallen starlight. Featherstep boots. Jump, and then jump again, as if the air were a stair.",
            ], options=[opt("Thank you, Selene.", '#end')]),
        },
    },
    'odo': {
        'entry': [
            (['!flag:met:odo'], 'meet'),
            (['side:wayfarer=0', 'has:afterworld:gear_spring*6', 'has:afterworld:clockwork_fragment*2'], 'wayfarer_parts'),
            (['side:wayfarer=0'], 'wayfarer_progress'),
            (['side:wayfarer=1'], 'wayfarer_race'),
            (['side:wayfarer=2'], 'wayfarer_done'),
            ([], 'greet'),
        ],
        'nodes': {
            'meet': dict(lines=[
                "[booming] Ahoy, groundling! Captain Odo Brassmere, of the Wayfarer. Best ship in the sky. [beat] Only ship in the sky, come to think of it.",
                "She's moored for repairs. Bring me gear springs and clockwork, and I'll make it worth your while.",
            ], options=[opt("Tell me about the sky.", 'sky'), opt("What needs fixing?", 'wayfarer_offer', ['!side:wayfarer:started']),
                        opt("Let's trade.", '#shop'), END]),
            'greet': dict(lines=["Wind's from the west today. Good sailing weather, if one had a sail."],
                          options=[opt("Tell me about the sky.", 'sky'), opt("What needs fixing?", 'wayfarer_offer', ['!side:wayfarer:started']),
                                   opt("Fly me somewhere.", 'charter', ['side:wayfarer:finished']), opt("Let's trade.", '#shop'), END]),
            'sky': dict(lines=[
                "There are whales up there, you know. Great gentle things. They sing at dawn.",
                "Lovely creatures. Just don't let one sit on your balloon. [laughs] Ask me how I know.",
            ], options=[opt("Let's trade.", '#shop'), END]),
            'wayfarer_offer': dict(lines=[
                "Here's the trouble. The Wayfarer's rudder springs are shot, and her clock-heart is skipping. I need six gear springs and two clockwork fragments.",
                "Gearhounds carry springs in their bellies, poor things. Clockwork you'll find wherever the King's toys wander.",
            ], options=[opt("I'll get them.", '#end', do=['side:wayfarer:start']), opt("Not now.", '#end')]),
            'wayfarer_progress': dict(lines=["Six springs and two fragments, groundling. The sky won't wait forever. [beat] Actually, it will. But I won't."],
                                      options=[opt("Let's trade.", '#shop'), END]),
            'wayfarer_parts': dict(do=['take:afterworld:gear_spring*6', 'take:afterworld:clockwork_fragment*2', 'side:wayfarer:advance',
                                       'give:afterworld:prototype_wing'], lines=[
                "[laughs] Look at that! Springs, clockwork, and not a single finger missing. Splendid.",
                "Now, before I trust her with passengers, someone has to test the wings. Climb the tower, take this practice wing, and fly through every ring before the sand runs out.",
            ], options=[opt("Start the race.", '#end', do=['minigame:race']), opt("In a moment.", '#end')]),
            'wayfarer_race': dict(lines=["The rings, groundling! Top of the mooring tower, jump, and steer for the lights. Sixty seconds, all eight rings."],
                                  options=[opt("Start the race.", '#end', do=['minigame:race']), END]),
            'wayfarer_done': dict(do=['side:wayfarer:finish', 'give:afterworld:wayfarers_hook'], lines=[
                "[delighted] Ha! You fly like you were hatched up there! Here. My old hook. Point it at anything and it'll pull you there, or pull it to you. [beat] Don't point it at me.",
                "And any time you want to go somewhere far, come and see me. For a few Marks, the Wayfarer will take you.",
            ], options=[opt("Thank you, Captain.", '#end')]),
            'charter': dict(lines=["Where to, groundling? Thirty Marks a leg, and I throw in the view for free."],
                            options=[opt("Show me the map.", '#charter'), END]),
        },
    },
    'mab': {
        'entry': [
            (['!flag:met:mab'], 'meet'),
            (['side:brew=0', 'has:afterworld:lurker_fang*3', 'has:afterworld:bog_amber', 'has:afterworld:moth_dust*2'], 'brew_cook'),
            (['side:brew=0'], 'brew_progress'),
            (['side:brew=1'], 'brew_cook_again'),
            (['side:brew=2'], 'brew_test'),
            (['side:brew=3'], 'brew_done'),
            ([], 'greet'),
        ],
        'nodes': {
            'meet': dict(lines=[
                "[cackles] Heh. Another one wading in. I'm Mab. The fen is mine, and I'm the fen's.",
                "Mind the lurkers. They look like logs, right up until they don't.",
            ], options=[opt("Any advice?", 'advice'), opt("I'd like to cross the water dry.", 'brew_offer', ['!side:brew:started']),
                        opt("What are you brewing?", '#shop'), END]),
            'greet': dict(lines=["Still breathing? The fen likes you, then. [sly] Or it's saving you for later."],
                          options=[opt("Any advice?", 'advice'), opt("I'd like to cross the water dry.", 'brew_offer', ['!side:brew:started']),
                                   opt("What are you brewing?", '#shop'), END]),
            'advice': dict(lines=["Brew before you fight, not after. And never answer the bog when it calls your name."],
                           options=[opt("What are you brewing?", '#shop'), END]),
            'brew_offer': dict(lines=[
                "Want to cross my fen without wet boots? Heh. There's a charm for that. But a charm needs a brew, and a brew needs a fool to fetch for it.",
                "Three lurker fangs, one lump of bog amber, and two pinches of moth dust. Bring them, and mind the logs.",
            ], options=[opt("I'll fetch them.", '#end', do=['side:brew:start']), opt("Not now.", '#end')]),
            'brew_progress': dict(lines=["Fangs, amber, dust. I'll not repeat myself a third time. [cackles] I will, actually. Fangs. Amber. Dust."],
                                  options=[opt("What are you brewing?", '#shop'), END]),
            'brew_cook': dict(do=['take:afterworld:lurker_fang*3', 'take:afterworld:bog_amber', 'take:afterworld:moth_dust*2', 'side:brew:advance'], lines=[
                "Good, good. Now, the cauldron. Drop each one in when the brew turns the right colour, and stir with the bubbles, not against them.",
            ], options=[opt("Start brewing.", '#end', do=['minigame:brewing']), opt("In a moment.", '#end')]),
            'brew_cook_again': dict(lines=["The cauldron's still hot. Again, and gently this time."], options=[opt("Start brewing.", '#end', do=['minigame:brewing']), END]),
            'brew_test': dict(lines=[
                "Drink it, and walk out to the dead tree in the middle of the water and back. If you come back dry, the charm is yours.",
                "If you come back wet, well. [cackles] It's a learning experience.",
            ], options=[opt("Drink it.", '#end', do=['minigame:waterwalk']), END]),
            'brew_done': dict(do=['side:brew:finish', 'give:afterworld:mirewalk_charm'], lines=[
                "[cackles] Dry as a bone! Take the charm. It'll hold you up on water, and on lava that's had a good long think about cooling.",
                "Don't make a habit of the lava.",
            ], options=[opt("Thank you, Mab.", '#end')]),
        },
    },
    'torvald': {
        'entry': [
            (['!flag:met:torvald'], 'meet'),
            (['side:hunt=0'], 'hunt_progress'),
            (['side:hunt=1'], 'hunt_killed'),
            (['side:hunt=2'], 'hunt_contest'),
            (['side:hunt=3'], 'hunt_done'),
            ([], 'greet'),
        ],
        'nodes': {
            'meet': dict(lines=[
                "Stranger. Sit by the fire if you like. Torvald. I hunt what hunts the valley.",
                "The rimehorns are restless this year. And something walks the frozen peaks. Taller than the trees.",
            ], options=[opt("Something taller than trees?", 'big'), opt("Need a hunter?", 'hunt_offer', ['!side:hunt:started']),
                        opt("Can we trade?", '#shop'), END]),
            'greet': dict(lines=["The fire's warm. The meat's fresh. Sit."],
                          options=[opt("Something taller than trees?", 'big'), opt("Need a hunter?", 'hunt_offer', ['!side:hunt:started']),
                                   opt("Can we trade?", '#shop'), END]),
            'big': dict(lines=[
                "Ice and white fur, and horns like a cathedral's ribs. The old songs call it the Rimeback.",
                "If you ever see the snow stand up, run. [beat] Then run some more.",
            ], options=[opt("Can we trade?", '#shop'), END]),
            'hunt_offer': dict(lines=[
                "The rimehorn alpha has been taking the young ones from the valley. I am old, and my knees are older. You are neither.",
                "Follow its tracks from the lodge. They're deep and wide, and the snow fills them fast. Lose them, and you lose it.",
            ], options=[opt("I'll hunt it.", '#end', do=['side:hunt:start', 'minigame:tracking']), opt("Not now.", '#end')]),
            'hunt_progress': dict(lines=["Its tracks start at the lodge. Watch the snow. The fresh ones still have their edges."],
                                  options=[opt("Show me the trail again.", '#end', do=['minigame:tracking']), END]),
            'hunt_killed': dict(do=['side:hunt:advance'], lines=[
                "You brought down the alpha? [laughs] Then you've earned a contest.",
                "Every hunter at this lodge has shot the targets. Hit six of eight, and I'll call you hunter too.",
            ], options=[opt("Let's shoot.", '#end', do=['minigame:archery']), opt("Later.", '#end')]),
            'hunt_contest': dict(lines=["The targets are set. Six of eight. Breathe out before you loose."],
                                 options=[opt("Let's shoot.", '#end', do=['minigame:archery']), END]),
            'hunt_done': dict(do=['side:hunt:finish', 'give:afterworld:climbing_claws', 'give:afterworld:wayfarers_whistle'], lines=[
                "Six of eight. Good. Better than I did, my first winter.",
                "Take these claws. They'll let you climb where the rimehorns climb. And the stag, he's taken a liking to you. Whistle, and he'll come.",
                "One more thing. The big tracks, the ones as wide as a cart. Those aren't rimehorn. If you ever follow them, go well armed.",
            ], options=[opt("I will.", '#end')]),
        },
    },
    'ines': {
        'entry': [
            (['!flag:met:ines'], 'meet'),
            (['side:bell=0', 'has:afterworld:bell_rope'], 'bell_rope'),
            (['side:bell=0'], 'bell_progress'),
            (['side:bell=1'], 'bell_listen'),
            (['side:bell=2'], 'bell_done'),
            ([], 'greet'),
        ],
        'nodes': {
            'meet': dict(lines=[
                "Peace, traveller. I am Sister Ines, of the Morrow Court. We remember the dead, so that they may rest.",
                "The bell of this chapel drowned long ago. Still, some nights, I hear it ring.",
            ], options=[opt("Can you tend my wounds?", 'heal', do=['heal']), opt("Who rings it?", 'bell_offer', ['!side:bell:started']),
                        opt("What do you keep here?", '#shop'), END]),
            'greet': dict(lines=["Welcome back. The candles burned steady while you were gone."],
                          options=[opt("Can you tend my wounds?", 'heal', do=['heal']), opt("Who rings the bell?", 'bell_offer', ['!side:bell:started']),
                                   opt("What do the crypt records say?", 'lich', ['chapter>=3']), opt("What do you keep here?", '#shop'), END]),
            'heal': dict(lines=["There. Rest a moment. The road will wait for you. It always does."], options=[END]),
            'bell_offer': dict(lines=[
                "Some nights a voice hums near the altar. A child's voice, I think. The bell-ringer's apprentice, who drowned with the bell.",
                "The bell's rope was carried off by the tide. It lies in the drowned shrine on the coast, if the old records are right. Bring it back, and perhaps she can finish her song.",
            ], options=[opt("I'll find the rope.", '#end', do=['side:bell:start']), opt("Not now.", '#end')]),
            'bell_progress': dict(lines=["The drowned shrine, on the coast. Go carefully. The sea keeps what it takes."], options=[END]),
            'bell_rope': dict(do=['take:afterworld:bell_rope', 'side:bell:advance'], lines=[
                "The rope! It's still whole. Bring it to the altar bells, and listen.",
                "When the humming starts, ring the small bells in the same order. [softly] Gently.",
            ], options=[opt("I'll listen.", '#end', do=['minigame:melody'])]),
            'bell_listen': dict(lines=["Ring them as she hums. If you lose the tune, she will hum it again."],
                                options=[opt("Listen again.", '#end', do=['minigame:melody']), END]),
            'bell_done': dict(do=['side:bell:finish', 'give:afterworld:skyward_bell'], lines=[
                "[softly] She finished it. Did you hear? The whole song.",
                "This bell is hers. Ring it, and it will lift you, the way a song lifts a heavy heart. Up, and up.",
            ], options=[opt("Thank you, Sister.", '#end')]),
            'lich': dict(lines=[
                "The records speak of a lich who hid his heart in three places. A barrow on the Vesper Downs. An ossuary gate in the Pale Reach. And his own necropolis.",
                "Break all three, or he will not stay down. [quietly] The Court will pray for you. It helps more than you'd think.",
            ], options=[END]),
        },
    },
    'rashid': {
        'entry': [
            (['!flag:met:rashid'], 'meet'),
            (['side:caravan=0', 'flag:clue:wagon', 'flag:clue:manifest', 'flag:clue:tracks'], 'caravan_deduce'),
            (['side:caravan=0'], 'caravan_progress'),
            (['side:caravan=1'], 'caravan_rescue'),
            (['side:caravan=2'], 'caravan_done'),
            ([], 'greet'),
        ],
        'nodes': {
            'meet': dict(lines=[
                "Welcome to the Hourglass, friend. Rashid, master of this caravan. The water is free. [playful] Everything else, we can discuss.",
            ], options=[opt("What is the obelisk for?", 'obelisk'), opt("You look worried.", 'caravan_offer', ['!side:caravan:started']),
                        opt("Let's discuss.", '#shop'), END]),
            'greet': dict(lines=["Ah, my friend returns. The sand remembers you, and so do I."],
                          options=[opt("What is the obelisk for?", 'obelisk'), opt("You look worried.", 'caravan_offer', ['!side:caravan:started']),
                                   opt("Let's discuss.", '#shop'), END]),
            'obelisk': dict(lines=[
                "It tells the hours with its shadow. But at noon, sometimes, it tells the wrong one.",
                "On those days the sand remembers things it shouldn't. We pack up early.",
            ], options=[opt("Let's discuss.", '#shop'), END]),
            'caravan_offer': dict(lines=[
                "Three days ago my sister's caravan left for the nomad camps of the Ember Steppe. Twelve camels, eight drivers, a cargo of spice. It never arrived.",
                "No sandstorm. No bandits seen. Just silence. Find out what happened to them, and you will have a friend in every market I trade in.",
            ], options=[opt("I'll find them.", '#end', do=['side:caravan:start', 'mark:caravan']), opt("Not now.", '#end')]),
            'caravan_progress': dict(lines=[
                "Start where they were last seen, the old road east of here. Look for anything out of place. Sand hides things, but it is a poor liar.",
            ], options=[opt("Mark the road for me.", '#end', do=['mark:caravan']), END]),
            'caravan_deduce': dict(lines=[
                "You found the wagon, the manifest, and the tracks. So. Where did they go? You saw what I did not.",
            ], options=[
                opt("Into the buried obelisk, hiding from something.", 'caravan_right', do=['side:caravan:advance', 'mark:obelisk']),
                opt("Bandits took them north.", 'caravan_wrong'),
                opt("They sold the cargo and ran.", 'caravan_wrong')]),
            'caravan_wrong': dict(lines=["[doubtful] Hm. That does not fit the tracks, my friend. Look at what you found again. Sand is a poor liar, remember."],
                                  options=[opt("Let me think again.", 'caravan_deduce'), END]),
            'caravan_right': dict(lines=["The obelisk! Of course. They hid from the storm-eaters. Go, quickly. If they hid there, something chased them in."],
                                  options=[opt("I'm going.", '#end')]),
            'caravan_rescue': dict(lines=["The buried obelisk, east and south. Bring them home, friend."], options=[END]),
            'caravan_done': dict(do=['side:caravan:finish', 'give:afterworld:caravan_seal'], lines=[
                "My sister. Alive. And most of the spice, which she says I care about more. [laughs] She is wrong. Mostly.",
                "Take this seal. Show it to any Guild merchant, and they will treat you as family. Which means slightly less robbery.",
            ], options=[opt("Thank you, Rashid.", '#end')]),
        },
    },
    'leyla': {
        'entry': [
            (['side:caravan=1'], 'found'),
            (['side:caravan=2'], 'waiting'),
            (['side:caravan:finished'], 'home'),
            ([], 'stranger'),
        ],
        'nodes': {
            'stranger': dict(lines=[
                "[whispering] Shh. Keep your voice down. The storm-eaters hunt by sound.",
                "If you're going back to the Hourglass, find a merchant called Rashid. Tell him his sister is alive, and very, very annoyed.",
            ], options=[END]),
            'found': dict(lines=[
                "You're not a storm-eater. [relieved] Rashid sent you? Of course he did. He'd send the whole Guild if it didn't cost him a coin.",
                "We hid down here when the sand opened and those things came out. Eight drivers, all alive. Mostly whole.",
            ], options=[
                opt("The way out is clear. Go now.", 'freed', ['clear:24'], ['side:caravan:advance', 'xp:40']),
                opt("Stay hidden. I'll clear the way first.", '#end', ['!clear:24']),
                END]),
            'freed': dict(lines=[
                "Then we walk at dusk. Tell my brother we're coming. [smiles] And that I counted the spice.",
            ], options=[opt("Safe road, Leyla.", '#end')]),
            'waiting': dict(lines=["We leave at dusk. Go on ahead, and tell Rashid to put the kettle on."], options=[END]),
            'home': dict(lines=["The road's quiet again. The sand owes you one, traveller. So do I."], options=[END]),
        },
    },
    'akane': {
        'entry': [
            (['!flag:met:akane'], 'meet'),
            (['side:duel=1'], 'duel_won'),
            ([], 'greet'),
        ],
        'nodes': {
            'meet': dict(lines=[
                "You walk into the Lantern Dojo with your boots on. Bold. I am Akane, the last disciple of this house.",
                "My master became the Ashen Shogun. Every lantern on these walls is a life he took. If you have come to challenge him, you must first face me.",
            ], options=[opt("Then I'll face you.", 'duel_rules', ['chapter>=3']), opt("Then I'll face you.", 'not_yet', ['!chapter>=3']),
                        opt("Tell me about your master.", 'master'), END]),
            'greet': dict(lines=["The lanterns flicker when you enter. They remember you."],
                          options=[opt("I'll face you.", 'duel_rules', ['chapter>=3', '!side:duel:finished']), opt("I'll face you.", 'not_yet', ['!chapter>=3']),
                                   opt("Tell me about your master.", 'master'), END]),
            'not_yet': dict(lines=[
                "[quietly] Not yet. You still smell of the camp's woodsmoke. He has burned a hundred who were braver than you look.",
                "When the Warden of the waystones speaks to you of the five Wardens, come back. The lanterns will still be burning. They always are.",
            ], options=[opt("Tell me about your master.", 'master'), END]),
            'master': dict(lines=[
                "He was the gentlest man I knew. He swore to guard the blossom country forever, and the world took him at his word.",
                "Forever is too long for a promise. It burned him to ash, and left the oath walking.",
            ], options=[END]),
            'duel_rules': dict(lines=[
                "The Duel of Lanterns has one rule. Only a perfect parry may strike. Any other blow, and the lanterns go dark.",
                "Turn my blade aside five times, and I will give you his challenge lantern. [quietly] And perhaps, peace.",
            ], options=[opt("Begin.", '#end', do=['side:duel:start', 'minigame:duel']), opt("Not yet.", '#end')]),
            'duel_won': dict(do=['side:duel:finish', 'give:afterworld:challenge_lantern'], lines=[
                "[breathing hard] Five. You turned my blade five times. Take the lantern.",
                "Light it at the dojo's heart, and he will come. Do not let him reach the hundredth lantern.",
            ], options=[opt("I won't.", '#end')]),
        },
    },
}

# ------------------------------------------------------------------ the great powers (cutscenes and fights)
BARKS = {
    'shogun': {
        'intro': ["You came for the lanterns. Every one is a life I took.", "[slowly] Add yours."],
        'phase2': ["Which of us is the ash? Choose."],
        'phase3': ["A hundred lanterns. [quietly] Light the last one."],
        'defeat': ["The wind is clean again.", "Take my lantern. Walk where I could not."],
    },
    'lich': {
        'intro': ["Ah. A visitor who still has a heartbeat. [amused] How quaint.", "Kneel, and I shall make it quick. Stand, and I shall make it last."],
        'phase2': ["My obelisks drink. You will tire long before they do."],
        'phase3': ["[roaring] Rise. All of you. RISE."],
        'revive': ["[laughs] Did you think death was something I had not already survived?"],
        'defeat': ["The phylacteries. You found them. [weakly] Clever.", "Then let me, at last, be still."],
    },
    'king': {
        'intro': ["Late. Everyone is always late. Shall we see how late you are?"],
        'bargain': ["A trade, then. A second of my power for every second you spare me. Tick, tock. Decide."],
        'defeat': ["[slowing] Tick. Tock. Tick."],
    },
    'bell': {
        'intro': ["[whispers] Ring. Must ring. Must..."],
        'name': ["[whispers] That was... my name. Yes. That was me."],
        'defeat': ["[whispers] Quiet. So quiet now."],
    },
    'warden': {
        'intro': ["None pass the Gilded Vault without the five glyphs. You carry them.", "[resonant] Then be judged."],
        'phase2': ["The shell remembers every blow. So do I."],
        'phase3': ["Judgment is not cruelty. It is accounting."],
        'defeat': ["The account is settled. Go, traveller. You are owed nothing, and owe nothing."],
    },
    'sentinel': {
        'intro': ["[booming] WHO... WAKES... THE SUN?"],
        'defeat': ["[grinding] Sleep... again..."],
    },
    'narrator': {
        'prologue': ["The world listens. It always has. Today, it heard you wake."],
        'chapter1': ["Chapter One. The Listening World. Where the ground hums, a rule has slipped its leash."],
        'chapter2': ["Chapter Two. The Three Who Wait. A bell, a clock, and a pilgrim. Each one stuck. Each one waiting for you."],
        'chapter3': ["Chapter Three. Wardens of the Five Lands. The old guardians never stopped guarding. Not even when they should have."],
        'epilogue': ["And the world, at last, let out its breath."],
    },
}

CUE = re.compile(r'\[[^\]]*\]\s*')


def clean(text):
    """The subtitle: the line without its delivery cues."""
    return re.sub(r'\s+', ' ', CUE.sub('', text)).strip()


def load_timing():
    if os.path.exists(TIMING):
        with open(TIMING) as f:
            return json.load(f)
    return {}


_BEATS = None


def entry(who, key, text, timing):
    """A line for the game: the subtitle, its delivery cue (shown beside the speaker, like a stage
    direction), and its sound and typing beats if a recording has been imported."""
    global _BEATS
    if _BEATS is None:
        _BEATS = json.load(open(BEATS)) if os.path.exists(BEATS) else {}
    ticks = timing.get(f'{who}/{key}', 0)
    has = ticks > 0 and os.path.exists(os.path.join(SOUNDS, who, key + '.ogg'))
    m = re.search(r'\[([^\]]+)\]', text)
    out = dict(text=clean(text), sound=f'afterworld:va.{who}.{key}' if has else '', ticks=ticks if has else 0)
    if m:
        out['cue'] = m.group(1).strip()
    if has and f'{who}/{key}' in _BEATS:
        out['beats'] = _BEATS[f'{who}/{key}']
    return out


def main():
    timing = load_timing()
    os.makedirs(DATA, exist_ok=True)
    rows = []
    for npc, script in SCRIPT.items():
        nodes = {}
        for node_id, node in script['nodes'].items():
            lines = []
            for i, text in enumerate(node['lines']):
                key = f'{node_id}_{i}'
                lines.append(entry(npc, key, text, timing))
                rows.append((npc, key, text))
            out = dict(lines=lines)
            if node.get('do'):
                out['do'] = node['do']
            if node.get('options'):
                out['options'] = [dict({'text': t, 'go': g}, **({'if': w} if w else {}), **({'do': a} if a else {})) for (t, g, w, a) in node['options']]
            nodes[node_id] = out
        doc = dict(npc=npc, entry=[dict({'go': go}, **({'if': cond} if cond else {})) for cond, go in script['entry']], nodes=nodes)
        with open(os.path.join(DATA, npc + '.json'), 'w') as f:
            json.dump(doc, f, indent=1)
            f.write('\n')
    barks = {}
    for who, groups in BARKS.items():
        for group, lines in groups.items():
            out = []
            for i, text in enumerate(lines):
                key = f'{group}_{i}'
                out.append(entry(who, key, text, timing))
                rows.append((who, key, text))
            barks.setdefault(who, {})[group] = out
    with open(os.path.join(DATA, 'barks.json'), 'w') as f:
        json.dump(barks, f, indent=1)
        f.write('\n')
    write_voice_script(rows)
    voiced = sum(1 for (w, k, t) in rows if timing.get(f'{w}/{k}', 0) > 0)
    print(f'{len(SCRIPT)} people, {len(BARKS)} other voices, {len(rows)} lines ({voiced} recorded)')


def write_voice_script(rows):
    os.makedirs(os.path.join(VOICE_DIR, 'incoming'), exist_ok=True)
    with open(os.path.join(VOICE_DIR, 'lines.csv'), 'w', newline='') as f:
        w = csv.writer(f)
        w.writerow(['file', 'character', 'elevenlabs_voice', 'elevenlabs_voice_id', 'text_with_cues', 'subtitle', 'characters'])
        for who, key, text in rows:
            c = CAST[who]
            w.writerow([f'{who}__{key}.mp3', c['role'], c['el'][0], c['el'][1], text, clean(text), len(clean(text))])
    by = {}
    for who, key, text in rows:
        by.setdefault(who, []).append((key, text))
    total_chars = sum(len(clean(t)) for (_, _, t) in rows)
    out = ['# Afterworld — Voice Script', '',
           f'{len(rows)} lines, about {total_chars:,} characters of speech, across {len(by)} voices.', '',
           '## How to record and bring lines in', '',
           '1. Pick the voice listed for a character: the ElevenLabs library voice, or search Fish Audio with the hint.',
           '2. Generate each line. Keep the **[cues]**: ElevenLabs v3 reads them as audio tags. For Fish Audio, write them in round '
           'brackets instead, so `[softly]` becomes `(softly)`.',
           '3. Name each file exactly as given, e.g. `isolde__welcome_0.mp3` (mp3, wav, ogg or flac all work).',
           '4. Put the files in `voice/incoming/` and run `python3 tools/import_voices.py`. It trims silence, levels the volume, '
           'applies the character\'s treatment (the great powers are pitched down and given halls, metal or sand), converts to Ogg '
           'and wires the line into the game. Lines you haven\'t recorded keep their subtitles.',
           '5. `voice/lines.csv` has every line in one table, for batch tools.', '',
           'Model: **Eleven v3** for anything with cues (most lines); Multilingual v2 is steadier for long calm lines. '
           'Generate two or three takes of the important lines and keep the best.', '']
    for who, lines in by.items():
        c = CAST[who]
        out += ['---', '', f'## {c["role"]}', '', f'**Who they are:** {c["who"]}', '',
                f'- **ElevenLabs:** {c["el"][0]}' + (f' — `{c["el"][1]}`' if c['el'][1] else ''),
                f'- **Second choice:** {c["alt"][0]}' + (f' — `{c["alt"][1]}`' if c['alt'][1] else ''),
                f'- **Fish Audio search:** {c["fish"]}',
                f'- **Settings:** {c["settings"]}']
        if c['fx']:
            out.append('- **Treatment on import:** ' + ', '.join(k if v is True else f'{k} {v}' for k, v in c['fx'].items()))
        out += ['', '| File | Line |', '|---|---|']
        for key, text in lines:
            out.append(f'| `{who}__{key}` | {text} |')
        out.append('')
    with open(os.path.join(VOICE_DIR, 'VOICE_SCRIPT.md'), 'w') as f:
        f.write('\n'.join(out) + '\n')


if __name__ == '__main__':
    main()

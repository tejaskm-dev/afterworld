package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.client.hud.TitleCards;
import dev.afterworld.client.hud.Voices;
import dev.afterworld.client.screen.DialogueScreen;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.chat.Component;

/**
 * Voices never speak over one another. The narrator is mid-line when a conversation opens (as when
 * the prologue ends in Warden Isolde's conversation and the chapter's narration begins): the
 * narrator stops at once and is heard again after the conversation, and a line arriving during it
 * waits too, as does the chapter's title card. Then the two lines are spoken one after the other,
 * never together.
 */
public class AfterworldVoiceTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(s -> s.setSeed("afterworld"))
			.create()) {
			Arrive.quickly(context, Origin.WANDERER, Discipline.VANGUARD);
			context.waitTicks(40);
			// The arrival's own cards (the traveller's name, the land's) first play out.
			context.waitFor(mc -> TitleCards.card() == 0, 600);
			context.runOnClient(mc -> Voices.reset());
			AwPayloads.DialogueShow.Line chapter = new AwPayloads.DialogueShow.Line(
				"Chapter One. The Listening World. Where the ground hums, a rule has slipped its leash.", "afterworld:va.narrator.chapter1_0", 169, "", List.of());
			AwPayloads.DialogueShow.Line second = new AwPayloads.DialogueShow.Line(
				"Follow the hum.", "", 40, "", List.of());
			context.runOnClient(mc -> Voices.receive(new AwPayloads.VoiceLine(-1, "", 0xE8DCC0, AwPayloads.VoiceLine.NARRATOR, chapter)));
			context.waitTicks(10);
			check(context.computeOnClient(mc -> Voices.speaking()), "the narrator speaks");

			// Isolde's conversation opens: the narrator falls silent at once.
			AwPayloads.DialogueShow page = new AwPayloads.DialogueShow(-1, "isolde", "Warden Isolde", "Warden of the Waystones", 0x7FC8E8,
				List.of(new AwPayloads.DialogueShow.Line("The waystone answers you. Good.", "afterworld:va.isolde.greet_0", 80, "", List.of())), List.of(), false);
			context.runOnClient(mc -> mc.gui.setScreen(new DialogueScreen(page)));
			context.waitTicks(5);
			check(!context.computeOnClient(mc -> Voices.speaking()), "the narrator stops when the conversation begins");
			// A second line and the chapter's card arrive mid-conversation: both wait.
			context.runOnClient(mc -> {
				Voices.receive(new AwPayloads.VoiceLine(-1, "", 0xE8DCC0, AwPayloads.VoiceLine.NARRATOR, second));
				TitleCards.show(AwPayloads.Title.CHAPTER, Component.literal("Chapter One"), Component.literal("The Listening World"), 0xE8C873);
			});
			context.waitTicks(60);
			check(!context.computeOnClient(mc -> Voices.speaking()), "nothing else speaks while the conversation is open");
			check(context.computeOnClient(mc -> TitleCards.card()) == 0, "the chapter card waits for the conversation");

			// The conversation ends: the card shows, and the narrator speaks again, from the start.
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(20);
			check(context.computeOnClient(mc -> TitleCards.card()) > 0, "the chapter card shows once the conversation ends");
			check(context.computeOnClient(mc -> Voices.speaking()), "the narrator is heard again after the conversation");
			context.waitTicks(100);
			check(context.computeOnClient(mc -> Voices.speaking()), "its line still runs, uncut by the next");
			// 169 ticks for the first line, a breath (9), then the second's 40: it speaks from about 178 to 218.
			context.waitTicks(75);
			check(context.computeOnClient(mc -> Voices.speaking()), "the second line follows the first");
			context.waitTicks(65);
			check(!context.computeOnClient(mc -> Voices.speaking()), "and then all is quiet");
			int most = context.computeOnClient(mc -> Voices.mostAtOnce);
			check(most == 1, "never more than one voice at once (most: " + most + ")");
			Afterworld.LOG.info("VOICETEST done");
		}
	}

	private static void check(boolean ok, String what) {
		Afterworld.LOG.info("VOICETEST {} {}", ok ? "ok" : "FAILED", what);
		if (!ok) {
			throw new AssertionError(what);
		}
	}
}

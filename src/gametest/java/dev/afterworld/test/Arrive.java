package dev.afterworld.test;

import dev.afterworld.client.screen.ArrivalScreen;
import dev.afterworld.edict.Edict;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Answers the arrival's questions with fixed choices, for tests that only need a traveller. */
final class Arrive {
	private Arrive() {
	}

	static void quickly(ClientGameTestContext context, Origin origin, Discipline discipline) {
		context.waitFor(mc -> mc.gui.screen() instanceof ArrivalScreen, 400);
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof ArrivalScreen s) {
				s.skipIntro();
				s.selectOrigin(origin);
				s.confirm();
				s.selectDiscipline(discipline);
				s.confirm();
				s.selectAffinity(discipline.starter != null ? discipline.starter : Edict.WITNESS);
				s.confirm();
				s.confirm();
				s.confirm();
				s.confirm();
			}
		});
		context.waitFor(mc -> mc.gui.screen() == null, 300);
	}
}

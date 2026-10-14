package dev.afterworld.world;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.Progress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Writing to the shared Chronicle. Every consequential world change goes through here. */
public final class Chronicle {
	private Chronicle() {
	}

	public static ChronicleEntry record(MinecraftServer server, String title, String text, String place, int faction, String actor, String cause) {
		AfterworldData data = AfterworldData.get(server);
		ChronicleEntry e = new ChronicleEntry();
		e.day = Progress.day(server);
		e.title = title;
		e.text = text;
		e.place = place;
		e.faction = faction;
		e.actor = actor;
		e.cause = cause;
		data.addChronicle(e);
		announce(server, e);
		return e;
	}

	/** A page whose cause is concealed. Its {@code hidden} text is revealed by {@link #reveal}. */
	public static ChronicleEntry missing(MinecraftServer server, String title, String visible, String hidden, String place, int faction, String cause) {
		AfterworldData data = AfterworldData.get(server);
		ChronicleEntry e = new ChronicleEntry();
		e.day = Progress.day(server);
		e.title = title;
		e.text = visible;
		e.hidden = hidden;
		e.missing = true;
		e.certainty = ChronicleEntry.DISPUTED;
		e.place = place;
		e.faction = faction;
		e.cause = cause;
		data.addChronicle(e);
		return e;
	}

	/** Reveals every missing page with this cause. Returns how many were uncovered. */
	public static int reveal(ServerPlayer player, String cause) {
		AfterworldData data = AfterworldData.get(player.level().getServer());
		int n = 0;
		for (ChronicleEntry e : data.chronicle) {
			if (e.missing && e.cause.equals(cause)) {
				e.missing = false;
				e.certainty = ChronicleEntry.CONFIRMED;
				e.text = e.text + " " + e.hidden;
				e.actor = player.getName().getString();
				n++;
				Progress.notice(player, AwPayloads.Notice.CHRONICLE, Component.translatable("notice.afterworld.page_restored", e.title));
				Progress.legend(player, "discovery", "Restored a missing page of the Chronicle: " + e.title + ".");
			}
		}
		if (n > 0) {
			data.setDirty();
		}
		return n;
	}

	private static void announce(MinecraftServer server, ChronicleEntry e) {
		Component text = Component.translatable("notice.afterworld.chronicle", e.title);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Progress.notice(p, AwPayloads.Notice.CHRONICLE, text);
		}
	}
}

package dev.afterworld.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.afterworld.Afterworld;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.util.Scheduler;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Voices heard in the world rather than in conversation: the great powers taunting, breaking and
 * falling, and the narrator at the turn of each chapter. Lines come from
 * {@code data/afterworld/dialogue/barks.json} (written by tools/dialogue.py) and are spoken in
 * sequence, each with its subtitle typed in time with the voice.
 */
public final class Barks {
	/** Who speaks, as the subtitles name them, and their colour. */
	private static final Map<String, String[]> SPEAKERS = Map.of(
		"shogun", new String[] {"The Ashen Shogun", "E8A33D"},
		"lich", new String[] {"The Crimson Lich", "D02040"},
		"king", new String[] {"The King of Seconds", "F2D25C"},
		"bell", new String[] {"The Bell-Ringer", "7AFFD8"},
		"warden", new String[] {"The Gilded Warden", "FFD060"},
		"sentinel", new String[] {"The Dune Sentinel", "E8B33D"},
		"narrator", new String[] {"", "E8DCC0"});
	private static final Map<String, Map<String, List<Dialogue.Line>>> LINES = new HashMap<>();

	private Barks() {
	}

	public static void init() {
		try (InputStream in = Barks.class.getResourceAsStream("/data/afterworld/dialogue/barks.json")) {
			if (in == null) {
				return;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> who : root.entrySet()) {
				Map<String, List<Dialogue.Line>> groups = new HashMap<>();
				for (Map.Entry<String, JsonElement> g : who.getValue().getAsJsonObject().entrySet()) {
					List<Dialogue.Line> lines = new ArrayList<>();
					for (JsonElement l : g.getValue().getAsJsonArray()) {
						lines.add(Dialogue.Line.of(l.getAsJsonObject()));
					}
					groups.put(g.getKey(), lines);
				}
				LINES.put(who.getKey(), groups);
			}
		} catch (Exception e) {
			Afterworld.LOG.error("Couldn't read the barks", e);
		}
	}

	public static boolean has(String who, String group) {
		return LINES.containsKey(who) && LINES.get(who).containsKey(group);
	}

	/**
	 * A great power speaks a group of lines to everyone within {@code radius}. Returns how many
	 * ticks it will take, so callers can time what follows.
	 */
	public static int say(ServerLevel level, Entity speaker, String who, String group, double radius) {
		List<ServerPlayer> hearers = level.getPlayers(p -> p.distanceToSqr(speaker) < radius * radius);
		return speak(hearers, speaker.getId(), who, group, AwPayloads.VoiceLine.BOSS);
	}

	/** The narrator, to one traveller. */
	public static int narrate(ServerPlayer p, String group) {
		return speak(List.of(p), -1, "narrator", group, AwPayloads.VoiceLine.NARRATOR);
	}

	private static int speak(List<ServerPlayer> hearers, int entity, String who, String group, int style) {
		List<Dialogue.Line> lines = LINES.getOrDefault(who, Map.of()).get(group);
		if (lines == null || lines.isEmpty() || hearers.isEmpty()) {
			return 0;
		}
		String[] sp = SPEAKERS.getOrDefault(who, new String[] {who, "E8DCC0"});
		int color = Integer.parseInt(sp[1], 16);
		int at = 0;
		for (Dialogue.Line l : lines) {
			AwPayloads.VoiceLine packet = new AwPayloads.VoiceLine(entity, sp[0], color, style, l.wire());
			Runnable send = () -> {
				for (ServerPlayer p : hearers) {
					if (!p.isRemoved() && ServerPlayNetworking.canSend(p, AwPayloads.VoiceLine.TYPE)) {
						ServerPlayNetworking.send(p, packet);
					}
				}
			};
			if (at == 0) {
				send.run();
			} else {
				Scheduler.after(at, send);
			}
			int len = l.ticks() > 0 ? l.ticks() : Math.max(40, l.text().length() * 3 / 2);
			at += len + 8;
		}
		return at;
	}
}

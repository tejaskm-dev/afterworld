package dev.afterworld.edict;

import net.minecraft.network.chat.Component;

/**
 * An Edict is a fragment of one of the world's underlying rules. Each one bends a rule rather
 * than simply dealing damage.
 */
public enum Edict {
	RETURN("return", 0xE8A33D, 120, Kind.OFFENSIVE),
	ECHOES("echoes", 0xB79CE8, 260, Kind.OFFENSIVE),
	BORROWED_TIME("borrowed_time", 0x4FB3A9, 800, Kind.DEFENSIVE),
	WEIGHTLESSNESS("weightlessness", 0x9BD08A, 360, Kind.TRAVERSAL),
	HUNGER("hunger", 0xC4553B, 520, Kind.OFFENSIVE),
	HOSPITALITY("hospitality", 0xEBD9A6, 600, Kind.SOCIAL),
	TRESPASS("trespass", 0x6E8FB8, 200, Kind.TRAVERSAL),
	STILLNESS("stillness", 0xA9D8E8, 460, Kind.DEFENSIVE),
	WITNESS("witness", 0xF2D25C, 600, Kind.EXPLORATORY),
	FORGOTTEN("forgotten", 0x8A7FA0, 700, Kind.SOCIAL);

	public static final Edict[] VALUES = values();
	public static final int COUNT = VALUES.length;

	public enum Kind {
		OFFENSIVE, DEFENSIVE, TRAVERSAL, SOCIAL, EXPLORATORY
	}

	public final String id;
	public final int color;
	public final int cooldown;
	public final Kind kind;

	Edict(String id, int color, int cooldown, Kind kind) {
		this.id = id;
		this.color = color;
		this.cooldown = cooldown;
		this.kind = kind;
	}

	public Component displayName() {
		return Component.translatable("edict.afterworld." + id);
	}

	public Component rule() {
		return Component.translatable("edict.afterworld." + id + ".rule");
	}

	public Component description() {
		return Component.translatable("edict.afterworld." + id + ".desc");
	}

	public Component limit() {
		return Component.translatable("edict.afterworld." + id + ".limit");
	}

	public static Edict byId(String id) {
		for (Edict e : VALUES) {
			if (e.id.equals(id)) {
				return e;
			}
		}
		return null;
	}

	public static Edict byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < COUNT ? VALUES[ordinal] : null;
	}
}

package dev.afterworld.player;

import dev.afterworld.edict.Edict;
import net.minecraft.network.chat.Component;

/** The first progression layer: a loose combat identity with a signature technique. */
public enum Discipline {
	NONE("none", 0x9AA3A8, null, 0),
	VANGUARD("vanguard", 0xC9714A, Edict.RETURN, 600),
	WAYFARER("wayfarer", 0x7FB069, Edict.WEIGHTLESSNESS, 360),
	ARCANIST("arcanist", 0x9E86D9, Edict.ECHOES, 600),
	KEEPER("keeper", 0xD9B45A, Edict.HOSPITALITY, 900);

	public static final Discipline[] VALUES = values();

	public final String id;
	public final int color;
	public final Edict starter;
	public final int signatureCooldown;

	Discipline(String id, int color, Edict starter, int signatureCooldown) {
		this.id = id;
		this.color = color;
		this.starter = starter;
		this.signatureCooldown = signatureCooldown;
	}

	public Component displayName() {
		return Component.translatable("discipline.afterworld." + id);
	}

	public Component signatureName() {
		return Component.translatable("discipline.afterworld." + id + ".signature");
	}

	public static Discipline byOrdinal(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : NONE;
	}

	/** Arcanists get a fourth Edict slot. */
	public int slotCount() {
		return this == ARCANIST ? 4 : 3;
	}
}

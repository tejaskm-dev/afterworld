package dev.afterworld.world;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

/** Persistent state for the world's great actors. Bosses exist in the data even when not loaded. */
public final class BossStates {
	private BossStates() {
	}

	static void putUuid(CompoundTag t, String key, Optional<UUID> id) {
		id.ifPresent(u -> t.put(key, UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, u).getOrThrow()));
	}

	/** The Pilgrim Without Feet: walks a loop of four shrines. */
	public static final class Pilgrim {
		public static final int UNBORN = 0;
		public static final int WANDERING = 1;
		public static final int DEFEATED = 2;
		public static final int AT_PEACE = 3;

		public int state = UNBORN;
		public final int[] shrines = {-1, -1, -1, -1};
		public int leg;
		public double x;
		public double y = 64;
		public double z;
		public float health = -1;
		public int repairedMask;
		public int visitedMask;
		public Optional<UUID> entity = Optional.empty();
		public long lastMoveTime;
		public String resolvedBy = "";
		public final java.util.List<UUID> helpers = new java.util.ArrayList<>();

		public CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putInt("state", state);
			net.minecraft.nbt.ListTag h = new net.minecraft.nbt.ListTag();
			for (UUID u : helpers) {
				h.add(UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, u).getOrThrow());
			}
			t.put("helpers", h);
			t.putIntArray("shrines", shrines);
			t.putInt("leg", leg);
			t.putDouble("x", x);
			t.putDouble("y", y);
			t.putDouble("z", z);
			t.putFloat("health", health);
			t.putInt("repaired", repairedMask);
			t.putInt("visited", visitedMask);
			putUuid(t, "entity", entity);
			t.putLong("lastMove", lastMoveTime);
			t.putString("resolvedBy", resolvedBy);
			return t;
		}

		public void load(CompoundTag t) {
			state = t.getIntOr("state", UNBORN);
			helpers.clear();
			net.minecraft.nbt.ListTag h = t.getListOrEmpty("helpers");
			for (int i = 0; i < h.size(); i++) {
				UUIDUtil.CODEC.parse(NbtOps.INSTANCE, h.get(i)).result().ifPresent(helpers::add);
			}
			int[] s = t.getIntArray("shrines").orElse(new int[] {-1, -1, -1, -1});
			System.arraycopy(s, 0, shrines, 0, Math.min(4, s.length));
			leg = t.getIntOr("leg", 0);
			x = t.getDoubleOr("x", 0);
			y = t.getDoubleOr("y", 64);
			z = t.getDoubleOr("z", 0);
			health = t.getFloatOr("health", -1);
			repairedMask = t.getIntOr("repaired", 0);
			visitedMask = t.getIntOr("visited", 0);
			entity = t.read("entity", UUIDUtil.CODEC);
			lastMoveTime = t.getLongOr("lastMove", 0);
			resolvedBy = t.getStringOr("resolvedBy", "");
		}
	}

	/** The Bell-Ringer of the Hollow Cathedral and the name it forgot. */
	public static final class BellRinger {
		public static final int WAITING = 0;
		public static final int DEFEATED = 1;
		public static final int REMEMBERED = 2;

		public int state = WAITING;
		public int site = -1;
		public int nameA = -1;
		public int nameB = -1;
		public boolean bellLost;
		public Optional<UUID> entity = Optional.empty();
		public String resolvedBy = "";

		public CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putInt("state", state);
			t.putInt("site", site);
			t.putInt("nameA", nameA);
			t.putInt("nameB", nameB);
			t.putBoolean("bellLost", bellLost);
			putUuid(t, "entity", entity);
			t.putString("resolvedBy", resolvedBy);
			return t;
		}

		public void load(CompoundTag t) {
			state = t.getIntOr("state", WAITING);
			site = t.getIntOr("site", -1);
			nameA = t.getIntOr("nameA", -1);
			nameB = t.getIntOr("nameB", -1);
			bellLost = t.getBooleanOr("bellLost", false);
			entity = t.read("entity", UUIDUtil.CODEC);
			resolvedBy = t.getStringOr("resolvedBy", "");
		}
	}

	/** The Clockwork Vault: an adaptive dungeon that remembers how it was entered and fought. */
	public static final class Vault {
		public static final int KING_WAITING = 0;
		public static final int KING_DEFEATED = 1;
		public static final int KING_BARGAINED = 2;

		public int site = -1;
		public int king = KING_WAITING;
		public int kingDefeats;
		public int visits;
		public final int[] wingFirst = new int[3];
		public int lastWing = -1;
		public int adaptations;
		public final int[] observed = new int[16];
		public int temperament;
		public long lastAdaptDay = -1;
		public Optional<UUID> kingEntity = Optional.empty();
		public int brokenEscapements;
		public long lastVisitDay = -1;
		public long kingDefeatDay = -100;

		public CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putInt("site", site);
			t.putInt("king", king);
			t.putInt("kingDefeats", kingDefeats);
			t.putInt("visits", visits);
			t.putIntArray("wingFirst", wingFirst);
			t.putInt("lastWing", lastWing);
			t.putInt("adaptations", adaptations);
			t.putIntArray("observed", observed);
			t.putInt("temperament", temperament);
			t.putLong("lastAdaptDay", lastAdaptDay);
			putUuid(t, "king_entity", kingEntity);
			t.putInt("broken", brokenEscapements);
			t.putLong("lastVisitDay", lastVisitDay);
			t.putLong("kingDefeatDay", kingDefeatDay);
			return t;
		}

		public void load(CompoundTag t) {
			site = t.getIntOr("site", -1);
			king = t.getIntOr("king", KING_WAITING);
			kingDefeats = t.getIntOr("kingDefeats", 0);
			visits = t.getIntOr("visits", 0);
			int[] w = t.getIntArray("wingFirst").orElse(new int[3]);
			System.arraycopy(w, 0, wingFirst, 0, Math.min(3, w.length));
			lastWing = t.getIntOr("lastWing", -1);
			adaptations = t.getIntOr("adaptations", 0);
			int[] o = t.getIntArray("observed").orElse(new int[0]);
			System.arraycopy(o, 0, observed, 0, Math.min(observed.length, o.length));
			temperament = t.getIntOr("temperament", 0);
			lastAdaptDay = t.getLongOr("lastAdaptDay", -1);
			kingEntity = t.read("king_entity", UUIDUtil.CODEC);
			brokenEscapements = t.getIntOr("broken", 0);
			lastVisitDay = t.getLongOr("lastVisitDay", -1);
			kingDefeatDay = t.getLongOr("kingDefeatDay", -100);
		}
	}
}

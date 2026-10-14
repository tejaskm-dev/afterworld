package dev.afterworld.site;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

/**
 * A place the world has decided exists: a shrine, an anomaly, a ruin. Sites are recorded first and
 * built lazily the first time a player comes near, on ground nobody has made their home.
 */
public final class Site {
	public static final int PENDING = 0;
	public static final int BUILT = 1;
	public static final int RESOLVED = 2;
	public static final int FAILED = 3;

	public int id;
	public SiteKind kind = SiteKind.ANOMALY;
	public String dim = "minecraft:overworld";
	public int x;
	public int y = Integer.MIN_VALUE;
	public int z;
	public long seed;
	public int state = PENDING;
	public String name = "";
	/** Kind-specific: the Edict an anomaly holds, the shrine's index on the route, etc. */
	public int data = -1;
	public int settlement = -1;
	public int faction = -1;
	public long createdDay;
	public long resolvedDay;
	public String outcome = "";
	public Optional<UUID> resolvedBy = Optional.empty();
	/** Free-form kind-specific flags. */
	public int flags;

	public boolean built() {
		return y != Integer.MIN_VALUE && state != PENDING;
	}

	public boolean open() {
		return state == PENDING || state == BUILT;
	}

	public BlockPos pos() {
		return new BlockPos(x, y == Integer.MIN_VALUE ? 64 : y, z);
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putInt("id", id);
		t.putString("kind", kind.id);
		t.putString("dim", dim);
		t.putInt("x", x);
		t.putInt("y", y);
		t.putInt("z", z);
		t.putLong("seed", seed);
		t.putInt("state", state);
		t.putString("name", name);
		t.putInt("data", data);
		t.putInt("settlement", settlement);
		t.putInt("faction", faction);
		t.putLong("created", createdDay);
		t.putLong("resolved", resolvedDay);
		t.putString("outcome", outcome);
		resolvedBy.ifPresent(u -> t.put("by", UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, u).getOrThrow()));
		t.putInt("flags", flags);
		return t;
	}

	public static Site load(CompoundTag t) {
		Site s = new Site();
		s.id = t.getIntOr("id", 0);
		s.kind = SiteKind.byId(t.getStringOr("kind", "anomaly"));
		s.dim = t.getStringOr("dim", "minecraft:overworld");
		s.x = t.getIntOr("x", 0);
		s.y = t.getIntOr("y", Integer.MIN_VALUE);
		s.z = t.getIntOr("z", 0);
		s.seed = t.getLongOr("seed", 0);
		s.state = t.getIntOr("state", PENDING);
		s.name = t.getStringOr("name", "");
		s.data = t.getIntOr("data", -1);
		s.settlement = t.getIntOr("settlement", -1);
		s.faction = t.getIntOr("faction", -1);
		s.createdDay = t.getLongOr("created", 0);
		s.resolvedDay = t.getLongOr("resolved", 0);
		s.outcome = t.getStringOr("outcome", "");
		s.resolvedBy = t.read("by", UUIDUtil.CODEC);
		s.flags = t.getIntOr("flags", 0);
		return s;
	}
}

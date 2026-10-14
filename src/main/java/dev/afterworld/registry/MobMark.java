package dev.afterworld.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;

/**
 * Small immutable tag attached to vanilla mobs that Afterworld has touched: hunting echoes,
 * pacted creatures, vault wardens, ghosts. Synced so clients can render echoes as spectres.
 */
public record MobMark(int flags, Optional<UUID> owner, long expires, int data) {
	public static final int ECHO = 1;
	public static final int BOUND = 1 << 1;
	public static final int BETRAYER = 1 << 2;
	public static final int GHOST = 1 << 3;
	public static final int PACT = 1 << 4;
	public static final int ENRAGED = 1 << 5;
	public static final int WARDEN = 1 << 6;
	public static final int MIRRORED = 1 << 7;
	public static final int ANCHORED = 1 << 8;
	public static final int SPIKED = 1 << 9;
	public static final int MARKED_BEAST = 1 << 10;
	public static final int RAIDER = 1 << 11;
	public static final int VAULT_MINION = 1 << 12;
	public static final int ROADWRIGHT = 1 << 13;
	public static final int SUSPENDED = 1 << 14;
	public static final int LISTENER = 1 << 15;
	public static final int TOUCHED = 1 << 16;
	public static final int ESCORT = 1 << 17;
	public static final int BOSS = 1 << 18;

	public static final MobMark NONE = new MobMark(0, Optional.empty(), 0, 0);

	public static final Codec<MobMark> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("flags", 0).forGetter(MobMark::flags),
		UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(MobMark::owner),
		Codec.LONG.optionalFieldOf("expires", 0L).forGetter(MobMark::expires),
		Codec.INT.optionalFieldOf("data", 0).forGetter(MobMark::data)
	).apply(i, MobMark::new));

	public static final StreamCodec<ByteBuf, MobMark> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, MobMark::flags,
		ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), MobMark::owner,
		ByteBufCodecs.VAR_LONG, MobMark::expires,
		ByteBufCodecs.VAR_INT, MobMark::data,
		MobMark::new
	);

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public MobMark with(int flag) {
		return new MobMark(flags | flag, owner, expires, data);
	}

	public MobMark without(int flag) {
		return new MobMark(flags & ~flag, owner, expires, data);
	}

	public MobMark withOwner(UUID id) {
		return new MobMark(flags, Optional.ofNullable(id), expires, data);
	}

	public MobMark withExpires(long t) {
		return new MobMark(flags, owner, t, data);
	}

	public MobMark withData(int d) {
		return new MobMark(flags, owner, expires, d);
	}

	public boolean ownedBy(UUID id) {
		return owner.isPresent() && owner.get().equals(id);
	}

	public static MobMark of(Entity e) {
		MobMark m = e.getAttached(AwAttachments.MOB);
		return m == null ? NONE : m;
	}

	public static boolean has(Entity e, int flag) {
		MobMark m = e.getAttached(AwAttachments.MOB);
		return m != null && (m.flags & flag) != 0;
	}

	public static void set(Entity e, MobMark mark) {
		e.setAttached(AwAttachments.MOB, mark.flags == 0 ? null : mark);
	}
}

package dev.afterworld.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.afterworld.edict.Edict;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;

/**
 * A traveller's garb: purely cosmetic pieces worn over the skin, chosen at arrival and changeable
 * in the wardrobe. Every field is a small index so the whole thing syncs in a few bytes.
 * {@code heart} is the Edict shard glowing on the chest (an Edict ordinal, or -1 for none). It
 * mirrors the traveller's affinity, which only the server sets.
 */
public record Garb(int head, int cloak, int accent, int arms, int feet, int primary, int secondary, int emblem, boolean visible, int heart) {
	public static final int HEADS = 5;
	public static final int CLOAKS = 6;
	public static final int ACCENTS = 5;
	public static final int ARMS = 3;
	public static final int FEET = 3;
	public static final int EMBLEMS = 7;

	/** Dyes for cloth (primary) and trim (secondary). */
	public static final int[] PALETTE = {
		0xE8DCC0, 0x8C8A86, 0x3A3A3E, 0x2B3550, 0x3F6E8C, 0x2F7F78, 0x5E7A3A, 0x2F4F2F,
		0xC08A2E, 0xE8A33D, 0xA5482A, 0x8E2430, 0x5E3A6E, 0x9C86C8, 0x6B4A2E, 0xC07A86
	};
	public static final String[] PALETTE_NAMES = {
		"bone", "ash", "charcoal", "midnight", "lake", "teal", "moss", "forest",
		"ochre", "amber", "rust", "crimson", "plum", "lavender", "umber", "rose"
	};

	public static final Garb DEFAULT = new Garb(1, 2, 1, 2, 1, 1, 9, 0, true);

	public static final Codec<Garb> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("head", 0).forGetter(Garb::head),
		Codec.INT.optionalFieldOf("cloak", 0).forGetter(Garb::cloak),
		Codec.INT.optionalFieldOf("accent", 0).forGetter(Garb::accent),
		Codec.INT.optionalFieldOf("arms", 0).forGetter(Garb::arms),
		Codec.INT.optionalFieldOf("feet", 0).forGetter(Garb::feet),
		Codec.INT.optionalFieldOf("primary", 1).forGetter(Garb::primary),
		Codec.INT.optionalFieldOf("secondary", 9).forGetter(Garb::secondary),
		Codec.INT.optionalFieldOf("emblem", 0).forGetter(Garb::emblem),
		Codec.BOOL.optionalFieldOf("visible", true).forGetter(Garb::visible),
		Codec.INT.optionalFieldOf("heart", -1).forGetter(Garb::heart)
	).apply(i, Garb::new));

	public static final StreamCodec<ByteBuf, Garb> STREAM_CODEC = StreamCodec.of(
		(buf, g) -> {
			buf.writeByte(g.head);
			buf.writeByte(g.cloak);
			buf.writeByte(g.accent);
			buf.writeByte(g.arms);
			buf.writeByte(g.feet);
			buf.writeByte(g.primary);
			buf.writeByte(g.secondary);
			buf.writeByte(g.emblem);
			buf.writeBoolean(g.visible);
			buf.writeByte(g.heart);
		},
		buf -> new Garb(buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean(), buf.readByte())
	);

	/** Every index is wrapped into range, so a hand-edited save or a bad packet can't crash a renderer. */
	public Garb {
		head = Math.floorMod(head, HEADS);
		cloak = Math.floorMod(cloak, CLOAKS);
		accent = Math.floorMod(accent, ACCENTS);
		arms = Math.floorMod(arms, ARMS);
		feet = Math.floorMod(feet, FEET);
		primary = Math.floorMod(primary, PALETTE.length);
		secondary = Math.floorMod(secondary, PALETTE.length);
		emblem = Math.floorMod(emblem, EMBLEMS);
		heart = heart >= 0 && heart < Edict.COUNT ? heart : -1;
	}

	/** Garb without a heart shard. */
	public Garb(int head, int cloak, int accent, int arms, int feet, int primary, int secondary, int emblem, boolean visible) {
		this(head, cloak, accent, arms, feet, primary, secondary, emblem, visible, -1);
	}

	public static Garb random(RandomSource r) {
		return new Garb(r.nextInt(HEADS), r.nextInt(CLOAKS), r.nextInt(ACCENTS), r.nextInt(ARMS), r.nextInt(FEET),
			r.nextInt(PALETTE.length), r.nextInt(PALETTE.length), r.nextInt(EMBLEMS), true);
	}

	public int primaryColor() {
		return PALETTE[primary];
	}

	public int secondaryColor() {
		return PALETTE[secondary];
	}

	/** Returns a copy with one field (by {@link #field(int)} order) changed. */
	public Garb with(int field, int value) {
		return switch (field) {
			case 0 -> new Garb(value, cloak, accent, arms, feet, primary, secondary, emblem, visible, heart);
			case 1 -> new Garb(head, value, accent, arms, feet, primary, secondary, emblem, visible, heart);
			case 2 -> new Garb(head, cloak, value, arms, feet, primary, secondary, emblem, visible, heart);
			case 3 -> new Garb(head, cloak, accent, value, feet, primary, secondary, emblem, visible, heart);
			case 4 -> new Garb(head, cloak, accent, arms, value, primary, secondary, emblem, visible, heart);
			case 5 -> new Garb(head, cloak, accent, arms, feet, value, secondary, emblem, visible, heart);
			case 6 -> new Garb(head, cloak, accent, arms, feet, primary, value, emblem, visible, heart);
			case 7 -> new Garb(head, cloak, accent, arms, feet, primary, secondary, value, visible, heart);
			case 8 -> new Garb(head, cloak, accent, arms, feet, primary, secondary, emblem, value != 0, heart);
			default -> new Garb(head, cloak, accent, arms, feet, primary, secondary, emblem, visible, value);
		};
	}

	public Garb withHeart(Edict e) {
		return with(9, e == null ? -1 : e.ordinal());
	}

	/** The Edict glowing on the chest, if any. */
	public Edict heartEdict() {
		return Edict.byOrdinal(heart);
	}

	public int field(int field) {
		return switch (field) {
			case 0 -> head;
			case 1 -> cloak;
			case 2 -> accent;
			case 3 -> arms;
			case 4 -> feet;
			case 5 -> primary;
			case 6 -> secondary;
			case 7 -> emblem;
			case 8 -> visible ? 1 : 0;
			default -> heart;
		};
	}
}

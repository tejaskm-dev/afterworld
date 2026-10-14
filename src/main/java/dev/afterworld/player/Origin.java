package dev.afterworld.player;

import dev.afterworld.world.Faction;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Where a traveller says they came from: a faction's goodwill, a starting kit and a suggested garb. */
public enum Origin {
	NONE("none", null, 0, Garb.DEFAULT),
	CROWN_SQUIRE("crown_squire", Faction.CROWN, 0xC9A227, new Garb(0, 1, 1, 1, 1, 11, 8, 1, true)),
	GUILD_CARTOGRAPHER("guild_cartographer", Faction.GUILD, 0x5B8DB8, new Garb(2, 2, 1, 2, 1, 4, 14, 2, true)),
	COURT_MOURNER("court_mourner", Faction.COURT, 0x8E7CC3, new Garb(1, 3, 2, 0, 2, 12, 0, 3, true)),
	BUREAU_CLERK("bureau_clerk", Faction.BUREAU, 0x4FA89B, new Garb(0, 4, 3, 1, 1, 5, 2, 4, true)),
	WANDERER("wanderer", null, 0xB8AE98, new Garb(1, 2, 4, 2, 2, 1, 9, 0, true));

	public static final Origin[] VALUES = values();
	/** The origins a player can pick (everything but NONE). */
	public static final List<Origin> CHOICES = List.of(CROWN_SQUIRE, GUILD_CARTOGRAPHER, COURT_MOURNER, BUREAU_CLERK, WANDERER);

	public final String id;
	public final Faction faction;
	public final int color;
	public final Garb garb;

	Origin(String id, Faction faction, int color, Garb garb) {
		this.id = id;
		this.faction = faction;
		this.color = color;
		this.garb = garb;
	}

	public Component displayName() {
		return Component.translatable("origin.afterworld." + id);
	}

	public static Origin byOrdinal(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : NONE;
	}

	public static Origin byId(String id) {
		for (Origin o : VALUES) {
			if (o.id.equals(id)) {
				return o;
			}
		}
		return NONE;
	}

	/** What the traveller arrives carrying. Shared by the server (to give) and the client (to preview). */
	public List<ItemStack> kit() {
		return switch (this) {
			case CROWN_SQUIRE -> List.of(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD), new ItemStack(Items.BREAD, 8));
			case GUILD_CARTOGRAPHER -> List.of(new ItemStack(Items.COMPASS), new ItemStack(Items.SPYGLASS), new ItemStack(Items.MAP, 2), new ItemStack(Items.COOKED_SALMON, 8));
			case COURT_MOURNER -> List.of(new ItemStack(Items.SOUL_LANTERN), new ItemStack(Items.CANDLE, 4), new ItemStack(Items.GOLDEN_CARROT, 4));
			case BUREAU_CLERK -> List.of(new ItemStack(Items.CLOCK), new ItemStack(Items.WRITABLE_BOOK), new ItemStack(Items.REDSTONE, 16), new ItemStack(Items.BAKED_POTATO, 8));
			case WANDERER -> List.of(new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_AXE), new ItemStack(Items.TORCH, 16), new ItemStack(Items.BREAD, 12));
			case NONE -> List.of();
		};
	}

	/** Starting reputation: a strong tie to one faction, or a little with everyone. */
	public int repWith(Faction f) {
		if (this == WANDERER) {
			return 5;
		}
		return f == faction ? 20 : 0;
	}
}

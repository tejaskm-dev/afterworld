package dev.afterworld.npc;

import dev.afterworld.player.Garb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The people of the Afterworld: who they are, how they look and what they hold. Their words live
 * in {@code data/afterworld/dialogue/<id>.json}, generated (with their voices) by tools/dialogue.py.
 */
public enum Npc {
	ISOLDE("isolde", "Warden Isolde", "Warden of the Waystones", 0x7FE3D2, true,
		new Garb(0, 2, 4, 1, 1, 5, 8, 5, true), Items.LANTERN, Items.AIR),
	BRAM("bram", "Quartermaster Bram", "Supplies and Provisions", 0xE8A33D, false,
		new Garb(0, 0, 1, 2, 1, 14, 10, 0, true), Items.AIR, Items.WRITABLE_BOOK),
	WREN("wren", "Wren Halloway", "Cartographers' Guild", 0x5B8DB8, true,
		new Garb(2, 1, 1, 0, 2, 4, 9, 2, true), Items.MAP, Items.COMPASS),
	MARTA("marta", "Marta Ironvein", "Smith to the Wardens", 0xD8743A, false,
		new Garb(0, 0, 1, 1, 1, 2, 10, 0, true), Items.IRON_AXE, Items.AIR),
	SELENE("selene", "Stargazer Selene", "Keeper of the Moon Gazebo", 0xB9A4FF, true,
		new Garb(4, 5, 2, 0, 0, 3, 13, 5, true), Items.SPYGLASS, Items.AIR),
	ODO("odo", "Captain Odo Brassmere", "Master of the Wayfarer", 0xD8B45A, false,
		new Garb(2, 1, 3, 1, 1, 11, 9, 4, true), Items.CLOCK, Items.AIR),
	MAB("mab", "Fen-Keeper Mab", "Witch of the Black Water", 0x8FBF5A, true,
		new Garb(1, 3, 4, 2, 2, 6, 14, 3, true), Items.STICK, Items.AIR),
	TORVALD("torvald", "Torvald the Hunter", "Keeper of the Lodge", 0xC9A06A, false,
		new Garb(0, 4, 3, 1, 2, 14, 0, 0, true), Items.BOW, Items.AIR),
	INES("ines", "Sister Ines", "The Morrow Court", 0xE8E4DC, true,
		new Garb(3, 2, 2, 0, 0, 2, 11, 3, true), Items.AIR, Items.CANDLE),
	RASHID("rashid", "Rashid of the Sands", "Master of the Caravan", 0xE8A33D, false,
		new Garb(0, 1, 2, 2, 1, 9, 5, 2, true), Items.AIR, Items.GOAT_HORN),
	LEYLA("leyla", "Leyla of the Sands", "Rashid's Sister, Caravan Master", 0xD8743A, true,
		new Garb(0, 1, 2, 2, 1, 9, 5, 2, true), Items.AIR, Items.AIR),
	AKANE("akane", "Akane", "Last Disciple of the Lantern Dojo", 0xE86A7A, true,
		new Garb(3, 2, 2, 0, 0, 2, 11, 3, true), Items.IRON_SWORD, Items.LANTERN),
	ROOK("rook", "Rook", "Keeper of the Landings", 0x9C86C8, false,
		new Garb(2, 1, 3, 1, 1, 12, 8, 4, true), Items.LANTERN, Items.AIR);

	public static final Npc[] VALUES = values();

	public final String id;
	public final String displayName;
	public final String role;
	public final int color;
	public final boolean slim;
	public final Garb garb;
	public final Item mainHand;
	public final Item offHand;

	Npc(String id, String displayName, String role, int color, boolean slim, Garb garb, Item mainHand, Item offHand) {
		this.id = id;
		this.displayName = displayName;
		this.role = role;
		this.color = color;
		this.slim = slim;
		this.garb = garb;
		this.mainHand = mainHand;
		this.offHand = offHand;
	}

	public static Npc byId(String id) {
		for (Npc n : VALUES) {
			if (n.id.equals(id)) {
				return n;
			}
		}
		return null;
	}
}

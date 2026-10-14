package dev.afterworld.registry;

import dev.afterworld.Afterworld;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class AwTags {
	/** Blocks the Edict of Trespass cannot pass through. */
	public static final TagKey<Block> WARDED = TagKey.create(Registries.BLOCK, Afterworld.id("warded"));
	/** Natural ground the Pilgrim's footsteps may wear into a path. */
	public static final TagKey<Block> PILGRIM_TRAMPLES = TagKey.create(Registries.BLOCK, Afterworld.id("pilgrim_tramples"));
	/** Items kept through death: the Journal, the relics, Edict fragments. */
	/** Things no merchant will take: the Journal, quest items, keys. */
	public static final TagKey<Item> UNSELLABLE = TagKey.create(Registries.ITEM, Afterworld.id("unsellable"));
	public static final TagKey<Item> SOULBOUND = TagKey.create(Registries.ITEM, Afterworld.id("soulbound"));

	private AwTags() {
	}
}

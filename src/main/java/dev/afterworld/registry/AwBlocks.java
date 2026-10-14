package dev.afterworld.registry;

import dev.afterworld.Afterworld;
import dev.afterworld.block.AnomalyCoreBlock;
import dev.afterworld.block.CounterweightBlock;
import dev.afterworld.block.EscapementBlock;
import dev.afterworld.block.ShrineStoneBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class AwBlocks {
	public static final List<Block> WITH_ITEMS = new ArrayList<>();

	public static final Block SHRINE_STONE = register("shrine_stone", ShrineStoneBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE_BRICKS)
			.lightLevel(s -> s.getValue(ShrineStoneBlock.REPAIRED) ? 12 : 3).pushReaction(PushReaction.IMMOVEABLE));
	public static final Block ANOMALY_CORE = register("anomaly_core", AnomalyCoreBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(6.0F, 1200.0F).sound(SoundType.AMETHYST)
			.lightLevel(s -> 15).emissiveRendering(s -> true).pushReaction(PushReaction.IMMOVEABLE).noLootTable());
	public static final Block CLOCKWORK_BRICKS = register("clockwork_bricks", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(3.0F, 9.0F).sound(SoundType.DEEPSLATE_BRICKS).requiresCorrectToolForDrops());
	public static final Block GEARWORK = register("gearwork", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(4.0F, 9.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops());
	public static final Block WARDED_GEARWORK = register("warded_gearwork", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(-1.0F, 3600000.0F).sound(SoundType.COPPER)
			.lightLevel(s -> 4).noLootTable().pushReaction(PushReaction.IMMOVEABLE));
	public static final Block ESCAPEMENT = register("escapement", EscapementBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(8.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK)
			.lightLevel(s -> 7).noLootTable().pushReaction(PushReaction.IMMOVEABLE));
	public static final Block COUNTERWEIGHT = register("counterweight", CounterweightBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.CHAIN).noLootTable()
			.pushReaction(PushReaction.IMMOVEABLE));

	public static final Block WAYSTONE = register("waystone", dev.afterworld.block.WaystoneBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(4.0F, 1200.0F).sound(SoundType.DEEPSLATE_BRICKS)
			.lightLevel(s -> 9).noOcclusion().requiresCorrectToolForDrops().pushReaction(PushReaction.IMMOVEABLE));

	public static final Block CLUE = register("clue", dev.afterworld.block.ClueBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).sound(SoundType.WOOL).noOcclusion().noCollision()
			.noLootTable().pushReaction(PushReaction.POPPED));
	public static final Block SEAL = register("seal", dev.afterworld.block.SealBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE_BRICKS)
			.lightLevel(s -> s.getValue(dev.afterworld.block.SealBlock.SOLVED) ? 12 : 4).noLootTable().pushReaction(PushReaction.IMMOVEABLE));
	public static final Block PHYLACTERY = register("phylactery", dev.afterworld.block.PhylacteryBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.5F, 6.0F).sound(SoundType.AMETHYST).lightLevel(s -> 10)
			.noOcclusion().pushReaction(PushReaction.IMMOVEABLE));

	public static final Block WARDENS_BOARD = register("wardens_board", dev.afterworld.block.BoardBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 6.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());

	/** The Warden's Ladder: its gate in the world, and the rungs up and out on each floor. */
	public static final Block LADDER_STONE = register("ladder_stone", dev.afterworld.block.LadderStoneBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).sound(SoundType.DEEPSLATE_BRICKS)
			.lightLevel(s -> s.getValue(dev.afterworld.block.LadderStoneBlock.KIND) == dev.afterworld.block.LadderStoneBlock.Kind.GATE ? 10
				: s.getValue(dev.afterworld.block.LadderStoneBlock.ACTIVE) ? 14 : 4)
			.noLootTable().pushReaction(PushReaction.IMMOVEABLE));

	public static final BlockEntityType<dev.afterworld.block.ClueBlockEntity> CLUE_ENTITY = blockEntity("clue", dev.afterworld.block.ClueBlockEntity::new, CLUE);
	public static final BlockEntityType<dev.afterworld.block.SealBlockEntity> SEAL_ENTITY = blockEntity("seal", dev.afterworld.block.SealBlockEntity::new, SEAL);

	private AwBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Afterworld.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Afterworld.id(name));
		BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		WITH_ITEMS.add(block);
		return block;
	}

	private static <T extends BlockEntity> BlockEntityType<T> blockEntity(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Afterworld.id(name), new BlockEntityType<>(factory, java.util.Set.of(blocks)));
	}

	public static void init() {
	}
}

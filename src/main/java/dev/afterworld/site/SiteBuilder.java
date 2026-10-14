package dev.afterworld.site;

import dev.afterworld.Afterworld;
import dev.afterworld.edict.Edict;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.util.Ground;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;

/** Hand-written layouts for every kind of site. Plans are relative to the site's ground origin. */
public final class SiteBuilder {
	public static final ResourceKey<LootTable> LOOT_SHRINE = loot("shrine_cache");
	public static final ResourceKey<LootTable> LOOT_LAIR = loot("lair_hoard");
	public static final ResourceKey<LootTable> LOOT_CACHE = loot("old_cache");
	public static final ResourceKey<LootTable> LOOT_CARAVAN = loot("caravan_goods");
	public static final ResourceKey<LootTable> LOOT_ARCHIVE = loot("cathedral_archive");
	public static final ResourceKey<LootTable> LOOT_HOUSE = loot("unremembered_house");
	public static final ResourceKey<LootTable> LOOT_VAULT = loot("vault_reward");

	static final BlockState AIR = Blocks.AIR.defaultBlockState();
	static final BlockState[] RUIN = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
		Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};
	static final float[] RUIN_W = {0.45F, 0.3F, 0.2F, 0.05F};
	static final BlockState[] DEEP = {Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
		Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState()};
	static final float[] DEEP_W = {0.55F, 0.2F, 0.15F, 0.1F};
	static final BlockState[] TUFF = {Blocks.TUFF_BRICKS.defaultBlockState(), Blocks.POLISHED_TUFF.defaultBlockState(),
		AwBlocks.CLOCKWORK_BRICKS.defaultBlockState(), Blocks.CHISELED_TUFF.defaultBlockState()};
	static final float[] TUFF_W = {0.45F, 0.2F, 0.3F, 0.05F};

	private SiteBuilder() {
	}

	private static ResourceKey<LootTable> loot(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, Afterworld.id("chests/" + name));
	}

	/** Horizontal extent (radius) and vertical extent used for the protection and loading checks. */
	public static int[] footprint(SiteKind kind) {
		return switch (kind) {
			case SHRINE -> new int[] {-5, 5, -5, 5};
			case ANOMALY -> new int[] {-10, 10, -10, 10};
			case LAIR -> new int[] {-9, 9, -9, 9};
			case CACHE -> new int[] {-5, 5, -5, 5};
			case CARAVAN -> new int[] {-8, 8, -8, 8};
			case ROADSTOP -> new int[] {-5, 5, -5, 5};
			case UNREMEMBERED -> new int[] {-24, 24, -24, 24};
			case CATHEDRAL -> new int[] {-14, 14, -24, 20};
			case VAULT -> new int[] {-28, 28, -36, 8};
			case BASTION -> new int[] {-22, 22, -22, 22};
			case OBSERVATORY -> new int[] {-13, 13, -13, 13};
			case MAUSOLEUM -> new int[] {-17, 17, -21, 21};
			case CONTAINMENT -> new int[] {-19, 19, -19, 19};
			case ARCHIVE -> new int[] {-15, 15, -15, 15};
		};
	}

	public static Plan plan(ServerLevel level, Site site, BlockPos origin) {
		Plan p = new Plan(origin, site.seed);
		switch (site.kind) {
			case SHRINE -> shrine(level, p, site);
			case ANOMALY -> anomaly(level, p, site);
			case LAIR -> lair(level, p);
			case CACHE -> cache(level, p);
			case CARAVAN -> caravan(level, p);
			case ROADSTOP -> roadstop(level, p);
			case UNREMEMBERED -> unremembered(level, p);
			case CATHEDRAL -> cathedral(level, p);
			case VAULT -> vault(level, p, site);
			case BASTION -> GrandBuilder.bastion(level, p);
			case OBSERVATORY -> GrandBuilder.observatory(level, p);
			case MAUSOLEUM -> GrandBuilder.mausoleum(level, p);
			case CONTAINMENT -> GrandBuilder.containment(level, p);
			case ARCHIVE -> GrandBuilder.archive(level, p);
		}
		return p;
	}

	// ------------------------------------------------------------------ helpers

	/**
	 * Levels the terrain under a rectangle: foundation fills dips, hills are cut back, and the
	 * floor keeps the column's own surface block so a site sits in the land rather than on a slab.
	 */
	static void ground(ServerLevel level, Plan p, int x1, int x2, int z1, int z2, int clearHeight, BlockState foundation) {
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				BlockPos top = Ground.surface(level, p.origin.getX() + x, p.origin.getZ() + z).below();
				BlockState surface = level.getBlockState(top);
				if (!surface.isCollisionShapeFullBlock(level, top) || surface.is(net.minecraft.tags.BlockTags.LOGS)) {
					surface = Blocks.GRASS_BLOCK.defaultBlockState();
				}
				int surfaceDy = top.getY() - p.origin.getY();
				if (surfaceDy < 0) {
					for (int y = Math.max(-6, surfaceDy + 1); y < 0; y++) {
						p.set(x, y, z, foundation);
					}
					p.set(x, 0, z, surface);
				} else if (surfaceDy > 0) {
					p.set(x, 0, z, surface);
				}
				for (int y = 1; y <= clearHeight; y++) {
					if (y <= surfaceDy + 3 || y <= 2) {
						p.set(x, y, z, AIR);
					}
				}
			}
		}
	}

	static BlockState stairs(BlockState base, Direction facing, boolean top) {
		return base.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(BlockState base, boolean top) {
		return base.setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState candles(int n) {
		return Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, Math.max(1, Math.min(4, n))).setValue(BlockStateProperties.LIT, false);
	}

	static BlockState hangingLantern(boolean soul) {
		return (soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, true);
	}

	static void chest(Plan p, int x, int y, int z, Direction facing, ResourceKey<LootTable> loot) {
		p.set(x, y, z, Plan.facing(Blocks.CHEST.defaultBlockState(), facing));
		BlockPos pos = p.at(x, y, z);
		long seed = p.random.nextLong();
		p.then(level -> {
			if (level.getBlockEntity(pos) instanceof RandomizableContainer c) {
				c.setLootTable(loot, seed);
			}
		});
	}

	// ------------------------------------------------------------------ shrine

	static void shrine(ServerLevel level, Plan p, Site site) {
		ground(level, p, -4, 4, -4, 5, 5, Blocks.COBBLESTONE.defaultBlockState());
		p.mixed(-3, 0, -3, 3, 0, 3, RUIN, RUIN_W);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if ((Math.abs(x) == 3 || Math.abs(z) == 3) && p.random.nextFloat() < 0.25F) {
					p.set(x, 1, z, Blocks.MOSS_CARPET.defaultBlockState());
				}
			}
		}
		int[][] corners = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
		for (int[] c : corners) {
			int h = 1 + p.random.nextInt(3);
			for (int y = 1; y <= h; y++) {
				p.set(c[0], y, c[1], y == h ? Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState() : Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			}
		}
		p.set(0, 1, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		p.set(0, 2, 0, AwBlocks.SHRINE_STONE.defaultBlockState());
		p.set(-1, 1, 0, stairs(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.EAST, false));
		p.set(1, 1, 0, stairs(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.WEST, false));
		p.set(0, 1, -1, stairs(Blocks.STONE_BRICK_STAIRS.defaultBlockState(), Direction.SOUTH, false));
		p.set(-1, 2, 0, candles(1 + p.random.nextInt(3)));
		p.set(1, 2, 0, candles(1 + p.random.nextInt(3)));
		p.set(0, 2, -1, candles(2));
		for (int z = 4; z <= 5; z++) {
			p.set(0, 0, z, Blocks.DIRT_PATH.defaultBlockState());
		}
		// The hidden cache sleeps under the tile in front of the plinth.
		p.clear(-1, -3, 1, 1, -1, 3);
		p.fill(-2, -4, 0, 2, -4, 4, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
		p.set(0, 0, 2, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
		chest(p, 0, -1, 2, Direction.NORTH, LOOT_SHRINE);
		p.set(0, -2, 2, Blocks.COBBLESTONE.defaultBlockState());
	}

	/** The Pilgrim's arrival (or a curious traveller) opens the shrine's hidden cache. */
	public static void revealShrineCache(ServerLevel level, Site site) {
		BlockPos tile = site.pos().offset(0, 0, 2);
		level.setBlock(tile, AIR, 3);
	}

	// ------------------------------------------------------------------ anomaly

	static void anomaly(ServerLevel level, Plan p, Site site) {
		Edict edict = Edict.byOrdinal(site.data);
		if (edict == null) {
			edict = Edict.WEIGHTLESSNESS;
		}
		ground(level, p, -6, 6, -6, 6, 10, Blocks.STONE.defaultBlockState());
		p.fill(-1, 0, -1, 1, 0, 1, Blocks.CHISELED_TUFF.defaultBlockState());
		p.set(0, 1, 0, Blocks.POLISHED_TUFF.defaultBlockState());
		p.set(0, 2, 0, AwBlocks.ANOMALY_CORE.defaultBlockState());
		switch (edict) {
			case WEIGHTLESSNESS -> {
				ring(p, 3, 6, Blocks.ROOTED_DIRT.defaultBlockState(), 0.6F);
				for (int i = 0; i < 14; i++) {
					int x = p.random.nextInt(15) - 7;
					int z = p.random.nextInt(15) - 7;
					int y = 3 + p.random.nextInt(7);
					BlockState s = p.random.nextBoolean() ? Blocks.GRASS_BLOCK.defaultBlockState() : p.random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
					p.set(x, y, z, s);
					if (p.random.nextBoolean()) {
						p.set(x + 1, y, z, Blocks.DIRT.defaultBlockState());
					}
					if (p.random.nextFloat() < 0.3F) {
						p.set(x, y - 1, z, Blocks.ROOTED_DIRT.defaultBlockState());
					}
				}
				for (int y = 5; y <= 8; y++) {
					p.set(4, y, -3, Blocks.OAK_LOG.defaultBlockState());
				}
				p.fill(3, 9, -4, 5, 10, -2, Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
			}
			case ECHOES -> {
				ring(p, 2, 4, Blocks.CALCITE.defaultBlockState(), 0.8F);
				for (int i = 0; i < 6; i++) {
					double a = i * Math.PI / 3;
					int x = (int) Math.round(Math.cos(a) * 5);
					int z = (int) Math.round(Math.sin(a) * 5);
					int h = 4 - i / 2;
					for (int y = 1; y <= h; y++) {
						p.set(x, y, z, y == h ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
					}
					p.set(x, h + 1, z, Blocks.AMETHYST_CLUSTER.defaultBlockState());
				}
			}
			case STILLNESS -> {
				ring(p, 2, 6, Blocks.PACKED_ICE.defaultBlockState(), 0.5F);
				ring(p, 2, 4, Blocks.BLUE_ICE.defaultBlockState(), 0.25F);
				for (int i = 0; i < 8; i++) {
					int x = p.random.nextInt(11) - 5;
					int z = p.random.nextInt(11) - 5;
					int y = 2 + p.random.nextInt(4);
					p.set(x, y, z, Blocks.PACKED_ICE.defaultBlockState());
				}
			}
			case WITNESS -> {
				ring(p, 2, 5, Blocks.TUFF.defaultBlockState(), 0.7F);
				for (int i = 0; i < 4; i++) {
					Direction d = Direction.from2DDataValue(i);
					int x = d.getStepX() * 5;
					int z = d.getStepZ() * 5;
					for (int y = 1; y <= 4; y++) {
						p.set(x, y, z, Blocks.POLISHED_TUFF.defaultBlockState());
					}
					p.set(x - d.getStepX(), 3, z - d.getStepZ(), Blocks.SEA_LANTERN.defaultBlockState());
				}
			}
			case FORGOTTEN -> {
				ring(p, 2, 5, Blocks.SMOOTH_STONE.defaultBlockState(), 0.8F);
				for (int i = 0; i < 5; i++) {
					double a = i * Math.PI * 2 / 5;
					int x = (int) Math.round(Math.cos(a) * 5);
					int z = (int) Math.round(Math.sin(a) * 5);
					int h = 3 + p.random.nextInt(3);
					for (int y = 1; y <= h; y++) {
						if (p.random.nextFloat() < 0.85F) {
							p.set(x, y, z, Blocks.SMOOTH_STONE.defaultBlockState());
						}
					}
					p.set(x + 1, 1, z, Blocks.COBWEB.defaultBlockState());
				}
			}
			case HUNGER -> {
				ring(p, 2, 6, Blocks.SOUL_SOIL.defaultBlockState(), 0.5F);
				for (int z = -5; z <= 5; z += 2) {
					int h = 4 - Math.abs(z) / 2;
					for (int y = 1; y <= h; y++) {
						p.set(-4, y, z, Blocks.BONE_BLOCK.defaultBlockState());
						p.set(4, y, z, Blocks.BONE_BLOCK.defaultBlockState());
					}
					p.fill(-3, h, z, 3, h, z, Blocks.BONE_BLOCK.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
				}
				p.set(2, 1, 2, Blocks.SKELETON_SKULL.defaultBlockState());
			}
			case HOSPITALITY -> {
				ring(p, 2, 4, Blocks.MOSS_BLOCK.defaultBlockState(), 0.6F);
				p.fill(-1, 1, 3, 1, 1, 5, slab(Blocks.SPRUCE_SLAB.defaultBlockState(), true));
				p.set(-2, 1, 4, stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.EAST, false));
				p.set(2, 1, 4, stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.WEST, false));
				p.set(0, 1, 2, stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.SOUTH, false));
				p.set(0, 1, 6, stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.NORTH, false));
				p.set(-1, 2, 4, candles(3));
				p.set(1, 2, 4, Blocks.DECORATED_POT.defaultBlockState());
				p.set(0, 2, 5, Blocks.LANTERN.defaultBlockState());
			}
			default -> {
				ring(p, 2, 5, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 0.6F);
				for (int i = 0; i < 6; i++) {
					double a = i * Math.PI / 3;
					p.set((int) Math.round(Math.cos(a) * 4), 1, (int) Math.round(Math.sin(a) * 4), Blocks.POLISHED_ANDESITE.defaultBlockState());
				}
			}
		}
	}

	static void ring(Plan p, int inner, int outer, BlockState state, float density) {
		for (int x = -outer; x <= outer; x++) {
			for (int z = -outer; z <= outer; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d >= inner && d <= outer && p.random.nextFloat() < density) {
					p.set(x, 0, z, state);
				}
			}
		}
	}

	// ------------------------------------------------------------------ lair

	static void lair(ServerLevel level, Plan p) {
		ground(level, p, -7, 7, -7, 7, 6, Blocks.DIRT.defaultBlockState());
		BlockState[] floor = {Blocks.COARSE_DIRT.defaultBlockState(), Blocks.PODZOL.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				if (x * x + z * z <= 40) {
					p.set(x, 0, z, p.pick(floor, new float[] {0.45F, 0.25F, 0.2F, 0.1F}));
				}
			}
		}
		for (int i = 0; i < 28; i++) {
			double a = i * Math.PI * 2 / 28;
			if (p.random.nextFloat() < 0.65F) {
				p.set((int) Math.round(Math.cos(a) * 6), 1, (int) Math.round(Math.sin(a) * 6), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		for (int i = 0; i < 7; i++) {
			int x = p.random.nextInt(9) - 4;
			int z = p.random.nextInt(9) - 4;
			p.set(x, 1, z, Blocks.BONE_BLOCK.defaultBlockState().setValue(BlockStateProperties.AXIS, p.random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z));
		}
		p.set(2, 1, -2, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
		p.set(-3, 1, -3, Blocks.HAY_BLOCK.defaultBlockState());
		p.set(-3, 1, -2, Blocks.BARREL.defaultBlockState());
		p.set(-1, 1, 3, Blocks.SKELETON_SKULL.defaultBlockState());
		chest(p, -3, 1, 2, Direction.EAST, LOOT_LAIR);
	}

	// ------------------------------------------------------------------ cache

	static void cache(ServerLevel level, Plan p) {
		ground(level, p, -4, 4, -4, 4, 4, Blocks.DIRT.defaultBlockState());
		p.fill(-3, -4, -3, 3, 0, 3, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
		p.clear(-2, -3, -2, 2, -1, 2);
		p.ruin(-3, 0, -3, 3, 0, 3, Blocks.COBBLESTONE.defaultBlockState(), 0.7F);
		p.clear(1, 0, 1, 2, 0, 2);
		for (int y = -3; y <= 0; y++) {
			p.set(2, y, 2, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
		}
		p.set(2, -3, 1, Blocks.COBBLESTONE.defaultBlockState());
		chest(p, -2, -3, -2, Direction.SOUTH, LOOT_CACHE);
		p.set(0, -3, -2, Blocks.BARREL.defaultBlockState());
		p.set(-2, -3, 0, Blocks.LECTERN.defaultBlockState());
		p.set(-2, -1, 2, Blocks.COBWEB.defaultBlockState());
		p.set(0, -1, 0, hangingLantern(false));
	}

	// ------------------------------------------------------------------ caravan & road stop

	static void caravan(ServerLevel level, Plan p) {
		ground(level, p, -7, 7, -7, 7, 5, Blocks.DIRT.defaultBlockState());
		for (int z = -7; z <= 7; z++) {
			for (int x = -1; x <= 1; x++) {
				if (p.random.nextFloat() < 0.8F) {
					p.set(x, 0, z, Blocks.DIRT_PATH.defaultBlockState());
				}
			}
		}
		p.fill(2, 1, -2, 3, 1, 2, Blocks.SPRUCE_PLANKS.defaultBlockState());
		p.fill(2, 2, -2, 3, 2, 2, slab(Blocks.SPRUCE_SLAB.defaultBlockState(), false));
		p.set(4, 1, -2, Blocks.SPRUCE_FENCE.defaultBlockState());
		p.set(4, 1, 2, Blocks.SPRUCE_FENCE.defaultBlockState());
		p.set(2, 3, -1, Blocks.BARREL.defaultBlockState());
		p.set(3, 3, 1, Blocks.HAY_BLOCK.defaultBlockState());
		p.set(-3, 1, 3, Blocks.BARREL.defaultBlockState());
		p.set(-4, 1, -2, Blocks.HAY_BLOCK.defaultBlockState());
		p.set(-3, 1, -4, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
		chest(p, 3, 2, 0, Direction.WEST, LOOT_CARAVAN);
	}

	static void roadstop(ServerLevel level, Plan p) {
		ground(level, p, -4, 4, -4, 4, 5, Blocks.DIRT.defaultBlockState());
		p.fill(-1, 0, -4, 1, 0, 4, Blocks.DIRT_PATH.defaultBlockState());
		for (int y = 1; y <= 3; y++) {
			p.set(3, y, 0, Blocks.SPRUCE_FENCE.defaultBlockState());
		}
		p.set(3, 4, 0, Blocks.SPRUCE_PLANKS.defaultBlockState());
		p.set(2, 4, 0, Blocks.SPRUCE_FENCE.defaultBlockState());
		p.set(2, 3, 0, hangingLantern(false));
		p.set(-3, 1, -1, Blocks.BARREL.defaultBlockState());
		p.set(-3, 1, 1, Blocks.HAY_BLOCK.defaultBlockState());
	}

	// ------------------------------------------------------------------ the unremembered village

	static void unremembered(ServerLevel level, Plan p) {
		ground(level, p, -22, 22, -22, 22, 9, Blocks.DIRT.defaultBlockState());
		for (int x = -20; x <= 20; x++) {
			for (int z = -20; z <= 20; z++) {
				boolean road = Math.abs(x) <= 1 || Math.abs(z) <= 1;
				if (road && p.random.nextFloat() < 0.75F) {
					p.set(x, 0, z, Blocks.DIRT_PATH.defaultBlockState());
				} else if (p.random.nextFloat() < 0.04F) {
					p.set(x, 1, z, Blocks.TALL_GRASS.defaultBlockState());
				}
			}
		}
		// The well.
		p.walls(-2, 0, -2, 2, 1, 2, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 0.3F);
		p.fill(-1, -2, -1, 1, 0, 1, Blocks.WATER.defaultBlockState());
		// The bell post, its bell cracked silent.
		for (int y = 1; y <= 3; y++) {
			p.set(4, y, 4, Blocks.DARK_OAK_LOG.defaultBlockState());
		}
		p.set(4, 4, 4, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		p.set(4, 3, 5, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.SINGLE_WALL).setValue(BellBlock.FACING, Direction.SOUTH));
		int[][] houses = {{-12, -12, 0}, {10, -13, 1}, {-13, 10, 2}, {11, 11, 3}, {-3, -16, 4}};
		for (int[] h : houses) {
			house(p, h[0], h[1], h[2] == 0);
		}
		// Wild gardens where fields were.
		for (int x = 14; x <= 19; x++) {
			for (int z = -4; z <= 3; z++) {
				p.set(x, 0, z, Blocks.FARMLAND.defaultBlockState());
				if (p.random.nextFloat() < 0.6F) {
					p.set(x, 1, z, (p.random.nextBoolean() ? Blocks.WHEAT : Blocks.POTATOES).defaultBlockState().setValue(BlockStateProperties.AGE_7, p.random.nextInt(8)));
				}
			}
		}
	}

	static void house(Plan p, int cx, int cz, boolean archive) {
		int x1 = cx - 3, x2 = cx + 3, z1 = cz - 3, z2 = cz + 3;
		p.fill(x1, 0, z1, x2, 0, z2, Blocks.COBBLESTONE.defaultBlockState());
		p.walls(x1, 1, z1, x2, 4, z2, Blocks.SPRUCE_PLANKS.defaultBlockState(), 0.55F);
		for (int y = 1; y <= 4; y++) {
			if (p.random.nextFloat() < 0.9F || y < 3) {
				p.set(x1, y, z1, Blocks.SPRUCE_LOG.defaultBlockState());
				p.set(x2, y, z1, Blocks.SPRUCE_LOG.defaultBlockState());
				p.set(x1, y, z2, Blocks.SPRUCE_LOG.defaultBlockState());
				p.set(x2, y, z2, Blocks.SPRUCE_LOG.defaultBlockState());
			}
		}
		p.clear(cx, 1, z2, cx, 2, z2);
		p.clear(x1 + 1, 1, z1 + 1, x2 - 1, 4, z2 - 1);
		for (int x = x1; x <= x2; x++) {
			if (p.random.nextFloat() < 0.6F) {
				p.set(x, 5, z1 + 1, stairs(Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.SOUTH, false));
			}
			if (p.random.nextFloat() < 0.4F) {
				p.set(x, 5, z2 - 1, stairs(Blocks.DARK_OAK_STAIRS.defaultBlockState(), Direction.NORTH, false));
			}
		}
		p.set(cx + 2, 1, cz - 2, Blocks.COBWEB.defaultBlockState());
		p.set(cx - 2, 1, cz - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		if (archive) {
			chest(p, cx + 2, 1, cz + 1, Direction.WEST, LOOT_HOUSE);
			p.set(cx - 2, 1, cz + 1, Blocks.LECTERN.defaultBlockState());
			p.set(cx - 2, 1, cz, Blocks.BOOKSHELF.defaultBlockState());
		} else {
			p.set(cx + 2, 1, cz + 1, Blocks.BARREL.defaultBlockState());
		}
	}

	/** The census chest is the one in the archive house. */
	public static BlockPos censusChest(Site site) {
		return site.pos().offset(-12 + 2, 1, -12 + 1);
	}

	// ------------------------------------------------------------------ the hollow cathedral

	static void cathedral(ServerLevel level, Plan p) {
		BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		ground(level, p, -12, 12, -23, 19, 22, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
		// Foundation and floor.
		p.fill(-9, 0, -16, 9, 0, 18, floor);
		p.fill(0, 0, -14, 0, 0, 13, Blocks.CARPET.red().defaultBlockState());
		p.fill(0, 1, -14, 0, 1, 13, Blocks.CARPET.red().defaultBlockState());
		p.fill(0, 0, -14, 0, 0, 13, floor);
		// Walls.
		for (int y = 1; y <= 12; y++) {
			for (int z = -15; z <= 15; z++) {
				p.set(-8, y, z, p.pick(DEEP, DEEP_W));
				p.set(8, y, z, p.pick(DEEP, DEEP_W));
			}
			for (int x = -8; x <= 8; x++) {
				p.set(x, y, -15, p.pick(DEEP, DEEP_W));
				p.set(x, y, 15, p.pick(DEEP, DEEP_W));
			}
		}
		// Windows.
		for (int z = -11; z <= 11; z += 4) {
			for (int y = 3; y <= 9; y++) {
				p.set(-8, y, z, Blocks.STAINED_GLASS_PANE.gray().defaultBlockState());
				p.set(8, y, z, Blocks.STAINED_GLASS_PANE.gray().defaultBlockState());
			}
			// Buttresses.
			for (int y = 1; y <= 10; y++) {
				p.set(-9, y, z + 2, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
				p.set(9, y, z + 2, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
				if (y <= 6) {
					p.set(-10, y, z + 2, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
					p.set(10, y, z + 2, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
				}
			}
		}
		// Gabled roof along the nave.
		for (int layer = 0; layer <= 8; layer++) {
			int y = 13 + layer;
			int x = 9 - layer;
			for (int z = -16; z <= 16; z++) {
				if (x > 0) {
					p.set(-x, y, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState(), Direction.EAST, false));
					p.set(x, y, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState(), Direction.WEST, false));
					p.fill(-x + 1, y, z, x - 1, y, z, layer == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : AIR);
				} else {
					p.set(0, y, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
				}
			}
		}
		p.fill(-7, 13, -14, 7, 13, 14, Blocks.DEEPSLATE_TILES.defaultBlockState());
		// Roof holes: the cathedral is hollow in more ways than one.
		for (int i = 0; i < 5; i++) {
			int z = -12 + p.random.nextInt(24);
			int x = -4 + p.random.nextInt(8);
			p.clear(x, 13, z, x + 1, 16, z + 1);
		}
		belltower(p);
		// Entrance.
		p.clear(-1, 1, -15, 1, 5, -15);
		p.set(0, 6, -15, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
		for (int x = -2; x <= 2; x++) {
			p.set(x, 0, -23, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState(), Direction.SOUTH, false));
		}
		// Pews.
		for (int z = -11; z <= 7; z += 2) {
			for (int x = 2; x <= 6; x++) {
				if (p.random.nextFloat() < 0.85F) {
					p.set(x, 1, z, stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.SOUTH, false));
				}
				if (p.random.nextFloat() < 0.85F) {
					p.set(-x, 1, z, stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.SOUTH, false));
				}
			}
		}
		// Apse and altar.
		p.fill(-7, 1, 10, 7, 1, 14, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
		p.fill(-2, 2, 12, 2, 2, 13, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
		p.set(-2, 3, 12, candles(4));
		p.set(2, 3, 12, candles(3));
		p.set(0, 3, 13, Blocks.GILDED_BLACKSTONE.defaultBlockState());
		// The great chandelier hangs above the centre of the nave.
		for (int y = 9; y <= 12; y++) {
			p.set(0, y, 0, Blocks.IRON_CHAIN.defaultBlockState());
		}
		p.fill(-2, 8, -2, 2, 8, 2, Blocks.DARK_OAK_SLAB.defaultBlockState());
		p.clear(-1, 8, -1, 1, 8, 1);
		p.set(0, 8, 0, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			p.set(c[0], 7, c[1], hangingLantern(true));
		}
		// Loft above the entrance, with the counterweight release.
		p.fill(-7, 7, -14, 7, 7, -12, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		for (int x = -7; x <= 7; x++) {
			p.set(x, 8, -12, Blocks.DARK_OAK_FENCE.defaultBlockState());
		}
		for (int y = 1; y <= 7; y++) {
			p.set(-7, y, -14, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		}
		p.clear(-7, 8, -14, -7, 9, -14);
		p.set(5, 8, -14, AwBlocks.COUNTERWEIGHT.defaultBlockState());
		for (int y = 9; y <= 12; y++) {
			p.set(5, y, -14, Blocks.IRON_CHAIN.defaultBlockState());
		}
		// The archive, where the ringer's name was once written down.
		p.fill(9, 0, -5, 14, 0, 1, floor);
		p.walls(9, 1, -5, 14, 5, 1, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 0.1F);
		p.fill(9, 6, -5, 14, 6, 1, Blocks.DEEPSLATE_TILES.defaultBlockState());
		p.clear(10, 1, -4, 13, 5, 0);
		p.clear(8, 1, -2, 9, 3, -2);
		for (int z = -4; z <= 0; z++) {
			p.set(13, 1, z, Blocks.BOOKSHELF.defaultBlockState());
			p.set(13, 2, z, z % 2 == 0 ? Blocks.CHISELED_BOOKSHELF.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
		}
		chest(p, 10, 1, -4, Direction.SOUTH, LOOT_ARCHIVE);
		p.set(11, 1, 0, Blocks.LECTERN.defaultBlockState());
		p.set(11, 5, -2, hangingLantern(false));
		// Age and decay.
		for (int i = 0; i < 40; i++) {
			int x = -7 + p.random.nextInt(15);
			int y = 1 + p.random.nextInt(11);
			int z = -14 + p.random.nextInt(29);
			if (p.get(x, y, z) == null || p.get(x, y, z).isAir()) {
				if (y > 6 && p.random.nextFloat() < 0.5F) {
					p.set(x, y, z, Blocks.COBWEB.defaultBlockState());
				}
			}
		}
		for (int i = 0; i < 18; i++) {
			int side = p.random.nextBoolean() ? -8 : 8;
			int z = -14 + p.random.nextInt(29);
			int y = 2 + p.random.nextInt(9);
			p.set(side, y, z, AIR);
		}
		p.clear(-7, 1, -14, 7, 6, -13);
		p.fill(-7, 1, -14, -7, 7, -14, AIR);
		for (int y = 1; y <= 7; y++) {
			p.set(-7, y, -14, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		}
		p.set(-8, 1, -14, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
		for (int z = -10; z <= 10; z += 5) {
			p.set(-7, 9, z, Blocks.SOUL_LANTERN.defaultBlockState());
			p.set(7, 9, z, Blocks.SOUL_LANTERN.defaultBlockState());
			p.set(-7, 8, z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
			p.set(7, 8, z, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
		}
	}

	static void belltower(Plan p) {
		BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		for (int y = 1; y <= 26; y++) {
			for (int x = -4; x <= 4; x++) {
				for (int z = -22; z <= -15; z++) {
					boolean edge = x == -4 || x == 4 || z == -22 || z == -15;
					if (edge) {
						boolean arch = y >= 19 && y <= 23 && (Math.abs(x) <= 1 || z == -20 || z == -17) && !(x == -4 && z == -22);
						p.set(x, y, z, arch ? AIR : p.pick(DEEP, DEEP_W));
					}
				}
			}
		}
		for (int x = -4; x <= 4; x++) {
			for (int z = -22; z <= -15; z++) {
				p.set(x, 0, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
				p.set(x, 18, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
				p.set(x, 27, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
			}
		}
		for (int y = 1; y <= 5; y++) {
			for (int x = -1; x <= 1; x++) {
				p.set(x, y, -22, AIR);
			}
		}
		p.set(0, 6, -22, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
		// The spire.
		for (int layer = 0; layer < 6; layer++) {
			int r = 4 - layer * 4 / 6;
			for (int x = -r; x <= r; x++) {
				for (int z = -18 - r; z <= -18 + r; z++) {
					if (Math.abs(x) == r || Math.abs(z + 18) == r) {
						p.set(x, 28 + layer, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
					}
				}
			}
		}
		p.set(0, 34, -18, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState());
		// The belfry stays empty: its bell is down in the nave.
		for (int y = 19; y <= 22; y++) {
			p.set(0, y, -18, Blocks.IRON_CHAIN.defaultBlockState());
		}
		// A rose window over the door.
		for (int x = -3; x <= 3; x++) {
			for (int y = 8; y <= 14; y++) {
				double dx = x;
				double dy = y - 11;
				double r = Math.sqrt(dx * dx + dy * dy);
				if (r <= 3.2) {
					p.set(x, y, -15, r <= 1.2 ? Blocks.STAINED_GLASS_PANE.red().defaultBlockState()
						: (x + y) % 2 == 0 ? Blocks.STAINED_GLASS_PANE.purple().defaultBlockState() : Blocks.STAINED_GLASS_PANE.gray().defaultBlockState());
				}
			}
		}
	}

	/** Where the counterweight's chandelier will land. */
	public static BlockPos chandelier(Site site) {
		return site.pos().offset(0, 8, 0);
	}

	// ------------------------------------------------------------------ the vault of seconds

	public static final int VAULT_DEPTH = 22;

	/** How far below its clocktower this vault was dug (shallower on low terrain). */
	public static int depth(Site site) {
		return site.kind == SiteKind.VAULT && site.data >= 8 ? site.data : VAULT_DEPTH;
	}

	static void vault(ServerLevel level, Plan p, Site site) {
		ground(level, p, -5, 5, -5, 5, 18, Blocks.TUFF.defaultBlockState());
		// The clocktower.
		for (int y = 0; y <= 15; y++) {
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
					boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
					if (y == 0) {
						p.set(x, y, z, Blocks.POLISHED_TUFF.defaultBlockState());
					} else if (corner) {
						p.set(x, y, z, Blocks.CHISELED_TUFF.defaultBlockState());
					} else if (edge) {
						p.set(x, y, z, p.pick(TUFF, TUFF_W));
					} else {
						p.set(x, y, z, AIR);
					}
				}
			}
		}
		p.fill(-3, 16, -3, 3, 16, 3, Blocks.TUFF_BRICKS.defaultBlockState());
		p.fill(-2, 17, -2, 2, 17, 2, AwBlocks.GEARWORK.defaultBlockState());
		p.set(0, 18, 0, Blocks.LIGHTNING_ROD.waxed().oxidized().defaultBlockState());
		// Clock faces.
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int a = -1; a <= 1; a++) {
				for (int b = 10; b <= 12; b++) {
					int x = d.getStepX() * 3 + (d.getAxis() == Direction.Axis.Z ? a : 0);
					int z = d.getStepZ() * 3 + (d.getAxis() == Direction.Axis.X ? a : 0);
					p.set(x, b, z, a == 0 && b == 11 ? AwBlocks.ESCAPEMENT.defaultBlockState() : AwBlocks.GEARWORK.defaultBlockState());
				}
			}
		}
		p.clear(0, 1, 3, 0, 2, 3);
		p.set(0, 3, 3, Blocks.CHISELED_TUFF.defaultBlockState());
		// The shaft down.
		int d = -depth(site);
		for (int y = d + 1; y <= 0; y++) {
			p.fill(-2, y, -2, 2, y, 2, p.pick(TUFF, TUFF_W));
			p.clear(-1, y, -1, 1, y, 1);
			p.set(0, y, -1, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
			p.set(0, y, -2, AwBlocks.CLOCKWORK_BRICKS.defaultBlockState());
		}
		p.set(0, 0, -1, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		p.set(1, 0, 1, AIR);
		// Hub.
		room(p, -6, d, -6, 6, d + 6, 6);
		gearFloor(p, -5, d, -5, 5, 5);
		p.set(0, d + 5, 0, Blocks.COPPER_LANTERN.waxed().unaffected().defaultBlockState().setValue(LanternBlock.HANGING, true));
		p.clear(-1, d + 1, -1, 1, d + 5, 1);
		p.set(0, d + 1, -1, Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		// West wing.
		corridor(p, -16, d, -1, -6, 1);
		room(p, -28, d, -6, -16, d + 7, 6);
		gearFloor(p, -27, d, -5, -17, 5);
		p.set(-27, d + 2, 0, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		p.set(-28, d + 2, 0, Blocks.CHISELED_TUFF.defaultBlockState());
		// East wing.
		corridor(p, 6, d, -1, 16, 1);
		room(p, 16, d, -6, 28, d + 7, 6);
		gearFloor(p, 17, d, -5, 27, 5);
		p.set(27, d + 2, 0, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		p.set(28, d + 2, 0, Blocks.CHISELED_TUFF.defaultBlockState());
		// North corridor and the warded gate.
		corridor(p, -1, d, -16, 1, -6);
		p.fill(-1, d + 1, -12, 1, d + 3, -12, AwBlocks.WARDED_GEARWORK.defaultBlockState());
		// Throne room.
		room(p, -10, d, -36, 10, d + 10, -16);
		gearFloor(p, -9, d, -35, 9, -17);
		for (int[] c : new int[][] {{-7, -33}, {7, -33}, {-7, -19}, {7, -19}}) {
			p.set(c[0], d + 1, c[1], Blocks.POLISHED_TUFF.defaultBlockState());
			p.set(c[0], d + 2, c[1], AwBlocks.ESCAPEMENT.defaultBlockState());
			for (int y = d + 3; y <= d + 9; y++) {
				p.set(c[0], y, c[1], Blocks.COPPER_CHAIN.waxed().oxidized().defaultBlockState());
			}
		}
		for (int x = -9; x <= 9; x += 6) {
			for (int y = d + 1; y <= d + 9; y++) {
				p.set(x, y, -26, Blocks.TUFF_BRICKS.defaultBlockState());
			}
		}
		p.clear(-3, d + 1, -26, 3, d + 9, -26);
		p.fill(-2, d + 1, -35, 2, d + 1, -33, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		p.set(0, d + 2, -34, stairs(Blocks.POLISHED_DEEPSLATE_STAIRS.defaultBlockState(), Direction.SOUTH, false));
		p.set(-1, d + 2, -35, Blocks.GOLD_BLOCK.defaultBlockState());
		p.set(1, d + 2, -35, Blocks.GOLD_BLOCK.defaultBlockState());
		p.set(0, d + 3, -35, Blocks.GOLD_BLOCK.defaultBlockState());
		chest(p, 3, d + 1, -35, Direction.SOUTH, LOOT_VAULT);
		for (int x = -8; x <= 8; x += 4) {
			p.set(x, d + 9, -30, Blocks.COPPER_LANTERN.waxed().unaffected().defaultBlockState().setValue(LanternBlock.HANGING, true));
			p.set(x, d + 9, -22, Blocks.COPPER_LANTERN.waxed().unaffected().defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
	}

	static void room(Plan p, int x1, int y1, int z1, int x2, int y2, int z2) {
		for (int x = x1; x <= x2; x++) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) {
					boolean shell = x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2;
					p.set(x, y, z, shell ? p.pick(TUFF, TUFF_W) : AIR);
				}
			}
		}
		for (int x = x1 + 2; x <= x2 - 2; x += 4) {
			p.set(x, y2 - 1, z1 + 1, Blocks.COPPER_LANTERN.waxed().exposed().defaultBlockState().setValue(LanternBlock.HANGING, true));
			p.set(x, y2 - 1, z2 - 1, Blocks.COPPER_LANTERN.waxed().exposed().defaultBlockState().setValue(LanternBlock.HANGING, true));
		}
	}

	static void corridor(Plan p, int x1, int y, int z1, int x2, int z2) {
		for (int x = x1 - 1; x <= x2 + 1; x++) {
			for (int z = z1 - 1; z <= z2 + 1; z++) {
				for (int dy = 0; dy <= 5; dy++) {
					boolean inner = x >= x1 && x <= x2 && z >= z1 && z <= z2 && dy >= 1 && dy <= 4;
					p.set(x, y + dy, z, inner ? AIR : p.pick(TUFF, TUFF_W));
				}
			}
		}
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				p.set(x, y, z, AwBlocks.CLOCKWORK_BRICKS.defaultBlockState());
			}
		}
	}

	static void gearFloor(Plan p, int x1, int y, int z1, int x2, int z2) {
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				boolean gear = (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0);
				p.set(x, y, z, gear ? AwBlocks.GEARWORK.defaultBlockState() : AwBlocks.CLOCKWORK_BRICKS.defaultBlockState());
			}
		}
	}

	/** Positions used by the vault's mechanisms, relative to the site origin. */
	public static BlockPos vaultLever(Site site, boolean west) {
		return site.pos().offset(west ? -27 : 27, -depth(site) + 2, 0);
	}

	public static BlockPos vaultGate(Site site) {
		return site.pos().offset(0, -depth(site) + 1, -12);
	}

	public static BlockPos vaultRoomCenter(Site site, int wing) {
		return switch (wing) {
			case 0 -> site.pos().offset(-22, -depth(site) + 1, 0);
			case 1 -> site.pos().offset(22, -depth(site) + 1, 0);
			default -> site.pos().offset(0, -depth(site) + 1, -26);
		};
	}

	public static BlockPos[] escapements(Site site) {
		int d = -depth(site);
		return new BlockPos[] {site.pos().offset(-7, d + 2, -33), site.pos().offset(7, d + 2, -33), site.pos().offset(-7, d + 2, -19), site.pos().offset(7, d + 2, -19)};
	}
}

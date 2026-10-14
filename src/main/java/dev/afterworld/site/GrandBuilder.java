package dev.afterworld.site;

import dev.afterworld.Afterworld;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.registry.AwEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The grand structures, one for each faction plus the Archive of Almost. Each is laid out on a
 * {@link Plan} around its centre at ground level (y = 0) and built over a few seconds by
 * {@link Builds}. Entrances face north (-z).
 */
final class GrandBuilder {
	static final ResourceKey<LootTable> LOOT_ARMORY = loot("bastion_armory");
	static final ResourceKey<LootTable> LOOT_MAPS = loot("observatory_maps");
	static final ResourceKey<LootTable> LOOT_CRYPT = loot("mausoleum_crypt");
	static final ResourceKey<LootTable> LOOT_CONTAINMENT = loot("containment_archive");
	static final ResourceKey<LootTable> LOOT_ALMOST = loot("archive_almost");

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState[] KEEP = {Blocks.STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
		Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()};
	private static final float[] KEEP_W = {7, 2, 1, 1};
	private static final BlockState[] BLACK = {Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
		Blocks.POLISHED_BLACKSTONE.defaultBlockState()};
	private static final float[] BLACK_W = {7, 2, 1};
	private static final BlockState[] SLATE = {Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
		Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()};
	private static final float[] SLATE_W = {5, 3, 1};

	private GrandBuilder() {
	}

	private static ResourceKey<LootTable> loot(String name) {
		return ResourceKey.create(Registries.LOOT_TABLE, Afterworld.id("chests/" + name));
	}

	// ------------------------------------------------------------------ shapes

	private static BlockState block(net.minecraft.world.level.block.Block b) {
		return b.defaultBlockState();
	}

	private static BlockState facing(BlockState s, Direction d) {
		return Plan.facing(s, d);
	}

	/** A hollow round wall between radius r-1 and r, cleared inside. */
	static void roundWall(Plan p, int cx, int cz, double r, int y1, int y2, BlockState[] palette, float[] w) {
		int ir = (int) Math.ceil(r) + 1;
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				for (int y = y1; y <= y2; y++) {
					if (d <= r + 0.5 && d > r - 0.9) {
						p.set(cx + dx, y, cz + dz, p.pick(palette, w));
					} else if (d <= r - 0.9) {
						p.set(cx + dx, y, cz + dz, AIR);
					}
				}
			}
		}
	}

	static void disc(Plan p, int cx, int cz, double r, int y, BlockState s) {
		int ir = (int) Math.ceil(r);
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				if (dx * dx + dz * dz <= r * r + 0.5) {
					p.set(cx + dx, y, cz + dz, s);
				}
			}
		}
	}

	/** A stepped cone roof of rings, closed at the tip. */
	static void cone(Plan p, int cx, int cz, double r, int y, int height, BlockState s) {
		for (int i = 0; i <= height; i++) {
			double rr = r * (1 - i / (double) height);
			int ir = (int) Math.ceil(rr) + 1;
			for (int dx = -ir; dx <= ir; dx++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d <= rr + 0.5 && (d > rr - 1.2 || i == height)) {
						p.set(cx + dx, y + i, cz + dz, s);
					}
				}
			}
		}
	}

	static void dome(Plan p, int cx, int y0, int cz, double r, BlockState s) {
		int ir = (int) Math.ceil(r) + 1;
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dz = -ir; dz <= ir; dz++) {
				for (int dy = 0; dy <= ir; dy++) {
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (Math.abs(d - r) < 0.6) {
						p.set(cx + dx, y0 + dy, cz + dz, s);
					} else if (d < r - 0.6) {
						p.set(cx + dx, y0 + dy, cz + dz, AIR);
					}
				}
			}
		}
	}

	static void roundCrenels(Plan p, int cx, int cz, double r, int y, BlockState s) {
		int steps = (int) (r * 6);
		for (int i = 0; i < steps; i += 2) {
			double a = i * Math.PI * 2 / steps;
			p.set(cx + (int) Math.round(Math.cos(a) * r), y, cz + (int) Math.round(Math.sin(a) * r), s);
		}
	}

	/** Merlons on every other block of a rectangle's outline. */
	static void crenels(Plan p, int x1, int z1, int x2, int z2, int y, BlockState s) {
		for (int x = x1; x <= x2; x++) {
			if ((x - x1) % 2 == 0) {
				p.set(x, y, z1, s);
				p.set(x, y, z2, s);
			}
		}
		for (int z = z1; z <= z2; z++) {
			if ((z - z1) % 2 == 0) {
				p.set(x1, y, z, s);
				p.set(x2, y, z, s);
			}
		}
	}

	static void banner(Plan p, int x, int y, int z, Direction facing, DyeColor color) {
		p.set(x, y, z, Blocks.WALL_BANNER.pick(color).defaultBlockState().setValue(WallBannerBlock.FACING, facing));
	}

	static void lanternPost(Plan p, int x, int z, int y, BlockState post) {
		p.set(x, y, z, post);
		p.set(x, y + 1, z, post);
		p.set(x, y + 2, z, block(Blocks.LANTERN));
	}

	static BlockState litCandles(int n) {
		return SiteBuilder.candles(n).setValue(BlockStateProperties.LIT, true);
	}

	static void barrel(Plan p, int x, int y, int z, ResourceKey<LootTable> loot) {
		p.set(x, y, z, block(Blocks.BARREL).setValue(BarrelBlock.FACING, Direction.UP));
		BlockPos pos = p.at(x, y, z);
		long seed = p.random.nextLong();
		p.then(level -> {
			if (level.getBlockEntity(pos) instanceof net.minecraft.world.RandomizableContainer c) {
				c.setLootTable(loot, seed);
			}
		});
	}

	/** Spawns a persistent mob once the build lands. */
	static <T extends Mob> void spawn(Plan p, EntityType<T> type, int x, int y, int z, float yaw) {
		BlockPos pos = p.at(x, y, z);
		p.then(level -> {
			T mob = type.create(level, EntitySpawnReason.STRUCTURE);
			if (mob != null) {
				mob.snapTo(pos, yaw, 0);
				mob.setPersistenceRequired();
				level.addFreshEntity(mob);
			}
		});
	}

	static void armorStand(Plan p, int x, int y, int z, float yaw, boolean gold) {
		BlockPos pos = p.at(x, y, z);
		p.then(level -> {
			ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.STRUCTURE);
			if (stand != null) {
				stand.snapTo(pos, yaw, 0);
				stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(gold ? Items.GOLDEN_HELMET : Items.IRON_HELMET));
				stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
				stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
				level.addFreshEntity(stand);
			}
		});
	}

	// ------------------------------------------------------------------ the Crown Bastion

	static void bastion(ServerLevel level, Plan p) {
		SiteBuilder.ground(level, p, -21, 21, -21, 21, 28, block(Blocks.STONE_BRICKS));
		p.mixed(-16, 0, -16, 16, 0, 16, new BlockState[] {block(Blocks.STONE_BRICKS), block(Blocks.COBBLESTONE), block(Blocks.ANDESITE), block(Blocks.GRAVEL)},
			new float[] {4, 3, 2, 1});
		p.fill(-1, 0, -21, 1, 0, -3, block(Blocks.POLISHED_ANDESITE));
		BlockState trim = block(Blocks.CHISELED_STONE_BRICKS);
		// Curtain walls with a walk along the top.
		p.mixed(-18, 1, -18, 18, 9, -17, KEEP, KEEP_W);
		p.mixed(-18, 1, 17, 18, 9, 18, KEEP, KEEP_W);
		p.mixed(-18, 1, -18, -17, 9, 18, KEEP, KEEP_W);
		p.mixed(17, 1, -18, 18, 9, 18, KEEP, KEEP_W);
		crenels(p, -18, -18, 18, 18, 10, block(Blocks.STONE_BRICKS));
		for (int x = -16; x <= 16; x += 4) {
			p.set(x, 6, -18, block(Blocks.IRON_BARS));
			p.set(x, 6, 18, block(Blocks.IRON_BARS));
		}
		// Steps up to the wall walk, inside the east wall.
		for (int i = 0; i < 9; i++) {
			p.set(16, 1 + i, -8 + i, SiteBuilder.stairs(block(Blocks.STONE_BRICK_STAIRS), Direction.SOUTH, false));
			p.fill(16, 1, -8 + i, 16, i, -8 + i, block(Blocks.STONE_BRICKS));
		}
		// Corner towers with conical slate roofs.
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int cx = sx * 18;
				int cz = sz * 18;
				roundWall(p, cx, cz, 4.5, 1, 17, KEEP, KEEP_W);
				disc(p, cx, cz, 3.6, 0, block(Blocks.POLISHED_ANDESITE));
				disc(p, cx, cz, 3.6, 9, block(Blocks.SPRUCE_PLANKS));
				disc(p, cx, cz, 3.6, 17, block(Blocks.SPRUCE_PLANKS));
				roundCrenels(p, cx, cz, 4.5, 18, block(Blocks.STONE_BRICKS));
				cone(p, cx, cz, 5.0, 19, 8, block(Blocks.DEEPSLATE_TILES));
				p.set(cx, 28, cz, block(Blocks.LIGHTNING_ROD.waxed().unaffected()));
				p.clear(cx - sx * 4, 10, cz, cx - sx * 4, 11, cz);
				p.clear(cx, 10, cz - sz * 4, cx, 11, cz - sz * 4);
				p.clear(cx - sx * 4, 1, cz - sz * 2, cx - sx * 4, 3, cz - sz * 2);
				for (int y : new int[] {5, 13}) {
					p.set(cx + sx * 4, y, cz, block(Blocks.IRON_BARS));
					p.set(cx, y, cz + sz * 4, block(Blocks.IRON_BARS));
				}
				p.set(cx, 16, cz, SiteBuilder.hangingLantern(false));
				banner(p, cx + sx * 5, 15, cz, sx > 0 ? Direction.EAST : Direction.WEST, sz < 0 ? DyeColor.RED : DyeColor.YELLOW);
			}
		}
		// Gatehouse on the north wall.
		for (int sx = -1; sx <= 1; sx += 2) {
			int x1 = sx * 3;
			int x2 = sx * 7;
			p.mixed(Math.min(x1, x2), 1, -21, Math.max(x1, x2), 14, -15, KEEP, KEEP_W);
			p.clear(Math.min(x1, x2) + 1, 1, -20, Math.max(x1, x2) - 1, 13, -16);
			crenels(p, Math.min(x1, x2), -21, Math.max(x1, x2), -15, 15, block(Blocks.STONE_BRICKS));
			banner(p, sx * 5, 11, -22, Direction.NORTH, DyeColor.RED);
			banner(p, sx * 5, 8, -22, Direction.NORTH, DyeColor.YELLOW);
		}
		p.mixed(-3, 7, -21, 3, 14, -15, KEEP, KEEP_W);
		p.clear(-2, 1, -21, 2, 6, -15);
		p.fill(-2, 6, -21, 2, 6, -21, block(Blocks.IRON_BARS));
		p.fill(-2, 5, -21, 2, 5, -21, block(Blocks.IRON_BARS));
		p.fill(-3, 7, -22, 3, 7, -22, trim);
		p.set(0, 9, -22, block(Blocks.GOLD_BLOCK));
		p.set(-1, 10, -22, block(Blocks.GOLD_BLOCK));
		p.set(1, 10, -22, block(Blocks.GOLD_BLOCK));
		p.set(0, 11, -22, facing(block(Blocks.LANTERN), Direction.NORTH));
		crenels(p, -3, -21, 3, -15, 15, block(Blocks.STONE_BRICKS));

		keep(p);

		// Courtyard: a well, training dummies, a smithy, lamp posts.
		p.fill(-12, 0, 5, -9, 0, 8, block(Blocks.WATER));
		for (int x = -13; x <= -8; x++) {
			for (int z = 4; z <= 9; z++) {
				if (x == -13 || x == -8 || z == 4 || z == 9) {
					p.set(x, 1, z, block(Blocks.COBBLESTONE));
				}
			}
		}
		for (int[] c : new int[][] {{-13, 4}, {-8, 4}, {-13, 9}, {-8, 9}}) {
			p.fill(c[0], 2, c[1], c[0], 3, c[1], block(Blocks.SPRUCE_FENCE));
		}
		p.fill(-13, 4, 4, -8, 4, 9, SiteBuilder.slab(block(Blocks.SPRUCE_SLAB), false));
		for (int i = 0; i < 3; i++) {
			int x = 9 + i * 2;
			p.set(x, 1, -9, block(Blocks.SPRUCE_FENCE));
			p.set(x, 2, -9, block(Blocks.HAY_BLOCK));
			p.set(x, 3, -9, facing(block(Blocks.CARVED_PUMPKIN), Direction.NORTH));
		}
		p.set(10, 1, 8, block(Blocks.ANVIL));
		p.set(12, 1, 8, block(Blocks.GRINDSTONE));
		p.set(13, 1, 10, facing(block(Blocks.BLAST_FURNACE), Direction.WEST));
		p.set(13, 1, 12, block(Blocks.LAVA_CAULDRON));
		p.set(11, 1, 12, block(Blocks.SMITHING_TABLE));
		p.fill(9, 4, 7, 14, 4, 13, SiteBuilder.slab(block(Blocks.DARK_OAK_SLAB), false));
		for (int[] c : new int[][] {{9, 7}, {14, 7}, {9, 13}, {14, 13}}) {
			p.fill(c[0], 1, c[1], c[0], 3, c[1], block(Blocks.DARK_OAK_FENCE));
		}
		for (int z = -14; z <= -6; z += 4) {
			lanternPost(p, -3, z, 1, block(Blocks.STONE_BRICK_WALL));
			lanternPost(p, 3, z, 1, block(Blocks.STONE_BRICK_WALL));
		}
		spawn(p, EntityTypes.IRON_GOLEM, -4, 1, -13, 180);
		spawn(p, EntityTypes.IRON_GOLEM, 4, 1, -13, 180);
	}

	/** The keep: great hall below, armoury above, the lord's study at the top. */
	private static void keep(Plan p) {
		int x1 = -7;
		int x2 = 7;
		int z1 = -2;
		int z2 = 12;
		p.mixed(x1, 1, z1, x2, 21, z2, KEEP, KEEP_W);
		p.clear(x1 + 1, 1, z1 + 1, x2 - 1, 20, z2 - 1);
		p.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, block(Blocks.POLISHED_ANDESITE));
		p.fill(x1 + 1, 7, z1 + 1, x2 - 1, 7, z2 - 1, block(Blocks.SPRUCE_PLANKS));
		p.fill(x1 + 1, 14, z1 + 1, x2 - 1, 14, z2 - 1, block(Blocks.SPRUCE_PLANKS));
		p.fill(x1, 21, z1, x2, 21, z2, block(Blocks.STONE_BRICKS));
		crenels(p, x1, z1, x2, z2, 22, block(Blocks.STONE_BRICKS));
		for (int[] c : new int[][] {{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
			p.fill(c[0], 22, c[1], c[0], 24, c[1], block(Blocks.STONE_BRICKS));
			p.set(c[0], 25, c[1], block(Blocks.LANTERN));
		}
		p.fill(0, 22, 5, 0, 26, 5, block(Blocks.SPRUCE_FENCE));
		banner(p, 1, 26, 5, Direction.EAST, DyeColor.RED);
		banner(p, -1, 26, 5, Direction.WEST, DyeColor.YELLOW);
		// Door and arched windows.
		p.clear(-1, 1, z1, 1, 3, z1);
		p.set(0, 4, z1, block(Blocks.CHISELED_STONE_BRICKS));
		for (int y : new int[] {3, 9, 16}) {
			for (int k = -4; k <= 4; k += 4) {
				for (int dy = 0; dy < 3; dy++) {
					p.set(x1, y + dy, 5 + k, block(Blocks.GLASS_PANE));
					p.set(x2, y + dy, 5 + k, block(Blocks.GLASS_PANE));
					if (k != 0 || y != 3) {
						p.set(k, y + dy, z2, block(Blocks.GLASS_PANE));
					}
				}
			}
		}
		// Great hall: carpet aisle, two long tables, a throne, chandeliers, a hearth.
		p.fill(0, 1, z1 + 1, 0, 1, 9, Blocks.CARPET.pick(DyeColor.RED).defaultBlockState());
		for (int sx = -1; sx <= 1; sx += 2) {
			int tx = sx * 3;
			p.fill(tx, 1, 1, tx, 1, 8, SiteBuilder.slab(block(Blocks.DARK_OAK_SLAB), true));
			for (int z = 1; z <= 8; z += 2) {
				p.set(tx + sx, 1, z, SiteBuilder.stairs(block(Blocks.SPRUCE_STAIRS), sx > 0 ? Direction.EAST : Direction.WEST, false));
				p.set(tx - sx, 1, z, SiteBuilder.stairs(block(Blocks.SPRUCE_STAIRS), sx > 0 ? Direction.WEST : Direction.EAST, false));
			}
			p.set(tx, 2, 3, litCandles(3));
			p.set(tx, 2, 6, litCandles(2));
		}
		p.fill(-2, 1, 10, 2, 1, 11, block(Blocks.POLISHED_BLACKSTONE));
		p.set(0, 2, 10, SiteBuilder.stairs(block(Blocks.POLISHED_BLACKSTONE_STAIRS), Direction.SOUTH, false));
		p.set(-1, 2, 10, block(Blocks.GOLD_BLOCK));
		p.set(1, 2, 10, block(Blocks.GOLD_BLOCK));
		banner(p, -2, 5, z2 - 1, Direction.NORTH, DyeColor.RED);
		banner(p, 0, 6, z2 - 1, Direction.NORTH, DyeColor.YELLOW);
		banner(p, 2, 5, z2 - 1, Direction.NORTH, DyeColor.RED);
		for (int z = 1; z <= 9; z += 4) {
			p.set(0, 6, z, block(Blocks.IRON_CHAIN));
			p.set(0, 5, z, SiteBuilder.hangingLantern(false));
		}
		p.fill(x1 + 1, 1, 4, x1 + 1, 3, 6, block(Blocks.BRICKS));
		p.set(x1 + 1, 1, 5, facing(block(Blocks.CAMPFIRE), Direction.EAST));
		// Stairs: east wall up to the armoury, west wall up to the study.
		for (int i = 0; i < 7; i++) {
			p.set(5, 1 + i, i, SiteBuilder.stairs(block(Blocks.SPRUCE_STAIRS), Direction.SOUTH, false));
			p.set(6, 1 + i, i, SiteBuilder.stairs(block(Blocks.SPRUCE_STAIRS), Direction.SOUTH, false));
		}
		p.clear(5, 7, 3, 6, 7, 6);
		for (int i = 0; i < 7; i++) {
			p.set(-5, 8 + i, 10 - i, SiteBuilder.stairs(block(Blocks.SPRUCE_STAIRS), Direction.NORTH, false));
			p.set(-6, 8 + i, 10 - i, SiteBuilder.stairs(block(Blocks.SPRUCE_STAIRS), Direction.NORTH, false));
		}
		p.clear(-6, 14, 4, -5, 14, 7);
		// Armoury.
		armorStand(p, -4, 8, 1, 180, false);
		armorStand(p, -2, 8, 1, 180, false);
		armorStand(p, 2, 8, 1, 180, true);
		SiteBuilder.chest(p, -6, 8, 11, Direction.EAST, LOOT_ARMORY);
		SiteBuilder.chest(p, 3, 8, 11, Direction.NORTH, LOOT_ARMORY);
		p.set(1, 8, 11, block(Blocks.BARREL));
		p.set(0, 8, 11, block(Blocks.BARREL));
		p.set(-1, 8, 11, block(Blocks.FLETCHING_TABLE));
		p.set(0, 13, 5, SiteBuilder.hangingLantern(false));
		// The lord's study.
		p.fill(x1 + 1, 15, z2 - 1, x2 - 1, 17, z2 - 1, block(Blocks.BOOKSHELF));
		p.set(0, 15, 9, facing(block(Blocks.LECTERN), Direction.NORTH));
		SiteBuilder.chest(p, 4, 15, 9, Direction.WEST, LOOT_ARMORY);
		p.set(-3, 15, 2, block(Blocks.CARTOGRAPHY_TABLE));
		p.set(0, 20, 5, SiteBuilder.hangingLantern(false));
		banner(p, 0, 18, z2 - 1, Direction.NORTH, DyeColor.YELLOW);
	}

	// ------------------------------------------------------------------ the Guild Observatory

	static void observatory(ServerLevel level, Plan p) {
		SiteBuilder.ground(level, p, -13, 13, -13, 13, 46, block(Blocks.CALCITE));
		BlockState copper = block(Blocks.CUT_COPPER.waxed().unaffected());
		BlockState verdigris = block(Blocks.COPPER_BLOCK.waxed().oxidized());
		p.fill(-1, 0, -13, 1, 0, 13, block(Blocks.SMOOTH_STONE));
		p.fill(-13, 0, -1, 13, 0, 1, block(Blocks.SMOOTH_STONE));
		// Octagonal hall with a compass rose set in the floor.
		for (int dx = -10; dx <= 10; dx++) {
			for (int dz = -10; dz <= 10; dz++) {
				double oct = Math.max(Math.max(Math.abs(dx), Math.abs(dz)), (Math.abs(dx) + Math.abs(dz)) * 0.7071);
				if (oct <= 9.5 && oct > 8.5) {
					for (int y = 1; y <= 7; y++) {
						p.set(dx, y, dz, y == 1 || y == 7 ? copper : block(Blocks.CALCITE));
					}
					p.set(dx, 8, dz, block(Blocks.SMOOTH_STONE));
					if ((dx + dz) % 2 == 0) {
						p.set(dx, 9, dz, block(Blocks.CALCITE));
					}
				} else if (oct <= 8.5) {
					p.set(dx, 0, dz, rose(dx, dz));
					p.fill(dx, 1, dz, dx, 7, dz, AIR);
					p.set(dx, 8, dz, block(Blocks.SMOOTH_STONE));
				}
			}
		}
		for (int[] w : new int[][] {{0, -9}, {0, 9}, {-9, 0}, {9, 0}, {-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
			for (int y = 3; y <= 5; y++) {
				p.set(w[0], y, w[1], block(Blocks.GLASS_PANE));
			}
		}
		p.clear(-1, 1, -9, 1, 3, -9);
		p.set(0, 4, -9, block(Blocks.COPPER_BULB.waxed().unaffected()).setValue(BlockStateProperties.LIT, true));
		for (int[] c : new int[][] {{-6, -4}, {6, -4}, {-6, 4}, {6, 4}}) {
			p.set(c[0], 1, c[1], block(Blocks.BOOKSHELF));
			p.set(c[0], 2, c[1], block(Blocks.BOOKSHELF));
		}
		p.set(-4, 1, -6, block(Blocks.CARTOGRAPHY_TABLE));
		p.set(4, 1, -6, facing(block(Blocks.LECTERN), Direction.NORTH));
		SiteBuilder.chest(p, 7, 1, 0, Direction.WEST, LOOT_MAPS);
		for (int[] c : new int[][] {{-5, 0}, {5, 0}, {0, 5}}) {
			p.set(c[0], 7, c[1], SiteBuilder.hangingLantern(false));
		}
		// The tower, with a copper spiral stair around a verdigris column.
		roundWall(p, 0, 0, 4.5, 1, 34, new BlockState[] {block(Blocks.CALCITE), block(Blocks.POLISHED_DIORITE)}, new float[] {5, 1});
		for (int y = 6; y <= 34; y += 6) {
			roundWall(p, 0, 0, 4.5, y, y, new BlockState[] {copper}, new float[] {1});
		}
		p.clear(0, 1, -4, 0, 3, -4);
		p.clear(-1, 1, -4, 1, 3, -4);
		p.fill(0, 1, 0, 0, 34, 0, verdigris);
		for (int y = 1; y <= 33; y++) {
			double a = y * Math.PI * 2 / 12;
			for (int r = 1; r <= 3; r++) {
				p.set((int) Math.round(Math.cos(a) * r), y, (int) Math.round(Math.sin(a) * r), copper);
			}
		}
		for (int y : new int[] {12, 18, 24, 30}) {
			for (int[] d : new int[][] {{0, -4}, {0, 4}, {-4, 0}, {4, 0}}) {
				p.set(d[0], y, d[1], block(Blocks.GLASS_PANE));
				p.set(d[0], y + 1, d[1], block(Blocks.GLASS_PANE));
			}
		}
		// The map room: a floor at 18 with a gap where the stair passes.
		double stairA = 18 * Math.PI * 2 / 12;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (dx * dx + dz * dz > 13 || dx == 0 && dz == 0) {
					continue;
				}
				double a = Math.atan2(dz, dx);
				double diff = Math.abs(Math.atan2(Math.sin(a - stairA), Math.cos(a - stairA)));
				if (diff > 0.9) {
					p.set(dx, 18, dz, block(Blocks.SPRUCE_PLANKS));
				}
			}
		}
		p.set(-3, 19, 1, block(Blocks.CARTOGRAPHY_TABLE));
		p.set(3, 19, -1, facing(block(Blocks.LECTERN), Direction.WEST));
		SiteBuilder.chest(p, 1, 19, 3, Direction.NORTH, LOOT_MAPS);
		// Observation deck, railing and dome with a telescope.
		disc(p, 0, 0, 6.5, 34, copper);
		p.set(0, 34, 0, verdigris);
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI * 2 / 40;
			p.set((int) Math.round(Math.cos(a) * 6.5), 35, (int) Math.round(Math.sin(a) * 6.5), block(Blocks.IRON_BARS));
		}
		dome(p, 0, 35, 0, 5.0, verdigris);
		for (int y = 37; y <= 41; y++) {
			p.set(0, y, -5, AIR);
			p.set(0, y, -4, AIR);
		}
		p.clear(-1, 35, 4, 1, 37, 5);
		for (int i = 0; i <= 4; i++) {
			p.set(0, 36 + i, 1 - i, block(Blocks.COPPER_BLOCK.waxed().unaffected()));
		}
		p.set(0, 41, -4, block(Blocks.LIGHTNING_ROD.waxed().unaffected()));
		p.set(0, 35, 2, block(Blocks.COPPER_BLOCK.waxed().exposed()));
		p.set(2, 35, 0, facing(block(Blocks.LECTERN), Direction.WEST));
		SiteBuilder.chest(p, -2, 35, 1, Direction.EAST, LOOT_MAPS);
		p.set(-2, 39, 0, SiteBuilder.hangingLantern(false));
		// Obelisks at the corners of the grounds.
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				p.set(sx * 11, 1, sz * 11, copper);
				p.fill(sx * 11, 2, sz * 11, sx * 11, 5, sz * 11, block(Blocks.CALCITE));
				p.set(sx * 11, 6, sz * 11, block(Blocks.LIGHTNING_ROD.waxed().unaffected()));
				p.set(sx * 11 - sx, 1, sz * 11, block(Blocks.COPPER_LANTERN.waxed().unaffected()));
			}
		}
	}

	/** The compass rose: four blue points, four cyan, a copper heart, on pale tile. */
	private static BlockState rose(int dx, int dz) {
		int ax = Math.abs(dx);
		int az = Math.abs(dz);
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d < 1.5) {
			return block(Blocks.CUT_COPPER.waxed().unaffected());
		}
		if (ax == 0 && az <= 7 || az == 0 && ax <= 7 || (ax <= 1 && az <= 7 - 3) || (az <= 1 && ax <= 7 - 3)) {
			return Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE).defaultBlockState();
		}
		if (ax == az && ax <= 5) {
			return Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN).defaultBlockState();
		}
		if (d > 7.2 && d < 8.2) {
			return Blocks.DYED_TERRACOTTA.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
		}
		return ((ax + az) % 2 == 0 ? Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE) : Blocks.DYED_TERRACOTTA.pick(DyeColor.LIGHT_GRAY)).defaultBlockState();
	}

	// ------------------------------------------------------------------ the Morrow Court Mausoleum

	static void mausoleum(ServerLevel level, Plan p) {
		SiteBuilder.ground(level, p, -17, 17, -21, 21, 22, block(Blocks.STONE));
		// Graveyard fence, path and graves.
		for (int x = -17; x <= 17; x++) {
			if (Math.abs(x) > 1) {
				p.set(x, 1, -21, block(Blocks.IRON_BARS));
			}
			p.set(x, 1, 21, block(Blocks.IRON_BARS));
		}
		for (int z = -21; z <= 21; z++) {
			p.set(-17, 1, z, block(Blocks.IRON_BARS));
			p.set(17, 1, z, block(Blocks.IRON_BARS));
		}
		p.set(-2, 1, -21, block(Blocks.POLISHED_BLACKSTONE_WALL));
		p.set(2, 1, -21, block(Blocks.POLISHED_BLACKSTONE_WALL));
		p.set(-2, 2, -21, block(Blocks.SOUL_LANTERN));
		p.set(2, 2, -21, block(Blocks.SOUL_LANTERN));
		p.mixed(-1, 0, -21, 1, 0, -11, new BlockState[] {block(Blocks.COBBLESTONE), block(Blocks.GRAVEL), block(Blocks.MOSSY_COBBLESTONE)}, new float[] {3, 2, 1});
		for (int z = -19; z <= -12; z += 3) {
			for (int x : new int[] {-14, -11, -8, -5, 5, 8, 11, 14}) {
				grave(p, x, z);
			}
		}
		for (int z = -6; z <= 18; z += 3) {
			grave(p, -14, z);
			grave(p, 14, z);
		}
		deadTree(p, -12, 15);
		deadTree(p, 12, -6);
		for (int sx = -1; sx <= 1; sx += 2) {
			p.set(sx * 4, 1, -12, block(Blocks.POLISHED_BLACKSTONE_WALL));
			p.set(sx * 4, 2, -12, block(Blocks.SOUL_CAMPFIRE));
		}
		// The mausoleum on its plinth, with a white portico.
		p.fill(-11, 0, -11, 11, 1, 16, block(Blocks.POLISHED_BLACKSTONE_BRICKS));
		for (int x = -3; x <= 3; x++) {
			p.set(x, 1, -12, SiteBuilder.stairs(block(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS), Direction.SOUTH, false));
		}
		p.mixed(-9, 2, -6, 9, 12, 14, BLACK, BLACK_W);
		p.clear(-8, 2, -5, 8, 11, 13);
		for (int[] c : new int[][] {{-9, -6}, {9, -6}, {-9, 14}, {9, 14}}) {
			p.fill(c[0], 2, c[1], c[0], 12, c[1], block(Blocks.QUARTZ_PILLAR));
		}
		for (int x : new int[] {-7, -3, 3, 7}) {
			p.fill(x, 2, -10, x, 10, -10, block(Blocks.QUARTZ_PILLAR));
			p.set(x, 11, -10, block(Blocks.CHISELED_QUARTZ_BLOCK));
		}
		p.fill(-9, 12, -11, 9, 12, -7, block(Blocks.POLISHED_BLACKSTONE));
		p.fill(-9, 2, -10, 9, 2, -7, block(Blocks.POLISHED_DEEPSLATE));
		// Gabled roof over portico and hall.
		for (int layer = 0; layer <= 9; layer++) {
			int y = 13 + layer;
			int x = 10 - layer;
			for (int z = -11; z <= 15; z++) {
				if (x > 0) {
					p.set(-x, y, z, SiteBuilder.stairs(block(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS), Direction.EAST, false));
					p.set(x, y, z, SiteBuilder.stairs(block(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS), Direction.WEST, false));
				} else {
					p.set(0, y, z, block(Blocks.POLISHED_BLACKSTONE_BRICKS));
				}
			}
			if (x > 0) {
				p.fill(-x + 1, y, -11, x - 1, y, -11, block(Blocks.QUARTZ_BLOCK));
			}
		}
		p.set(0, 17, -12, block(Blocks.SOUL_LANTERN));
		// The hall: an aisle between sarcophagi, candles, an angel at the far end.
		p.clear(-1, 2, -6, 1, 5, -6);
		p.set(0, 6, -6, block(Blocks.CHISELED_POLISHED_BLACKSTONE));
		p.fill(-8, 1, -5, 8, 1, 13, block(Blocks.POLISHED_DEEPSLATE));
		p.fill(0, 2, -5, 0, 2, 11, Blocks.CARPET.pick(DyeColor.BLACK).defaultBlockState());
		for (int z : new int[] {-2, 3, 8}) {
			for (int sx = -1; sx <= 1; sx += 2) {
				int x = sx * 5;
				p.fill(x, 2, z, x, 2, z + 1, block(Blocks.POLISHED_DEEPSLATE));
				p.fill(x - 1, 2, z, x - 1, 2, z + 1, SiteBuilder.stairs(block(Blocks.POLISHED_DEEPSLATE_STAIRS), Direction.WEST, false));
				p.fill(x + 1, 2, z, x + 1, 2, z + 1, SiteBuilder.stairs(block(Blocks.POLISHED_DEEPSLATE_STAIRS), Direction.EAST, false));
				p.set(x, 3, z, SiteBuilder.slab(block(Blocks.SMOOTH_QUARTZ_SLAB), false));
				p.set(x, 3, z + 1, SiteBuilder.slab(block(Blocks.SMOOTH_QUARTZ_SLAB), false));
				p.set(sx * 8, 2, z, litCandles(1 + p.random.nextInt(4)));
			}
		}
		p.fill(-1, 2, 12, 1, 2, 12, block(Blocks.QUARTZ_BLOCK));
		p.fill(0, 3, 12, 0, 4, 12, block(Blocks.QUARTZ_PILLAR));
		p.set(0, 5, 12, block(Blocks.CHISELED_QUARTZ_BLOCK));
		p.set(-1, 4, 12, SiteBuilder.stairs(block(Blocks.QUARTZ_STAIRS), Direction.EAST, true));
		p.set(1, 4, 12, SiteBuilder.stairs(block(Blocks.QUARTZ_STAIRS), Direction.WEST, true));
		p.set(0, 2, 10, facing(block(Blocks.LECTERN), Direction.NORTH));
		p.set(-2, 2, 11, litCandles(4));
		p.set(2, 2, 11, litCandles(4));
		for (int z = -2; z <= 10; z += 4) {
			p.set(0, 11, z, block(Blocks.IRON_CHAIN));
			p.set(0, 10, z, SiteBuilder.hangingLantern(true));
		}
		// Down to the crypt.
		for (int i = 0; i < 8; i++) {
			p.set(7, 1 - i, 5 + i, SiteBuilder.stairs(block(Blocks.POLISHED_BLACKSTONE_STAIRS), Direction.SOUTH, false));
			p.set(6, 1 - i, 5 + i, SiteBuilder.stairs(block(Blocks.POLISHED_BLACKSTONE_STAIRS), Direction.SOUTH, false));
			p.clear(6, 2 - i, 5 + i, 7, 4 - i, 5 + i);
		}
		crypt(p);
	}

	private static void crypt(Plan p) {
		BlockState[] ossuary = {block(Blocks.BONE_BLOCK), block(Blocks.POLISHED_BLACKSTONE_BRICKS), block(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)};
		float[] ow = {3, 4, 2};
		p.mixed(-9, -8, -4, 9, -1, 15, ossuary, ow);
		p.clear(-8, -7, -3, 8, -2, 14);
		p.fill(-8, -8, -3, 8, -8, 14, block(Blocks.POLISHED_DEEPSLATE));
		p.clear(6, -7, 12, 7, -2, 13);
		for (int z = -2; z <= 12; z += 3) {
			for (int sx = -1; sx <= 1; sx += 2) {
				int x = sx * 8;
				p.set(x, -5, z, block(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, sx > 0 ? 12 : 4));
				p.set(x, -6, z, litCandles(1 + p.random.nextInt(3)));
				p.set(x, -6, z + 1, block(Blocks.COBWEB));
			}
		}
		for (int z = -1; z <= 11; z += 4) {
			p.set(0, -2, z, SiteBuilder.hangingLantern(true));
		}
		SiteBuilder.chest(p, -7, -7, 6, Direction.EAST, LOOT_CRYPT);
		SiteBuilder.chest(p, 7, -7, 2, Direction.WEST, LOOT_CRYPT);
		// A sealed alcove behind cracked brick.
		p.fill(-2, -7, -4, 2, -5, -4, block(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS));
		p.clear(-1, -7, -6, 1, -5, -5);
		p.mixed(-2, -8, -7, 2, -4, -7, BLACK, BLACK_W);
		SiteBuilder.chest(p, 0, -7, -6, Direction.SOUTH, LOOT_CRYPT);
		p.set(0, -5, -6, SiteBuilder.hangingLantern(true));
	}

	private static void grave(Plan p, int x, int z) {
		float f = p.random.nextFloat();
		BlockState stone = f < 0.4F ? block(Blocks.STONE_BRICK_WALL) : f < 0.7F ? block(Blocks.MOSSY_STONE_BRICK_WALL) : block(Blocks.CHISELED_STONE_BRICKS);
		p.set(x, 1, z, stone);
		if (p.random.nextFloat() < 0.35F) {
			p.set(x, 2, z, litCandles(1 + p.random.nextInt(2)));
		} else if (p.random.nextFloat() < 0.15F) {
			p.set(x, 2, z, block(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, p.random.nextInt(16)));
		}
		p.set(x, 0, z + 1, block(Blocks.PODZOL));
		p.set(x, 0, z + 2, block(Blocks.COARSE_DIRT));
	}

	private static void deadTree(Plan p, int x, int z) {
		BlockState log = block(Blocks.PALE_OAK_LOG);
		int h = 5 + p.random.nextInt(3);
		p.fill(x, 1, z, x, h, z, log);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (p.random.nextBoolean()) {
				int len = 2 + p.random.nextInt(2);
				for (int i = 1; i <= len; i++) {
					p.set(x + d.getStepX() * i, h - 1 + i / 2, z + d.getStepZ() * i, log.setValue(RotatedPillarBlock.AXIS, d.getAxis()));
				}
			}
		}
	}

	// ------------------------------------------------------------------ the Bureau Containment Facility

	static void containment(ServerLevel level, Plan p) {
		SiteBuilder.ground(level, p, -19, 19, -19, 19, 14, block(Blocks.SMOOTH_STONE));
		BlockState copper = block(Blocks.CUT_COPPER.waxed().unaffected());
		BlockState wall = block(Blocks.SMOOTH_STONE);
		p.mixed(-17, 0, -17, 17, 0, 17, new BlockState[] {block(Blocks.GRAVEL), block(Blocks.ANDESITE), block(Blocks.POLISHED_ANDESITE)}, new float[] {2, 2, 1});
		// Perimeter fence.
		for (int i = -18; i <= 18; i++) {
			for (int[] c : new int[][] {{i, -18}, {i, 18}, {-18, i}, {18, i}}) {
				if (c[1] == -18 && Math.abs(c[0]) <= 2) {
					continue;
				}
				p.set(c[0], 1, c[1], wall);
				p.set(c[0], 2, c[1], block(Blocks.IRON_BARS));
				p.set(c[0], 3, c[1], block(Blocks.IRON_BARS));
			}
		}
		for (int[] c : new int[][] {{-3, -18}, {3, -18}, {-18, -18}, {18, -18}, {-18, 18}, {18, 18}}) {
			p.fill(c[0], 1, c[1], c[0], 4, c[1], block(Blocks.POLISHED_ANDESITE));
			p.set(c[0], 5, c[1], block(Blocks.COPPER_LANTERN.waxed().exposed()));
		}
		// The hall.
		int x1 = -12;
		int x2 = 12;
		int z1 = -8;
		int z2 = 16;
		p.fill(x1, 1, z1, x2, 8, z2, wall);
		p.clear(x1 + 1, 1, z1 + 1, x2 - 1, 7, z2 - 1);
		p.fill(x1, 4, z1, x2, 4, z2, copper);
		p.fill(x1, 8, z1, x2, 8, z2, copper);
		p.clear(x1 + 1, 4, z1 + 1, x2 - 1, 4, z2 - 1);
		for (int[] c : new int[][] {{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
			p.fill(c[0], 1, c[1], c[0], 9, c[1], block(Blocks.POLISHED_ANDESITE));
		}
		p.fill(x1 + 1, 9, z1 + 1, x2 - 1, 9, z2 - 1, wall);
		for (int x = x1 + 3; x <= x2 - 3; x += 6) {
			for (int z = z1 + 3; z <= z2 - 3; z += 6) {
				p.fill(x, 9, z, x + 1, 9, z + 1, block(Blocks.GLASS));
				p.set(x + 3, 10, z, block(Blocks.COPPER_GRATE.waxed().exposed()));
			}
		}
		for (int x = x1 + 2; x <= x2 - 2; x += 3) {
			p.set(x, 6, z1, block(Blocks.TINTED_GLASS));
			p.set(x, 6, z2, block(Blocks.TINTED_GLASS));
		}
		p.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, block(Blocks.POLISHED_ANDESITE));
		p.fill(-1, 0, z1, 1, 0, z2 - 6, copper);
		p.clear(-1, 1, z1, 1, 3, z1);
		p.set(0, 7, z1 - 1, block(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true));
		p.set(0, 7, z1, block(Blocks.REDSTONE_BLOCK));
		// Outside: an antenna mast with a warning light, a water tank, pipes, a booth and a loading dock.
		BlockState rod = block(Blocks.LIGHTNING_ROD.waxed().unaffected());
		p.set(8, 10, 12, block(Blocks.POLISHED_ANDESITE));
		p.fill(8, 11, 12, 8, 16, 12, block(Blocks.IRON_BARS));
		p.set(7, 14, 12, block(Blocks.IRON_BARS));
		p.set(9, 14, 12, block(Blocks.IRON_BARS));
		p.set(8, 17, 12, block(Blocks.REDSTONE_BLOCK));
		p.set(8, 18, 12, block(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true));
		p.set(8, 19, 12, rod);
		for (int[] c : new int[][] {{x1 + 1, z1 + 1}, {x2 - 1, z1 + 1}, {x1 + 1, z2 - 1}, {x2 - 1, z2 - 1}}) {
			p.set(c[0], 10, c[1], block(Blocks.REDSTONE_BLOCK));
			p.set(c[0], 11, c[1], block(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true));
		}
		for (int[] c : new int[][] {{-9, 8}, {-5, 8}, {-9, 12}, {-5, 12}}) {
			p.fill(c[0], 10, c[1], c[0], 11, c[1], block(Blocks.IRON_BARS));
		}
		roundWall(p, -7, 10, 2.2, 12, 15, new BlockState[] {block(Blocks.COPPER_BLOCK.waxed().exposed()), block(Blocks.CUT_COPPER.waxed().exposed())}, new float[] {2, 1});
		disc(p, -7, 10, 2.2, 12, block(Blocks.COPPER_BLOCK.waxed().exposed()));
		disc(p, -7, 10, 2.2, 16, block(Blocks.CUT_COPPER.waxed().weathered()));
		for (int z = z1 + 3; z <= z2 - 3; z += 6) {
			p.fill(x1 - 1, 1, z, x1 - 1, 9, z, rod);
			p.fill(x2 + 1, 1, z, x2 + 1, 9, z, rod);
		}
		p.fill(5, 1, -16, 7, 3, -14, wall);
		p.clear(6, 1, -15, 6, 2, -15);
		p.set(6, 2, -16, block(Blocks.GLASS_PANE));
		p.set(5, 2, -15, block(Blocks.GLASS_PANE));
		p.set(7, 2, -15, block(Blocks.GLASS_PANE));
		p.fill(4, 4, -17, 8, 4, -13, SiteBuilder.slab(block(Blocks.SMOOTH_STONE_SLAB), false));
		p.set(6, 3, -15, SiteBuilder.hangingLantern(false));
		p.clear(6, 1, -14, 6, 2, -14);
		for (int z = 0; z <= 8; z += 2) {
			p.set(14, 1, z, block(Blocks.BARREL));
			if (p.random.nextBoolean()) {
				p.set(14, 2, z, block(Blocks.BARREL));
			}
			p.set(15, 1, z + 1, p.random.nextBoolean() ? block(Blocks.BARREL) : block(Blocks.COPPER_GRATE.waxed().unaffected()));
		}
		// Containment cells, three a side, glass fronts on the corridor.
		int[][] cells = {{-1, -7}, {-1, -1}, {-1, 5}, {1, -7}, {1, -1}, {1, 5}};
		for (int i = 0; i < cells.length; i++) {
			int side = cells[i][0];
			int cz1 = cells[i][1];
			int cz2 = cz1 + 4;
			int ix1 = side < 0 ? -11 : 3;
			int ix2 = side < 0 ? -3 : 11;
			int front = side < 0 ? -2 : 2;
			p.fill(ix1, 1, cz1 - 1, ix2, 7, cz1 - 1, wall);
			p.fill(ix1, 1, cz2 + 1, ix2, 7, cz2 + 1, wall);
			p.fill(front, 1, cz1, front, 4, cz2, block(Blocks.GLASS));
			p.fill(front, 5, cz1, front, 7, cz2, wall);
			p.set(front + (side < 0 ? 1 : -1), 6, cz1 + 2, block(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true));
			p.set(front, 6, cz1 + 2, block(Blocks.REDSTONE_BLOCK));
			cell(p, i, ix1, cz1, ix2, cz2, front, side);
		}
		// The records office at the back.
		p.fill(x1 + 1, 1, 11, x2 - 1, 1, 11, SiteBuilder.slab(block(Blocks.SMOOTH_STONE_SLAB), true));
		for (int x = x1 + 1; x <= x2 - 1; x++) {
			p.set(x, 1, z2 - 1, block(Blocks.BARREL));
			p.set(x, 2, z2 - 1, block(Blocks.BARREL));
		}
		barrel(p, -6, 3, z2 - 1, LOOT_CONTAINMENT);
		barrel(p, 6, 3, z2 - 1, LOOT_CONTAINMENT);
		p.set(-4, 1, 13, facing(block(Blocks.LECTERN), Direction.NORTH));
		p.set(4, 1, 13, block(Blocks.CARTOGRAPHY_TABLE));
		p.set(0, 1, 13, block(Blocks.CRAFTER));
		for (int x = -8; x <= 8; x += 4) {
			p.set(x, 7, 13, block(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true));
			p.set(x, 8, 13, block(Blocks.REDSTONE_BLOCK));
		}
		spawn(p, AwEntities.CLOCKWORK_SENTRY, -6, 1, -12, 180);
		spawn(p, AwEntities.CLOCKWORK_SENTRY, 6, 1, -12, 180);
	}

	/** Each cell holds a different contained rule. */
	private static void cell(Plan p, int index, int x1, int z1, int x2, int z2, int front, int side) {
		int cx = (x1 + x2) / 2;
		int cz = (z1 + z2) / 2;
		switch (index) {
			case 0 -> {
				// Weightless: things hang where they were dropped.
				p.set(cx, 3, cz, block(Blocks.ANVIL));
				p.set(cx - 2, 2, cz - 1, block(Blocks.COBBLESTONE));
				p.set(cx + 2, 4, cz + 1, block(Blocks.LANTERN));
				p.set(cx - 1, 5, cz + 2, block(Blocks.BARREL));
				p.set(cx + 1, 2, cz - 2, facing(block(Blocks.END_ROD), Direction.UP));
			}
			case 1 -> {
				// Stillness: a splash frozen mid-air.
				p.fill(cx, 1, cz, cx, 5, cz, block(Blocks.BLUE_ICE));
				for (Direction d : Direction.Plane.HORIZONTAL) {
					p.set(cx + d.getStepX(), 5, cz + d.getStepZ(), block(Blocks.PACKED_ICE));
					p.set(cx + d.getStepX() * 2, 4, cz + d.getStepZ() * 2, block(Blocks.ICE));
				}
				p.fill(x1 + 1, 1, z1, x2 - 1, 1, z2, block(Blocks.SNOW));
			}
			case 2 -> {
				// Witness: everything faces the middle, and something looks back.
				for (int z = z1; z <= z2; z++) {
					p.set(x1, 3, z, facing(block(Blocks.OBSERVER), Direction.WEST));
					p.set(x2, 3, z, facing(block(Blocks.OBSERVER), Direction.EAST));
				}
				p.set(cx, 1, cz, block(Blocks.END_PORTAL_FRAME).setValue(EndPortalFrameBlock.HAS_EYE, true).setValue(EndPortalFrameBlock.FACING, side < 0 ? Direction.EAST : Direction.WEST));
			}
			case 3 -> {
				// Hunger: bones, and nothing else.
				p.ruin(x1 + 1, 1, z1, x2 - 1, 2, z2, block(Blocks.BONE_BLOCK), 0.35F);
				p.set(cx, 3, cz, block(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, 4));
				p.ruin(x1 + 1, 4, z1, x2 - 1, 6, z2, block(Blocks.COBWEB), 0.2F);
			}
			case 4 -> {
				// Echoes: the same room twice.
				for (int k = -1; k <= 1; k += 2) {
					p.set(cx + k * 2, 1, cz - 1, block(Blocks.BOOKSHELF));
					p.set(cx + k * 2, 2, cz - 1, block(Blocks.LANTERN));
					p.set(cx + k * 2, 1, cz + 1, facing(block(Blocks.LECTERN), Direction.NORTH));
				}
			}
			default -> {
				// The breach: glass broken, the core still pulsing, scorched floor.
				p.ruin(front, 1, z1, front, 4, z2, AIR, 0.6F);
				p.fill(x1 + 1, 0, z1, x2 - 1, 0, z2, block(Blocks.BLACKSTONE));
				p.set(cx, 1, cz, block(Blocks.SMOOTH_STONE));
				p.set(cx, 2, cz, AwBlocks.ANOMALY_CORE.defaultBlockState());
				p.set(cx, 6, cz, block(Blocks.REDSTONE_LAMP).setValue(RedstoneLampBlock.LIT, true));
				p.set(cx, 7, cz, block(Blocks.REDSTONE_BLOCK));
			}
		}
	}

	// ------------------------------------------------------------------ the Archive of Almost

	static void archive(ServerLevel level, Plan p) {
		SiteBuilder.ground(level, p, -15, 15, -15, 15, 46, block(Blocks.COBBLED_DEEPSLATE));
		// The ruined round hall, open to the sky.
		roundWall(p, 0, 0, 10, 1, 8, SLATE, SLATE_W);
		roundWall(p, 0, 0, 9, 1, 6, new BlockState[] {block(Blocks.CHISELED_BOOKSHELF), block(Blocks.BOOKSHELF)}, new float[] {1, 2});
		disc(p, 0, 0, 9.5, 0, block(Blocks.POLISHED_DEEPSLATE));
		for (int i = 0; i < 60; i++) {
			double a = i * 0.21;
			double r = 1.5 + i * 0.13;
			p.set((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r), block(Blocks.LAPIS_BLOCK));
		}
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24;
			int x = (int) Math.round(Math.cos(a) * 10);
			int z = (int) Math.round(Math.sin(a) * 10);
			int h = 8 + p.random.nextInt(5) - (i % 5 == 0 ? 6 : 0);
			for (int y = 9; y <= h; y++) {
				p.set(x, y, z, p.pick(SLATE, SLATE_W));
			}
		}
		p.clear(-1, 1, -10, 1, 4, -9);
		// Inner rings of shelves with gaps, and the dais.
		for (int i = 0; i < 36; i++) {
			double a = i * Math.PI * 2 / 36;
			if (i % 9 < 2) {
				continue;
			}
			int x = (int) Math.round(Math.cos(a) * 6.5);
			int z = (int) Math.round(Math.sin(a) * 6.5);
			p.fill(x, 1, z, x, 3, z, block(Blocks.BOOKSHELF));
		}
		disc(p, 0, 0, 2.5, 1, block(Blocks.POLISHED_DEEPSLATE));
		p.set(0, 2, 0, block(Blocks.ENCHANTING_TABLE));
		for (Direction d : Direction.Plane.HORIZONTAL) {
			p.set(d.getStepX() * 2, 2, d.getStepZ() * 2, litCandles(3));
		}
		// A floating stair spiralling up to the first fragment.
		double a0 = Math.PI * 0.25;
		for (int i = 0; i < 12; i++) {
			double a = a0 + i * 0.42;
			int x = (int) Math.round(Math.cos(a) * 4.0);
			int z = (int) Math.round(Math.sin(a) * 4.0);
			p.set(x, 2 + i, z, SiteBuilder.slab(block(Blocks.POLISHED_DEEPSLATE_SLAB), true));
		}
		// Fragments of the upper archive, drifting apart.
		int[][] frags = {{4, 14, -2, 4}, {-4, 19, 4, 3}, {3, 24, 6, 3}, {-5, 29, -1, 3}, {0, 35, 0, 4}};
		int[] lastTop = {(int) Math.round(Math.cos(a0 + 11 * 0.42) * 4.0), 13, (int) Math.round(Math.sin(a0 + 11 * 0.42) * 4.0)};
		for (int i = 0; i < frags.length; i++) {
			int[] f = frags[i];
			fragment(p, f[0], f[1], f[2], f[3], i == frags.length - 1);
			steps(p, lastTop[0], lastTop[1], lastTop[2], f[0], f[1], f[2], f[3]);
			lastTop = new int[] {f[0], f[1], f[2]};
		}
		p.set(0, 36, -1, facing(block(Blocks.LECTERN), Direction.SOUTH));
		SiteBuilder.chest(p, 1, 36, 1, Direction.WEST, LOOT_ALMOST);
		SiteBuilder.chest(p, 7, 2, 2, Direction.WEST, LOOT_ALMOST);
		for (int i = 0; i < 3; i++) {
			p.set(-1 + i, 36, 2, litCandles(2 + i % 2));
		}
		p.fill(0, 37, 3, 0, 38, 3, block(Blocks.END_ROD));
	}

	/** A disc of floor torn loose, with a jagged underside, a wall of shelves and a light. */
	private static void fragment(Plan p, int cx, int y, int cz, int r, boolean top) {
		for (int dx = -r - 1; dx <= r + 1; dx++) {
			for (int dz = -r - 1; dz <= r + 1; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r + 0.3) {
					continue;
				}
				p.set(cx + dx, y, cz + dz, block(Blocks.POLISHED_DEEPSLATE));
				int depth = (int) Math.round((r - d) * 0.9) + p.random.nextInt(2);
				for (int k = 1; k <= depth; k++) {
					p.set(cx + dx, y - k, cz + dz, p.pick(SLATE, SLATE_W));
				}
				if (d > r - 0.8 && dz > 0 && !top) {
					p.fill(cx + dx, y + 1, cz + dz, cx + dx, y + 2 + p.random.nextInt(2), cz + dz, block(Blocks.BOOKSHELF));
				}
			}
		}
		p.set(cx, y - (int) Math.round(r * 0.9) - 1, cz, block(Blocks.AMETHYST_CLUSTER).setValue(AmethystClusterBlock.FACING, Direction.DOWN));
		p.set(cx + 1, y + 1, cz - 1, block(Blocks.SOUL_LANTERN));
		p.set(cx - 1, y - 1, cz - r, SiteBuilder.hangingLantern(true));
	}

	/** Single floating steps between two fragments: never more than one up or two across. */
	private static void steps(Plan p, int x1, int y1, int z1, int x2, int y2, int z2, int r) {
		double dx = x2 - x1;
		double dz = z2 - z1;
		double len = Math.sqrt(dx * dx + dz * dz);
		double ex = x2 - (len > 0 ? dx / len * (r + 0.5) : 0);
		double ez = z2 - (len > 0 ? dz / len * (r + 0.5) : 0);
		int rise = y2 - y1;
		double run = Math.hypot(ex - x1, ez - z1);
		int n = Math.max(rise, (int) Math.ceil(run / 1.6));
		for (int i = 1; i < n; i++) {
			double t = i / (double) n;
			int x = (int) Math.round(x1 + (ex - x1) * t);
			int z = (int) Math.round(z1 + (ez - z1) * t);
			int y = y1 + (int) Math.round(rise * t);
			p.set(x, y, z, i % 3 == 0 ? block(Blocks.CHISELED_BOOKSHELF) : SiteBuilder.slab(block(Blocks.POLISHED_DEEPSLATE_SLAB), true));
		}
	}

	static BlockState lantern() {
		return block(Blocks.LANTERN).setValue(LanternBlock.HANGING, false);
	}

	static BlockState lit(BlockState campfire) {
		return campfire.setValue(CampfireBlock.LIT, true);
	}

	static BlockState lectern(Direction d) {
		return block(Blocks.LECTERN).setValue(LecternBlock.FACING, d);
	}
}

package dev.afterworld.world.wonder;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.afterworld.Afterworld;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * A wonder's blocks as built offline by tools/wonders: a palette, then one run-length-encoded tile
 * per 16x16 column. The file stays compressed in memory (tens of kilobytes) and each tile is decoded
 * only while the chunk it lands in is generated, so a whole city costs about a chunk's worth of
 * work per chunk, and nothing lingers afterwards.
 */
public final class WonderData {
	private static final Map<String, WonderData> CACHE = new ConcurrentHashMap<>();
	private static final WonderData MISSING = new WonderData();

	public final int sx;
	public final int sy;
	public final int sz;
	private final String[] palette;
	private final int tilesX;
	private final byte[][] tiles;
	private final short[] lootPos;
	private final String[] lootTables;
	private final BlockState[][] rotated = new BlockState[4][];

	private WonderData() {
		this.sx = this.sy = this.sz = 0;
		this.palette = new String[0];
		this.tilesX = 0;
		this.tiles = new byte[0][];
		this.lootPos = new short[0];
		this.lootTables = new String[0];
	}

	private WonderData(DataInputStream in) throws IOException {
		if (in.readInt() != 0x41575731) {
			throw new IOException("not a wonder file");
		}
		this.sx = in.readInt();
		this.sy = in.readInt();
		this.sz = in.readInt();
		this.palette = new String[in.readInt()];
		for (int i = 0; i < this.palette.length; i++) {
			this.palette[i] = in.readUTF();
		}
		this.tilesX = in.readInt();
		int tilesZ = in.readInt();
		this.tiles = new byte[this.tilesX * tilesZ][];
		for (int i = 0; i < this.tiles.length; i++) {
			int len = in.readInt();
			this.tiles[i] = len == 0 ? null : in.readNBytes(len);
		}
		int loot = in.readInt();
		this.lootPos = new short[loot * 3];
		this.lootTables = new String[loot];
		for (int i = 0; i < loot; i++) {
			this.lootPos[i * 3] = in.readShort();
			this.lootPos[i * 3 + 1] = in.readShort();
			this.lootPos[i * 3 + 2] = in.readShort();
			this.lootTables[i] = in.readUTF();
		}
	}

	/** The named wonder, loaded once from the mod's resources; null if it is missing or broken. */
	public static @Nullable WonderData get(String name) {
		WonderData d = CACHE.computeIfAbsent(name, WonderData::load);
		return d == MISSING ? null : d;
	}

	private static WonderData load(String name) {
		try (InputStream raw = WonderData.class.getResourceAsStream("/data/afterworld/wonder/" + name + ".aww")) {
			if (raw == null) {
				throw new FileNotFoundException(name);
			}
			WonderData d = new WonderData(new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw), 1 << 16)));
			// Resolved up front: worldgen threads then only ever read finished arrays.
			for (Rotation r : Rotation.values()) {
				d.rotated[r.ordinal()] = d.resolve(r);
			}
			return d;
		} catch (IOException e) {
			Afterworld.LOG.error("Could not load wonder {}", name, e);
			return MISSING;
		}
	}

	/** How many palette entries failed to parse (a renamed block, say). */
	public int unresolved() {
		int n = 0;
		for (int i = 1; i < this.palette.length; i++) {
			if (this.rotated[0][i] == null) {
				n++;
			}
		}
		return n;
	}

	public int width(Rotation r) {
		return turned(r) ? this.sz : this.sx;
	}

	public int depth(Rotation r) {
		return turned(r) ? this.sx : this.sz;
	}

	private static boolean turned(Rotation r) {
		return r == Rotation.CLOCKWISE_90 || r == Rotation.COUNTERCLOCKWISE_90;
	}

	private BlockState[] resolve(Rotation r) {
		BlockState[] s = new BlockState[this.palette.length];
		for (int i = 1; i < this.palette.length; i++) {
			try {
				s[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, this.palette[i], false).blockState().rotate(r);
			} catch (CommandSyntaxException e) {
				Afterworld.LOG.warn("Wonder block {} not understood: {}", this.palette[i], e.getMessage());
			}
		}
		return s;
	}

	/** Where an unrotated column (ux, uz) lands once the wonder is turned: {x, z}. */
	private int rx(Rotation r, int ux, int uz) {
		return switch (r) {
			case NONE -> ux;
			case CLOCKWISE_90 -> this.sz - 1 - uz;
			case CLOCKWISE_180 -> this.sx - 1 - ux;
			case COUNTERCLOCKWISE_90 -> uz;
		};
	}

	private int rz(Rotation r, int ux, int uz) {
		return switch (r) {
			case NONE -> uz;
			case CLOCKWISE_90 -> ux;
			case CLOCKWISE_180 -> this.sz - 1 - uz;
			case COUNTERCLOCKWISE_90 -> this.sx - 1 - ux;
		};
	}

	/** The unrotated column that lands on rotated column (x, z): {ux, uz}. */
	private int ux(Rotation r, int x, int z) {
		return switch (r) {
			case NONE -> x;
			case CLOCKWISE_90 -> z;
			case CLOCKWISE_180 -> this.sx - 1 - x;
			case COUNTERCLOCKWISE_90 -> this.sx - 1 - z;
		};
	}

	private int uz(Rotation r, int x, int z) {
		return switch (r) {
			case NONE -> z;
			case CLOCKWISE_90 -> this.sz - 1 - x;
			case CLOCKWISE_180 -> this.sz - 1 - z;
			case COUNTERCLOCKWISE_90 -> x;
		};
	}

	/** Places whatever part of the wonder falls inside {@code chunkBox} (one chunk column). */
	public void place(WorldGenLevel level, BlockPos origin, Rotation r, BoundingBox chunkBox, RandomSource random) {
		int lx = chunkBox.minX() - origin.getX();
		int lz = chunkBox.minZ() - origin.getZ();
		if (lx < 0 || lz < 0 || lx >= width(r) || lz >= depth(r)) {
			return;
		}
		// The origin is chunk-aligned and sizes are multiples of 16, so exactly one tile lands here.
		int tx = Math.min(ux(r, lx, lz), ux(r, lx + 15, lz + 15)) >> 4;
		int tz = Math.min(uz(r, lx, lz), uz(r, lx + 15, lz + 15)) >> 4;
		byte[] data = this.tiles[tz * this.tilesX + tx];
		if (data == null) {
			return;
		}
		BlockState[] st = this.rotated[r.ordinal()];
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int[] cur = {0};
		int cell = 0;
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		while (cur[0] < data.length) {
			int count = varint(data, cur);
			int id = varint(data, cur);
			BlockState state = id == 0 ? null : st[id];
			if (state != null) {
				for (int c = cell; c < cell + count; c++) {
					int y = origin.getY() + (c >> 8);
					if (y < minY || y > maxY) {
						continue;
					}
					int ux = (tx << 4) + (c & 15);
					int uz = (tz << 4) + ((c >> 4) & 15);
					p.set(origin.getX() + rx(r, ux, uz), y, origin.getZ() + rz(r, ux, uz));
					level.setBlock(p, state, 2);
				}
			}
			cell += count;
		}
		for (int i = 0; i < this.lootTables.length; i++) {
			int ux = this.lootPos[i * 3];
			int uz = this.lootPos[i * 3 + 2];
			if (ux >> 4 == tx && uz >> 4 == tz) {
				p.set(origin.getX() + rx(r, ux, uz), origin.getY() + this.lootPos[i * 3 + 1], origin.getZ() + rz(r, ux, uz));
				if (this.lootTables[i].startsWith("@")) {
					// A marker, not a chest: where a keeper stands or a waystone waits.
					dev.afterworld.npc.Npcs.marker(level, p.immutable(), this.lootTables[i].substring(1), r);
					continue;
				}
				RandomizableContainer.setBlockEntityLootTable(level, random, p.immutable(),
					ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(this.lootTables[i])));
			}
		}
	}

	/** Every marker in the wonder ({@code @name} entries), as world positions once placed at {@code origin}. */
	public java.util.List<java.util.Map.Entry<BlockPos, String>> markers(BlockPos origin, Rotation r) {
		java.util.List<java.util.Map.Entry<BlockPos, String>> out = new java.util.ArrayList<>();
		for (int i = 0; i < this.lootTables.length; i++) {
			if (this.lootTables[i].startsWith("@")) {
				int ux = this.lootPos[i * 3];
				int uz = this.lootPos[i * 3 + 2];
				out.add(java.util.Map.entry(new BlockPos(origin.getX() + rx(r, ux, uz), origin.getY() + this.lootPos[i * 3 + 1], origin.getZ() + rz(r, ux, uz)),
					this.lootTables[i].substring(1)));
			}
		}
		return out;
	}

	/**
	 * Counts blocks in the world that differ (by block, ignoring properties) from what this wonder
	 * placed there, skipping cells meant to be air. For tests: {total, missing}.
	 */
	public int[] verify(net.minecraft.world.level.Level level, BlockPos origin, Rotation r) {
		int total = 0;
		int missing = 0;
		java.util.Map<String, Integer> what = new java.util.HashMap<>();
		BlockState[] st = this.rotated[r.ordinal()];
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int tz = 0; tz < this.tiles.length / this.tilesX; tz++) {
			for (int tx = 0; tx < this.tilesX; tx++) {
				byte[] data = this.tiles[tz * this.tilesX + tx];
				if (data == null) {
					continue;
				}
				int[] cur = {0};
				int cell = 0;
				while (cur[0] < data.length) {
					int count = varint(data, cur);
					int id = varint(data, cur);
					BlockState state = id == 0 ? null : st[id];
					if (state != null && !state.isAir()) {
						for (int c = cell; c < cell + count; c++) {
							int ux = (tx << 4) + (c & 15);
							int uz = (tz << 4) + ((c >> 4) & 15);
							p.set(origin.getX() + rx(r, ux, uz), origin.getY() + (c >> 8), origin.getZ() + rz(r, ux, uz));
							total++;
							BlockState there = level.getBlockState(p);
							if (!there.is(state.getBlock())) {
								missing++;
								what.merge(state.getBlock().builtInRegistryHolder().key().identifier().getPath() + "->"
									+ there.getBlock().builtInRegistryHolder().key().identifier().getPath(), 1, Integer::sum);
							}
						}
					}
					cell += count;
				}
			}
		}
		if (missing > 0) {
			StringBuilder sb = new StringBuilder();
			what.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6)
				.forEach(e -> sb.append(e.getKey()).append(" x").append(e.getValue()).append("; "));
			dev.afterworld.Afterworld.LOG.info("WONDERVERIFY differences: {}", sb);
		}
		return new int[] {total, missing};
	}

	private static int varint(byte[] b, int[] cur) {
		int v = 0;
		int shift = 0;
		while (true) {
			int x = b[cur[0]++] & 0xFF;
			v |= (x & 0x7F) << shift;
			if ((x & 0x80) == 0) {
				return v;
			}
			shift += 7;
		}
	}
}

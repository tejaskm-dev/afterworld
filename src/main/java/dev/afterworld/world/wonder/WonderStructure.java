package dev.afterworld.world.wonder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * One of Afterworld's wonders: a single large build from {@link WonderData}, turned at random,
 * centred on its start chunk and set down on reasonably level, dry ground. {@code sink} layers of
 * foundation go below the ground line; the terrain is levelled to that line around it.
 */
public final class WonderStructure extends Structure {
	public static final MapCodec<WonderStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		settingsCodec(i),
		Codec.STRING.fieldOf("wonder").forGetter(s -> s.wonder),
		Codec.INT.optionalFieldOf("sink", 0).forGetter(s -> s.sink),
		Codec.INT.optionalFieldOf("max_relief", 20).forGetter(s -> s.maxRelief),
		Codec.BOOL.optionalFieldOf("shore", false).forGetter(s -> s.shore),
		Codec.BOOL.optionalFieldOf("atlas", false).forGetter(s -> s.atlas)
	).apply(i, WonderStructure::new));

	public final String wonder;
	private final int sink;
	private final int maxRelief;
	/** Shore builds stand with their feet in the sea: some water underfoot is fine, and so is sea level. */
	private final boolean shore;
	/** Placed by the Atlas, once per world: it always stands, on the ground the Atlas chose. */
	private final boolean atlas;
	/** How far round an Atlas site the trees are felled and the ground levelled. */
	private static final int CLEARING = 10;

	public WonderStructure(Structure.StructureSettings settings, String wonder, int sink, int maxRelief, boolean shore, boolean atlas) {
		super(settings);
		this.wonder = wonder;
		this.sink = sink;
		this.maxRelief = maxRelief;
		this.shore = shore;
		this.atlas = atlas;
	}

	@Override
	protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
		WonderData data = WonderData.get(this.wonder);
		if (data == null) {
			return Optional.empty();
		}
		Rotation rotation = Rotation.getRandom(context.random());
		int w = data.width(rotation);
		int d = data.depth(rotation);
		ChunkPos chunk = context.chunkPos();
		// Centred on the start chunk (references only reach eight chunks), and still chunk-aligned.
		int x0 = chunk.getMinBlockX() - (w / 32) * 16;
		int z0 = chunk.getMinBlockZ() - (d / 32) * 16;
		ChunkGenerator gen = context.chunkGenerator();
		int[] heights = new int[9];
		int k = 0;
		int wet = 0;
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				int x = x0 + 4 + (w - 9) * i / 2;
				int z = z0 + 4 + (d - 9) * j / 2;
				int surface = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
				int floor = gen.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
				if (surface - floor > 2 && !this.atlas) {
					// Standing in water: only a shore build may, and not too deep or too much of it.
					if (!this.shore || surface - floor > 6 || ++wet > 5) {
						return Optional.empty();
					}
				}
				heights[k++] = surface;
			}
		}
		Arrays.sort(heights);
		// Ignore the single highest and lowest sample: a tree or a gully shouldn't veto a site.
		if (!this.atlas && (heights[7] - heights[1] > this.maxRelief || !this.shore && heights[4] <= gen.getSeaLevel() + 1)) {
			return Optional.empty();
		}
		int ground = this.shore || this.atlas ? Math.max(heights[4], gen.getSeaLevel() + 1) : heights[4];
		BlockPos origin = new BlockPos(x0, ground - this.sink, z0);
		// The Atlas's lairs stand in a clearing of their own: the box (and so the levelled ground) reaches further.
		int clear = this.atlas ? CLEARING : 0;
		BoundingBox box = new BoundingBox(x0 - clear, ground, z0 - clear, x0 + w - 1 + clear, ground - this.sink + data.sy - 1, z0 + d - 1 + clear);
		return Optional.of(new Structure.GenerationStub(new BlockPos(x0 + w / 2, ground, z0 + d / 2),
			builder -> builder.addPiece(new WonderPiece(this.wonder, origin, rotation, box, clear))));
	}

	@Override
	public StructureType<?> type() {
		return Wonders.TYPE;
	}
}

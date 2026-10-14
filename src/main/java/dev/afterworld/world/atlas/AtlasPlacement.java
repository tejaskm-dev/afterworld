package dev.afterworld.world.atlas;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

/**
 * Places a structure exactly once in a world, where the {@link Atlas} says ({@code "site"} is the
 * structure's name). It is a random spread with one cell the size of the world, so the game's own
 * locating (the {@code /locate} command, maps, compasses) finds it like any other structure.
 */
public final class AtlasPlacement extends RandomSpreadStructurePlacement {
	public static final MapCodec<AtlasPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> placementCodec(i)
		.and(Codec.STRING.fieldOf("site").forGetter(p -> p.site))
		.apply(i, AtlasPlacement::new));
	/** Where a site with no place in this world points: far beyond anywhere anyone walks. */
	private static final ChunkPos NOWHERE = new ChunkPos(1_800_000, 1_800_000);

	private final String site;

	private AtlasPlacement(Vec3i locateOffset, AbstractSpreadingStructurePlacement.FrequencyReductionMethod method, float frequency, int salt,
		Optional<AbstractSpreadingStructurePlacement.ExclusionZone> exclusion, String site) {
		super(locateOffset, method, frequency, salt, exclusion, 4096, 1, RandomSpreadType.LINEAR);
		this.site = site;
	}

	@Override
	public ChunkPos getPotentialStructureChunk(long seed, int sourceX, int sourceZ) {
		ChunkPos at = Atlas.chunk(this.site);
		return at != null ? at : NOWHERE;
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		ChunkPos at = Atlas.chunk(this.site);
		return at != null && at.x() == sourceX && at.z() == sourceZ;
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public MapCodec<RandomSpreadStructurePlacement> codec() {
		// The registered codec is ours; the cast only satisfies the parent's signature.
		return (MapCodec) CODEC;
	}
}

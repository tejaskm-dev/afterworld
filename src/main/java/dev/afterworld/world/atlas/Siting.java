package dev.afterworld.world.atlas;

import dev.afterworld.mixin.SpreadPlacementAccessor;
import dev.afterworld.mixin.StructureStateAccessor;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Keeps the world's buildings out of each other's way. Every structure set, ours and the game's,
 * has a rank by size; a structure may not start where a bigger one could start within reach of
 * it, and nothing starts inside a reserved place (the waking town, the Wardens' lairs, the great
 * powers' seats). Ranks, biggest first: the Atlas's own sites; our wonders (each ahead of the
 * next); villages, mansions, outposts and our landmarks; then every smaller thing.
 *
 * <p>Like the game's own exclusion zones this goes by where a bigger structure <em>could</em>
 * start, so it errs on the side of a little more open country, never of overlap.
 */
public final class Siting {
	/** A set's rank (lower is bigger, and wins) and how far its buildings reach from their start, in chunks. */
	record Rule(int rank, int reach) {
	}

	/** A place nothing else may be built on: its centre chunk and radius in chunks. */
	record Reserved(int x, int z, int radius) {
	}

	/** The game's smaller surface structures (temples, igloos, ruined portals, camps, ruins). Our sites make way for them. */
	private static final Rule SMALL = new Rule(29, 2);
	/** Things deep underground or at sea level's floor, which never meet a building on the surface. */
	private static final Rule BELOW = new Rule(Integer.MAX_VALUE, 0);
	/** For comparing against the world without these rules (-Dafterworld.siting.off=true). */
	private static final boolean OFF = Boolean.getBoolean("afterworld.siting.off");
	private static final List<Reserved> RESERVED = new CopyOnWriteArrayList<>();

	private Siting() {
	}

	/** The rule for a placement, known by its salt (each of our sets has its own; the game's are fixed). */
	static Rule rule(int salt) {
		return switch (salt) {
			// Our wonders, by footprint (half the widest side, in chunks): the cathedral and citadel are the largest.
			case 7102 -> new Rule(10, 5);
			case 7103 -> new Rule(11, 5);
			case 7104 -> new Rule(12, 4);
			case 7101 -> new Rule(13, 4);
			case 7105 -> new Rule(14, 3);
			// Villages (their streets run up to 80 blocks out, and the start is rarely the middle), woodland mansions, pillager outposts.
			case 10387312 -> new Rule(20, 7);
			case 10387319 -> new Rule(21, 4);
			case 165745296 -> new Rule(22, 2);
			// Our landmarks (up to 64 across), then our sites (48).
			case 7201 -> new Rule(25, 2);
			case 7301 -> new Rule(30, 2);
			// Mineshafts and buried treasure (salt 0), trial chambers, ancient cities.
			case 0, 94251327, 20083232 -> BELOW;
			default -> SMALL;
		};
	}

	public static void reserve(int chunkX, int chunkZ, int radius) {
		RESERVED.add(new Reserved(chunkX, chunkZ, radius));
	}

	public static void clearReserved() {
		RESERVED.clear();
	}

	public static List<Reserved> reserved() {
		return List.copyOf(RESERVED);
	}

	/** Whether a structure this placement would start at chunk (x, z) may stand there. */
	public static boolean allowed(RandomSpreadStructurePlacement placement, ChunkGeneratorStructureState state, int x, int z) {
		if (placement instanceof AtlasPlacement || OFF) {
			return true;
		}
		Rule me = rule(((SpreadPlacementAccessor) placement).afterworld$salt());
		if (me == BELOW) {
			return true;
		}
		for (Reserved r : RESERVED) {
			double d = Math.hypot(r.x() - x, r.z() - z);
			if (d < r.radius() + me.reach()) {
				return false;
			}
		}
		for (Holder<StructureSet> set : state.possibleStructureSets()) {
			if (!(set.value().placement() instanceof RandomSpreadStructurePlacement other) || other == placement || other instanceof AtlasPlacement) {
				continue;
			}
			Rule them = rule(((SpreadPlacementAccessor) other).afterworld$salt());
			if (them == BELOW || them.rank() >= me.rank()) {
				continue;
			}
			if (startsNear(state, set.value(), other, x, z, them.reach() + me.reach())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether {@code other} could really start a structure within {@code range} chunks of (x, z):
	 * its placement picks the chunk, and the land there is one some structure of the set stands in.
	 */
	private static boolean startsNear(ChunkGeneratorStructureState state, StructureSet set, RandomSpreadStructurePlacement other, int x, int z, int range) {
		int spacing = Math.max(1, other.spacing());
		long seed = state.getLevelSeed();
		for (int cx = Math.floorDiv(x - range, spacing); cx <= Math.floorDiv(x + range, spacing); cx++) {
			for (int cz = Math.floorDiv(z - range, spacing); cz <= Math.floorDiv(z + range, spacing); cz++) {
				ChunkPos c = other.getPotentialStructureChunk(seed, cx * spacing, cz * spacing);
				if (Math.abs(c.x() - x) <= range && Math.abs(c.z() - z) <= range && other.isStructureChunk(state, c.x(), c.z()) && couldStand(state, set, c)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Whether any structure of the set allows the land in a chunk. Afterworld's lands vary with
	 * height and structures test theirs at a corner or the middle at whatever height the ground is,
	 * so the corners and the middle are sampled over the whole range the surface can be at.
	 */
	public static boolean couldStand(ChunkGeneratorStructureState state, StructureSet set, ChunkPos c) {
		BiomeSource source = ((StructureStateAccessor) state).afterworld$biomeSource();
		var biomes = source.createCachingResolver(state.randomState());
		int[][] points = {{c.getMinBlockX(), c.getMinBlockZ()}, {c.getMiddleBlockX(), c.getMiddleBlockZ()}, {c.getMaxBlockX(), c.getMinBlockZ()},
			{c.getMinBlockX(), c.getMaxBlockZ()}, {c.getMaxBlockX(), c.getMaxBlockZ()}};
		for (int[] p : points) {
			for (int y = 56; y <= 200; y += 12) {
				Holder<Biome> b = biomes.getNoiseBiome(QuartPos.fromBlock(p[0]), QuartPos.fromBlock(y), QuartPos.fromBlock(p[1]));
				for (StructureSet.StructureSelectionEntry e : set.structures()) {
					if (e.structure().value().biomes().contains(b)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}

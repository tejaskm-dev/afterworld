package dev.afterworld.world.gen;

import com.mojang.datafixers.util.Pair;
import dev.afterworld.Afterworld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import org.jspecify.annotations.Nullable;

/**
 * Afterworld's overworld biomes. Rather than redraw the climate map, each takes the place of the
 * vanilla biome(s) the climate noise would have chosen: plains become the Vesper Downs, deserts the
 * Hourglass Dunes, oceans the Mirrorsea and so on, some split by weirdness between two of ours. The
 * world keeps vanilla's shape (and the same seed gives the same world), but nearly all of it is ours.
 *
 * <p>The biomes are also added to the overworld parameter list at climate points nothing can reach,
 * so the chunk generator knows about them (and sorts their features) without ever picking them
 * itself; {@link Remapper#remap} is the only way in.
 */
public final class AwBiomes {
	public static final ResourceKey<Biome> ASHEN_MOOR = key("ashen_moor");
	public static final ResourceKey<Biome> HOLLOWWOOD = key("hollowwood");
	public static final ResourceKey<Biome> SHARDFIELDS = key("shardfields");
	public static final ResourceKey<Biome> GLOAMFEN = key("gloamfen");
	public static final ResourceKey<Biome> VESPER_DOWNS = key("vesper_downs");
	public static final ResourceKey<Biome> MOSSMERE_WOOD = key("mossmere_wood");
	public static final ResourceKey<Biome> PALE_REACH = key("pale_reach");
	public static final ResourceKey<Biome> GLOOMWALD = key("gloomwald");
	public static final ResourceKey<Biome> PETALFALL = key("petalfall");
	public static final ResourceKey<Biome> FROSTMERE_PINES = key("frostmere_pines");
	public static final ResourceKey<Biome> WHITEWASTE = key("whitewaste");
	public static final ResourceKey<Biome> RIMEGLASS_SPIRES = key("rimeglass_spires");
	public static final ResourceKey<Biome> SKYSPIRE_PEAKS = key("skyspire_peaks");
	public static final ResourceKey<Biome> WINDWORN_HIGHLANDS = key("windworn_highlands");
	public static final ResourceKey<Biome> EMBER_STEPPE = key("ember_steppe");
	public static final ResourceKey<Biome> HOURGLASS_DUNES = key("hourglass_dunes");
	public static final ResourceKey<Biome> RUSTSCAR_CANYONS = key("rustscar_canyons");
	public static final ResourceKey<Biome> VERDANT_TANGLE = key("verdant_tangle");
	public static final ResourceKey<Biome> SPOREVEIL = key("sporeveil");
	public static final ResourceKey<Biome> MIRRORSEA = key("mirrorsea");
	public static final ResourceKey<Biome> MIRRORDEEP = key("mirrordeep");
	public static final ResourceKey<Biome> EMBER_SHALLOWS = key("ember_shallows");
	public static final ResourceKey<Biome> RIMESEA = key("rimesea");
	public static final ResourceKey<Biome> RIMEDEEP = key("rimedeep");
	public static final ResourceKey<Biome> SILVERRUN = key("silverrun");
	public static final ResourceKey<Biome> BONE_SHORE = key("bone_shore");
	public static final ResourceKey<Biome> BREAKSTONE_SHORE = key("breakstone_shore");
	public static final ResourceKey<Biome> ECHO_HOLLOWS = key("echo_hollows");
	public static final ResourceKey<Biome> ROOTWAYS = key("rootways");
	public static final ResourceKey<Biome> DUSKPINE_WEALD = key("duskpine_weald");
	public static final ResourceKey<Biome> GLIMMERFROST = key("glimmerfrost");
	public static final ResourceKey<Biome> RIMEFLOE = key("rimefloe");

	/** Order matters: the indices below point into it. */
	public static final List<ResourceKey<Biome>> ALL = List.of(ASHEN_MOOR, HOLLOWWOOD, SHARDFIELDS, GLOAMFEN, VESPER_DOWNS, MOSSMERE_WOOD, PALE_REACH,
		GLOOMWALD, PETALFALL, FROSTMERE_PINES, WHITEWASTE, RIMEGLASS_SPIRES, SKYSPIRE_PEAKS, WINDWORN_HIGHLANDS, EMBER_STEPPE, HOURGLASS_DUNES,
		RUSTSCAR_CANYONS, VERDANT_TANGLE, SPOREVEIL, MIRRORSEA, MIRRORDEEP, EMBER_SHALLOWS, RIMESEA, RIMEDEEP, SILVERRUN, BONE_SHORE,
		BREAKSTONE_SHORE, ECHO_HOLLOWS, ROOTWAYS, DUSKPINE_WEALD, GLIMMERFROST, RIMEFLOE);
	/** The land biomes worth announcing with a title card (not seas, rivers, shores or caves). */
	public static final List<ResourceKey<Biome>> MAJOR = java.util.stream.Stream.concat(ALL.subList(0, 19).stream(),
		java.util.stream.Stream.of(DUSKPINE_WEALD, GLIMMERFROST)).toList();

	private static final int ASHEN = 0;
	private static final int HOLLOW = 1;
	private static final int SHARD = 2;
	private static final int FEN = 3;
	private static final int DOWNS = 4;
	private static final int MOSSMERE = 5;
	private static final int PALE = 6;
	private static final int GLOOM = 7;
	private static final int PETAL = 8;
	private static final int FROST = 9;
	private static final int WHITE = 10;
	private static final int RIME = 11;
	private static final int PEAKS = 12;
	private static final int WINDWORN = 13;
	private static final int EMBER = 14;
	private static final int DUNES = 15;
	private static final int RUST = 16;
	private static final int TANGLE = 17;
	private static final int SPORE = 18;
	private static final int MIRROR = 19;
	private static final int MIRROR_DEEP = 20;
	private static final int SHALLOWS = 21;
	private static final int RIME_SEA = 22;
	private static final int RIME_DEEP = 23;
	private static final int SILVER = 24;
	private static final int BONE = 25;
	private static final int BREAK = 26;
	private static final int ECHO = 27;
	private static final int ROOT = 28;
	private static final int DUSKPINE = 29;
	private static final int GLIMMER = 30;
	private static final int FLOE = 31;

	/** Picks which of our biomes replaces a vanilla one at a climate point (an index into ALL). */
	@FunctionalInterface
	interface Rule {
		int pick(Climate.TargetPoint t);
	}

	private static final Map<ResourceKey<Biome>, Rule> RULES = new HashMap<>();

	static {
		// Plains: mostly the Downs; crystal fields high on the weirdness ridges; ash moor where it is dry.
		Rule plains = t -> {
			long aw = Math.abs(t.weirdness());
			if (aw > 5600 && aw < 8800 && t.continentalness() > 300) {
				return SHARD;
			}
			return t.weirdness() > -5400 && t.weirdness() < -1600 && t.humidity() < 0 ? ASHEN : DOWNS;
		};
		rule(plains, Biomes.PLAINS);
		rule(t -> DOWNS, Biomes.SUNFLOWER_PLAINS);
		rule(t -> SHARD, Biomes.MEADOW);
		rule(t -> t.weirdness() > -5600 && t.weirdness() < 0 ? ASHEN : EMBER, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA);
		// Forests: a band of each turns into Hollowwood's giants.
		rule(t -> t.weirdness() > 1200 && t.weirdness() < 5200 ? HOLLOW : MOSSMERE, Biomes.FOREST);
		rule(t -> t.weirdness() < -1200 ? HOLLOW : GLOOM, Biomes.DARK_FOREST);
		rule(t -> t.weirdness() > 1200 && t.weirdness() < 5200 ? HOLLOW : PALE, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST);
		rule(t -> PALE, Biomes.PALE_GARDEN);
		rule(t -> PETAL, Biomes.FLOWER_FOREST, Biomes.CHERRY_GROVE);
		// Only the snowy taiga is snowbound; the others are the dark, snowless Duskpine Weald.
		rule(t -> FROST, Biomes.SNOWY_TAIGA);
		rule(t -> DUSKPINE, Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA);
		// The snowy plains alternate between the bare Whitewaste and the crystal-lit Glimmerfrost.
		rule(t -> Math.abs(t.weirdness()) < 3000 ? GLIMMER : WHITE, Biomes.SNOWY_PLAINS);
		rule(t -> RIME, Biomes.ICE_SPIKES);
		rule(t -> PEAKS, Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS, Biomes.STONY_PEAKS);
		rule(t -> WINDWORN, Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_FOREST);
		rule(t -> DUNES, Biomes.DESERT);
		rule(t -> RUST, Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS);
		rule(t -> TANGLE, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE);
		rule(t -> FEN, Biomes.SWAMP, Biomes.MANGROVE_SWAMP);
		rule(t -> SPORE, Biomes.MUSHROOM_FIELDS);
		// Water.
		rule(t -> MIRROR, Biomes.OCEAN);
		rule(t -> MIRROR_DEEP, Biomes.DEEP_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN);
		rule(t -> SHALLOWS, Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN);
		// Cold seas stay open water; only the frozen oceans freeze over.
		rule(t -> RIME_SEA, Biomes.COLD_OCEAN);
		rule(t -> RIME_DEEP, Biomes.DEEP_COLD_OCEAN);
		rule(t -> FLOE, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN);
		rule(t -> SILVER, Biomes.RIVER);
		rule(t -> BONE, Biomes.BEACH);
		rule(t -> BREAK, Biomes.STONY_SHORE);
		// Underground.
		rule(t -> ECHO, Biomes.DRIPSTONE_CAVES);
		rule(t -> ROOT, Biomes.LUSH_CAVES);
	}

	@SafeVarargs
	private static void rule(Rule r, ResourceKey<Biome>... vanilla) {
		for (ResourceKey<Biome> k : vanilla) {
			RULES.put(k, r);
		}
	}

	/** Implemented by the overworld parameter list (via mixin) to hand out its remapper. */
	public interface Holders {
		@Nullable Remapper afterworld$remapper();
	}

	/**
	 * Our holders plus a table from each vanilla holder in the list to its rule, keyed by identity.
	 * Built once per parameter list and only read afterwards, so it is safe across worldgen threads.
	 */
	public static final class Remapper {
		private final Holder<Biome>[] ours;
		private final IdentityHashMap<Holder<Biome>, Rule> table;

		Remapper(Holder<Biome>[] ours, IdentityHashMap<Holder<Biome>, Rule> table) {
			this.ours = ours;
			this.table = table;
		}

		public Holder<Biome>[] ours() {
			return this.ours;
		}

		/** Called by the overworld biome source for every biome lookup, so it must stay cheap. */
		public Holder<Biome> remap(Holder<Biome> b, Climate.TargetPoint t) {
			Rule r = this.table.get(b);
			return r == null ? b : this.ours[r.pick(t)];
		}
	}

	private AwBiomes() {
	}

	private static ResourceKey<Biome> key(String name) {
		return ResourceKey.create(Registries.BIOME, Afterworld.id(name));
	}

	/** Our biomes from the same lookup vanilla's came from, or null if a data pack removed one. */
	@SuppressWarnings("unchecked")
	public static @Nullable Holder<Biome>[] resolve(HolderGetter<Biome> biomes) {
		// Vanilla's built-in registries (used by data generation and some mods) bootstrap this list
		// too; they don't contain our data-driven biomes, and asking for them would leave dangling holders.
		if (biomes instanceof HolderLookup<Biome> lookup) {
			try {
				if (lookup.listElementIds().noneMatch(ALL::contains)) {
					return null;
				}
			} catch (UnsupportedOperationException bootstrap) {
				return null;
			}
		}
		Holder<Biome>[] out = new Holder[ALL.size()];
		for (int i = 0; i < ALL.size(); i++) {
			Optional<? extends Holder<Biome>> h = biomes.get(ALL.get(i));
			if (h.isEmpty()) {
				Afterworld.LOG.warn("Afterworld biome {} is missing; its terrain will not generate", ALL.get(i).identifier());
				return null;
			}
			out[i] = h.get();
		}
		return out;
	}

	/** The remapper for a parameter list: which of its vanilla holders become which of ours. */
	public static Remapper remapper(Climate.ParameterList<Holder<Biome>> list, Holder<Biome>[] ours) {
		IdentityHashMap<Holder<Biome>, Rule> table = new IdentityHashMap<>();
		for (Pair<Climate.ParameterPoint, Holder<Biome>> p : list.values()) {
			Holder<Biome> h = p.getSecond();
			h.unwrapKey().map(RULES::get).ifPresent(r -> table.put(h, r));
		}
		return new Remapper(ours, table);
	}

	/** The parameter list plus our biomes at climate points far outside anything the noise produces. */
	public static Climate.ParameterList<Holder<Biome>> extend(Climate.ParameterList<Holder<Biome>> list, Holder<Biome>[] ours) {
		List<Pair<Climate.ParameterPoint, Holder<Biome>>> values = new ArrayList<>(list.values());
		for (int i = 0; i < ours.length; i++) {
			values.add(Pair.of(Climate.parameters(9.0F + i, 9.0F, 9.0F, 9.0F, 9.0F, 9.0F, 1.0F), ours[i]));
		}
		return new Climate.ParameterList<>(values);
	}
}

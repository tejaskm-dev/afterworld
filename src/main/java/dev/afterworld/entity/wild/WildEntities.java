package dev.afterworld.entity.wild;

import dev.afterworld.Afterworld;
import dev.afterworld.entity.creature.AwSpawns;
import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwItems;
import dev.afterworld.registry.AwTabs;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;

/** Registers the second bestiary: an entity type, attributes, spawn rules and an egg per species. */
public final class WildEntities {
	public static final Map<Species, EntityType<? extends Mob>> TYPES = new EnumMap<>(Species.class);
	private static final Map<EntityType<?>, Species> BY_TYPE = new HashMap<>();

	private WildEntities() {
	}

	public static Species speciesOf(EntityType<?> type) {
		return BY_TYPE.get(type);
	}

	public static EntityType<? extends Mob> type(Species s) {
		return TYPES.get(s);
	}

	/** Registers every species. Called from {@link AwEntities#init}. */
	public static void init() {
		for (Species s : Species.VALUES) {
			EntityType<? extends Mob> type = switch (s.kind) {
				case BEAST -> register(s, EntityType.Builder.<WildBeast>of(WildBeast::new, s.category()));
				case MONSTER -> register(s, EntityType.Builder.<WildMonster>of(WildMonster::new, s.category()));
				case FLYER -> s.hostile()
					? register(s, EntityType.Builder.<WildFlyerHostile>of(WildFlyerHostile::new, s.category()))
					: register(s, EntityType.Builder.<WildFlyer>of(WildFlyer::new, s.category()));
				case SWIMMER -> s.hostile()
					? register(s, EntityType.Builder.<WildSwimmerHostile>of(WildSwimmerHostile::new, s.category()))
					: register(s, EntityType.Builder.<WildSwimmer>of(WildSwimmer::new, s.category()));
			};
			TYPES.put(s, type);
			BY_TYPE.put(type, s);
			attributes(s, type);
			placement(s, type);
			AwEntities.SPAWN_EGGS.add(AwItems.register(AwTabs.Section.BESTIARY, s.id + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type)));
		}
	}

	private static <T extends Mob> EntityType<T> register(Species s, EntityType.Builder<T> builder) {
		builder.sized(s.width, s.height).eyeHeight(s.height * 0.82F).clientTrackingRange(10);
		if (s.has(Ability.FIRE_BITE) || s == Species.ASH_WYRMLING || s == Species.KITSUNE) {
			builder.fireImmune();
		}
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Afterworld.id(s.id));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	@SuppressWarnings("unchecked")
	private static void attributes(Species s, EntityType<? extends Mob> type) {
		var attrs = switch (s.kind) {
			case BEAST -> WildBeast.attributes(s);
			case MONSTER -> WildMonster.attributes(s);
			case FLYER -> WildFlyer.attributes(s);
			case SWIMMER -> WildSwimmer.attributes(s);
		};
		FabricDefaultAttributeRegistry.register((EntityType<? extends net.minecraft.world.entity.LivingEntity>) type, attrs);
	}

	@SuppressWarnings("unchecked")
	private static void placement(Species s, EntityType<? extends Mob> type) {
		switch (s.spawn) {
			case WILD -> SpawnPlacements.register((EntityType<Mob>) type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, AwSpawns::wild);
			case NIGHT -> SpawnPlacements.register((EntityType<Monster>) type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, AwSpawns::night);
			case ROAMING -> SpawnPlacements.register((EntityType<Monster>) type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				AwSpawns::roaming);
			case AIR -> SpawnPlacements.register((EntityType<Mob>) type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING, WildEntities::air);
			case AIR_NIGHT -> SpawnPlacements.register((EntityType<Mob>) type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING,
				WildEntities::airNight);
			case WATER -> SpawnPlacements.register((EntityType<Mob>) type, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				s == Species.ABYSSAL_ANGLER ? WildEntities::deepWater : WildEntities::water);
		}
	}

	/** Birds: on anything solid or leafy, by daylight or moonlight alike. */
	private static boolean air(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		var below = level.getBlockState(pos.below());
		return EntitySpawnReason.ignoresLightRequirements(reason) || below.is(BlockTags.LEAVES) || below.isFaceSturdy(level, pos.below(), Direction.UP);
	}

	/** Night flyers: as birds, but only in the dark and never when peaceful. */
	private static boolean airNight(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		if (EntitySpawnReason.ignoresLightRequirements(reason)) {
			return true;
		}
		return level instanceof ServerLevelAccessor sl && Monster.isDarkEnoughToSpawn(sl, pos, random) && air(type, level, reason, pos, random);
	}

	private static boolean water(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.above()).is(FluidTags.WATER);
	}

	/** Anglers keep to deep water, well under the surface. */
	private static boolean deepWater(EntityType<? extends Mob> type, LevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
		return water(type, level, reason, pos, random) && (EntitySpawnReason.ignoresLightRequirements(reason) || pos.getY() < level.getSeaLevel() - 8);
	}
}

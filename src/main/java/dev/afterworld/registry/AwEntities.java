package dev.afterworld.registry;

import dev.afterworld.Afterworld;
import dev.afterworld.entity.ThrownWeapon;
import dev.afterworld.entity.creature.ClockworkSentry;
import dev.afterworld.entity.creature.Crow;
import dev.afterworld.entity.creature.HollowStag;
import dev.afterworld.entity.creature.LanternMoth;
import dev.afterworld.entity.creature.Mossback;
import dev.afterworld.entity.creature.RuleWraith;
import dev.afterworld.entity.creature.Wisp;
import dev.afterworld.entity.creature.AshenHusk;
import dev.afterworld.entity.creature.AwSpawns;
import dev.afterworld.entity.creature.BogLurker;
import dev.afterworld.entity.creature.EchoStalker;
import dev.afterworld.entity.creature.Emberfox;
import dev.afterworld.entity.creature.Gearhound;
import dev.afterworld.entity.creature.Glimmerfin;
import dev.afterworld.entity.creature.MirrorRay;
import dev.afterworld.entity.creature.PetalSprite;
import dev.afterworld.entity.creature.RimeWight;
import dev.afterworld.entity.creature.Rimehorn;
import dev.afterworld.entity.creature.Shardling;
import dev.afterworld.entity.creature.Skywhale;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import dev.afterworld.entity.boss.BellRingerEntity;
import dev.afterworld.entity.boss.HeavyProjectile;
import dev.afterworld.entity.boss.KingEntity;
import dev.afterworld.entity.boss.PilgrimEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class AwEntities {
	public static final List<Item> SPAWN_EGGS = new ArrayList<>();

	public static final EntityType<ThrownWeapon> THROWN_WEAPON = register("thrown_weapon",
		EntityType.Builder.<ThrownWeapon>of(ThrownWeapon::new, MobCategory.MISC).sized(0.6F, 0.6F).clientTrackingRange(6).updateInterval(1).noSave().noSummon());
	public static final EntityType<dev.afterworld.entity.CrystalShard> CRYSTAL_SHARD = register("crystal_shard",
		EntityType.Builder.<dev.afterworld.entity.CrystalShard>of(dev.afterworld.entity.CrystalShard::new, MobCategory.MISC).sized(0.25F, 0.25F)
			.clientTrackingRange(6).updateInterval(1).noSave().noSummon());
	public static final EntityType<dev.afterworld.entity.MagicProjectile> MAGIC_PROJECTILE = register("magic_projectile",
		EntityType.Builder.<dev.afterworld.entity.MagicProjectile>of(dev.afterworld.entity.MagicProjectile::new, MobCategory.MISC).sized(0.3F, 0.3F)
			.clientTrackingRange(8).updateInterval(1).noSave().noSummon());
	public static final EntityType<dev.afterworld.entity.mount.Duskstrider> DUSKSTRIDER = register("duskstrider",
		EntityType.Builder.<dev.afterworld.entity.mount.Duskstrider>of(dev.afterworld.entity.mount.Duskstrider::new, MobCategory.CREATURE).sized(1.2F, 1.9F)
			.eyeHeight(1.7F).clientTrackingRange(10));
	public static final EntityType<dev.afterworld.entity.TrainingDummy> TRAINING_DUMMY = register("training_dummy",
		EntityType.Builder.<dev.afterworld.entity.TrainingDummy>of(dev.afterworld.entity.TrainingDummy::new, MobCategory.MISC).sized(0.8F, 2.0F)
			.eyeHeight(1.6F).clientTrackingRange(8));
	public static final EntityType<HeavyProjectile> HEAVY_PROJECTILE = register("heavy_projectile",
		EntityType.Builder.<HeavyProjectile>of(HeavyProjectile::new, MobCategory.MISC).sized(0.9F, 0.9F).clientTrackingRange(8).updateInterval(2).noSave().noSummon());
	public static final EntityType<PilgrimEntity> PILGRIM = register("pilgrim",
		EntityType.Builder.<PilgrimEntity>of(PilgrimEntity::new, MobCategory.MISC).sized(2.2F, 4.6F).eyeHeight(4.0F).clientTrackingRange(10).noSave().fireImmune());
	public static final EntityType<BellRingerEntity> BELL_RINGER = register("bell_ringer",
		EntityType.Builder.<BellRingerEntity>of(BellRingerEntity::new, MobCategory.MONSTER).sized(1.8F, 3.2F).eyeHeight(2.6F).clientTrackingRange(8).noSave());
	public static final EntityType<KingEntity> KING_OF_SECONDS = register("king_of_seconds",
		EntityType.Builder.<KingEntity>of(KingEntity::new, MobCategory.MONSTER).sized(1.2F, 3.1F).eyeHeight(2.7F).clientTrackingRange(8).noSave().fireImmune());

	// The Wardens of the Lands: they keep their lairs, and they are saved with them.
	public static final EntityType<dev.afterworld.entity.boss.warden.AshenShogun> ASHEN_SHOGUN = register("ashen_shogun",
		EntityType.Builder.<dev.afterworld.entity.boss.warden.AshenShogun>of(dev.afterworld.entity.boss.warden.AshenShogun::new, MobCategory.MONSTER)
			.sized(0.9F, 2.4F).eyeHeight(2.1F).clientTrackingRange(10).fireImmune());
	public static final EntityType<dev.afterworld.entity.boss.warden.CrimsonLich> CRIMSON_LICH = register("crimson_lich",
		EntityType.Builder.<dev.afterworld.entity.boss.warden.CrimsonLich>of(dev.afterworld.entity.boss.warden.CrimsonLich::new, MobCategory.MONSTER)
			.sized(0.9F, 2.6F).eyeHeight(2.3F).clientTrackingRange(10).fireImmune());
	public static final EntityType<dev.afterworld.entity.boss.warden.RimebackColossus> RIMEBACK_COLOSSUS = register("rimeback_colossus",
		EntityType.Builder.<dev.afterworld.entity.boss.warden.RimebackColossus>of(dev.afterworld.entity.boss.warden.RimebackColossus::new, MobCategory.MONSTER)
			.sized(2.2F, 3.2F).eyeHeight(2.7F).clientTrackingRange(10));
	public static final EntityType<dev.afterworld.entity.boss.warden.DuneSentinel> DUNE_SENTINEL = register("dune_sentinel",
		EntityType.Builder.<dev.afterworld.entity.boss.warden.DuneSentinel>of(dev.afterworld.entity.boss.warden.DuneSentinel::new, MobCategory.MONSTER)
			.sized(2.4F, 3.6F).eyeHeight(3.0F).clientTrackingRange(10).fireImmune());
	public static final EntityType<dev.afterworld.entity.boss.warden.GildedWarden> GILDED_WARDEN = register("gilded_warden",
		EntityType.Builder.<dev.afterworld.entity.boss.warden.GildedWarden>of(dev.afterworld.entity.boss.warden.GildedWarden::new, MobCategory.MONSTER)
			.sized(3.4F, 2.4F).eyeHeight(1.8F).clientTrackingRange(10).fireImmune());

	public static final EntityType<LanternMoth> LANTERN_MOTH = register("lantern_moth",
		EntityType.Builder.<LanternMoth>of(LanternMoth::new, MobCategory.AMBIENT).sized(0.5F, 0.5F).eyeHeight(0.3F).clientTrackingRange(8));
	public static final EntityType<Crow> CROW = register("crow",
		EntityType.Builder.<Crow>of(Crow::new, MobCategory.CREATURE).sized(0.45F, 0.6F).eyeHeight(0.5F).clientTrackingRange(8));
	public static final EntityType<Mossback> MOSSBACK = register("mossback",
		EntityType.Builder.<Mossback>of(Mossback::new, MobCategory.CREATURE).sized(1.8F, 1.4F).eyeHeight(1.1F).clientTrackingRange(10));
	public static final EntityType<HollowStag> HOLLOW_STAG = register("hollow_stag",
		EntityType.Builder.<HollowStag>of(HollowStag::new, MobCategory.CREATURE).sized(0.9F, 1.6F).eyeHeight(1.45F).clientTrackingRange(10));
	public static final EntityType<ClockworkSentry> CLOCKWORK_SENTRY = register("clockwork_sentry",
		EntityType.Builder.<ClockworkSentry>of(ClockworkSentry::new, MobCategory.MONSTER).sized(1.1F, 1.2F).eyeHeight(1.0F).clientTrackingRange(8));
	public static final EntityType<RuleWraith> RULE_WRAITH = register("rule_wraith",
		EntityType.Builder.<RuleWraith>of(RuleWraith::new, MobCategory.MONSTER).sized(0.7F, 1.9F).eyeHeight(1.6F).clientTrackingRange(8));
	public static final EntityType<Wisp> WISP = register("wisp",
		EntityType.Builder.<Wisp>of(Wisp::new, MobCategory.AMBIENT).sized(0.4F, 0.4F).eyeHeight(0.2F).clientTrackingRange(8));

	// The second wave: monsters.
	public static final EntityType<AshenHusk> ASHEN_HUSK = register("ashen_husk",
		EntityType.Builder.<AshenHusk>of(AshenHusk::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8).fireImmune());
	public static final EntityType<Shardling> SHARDLING = register("shardling",
		EntityType.Builder.<Shardling>of(Shardling::new, MobCategory.MONSTER).sized(0.9F, 0.7F).eyeHeight(0.45F).clientTrackingRange(8));
	public static final EntityType<BogLurker> BOG_LURKER = register("bog_lurker",
		EntityType.Builder.<BogLurker>of(BogLurker::new, MobCategory.MONSTER).sized(1.3F, 0.7F).eyeHeight(0.55F).clientTrackingRange(8));
	public static final EntityType<EchoStalker> ECHO_STALKER = register("echo_stalker",
		EntityType.Builder.<EchoStalker>of(EchoStalker::new, MobCategory.MONSTER).sized(0.6F, 2.7F).eyeHeight(2.45F).clientTrackingRange(8));
	public static final EntityType<Gearhound> GEARHOUND = register("gearhound",
		EntityType.Builder.<Gearhound>of(Gearhound::new, MobCategory.MONSTER).sized(0.7F, 0.95F).eyeHeight(0.8F).clientTrackingRange(10));
	public static final EntityType<RimeWight> RIME_WIGHT = register("rime_wight",
		EntityType.Builder.<RimeWight>of(RimeWight::new, MobCategory.MONSTER).sized(0.7F, 2.0F).eyeHeight(1.7F).clientTrackingRange(8));
	// ... and wildlife.
	public static final EntityType<Skywhale> SKYWHALE = register("skywhale",
		EntityType.Builder.<Skywhale>of(Skywhale::new, MobCategory.MISC).sized(4.0F, 3.0F).eyeHeight(2.0F).clientTrackingRange(20).updateInterval(2));
	public static final EntityType<Glimmerfin> GLIMMERFIN = register("glimmerfin",
		EntityType.Builder.<Glimmerfin>of(Glimmerfin::new, MobCategory.WATER_AMBIENT).sized(0.4F, 0.3F).eyeHeight(0.2F).clientTrackingRange(4));
	public static final EntityType<Emberfox> EMBERFOX = register("emberfox",
		EntityType.Builder.<Emberfox>of(Emberfox::new, MobCategory.CREATURE).sized(0.6F, 0.7F).eyeHeight(0.5F).clientTrackingRange(8).fireImmune());
	public static final EntityType<Rimehorn> RIMEHORN = register("rimehorn",
		EntityType.Builder.<Rimehorn>of(Rimehorn::new, MobCategory.CREATURE).sized(1.3F, 1.5F).eyeHeight(1.2F).clientTrackingRange(10));
	public static final EntityType<PetalSprite> PETAL_SPRITE = register("petal_sprite",
		EntityType.Builder.<PetalSprite>of(PetalSprite::new, MobCategory.AMBIENT).sized(0.35F, 0.5F).eyeHeight(0.35F).clientTrackingRange(8));
	public static final EntityType<MirrorRay> MIRROR_RAY = register("mirror_ray",
		EntityType.Builder.<MirrorRay>of(MirrorRay::new, MobCategory.WATER_CREATURE).sized(1.6F, 0.4F).eyeHeight(0.25F).clientTrackingRange(10));

	private AwEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Afterworld.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(DUSKSTRIDER, dev.afterworld.entity.mount.Duskstrider.attributes());
		FabricDefaultAttributeRegistry.register(TRAINING_DUMMY, dev.afterworld.entity.TrainingDummy.attributes());
		FabricDefaultAttributeRegistry.register(ASHEN_SHOGUN, dev.afterworld.entity.boss.warden.AshenShogun.attributes());
		FabricDefaultAttributeRegistry.register(CRIMSON_LICH, dev.afterworld.entity.boss.warden.CrimsonLich.attributes());
		FabricDefaultAttributeRegistry.register(RIMEBACK_COLOSSUS, dev.afterworld.entity.boss.warden.RimebackColossus.attributes());
		FabricDefaultAttributeRegistry.register(DUNE_SENTINEL, dev.afterworld.entity.boss.warden.DuneSentinel.attributes());
		FabricDefaultAttributeRegistry.register(GILDED_WARDEN, dev.afterworld.entity.boss.warden.GildedWarden.attributes());
		FabricDefaultAttributeRegistry.register(PILGRIM, PilgrimEntity.attributes());
		FabricDefaultAttributeRegistry.register(BELL_RINGER, BellRingerEntity.attributes());
		FabricDefaultAttributeRegistry.register(KING_OF_SECONDS, KingEntity.attributes());
		SPAWN_EGGS.add(AwItems.register(AwTabs.Section.BOSSES, "pilgrim_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(PILGRIM)));
		SPAWN_EGGS.add(AwItems.register(AwTabs.Section.BOSSES, "bell_ringer_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(BELL_RINGER)));
		SPAWN_EGGS.add(AwItems.register(AwTabs.Section.BOSSES, "king_of_seconds_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(KING_OF_SECONDS)));
		creature(LANTERN_MOTH, LanternMoth.attributes(), "lantern_moth", LanternMoth::checkSpawnRules);
		creature(CROW, Crow.attributes(), "crow", Crow::checkSpawnRules);
		creature(MOSSBACK, Mossback.attributes(), "mossback", AwSpawns::wild);
		creature(HOLLOW_STAG, HollowStag.attributes(), "hollow_stag", AwSpawns::wild);
		creature(CLOCKWORK_SENTRY, ClockworkSentry.attributes(), "clockwork_sentry", Monster::checkMonsterSpawnRules);
		creature(RULE_WRAITH, RuleWraith.attributes(), "rule_wraith", Monster::checkMonsterSpawnRules);
		creature(WISP, Wisp.attributes(), "wisp", Wisp::checkSpawnRules);
		creature(ASHEN_HUSK, AshenHusk.attributes(), "ashen_husk", AwSpawns::roaming);
		creature(SHARDLING, Shardling.attributes(), "shardling", AwSpawns::night);
		creature(BOG_LURKER, BogLurker.attributes(), "bog_lurker", AwSpawns::roaming);
		creature(ECHO_STALKER, EchoStalker.attributes(), "echo_stalker", AwSpawns::night);
		creature(GEARHOUND, Gearhound.attributes(), "gearhound", AwSpawns::roaming);
		creature(RIME_WIGHT, RimeWight.attributes(), "rime_wight", AwSpawns::roaming);
		creature(EMBERFOX, Emberfox.attributes(), "emberfox", AwSpawns::wild);
		creature(RIMEHORN, Rimehorn.attributes(), "rimehorn", AwSpawns::wild);
		creature(PETAL_SPRITE, PetalSprite.attributes(), "petal_sprite", PetalSprite::checkSpawnRules);
		swimmer(GLIMMERFIN, Glimmerfin.attributes(), "glimmerfin");
		swimmer(MIRROR_RAY, MirrorRay.attributes(), "mirror_ray");
		FabricDefaultAttributeRegistry.register(SKYWHALE, Skywhale.attributes());
		SPAWN_EGGS.add(AwItems.register(AwTabs.Section.BESTIARY, "skywhale_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(SKYWHALE)));
		dev.afterworld.entity.wild.WildEntities.init();
		AwItems.ORDERED.removeAll(SPAWN_EGGS);
		Skywhale.init();
	}

	private static <T extends net.minecraft.world.entity.animal.fish.WaterAnimal> void swimmer(EntityType<T> type, AttributeSupplier.Builder attributes, String name) {
		FabricDefaultAttributeRegistry.register(type, attributes);
		SpawnPlacements.register(type, SpawnPlacementTypes.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			net.minecraft.world.entity.animal.fish.WaterAnimal::checkSurfaceWaterAnimalSpawnRules);
		SPAWN_EGGS.add(AwItems.register(AwTabs.Section.BESTIARY, name + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type)));
	}

	private static <T extends Mob> void creature(EntityType<T> type, AttributeSupplier.Builder attributes, String name, SpawnPlacements.SpawnPredicate<T> rule) {
		FabricDefaultAttributeRegistry.register(type, attributes);
		SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rule);
		SPAWN_EGGS.add(AwItems.register(AwTabs.Section.BESTIARY, name + "_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(type)));
	}

	/** Where the creatures live: across Afterworld's biomes, each in the places that suit it. */
}

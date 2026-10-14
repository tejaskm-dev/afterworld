package dev.afterworld.entity.wild;

import static dev.afterworld.entity.wild.Ability.BURROW;
import static dev.afterworld.entity.wild.Ability.CHARGE;
import static dev.afterworld.entity.wild.Ability.CLIMB;
import static dev.afterworld.entity.wild.Ability.EXPLODE_ON_DEATH;
import static dev.afterworld.entity.wild.Ability.FIRE_BITE;
import static dev.afterworld.entity.wild.Ability.FREEZE_BITE;
import static dev.afterworld.entity.wild.Ability.GLOW;
import static dev.afterworld.entity.wild.Ability.GRAPPLE;
import static dev.afterworld.entity.wild.Ability.LUNGE;
import static dev.afterworld.entity.wild.Ability.PACK;
import static dev.afterworld.entity.wild.Ability.POISON_BITE;
import static dev.afterworld.entity.wild.Ability.REFLECT;
import static dev.afterworld.entity.wild.Ability.SCREECH;
import static dev.afterworld.entity.wild.Ability.SLAM;
import static dev.afterworld.entity.wild.Ability.SPIT_CHILL;
import static dev.afterworld.entity.wild.Ability.SPIT_FIRE;
import static dev.afterworld.entity.wild.Ability.SPIT_WEB;
import static dev.afterworld.entity.wild.Ability.SPORE_CLOUD;
import static dev.afterworld.entity.wild.Ability.STATIONARY;
import static dev.afterworld.entity.wild.Ability.STING;
import static dev.afterworld.entity.wild.Ability.SWOOP;
import static dev.afterworld.entity.wild.Ability.TELEPORT;
import static dev.afterworld.entity.wild.Ability.THROW;

import dev.afterworld.registry.AwSounds;
import java.util.EnumSet;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.MobCategory;

/**
 * Afterworld's second bestiary: thirty-eight creatures, one or more for every land and sea, each
 * built from an archetype (beast, monster, flyer, swimmer) and a handful of abilities. Their
 * models and textures come from tools/mobs_v2.py; where they live, from tools/gen_worldgen.py.
 */
public enum Species {
	// ---------------------------------------------------------------- wildlife and beasts
	DUNE_STRIDER("dune_strider", Kind.BEAST, Temper.PASSIVE, 1.2F, 2.4F, 26, 0, 0.24, 0, s(() -> SoundEvents.CAMEL_AMBIENT, () -> SoundEvents.CAMEL_HURT, () -> SoundEvents.CAMEL_DEATH, 1.1F), Spawn.WILD),
	SNOW_HARE("snow_hare", Kind.BEAST, Temper.PASSIVE, 0.4F, 0.45F, 6, 0, 0.38, 0, s(() -> SoundEvents.RABBIT_AMBIENT, () -> SoundEvents.RABBIT_HURT, () -> SoundEvents.RABBIT_DEATH, 1.0F), Spawn.WILD),
	THORNBACK_BOAR("thornback_boar", Kind.BEAST, Temper.NEUTRAL, 1.0F, 0.9F, 22, 5, 0.27, 2, s(() -> SoundEvents.HOGLIN_AMBIENT, () -> SoundEvents.HOGLIN_HURT, () -> SoundEvents.HOGLIN_DEATH, 1.25F), Spawn.WILD, CHARGE),
	HIGHLAND_RAM("highland_ram", Kind.BEAST, Temper.NEUTRAL, 0.9F, 1.3F, 18, 4, 0.25, 1, s(() -> SoundEvents.GOAT_AMBIENT, () -> SoundEvents.GOAT_HURT, () -> SoundEvents.GOAT_DEATH, 0.8F), Spawn.WILD, CHARGE),
	DUSKPINE_LYNX("duskpine_lynx", Kind.BEAST, Temper.NEUTRAL, 0.7F, 0.75F, 16, 4, 0.33, 0, s(() -> SoundEvents.OCELOT_AMBIENT, () -> SoundEvents.OCELOT_HURT, () -> SoundEvents.OCELOT_DEATH, 0.8F), Spawn.WILD, LUNGE),
	FEN_TOAD("fen_toad", Kind.BEAST, Temper.NEUTRAL, 0.9F, 0.6F, 16, 3, 0.2, 0, s(() -> SoundEvents.FROG_AMBIENT, () -> SoundEvents.FROG_HURT, () -> SoundEvents.FROG_DEATH, 0.6F), Spawn.WILD, GRAPPLE),
	CRYSTAL_CRAB("crystal_crab", Kind.BEAST, Temper.NEUTRAL, 0.9F, 0.5F, 14, 4, 0.22, 6, s(() -> SoundEvents.TURTLE_AMBIENT_LAND, () -> SoundEvents.TURTLE_HURT, () -> SoundEvents.TURTLE_DEATH, 1.4F), Spawn.WILD),
	EMBER_SALAMANDER("ember_salamander", Kind.BEAST, Temper.NEUTRAL, 0.7F, 0.4F, 14, 3, 0.26, 0, s(() -> SoundEvents.AXOLOTL_IDLE_AIR, () -> SoundEvents.AXOLOTL_HURT, () -> SoundEvents.AXOLOTL_DEATH, 0.7F), Spawn.WILD, FIRE_BITE, GLOW),
	SILVERRUN_OTTER("silverrun_otter", Kind.BEAST, Temper.PASSIVE, 0.6F, 0.45F, 10, 0, 0.3, 0, s(() -> SoundEvents.FOX_AMBIENT, () -> SoundEvents.FOX_HURT, () -> SoundEvents.FOX_DEATH, 1.5F), Spawn.WILD),
	KITSUNE("kitsune", Kind.BEAST, Temper.NEUTRAL, 0.7F, 0.9F, 22, 4, 0.32, 0, s(() -> SoundEvents.FOX_AMBIENT, () -> SoundEvents.FOX_HURT, () -> SoundEvents.FOX_DEATH, 0.9F), Spawn.WILD, TELEPORT, SPIT_FIRE, GLOW),
	SPORELING("sporeling", Kind.BEAST, Temper.PASSIVE, 0.45F, 0.6F, 6, 0, 0.24, 0, v("sporeling", 1.0F), Spawn.WILD, GLOW),
	FLOE_SEAL("floe_seal", Kind.BEAST, Temper.PASSIVE, 1.0F, 0.6F, 14, 0, 0.14, 1, s(() -> AwSounds.MOB_SEAL.value(), () -> SoundEvents.DOLPHIN_HURT, () -> SoundEvents.DOLPHIN_DEATH, 1.0F), Spawn.WILD),
	BLOSSOM_CRANE("blossom_crane", Kind.BEAST, Temper.PASSIVE, 0.6F, 1.6F, 10, 0, 0.24, 0, s(() -> AwSounds.MOB_CRANE.value(), () -> SoundEvents.PARROT_HURT, () -> SoundEvents.PARROT_DEATH, 1.0F), Spawn.WILD),

	// ---------------------------------------------------------------- monsters
	CINDER_BEETLE("cinder_beetle", Kind.MONSTER, Temper.HOSTILE, 0.7F, 0.45F, 12, 3, 0.28, 3, v("chitter", 0.8F), Spawn.ROAMING, FIRE_BITE, EXPLODE_ON_DEATH, GLOW),
	SAND_SIFTER("sand_sifter", Kind.MONSTER, Temper.HOSTILE, 1.2F, 0.8F, 24, 6, 0.26, 4, v("chitter", 0.65F), Spawn.ROAMING, BURROW, LUNGE),
	GLYPH_SCARAB("glyph_scarab", Kind.MONSTER, Temper.HOSTILE, 0.45F, 0.3F, 6, 2, 0.34, 4, v("chitter", 1.25F), Spawn.ROAMING, PACK, GLOW),
	RUST_GOLEMITE("rust_golemite", Kind.MONSTER, Temper.HOSTILE, 1.3F, 2.2F, 40, 8, 0.2, 8, s(() -> SoundEvents.IRON_GOLEM_STEP, () -> SoundEvents.IRON_GOLEM_HURT, () -> SoundEvents.IRON_GOLEM_DEATH, 0.7F), Spawn.ROAMING, SLAM),
	ICE_CRAWLER("ice_crawler", Kind.MONSTER, Temper.HOSTILE, 1.2F, 0.7F, 18, 4, 0.3, 3, s(() -> SoundEvents.SPIDER_AMBIENT, () -> SoundEvents.SPIDER_HURT, () -> SoundEvents.SPIDER_DEATH, 1.35F), Spawn.NIGHT, FREEZE_BITE, CLIMB, GLOW),
	RIME_BRUTE("rime_brute", Kind.MONSTER, Temper.HOSTILE, 1.5F, 2.6F, 45, 9, 0.23, 5, v("brute", 1.0F), Spawn.ROAMING, THROW, SLAM),
	DROWNED_REVENANT("drowned_revenant", Kind.MONSTER, Temper.HOSTILE, 0.6F, 1.95F, 24, 5, 0.24, 3, s(() -> SoundEvents.DROWNED_AMBIENT, () -> SoundEvents.DROWNED_HURT, () -> SoundEvents.DROWNED_DEATH, 0.85F), Spawn.NIGHT, THROW),
	MIRE_SHAMBLER("mire_shambler", Kind.MONSTER, Temper.HOSTILE, 0.8F, 2.0F, 28, 5, 0.19, 2, v("shambler", 1.0F), Spawn.NIGHT, SPORE_CLOUD, POISON_BITE),
	VINE_STRANGLER("vine_strangler", Kind.MONSTER, Temper.HOSTILE, 1.0F, 1.8F, 30, 6, 0.0, 4, v("treant", 1.2F), Spawn.ROAMING, STATIONARY, GRAPPLE, POISON_BITE),
	FUNGAL_BRUTE("fungal_brute", Kind.MONSTER, Temper.HOSTILE, 1.3F, 2.4F, 34, 7, 0.22, 3, v("fungal", 1.0F), Spawn.NIGHT, SPORE_CLOUD, SLAM, GLOW),
	ROOTLING_SENTINEL("rootling_sentinel", Kind.MONSTER, Temper.HOSTILE, 0.9F, 2.3F, 26, 6, 0.24, 4, v("treant", 0.85F), Spawn.ROAMING, BURROW, GRAPPLE),
	GLOOMWEAVER("gloomweaver", Kind.MONSTER, Temper.HOSTILE, 1.4F, 0.8F, 20, 4, 0.3, 2, v("weaver", 1.0F), Spawn.NIGHT, SPIT_WEB, CLIMB, POISON_BITE),
	PRISM_GOLEM("prism_golem", Kind.MONSTER, Temper.HOSTILE, 1.4F, 2.7F, 50, 9, 0.2, 10, s(() -> SoundEvents.AMETHYST_BLOCK_CHIME, () -> SoundEvents.IRON_GOLEM_HURT, () -> SoundEvents.IRON_GOLEM_DEATH, 1.3F), Spawn.ROAMING, REFLECT, SLAM, GLOW),
	DUSK_HOUND("dusk_hound", Kind.MONSTER, Temper.HOSTILE, 0.8F, 0.9F, 18, 5, 0.33, 2, v("hound", 1.0F), Spawn.NIGHT, PACK, LUNGE),

	// ---------------------------------------------------------------- flyers
	ASH_WYRMLING("ash_wyrmling", Kind.FLYER, Temper.HOSTILE, 1.0F, 0.8F, 20, 4, 0.28, 2, v("wyrmling", 1.0F), Spawn.AIR_NIGHT, SPIT_FIRE, SWOOP, GLOW),
	CANYON_VULTURE("canyon_vulture", Kind.FLYER, Temper.NEUTRAL, 0.9F, 0.9F, 16, 4, 0.26, 0, s(() -> SoundEvents.PHANTOM_AMBIENT, () -> SoundEvents.PHANTOM_HURT, () -> SoundEvents.PHANTOM_DEATH, 1.2F), Spawn.AIR, SWOOP),
	MOSSMERE_OWL("mossmere_owl", Kind.FLYER, Temper.PASSIVE, 0.5F, 0.7F, 8, 0, 0.24, 0, s(() -> AwSounds.MOB_OWL.value(), () -> SoundEvents.PARROT_HURT, () -> SoundEvents.PARROT_DEATH, 1.0F), Spawn.AIR),
	ECHO_BAT("echo_bat", Kind.FLYER, Temper.HOSTILE, 0.6F, 0.6F, 10, 3, 0.3, 0, s(() -> SoundEvents.BAT_AMBIENT, () -> SoundEvents.BAT_HURT, () -> SoundEvents.BAT_DEATH, 0.6F), Spawn.AIR_NIGHT, SCREECH, SWOOP),
	WINDRIDER_EAGLE("windrider_eagle", Kind.FLYER, Temper.NEUTRAL, 1.4F, 1.0F, 24, 6, 0.3, 1, s(() -> AwSounds.MOB_EAGLE.value(), () -> SoundEvents.PARROT_HURT, () -> SoundEvents.PARROT_DEATH, 0.8F), Spawn.AIR, SWOOP),
	MOURNING_SHADE("mourning_shade", Kind.FLYER, Temper.HOSTILE, 0.7F, 1.8F, 22, 5, 0.24, 0, v("shade", 1.0F), Spawn.AIR_NIGHT, SPIT_CHILL, TELEPORT, GLOW),
	BONE_GULL("bone_gull", Kind.FLYER, Temper.PASSIVE, 0.5F, 0.5F, 6, 0, 0.26, 0, s(() -> SoundEvents.PARROT_AMBIENT, () -> SoundEvents.PARROT_HURT, () -> SoundEvents.PARROT_DEATH, 1.35F), Spawn.AIR),
	RIME_MOTH("rime_moth", Kind.FLYER, Temper.PASSIVE, 0.5F, 0.4F, 4, 0, 0.2, 0, v("moth", 1.3F), Spawn.AIR, GLOW),

	// ---------------------------------------------------------------- swimmers
	ABYSSAL_ANGLER("abyssal_angler", Kind.SWIMMER, Temper.HOSTILE, 0.9F, 0.7F, 20, 6, 1.0, 2, v("angler", 1.0F), Spawn.WATER, LUNGE, GLOW),
	TIDECALLER_JELLY("tidecaller_jelly", Kind.SWIMMER, Temper.PASSIVE, 0.6F, 0.8F, 8, 0, 0.6, 0, s(() -> SoundEvents.GLOW_SQUID_AMBIENT, () -> SoundEvents.GLOW_SQUID_HURT, () -> SoundEvents.GLOW_SQUID_DEATH, 1.3F), Spawn.WATER, STING, GLOW),
	TUSKED_NARWHAL("tusked_narwhal", Kind.SWIMMER, Temper.NEUTRAL, 1.2F, 0.8F, 30, 6, 1.1, 2, s(() -> SoundEvents.DOLPHIN_AMBIENT, () -> SoundEvents.DOLPHIN_HURT, () -> SoundEvents.DOLPHIN_DEATH, 0.7F), Spawn.WATER, CHARGE);

	public enum Kind {
		BEAST, MONSTER, FLYER, SWIMMER
	}

	/** Passive: flees when hurt. Neutral: fights back. Hostile: hunts. */
	public enum Temper {
		PASSIVE, NEUTRAL, HOSTILE
	}

	/** Where and when it may spawn naturally. */
	public enum Spawn {
		WILD, NIGHT, ROAMING, AIR, AIR_NIGHT, WATER
	}

	public record Sounds(Supplier<SoundEvent> ambient, Supplier<SoundEvent> hurt, Supplier<SoundEvent> death, float pitch) {
	}

	private static Sounds s(Supplier<SoundEvent> ambient, Supplier<SoundEvent> hurt, Supplier<SoundEvent> death, float pitch) {
		return new Sounds(ambient, hurt, death, pitch);
	}

	/** One of Afterworld's own voice sets (AwSounds.VOICES), pitched for this species. */
	private static Sounds v(String set, float pitch) {
		return new Sounds(() -> AwSounds.voice(set).ambient(), () -> AwSounds.voice(set).hurt(), () -> AwSounds.voice(set).death(), pitch);
	}

	public static final Species[] VALUES = values();
	public final String id;
	public final Kind kind;
	public final Temper temper;
	public final float width;
	public final float height;
	public final double health;
	public final double damage;
	public final double speed;
	public final double armor;
	public final Sounds sounds;
	public final Spawn spawn;
	public final EnumSet<Ability> abilities;

	Species(String id, Kind kind, Temper temper, float width, float height, double health, double damage, double speed, double armor, Sounds sounds, Spawn spawn,
			Ability... abilities) {
		this.id = id;
		this.kind = kind;
		this.temper = temper;
		this.width = width;
		this.height = height;
		this.health = health;
		this.damage = damage;
		this.speed = speed;
		this.armor = armor;
		this.sounds = sounds;
		this.spawn = spawn;
		this.abilities = abilities.length == 0 ? EnumSet.noneOf(Ability.class) : EnumSet.of(abilities[0], abilities);
	}

	public boolean has(Ability a) {
		return this.abilities.contains(a);
	}

	public boolean hostile() {
		return this.temper == Temper.HOSTILE;
	}

	public MobCategory category() {
		return switch (this.spawn) {
			case NIGHT, ROAMING, AIR_NIGHT -> MobCategory.MONSTER;
			case WATER -> this == TIDECALLER_JELLY ? MobCategory.WATER_AMBIENT : MobCategory.WATER_CREATURE;
			case AIR -> this == RIME_MOTH || this == MOSSMERE_OWL ? MobCategory.AMBIENT : MobCategory.CREATURE;
			default -> MobCategory.CREATURE;
		};
	}

	public static Species byId(String id) {
		for (Species s : VALUES) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
	}
}

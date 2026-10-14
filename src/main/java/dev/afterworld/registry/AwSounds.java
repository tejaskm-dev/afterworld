package dev.afterworld.registry;

import dev.afterworld.Afterworld;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Afterworld's own sounds: synthesized music, Edict casts, bosses, UI and ambience. */
public final class AwSounds {
	public static final Map<String, Holder.Reference<SoundEvent>> ALL = new LinkedHashMap<>();

	public static final Holder.Reference<SoundEvent> MUSIC_TITLE = register("music.title");
	public static final Holder.Reference<SoundEvent> MUSIC_WILDS = register("music.wilds");
	public static final Holder.Reference<SoundEvent> MUSIC_BOSS = register("music.boss");
	public static final Holder.Reference<SoundEvent> UI_PAGE = register("ui.page");
	public static final Holder.Reference<SoundEvent> UI_SELECT = register("ui.select");
	public static final Holder.Reference<SoundEvent> UI_OPEN = register("ui.open");
	public static final Holder.Reference<SoundEvent> UI_DISCOVERY = register("ui.discovery");
	public static final Holder.Reference<SoundEvent> UI_RUMOUR = register("ui.rumour");
	public static final Holder.Reference<SoundEvent> UI_CHRONICLE = register("ui.chronicle");
	public static final Holder.Reference<SoundEvent> UI_AREA = register("ui.area");
	public static final Holder.Reference<SoundEvent> UI_BOSS = register("ui.boss");
	public static final Holder.Reference<SoundEvent> UI_TYPE = register("ui.type");
	public static final Holder.Reference<SoundEvent> COIN = register("ui.coin");
	public static final Holder.Reference<SoundEvent> PURCHASE = register("ui.purchase");
	public static final Holder.Reference<SoundEvent> PARRY = register("combat.parry");
	public static final Holder.Reference<SoundEvent> PERFECT = register("combat.perfect");
	public static final Holder.Reference<SoundEvent> ASPECT_UNLEASH = register("aspect.unleash");
	public static final Holder.Reference<SoundEvent> MOB_OWL = register("mob.owl");
	public static final Holder.Reference<SoundEvent> MOB_EAGLE = register("mob.eagle");
	public static final Holder.Reference<SoundEvent> MOB_CRANE = register("mob.crane");
	public static final Holder.Reference<SoundEvent> MOB_SEAL = register("mob.seal");
	public static final Holder.Reference<SoundEvent> MOB_HOUND = register("mob.hound");
	public static final Holder.Reference<SoundEvent> UI_MOVE = register("ui.move");
	public static final Holder.Reference<SoundEvent> UI_CHOOSE = register("ui.choose");
	public static final Holder.Reference<SoundEvent> UI_STEP = register("ui.step");
	public static final Holder.Reference<SoundEvent> UI_AWAKEN = register("ui.awaken");
	public static final Holder.Reference<SoundEvent> EDICT_READY = register("edict.ready");
	public static final Holder.Reference<SoundEvent> EDICT_FAIL = register("edict.fail");
	public static final Holder.Reference<SoundEvent> EDICT_RETURN = register("edict.return");
	public static final Holder.Reference<SoundEvent> EDICT_ECHOES = register("edict.echoes");
	public static final Holder.Reference<SoundEvent> EDICT_BORROWED_TIME = register("edict.borrowed_time");
	public static final Holder.Reference<SoundEvent> EDICT_WEIGHTLESSNESS = register("edict.weightlessness");
	public static final Holder.Reference<SoundEvent> EDICT_HUNGER = register("edict.hunger");
	public static final Holder.Reference<SoundEvent> EDICT_HOSPITALITY = register("edict.hospitality");
	public static final Holder.Reference<SoundEvent> EDICT_TRESPASS = register("edict.trespass");
	public static final Holder.Reference<SoundEvent> EDICT_STILLNESS = register("edict.stillness");
	public static final Holder.Reference<SoundEvent> EDICT_WITNESS = register("edict.witness");
	public static final Holder.Reference<SoundEvent> EDICT_FORGOTTEN = register("edict.forgotten");
	public static final Holder.Reference<SoundEvent> BOSS_BELL = register("boss.bell");
	public static final Holder.Reference<SoundEvent> BOSS_STOMP = register("boss.stomp");
	public static final Holder.Reference<SoundEvent> BOSS_TICK = register("boss.tick");
	public static final Holder.Reference<SoundEvent> BOSS_LOOP = register("boss.loop");
	public static final Holder.Reference<SoundEvent> BOSS_ROAR = register("boss.roar");
	public static final Holder.Reference<SoundEvent> WORLD_ANOMALY = register("world.anomaly");
	public static final Holder.Reference<SoundEvent> WORLD_WHISPER = register("world.whisper");
	public static final Holder.Reference<SoundEvent> WORLD_SHARD = register("world.shard");
	public static final Holder.Reference<SoundEvent> MOB_MOTH = register("mob.moth");
	public static final Holder.Reference<SoundEvent> MOB_CROW = register("mob.crow");
	public static final Holder.Reference<SoundEvent> MOB_SENTRY = register("mob.sentry");
	public static final Holder.Reference<SoundEvent> MOB_WRAITH = register("mob.wraith");
	public static final Holder.Reference<SoundEvent> MOB_STAG = register("mob.stag");
	public static final Holder.Reference<SoundEvent> MOB_MOSSBACK = register("mob.mossback");
	public static final Holder.Reference<SoundEvent> MOB_WHALE = register("mob.whale");
	public static final Holder.Reference<SoundEvent> MOB_SPRITE = register("mob.sprite");
	public static final Holder.Reference<SoundEvent> MOB_RIMEHORN = register("mob.rimehorn");
	public static final Holder.Reference<SoundEvent> MOB_EMBERFOX = register("mob.emberfox");
	// Remixed by tools/gen_sfx.py: hits by material, creature voices and the interface.
	public static final Holder.Reference<SoundEvent> IMPACT_CRIT = register("impact.crit");
	public static final Holder.Reference<SoundEvent> IMPACT_KILL = register("impact.kill");
	public static final Holder.Reference<SoundEvent> IMPACT_GUARD = register("impact.guard");
	public static final Holder.Reference<SoundEvent> UI_HOVER = register("ui.hover");
	public static final Holder.Reference<SoundEvent> UI_CLOSE = register("ui.close");
	public static final Holder.Reference<SoundEvent> UI_TAB = register("ui.tab");
	public static final Holder.Reference<SoundEvent> UI_ERROR = register("ui.error");
	public static final Holder.Reference<SoundEvent> UI_EQUIP = register("ui.equip");
	/** The materials a blow can land on; each has impact.<material>. */
	public static final List<String> MATERIALS = List.of("flesh", "hide", "chitin", "stone", "metal", "crystal", "wood", "bone", "spirit", "slime", "ice", "scale");
	/** Creature voice sets; each has creature.<set>.ambient, .hurt and .death. */
	public static final List<String> VOICES = List.of("whale", "wyrmling", "brute", "shambler", "weaver", "shade", "hound", "angler", "fungal",
		"chitter", "moth", "sprite", "stalker", "lurker", "husk", "sporeling", "treant", "wight");

	static {
		MATERIALS.forEach(m -> register("impact." + m));
		VOICES.forEach(v -> {
			register("creature." + v + ".ambient");
			register("creature." + v + ".hurt");
			register("creature." + v + ".death");
		});
	}

	/** A creature's ambient, hurt and death sounds. */
	public record Voice(SoundEvent ambient, SoundEvent hurt, SoundEvent death) {
	}

	private AwSounds() {
	}

	/** The hit sound of a material (see {@link #MATERIALS}). */
	public static SoundEvent impact(String material) {
		return ALL.get("impact." + material).value();
	}

	/** A creature voice set (see {@link #VOICES}). */
	public static Voice voice(String set) {
		return new Voice(ALL.get("creature." + set + ".ambient").value(), ALL.get("creature." + set + ".hurt").value(), ALL.get("creature." + set + ".death").value());
	}

	/** The cast sound of an Edict (its id matches the sound name). */
	public static Holder.Reference<SoundEvent> edict(dev.afterworld.edict.Edict e) {
		return ALL.get("edict." + e.id);
	}

	private static Holder.Reference<SoundEvent> register(String name) {
		Identifier id = Afterworld.id(name);
		Holder.Reference<SoundEvent> h = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
		ALL.put(name, h);
		return h;
	}

	public static void init() {
	}
}

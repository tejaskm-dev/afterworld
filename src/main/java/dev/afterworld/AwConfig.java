package dev.afterworld;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server and client knobs. Stored as config/afterworld.json and re-written on load so new
 * fields appear with their defaults after an update.
 */
public final class AwConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static AwConfig instance = new AwConfig();

	// --- Simulation (server) ---
	/** Master switch for the abstract daily simulation of settlements and factions. */
	public boolean worldSimulation = true;
	/** Scales how often world events and faction operations fire. 0 disables them. */
	public double worldEventFrequency = 1.0;
	/** Whether neglected settlements can decline and be abandoned. */
	public boolean settlementDecline = true;
	/** Whether faction operations may topple settlement leadership (multiplayer politics). */
	public boolean allowDestructivePolitics = true;
	/** Whether Afterworld may build sites (shrines, anomalies, ruins) in the world. */
	public boolean generateSites = true;
	/** Chunks players have spent this long in are treated as protected and never built on. */
	public int protectedChunkInhabitedTicks = 36000;
	/** Whether the wandering bosses exist at all. */
	public boolean bossesEnabled = true;
	/** Whether the Pilgrim's footsteps turn grass into path along its route. */
	public boolean pilgrimTerrainTrail = true;
	/** Days of absence after which settlements stop decaying (offline protection). */
	public int maxOfflineSimulationDays = 3;

	// --- Gameplay (server) ---
	public double edictCooldownMultiplier = 1.0;
	public int maxActiveRumours = 8;
	public boolean giveJournalOnFirstJoin = true;
	/** Show the arrival screen (origin, discipline, garb) on first join. Off: random garb, no origin. */
	public boolean arrivalScreen = true;

	// --- Effects ---
	/** Multiplier for server-sent particle counts. 0.25 is a good value for weak machines. */
	public double particleDensity = 1.0;
	/** Camera shake from slams, roars and tolls, as a fraction (0 turns it off). */
	public double screenShake = 1.0;
	/** The glowing shapes of attacks and powers (arcs, shockwaves, beams, sigils), as a fraction: 0 turns them off, 0.5 dims them. */
	public double abilityEffects = 1.0;
	/** An arc of light behind every swing of a blade. */
	public boolean swingTrails = true;

	// --- Client ---
	/**
	 * How big Afterworld's screens and HUD are. They are sized to your window, whatever Minecraft's
	 * GUI scale is (1x, 3x, auto or 7x all look alike); this makes them bigger (1.25) or smaller (0.8).
	 */
	public double uiSize = 1.0;
	/** Size Afterworld's screens and HUD by Minecraft's GUI scale instead, like the game's own menus. */
	public boolean uiFollowsGuiScale = false;
	public boolean showEdictHud = true;
	/** 0 = left of the hotbar, 1 = right of the hotbar. */
	public int hudSide = 0;
	public boolean showWorldNotices = true;
	/** The quest tracker in the top-right corner, and the marker on the focused objective. */
	public boolean questTracker = true;
	/** The pillar of light standing on the focused objective. */
	public boolean questBeam = true;
	/** Voiced dialogue (subtitles are always shown). */
	public boolean voices = true;
	/** Climbing: jump at a wall and keep pushing into it to take hold. */
	public boolean climbing = true;
	/** Pulling yourself up onto a ledge with a press of jump in the air. */
	public boolean mantle = true;
	/** Numbers rising from what you strike. */
	public boolean damageNumbers = true;
	/** Letterbox bars and titles when an ultimate or a boss phase begins. */
	public boolean cinematics = true;

	public static AwConfig get() {
		return instance;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("afterworld.json");
		try {
			if (Files.exists(file)) {
				try (Reader reader = Files.newBufferedReader(file)) {
					AwConfig loaded = GSON.fromJson(reader, AwConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
			instance.clamp();
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(instance, writer);
			}
		} catch (Exception e) {
			Afterworld.LOG.warn("Could not read config/afterworld.json, using defaults", e);
			instance = new AwConfig();
		}
	}

	private void clamp() {
		worldEventFrequency = Math.max(0, Math.min(4, worldEventFrequency));
		edictCooldownMultiplier = Math.max(0.1, Math.min(10, edictCooldownMultiplier));
		particleDensity = Math.max(0, Math.min(2, particleDensity));
		maxActiveRumours = Math.max(1, Math.min(20, maxActiveRumours));
		maxOfflineSimulationDays = Math.max(0, Math.min(30, maxOfflineSimulationDays));
		hudSide = hudSide == 1 ? 1 : 0;
	}
}

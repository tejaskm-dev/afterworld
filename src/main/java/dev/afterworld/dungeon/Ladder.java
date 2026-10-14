package dev.afterworld.dungeon;

import dev.afterworld.Afterworld;
import dev.afterworld.block.LadderStoneBlock;
import dev.afterworld.entity.boss.WardenBoss;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.player.Renown;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.util.Fx;
import dev.afterworld.world.wonder.WonderData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Warden's Ladder: fifty floors in a world of their own, where the old Edict-writers proved
 * their Wardens. Each floor is an arena hanging in the dark, built as the climbers arrive: waves of
 * the land's creatures (more of them, and stronger, the higher it goes), then a chest and the rung
 * up. Every fifth floor is a landing with a fire, a merchant and a way home, and the climb can be
 * taken up again from there; every tenth an echo of one of the five Wardens holds the way. Falling
 * in the Ladder never kills: it throws the climber out at the gate.
 *
 * <p>Floors are builds like any site's ({@code ladder_<tier>_<n>}, {@code ladder_<tier>_echo},
 * {@code ladder_landing}), stamped into each climb's own strip of the Ladder world. Their markers
 * say where climbers arrive, where creatures come from and where the rungs and the chest stand.
 */
public final class Ladder {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Afterworld.id("ladder"));
	public static final int TOP = 50;
	/** Each climb has its own strip of the Ladder world, and each floor its own plot along it. */
	private static final int STRIP = 4096;
	private static final int PLOT = 128;
	private static final int GROUND = 96;
	/** Every Ladder build has its floor on this layer (tools/wonders/ladder.py). */
	private static final int BUILD_FLOOR = 12;
	/** How long the floor's name hangs before the first wave, and the pause between waves. */
	private static final int ARRIVE_TICKS = 70;
	private static final int BREATHER_TICKS = 50;
	/** The five tiers, each with the land it remembers and the Warden whose echo ends it. */
	public static final String[] TIERS = {"overgrown", "crimson", "rime", "sun", "gilded"};
	private static final String[] ECHOES = {"ashen_shogun", "crimson_lich", "rimeback_colossus", "dune_sentinel", "gilded_warden"};
	private static final int[] COLORS = {0x9FD86A, 0xE0485A, 0x9AD8F0, 0xF0C060, 0xE8C060};
	/** Each tier's creatures: the common ones, then the heavy ones that lead later waves. */
	private static final String[][] FOES = {
		{"mire_shambler", "vine_strangler", "rootling_sentinel", "dusk_hound", "bog_lurker", "gloomweaver"},
		{"ashen_husk", "drowned_revenant", "mourning_shade", "echo_bat", "rule_wraith"},
		{"ice_crawler", "rime_wight", "shardling", "ice_crawler", "rime_wight"},
		{"sand_sifter", "glyph_scarab", "cinder_beetle", "ash_wyrmling", "sand_sifter"},
		{"clockwork_sentry", "gearhound", "rule_wraith", "echo_stalker", "gearhound"},
	};
	private static final String[] HEAVY = {"fungal_brute", "echo_stalker", "rime_brute", "rust_golemite", "prism_golem"};
	/** How many layouts each tier has for its ordinary floors. */
	private static final int LAYOUTS = 2;
	private static final String FOE_TAG = "afterworld:ladder";

	enum Phase {
		ARRIVING, FIGHTING, BREATHER, CLEARED, RESTING
	}

	static final class Climb {
		final int strip;
		final List<UUID> climbers = new ArrayList<>();
		int floor;
		Phase phase = Phase.ARRIVING;
		int phaseTicks;
		int wave;
		int waves;
		final List<UUID> foes = new ArrayList<>();
		int foesTotal;
		BoundingBox box;
		BlockPos start;
		@Nullable BlockPos up;
		@Nullable BlockPos out;
		@Nullable BlockPos chest;
		@Nullable BlockPos bossAt;
		final List<BlockPos> spawns = new ArrayList<>();
		@Nullable BoundingBox oldBox;
		int clearOldIn;
		/** Ticks since arriving on a floor during which its rungs don't take anyone. */
		int settle;

		Climb(int strip) {
			this.strip = strip;
		}
	}

	private static final Map<Integer, Climb> CLIMBS = new HashMap<>();
	private static final Map<UUID, Climb> CLIMBER = new HashMap<>();
	/** The climb whose floor is being stamped, for the markers the stamping turns up. */
	private static @Nullable Climb stamping;

	private Ladder() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.LadderAction.TYPE, (payload, ctx) -> action(ctx.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(Ladder::tick);
		// The Ladder never kills: a mortal blow throws the climber out at the gate.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && CLIMBER.containsKey(p.getUUID())) {
				p.setHealth(Math.max(2.0F, p.getMaxHealth() * 0.5F));
				fall(p);
				return false;
			}
			return true;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leave(handler.player, false));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			// Back from a game that stopped mid-climb: the Ladder lets them go where they came in.
			ServerPlayer p = handler.player;
			if (p.level().dimension() == DIMENSION && !CLIMBER.containsKey(p.getUUID())) {
				server.execute(() -> sendHome(p));
			}
		});
		// Creatures left over from a climb that ended with the game have nobody to fight.
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (level.dimension() == DIMENSION && entity.entityTags().contains(FOE_TAG) && !tracked(entity.getUUID())) {
				entity.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CLIMBS.clear();
			CLIMBER.clear();
		});
	}

	// ------------------------------------------------------------------ the stones

	/** A Ladder stone touched (or stood on): the gate opens its window, a lit rung climbs on or leads out. */
	public static void use(ServerLevel level, ServerPlayer p, BlockPos pos, BlockState state) {
		LadderStoneBlock.Kind kind = state.getValue(LadderStoneBlock.KIND);
		if (kind == LadderStoneBlock.Kind.GATE) {
			if (CLIMBER.containsKey(p.getUUID())) {
				return;
			}
			PlayerState s = PlayerState.of(p);
			CompoundTag t = new CompoundTag();
			t.putInt("best", s.ladderBest);
			t.putInt("resume", s.ladderResume);
			t.putInt("top", TOP);
			t.putLong("gate", pos.asLong());
			int party = 0;
			for (ServerPlayer o : level.getPlayers(o -> o != p && o.distanceToSqr(p) < 8 * 8 && !o.isSpectator())) {
				party++;
			}
			t.putInt("party", party);
			ServerPlayNetworking.send(p, new AwPayloads.LadderOpen(t));
			Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 0.6F);
			return;
		}
		Climb c = CLIMBER.get(p.getUUID());
		// Only a rung on the floor the climber is actually on (just after a climb the game can
		// still think they stand on the one below), and not in the moment of arriving.
		if (c != null && (c.box == null || !c.box.isInside(pos) || c.settle > 0 || p.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) > 6.0)) {
			return;
		}
		if (c == null || !state.getValue(LadderStoneBlock.ACTIVE)) {
			if (c != null && kind == LadderStoneBlock.Kind.UP) {
				p.sendOverlayMessage(Component.translatable("ladder.afterworld.rung_cold").withColor(0xA89A7C));
			}
			return;
		}
		if (kind == LadderStoneBlock.Kind.UP) {
			if (c.floor >= TOP) {
				return;
			}
			climb(level, c, c.floor + 1);
		} else {
			leave(p, true);
			sendHome(p);
		}
	}

	private static void action(ServerPlayer p, AwPayloads.LadderAction a) {
		if (CLIMBER.containsKey(p.getUUID()) || p.level().dimension() == DIMENSION) {
			return;
		}
		BlockPos gate = a.gate();
		if (p.distanceToSqr(Vec3.atCenterOf(gate)) > 8 * 8 || !p.level().getBlockState(gate).is(AwBlocks.LADDER_STONE)) {
			return;
		}
		int floor = a.action() == AwPayloads.LadderAction.RESUME ? Math.max(1, Math.min(TOP, PlayerState.of(p).ladderResume)) : 1;
		begin(p, floor);
	}

	/** Starts a climb from {@code floor} for the traveller and anyone standing with them at the gate. */
	public static void begin(ServerPlayer p, int floor) {
		MinecraftServer server = p.level().getServer();
		ServerLevel ladder = server.getLevel(DIMENSION);
		if (ladder == null) {
			Afterworld.LOG.warn("The Warden's Ladder has no world to stand in");
			return;
		}
		int strip = 0;
		while (CLIMBS.containsKey(strip)) {
			strip++;
		}
		Climb c = new Climb(strip);
		CLIMBS.put(strip, c);
		List<ServerPlayer> party = new ArrayList<>();
		party.add(p);
		for (ServerPlayer o : p.level().getPlayers(o -> o != p && o.distanceToSqr(p) < 8 * 8 && !o.isSpectator() && !CLIMBER.containsKey(o.getUUID()))) {
			party.add(o);
		}
		for (ServerPlayer o : party) {
			PlayerState s = PlayerState.of(o);
			s.ladderReturnDim = o.level().dimension().identifier().toString();
			s.ladderReturnPos = o.blockPosition().asLong();
			c.climbers.add(o.getUUID());
			CLIMBER.put(o.getUUID(), c);
		}
		climb(ladder, c, floor);
	}

	// ------------------------------------------------------------------ floors

	/** Builds floor {@code floor} of the climb and brings its climbers up to it. */
	private static void climb(ServerLevel ladder, Climb c, int floor) {
		MinecraftServer server = ladder.getServer();
		discardFoes(ladder, c);
		if (c.box != null) {
			c.oldBox = c.box;
			c.clearOldIn = 60;
		}
		c.floor = floor;
		c.wave = 0;
		c.waves = waves(floor);
		c.spawns.clear();
		c.up = null;
		c.out = null;
		c.chest = null;
		c.bossAt = null;
		c.start = null;
		String build = build(floor, server.overworld().getSeed() ^ c.strip * 31L);
		WonderData data = WonderData.get(build);
		if (data == null) {
			Afterworld.LOG.warn("The Ladder has no floor called {}", build);
			end(ladder, c);
			return;
		}
		int x0 = c.strip * STRIP;
		int z0 = floor * PLOT;
		BlockPos origin = new BlockPos(x0, GROUND - BUILD_FLOOR, z0);
		c.box = new BoundingBox(x0, origin.getY(), z0, x0 + data.width(Rotation.NONE) - 1, origin.getY() + data.sy - 1, z0 + data.depth(Rotation.NONE) - 1);
		clear(ladder, c.box);
		RandomSource random = RandomSource.create(build.hashCode() ^ c.strip);
		stamping = c;
		try {
			for (int cx = c.box.minX(); cx <= c.box.maxX(); cx += 16) {
				for (int cz = c.box.minZ(); cz <= c.box.maxZ(); cz += 16) {
					ladder.getChunk(cx >> 4, cz >> 4);
					data.place(ladder, origin, Rotation.NONE, new BoundingBox(cx, ladder.getMinY(), cz, cx + 15, ladder.getMaxY(), cz + 15), random);
				}
			}
		} finally {
			stamping = null;
		}
		if (c.start == null) {
			c.start = new BlockPos(c.box.getCenter().getX(), GROUND, c.box.maxZ() - 4);
		}
		boolean landing = landing(floor);
		boolean echo = echo(floor);
		c.phase = landing ? Phase.RESTING : Phase.ARRIVING;
		c.phaseTicks = 0;
		c.settle = 30;
		Component title = Component.translatable("ladder.afterworld.floor", floor);
		Component sub = Component.translatable(landing ? "ladder.afterworld.landing" : echo ? "ladder.afterworld.echo." + ECHOES[tier(floor)]
			: "ladder.afterworld.tier." + TIERS[tier(floor)]);
		for (ServerPlayer p : climbers(server, c)) {
			p.teleportTo(ladder, c.start.getX() + 0.5, c.start.getY(), c.start.getZ() + 0.5, java.util.Set.of(), 180.0F, 0.0F, false);
			p.fallDistance = 0;
			ServerPlayNetworking.send(p, new AwPayloads.Title(echo ? AwPayloads.Title.BOSS : AwPayloads.Title.PLACE, title, sub, COLORS[tier(floor)]));
			Fx.privateSound(p, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.END_PORTAL_SPAWN), 0.5F, 1.4F);
		}
		if (landing) {
			rest(ladder, c);
		}
		sync(server, c);
	}

	/** Which build stands at a floor: a landing, a Warden's echo, or one of the tier's arenas. */
	static String build(int floor, long seed) {
		if (landing(floor)) {
			return "ladder_landing";
		}
		String tier = TIERS[tier(floor)];
		if (echo(floor)) {
			return "ladder_" + tier + "_echo";
		}
		int n = 1 + (int) Math.floorMod(seed + floor * 7919L, (long) LAYOUTS);
		return "ladder_" + tier + "_" + n;
	}

	static int tier(int floor) {
		return Math.min(TIERS.length - 1, (floor - 1) / 10);
	}

	static boolean landing(int floor) {
		return floor % 10 == 5;
	}

	static boolean echo(int floor) {
		return floor % 10 == 0;
	}

	/** Waves on a floor: two at the bottom, seven near the top. */
	static int waves(int floor) {
		return echo(floor) || landing(floor) ? 0 : 2 + tier(floor) + (floor % 10 >= 6 ? 1 : 0);
	}

	/** Creatures in a wave. */
	static int foes(int floor, int wave, int party) {
		return 3 + tier(floor) + (floor % 10) / 3 + wave / 2 + 2 * Math.max(0, party - 1);
	}

	static double healthScale(int floor) {
		return 1.0 + 0.07 * (floor - 1);
	}

	static double damageScale(int floor) {
		return 1.0 + 0.035 * (floor - 1);
	}

	/** A marker in a floor being built. */
	public static boolean marker(BlockPos pos, String name) {
		Climb c = stamping;
		if (c == null) {
			return false;
		}
		switch (name) {
			case "ladder:start" -> c.start = pos;
			case "ladder:up" -> c.up = pos;
			case "ladder:out" -> c.out = pos;
			case "ladder:chest" -> c.chest = pos;
			case "ladder:boss" -> c.bossAt = pos;
			case "ladder:spawn" -> c.spawns.add(pos);
			default -> {
				return false;
			}
		}
		return true;
	}

	private static void clear(ServerLevel level, BoundingBox box) {
		// Whatever was left on the floor goes with it: Rook, dropped things, orbs (never a player).
		for (Entity e : level.getEntitiesOfClass(Entity.class, net.minecraft.world.phys.AABB.of(box).inflate(4), e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
			e.discard();
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y <= box.maxY(); y++) {
					m.set(x, y, z);
					if (!level.getBlockState(m).isAir()) {
						level.setBlock(m, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ the fight

	private static void tick(MinecraftServer server) {
		if (CLIMBS.isEmpty()) {
			return;
		}
		ServerLevel ladder = server.getLevel(DIMENSION);
		if (ladder == null) {
			return;
		}
		for (Climb c : new ArrayList<>(CLIMBS.values())) {
			List<ServerPlayer> here = climbers(server, c);
			if (here.isEmpty()) {
				end(ladder, c);
				continue;
			}
			if (c.oldBox != null && --c.clearOldIn <= 0) {
				clear(ladder, c.oldBox);
				c.oldBox = null;
			}
			// Anyone who falls off the floor is caught and set back at its start.
			for (ServerPlayer p : here) {
				if (p.getY() < GROUND - 24 && c.start != null) {
					p.teleportTo(ladder, c.start.getX() + 0.5, c.start.getY(), c.start.getZ() + 0.5, java.util.Set.of(), p.getYRot(), p.getXRot(), false);
					p.fallDistance = 0;
					p.hurtServer(ladder, ladder.damageSources().fellOutOfWorld(), 4.0F);
					p.sendOverlayMessage(Component.translatable("ladder.afterworld.caught").withColor(0xB8AE98));
				}
			}
			if (c.settle > 0) {
				c.settle--;
			}
			// Standing on a lit rung (however one got there) takes it, unless crouching.
			if (c.phaseTicks % 5 == 0 && c.settle == 0) {
				for (ServerPlayer p : here) {
					BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
					BlockState st = ladder.getBlockState(under);
					if (p.onGround() && !p.isShiftKeyDown() && st.is(AwBlocks.LADDER_STONE) && st.getValue(LadderStoneBlock.ACTIVE)) {
						use(ladder, p, under, st);
						break;
					}
				}
				if (!CLIMBS.containsKey(c.strip)) {
					continue;
				}
			}
			c.phaseTicks++;
			switch (c.phase) {
				case ARRIVING -> {
					if (c.phaseTicks >= ARRIVE_TICKS) {
						if (echo(c.floor)) {
							summonEcho(ladder, c, here.size());
							c.phase = Phase.FIGHTING;
						} else {
							nextWave(ladder, c, here.size());
						}
						c.phaseTicks = 0;
						sync(server, c);
					}
				}
				case FIGHTING -> {
					if (c.phaseTicks % 10 == 0) {
						int before = c.foes.size();
						prune(ladder, c);
						if (c.foes.size() != before) {
							sync(server, c);
						}
						if (c.foes.isEmpty()) {
							if (c.wave >= c.waves) {
								cleared(ladder, c);
							} else {
								c.phase = Phase.BREATHER;
								c.phaseTicks = 0;
								for (ServerPlayer p : here) {
									p.sendOverlayMessage(Component.translatable("ladder.afterworld.wave_done", c.wave, c.waves).withColor(0xE8DCC0));
								}
							}
							sync(server, c);
						} else if (c.phaseTicks % 40 == 0) {
							// Creatures that lose the climbers are pointed back at them.
							for (UUID id : c.foes) {
								if (ladder.getEntity(id) instanceof Mob m && (m.getTarget() == null || !m.getTarget().isAlive())) {
									ServerPlayer near = nearest(here, m.position());
									if (near != null) {
										m.setTarget(near);
									}
								}
							}
						}
					}
				}
				case BREATHER -> {
					if (c.phaseTicks >= BREATHER_TICKS) {
						nextWave(ladder, c, here.size());
						c.phaseTicks = 0;
						sync(server, c);
					}
				}
				default -> {
				}
			}
		}
	}

	private static void nextWave(ServerLevel ladder, Climb c, int party) {
		c.wave++;
		c.phase = Phase.FIGHTING;
		int n = foes(c.floor, c.wave, party);
		int tier = tier(c.floor);
		RandomSource r = ladder.getRandom();
		List<ServerPlayer> here = climbers(ladder.getServer(), c);
		int heavies = c.wave >= 2 ? c.wave / 2 : 0;
		for (int i = 0; i < n; i++) {
			String id = i < heavies ? HEAVY[tier] : FOES[tier][r.nextInt(FOES[tier].length)];
			BlockPos at = c.spawns.isEmpty() ? c.start.offset(r.nextInt(9) - 4, 0, 12) : c.spawns.get(r.nextInt(c.spawns.size()));
			boolean elite = i >= heavies && r.nextFloat() < Math.min(0.35F, 0.015F * c.floor);
			Mob m = spawn(ladder, c, id, Vec3.atBottomCenterOf(at).add(r.nextDouble() * 2 - 1, 0, r.nextDouble() * 2 - 1), elite);
			if (m == null) {
				continue;
			}
			ServerPlayer near = nearest(here, m.position());
			if (near != null) {
				m.setTarget(near);
			}
			Fx.burst(ladder, Fx.dust(COLORS[tier], 1.6F), m.position().add(0, 1, 0), 18, 0.5, 0.05);
		}
		c.foesTotal = c.foes.size();
		Fx.sound(ladder, Vec3.atCenterOf(c.start), SoundEvents.RAID_HORN.value(), 1.4F, 1.0F + 0.06F * c.wave);
		for (ServerPlayer p : here) {
			p.sendOverlayMessage(Component.translatable("ladder.afterworld.wave", c.wave, c.waves).withColor(COLORS[tier]));
		}
	}

	private static @Nullable Mob spawn(ServerLevel ladder, Climb c, String id, Vec3 at, boolean elite) {
		return foe(ladder, id, at, c.floor, elite, FOE_TAG, c.foes::add);
	}

	/**
	 * One of the Ladder's creatures, made as strong as floor {@code floor}'s and tagged; {@code track}
	 * hears of it before it enters the world. Proving rings in the world use these too.
	 */
	public static @Nullable Mob foe(ServerLevel level, String id, Vec3 at, int floor, boolean elite, String tag,
		java.util.function.Consumer<UUID> track) {
		var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Afterworld.id(id));
		if (type.isEmpty()) {
			return null;
		}
		Entity e = type.get().create(level, EntitySpawnReason.SPAWNER);
		if (!(e instanceof Mob m)) {
			return null;
		}
		m.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
		m.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.SPAWNER, null);
		scale(m, floor);
		if (elite) {
			dev.afterworld.npc.Npcs.makeElite(m);
		}
		m.setPersistenceRequired();
		m.addTag(tag);
		track.accept(m.getUUID());
		level.addFreshEntity(m);
		return m;
	}

	/** Which of the tiers' creatures belong to the land at {@code pos}: the frost, the sands, the pale and ashen lands, the vaults and crystal, or the green. */
	public static String[] poolFor(ServerLevel level, BlockPos pos) {
		String b = level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		int tier;
		if (b.contains("frost") || b.contains("white") || b.contains("glimmer") || b.contains("rime") || b.contains("snow") || b.contains("ice")
			|| b.contains("skyspire") || b.contains("frozen")) {
			tier = 2;
		} else if (b.contains("dune") || b.contains("desert") || b.contains("hourglass") || b.contains("ember_steppe") || b.contains("badlands")) {
			tier = 3;
		} else if (b.contains("pale") || b.contains("ashen") || b.contains("gloom")) {
			tier = 1;
		} else if (b.contains("shard") || b.contains("rustscar") || b.contains("crystal")) {
			tier = 4;
		} else {
			tier = 0;
		}
		return FOES[tier];
	}

	/** The heavy creature that leads later waves in the land at {@code pos}. */
	public static String heavyFor(ServerLevel level, BlockPos pos) {
		String[] pool = poolFor(level, pos);
		for (int t = 0; t < FOES.length; t++) {
			if (FOES[t] == pool) {
				return HEAVY[t];
			}
		}
		return HEAVY[0];
	}

	private static void scale(Mob m, int floor) {
		var hp = m.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(hp.getBaseValue() * healthScale(floor));
			m.setHealth(m.getMaxHealth());
		}
		var atk = m.getAttribute(Attributes.ATTACK_DAMAGE);
		if (atk != null) {
			atk.setBaseValue(atk.getBaseValue() * damageScale(floor));
		}
		var follow = m.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(Math.max(follow.getBaseValue(), 48));
		}
	}

	private static void summonEcho(ServerLevel ladder, Climb c, int party) {
		int tier = tier(c.floor);
		BlockPos at = c.bossAt != null ? c.bossAt : c.start.offset(0, 0, 20);
		var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Afterworld.id(ECHOES[tier]));
		if (type.isEmpty() || !(type.get().create(ladder, EntitySpawnReason.SPAWNER) instanceof WardenBoss boss)) {
			cleared(ladder, c);
			return;
		}
		boss.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		boss.setHome(Vec3.atBottomCenterOf(at));
		boss.makeEcho((float) (0.55 + 0.012 * c.floor + 0.25 * (party - 1)));
		boss.setPersistenceRequired();
		boss.addTag(FOE_TAG);
		c.foes.add(boss.getUUID());
		ladder.addFreshEntity(boss);
		c.foesTotal = 1;
		c.wave = 1;
		c.waves = 1;
		Fx.shake(ladder, Vec3.atCenterOf(at), 64, 4.0F, 30);
		Fx.burst(ladder, ParticleTypes.SOUL_FIRE_FLAME, Vec3.atCenterOf(at).add(0, 1.5, 0), 80, 1.5, 0.08);
	}

	/** Forgets creatures that died, fell or wandered off into the dark. */
	private static void prune(ServerLevel ladder, Climb c) {
		for (Iterator<UUID> it = c.foes.iterator(); it.hasNext(); ) {
			Entity e = ladder.getEntity(it.next());
			if (e == null || !e.isAlive()) {
				it.remove();
			} else if (e.getY() < GROUND - 30) {
				e.discard();
				it.remove();
			}
		}
	}

	private static void discardFoes(ServerLevel ladder, Climb c) {
		for (UUID id : c.foes) {
			Entity e = ladder.getEntity(id);
			if (e != null) {
				e.discard();
			}
		}
		c.foes.clear();
	}

	// ------------------------------------------------------------------ winning a floor

	private static void cleared(ServerLevel ladder, Climb c) {
		c.phase = Phase.CLEARED;
		c.phaseTicks = 0;
		int tier = tier(c.floor);
		boolean echo = echo(c.floor);
		MinecraftServer server = ladder.getServer();
		if (c.chest != null) {
			ladder.setBlock(c.chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, net.minecraft.core.Direction.SOUTH), Block.UPDATE_ALL);
			if (ladder.getBlockEntity(c.chest) instanceof ChestBlockEntity chest) {
				String table = c.floor >= TOP ? "ladder_summit" : echo ? "ladder_echo_" + (tier + 1) : "ladder_" + (tier + 1);
				chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Afterworld.id("chests/" + table)), ladder.getRandom().nextLong());
			}
			Fx.burst(ladder, ParticleTypes.END_ROD, Vec3.atCenterOf(c.chest).add(0, 0.6, 0), 40, 0.6, 0.12);
			Fx.sound(ladder, Vec3.atCenterOf(c.chest), SoundEvents.VAULT_OPEN_SHUTTER, 1.2F, 1.1F);
		}
		light(ladder, c.up);
		if (echo) {
			light(ladder, c.out);
		}
		for (ServerPlayer p : climbers(server, c)) {
			PlayerState s = PlayerState.of(p);
			if (c.floor > s.ladderBest) {
				s.ladderBest = c.floor;
			}
			if (echo && c.floor < TOP) {
				s.ladderResume = Math.max(s.ladderResume, c.floor + 1);
			}
			ExperienceOrb.award(ladder, p.position().add(0, 1, 0), 8 + c.floor * 3);
			Renown.grant(p, echo ? 30 + c.floor : 2 + c.floor / 4, Component.translatable("ladder.afterworld.renown", c.floor).getString());
			if (echo) {
				Progress.legend(p, "battle", Component.translatable("ladder.afterworld.legend_echo", Component.translatable("entity.afterworld." + ECHOES[tier]), c.floor).getString());
			}
			if (c.floor >= TOP) {
				ServerPlayNetworking.send(p, new AwPayloads.Title(AwPayloads.Title.CHAPTER, Component.translatable("ladder.afterworld.summit"),
					Component.translatable("ladder.afterworld.summit.sub"), 0xE8C060));
				Progress.legend(p, "battle", Component.translatable("ladder.afterworld.legend_summit").getString());
				s.ladderResume = 1;
			} else {
				p.sendOverlayMessage(Component.translatable(echo ? "ladder.afterworld.echo_down" : "ladder.afterworld.floor_won", c.floor).withColor(COLORS[tier]));
			}
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, false));
		}
		if (c.floor >= TOP) {
			light(ladder, c.out);
		}
	}

	/** A landing: rest, a merchant, a fire, and the rungs lit at once. */
	private static void rest(ServerLevel ladder, Climb c) {
		light(ladder, c.up);
		light(ladder, c.out);
		for (ServerPlayer p : climbers(ladder.getServer(), c)) {
			PlayerState s = PlayerState.of(p);
			s.ladderResume = Math.max(s.ladderResume, c.floor + 1);
			if (c.floor > s.ladderBest) {
				s.ladderBest = c.floor;
			}
			p.heal(p.getMaxHealth());
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, false));
			p.getFoodData().eat(8, 0.8F);
		}
	}

	private static void light(ServerLevel ladder, @Nullable BlockPos at) {
		if (at == null) {
			return;
		}
		BlockState s = ladder.getBlockState(at);
		if (s.is(AwBlocks.LADDER_STONE)) {
			ladder.setBlock(at, s.setValue(LadderStoneBlock.ACTIVE, true), Block.UPDATE_ALL);
			Fx.burst(ladder, ParticleTypes.END_ROD, Vec3.atCenterOf(at).add(0, 0.7, 0), 30, 0.4, 0.1);
			Fx.sound(ladder, Vec3.atCenterOf(at), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.3F);
		}
	}

	// ------------------------------------------------------------------ leaving

	/** A mortal blow in the Ladder: the climber is thrown out at the gate, alive. */
	private static void fall(ServerPlayer p) {
		Climb c = CLIMBER.get(p.getUUID());
		int floor = c == null ? 0 : c.floor;
		leave(p, true);
		p.removeAllEffects();
		p.clearFire();
		sendHome(p);
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0, false, true));
		ServerPlayNetworking.send(p, new AwPayloads.Title(AwPayloads.Title.PLACE, Component.translatable("ladder.afterworld.thrown"),
			Component.translatable("ladder.afterworld.thrown.sub", floor), 0xB8AE98));
	}

	/** Takes a climber out of their climb (the climb ends with its last climber). */
	private static void leave(ServerPlayer p, boolean notify) {
		Climb c = CLIMBER.remove(p.getUUID());
		if (c == null) {
			return;
		}
		c.climbers.remove(p.getUUID());
		if (ServerPlayNetworking.canSend(p, AwPayloads.LadderState.TYPE)) {
			ServerPlayNetworking.send(p, new AwPayloads.LadderState(new CompoundTag()));
		}
		if (c.climbers.isEmpty()) {
			ServerLevel ladder = p.level().getServer().getLevel(DIMENSION);
			if (ladder != null) {
				end(ladder, c);
			}
		}
	}

	private static void end(ServerLevel ladder, Climb c) {
		discardFoes(ladder, c);
		for (UUID id : c.climbers) {
			CLIMBER.remove(id);
		}
		c.climbers.clear();
		if (c.box != null) {
			clear(ladder, c.box);
		}
		if (c.oldBox != null) {
			clear(ladder, c.oldBox);
		}
		CLIMBS.remove(c.strip);
	}

	/** Back where the traveller stepped into the Ladder (or their spawn, if that is lost). */
	private static void sendHome(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		PlayerState s = PlayerState.of(p);
		ServerLevel level = null;
		if (!s.ladderReturnDim.isEmpty()) {
			level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(s.ladderReturnDim)));
		}
		BlockPos at = s.ladderReturnPos != 0 ? BlockPos.of(s.ladderReturnPos) : null;
		if (level == null || at == null || level.dimension() == DIMENSION) {
			level = server.overworld();
			at = level.getRespawnData().pos();
		}
		p.teleportTo(level, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, java.util.Set.of(), p.getYRot(), 0.0F, false);
		p.fallDistance = 0;
		Fx.privateSound(p, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ENDERMAN_TELEPORT), 0.6F, 0.8F);
	}

	// ------------------------------------------------------------------ the panel

	private static void sync(MinecraftServer server, Climb c) {
		CompoundTag t = new CompoundTag();
		t.putInt("floor", c.floor);
		t.putInt("tier", tier(c.floor));
		t.putString("phase", c.phase.name().toLowerCase(java.util.Locale.ROOT));
		t.putInt("wave", c.wave);
		t.putInt("waves", c.waves);
		t.putInt("foes", c.foes.size());
		t.putInt("total", c.foesTotal);
		t.putBoolean("echo", echo(c.floor));
		t.putBoolean("landing", landing(c.floor));
		for (ServerPlayer p : climbers(server, c)) {
			if (ServerPlayNetworking.canSend(p, AwPayloads.LadderState.TYPE)) {
				ServerPlayNetworking.send(p, new AwPayloads.LadderState(t));
			}
		}
	}

	private static List<ServerPlayer> climbers(MinecraftServer server, Climb c) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : c.climbers) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && p.isAlive()) {
				out.add(p);
			}
		}
		return out;
	}

	private static @Nullable ServerPlayer nearest(List<ServerPlayer> players, Vec3 at) {
		ServerPlayer best = null;
		double bd = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			double d = p.position().distanceToSqr(at);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	private static boolean tracked(UUID id) {
		for (Climb c : CLIMBS.values()) {
			if (c.foes.contains(id)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ for tests and commands

	/** Moves a climb straight to {@code floor} (commands and tests); starts one if the traveller isn't climbing. */
	public static void jump(ServerPlayer p, int floor) {
		floor = Math.max(1, Math.min(TOP, floor));
		Climb c = CLIMBER.get(p.getUUID());
		ServerLevel ladder = p.level().getServer().getLevel(DIMENSION);
		if (c == null || ladder == null) {
			begin(p, floor);
		} else {
			climb(ladder, c, floor);
		}
	}

	/** The floor a traveller is climbing, or 0. */
	public static int floorOf(ServerPlayer p) {
		Climb c = CLIMBER.get(p.getUUID());
		return c == null ? 0 : c.floor;
	}

	/** Ends the current wave at once (tests). */
	public static void clearWave(ServerPlayer p) {
		Climb c = CLIMBER.get(p.getUUID());
		ServerLevel ladder = p.level().getServer().getLevel(DIMENSION);
		if (c != null && ladder != null) {
			for (UUID id : c.foes) {
				if (ladder.getEntity(id) instanceof LivingEntity e) {
					e.kill(ladder);
				}
			}
		}
	}

	/** Where the climb's up-rung and chest stand on the current floor (tests). */
	public static @Nullable BlockPos upRung(ServerPlayer p) {
		Climb c = CLIMBER.get(p.getUUID());
		return c == null ? null : c.up;
	}

	public static @Nullable BlockPos chest(ServerPlayer p) {
		Climb c = CLIMBER.get(p.getUUID());
		return c == null ? null : c.chest;
	}

	public static String phaseOf(ServerPlayer p) {
		Climb c = CLIMBER.get(p.getUUID());
		return c == null ? "" : c.phase.name();
	}
}

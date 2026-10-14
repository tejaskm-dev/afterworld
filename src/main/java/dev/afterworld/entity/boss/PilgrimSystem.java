package dev.afterworld.entity.boss;

import dev.afterworld.AwConfig;
import dev.afterworld.edict.Edict;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.player.Rewards;
import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwItems;
import dev.afterworld.rumour.RumourEntry;
import dev.afterworld.rumour.RumourKind;
import dev.afterworld.site.Builds;
import dev.afterworld.site.Plan;
import dev.afterworld.site.Site;
import dev.afterworld.site.SiteBuilder;
import dev.afterworld.site.SiteInteractions;
import dev.afterworld.site.Sites;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Ground;
import dev.afterworld.util.Scheduler;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.BossStates;
import dev.afterworld.world.Chronicle;
import dev.afterworld.world.Faction;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the Pilgrim's walk going whether or not anyone is watching. Unobserved, it is three
 * numbers advancing along a loop; observed, it is a colossal statue gliding over the grass.
 */
public final class PilgrimSystem {
	private static PilgrimEntity live;
	private static int lonelyTicks;

	private PilgrimSystem() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 == 23) {
				tick(server);
			}
		});
		SiteInteractions.SHRINE_REPAIRED.add((player, site) -> shrineRepaired(player, site));
	}

	static BossStates.Pilgrim state(MinecraftServer server) {
		return AfterworldData.get(server).pilgrim;
	}

	public static boolean atPeace(ServerLevel level) {
		BossStates.Pilgrim s = state(level.getServer());
		return s.state == BossStates.Pilgrim.AT_PEACE || s.repairedMask == 0b1111;
	}

	private static Site shrine(AfterworldData data, int leg) {
		int id = data.pilgrim.shrines[Math.floorMod(leg, 4)];
		return data.sites.get(id);
	}

	private static void tick(MinecraftServer server) {
		AfterworldData data = AfterworldData.get(server);
		BossStates.Pilgrim s = data.pilgrim;
		if (s.state != BossStates.Pilgrim.WANDERING || !AwConfig.get().bossesEnabled) {
			if (live != null && !live.isRemoved()) {
				live.discard();
			}
			live = null;
			return;
		}
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null) {
			return;
		}
		if (live != null && (live.isRemoved() || !live.isAlive())) {
			if (!live.isAlive() && live.isRemoved() && live.getRemovalReason() == Entity.RemovalReason.KILLED) {
				live = null;
				return;
			}
			s.health = live.getHealth();
			live = null;
		}
		Site target = shrine(data, s.leg);
		if (target == null) {
			return;
		}
		if (live != null) {
			s.x = live.getX();
			s.y = live.getY();
			s.z = live.getZ();
			s.health = live.getHealth();
			live.setWaypoint(new Vec3(target.x + 0.5, 0, target.z + 2.5));
			boolean anyone = false;
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(live) < 160 * 160) {
					anyone = true;
					break;
				}
			}
			lonelyTicks = anyone ? 0 : lonelyTicks + 40;
			if (lonelyTicks > 1200 && !live.engaged()) {
				live.discard();
				live = null;
				s.entity = Optional.empty();
			}
			data.setDirty();
			return;
		}
		// Unobserved: advance along the road.
		long now = level.getGameTime();
		long elapsed = s.lastMoveTime == 0 ? 40 : Math.min(24000, now - s.lastMoveTime);
		s.lastMoveTime = now;
		double step = 0.12 * elapsed;
		double dx = target.x - s.x;
		double dz = target.z - s.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d <= step + 3) {
			s.x = target.x;
			s.z = target.z + 2.5;
			s.leg = (s.leg + 1) % 4;
		} else {
			s.x += dx / d * step;
			s.z += dz / d * step;
		}
		data.setDirty();
		for (ServerPlayer p : level.players()) {
			double px = p.getX() - s.x;
			double pz = p.getZ() - s.z;
			if (px * px + pz * pz < 96 * 96 && level.hasChunk((int) Math.floor(s.x) >> 4, (int) Math.floor(s.z) >> 4)) {
				spawn(level, data, s, target);
				break;
			}
		}
	}

	private static void spawn(ServerLevel level, AfterworldData data, BossStates.Pilgrim s, Site target) {
		BlockPos ground = Ground.surface(level, (int) Math.floor(s.x), (int) Math.floor(s.z));
		PilgrimEntity p = AwEntities.PILGRIM.create(level, EntitySpawnReason.EVENT);
		if (p == null) {
			return;
		}
		p.snapTo(s.x, ground.getY() + 0.5, s.z, 0, 0);
		if (s.health > 0) {
			p.setHealth(Math.min(p.getMaxHealth(), s.health));
		}
		p.setWaypoint(new Vec3(target.x + 0.5, 0, target.z + 2.5));
		level.addFreshEntity(p);
		live = p;
		lonelyTicks = 0;
		s.entity = Optional.of(p.getUUID());
		data.setDirty();
	}

	/** The Pilgrim reached the shrine it was walking toward. */
	static void arrived(PilgrimEntity pilgrim) {
		ServerLevel level = (ServerLevel) pilgrim.level();
		AfterworldData data = AfterworldData.get(level.getServer());
		BossStates.Pilgrim s = data.pilgrim;
		Site shrine = shrine(data, s.leg);
		boolean watched = false;
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(pilgrim) < 48 * 48) {
				watched = true;
				Progress.codex(p, "boss.pilgrim");
				PlayerState ps = PlayerState.of(p);
				for (RumourEntry r : ps.rumours) {
					if (r.active() && r.kind == RumourKind.PILGRIM && !r.clues.contains("You watched the Pilgrim open a shrine.")) {
						r.clues.add("You watched the Pilgrim open a shrine.");
					}
				}
			}
		}
		if (shrine != null && shrine.built() && watched && (shrine.flags & Sites.F_CACHE_REVEALED) == 0) {
			shrine.flags |= Sites.F_CACHE_REVEALED;
			SiteBuilder.revealShrineCache(level, shrine);
			Fx.burst(level, ParticleTypes.END_ROD, Vec3.atCenterOf(shrine.pos().above(2)), 30, 0.6, 0.08);
			Fx.sound(level, Vec3.atCenterOf(shrine.pos()), SoundEvents.CHEST_LOCKED, 1.0F, 0.6F);
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(pilgrim) < 48 * 48) {
					p.sendSystemMessage(Component.translatable("message.afterworld.pilgrim_opens", shrine.name).withColor(0xB5AE98));
				}
			}
		}
		if (s.repairedMask == 0b1111 && s.state == BossStates.Pilgrim.WANDERING) {
			makePeace(level, data, pilgrim);
			return;
		}
		s.leg = (s.leg + 1) % 4;
		Site next = shrine(data, s.leg);
		if (next != null) {
			pilgrim.setWaypoint(new Vec3(next.x + 0.5, 0, next.z + 2.5));
		}
		data.setDirty();
	}

	static void provoked(PilgrimEntity pilgrim, LivingEntity attacker) {
		if (attacker instanceof ServerPlayer p) {
			Progress.codex(p, "boss.pilgrim");
		}
	}

	private static void shrineRepaired(ServerPlayer player, Site site) {
		AfterworldData data = AfterworldData.get(player.level().getServer());
		BossStates.Pilgrim s = data.pilgrim;
		for (int i = 0; i < 4; i++) {
			if (s.shrines[i] == site.id) {
				s.repairedMask |= 1 << i;
			}
		}
		if (!s.helpers.contains(player.getUUID())) {
			s.helpers.add(player.getUUID());
		}
		int mended = Integer.bitCount(s.repairedMask);
		player.sendSystemMessage(Component.translatable("message.afterworld.shrines_mended", mended).withColor(0xB5AE98));
		data.setDirty();
	}

	private static void makePeace(ServerLevel level, AfterworldData data, PilgrimEntity pilgrim) {
		BossStates.Pilgrim s = data.pilgrim;
		s.state = BossStates.Pilgrim.AT_PEACE;
		data.setDirty();
		pilgrim.kneel();
		Fx.hostileSound(level, pilgrim.position(), SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.5F);
		Scheduler.after(80, () -> {
			BlockPos base = Ground.surface(level, (int) Math.floor(pilgrim.getX()), (int) Math.floor(pilgrim.getZ()));
			Plan statue = new Plan(base, 1L);
			statue.fill(-1, 0, -1, 1, 0, 1, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
			statue.fill(-1, 1, -1, 1, 3, 1, Blocks.STONE_BRICKS.defaultBlockState());
			statue.fill(-2, 4, -1, 2, 4, 1, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
			statue.set(0, 5, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			statue.set(0, 6, 0, Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState());
			for (int y = 1; y <= 5; y++) {
				statue.set(-2, y, 0, Blocks.SPRUCE_FENCE.defaultBlockState());
			}
			statue.set(-2, 6, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
			Builds.submit(level, statue, null);
			Fx.burst(level, ParticleTypes.END_ROD, pilgrim.position().add(0, 3, 0), 60, 1.5, 0.1);
			pilgrim.discard();
			live = null;
		});
		String names = "";
		for (var id : s.helpers) {
			Rewards.give(level.getServer(), id, Rewards.item(AwItems.fragment(Edict.TRESPASS), 1));
			Rewards.give(level.getServer(), id, Rewards.item(dev.afterworld.registry.AwGear.HEARTS.get(dev.afterworld.aspect.Aspect.FOOTSTEPS_OF_STONE), 1));
			Rewards.give(level.getServer(), id, "bond:pilgrim");
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				names = names.isEmpty() ? p.getName().getString() : names + ", " + p.getName().getString();
				Progress.codex(p, "boss.pilgrim.peace");
				Progress.rep(p, Faction.COURT, 10);
				Progress.legend(p, "milestone", "Helped the Pilgrim Without Feet finish its walk.");
			}
		}
		s.resolvedBy = names;
		Chronicle.record(level.getServer(), "The Pilgrim's Walk Ends", "With its four shrines mended, the Pilgrim Without Feet knelt and did not rise. "
			+ (names.isEmpty() ? "Nobody is sure who mended them." : "The shrines were mended by " + names + "."), "", Faction.COURT.ordinal(), names, "boss:pilgrim");
	}

	static void died(ServerLevel level, PilgrimEntity pilgrim, DamageSource source) {
		AfterworldData data = AfterworldData.get(level.getServer());
		data.pilgrim.state = BossStates.Pilgrim.DEFEATED;
		data.setDirty();
		live = null;
		drop(level, pilgrim, new ItemStack(AwItems.fragment(Edict.TRESPASS)));
		drop(level, pilgrim, new ItemStack(AwItems.PILGRIM_OFFERING, 3));
		String killer = source.getEntity() instanceof ServerPlayer p ? p.getName().getString() : "";
		if (source.getEntity() instanceof ServerPlayer p) {
			Progress.codex(p, "boss.pilgrim");
			Progress.legend(p, "milestone", "Brought down the Pilgrim Without Feet. Its shrines went cold.");
			Progress.rep(p, Faction.COURT, -10);
			Progress.rep(p, Faction.CROWN, 5);
		}
		Chronicle.record(level.getServer(), "The Pilgrim Falls", "The Pilgrim Without Feet was brought down " + (killer.isEmpty() ? "" : "by " + killer + " ")
			+ "far from its last shrine. The road it wore into the grass is already growing over.", "", -1, killer, "boss:pilgrim");
	}

	private static void drop(ServerLevel level, Entity at, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, at.getX(), at.getY() + 1.0, at.getZ(), stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}
}

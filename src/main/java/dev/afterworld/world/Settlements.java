package dev.afterworld.world;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.MobMark;
import dev.afterworld.util.Ground;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Villages as remembered places. Detection is a single structure lookup per player every five
 * seconds; consequences fire at most once per settlement per day.
 */
public final class Settlements {
	private Settlements() {
	}

	public static void init() {
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
			if (!(killed instanceof Enemy) || level.dimension() != Level.OVERWORLD) {
				return;
			}
			boolean byPlayer = killer instanceof ServerPlayer || MobMark.has(killer, MobMark.ECHO);
			if (!byPlayer) {
				return;
			}
			Settlement s = at(level, killed.blockPosition());
			if (s != null) {
				s.kills++;
				if (MobMark.has(killed, MobMark.RAIDER)) {
					s.kills += 3;
				}
				AfterworldData.get(level.getServer()).setDirty();
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof AbstractVillager || entity instanceof IronGolem) || !(entity.level() instanceof ServerLevel level)
				|| level.dimension() != Level.OVERWORLD || MobMark.has(entity, MobMark.GHOST)) {
				return;
			}
			Settlement s = at(level, entity.blockPosition());
			if (s == null) {
				return;
			}
			s.deaths++;
			AfterworldData.get(level.getServer()).setDirty();
			if (source.getEntity() instanceof ServerPlayer p) {
				Progress.rep(p, Faction.byOrdinal(s.patron), -6);
				Progress.rep(p, Faction.CROWN, -3);
				p.sendOverlayMessage(Component.translatable("message.afterworld.settlement_harm", s.name).withStyle(ChatFormatting.RED));
			}
		});
	}

	/** The settlement containing {@code pos}, if any. Creates its record on first sight. */
	public static Settlement at(ServerLevel level, BlockPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return null;
		}
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);
		if (start == null || !start.isValid()) {
			return null;
		}
		AfterworldData data = AfterworldData.get(level.getServer());
		long key = start.getChunkPos().pack();
		Settlement s = data.settlements.get(key);
		if (s == null) {
			s = create(level, data, start, key);
		}
		return s;
	}

	private static Settlement create(ServerLevel level, AfterworldData data, StructureStart start, long key) {
		RandomSource r = RandomSource.create(level.getSeed() ^ key * 31L);
		Settlement s = new Settlement();
		s.id = data.nextSettlementId++;
		s.key = key;
		s.name = uniqueName(data, r);
		BoundingBox box = start.getBoundingBox();
		BlockPos c = box.getCenter();
		s.x = c.getX();
		s.z = c.getZ();
		s.y = Ground.loaded(level, s.x, s.z) ? Ground.surface(level, s.x, s.z).getY() : c.getY();
		s.safety = 45 + r.nextInt(25);
		s.prosperity = 40 + r.nextInt(25);
		s.legitimacy = 50 + r.nextInt(25);
		s.anomaly = 5 + r.nextInt(15);
		float roll = r.nextFloat();
		s.patron = (roll < 0.6F ? Faction.CROWN : roll < 0.75F ? Faction.GUILD : roll < 0.9F ? Faction.COURT : Faction.BUREAU).ordinal();
		s.leader = Names.person(r);
		s.foundedDay = Progress.day(level.getServer());
		data.settlements.put(key, s);
		data.setDirty();
		return s;
	}

	private static String uniqueName(AfterworldData data, RandomSource r) {
		for (int i = 0; i < 12; i++) {
			String name = Names.place(r);
			boolean taken = false;
			for (Settlement s : data.settlements.values()) {
				if (s.name.equals(name)) {
					taken = true;
					break;
				}
			}
			if (!taken) {
				return name;
			}
		}
		return Names.place(r) + " " + (data.settlements.size() + 1);
	}

	/** Called for each player every five seconds. */
	public static void check(ServerPlayer p, PlayerState ps) {
		ServerLevel level = p.level();
		Settlement s = at(level, p.blockPosition());
		if (s == null) {
			if (ps.lastSettlementKey != 0) {
				ps.lastSettlementKey = 0;
			}
			return;
		}
		AfterworldData data = AfterworldData.get(level.getServer());
		long day = Progress.day(level.getServer());
		if (ps.lastSettlementKey != s.id) {
			ps.lastSettlementKey = s.id;
			enter(p, ps, s, day);
		}
		if (s.lastVisitDay != day) {
			s.lastVisitDay = day;
			data.setDirty();
		}
		consequences(p, ps, s, level, day, data);
	}

	private static void enter(ServerPlayer p, PlayerState ps, Settlement s, long day) {
		Faction patron = Faction.byOrdinal(s.patron);
		Component line = Component.literal(s.name).withColor(0xE8DCC0)
			.append(Component.literal("  ·  " + s.safetyWord() + "  ·  " + s.prosperityWord()).withColor(0xA8A08C))
			.append(Component.literal("  ·  ").withColor(0x6C665A))
			.append(patron.displayName().copy().withColor(patron.color));
		if (s.state == Settlement.ABANDONED) {
			line = Component.literal(s.name + " · abandoned").withColor(0x8C8578);
		}
		p.sendOverlayMessage(line);
		if (Progress.codex(p, "place.settlement")) {
			Progress.legend(p, "discovery", "Reached " + s.name + ", where " + s.leader + " keeps the peace.");
		}
		if (patron != null && ps.rep(patron) >= 40 && s.prosperity >= 55 && s.state != Settlement.ABANDONED) {
			p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 6000, 0, true, true));
			p.sendSystemMessage(Component.translatable("message.afterworld.settlement_welcome", s.name).withColor(0xC9A227));
		}
	}

	private static void consequences(ServerPlayer p, PlayerState ps, Settlement s, ServerLevel level, long day, AfterworldData data) {
		RandomSource r = level.getRandom();
		if (s.safety >= 70 && day >= s.lastGolemDay + 3 && s.state != Settlement.ABANDONED) {
			int golems = level.getEntitiesOfClass(IronGolem.class, p.getBoundingBox().inflate(48)).size();
			if (golems < 2) {
				s.lastGolemDay = day;
				data.setDirty();
				IronGolem g = Ground.spawn(level, EntityTypes.IRON_GOLEM, s.x + r.nextInt(9) - 4, s.z + r.nextInt(9) - 4, EntitySpawnReason.EVENT);
				if (g != null) {
					g.setPlayerCreated(false);
					p.sendOverlayMessage(Component.translatable("message.afterworld.settlement_guard", s.name).withColor(0xA8A08C));
				}
			}
		}
		if (s.safety < 35 && level.isDarkOutside() && day > s.lastRaidDay) {
			s.lastRaidDay = day;
			data.setDirty();
			int count = 2 + (s.safety < 20 ? 2 : 0);
			EntityType<? extends Mob> type = s.safety < 20 ? EntityTypes.PILLAGER : EntityTypes.ZOMBIE;
			double a = r.nextDouble() * Math.PI * 2;
			for (int i = 0; i < count; i++) {
				int x = (int) (p.getX() + Math.cos(a) * (26 + r.nextInt(8))) + r.nextInt(5) - 2;
				int z = (int) (p.getZ() + Math.sin(a) * (26 + r.nextInt(8))) + r.nextInt(5) - 2;
				Mob m = Ground.spawn(level, type, x, z, EntitySpawnReason.EVENT);
				if (m != null) {
					MobMark.set(m, MobMark.of(m).with(MobMark.RAIDER));
					m.setPersistenceRequired();
				}
			}
			p.sendSystemMessage(Component.translatable("message.afterworld.settlement_raid", s.name).withColor(0xC4553B));
		}
	}

	/** A villager traded: prosperity and the patron's goodwill rise a little. */
	public static void onTrade(AbstractVillager villager, ServerPlayer player) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		Settlement s = at(level, villager.blockPosition());
		if (s == null) {
			return;
		}
		s.trades++;
		AfterworldData.get(level.getServer()).setDirty();
		if (player != null && level.getRandom().nextInt(4) == 0) {
			Progress.rep(player, Faction.byOrdinal(s.patron), 1);
		}
	}

	public static Settlement nearest(AfterworldData data, String dim, double x, double z, double maxDist) {
		Settlement best = null;
		double bestD = maxDist * maxDist;
		for (Settlement s : data.settlements.values()) {
			if (!s.dim.equals(dim)) {
				continue;
			}
			double dx = s.x - x;
			double dz = s.z - z;
			double d = dx * dx + dz * dz;
			if (d < bestD) {
				bestD = d;
				best = s;
			}
		}
		return best;
	}
}

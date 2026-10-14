package dev.afterworld.site;

import dev.afterworld.block.AwBlockHooks;
import dev.afterworld.block.ShrineStoneBlock;
import dev.afterworld.edict.AwTargeting;
import dev.afterworld.edict.Edict;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwItems;
import dev.afterworld.registry.MobMark;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Ground;
import dev.afterworld.util.Inv;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.Chronicle;
import dev.afterworld.world.Faction;
import dev.afterworld.world.Names;
import dev.afterworld.world.Settlement;
import dev.afterworld.world.Settlements;
import dev.afterworld.world.WorldEvent;
import dev.afterworld.world.WorldSim;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** What happens at each kind of site: guardians, beasts, caravans, shrines and anomaly cores. */
public final class SiteInteractions {
	public static final List<BiConsumer<ServerPlayer, Site>> SHRINE_REPAIRED = new ArrayList<>();
	private static final String[] BEASTS = {"Gnawbone", "The Grey Mother", "Old Rattle", "The Marked Woodsman"};

	private SiteInteractions() {
	}

	static void init() {
		Sites.onPresence(SiteKind.ANOMALY, SiteInteractions::anomalyPresence);
		Sites.onPresence(SiteKind.LAIR, SiteInteractions::lairPresence);
		Sites.onPresence(SiteKind.CACHE, SiteInteractions::cachePresence);
		Sites.onPresence(SiteKind.CARAVAN, SiteInteractions::caravanPresence);
		Sites.onPresence(SiteKind.ROADSTOP, SiteInteractions::roadstopPresence);
		Sites.onPresence(SiteKind.SHRINE, (p, s, site, d2) -> {
			if (d2 < 64) {
				Progress.codex(p, "place.shrine");
			}
		});
		AwBlockHooks.shrineUse = SiteInteractions::shrineUse;
		AwBlockHooks.anomalyUse = SiteInteractions::anomalyUse;
		AwBlockHooks.anomalyBroken = SiteInteractions::anomalyBroken;
		ServerLivingEntityEvents.AFTER_DEATH.register(SiteInteractions::afterDeath);
	}

	// ------------------------------------------------------------------ anomalies

	private static void anomalyPresence(ServerPlayer p, PlayerState s, Site site, double d2) {
		if (!site.open() || site.state != Site.BUILT) {
			return;
		}
		ServerLevel level = p.level();
		AfterworldData data = AfterworldData.get(level.getServer());
		if ((site.flags & Sites.F_GUARDS) == 0 && d2 < 26 * 26) {
			site.flags |= Sites.F_GUARDS;
			data.setDirty();
			spawnTouched(level, site, 2 + level.getRandom().nextInt(2));
			p.sendOverlayMessage(Component.translatable("message.afterworld.anomaly_near", site.name).withColor(0xB79CE8));
			Progress.codex(p, "place.anomaly");
		}
		if (d2 > 11 * 11) {
			return;
		}
		Edict e = Edict.byOrdinal(site.data);
		if (e == null) {
			return;
		}
		long now = level.getGameTime();
		switch (e) {
			case WEIGHTLESSNESS -> {
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 0, true, false));
				p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 50, 2, true, false));
			}
			case ECHOES -> {
				s.echoWindowUntil = Math.max(s.echoWindowUntil, now + 50);
				s.hudDirty = true;
			}
			case WITNESS -> {
				for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), x -> x != p)) {
					le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, true, false));
				}
			}
			case FORGOTTEN, HOSPITALITY -> AwTargeting.forget(p.getUUID(), now + 50);
			case HUNGER -> {
				s.hungerUntil = Math.max(s.hungerUntil, now + 50);
				s.hudDirty = true;
			}
			case STILLNESS -> {
				for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12), x -> x instanceof Monster)) {
					m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1, true, false));
				}
			}
			case RETURN -> {
				for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(10), i -> i.getOwner() == p && i.getAge() > 40)) {
					item.setPos(p.getX(), p.getY() + 0.3, p.getZ());
				}
			}
			default -> {
			}
		}
		Progress.codex(p, "anomaly." + e.id);
	}

	private static void spawnTouched(ServerLevel level, Site site, int count) {
		RandomSource r = level.getRandom();
		EntityType<?>[] types = {EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.HUSK};
		for (int i = 0; i < count; i++) {
			EntityType<?> type = types[r.nextInt(types.length)];
			Entity e = Ground.spawn(level, type, site.x + r.nextInt(13) - 6, site.z + r.nextInt(13) - 6, EntitySpawnReason.EVENT);
			if (e instanceof Mob m) {
				MobMark.set(m, MobMark.of(m).with(MobMark.TOUCHED).withData(site.id));
				m.setPersistenceRequired();
				m.setCustomName(Component.translatable("entity.afterworld.rule_touched", m.getType().getDescription()));
				m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
				Fx.burst(level, ParticleTypes.REVERSE_PORTAL, m.position().add(0, 1, 0), 20, 0.4, 0.05);
			}
		}
	}

	private static boolean anomalyUse(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, ServerPlayer p, ItemStack stack) {
		Site site = Sites.at(level.getServer(), level.dimension().identifier().toString(), pos, SiteKind.ANOMALY);
		if (site == null || !site.open()) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.anomaly_inert").withColor(0x9A9488));
			return true;
		}
		if (!stack.is(Items.GLASS_BOTTLE)) {
			Edict e = Edict.byOrdinal(site.data);
			p.sendSystemMessage(Component.translatable("message.afterworld.anomaly_hint", e == null ? Component.literal("?") : e.displayName().copy().withColor(e.color))
				.withStyle(ChatFormatting.GRAY));
			Progress.codex(p, "anomaly.choice");
			return true;
		}
		// Tapping: exploit the anomaly for residue. It grows, and the Bureau notices.
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		AfterworldData data = AfterworldData.get(level.getServer());
		int taps = (site.flags >> 16) & 0xFF;
		taps++;
		site.flags = (site.flags & 0xFFFF) | (taps << 16) | Sites.F_TAPPED;
		data.setDirty();
		Inv.give(p, new ItemStack(AwItems.ANOMALY_RESIDUE, 2 + level.getRandom().nextInt(2)));
		Fx.burst(level, ParticleTypes.REVERSE_PORTAL, Vec3.atCenterOf(pos), 30, 0.4, 0.1);
		Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.BOTTLE_FILL_DRAGONBREATH, 1.0F, 0.8F);
		Progress.rep(p, Faction.BUREAU, -2);
		Progress.rep(p, Faction.GUILD, 1);
		Settlement near = Settlements.nearest(data, site.dim, site.x, site.z, 600);
		if (near != null) {
			near.anomaly += 4;
		}
		Progress.codex(p, "anomaly.tapped");
		if (taps >= 4 && (site.flags & Sites.F_CACHE_REVEALED) == 0) {
			site.flags |= Sites.F_CACHE_REVEALED;
			Edict e = Edict.byOrdinal(site.data);
			if (e != null) {
				Inv.give(p, new ItemStack(AwItems.fragment(e)));
			}
			spawnTouched(level, site, 3);
			if (near != null) {
				near.anomaly += 12;
			}
			Progress.rep(p, Faction.BUREAU, -8);
			Chronicle.record(level.getServer(), "The " + site.name.replace("The ", "") + " Spills Over",
				p.getName().getString() + " drew too deeply from " + site.name + ". The rule there is spreading.", site.name, Faction.BUREAU.ordinal(),
				p.getName().getString(), "site:" + site.id);
			Progress.legend(p, "choice", "Exploited " + site.name + " until it spilled over, and took its Edict.");
			p.sendSystemMessage(Component.translatable("message.afterworld.anomaly_spill").withColor(0xC4553B));
		}
		return true;
	}

	private static void anomalyBroken(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, ServerPlayer p) {
		Site site = Sites.at(level.getServer(), level.dimension().identifier().toString(), pos, SiteKind.ANOMALY);
		if (site == null || !site.open()) {
			return;
		}
		AfterworldData data = AfterworldData.get(level.getServer());
		Edict e = Edict.byOrdinal(site.data);
		boolean spilled = (site.flags & Sites.F_CACHE_REVEALED) != 0;
		if (e != null && !spilled) {
			Inv.give(p, new ItemStack(AwItems.fragment(e)));
		}
		Inv.give(p, new ItemStack(AwItems.ANOMALY_RESIDUE, 2 + level.getRandom().nextInt(3)));
		p.giveExperiencePoints(60);
		Fx.burst(level, ParticleTypes.EXPLOSION, Vec3.atCenterOf(pos), 1, 0, 0);
		Fx.burst(level, ParticleTypes.END_ROD, Vec3.atCenterOf(pos), 40, 0.6, 0.15);
		Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.BEACON_DEACTIVATE, 1.2F, 0.6F);
		Settlement near = Settlements.nearest(data, site.dim, site.x, site.z, 600);
		if (near != null) {
			near.anomaly -= 20;
			near.helped++;
			near.clamp();
		}
		Progress.rep(p, Faction.BUREAU, 8);
		Progress.rep(p, Faction.GUILD, 2);
		Progress.codex(p, "anomaly.sealed");
		Progress.legend(p, "choice", "Sealed " + site.name + (e != null ? " and kept its Edict of " + e.displayName().getString() : "") + ".");
		Chronicle.record(level.getServer(), "The Sealing of " + site.name.replace("The ", ""),
			p.getName().getString() + " sealed the anomaly called " + site.name + (near != null ? " near " + near.name : "") + ".",
			site.name, Faction.BUREAU.ordinal(), p.getName().getString(), "site:" + site.id);
		Sites.resolve(p, site, "Sealed by " + p.getName().getString() + ".");
	}

	// ------------------------------------------------------------------ lairs

	private static void lairPresence(ServerPlayer p, PlayerState s, Site site, double d2) {
		if (!site.open() || site.state != Site.BUILT || (site.flags & Sites.F_GUARDS) != 0 || d2 > 40 * 40) {
			return;
		}
		ServerLevel level = p.level();
		site.flags |= Sites.F_GUARDS;
		AfterworldData.get(level.getServer()).setDirty();
		int kind = Math.floorMod((int) site.seed, BEASTS.length);
		EntityType<? extends Mob> type = switch (kind) {
			case 1 -> EntityTypes.SPIDER;
			case 2 -> EntityTypes.SKELETON;
			case 3 -> EntityTypes.VINDICATOR;
			default -> EntityTypes.ZOMBIE;
		};
		Mob beast = Ground.spawn(level, type, site.x, site.z, EntitySpawnReason.EVENT);
		if (beast == null) {
			site.flags &= ~Sites.F_GUARDS;
			return;
		}
		beast.setCustomName(Component.literal(BEASTS[kind]).withColor(0xC4553B));
		beast.setCustomNameVisible(true);
		beast.setPersistenceRequired();
		boost(beast, 3.5, 4.0, 0.15);
		beast.setHealth(beast.getMaxHealth());
		MobMark.set(beast, MobMark.of(beast).with(MobMark.MARKED_BEAST).withData(site.id));
		Fx.sound(level, beast.position(), SoundEvents.RAVAGER_ROAR, 1.0F, 0.8F);
		p.sendOverlayMessage(Component.translatable("message.afterworld.beast_near", BEASTS[kind]).withColor(0xC4553B));
		Progress.codex(p, "place.lair");
	}

	static void boost(Mob m, double healthMult, double damageAdd, double speedMult) {
		var hp = m.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.addOrReplacePermanentModifier(new AttributeModifier(dev.afterworld.Afterworld.id("marked_health"), healthMult - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		var dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
		if (dmg != null) {
			dmg.addOrReplacePermanentModifier(new AttributeModifier(dev.afterworld.Afterworld.id("marked_damage"), damageAdd, AttributeModifier.Operation.ADD_VALUE));
		}
		var spd = m.getAttribute(Attributes.MOVEMENT_SPEED);
		if (spd != null) {
			spd.addOrReplacePermanentModifier(new AttributeModifier(dev.afterworld.Afterworld.id("marked_speed"), speedMult, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		var kb = m.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) {
			kb.addOrReplacePermanentModifier(new AttributeModifier(dev.afterworld.Afterworld.id("marked_kb"), 0.6, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	// ------------------------------------------------------------------ caches

	private static void cachePresence(ServerPlayer p, PlayerState s, Site site, double d2) {
		if (!site.open() || site.state != Site.BUILT) {
			return;
		}
		BlockPos chest = site.pos().offset(-2, -3, -2);
		if (p.blockPosition().distSqr(chest) <= 16) {
			Progress.codex(p, "place.cache");
			Progress.rep(p, Faction.GUILD, 3);
			Progress.legend(p, "discovery", "Found " + site.name + ".");
			Sites.resolve(p, site, "Found by " + p.getName().getString() + ".");
		}
	}

	// ------------------------------------------------------------------ caravans

	private static void caravanPresence(ServerPlayer p, PlayerState s, Site site, double d2) {
		if (!site.open() || site.state != Site.BUILT) {
			return;
		}
		ServerLevel level = p.level();
		AfterworldData data = AfterworldData.get(level.getServer());
		if ((site.flags & Sites.F_GUARDS) == 0) {
			if (d2 > 36 * 36) {
				return;
			}
			site.flags |= Sites.F_GUARDS;
			data.setDirty();
			RandomSource r = level.getRandom();
			WanderingTrader master = Ground.spawn(level, EntityTypes.WANDERING_TRADER, site.x - 1, site.z + 1, EntitySpawnReason.EVENT);
			if (master == null) {
				site.flags &= ~Sites.F_GUARDS;
				return;
			}
			master.setCustomName(Component.literal("Caravan Master " + Names.first(r)));
			master.setDespawnDelay(48000);
			master.setPersistenceRequired();
			MobMark.set(master, MobMark.of(master).with(MobMark.ESCORT).withData(site.id));
			for (int i = 0; i < 2; i++) {
				Ground.spawn(level, EntityTypes.TRADER_LLAMA, site.x + 2 + i, site.z - 2, EntitySpawnReason.EVENT);
			}
			boolean night = level.isDarkOutside();
			EntityType<? extends Mob>[] types = night ? new EntityType[] {EntityTypes.ZOMBIE, EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.VINDICATOR}
				: new EntityType[] {EntityTypes.PILLAGER, EntityTypes.PILLAGER, EntityTypes.VINDICATOR};
			for (EntityType<? extends Mob> type : types) {
				double a = r.nextDouble() * Math.PI * 2;
				Mob raider = Ground.spawn(level, type, site.x + (int) (Math.cos(a) * 10), site.z + (int) (Math.sin(a) * 10), EntitySpawnReason.EVENT);
				if (raider != null) {
					raider.setPersistenceRequired();
					MobMark.set(raider, MobMark.of(raider).with(MobMark.RAIDER).withData(site.id));
					raider.setTarget(master);
				}
			}
			p.sendSystemMessage(Component.translatable("message.afterworld.caravan_attack").withColor(0xC4553B));
			Fx.sound(level, p.position(), SoundEvents.RAID_HORN.value(), 0.6F, 1.1F);
			return;
		}
		AABB area = new AABB(site.x - 64, site.pos().getY() - 24, site.z - 64, site.x + 64, site.pos().getY() + 24, site.z + 64);
		Mob master = null;
		int raiders = 0;
		for (Mob m : level.getEntitiesOfClass(Mob.class, area, m -> MobMark.of(m).data() == site.id)) {
			MobMark mark = MobMark.of(m);
			if (mark.has(MobMark.ESCORT) && m.isAlive()) {
				master = m;
			} else if (mark.has(MobMark.RAIDER) && m.isAlive()) {
				raiders++;
				if (m.getTarget() == null && master != null) {
					m.setTarget(master);
				}
			}
		}
		if (master == null) {
			site.state = Site.FAILED;
			site.outcome = "The caravan master fell on the road.";
			data.setDirty();
			endCaravanEvent(level.getServer(), data, site, false);
			p.sendSystemMessage(Component.translatable("message.afterworld.caravan_lost").withColor(0x9A9488));
			Progress.legend(p, "milestone", "Arrived too late to save the Silent Caravan.");
			return;
		}
		if (raiders == 0) {
			Settlement st = data.settlementById(site.settlement);
			if (st != null) {
				st.safety += 15;
				st.prosperity += 10;
				st.helped++;
				st.clamp();
			}
			Inv.give(p, new ItemStack(Items.EMERALD, 6 + level.getRandom().nextInt(6)));
			if (level.getRandom().nextFloat() < 0.35F) {
				Inv.give(p, new ItemStack(AwItems.fragment(WorldSim.anomalyEdict(level.getRandom()))));
			}
			Progress.rep(p, Faction.CROWN, 10);
			Progress.codex(p, "event.silent_caravan");
			Progress.legend(p, "milestone", "Saved the Silent Caravan bound for " + (st == null ? "the road" : st.name) + ".");
			master.setCustomName(Component.literal(master.getCustomName() == null ? "Caravan Master" : master.getCustomName().getString()).append(" (grateful)"));
			Chronicle.record(level.getServer(), "The Caravan Arrives", p.getName().getString() + " drove off the raiders on the road to "
				+ (st == null ? "the village" : st.name) + ". The caravan arrived a day late and full of stories.", st == null ? "" : st.name, Faction.CROWN.ordinal(), p.getName().getString(), "site:" + site.id);
			Sites.resolve(p, site, "Defended by " + p.getName().getString() + ".");
			endCaravanEvent(level.getServer(), data, site, true);
		}
	}

	private static void endCaravanEvent(MinecraftServer server, AfterworldData data, Site site, boolean success) {
		for (WorldEvent e : data.events) {
			if (e.active && e.kind.equals(WorldEvent.SILENT_CARAVAN) && e.site == site.id) {
				WorldSim.endEvent(server, data, e, Progress.day(server), success);
			}
		}
	}

	// ------------------------------------------------------------------ the road's last stop

	private static void roadstopPresence(ServerPlayer p, PlayerState s, Site site, double d2) {
		if (!site.open() || site.state != Site.BUILT || (site.flags & Sites.F_GUARDS) != 0 || d2 > 40 * 40) {
			return;
		}
		ServerLevel level = p.level();
		site.flags |= Sites.F_GUARDS;
		AfterworldData.get(level.getServer()).setDirty();
		WanderingTrader roadwright = Ground.spawn(level, EntityTypes.WANDERING_TRADER, site.x + 1, site.z, EntitySpawnReason.EVENT);
		if (roadwright == null) {
			site.flags &= ~Sites.F_GUARDS;
			return;
		}
		roadwright.setCustomName(Component.literal("The Roadwright").withColor(0xE8A33D));
		roadwright.setCustomNameVisible(true);
		roadwright.setDespawnDelay(72000);
		MobMark.set(roadwright, MobMark.of(roadwright).with(MobMark.ROADWRIGHT).withData(site.id));
		var offers = roadwright.getOffers();
		offers.clear();
		RandomSource r = RandomSource.create(site.seed);
		Edict a = WorldSim.anomalyEdict(r);
		Edict b = Edict.VALUES[r.nextInt(Edict.COUNT)];
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 18), Optional.of(new ItemCost(AwItems.ANOMALY_RESIDUE, 4)), new ItemStack(AwItems.fragment(a)), 1, 30, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 24), Optional.of(new ItemCost(AwItems.CLOCKWORK_FRAGMENT, 2)), new ItemStack(AwItems.fragment(b)), 1, 30, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(AwItems.CHRONICLE_PAGE, 2), 6, 5, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(AwItems.PILGRIM_OFFERING, 1), 4, 5, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(AwItems.ANOMALY_RESIDUE, 6), new ItemStack(Items.EMERALD, 5), 8, 5, 0.05F));
		Fx.sound(level, roadwright.position(), SoundEvents.WANDERING_TRADER_REAPPEARED, 1.0F, 0.8F);
		p.sendSystemMessage(Component.translatable("message.afterworld.roadwright").withColor(0xE8A33D));
		Progress.codex(p, "npc.roadwright");
		Sites.resolve(p, site, "Met by " + p.getName().getString() + ".");
	}

	// ------------------------------------------------------------------ shrines

	private static boolean shrineUse(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, ServerPlayer p, ItemStack stack) {
		Site site = Sites.at(level.getServer(), level.dimension().identifier().toString(), pos, SiteKind.SHRINE);
		if (site == null) {
			return false;
		}
		if (state.getValue(ShrineStoneBlock.REPAIRED)) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.shrine_whole").withColor(0xA8A08C));
			return true;
		}
		if (!stack.is(AwItems.PILGRIM_OFFERING)) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.shrine_cracked").withColor(0xA8A08C));
			Progress.codex(p, "place.shrine");
			return true;
		}
		if (!p.isCreative()) {
			stack.shrink(1);
		}
		level.setBlock(pos, state.setValue(ShrineStoneBlock.REPAIRED, true), 3);
		site.flags |= Sites.F_REPAIRED;
		AfterworldData data = AfterworldData.get(level.getServer());
		data.setDirty();
		Fx.burst(level, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(pos), 20, 0.6, 0.1);
		Fx.burst(level, ParticleTypes.END_ROD, Vec3.atCenterOf(pos).add(0, 1, 0), 12, 0.2, 0.05);
		Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 0.9F);
		Progress.codex(p, "pilgrim.shrine_repaired");
		Progress.rep(p, Faction.COURT, 3);
		Progress.legend(p, "choice", "Mended " + site.name + ".");
		for (BiConsumer<ServerPlayer, Site> l : SHRINE_REPAIRED) {
			l.accept(p, site);
		}
		return true;
	}

	// ------------------------------------------------------------------ deaths

	private static void afterDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		MobMark mark = entity.getAttached(dev.afterworld.registry.AwAttachments.MOB);
		if (mark == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (mark.has(MobMark.TOUCHED)) {
			ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), new ItemStack(AwItems.ANOMALY_RESIDUE, 1 + level.getRandom().nextInt(2)));
			level.addFreshEntity(drop);
		}
		if (mark.has(MobMark.MARKED_BEAST)) {
			AfterworldData data = AfterworldData.get(level.getServer());
			Site site = data.sites.get(mark.data());
			ServerPlayer killer = source.getEntity() instanceof ServerPlayer sp ? sp : null;
			if (site != null && site.open()) {
				Settlement st = data.settlementById(site.settlement);
				if (st != null) {
					st.safety += 15;
					st.helped++;
					st.clamp();
				}
				String name = entity.getCustomName() == null ? "the beast" : entity.getCustomName().getString();
				if (killer != null) {
					Inv.give(killer, new ItemStack(Items.EMERALD, 3 + level.getRandom().nextInt(4)));
					if (level.getRandom().nextFloat() < 0.3F) {
						Inv.give(killer, new ItemStack(AwItems.fragment(WorldSim.anomalyEdict(level.getRandom()))));
					}
					killer.giveExperiencePoints(80);
					Progress.rep(killer, Faction.CROWN, 8);
					Progress.codex(killer, "place.lair");
					Progress.legend(killer, "milestone", "Slew " + name + (st == null ? "" : ", which had been hunting near " + st.name) + ".");
					Chronicle.record(level.getServer(), "The End of " + name, killer.getName().getString() + " slew " + name
						+ (st == null ? "." : ". The herds of " + st.name + " sleep easier."), st == null ? "" : st.name, Faction.CROWN.ordinal(), killer.getName().getString(), "site:" + site.id);
					Sites.resolve(killer, site, "Slain by " + killer.getName().getString() + ".");
				} else {
					Sites.resolveByFaction(level.getServer(), site, null, name + " died, and nobody saw how.");
				}
			}
		}
	}
}

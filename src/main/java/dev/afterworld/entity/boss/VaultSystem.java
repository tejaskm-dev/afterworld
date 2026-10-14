package dev.afterworld.entity.boss;

import dev.afterworld.AwConfig;
import dev.afterworld.block.AwBlockHooks;
import dev.afterworld.edict.Edict;
import dev.afterworld.edict.EdictSystem;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwItems;
import dev.afterworld.registry.MobMark;
import dev.afterworld.site.Builds;
import dev.afterworld.site.Plan;
import dev.afterworld.site.Site;
import dev.afterworld.site.SiteBuilder;
import dev.afterworld.site.SiteKind;
import dev.afterworld.site.Sites;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Inv;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.BossStates;
import dev.afterworld.world.Chronicle;
import dev.afterworld.world.Faction;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Vault of Seconds: an adaptive dungeon. It watches how you fight, rebuilds its wardens to
 * answer, remembers which wing you took, and says so plainly when it changes.
 */
public final class VaultSystem {
	public static final int A_MIRRORED = 1;
	public static final int A_SPIKED = 1 << 1;
	public static final int A_ANCHORED = 1 << 2;
	public static final int A_WARDED = 1 << 3;
	public static final int A_LISTENING = 1 << 4;
	public static final int A_EXPECTED = 1 << 5;
	private static final String[] ADAPT_IDS = {"mirrored", "spiked", "anchored", "warded", "listening", "expected"};

	private static final class Visit {
		long day = -1;
		final boolean[] wing = new boolean[2];
		int firstWing = -1;
		int expectedWing = -1;
		long lastBell;
		final Map<UUID, int[]> snapshots = new HashMap<>();
	}

	private static final Int2ObjectOpenHashMap<Visit> VISITS = new Int2ObjectOpenHashMap<>();
	private static KingEntity king;

	private VaultSystem() {
	}

	public static void init() {
		Sites.onPresence(SiteKind.VAULT, VaultSystem::presence);
		AwBlockHooks.escapementBroken = (level, pos, state, player) -> {
			if (king != null && !king.isRemoved() && king.level() == level) {
				king.stagger(100);
				for (ServerPlayer p : level.players()) {
					if (p.distanceToSqr(king) < 48 * 48) {
						p.sendSystemMessage(Component.translatable("message.afterworld.king_staggers").withColor(0xE8D7A0));
					}
				}
			}
		};
		EdictSystem.DAMAGE_SCALERS.add((target, source, damage) -> target instanceof KingEntity k ? damage * (1.0F - 0.15F * k.intact) : damage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(VaultSystem::reflect);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			MobMark mark = victim.getAttached(dev.afterworld.registry.AwAttachments.MOB);
			if (mark != null && mark.has(MobMark.SPIKED) && source.getEntity() instanceof ServerPlayer p && source.getDirectEntity() == p && taken > 0) {
				ServerLevel level = p.level();
				p.hurtServer(level, level.damageSources().thorns(victim), 2.0F + taken * 0.25F);
				Fx.burst(level, ParticleTypes.CRIT, p.position().add(0, 1, 0), 6, 0.3, 0.1);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (MobMark.has(entity, MobMark.VAULT_MINION) && entity.level() instanceof ServerLevel level && level.getRandom().nextFloat() < 0.3F) {
				level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), new ItemStack(AwItems.CLOCKWORK_FRAGMENT)));
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof KingEntity k) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (k.bargaining() && player.isShiftKeyDown() && player.getMainHandItem().is(Items.CLOCK)) {
				bargain(sp, k);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
	}

	static BossStates.Vault state(ServerLevel level) {
		return AfterworldData.get(level.getServer()).vault;
	}

	// ------------------------------------------------------------------ presence

	private static void presence(ServerPlayer p, PlayerState ps, Site site, double d2) {
		ServerLevel level = p.level();
		BlockPos o = site.pos();
		int dx = p.getBlockX() - o.getX();
		int dy = p.getBlockY() - (o.getY() - SiteBuilder.depth(site));
		int dz = p.getBlockZ() - o.getZ();
		if (dy < -1 || dy > 10) {
			return;
		}
		Visit v = VISITS.computeIfAbsent(site.id, k -> new Visit());
		long day = Progress.day(level.getServer());
		if (Math.abs(dx) <= 6 && Math.abs(dz) <= 6) {
			if (v.day != day) {
				startVisit(level, site, v, day);
			}
		}
		int wing = dx <= -16 && dx >= -28 && Math.abs(dz) <= 6 ? 0 : dx >= 16 && dx <= 28 && Math.abs(dz) <= 6 ? 1 : -1;
		if (wing >= 0 && v.day == day && !v.wing[wing]) {
			v.wing[wing] = true;
			if (v.firstWing < 0) {
				v.firstWing = wing;
				BossStates.Vault s = state(level);
				s.wingFirst[wing]++;
				s.lastWing = wing;
				AfterworldData.get(level.getServer()).setDirty();
			}
			spawnWave(level, site, wing, v);
		}
		if (wing >= 0 && p.isShiftKeyDown() && (state(level).adaptations & A_LISTENING) != 0 && level.getGameTime() - v.lastBell > 100) {
			v.lastBell = level.getGameTime();
			Fx.sound(level, p.position(), SoundEvents.BELL_BLOCK, 1.5F, 1.4F);
			for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(14), m -> MobMark.has(m, MobMark.VAULT_MINION))) {
				m.setTarget(p);
			}
			p.sendOverlayMessage(Component.translatable("message.afterworld.vault_bell").withColor(0xC4553B));
		}
		if ((site.flags & Sites.F_GATE_OPEN) == 0 && lever(level, SiteBuilder.vaultLever(site, true)) && lever(level, SiteBuilder.vaultLever(site, false))) {
			site.flags |= Sites.F_GATE_OPEN;
			AfterworldData.get(level.getServer()).setDirty();
			BlockPos g = SiteBuilder.vaultGate(site);
			for (int x = -1; x <= 1; x++) {
				for (int y = 0; y <= 2; y++) {
					level.setBlock(g.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 3);
				}
			}
			Fx.sound(level, Vec3.atCenterOf(g), SoundEvents.IRON_DOOR_OPEN, 2.0F, 0.5F);
			p.sendSystemMessage(Component.translatable("message.afterworld.vault_gate").withColor(0xE8D7A0));
		}
		boolean throne = Math.abs(dx) <= 10 && dz >= -36 && dz <= -17;
		if (throne) {
			throne(level, site, p);
		}
	}

	private static boolean lever(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.is(Blocks.LEVER) && s.getValue(LeverBlock.POWERED);
	}

	private static void startVisit(ServerLevel level, Site site, Visit v, long day) {
		BossStates.Vault s = state(level);
		v.day = day;
		v.wing[0] = v.wing[1] = false;
		v.expectedWing = s.lastWing;
		v.firstWing = -1;
		s.visits++;
		s.lastVisitDay = day;
		for (int i = 0; i < s.observed.length; i++) {
			s.observed[i] = (int) (s.observed[i] * 0.7);
		}
		java.util.List<ServerPlayer> present = level.getEntitiesOfClass(ServerPlayer.class, new AABB(site.pos()).inflate(60, 40, 60));
		for (ServerPlayer p : present) {
			PlayerState ps = PlayerState.of(p);
			int[] prev = v.snapshots.get(p.getUUID());
			if (prev != null) {
				for (int i = 0; i < Math.min(s.observed.length, ps.tactics.length); i++) {
					s.observed[i] += Math.max(0, ps.tactics[i] - prev[i]);
				}
			} else {
				// First time this traveller enters: the Vault has heard stories about them.
				for (int i = 0; i < Math.min(s.observed.length, ps.tactics.length); i++) {
					s.observed[i] += ps.tactics[i] / 4;
				}
			}
			v.snapshots.put(p.getUUID(), ps.tactics.clone());
			Progress.codex(p, "vault.entered");
			Progress.codex(p, "place.vault");
		}
		int before = s.adaptations;
		s.adaptations = adapt(s);
		AfterworldData.get(level.getServer()).setDirty();
		int gained = s.adaptations & ~before;
		for (int bit = 0; bit < ADAPT_IDS.length; bit++) {
			if ((gained & (1 << bit)) != 0) {
				for (ServerPlayer p : present) {
					p.sendSystemMessage(Component.translatable("message.afterworld.vault_adapt." + ADAPT_IDS[bit]).withColor(0xC4553B));
					Progress.notice(p, AwPayloads.Notice.ADAPT, Component.translatable("codex.afterworld.vault.adapt." + ADAPT_IDS[bit]));
					Progress.codex(p, "vault.adapt." + ADAPT_IDS[bit]);
				}
			}
		}
		// Reset the mechanisms so the Vault can be run again.
		site.flags &= ~Sites.F_GATE_OPEN;
		Plan reset = new Plan(site.pos(), site.seed);
		int d = -SiteBuilder.depth(site);
		reset.fill(-1, d + 1, -12, 1, d + 3, -12, AwBlocks.WARDED_GEARWORK.defaultBlockState());
		for (boolean west : new boolean[] {true, false}) {
			BlockPos lp = SiteBuilder.vaultLever(site, west);
			BlockState ls = level.getBlockState(lp);
			if (ls.is(Blocks.LEVER)) {
				reset.set(lp.getX() - site.x, lp.getY() - site.pos().getY(), lp.getZ() - site.z, ls.setValue(LeverBlock.POWERED, false));
			}
		}
		for (BlockPos e : SiteBuilder.escapements(site)) {
			reset.set(e.getX() - site.x, e.getY() - site.pos().getY(), e.getZ() - site.z, AwBlocks.ESCAPEMENT.defaultBlockState());
		}
		if ((s.adaptations & A_WARDED) != 0) {
			for (int x = -15; x <= -7; x++) {
				reset.set(x, d + 2, -2, AwBlocks.WARDED_GEARWORK.defaultBlockState());
				reset.set(x, d + 2, 2, AwBlocks.WARDED_GEARWORK.defaultBlockState());
				reset.set(-x, d + 2, -2, AwBlocks.WARDED_GEARWORK.defaultBlockState());
				reset.set(-x, d + 2, 2, AwBlocks.WARDED_GEARWORK.defaultBlockState());
			}
		}
		if ((s.adaptations & A_LISTENING) != 0) {
			reset.set(-22, d + 1, 3, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
			reset.set(22, d + 1, 3, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
		}
		Builds.submit(level, reset, null);
	}

	static int adapt(BossStates.Vault s) {
		int melee = s.observed[PlayerState.TACTIC_MELEE];
		int ranged = s.observed[PlayerState.TACTIC_RANGED];
		int stealth = s.observed[PlayerState.TACTIC_STEALTH];
		int lift = s.observed[PlayerState.TACTIC_EDICT_BASE + Edict.WEIGHTLESSNESS.ordinal()];
		int walls = s.observed[PlayerState.TACTIC_EDICT_BASE + Edict.TRESPASS.ordinal()];
		int a = 0;
		if (ranged >= 20 && ranged > melee * 1.2) a |= A_MIRRORED;
		if (melee >= 40 && melee > ranged * 1.5) a |= A_SPIKED;
		if (lift >= 4) a |= A_ANCHORED;
		if (walls >= 3) a |= A_WARDED;
		if (stealth >= 60) a |= A_LISTENING;
		if (s.lastWing >= 0) a |= A_EXPECTED;
		return a;
	}

	// ------------------------------------------------------------------ wardens

	private static void spawnWave(ServerLevel level, Site site, int wing, Visit v) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		BossStates.Vault s = state(level);
		BlockPos center = SiteBuilder.vaultRoomCenter(site, wing);
		RandomSource r = level.getRandom();
		int wardens = 3 + ((s.adaptations & A_EXPECTED) != 0 && wing == v.expectedWing ? 2 : 0) + Math.min(2, s.kingDefeats);
		for (int i = 0; i < wardens; i++) {
			EntityType<? extends Mob> type = r.nextBoolean() ? EntityTypes.ZOMBIE : EntityTypes.HUSK;
			Mob m = type.create(level, EntitySpawnReason.EVENT);
			if (m == null) {
				continue;
			}
			m.snapTo(center.getX() + 0.5 + r.nextInt(7) - 3, center.getY(), center.getZ() + 0.5 + r.nextInt(7) - 3, r.nextFloat() * 360, 0);
			m.finalizeSpawn(level, level.getCurrentDifficultyAt(center), EntitySpawnReason.EVENT, null);
			equip(m, EquipmentSlot.HEAD, Items.COPPER_HELMET);
			equip(m, EquipmentSlot.CHEST, Items.COPPER_CHESTPLATE);
			equip(m, EquipmentSlot.LEGS, Items.COPPER_LEGGINGS);
			equip(m, EquipmentSlot.FEET, Items.COPPER_BOOTS);
			equip(m, EquipmentSlot.MAINHAND, Items.COPPER_SWORD);
			int flags = MobMark.WARDEN | MobMark.VAULT_MINION;
			String name = "entity.afterworld.vault_warden";
			if ((s.adaptations & A_MIRRORED) != 0 && i % 2 == 0) {
				flags |= MobMark.MIRRORED;
				equip(m, EquipmentSlot.OFFHAND, Items.SHIELD);
				name = "entity.afterworld.mirrored_warden";
			} else if ((s.adaptations & A_SPIKED) != 0 && i % 2 == 1) {
				flags |= MobMark.SPIKED;
				name = "entity.afterworld.spiked_warden";
			}
			if ((s.adaptations & A_ANCHORED) != 0) {
				flags |= MobMark.ANCHORED;
				var kb = m.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
				if (kb != null) {
					kb.addOrReplacePermanentModifier(new AttributeModifier(dev.afterworld.Afterworld.id("anchored"), 1.0, AttributeModifier.Operation.ADD_VALUE));
				}
				if (name.equals("entity.afterworld.vault_warden")) {
					name = "entity.afterworld.anchored_warden";
				}
			}
			m.setCustomName(Component.translatable(name));
			m.setPersistenceRequired();
			MobMark.set(m, new MobMark(flags, java.util.Optional.empty(), 0, site.id));
			level.addFreshEntity(m);
		}
		Mob archer = EntityTypes.SKELETON.create(level, EntitySpawnReason.EVENT);
		if (archer != null) {
			archer.snapTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5 + (wing == 0 ? -4 : 4), 0, 0);
			archer.finalizeSpawn(level, level.getCurrentDifficultyAt(center), EntitySpawnReason.EVENT, null);
			equip(archer, EquipmentSlot.HEAD, Items.COPPER_HELMET);
			archer.setCustomName(Component.translatable("entity.afterworld.vault_marksman"));
			archer.setPersistenceRequired();
			MobMark.set(archer, new MobMark(MobMark.VAULT_MINION, java.util.Optional.empty(), 0, site.id));
			level.addFreshEntity(archer);
		}
		Fx.sound(level, Vec3.atCenterOf(center), SoundEvents.IRON_GOLEM_REPAIR, 1.5F, 0.6F);
	}

	private static void equip(Mob m, EquipmentSlot slot, net.minecraft.world.item.Item item) {
		m.setItemSlot(slot, new ItemStack(item));
		m.setDropChance(slot, 0.0F);
	}

	/** Mirrored wardens turn arrows back on whoever loosed them, half the time. */
	private static boolean reflect(LivingEntity victim, DamageSource source, float amount) {
		if (!(source.getDirectEntity() instanceof Projectile proj) || !(source.getEntity() instanceof ServerPlayer shooter)) {
			return true;
		}
		MobMark mark = victim.getAttached(dev.afterworld.registry.AwAttachments.MOB);
		if (mark == null || !mark.has(MobMark.MIRRORED) || victim.getRandom().nextFloat() > 0.5F) {
			return true;
		}
		ServerLevel level = (ServerLevel) victim.level();
		Vec3 back = shooter.getEyePosition().subtract(proj.position()).normalize();
		proj.setOwner(victim);
		proj.setDeltaMovement(back.scale(1.6));
		if (proj instanceof AbstractArrow arrow) {
			arrow.setPos(victim.getX() + back.x, victim.getEyeY(), victim.getZ() + back.z);
		}
		Fx.sound(level, victim.position(), SoundEvents.SHIELD_BLOCK.value(), 1.0F, 1.3F);
		Fx.burst(level, ParticleTypes.ENCHANTED_HIT, victim.position().add(0, 1.2, 0), 8, 0.3, 0.1);
		Progress.codex(shooter, "vault.adapt.mirrored");
		return false;
	}

	// ------------------------------------------------------------------ the king

	private static void throne(ServerLevel level, Site site, ServerPlayer p) {
		if (king != null && (king.isRemoved() || !king.isAlive())) {
			king = null;
		}
		if (king != null || !AwConfig.get().bossesEnabled || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		BossStates.Vault s = state(level);
		long day = Progress.day(level.getServer());
		boolean returning = false;
		if (s.king != BossStates.Vault.KING_WAITING) {
			if (day - s.kingDefeatDay < 7) {
				return;
			}
			returning = true;
			s.king = BossStates.Vault.KING_WAITING;
		}
		KingEntity k = AwEntities.KING_OF_SECONDS.create(level, EntitySpawnReason.EVENT);
		if (k == null) {
			return;
		}
		BlockPos throne = site.pos().offset(0, -SiteBuilder.depth(site) + 2, -33);
		k.snapTo(throne.getX() + 0.5, throne.getY(), throne.getZ() + 0.5, 0, 0);
		k.escapements = SiteBuilder.escapements(site);
		k.siteId = site.id;
		float scale = 1.0F + 0.2F * s.kingDefeats;
		var hp = k.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null && scale > 1) {
			hp.addOrReplacePermanentModifier(new AttributeModifier(dev.afterworld.Afterworld.id("rewound"), scale - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			k.setHealth(k.getMaxHealth());
		}
		k.fast = s.kingDefeats > 0;
		k.bargainOffered = returning && s.king == BossStates.Vault.KING_BARGAINED;
		level.addFreshEntity(k);
		king = k;
		s.kingEntity = java.util.Optional.of(k.getUUID());
		AfterworldData.get(level.getServer()).setDirty();
		Fx.sound(level, k.position(), SoundEvents.BELL_RESONATE, 2.0F, 1.8F);
		for (ServerPlayer near : level.players()) {
			if (near.distanceToSqr(k) < 48 * 48) {
				near.sendSystemMessage(Component.translatable(returning ? "message.afterworld.king_rewound" : "message.afterworld.king_wakes").withColor(0xE8D7A0));
				Progress.codex(near, "boss.king");
			}
		}
	}

	private static void bargain(ServerPlayer p, KingEntity k) {
		ServerLevel level = p.level();
		BossStates.Vault s = state(level);
		s.king = BossStates.Vault.KING_BARGAINED;
		s.kingDefeatDay = Progress.day(level.getServer());
		AfterworldData.get(level.getServer()).setDirty();
		if (!p.isCreative()) {
			p.getMainHandItem().shrink(1);
		}
		Inv.give(p, new ItemStack(AwItems.fragment(Edict.STILLNESS)));
		Inv.give(p, new ItemStack(AwItems.CLOCKWORK_FRAGMENT, 4));
		Inv.give(p, new ItemStack(dev.afterworld.registry.AwGear.HEARTS.get(dev.afterworld.aspect.Aspect.STOPPED_HOUR)));
		Progress.codex(p, "boss.king.bargain");
		Progress.rep(p, Faction.BUREAU, 6);
		Progress.legend(p, "choice", "Accepted a second of the King of Seconds' time in exchange for his life.");
		Chronicle.record(level.getServer(), "A Bargain Beneath the Clocktower", p.getName().getString()
			+ " spared the King of Seconds for a fragment of his power. The Vault remembers the bargain.", "The Vault of Seconds", Faction.BUREAU.ordinal(), p.getName().getString(), "boss:king");
		Fx.burst(level, ParticleTypes.END_ROD, k.position().add(0, 1.5, 0), 60, 0.8, 0.1);
		Fx.sound(level, k.position(), SoundEvents.BELL_RESONATE, 2.0F, 2.0F);
		p.sendSystemMessage(Component.translatable("message.afterworld.king_bargained").withColor(0xE8D7A0));
		k.discard();
		king = null;
	}

	static void kingDied(ServerLevel level, KingEntity k, DamageSource source) {
		BossStates.Vault s = state(level);
		boolean first = s.kingDefeats == 0;
		s.king = BossStates.Vault.KING_DEFEATED;
		s.kingDefeats++;
		s.kingDefeatDay = Progress.day(level.getServer());
		AfterworldData.get(level.getServer()).setDirty();
		king = null;
		ItemStack prize = first ? new ItemStack(AwItems.fragment(Edict.BORROWED_TIME)) : new ItemStack(AwItems.CLOCKWORK_FRAGMENT, 6);
		level.addFreshEntity(new ItemEntity(level, k.getX(), k.getY() + 1, k.getZ(), prize));
		String killer = source.getEntity() instanceof ServerPlayer p ? p.getName().getString() : "";
		if (source.getEntity() instanceof ServerPlayer p) {
			Progress.codex(p, "boss.king");
			Progress.legend(p, "milestone", "Stopped the King of Seconds" + (first ? "." : " again. He will rewind himself again."));
			Progress.rep(p, Faction.GUILD, 8);
		}
		Chronicle.record(level.getServer(), first ? "The King of Seconds Stops" : "The King of Seconds Stops Again",
			"The King of Seconds was stopped in his throne room" + (killer.isEmpty() ? "." : " by " + killer + ".")
				+ " The Guild expects him to rewind himself within the week.", "The Vault of Seconds", Faction.GUILD.ordinal(), killer, "boss:king");
	}
}

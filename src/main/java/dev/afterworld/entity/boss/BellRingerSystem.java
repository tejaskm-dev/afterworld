package dev.afterworld.entity.boss;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.AwConfig;
import dev.afterworld.block.AwBlockHooks;
import dev.afterworld.edict.Edict;
import dev.afterworld.edict.EdictSystem;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwEntities;
import dev.afterworld.registry.AwItems;
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
import dev.afterworld.world.Names;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.Vec3;

/** The Hollow Cathedral's encounter: spawning, the bell, the chandelier, and the forgotten name. */
public final class BellRingerSystem {
	private static BellRingerEntity live;
	private static int lonely;

	private BellRingerSystem() {
	}

	public static void init() {
		Sites.onPresence(SiteKind.CATHEDRAL, BellRingerSystem::presence);
		Sites.BUILT_LISTENERS.add((level, site) -> {
			if (site.kind == SiteKind.CATHEDRAL) {
				placeLedger(level, site);
			}
		});
		EdictSystem.RIFT_LISTENERS.add((living, zone) -> {
			if (living instanceof BellRingerEntity r && !r.stunned()) {
				r.stun(70);
				for (ServerPlayer p : ((ServerLevel) r.level()).players()) {
					if (p.distanceToSqr(r) < 32 * 32) {
						p.sendOverlayMessage(Component.translatable("message.afterworld.ringer_topples").withColor(0x9BD08A));
						Progress.codex(p, "boss.bell_ringer.rift");
					}
				}
			}
			return false;
		});
		EdictSystem.DAMAGE_SCALERS.add((target, source, damage) -> target instanceof BellRingerEntity r && r.stunned() ? damage * 1.5F : damage);
		AwBlockHooks.counterweightUse = BellRingerSystem::counterweight;
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof BellRingerEntity ringer) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getMainHandItem();
			if (!stack.is(Items.NAME_TAG) || stack.get(DataComponents.CUSTOM_NAME) == null) {
				return InteractionResult.PASS;
			}
			offerName(sp, ringer, stack);
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	static BossStates.BellRinger state(ServerLevel level) {
		return AfterworldData.get(level.getServer()).bell;
	}

	static boolean bellLost(ServerLevel level) {
		return state(level).bellLost;
	}

	public static String trueName(ServerLevel level) {
		BossStates.BellRinger s = state(level);
		return Names.bellName(s.nameA, s.nameB);
	}

	private static void presence(ServerPlayer p, dev.afterworld.player.PlayerState ps, Site site, double d2) {
		ServerLevel level = p.level();
		BossStates.BellRinger s = state(level);
		BlockPos o = site.pos();
		int dx = p.getBlockX() - o.getX();
		int dy = p.getBlockY() - o.getY();
		int dz = p.getBlockZ() - o.getZ();
		boolean inside = Math.abs(dx) <= 8 && dz >= -15 && dz <= 15 && dy >= -1 && dy <= 14;
		if (inside) {
			Progress.codex(p, "place.cathedral");
		}
		if (live != null && (live.isRemoved() || !live.isAlive())) {
			live = null;
		}
		if (live != null) {
			lonely = live.position().distanceToSqr(p.position()) < 64 * 64 ? 0 : lonely + 1;
			if (lonely > 15) {
				live.discard();
				live = null;
			}
			return;
		}
		if (s.state != BossStates.BellRinger.WAITING || !inside || !AwConfig.get().bossesEnabled || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		BellRingerEntity r = AwEntities.BELL_RINGER.create(level, EntitySpawnReason.EVENT);
		if (r == null) {
			return;
		}
		Vec3 home = Vec3.atBottomCenterOf(o.offset(0, 1, 8));
		r.snapTo(home.x, home.y, home.z, 180, 0);
		r.home = home;
		r.siteId = site.id;
		r.setBellHeld(!s.bellLost);
		r.enraged = s.bellLost;
		level.addFreshEntity(r);
		live = r;
		lonely = 0;
		s.entity = Optional.of(r.getUUID());
		AfterworldData.get(level.getServer()).setDirty();
		Fx.hostileSound(level, home, AwSounds.BOSS_BELL, 3.0F, 1.0F);
		p.sendSystemMessage(Component.translatable("message.afterworld.ringer_appears").withColor(0xE8A33D));
	}

	private static void placeLedger(ServerLevel level, Site site) {
		BlockPos chest = site.pos().offset(10, 1, -4);
		if (level.getBlockEntity(chest) instanceof RandomizableContainer c) {
			BossStates.BellRinger s = state(level);
			ItemStack ledger = new ItemStack(AwItems.RINGERS_LEDGER);
			ledger.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Candle accounts, the year of the long winter."),
				Component.literal("Paid to the ringer, " + Names.BELL_A[Math.max(0, s.nameA) % Names.BELL_A.length] + "—, for tolling the dead."),
				Component.literal("The rest of the name is water-stained."))));
			c.setItem(13, ledger);
		}
	}

	/** Lore for the second half of the name, written into the Unremembered census. */
	public static Component censusClue(ServerLevel level) {
		BossStates.BellRinger s = state(level);
		return Component.literal("In the margin: “…the bell-ringer, —" + Names.BELL_B[Math.max(0, s.nameB) % Names.BELL_B.length] + ", still owes us for the candles.”");
	}

	// ------------------------------------------------------------------ the bell

	static void bellLanded(ServerLevel level, HeavyProjectile bell) {
		if (live == null || live.isRemoved()) {
			return;
		}
		BlockPos at = BlockPos.containing(bell.position());
		for (int i = 0; i < 6 && level.getBlockState(at).isAir() && level.getBlockState(at.below()).isAir(); i++) {
			at = at.below();
		}
		while (!level.getBlockState(at).isAir() && at.getY() < level.getMaxY()) {
			at = at.above();
		}
		level.setBlock(at, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR), 3);
		live.landedBell = at;
	}

	static void pickUpBell(ServerLevel level, BellRingerEntity r) {
		BlockPos at = r.landedBell;
		r.landedBell = null;
		if (at != null && level.getBlockState(at).is(Blocks.BELL)) {
			level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
			r.setBellHeld(true);
			Fx.hostileSound(level, Vec3.atCenterOf(at), SoundEvents.BELL_BLOCK, 1.0F, 0.8F);
		} else {
			lose(level, r, null);
		}
	}

	/** Called whenever someone casts Return: a landed bell within reach is stolen. */
	public static boolean tryStealBell(ServerPlayer p) {
		if (live == null || live.landedBell == null || live.level() != p.level()) {
			return false;
		}
		BlockPos at = live.landedBell;
		if (p.blockPosition().distSqr(at) > 24 * 24) {
			return false;
		}
		ServerLevel level = p.level();
		Fx.line(level, Fx.dust(Edict.RETURN.color, 1.2F), Vec3.atCenterOf(at), p.position().add(0, 1, 0), 0.6);
		if (level.getBlockState(at).is(Blocks.BELL)) {
			level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
		}
		Inv.give(p, new ItemStack(AwItems.BELL_FRAGMENT, 3));
		live.landedBell = null;
		lose(level, live, p);
		return true;
	}

	private static void lose(ServerLevel level, BellRingerEntity r, ServerPlayer by) {
		state(level).bellLost = true;
		AfterworldData.get(level.getServer()).setDirty();
		r.setBellHeld(false);
		r.enraged = true;
		Fx.hostileSound(level, r.position(), AwSounds.BOSS_ROAR, 2.0F, 0.85F);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(r) < 48 * 48) {
				p.sendSystemMessage(Component.translatable("message.afterworld.ringer_bell_lost").withColor(0xE8A33D));
				Progress.codex(p, "boss.bell_ringer.stolen");
			}
		}
	}

	// ------------------------------------------------------------------ the chandelier

	private static boolean counterweight(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, ServerPlayer p, ItemStack stack) {
		Site site = Sites.at(level.getServer(), level.dimension().identifier().toString(), pos, SiteKind.CATHEDRAL);
		if (site == null) {
			return false;
		}
		if ((site.flags & Sites.F_CHANDELIER) != 0) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.counterweight_spent").withColor(0x9A9488));
			return true;
		}
		site.flags |= Sites.F_CHANDELIER;
		AfterworldData.get(level.getServer()).setDirty();
		BlockPos c = SiteBuilder.chandelier(site);
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = -1; y <= 4; y++) {
					BlockPos b = c.offset(x, y, z);
					if (!level.getBlockState(b).isAir()) {
						level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
		Vec3 floor = Vec3.atBottomCenterOf(site.pos().above(1));
		Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.CHAIN_BREAK, 2.0F, 0.6F);
		Fx.burst(level, ParticleTypes.EXPLOSION, floor, 4, 2.0, 0);
		Fx.hostileSound(level, floor, SoundEvents.ANVIL_LAND, 2.5F, 0.5F);
		Progress.codex(p, "boss.bell_ringer.counterweight");
		if (live != null && !live.isRemoved()) {
			double dx = live.getX() - floor.x;
			double dz = live.getZ() - floor.z;
			if (dx * dx + dz * dz < 4.5 * 4.5) {
				live.hurtServer(level, level.damageSources().fallingBlock(p), 45.0F);
				live.stun(100);
				p.sendSystemMessage(Component.translatable("message.afterworld.chandelier_hit").withColor(0xE8A33D));
				return true;
			}
		}
		p.sendSystemMessage(Component.translatable("message.afterworld.chandelier_miss").withColor(0x9A9488));
		return true;
	}

	// ------------------------------------------------------------------ the name

	private static void offerName(ServerPlayer p, BellRingerEntity r, ItemStack tag) {
		ServerLevel level = p.level();
		String offered = tag.getHoverName().getString().trim();
		if (!p.isCreative()) {
			tag.shrink(1);
		}
		if (offered.equalsIgnoreCase(trueName(level))) {
			remembered(level, r, p);
		} else {
			p.sendSystemMessage(Component.translatable("message.afterworld.ringer_wrong_name", offered).withColor(0xC4553B));
			r.stun(0);
			Fx.hostileSound(level, r.position(), AwSounds.BOSS_ROAR, 2.0F, 0.95F);
			Progress.codex(p, "boss.bell_ringer.name");
		}
	}

	private static void remembered(ServerLevel level, BellRingerEntity r, ServerPlayer by) {
		BossStates.BellRinger s = state(level);
		s.state = BossStates.BellRinger.REMEMBERED;
		s.resolvedBy = by.getName().getString();
		AfterworldData.get(level.getServer()).setDirty();
		r.remember(level, by);
		int spoken = dev.afterworld.npc.Barks.say(level, r, "bell", "name", 64);
		dev.afterworld.util.Scheduler.after(spoken + 20, () -> dev.afterworld.npc.Barks.say(level, r, "bell", "defeat", 64));
		String name = trueName(level);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(r) < 48 * 48) {
				p.sendSystemMessage(Component.translatable("message.afterworld.ringer_remembers", name).withColor(0xE8DCC0));
			}
		}
		Inv.give(by, new ItemStack(AwItems.fragment(Edict.FORGOTTEN)));
		Inv.give(by, new ItemStack(AwItems.BELL_FRAGMENT, 5));
		Inv.give(by, new ItemStack(dev.afterworld.registry.AwGear.HEARTS.get(dev.afterworld.aspect.Aspect.REQUIEM_TOLL)));
		Progress.rep(by, Faction.COURT, 20);
		Progress.codex(by, "boss.bell_ringer.name");
		Progress.legend(by, "choice", "Gave the Bell-Ringer back its name, " + name + ", and it laid down its bell.");
		Chronicle.record(level.getServer(), "The Ringer Remembers", by.getName().getString() + " spoke the Bell-Ringer's forgotten name, " + name
			+ ". It set down its bell in the Hollow Cathedral and has not been seen since.", "The Hollow Cathedral", Faction.COURT.ordinal(), by.getName().getString(), "boss:bell");
		Chronicle.reveal(by, "boss:bell");
		dev.afterworld.util.Scheduler.after(80, () -> {
			if (!r.isRemoved()) {
				BlockPos at = r.blockPosition();
				if (level.getBlockState(at).isAir() && !s.bellLost) {
					level.setBlock(at, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR), 3);
				}
				Fx.burst(level, ParticleTypes.SOUL, r.position().add(0, 1.5, 0), 40, 0.8, 0.05);
				Fx.hostileSound(level, r.position(), SoundEvents.SOUL_ESCAPE.value(), 2.0F, 0.6F);
				r.discard();
				live = null;
			}
		});
	}

	static void died(ServerLevel level, BellRingerEntity r, DamageSource source) {
		BossStates.BellRinger s = state(level);
		s.state = BossStates.BellRinger.DEFEATED;
		AfterworldData.get(level.getServer()).setDirty();
		live = null;
		drop(level, r, new ItemStack(AwItems.fragment(Edict.FORGOTTEN)));
		drop(level, r, new ItemStack(AwItems.BELL_FRAGMENT, 2));
		String killer = source.getEntity() instanceof ServerPlayer p ? p.getName().getString() : "";
		if (source.getEntity() instanceof ServerPlayer p) {
			s.resolvedBy = killer;
			Progress.codex(p, "boss.bell_ringer");
			Progress.rep(p, Faction.CROWN, 5);
			Progress.legend(p, "milestone", "Silenced the Bell-Ringer of the Hollow Cathedral.");
			Chronicle.reveal(p, "boss:bell");
		}
		Chronicle.record(level.getServer(), "The Bell Falls Silent", "The Bell-Ringer of the Hollow Cathedral is dead" + (killer.isEmpty() ? "." : ", killed by " + killer + ".")
			+ " Nobody living remembers what it was called.", "The Hollow Cathedral", -1, killer, "boss:bell");
	}

	private static void drop(ServerLevel level, BellRingerEntity at, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, at.getX(), at.getY() + 1.0, at.getZ(), stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}
}

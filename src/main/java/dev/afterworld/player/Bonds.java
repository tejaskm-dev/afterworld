package dev.afterworld.player;

import dev.afterworld.Afterworld;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.site.Site;
import dev.afterworld.site.SiteKind;
import dev.afterworld.site.Sites;
import dev.afterworld.util.Fx;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.WorldSim;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Bonds are relationships, not pet slots: each one provides a service in exchange for an
 * obligation, and neglect brings a complication rather than a silent penalty.
 */
public final class Bonds {
	public static final int MAX = 2;
	private static final Identifier PILGRIM_STEP = Afterworld.id("pilgrim_step");
	private static final Identifier PILGRIM_ROAD = Afterworld.id("pilgrim_road");

	private Bonds() {
	}

	public static void init() {
		WorldSim.DAY_LISTENERS.add(Bonds::onNewDay);
		ServerTickEvents.END_SERVER_TICK.register(Bonds::tick);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof net.minecraft.server.level.ServerPlayer player) || !tenancy(player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applyPassives(newPlayer, PlayerState.of(newPlayer)));
		Sites.onPresence(SiteKind.SHRINE, (p, s, site, d2) -> {
			BondState b = s.bond(BondType.PILGRIM);
			if (b != null && d2 < 100 && (site.flags & Sites.F_REPAIRED) != 0 && b.lastPaidDay != Progress.day(p)) {
				pay(p, s, b);
			}
		});
	}

	// ------------------------------------------------------------------ forming

	public static void offer(ServerPlayer p, PlayerState s, BondType type) {
		if (s.hasBond(type)) {
			return;
		}
		long now = p.level().getGameTime();
		if (s.pendingBond == type.ordinal() && s.pendingBondUntil > now) {
			return;
		}
		s.pendingBond = type.ordinal();
		s.pendingBondUntil = now + 24000;
		Progress.notice(p, AwPayloads.Notice.BOND, Component.translatable("notice.afterworld.bond_offer", type.displayName()));
		p.sendSystemMessage(Component.translatable("message.afterworld.bond_offer." + type.id).withColor(type.color));
		p.sendSystemMessage(Component.translatable("message.afterworld.bond_offer_hint").withStyle(ChatFormatting.DARK_GRAY));
		Progress.codex(p, "bond." + type.id);
	}

	public static boolean accept(ServerPlayer p, BondType type) {
		PlayerState s = PlayerState.of(p);
		long now = p.level().getGameTime();
		if (s.pendingBond != type.ordinal() || s.pendingBondUntil < now || s.hasBond(type)) {
			return false;
		}
		if (s.bonds.size() >= MAX) {
			p.sendSystemMessage(Component.translatable("message.afterworld.bond_full").withStyle(ChatFormatting.RED));
			return false;
		}
		s.pendingBond = -1;
		BondState b = new BondState(type, Progress.day(p));
		s.bonds.add(b);
		applyPassives(p, s);
		Fx.burst(p.level(), ParticleTypes.SOUL, p.position().add(0, 1, 0), 20, 0.5, 0.03);
		Fx.sound(p.level(), p.position(), SoundEvents.ALLAY_ITEM_GIVEN, 1.0F, 0.7F);
		p.sendSystemMessage(Component.translatable("message.afterworld.bond_formed", type.displayName().copy().withColor(type.color)));
		Progress.legend(p, "bond", "Formed a Bond with " + type.displayName().getString() + ".");
		Progress.codex(p, "bond." + type.id);
		return true;
	}

	public static void decline(ServerPlayer p) {
		PlayerState.of(p).pendingBond = -1;
	}

	public static void release(ServerPlayer p, BondType type) {
		PlayerState s = PlayerState.of(p);
		BondState b = s.bond(type);
		if (b == null) {
			return;
		}
		s.bonds.remove(b);
		applyPassives(p, s);
		switch (type) {
			case LANDLORD -> p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 2400, 0));
			case MOTH -> p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 600, 0));
			case PILGRIM -> p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1200, 0));
		}
		p.sendSystemMessage(Component.translatable("message.afterworld.bond_released." + type.id).withColor(type.color));
		Progress.legend(p, "bond", "Ended a Bond with " + type.displayName().getString() + ".");
	}

	// ------------------------------------------------------------------ obligations

	/** Tries to settle the obligation now. Returns true if it was met. */
	public static boolean pay(ServerPlayer p, PlayerState s, BondState b) {
		boolean paid = switch (b.type) {
			case LANDLORD -> take(p, Items.EMERALD, 3);
			case MOTH -> take(p, Items.GLOWSTONE_DUST, 2) || take(p, Items.TORCH, 4);
			case PILGRIM -> nearRepairedShrine(p);
		};
		if (paid) {
			b.lastPaidDay = Progress.day(p);
			b.complication = false;
			b.counter = 0;
			p.sendOverlayMessage(Component.translatable("message.afterworld.bond_paid." + b.type.id).withColor(b.type.color));
		}
		return paid;
	}

	private static boolean nearRepairedShrine(ServerPlayer p) {
		Site site = Sites.at(p.level().getServer(), p.level().dimension().identifier().toString(), p.blockPosition(), SiteKind.SHRINE);
		return site != null && (site.flags & Sites.F_REPAIRED) != 0;
	}

	private static boolean take(ServerPlayer p, net.minecraft.world.item.Item item, int count) {
		if (p.isCreative()) {
			return true;
		}
		var inv = p.getInventory();
		int have = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item)) {
				have += st.getCount();
			}
		}
		if (have < count) {
			return false;
		}
		int need = count;
		for (int i = 0; i < inv.getContainerSize() && need > 0; i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item)) {
				int n = Math.min(need, st.getCount());
				st.shrink(n);
				need -= n;
			}
		}
		return true;
	}

	private static void onNewDay(MinecraftServer server, AfterworldData data, long day) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerState s = PlayerState.of(p);
			for (BondState b : s.bonds) {
				int grace = s.discipline == Discipline.KEEPER ? 1 : 0;
				if (day >= b.dueDay() + grace && !b.complication) {
					if (!pay(p, s, b)) {
						b.complication = true;
						p.sendSystemMessage(Component.translatable("message.afterworld.bond_overdue." + b.type.id).withColor(0xC4553B));
						Progress.legend(p, "bond", "Fell behind on the obligations of " + b.type.displayName().getString() + ".");
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ services and complications

	private static void tick(MinecraftServer server) {
		long t = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if ((t + p.getId()) % 40 != 7) {
				continue;
			}
			PlayerState s = PlayerState.of(p);
			if (s.bonds.isEmpty()) {
				continue;
			}
			ServerLevel level = p.level();
			boolean night = level.isDarkOutside();
			for (BondState b : s.bonds) {
				switch (b.type) {
					case LANDLORD -> {
						if (b.complication && night && p.getRandom().nextFloat() < 0.25F) {
							level.playSound(null, p.blockPosition().offset(p.getRandom().nextInt(9) - 4, 1, p.getRandom().nextInt(9) - 4),
								SoundEvents.AMBIENT_CAVE.value(), net.minecraft.sounds.SoundSource.AMBIENT, 0.7F, 0.6F);
							p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 600, 0, true, true));
						}
					}
					case MOTH -> {
						if (b.complication) {
							if (night) {
								p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false));
							}
						} else {
							int light = level.getBrightness(LightLayer.BLOCK, p.blockPosition());
							int sky = level.getBrightness(LightLayer.SKY, p.blockPosition());
							if (light <= 4 && (sky <= 4 || night)) {
								p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false));
							}
							for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(16), e -> e != p && e.isInvisible())) {
								e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
							}
						}
					}
					case PILGRIM -> {
						if (b.complication) {
							p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0, true, false));
						}
						AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
						if (speed != null) {
							boolean onPath = level.getBlockState(BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ())).is(Blocks.DIRT_PATH);
							if (onPath && !b.complication) {
								speed.addOrReplacePermanentModifier(new AttributeModifier(PILGRIM_ROAD, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
							} else {
								speed.removeModifier(PILGRIM_ROAD);
							}
						}
					}
				}
			}
		}
	}

	public static void applyPassives(ServerPlayer p, PlayerState s) {
		AttributeInstance step = p.getAttribute(Attributes.STEP_HEIGHT);
		if (step != null) {
			if (s.hasBond(BondType.PILGRIM)) {
				step.addOrReplacePermanentModifier(new AttributeModifier(PILGRIM_STEP, 0.5, AttributeModifier.Operation.ADD_VALUE));
			} else {
				step.removeModifier(PILGRIM_STEP);
			}
		}
		if (!s.hasBond(BondType.PILGRIM)) {
			AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(PILGRIM_ROAD);
			}
		}
	}

	/** The Ghost Landlord will not have a tenant die on the premises. Once per rent period. */
	private static boolean tenancy(ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		BondState b = s.bond(BondType.LANDLORD);
		if (b == null || b.complication || b.counter > 0) {
			return false;
		}
		b.counter = 1;
		p.setHealth(Math.min(p.getMaxHealth(), 6.0F));
		p.removeAllEffects();
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 3));
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
		p.setRemainingFireTicks(0);
		Fx.burst(p.level(), ParticleTypes.SOUL, p.position().add(0, 1, 0), 30, 0.6, 0.05);
		Fx.sound(p.level(), p.position(), SoundEvents.TOTEM_USE, 0.6F, 0.6F);
		p.sendSystemMessage(Component.translatable("message.afterworld.tenancy").withStyle(ChatFormatting.ITALIC).withColor(BondType.LANDLORD.color));
		Progress.codex(p, "bond.landlord.tenancy");
		Progress.legend(p, "bond", "Survived a killing blow on the Ghost Landlord's say-so.");
		return true;
	}
}

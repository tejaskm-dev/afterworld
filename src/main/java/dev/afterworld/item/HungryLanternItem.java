package dev.afterworld.item;

import dev.afterworld.edict.EdictSystem;
import dev.afterworld.player.BondType;
import dev.afterworld.player.Bonds;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwComponents;
import dev.afterworld.rumour.RumourEntry;
import dev.afterworld.util.Fx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Reveals invisible creatures and old traces while lit. It feeds on your experience. */
public class HungryLanternItem extends LoreItem {
	public HungryLanternItem(Properties properties) {
		super(properties, 2, false);
	}

	public static boolean lit(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(AwComponents.LIT));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean on = !lit(stack);
		if (on && !player.isCreative() && player.totalExperience <= 0 && player.experienceLevel <= 0) {
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.afterworld.lantern_hungry").withColor(0xE7C46A));
			}
			return InteractionResult.FAIL;
		}
		if (on) {
			stack.set(AwComponents.LIT, true);
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		} else {
			stack.remove(AwComponents.LIT);
			stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		}
		level.playSound(null, player.blockPosition(), on ? SoundEvents.FIRECHARGE_USE : SoundEvents.CANDLE_EXTINGUISH, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (!lit(stack) || !(owner instanceof ServerPlayer p) || (owner.tickCount + p.getId()) % 20 != 0) {
			return;
		}
		boolean held = p.getMainHandItem() == stack || p.getOffhandItem() == stack;
		if (!held) {
			return;
		}
		if (!p.isCreative()) {
			if (p.totalExperience <= 0 && p.experienceLevel <= 0) {
				stack.remove(AwComponents.LIT);
				stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
				p.sendOverlayMessage(Component.translatable("message.afterworld.lantern_starved").withColor(0xE7C46A));
				return;
			}
			p.giveExperiencePoints(-1);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(20), e -> e != p && e.isInvisible())) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
		}
		PlayerState s = PlayerState.of(p);
		if (p.tickCount % 60 == 0) {
			RumourEntry lead = EdictSystem.nearestLead(p, s);
			if (lead != null) {
				Vec3 dir = new Vec3(lead.x - p.getX(), 0, lead.z - p.getZ()).normalize();
				for (int i = 2; i <= 7; i++) {
					Vec3 at = p.position().add(dir.scale(i * 1.6)).add(0, 0.05, 0);
					Fx.burst(level, ParticleTypes.SOUL_FIRE_FLAME, at, 1, 0.05, 0);
				}
			}
		}
		Progress.codex(p, "relic.hungry_lantern");
		s.lanternTicks += 20;
		if (s.lanternTicks >= 12000 && !s.hasBond(BondType.MOTH)) {
			Bonds.offer(p, s, BondType.MOTH);
		}
	}
}

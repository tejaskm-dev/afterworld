package dev.afterworld.player;

import dev.afterworld.Afterworld;
import dev.afterworld.registry.AwAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Relics: three slots of quiet power, opened from the Journal. Each relic's effect is checked
 * where it matters (coin drops, stamina, damage) by asking whether it is equipped.
 */
public final class Relics {
	public static final int SLOTS = 3;

	private Relics() {
	}

	public static List<ItemStack> of(Player p) {
		List<ItemStack> l = p.getAttached(AwAttachments.RELICS);
		return l == null ? List.of() : l;
	}

	public static boolean has(Player p, String id) {
		for (ItemStack s : of(p)) {
			if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).equals(Afterworld.id(id))) {
				return true;
			}
		}
		return false;
	}

	/** Puts {@code stack} in relic slot {@code slot}, returning whatever was there. */
	public static ItemStack equip(ServerPlayer p, int slot, ItemStack stack) {
		List<ItemStack> cur = new ArrayList<>(of(p));
		while (cur.size() < SLOTS) {
			cur.add(ItemStack.EMPTY);
		}
		ItemStack old = cur.get(slot);
		cur.set(slot, stack.copyWithCount(1));
		p.setAttached(AwAttachments.RELICS, List.copyOf(cur));
		return old;
	}

	public static double coinBonus(ServerPlayer p) {
		return (has(p, "bram_purse_knot") ? 1.15 : 1.0) * (has(p, "lucky_sextant") ? 1.1 : 1.0);
	}

	private static final net.minecraft.resources.Identifier SPRING = Afterworld.id("relic_spring");

	/** Once a second: the relics whose power is always on. */
	public static void tick(ServerPlayer p) {
		if (has(p, "glimmerfin_scale") && p.isInWater()) {
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE, 60, 0, true, false, true));
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING, 60, 0, true, false, true));
		}
		if (has(p, "mothlantern_locket")) {
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 300, 0, true, false, true));
		}
		if (has(p, "starseed")) {
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(dev.afterworld.registry.AwEffects.STARLIT, 60, 0, true, false, true));
		}
		if (has(p, "rimecore_charm")) {
			p.setTicksFrozen(0);
		}
		var safe = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE);
		boolean spring = has(p, "gearsprung_anklet");
		if (safe != null && spring != (safe.getModifier(SPRING) != null)) {
			safe.removeModifier(SPRING);
			if (spring) {
				safe.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SPRING, 5.0,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
			}
		}
		if (spring) {
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP_BOOST, 60, 0, true, false, true));
		}
	}
}

package dev.afterworld.combat;

import dev.afterworld.Afterworld;
import dev.afterworld.player.PlayerState;
import dev.afterworld.registry.AwGear;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Armour sets: wear all four pieces and the set wakes. Each bonus is something you play
 * differently with, not just a bigger number: the Warden's Regalia feeds your Aspect, the
 * Ranger's leathers make you quick and deadly with a bow, Emberforged plate walks through fire.
 */
public final class SetBonuses {
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Identifier KNOCKBACK = Afterworld.id("set_knockback");
	private static final Identifier SPEED = Afterworld.id("set_speed");
	private static final Identifier HEALTH = Afterworld.id("set_health");
	private static final Identifier TOUGHNESS = Afterworld.id("set_toughness");

	private SetBonuses() {
	}

	public static int count(Player p, AwGear.Set set) {
		int n = 0;
		for (int i = 0; i < 4; i++) {
			if (set.pieces[i] != null && p.getItemBySlot(SLOTS[i]).is(set.pieces[i])) {
				n++;
			}
		}
		return n;
	}

	public static boolean has(Player p, AwGear.Set set) {
		return count(p, set) == 4;
	}

	/** Once a second: wake or quiet each set's lasting effects. */
	static void tick(ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		modifier(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK, has(p, AwGear.Set.WARDENS_REGALIA) ? 0.5 : 0, AttributeModifier.Operation.ADD_VALUE);
		modifier(p.getAttribute(Attributes.MOVEMENT_SPEED), SPEED, has(p, AwGear.Set.DUSKPINE_RANGER) ? 0.1 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(p.getAttribute(Attributes.MAX_HEALTH), HEALTH, has(p, AwGear.Set.GILDED_GLYPHMAIL) ? 4 : 0, AttributeModifier.Operation.ADD_VALUE);
		modifier(p.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS, has(p, AwGear.Set.RIMEGUARD) ? 2 : 0, AttributeModifier.Operation.ADD_VALUE);
		s.starweave = has(p, AwGear.Set.STARWEAVE);
		if (s.starweave) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, true));
		}
		if (has(p, AwGear.Set.EMBERFORGED) && p.isOnFire()) {
			p.clearFire();
		}
		if (has(p, AwGear.Set.RIMEGUARD)) {
			p.setTicksFrozen(0);
		}
	}

	private static void modifier(AttributeInstance a, Identifier id, double value, AttributeModifier.Operation op) {
		if (a == null) {
			return;
		}
		AttributeModifier cur = a.getModifier(id);
		if (value == 0) {
			if (cur != null) {
				a.removeModifier(id);
			}
			return;
		}
		if (cur == null || cur.amount() != value) {
			a.removeModifier(id);
			a.addTransientModifier(new AttributeModifier(id, value, op));
		}
	}
}

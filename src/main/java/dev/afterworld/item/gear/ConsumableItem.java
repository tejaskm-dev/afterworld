package dev.afterworld.item.gear;

import dev.afterworld.item.Tooltipped;
import dev.afterworld.util.Fx;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Things used up: elixirs drunk at once, oils that coat the weapon in your other hand, bombs
 * that are thrown, and scrolls that are read.
 */
public class ConsumableItem extends Item implements Tooltipped {
	public enum Kind {
		STAMINA_ELIXIR, RESONANCE_DRAUGHT, SECOND_WIND, EMBER_OIL, FROST_OIL, VENOM_OIL, SMOKE_BOMB, FROST_BOMB, THUNDER_BOMB,
		WAYSTONE_SHARD, SURVEY_SCROLL, LOCKPICK
	}

	private final Kind kind;

	public ConsumableItem(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return this.kind;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer p)) {
			return this.kind == Kind.LOCKPICK ? InteractionResult.PASS : InteractionResult.SUCCESS;
		}
		boolean used = switch (this.kind) {
			case STAMINA_ELIXIR -> {
				dev.afterworld.movement.Stamina.boost(p, 20 * 180);
				drink(sl, p);
				yield true;
			}
			case RESONANCE_DRAUGHT -> {
				dev.afterworld.aspect.Aspects.addResonance(p, 50);
				drink(sl, p);
				yield true;
			}
			case SECOND_WIND -> {
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 2));
				drink(sl, p);
				yield true;
			}
			case EMBER_OIL, FROST_OIL, VENOM_OIL -> coat(p);
			case SMOKE_BOMB, FROST_BOMB, THUNDER_BOMB -> {
				dev.afterworld.combat.Projectiles.bomb(sl, p, this.kind);
				yield true;
			}
			case WAYSTONE_SHARD -> dev.afterworld.world.Waystones.toCamp(p);
			case SURVEY_SCROLL -> {
				dev.afterworld.quest.Quests.mark(p, "landmark", null);
				Fx.sound(sl, p.position(), SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
				yield true;
			}
			case LOCKPICK -> false;
		};
		if (used && !p.isCreative()) {
			stack.shrink(1);
		}
		return used ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	private static void drink(ServerLevel level, ServerPlayer p) {
		Fx.sound(level, p.position(), SoundEvents.GENERIC_DRINK.value(), 1.0F, 1.0F);
		Fx.burst(level, ParticleTypes.HAPPY_VILLAGER, p.position().add(0, 1.2, 0), 8, 0.4, 0.02);
	}

	/** Coats the weapon in the main hand (this oil must be in the off hand) for 60 hits. */
	private boolean coat(ServerPlayer p) {
		ItemStack weapon = p.getMainHandItem();
		if (weapon.isEmpty() || weapon == p.getOffhandItem() || !weapon.has(DataComponents.ATTRIBUTE_MODIFIERS)) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.oil_how").withColor(0x9A9488));
			return false;
		}
		String oil = this.kind.name().toLowerCase(java.util.Locale.ROOT);
		CustomData.update(DataComponents.CUSTOM_DATA, weapon, t -> {
			t.putString("oil", oil);
			t.putInt("oil_hits", 60);
		});
		Fx.sound(p.level(), p.position(), SoundEvents.HONEY_BLOCK_PLACE, 1.0F, 1.2F);
		p.sendOverlayMessage(Component.translatable("message.afterworld.oil_coated", weapon.getHoverName()).withColor(0xE8C873));
		return true;
	}

	@Override
	public void lore(ItemStack stack, Consumer<Component> out) {
		out.accept(Component.translatable("consumable.afterworld." + this.kind.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.DARK_AQUA));
	}
}

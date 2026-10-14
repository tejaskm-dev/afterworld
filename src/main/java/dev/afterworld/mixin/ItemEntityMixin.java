package dev.afterworld.mixin;

import dev.afterworld.economy.Coins;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Coins never sit in the inventory: touching one puts its value in the wallet. */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
	@Shadow
	public abstract boolean hasPickUpDelay();

	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void afterworld$coins(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer p && !hasPickUpDelay() && Coins.pickUp(p, (ItemEntity) (Object) this)) {
			ci.cancel();
		}
	}
}

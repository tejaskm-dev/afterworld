package dev.afterworld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.afterworld.player.Soulbound;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Soulbound things stay put when a dying player's inventory spills. */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Inject(method = "dropAll", at = @At("HEAD"))
	private void afterworld$stowSoulbound(CallbackInfo ci) {
		Soulbound.stow((Inventory) (Object) this);
	}

	@WrapOperation(method = "dropAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"))
	private boolean afterworld$skipSoulbound(ItemStack stack, Operation<Boolean> original) {
		return original.call(stack) || Soulbound.is(stack);
	}
}

package dev.afterworld.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Mannequins make good people: these are the setters vanilla keeps to itself. */
@Mixin(Mannequin.class)
public interface MannequinAccessor {
	@Invoker("setProfile")
	void afterworld$setProfile(ResolvableProfile profile);

	@Invoker("setImmovable")
	void afterworld$setImmovable(boolean immovable);

	@Invoker("setDescription")
	void afterworld$setDescription(Component description);
}

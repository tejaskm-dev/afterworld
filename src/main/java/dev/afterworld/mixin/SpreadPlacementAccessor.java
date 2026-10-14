package dev.afterworld.mixin;

import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A structure placement's salt, which tells the structure sets apart (see Siting). */
@Mixin(AbstractSpreadingStructurePlacement.class)
public interface SpreadPlacementAccessor {
	@Accessor("salt")
	int afterworld$salt();
}

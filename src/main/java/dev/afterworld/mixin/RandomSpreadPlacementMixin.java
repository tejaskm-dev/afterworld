package dev.afterworld.mixin;

import dev.afterworld.world.atlas.Siting;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every randomly spread structure, ours or the game's, also has to respect {@link Siting}. */
@Mixin(RandomSpreadStructurePlacement.class)
abstract class RandomSpreadPlacementMixin {
	@Inject(method = "isPlacementChunk", at = @At("RETURN"), cancellable = true)
	private void afterworld$siting(ChunkGeneratorStructureState state, int x, int z, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !Siting.allowed((RandomSpreadStructurePlacement) (Object) this, state, x, z)) {
			cir.setReturnValue(false);
		}
	}
}

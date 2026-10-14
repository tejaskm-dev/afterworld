package dev.afterworld.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import dev.afterworld.world.gen.AwBiomes;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Lets Afterworld's biomes take the place of vanilla ones (see AwBiomes.Remapper#remap). */
@Mixin(MultiNoiseBiomeSource.class)
abstract class MultiNoiseBiomeSourceMixin {
	@Shadow
	@Final
	private Either<Climate.ParameterList<Holder<Biome>>, Holder<MultiNoiseBiomeSourceParameterList>> parameters;
	@Unique
	private AwBiomes.Remapper afterworld$remapper;
	@Unique
	private volatile boolean afterworld$resolved;

	@ModifyReturnValue(method = "getNoiseBiome(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Lnet/minecraft/core/Holder;", at = @At("RETURN"))
	private Holder<Biome> afterworld$remap(Holder<Biome> original, @Local(argsOnly = true) Climate.TargetPoint target) {
		if (!this.afterworld$resolved) {
			this.afterworld$remapper = this.parameters.right().map(h -> ((AwBiomes.Holders) h.value()).afterworld$remapper()).orElse(null);
			this.afterworld$resolved = true;
		}
		AwBiomes.Remapper r = this.afterworld$remapper;
		return r == null ? original : r.remap(original, target);
	}
}

package dev.afterworld.mixin;

import dev.afterworld.world.gen.AwBiomes;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers Afterworld's biomes with the overworld preset (unreachable points; see AwBiomes). */
@Mixin(MultiNoiseBiomeSourceParameterList.class)
abstract class MultiNoiseBiomeSourceParameterListMixin implements AwBiomes.Holders {
	@Shadow
	@Final
	@Mutable
	private Climate.ParameterList<Holder<Biome>> parameters;
	@Unique
	private AwBiomes.Remapper afterworld$remapper;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void afterworld$addBiomes(MultiNoiseBiomeSourceParameterList.Preset preset, HolderGetter<Biome> biomes, CallbackInfo ci) {
		if (preset != MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD) {
			return;
		}
		Holder<Biome>[] ours = AwBiomes.resolve(biomes);
		if (ours != null) {
			this.afterworld$remapper = AwBiomes.remapper(this.parameters, ours);
			this.parameters = AwBiomes.extend(this.parameters, ours);
		}
	}

	@Override
	public AwBiomes.Remapper afterworld$remapper() {
		return this.afterworld$remapper;
	}
}

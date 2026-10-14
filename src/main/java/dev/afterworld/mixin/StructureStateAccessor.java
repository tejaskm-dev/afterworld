package dev.afterworld.mixin;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The biome source behind structure placement, so Siting can tell where a structure could really stand. */
@Mixin(ChunkGeneratorStructureState.class)
public interface StructureStateAccessor {
	@Accessor("biomeSource")
	BiomeSource afterworld$biomeSource();
}

package dev.afterworld.world.gen;

import com.mojang.serialization.MapCodec;
import dev.afterworld.Afterworld;
import dev.afterworld.world.gen.feature.AshVent;
import dev.afterworld.world.gen.feature.AwTree;
import dev.afterworld.world.gen.feature.Bones;
import dev.afterworld.world.gen.feature.CaveCamp;
import dev.afterworld.world.gen.feature.CaveDecor;
import dev.afterworld.world.gen.feature.GiantMushroom;
import dev.afterworld.world.gen.feature.Ground;
import dev.afterworld.world.gen.feature.Outcrop;
import dev.afterworld.world.gen.feature.Ruin;
import dev.afterworld.world.gen.feature.Spire;
import dev.afterworld.world.gen.feature.Causeway;
import dev.afterworld.world.gen.feature.CrystalSpire;
import dev.afterworld.world.gen.feature.FloatingIsland;
import dev.afterworld.world.gen.feature.GnarledTree;
import dev.afterworld.world.gen.feature.HollowGiant;
import dev.afterworld.world.gen.feature.StandingStones;
import dev.afterworld.world.gen.feature.StoneArch;
import dev.afterworld.world.gen.feature.SurfacePaint;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Feature types for Afterworld's terrain. The features themselves are data (worldgen/feature). */
public final class AwFeatures {
	private AwFeatures() {
	}

	public static void init() {
		register("surface_paint", SurfacePaint.CODEC);
		register("floating_island", FloatingIsland.CODEC);
		register("crystal_spire", CrystalSpire.CODEC);
		register("hollow_giant", HollowGiant.CODEC);
		register("standing_stones", StandingStones.CODEC);
		register("ash_vent", AshVent.CODEC);
		register("gnarled_tree", GnarledTree.CODEC);
		register("stone_arch", StoneArch.CODEC);
		register("causeway", Causeway.CODEC);
		register("ground", Ground.CODEC);
		register("tree", AwTree.CODEC);
		register("outcrop", Outcrop.CODEC);
		register("spire", Spire.CODEC);
		register("ruin", Ruin.CODEC);
		register("giant_mushroom", GiantMushroom.CODEC);
		register("cave_decor", CaveDecor.CODEC);
		register("bones", Bones.CODEC);
		register("cave_camp", CaveCamp.CODEC);
	}

	private static void register(String name, MapCodec<? extends Feature> codec) {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Afterworld.id(name), codec);
	}
}

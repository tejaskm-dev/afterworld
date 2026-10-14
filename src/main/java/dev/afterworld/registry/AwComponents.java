package dev.afterworld.registry;

import com.mojang.serialization.Codec;
import dev.afterworld.Afterworld;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

public final class AwComponents {
	/** Whether a Hungry Lantern is lit. */
	public static final DataComponentType<Boolean> LIT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Afterworld.id("lit"),
		DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
	/** Stored charges in a Clockwork Heart. */
	public static final DataComponentType<Integer> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Afterworld.id("charge"),
		DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	private AwComponents() {
	}

	public static void init() {
	}
}

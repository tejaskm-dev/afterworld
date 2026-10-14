package dev.afterworld.registry;

import dev.afterworld.Afterworld;
import dev.afterworld.player.Garb;
import dev.afterworld.player.PlayerState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class AwAttachments {
	public static final AttachmentType<PlayerState> PLAYER = AttachmentRegistry.<PlayerState>create(
		Afterworld.id("player"),
		b -> b.persistent(PlayerState.CODEC).copyOnDeath().initializer(PlayerState::new)
	);

	public static final AttachmentType<MobMark> MOB = AttachmentRegistry.<MobMark>create(
		Afterworld.id("mark"),
		b -> b.persistent(MobMark.CODEC).syncWith(MobMark.STREAM_CODEC, AttachmentSyncPredicate.all())
	);

	/** Cosmetic garb, synced to everyone who can see the player. */
	public static final AttachmentType<Garb> GARB = AttachmentRegistry.<Garb>create(
		Afterworld.id("garb"),
		b -> b.persistent(Garb.CODEC).copyOnDeath().syncWith(Garb.STREAM_CODEC, AttachmentSyncPredicate.all())
	);

	/** Marks a mannequin as one of the Afterworld's people (an {@link dev.afterworld.npc.Npc} id). Synced so clients know who they can talk to. */
	public static final AttachmentType<String> NPC = AttachmentRegistry.<String>create(
		Afterworld.id("npc"),
		b -> b.persistent(com.mojang.serialization.Codec.STRING).syncWith(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all())
	);

	/** The three relics a traveller has equipped. Kept through death; synced to their owner for the Journal. */
	public static final AttachmentType<java.util.List<net.minecraft.world.item.ItemStack>> RELICS = AttachmentRegistry.<java.util.List<net.minecraft.world.item.ItemStack>>create(
		Afterworld.id("relics"),
		b -> b.persistent(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf()).copyOnDeath()
			.syncWith(net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list(3)), AttachmentSyncPredicate.targetOnly())
	);

	private AwAttachments() {
	}

	public static void init() {
	}
}

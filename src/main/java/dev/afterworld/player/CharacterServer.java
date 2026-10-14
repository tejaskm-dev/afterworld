package dev.afterworld.player;

import dev.afterworld.aspect.Aspect;
import dev.afterworld.aspect.Aspects;
import dev.afterworld.economy.Wallet;
import dev.afterworld.edict.Edict;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwGear;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Inv;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The Character screen's server side: what it shows, and the choices made on it. */
public final class CharacterServer {
	/** Edict Shards and Marks to raise an Edict to rank 2, and to rank 3. */
	public static final int[] RANK_SHARDS = {0, 0, 3, 6};
	public static final int[] RANK_MARKS = {0, 0, 150, 400};

	private CharacterServer() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.CharacterAction.TYPE, (payload, ctx) -> handle(ctx.player(), payload));
	}

	private static void handle(ServerPlayer p, AwPayloads.CharacterAction a) {
		PlayerState s = PlayerState.of(p);
		switch (a.action()) {
			case AwPayloads.CharacterAction.OPEN -> {
			}
			case AwPayloads.CharacterAction.EQUIP_ASPECT -> {
				Aspect asp = Aspect.byOrdinal(a.a());
				if (asp != null && s.aspects.contains(asp.id) && !asp.id.equals(s.aspect)) {
					s.resonance *= 0.5F;
					Aspects.equip(p, asp);
					Fx.privateSound(p, dev.afterworld.registry.AwSounds.UI_SELECT, 1.0F, 0.9F);
				}
			}
			case AwPayloads.CharacterAction.UNEQUIP_RELIC -> {
				List<ItemStack> worn = Relics.of(p);
				if (a.a() >= 0 && a.a() < worn.size() && !worn.get(a.a()).isEmpty()) {
					ItemStack old = Relics.equip(p, a.a(), ItemStack.EMPTY);
					Inv.give(p, old);
					Fx.privateSound(p, dev.afterworld.registry.AwSounds.UI_SELECT, 1.0F, 0.7F);
				}
			}
			case AwPayloads.CharacterAction.BUY_SKILL -> Skills.buy(p, a.s());
			case AwPayloads.CharacterAction.RANK_EDICT -> rankEdict(p, s, Edict.byId(a.s()));
			default -> {
			}
		}
		send(p);
	}

	private static void rankEdict(ServerPlayer p, PlayerState s, Edict e) {
		if (e == null || !s.learned.contains(e)) {
			return;
		}
		int rank = s.edictRank.getOrDefault(e.id, 1);
		if (rank >= 3) {
			return;
		}
		int shards = RANK_SHARDS[rank + 1];
		int marks = RANK_MARKS[rank + 1];
		if (Inv.count(p, AwGear.EDICT_SHARD) < shards) {
			p.sendOverlayMessage(Component.translatable("character.afterworld.need_shards", shards).withColor(0xC86B5A));
			return;
		}
		if (!Wallet.spend(p, marks)) {
			p.sendOverlayMessage(Component.translatable("shop.afterworld.too_poor").withColor(0xC86B5A));
			return;
		}
		Inv.take(p, AwGear.EDICT_SHARD, shards);
		s.edictRank.put(e.id, rank + 1);
		Fx.burst(p.level(), dev.afterworld.registry.AwParticles.of(dev.afterworld.registry.AwParticles.RUNE, e.color), p.position().add(0, 1, 0), 40, 0.6, 0.1);
		Fx.privateSound(p, dev.afterworld.registry.AwSounds.UI_AWAKEN, 1.0F, 0.8F);
		p.sendOverlayMessage(Component.translatable("character.afterworld.ranked", e.displayName(), rank + 1).withColor(e.color));
		Progress.legend(p, "milestone", "Raised " + e.displayName().getString() + " to rank " + (rank + 1) + ".");
		s.hudDirty = true;
	}

	/** Sends the screen's data: Renown, Insight, skills, relics, Aspects, Edict ranks, gifts. */
	public static void send(ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		CompoundTag t = new CompoundTag();
		t.putInt("level", Renown.level(s));
		t.putFloat("progress", Renown.progress(s));
		t.putLong("renown", s.renown);
		t.putInt("insight", s.insight);
		t.putLong("marks", s.marks);
		t.putInt("shards", Inv.count(p, AwGear.EDICT_SHARD));
		ListTag skills = new ListTag();
		for (String k : s.skills) {
			skills.add(StringTag.valueOf(k));
		}
		t.put("skills", skills);
		ListTag relics = new ListTag();
		RegistryAccess access = p.level().registryAccess();
		List<ItemStack> worn = Relics.of(p);
		for (int i = 0; i < Relics.SLOTS; i++) {
			ItemStack st = i < worn.size() ? worn.get(i) : ItemStack.EMPTY;
			relics.add(st.isEmpty() ? new CompoundTag() : ItemStack.CODEC.encodeStart(access.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), st)
				.result().orElse(new CompoundTag()));
		}
		t.put("relics", relics);
		CompoundTag ranks = new CompoundTag();
		for (Edict e : s.learned) {
			ranks.putInt(e.id, s.edictRank.getOrDefault(e.id, 1));
		}
		t.put("ranks", ranks);
		t.putInt("gifts", s.gifts);
		t.putString("discipline", s.discipline.id);
		ServerPlayNetworking.send(p, new AwPayloads.CharacterView(t));
	}
}

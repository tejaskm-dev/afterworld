package dev.afterworld.player;

import dev.afterworld.AwConfig;
import dev.afterworld.edict.AwTargeting;
import dev.afterworld.edict.Edict;
import dev.afterworld.edict.EdictSystem;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.registry.AwAttachments;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Inv;
import dev.afterworld.world.Faction;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A traveller's arrival. On first join the client shows the arrival screen (origin, discipline,
 * garb); until they step out of it the world leaves them alone. The wardrobe reuses the garb step.
 */
public final class Arrival {
	/** At most five minutes of grace while choosing. */
	private static final long GRACE_TICKS = 20L * 60 * 5;

	private Arrival() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.Arrive.TYPE, (payload, ctx) -> handle(ctx.player(), payload));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && isArriving(p)));
	}

	/** On join: opens the arrival screen for travellers who have not arrived yet. Returns true if it did. */
	static boolean onJoin(ServerPlayer p, PlayerState s) {
		if (s.arrived) {
			return false;
		}
		if (!AwConfig.get().arrivalScreen) {
			s.arrived = true;
			if (!p.hasAttached(AwAttachments.GARB)) {
				p.setAttached(AwAttachments.GARB, Garb.random(p.getRandom()));
			}
			return false;
		}
		s.arrivingUntil = p.level().getGameTime() + GRACE_TICKS;
		AwTargeting.forget(p.getUUID(), s.arrivingUntil);
		ServerPlayNetworking.send(p, new AwPayloads.OpenArrival(false, s.discipline.ordinal()));
		return true;
	}

	/** Opens just the garb step. */
	public static void openWardrobe(ServerPlayer p) {
		ServerPlayNetworking.send(p, new AwPayloads.OpenArrival(true, PlayerState.of(p).discipline.ordinal()));
	}

	public static boolean isArriving(ServerPlayer p) {
		return PlayerState.of(p).arrivingUntil > p.level().getGameTime();
	}

	private static void handle(ServerPlayer p, AwPayloads.Arrive a) {
		PlayerState s = PlayerState.of(p);
		if (a.origin() < 0 || s.arrived) {
			// The wardrobe changes clothes, never the heart.
			p.setAttached(AwAttachments.GARB, a.garb().withHeart(s.affinity));
			Fx.sound(p.level(), p.position(), AwSounds.UI_SELECT, 0.8F, 1.0F);
			return;
		}
		Origin origin = Origin.byOrdinal(a.origin());
		if (origin == Origin.NONE) {
			origin = Origin.WANDERER;
		}
		s.origin = origin;
		s.affinity = Edict.byOrdinal(a.affinity());
		s.travellerName = cleanName(a.name());
		p.setAttached(AwAttachments.GARB, a.garb().withHeart(s.affinity));
		s.arrived = true;
		s.arrivingUntil = 0;
		AwTargeting.remember(p.getUUID());
		Origin chosen = origin;
		// The arrival screen has already said all of this, so it happens without notices or chat.
		Progress.quietly(() -> {
			for (Faction f : Faction.VALUES) {
				Progress.rep(p, f, chosen.repWith(f));
			}
			Discipline d = Discipline.byOrdinal(a.discipline());
			if (d != Discipline.NONE && s.discipline == Discipline.NONE) {
				Disciplines.choose(p, d);
			}
			// The Edict chosen as "what lies within" is yours from the start, ready in a slot.
			if (s.affinity != null && !s.learned.contains(s.affinity)) {
				Progress.learn(p, s.affinity);
			}
			Progress.codex(p, "origin." + chosen.id);
			if (s.affinity != null) {
				Progress.codex(p, "edict." + s.affinity.id);
			}
		});
		// The starting kit is bound to its traveller: it comes back after every death.
		for (ItemStack stack : origin.kit()) {
			Inv.give(p, Soulbound.mark(stack.copy()));
		}
		Progress.legend(p, "milestone", (s.travellerName.isEmpty() ? "Arrived as " : "Arrived as " + s.travellerName + ", ")
			+ article(origin.displayName().getString()) + (s.affinity != null ? ", with " + s.affinity.displayName().getString() + " within." : "."));
		Fx.burst(p.level(), ParticleTypes.END_ROD, p.position().add(0, 1, 0), 30, 0.6, 0.06);
		Fx.sound(p.level(), p.position(), AwSounds.UI_DISCOVERY, 1.0F, 0.9F);
		Welcome.intro(p, s);
		s.hudDirty = true;
		s.questDirty = true;
		dev.afterworld.util.Scheduler.after(40, () -> dev.afterworld.quest.Quests.announce(p));
		EdictSystem.syncHud(p, s);
	}

	/** A name to be called by: letters, digits and a little punctuation, at most sixteen characters. */
	public static String cleanName(String raw) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < raw.length() && b.length() < 16; i++) {
			char c = raw.charAt(i);
			if (Character.isLetterOrDigit(c) || c == '\'' || c == '-' || c == '.' || c == ' ' && b.length() > 0 && b.charAt(b.length() - 1) != ' ') {
				b.append(c);
			}
		}
		return b.toString().trim();
	}

	private static String article(String noun) {
		return ("AEIOU".indexOf(Character.toUpperCase(noun.charAt(0))) >= 0 ? "an " : "a ") + noun;
	}
}

package dev.afterworld.rumour;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.AwConfig;
import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwItems;
import dev.afterworld.registry.MobMark;
import dev.afterworld.site.Site;
import dev.afterworld.site.SiteKind;
import dev.afterworld.site.Sites;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Inv;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.BossStates;
import dev.afterworld.world.Faction;
import dev.afterworld.world.Names;
import dev.afterworld.world.Settlement;
import dev.afterworld.world.Settlements;
import dev.afterworld.world.WorldEvent;
import dev.afterworld.world.WorldSim;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Rumourcraft. Every rumour is generated from a real world condition and points at a real place,
 * described by direction and distance rather than a waypoint. Some are wrong; all of them lead
 * somewhere.
 */
public final class Rumours {
	private static final Object2LongOpenHashMap<UUID> ASKED = new Object2LongOpenHashMap<>();
	private static final String[] GOSSIP = {
		"The census lists a child called Wren. Nobody here has ever had a child called Wren.",
		"My grandmother says the moon used to be closer. She also says she's my grandmother, so.",
		"There's a ghost in the old mill who won't haunt anyone until his rent is paid. Principled, I suppose.",
		"Don't whistle near the well. It whistles back, and it's better at it.",
		"The Crown's tax collector came twice this week. The second one didn't know about the first.",
		"Every night the bell in the square rings once. Every morning nobody remembers it.",
		"The Bureau says nothing unusual happened here. They say it very often.",
		"I sold a map to the Guild once. They paid me for the parts that weren't there."
	};

	private Rumours() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer sp) || MobMark.has(entity, MobMark.GHOST)) {
				return InteractionResult.PASS;
			}
			if (entity instanceof Villager v && !v.isBaby()) {
				ask(sp, v.getUUID(), speakerName(v), settlementNear(sp));
				return InteractionResult.SUCCESS_SERVER;
			}
			if (entity instanceof WanderingTrader t && !MobMark.has(t, MobMark.ESCORT)) {
				ask(sp, t.getUUID(), t.getCustomName() != null ? t.getCustomName().getString() : "a wandering trader", null);
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});
		Sites.RESOLVE_LISTENERS.add(Rumours::onSiteResolved);
		Sites.onPresenceAll((p, s, site, d2) -> arrive(p, s, site, d2));
	}

	private static Settlement settlementNear(ServerPlayer p) {
		return Settlements.at(p.level(), p.blockPosition());
	}

	private static String speakerName(Villager v) {
		RandomSource r = RandomSource.create(v.getUUID().getLeastSignificantBits());
		String prof = v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
		String role = switch (prof) {
			case "none", "nitwit" -> "a villager";
			default -> "the " + prof;
		};
		return Names.person(r) + ", " + role;
	}

	// ------------------------------------------------------------------ asking

	public static void ask(ServerPlayer p, UUID speaker, String name, Settlement here) {
		ServerLevel level = p.level();
		long now = level.getGameTime();
		long last = ASKED.getOrDefault(speaker, -100000L);
		if (ASKED.size() > 2000) {
			ASKED.clear();
		}
		PlayerState s = PlayerState.of(p);
		if (now - last < 6000) {
			say(p, name, "I've told you all I know. Come back tomorrow.");
			return;
		}
		ASKED.put(speaker, now);
		if (s.activeRumourCount() >= AwConfig.get().maxActiveRumours) {
			say(p, name, gossip(level.getRandom()));
			p.sendOverlayMessage(Component.translatable("message.afterworld.rumours_full").withColor(0x9A9488));
			return;
		}
		RumourEntry r = generate(p, s, name, here);
		if (r == null) {
			say(p, name, gossip(level.getRandom()));
			return;
		}
		say(p, name, r.text);
		add(p, s, r);
	}

	static void say(ServerPlayer p, String name, String line) {
		p.sendSystemMessage(Component.literal("<" + name + "> ").withColor(0xC9B98F).append(Component.literal(line).withColor(0xE8E0CC)));
	}

	static String gossip(RandomSource r) {
		return GOSSIP[r.nextInt(GOSSIP.length)];
	}

	public static void add(ServerPlayer p, PlayerState s, RumourEntry r) {
		r.id = s.nextRumourId++;
		r.day = Progress.day(p);
		s.rumours.add(r);
		while (s.rumours.size() > 40) {
			RumourEntry oldest = null;
			for (RumourEntry e : s.rumours) {
				if (!e.active()) {
					oldest = e;
					break;
				}
			}
			s.rumours.remove(oldest != null ? oldest : s.rumours.get(0));
		}
		if (s.trackedRumour < 0) {
			s.trackedRumour = r.id;
		}
		s.questDirty = true;
		Progress.notice(p, AwPayloads.Notice.RUMOUR, Component.translatable("notice.afterworld.rumour", r.title));
		Progress.codex(p, "rumour.first");
		p.level().playSound(null, p.blockPosition(), AwSounds.UI_RUMOUR.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** Chooses what this person would plausibly know about, from the world as it is now. */
	static RumourEntry generate(ServerPlayer p, PlayerState s, String source, Settlement here) {
		MinecraftServer server = p.level().getServer();
		AfterworldData data = AfterworldData.get(server);
		RandomSource rand = p.level().getRandom();
		String dim = p.level().dimension().identifier().toString();
		double ox = here != null ? here.x : p.getX();
		double oz = here != null ? here.z : p.getZ();
		String place = here != null ? here.name : "here";
		List<Site> candidates = new ArrayList<>();

		if (here != null && here.safety < 45) {
			Site lair = nearestUnknown(data, s, SiteKind.LAIR, dim, ox, oz, 600);
			if (lair == null && AwConfig.get().generateSites) {
				lair = Sites.createNear(server, SiteKind.LAIR, here.x, here.z, 120, 260, rand, here.id, Faction.CROWN.ordinal(), -1);
			}
			if (lair != null) {
				return fromSite(lair, RumourKind.THREAT, source, "Something has been taking our livestock. The tracks go " + dir(lair, ox, oz) + ", "
					+ Names.distance(dist(lair, ox, oz)) + " from " + place + ".", "Something Hunts near " + place, 55, RumourEntry.ACCURATE, -1);
			}
		}
		for (WorldEvent e : data.events) {
			if (e.active && e.kind.equals(WorldEvent.SILENT_CARAVAN) && here != null && e.settlement == here.id) {
				Site site = data.sites.get(e.site);
				if (site != null && site.open() && !s.hasActiveRumourForSite(site.id)) {
					return fromSite(site, RumourKind.CARAVAN, source, "The caravan's late. Three days late. It always comes up the road from the "
						+ dir(site, ox, oz) + ".", "The Silent Caravan", 70, RumourEntry.ACCURATE, -1);
				}
			}
		}
		if (data.pilgrim.state == BossStates.Pilgrim.WANDERING && !hasKind(s, RumourKind.PILGRIM)) {
			Site shrine = nearestShrine(data, ox, oz);
			if (shrine != null && rand.nextFloat() < 0.6F) {
				return fromSite(shrine, RumourKind.PILGRIM, source, "There's a stone giant that walks between the old shrines without touching the ground. The nearest shrine is "
					+ dir(shrine, ox, oz) + ", " + Names.distance(dist(shrine, ox, oz)) + ".", "The Pilgrim Without Feet", 45, RumourEntry.ACCURATE, -1);
			}
		}
		Site village = firstOpen(data, SiteKind.UNREMEMBERED);
		if (village != null && !s.hasActiveRumourForSite(village.id) && !hasResolvedSite(s, village.id) && rand.nextFloat() < 0.5F) {
			return fromSite(village, RumourKind.UNREMEMBERED, source, "A merchant swore a village appears in the woods to the " + dir(village, ox, oz)
				+ " when there's thunder. Lit windows, people at the well. Gone by morning.", "The Village That Appears at Night", 35, RumourEntry.INCOMPLETE, -1);
		}
		Site cathedral = firstOpen(data, SiteKind.CATHEDRAL);
		if (cathedral != null && !s.hasActiveRumourForSite(cathedral.id) && s.learned.size() >= 2 && rand.nextFloat() < 0.5F) {
			return fromSite(cathedral, RumourKind.CATHEDRAL, source, "Nobody goes to the Hollow Cathedral, " + Names.distance(dist(cathedral, ox, oz)) + " to the "
				+ dir(cathedral, ox, oz) + ". Something drags a bell around in there.", "The Bell in the Hollow Cathedral", 50, RumourEntry.ACCURATE, -1);
		}
		Site vault = firstOpen(data, SiteKind.VAULT);
		if (vault != null && !s.hasActiveRumourForSite(vault.id) && s.learned.size() >= 4 && rand.nextFloat() < 0.5F) {
			return fromSite(vault, RumourKind.VAULT, source, "The Guild lost three surveyors at a clocktower to the " + dir(vault, ox, oz)
				+ ". They came back a few seconds older. One of them came back twice.", "The Vault of Seconds", 40, RumourEntry.ACCURATE, -1);
		}
		Site anomaly = nearestUnknown(data, s, SiteKind.ANOMALY, dim, ox, oz, 1000);
		Site cache = nearestUnknown(data, s, SiteKind.CACHE, dim, ox, oz, 900);
		if (anomaly != null) {
			candidates.add(anomaly);
		}
		if (cache != null) {
			candidates.add(cache);
		}
		if (candidates.isEmpty() && AwConfig.get().generateSites) {
			boolean lie = rand.nextFloat() < 0.18F;
			SiteKind kind = lie || rand.nextBoolean() ? SiteKind.CACHE : SiteKind.ANOMALY;
			Site made = Sites.createNear(server, kind, (int) ox, (int) oz, 160, 480, rand, here == null ? -1 : here.id, -1,
				kind == SiteKind.ANOMALY ? WorldSim.anomalyEdict(rand).ordinal() : -1);
			if (made != null) {
				if (lie) {
					made.flags |= Sites.F_LIAR;
					data.setDirty();
				}
				candidates.add(made);
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}
		Site target = candidates.get(rand.nextInt(candidates.size()));
		boolean lie = (target.flags & Sites.F_LIAR) != 0;
		int reliability = lie ? RumourEntry.FALSE : rand.nextFloat() < 0.25F ? RumourEntry.INCOMPLETE : RumourEntry.ACCURATE;
		String text;
		String title;
		if (target.kind == SiteKind.ANOMALY) {
			title = "Strangeness " + capital(dir(target, ox, oz)) + " of " + place;
			text = "Past the " + dir(target, ox, oz) + " fields the rules run thin. " + flavour(target) + (reliability == RumourEntry.INCOMPLETE ? "" : " " + capital(Names.distance(dist(target, ox, oz))) + ", maybe.");
		} else if (lie) {
			title = "Buried Silver";
			text = "There's silver buried in an old cellar to the " + dir(target, ox, oz) + ". Go now, before anyone else hears about it.";
		} else {
			title = "An Old Cache";
			text = "My uncle kept a cache in a cellar to the " + dir(target, ox, oz) + ", " + Names.distance(dist(target, ox, oz)) + ". He never came back for it.";
		}
		return fromSite(target, target.kind == SiteKind.ANOMALY ? RumourKind.ANOMALY : RumourKind.CACHE, source, text, title, lie ? 55 : 40, reliability, -1);
	}

	static String flavour(Site site) {
		var e = dev.afterworld.edict.Edict.byOrdinal(site.data);
		if (e == null) {
			return "Birds fly around it, not over it.";
		}
		return switch (e) {
			case WEIGHTLESSNESS -> "Stones float there like they've forgotten which way is down.";
			case ECHOES -> "Every sound there happens twice.";
			case STILLNESS -> "The stream there stopped moving last spring. It's still wet.";
			case WITNESS -> "You feel watched there, by something that's taking notes.";
			case FORGOTTEN -> "People go there and can't remember why they went.";
			case HUNGER -> "The bones there are always fresh, and never from anything that died there.";
			case RETURN -> "Whatever you throw there comes back to you. Whatever you leave there, too.";
			case HOSPITALITY -> "There's a table set for guests. The food is never cold and the chairs are never empty for long.";
			default -> "Birds fly around it, not over it.";
		};
	}

	static RumourEntry fromSite(Site site, RumourKind kind, String source, String text, String title, int confidence, int reliability, int faction) {
		RumourEntry r = new RumourEntry();
		r.kind = kind;
		r.title = title;
		r.text = text;
		r.source = source;
		r.sourceConfidence = confidence;
		r.reliability = reliability;
		r.siteId = site.id;
		r.faction = faction;
		r.x = site.x;
		r.y = site.y == Integer.MIN_VALUE ? 64 : site.y;
		r.z = site.z;
		r.dim = site.dim;
		return r;
	}

	// ------------------------------------------------------------------ contracts

	/** A faction offers its current contract as a high-confidence rumour. */
	public static void contract(ServerPlayer p, Faction f) {
		PlayerState s = PlayerState.of(p);
		for (RumourEntry r : s.rumours) {
			if (r.active() && r.faction == f.ordinal()) {
				p.sendOverlayMessage(Component.translatable("message.afterworld.contract_active").withColor(0x9A9488));
				return;
			}
		}
		if (s.rep(f) <= -15) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.contract_refused", f.displayName()).withColor(0xC4553B));
			return;
		}
		MinecraftServer server = p.level().getServer();
		AfterworldData data = AfterworldData.get(server);
		RandomSource rand = p.level().getRandom();
		String dim = p.level().dimension().identifier().toString();
		Site target = switch (f) {
			case CROWN -> nearestUnknown(data, s, SiteKind.LAIR, dim, p.getX(), p.getZ(), 1500);
			case GUILD -> nearestUnknown(data, s, SiteKind.CACHE, dim, p.getX(), p.getZ(), 1500);
			case COURT -> firstOpen(data, SiteKind.UNREMEMBERED);
			case BUREAU -> nearestUnknown(data, s, SiteKind.ANOMALY, dim, p.getX(), p.getZ(), 1500);
		};
		if (target == null && AwConfig.get().generateSites && f != Faction.COURT) {
			SiteKind kind = f == Faction.CROWN ? SiteKind.LAIR : f == Faction.GUILD ? SiteKind.CACHE : SiteKind.ANOMALY;
			target = Sites.createNear(server, kind, (int) p.getX(), (int) p.getZ(), 220, 520, rand, -1, f.ordinal(),
				kind == SiteKind.ANOMALY ? WorldSim.anomalyEdict(rand).ordinal() : -1);
		}
		if (target == null) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.contract_none", f.displayName()).withColor(0x9A9488));
			return;
		}
		String leader = data.faction(f).leader;
		String what = switch (f) {
			case CROWN -> "A beast has been marked for death " + dir(target, p.getX(), p.getZ()) + " of here. The Crown pays for its head.";
			case GUILD -> "The Guild wants the old cache " + dir(target, p.getX(), p.getZ()) + " of here charted before anyone else finds it.";
			case COURT -> "The Morrow Court asks that someone visit the village that appears in thunder, " + dir(target, p.getX(), p.getZ()) + " of here, and listen.";
			case BUREAU -> "The Bureau requests that the anomaly " + dir(target, p.getX(), p.getZ()) + " of here be sealed. Quietly.";
		};
		RumourEntry r = fromSite(target, RumourKind.CONTRACT, leader + ", for " + f.displayName().getString(), what,
			f.displayName().getString() + " Contract", 85, RumourEntry.ACCURATE, f.ordinal());
		add(p, s, r);
		p.sendSystemMessage(Component.literal(what).withColor(f.color));
	}

	// ------------------------------------------------------------------ progress

	private static void arrive(ServerPlayer p, PlayerState s, Site site, double d2) {
		int r = site.kind.radius + 6;
		if (d2 > (double) r * r) {
			return;
		}
		for (RumourEntry e : s.rumours) {
			if (e.active() && e.siteId == site.id && !e.clues.contains("arrived")) {
				e.clues.add("arrived");
				if (e.reliability == RumourEntry.INCOMPLETE) {
					e.clues.add("The rumour left out what was really here.");
				}
				Progress.notice(p, AwPayloads.Notice.RUMOUR, Component.translatable("notice.afterworld.rumour_found", e.title));
			}
		}
	}

	private static void onSiteResolved(MinecraftServer server, Site site, ServerPlayer by) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerState s = PlayerState.of(p);
			for (RumourEntry e : s.rumours) {
				if (!e.active() || e.siteId != site.id) {
					continue;
				}
				if (by != null && by == p) {
					settle(p, s, e, site);
				} else {
					e.state = RumourEntry.RESOLVED;
					e.reliability = RumourEntry.OUTDATED;
					e.outcome = (by != null ? "Someone else got there first: " + by.getName().getString() + "." : site.outcome);
					Progress.notice(p, AwPayloads.Notice.RUMOUR, Component.translatable("notice.afterworld.rumour_outdated", e.title));
					Progress.codex(p, "rumour.outdated");
				}
			}
		}
	}

	private static void settle(ServerPlayer p, PlayerState s, RumourEntry e, Site site) {
		e.state = RumourEntry.RESOLVED;
		e.outcome = site.outcome;
		p.giveExperiencePoints(30);
		if (e.reliability == RumourEntry.FALSE || (site.flags & Sites.F_LIAR) != 0) {
			e.reliability = RumourEntry.FALSE;
			String motive = new String[] {
				"The Crown's men paid me to send travellers away from the north road.",
				"The Guild wanted someone to find this cellar before the Bureau did.",
				"I just wanted to see if anyone would actually go. Sorry about the walk."}[Math.floorMod((int) site.seed, 3)];
			e.outcome = "The silver was a lie. A note in the cellar reads: “" + motive + "”";
			p.sendSystemMessage(Component.literal(e.outcome).withStyle(ChatFormatting.ITALIC).withColor(0xC9B98F));
			Progress.codex(p, "rumour.false");
			Progress.rep(p, Faction.GUILD, 2);
			Inv.give(p, new ItemStack(AwItems.CHRONICLE_PAGE, 2));
		}
		if (e.faction >= 0) {
			Faction f = Faction.byOrdinal(e.faction);
			Progress.rep(p, f, 10);
			Inv.give(p, new ItemStack(Items.EMERALD, 8 + p.getRandom().nextInt(6)));
			Progress.legend(p, "milestone", "Fulfilled a contract for " + f.displayName().getString() + ".");
			p.sendSystemMessage(Component.translatable("message.afterworld.contract_paid", f.displayName()).withColor(f.color));
		}
		Progress.notice(p, AwPayloads.Notice.RUMOUR, Component.translatable("notice.afterworld.rumour_resolved", e.title));
		Fx.burst(p.level(), ParticleTypes.HAPPY_VILLAGER, p.position().add(0, 1, 0), 8, 0.4, 0.05);
		if (s.trackedRumour == e.id) {
			s.trackedRumour = -1;
		}
	}

	// ------------------------------------------------------------------ helpers

	static Site nearestUnknown(AfterworldData data, PlayerState s, SiteKind kind, String dim, double x, double z, double max) {
		Site best = null;
		double bestD = max * max;
		for (Site site : data.sites.values()) {
			if (site.kind != kind || !site.open() || !site.dim.equals(dim) || s.hasActiveRumourForSite(site.id) || hasResolvedSite(s, site.id)) {
				continue;
			}
			double d = (site.x - x) * (site.x - x) + (site.z - z) * (site.z - z);
			if (d < bestD) {
				bestD = d;
				best = site;
			}
		}
		return best;
	}

	static Site firstOpen(AfterworldData data, SiteKind kind) {
		for (Site site : data.sites.values()) {
			if (site.kind == kind && site.open()) {
				return site;
			}
		}
		return null;
	}

	static Site nearestShrine(AfterworldData data, double x, double z) {
		Site best = null;
		double bestD = Double.MAX_VALUE;
		for (int id : data.pilgrim.shrines) {
			Site s = data.sites.get(id);
			if (s != null) {
				double d = (s.x - x) * (s.x - x) + (s.z - z) * (s.z - z);
				if (d < bestD) {
					bestD = d;
					best = s;
				}
			}
		}
		return best;
	}

	static boolean hasKind(PlayerState s, RumourKind kind) {
		for (RumourEntry r : s.rumours) {
			if (r.active() && r.kind == kind) {
				return true;
			}
		}
		return false;
	}

	static boolean hasResolvedSite(PlayerState s, int site) {
		for (RumourEntry r : s.rumours) {
			if (r.siteId == site && r.state == RumourEntry.RESOLVED) {
				return true;
			}
		}
		return false;
	}

	static String dir(Site s, double fromX, double fromZ) {
		return Names.direction(s.x - fromX, s.z - fromZ);
	}

	static double dist(Site s, double fromX, double fromZ) {
		return Math.sqrt((s.x - fromX) * (s.x - fromX) + (s.z - fromZ) * (s.z - fromZ));
	}

	static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}

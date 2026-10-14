package dev.afterworld.site;

import dev.afterworld.registry.AwSounds;
import dev.afterworld.edict.Edict;
import dev.afterworld.entity.boss.BellRingerSystem;
import dev.afterworld.player.BondType;
import dev.afterworld.player.Bonds;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.registry.AwItems;
import dev.afterworld.registry.MobMark;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Inv;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.Chronicle;
import dev.afterworld.world.Faction;
import dev.afterworld.world.Names;
import dev.afterworld.world.Settlement;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

/**
 * The Village That Appears at Night. Its people are echoes of a village erased by an ancient
 * Edict, and every night they forget another part of their lives. What becomes of them is a choice.
 */
public final class Unremembered {
	public static final int ROLE_ECHO = 0;
	public static final int ROLE_ARCHIVIST = 1;
	public static final int ROLE_LANDLORD = 2;
	private static final Int2ObjectOpenHashMap<List<UUID>> GHOSTS = new Int2ObjectOpenHashMap<>();
	private static final String[] LINES = {
		"I had a daughter, I think. Her name was… it's gone again.",
		"The well water tastes of yesterday. Every day.",
		"Do you know what we were celebrating? There's bunting, but nobody remembers why.",
		"I keep setting four places at the table. There are three of us. I think.",
		"The miller says I owe him for flour. I don't remember borrowing it, but then, I wouldn't.",
		"Thunder is when we're allowed to be here. I don't know who allows it."
	};

	private Unremembered() {
	}

	public static void init() {
		Sites.onPresence(SiteKind.UNREMEMBERED, Unremembered::presence);
		Sites.BUILT_LISTENERS.add((level, site) -> {
			if (site.kind == SiteKind.UNREMEMBERED && level.getBlockEntity(SiteBuilder.censusChest(site)) instanceof RandomizableContainer c) {
				ItemStack census = new ItemStack(AwItems.TORN_CENSUS);
				census.set(DataComponents.LORE, new ItemLore(List.of(
					Component.literal("Lists thirty-one residents of " + site.name + "."),
					Component.literal("Eleven of them were never born."),
					BellRingerSystem.censusClue(level))));
				c.setItem(4, census);
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			MobMark mark = MobMark.of(entity);
			if (!mark.has(MobMark.GHOST)) {
				return InteractionResult.PASS;
			}
			talk(sp, entity, mark.data() >> 2, mark.data() & 3);
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	// ------------------------------------------------------------------ the nightly village

	private static void presence(ServerPlayer p, PlayerState ps, Site site, double d2) {
		ServerLevel level = p.level();
		if (d2 < 30 * 30) {
			if (Progress.codex(p, "place.unremembered")) {
				Progress.legend(p, "discovery", "Found the ruins of " + site.name + ".");
			}
		}
		boolean open = site.open();
		boolean night = level.isDarkOutside() || level.isThundering();
		List<UUID> ghosts = GHOSTS.get(site.id);
		if (!open || !night) {
			if (ghosts != null) {
				dismiss(level, site, false);
			}
			return;
		}
		if (ghosts != null || d2 > 44 * 44) {
			return;
		}
		RandomSource r = RandomSource.create(site.seed);
		List<UUID> ids = new ArrayList<>();
		int[][] spots = {{-10, -9}, {9, -10}, {-10, 9}, {10, 9}, {2, 3}, {-3, -13}};
		for (int i = 0; i < spots.length; i++) {
			int role = i == 0 ? ROLE_ARCHIVIST : i == 1 && !ps.hasBond(BondType.LANDLORD) ? ROLE_LANDLORD : ROLE_ECHO;
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (v == null) {
				continue;
			}
			BlockPos at = site.pos().offset(spots[i][0], 1, spots[i][1]);
			v.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, r.nextFloat() * 360, 0);
			ResourceKey<VillagerProfession> prof = role == ROLE_ARCHIVIST ? VillagerProfession.LIBRARIAN : role == ROLE_LANDLORD ? VillagerProfession.NITWIT
				: new ResourceKey[] {VillagerProfession.FARMER, VillagerProfession.MASON, VillagerProfession.SHEPHERD, VillagerProfession.FISHERMAN}[i % 4];
			v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(), prof));
			String name = role == ROLE_ARCHIVIST ? "The Archivist" : role == ROLE_LANDLORD ? "Mr. Hollowell" : Names.person(r);
			v.setCustomName(Component.literal(name).withColor(0xB8C8D8));
			v.setPermanentlyInvulnerable(true);
			v.setPersistenceRequired();
			v.setSilent(true);
			MobMark.set(v, new MobMark(MobMark.GHOST, Optional.empty(), 0, (site.id << 2) | role));
			level.addFreshEntity(v);
			ids.add(v.getUUID());
			Fx.burst(level, ParticleTypes.SOUL, v.position().add(0, 1, 0), 10, 0.3, 0.02);
		}
		GHOSTS.put(site.id, ids);
		Fx.sound(level, p.position(), AwSounds.WORLD_WHISPER, 1.0F, 0.9F);
		p.sendSystemMessage(Component.translatable("message.afterworld.unremembered_appear", site.name).withColor(0xB8C8D8));
	}

	private static void dismiss(ServerLevel level, Site site, boolean freed) {
		List<UUID> ids = GHOSTS.remove(site.id);
		if (ids == null) {
			return;
		}
		for (UUID id : ids) {
			Entity e = level.getEntity(id);
			if (e != null) {
				Fx.burst(level, freed ? ParticleTypes.END_ROD : ParticleTypes.SOUL, e.position().add(0, 1, 0), 12, 0.3, 0.03);
				e.discard();
			}
		}
		// Ghosts that were saved with their chunk are swept up too.
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(site.pos()).inflate(40), v -> MobMark.has(v, MobMark.GHOST))) {
			v.discard();
		}
	}

	// ------------------------------------------------------------------ conversations

	private static void talk(ServerPlayer p, Entity ghost, int siteId, int role) {
		ServerLevel level = p.level();
		Site site = AfterworldData.get(level.getServer()).sites.get(siteId);
		String name = ghost.getCustomName() == null ? "Someone" : ghost.getCustomName().getString();
		if (site == null || !site.open()) {
			say(p, name, "Thank you. I think. I don't remember what for.");
			return;
		}
		switch (role) {
			case ROLE_ARCHIVIST -> {
				Progress.codex(p, "npc.archivist");
				if (!has(p, AwItems.TORN_CENSUS)) {
					say(p, name, "Every night I forget another page of the census. Last night it was the miller's children. The book was in my house, by the shelves. Will you find it before I forget that too?");
					return;
				}
				say(p, name, "You found it. Then you know what we are. Decide what becomes of us; I've forgotten how to.");
				p.sendSystemMessage(option("Restore their history", "Needs the Torn Census and 3 Chronicle Pages.", "restore", site.id)
					.append(Component.literal("  ")).append(option("Sell the census to the Crown", "The Crown pays well for records.", "sell", site.id))
					.append(Component.literal("  ")).append(option("Let them begin anew", "Free them from who they were.", "anew", site.id)));
			}
			case ROLE_LANDLORD -> {
				Progress.codex(p, "npc.landlord");
				if (PlayerState.of(p).hasBond(BondType.LANDLORD)) {
					say(p, name, "Rent is due on the third day. I am not a monster. I am a ghost. There is a difference, legally.");
				} else {
					say(p, name, "A living tenant! I have a room. It's very quiet. Mostly. Sign here, and I won't let anything happen to you on my premises.");
					Bonds.offer(p, PlayerState.of(p), BondType.LANDLORD);
				}
			}
			default -> say(p, name, LINES[Math.floorMod(ghost.getUUID().hashCode() + (int) (level.getGameTime() / 2400), LINES.length)]);
		}
	}

	private static MutableComponent option(String label, String hint, String choice, int site) {
		return Component.literal("[" + label + "]").withStyle(s -> s.withColor(0xE8A33D)
			.withClickEvent(new ClickEvent.RunCommand("/afterworld choice census_" + choice + " " + site))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal(hint))));
	}

	private static void say(ServerPlayer p, String name, String line) {
		p.sendSystemMessage(Component.literal("<" + name + "> ").withColor(0xB8C8D8).append(Component.literal(line).withColor(0xDCE6F0)));
	}

	private static boolean has(ServerPlayer p, net.minecraft.world.item.Item item) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}

	private static boolean take(ServerPlayer p, net.minecraft.world.item.Item item, int count) {
		if (p.isCreative()) {
			return true;
		}
		var inv = p.getInventory();
		int have = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				have += inv.getItem(i).getCount();
			}
		}
		if (have < count) {
			return false;
		}
		for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item)) {
				int n = Math.min(count, st.getCount());
				st.shrink(n);
				count -= n;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------ choices

	/** Handles /afterworld choice census_restore|census_sell|census_anew <site>. */
	public static boolean choose(ServerPlayer p, String choice, int siteId) {
		ServerLevel level = p.level();
		AfterworldData data = AfterworldData.get(level.getServer());
		Site site = data.sites.get(siteId);
		if (site == null || site.kind != SiteKind.UNREMEMBERED || !site.open() || p.blockPosition().distSqr(site.pos()) > 48 * 48) {
			return false;
		}
		if (!has(p, AwItems.TORN_CENSUS)) {
			p.sendSystemMessage(Component.translatable("message.afterworld.need_census").withColor(0xC4553B));
			return false;
		}
		switch (choice) {
			case "census_restore" -> {
				if (!p.isCreative() && countOf(p, AwItems.CHRONICLE_PAGE) < 3) {
					p.sendSystemMessage(Component.translatable("message.afterworld.need_pages").withColor(0xC4553B));
					return false;
				}
				take(p, AwItems.TORN_CENSUS, 1);
				take(p, AwItems.CHRONICLE_PAGE, 3);
				restore(level, p, site, data);
			}
			case "census_sell" -> {
				take(p, AwItems.TORN_CENSUS, 1);
				sell(level, p, site, data);
			}
			case "census_anew" -> {
				take(p, AwItems.TORN_CENSUS, 1);
				anew(level, p, site, data);
			}
			default -> {
				return false;
			}
		}
		Progress.codex(p, "quest.unremembered");
		return true;
	}

	private static int countOf(ServerPlayer p, net.minecraft.world.item.Item item) {
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).is(item)) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	private static Settlement register(AfterworldData data, Site site, int state, String leader) {
		Settlement s = new Settlement();
		s.id = data.nextSettlementId++;
		s.key = -site.id;
		s.name = site.name;
		s.x = site.x;
		s.y = site.pos().getY();
		s.z = site.z;
		s.state = state;
		s.leader = leader;
		s.patron = Faction.COURT.ordinal();
		s.safety = 60;
		s.prosperity = state == Settlement.REMEMBERED ? 70 : 40;
		data.settlements.put(s.key, s);
		return s;
	}

	private static void restore(ServerLevel level, ServerPlayer p, Site site, AfterworldData data) {
		dismiss(level, site, true);
		RandomSource r = RandomSource.create(site.seed);
		for (int i = 0; i < 3; i++) {
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (v != null) {
				BlockPos at = site.pos().offset(-2 + i * 2, 1, 3);
				v.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
				v.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
				v.setCustomName(Component.literal(Names.person(r)));
				v.setPersistenceRequired();
				level.addFreshEntity(v);
			}
		}
		WanderingTrader archivist = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
		if (archivist != null) {
			BlockPos at = site.pos().offset(-10, 1, -10);
			archivist.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			archivist.setCustomName(Component.literal("The Archivist").withColor(0xB8C8D8));
			archivist.setCustomNameVisible(true);
			archivist.setDespawnDelay(Integer.MAX_VALUE / 2);
			archivist.setPersistenceRequired();
			var offers = archivist.getOffers();
			offers.clear();
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 14), Optional.of(new ItemCost(AwItems.CHRONICLE_PAGE, 2)), new ItemStack(AwItems.fragment(Edict.WITNESS)), 2, 20, 0.05F));
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 14), Optional.of(new ItemCost(AwItems.CHRONICLE_PAGE, 2)), new ItemStack(AwItems.fragment(Edict.FORGOTTEN)), 2, 20, 0.05F));
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10), Optional.of(new ItemCost(AwItems.GRAVE_WAX, 2)), new ItemStack(AwItems.fragment(Edict.ECHOES)), 2, 20, 0.05F));
			offers.add(new MerchantOffer(new ItemCost(Items.PAPER, 6), new ItemStack(AwItems.CHRONICLE_PAGE, 2), 12, 4, 0.05F));
			level.addFreshEntity(archivist);
		}
		register(data, site, Settlement.REMEMBERED, "The Archivist");
		Sites.resolve(p, site, "Its history was restored by " + p.getName().getString() + ".");
		Progress.rep(p, Faction.COURT, 18);
		Inv.give(p, new ItemStack(AwItems.fragment(Edict.ECHOES)));
		Progress.legend(p, "choice", "Restored the missing history of " + site.name + ". Its people remember themselves now.");
		Chronicle.record(level.getServer(), "The Census of " + site.name + " Restored", p.getName().getString() + " restored the missing history of "
			+ site.name + ". Its people remember their own names, and the village has become a place where lost records are traded.", site.name,
			Faction.COURT.ordinal(), p.getName().getString(), "site:" + site.id);
		Fx.sound(level, p.position(), SoundEvents.BELL_RESONATE, 1.5F, 1.2F);
	}

	private static void sell(ServerLevel level, ServerPlayer p, Site site, AfterworldData data) {
		dismiss(level, site, false);
		for (int i = 0; i < 3; i++) {
			Vex vex = EntityTypes.VEX.create(level, EntitySpawnReason.EVENT);
			if (vex != null) {
				vex.snapTo(site.x + i * 2 - 2, site.pos().getY() + 3, site.z, 0, 0);
				vex.finalizeSpawn(level, level.getCurrentDifficultyAt(site.pos()), EntitySpawnReason.EVENT, null);
				vex.setLimitedLife(900);
				vex.setCustomName(Component.literal("Restless Echo").withColor(0xB8C8D8));
				vex.setTarget(p);
				level.addFreshEntity(vex);
			}
		}
		Inv.give(p, new ItemStack(Items.EMERALD, 20));
		Progress.rep(p, Faction.CROWN, 12);
		Progress.rep(p, Faction.COURT, -15);
		Sites.resolve(p, site, "Its census was sold to the Crown by " + p.getName().getString() + ".");
		Progress.legend(p, "choice", "Sold the census of " + site.name + " to the Crown. Its echoes did not go quietly.");
		Chronicle.record(level.getServer(), "The Crown Buys a Census", "The Crown acquired the census of " + site.name + " from " + p.getName().getString()
			+ ". The village no longer appears in thunderstorms.", site.name, Faction.CROWN.ordinal(), p.getName().getString(), "site:" + site.id);
		Chronicle.missing(level.getServer(), "What the Crown Wanted With It", "The Crown has not said why it wanted the census.",
			"Eleven names on it belong to people the Crown would prefer had never been born.", site.name, Faction.CROWN.ordinal(), "census_sold");
	}

	private static void anew(ServerLevel level, ServerPlayer p, Site site, AfterworldData data) {
		dismiss(level, site, true);
		RandomSource r = RandomSource.create(site.seed ^ 77);
		String leader = Names.person(r);
		for (int i = 0; i < 3; i++) {
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (v != null) {
				BlockPos at = site.pos().offset(i * 2 - 2, 1, -3);
				v.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
				v.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
				v.setPersistenceRequired();
				level.addFreshEntity(v);
			}
		}
		register(data, site, Settlement.ANEW, leader);
		Inv.give(p, new ItemStack(AwItems.GRAVE_WAX, 3));
		Progress.rep(p, Faction.COURT, 6);
		Sites.resolve(p, site, "Its echoes were freed by " + p.getName().getString() + ".");
		Progress.legend(p, "choice", "Let the echoes of " + site.name + " go, and watched a new village begin there.");
		Chronicle.record(level.getServer(), site.name + " Begins Anew", "The echoes of " + site.name + " were freed from who they had been. "
			+ "New families have settled among the ruins under " + leader + ".", site.name, Faction.COURT.ordinal(), p.getName().getString(), "site:" + site.id);
		Fx.sound(level, p.position(), SoundEvents.BELL_BLOCK, 2.0F, 1.0F);
	}
}

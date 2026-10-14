package dev.afterworld.economy;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.npc.Npc;
import dev.afterworld.npc.Npcs;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.player.Renown;
import dev.afterworld.registry.AwGoods;
import dev.afterworld.site.Site;
import dev.afterworld.site.SiteKind;
import dev.afterworld.site.Sites;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Ground;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.Faction;
import dev.afterworld.world.Names;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Warden's Board: a notice board at the camp and in the settlements with three to five
 * contracts that change every day. Bounties name an elite at a place; deliveries carry a trade
 * good to a keeper; surveys stand at three points; gatherings bring a stack of something back to
 * any board; escorts see a caravan through. Each pays Marks and Renown, and the faction behind it
 * thinks better of you. A traveller may hold three at once.
 */
public final class Board {
	public static final int MAX_ACTIVE = 3;
	/** What a gathering may ask for: the item, how many, and Marks for each. */
	private static final String[][] GATHER = {
		{"minecraft:iron_ingot", "12", "6"}, {"minecraft:bone", "16", "3"}, {"minecraft:leather", "10", "4"},
		{"minecraft:amethyst_shard", "12", "4"}, {"minecraft:string", "16", "3"}, {"minecraft:gunpowder", "10", "5"},
		{"minecraft:copper_ingot", "16", "3"}, {"afterworld:boar_tusk", "4", "16"}, {"afterworld:ram_horn", "4", "16"},
		{"afterworld:lynx_pelt", "3", "20"}, {"afterworld:toad_oil", "4", "16"}, {"afterworld:crab_shell", "4", "16"},
		{"afterworld:ember_chitin", "4", "18"}, {"afterworld:sifter_mandible", "3", "22"}, {"afterworld:root_heartwood", "3", "24"},
		{"afterworld:glimmerfin_scale", "6", "10"}, {"afterworld:dusk_fang", "4", "16"}, {"afterworld:windrider_feather", "3", "22"},
		{"afterworld:wyrm_scale", "3", "24"}, {"afterworld:lurker_fang", "3", "20"}};
	private static final String[] EPITHETS = {"the Hollow", "the Unbowed", "Many-Scars", "the Gnawer", "the Patient", "Ashjaw", "the Old",
		"the Unwelcome", "Cinderback", "the Last", "Greymaw", "the Restless", "Ironhide", "the Widow-Maker"};
	/** The keepers of the landmarks, who want trade goods carried to them. */
	private static final Npc[] KEEPERS = {Npc.SELENE, Npc.ODO, Npc.MAB, Npc.TORVALD, Npc.INES, Npc.RASHID, Npc.AKANE};

	private Board() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.BoardAction.TYPE, (payload, ctx) -> action(ctx.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.overworld().getGameTime();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if ((now + p.getId()) % 20 == 7) {
					PlayerState s = PlayerState.of(p);
					if (!s.contracts.isEmpty()) {
						tick(p, s);
					}
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.entityTags().contains("afterworld.bounty") && entity.level() instanceof ServerLevel level) {
				bountyFallen(level.getServer(), entity);
			}
		});
	}

	// ------------------------------------------------------------------ offers

	/** Today's offers at the board at {@code board}; the same for everyone all day. */
	static List<Contract> offers(ServerLevel level, BlockPos board) {
		long day = level.getGameTime() / 24000L;
		RandomSource r = RandomSource.create(board.asLong() * 31L + day * 7919L + level.getSeed());
		int n = 3 + r.nextInt(3);
		List<Contract.Kind> kinds = new ArrayList<>(List.of(Contract.Kind.values()));
		List<Contract> out = new ArrayList<>();
		while (out.size() < n && !kinds.isEmpty()) {
			Contract.Kind kind = kinds.remove(r.nextInt(kinds.size()));
			Contract c = make(level, board, kind, r);
			if (c != null) {
				c.origin = board.asLong() + ":" + day + ":" + kind.id;
				out.add(c);
			}
		}
		return out;
	}

	private static Contract make(ServerLevel level, BlockPos board, Contract.Kind kind, RandomSource r) {
		Contract c = new Contract();
		c.kind = kind;
		c.dim = level.dimension().identifier().toString();
		switch (kind) {
			case BOUNTY -> {
				double a = r.nextDouble() * Math.PI * 2;
				int dist = 180 + r.nextInt(260);
				int x = board.getX() + (int) (Math.cos(a) * dist);
				int z = board.getZ() + (int) (Math.sin(a) * dist);
				// What it is shows when it is found: the land decides (see quarry).
				c.who = Names.first(r) + " " + EPITHETS[r.nextInt(EPITHETS.length)];
				c.points = new long[] {new BlockPos(x, 0, z).asLong()};
				c.marks = 90 + dist / 4 + r.nextInt(30);
				c.renown = 30;
				c.faction = Faction.CROWN.ordinal();
			}
			case DELIVERY -> {
				List<Npc> known = new ArrayList<>();
				for (Npc k : KEEPERS) {
					BlockPos home = Npcs.home(level.getServer(), k);
					if (home != null && home.distSqr(board) < 2400.0 * 2400.0) {
						known.add(k);
					}
				}
				if (known.isEmpty()) {
					return null;
				}
				Npc keeper = known.get(r.nextInt(known.size()));
				Item good = AwGoods.TRADE_GOODS.get(r.nextInt(AwGoods.TRADE_GOODS.size()));
				c.what = BuiltInRegistries.ITEM.getKey(good).toString();
				c.who = keeper.id;
				c.count = 1 + r.nextInt(2);
				BlockPos home = Npcs.home(level.getServer(), keeper);
				c.points = new long[] {home.asLong()};
				c.marks = Values.of(good) * c.count * 2L + (long) Math.sqrt(home.distSqr(board)) / 8 + 20;
				c.renown = 20;
				c.faction = Faction.GUILD.ordinal();
			}
			case SURVEY -> {
				double a0 = r.nextDouble() * Math.PI * 2;
				long[] pts = new long[3];
				for (int i = 0; i < 3; i++) {
					double a = a0 + i * (Math.PI * 2 / 3) + (r.nextDouble() - 0.5) * 0.6;
					int dist = 120 + r.nextInt(180);
					pts[i] = new BlockPos(board.getX() + (int) (Math.cos(a) * dist), 0, board.getZ() + (int) (Math.sin(a) * dist)).asLong();
				}
				c.points = pts;
				c.count = 3;
				c.marks = 110 + r.nextInt(40);
				c.renown = 25;
				c.faction = Faction.GUILD.ordinal();
			}
			case GATHERING -> {
				for (int tries = 0; tries < 8; tries++) {
					String[] g = GATHER[r.nextInt(GATHER.length)];
					var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(g[0]));
					if (item.isPresent()) {
						c.what = g[0];
						c.count = Integer.parseInt(g[1]);
						c.marks = (long) c.count * Integer.parseInt(g[2]) + 20;
						c.renown = 15;
						c.faction = Faction.BUREAU.ordinal();
						return c;
					}
				}
				return null;
			}
			case ESCORT -> {
				c.count = 1;
				c.marks = 150 + r.nextInt(50);
				c.renown = 35;
				c.faction = Faction.CROWN.ordinal();
			}
		}
		return c;
	}

	/** A worthy quarry for a bounty: one of the monsters that live where it was found. */
	private static EntityType<?> quarry(ServerLevel level, BlockPos at, RandomSource r) {
		var spawns = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.NATURAL_MOB_SPAWNS, at);
		List<EntityType<?>> ours = new ArrayList<>();
		for (var entry : spawns.getMobsToSpawn(MobCategory.MONSTER).unwrap()) {
			EntityType<?> type = entry.value().type();
			if (BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("afterworld")) {
				ours.add(type);
			}
		}
		if (!ours.isEmpty()) {
			return ours.get(r.nextInt(ours.size()));
		}
		EntityType<?>[] common = {EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.VINDICATOR, EntityTypes.HUSK};
		return common[r.nextInt(common.length)];
	}

	// ------------------------------------------------------------------ the screen

	/** Opens the board at {@code board} for a player. */
	public static void open(ServerPlayer p, BlockPos board) {
		if (!ServerPlayNetworking.canSend(p, AwPayloads.BoardOpen.TYPE)) {
			return;
		}
		// Every panel of a great board is the same board: its first panel's.
		board = dev.afterworld.block.BoardBlock.anchor(p.level(), board);
		PlayerState s = PlayerState.of(p);
		CompoundTag t = new CompoundTag();
		t.putLong("board", board.asLong());
		ListTag offers = new ListTag();
		for (Contract c : offers(p.level(), board)) {
			CompoundTag o = c.save();
			o.putBoolean("taken", taken(s, c.origin));
			describe(p, c, o);
			offers.add(o);
		}
		t.put("offers", offers);
		ListTag active = new ListTag();
		for (Contract c : s.contracts) {
			CompoundTag o = c.save();
			o.putInt("held", c.kind == Contract.Kind.GATHERING || c.kind == Contract.Kind.DELIVERY ? count(p, c.what) : 0);
			describe(p, c, o);
			active.add(o);
		}
		t.put("active", active);
		t.putInt("max", MAX_ACTIVE);
		ServerPlayNetworking.send(p, new AwPayloads.BoardOpen(t));
		Fx.privateSound(p, dev.afterworld.registry.AwSounds.UI_OPEN, 0.7F, 0.9F);
	}

	/** Where the contract points, said the way a notice would: north-east, about 300 paces. */
	private static void describe(ServerPlayer p, Contract c, CompoundTag o) {
		if (c.points.length > 0) {
			BlockPos at = BlockPos.of(c.points[Math.min(c.have, c.points.length - 1)]);
			double dx = at.getX() + 0.5 - p.getX();
			double dz = at.getZ() + 0.5 - p.getZ();
			o.putString("bearing", Names.direction(dx, dz));
			o.putString("distance", Names.distance(Math.sqrt(dx * dx + dz * dz)));
		}
	}

	private static boolean taken(PlayerState s, String origin) {
		for (Contract c : s.contracts) {
			if (c.origin.equals(origin)) {
				return true;
			}
		}
		return s.flags.contains("contract:" + origin);
	}

	private static void action(ServerPlayer p, AwPayloads.BoardAction a) {
		if (p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(a.board())) > 8 * 8 || !(p.level().getBlockState(a.board()).getBlock() instanceof dev.afterworld.block.BoardBlock)) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		switch (a.action()) {
			case AwPayloads.BoardAction.TAKE -> take(p, s, dev.afterworld.block.BoardBlock.anchor(p.level(), a.board()), a.index());
			case AwPayloads.BoardAction.HAND_IN -> handIn(p, s, a.index());
			case AwPayloads.BoardAction.ABANDON -> {
				if (a.index() >= 0 && a.index() < s.contracts.size()) {
					Contract c = s.contracts.remove(a.index());
					p.sendOverlayMessage(Component.translatable("message.afterworld.contract_abandoned").withColor(0x9A9488));
					forgetElite(p.level().getServer(), c);
				}
			}
			default -> {
			}
		}
		s.questDirty = true;
		open(p, a.board());
	}

	private static void take(ServerPlayer p, PlayerState s, BlockPos board, int index) {
		List<Contract> offers = offers(p.level(), board);
		if (index < 0 || index >= offers.size()) {
			return;
		}
		Contract c = offers.get(index);
		if (taken(s, c.origin)) {
			return;
		}
		if (s.contracts.size() >= MAX_ACTIVE) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.contract_full", MAX_ACTIVE).withColor(0xC4553B));
			Fx.privateSound(p, dev.afterworld.registry.AwSounds.UI_ERROR, 0.7F, 1.0F);
			return;
		}
		if (c.kind == Contract.Kind.ESCORT) {
			Site site = Sites.createNear(p.level().getServer(), SiteKind.CARAVAN, board.getX(), board.getZ(), 160, 320, p.getRandom(), -1,
				Faction.CROWN.ordinal(), -1);
			if (site == null) {
				p.sendOverlayMessage(Component.translatable("message.afterworld.contract_none_here").withColor(0x9A9488));
				return;
			}
			c.siteId = site.id;
			c.points = new long[] {new BlockPos(site.x, 0, site.z).asLong()};
		}
		s.contracts.add(c);
		s.flags.add("contract:" + c.origin);
		s.questDirty = true;
		Fx.privateSound(p, dev.afterworld.registry.AwSounds.UI_CHOOSE, 0.8F, 1.0F);
		p.sendSystemMessage(Component.translatable("message.afterworld.contract_taken", Component.translatable("board.afterworld.kind." + c.kind.id))
			.withColor(0xE8C873));
	}

	private static void handIn(ServerPlayer p, PlayerState s, int index) {
		if (index < 0 || index >= s.contracts.size()) {
			return;
		}
		Contract c = s.contracts.get(index);
		if (c.kind != Contract.Kind.GATHERING || c.done) {
			return;
		}
		if (count(p, c.what) < c.count) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.contract_short").withColor(0xC4553B));
			Fx.privateSound(p, dev.afterworld.registry.AwSounds.UI_ERROR, 0.7F, 1.0F);
			return;
		}
		take(p, c.what, c.count);
		c.have = c.count;
		complete(p, s, c);
	}

	// ------------------------------------------------------------------ progress

	private static void tick(ServerPlayer p, PlayerState s) {
		ServerLevel level = p.level();
		String dim = level.dimension().identifier().toString();
		for (Contract c : new ArrayList<>(s.contracts)) {
			if (c.done || c.failed || !c.dim.equals(dim)) {
				continue;
			}
			switch (c.kind) {
				case BOUNTY -> {
					BlockPos at = BlockPos.of(c.points[0]);
					if (!c.spawned && horizontal(p, at) < 56 * 56 && Ground.loaded(level, at.getX(), at.getZ())) {
						spawnBounty(p, s, c, at);
					}
				}
				case SURVEY -> survey(p, s, c);
				case ESCORT -> {
					Site site = AfterworldData.get(level.getServer()).sites.get(c.siteId);
					if (site == null || site.state == Site.FAILED) {
						fail(p, s, c);
					} else if (site.state == Site.RESOLVED) {
						complete(p, s, c);
					}
				}
				case GATHERING -> {
					int held = Math.min(c.count, count(p, c.what));
					if (held != c.have) {
						c.have = held;
						s.questDirty = true;
					}
				}
				default -> {
				}
			}
		}
	}

	private static double horizontal(ServerPlayer p, BlockPos at) {
		double dx = at.getX() + 0.5 - p.getX();
		double dz = at.getZ() + 0.5 - p.getZ();
		return dx * dx + dz * dz;
	}

	private static void spawnBounty(ServerPlayer p, PlayerState s, Contract c, BlockPos at) {
		ServerLevel level = p.level();
		if (c.what.isEmpty()) {
			c.what = BuiltInRegistries.ENTITY_TYPE.getKey(quarry(level, Ground.surface(level, at.getX(), at.getZ()), p.getRandom())).toString();
		}
		var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(c.what));
		if (type.isEmpty()) {
			fail(p, s, c);
			return;
		}
		Entity e = Ground.spawn(level, type.get(), at.getX(), at.getZ(), EntitySpawnReason.EVENT);
		if (!(e instanceof Mob mob)) {
			if (e != null) {
				e.discard();
			}
			return;
		}
		Npcs.makeElite(mob);
		mob.setCustomName(Component.literal(c.who).withColor(0xE8C873));
		mob.setCustomNameVisible(true);
		mob.setPersistenceRequired();
		mob.addTag("afterworld.bounty");
		c.elite = mob.getUUID().toString();
		c.spawned = true;
		// It does not hunt alone.
		for (int i = 0; i < 2; i++) {
			Entity kin = Ground.spawn(level, type.get(), at.getX() + p.getRandom().nextInt(7) - 3, at.getZ() + p.getRandom().nextInt(7) - 3, EntitySpawnReason.EVENT);
			if (kin instanceof Mob m) {
				m.setPersistenceRequired();
			}
		}
		Fx.burst(level, ParticleTypes.LARGE_SMOKE, mob.position().add(0, 1, 0), 20, 0.6, 0.02);
		Fx.sound(level, mob.position(), SoundEvents.RAID_HORN.value(), 0.5F, 0.8F);
		p.sendSystemMessage(Component.translatable("message.afterworld.bounty_found", c.who).withColor(0xE8C873));
		s.questDirty = true;
	}

	private static void bountyFallen(MinecraftServer server, Entity elite) {
		String id = elite.getUUID().toString();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			PlayerState s = PlayerState.of(p);
			for (Contract c : new ArrayList<>(s.contracts)) {
				if (c.kind == Contract.Kind.BOUNTY && id.equals(c.elite) && !c.done) {
					c.have = 1;
					complete(p, s, c);
				}
			}
		}
	}

	private static void forgetElite(MinecraftServer server, Contract c) {
		if (c.kind != Contract.Kind.BOUNTY || c.elite.isEmpty()) {
			return;
		}
		UUID id = UUID.fromString(c.elite);
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e != null) {
				e.removeTag("afterworld.bounty");
				if (e instanceof Mob m) {
					m.setPersistenceRequired();
				}
			}
		}
	}

	private static void survey(ServerPlayer p, PlayerState s, Contract c) {
		if (c.have >= c.points.length) {
			return;
		}
		BlockPos at = BlockPos.of(c.points[c.have]);
		if (horizontal(p, at) > 6 * 6) {
			c.dwell = 0;
			return;
		}
		ServerLevel level = p.level();
		BlockPos ground = Ground.surface(level, at.getX(), at.getZ());
		Fx.column(level, Fx.dust(0x5B8DB8, 1.0F), net.minecraft.world.phys.Vec3.atBottomCenterOf(ground), 8, 0.2, 3.0);
		if (++c.dwell < 3) {
			Fx.privateSound(p, dev.afterworld.registry.AwSounds.UI_MOVE, 0.6F, 0.9F + c.dwell * 0.15F);
			return;
		}
		c.dwell = 0;
		c.have++;
		Fx.ring(level, Fx.dust(0x5B8DB8, 1.4F), net.minecraft.world.phys.Vec3.atBottomCenterOf(ground).add(0, 0.2, 0), 3.0, 24);
		Fx.sound(level, net.minecraft.world.phys.Vec3.atBottomCenterOf(ground), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0F, 1.0F);
		p.sendOverlayMessage(Component.translatable("message.afterworld.survey_point", c.have, c.points.length).withColor(0x5B8DB8));
		s.questDirty = true;
		if (c.have >= c.points.length) {
			complete(p, s, c);
		}
	}

	/** A keeper is spoken to: if a delivery is due to them and it is carried, it changes hands. */
	public static boolean onTalk(ServerPlayer p, Npc npc) {
		PlayerState s = PlayerState.of(p);
		for (Contract c : new ArrayList<>(s.contracts)) {
			if (c.kind == Contract.Kind.DELIVERY && !c.done && c.who.equals(npc.id) && count(p, c.what) >= c.count) {
				take(p, c.what, c.count);
				c.have = c.count;
				complete(p, s, c);
				return true;
			}
		}
		return false;
	}

	private static void complete(ServerPlayer p, PlayerState s, Contract c) {
		c.done = true;
		s.contracts.remove(c);
		s.questDirty = true;
		long pay = Math.round(c.marks * (1.0 + Renown.level(s) * 0.06));
		Wallet.add(p, pay);
		Wallet.chime(p, pay);
		Renown.grant(p, c.renown, Component.translatable("board.afterworld.kind." + c.kind.id).getString());
		if (c.faction >= 0) {
			Progress.rep(p, Faction.byOrdinal(c.faction), 5);
		}
		Progress.legend(p, "milestone", "Fulfilled a " + Component.translatable("board.afterworld.kind." + c.kind.id).getString().toLowerCase() + " from the Warden's Board.");
		p.sendSystemMessage(Component.translatable("message.afterworld.contract_done", Component.translatable("board.afterworld.kind." + c.kind.id), pay)
			.withColor(0xE8C873));
		Fx.privateSound(p, dev.afterworld.registry.AwSounds.PURCHASE, 0.9F, 1.0F);
		Fx.burst(p.level(), ParticleTypes.HAPPY_VILLAGER, p.position().add(0, 1, 0), 10, 0.5, 0.05);
		dev.afterworld.quest.Quests.onContract(p);
	}

	private static void fail(ServerPlayer p, PlayerState s, Contract c) {
		c.failed = true;
		s.contracts.remove(c);
		s.questDirty = true;
		p.sendSystemMessage(Component.translatable("message.afterworld.contract_failed", Component.translatable("board.afterworld.kind." + c.kind.id))
			.withColor(0x9A9488));
	}

	// ------------------------------------------------------------------ helpers

	private static int count(ServerPlayer p, String itemId) {
		var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId));
		if (item.isEmpty()) {
			return 0;
		}
		int n = 0;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item.get())) {
				n += st.getCount();
			}
		}
		return n;
	}

	private static void take(ServerPlayer p, String itemId, int amount) {
		var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId));
		if (item.isEmpty()) {
			return;
		}
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(item.get())) {
				int t = Math.min(amount, st.getCount());
				st.shrink(t);
				amount -= t;
			}
		}
		inv.setChanged();
	}

	/** The contracts in the quest tracker: where to go next for each. */
	public static List<AwPayloads.QuestSync.Entry> entries(PlayerState s) {
		List<AwPayloads.QuestSync.Entry> out = new ArrayList<>();
		for (Contract c : s.contracts) {
			Component text = Component.translatable("board.afterworld.track." + c.kind.id, label(c));
			boolean has = c.points.length > 0 && c.kind != Contract.Kind.GATHERING;
			BlockPos at = has ? BlockPos.of(c.points[Math.min(c.have, c.points.length - 1)]) : BlockPos.ZERO;
			int need = c.kind == Contract.Kind.SURVEY || c.kind == Contract.Kind.GATHERING ? c.count : 0;
			out.add(new AwPayloads.QuestSync.Entry(AwPayloads.QuestSync.CONTRACT, text, c.have, need, false, has, at.getX(),
				c.kind == Contract.Kind.DELIVERY ? at.getY() : Integer.MIN_VALUE, at.getZ(), c.dim));
		}
		return out;
	}

	private static Component label(Contract c) {
		return switch (c.kind) {
			case BOUNTY -> Component.literal(c.who);
			case DELIVERY -> {
				Npc keeper = Npc.byId(c.who);
				yield Component.literal(keeper == null ? c.who : keeper.displayName);
			}
			case GATHERING -> BuiltInRegistries.ITEM.getOptional(Identifier.parse(c.what)).map(i -> new ItemStack(i).getHoverName()).orElse(Component.literal(c.what));
			default -> Component.empty();
		};
	}
}

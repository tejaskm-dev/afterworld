package dev.afterworld.economy;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.npc.Npc;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.player.Soulbound;
import dev.afterworld.quest.Chapter;
import dev.afterworld.quest.Quests;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.registry.AwTags;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Inv;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * The shop counter: one session per traveller, standing at a merchant's elbow. Every action is
 * checked here against the merchant's real stock, prices and the traveller's wallet, then the
 * whole view is sent back so the screen always shows the truth.
 */
public final class Shop {
	public static final int BUY = 0;
	public static final int SELL = 1;
	public static final int BUYBACK = 2;
	public static final int HAGGLE = 3;
	public static final int CLOSE = 4;

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	record Bought(ItemStack stack, int price) {
	}

	static final class Session {
		final Merchant merchant;
		final Entity keeper;
		final Npc npc;
		final List<Bought> buyback = new ArrayList<>();

		Session(Merchant merchant, Entity keeper, Npc npc) {
			this.merchant = merchant;
			this.keeper = keeper;
			this.npc = npc;
		}
	}

	private Shop() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.ShopAction.TYPE, (payload, ctx) -> handle(ctx.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUUID()));
	}

	public static void open(ServerPlayer p, Entity keeper, Npc npc) {
		Merchant m = Merchants.get(npc.id);
		if (m == null) {
			return;
		}
		if (!Market.willTrade(p, m)) {
			p.sendSystemMessage(Component.translatable("message.afterworld.shop_refused", npc.displayName).withColor(0xC86B5A));
			return;
		}
		Session s = new Session(m, keeper, npc);
		SESSIONS.put(p.getUUID(), s);
		Fx.privateSound(p, AwSounds.UI_OPEN, 0.6F, 1.1F);
		send(p, s, Component.empty());
	}

	private static boolean sellable(ItemStack stack) {
		return !stack.isEmpty() && !Soulbound.is(stack) && !stack.is(AwTags.UNSELLABLE) && Values.of(stack) > 0;
	}

	static void send(ServerPlayer p, Session s, Component message) {
		Merchant m = s.merchant;
		Market.State st = Market.state(p.level().getServer(), m);
		PlayerState ps = PlayerState.of(p);
		int renown = dev.afterworld.player.Renown.level(ps);
		List<AwPayloads.ShopView.Buy> buy = new ArrayList<>();
		for (int i = 0; i < m.offers.size(); i++) {
			Merchant.Offer o = m.offers.get(i);
			ItemStack stack = o.make();
			buy.add(new AwPayloads.ShopView.Buy(stack, Market.buyPrice(p, m, st, i), st.stock[i], o.category().ordinal(),
				Market.demandArrow(m, stack.getItem()), renown < o.minRenown() ? o.minRenown() : 0));
		}
		List<AwPayloads.ShopView.Sell> sell = new ArrayList<>();
		var inv = p.getInventory();
		for (int slot = 0; slot < 36; slot++) {
			ItemStack stack = inv.getItem(slot);
			if (sellable(stack)) {
				int price = Market.sellPrice(p, m, st, stack);
				if (price > 0) {
					sell.add(new AwPayloads.ShopView.Sell(slot, stack.copy(), price, Market.demandArrow(m, stack.getItem())));
				}
			}
		}
		List<AwPayloads.ShopView.Sell> back = new ArrayList<>();
		for (int i = 0; i < s.buyback.size(); i++) {
			back.add(new AwPayloads.ShopView.Sell(i, s.buyback.get(i).stack(), s.buyback.get(i).price(), 0));
		}
		long today = Progress.day(p);
		long h = ps.haggles.getOrDefault(m.id, Long.MIN_VALUE);
		int haggle = h != Long.MIN_VALUE && h / 1000 == today ? (int) (h % 1000) - 500 : 999;
		ServerPlayNetworking.send(p, new AwPayloads.ShopView(s.keeper.getId(), m.id, s.npc.displayName, s.npc.role, s.npc.color, ps.marks,
			Market.repTier(p, m), haggle, buy, sell, back, message));
	}

	private static void handle(ServerPlayer p, AwPayloads.ShopAction a) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		if (a.action() == CLOSE || s.keeper.isRemoved() || s.keeper.distanceToSqr(p) > 10 * 10) {
			SESSIONS.remove(p.getUUID());
			return;
		}
		Merchant m = s.merchant;
		Market.State st = Market.state(p.level().getServer(), m);
		PlayerState ps = PlayerState.of(p);
		Component msg = Component.empty();
		switch (a.action()) {
			case BUY -> msg = buy(p, ps, s, m, st, a.index(), Math.max(1, Math.min(64, a.count())));
			case SELL -> msg = sell(p, ps, s, m, st, a.index(), Math.max(1, a.count()));
			case BUYBACK -> msg = buyback(p, s, a.index());
			case HAGGLE -> msg = haggle(p, ps, m, a.extra());
			default -> {
			}
		}
		send(p, s, msg);
	}

	private static Component buy(ServerPlayer p, PlayerState ps, Session s, Merchant m, Market.State st, int i, int n) {
		if (i < 0 || i >= m.offers.size()) {
			return Component.empty();
		}
		Merchant.Offer o = m.offers.get(i);
		if (dev.afterworld.player.Renown.level(ps) < o.minRenown()) {
			return Component.translatable("shop.afterworld.locked", o.minRenown()).withColor(0xC86B5A);
		}
		if (st.stock[i] == 0) {
			return Component.translatable("shop.afterworld.sold_out").withColor(0xC86B5A);
		}
		if (st.stock[i] > 0) {
			n = Math.min(n, st.stock[i]);
		}
		int unit = Market.buyPrice(p, m, st, i);
		long afford = ps.marks / unit;
		if (afford <= 0) {
			return Component.translatable("shop.afterworld.too_poor").withColor(0xC86B5A);
		}
		n = (int) Math.min(n, afford);
		Wallet.spend(p, (long) unit * n);
		for (int k = 0; k < n; k++) {
			Inv.give(p, o.make());
		}
		if (st.stock[i] > 0) {
			st.stock[i] -= n;
		}
		dev.afterworld.world.AfterworldData.get(p.level().getServer()).setDirty();
		Fx.privateSound(p, AwSounds.PURCHASE, 0.7F, 1.0F);
		if (!ps.boughtOnce) {
			ps.boughtOnce = true;
			Quests.progress(p, Chapter.Goal.TRADE, 1);
		}
		return Component.translatable("shop.afterworld.bought", n, o.make().getHoverName(), (long) unit * n).withColor(0xE8C873);
	}

	private static Component sell(ServerPlayer p, PlayerState ps, Session s, Merchant m, Market.State st, int slot, int n) {
		if (slot < 0 || slot >= 36) {
			return Component.empty();
		}
		ItemStack stack = p.getInventory().getItem(slot);
		if (!sellable(stack)) {
			return Component.empty();
		}
		n = Math.min(n, stack.getCount());
		int unit = Market.sellPrice(p, m, st, stack);
		if (unit <= 0) {
			return Component.translatable("shop.afterworld.not_wanted").withColor(0xC86B5A);
		}
		ItemStack taken = stack.split(n);
		p.getInventory().setChanged();
		Market.sold(p.level().getServer(), st, taken.getItem(), n);
		long total = (long) unit * n;
		Wallet.add(p, total);
		Wallet.chime(p, total);
		s.buyback.add(0, new Bought(taken, (int) Math.min(Integer.MAX_VALUE, total)));
		while (s.buyback.size() > 9) {
			s.buyback.remove(s.buyback.size() - 1);
		}
		if (!ps.soldOnce) {
			ps.soldOnce = true;
			Quests.progress(p, Chapter.Goal.TRADE, 1);
		}
		return Component.translatable("shop.afterworld.sold", n, taken.getHoverName(), total).withColor(0xE8C873);
	}

	private static Component buyback(ServerPlayer p, Session s, int i) {
		if (i < 0 || i >= s.buyback.size()) {
			return Component.empty();
		}
		Bought b = s.buyback.get(i);
		if (!Wallet.spend(p, b.price())) {
			return Component.translatable("shop.afterworld.too_poor").withColor(0xC86B5A);
		}
		s.buyback.remove(i);
		Inv.give(p, b.stack());
		Fx.privateSound(p, AwSounds.PURCHASE, 0.7F, 1.2F);
		return Component.translatable("shop.afterworld.bought_back", b.stack().getHoverName()).withColor(0xE8C873);
	}

	/** Flatter (0), drive a hard bargain (1) or name a rival (2). Once per merchant per day. */
	private static Component haggle(ServerPlayer p, PlayerState ps, Merchant m, int tactic) {
		long today = Progress.day(p);
		long h = ps.haggles.getOrDefault(m.id, Long.MIN_VALUE);
		if (h != Long.MIN_VALUE && h / 1000 == today) {
			return Component.translatable("shop.afterworld.haggled_already").withColor(0x9A9488);
		}
		int favoured = m.temperament.ordinal();
		double chance = 0.32 + (tactic == favoured ? 0.33 : 0) + 0.05 * Math.max(0, Market.repTier(p, m));
		boolean win = p.getRandom().nextDouble() < chance;
		int pct = win ? -(15 + p.getRandom().nextInt(16)) : 10;
		ps.haggles.put(m.id, today * 1000 + 500 + pct);
		Fx.privateSound(p, win ? AwSounds.UI_DISCOVERY : AwSounds.UI_SELECT, 0.7F, win ? 1.2F : 0.6F);
		String key = "shop.afterworld.haggle." + m.temperament.name().toLowerCase(java.util.Locale.ROOT) + "." + tactic + (win ? ".win" : ".lose");
		return Component.translatable(key).append(" ").append(Component.translatable(win ? "shop.afterworld.haggle_win" : "shop.afterworld.haggle_lose", Math.abs(pct)))
			.withColor(win ? 0x9BD08A : 0xC86B5A);
	}
}

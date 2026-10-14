package dev.afterworld.economy;

import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.player.Relics;
import dev.afterworld.world.AfterworldData;
import dev.afterworld.world.Faction;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Prices. A merchant's buy price is the item's value times their markup, how much they want it,
 * how scarce it is in their stock, what they think of you and any bargain you struck today. What
 * they pay is the value times their rate, their want, how much of it you've already sold them
 * (saturation, recovering by half each day) and what they think of you. Stock restocks daily.
 */
public final class Market {
	private Market() {
	}

	/** One merchant's memory: stock per offer, what they've been sold lately, and when. */
	public static final class State {
		public long day = -1;
		public int[] stock = new int[0];
		public final Object2DoubleOpenHashMap<String> sold = new Object2DoubleOpenHashMap<>();
		public long soldDay;

		public CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putLong("day", day);
			t.putIntArray("stock", stock);
			CompoundTag s = new CompoundTag();
			for (var e : sold.object2DoubleEntrySet()) {
				s.putDouble(e.getKey(), e.getDoubleValue());
			}
			t.put("sold", s);
			t.putLong("soldDay", soldDay);
			return t;
		}

		public static State load(CompoundTag t) {
			State st = new State();
			st.day = t.getLongOr("day", -1);
			st.stock = t.getIntArray("stock").orElse(new int[0]);
			CompoundTag s = t.getCompoundOrEmpty("sold");
			for (String k : s.keySet()) {
				st.sold.put(k, s.getDoubleOr(k, 0));
			}
			st.soldDay = t.getLongOr("soldDay", 0);
			return st;
		}
	}

	/** The merchant's state, restocked and with saturation decayed to today. */
	public static State state(MinecraftServer server, Merchant m) {
		AfterworldData data = AfterworldData.get(server);
		State st = data.markets.computeIfAbsent(m.id, k -> new State());
		long today = Progress.day(server);
		if (st.stock.length != m.offers.size()) {
			int[] fresh = new int[m.offers.size()];
			for (int i = 0; i < fresh.length; i++) {
				fresh[i] = i < st.stock.length ? st.stock[i] : m.offers.get(i).stock();
			}
			st.stock = fresh;
			data.setDirty();
		}
		if (st.day < 0) {
			st.day = today;
		}
		if (today > st.day) {
			long days = today - st.day;
			for (int i = 0; i < st.stock.length; i++) {
				Merchant.Offer o = m.offers.get(i);
				if (o.stock() >= 0) {
					st.stock[i] = (int) Math.min(o.stock(), st.stock[i] + o.restock() * days);
				}
			}
			st.day = today;
			data.setDirty();
		}
		if (today > st.soldDay) {
			double f = Math.pow(0.5, today - st.soldDay);
			st.sold.replaceAll((k, v) -> v * f);
			st.sold.object2DoubleEntrySet().removeIf(e -> e.getDoubleValue() < 0.25);
			st.soldDay = today;
			data.setDirty();
		}
		return st;
	}

	public static String key(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	public static int repTier(ServerPlayer p, Merchant m) {
		return m.faction == null ? 0 : Faction.tier(PlayerState.of(p).rep(m.faction));
	}

	/** Whether this merchant will deal with the traveller at all. */
	public static boolean willTrade(ServerPlayer p, Merchant m) {
		return repTier(p, m) > -2;
	}

	private static double repBuy(int tier) {
		return switch (tier) {
			case -1 -> 1.15;
			case 1 -> 0.95;
			case 2 -> 0.9;
			case 3 -> 0.8;
			default -> 1.0;
		};
	}

	private static double repSell(int tier) {
		return switch (tier) {
			case -1 -> 0.9;
			case 1 -> 1.03;
			case 2 -> 1.06;
			case 3 -> 1.1;
			default -> 1.0;
		};
	}

	private static double scarcity(State st, int i, Merchant.Offer o) {
		if (o.stock() <= 0 || st.stock[i] < 0) {
			return 1.0;
		}
		double frac = st.stock[i] / (double) o.stock();
		return frac < 0.1 ? 1.5 : frac < 0.3 ? 1.25 : 1.0;
	}

	public static double saturation(State st, Item item) {
		return Math.pow(0.88, st.sold.getOrDefault(key(item), 0.0) / 4.0);
	}

	/** Today's bargain with this merchant, as a price factor (1 if none). */
	public static double haggle(ServerPlayer p, Merchant m) {
		PlayerState s = PlayerState.of(p);
		long today = Progress.day(p);
		long v = s.haggles.getOrDefault(m.id, Long.MIN_VALUE);
		if (v == Long.MIN_VALUE || v / 1000 != today) {
			return 1.0;
		}
		int pct = (int) (v % 1000) - 500;
		return 1.0 + pct / 100.0;
	}

	public static int buyPrice(ServerPlayer p, Merchant m, State st, int i) {
		Merchant.Offer o = m.offers.get(i);
		ItemStack stack = o.make();
		double value = Values.of(stack) * stack.getCount();
		double price = Math.max(1, value) * m.markup * m.demand(stack.getItem()) * scarcity(st, i, o) * repBuy(repTier(p, m)) * haggle(p, m);
		if (m.faction == Faction.GUILD && Relics.has(p, "caravan_seal")) {
			price *= 0.85;
		}
		return (int) Math.max(1, Math.ceil(price));
	}

	public static int sellPrice(ServerPlayer p, Merchant m, State st, ItemStack stack) {
		int value = Values.of(stack);
		if (value <= 0) {
			return 0;
		}
		double price = value * m.rate * m.demand(stack.getItem()) * saturation(st, stack.getItem()) * repSell(repTier(p, m));
		return (int) Math.floor(price);
	}

	/** -1: cheap here, 0: usual, 1: dear here (for the little arrows). */
	public static int demandArrow(Merchant m, Item item) {
		double d = m.demand(item);
		return d < 0.95 ? -1 : d > 1.05 ? 1 : 0;
	}

	public static void sold(MinecraftServer server, State st, Item item, int count) {
		st.sold.addTo(key(item), count);
		AfterworldData.get(server).setDirty();
	}
}

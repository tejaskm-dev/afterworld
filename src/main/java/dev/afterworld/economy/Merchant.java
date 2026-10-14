package dev.afterworld.economy;

import dev.afterworld.world.Faction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A merchant's character: what they stock, what they pay well for, how greedy they are and how
 * they like to be haggled with. Their stock levels and memory of what you've sold them live in
 * {@link Market}.
 */
public final class Merchant {
	public enum Temperament {
		/** Likes to be flattered. */
		VAIN,
		/** Respects a hard bargain. */
		SHREWD,
		/** Hates to lose a customer to a rival. */
		JEALOUS
	}

	public enum Category {
		SUPPLIES, GEAR, ARMS, POTIONS, MATERIALS, GOODS, CURIOS
	}

	/** One line of stock. {@code bundle} items per purchase; {@code stock} bundles (-1: never runs out). */
	public record Offer(Supplier<ItemStack> stack, int stock, int restock, Category category, int minRenown) {
		public ItemStack make() {
			return stack.get();
		}
	}

	public final String id;
	public final Faction faction;
	/** Buy prices are value × markup; what they pay is value × rate. */
	public final double markup;
	public final double rate;
	public final Temperament temperament;
	public final List<Offer> offers = new ArrayList<>();
	/** How this merchant values particular items (trade goods mostly): above 1 they want it. */
	public final Map<Item, Double> demand = new HashMap<>();
	/** The trade good made here, cheap to buy and poorly paid for. */
	public Item produces;

	public Merchant(String id, Faction faction, double markup, double rate, Temperament temperament) {
		this.id = id;
		this.faction = faction;
		this.markup = markup;
		this.rate = rate;
		this.temperament = temperament;
	}

	public Merchant sell(Supplier<ItemStack> stack, int stock, int restock, Category category) {
		this.offers.add(new Offer(stack, stock, restock, category, 0));
		return this;
	}

	public Merchant sell(Supplier<ItemStack> stack, int stock, int restock, Category category, int minRenown) {
		this.offers.add(new Offer(stack, stock, restock, category, minRenown));
		return this;
	}

	public Merchant want(Item item, double factor) {
		this.demand.put(item, factor);
		return this;
	}

	public Merchant produce(Item good) {
		this.produces = good;
		this.demand.put(good, 0.55);
		return this;
	}

	public double demand(Item item) {
		return this.demand.getOrDefault(item, 1.0);
	}
}

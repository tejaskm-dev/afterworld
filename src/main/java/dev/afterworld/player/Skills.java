package dev.afterworld.player;

import dev.afterworld.Afterworld;
import dev.afterworld.util.Fx;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The skill tree: one root (the Waking, which every traveller has) and three limbs growing from it,
 * bought with Insight (one per Renown level, one per chapter, two for each Warden's first fall).
 * Blade makes you deadlier up close, Arcanum feeds your Edicts, Arts and Aspect, and Wayfarer makes
 * you tireless and hard to catch; two bridges join the limbs where they grow close. Each limb forks
 * into two branches of small nodes (a few percent of something), named skills, and a keystone at
 * its tip. A node can be learned when one next to it is: the tree is climbed outward from the root,
 * along any branch. Node positions are in tree units (x to the right, y up from the root), for the
 * tree's own screen.
 */
public final class Skills {
	public enum Kind {
		ROOT, MINOR, NOTABLE, KEYSTONE
	}

	/** What a small node raises, by how much per node (fractions are percentages). */
	public enum Stat {
		NONE, MELEE, ATTACK_SPEED, ARMOR, HEALTH, KNOCKBACK, MOVE, LUCK, STAMINA, ART_RECOVERY, EDICT_RECOVERY, RESONANCE
	}

	/** A node: where it stands, what it costs, what it raises, and the item drawn on it. */
	public record Node(String id, String branch, Kind kind, float x, float y, int cost, Stat stat, double amount, String icon) {
	}

	public static final List<String> BRANCHES = List.of("blade", "arcanum", "wayfarer");
	public static final String ROOT = "root";
	public static final List<Node> TREE = new ArrayList<>();
	/** Who grows next to whom (both ways). */
	public static final Map<String, List<String>> LINKS = new LinkedHashMap<>();
	private static final Map<String, Node> BY_ID = new HashMap<>();

	private static void node(String id, String branch, Kind kind, float x, float y, int cost, Stat stat, double amount, String icon, String... from) {
		Node n = new Node(id, branch, kind, x, y, cost, stat, amount, icon);
		TREE.add(n);
		BY_ID.put(id, n);
		LINKS.computeIfAbsent(id, k -> new ArrayList<>());
		for (String f : from) {
			LINKS.computeIfAbsent(f, k -> new ArrayList<>()).add(id);
			LINKS.get(id).add(f);
		}
	}

	private static void notable(String id, String branch, float x, float y, int cost, String icon, String... from) {
		node(id, branch, Kind.NOTABLE, x, y, cost, Stat.NONE, 0, icon, from);
	}

	private static void minor(String id, String branch, float x, float y, Stat stat, double amount, String icon, String... from) {
		node(id, branch, Kind.MINOR, x, y, 1, stat, amount, icon, from);
	}

	private static void keystone(String id, String branch, float x, float y, String icon, String... from) {
		node(id, branch, Kind.KEYSTONE, x, y, 3, Stat.NONE, 0, icon, from);
	}

	static {
		node(ROOT, "root", Kind.ROOT, 0, 0, 0, Stat.NONE, 0, "minecraft:nether_star");

		// Blade, up and to the left: the duelist's branch outside, the breaker's inside.
		notable("blade.edge", "blade", -1.6F, 1.0F, 1, "minecraft:iron_sword", ROOT);
		minor("blade.might1", "blade", -2.6F, 1.6F, Stat.MELEE, 0.03, "minecraft:flint", "blade.edge");
		notable("blade.flow", "blade", -3.8F, 1.8F, 1, "minecraft:golden_sword", "blade.might1");
		minor("blade.quick1", "blade", -4.8F, 2.5F, Stat.ATTACK_SPEED, 0.05, "minecraft:sugar", "blade.flow");
		notable("blade.riposte", "blade", -5.4F, 3.5F, 2, "minecraft:shield", "blade.quick1");
		minor("blade.might2", "blade", -5.0F, 4.5F, Stat.MELEE, 0.03, "minecraft:flint", "blade.riposte");
		keystone("blade.duelist", "blade", -5.6F, 5.6F, "minecraft:netherite_sword", "blade.might2");
		minor("blade.guard1", "blade", -2.6F, 2.8F, Stat.ARMOR, 1, "minecraft:iron_chestplate", "blade.might1");
		notable("blade.execution", "blade", -3.0F, 3.9F, 2, "minecraft:iron_axe", "blade.guard1");
		minor("blade.hale1", "blade", -3.7F, 4.9F, Stat.HEALTH, 2, "minecraft:apple", "blade.execution");
		minor("blade.stance1", "blade", -2.5F, 5.0F, Stat.KNOCKBACK, 0.1, "minecraft:anvil", "blade.execution");
		keystone("blade.bloodthirst", "blade", -3.1F, 6.1F, "minecraft:nether_wart", "blade.hale1", "blade.stance1");

		// Arcanum, straight up: a lattice, the Arts on its left and the Edicts on its right.
		notable("arcanum.attune", "arcanum", 0.0F, 1.4F, 1, "minecraft:amethyst_shard", ROOT);
		minor("arcanum.res1", "arcanum", 0.0F, 2.4F, Stat.RESONANCE, 0.05, "minecraft:glowstone_dust", "arcanum.attune");
		notable("arcanum.recall", "arcanum", -1.0F, 3.2F, 1, "minecraft:clock", "arcanum.res1");
		minor("arcanum.art1", "arcanum", -1.3F, 4.3F, Stat.ART_RECOVERY, 0.05, "minecraft:blaze_powder", "arcanum.recall");
		notable("arcanum.edictweaver", "arcanum", 1.0F, 3.2F, 2, "minecraft:writable_book", "arcanum.res1");
		minor("arcanum.edict1", "arcanum", 1.3F, 4.3F, Stat.EDICT_RECOVERY, 0.05, "minecraft:paper", "arcanum.edictweaver");
		minor("arcanum.res2", "arcanum", 0.0F, 3.5F, Stat.RESONANCE, 0.05, "minecraft:glowstone_dust", "arcanum.res1");
		notable("arcanum.overflow", "arcanum", 0.0F, 4.7F, 2, "minecraft:ender_eye", "arcanum.res2", "arcanum.art1", "arcanum.edict1");
		keystone("arcanum.echo", "arcanum", 0.0F, 6.0F, "minecraft:echo_shard", "arcanum.overflow");

		// Wayfarer, up and to the right: the runner's branch outside, the pathfinder's inside.
		notable("wayfarer.endurance", "wayfarer", 1.6F, 1.0F, 1, "minecraft:leather_boots", ROOT);
		minor("wayfarer.swift1", "wayfarer", 2.6F, 1.6F, Stat.MOVE, 0.03, "minecraft:sugar", "wayfarer.endurance");
		notable("wayfarer.ghoststep", "wayfarer", 3.8F, 1.8F, 2, "minecraft:phantom_membrane", "wayfarer.swift1");
		minor("wayfarer.lungs1", "wayfarer", 4.8F, 2.5F, Stat.STAMINA, 10, "minecraft:rabbit_foot", "wayfarer.ghoststep");
		notable("wayfarer.featherfall", "wayfarer", 5.4F, 3.5F, 1, "minecraft:feather", "wayfarer.lungs1");
		minor("wayfarer.swift2", "wayfarer", 5.0F, 4.5F, Stat.MOVE, 0.03, "minecraft:sugar", "wayfarer.featherfall");
		keystone("wayfarer.secondwind", "wayfarer", 5.6F, 5.6F, "minecraft:wind_charge", "wayfarer.swift2");
		minor("wayfarer.hardy1", "wayfarer", 2.6F, 2.8F, Stat.HEALTH, 2, "minecraft:sweet_berries", "wayfarer.swift1");
		notable("wayfarer.pathfinder", "wayfarer", 3.0F, 3.9F, 2, "minecraft:compass", "wayfarer.hardy1");
		minor("wayfarer.luck1", "wayfarer", 3.7F, 4.9F, Stat.LUCK, 1, "minecraft:emerald", "wayfarer.pathfinder");
		minor("wayfarer.lungs2", "wayfarer", 2.5F, 5.0F, Stat.STAMINA, 10, "minecraft:rabbit_foot", "wayfarer.pathfinder");
		keystone("wayfarer.scavenger", "wayfarer", 3.1F, 6.1F, "minecraft:gold_nugget", "wayfarer.luck1", "wayfarer.lungs2");

		// Where the limbs grow close, a bridge from one to the other.
		minor("bridge.spellblade", "bridge", -1.7F, 2.5F, Stat.MELEE, 0.03, "minecraft:enchanted_book", "blade.guard1", "arcanum.recall");
		minor("bridge.pilgrim", "bridge", 1.7F, 2.5F, Stat.EDICT_RECOVERY, 0.03, "minecraft:candle", "wayfarer.hardy1", "arcanum.edictweaver");
	}

	private static final Identifier MODIFIER = Afterworld.id("skill_tree");
	private static final Identifier PATHFINDER = Afterworld.id("skill_pathfinder");
	private static final Map<Stat, Holder<Attribute>> ATTRIBUTES = new EnumMap<>(Map.of(
		Stat.ATTACK_SPEED, Attributes.ATTACK_SPEED, Stat.ARMOR, Attributes.ARMOR, Stat.HEALTH, Attributes.MAX_HEALTH,
		Stat.KNOCKBACK, Attributes.KNOCKBACK_RESISTANCE, Stat.LUCK, Attributes.LUCK));

	private Skills() {
	}

	public static Node byId(String id) {
		return BY_ID.get(id);
	}

	public static boolean has(PlayerState s, String id) {
		return ROOT.equals(id) || s.skills.contains(id);
	}

	public static boolean has(ServerPlayer p, String id) {
		return has(PlayerState.of(p), id);
	}

	/** How much the traveller's learned nodes raise a stat, all told. */
	public static double bonus(PlayerState s, Stat stat) {
		double sum = 0;
		for (String id : s.skills) {
			Node n = BY_ID.get(id);
			if (n != null && n.stat() == stat) {
				sum += n.amount();
			}
		}
		return sum;
	}

	/** Whether a node grows next to one the traveller has (the root counts). */
	public static boolean reachable(java.util.Set<String> owned, String id) {
		for (String n : LINKS.getOrDefault(id, List.of())) {
			if (ROOT.equals(n) || owned.contains(n)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a node can be learned now: not had yet, next to one that is, and Insight to pay. */
	public static boolean canBuy(PlayerState s, Node n) {
		return n.kind() != Kind.ROOT && !s.skills.contains(n.id()) && s.insight >= n.cost() && reachable(s.skills, n.id());
	}

	public static boolean buy(ServerPlayer p, String id) {
		PlayerState s = PlayerState.of(p);
		Node n = byId(id);
		if (n == null || !canBuy(s, n)) {
			return false;
		}
		s.insight -= n.cost();
		s.skills.add(n.id());
		apply(p, s);
		Fx.privateSound(p, dev.afterworld.registry.AwSounds.UI_AWAKEN, 1.0F, n.kind() == Kind.KEYSTONE ? 0.9F : 1.2F);
		p.sendOverlayMessage(Component.translatable("skill.afterworld.learned", Component.translatable("skill.afterworld." + n.id())).withColor(0xE8C873));
		Progress.legend(p, "milestone", "Learned " + Component.translatable("skill.afterworld." + n.id()).getString() + ".");
		dev.afterworld.movement.Stamina.sync(p, s);
		return true;
	}

	/** Insight earned beyond Renown (a chapter's end, a Warden's first fall), told to the traveller. */
	public static void grantInsight(ServerPlayer p, int amount) {
		PlayerState s = PlayerState.of(p);
		s.insight += amount;
		s.hudDirty = true;
		Progress.notice(p, dev.afterworld.net.AwPayloads.Notice.QUEST, Component.translatable("notice.afterworld.insight", amount, s.insight));
	}

	/**
	 * The small nodes that are plain attributes (health, armour, attack speed, footing, luck), as
	 * one modifier each; on joining, on respawning and whenever a node is learned.
	 */
	public static void apply(ServerPlayer p, PlayerState s) {
		for (Map.Entry<Stat, Holder<Attribute>> e : ATTRIBUTES.entrySet()) {
			AttributeInstance a = p.getAttribute(e.getValue());
			if (a == null) {
				continue;
			}
			a.removeModifier(MODIFIER);
			double v = bonus(s, e.getKey());
			if (v != 0) {
				AttributeModifier.Operation op = e.getKey() == Stat.ATTACK_SPEED ? AttributeModifier.Operation.ADD_MULTIPLIED_BASE : AttributeModifier.Operation.ADD_VALUE;
				a.addPermanentModifier(new AttributeModifier(MODIFIER, v, op));
			}
		}
	}

	/** Once a second: the skills whose effect is a standing state. */
	public static void tick(ServerPlayer p) {
		PlayerState s = PlayerState.of(p);
		AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean calm = has(s, "wayfarer.pathfinder")
			&& p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p).isEmpty();
		double move = bonus(s, Stat.MOVE) + (calm ? 0.15 : 0);
		AttributeModifier now = speed.getModifier(PATHFINDER);
		if (now == null ? move != 0 : now.amount() != move) {
			speed.removeModifier(PATHFINDER);
			if (move != 0) {
				speed.addTransientModifier(new AttributeModifier(PATHFINDER, move, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		}
	}
}

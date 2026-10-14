package dev.afterworld.test;

import dev.afterworld.Afterworld;
import dev.afterworld.aspect.Aspect;
import dev.afterworld.economy.Contract;
import dev.afterworld.economy.Merchant;
import dev.afterworld.edict.Edict;
import dev.afterworld.movement.Gifts;
import dev.afterworld.player.BondType;
import dev.afterworld.player.Discipline;
import dev.afterworld.player.Origin;
import dev.afterworld.player.Skills;
import dev.afterworld.quest.Chapter;
import dev.afterworld.quest.SideQuests;
import dev.afterworld.registry.AwGear;
import dev.afterworld.site.SiteKind;
import dev.afterworld.world.Faction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Text the game builds from ids at runtime ("aspect.afterworld." + id + ".desc"), which no static
 * scan of the code can check: every family, for every value it can take, must have words.
 */
public class AfterworldLangTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		List<String> missing = context.computeOnClient(mc -> {
			List<String> keys = new ArrayList<>();
			for (Aspect a : Aspect.VALUES) {
				add(keys, "aspect.afterworld." + a.id, "", ".desc", ".hint");
			}
			for (Edict e : Edict.VALUES) {
				add(keys, "edict.afterworld." + e.id, "", ".desc", ".rule", ".capstone");
			}
			for (Discipline d : Discipline.VALUES) {
				add(keys, "discipline.afterworld." + d.id, "");
				if (d != Discipline.NONE) {
					add(keys, "discipline.afterworld." + d.id, ".signature", ".signature.desc", ".style", ".passive");
				}
			}
			for (Origin o : Origin.VALUES) {
				add(keys, "origin.afterworld." + o.id, "", ".tagline");
			}
			for (Gifts.Gift g : Gifts.Gift.values()) {
				add(keys, "gift.afterworld." + g.id, "", ".short", ".how", ".desc");
			}
			for (Skills.Node n : Skills.TREE) {
				add(keys, "skill.afterworld." + n.id(), "", ".desc");
				keys.add("skill.afterworld.branch." + n.branch());
				keys.add("skilltree.afterworld.kind." + n.kind().name().toLowerCase(java.util.Locale.ROOT));
			}
			for (AwGear.Set s : AwGear.Set.values()) {
				add(keys, "set.afterworld." + s.id, "", ".bonus");
			}
			for (Faction f : Faction.VALUES) {
				keys.add("faction.afterworld." + f.id);
			}
			for (BondType b : BondType.VALUES) {
				add(keys, "bond.afterworld." + b.id, "", ".obligation", ".service", ".terms");
				for (String m : new String[] {"bond_offer", "bond_paid", "bond_overdue", "bond_released", "bond_cannot_pay"}) {
					keys.add("message.afterworld." + m + "." + b.id);
				}
			}
			for (Contract.Kind k : Contract.Kind.values()) {
				keys.add("board.afterworld.kind." + k.id);
				keys.add("board.afterworld.track." + k.id);
			}
			for (SiteKind k : SiteKind.VALUES) {
				keys.add("title.afterworld.site." + k.id);
			}
			for (SideQuests.Side q : SideQuests.Side.VALUES) {
				keys.add("side.afterworld." + q.id);
			}
			for (Merchant.Category c : Merchant.Category.values()) {
				keys.add("shop.afterworld.cat." + c.name().toLowerCase(Locale.ROOT));
			}
			for (String t : new String[] {"smithing", "cooking", "brewing", "stars", "melody"}) {
				add(keys, "trial.afterworld." + t, "", ".hint");
			}
			for (Chapter c : Chapter.VALUES) {
				keys.add("quest.afterworld.chapter." + c.id);
				for (Chapter.Objective o : c.objectives) {
					keys.add("quest.afterworld.obj." + o.id());
				}
			}
			keys.removeIf(net.minecraft.locale.Language.getInstance()::has);
			return keys;
		});
		Afterworld.LOG.info("LANGTEST {} missing: {}", missing.size(), missing);
	}

	private static void add(List<String> keys, String base, String... suffixes) {
		for (String s : suffixes) {
			keys.add(base + s);
		}
	}
}

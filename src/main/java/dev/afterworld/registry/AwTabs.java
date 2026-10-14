package dev.afterworld.registry;

import dev.afterworld.Afterworld;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Afterworld's own section of the creative menu: a run of parchment tabs, one per kind of thing,
 * so the Journal and relics never get lost among the blocks and eggs.
 */
public final class AwTabs {
	public enum Section {
		RELICS("relics", () -> new ItemStack(AwItems.JOURNAL)),
		ARMS("arms", () -> new ItemStack(AwItems.APOLOGETIC_BLADE)),
		ARMOR("armor", () -> first("armor")),
		SUPPLIES("supplies", () -> first("supplies")),
		CURIOS("curios", () -> new ItemStack(AwItems.ANOMALY_RESIDUE)),
		BLOCKS("blocks", () -> new ItemStack(AwBlocks.SHRINE_STONE)),
		TRADE("trade", () -> first("trade")),
		BESTIARY("bestiary", () -> first("bestiary")),
		BOSSES("bosses", () -> first("bosses"));

		public final String id;
		final Supplier<ItemStack> icon;

		Section(String id, Supplier<ItemStack> icon) {
			this.id = id;
			this.icon = icon;
		}
	}

	/** The first item filed under a section, for its tab icon. */
	private static ItemStack first(String id) {
		for (var e : AwItems.SECTIONS.entrySet()) {
			if (e.getValue().id.equals(id)) {
				return new ItemStack(e.getKey());
			}
		}
		return new ItemStack(AwItems.JOURNAL);
	}

	private static final Identifier BACKGROUND = Afterworld.id("textures/gui/container/creative_inventory/tab_afterworld.png");

	private AwTabs() {
	}

	public static void init() {
		for (Section section : Section.values()) {
			Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Afterworld.id(section.id), FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.afterworld." + section.id))
				.icon(section.icon)
				.backgroundTexture(BACKGROUND)
				.displayItems((ctx, out) -> {
					if (section == Section.BLOCKS) {
						for (Block block : AwBlocks.WITH_ITEMS) {
							out.accept(block);
						}
						return;
					}
					AwItems.SECTIONS.forEach((item, home) -> {
						if (home == section) {
							out.accept(item);
						}
					});
				})
				.build());
		}
	}
}

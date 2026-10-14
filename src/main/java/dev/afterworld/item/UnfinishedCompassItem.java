package dev.afterworld.item;

import dev.afterworld.edict.EdictSystem;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.rumour.RumourEntry;
import dev.afterworld.site.Site;
import dev.afterworld.site.Sites;
import dev.afterworld.world.AfterworldData;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import org.jspecify.annotations.Nullable;

/**
 * Points toward the nearest unresolved rumour or hidden route. It is unfinished, and sometimes
 * it points at a clue on the way rather than the destination itself.
 */
public class UnfinishedCompassItem extends LoreItem {
	public UnfinishedCompassItem(Properties properties) {
		super(properties, 2, false);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (!(owner instanceof ServerPlayer p) || (owner.tickCount + p.getId()) % 20 != 0) {
			return;
		}
		boolean held = p.getMainHandItem() == stack || p.getOffhandItem() == stack;
		if (!held) {
			return;
		}
		PlayerState s = PlayerState.of(p);
		BlockPos target = null;
		RumourEntry lead = EdictSystem.nearestLead(p, s);
		if (lead != null) {
			target = new BlockPos(lead.x, lead.y, lead.z);
			double dist = Math.sqrt(p.distanceToSqr(lead.x, lead.y, lead.z));
			if (dist > 260) {
				// Far away, the needle settles on something along the way: a clue, not a destination.
				RandomSource r = RandomSource.create(lead.id * 31L + (level.getGameTime() / 2400));
				double t = 0.55;
				target = new BlockPos((int) (p.getX() + (lead.x - p.getX()) * t + (r.nextInt(81) - 40)), lead.y,
					(int) (p.getZ() + (lead.z - p.getZ()) * t + (r.nextInt(81) - 40)));
			}
		} else {
			Site site = Sites.nearestOpen(AfterworldData.get(level.getServer()), null, level.dimension().identifier().toString(), p.getX(), p.getZ());
			if (site != null) {
				target = site.pos();
			}
		}
		LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
		if (target == null) {
			if (current != null) {
				stack.remove(DataComponents.LODESTONE_TRACKER);
			}
			return;
		}
		GlobalPos pos = GlobalPos.of(level.dimension(), target);
		if (current == null || current.target().isEmpty() || !current.target().get().equals(pos)) {
			stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(pos), false));
		}
		Progress.codex(p, "relic.unfinished_compass");
	}
}

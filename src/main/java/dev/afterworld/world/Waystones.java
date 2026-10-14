package dev.afterworld.world;

import dev.afterworld.net.AwPayloads;
import dev.afterworld.player.PlayerState;
import dev.afterworld.player.Progress;
import dev.afterworld.quest.Quests;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.registry.AwSounds;
import dev.afterworld.util.Fx;
import dev.afterworld.world.wonder.WonderPiece;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Waystones: touch one and it remembers you; stand at any you know and it can carry you to any
 * other. The world keeps the list (named after the landmark, the camp, or whoever set the stone);
 * each traveller keeps the ones they have touched.
 */
public final class Waystones {
	/** English names for the landmarks a waystone may stand in. */
	private static final Map<String, String> LANDMARK_NAMES = Map.ofEntries(
		Map.entry("moon_gazebo", "The Moon Gazebo"), Map.entry("moon_gazebo_snow", "The Moon Gazebo"),
		Map.entry("star_spire", "The Crescent Spire"), Map.entry("icyshroom", "The Icyshroom House"),
		Map.entry("airship", "The Wayfarer's Mooring"), Map.entry("waystone", "The Old Waystone"),
		Map.entry("lodge", "The Hunter's Lodge"), Map.entry("lodge_snow", "The Hunter's Lodge"),
		Map.entry("chapel", "The Drowned Bell Chapel"), Map.entry("oasis", "The Hourglass Oasis"),
		Map.entry("stilt_house", "The Fen-Keeper's House"));
	private static final long COOLDOWN = 20L * 20;

	private Waystones() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(AwPayloads.WaystoneTravel.TYPE, (payload, ctx) -> travel(ctx.player(), payload.id()));
	}

	/** Right-clicking a waystone: learn it if new, then offer the road. */
	public static void use(ServerPlayer p, BlockPos pos) {
		ServerLevel level = p.level();
		AfterworldData data = AfterworldData.get(level.getServer());
		AfterworldData.Waystone w = register(level, pos, null, false);
		PlayerState s = PlayerState.of(p);
		if (!s.attuned.contains(w.id)) {
			s.attuned.add(w.id);
			s.questDirty = true;
			Fx.burst(level, ParticleTypes.END_ROD, Vec3.atCenterOf(pos).add(0, 1.0, 0), 40, 0.5, 0.08);
			Fx.runeRing(level, Vec3.atBottomCenterOf(pos), 0x7FE3D2, 1.6, 18);
			Fx.sound(level, Vec3.atCenterOf(pos), AwSounds.UI_DISCOVERY, 1.0F, 0.8F);
			Progress.notice(p, AwPayloads.Notice.WORLD, Component.translatable("notice.afterworld.attuned", Component.literal(w.name)));
			Progress.legend(p, "discovery", "Touched the waystone at " + w.name + ".");
			Quests.onAttune(p, w);
			data.setDirty();
			return;
		}
		openTravel(p, w, false);
	}

	/** Records a waystone in the world's list (naming it the first time). */
	public static AfterworldData.Waystone register(ServerLevel level, BlockPos pos, @Nullable String name, boolean camp) {
		AfterworldData data = AfterworldData.get(level.getServer());
		String dim = level.dimension().identifier().toString();
		AfterworldData.Waystone w = data.waystoneAt(dim, pos.asLong());
		if (w != null) {
			if (camp && !w.camp) {
				w.camp = true;
				w.name = name != null ? name : w.name;
				data.setDirty();
			}
			return w;
		}
		w = new AfterworldData.Waystone();
		w.id = data.nextWaystoneId++;
		w.dim = dim;
		w.pos = pos.asLong();
		w.camp = camp;
		w.name = name != null ? name : nameFor(level, pos);
		data.waystones.put(w.id, w);
		data.setDirty();
		return w;
	}

	private static String nameFor(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, Quests.LANDMARKS);
		if (start.isValid()) {
			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof WonderPiece wp && LANDMARK_NAMES.containsKey(wp.wonder())) {
					return LANDMARK_NAMES.get(wp.wonder());
				}
			}
		}
		Settlement near = Settlements.at(level, pos);
		if (near != null) {
			return near.name;
		}
		return "Waystone at " + pos.getX() + ", " + pos.getZ();
	}

	public static void forget(ServerLevel level, BlockPos pos) {
		AfterworldData data = AfterworldData.get(level.getServer());
		AfterworldData.Waystone w = data.waystoneAt(level.dimension().identifier().toString(), pos.asLong());
		if (w != null) {
			data.waystones.remove(w.id);
			data.setDirty();
		}
	}

	/** What a charter flight on the Wayfarer costs, per leg. */
	public static final int CHARTER_FARE = 30;

	/**
	 * Shows the traveller every waystone they know. {@code from} may be null (a Warden sends you).
	 * A charter (Captain Odo's airship) flies from anywhere, for a fare.
	 */
	public static void openTravel(ServerPlayer p, AfterworldData.@Nullable Waystone from, boolean charter) {
		PlayerState s = PlayerState.of(p);
		s.charterUntil = charter ? p.level().getGameTime() + 20 * 60 : 0;
		AfterworldData data = AfterworldData.get(p.level().getServer());
		List<AwPayloads.WaystoneList.Entry> list = new ArrayList<>();
		for (int id : s.attuned) {
			AfterworldData.Waystone w = data.waystones.get(id);
			if (w == null) {
				continue;
			}
			BlockPos bp = w.blockPos();
			list.add(new AwPayloads.WaystoneList.Entry(w.id, w.name, bp.getX(), bp.getY(), bp.getZ(), w.dim, w.camp));
		}
		long wait = charter ? 0 : Math.max(0, s.travelReadyAt - p.level().getGameTime());
		ServerPlayNetworking.send(p, new AwPayloads.WaystoneList(from == null ? -1 : from.id, (int) wait, charter ? CHARTER_FARE : 0, list));
	}

	private static boolean nearAWaystone(ServerPlayer p) {
		BlockPos at = p.blockPosition();
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-4, -3, -4), at.offset(4, 3, 4))) {
			if (p.level().getBlockState(q).is(AwBlocks.WAYSTONE)) {
				return true;
			}
		}
		// A Warden can send you on from beside her.
		return !dev.afterworld.npc.Npcs.loaded(dev.afterworld.npc.Npc.ISOLDE).stream().filter(m -> m.distanceToSqr(p) < 64).toList().isEmpty();
	}

	private static void travel(ServerPlayer p, int id) {
		PlayerState s = PlayerState.of(p);
		ServerLevel from = p.level();
		long now = from.getGameTime();
		AfterworldData data = AfterworldData.get(from.getServer());
		AfterworldData.Waystone w = data.waystones.get(id);
		boolean charter = s.charterUntil > now;
		if (w == null || !s.attuned.contains(id) || !charter && !nearAWaystone(p)) {
			return;
		}
		if (charter) {
			if (!dev.afterworld.economy.Wallet.spend(p, CHARTER_FARE)) {
				p.sendOverlayMessage(Component.translatable("shop.afterworld.too_poor").withColor(0xC86B5A));
				return;
			}
			s.charterUntil = 0;
		} else if (now < s.travelReadyAt) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.travel_wait", (s.travelReadyAt - now + 19) / 20).withColor(0x9A9488));
			return;
		}
		if (!w.dim.equals(from.dimension().identifier().toString())) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.travel_dimension").withColor(0x9A9488));
			return;
		}
		BlockPos stone = w.blockPos();
		from.getChunk(stone.getX() >> 4, stone.getZ() >> 4);
		if (!from.getBlockState(stone).is(AwBlocks.WAYSTONE)) {
			forget(from, stone);
			s.attuned.rem(id);
			p.sendOverlayMessage(Component.translatable("message.afterworld.travel_gone", w.name).withColor(0xC86B5A));
			return;
		}
		Vec3 land = landing(from, stone);
		if (!charter) {
			s.travelReadyAt = now + (s.skills.contains("wayfarer.pathfinder") ? COOLDOWN / 2 : COOLDOWN);
		}
		Fx.burst(from, ParticleTypes.REVERSE_PORTAL, p.position().add(0, 1, 0), 60, 0.5, 0.15);
		Fx.sound(from, p.position(), AwSounds.EDICT_READY, 1.0F, 0.6F);
		ServerPlayNetworking.send(p, charter ? new AwPayloads.Fade(14, 30, 24, 0xE8DCC0) : new AwPayloads.Fade(6, 10, 14, 0x0A0C12));
		dev.afterworld.util.Scheduler.after(charter ? 16 : 6, () -> {
			if (!p.isAlive() || p.isRemoved()) {
				return;
			}
			p.teleportTo(from, land.x, land.y, land.z, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
			Fx.burst(from, ParticleTypes.END_ROD, land.add(0, 1, 0), 40, 0.5, 0.1);
			Fx.sound(from, land, AwSounds.UI_DISCOVERY, 1.0F, 1.2F);
		});
	}

	/** A Waystone Shard: crush it and you are carried home to the Wayfarers' camp. */
	public static boolean toCamp(ServerPlayer p) {
		ServerLevel level = p.level();
		AfterworldData data = AfterworldData.get(level.getServer());
		AfterworldData.Waystone camp = null;
		for (AfterworldData.Waystone w : data.waystones.values()) {
			if (w.camp && w.dim.equals(level.dimension().identifier().toString())) {
				camp = w;
				break;
			}
		}
		if (camp == null) {
			p.sendOverlayMessage(Component.translatable("message.afterworld.travel_no_camp").withColor(0x9A9488));
			return false;
		}
		BlockPos stone = camp.blockPos();
		level.getChunk(stone.getX() >> 4, stone.getZ() >> 4);
		Vec3 land = landing(level, stone);
		Fx.burst(level, ParticleTypes.REVERSE_PORTAL, p.position().add(0, 1, 0), 60, 0.5, 0.15);
		Fx.sound(level, p.position(), AwSounds.EDICT_READY, 1.0F, 0.6F);
		ServerPlayNetworking.send(p, new AwPayloads.Fade(10, 16, 18, 0x0A0C12));
		dev.afterworld.util.Scheduler.after(10, () -> {
			if (!p.isAlive() || p.isRemoved()) {
				return;
			}
			p.teleportTo(level, land.x, land.y, land.z, java.util.Set.of(), p.getYRot(), p.getXRot(), true);
			Fx.burst(level, ParticleTypes.END_ROD, land.add(0, 1, 0), 40, 0.5, 0.1);
			Fx.sound(level, land, AwSounds.UI_DISCOVERY, 1.0F, 1.2F);
		});
		return true;
	}

	/** Somewhere to stand beside a waystone: the first open, solid-floored spot around it. */
	private static Vec3 landing(ServerLevel level, BlockPos stone) {
		for (Direction d : new Direction[] {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST}) {
			for (int r = 1; r <= 3; r++) {
				BlockPos q = stone.relative(d, r);
				for (int dy = -1; dy <= 2; dy++) {
					BlockPos f = q.above(dy);
					if (level.getBlockState(f.below()).isFaceSturdy(level, f.below(), Direction.UP) && level.getBlockState(f).isAir()
						&& level.getBlockState(f.above()).isAir()) {
						return Vec3.atBottomCenterOf(f);
					}
				}
			}
		}
		return Vec3.atBottomCenterOf(stone.above(2));
	}

	public static boolean isOverworld(ServerLevel level) {
		return level.dimension() == Level.OVERWORLD;
	}
}

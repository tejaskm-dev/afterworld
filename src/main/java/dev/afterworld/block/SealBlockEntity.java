package dev.afterworld.block;

import dev.afterworld.player.Progress;
import dev.afterworld.player.Renown;
import dev.afterworld.registry.AwBlocks;
import dev.afterworld.util.Fx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A seal's puzzle. {@code light}: every input (campfires, candles) must burn at once.
 * {@code order}: the inputs (buttons) must be struck in the order they are listed; a wrong one
 * resets the count. Solving clears the gate blocks, which is how a vault opens.
 *
 * <p>Sites set seals up with a marker, {@code seal:<kind>:<hint>:<inputs>:<gates>}, where inputs
 * and gates are {@code dx,dy,dz} offsets from the seal joined by {@code /}, before rotation.
 */
public class SealBlockEntity extends BlockEntity {
	private String kind = "light";
	private String hint = "";
	private int[] inputs = new int[0];
	private int[] gates = new int[0];
	private boolean solved;
	private int progress;
	private boolean[] last;
	private int litSeen = -1;
	/** A proving ring ({@code waves-<n>}): the wave under way (0: waiting), its creatures, the pause between. */
	private int wave;
	private final java.util.List<java.util.UUID> foes = new java.util.ArrayList<>();
	private int breather;
	private int idle;
	private static final String RING_TAG = "afterworld:ring";

	public SealBlockEntity(BlockPos pos, BlockState state) {
		super(AwBlocks.SEAL_ENTITY, pos, state);
	}

	/** Reads a site's {@code seal:} marker into the seal standing at {@code pos}. */
	public static void fromMarker(ServerLevelAccessor level, BlockPos pos, String name, Rotation r) {
		String[] parts = name.split(":", -1);
		if (parts.length < 5 || !(level.getBlockEntity(pos) instanceof SealBlockEntity be)) {
			dev.afterworld.Afterworld.LOG.warn("Seal marker without a seal at {}: {}", pos, name);
			return;
		}
		be.kind = parts[1];
		be.hint = parts[2];
		be.inputs = offsets(pos, parts[3], r);
		be.gates = offsets(pos, parts[4], r);
		be.setChanged();
	}

	private static int[] offsets(BlockPos origin, String list, Rotation r) {
		if (list.isEmpty()) {
			return new int[0];
		}
		String[] items = list.split("/");
		int[] out = new int[items.length * 3];
		for (int i = 0; i < items.length; i++) {
			String[] c = items[i].split(",");
			int dx = Integer.parseInt(c[0]);
			int dy = Integer.parseInt(c[1]);
			int dz = Integer.parseInt(c[2]);
			// The same turn the wonder's blocks were given (see WonderData.rx / rz).
			int rx = switch (r) {
				case NONE -> dx;
				case CLOCKWISE_90 -> -dz;
				case CLOCKWISE_180 -> -dx;
				case COUNTERCLOCKWISE_90 -> dz;
			};
			int rz = switch (r) {
				case NONE -> dz;
				case CLOCKWISE_90 -> dx;
				case CLOCKWISE_180 -> -dz;
				case COUNTERCLOCKWISE_90 -> -dx;
			};
			out[i * 3] = origin.getX() + rx;
			out[i * 3 + 1] = origin.getY() + dy;
			out[i * 3 + 2] = origin.getZ() + rz;
		}
		return out;
	}

	private BlockPos at(int[] list, int i) {
		return new BlockPos(list[i * 3], list[i * 3 + 1], list[i * 3 + 2]);
	}

	/**
	 * An offering seal ({@code offer-<namespace>.<item>-<count>}) opens when the traveller gives it
	 * what it asks for: the challenge lantern at the dojo's heart, five glyph tablets at the vault.
	 */
	public boolean offer(ServerLevel level, BlockPos pos, BlockState state, net.minecraft.server.level.ServerPlayer p, net.minecraft.world.item.ItemStack held) {
		if (this.solved || !this.kind.startsWith("offer-")) {
			return false;
		}
		String[] k = this.kind.split("-");
		if (k.length < 3) {
			return false;
		}
		var want = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(k[1].replaceFirst("\\.", ":")));
		int n = Integer.parseInt(k[2]);
		if (!held.is(want)) {
			return false;
		}
		if (dev.afterworld.util.Inv.count(p, want) < n) {
			p.sendSystemMessage(Component.translatable("seal.afterworld.offer_more", n, new net.minecraft.world.item.ItemStack(want).getHoverName()).withColor(0xD8B45A));
			return true;
		}
		dev.afterworld.util.Inv.take(p, want, n);
		Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.END_PORTAL_FRAME_FILL, 1.4F, 0.7F);
		solve(level, pos, state);
		return true;
	}

	public MutableComponent hintText() {
		int n = this.inputs.length / 3;
		MutableComponent text = Component.translatable(this.hint.isEmpty() ? "seal.afterworld.hint" : "seal.afterworld.hint." + this.hint);
		if (n > 0 && this.kind.equals("order") && this.progress > 0) {
			text.append(Component.translatable("seal.afterworld.progress", this.progress, n).withColor(0xB8AE98));
		}
		return text;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, SealBlockEntity be) {
		if (!be.solved && be.kind.startsWith("waves") && level instanceof ServerLevel ring) {
			be.ringTick(ring, pos, state);
			return;
		}
		if (be.solved || be.inputs.length == 0 || !(level instanceof ServerLevel sl) || (sl.getGameTime() + pos.asLong()) % 3 != 0) {
			return;
		}
		if (sl.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 40, false) == null) {
			return;
		}
		int n = be.inputs.length / 3;
		if (be.kind.equals("order")) {
			if (be.last == null || be.last.length != n) {
				be.last = new boolean[n];
			}
			for (int i = 0; i < n; i++) {
				BlockPos ip = be.at(be.inputs, i);
				if (!sl.isLoaded(ip)) {
					return;
				}
				BlockState s = sl.getBlockState(ip);
				boolean on = s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED);
				if (on && !be.last[i]) {
					if (i == be.progress) {
						be.progress++;
						// Each right stone rings a note higher than the last.
						Fx.sound(sl, Vec3.atCenterOf(ip), SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 0.6F + 0.9F * be.progress / n);
						Fx.burst(sl, ParticleTypes.NOTE, Vec3.atCenterOf(ip).add(0, 0.7, 0), 3, 0.2, 0.0);
						if (be.progress == n) {
							be.solve(sl, pos, state);
						}
					} else {
						be.progress = 0;
						Fx.sound(sl, Vec3.atCenterOf(ip), SoundEvents.NOTE_BLOCK_BASS.value(), 1.2F, 0.5F);
						Fx.burst(sl, ParticleTypes.SMOKE, Vec3.atCenterOf(ip).add(0, 0.6, 0), 8, 0.2, 0.02);
					}
					be.setChanged();
				}
				be.last[i] = on;
			}
			return;
		}
		int lit = 0;
		for (int i = 0; i < n; i++) {
			BlockPos ip = be.at(be.inputs, i);
			if (!sl.isLoaded(ip)) {
				return;
			}
			BlockState s = sl.getBlockState(ip);
			if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT)) {
				lit++;
			}
		}
		if (lit != be.litSeen) {
			if (be.litSeen >= 0 && lit > be.litSeen && lit < n) {
				Fx.sound(sl, Vec3.atCenterOf(pos), SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.6F + 0.8F * lit / n);
				Fx.burst(sl, ParticleTypes.ENCHANT, Vec3.atCenterOf(pos).add(0, 0.8, 0), 10 * lit, 0.4, 0.4);
			}
			be.litSeen = lit;
		}
		if (lit == n) {
			be.solve(sl, pos, state);
		}
	}

	// ------------------------------------------------------------------ proving rings

	/** A ring's stone touched: the first wave comes (true), or how the fight stands is said (false). */
	public boolean challenge(ServerLevel level, BlockPos pos, ServerPlayer p) {
		if (this.solved || !this.kind.startsWith("waves")) {
			return false;
		}
		if (this.wave > 0) {
			p.sendOverlayMessage(Component.translatable("seal.afterworld.ring.underway", this.wave, waves(), this.foes.size()).withColor(0xE8DCC0));
			return true;
		}
		this.wave = 1;
		this.idle = 0;
		spawnWave(level, pos);
		Fx.sound(level, Vec3.atCenterOf(pos), SoundEvents.RAID_HORN.value(), 2.0F, 0.9F);
		Fx.shake(level, Vec3.atCenterOf(pos), 24, 1.5F, 15);
		for (ServerPlayer o : level.getPlayers(o -> o.distanceToSqr(Vec3.atCenterOf(pos)) < 32 * 32)) {
			o.sendSystemMessage(Component.translatable("seal.afterworld.ring.wakes", waves()).withColor(0xE8A33D));
		}
		setChanged();
		return true;
	}

	/** For tests: how a ring's fight stands. */
	public String ringDebug() {
		return "kind " + this.kind + " wave " + this.wave + "/" + waves() + " foes " + this.foes.size() + " breather " + this.breather + " idle " + this.idle + " solved " + this.solved;
	}

	private int waves() {
		try {
			return Math.max(1, Integer.parseInt(this.kind.substring(this.kind.indexOf('-') + 1)));
		} catch (RuntimeException e) {
			return 3;
		}
	}

	/**
	 * Where a creature can stand near the seal's own height: the first floor found going down from a
	 * little above it (so a seal deep in a cave fills the cave, not the grass above it).
	 */
	private static int floorNear(ServerLevel level, int x, int y0, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = y0 + 4; y >= y0 - 8; y--) {
			p.set(x, y, z);
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
				&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
				return y;
			}
		}
		return y0;
	}

	private void spawnWave(ServerLevel level, BlockPos pos) {
		String[] pool = dev.afterworld.dungeon.Ladder.poolFor(level, pos);
		var near = level.getPlayers(o -> o.distanceToSqr(Vec3.atCenterOf(pos)) < 32 * 32 && !o.isSpectator());
		int n = 2 + this.wave + Math.max(0, near.size() - 1) * 2;
		var r = level.getRandom();
		for (int i = 0; i < n; i++) {
			String id = i == 0 && this.wave == waves() ? dev.afterworld.dungeon.Ladder.heavyFor(level, pos) : pool[r.nextInt(pool.length)];
			double a = r.nextDouble() * Math.PI * 2;
			double d = 6 + r.nextDouble() * 3;
			int x = pos.getX() + (int) Math.round(Math.cos(a) * d);
			int z = pos.getZ() + (int) Math.round(Math.sin(a) * d);
			int y = floorNear(level, x, pos.getY(), z);
			var m = dev.afterworld.dungeon.Ladder.foe(level, id, new Vec3(x + 0.5, y, z + 0.5), 3 + this.wave, r.nextFloat() < 0.08F, RING_TAG, this.foes::add);
			if (m != null) {
				ServerPlayer target = null;
				double best = Double.MAX_VALUE;
				for (ServerPlayer o : near) {
					double dd = o.distanceToSqr(m);
					if (dd < best) {
						best = dd;
						target = o;
					}
				}
				if (target != null) {
					m.setTarget(target);
				}
				Fx.burst(level, Fx.dust(0xE8A33D, 1.5F), m.position().add(0, 1, 0), 14, 0.5, 0.05);
			}
		}
	}

	private void ringTick(ServerLevel level, BlockPos pos, BlockState state) {
		if (this.wave == 0 || level.getGameTime() % 10 != 0) {
			return;
		}
		Vec3 c = Vec3.atCenterOf(pos);
		if (level.getNearestPlayer(c.x, c.y, c.z, 40, false) == null) {
			// Everyone ran: the ring goes back to sleep and its creatures with it.
			if (++this.idle > 120) {
				for (java.util.UUID id : this.foes) {
					if (level.getEntity(id) instanceof net.minecraft.world.entity.Entity e) {
						e.discard();
					}
				}
				this.foes.clear();
				this.wave = 0;
				setChanged();
			}
			return;
		}
		this.idle = 0;
		this.foes.removeIf(id -> !(level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity e) || !e.isAlive() || e.distanceToSqr(c) > 64 * 64);
		if (!this.foes.isEmpty()) {
			return;
		}
		if (this.wave >= waves()) {
			this.wave = 0;
			solve(level, pos, state);
			return;
		}
		if (++this.breather < 4) {
			return;
		}
		this.breather = 0;
		this.wave++;
		spawnWave(level, pos);
		Fx.sound(level, c, SoundEvents.RAID_HORN.value(), 1.6F, 1.0F + 0.08F * this.wave);
		for (ServerPlayer o : level.getPlayers(o -> o.distanceToSqr(c) < 32 * 32)) {
			o.sendOverlayMessage(Component.translatable("seal.afterworld.ring.wave", this.wave, waves()).withColor(0xE8A33D));
		}
		setChanged();
	}

	private void solve(ServerLevel level, BlockPos pos, BlockState state) {
		this.solved = true;
		setChanged();
		level.setBlock(pos, state.setValue(SealBlock.SOLVED, true), Block.UPDATE_ALL);
		for (int i = 0; i < this.gates.length / 3; i++) {
			BlockPos gp = at(this.gates, i);
			if (!level.getBlockState(gp).isAir()) {
				level.destroyBlock(gp, false);
			}
		}
		Vec3 c = Vec3.atCenterOf(pos);
		// Whatever slept behind this seal wakes now.
		dev.afterworld.entity.boss.WardenBoss.wakeNear(level, c, 48);
		Fx.sound(level, c, SoundEvents.VAULT_OPEN_SHUTTER, 1.6F, 0.8F);
		Fx.sound(level, c, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.4F);
		Fx.burst(level, ParticleTypes.END_ROD, c.add(0, 0.8, 0), 40, 0.6, 0.15);
		Fx.ring(level, ParticleTypes.ENCHANT, c.add(0, 0.2, 0), 2.5, 32);
		ExperienceOrb.award(level, c.add(0, 1, 0), 30);
		String what = Component.translatable("seal.afterworld.legend").getString();
		for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(c) < 48 * 48)) {
			p.sendSystemMessage(Component.translatable("seal.afterworld.opened").withColor(0xE8C060));
			Fx.privateSound(p, dev.afterworld.registry.AwSounds.UI_DISCOVERY, 1.0F, 0.9F);
			Progress.legend(p, "discovery", what);
			Renown.grant(p, 30, what);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.kind = input.getStringOr("kind", "light");
		this.hint = input.getStringOr("hint", "");
		this.inputs = input.getIntArray("inputs").orElse(new int[0]);
		this.gates = input.getIntArray("gates").orElse(new int[0]);
		this.solved = input.getBooleanOr("solved", false);
		this.progress = input.getIntOr("progress", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("kind", this.kind);
		output.putString("hint", this.hint);
		output.putIntArray("inputs", this.inputs);
		output.putIntArray("gates", this.gates);
		output.putBoolean("solved", this.solved);
		output.putInt("progress", this.progress);
	}
}

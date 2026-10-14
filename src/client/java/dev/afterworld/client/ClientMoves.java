package dev.afterworld.client;

import dev.afterworld.movement.Gifts;
import dev.afterworld.movement.Moves;
import dev.afterworld.net.AwPayloads.MoveAction;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The client half of movement: reads the keys, moves the player at once (so every move feels
 * instant), and tells the server what happened so it can pay stamina and grant protection.
 *
 * <ul>
 * <li>Dodge: the dodge key while moving. A short burst in the direction you're moving.</li>
 * <li>Parry: the dodge key while standing still. A heartbeat-long window that turns a blow.</li>
 * <li>Slide: crouch while sprinting. Low and fast, under gaps.</li>
 * <li>Climb: jump at a wall and keep pushing into it to grab hold. WASD moves along the wall,
 * jump leaps up it, back and jump kicks off, sneak lets go; over the top you pull yourself up.
 * Stamina drains as you go. Anyone can climb; the Climbing Claws make it faster and cheaper.</li>
 * <li>Mantle: in the air, press jump facing a ledge within reach (and with room above it) to pull
 * yourself up. Never from the ground, never on stairs: only when you mean it.</li>
 * <li>Gifts: double jump, glide (hold jump while falling), run on water.</li>
 * </ul>
 */
public final class ClientMoves {
	private static boolean dodgeWasDown;
	private static boolean jumpWasDown;
	private static boolean shiftWasDown;
	private static int dodgeTicks;
	private static Vec3 dodgeDir = Vec3.ZERO;
	private static int slideTicks;
	private static Vec3 slideDir = Vec3.ZERO;
	private static float slideSpeed;
	private static int moveCooldown;
	private static int airJumps;
	private static boolean gliding;
	private static boolean climbing;
	/** The side of the wall being climbed. */
	private static Direction climbFace;
	private static int climbCooldown;
	private static int climbSound;
	/** Pulling up over a ledge: ticks left, and which way. */
	private static int pullUpTicks;
	private static Direction pullDir;
	/** The top of the ledge being pulled onto (feet height to reach before stepping over). */
	private static double pullTopY;
	private static boolean waterWalking;
	private static int mantleCooldown;
	/** Ticks since the parry was pressed, for the HUD glint. */
	public static int parryFlash = -1;

	private ClientMoves() {
	}

	public static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || p.isSpectator() || p.getAbilities().flying || p.isPassenger() || mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) {
			stopHeld(p);
			dodgeWasDown = AfterworldClient.DODGE.isDown();
			return;
		}
		if (moveCooldown > 0) {
			moveCooldown--;
		}
		if (mantleCooldown > 0) {
			mantleCooldown--;
		}
		if (climbCooldown > 0) {
			climbCooldown--;
		}
		if (parryFlash >= 0 && ++parryFlash > 8) {
			parryFlash = -1;
		}
		boolean creative = p.isCreative();
		boolean onGround = p.onGround();
		if (onGround || p.isInWater() || p.onClimbable()) {
			airJumps = 0;
		}
		var keys = p.input.keyPresses;
		Vec2 move = p.input.getMoveVector();
		boolean jumpDown = mc.options.keyJump.isDown();
		boolean jumpPressed = jumpDown && !jumpWasDown;
		boolean shiftDown = mc.options.keyShift.isDown();
		boolean shiftPressed = shiftDown && !shiftWasDown;
		boolean dodgeDown = AfterworldClient.DODGE.isDown();
		boolean dodgePressed = dodgeDown && !dodgeWasDown;
		jumpWasDown = jumpDown;
		shiftWasDown = shiftDown;
		dodgeWasDown = dodgeDown;

		// ---- dodge and parry
		if (dodgePressed && moveCooldown == 0) {
			if (move.lengthSquared() > 0.01F) {
				if (afford(creative, Moves.DODGE_COST)) {
					dodgeDir = worldDir(p, move);
					dodgeTicks = 3;
					moveCooldown = 10;
					if (onGround) {
						p.setDeltaMovement(p.getDeltaMovement().x, 0.2, p.getDeltaMovement().z);
					}
					send(MoveAction.DODGE, (float) dodgeDir.x, (float) dodgeDir.z);
					spend(creative, Moves.DODGE_COST);
					puff(p, ParticleTypes.CLOUD, 6);
				}
			} else if (afford(creative, Moves.PARRY_COST)) {
				moveCooldown = 8;
				parryFlash = 0;
				p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
				send(MoveAction.PARRY, 0, 0);
				spend(creative, Moves.PARRY_COST);
			}
		}
		if (dodgeTicks > 0) {
			dodgeTicks--;
			Vec3 v = p.getDeltaMovement();
			double speed = onGround ? 0.78 : 0.6;
			p.setDeltaMovement(dodgeDir.x * speed, v.y, dodgeDir.z * speed);
		}

		// ---- slide
		if (shiftPressed && onGround && p.isSprinting() && keys.forward() && slideTicks == 0 && moveCooldown == 0 && afford(creative, Moves.SLIDE_COST)) {
			slideDir = worldDir(p, new Vec2(0, 1));
			slideSpeed = 0.95F;
			slideTicks = 14;
			moveCooldown = 6;
			send(MoveAction.SLIDE, (float) slideDir.x, (float) slideDir.z);
			spend(creative, Moves.SLIDE_COST);
		}
		if (slideTicks > 0) {
			slideTicks--;
			if (!shiftDown || !onGround && slideTicks < 10) {
				slideTicks = 0;
			} else {
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(slideDir.x * slideSpeed, v.y, slideDir.z * slideSpeed);
				slideSpeed *= 0.93F;
				if (p.tickCount % 2 == 0) {
					puff(p, ParticleTypes.POOF, 1);
				}
			}
		}

		// ---- the wall: climb, pull up over the top, kick off
		var config = dev.afterworld.AwConfig.get();
		Direction wall = wallSide(p);
		boolean facingWall = wall != null && facing(p, wall);
		if (pullUpTicks > 0) {
			// Up first, just clear of the edge, then over it onto the top.
			pullUpTicks--;
			Vec3 over = Vec3.atLowerCornerOf(pullDir.getUnitVec3i());
			double rise = pullTopY + 0.08 - p.getY();
			if (rise > 0.02 && pullUpTicks > 2) {
				p.setDeltaMovement(over.x * 0.03, Math.min(0.36, rise + 0.02), over.z * 0.03);
			} else {
				p.setDeltaMovement(over.x * 0.22, Math.max(0.0, Math.min(0.05, rise)), over.z * 0.22);
				if (onGround && rise <= 0.02) {
					pullUpTicks = 0;
				}
			}
			p.fallDistance = 0;
		} else if (climbing) {
			climbTick(p, creative, keys, move, jumpPressed, shiftPressed);
		} else if (config.climbing && !onGround && !p.isInWater() && !p.onClimbable() && !gliding && facingWall && keys.forward() && climbCooldown == 0
			&& wallRises(p, wall) && (creative || ClientState.stamina > 4)) {
			// A jump at a wall, still pushing into it: take hold (or, if the top is in reach, go straight over).
			if (ledgeInReach(p, wall)) {
				pullUp(p, wall, false);
			} else {
				climbFace = wall;
				setClimbing(p, true);
				p.setDeltaMovement(0, 0, 0);
			}
		} else if (config.mantle && jumpPressed && !onGround && facingWall && keys.forward() && mantleCooldown == 0 && ledgeInReach(p, wall)
			&& afford(creative, Moves.MANTLE_COST)) {
			pullUp(p, wall, true);
		} else if (jumpPressed && !onGround && wall != null && keys.backward() && !p.isInWater() && moveCooldown == 0 && afford(creative, Moves.WALL_JUMP_COST)) {
			// Only on purpose: back and jump, beside a wall.
			kickOff(p, wall, creative);
		}

		// ---- double jump and glide
		if (jumpPressed && !onGround && wall == null && !climbing && !p.isInWater() && airJumps == 0 && ClientState.hasGift(Gifts.Gift.DOUBLE_JUMP)
			&& p.getDeltaMovement().y < 0.25 && afford(creative, Moves.DOUBLE_JUMP_COST)) {
			airJumps = 1;
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(v.x * 1.1, 0.55, v.z * 1.1);
			p.fallDistance = 0;
			send(MoveAction.DOUBLE_JUMP, 0, 0);
			spend(creative, Moves.DOUBLE_JUMP_COST);
			puff(p, ParticleTypes.CLOUD, 8);
		}
		boolean wantGlide = ClientState.hasGift(Gifts.Gift.GLIDE) && jumpDown && !onGround && !climbing && !p.isInWater()
			&& (gliding || p.getDeltaMovement().y < -0.18) && (creative || ClientState.stamina > 0);
		if (wantGlide != gliding) {
			gliding = wantGlide;
			send(MoveAction.GLIDE, gliding ? 1 : 0, 0);
		}
		if (gliding) {
			Vec3 v = p.getDeltaMovement();
			Vec3 look = p.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
			double target = 0.55 + Math.max(0, -look.y) * 0.4;
			double vx = Mth.lerp(0.08, v.x, flat.x * target);
			double vz = Mth.lerp(0.08, v.z, flat.z * target);
			double vy = Math.max(v.y, look.y < -0.5 ? -0.3 : -0.085);
			p.setDeltaMovement(vx, vy, vz);
			p.fallDistance = 0;
		}

		// ---- running on water (the mixin lets the feet stand on it; this tells the server to drain stamina)
		BlockPos feet = p.blockPosition();
		boolean onWater = ClientState.hasGift(Gifts.Gift.WATER_WALK) && onGround && p.level().getFluidState(feet).is(FluidTags.WATER) && p.isSprinting();
		if (onWater != waterWalking) {
			waterWalking = onWater;
			send(MoveAction.WATER_WALK, onWater ? 1 : 0, 0);
		}
		if (waterWalking && p.tickCount % 2 == 0) {
			p.level().addParticle(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.05, p.getZ(), 0, 0.05, 0);
		}
	}

	/** Can the client's player stand on water right now? (Used by the shared mixin.) */
	public static boolean waterWalk(net.minecraft.world.entity.player.Player player) {
		Minecraft mc = Minecraft.getInstance();
		return player == mc.player && ClientState.hasGift(Gifts.Gift.WATER_WALK) && player.isSprinting() && !player.isShiftKeyDown()
			&& (player.isCreative() || ClientState.stamina > 0);
	}

	private static void stopHeld(LocalPlayer p) {
		if (gliding) {
			gliding = false;
			send(MoveAction.GLIDE, 0, 0);
		}
		if (p != null) {
			setClimbing(p, false);
		}
		slideTicks = 0;
		dodgeTicks = 0;
	}

	private static void setClimbing(LocalPlayer p, boolean on) {
		if (on != climbing) {
			climbing = on;
			send(MoveAction.CLIMB, on ? 1 : 0, on && climbFace != null ? climbFace.get3DDataValue() : -1);
			if (on) {
				p.playSound(SoundEvents.LADDER_STEP, 0.6F, 1.2F);
				ClientState.firstClimb();
			} else {
				climbCooldown = 6;
			}
		}
	}

	/** One tick on the wall: move along it, leap, kick off, let go, or reach the top. */
	private static void climbTick(LocalPlayer p, boolean creative, net.minecraft.world.entity.player.Input keys, Vec2 move, boolean jumpPressed, boolean shiftPressed) {
		Direction w = climbFace;
		if (w == null || p.isInWater() || p.onClimbable() || p.onGround() && !keys.forward()) {
			setClimbing(p, false);
			return;
		}
		if (!touching(p, w, 0.6)) {
			// The wall ends: over the top if there's floor to stand on, otherwise just drop.
			if (ledgeInReach(p, w)) {
				pullUp(p, w, false);
			} else {
				setClimbing(p, false);
			}
			return;
		}
		if (shiftPressed) {
			setClimbing(p, false);
			return;
		}
		if (!creative && ClientState.stamina <= 0) {
			// Spent: the grip goes.
			setClimbing(p, false);
			p.setDeltaMovement(0, -0.1, 0);
			return;
		}
		if (jumpPressed) {
			if (keys.backward()) {
				kickOff(p, w, creative);
				return;
			}
			if (afford(creative, Moves.CLIMB_LEAP_COST)) {
				Vec3 into = Vec3.atLowerCornerOf(w.getUnitVec3i());
				p.setDeltaMovement(into.x * 0.04, 0.62, into.z * 0.04);
				send(MoveAction.CLIMB_LEAP, 0, 0);
				spend(creative, Moves.CLIMB_LEAP_COST);
				p.playSound(SoundEvents.LADDER_STEP, 0.8F, 0.8F);
				return;
			}
		}
		boolean claws = ClientState.hasGift(Gifts.Gift.CLIMB);
		double speed = claws ? 0.14 : 0.095;
		// Forward is up the wall, back is down; strafing moves along it.
		Direction right = w.getClockWise();
		double up = move.y * speed;
		double side = -move.x * speed * 0.8;
		Vec3 into = Vec3.atLowerCornerOf(w.getUnitVec3i());
		Vec3 along = Vec3.atLowerCornerOf(right.getUnitVec3i());
		Vec3 v = p.getDeltaMovement();
		// Keep the leap's rise while it lasts; otherwise hold steady on the wall.
		double vy = v.y > up + 0.05 ? v.y - 0.08 : up;
		p.setDeltaMovement(along.x * side + into.x * 0.03, vy, along.z * side + into.z * 0.03);
		p.fallDistance = 0;
		if ((Math.abs(up) > 0.01 || Math.abs(side) > 0.01) && ++climbSound % 7 == 0) {
			p.playSound(SoundEvents.LADDER_STEP, 0.35F, 0.9F + p.getRandom().nextFloat() * 0.3F);
		}
	}

	private static void pullUp(LocalPlayer p, Direction w, boolean paid) {
		pullDir = w;
		pullTopY = p.getY() + Math.max(0, ledgeHeight(p, w));
		pullUpTicks = 10;
		mantleCooldown = 14;
		setClimbing(p, false);
		if (paid) {
			send(MoveAction.MANTLE, 0, 0);
			spend(p.isCreative(), Moves.MANTLE_COST);
		} else {
			send(MoveAction.MANTLE, 1, 0);
		}
		p.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.6F, 1.3F);
	}

	private static void kickOff(LocalPlayer p, Direction w, boolean creative) {
		Vec3 away = Vec3.atLowerCornerOf(w.getOpposite().getUnitVec3i());
		p.setDeltaMovement(away.x * 0.5, 0.6, away.z * 0.5);
		moveCooldown = 6;
		airJumps = 0;
		setClimbing(p, false);
		send(MoveAction.WALL_JUMP, (float) away.x, (float) away.z);
		spend(creative, Moves.WALL_JUMP_COST);
		puff(p, ParticleTypes.POOF, 4);
	}

	/** Whether the player is looking at the wall on side {@code w} (within about 55 degrees). */
	private static boolean facing(LocalPlayer p, Direction w) {
		Vec3 look = p.getLookAngle();
		double len = Math.sqrt(look.x * look.x + look.z * look.z);
		if (len < 1e-3) {
			return false;
		}
		return (look.x * w.getStepX() + look.z * w.getStepZ()) / len > 0.57;
	}

	/** Whether there is wall on side {@code w} at the player's chest (or {@code up} blocks above the feet). */
	private static boolean touching(LocalPlayer p, Direction w, double up) {
		AABB box = p.getBoundingBox();
		AABB probe = new AABB(box.minX, box.minY + up, box.minZ, box.maxX, box.minY + up + 0.6, box.maxZ).move(w.getStepX() * 0.14, 0, w.getStepZ() * 0.14).deflate(0.02, 0, 0.02);
		return !p.level().noCollision(p, probe);
	}

	/** Whether the wall goes on up past the player's head: something to climb, not a step. */
	private static boolean wallRises(LocalPlayer p, Direction w) {
		return touching(p, w, 0.4) && touching(p, w, 1.3);
	}

	/**
	 * Whether there is a ledge to pull up onto: the wall's top within reach of the hands (between
	 * a third of a block and a block and a third above the feet), two clear blocks above it to
	 * stand in, room over the head to rise through, and not just a stair.
	 */
	private static boolean ledgeInReach(LocalPlayer p, Direction w) {
		double ledge = ledgeHeight(p, w);
		if (ledge < 0.35 || ledge > 1.35) {
			return false;
		}
		var level = p.level();
		BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
		var below = level.getBlockState(under).getBlock();
		return !(below instanceof net.minecraft.world.level.block.StairBlock) && !(below instanceof net.minecraft.world.level.block.SlabBlock);
	}

	private static boolean afford(boolean creative, float cost) {
		return creative || ClientState.stamina >= cost * 0.8F;
	}

	private static void spend(boolean creative, float cost) {
		if (!creative) {
			ClientState.stamina = Math.max(0, ClientState.stamina - cost);
		}
	}

	private static void send(int action, float x, float z) {
		if (ClientPlayNetworking.canSend(MoveAction.TYPE)) {
			ClientPlayNetworking.send(new MoveAction(action, x, z));
		}
	}

	/** The world direction of a movement input (x: strafe left, y: forward), on the player's yaw. */
	private static Vec3 worldDir(LocalPlayer p, Vec2 move) {
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		double s = Mth.sin(yaw);
		double c = Mth.cos(yaw);
		Vec3 d = new Vec3(move.x * c - move.y * s, 0, move.y * c + move.x * s);
		return d.lengthSqr() < 1e-6 ? Vec3.ZERO : d.normalize();
	}

	/** The side on which the player is pressed against a wall (the one they face, if several). */
	private static Direction wallSide(LocalPlayer p) {
		AABB box = p.getBoundingBox();
		Direction facing = p.getDirection();
		Direction[] order = {facing, facing.getClockWise(), facing.getCounterClockWise(), facing.getOpposite()};
		for (Direction d : order) {
			AABB probe = box.move(d.getStepX() * 0.12, 0.1, d.getStepZ() * 0.12).deflate(0.02, 0.15, 0.02);
			if (!p.level().noCollision(p, probe)) {
				return d;
			}
		}
		return null;
	}

	/** How far above the feet the top of the wall is, if it can be climbed onto (-1: no ledge in reach). */
	private static double ledgeHeight(LocalPlayer p, Direction wall) {
		BlockPos feet = BlockPos.containing(p.getX(), p.getY() + 0.05, p.getZ());
		BlockPos front = feet.relative(wall);
		var level = p.level();
		for (int h = 0; h <= 3; h++) {
			BlockPos base = front.above(h);
			boolean solid = !level.getBlockState(base).getCollisionShape(level, base).isEmpty();
			if (solid) {
				continue;
			}
			if (h == 0) {
				return -1;
			}
			BlockPos above = base.above();
			boolean room = level.getBlockState(above).getCollisionShape(level, above).isEmpty()
				&& level.getBlockState(above.above()).getCollisionShape(level, above.above()).isEmpty();
			BlockPos head = feet.above(h + 1);
			boolean clear = level.getBlockState(head).getCollisionShape(level, head).isEmpty();
			if (!room || !clear) {
				return -1;
			}
			BlockPos under = base.below();
			double top = under.getY() + level.getBlockState(under).getCollisionShape(level, under).max(Direction.Axis.Y);
			return top - p.getY();
		}
		return -1;
	}

	private static void puff(LocalPlayer p, net.minecraft.core.particles.SimpleParticleType type, int n) {
		for (int i = 0; i < n; i++) {
			p.level().addParticle(type, p.getX() + (p.getRandom().nextDouble() - 0.5) * 0.6, p.getY() + 0.1, p.getZ() + (p.getRandom().nextDouble() - 0.5) * 0.6,
				0, 0.02, 0);
		}
	}

	public static void reset() {
		gliding = false;
		climbing = false;
		pullUpTicks = 0;
		waterWalking = false;
		slideTicks = 0;
		dodgeTicks = 0;
		airJumps = 0;
	}

	/** For the HUD: whether a held move is draining stamina right now. */
	public static boolean draining() {
		return gliding || climbing || waterWalking;
	}

	/** The local player's move right now, for the body animations (see PlayerPoses). */
	public static Pose pose() {
		if (pullUpTicks > 0) {
			return new Pose(Pose.MANTLE, 1.0F - pullUpTicks / 10.0F, null);
		}
		if (climbing) {
			// How fast the hands move with how fast the climber does.
			Vec3 v = Minecraft.getInstance().player.getDeltaMovement();
			return new Pose(Pose.CLIMB, (float) Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z), climbFace);
		}
		if (slideTicks > 0) {
			return new Pose(Pose.SLIDE, slideTicks / 14.0F, null);
		}
		if (dodgeTicks > 0) {
			return new Pose(Pose.DODGE, dodgeTicks / 3.0F, null);
		}
		if (gliding) {
			return new Pose(Pose.GLIDE, 0, null);
		}
		return Pose.NONE;
	}

	/** A move the body is acting out: which, how far through it (0..1), and the wall it faces. */
	public record Pose(int kind, float progress, Direction wall) {
		public static final int IDLE = 0;
		public static final int CLIMB = 1;
		public static final int MANTLE = 2;
		public static final int SLIDE = 3;
		public static final int DODGE = 4;
		public static final int GLIDE = 5;
		public static final Pose NONE = new Pose(IDLE, 0, null);
	}

}

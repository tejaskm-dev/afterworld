package dev.afterworld.entity.boss;

import dev.afterworld.player.Progress;
import dev.afterworld.player.Renown;
import dev.afterworld.util.Fx;
import dev.afterworld.world.AfterworldData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The Wardens of the Lands: five great powers that keep their lairs. They fight with a table of
 * moves, each a wind-up you can read (a stance, a glow, a circle on the ground), a strike, and a
 * moment of recovery that is your opening. Some sleep until woken (by a seal's riddle or an
 * offering); all of them stay near home. The first time each falls, its heart is given up and the
 * world remembers.
 */
public abstract class WardenBoss extends BossMob implements Enemy {
	public static final int A_IDLE = 0;
	public static final int A_WINDUP = 1;
	public static final int A_STRIKE = 2;
	public static final int A_SLAM = 3;
	public static final int A_CAST = 4;
	public static final int A_SPIN = 5;
	public static final int A_STAGGER = 6;
	public static final int A_SLEEP = 7;
	public static final int A_RISE = 8;
	public static final int A_THROW = 9;
	public static final int A_RAISE = 10;

	private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(WardenBoss.class, EntityDataSerializers.BOOLEAN);

	/** A move: how long it winds up, acts and recovers; when it can be used; how often. */
	public record Move(String id, int windup, int active, int recovery, double minRange, double maxRange, int cooldown, int minPhase, int weight,
						  int windAnim, int actAnim) {
	}

	protected Vec3 home = Vec3.ZERO;
	protected LivingEntity target;
	private Move current;
	private int moveTick;
	private final Map<String, Long> ready = new HashMap<>();
	private int rising;
	private boolean firstFall = true;
	/** An echo in the Warden's Ladder: the world keeps no record of its fall, and it has no heart. */
	private boolean echo;

	protected WardenBoss(EntityType<? extends PathfinderMob> type, Level level, BossEvent.BossBarColor color) {
		super(type, level, color);
		markBoss();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DORMANT, false);
	}

	/** The bit this Warden sets in the world's record of the fallen (see Chapter WARDENS). */
	protected abstract int bit();

	protected abstract List<Move> moves();

	/** The wind-up begins: face the target and show what is coming. */
	protected void windup(ServerLevel level, Move m, LivingEntity target) {
	}

	/** The wind-up is over: the blow, the volley, the wave. */
	protected abstract void release(ServerLevel level, Move m, LivingEntity target);

	/** Each tick of a move's active part, after its release (channels, dashes). */
	protected void during(ServerLevel level, Move m, int t, LivingEntity target) {
	}

	/** How fast it closes the distance. */
	protected double chaseSpeed() {
		return 1.0;
	}

	/** How close it likes to stand (casters keep their distance). */
	protected double preferredRange() {
		return 2.5;
	}

	/** The colour its warnings and waves are drawn in. */
	protected int color() {
		return 0xE8A33D;
	}

	public boolean dormant() {
		return this.entityData.get(DORMANT);
	}

	public void setDormant(boolean d) {
		this.entityData.set(DORMANT, d);
		this.bar.setVisible(!d);
		if (d) {
			setAnim(A_SLEEP);
		}
	}

	public void setHome(Vec3 at) {
		this.home = at;
	}

	/** Makes this an echo of its Warden (see dev.afterworld.dungeon.Ladder), its strength scaled. */
	public void makeEcho(float strength) {
		this.echo = true;
		this.firstFall = false;
		var hp = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(hp.getBaseValue() * strength);
			setHealth(getMaxHealth());
		}
		setCustomName(Component.translatable("ladder.afterworld.echo_name", getType().getDescription()).withColor(color()));
		this.bar.setName(getDisplayName());
	}

	public boolean isEcho() {
		return this.echo;
	}

	@Override
	protected String voice() {
		return dormant() ? null : wardenVoice();
	}

	protected String wardenVoice() {
		return null;
	}

	/** Wakes from its long sleep: the stone cracks, it rises, it speaks. */
	public void wake(ServerLevel level) {
		if (!dormant()) {
			return;
		}
		setDormant(false);
		this.rising = 50;
		setAnim(A_RISE);
		Vec3 c = position();
		Fx.hostileSound(level, c, dev.afterworld.registry.AwSounds.BOSS_ROAR, 3.0F, 0.6F);
		Fx.shake(level, c, 48, 5.0F, 40);
		Fx.burst(level, ParticleTypes.CLOUD, c.add(0, 1, 0), 60, getBbWidth(), 0.08);
		Fx.burst(level, Fx.dust(color(), 2.0F), c.add(0, getBbHeight() * 0.5, 0), 80, getBbWidth(), 0.1);
		for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(this) < 64 * 64 && !pl.isSpectator())) {
			p.sendSystemMessage(Component.translatable("boss.afterworld." + id() + ".wakes").withColor(color()));
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new dev.afterworld.net.AwPayloads.Cinematic(
				dev.afterworld.net.AwPayloads.Cinematic.BOSS_PHASE, 0, getId(), color()));
		}
	}

	/** Wakes every sleeping Warden within {@code radius} of {@code at}. */
	public static void wakeNear(ServerLevel level, Vec3 at, double radius) {
		for (WardenBoss b : level.getEntitiesOfClass(WardenBoss.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius), WardenBoss::dormant)) {
			b.wake(level);
		}
	}

	protected String id() {
		return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();
	}

	// ------------------------------------------------------------------ the fight

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (this.home == Vec3.ZERO) {
			this.home = position();
		}
		if (dormant()) {
			getNavigation().stop();
			setDeltaMovement(0, getDeltaMovement().y, 0);
			if (this.tickCount % 30 == 0) {
				Fx.burst(level, Fx.dust(color(), 1.0F), position().add(0, getBbHeight() * 0.6, 0), 4, getBbWidth() * 0.5, 0.01);
			}
			return;
		}
		super.customServerAiStep(level);
		if (this.rising > 0) {
			this.rising--;
			getNavigation().stop();
			if (this.rising == 0) {
				setAnim(A_IDLE);
			}
			return;
		}
		if (inTransition()) {
			getNavigation().stop();
			this.current = null;
			return;
		}
		long now = level.getGameTime();
		// Never far from home: pulled away, it walks back and its wounds close a little.
		if (position().distanceTo(this.home) > 30) {
			this.current = null;
			getNavigation().moveTo(this.home.x, this.home.y, this.home.z, chaseSpeed() * 1.2);
			if (this.tickCount % 20 == 0) {
				heal(getMaxHealth() * 0.01F);
			}
			setAnim(A_IDLE);
			return;
		}
		ServerPlayer near = nearestPlayer(36);
		if (near != null && near.position().distanceTo(this.home) > 40) {
			near = null;
		}
		this.target = near;
		if (this.current != null) {
			tickMove(level, now);
			return;
		}
		if (this.target == null) {
			if (position().distanceTo(this.home) > 3) {
				getNavigation().moveTo(this.home.x, this.home.y, this.home.z, chaseSpeed() * 0.8);
			}
			return;
		}
		getLookControl().setLookAt(this.target, 30, 30);
		double d = distanceTo(this.target);
		Move m = pick(level, d, now);
		if (m != null) {
			this.current = m;
			this.moveTick = 0;
			this.ready.put(m.id(), now + m.cooldown());
			getNavigation().stop();
			setAnim(m.windAnim());
			windup(level, m, this.target);
			return;
		}
		if (d > preferredRange()) {
			getNavigation().moveTo(this.target, chaseSpeed());
		} else {
			getNavigation().stop();
		}
	}

	private Move pick(ServerLevel level, double d, long now) {
		List<Move> ok = new ArrayList<>();
		int total = 0;
		for (Move m : moves()) {
			if (this.phase >= m.minPhase() && d >= m.minRange() && d <= m.maxRange() && now >= this.ready.getOrDefault(m.id(), 0L)) {
				ok.add(m);
				total += m.weight();
			}
		}
		if (ok.isEmpty() || level.getRandom().nextInt(4) == 0 && this.tickCount % 5 != 0) {
			return null;
		}
		int r = level.getRandom().nextInt(total);
		for (Move m : ok) {
			r -= m.weight();
			if (r < 0) {
				return m;
			}
		}
		return null;
	}

	private void tickMove(ServerLevel level, long now) {
		Move m = this.current;
		LivingEntity t = this.target != null && this.target.isAlive() ? this.target : null;
		int k = this.moveTick++;
		if (k < m.windup()) {
			getNavigation().stop();
			if (t != null) {
				getLookControl().setLookAt(t, 30, 30);
				faceTowards(t);
			}
			return;
		}
		if (k == m.windup()) {
			setAnim(m.actAnim());
			if (t != null) {
				release(level, m, t);
			}
		} else if (k < m.windup() + m.active()) {
			if (t != null) {
				during(level, m, k - m.windup(), t);
			}
		} else if (k == m.windup() + m.active()) {
			setAnim(A_IDLE);
		} else if (k >= m.windup() + m.active() + m.recovery()) {
			this.current = null;
		}
	}

	protected void faceTowards(LivingEntity t) {
		double dx = t.getX() - getX();
		double dz = t.getZ() - getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
		setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	/** Players within a cone in front: for sweeps and breaths. */
	protected List<ServerPlayer> inCone(double range, double halfAngleDeg) {
		List<ServerPlayer> out = new ArrayList<>();
		Vec3 look = Vec3.directionFromRotation(0, getYRot());
		for (ServerPlayer p : playersNear(range + 2)) {
			Vec3 rel = p.position().subtract(position());
			double dist = Math.hypot(rel.x, rel.z);
			if (dist > range || Math.abs(p.getY() - getY()) > 4) {
				continue;
			}
			double cos = (rel.x * look.x + rel.z * look.z) / Math.max(1.0E-4, dist);
			if (dist < 1.5 || cos >= Math.cos(Math.toRadians(halfAngleDeg))) {
				out.add(p);
			}
		}
		return out;
	}

	/** A melee blow against one player: goes through parry and dodge like any other. */
	protected boolean strike(ServerLevel level, ServerPlayer p, float damage, double push, double lift) {
		boolean hit = p.hurtServer(level, damageSources().mobAttack(this), damage);
		if (hit) {
			Vec3 out = p.position().subtract(position()).multiply(1, 0, 1).normalize().scale(push);
			p.push(out.x, lift, out.z);
			p.syncVelocity = true;
		}
		return hit;
	}

	/** Moves the body to a point in a few quick steps (a dash, a charge, a roll). */
	protected void lunge(Vec3 to) {
		Vec3 d = to.subtract(position());
		setDeltaMovement(d.x * 0.35, Math.max(0.15, d.y * 0.2), d.z * 0.35);
		this.syncVelocity = true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (dormant() || this.rising > 0) {
			return false;
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		if (this.echo) {
			this.firstFall = false;
			super.die(source);
			return;
		}
		if (this.level() instanceof ServerLevel level) {
			AfterworldData data = AfterworldData.get(level.getServer());
			boolean first = (data.wardensDefeated & (1 << bit())) == 0;
			data.wardensDefeated |= 1 << bit();
			data.setDirty();
			String name = getDisplayName().getString();
			for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(this) < 64 * 64 && !pl.isSpectator())) {
				Renown.grant(p, first ? 120 : 40, Component.translatable("boss.afterworld.felled", name).getString());
				Progress.legend(p, "battle", Component.translatable("boss.afterworld.felled", name).getString());
				Progress.codex(p, "boss." + id());
				dev.afterworld.player.PlayerState.of(p).questDirty = true;
			}
			// Its heart is given up only the first time; after that it leaves only its spoils.
			this.firstFall = first;
		}
		super.die(source);
	}

	@Override
	protected boolean givesHeart() {
		return this.firstFall;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Dormant", dormant());
		output.putBoolean("Echo", this.echo);
		output.putDouble("HomeX", this.home.x);
		output.putDouble("HomeY", this.home.y);
		output.putDouble("HomeZ", this.home.z);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setDormant(input.getBooleanOr("Dormant", false));
		this.echo = input.getBooleanOr("Echo", false);
		this.firstFall = !this.echo;
		this.home = new Vec3(input.getDoubleOr("HomeX", 0), input.getDoubleOr("HomeY", 0), input.getDoubleOr("HomeZ", 0));
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		this.bar.setVisible(!dormant());
	}

	@Override
	protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		playSound(SoundEvents.RAVAGER_STEP, 0.6F, 0.7F);
	}
}

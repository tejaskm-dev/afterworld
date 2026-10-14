package dev.afterworld.entity.wild;

import dev.afterworld.entity.MagicProjectile;
import dev.afterworld.registry.AwEffects;
import dev.afterworld.util.Fx;
import dev.afterworld.util.Scheduler;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The second bestiary's shared behaviour: every ability, when it may be used, and what it does.
 * The archetype classes (beast, monster, flyer, swimmer) call in here from their own hooks.
 */
public final class Wild {
	private Wild() {
	}

	/** Per-creature state for its abilities: cooldowns, a charge or swoop in progress, a burrow. */
	public static final class Brain {
		final Map<Ability, Integer> cooldown = new EnumMap<>(Ability.class);
		int charge;
		int swoop;
		int lunge;
		int stingAt;
		int windup;
		Ability pending;
		LivingEntity pendingTarget;

		boolean ready(Ability a) {
			return this.cooldown.getOrDefault(a, 0) <= 0;
		}

		void use(Ability a, int ticks) {
			this.cooldown.put(a, ticks);
		}

		void tick() {
			this.cooldown.replaceAll((a, t) -> Math.max(0, t - 1));
		}
	}

	// ---------------------------------------------------------------- the tick

	/** Every server tick: cooldowns, moves under way, burrowing, stinging, climbing. */
	public static void tick(WildCreature c) {
		Mob m = c.self();
		if (!(m.level() instanceof ServerLevel level)) {
			return;
		}
		Brain b = c.brain();
		b.tick();
		Species sp = c.species();
		LivingEntity target = m.getTarget();
		if (sp.has(Ability.CLIMB)) {
			c.setClimbing(m.horizontalCollision);
		}
		// A wind-up finishing: the move it was winding up for.
		if (b.windup > 0 && --b.windup == 0 && b.pending != null) {
			Ability a = b.pending;
			b.pending = null;
			if (b.pendingTarget != null && b.pendingTarget.isAlive()) {
				release(level, c, a, b.pendingTarget);
			}
		}
		if (b.charge > 0) {
			b.charge--;
			chargeStep(level, c, target);
		}
		if (b.swoop > 0) {
			b.swoop--;
			swoopStep(level, c, target);
		}
		if (b.lunge > 0) {
			b.lunge--;
			if (target != null && m.getBoundingBox().inflate(0.4).intersects(target.getBoundingBox())) {
				b.lunge = 0;
				m.doHurtTarget(level, target);
			}
		}
		if (sp.has(Ability.BURROW)) {
			burrowStep(level, c);
		}
		if (sp.has(Ability.STING) && m.tickCount % 10 == 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(0.25), e -> e != m && !(e instanceof WildCreature w
				&& w.species() == sp))) {
				e.hurtServer(level, level.damageSources().mobAttack(m), 1.5F);
				e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), m);
			}
		}
		if (sp.has(Ability.GLOW) && m.tickCount % 30 == m.getId() % 30 && !c.burrowed()) {
			level.sendParticles(ParticleTypes.GLOW, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 1, 0.2, 0.2, 0.2, 0);
		}
		if (c.action() != WildCreature.IDLE && c.actionTime(0) > 20) {
			c.setAction(WildCreature.IDLE);
		}
	}

	// ---------------------------------------------------------------- abilities in a fight

	/** The goal that decides, each moment of a fight, whether to use an ability. */
	public static final class AbilityGoal extends Goal {
		private final WildCreature c;
		private final Mob m;

		public AbilityGoal(WildCreature c) {
			this.c = c;
			this.m = c.self();
			this.setFlags(EnumSet.noneOf(Goal.Flag.class));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = this.m.getTarget();
			return t != null && t.isAlive() && !this.c.burrowed() && this.c.brain().windup <= 0;
		}

		@Override
		public boolean canContinueToUse() {
			return false;
		}

		@Override
		public void start() {
			if (this.m.level() instanceof ServerLevel level) {
				choose(level, this.c, this.m.getTarget());
			}
		}
	}

	private static void choose(ServerLevel level, WildCreature c, LivingEntity t) {
		Mob m = c.self();
		Species sp = c.species();
		Brain b = c.brain();
		double d = m.distanceTo(t);
		boolean sees = m.hasLineOfSight(t);
		if (sp.has(Ability.SLAM) && b.ready(Ability.SLAM) && d < 3.5) {
			windup(c, Ability.SLAM, t, 10, 90);
		} else if (sp.has(Ability.SCREECH) && b.ready(Ability.SCREECH) && d < 9) {
			windup(c, Ability.SCREECH, t, 8, 180);
		} else if (sp.has(Ability.SPORE_CLOUD) && b.ready(Ability.SPORE_CLOUD) && d < 5) {
			windup(c, Ability.SPORE_CLOUD, t, 10, 160);
		} else if (sp.has(Ability.GRAPPLE) && b.ready(Ability.GRAPPLE) && d > 3.5 && d < 10 && sees) {
			windup(c, Ability.GRAPPLE, t, 8, 110);
		} else if (sp.has(Ability.CHARGE) && b.ready(Ability.CHARGE) && d > 5 && d < 16 && sees) {
			windup(c, Ability.CHARGE, t, 14, 140);
		} else if (sp.has(Ability.LUNGE) && b.ready(Ability.LUNGE) && d > 2.5 && d < 8 && (m.onGround() || m.isInWater())) {
			windup(c, Ability.LUNGE, t, 6, 70);
		} else if (sp.has(Ability.SWOOP) && b.ready(Ability.SWOOP) && d > 3 && d < 18) {
			windup(c, Ability.SWOOP, t, 8, 80);
		} else if (sp.has(Ability.THROW) && b.ready(Ability.THROW) && d > 4.5 && d < 20 && sees) {
			windup(c, Ability.THROW, t, 12, 70);
		} else if (spit(sp) != null && b.ready(spit(sp)) && d > 4 && d < 18 && sees) {
			windup(c, spit(sp), t, 8, 60);
		} else if (sp.has(Ability.TELEPORT) && b.ready(Ability.TELEPORT) && d > 8 && m.getRandom().nextInt(4) == 0) {
			blink(level, c, t.position(), 2.5, 4.0);
			b.use(Ability.TELEPORT, 120);
		}
	}

	private static Ability spit(Species sp) {
		return sp.has(Ability.SPIT_FIRE) ? Ability.SPIT_FIRE : sp.has(Ability.SPIT_WEB) ? Ability.SPIT_WEB : sp.has(Ability.SPIT_CHILL) ? Ability.SPIT_CHILL : null;
	}

	private static void windup(WildCreature c, Ability a, LivingEntity t, int ticks, int cooldown) {
		Brain b = c.brain();
		b.use(a, cooldown);
		b.pending = a;
		b.pendingTarget = t;
		b.windup = ticks;
		c.setAction(WildCreature.WINDUP);
		Mob m = c.self();
		m.getLookControl().setLookAt(t, 30.0F, 30.0F);
		if (a == Ability.CHARGE || a == Ability.SLAM) {
			m.getNavigation().stop();
		}
	}

	private static void release(ServerLevel level, WildCreature c, Ability a, LivingEntity t) {
		Mob m = c.self();
		Brain b = c.brain();
		c.setAction(WildCreature.SPECIAL);
		Vec3 to = t.position().subtract(m.position());
		Vec3 flat = to.multiply(1, 0, 1).normalize();
		switch (a) {
			case LUNGE -> {
				if (m.isInWater()) {
					m.setDeltaMovement(to.normalize().scale(1.1));
				} else {
					m.setDeltaMovement(flat.x * 0.95, 0.42, flat.z * 0.95);
				}
				m.syncVelocity = true;
				b.lunge = 14;
				m.playSound(SoundEvents.RAVAGER_ATTACK, 0.6F, 1.6F);
			}
			case CHARGE -> {
				b.charge = 36;
				m.playSound(SoundEvents.HOGLIN_ATTACK, 1.0F, 0.7F);
			}
			case SWOOP -> {
				b.swoop = 30;
				m.playSound(SoundEvents.PHANTOM_SWOOP, 1.0F, 1.2F);
			}
			case SLAM -> {
				Vec3 at = m.position();
				Fx.shake(level, at, 14, 2.0F, 8);
				m.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 0.7F);
				BlockPos below = m.blockPosition().below();
				Fx.ring(level, new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), at.add(0, 0.2, 0), 2.5, 24);
				Fx.ring(level, ParticleTypes.CLOUD, at.add(0, 0.2, 0), 3.5, 16);
				dev.afterworld.util.Vfx.ring(level, at, 3.6F, 0.6F, 0.7F, 0xE8DCC8, 10);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(3.5), e -> e != m && enemyOf(m, e))) {
					if (e.hurtServer(level, level.damageSources().mobAttack(m), (float) m.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.9F)) {
						Vec3 out = e.position().subtract(at).multiply(1, 0, 1).normalize().scale(0.9);
						e.push(out.x, 0.55, out.z);
						e.syncVelocity = true;
					}
				}
			}
			case SCREECH -> {
				m.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 0.8F, 1.8F);
				Fx.ring(level, ParticleTypes.SONIC_BOOM, m.position().add(0, m.getBbHeight() * 0.5, 0), 1.0, 3);
				dev.afterworld.util.Vfx.ring(level, m.position().add(0, m.getBbHeight() * 0.5, 0), new Vec3(0, 1, 0), 9.0F, 0.4F, 0x9FD8FF, 10);
				for (Player p : level.getEntitiesOfClass(Player.class, m.getBoundingBox().inflate(9), p -> !p.isCreative() && !p.isSpectator())) {
					p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), m);
					p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1), m);
				}
			}
			case SPORE_CLOUD -> spores(level, m, c.species() == Species.FUNGAL_BRUTE);
			case GRAPPLE -> {
				Vec3 from = m.getEyePosition();
				Vec3 hit = t.position().add(0, t.getBbHeight() * 0.5, 0);
				int col = c.species() == Species.FEN_TOAD ? 0xD86A7A : 0x5A8A3A;
				Fx.line(level, Fx.dust(col, 1.0F), from, hit, 0.3);
				m.playSound(SoundEvents.FROG_TONGUE, 1.0F, 0.8F);
				Vec3 pull = from.subtract(hit);
				double len = pull.length();
				Vec3 v = pull.normalize().scale(Math.min(1.6, 0.35 + len * 0.13));
				t.setDeltaMovement(v.x, 0.25 + Math.min(0.3, len * 0.02), v.z);
				t.syncVelocity = true;
				t.hurtServer(level, level.damageSources().mobAttack(m), 2.0F);
			}
			case THROW -> {
				int kind = c.species() == Species.DROWNED_REVENANT ? MagicProjectile.HARPOON : MagicProjectile.ROCK;
				ItemStack look = new ItemStack(kind == MagicProjectile.HARPOON ? Items.TRIDENT : Items.PACKED_ICE);
				shoot(level, m, t, kind, look, kind == MagicProjectile.HARPOON ? 6.0F : 7.0F, 1.2F);
				m.playSound(SoundEvents.TRIDENT_THROW.value(), 1.0F, 0.6F);
			}
			case SPIT_FIRE -> {
				shoot(level, m, t, MagicProjectile.FIRE_SPIT, new ItemStack(Items.FIRE_CHARGE), 4.0F, 1.3F);
				m.playSound(SoundEvents.BLAZE_SHOOT, 0.8F, 1.3F);
			}
			case SPIT_WEB -> {
				shoot(level, m, t, MagicProjectile.WEB, new ItemStack(Items.COBWEB), 2.0F, 1.1F);
				m.playSound(SoundEvents.SLIME_ATTACK, 1.0F, 0.6F);
			}
			case SPIT_CHILL -> {
				shoot(level, m, t, MagicProjectile.CHILL, new ItemStack(Items.SNOWBALL), 4.0F, 1.2F);
				m.playSound(SoundEvents.ALLAY_THROW, 1.0F, 0.6F);
			}
			default -> {
			}
		}
	}

	private static void shoot(ServerLevel level, Mob m, LivingEntity t, int kind, ItemStack look, float damage, float speed) {
		MagicProjectile p = new MagicProjectile(level, m, look, kind, 1, damage);
		p.setPos(m.getX(), m.getEyeY() - 0.2, m.getZ());
		Vec3 aim = t.getEyePosition().add(t.getDeltaMovement().scale(8)).subtract(p.position());
		double arc = kind == MagicProjectile.ROCK || kind == MagicProjectile.HARPOON ? aim.horizontalDistance() * 0.12 : 0;
		p.shoot(aim.x, aim.y + arc, aim.z, speed, 3.0F);
		level.addFreshEntity(p);
	}

	private static void chargeStep(ServerLevel level, WildCreature c, LivingEntity t) {
		Mob m = c.self();
		Brain b = c.brain();
		if (t == null || !t.isAlive()) {
			b.charge = 0;
			return;
		}
		Vec3 dir = t.position().subtract(m.position());
		dir = m.isInWater() ? dir.normalize() : dir.multiply(1, 0, 1).normalize();
		double v = m.isInWater() ? 0.7 : 0.48;
		m.setDeltaMovement(dir.x * v, m.isInWater() ? dir.y * v : m.getDeltaMovement().y, dir.z * v);
		m.getLookControl().setLookAt(t);
		if (m.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, m.getX(), m.getY() + 0.1, m.getZ(), 2, 0.3, 0.05, 0.3, 0.01);
		}
		if (m.getBoundingBox().inflate(0.5).intersects(t.getBoundingBox())) {
			b.charge = 0;
			float dmg = (float) m.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 1.6F;
			if (t.hurtServer(level, level.damageSources().mobAttack(m), dmg)) {
				Vec3 kb = dir.scale(1.4);
				t.push(kb.x, 0.5, kb.z);
				t.syncVelocity = true;
				m.playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.6F);
			}
		} else if (m.horizontalCollision && !m.isInWater()) {
			// Into a wall: dazed.
			b.charge = 0;
			m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 3));
			m.playSound(SoundEvents.ANVIL_LAND, 0.5F, 1.4F);
			level.sendParticles(ParticleTypes.CRIT, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 10, 0.3, 0.2, 0.3, 0.1);
		}
	}

	private static void swoopStep(ServerLevel level, WildCreature c, LivingEntity t) {
		Mob m = c.self();
		Brain b = c.brain();
		if (t == null || !t.isAlive()) {
			b.swoop = 0;
			return;
		}
		Vec3 dir = t.position().add(0, t.getBbHeight() * 0.6, 0).subtract(m.position()).normalize();
		m.setDeltaMovement(dir.scale(0.75));
		m.getLookControl().setLookAt(t);
		if (m.getBoundingBox().inflate(0.4).intersects(t.getBoundingBox())) {
			b.swoop = 0;
			m.doHurtTarget(level, t);
			// Climb away after the strike.
			m.setDeltaMovement(dir.scale(-0.4).add(0, 0.7, 0));
		}
	}

	private static void spores(ServerLevel level, Mob m, boolean dizzy) {
		Vec3 at = m.position().add(0, 0.6, 0);
		m.playSound(SoundEvents.PUFFER_FISH_BLOW_UP, 1.0F, 0.6F);
		for (int i = 0; i < 60; i += 10) {
			Scheduler.after(i, () -> {
				Fx.burst(level, Fx.dust(dizzy ? 0xC86AB8 : 0x7A9A3A, 1.6F), at, 22, 1.8, 0.0);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.2), e -> e != m && enemyOf(m, e))) {
					e.addEffect(new MobEffectInstance(MobEffects.POISON, 50, 0), m);
					if (dizzy) {
						e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0), m);
					}
				}
			});
		}
	}

	// ---------------------------------------------------------------- burrowing

	private static void burrowStep(ServerLevel level, WildCreature c) {
		Mob m = c.self();
		if (m.tickCount % 5 != 0) {
			return;
		}
		Player near = level.getNearestPlayer(m, 6.0);
		boolean prey = near != null && !near.isCreative() && !near.isSpectator();
		if (c.burrowed()) {
			if (prey) {
				// Burst out and go for it.
				c.setBurrowed(false);
				m.setTarget(near);
				BlockPos below = m.blockPosition().below();
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), m.getX(), m.getY() + 0.3, m.getZ(), 40, 0.6, 0.3,
					0.6, 0.2);
				m.playSound(SoundEvents.SAND_BREAK, 1.5F, 0.6F);
				windup(c, Ability.LUNGE, near, 4, 60);
			} else {
				m.getNavigation().stop();
			}
		} else if (m.getTarget() == null && !prey && m.onGround() && m.getRandom().nextInt(20) == 0
			&& level.getNearestPlayer(m, 14.0) == null) {
			c.setBurrowed(true);
			BlockPos below = m.blockPosition().below();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(below)), m.getX(), m.getY() + 0.2, m.getZ(), 20, 0.5, 0.2,
				0.5, 0.1);
		}
	}

	// ---------------------------------------------------------------- hooks from the archetypes

	/** When it bites: the bites that carry something. */
	public static void onBite(WildCreature c, LivingEntity target) {
		Species sp = c.species();
		Mob m = c.self();
		if (sp.has(Ability.FIRE_BITE)) {
			target.igniteForSeconds(3.0F);
		}
		if (sp.has(Ability.FREEZE_BITE)) {
			target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 60, target.getTicksFrozen() + 60));
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), m);
		}
		if (sp.has(Ability.POISON_BITE)) {
			target.addEffect(new MobEffectInstance(MobEffects.POISON, 70, 0), m);
		}
		c.setAction(WildCreature.ATTACK);
	}

	/** Before it takes a hit. Returns false to refuse the hit (reflected, blinked away). */
	public static boolean onHurt(ServerLevel level, WildCreature c, DamageSource source) {
		Mob m = c.self();
		Species sp = c.species();
		Brain b = c.brain();
		if (c.burrowed()) {
			c.setBurrowed(false);
		}
		if (sp.has(Ability.REFLECT) && source.getDirectEntity() instanceof Projectile pr && m.getRandom().nextFloat() < 0.6F) {
			pr.setDeltaMovement(pr.getDeltaMovement().scale(-1.1));
			pr.setOwner(m);
			level.sendParticles(Fx.dust(0xE0C8FF, 1.2F), pr.getX(), pr.getY(), pr.getZ(), 10, 0.2, 0.2, 0.2, 0);
			m.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.2F, 1.4F);
			return false;
		}
		if (sp.has(Ability.TELEPORT) && b.ready(Ability.TELEPORT) && m.getRandom().nextFloat() < 0.35F) {
			Entity from = source.getEntity();
			b.use(Ability.TELEPORT, 80);
			Vec3 center = from != null && sp.temper != Species.Temper.PASSIVE ? from.position() : m.position();
			blink(level, c, center, 3.0, 7.0);
		}
		return true;
	}

	public static void onDeath(ServerLevel level, WildCreature c) {
		Mob m = c.self();
		if (c.species().has(Ability.EXPLODE_ON_DEATH)) {
			Vec3 at = m.position().add(0, 0.3, 0);
			Fx.burst(level, ParticleTypes.FLAME, at, 30, 0.6, 0.15);
			Fx.burst(level, ParticleTypes.LAVA, at, 6, 0.3, 0.1);
			m.playSound(SoundEvents.FIRECHARGE_USE, 1.2F, 0.7F);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3), e -> e != m && enemyOf(m, e))) {
				e.hurtServer(level, level.damageSources().explosion(m, m), 4.0F);
				e.igniteForSeconds(2.0F);
			}
		}
	}

	/** When it finds prey: a pack hunter calls the others. */
	public static void onTarget(WildCreature c, LivingEntity target) {
		Mob m = c.self();
		if (target == null || !c.species().has(Ability.PACK) || !(m.level() instanceof ServerLevel level)) {
			return;
		}
		for (Mob other : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(16),
			o -> o != m && o instanceof WildCreature w && w.species() == c.species() && o.getTarget() == null)) {
			other.setTarget(target);
		}
	}

	/** A short blink to somewhere solid near {@code center}, with a puff where it left and arrived. */
	public static void blink(ServerLevel level, WildCreature c, Vec3 center, double min, double max) {
		Mob m = c.self();
		for (int i = 0; i < 12; i++) {
			double a = m.getRandom().nextDouble() * Math.PI * 2;
			double r = min + m.getRandom().nextDouble() * (max - min);
			Vec3 to = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
			BlockPos pos = BlockPos.containing(to);
			for (int dy = 3; dy >= -3; dy--) {
				BlockPos p = pos.above(dy);
				if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
					&& level.noCollision(m, m.getBoundingBox().move(Vec3.atBottomCenterOf(p).subtract(m.position())))) {
					Vec3 from = m.position();
					int col = c.species() == Species.KITSUNE ? 0xFF9AC8 : 0x9AB8E8;
					level.sendParticles(Fx.dust(col, 1.4F), from.x, from.y + 0.6, from.z, 24, 0.3, 0.5, 0.3, 0.02);
					m.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
					level.sendParticles(Fx.dust(col, 1.4F), m.getX(), m.getY() + 0.6, m.getZ(), 24, 0.3, 0.5, 0.3, 0.02);
					m.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.6F);
					return;
				}
			}
		}
	}

	/** Who this creature's area attacks may hurt: players, and whatever it is fighting. */
	static boolean enemyOf(Mob m, LivingEntity e) {
		if (e instanceof WildCreature w && w.species() == ((WildCreature) m).species()) {
			return false;
		}
		return e instanceof Player p ? !p.isCreative() && !p.isSpectator() : e == m.getTarget();
	}

	/** The usual goals for a creature of this temper (abilities, fighting, wandering). */
	public static void standardTargets(WildCreature c, net.minecraft.world.entity.ai.goal.GoalSelector targets) {
		Mob m = c.self();
		if (!(m instanceof PathfinderMob pm)) {
			return;
		}
		switch (c.species().temper) {
			case HOSTILE -> {
				targets.addGoal(1, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(pm));
				targets.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(pm, Player.class, true));
			}
			case NEUTRAL -> targets.addGoal(1, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(pm));
			default -> {
			}
		}
	}

	/** Effects carried by the stopped-time effect apply to these as to anything. */
	public static boolean stopped(Mob m) {
		return m.hasEffect(AwEffects.STOPPED);
	}

	static List<Species> all() {
		return List.of(Species.VALUES);
	}
}

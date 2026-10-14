package dev.afterworld.entity.boss;

import dev.afterworld.registry.MobMark;
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Shared machinery for Afterworld's great actors: a boss bar, a synced animation state, and the
 * guarantee that they never despawn on their own. Their persistence is owned by world systems.
 */
public abstract class BossMob extends PathfinderMob {
	private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(BossMob.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ANIM_START = SynchedEntityData.defineId(BossMob.class, EntityDataSerializers.INT);
	protected final ServerBossEvent bar;
	/** 1, 2, 3...: the fight's acts. Crossing a threshold of health starts the next one. */
	protected int phase = 1;
	/** Ticks left in a phase change; the actor is untouchable and does not attack meanwhile. */
	protected int transition;
	private boolean scaledForParty;
	private int lonely;
	/** Whether this actor has spoken its opening words to the current challengers. */
	private boolean introduced;

	protected BossMob(EntityType<? extends PathfinderMob> type, Level level, BossEvent.BossBarColor color) {
		super(type, level);
		this.bar = new ServerBossEvent(Mth.createInsecureUUID(this.random), this.getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
		this.bar.setPlayBossMusic(true);
		this.setPersistenceRequired();
		this.xpReward = 120;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ANIM, 0);
		builder.define(ANIM_START, 0);
	}

	public int anim() {
		return this.entityData.get(ANIM);
	}

	/** Ticks since the current animation began (client and server agree within a tick). */
	public float animTime(float partial) {
		return this.tickCount + partial - this.entityData.get(ANIM_START);
	}

	protected void setAnim(int anim) {
		this.entityData.set(ANIM, anim);
		this.entityData.set(ANIM_START, this.tickCount);
	}

	/** Health fractions at which the next act begins. */
	protected float[] phaseAt() {
		return new float[] {0.66F, 0.33F};
	}

	/** Extra spectacle when a new act begins (the base already roars, shakes and announces it). */
	protected void onPhase(ServerLevel level, int phase) {
	}

	/** Back to the start when everyone has left: full health, first act. */
	protected void onReset(ServerLevel level) {
	}

	public int phase() {
		return this.phase;
	}

	/** The voice this actor speaks with in barks.json, or null for a silent one. */
	protected String voice() {
		return null;
	}

	/** The Aspect this power carries in its heart, given up when it falls (null: none). */
	public dev.afterworld.aspect.Aspect aspect() {
		return null;
	}

	/** Whether this fall gives up the heart (a power that has fallen before keeps it). */
	protected boolean givesHeart() {
		return true;
	}

	/** Lays the heart of this power where it fell, glowing and safe from fire and void. */
	public static void dropHeart(ServerLevel level, net.minecraft.world.entity.Entity at, dev.afterworld.aspect.Aspect a) {
		if (a == null) {
			return;
		}
		var item = new net.minecraft.world.entity.item.ItemEntity(level, at.getX(), at.getY() + 1.5, at.getZ(),
			new net.minecraft.world.item.ItemStack(dev.afterworld.registry.AwGear.HEARTS.get(a)));
		item.setDefaultPickUpDelay();
		item.setGlowingTag(true);
		item.setUnlimitedLifetime();
		level.addFreshEntity(item);
		dev.afterworld.util.Fx.column(level, dev.afterworld.util.Fx.dust(a.color, 1.6F), at.position(), 60, 0.5, 6.0);
		dev.afterworld.util.Fx.sound(level, at.position(), dev.afterworld.registry.AwSounds.UI_AWAKEN, 2.0F, 0.8F);
	}

	public boolean inTransition() {
		return this.transition > 0;
	}

	private String bossId() {
		return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(this.getType()).getPath();
	}

	private void beginPhase(ServerLevel level) {
		this.transition = 50;
		this.getNavigation().stop();
		var pos = this.position();
		dev.afterworld.util.Fx.hostileSound(level, pos, dev.afterworld.registry.AwSounds.BOSS_ROAR, 3.0F, 0.75F);
		dev.afterworld.util.Fx.shake(level, pos, 48, 4.0F, 30);
		dev.afterworld.util.Fx.burst(level, net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, pos.add(0, this.getBbHeight() * 0.6, 0), 3, 0.8, 0.0);
		Hazards.wave(level, this, pos, 14, 0.7, 3.0F, 0xE8A33D);
		this.removeAllEffects();
		net.minecraft.network.chat.Component sub = net.minecraft.network.chat.Component.translatable("boss.afterworld." + bossId() + ".phase" + this.phase);
		for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(this) < 48 * 48 && !pl.isSpectator())) {
			dev.afterworld.player.Titles.show(p, dev.afterworld.net.AwPayloads.Title.BOSS, this.getDisplayName(), sub, 0xE8A33D,
				"boss:" + this.getUUID() + ":phase" + this.phase, 20 * 60);
		}
		this.bar.setName(this.getDisplayName().copy().append(net.minecraft.network.chat.Component.literal("  ·  ")).append(sub));
		for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(this) < 48 * 48 && !pl.isSpectator())) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new dev.afterworld.net.AwPayloads.Cinematic(
				dev.afterworld.net.AwPayloads.Cinematic.BOSS_PHASE, Math.min(3, this.phase - 1), this.getId(), 0xE8A33D));
		}
		if (voice() != null && dev.afterworld.npc.Barks.has(voice(), "phase" + this.phase)) {
			dev.afterworld.npc.Barks.say(level, this, voice(), "phase" + this.phase, 64);
		}
		onPhase(level, this.phase);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		// A party makes for a tougher fight: more health for each extra challenger at the start.
		if (!this.scaledForParty && this.getHealth() < this.getMaxHealth()) {
			this.scaledForParty = true;
			int party = playersNear(40).size();
			if (party > 1) {
				var attr = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
				float frac = this.getHealth() / this.getMaxHealth();
				attr.setBaseValue(attr.getBaseValue() * (1 + 0.5 * (party - 1)));
				this.setHealth((float) attr.getValue() * frac);
			}
		}
		float[] at = phaseAt();
		float frac = this.getHealth() / this.getMaxHealth();
		if (this.phase - 1 < at.length && frac <= at[this.phase - 1] && this.isAlive()) {
			this.phase++;
			beginPhase(level);
		}
		if (this.transition > 0) {
			this.transition--;
		}
		// Nobody left to fight: heal back and start over.
		if (this.tickCount % 20 == 0) {
			if (playersNear(56).isEmpty() && (this.phase > 1 || this.getHealth() < this.getMaxHealth())) {
				if (++this.lonely >= 15) {
					this.lonely = 0;
					this.phase = 1;
					this.scaledForParty = false;
					this.setHealth(this.getMaxHealth());
					this.bar.setName(this.getDisplayName());
					this.introduced = false;
					onReset(level);
				}
			} else {
				this.lonely = 0;
			}
		}
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		// The opening words, the first time challengers come close.
		if (!this.introduced && this.tickCount % 10 == 3 && voice() != null && !playersNear(28).isEmpty()) {
			this.introduced = true;
			dev.afterworld.npc.Barks.say(level, this, voice(), "intro", 64);
		}
		// A title card for anyone who comes close, once every few minutes.
		if (this.tickCount % 40 == 7) {
			for (ServerPlayer p : level.getPlayers(pl -> pl.distanceToSqr(this) < 40 * 40 && !pl.isSpectator())) {
				dev.afterworld.player.Titles.show(p, dev.afterworld.net.AwPayloads.Title.BOSS, this.getDisplayName(),
					net.minecraft.network.chat.Component.translatable("boss.afterworld." + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(this.getType()).getPath() + ".epithet"),
					0xE8A33D, "boss:" + this.getUUID(), 20 * 60 * 5);
			}
		}
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		this.bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bar.removePlayer(player);
	}

	/**
	 * A great power does not cry out at every blow: the hit is answered by the sound of what it is
	 * made of (see combat.Impacts), and its voice speaks at the turns of the fight instead.
	 */
	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
		return null;
	}

	/** It falls with a roar under its last words. */
	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return dev.afterworld.registry.AwSounds.BOSS_ROAR.value();
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level) {
			if (voice() != null) {
				dev.afterworld.npc.Barks.say(level, this, voice(), "defeat", 64);
			}
			if (givesHeart()) {
				dropHeart(level, this, aspect());
			}
		}
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		this.bar.removeAllPlayers();
		super.remove(reason);
	}

	protected List<ServerPlayer> playersNear(double radius) {
		return ((ServerLevel) this.level()).getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(radius),
			p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
	}

	protected ServerPlayer nearestPlayer(double radius) {
		ServerPlayer best = null;
		double bestD = radius * radius;
		for (ServerPlayer p : playersNear(radius)) {
			double d = p.distanceToSqr(this);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
		// Great actors cannot be stopped in time, and shrug off the worst crowd control.
		return !effect.is(dev.afterworld.registry.AwEffects.STOPPED) && !effect.is(net.minecraft.world.effect.MobEffects.LEVITATION) && super.canBeAffected(effect);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.transition > 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (this.tickCount % 4 == 0) {
				this.playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.6F, 1.8F);
			}
			return false;
		}
		// No single blow takes more than a tenth of a great actor's health.
		if (!source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			amount = Math.min(amount, this.getMaxHealth() * 0.1F);
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected boolean canRide(net.minecraft.world.entity.Entity vehicle) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Phase", this.phase);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.phase = input.getIntOr("Phase", 1);
	}

	protected void markBoss() {
		MobMark.set(this, MobMark.of(this).with(MobMark.BOSS));
	}

	protected static boolean isTarget(Player p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative();
	}
}

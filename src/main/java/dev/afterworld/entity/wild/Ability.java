package dev.afterworld.entity.wild;

/** What a creature can do beyond walking up and biting. See {@link Wild} for how each works. */
public enum Ability {
	/** A short wind-up, then a leap at its prey. */
	LUNGE,
	/** Lowers its head and runs at you; hits like a cart. */
	CHARGE,
	SPIT_FIRE,
	SPIT_WEB,
	SPIT_CHILL,
	/** Hurls something heavy in an arc (a harpoon, a chunk of ice). */
	THROW,
	/** Lies hidden in the ground until something walks close, then bursts out. */
	BURROW,
	FIRE_BITE,
	FREEZE_BITE,
	POISON_BITE,
	/** Breathes out a cloud of spores around itself. */
	SPORE_CLOUD,
	/** Blinks away when hurt, or behind its prey. */
	TELEPORT,
	/** A cry that blinds and staggers. */
	SCREECH,
	/** A tongue or vine that drags you in. */
	GRAPPLE,
	/** Turns arrows and spells back at whoever loosed them. */
	REFLECT,
	/** Pounds the ground: everything close is thrown back. */
	SLAM,
	/** Dives out of the sky. */
	SWOOP,
	/** Calls the rest of the pack when it finds prey. */
	PACK,
	/** Bursts into embers when it dies. */
	EXPLODE_ON_DEATH,
	/** Hurts whatever touches it. */
	STING,
	/** Drawn bright even in darkness. */
	GLOW,
	/** Walks up walls. */
	CLIMB,
	/** Rooted to its spot. */
	STATIONARY
}

package dev.afterworld.entity.wild;

import net.minecraft.world.entity.Mob;

/** What every creature of the second bestiary shares, whatever it is built on. */
public interface WildCreature {
	/** Animation actions, synced for the model. */
	int IDLE = 0;
	int ATTACK = 1;
	int WINDUP = 2;
	int SPECIAL = 3;

	Species species();

	Mob self();

	int action();

	/** Ticks since the current action began (client and server agree within a tick). */
	float actionTime(float partial);

	void setAction(int action);

	boolean burrowed();

	void setBurrowed(boolean burrowed);

	boolean climbing();

	void setClimbing(boolean climbing);

	Wild.Brain brain();
}

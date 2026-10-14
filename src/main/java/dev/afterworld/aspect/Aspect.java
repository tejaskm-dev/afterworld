package dev.afterworld.aspect;

/** The Aspects: ultimate powers taken from the old powers, one equipped at a time. */
public enum Aspect {
	REQUIEM_TOLL("requiem_toll", "heart_of_the_bell", 0x7AFFD8),
	STOPPED_HOUR("stopped_hour", "heart_of_seconds", 0xF2D25C),
	FOOTSTEPS_OF_STONE("footsteps_of_stone", "heart_of_stone", 0xD8C08A),
	HUNDRED_LANTERNS("hundred_lanterns", "heart_of_lanterns", 0x7AFFD8),
	RED_DOMINION("red_dominion", "heart_of_crimson", 0xD02040),
	WHITE_WINTER("white_winter", "heart_of_winter", 0x8AE0FF),
	CHAINS_OF_THE_DUNE("chains_of_the_dune", "heart_of_the_dune", 0xE8B33D),
	GLYPH_OF_JUDGMENT("glyph_of_judgment", "heart_of_judgment", 0xFFD060);

	public static final Aspect[] VALUES = values();
	public final String id;
	public final String heart;
	public final int color;

	Aspect(String id, String heart, int color) {
		this.id = id;
		this.heart = heart;
		this.color = color;
	}

	public static Aspect byOrdinal(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : null;
	}

	public static Aspect byId(String id) {
		for (Aspect a : VALUES) {
			if (a.id.equals(id)) {
				return a;
			}
		}
		return null;
	}
}

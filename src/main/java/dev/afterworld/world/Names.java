package dev.afterworld.world;

import net.minecraft.util.RandomSource;

/** Deterministic names for places and people, so the same world always tells the same story. */
public final class Names {
	private static final String[] PLACE_A = {"Ash", "Hollow", "Morrow", "Thorn", "Wren", "Gallow", "Cinder", "Lantern", "Bram", "Fallow",
		"Mire", "Elder", "Candle", "Rook", "Sorrow", "Pale", "Salt", "Brack", "Duskel", "Weir", "Hearth", "Lark", "Murk", "Quill"};
	private static final String[] PLACE_B = {"hold", "mere", "ford", "wick", "stead", "fell", "barrow", "wold", "gate", "moor", "hythe",
		"combe", "reach", "end", "stone", "haven", "brook", "march", "ley", "rest"};
	private static final String[] FIRST = {"Maren", "Aldous", "Petra", "Edda", "Corvin", "Ilsa", "Tobias", "Wynne", "Hobb", "Sabine",
		"Osric", "Agnes", "Lio", "Tamsin", "Bartholomew", "Nell", "Ewan", "Greer", "Ottilie", "Fenwick", "Rosalind", "Ambrose", "Juniper", "Silas"};
	private static final String[] LAST = {"Holt", "Marlowe", "Thistle", "Crane", "Ashby", "Penhallow", "Gage", "Winter", "Fairweather",
		"Coombe", "Lockridge", "Quarrel", "Vane", "Mossop", "Ledger", "Hartwell", "Bellamy", "Underhill", "Greaves", "Pike"};
	/** Halves of the Bell-Ringer's forgotten name. */
	public static final String[] BELL_A = {"Hal", "Os", "Wen", "Ced", "Mor", "Bel", "Ald", "Eth"};
	public static final String[] BELL_B = {"vane", "ric", "dris", "mund", "wald", "laith", "gar", "wyn"};

	private Names() {
	}

	public static String place(RandomSource r) {
		return PLACE_A[r.nextInt(PLACE_A.length)] + PLACE_B[r.nextInt(PLACE_B.length)];
	}

	public static String person(RandomSource r) {
		return FIRST[r.nextInt(FIRST.length)] + " " + LAST[r.nextInt(LAST.length)];
	}

	public static String first(RandomSource r) {
		return FIRST[r.nextInt(FIRST.length)];
	}

	public static String bellName(int a, int b) {
		if (a < 0 || b < 0) {
			return "";
		}
		return BELL_A[a % BELL_A.length] + BELL_B[b % BELL_B.length];
	}

	public static String direction(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(-dx, dz));
		angle = (angle + 360) % 360;
		String[] dirs = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
		return dirs[(int) Math.round(angle / 45.0) % 8];
	}

	public static String distance(double blocks) {
		if (blocks < 120) return "a short walk";
		if (blocks < 300) return "a few hundred paces";
		if (blocks < 600) return "half a morning's walk";
		if (blocks < 1000) return "a long morning's walk";
		if (blocks < 1800) return "a day's walk";
		return "many days' walk";
	}
}

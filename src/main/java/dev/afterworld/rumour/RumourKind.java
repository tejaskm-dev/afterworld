package dev.afterworld.rumour;

public enum RumourKind {
	ANOMALY("anomaly"),
	THREAT("threat"),
	CACHE("cache"),
	PILGRIM("pilgrim"),
	CATHEDRAL("cathedral"),
	VAULT("vault"),
	UNREMEMBERED("unremembered"),
	CARAVAN("caravan"),
	ROAD("road"),
	CONTRACT("contract");

	public static final RumourKind[] VALUES = values();
	public final String id;

	RumourKind(String id) {
		this.id = id;
	}

	public static RumourKind byOrdinal(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : ANOMALY;
	}
}

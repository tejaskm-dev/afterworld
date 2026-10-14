package dev.afterworld.site;

public enum SiteKind {
	SHRINE("shrine", 6),
	ANOMALY("anomaly", 10),
	LAIR("lair", 10),
	CACHE("cache", 6),
	UNREMEMBERED("unremembered", 24),
	CATHEDRAL("cathedral", 22),
	VAULT("vault", 16),
	CARAVAN("caravan", 10),
	ROADSTOP("roadstop", 6),
	BASTION("bastion", 24),
	OBSERVATORY("observatory", 16),
	MAUSOLEUM("mausoleum", 22),
	CONTAINMENT("containment", 22),
	ARCHIVE("archive", 18);

	/** The grand structures: one per faction, plus the Archive. */
	public boolean grand() {
		return this == BASTION || this == OBSERVATORY || this == MAUSOLEUM || this == CONTAINMENT || this == ARCHIVE;
	}

	public static final SiteKind[] VALUES = values();
	public final String id;
	/** Rough radius of the site footprint, used for placement spacing and protection checks. */
	public final int radius;

	SiteKind(String id, int radius) {
		this.id = id;
		this.radius = radius;
	}

	public static SiteKind byId(String id) {
		for (SiteKind k : VALUES) {
			if (k.id.equals(id)) {
				return k;
			}
		}
		return ANOMALY;
	}
}

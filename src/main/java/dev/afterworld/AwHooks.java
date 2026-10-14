package dev.afterworld;

/** Bridges from common code to client-only code. Set by the client initializer; no-ops on servers. */
public final class AwHooks {
	public static Runnable openJournal = () -> {
	};

	private AwHooks() {
	}
}

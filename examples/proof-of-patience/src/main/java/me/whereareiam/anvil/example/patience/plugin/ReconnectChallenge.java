package me.whereareiam.anvil.example.patience.plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts reconnects until the player has presented sufficient evidence of having nothing better to
 * do.
 */
final class ReconnectChallenge {
	static final int NOT_STARTED = 0;
	private static final int REQUIRED_RECONNECTS = 3;
	private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

	void start(String username) {
		attempts.put(username, new AtomicInteger());
	}

	int reconnect(String username) {
		AtomicInteger attempt = attempts.get(username);
		return attempt == null ? NOT_STARTED : attempt.incrementAndGet();
	}

	boolean provenBy(int attempt) {
		return attempt >= REQUIRED_RECONNECTS;
	}
}

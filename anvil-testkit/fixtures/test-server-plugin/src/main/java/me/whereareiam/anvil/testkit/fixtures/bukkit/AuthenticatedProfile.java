package me.whereareiam.anvil.testkit.fixtures.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

/**
 * Applies the deterministic identity used by Anvil's authentication lifecycle system test.
 */
final class AuthenticatedProfile {
	static final UUID AUTHENTICATED_UUID = UUID.fromString("00000000-0000-4000-8000-000000000042");

	private AuthenticatedProfile() {
		throw new AssertionError("No instances");
	}

	static void apply(AsyncPlayerPreLoginEvent event) {
		event.setPlayerProfile(Bukkit.createProfileExact(AUTHENTICATED_UUID, event.getName()));
	}
}

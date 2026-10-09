package me.whereareiam.anvil.capability.server;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerProviderTest {
	@Test
	void describesAServerCapabilityWithoutDependencies() {
		ServerProvider provider = new ServerProvider();

		assertEquals(ServerProvider.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Server.class, provider.capability());
	}

	@Test
	void reportsThePlayersObservedIdentity() {
		PlayerIdentity identity = PlayerIdentity.builder()
				.username("Alice")
				.clientUniqueId(UUID.nameUUIDFromBytes("Alice".getBytes()))
				.build();

		Server server = new ServerProvider().create(new ObservationContext(new FixedObservation(identity)));

		assertSame(identity, server.identity());
	}

	/**
	 * Observes one identity that never changes.
	 */
	private record FixedObservation(PlayerIdentity identity) implements PlayerObservation {
		@Override
		public @NotNull PlayerIdentity await(@NotNull Predicate<PlayerIdentity> condition, @NotNull Duration timeout) {
			if (!condition.test(identity)) throw new AssertionError("The identity does not change");
			return identity;
		}
	}

	/**
	 * Supplies only the observation; the server capability needs no other player input.
	 */
	private record ObservationContext(PlayerObservation observation) implements PlayerCapabilityContext {
		@Override
		public @NotNull String playerName() {
			return "Alice";
		}

		@Override
		public @NotNull String clientVersion() {
			return "1.21.11";
		}

		@Override
		public void onDestroy(@NotNull Runnable action) {
			throw new AssertionError("The server capability registers no cleanup");
		}

		@Override
		public @NotNull <T extends PlayerCapability> Optional<T> findCapability(@NotNull Class<T> type) {
			return Optional.empty();
		}
	}
}

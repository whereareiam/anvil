package me.whereareiam.anvil.capability.server;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerRoute;
import me.whereareiam.anvil.api.player.PlayerObservation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObservedServerTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	@Test
	void reportsTheCurrentIdentityWithoutWaiting() {
		PlayerIdentity lobby = identity("lobby");
		ObservedServer server = new ObservedServer(new RecordedObservation(List.of(lobby)));

		assertSame(lobby, server.identity());
	}

	@Test
	void waitsForTheIdentityObservedOnTheNamedServer() {
		PlayerIdentity lobby = identity("lobby");
		PlayerIdentity survival = identity("survival");
		RecordedObservation observation = new RecordedObservation(List.of(lobby, survival));

		PlayerIdentity joined = new ObservedServer(observation).joined("survival", TIMEOUT);

		assertSame(survival, joined);
		assertEquals(TIMEOUT, observation.timeout);
	}

	@Test
	void acceptsAPlayerThatStaysAndNamesWhereALeavingPlayerWent() {
		PlayerIdentity lobby = identity("lobby");
		new ObservedServer(new RecordedObservation(List.of(lobby))).stayed("lobby", TIMEOUT);

		IllegalStateException moved = assertThrows(IllegalStateException.class,
				() -> new ObservedServer(new RecordedObservation(List.of(lobby, identity("survival")))).stayed("lobby", TIMEOUT));
		assertEquals("Player did not stay on server 'lobby'; observed on 'survival'", moved.getMessage());

		IllegalStateException gone = assertThrows(IllegalStateException.class,
				() -> new ObservedServer(new RecordedObservation(List.of(lobby, identity(null)))).stayed("lobby", TIMEOUT));
		assertEquals("Player did not stay on server 'lobby'; observed on no server", gone.getMessage());
	}

	private static PlayerIdentity identity(String server) {
		return PlayerIdentity.builder()
				.username("Alice")
				.clientUniqueId(UUID.nameUUIDFromBytes("Alice".getBytes()))
				.route(PlayerRoute.builder().server(server).build())
				.build();
	}

	/**
	 * Replays identities in observation order and answers a wait with the first one that matches.
	 */
	private static final class RecordedObservation implements PlayerObservation {
		private final List<PlayerIdentity> identities;
		private Duration timeout;

		private RecordedObservation(List<PlayerIdentity> identities) {
			this.identities = identities;
		}

		@Override
		public @NotNull PlayerIdentity identity() {
			return identities.getFirst();
		}

		@Override
		public @NotNull PlayerIdentity await(@NotNull Predicate<PlayerIdentity> condition, @NotNull Duration timeout) {
			this.timeout = timeout;
			return identities.stream().filter(condition).findFirst()
					.orElseThrow(() -> new IllegalStateException("Player did not satisfy the observation condition"));
		}
	}
}

package me.whereareiam.anvil.agent.client;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.exception.AgentUnavailableException;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class AgentPlayerObservationTest {
	private final PlayerIdentity initial = PlayerIdentity.builder()
			.username("Alice")
			.clientUniqueId(UUID.randomUUID())
			.build();

	@Test
	void observesOnlyConnectedProcessesAndFollowsReplacementConnections() {
		var directory = new ScenarioAgentDirectory();
		ProcessAgentClient lobby = directory.register("lobby");
		directory.register("unstarted");
		var observation = new AgentPlayerObservation("Alice", "lobby", () -> initial, directory, Set.of("lobby"));
		assertEquals(initial, observation.identity());

		AgentIdentity first = identity(UUID.randomUUID());
		lobby.attach(new TestAgent(() -> Optional.of(first)));
		assertEquals(first.getUniqueId(), observation.identity().getObservedUniqueId());
		assertEquals("lobby", observation.identity().getRoute().getServer());
		lobby.close();
		assertEquals(initial, observation.identity());

		AgentIdentity replacement = identity(UUID.randomUUID());
		lobby.attach(new TestAgent(() -> Optional.of(replacement)));
		assertEquals(replacement.getUniqueId(), observation.identity().getObservedUniqueId());
		lobby.close();
	}

	@Test
	void followsThePlayersOwnProxyToItsBackendAndIgnoresTheSameUsernameElsewhere() {
		UUID forwarded = UUID.randomUUID();
		Map<String, AgentClient> agents = Map.of(
				"proxy-a", new TestAgent(() -> Optional.of(onProxy("auth", UUID.randomUUID()))),
				"proxy-b", new TestAgent(() -> Optional.of(onProxy("lobby", UUID.randomUUID()))),
				"auth", new TestAgent(() -> Optional.of(identity(forwarded))),
				"lobby", new TestAgent(() -> Optional.of(identity(UUID.randomUUID()))));

		PlayerIdentity observed = new AgentPlayerObservation("alice-a", "proxy-a", () -> initial, () -> agents,
				Set.of("auth", "lobby")).identity();

		assertEquals("proxy-a", observed.getRoute().getProxy());
		assertEquals("auth", observed.getRoute().getServer());
		assertEquals(forwarded, observed.getObservedUniqueId());
	}

	@Test
	void reportsTheProxysObservationWhileNoBackendIsConnected() {
		UUID proxied = UUID.randomUUID();
		Map<String, AgentClient> agents = Map.of(
				"proxy", new TestAgent(() -> Optional.of(onProxy(null, proxied))),
				"lobby", new TestAgent(() -> {
					throw new AssertionError("A backend the proxy did not name was queried");
				}));

		PlayerIdentity observed = new AgentPlayerObservation("Alice", "proxy", () -> initial, () -> agents, Set.of("lobby")).identity();

		assertEquals("proxy", observed.getRoute().getProxy());
		assertNull(observed.getRoute().getServer());
		assertEquals(proxied, observed.getObservedUniqueId());
	}

	@Test
	void doesNotQueryAnUnavailableAgent() {
		var agent = new TestAgent(() -> {
			throw new AssertionError("Unavailable agent was queried");
		});
		agent.close();

		assertEquals(initial, observation(agent).identity());
	}

	@Test
	void acceptsAnEmptyObservationFromAConnectedAgent() {
		assertEquals(initial, observation(new TestAgent(Optional::empty)).identity());
	}

	@Test
	void toleratesDisconnectionBetweenAvailabilityCheckAndIdentityRequest() {
		var client = new ProcessAgentClient();
		client.attach(new TestAgent(Optional::empty) {
			@Override
			public boolean available() {
				client.close();
				return true;
			}
		});

		assertEquals(initial, observation(client).identity());
		assertFalse(client.available());
		assertThrows(AgentUnavailableException.class, () -> client.identity("Alice"));
	}

	@Test
	void propagatesCommunicationAndRemoteOperationFailures() {
		var failure = new AgentException("Agent returned an invalid response");
		var agent = new TestAgent(() -> {
			throw failure;
		});

		assertSame(failure, assertThrows(AgentException.class, () -> observation(agent).identity()));
	}

	private AgentPlayerObservation observation(AgentClient agent) {
		return new AgentPlayerObservation("Alice", "lobby", () -> initial, () -> Map.of("lobby", agent), Set.of("lobby"));
	}

	private AgentIdentity onProxy(String connectedServer, UUID uniqueId) {
		return AgentIdentity.builder()
				.username("Alice")
				.uniqueId(uniqueId)
				.location(ProxyLocation.builder().proxy("velocity").connectedServer(connectedServer).build())
				.build();
	}

	private AgentIdentity identity(UUID uniqueId) {
		return AgentIdentity.builder()
				.username("Alice")
				.uniqueId(uniqueId)
				.location(ServerLocation.builder().server("native-server").build())
				.build();
	}

	@RequiredArgsConstructor
	private static class TestAgent implements AgentClient {
		private final Supplier<Optional<AgentIdentity>> observation;
		private boolean closed;

		@Override
		public boolean available() {
			return !closed;
		}

		@Override
		public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
			if (closed) throw new AgentUnavailableException("Test connection is closed");

			return observation.get();
		}

		@Override
		public <T> @Nullable T request(@NotNull String operation, @Nullable Object arguments, @NotNull Class<T> responseType) {
			throw new AssertionError("Observation must use the typed identity operation");
		}

		@Override
		public boolean executeCommand(@NotNull String command) {
			throw new AssertionError("Observation must not execute commands");
		}

		@Override
		public void close() {
			closed = true;
		}
	}
}

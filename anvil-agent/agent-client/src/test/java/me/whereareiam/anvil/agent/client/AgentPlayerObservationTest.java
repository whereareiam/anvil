package me.whereareiam.anvil.agent.client;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.exception.AgentUnavailableException;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class AgentPlayerObservationTest {
	private final PlayerProfile alice = new PlayerProfile("Alice", UUID.randomUUID());
	private final PlayerIdentity initial = PlayerIdentity.builder().requested(alice).granted(alice).build();

	@Test
	void findsAPlayerTheProxyRenamedByTheUniqueIdItGranted() {
		PlayerProfile requested = new PlayerProfile("Steve", UUID.randomUUID());
		PlayerProfile granted = new PlayerProfile("Steve_Premium", UUID.randomUUID());
		Map<String, AgentClient> agents = Map.of(
				"proxy", new TestAgent(Map.of(granted.getUniqueId(), onProxy("lobby", granted))),
				"lobby", new TestAgent(Map.of(granted.getUniqueId(), onServer(granted))));
		PlayerIdentity client = PlayerIdentity.builder().requested(requested).granted(granted).build();

		PlayerIdentity observed = new AgentPlayerObservation("steve", "proxy", () -> client, () -> agents, Set.of("lobby"))
				.identity();

		assertEquals(requested, observed.getRequested());
		assertEquals(granted, observed.getObserved());
		assertEquals("lobby", observed.getRoute().getServer());
	}

	@Test
	void observesPlayersWhoseNamesDifferOnlyInLetterCaseAsThemselves() {
		PlayerProfile lower = new PlayerProfile("steve", UUID.randomUUID());
		PlayerProfile upper = new PlayerProfile("Steve", UUID.randomUUID());
		Map<String, AgentClient> agents = Map.of("lobby", new TestAgent(Map.of(
				lower.getUniqueId(), onServer(lower),
				upper.getUniqueId(), onServer(upper))));

		for (PlayerProfile profile : List.of(lower, upper)) {
			PlayerIdentity client = PlayerIdentity.builder().requested(profile).granted(profile).build();
			PlayerIdentity observed = new AgentPlayerObservation(profile.getUsername(), "lobby", () -> client, () -> agents,
					Set.of("lobby")).identity();

			assertEquals(profile, observed.getObserved());
		}
	}

	@Test
	void asksNoAgentBeforeThePlayerIsGrantedAProfile() {
		PlayerIdentity connecting = PlayerIdentity.builder().requested(alice).build();
		var agent = new TestAgent(() -> {
			throw new AssertionError("An agent was asked before the player was granted a profile");
		});

		assertEquals(connecting, new AgentPlayerObservation("Alice", "lobby", () -> connecting, () -> Map.of("lobby", agent),
				Set.of("lobby")).identity());
	}

	@Test
	void observesOnlyConnectedProcessesAndFollowsReplacementConnections() {
		var directory = new ScenarioAgentDirectory();
		ProcessAgentClient lobby = directory.register("lobby");
		directory.register("unstarted");
		var observation = new AgentPlayerObservation("Alice", "lobby", () -> initial, directory, Set.of("lobby"));
		assertEquals(initial, observation.identity());

		AgentIdentity first = identity(UUID.randomUUID());
		lobby.attach(new TestAgent(() -> Optional.of(first)));
		assertEquals(first.getUniqueId(), observation.identity().getObserved().getUniqueId());
		assertEquals("lobby", observation.identity().getRoute().getServer());
		lobby.close();
		assertEquals(initial, observation.identity());

		AgentIdentity replacement = identity(UUID.randomUUID());
		lobby.attach(new TestAgent(() -> Optional.of(replacement)));
		assertEquals(replacement.getUniqueId(), observation.identity().getObserved().getUniqueId());
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
		assertEquals(forwarded, observed.getObserved().getUniqueId());
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
		assertEquals(proxied, observed.getObserved().getUniqueId());
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
		assertThrows(AgentUnavailableException.class, () -> client.identity(alice.getUniqueId()));
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

	private static AgentIdentity onProxy(String connectedServer, PlayerProfile profile) {
		return AgentIdentity.builder()
				.username(profile.getUsername())
				.uniqueId(profile.getUniqueId())
				.location(ProxyLocation.builder().proxy("velocity").connectedServer(connectedServer).build())
				.build();
	}

	private static AgentIdentity onServer(PlayerProfile profile) {
		return AgentIdentity.builder()
				.username(profile.getUsername())
				.uniqueId(profile.getUniqueId())
				.location(ServerLocation.builder().server("native-server").build())
				.build();
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

	private static class TestAgent implements AgentClient {
		private final Function<UUID, Optional<AgentIdentity>> observation;
		private boolean closed;

		private TestAgent(Supplier<Optional<AgentIdentity>> observation) {
			this.observation = ignored -> observation.get();
		}

		/**
		 * Agent of a platform that knows its players by the UUID they were granted.
		 */
		private TestAgent(Map<UUID, AgentIdentity> players) {
			this.observation = uniqueId -> Optional.ofNullable(players.get(uniqueId));
		}

		@Override
		public boolean available() {
			return !closed;
		}

		@Override
		public @NotNull Optional<AgentIdentity> identity(@NotNull UUID uniqueId) {
			if (closed) throw new AgentUnavailableException("Test connection is closed");

			return observation.apply(uniqueId);
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

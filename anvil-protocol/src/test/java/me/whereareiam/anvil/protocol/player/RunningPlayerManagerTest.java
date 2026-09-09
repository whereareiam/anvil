package me.whereareiam.anvil.protocol.player;

import me.whereareiam.anvil.api.exception.AnvilException;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.player.PlayerState;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.type.ProtocolCapability;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunningPlayerManagerTest {
	@TempDir
	Path temporary;

	@Test
	void createsDynamicPlayersUsingTheNativeCompatibleVersionAndAllowsNameReuse() {
		StubBackend backend = new StubBackend();
		RunningPlayerManager manager = manager(backend);

		SimulatedPlayer alice = manager.create("Alice");

		assertEquals("1.21.11", alice.clientVersion());
		assertEquals("1.21.11", backend.lastRequest.getClientVersion());
		assertFalse(alice instanceof AutoCloseable);
		assertEquals(1, manager.all().size());

		alice.destroy();
		assertTrue(alice.state().destroyed());
		assertTrue(manager.all().isEmpty());
		assertEquals("Alice", manager.create("Alice").name());
	}

	@Test
	void supportsDynamicAmountsAndExplicitlyRejectsNativeVersionMismatch() {
		RunningPlayerManager manager = manager(new StubBackend());
		for (int index = 0; index < 25; index++)
			manager.create("Bot-" + index);

		assertEquals(25, manager.all().size());
		assertThrows(AnvilException.class, () -> manager.create(PlayerOptions.builder()
				.name("WrongVersion")
				.clientVersion("26.1.2")
				.build()));

		manager.destroyAll();
		assertTrue(manager.all().isEmpty());
	}

	@Test
	void resolvesTheCurrentProcessGenerationForEachNewPlayer() {
		StubBackend backend = new StubBackend();
		StubScenarioProcesses processes = new StubScenarioProcesses("server", temporary.resolve("server"));
		RunningProcess original = processes.get("server");
		RunningPlayerManager manager = new RunningPlayerManager(scenario(), backend, processes, player -> observation(player), composer(), ignored -> { });
		manager.create("Before");
		assertEquals(original.address(), backend.lastRequest.getAddress());

		RunningProcess replacement = processes.restart("server");
		manager.create("After");

		assertEquals(replacement.address(), backend.lastRequest.getAddress());
		assertEquals(25565, original.address().getPort());
		assertEquals(25566, replacement.address().getPort());
	}

	@Test
	void retainsCompositionFailureWhenBackendPlayerCleanupAlsoFails() {
		StubBackend backend = new StubBackend();
		IllegalStateException failure = new IllegalStateException("composition failed");
		backend.destructionFailure = new IllegalStateException("destruction failed");
		ProtocolPlayerComposer composer = (player, observation, onDestroyed) -> { throw failure; };
		RunningPlayerManager manager = new RunningPlayerManager(scenario(), backend,
				new StubScenarioProcesses("server", temporary), player -> observation(player), composer, ignored -> { });

		assertSame(failure, assertThrows(IllegalStateException.class, () -> manager.create("Alice")));
		assertEquals(1, failure.getSuppressed().length);
		assertSame(backend.destructionFailure, failure.getSuppressed()[0]);
		assertTrue(backend.lastPlayer.destroyed());
		assertTrue(manager.all().isEmpty());
	}

	@Test
	void releasesRegistrationAfterCleanupFailureAndOnlyOnce() {
		StubBackend backend = new StubBackend();
		backend.destructionFailure = new IllegalStateException("destruction failed");
		AtomicInteger releases = new AtomicInteger();
		RunningPlayerManager manager = new RunningPlayerManager(scenario(), backend,
				new StubScenarioProcesses("server", temporary), player -> observation(player), composer(),
				closed -> {
					assertTrue(closed.all().isEmpty());
					assertThrows(IllegalStateException.class, () -> closed.create("late"));
					releases.incrementAndGet();
				});
		manager.create("Alice");

		assertSame(backend.destructionFailure, assertThrows(IllegalStateException.class, manager::close));
		manager.close();

		assertEquals(1, releases.get());
	}

	private RunningPlayerManager manager(StubBackend backend) {
		return new RunningPlayerManager(scenario(), backend,
				new StubScenarioProcesses("server", temporary.resolve("server")), player -> observation(player), composer(), ignored -> { });
	}

	private AnvilScenario scenario() {
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.build();
		return AnvilScenario.builder()
				.name("players")
				.entrypoint(server.getName())
				.server(server)
				.build();
	}

	private PlayerObservation observation(ProtocolPlayer player) {
		return new PlayerObservation() {
			public PlayerIdentity identity() { return player.identity(); }
			public PlayerIdentity await(Predicate<PlayerIdentity> condition, Duration timeout) { return identity(); }
		};
	}

	private ProtocolPlayerComposer composer() {
		return (player, observation, onDestroyed) -> new SimulatedPlayer() {
			@Override
			public @NotNull String name() {
				return player.name();
			}

			@Override
			public @NotNull String clientVersion() {
				return player.clientVersion();
			}

			@Override
			public @NotNull <C extends PlayerCapability> C capability(@NotNull Class<C> type) {
				throw new UnsupportedOperationException("No capabilities are installed in this test");
			}

			@Override
			public boolean hasCapability(@NotNull Class<? extends PlayerCapability> type) {
				return false;
			}

			@Override
			public @NotNull PlayerState state() {
				return PlayerState.builder().destroyed(player.destroyed()).build();
			}

			@Override
			public void destroy() {
				player.destroy();
				onDestroyed.accept(this);
			}
		};
	}

	private static final class StubBackend implements ProtocolBackend {
		private PlayerRequest lastRequest;
		private StubPlayer lastPlayer;
		private RuntimeException destructionFailure;

		@Override
		public @NotNull String id() {
			return "test";
		}

		@Override
		public @NotNull Collection<ProtocolSupport> supportedProtocols() {
			return List.of(ProtocolSupport.builder()
					.minecraftVersion("1.21.11")
					.protocolNumber(1)
					.libraryVersion("test")
					.bindingFamily("test")
					.javaVersion(21)
					.capability(ProtocolCapability.ONLINE_AUTHENTICATION)
					.build());
		}

		@Override
		public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
			lastRequest = request;
			lastPlayer = new StubPlayer(request, destructionFailure);
			return lastPlayer;
		}

		@Override
		public void close() { }
	}

	private static final class StubPlayer implements ProtocolPlayer {
		private final PlayerRequest request;
		private final PlayerIdentity identity;
		private final RuntimeException destructionFailure;
		private boolean destroyed;

		private StubPlayer(PlayerRequest request, RuntimeException destructionFailure) {
			this.request = request;
			this.destructionFailure = destructionFailure;
			this.identity = PlayerIdentity.builder()
					.username(request.getName())
					.clientUniqueId(UUID.nameUUIDFromBytes(("OfflinePlayer:" + request.getName())
							.getBytes(StandardCharsets.UTF_8)))
					.build();
		}

		@Override
		public @NotNull String name() {
			return request.getName();
		}

		@Override
		public @NotNull String clientVersion() {
			return request.getClientVersion();
		}

		@Override
		public @NotNull PlayerIdentity identity() {
			return identity;
		}

		@Override
		public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
			return Optional.empty();
		}

		@Override
		public boolean destroyed() {
			return destroyed;
		}

		@Override
		public void destroy() {
			destroyed = true;
			if (destructionFailure != null) throw destructionFailure;
		}
	}
}

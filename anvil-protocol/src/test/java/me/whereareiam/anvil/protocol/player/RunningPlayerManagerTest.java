package me.whereareiam.anvil.protocol.player;

import me.whereareiam.anvil.api.exception.AnvilException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.model.player.PlayerConnection;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerLogin;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.player.PlayerState;
import me.whereareiam.anvil.api.model.player.SessionIdentity;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.player.account.AccountPool.AccountLease;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.api.type.SupportPolicy;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import me.whereareiam.anvil.protocol.player.account.AccountReservations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunningPlayerManagerTest {
	@TempDir
	Path temporary;

	@Test
	void createsDynamicPlayersUsingTheNativeCompatibleVersionAndAllowsNameReuse() {
		StubLibrary library = new StubLibrary();
		RunningPlayerManager manager = manager(library);

		SimulatedPlayer alice = manager.create("Alice");

		assertEquals("1.21.11", alice.clientVersion());
		assertEquals(MinecraftVersion.parse("1.21.11"), library.lastRequest.getClientVersion());
		assertFalse(alice instanceof AutoCloseable);
		assertEquals(1, manager.all().size());

		alice.destroy();
		assertTrue(alice.state().destroyed());
		assertTrue(manager.all().isEmpty());
		assertEquals("Alice", manager.create("Alice").name());
	}

	@Test
	void severalPlayersShareAUsernameUnderTheirOwnNames() {
		StubLibrary library = new StubLibrary();
		RunningPlayerManager manager = manager(library);

		manager.create("Alice");
		assertEquals("Alice", library.lastRequest.getUsername());

		SimulatedPlayer again = manager.create(PlayerOptions.builder().name("alice-again").login(PlayerLogin.offline("Alice")).build());

		assertEquals("alice-again", again.name());
		assertEquals("alice-again", library.lastRequest.getName());
		assertEquals("Alice", library.lastRequest.getUsername());
		assertEquals(2, manager.all().size());
		assertSame(again, manager.get("alice-again"));
	}

	@Test
	void aLeasedLoginIsRefusedWithoutALease() {
		StubLibrary library = new StubLibrary();
		RunningPlayerManager manager = manager(library);

		var refused = assertThrows(ScenarioValidationException.class, () -> manager.create(PlayerOptions.builder()
				.name("alice").login(PlayerLogin.leased(AuthenticationMode.ON_REQUEST)).build()));

		assertEquals("Player 'alice' declares a leased login; create it with PlayerManager.create(PlayerOptions, AccountLease)",
				refused.getMessage());
		assertNull(library.lastRequest);
	}

	@Test
	void passesTheTargetVirtualHostAndSourceAddressToTheLibrary() {
		StubLibrary library = new StubLibrary();
		RunningPlayerManager manager = manager(library);

		manager.create(PlayerOptions.builder().name("alice").connection(PlayerConnection.builder()
				.target("server").virtualHost("lobby.example.test").sourceAddress("127.0.0.1").build()).build());

		assertEquals("lobby.example.test", library.lastRequest.getConnection().getVirtualHost());
		assertEquals(InetAddress.getLoopbackAddress(), library.lastRequest.getConnection().getSourceAddress());
		assertEquals("127.0.0.1", library.lastRequest.getConnection().getAddress().getHostString());

		manager.create("bob");
		assertNull(library.lastRequest.getConnection().getVirtualHost());
		assertNull(library.lastRequest.getConnection().getSourceAddress());
	}

	@Test
	void aSourceAddressIsRefusedWhenTheExecutionTranslatesConnections() {
		StubLibrary library = new StubLibrary();
		RunningPlayerManager manager = new RunningPlayerManager(scenario(), selector(library), ignored -> library,
				new StubScenarioProcesses("server", temporary.resolve("server")), (player, connectedTo) -> observation(player),
				composer(), ignored -> { }, List::of, new AccountReservations(), false);

		var refused = assertThrows(ScenarioValidationException.class, () -> manager.create(PlayerOptions.builder()
				.name("alice").connection(PlayerConnection.builder().sourceAddress("127.0.0.2").build()).build()));

		assertTrue(refused.getMessage().startsWith("Player 'alice' declares the source address 127.0.0.2, but the "
				+ "scenario's execution provider translates game connections"), refused.getMessage());
		assertNull(library.lastRequest);
	}

	@Test
	void aSourceAddressIsRefusedForAListenerOfAnotherAddressFamily() {
		StubLibrary library = new StubLibrary();
		RunningPlayerManager manager = manager(library);

		var refused = assertThrows(ScenarioValidationException.class, () -> manager.create(PlayerOptions.builder()
				.name("alice").connection(PlayerConnection.builder().sourceAddress("::1").build()).build()));

		assertTrue(refused.getMessage().contains("a loopback source address only reaches a loopback listener of the same "
				+ "address family"), refused.getMessage());
		assertNull(library.lastRequest);
	}

	@Test
	void supportsDynamicAmountsAndExplicitlyRejectsNativeVersionMismatch() {
		RunningPlayerManager manager = manager(new StubLibrary());
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
	void unparseableClientAndServerVersionsAreScenarioValidationFailures() {
		StubLibrary library = new StubLibrary();
		RunningPlayerManager manager = manager(library);

		var client = assertThrows(ScenarioValidationException.class, () -> manager.create(PlayerOptions.builder()
				.name("Snapshot")
				.clientVersion("1.21.11-pre1")
				.build()));
		assertTrue(client.getMessage().startsWith("Player 'Snapshot' declares an invalid client version"), client.getMessage());

		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform("test")
				.distribution(Distribution.remote("26.1-snapshot-1", "1"))
				.build();
		AnvilScenario scenario = AnvilScenario.builder().name("snapshot").entrypoint("server").server(server).build();
		RunningPlayerManager snapshot = manager(scenario, library, new StubScenarioProcesses("server", temporary), composer(), ignored -> { });

		var serverVersion = assertThrows(ScenarioValidationException.class, () -> snapshot.create("Alice"));
		assertTrue(serverVersion.getMessage().startsWith("Server 'server' declares an invalid native Minecraft version"),
				serverVersion.getMessage());
		assertNull(library.lastRequest);
	}

	@Test
	void resolvesTheCurrentProcessGenerationForEachNewPlayer() {
		StubLibrary library = new StubLibrary();
		StubScenarioProcesses processes = new StubScenarioProcesses("server", temporary.resolve("server"));
		RunningProcess original = processes.get("server");
		RunningPlayerManager manager = manager(scenario(), library, processes, composer(), ignored -> { });
		manager.create("Before");
		assertEquals(original.address(), library.lastRequest.getConnection().getAddress());

		RunningProcess replacement = processes.restart("server");
		manager.create("After");

		assertEquals(replacement.address(), library.lastRequest.getConnection().getAddress());
		assertEquals(25565, original.address().getPort());
		assertEquals(25566, replacement.address().getPort());
	}

	@Test
	void retainsCompositionFailureWhenLibraryPlayerCleanupAlsoFails() {
		StubLibrary library = new StubLibrary();
		IllegalStateException failure = new IllegalStateException("composition failed");
		library.destructionFailure = new IllegalStateException("destruction failed");
		ProtocolPlayerComposer composer = (player, observation, metadata, onDestroyed) -> { throw failure; };
		RunningPlayerManager manager = manager(scenario(), library, new StubScenarioProcesses("server", temporary), composer, ignored -> { });

		assertSame(failure, assertThrows(IllegalStateException.class, () -> manager.create("Alice")));
		assertEquals(1, failure.getSuppressed().length);
		assertSame(library.destructionFailure, failure.getSuppressed()[0]);
		assertTrue(library.lastPlayer.destroyed());
		assertTrue(manager.all().isEmpty());
	}

	@Test
	void releasesRegistrationAfterCleanupFailureAndOnlyOnce() {
		StubLibrary library = new StubLibrary();
		library.destructionFailure = new IllegalStateException("destruction failed");
		AtomicInteger releases = new AtomicInteger();
		RunningPlayerManager manager = manager(scenario(), library, new StubScenarioProcesses("server", temporary), composer(),
				closed -> {
					assertTrue(closed.all().isEmpty());
					assertThrows(IllegalStateException.class, () -> closed.create("late"));
					releases.incrementAndGet();
				});
		manager.create("Alice");

		assertSame(library.destructionFailure, assertThrows(IllegalStateException.class, manager::close));
		manager.close();

		assertEquals(1, releases.get());
	}

	@Test
	void failedOnlineCreationReleasesTheAccountForTheNextAttempt() {
		StubLibrary library = new StubLibrary();
		IllegalStateException refresh = new IllegalStateException("Token refresh failed");
		library.creationFailure = refresh;
		AuthenticationAccount alice = new AuthenticationAccount("alice", "test", "Alice", null);
		RunningPlayerManager manager = onlineManager(library, new AccountReservations(), alice);
		PlayerOptions options = online("alice");

		assertSame(refresh, assertThrows(IllegalStateException.class, () -> manager.create(options)));
		library.creationFailure = null;

		assertEquals("alice", manager.create(options).name());
	}

	@Test
	void scenariosOfOneEngineCannotUseTheSameAccountAtOnce() {
		AuthenticationAccount alice = new AuthenticationAccount("alice", "test", "Alice", null);
		AccountReservations reservations = new AccountReservations();
		RunningPlayerManager first = onlineManager(new StubLibrary(), reservations, alice);
		RunningPlayerManager second = onlineManager(new StubLibrary(), reservations, alice);
		var player = first.create(online("alice"));

		assertThrows(ScenarioValidationException.class, () -> second.create(online("alice")));
		player.destroy();
		assertEquals("alice", second.create(online("alice")).name());
	}

	@Test
	void onlinePlayersRequireAnAccountOwnedByTheirSelectedLibrary() {
		StubLibrary library = new StubLibrary();
		AuthenticationAccount foreign = new AuthenticationAccount("alice", "other", "Alice", null);
		RunningPlayerManager manager = onlineManager(library, new AccountReservations(), foreign);

		var failure = assertThrows(ScenarioValidationException.class, () -> manager.create(online("alice")));

		assertTrue(failure.getMessage().contains("is not stored by protocol library 'test'"), failure.getMessage());
		assertTrue(failure.getMessage().endsWith("it is stored by [other]"), failure.getMessage());
		assertNull(library.lastRequest);
	}

	@Test
	void onRequestPlayersSignInOnAnOfflineEntrypointThatOnlinePlayersCannotJoin() {
		StubLibrary library = new StubLibrary();
		AuthenticationAccount account = new AuthenticationAccount("alice", "test", "Alice", null);
		RunningPlayerManager manager = new RunningPlayerManager(scenario(), selector(library), ignored -> library,
				new StubScenarioProcesses("server", temporary.resolve("server")), (player, connectedTo) -> observation(player), composer(),
				ignored -> { }, () -> List.of(account), new AccountReservations(), true);

		var refused = assertThrows(ScenarioValidationException.class, () -> manager.create(online("alice")));
		assertEquals("Online player 'alice' requires an online-mode entrypoint; use AuthenticationMode.ON_REQUEST when a "
				+ "plugin of an offline-mode entrypoint requests authentication itself", refused.getMessage());

		assertEquals("alice", manager.create(online("alice").toBuilder()
				.login(PlayerLogin.account(AuthenticationMode.ON_REQUEST, "alice")).build()).name());
		assertEquals(AuthenticationMode.ON_REQUEST, library.lastRequest.getLogin().getAuthentication());
		assertEquals("alice", library.lastRequest.getLogin().getAccountId());
	}

	@Test
	void anAccountIdStoredBySeveralLibrariesSignsInWithTheAccountOfTheSelectedLibrary() {
		StubLibrary library = new StubLibrary();
		RunningPlayerManager manager = onlineManager(library, new AccountReservations(),
				new AuthenticationAccount("alice", "other", "Alice", null),
				new AuthenticationAccount("alice", "test", "Alice", null));

		assertEquals("alice", manager.create(online("alice")).name());
		assertEquals("alice", library.lastRequest.getLogin().getAccountId());
	}

	@Test
	void aSessionIdentitySignsInWithoutAStoredAccountWhenTheProcessVerifiesAgainstTheSameServer() {
		StubLibrary library = new StubLibrary();
		URI sessionServer = URI.create("http://127.0.0.1:25580/session/minecraft");
		SessionIdentity identity = SessionIdentity.builder().username("Alice").uniqueId(UUID.randomUUID())
				.accessToken("token").sessionServer(sessionServer).build();
		PlayerOptions options = PlayerOptions.builder().name("alice")
				.login(PlayerLogin.session(AuthenticationMode.ONLINE, identity)).build();

		onlineManager(selector(library), library, new AccountReservations(), sessionServer).create(options);
		assertSame(identity, library.lastRequest.getLogin().getSessionIdentity());

		RunningPlayerManager mojang = onlineManager(library, new AccountReservations());
		var refused = assertThrows(ScenarioValidationException.class, () -> mojang.create(options));
		assertTrue(refused.getMessage().contains("verifies logins against Mojang"), refused.getMessage());
	}

	@Test
	void aLeasedAccountSelectsItsOwnLibraryOverTheEngineChoice() {
		StubLibrary library = new StubLibrary();
		AuthenticationAccount leased = new AuthenticationAccount("alice", "other", "Alice", null);
		ProtocolLibrarySelector selector = new ProtocolLibrarySelector(List.of("test", "other"),
				Map.of("test", library.releases(), "other", List.of(release("other-1.21.11", "1.21.11")))::get, "test",
				SupportPolicy.LENIENT, ignored -> { });
		RunningPlayerManager manager = onlineManager(selector, library, leased, new AuthenticationAccount("alice", "test", "Alice", null));
		RecordingLease lease = new RecordingLease(leased);

		manager.create("alice", lease);

		assertEquals("other", library.lastSelectedId);
		assertFalse(lease.closed);
	}

	@Test
	void aLeaseKeepsThePlayerOptionsAndSuppliesItsAccount() {
		StubLibrary library = new StubLibrary();
		AuthenticationAccount leased = new AuthenticationAccount("stored", "test", "Alice", null);
		RunningPlayerManager manager = onlineManager(new ProtocolLibrarySelector(List.of("test"),
				Map.of("test", library.releases())::get, "test", SupportPolicy.LENIENT, ignored -> { }), library, leased);
		RecordingLease lease = new RecordingLease(leased);

		manager.create(PlayerOptions.builder().name("alice").login(PlayerLogin.leased(AuthenticationMode.ON_REQUEST)).build(), lease);

		assertEquals("stored", library.lastRequest.getLogin().getAccountId());
		assertEquals(AuthenticationMode.ON_REQUEST, library.lastRequest.getLogin().getAuthentication());
		assertFalse(lease.closed);
	}

	@Test
	void aLeaseIsRefusedForALoginThatTakesNoLeaseWithoutBeingClaimed() {
		StubLibrary library = new StubLibrary();
		AuthenticationAccount leased = new AuthenticationAccount("stored", "test", "Alice", null);
		RunningPlayerManager manager = onlineManager(new ProtocolLibrarySelector(List.of("test"),
				Map.of("test", library.releases())::get, "test", SupportPolicy.LENIENT, ignored -> { }), library, leased);
		RecordingLease lease = new RecordingLease(leased);

		assertThrows(IllegalArgumentException.class,
				() -> manager.create(PlayerOptions.builder().name("alice").build(), lease));
		assertThrows(IllegalArgumentException.class, () -> manager.create(online("alice"), lease));
		assertTrue(lease.claim());
	}

	@Test
	void aLeasedAccountWhoseLibraryCannotServeThePlayerIsRefusedAndReturned() {
		StubLibrary library = new StubLibrary();
		AuthenticationAccount leased = new AuthenticationAccount("alice", "other", "Alice", null);
		ProtocolLibrarySelector selector = new ProtocolLibrarySelector(List.of("test", "other"),
				Map.of("test", library.releases(), "other", List.of(release("other-1.20.6", "1.20.6")))::get, null,
				SupportPolicy.LENIENT, ignored -> { });
		RunningPlayerManager manager = onlineManager(selector, library, leased);
		RecordingLease lease = new RecordingLease(leased);

		var failure = assertThrows(ScenarioValidationException.class, () -> manager.create("alice", lease));

		assertEquals("Protocol library 'other' selected for player 'alice' does not support Minecraft 1.21.11. Supported: [1.20.6]",
				failure.getMessage());
		assertTrue(lease.closed);
		assertNull(library.lastRequest);
	}

	@Test
	void aLeasedAccountOfTheSelectedLibrarySignsInAndIsReturnedOnDestruction() {
		StubLibrary library = new StubLibrary();
		AuthenticationAccount leased = new AuthenticationAccount("alice", "test", "Alice", null);
		RunningPlayerManager manager = onlineManager(library, new AccountReservations(), leased);
		RecordingLease lease = new RecordingLease(leased);

		SimulatedPlayer player = manager.create("alice", lease);

		assertEquals("alice", library.lastRequest.getLogin().getAccountId());
		assertFalse(lease.closed);
		player.destroy();
		assertTrue(lease.closed);
	}

	private RunningPlayerManager onlineManager(ProtocolLibrarySelector selector, StubLibrary library, AuthenticationAccount... accounts) {
		return onlineManager(selector, library, new AccountReservations(), accounts);
	}

	private RunningPlayerManager onlineManager(StubLibrary library, AccountReservations reservations, AuthenticationAccount... accounts) {
		return onlineManager(selector(library), library, reservations, accounts);
	}

	private RunningPlayerManager onlineManager(
			ProtocolLibrarySelector selector,
			StubLibrary library,
			AccountReservations reservations,
			AuthenticationAccount... accounts
	) {
		return onlineManager(selector, library, reservations, null, accounts);
	}

	private RunningPlayerManager onlineManager(
			ProtocolLibrarySelector selector,
			StubLibrary library,
			AccountReservations reservations,
			@Nullable URI sessionServer,
			AuthenticationAccount... accounts
	) {
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.onlineMode(true)
				.sessionServer(sessionServer)
				.build();
		AnvilScenario scenario = AnvilScenario.builder().name("online").entrypoint("server").server(server).build();

		return new RunningPlayerManager(scenario, selector, selected -> {
			library.lastSelectedId = selected;
			return library;
		}, new StubScenarioProcesses("server", temporary.resolve("server")), (player, connectedTo) -> observation(player), composer(),
				ignored -> { }, () -> List.of(accounts), reservations, true);
	}

	private PlayerOptions online(String accountId) {
		return PlayerOptions.builder()
				.name(accountId)
				.login(PlayerLogin.account(AuthenticationMode.ONLINE, accountId))
				.build();
	}

	private RunningPlayerManager manager(StubLibrary library) {
		return manager(scenario(), library, new StubScenarioProcesses("server", temporary.resolve("server")), composer(), ignored -> { });
	}

	private RunningPlayerManager manager(
			AnvilScenario scenario,
			StubLibrary library,
			StubScenarioProcesses processes,
			ProtocolPlayerComposer composer,
			Consumer<RunningPlayerManager> onClosed
	) {
		return new RunningPlayerManager(scenario, selector(library), ignored -> library, processes,
				(player, connectedTo) -> observation(player), composer, onClosed, List::of, new AccountReservations(), true);
	}

	private static ProtocolRelease release(String libraryVersion, String minecraft) {
		MinecraftVersion version = MinecraftVersion.parse(minecraft);
		return ProtocolRelease.builder()
				.libraryVersion(libraryVersion)
				.minecraftVersion(version)
				.verifiedVersion(version)
				.protocolNumber(1)
				.javaVersion(21)
				.feature(ProtocolFeature.ONLINE_AUTHENTICATION)
				.build();
	}

	private ProtocolLibrarySelector selector(StubLibrary library) {
		return new ProtocolLibrarySelector(List.of(library.id()), Map.of(library.id(), library.releases())::get, null,
				SupportPolicy.LENIENT, ignored -> { });
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
		return (player, observation, metadata, onDestroyed) -> new SimulatedPlayer() {
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

	/**
	 * Lease that can be claimed once and records being returned.
	 */
	private static final class RecordingLease implements AccountLease {
		private final AuthenticationAccount account;
		private boolean claimed;
		private boolean closed;

		private RecordingLease(AuthenticationAccount account) {
			this.account = account;
		}

		@Override
		public @NotNull AuthenticationAccount account() {
			return account;
		}

		@Override
		public boolean claim() {
			if (claimed || closed) return false;

			claimed = true;
			return true;
		}

		@Override
		public void close() {
			closed = true;
		}
	}

	private static final class StubLibrary implements ProtocolLibrary {
		private String lastSelectedId;
		private PlayerRequest lastRequest;
		private StubPlayer lastPlayer;
		private RuntimeException destructionFailure;
		private RuntimeException creationFailure;

		@Override
		public @NotNull String id() {
			return "test";
		}

		@Override
		public @NotNull List<ProtocolRelease> releases() {
			MinecraftVersion version = MinecraftVersion.parse("1.21.11");
			return List.of(ProtocolRelease.builder()
					.libraryVersion("test-1.21.11")
					.minecraftVersion(version)
					.verifiedVersion(version)
					.protocolNumber(1)
					.javaVersion(21)
					.feature(ProtocolFeature.ONLINE_AUTHENTICATION)
					.build());
		}

		@Override
		public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
			if (creationFailure != null) throw creationFailure;
			lastRequest = request;
			lastPlayer = new StubPlayer(request, releases().getFirst(), destructionFailure);
			return lastPlayer;
		}

		@Override
		public void close() { }
	}

	private static final class StubPlayer implements ProtocolPlayer {
		private final PlayerRequest request;
		private final ProtocolRelease release;
		private final PlayerIdentity identity;
		private final RuntimeException destructionFailure;
		private boolean destroyed;

		private StubPlayer(PlayerRequest request, ProtocolRelease release, RuntimeException destructionFailure) {
			this.request = request;
			this.release = release;
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
			return request.getClientVersion().toString();
		}

		@Override
		public @NotNull String libraryId() {
			return "test";
		}

		@Override
		public @NotNull ProtocolRelease release() {
			return release;
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

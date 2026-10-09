package me.whereareiam.anvil.protocol.player;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.protocol.api.library.ProtocolArtifactResolver;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPlayerServiceTest {
	@TempDir
	Path directory;

	@Test
	void createsOneLibraryPerSelectedLibraryOnFirstUseAndClosesThemAfterTheirManagers() {
		StubProvider first = new StubProvider("first");
		StubProvider second = new StubProvider("second");
		DefaultPlayerService service = service(options(), first, second);
		AnvilScenario scenario = scenario();

		service.prepare(scenario);
		PlayerManager manager = service.open(scenario, new StubScenarioProcesses("server", directory), player -> { throw new AssertionError(); }, composer());
		assertEquals(0, first.created + second.created, "Preparation must not create libraries");

		create(manager, "Alice", "first");
		create(manager, "Bob", "first");
		create(manager, "Carol", "second");
		assertEquals(1, first.created);
		assertEquals(1, second.created);
		assertEquals(directory, first.context.getCacheDirectory());
		assertEquals(List.of("Alice", "Bob"), first.requests);

		first.onClose = () -> assertThrows(IllegalStateException.class, () -> manager.create("late"));
		service.close();
		service.close();

		assertEquals(1, first.closed);
		assertEquals(1, second.closed);
		assertThrows(IllegalStateException.class, () -> service.prepare(scenario));
		assertThrows(IllegalStateException.class,
				() -> service.open(scenario, new StubScenarioProcesses("server", directory), player -> { throw new AssertionError(); }, composer()));
	}

	@Test
	void scenarioLibrariesRankInstalledLibrariesUnlessTheScenarioOrEngineChoosesOne() {
		StubProvider first = new StubProvider("first");
		StubProvider second = new StubProvider("second");
		DefaultPlayerService sole = service(options(), first);
		DefaultPlayerService tied = service(options(), first, second);
		DefaultPlayerService engine = service(options().toBuilder().protocolLibrary("second").build(), first, second);

		assertEquals(List.of("first"), sole.scenarioLibraries(scenario()));
		assertEquals(List.of(), tied.scenarioLibraries(scenario()), "Tied libraries are selectable only by declaration");
		assertEquals(List.of("first"), tied.scenarioLibraries(scenario().toBuilder().protocolLibrary("first").build()));
		assertEquals(List.of("second"), engine.scenarioLibraries(scenario()));
		assertEquals(0, first.created + second.created, "Ranking libraries must not create them");

		tied.close();
		assertThrows(IllegalStateException.class, () -> tied.scenarioLibraries(scenario()));
		sole.close();
		engine.close();
	}

	@Test
	void closingWhilePlayerCreationHoldsItsManagerNeitherDeadlocksNorLeaksTheLibrary() throws Exception {
		StubProvider provider = new StubProvider("test");
		DefaultPlayerService service = service(options(), provider);
		PlayerManager manager = service.open(scenario(), new StubScenarioProcesses("server", directory), player -> { throw new AssertionError(); }, composer());
		AtomicReference<Thread> closer = new AtomicReference<>();
		CountDownLatch selecting = new CountDownLatch(1);
		// Release selection runs while creation holds the manager lock; it waits until close() holds the
		// service lock and blocks on that manager, so creation must then obtain its library without the service lock.
		provider.onReleases = () -> {
			selecting.countDown();
			awaitBlocked(closer);
		};
		AtomicReference<Throwable> creation = new AtomicReference<>();
		AtomicReference<Throwable> closing = new AtomicReference<>();

		Thread creator = Thread.ofPlatform().daemon().start(() -> capture(() -> manager.create("Alice"), creation));
		assertTrue(selecting.await(10, TimeUnit.SECONDS), "Player creation never selected a library");
		closer.set(Thread.ofPlatform().daemon().start(() -> capture(service::close, closing)));
		creator.join(Duration.ofSeconds(10));
		closer.get().join(Duration.ofSeconds(10));

		assertFalse(creator.isAlive(), "Player creation deadlocked with close()");
		assertFalse(closer.get().isAlive(), "close() deadlocked with player creation");
		assertInstanceOf(UnsupportedOperationException.class, creation.get());
		assertEquals("stub library creates no players", creation.get().getMessage());
		assertNull(closing.get());
		assertEquals(1, provider.created);
		assertEquals(1, provider.closed);
	}

	@Test
	void closingAnUnusedServiceDoesNotCreateLibraries() {
		StubProvider provider = new StubProvider("test");
		DefaultPlayerService service = service(options(), provider);

		service.close();

		assertEquals(0, provider.created);
		assertEquals(0, provider.closed);
		assertThrows(IllegalStateException.class, () -> service.prepare(scenario()));
	}

	@Test
	void reportsLibraryCleanupFailureAndRemainsClosed() {
		StubProvider provider = new StubProvider("test");
		IllegalStateException failure = new IllegalStateException("library cleanup failed");
		provider.onClose = () -> { throw failure; };
		DefaultPlayerService service = service(options(), provider);
		PlayerManager manager = service.open(scenario(), new StubScenarioProcesses("server", directory), player -> { throw new AssertionError(); }, composer());
		create(manager, "Alice", null);

		assertSame(failure, assertThrows(IllegalStateException.class, service::close));
		service.close();

		assertEquals(1, provider.closed);
	}

	@Test
	void rejectsEngineAndScenarioDeclarationsOfLibrariesThatAreNotInstalled() {
		StubProvider provider = new StubProvider("test");
		AnvilScenario scenario = scenario();

		var engine = assertThrows(IllegalArgumentException.class,
				() -> service(options().toBuilder().protocolLibrary("missing").build(), provider));
		var releases = assertThrows(IllegalArgumentException.class,
				() -> service(options().toBuilder().protocolRelease("missing", directory.resolve("releases.toml")).build(), provider));
		var declared = assertThrows(ScenarioValidationException.class,
				() -> service(options(), provider).prepare(scenario.toBuilder().protocolLibrary("missing").build()));

		for (var failure : List.of(engine, releases, declared)) {
			assertTrue(failure.getMessage().contains("'missing'"), failure.getMessage());
			assertTrue(failure.getMessage().contains("Installed: [test]"), failure.getMessage());
		}
		assertEquals(0, provider.created);
	}

	@Test
	void suppliesConfiguredAdditionalReleasesToTheirLibrary() {
		Path releases = directory.resolve("releases.toml");
		StubProvider provider = new StubProvider("test");
		DefaultPlayerService service = service(options().toBuilder().protocolRelease("test", releases).build(), provider);
		PlayerManager manager = service.open(scenario(), new StubScenarioProcesses("server", directory), player -> { throw new AssertionError(); }, composer());

		create(manager, "Alice", null);

		assertEquals(releases, provider.context.getAdditionalReleases());
		assertEquals(directory.resolve("accounts"), provider.context.getAccountsDirectory());
		service.close();
	}

	private static void awaitBlocked(AtomicReference<Thread> thread) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (thread.get() == null || thread.get().getState() != Thread.State.BLOCKED) {
			if (System.nanoTime() > deadline) throw new AssertionError("close() never waited for the creating player manager");
			LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
		}
	}

	private static void capture(Runnable action, AtomicReference<Throwable> failure) {
		try {
			action.run();
		} catch (RuntimeException | Error thrown) {
			failure.set(thrown);
		}
	}

	private void create(PlayerManager manager, String name, String library) {
		PlayerOptions options = PlayerOptions.builder().name(name).protocolLibrary(library).build();
		var failure = assertThrows(UnsupportedOperationException.class, () -> manager.create(options));
		assertEquals("stub library creates no players", failure.getMessage());
	}

	private DefaultPlayerService service(EngineOptions options, StubProvider... providers) {
		return new DefaultPlayerService(new ProtocolLibraryRegistry(List.of(providers)), options, artifacts());
	}

	private EngineOptions options() {
		return EngineOptions.builder()
				.cacheDirectory(directory)
				.accountsDirectory(directory.resolve("accounts"))
				.build();
	}

	private AnvilScenario scenario() {
		return AnvilScenario.builder()
				.name("players")
				.entrypoint("server")
				.server(MinecraftServer.builder()
						.name("server")
						.platform("test")
						.distribution(Distribution.remote("1.21.11", "1"))
						.build())
				.build();
	}

	private ProtocolArtifactResolver artifacts() {
		return (uri, destination, checksum) -> { throw new UnsupportedOperationException(); };
	}

	private ProtocolPlayerComposer composer() {
		return (player, observation, metadata, onDestroyed) -> {
			throw new AssertionError("These service lifecycle tests do not compose players");
		};
	}

	private static final class StubProvider implements ProtocolLibraryProvider {
		private final String id;
		private final List<String> requests = new ArrayList<>();
		private int created;
		private int closed;
		private ProtocolLibraryContext context;
		private Runnable onClose = () -> { };
		private Runnable onReleases = () -> { };

		private StubProvider(String id) {
			this.id = id;
		}

		@Override
		public @NotNull String id() {
			return id;
		}

		@Override
		public @NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context) {
			onReleases.run();
			MinecraftVersion version = MinecraftVersion.parse("1.21.11");
			return List.of(ProtocolRelease.builder().libraryVersion(id).minecraftVersion(version).verifiedVersion(version)
					.protocolNumber(774).javaVersion(21).build());
		}

		@Override
		public @NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context) {
			created++;
			this.context = context;
			return new ProtocolLibrary() {
				@Override
				public @NotNull String id() {
					return id;
				}

				@Override
				public @NotNull List<ProtocolRelease> releases() {
					return StubProvider.this.releases(context);
				}

				@Override
				public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
					requests.add(request.getName());
					throw new UnsupportedOperationException("stub library creates no players");
				}

				@Override
				public void close() {
					closed++;
					onClose.run();
				}
			};
		}
	}
}

package me.whereareiam.anvil.testkit.tests.runtime.provider;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.player.PlayerState;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.process.type.RunningProxy;
import me.whereareiam.anvil.api.process.type.RunningServer;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.protocol.api.library.ProtocolArtifactResolver;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.player.DefaultPlayerService;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import me.whereareiam.anvil.testkit.support.TestExtensionLoader;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies external protocol-library selection and capability validation without launching Minecraft.
 */
class ExternalProviderDiscoveryIntegrationTest {
	@TempDir
	Path temporary;

	@Test
	void installsSeveralLibrariesWithoutCreatingThem() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.extension(), true);
		     var ignored1 = AnvilLauncher.create(options())) {
			assertEquals(List.of("mcprotocol", "fixture"), List.copyOf(ProtocolLibraryRegistry.discover().ids()));
		}
		assertFalse(Files.exists(temporary.resolve("cache/fixture.lifecycle")));
	}

	@Test
	void explicitPlayerScenarioAndEngineChoicesSelectALibraryWhileAnUndeclaredTieIsRefused() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.extension(), true)) {
			List<String> composed = new ArrayList<>();
			try (var players = new DefaultPlayerService(ProtocolLibraryRegistry.discover(), options(), artifacts())) {
				AnvilScenario scenario = scenario();
				players.prepare(scenario);
				PlayerManager manager = players.open(scenario, new Processes(), ExternalProviderDiscoveryIntegrationTest::observation, composer(composed));

				var tie = assertThrows(ScenarioValidationException.class, () -> manager.create("Undeclared"));
				assertTrue(tie.getMessage().contains("[mcprotocol, fixture]"), tie.getMessage());
				assertTrue(tie.getMessage().contains("set protocolLibrary"), tie.getMessage());

				manager.create(PlayerOptions.builder().name("Declared").protocolLibrary("fixture").build());
				var unknown = assertThrows(ScenarioValidationException.class,
						() -> manager.create(PlayerOptions.builder().name("Unknown").protocolLibrary("missing").build()));
				assertTrue(unknown.getMessage().contains("Installed: [mcprotocol, fixture]"), unknown.getMessage());

				PlayerManager scoped = players.open(scenario.toBuilder().protocolLibrary("fixture").build(), new Processes(),
						ExternalProviderDiscoveryIntegrationTest::observation, composer(composed));
				scoped.create("ScenarioChoice");
			}

			try (var players = new DefaultPlayerService(ProtocolLibraryRegistry.discover(),
					options().toBuilder().protocolLibrary("fixture").build(), artifacts())) {
				players.open(scenario(), new Processes(), ExternalProviderDiscoveryIntegrationTest::observation, composer(composed)).create("EngineChoice");
			}

			assertEquals(List.of("Declared:fixture", "ScenarioChoice:fixture", "EngineChoice:fixture"), composed);
			assertEquals(List.of("created", "closed", "created", "closed"),
					Files.readAllLines(temporary.resolve("cache/fixture.lifecycle")));
		}
	}

	@Test
	void reportsMissingCapabilitiesBeforeCreatingTheLibrary() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.brokenExtension(), false);
		     var engine = AnvilLauncher.create(options().toBuilder().eulaAccepted(true).build())) {
			var failure = assertThrows(CapabilityException.class, () -> engine.start(scenario().toBuilder().name("missing-capability").build()));
			assertTrue(failure.getMessage().contains("missing capabilities"));
			assertFalse(Files.exists(temporary.resolve("cache/fixture-broken.lifecycle")));
		}
	}

	@Test
	void offlineLibraryHasNoAuthenticationRequirement() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.extension(), false)) {
			assertTrue(ProtocolLibraryRegistry.discover().require("fixture").authentication(temporary).isEmpty());
		}
	}

	private EngineOptions options() {
		return EngineOptions.builder()
				.cacheDirectory(temporary.resolve("cache"))
				.accountsDirectory(temporary.resolve("accounts"))
				.build();
	}

	private AnvilScenario scenario() {
		return AnvilScenario.builder().name("library-selection").entrypoint("server")
				.server(MinecraftServer.builder().name("server").platform(Platforms.PAPER)
						.distribution(Distribution.remote("1.21.11", "132")).build())
				.build();
	}

	private static ProtocolArtifactResolver artifacts() {
		return (uri, destination, sha256) -> { throw new AssertionError("Selection must not resolve " + uri); };
	}

	private static ProtocolPlayerComposer composer(List<String> composed) {
		return (player, observation, metadata, onDestroyed) -> {
			composed.add(player.name() + ":" + player.libraryId());
			return new SimulatedPlayer() {
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
					throw new UnsupportedOperationException();
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
		};
	}

	private static PlayerObservation observation(ProtocolPlayer player) {
		PlayerIdentity identity = player.identity();
		return new PlayerObservation() {
			@Override
			public PlayerIdentity identity() {
				return identity;
			}

			@Override
			public PlayerIdentity await(Predicate<PlayerIdentity> condition, Duration timeout) {
				return identity;
			}
		};
	}

	private static final class Processes implements ScenarioProcesses {
		private final RunningServer server = new Server();

		@Override
		public @NotNull Collection<RunningProcess> all() {
			return List.of(server);
		}

		@Override
		public @NotNull RunningProcess get(@NotNull String name) {
			return server(name);
		}

		@Override
		public @NotNull Collection<RunningServer> servers() {
			return List.of(server);
		}

		@Override
		public @NotNull RunningServer server(@NotNull String name) {
			if (!server.name().equals(name)) throw new NoSuchElementException(name);
			return server;
		}

		@Override
		public @NotNull Collection<RunningProxy> proxies() {
			return List.of();
		}

		@Override
		public @NotNull RunningProxy proxy(@NotNull String name) {
			throw new NoSuchElementException(name);
		}

		@Override
		public @NotNull RunningProcess start(@NotNull String name) {
			return get(name);
		}

		@Override
		public void stop(@NotNull String name) {
			get(name);
		}

		@Override
		public @NotNull RunningProcess restart(@NotNull String name) {
			return get(name);
		}
	}

	private static final class Server implements RunningServer {
		private final UUID executionId = UUID.randomUUID();

		@Override
		public @NotNull UUID executionId() {
			return executionId;
		}

		@Override
		public @NotNull String name() {
			return "server";
		}

		@Override
		public @NotNull InetSocketAddress address() {
			return new InetSocketAddress("127.0.0.1", 25565);
		}

		@Override
		public @NotNull Path workDirectory() {
			return Path.of("server");
		}

		@Override
		public @NotNull ProcessState state() {
			return ProcessState.READY;
		}

		@Override
		public @NotNull ProcessConsole console() {
			throw new UnsupportedOperationException();
		}
	}
}

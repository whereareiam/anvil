package me.whereareiam.anvil.engine.player;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.player.PlayerState;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.type.ProtocolCapability;
import me.whereareiam.anvil.engine.AnvilException;
import me.whereareiam.anvil.engine.runtime.process.ManagedServer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

	private RunningPlayerManager manager(StubBackend backend) {
		MinecraftServer server = MinecraftServer.builder()
				.name("server")
				.platform("test")
				.distribution(Distribution.remote("1.21.11", "1"))
				.build();
		AnvilScenario scenario = AnvilScenario.builder()
				.name("players")
				.entrypoint(server.getName())
				.server(server)
				.build();
		ManagedServer running = new ManagedServer(
				server.getName(),
				new InetSocketAddress("127.0.0.1", 25565),
				temporary.resolve("server")
		);
		return new RunningPlayerManager(
				scenario,
				backend,
				Map.of(server.getName(), running),
				composer()
			);
	}

	private ProtocolPlayerComposer composer() {
		return (player, services, onDestroyed) -> new SimulatedPlayer() {
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
			return new StubPlayer(request);
		}

		@Override
		public void close() { }
	}

	private static final class StubPlayer implements ProtocolPlayer {
		private final PlayerRequest request;
		private final PlayerIdentity identity;
		private boolean destroyed;

		private StubPlayer(PlayerRequest request) {
			this.request = request;
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
		}
	}
}

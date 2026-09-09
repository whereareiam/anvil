package me.whereareiam.anvil.protocol.player;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
import me.whereareiam.anvil.protocol.api.provider.ProtocolRuntimeResolver;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultPlayerServiceTest {
	@TempDir
	Path directory;

	@Test
	void preparesOneBackendAndClosesItsManagersBeforeReleasingIt() {
		StubProvider provider = new StubProvider();
		DefaultPlayerService service = new DefaultPlayerService(provider, directory, artifacts());
		assertEquals(0, provider.created);

		service.prepare();
		service.prepare();
		assertEquals(1, provider.created);
		assertEquals(directory, provider.cacheDirectory);

		AnvilScenario scenario = AnvilScenario.builder().name("players").entrypoint("server").build();
		PlayerManager manager = service.open(scenario, new StubScenarioProcesses("server", directory), player -> { throw new AssertionError(); }, composer());
		provider.onClose = () -> assertThrows(IllegalStateException.class, () -> manager.create("late"));
		service.close();
		service.close();

		assertEquals(1, provider.closed);
		assertThrows(IllegalStateException.class, service::prepare);
		assertThrows(IllegalStateException.class,
				() -> service.open(scenario, new StubScenarioProcesses("server", directory), player -> { throw new AssertionError(); }, composer()));
	}

	@Test
	void closingAnUnusedServiceDoesNotInitializeItsBackend() {
		StubProvider provider = new StubProvider();
		DefaultPlayerService service = new DefaultPlayerService(provider, directory, artifacts());

		service.close();

		assertEquals(0, provider.created);
		assertEquals(0, provider.closed);
		assertThrows(IllegalStateException.class, service::prepare);
	}

	@Test
	void reportsBackendCleanupFailureAndRemainsClosed() {
		StubProvider provider = new StubProvider();
		IllegalStateException failure = new IllegalStateException("backend cleanup failed");
		provider.onClose = () -> { throw failure; };
		DefaultPlayerService service = new DefaultPlayerService(provider, directory, artifacts());
		service.prepare();

		assertSame(failure, assertThrows(IllegalStateException.class, service::close));
		service.close();

		assertEquals(1, provider.closed);
	}

	private ProtocolRuntimeResolver artifacts() {
		return (uri, destination, checksum) -> { throw new UnsupportedOperationException(); };
	}

	private ProtocolPlayerComposer composer() {
		return (player, observation, onDestroyed) -> {
			throw new AssertionError("These service lifecycle tests do not create players");
		};
	}

	private static final class StubProvider implements ProtocolProvider {
		private int created;
		private int closed;
		private Path cacheDirectory;
		private Runnable onClose = () -> { };

		@Override
		public @NotNull String id() {
			return "test";
		}

		@Override
		public @NotNull ProtocolBackend create(@NotNull Path cacheDirectory, @NotNull ProtocolRuntimeResolver artifacts) {
			created++;
			this.cacheDirectory = cacheDirectory;
			return new ProtocolBackend() {
				@Override
				public @NotNull String id() {
					return "test";
				}

				@Override
				public @NotNull Collection<ProtocolSupport> supportedProtocols() {
					return List.of();
				}

				@Override
				public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
					throw new UnsupportedOperationException();
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

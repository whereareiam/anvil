package external.fixture.protocol;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import org.jetbrains.annotations.NotNull;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deterministic in-process transport fixture. It tests extension contracts, not Minecraft packets.
 */
public class FixtureProtocolProvider implements ProtocolProvider {
	@Override
	public @NotNull String id() {
		return "fixture";
	}

	@Override
	public @NotNull ProtocolBackend create(@NotNull Path cacheDirectory, @NotNull ArtifactResolver artifacts) {
		Path trace = cacheDirectory.resolve(id() + ".lifecycle");
		trace(trace, "created");
		return new ProtocolBackend() {
			@Override
			public @NotNull String id() {
				return FixtureProtocolProvider.this.id();
			}

			@Override
			public @NotNull Collection<ProtocolSupport> supportedProtocols() {
				return List.of(ProtocolSupport.builder().minecraftVersion("1.21.11").protocolNumber(774)
						.libraryVersion("fixture").bindingFamily("fixture").javaVersion(21).build());
			}

			@Override
			public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
				return player(request);
			}

			@Override
			public void close() {
				trace(trace, "closed");
			}
		};
	}

	private ProtocolPlayer player(PlayerRequest request) {
		return new ProtocolPlayer() {
			private final MemoryConnection connection = new MemoryConnection();
			private boolean destroyed;

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
				return PlayerIdentity.builder().username(name())
						.clientUniqueId(UUID.nameUUIDFromBytes(name().getBytes(StandardCharsets.UTF_8))).build();
			}

			@Override
			public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) {
				if (type.isInstance(connection))
					return Optional.of(type.cast(connection));
				return Optional.empty();
			}

			@Override
			public boolean destroyed() {
				return destroyed;
			}

			@Override
			public void destroy() {
				destroyed = true;
				connection.disconnect();
			}
		};
	}

	private void trace(Path file, String event) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, event + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static final class MemoryConnection implements FixtureConnection {
		private boolean connected;
		private String lastSent = "";

		@Override
		public void connect() {
			connected = true;
		}

		@Override
		public void disconnect() {
			connected = false;
		}

		@Override
		public boolean connected() {
			return connected;
		}

		@Override
		public void send(String payload) {
			if (!connected)
				throw new IllegalStateException("Fixture connection is disconnected");
			lastSent = payload;
		}

		@Override
		public String lastSent() {
			return lastSent;
		}
	}
}

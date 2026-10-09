package external.tooling;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
import me.whereareiam.anvil.protocol.api.provider.ProtocolRuntimeResolver;
import org.jetbrains.annotations.NotNull;

/**
 * In-memory protocol fixture for host capabilities; it does not emulate Minecraft packets.
 */
public final class FixtureProtocol implements ProtocolProvider {
	@Override public @NotNull String id() { return "fixture-tooling"; }
	@Override public @NotNull ProtocolBackend create(@NotNull Path cache, @NotNull Path accounts, @NotNull ProtocolRuntimeResolver artifacts) {
		return new ProtocolBackend() {
			@Override public @NotNull String id() { return "fixture-tooling"; }
			@Override public @NotNull Collection<ProtocolSupport> supportedProtocols() {
				return List.of(ProtocolSupport.builder().minecraftVersion("1.21.11").protocolNumber(0)
						.libraryVersion("fixture").bindingFamily("fixture").javaVersion(21).build());
			}
			@Override public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
				return new ProtocolPlayer() {
					private final PlayerIdentity identity = PlayerIdentity.builder().username(request.getName()).clientUniqueId(UUID.randomUUID()).build();
					private boolean destroyed;
					@Override public @NotNull String name() { return request.getName(); }
					@Override public @NotNull String clientVersion() { return request.getClientVersion(); }
					@Override public @NotNull PlayerIdentity identity() { return identity; }
					@Override public @NotNull <T> Optional<T> findService(@NotNull Class<T> type) { return Optional.empty(); }
					@Override public boolean destroyed() { return destroyed; }
					@Override public void destroy() { destroyed = true; }
				};
			}
			@Override public void close() { }
		};
	}
}

package external.tooling;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * In-memory protocol library fixture for host capabilities; it does not emulate Minecraft packets.
 */
public final class FixtureLibrary implements ProtocolLibraryProvider {
	private static final String ID = "fixture-tooling";

	@Override public @NotNull String id() { return ID; }

	@Override public @NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context) {
		MinecraftVersion version = MinecraftVersion.parse("1.21.11");
		return List.of(ProtocolRelease.builder().libraryVersion("fixture").minecraftVersion(version)
				.verifiedVersion(version).protocolNumber(774).javaVersion(21).build());
	}

	@Override public @NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context) {
		List<ProtocolRelease> releases = releases(context);
		return new ProtocolLibrary() {
			@Override public @NotNull String id() { return ID; }
			@Override public @NotNull List<ProtocolRelease> releases() { return releases; }
			@Override public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
				return new ProtocolPlayer() {
					private final PlayerIdentity identity = PlayerIdentity.builder().username(request.getName()).clientUniqueId(UUID.randomUUID()).build();
					private boolean destroyed;
					@Override public @NotNull String name() { return request.getName(); }
					@Override public @NotNull String clientVersion() { return request.getClientVersion().toString(); }
					@Override public @NotNull String libraryId() { return ID; }
					@Override public @NotNull ProtocolRelease release() { return releases.getFirst(); }
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

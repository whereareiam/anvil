package external.fixture.java;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.worker.WorkerExtensions;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * An in-process protocol library whose players speak the Java Edition protocol to an {@link EchoServer} instead of a
 * Minecraft server. It is built only on the public worker kits, so the worker extensions of the built-in
 * capabilities run on it as they run on MCProtocolLib.
 */
public final class JavaFixtureLibraryProvider implements ProtocolLibraryProvider {
	static final String ID = "java-fixture";

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public @NotNull List<ProtocolRelease> releases(@NotNull ProtocolLibraryContext context) {
		MinecraftVersion version = MinecraftVersion.parse("1.21.11");
		return List.of(ProtocolRelease.builder()
				.libraryVersion(ID)
				.minecraftVersion(version)
				.verifiedVersion(version)
				.protocolNumber(774)
				.javaVersion(21)
				.build());
	}

	@Override
	public @NotNull Set<Class<?>> protocols() {
		return Set.of(JavaProtocol.class);
	}

	@Override
	public @NotNull ProtocolLibrary create(@NotNull ProtocolLibraryContext context) {
		List<ProtocolRelease> releases = releases(context);
		WorkerExtensions extensions = WorkerExtensions.discover(ID, Object.class, Set.of());
		return new ProtocolLibrary() {
			@Override
			public @NotNull String id() {
				return ID;
			}

			@Override
			public @NotNull List<ProtocolRelease> releases() {
				return releases;
			}

			@Override
			public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request) {
				return new JavaFixturePlayer(request, releases.getFirst(), extensions);
			}

			@Override
			public void close() {
			}
		};
	}
}

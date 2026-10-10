package me.whereareiam.anvil.protocol.mcprotocol.library;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.model.GameConnection;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolClientPoolTest {
	private static final String PINNED = "a".repeat(64);

	@TempDir
	Path temporary;

	private final List<String> resolved = new ArrayList<>();

	@Test
	void refusesAReleaseWithAnUnpinnedArtifactBeforeLaunchingItsWorker() throws Exception {
		Path releases = temporary.resolve("unpinned-releases.toml");
		Files.writeString(releases, """
				[[release]]
				version = "9.9.9-test"
				module = "demo:protocol:9.9.9-test"
				protocol = 999
				minecraft = ["9.9.9"]
				verified = []
				java = 17

				[[release.artifact]]
				module = "demo:protocol:9.9.9-test"
				url = "https://repository.example/demo/protocol-9.9.9-test.jar"
				sha256 = ""
				""");

		try (ProtocolLibrary library = new McProtocolLibraryProvider().create(context(releases))) {
			IllegalStateException failure = assertThrows(IllegalStateException.class, () -> library.create(request("9.9.9")));

			assertEquals("MCProtocolLib release 9.9.9-test has no pinned checksum for demo:protocol:9.9.9-test; "
					+ "set its sha256 in " + releases, failure.getMessage());
			assertEquals(List.of(), resolved);
		}
	}

	@Test
	void cachesTheReleaseClosureUnderItsReleaseVersion() throws Exception {
		Path releases = temporary.resolve("extra-releases.toml");
		Files.writeString(releases, """
				[[release]]
				version = "9.9.9-test"
				module = "demo:protocol:9.9.9-test"
				protocol = 999
				minecraft = ["9.9.9"]
				verified = []
				java = 17

				[[release.artifact]]
				module = "demo:protocol:9.9.9-test"
				url = "https://repository.example/demo/protocol-9.9.9-test.jar"
				sha256 = "%1$s"

				[[release.artifact]]
				module = "demo:transport:1.0:linux-x86_64"
				url = "https://repository.example/demo/transport-1.0-linux-x86_64.jar"
				sha256 = "%1$s"
				""".formatted(PINNED));

		try (ProtocolLibrary library = new McProtocolLibraryProvider().create(context(releases))) {
			IllegalStateException stopped = assertThrows(IllegalStateException.class, () -> library.create(request("9.9.9")));

			assertEquals("stopped before launch", stopped.getMessage());
			Path directory = temporary.resolve("cache/protocol/mcprotocol/9.9.9-test");
			assertEquals(List.of(
					"https://repository.example/demo/protocol-9.9.9-test.jar -> " + directory.resolve("protocol-9.9.9-test.jar") + " " + PINNED
			), resolved);
		}
	}

	@Test
	void refusesNewPlayersAfterShutdownAndVersionsNoReleaseSpeaks() {
		ProtocolLibrary library = new McProtocolLibraryProvider().create(context(null));
		PlayerRequest request = request("26.1.2");

		IllegalArgumentException version = assertThrows(IllegalArgumentException.class, () -> library.create(request("1.20.4")));
		assertTrue(version.getMessage().startsWith("No MCProtocolLib release speaks Minecraft 1.20.4"), version.getMessage());
		library.close();
		library.close();
		assertThrows(IllegalStateException.class, () -> library.create(request));
	}

	private ProtocolLibraryContext context(Path additionalReleases) {
		return ProtocolLibraryContext.builder()
				.cacheDirectory(temporary.resolve("cache"))
				.accountsDirectory(temporary.resolve("accounts"))
				.artifacts((URI artifact, Path destination, String sha256) -> {
					resolved.add(artifact + " -> " + destination + " " + sha256);
					throw new IllegalStateException("stopped before launch");
				})
				.additionalReleases(additionalReleases)
				.build();
	}

	private PlayerRequest request(String version) {
		return PlayerRequest.builder()
				.name("Alice")
				.clientVersion(MinecraftVersion.parse(version))
				.connection(GameConnection.builder().address(new InetSocketAddress("127.0.0.1", 9)).build())
				.build();
	}
}

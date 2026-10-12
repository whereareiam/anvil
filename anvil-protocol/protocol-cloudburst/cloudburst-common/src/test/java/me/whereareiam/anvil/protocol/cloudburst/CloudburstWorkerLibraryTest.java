package me.whereareiam.anvil.protocol.cloudburst;

import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibrary;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.model.GameConnection;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The library as Anvil installs it: discovered through the native worker's protocol library host.
 */
class CloudburstWorkerLibraryTest {
	@TempDir
	Path temporary;

	@Test
	void installsTheLibraryWithItsSessionDerivationThroughTheWorkerHost() {
		ProtocolLibraryProvider installed = installed();

		assertEquals(CloudburstClient.LIBRARY_ID, installed.id());
		assertTrue(ProtocolLibraryRegistry.discover().authentication(temporary.resolve("accounts")).isPresent());
		assertInstanceOf(XboxAuthentication.class, new CloudburstWorkerLibrary().authentication().orElseThrow());
	}

	@Test
	void derivesAnOfflineUniqueIdOfItsOwnPerUsername() {
		UUID alice = CloudburstWorkerLibrary.LIBRARY.offlineUniqueId("Alice");

		assertEquals(UUID.nameUUIDFromBytes("BedrockPlayer:Alice".getBytes(StandardCharsets.UTF_8)), alice);
		assertEquals(alice, CloudburstWorkerLibrary.LIBRARY.offlineUniqueId("Alice"));
		assertNotEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Alice".getBytes(StandardCharsets.UTF_8)), alice);
	}

	@Test
	void refusesPlayersOfAnotherEditionAndAfterShutdown() {
		ProtocolLibrary library = installed().create(ProtocolLibraryContext.builder()
				.cacheDirectory(temporary.resolve("cache"))
				.accountsDirectory(temporary.resolve("accounts"))
				.artifacts((artifact, destination, sha256) -> {
					throw new IllegalStateException("stopped before launch");
				})
				.build());

		IllegalArgumentException edition = assertThrows(IllegalArgumentException.class, () -> library.create(request("java", "1.21.0")));
		assertTrue(edition.getMessage().startsWith("No CloudburstMC Protocol release speaks java Minecraft 1.21"), edition.getMessage());
		library.close();
		assertThrows(IllegalStateException.class, () -> library.create(request("bedrock", "1.21.50")));
	}

	private static ProtocolLibraryProvider installed() {
		return ProtocolLibraryRegistry.discover().require(CloudburstClient.LIBRARY_ID);
	}

	private static PlayerRequest request(String edition, String version) {
		return PlayerRequest.builder()
				.name("Alice")
				.edition(MinecraftEdition.of(edition))
				.clientVersion(MinecraftVersion.parse(version))
				.connection(GameConnection.builder().address(new InetSocketAddress("127.0.0.1", 9)).build())
				.build();
	}
}

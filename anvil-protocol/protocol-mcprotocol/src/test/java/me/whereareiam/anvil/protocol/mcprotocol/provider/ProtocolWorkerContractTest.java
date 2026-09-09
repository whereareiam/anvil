package me.whereareiam.anvil.protocol.mcprotocol.provider;

import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import me.whereareiam.anvil.environment.provisioning.artifact.HttpArtifactAcquirer;
import me.whereareiam.anvil.launcher.assembly.provisioning.CacheArtifactStorage;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolWorkerContractTest {
	private HttpArtifactAcquirer artifacts;

	@org.junit.jupiter.api.BeforeEach
	void createArtifacts() {
		artifacts = new HttpArtifactAcquirer(temporary, new CacheArtifactStorage(new FileCache(temporary)), false, false, 4);
	}

	@org.junit.jupiter.api.AfterEach
	void closeArtifacts() {
		artifacts.close();
	}

	@TempDir
	Path temporary;

	@Test
	void refusesNewPlayersAfterClientPoolShutdown() {
		ProtocolBackend clients = new McProtocolProvider().create(temporary, artifacts::obtain);
		clients.close();
		clients.close();
		PlayerRequest request = PlayerRequest.builder().name("Alice").clientVersion("1.21.11")
				.address(new InetSocketAddress("localhost", 25565)).build();
		assertThrows(IllegalStateException.class, () -> clients.create(request));
	}

	@ParameterizedTest(name = "exact worker for {0}")
	@ValueSource(strings = {"1.21.11", "26.1.2"})
	void launchesExactWorkerWithBuiltInCapabilities(String version) {
		try (ProtocolBackend clients = new McProtocolProvider().create(temporary, artifacts::obtain)) {
			PlayerRequest request = PlayerRequest.builder()
					.name("Alice")
					.clientVersion(version)
					.address(new InetSocketAddress("127.0.0.1", 9))
					.authentication(AuthenticationMode.OFFLINE)
					.build();
			ProtocolPlayer player = clients.create(request);
			try {
				assertEquals(version, player.clientVersion());
				assertEquals(UUID.nameUUIDFromBytes("OfflinePlayer:Alice".getBytes(StandardCharsets.UTF_8)),
						player.identity().getClientUniqueId());
				var connection = player.channel().orElseThrow();
				assertTrue(connection.installedCapabilities().containsAll(Set.of(
						"me.whereareiam.anvil.session",
						"me.whereareiam.anvil.messages",
						"me.whereareiam.anvil.movement",
						"me.whereareiam.anvil.inventory",
						"me.whereareiam.anvil.interaction"
				)));
			} finally {
				player.destroy();
			}
		}
	}
}

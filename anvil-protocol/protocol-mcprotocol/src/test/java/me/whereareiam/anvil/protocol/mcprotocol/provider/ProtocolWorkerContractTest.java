package me.whereareiam.anvil.protocol.mcprotocol.provider;

import me.whereareiam.anvil.api.type.AuthenticationMode;
import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
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
	@TempDir
	Path temporary;

	@Test
	void refusesNewPlayersAfterClientPoolShutdown() {
		ProtocolBackend clients = new McProtocolProvider().create(temporary);
		clients.close();
		clients.close();
		PlayerRequest request = PlayerRequest.builder().name("Alice").clientVersion("1.21.11")
				.address(new InetSocketAddress("localhost", 25565)).build();
		assertThrows(IllegalStateException.class, () -> clients.create(request));
	}

	@ParameterizedTest(name = "exact worker for {0}")
	@ValueSource(strings = {"1.21.11", "26.1.2"})
	void launchesExactWorkerWithBuiltInCapabilities(String version) {
		try (ProtocolBackend clients = new McProtocolProvider().create(temporary)) {
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
				var connection = player.findService(ProtocolPlayerConnection.class).orElseThrow();
				assertTrue(connection.workerCapabilities().containsAll(Set.of(
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

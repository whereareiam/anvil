package me.whereareiam.anvil.protocol.mcprotocol;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.fixture.RecordingClient;
import me.whereareiam.anvil.protocol.worker.WorkerClient;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerConnection;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerCredentials;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerProfile;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolWorkerClientTest {
	private final RecordingClient client = new RecordingClient();
	private final McProtocolWorkerClient worker = new McProtocolWorkerClient(client);
	private final UUID uuid = UUID.randomUUID();
	private final List<String> callbacks = new ArrayList<>();

	@Test
	void opensSessionsThroughTheClientWithLoginAndClientSettings() {
		Object session = worker.open(options("token"), new Recorder());
		worker.connect(session);

		ClientLogin login = client.logins().getFirst();
		assertEquals(1, client.logins().size());
		assertEquals("Alice", login.getProfile().getName());
		assertEquals(uuid, login.getProfile().getUniqueId());
		assertEquals("token", login.getCredentials().getAccessToken());
		assertEquals("en_us", login.getSettings().getLocale());
		assertEquals(8, login.getSettings().getViewDistance());
		assertEquals("localhost", login.getConnection().getHost());
		assertEquals(25565, login.getConnection().getPort());
		assertEquals("lobby.example.test", login.getConnection().getVirtualHost());
		assertEquals("127.0.0.2", login.getConnection().getSourceAddress());
		assertFalse(login.toString().contains("token"));
		assertSame(client.sessions().getFirst(), session);
		assertTrue(worker.connected(session));
		assertEquals(774, worker.protocolNumber());
		assertEquals(Object.class, worker.sessionType());

		worker.disconnect(session, "done");
		assertFalse(worker.connected(session));
		assertEquals(List.of("done"), client.disconnects());
	}

	@Test
	void forwardsTheClientsCallbacksWithTheSessionTheyNameAndTheGrantedProfile() {
		Object session = worker.open(options(null), new Recorder());

		client.listener(session).loggedIn(session, ClientProfile.builder().name("Alice_Renamed").uniqueId(uuid).build());
		client.listener(session).teleported(session, 30, 40);
		client.listener(session).disconnected(session, DisconnectCause.SERVER, "kicked");

		assertEquals(List.of(
				"loggedIn " + session + " Alice_Renamed " + uuid,
				"teleported " + session + " 30.0 40.0",
				"disconnected " + session + " SERVER kicked"
		), callbacks);
		assertNull(client.logins().getFirst().getCredentials());
	}

	private WorkerPlayerOptions options(String accessToken) {
		return WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder().name("Alice").uniqueId(uuid).build())
				.credentials(accessToken == null ? null : WorkerCredentials.builder().accessToken(accessToken).build())
				.connection(WorkerConnection.builder().host("localhost").port(25565)
						.virtualHost("lobby.example.test").sourceAddress("127.0.0.2").build())
				.build();
	}

	/**
	 * Records the session callbacks the worker client forwards.
	 */
	private final class Recorder implements WorkerClient.SessionListener {
		@Override
		public void loggedIn(@NotNull Object session, @NotNull PlayerProfile granted) {
			callbacks.add("loggedIn " + session + " " + granted.getUsername() + " " + granted.getUniqueId());
		}

		@Override
		public void teleported(@NotNull Object session, float yaw, float pitch) {
			callbacks.add("teleported " + session + " " + yaw + " " + pitch);
		}

		@Override
		public void disconnected(@NotNull Object session, @NotNull DisconnectCause cause, @NotNull String reason) {
			callbacks.add("disconnected " + session + " " + cause + " " + reason);
		}
	}
}

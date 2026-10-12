package me.whereareiam.anvil.protocol.cloudburst;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientListener;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstClient;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudburstWorkerClientTest {
	private final RecordingClient client = new RecordingClient();
	private final CloudburstWorkerClient worker = new CloudburstWorkerClient(client);
	private final UUID uuid = UUID.randomUUID();
	private final List<String> callbacks = new ArrayList<>();

	@Test
	void opensOfflineSessionsWithASelfSignedIdentityOfTheProfile() {
		Object session = worker.open(options(null, null), new Recorder());
		worker.connect(session);

		ClientLogin login = client.logins.getFirst();
		assertEquals("Alice", login.getProfile().getName());
		assertEquals(uuid, login.getProfile().getUniqueId());
		assertFalse(login.getIdentity().authenticated());
		assertEquals(1, login.getIdentity().chain().size());
		assertEquals("localhost", login.getConnection().getHost());
		assertEquals(19132, login.getConnection().getPort());
		assertEquals("127.0.0.2", login.getConnection().getSourceAddress());
		assertEquals(2193, login.getConnection().getProtocol());
		assertTrue(worker.connected(session));
		assertEquals(2225, worker.protocolNumber());
		assertEquals(Object.class, worker.sessionType());

		worker.disconnect(session, "done");
		assertFalse(worker.connected(session));
	}

	@Test
	void refusesAVirtualHostThatBedrockCannotAnnounce() {
		WorkerPlayerOptions options = options(null, "lobby.example.test");

		var failure = assertThrows(IllegalArgumentException.class, () -> worker.open(options, new Recorder()));

		assertEquals("A Bedrock connection has no handshake host, so it cannot announce the virtual host 'lobby.example.test'; "
				+ "connect without one", failure.getMessage());
		assertTrue(client.logins.isEmpty());
	}

	@Test
	void refusesOnlineCredentialsWithoutAnXboxIdentity() {
		WorkerCredentials session = WorkerCredentials.builder().accessToken("yggdrasil-token").build();

		var failure = assertThrows(IllegalArgumentException.class, () -> worker.open(options(session, null), new Recorder()));

		assertEquals("A Bedrock player signs in online only with a stored Xbox account; a session identity without one cannot "
				+ "sign a Bedrock login", failure.getMessage());
	}

	@Test
	void forwardsTheClientsCallbacksWithTheSessionTheyName() {
		Object session = worker.open(options(null, null), new Recorder());
		ClientListener<Object> listener = client.listeners.getFirst();

		listener.teleported(session, 90, 10);
		listener.loggedIn(session, ClientProfile.builder().name("Renamed").uniqueId(uuid).build());
		listener.disconnected(session, DisconnectCause.SERVER, "kicked");

		assertEquals(List.of("view 90.0 10.0", "login Renamed " + uuid, "disconnect SERVER kicked"), callbacks);
	}

	private WorkerPlayerOptions options(WorkerCredentials credentials, String virtualHost) {
		return WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder().name("Alice").uniqueId(uuid).build())
				.credentials(credentials)
				.connection(WorkerConnection.builder()
						.host("localhost")
						.port(19132)
						.virtualHost(virtualHost)
						.sourceAddress("127.0.0.2")
						.protocol(2193)
						.build())
				.build();
	}

	/**
	 * Records the session callbacks the worker client forwards.
	 */
	private final class Recorder implements WorkerClient.SessionListener {
		@Override
		public void loggedIn(@NotNull Object session, @NotNull PlayerProfile granted) {
			callbacks.add("login " + granted.getUsername() + " " + granted.getUniqueId());
		}

		@Override
		public void teleported(@NotNull Object session, float yaw, float pitch) {
			callbacks.add("view " + yaw + " " + pitch);
		}

		@Override
		public void disconnected(@NotNull Object session, @NotNull DisconnectCause cause, @NotNull String reason) {
			callbacks.add("disconnect " + cause + " " + reason);
		}
	}

	/**
	 * A client segment that records logins and keeps sessions in memory.
	 */
	private static final class RecordingClient implements CloudburstClient<Object> {
		private final List<ClientLogin> logins = new ArrayList<>();
		private final List<ClientListener<Object>> listeners = new ArrayList<>();
		private final List<Object> connected = new ArrayList<>();

		@Override
		public int protocolNumber() {
			return 2225;
		}

		@Override
		public @NotNull Class<Object> sessionType() {
			return Object.class;
		}

		@Override
		@SuppressWarnings("unchecked")
		public @NotNull Object open(@NotNull ClientLogin login, @NotNull ClientListener<? super Object> listener) {
			logins.add(login);
			listeners.add((ClientListener<Object>) listener);
			return new Object();
		}

		@Override
		public void connect(@NotNull Object session) {
			connected.add(session);
		}

		@Override
		public boolean connected(@NotNull Object session) {
			return connected.contains(session);
		}

		@Override
		public void disconnect(@NotNull Object session, @NotNull String reason) {
			assertSame(session, connected.remove(connected.indexOf(session)));
		}
	}
}

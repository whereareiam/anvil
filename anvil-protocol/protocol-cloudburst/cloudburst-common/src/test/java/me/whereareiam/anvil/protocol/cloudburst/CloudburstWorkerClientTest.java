package me.whereareiam.anvil.protocol.cloudburst;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.worker.WorkerClient;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerConnection;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerCredentials;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.api.worker.model.message.WorkerProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.ClientListener;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstClient;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.PacketSubscription;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientPosition;
import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

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
		assertEquals(CloudburstSession.class, worker.sessionType());

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
		ClientListener<CloudburstSession<?, ?, ?>> listener = client.listeners.getFirst();
		CloudburstSession<?, ?, ?> opened = (CloudburstSession<?, ?, ?>) session;

		listener.teleported(opened, 90, 10);
		listener.loggedIn(opened, ClientProfile.builder().name("Renamed").uniqueId(uuid).build());
		listener.disconnected(opened, DisconnectCause.SERVER, "kicked");

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
	private static final class RecordingClient implements CloudburstClient<CloudburstSession<?, ?, ?>> {
		private final List<ClientLogin> logins = new ArrayList<>();
		private final List<ClientListener<CloudburstSession<?, ?, ?>>> listeners = new ArrayList<>();
		private final List<CloudburstSession<?, ?, ?>> connected = new ArrayList<>();

		@Override
		public int protocolNumber() {
			return 2225;
		}

		@Override
		@SuppressWarnings({"unchecked", "rawtypes"})
		public @NotNull Class<CloudburstSession<?, ?, ?>> sessionType() {
			return (Class) CloudburstSession.class;
		}

		@Override
		@SuppressWarnings("unchecked")
		public @NotNull CloudburstSession<?, ?, ?> open(
				@NotNull ClientLogin login,
				@NotNull ClientListener<? super CloudburstSession<?, ?, ?>> listener
		) {
			logins.add(login);
			listeners.add((ClientListener<CloudburstSession<?, ?, ?>>) listener);
			return new IdleSession();
		}

		@Override
		public void connect(@NotNull CloudburstSession<?, ?, ?> session) {
			connected.add(session);
		}

		@Override
		public boolean connected(@NotNull CloudburstSession<?, ?, ?> session) {
			return connected.contains(session);
		}

		@Override
		public void disconnect(@NotNull CloudburstSession<?, ?, ?> session, @NotNull String reason) {
			assertSame(session, connected.remove(connected.indexOf(session)));
		}
	}

	/**
	 * A session the worker client only passes around.
	 */
	private static final class IdleSession implements CloudburstSession<Object, Object, Object> {
		@Override
		public int protocol() {
			return 2193;
		}

		@Override
		public boolean connected() {
			return false;
		}

		@Override
		public @NotNull ClientProfile profile() {
			throw new AssertionError("The worker client reads no profile from its session");
		}

		@Override
		public void send(@NotNull Object packet) {
			throw new AssertionError("The worker client sends no packet");
		}

		@Override
		public <T> @NotNull PacketSubscription subscribe(@NotNull Class<T> type, @NotNull Consumer<? super T> listener) {
			throw new AssertionError("The worker client subscribes to no packet");
		}

		@Override
		public @NotNull Optional<GameStart> start() {
			return Optional.empty();
		}

		@Override
		public boolean serverAuthoritativeMovement() {
			throw new IllegalStateException("not started");
		}

		@Override
		public @NotNull List<Object> itemDefinitions() {
			return List.of();
		}

		@Override
		public long tick() {
			throw new IllegalStateException("not started");
		}

		@Override
		public boolean serverAuthoritativeInventory() {
			throw new IllegalStateException("not started");
		}

		@Override
		public int nextItemStackRequestId() {
			return -1;
		}

		@Override
		public int selectedSlot() {
			return 0;
		}

		@Override
		public @NotNull Optional<Object> item(int containerId, int slot) {
			return Optional.empty();
		}

		@Override
		public void select(int slot) {
			throw new AssertionError("The worker client selects no slot");
		}

		@Override
		public @NotNull ClientPosition position() {
			throw new IllegalStateException("not started");
		}

		@Override
		public @NotNull OptionalInt blockAt(int x, int y, int z) {
			return OptionalInt.empty();
		}
	}
}

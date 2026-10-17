package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.model.player.PlayerConnectionEvent;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientProfile;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerConnection;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerCredentials;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerProfile;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.RecordingClient;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McProtocolPlayerTest {
	private final ByteArrayOutputStream output = new ByteArrayOutputStream();
	private final RecordingClient client = new RecordingClient();
	private final UUID uuid = UUID.randomUUID();

	@Test
	void opensSessionsThroughTheClientWithLoginAndClientSettings() {
		try (McProtocolPlayer player = player("token")) {
			player.connect();
			player.connect();

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
			assertSame(client.sessions().getFirst(), player.nativeSession());
		}
	}

	@Test
	void reportsConnectionEventsAndViewOnlyForTheCurrentSession() {
		try (McProtocolPlayer player = player(null)) {
			player.connect();
			Object first = client.sessions().getFirst();
			player.rejoin();
			Object second = client.sessions().get(1);

			client.listener(first).loggedIn(first, granted("Alice"));
			client.listener(first).teleported(first, 10, 20);
			client.listener(first).disconnected(first, DisconnectCause.SERVER, "replaced");
			client.listener(second).loggedIn(second, granted("Alice_Renamed"));
			client.listener(second).teleported(second, 30, 40);
			client.listener(second).disconnected(second, DisconnectCause.SERVER, "kicked anvil:requested-kick");
			client.listener(second).disconnected(second, DisconnectCause.CONNECTION_LOST, "closed");

			assertEquals(new ViewRotation(30, 40), player.viewRotation());
			assertEquals(List.of("Disconnected by Anvil"), client.disconnects());
			assertEquals(List.of(
					new PlayerConnectionEvent(true, null, null, new PlayerProfile("Alice_Renamed", uuid)),
					new PlayerConnectionEvent(false, "kicked anvil:requested-kick", DisconnectCause.SERVER, null)
			), events());
		}
	}

	@Test
	void prefersTheServersReasonOverAConnectionThatClosedFirst() throws Exception {
		try (McProtocolPlayer player = player(null)) {
			player.connect();
			Object session = client.sessions().getFirst();

			client.listener(session).disconnected(session, DisconnectCause.CONNECTION_LOST, "disconnect.endOfStream");
			client.listener(session).disconnected(session, DisconnectCause.SERVER, "anvil:kicked");
			Thread.sleep(600);

			assertEquals(List.of(new PlayerConnectionEvent(false, "anvil:kicked", DisconnectCause.SERVER, null)), events());
		}
	}

	@Test
	void reportsAClosedConnectionOnceNoReasonFollowsAndARefusedEncryptionAtOnce() throws Exception {
		try (McProtocolPlayer player = player(null)) {
			player.connect();
			Object first = client.sessions().getFirst();
			client.listener(first).disconnected(first, DisconnectCause.CONNECTION_LOST, "disconnect.endOfStream");
			assertEquals(List.of(), events());
			Thread.sleep(600);
			assertEquals(DisconnectCause.CONNECTION_LOST, events().getFirst().getCause());

			player.rejoin();
			Object second = client.sessions().get(1);
			client.listener(second).disconnected(second, DisconnectCause.AUTHENTICATION_REQUIRED, "encryption");
			assertEquals(DisconnectCause.AUTHENTICATION_REQUIRED, events().get(1).getCause());
		}
	}

	@Test
	void refusesTheNativeSessionWhileDisconnected() {
		try (McProtocolPlayer player = player(null)) {
			assertThrows(IllegalStateException.class, player::nativeSession);
			player.connect();
			player.disconnect();
			assertThrows(IllegalStateException.class, player::nativeSession);
		}
	}

	private McProtocolPlayer player(String accessToken) {
		var options = WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder().name("Alice").uniqueId(uuid).build())
				.credentials(accessToken == null ? null : WorkerCredentials.builder().accessToken(accessToken).build())
				.connection(WorkerConnection.builder().host("localhost").port(25565)
						.virtualHost("lobby.example.test").sourceAddress("127.0.0.2").build())
				.build();
		var player = new McProtocolPlayer("player", options, client, SegmentRoots.none(),
				new WorkerMessageWriter(new PrintStream(output, true, StandardCharsets.UTF_8)),
				new WorkerCapabilityRegistry(Object.class, List.of()));
		player.initialize();
		return player;
	}

	private ClientProfile granted(String name) {
		return ClientProfile.builder().name(name).uniqueId(uuid).build();
	}

	private List<PlayerConnectionEvent> events() {
		WorkerMessageCodec codec = new WorkerMessageCodec();
		List<PlayerConnectionEvent> events = new ArrayList<>();
		for (String line : output.toString(StandardCharsets.UTF_8).lines().toList()) {
			WorkerEvent event = (WorkerEvent) codec.decodeMessage(line).orElseThrow();
			assertEquals(PlayerConnectionEvent.CHANGED.getId(), event.getEvent());
			events.add(codec.open(event.getPayload(), PlayerConnectionEvent.class));
		}

		return events;
	}
}

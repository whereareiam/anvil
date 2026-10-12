package me.whereareiam.anvil.protocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.type.DisconnectCause;
import me.whereareiam.anvil.protocol.api.model.NativeWorkerContext;
import me.whereareiam.anvil.protocol.worker.fixture.RecordingClient;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerConnection;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerCredentials;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerEvent;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerProfile;
import me.whereareiam.anvil.protocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.worker.transport.WorkerMessageWriter;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerPlayerTest {
	private final ByteArrayOutputStream output = new ByteArrayOutputStream();
	private final RecordingClient client = new RecordingClient();
	private final UUID uuid = UUID.randomUUID();

	@Test
	void opensOneSessionThroughTheClientWithThePlayersOptions() {
		try (WorkerPlayer player = player("token")) {
			player.connect();
			player.connect();

			WorkerPlayerOptions options = client.opened().getFirst();
			assertEquals(1, client.opened().size());
			assertEquals("Alice", options.getProfile().getName());
			assertEquals(uuid, options.getProfile().getUniqueId());
			assertEquals("token", options.getCredentials().getAccessToken());
			assertEquals("lobby.example.test", options.getConnection().getVirtualHost());
			assertFalse(options.toString().contains("token"));
			assertSame(client.sessions().getFirst(), player.nativeSession());
		}
	}

	@Test
	void reportsConnectionEventsAndViewOnlyForTheCurrentSession() throws Exception {
		try (WorkerPlayer player = player(null)) {
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

			assertEquals(30, player.yaw());
			assertEquals(40, player.pitch());
			assertEquals(List.of("Disconnected by Anvil"), client.disconnects());
			List<JsonNode> events = events();
			assertEquals(2, events.size());
			assertTrue(events.getFirst().path("connected").asBoolean());
			assertEquals("Alice_Renamed", events.getFirst().path("granted").path("username").asText());
			assertEquals(uuid.toString(), events.getFirst().path("granted").path("uniqueId").asText());
			assertTrue(events.get(1).path("granted").isNull());
			assertEquals("kicked anvil:requested-kick", events.get(1).path("reason").asText());
			assertEquals("SERVER", events.get(1).path("cause").asText());
		}
	}

	@Test
	void prefersTheServersReasonOverAConnectionThatClosedFirst() throws Exception {
		try (WorkerPlayer player = player(null)) {
			player.connect();
			Object session = client.sessions().getFirst();

			client.listener(session).disconnected(session, DisconnectCause.CONNECTION_LOST, "disconnect.endOfStream");
			client.listener(session).disconnected(session, DisconnectCause.SERVER, "anvil:kicked");
			Thread.sleep(600);

			List<JsonNode> events = events();
			assertEquals(1, events.size());
			assertEquals("anvil:kicked", events.getFirst().path("reason").asText());
			assertEquals("SERVER", events.getFirst().path("cause").asText());
		}
	}

	@Test
	void reportsAClosedConnectionOnceNoReasonFollowsAndARefusedEncryptionAtOnce() throws Exception {
		try (WorkerPlayer player = player(null)) {
			player.connect();
			Object first = client.sessions().getFirst();
			client.listener(first).disconnected(first, DisconnectCause.CONNECTION_LOST, "disconnect.endOfStream");
			assertEquals(List.of(), events());
			Thread.sleep(600);
			assertEquals("CONNECTION_LOST", events().getFirst().path("cause").asText());

			player.rejoin();
			Object second = client.sessions().get(1);
			client.listener(second).disconnected(second, DisconnectCause.AUTHENTICATION_REQUIRED, "encryption");
			assertEquals("AUTHENTICATION_REQUIRED", events().get(1).path("cause").asText());
		}
	}

	@Test
	void refusesTheNativeSessionWhileDisconnected() {
		try (WorkerPlayer player = player(null)) {
			assertThrows(IllegalStateException.class, player::nativeSession);
			player.connect();
			player.disconnect();
			assertThrows(IllegalStateException.class, player::nativeSession);
		}
	}

	private WorkerPlayer player(String accessToken) {
		var context = NativeWorkerContext.builder().libraryId("probelib").edition(MinecraftEdition.of("java"))
				.version(MinecraftVersion.parse("1.21.11")).protocolNumber(774).nativeSessionType(Object.class).build();
		var options = WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder().name("Alice").uniqueId(uuid).build())
				.credentials(accessToken == null ? null : WorkerCredentials.builder().accessToken(accessToken).build())
				.connection(WorkerConnection.builder().host("localhost").port(25565)
						.virtualHost("lobby.example.test").sourceAddress("127.0.0.2").build())
				.build();
		var player = new WorkerPlayer("player", options, client, SegmentRoots.none(),
				new WorkerMessageWriter(new PrintStream(output, true, StandardCharsets.UTF_8)),
				new WorkerCapabilityRegistry(context, List.of()));
		player.initialize();
		return player;
	}

	private PlayerProfile granted(String name) {
		return new PlayerProfile(name, uuid);
	}

	private List<JsonNode> events() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		WorkerMessageCodec codec = new WorkerMessageCodec();
		List<JsonNode> events = new ArrayList<>();
		for (String line : output.toString(StandardCharsets.UTF_8).lines().toList()) {
			WorkerEvent event = (WorkerEvent) codec.decodeMessage(line).orElseThrow();
			assertEquals("player.connection", event.getEvent());
			events.add(mapper.readTree(codec.messageBytes(event.getPayload())));
		}
		return events;
	}
}

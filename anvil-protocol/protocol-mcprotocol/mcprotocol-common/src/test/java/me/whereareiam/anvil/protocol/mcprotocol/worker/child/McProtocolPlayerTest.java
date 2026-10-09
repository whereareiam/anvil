package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.NativeWorkerContext;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
			assertEquals("Alice", login.getName());
			assertEquals(uuid, login.getUniqueId());
			assertEquals("localhost", login.getHost());
			assertEquals(25565, login.getPort());
			assertEquals("token", login.getAccessToken());
			assertEquals("en_us", login.getLocale());
			assertEquals(8, login.getViewDistance());
			assertFalse(login.toString().contains("token"));
			assertSame(client.sessions().getFirst(), player.nativeSession());
		}
	}

	@Test
	void reportsConnectionEventsAndViewOnlyForTheCurrentSession() throws Exception {
		try (McProtocolPlayer player = player(null)) {
			player.connect();
			Object first = client.sessions().getFirst();
			player.rejoin();
			Object second = client.sessions().get(1);

			client.listener(first).loggedIn(first);
			client.listener(first).teleported(first, 10, 20);
			client.listener(first).disconnected(first, "replaced");
			client.listener(second).loggedIn(second);
			client.listener(second).teleported(second, 30, 40);
			client.listener(second).disconnected(second, "kicked anvil:requested-kick");
			client.listener(second).disconnected(second, "closed");

			assertEquals(30, player.yaw());
			assertEquals(40, player.pitch());
			assertEquals(List.of("Disconnected by Anvil"), client.disconnects());
			List<JsonNode> events = events();
			assertEquals(2, events.size());
			assertTrue(events.getFirst().path("connected").asBoolean());
			assertEquals("kicked anvil:requested-kick", events.get(1).path("reason").asText());
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
		var context = NativeWorkerContext.builder().libraryId("mcprotocol").version(MinecraftVersion.parse("1.21.11"))
				.protocolNumber(774).nativeSessionType(Object.class).build();
		var player = new McProtocolPlayer("player", "Alice", "localhost", 25565, uuid, accessToken, null, client, SegmentRoots.none(),
				new WorkerMessageWriter(new PrintStream(output, true, StandardCharsets.UTF_8)),
				new WorkerCapabilityRegistry(context, List.of()));
		player.initialize();
		return player;
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

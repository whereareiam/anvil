package me.whereareiam.anvil.agent.server.transport;

import me.whereareiam.anvil.agent.api.model.AgentPlayerEvent;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentOperations;
import me.whereareiam.anvil.agent.api.model.AgentProfile;
import me.whereareiam.anvil.agent.api.model.transport.AgentPingResponse;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsRequest;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsResponse;
import me.whereareiam.anvil.agent.api.type.AgentPlayerEventType;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.exception.AgentUnavailableException;
import me.whereareiam.anvil.agent.client.transport.connection.JsonLineAgentConnectionProvider;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.transport.AgentServerProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentTransportLifecycleTest {
	@Test
	void discoversThePackagedProviderWithoutTheThreadContextLoader() {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(null);
			assertInstanceOf(JsonLineAgentServerProvider.class, AgentServerProvider.discover());
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	@Test
	void roundTripsRequestsUntilTheClientCloses() throws Exception {
		int port = freePort();
		try (PlatformAgentServer server = new PlatformAgentServer(port, "token", new TestPlatform(), ignored -> {});
		     AgentClient client = new JsonLineAgentConnectionProvider().connect(port, "token", Duration.ofSeconds(2))) {
			assertTrue(client.available());
			AgentPingResponse ping = client.request(AgentOperations.PING, Map.of());
			assertEquals("test", ping.getPlatform());
			assertEquals("proxy", ping.getRole());
			assertTrue(client.request(AgentOperations.COMMAND, AgentCommandRequest.builder().command("accepted").build()).isAccepted());
			assertFalse(client.request(AgentOperations.COMMAND, AgentCommandRequest.builder().command("rejected").build()).isAccepted());

			AgentProfile alice = AgentProfile.builder().username("Alice").uniqueId(UUID.randomUUID()).build();
			server.playerEvents().joined(alice, "lobby");
			server.playerEvents().left(alice, "lobby");
			AgentPlayerEventsResponse events = client.request(AgentOperations.PLAYER_EVENTS, AgentPlayerEventsRequest.builder().after(0).build());
			assertEquals(List.of(AgentPlayerEventType.JOINED, AgentPlayerEventType.LEFT), events.getEvents().stream().map(AgentPlayerEvent::getType).toList());
			assertEquals(alice, events.getEvents().getFirst().getProfile());
			assertEquals("lobby", events.getEvents().getFirst().getServer());
			AgentPlayerEventsResponse later = client.request(AgentOperations.PLAYER_EVENTS, AgentPlayerEventsRequest.builder().after(1).build());
			assertEquals(events.getGeneration(), later.getGeneration());
			assertEquals(1, later.getEvents().size());
			client.close();
			assertFalse(client.available());
			assertThrows(AgentUnavailableException.class, () -> client.request(AgentOperations.PING, Map.of()));
		}
	}

	@Test
	void closesAcceptedSocketsWhenThePlatformStops() throws Exception {
		int port = freePort();
		try (PlatformAgentServer server = new PlatformAgentServer(port, "token", new TestPlatform(), ignored -> {});
		     Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
			socket.setSoTimeout(2000);
			socket.getOutputStream().write(
					"{\"id\":1,\"token\":\"token\",\"operation\":\"ping\",\"arguments\":{}}\n"
							.getBytes(StandardCharsets.UTF_8));
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			assertNotNull(reader.readLine());
			server.close();
			assertNull(reader.readLine());
		}
	}

	private int freePort() throws Exception {
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			return socket.getLocalPort();
		}
	}

	private static final class TestPlatform implements PlatformAgent {
		@Override
		public @NotNull AgentInfo info() {
			return AgentInfo.builder().platform("test").version("1").role(AgentRole.PROXY).build();
		}

		@Override
		public boolean executeCommand(@NotNull String command) {
			return command.equals("accepted");
		}
	}
}

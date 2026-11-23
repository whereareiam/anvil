package me.whereareiam.anvil.agent.client.transport.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentPlayerEvent;
import me.whereareiam.anvil.agent.api.model.AgentProfile;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsRequest;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsResponse;
import me.whereareiam.anvil.agent.api.type.AgentPlayerEventType;
import me.whereareiam.anvil.agent.client.api.exception.AgentUnavailableException;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLineAgentConnectionTest {
	@Test
	void writesArgumentsAndReadsResultsUnderTheNamesOfTheirFields() throws Exception {
		AgentPlayerEventsResponse expected = AgentPlayerEventsResponse.builder()
				.generation("first")
				.proxy(true)
				.truncated(false)
				.event(AgentPlayerEvent.builder()
						.position(1)
						.at(5)
						.type(AgentPlayerEventType.JOINED)
						.profile(AgentProfile.builder().username("Alice").uniqueId(new UUID(0, 1)).build())
						.server("lobby")
						.build())
				.build();
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		     var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var remote = executor.submit(() -> {
				try (var socket = server.accept()) {
					socket.setSoTimeout(2000);
					var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
					JsonNode request = new ObjectMapper().readTree(reader.readLine());
					socket.getOutputStream().write(("{\"id\":" + request.path("id").asLong() + ",\"success\":true,\"result\":"
							+ "{\"generation\":\"first\",\"proxy\":true,\"truncated\":false,\"events\":[{\"position\":1,"
							+ "\"at\":5,\"type\":\"JOINED\",\"profile\":{\"username\":\"Alice\",\"uniqueId\":"
							+ "\"00000000-0000-0000-0000-000000000001\"},\"server\":\"lobby\"}]}}\n").getBytes(StandardCharsets.UTF_8));
					return request.path("arguments").toString();
				}
			});
			try (var connection = new JsonLineAgentConnection(server.getLocalPort(), "token")) {
				assertEquals(expected, connection.request("player-events", AgentPlayerEventsRequest.builder().after(4).build(),
						AgentPlayerEventsResponse.class));
			}
			assertEquals("{\"after\":4}", remote.get(3, TimeUnit.SECONDS));
		}
	}

	@Test
	void rejectsAnUnrelatedResponseAndClosesTheConnection() throws Exception {
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
			 var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var remote = executor.submit(() -> {
				try (var socket = server.accept()) {
					socket.setSoTimeout(2000);
					var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
					assertNotNull(reader.readLine());
					socket.getOutputStream().write("{\"id\":999,\"success\":true,\"result\":{}}\n"
							.getBytes(StandardCharsets.UTF_8));
					assertNull(reader.readLine());
				}
				return null;
			});
			try (var connection = new JsonLineAgentConnection(server.getLocalPort(), "token")) {
				AgentException failure = assertThrows(AgentException.class,
						() -> connection.request("ping", Map.of(), Map.class));
				assertTrue(failure.getMessage().contains("Mismatched response"));
				assertFalse(connection.available());
				assertThrows(AgentUnavailableException.class, () -> connection.request("ping", Map.of(), Map.class));
			}
			remote.get(3, TimeUnit.SECONDS);
		}
	}

	@Test
	void reportsUnexpectedDisconnectionAsCommunicationFailureBeforeBecomingUnavailable() throws Exception {
		try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		     var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var remote = executor.submit(() -> {
				try (var socket = server.accept()) {
					socket.setSoTimeout(2000);
					var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
					assertNotNull(reader.readLine());
				}
				return null;
			});
			try (var connection = new JsonLineAgentConnection(server.getLocalPort(), "token")) {
				assertTrue(connection.available());
				AgentException failure = assertThrows(AgentException.class,
						() -> connection.request("ping", Map.of(), Map.class));
				assertFalse(failure instanceof AgentUnavailableException);
				assertFalse(connection.available());
				assertThrows(AgentUnavailableException.class, () -> connection.request("ping", Map.of(), Map.class));
			}
			remote.get(3, TimeUnit.SECONDS);
		}
	}
}

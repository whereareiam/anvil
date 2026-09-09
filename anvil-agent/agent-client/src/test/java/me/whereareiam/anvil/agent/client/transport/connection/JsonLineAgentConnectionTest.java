package me.whereareiam.anvil.agent.client.transport.connection;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JsonLineAgentConnectionTest {
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
			}
			remote.get(3, TimeUnit.SECONDS);
		}
	}
}

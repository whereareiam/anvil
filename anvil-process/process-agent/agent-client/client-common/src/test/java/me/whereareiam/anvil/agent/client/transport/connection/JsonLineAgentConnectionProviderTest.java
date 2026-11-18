package me.whereareiam.anvil.agent.client.transport.connection;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLineAgentConnectionProviderTest {
	@Test
	void reportsHowLongItWaitedAndWhyTheLastAttemptFailedWhenNoAgentListens() throws Exception {
		int port;
		try (ServerSocket released = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			port = released.getLocalPort();
		}

		AgentException failure = assertThrows(AgentException.class,
				() -> new JsonLineAgentConnectionProvider().connect(port, "token", Duration.ofMillis(300)));

		assertTrue(failure.getMessage().contains("loopback:" + port + " did not become ready within PT0.3S"), failure.getMessage());
		assertTrue(failure.getMessage().matches("(?s).*\\(\\d+ attempts\\); last attempt: Could not connect.*"), failure.getMessage());
		assertNotNull(failure.getCause());
	}
}

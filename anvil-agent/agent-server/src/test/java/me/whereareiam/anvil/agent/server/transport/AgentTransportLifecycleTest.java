package me.whereareiam.anvil.agent.server.transport;

import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgentTransportLifecycleTest {
	private static final UUID ALICE = new UUID(0, 1);
	private static final UUID MISSING = new UUID(0, 2);

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
	void roundTripsAbsentAndProxyIdentitiesAndCommandResults() throws Exception {
		int port = freePort();
		try (PlatformAgentServer ignored1 = new PlatformAgentServer(port, "token", new TestPlatform(), ignored -> {});
		     AgentClient client = new JsonLineAgentConnectionProvider().connect(port, "token", Duration.ofSeconds(2))) {
			assertTrue(client.available());
			assertTrue(client.identity(MISSING).isEmpty());
			AgentIdentity identity = client.identity(ALICE).orElseThrow();
			assertEquals("Alice", identity.getUsername());
			assertEquals(ALICE, identity.getUniqueId());
			ProxyLocation location = assertInstanceOf(ProxyLocation.class, identity.getLocation());
			assertEquals("proxy", location.getProxy());
			assertEquals("server", location.getConnectedServer());
			assertTrue(client.executeCommand("accepted"));
			assertFalse(client.executeCommand("rejected"));
			client.close();
			assertFalse(client.available());
			assertThrows(AgentUnavailableException.class, () -> client.identity(MISSING));
			assertThrows(AgentUnavailableException.class, () -> client.executeCommand("accepted"));
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
		public @NotNull Optional<AgentIdentity> identity(@NotNull UUID uniqueId) {
			if (!uniqueId.equals(ALICE))
				return Optional.empty();
			return Optional.of(AgentIdentity.builder().username("Alice").uniqueId(uniqueId)
					.location(ProxyLocation.builder().proxy("proxy").connectedServer("server").build()).build());
		}

		@Override
		public boolean executeCommand(@NotNull String command) {
			return command.equals("accepted");
		}
	}
}

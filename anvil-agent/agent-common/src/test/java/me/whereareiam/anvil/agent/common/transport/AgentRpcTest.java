package me.whereareiam.anvil.agent.common.transport;

import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.common.transport.connection.JsonLineAgentConnectionProvider;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentRpcTest {
	@Test
	void authenticatesJsonLinesRequestsOnLoopback() throws IOException {
		int port = freePort();
		JsonLineAgentConnectionProvider provider = new JsonLineAgentConnectionProvider();
		try (PlatformAgentServer server = new PlatformAgentServer(port, "correct", new PlatformAgent() {
			@Override
			public @NonNull AgentInfo info() {
				return AgentInfo.builder().platform("test").version("1").role(AgentRole.SERVER).build();
			}

			@Override
			public @NonNull Optional<AgentIdentity> identity(@NonNull String username) {
				return Optional.of(AgentIdentity.builder()
						.username(username)
						.uniqueId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
						.location(ServerLocation.builder().server("server").build())
						.build());
			}

			@Override
			public boolean executeCommand(@NonNull String command) {
				return true;
			}
		}, ignored -> { });
				AgentClient client = provider.connect(port, "correct", Duration.ofSeconds(2))) {
			assertEquals("hello", client.identity("hello").orElseThrow().getUsername());
		}

		int deniedPort = freePort();
		try (PlatformAgentServer server = new PlatformAgentServer(deniedPort, "correct", new PlatformAgent() {
			@Override
			public @NonNull AgentInfo info() {
				return AgentInfo.builder().platform("test").version("1").role(AgentRole.SERVER).build();
			}

			@Override
			public @NonNull Optional<AgentIdentity> identity(@NonNull String username) {
				return Optional.empty();
			}

			@Override
			public boolean executeCommand(@NonNull String command) {
				return false;
			}
		}, ignored -> { })) {
			assertThrows(AgentException.class,
					() -> provider.connect(deniedPort, "wrong", Duration.ofMillis(250)));
		}
	}

	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}

package me.whereareiam.anvil.agent.server.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentProfile;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformAgentRequestDispatcherTest {
	private final ObjectMapper json = new ObjectMapper();

	@Test
	void writesResultsUnderTheNamesOfTheirFields() throws Exception {
		PlatformAgentRequestDispatcher dispatcher = new PlatformAgentRequestDispatcher(new PlatformAgent() {
			@Override
			public @NotNull AgentInfo info() {
				return AgentInfo.builder().platform("test").version("1").role(AgentRole.PROXY).build();
			}

			@Override
			public boolean executeCommand(@NotNull String command) {
				return command.equals("accepted");
			}
		});
		dispatcher.playerEvents().joined(AgentProfile.builder().username("Alice").uniqueId(new UUID(0, 1)).build(), "lobby");
		// The generation and the time of each event differ from run to run.
		JsonNode events = dispatcher.handle("player-events", json.readTree("{\"after\":0}"));
		((ObjectNode) events).put("generation", "generation");
		((ObjectNode) events.path("events").get(0)).put("at", 0);

		assertEquals("{\"platform\":\"test\",\"version\":\"1\",\"role\":\"proxy\"}",
				dispatcher.handle("ping", json.createObjectNode()).toString());
		assertEquals("{\"accepted\":true}",
				dispatcher.handle("command", json.readTree("{\"command\":\"accepted\"}")).toString());
		assertEquals("{\"generation\":\"generation\",\"proxy\":true,\"truncated\":false,\"events\":[{\"position\":1,"
				+ "\"at\":0,\"type\":\"JOINED\",\"profile\":{\"username\":\"Alice\",\"uniqueId\":\"00000000-0000-0000-0000-000000000001\"},"
				+ "\"server\":\"lobby\"}]}",
				events.toString());
	}
}

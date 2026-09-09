package me.whereareiam.anvil.agent.client;

import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioAgentDirectoryTest {
	@Test
	void registersOneStableHandlePerProcessAndReturnsImmutableSnapshots() {
		ScenarioAgentDirectory directory = new ScenarioAgentDirectory();
		ProcessAgentClient server = directory.register("server");
		Map<String, AgentClient> snapshot = directory.agents();
		ProcessAgentClient proxy = directory.register("proxy");

		assertSame(server, directory.register("server"));
		assertSame(server, directory.require("server"));
		assertSame(proxy, directory.require("proxy"));
		assertEquals(Map.of("server", server), snapshot);
		assertThrows(UnsupportedOperationException.class, () -> snapshot.put("other", server));
		assertThrows(AgentException.class, () -> server.executeCommand("before-start"));
	}
}

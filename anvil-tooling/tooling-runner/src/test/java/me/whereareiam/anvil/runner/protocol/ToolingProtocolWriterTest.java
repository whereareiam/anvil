package me.whereareiam.anvil.runner.protocol;

import me.whereareiam.anvil.tooling.api.model.PlayerDescriptor;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioLaunchRequest;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolingProtocolWriterTest {
	@Test
	void writesPayloadsUnderTheNamesOfTheirFields() {
		StringWriter output = new StringWriter();
		new ToolingProtocolWriter(new PrintWriter(output)).snapshot(SessionSnapshot.builder()
				.sessionId("session")
				.definition("com.example.LobbyScenario")
				.scenario("lobby")
				.state(SessionState.RUNNING)
				.setupComplete(true)
				.processes(List.of(ProcessSnapshot.builder()
						.name("lobby")
						.executionId(new UUID(0, 1))
						.displayName("Lobby")
						.state(ProcessState.READY)
						.host("127.0.0.1")
						.port(25565)
						.workDirectory("/work/lobby")
						.build()))
				.players(List.of(PlayerDescriptor.builder().name("alice").displayName("Alice").build()))
				.build());

		assertEquals("{\"type\":\"snapshot\",\"snapshot\":{\"sessionId\":\"session\",\"definition\":\"com.example.LobbyScenario\","
				+ "\"scenario\":\"lobby\",\"entrypoint\":null,\"displayName\":null,\"state\":\"RUNNING\","
				+ "\"setupComplete\":true,\"processes\":[{\"name\":\"lobby\",\"executionId\":\"00000000-0000-0000-0000-000000000001\","
				+ "\"displayName\":\"Lobby\",\"state\":\"READY\",\"host\":\"127.0.0.1\",\"port\":25565,"
				+ "\"workDirectory\":\"/work/lobby\"}],\"players\":[{\"name\":\"alice\",\"displayName\":\"Alice\"}],"
				+ "\"actions\":[],\"observations\":[],\"failure\":null}}",
				output.toString().strip());
	}

	@Test
	void readsPayloadsUnderTheNamesOfTheirFields() throws Exception {
		ToolingRequestReader reader = new ToolingRequestReader();
		ToolingRequestReader.Request request = reader.read(
				"{\"id\":\"1\",\"operation\":\"start\",\"definition\":\"com.example.LobbyScenario\",\"target\":\"lobby\"}");

		assertEquals(ScenarioLaunchRequest.builder().definition("com.example.LobbyScenario").target("lobby").build(),
				reader.decode(request, ScenarioLaunchRequest.class));
	}
}

package me.whereareiam.anvil.integration.intellij.tooling.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import me.whereareiam.anvil.tooling.api.ScenarioOperations;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioLaunchRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolingMessageCodecTest {
	private final ToolingMessageCodec codec = new ToolingMessageCodec();

	@Test
	void genericRequestEncodingPreservesProtocolSevenShape() throws Exception {
		var request = ScenarioLaunchRequest.builder()
				.definition("example.Definition").scenario("registration").target("lobby").build();
		var payload = new ObjectMapper().readTree(codec.write("request-1", ScenarioOperations.START, request));

		assertEquals("start", payload.path("operation").asText());
		assertEquals("request-1", payload.path("id").asText());
		assertNotEquals("start", payload.path("id").asText());
		assertEquals("example.Definition", payload.path("definition").asText());
		assertEquals("registration", payload.path("scenario").asText());
		assertEquals("lobby", payload.path("target").asText());
		var whole = new ObjectMapper().readTree(codec.write("whole", ScenarioOperations.START,
				ScenarioLaunchRequest.builder().definition("example.Definition").scenario("registration").build()));
		assertFalse(whole.has("target"));

	}

	@Test
	void genericEncodingRequiresDeclaredPayloadAndLeavesVoidOperationsEmpty() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> codec.write("missing", ScenarioOperations.START, null));
		var empty = new ObjectMapper().readTree(codec.write("empty", ScenarioOperations.DISCOVER, null));
		assertEquals(2, empty.size());
		assertEquals("scenarios", empty.path("operation").asText());
	}

	@Test
	void handshakeRequiresAnExplicitSupportedIntegerVersion() throws Exception {
		assertInstanceOf(ToolingMessageCodec.Ready.class,
				codec.read("{\"type\":\"ready\",\"protocolVersion\":" + ToolingSession.PROTOCOL_VERSION + "}"));
		for (String value : List.of("null", "\"7\"", "7.0", "6", "true", "0", "99999999999999999"))
			assertThrows(IOException.class, () -> codec.read("{\"type\":\"ready\",\"protocolVersion\":" + value + "}"));
		assertThrows(IOException.class, () -> codec.read("{\"type\":\"ready\"}"));
	}

	@Test
	void protocolSixExplainsThatBothPeersNeedUpdating() {
		IOException failure = assertThrows(IOException.class,
				() -> codec.read("{\"type\":\"ready\",\"protocolVersion\":6}"));
		assertEquals("Unsupported Anvil tooling protocol. Update the project and IDE plugin together.", failure.getMessage());
	}

	@Test
	void responseAndEventEnvelopesRejectMalformedFields() {
		for (String message : List.of("", "[]", "{}",
				"{\"type\":\"response\",\"id\":\"\",\"success\":true,\"result\":{}}",
				"{\"type\":\"response\",\"id\":\"request-1\"}",
				"{\"type\":\"response\",\"id\":\"request-1\",\"success\":\"true\"}",
				"{\"type\":\"response\",\"id\":\"request-1\",\"success\":true}",
				"{\"type\":\"response\",\"id\":\"request-1\",\"success\":false,\"error\":{}}"))
			assertThrows(IOException.class, () -> codec.read(message));
	}
}

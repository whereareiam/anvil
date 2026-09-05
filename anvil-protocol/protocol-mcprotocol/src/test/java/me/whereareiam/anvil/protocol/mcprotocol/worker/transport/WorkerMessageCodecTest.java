package me.whereareiam.anvil.protocol.mcprotocol.worker.transport;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerReady;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerRequest;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkerMessageCodecTest {
	private final WorkerMessageCodec codec = new WorkerMessageCodec();

	@Test
	void roundTripsEveryEnvelopeWithoutTreatingDiagnosticsAsMessages() {
		var result = JsonNodeFactory.instance.objectNode().put("message", "line\nbreak");
		var response = WorkerResponse.builder().id(1).success(true).result(result).build();
		var event = WorkerEvent.builder().event("example.changed").player("player").payload(result).build();
		var ready = WorkerReady.builder().protocol(774).capabilities(Set.of("example.capability")).build();
		assertEquals(response, codec.decodeMessage(codec.encodeMessage(response)).orElseThrow());
		assertEquals(event, codec.decodeMessage(codec.encodeMessage(event)).orElseThrow());
		assertEquals(ready, codec.decodeMessage(codec.encodeMessage(ready)).orElseThrow());
		assertTrue(codec.decodeMessage("ordinary worker diagnostic").isEmpty());
	}

	@Test
	void carriesCredentialsPrivatelyWithoutIncludingThemInModelDiagnostics() {
		var options = WorkerPlayerOptions.builder().name("Alice").uuid(UUID.randomUUID())
				.host("localhost").port(25565).accessToken("private-token").build();
		var request = WorkerRequest.builder().id(1).operation(WorkerControlOperation.CREATE_PLAYER.getWireName())
				.player("player").arguments(codec.payload(options)).build();
		var decoded = codec.decodeRequest(codec.encodeRequest(request));
		assertEquals(options, codec.decodePayload(decoded.getArguments(), WorkerPlayerOptions.class));
		assertFalse(options.toString().contains("private-token"));
		assertFalse(request.toString().contains("private-token"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"ANVIL:null", "ANVIL:[]", "ANVIL:{}", "ANVIL:{\"id\":1}",
			"ANVIL:{\"event\":\"ready\",\"protocol\":774}", "ANVIL:{\"event\":\"example.event\"}"
	})
	void rejectsMalformedWorkerEnvelopes(String line) {
		assertThrows(IllegalArgumentException.class, () -> codec.decodeMessage(line));
	}

	@Test
	void neverIncludesMalformedPayloadsOrParserCausesInFailures() {
		var failure = assertThrows(IllegalArgumentException.class, () -> codec.decodeRequest(
				"{\"id\":1,\"operation\":\"create\",\"arguments\":{\"accessToken\":\"private-token\"},BROKEN}"
		));
		assertEquals("Invalid worker message", failure.getMessage());
		assertNull(failure.getCause());
		assertThrows(IllegalArgumentException.class, () -> codec.decodeRequest("{}"));
	}
}

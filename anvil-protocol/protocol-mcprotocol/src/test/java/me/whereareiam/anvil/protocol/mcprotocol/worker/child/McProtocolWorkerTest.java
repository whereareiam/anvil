package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterRegistry;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerRequest;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class McProtocolWorkerTest {
	private final WorkerMessageCodec codec = new WorkerMessageCodec();

	@Test
	void dispatchesPlayerLifecycleAndExternalOperationsUntilShutdown() throws Exception {
		UUID identity = UUID.randomUUID();
		var options = WorkerPlayerOptions.builder().name("Alice").uuid(identity).host("localhost").port(25565).build();
		var empty = JsonNodeFactory.instance.objectNode();
		String input = String.join("\n",
				request(1, "create", codec.payload(options)),
				request(2, "example.identity", empty),
				request(3, "create", codec.payload(options)),
				request(4, "destroy", empty),
				request(5, "example.identity", empty),
				request(6, "shutdown", empty),
				request(7, "create", codec.payload(options))
		);
		var output = new ByteArrayOutputStream();
		var writer = new WorkerMessageWriter(new PrintStream(output, true, StandardCharsets.UTF_8));
		var registry = new WorkerCapabilityRegistry(774, List.of(identityAdapter()));
		try (var worker = new McProtocolWorker(registry, writer)) {
			worker.run(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
		}
		var responses = output.toString(StandardCharsets.UTF_8).lines()
				.map(line -> assertInstanceOf(WorkerResponse.class, codec.decodeMessage(line).orElseThrow())).toList();
		assertEquals(6, responses.size());
		assertTrue(responses.getFirst().isSuccess());
		assertEquals(identity.toString(), responses.get(1).getResult().path("uuid").asText());
		assertTrue(responses.get(2).getError().contains("already exists"));
		assertTrue(responses.get(3).isSuccess());
		assertTrue(responses.get(4).getError().contains("Unknown worker player"));
		assertTrue(responses.get(5).isSuccess());
	}

	private String request(long id, String operation, JsonNode arguments) {
		return codec.encodeRequest(WorkerRequest.builder().id(id).operation(operation)
				.player("player").arguments(arguments).build());
	}

	private ProtocolCapabilityAdapter identityAdapter() {
		return new ProtocolCapabilityAdapter() {
			@Override
			public String id() {
				return "example.identity";
			}

			@Override
			public void install(ProtocolCapabilityAdapterRegistry registry) {
				registry.operation("example.identity", (player, arguments) ->
						player.mapper().createObjectNode().put("uuid", player.uuid().toString()));
			}
		};
	}
}

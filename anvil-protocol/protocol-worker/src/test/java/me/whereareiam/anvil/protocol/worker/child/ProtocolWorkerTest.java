package me.whereareiam.anvil.protocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.NativeWorkerContext;
import me.whereareiam.anvil.protocol.api.worker.NativeBinding;
import me.whereareiam.anvil.protocol.api.worker.NativeOperations;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerExtension;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerProvider;
import me.whereareiam.anvil.protocol.worker.fixture.RecordingClient;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerConnection;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerCapabilities;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerProfile;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerRequest;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerResponse;
import me.whereareiam.anvil.protocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.worker.transport.WorkerMessageWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolWorkerTest {
	private final WorkerMessageCodec codec = new WorkerMessageCodec();

	@Test
	void dispatchesPlayerLifecycleAndExternalOperationsUntilShutdown() throws Exception {
		UUID identity = UUID.randomUUID();
		var options = WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder().name("Alice").uniqueId(identity).build())
				.connection(WorkerConnection.builder().host("localhost").port(25565).build())
				.build();
		var empty = JsonNodeFactory.instance.objectNode();
		String input = String.join("\n",
				request(1, "create", codec.payload(options)),
				request(2, "example.identity", WorkerMessageCodec.message(new byte[0])),
				request(3, "create", codec.payload(options)),
				request(4, "destroy", empty),
				request(5, "example.identity", WorkerMessageCodec.message(new byte[0])),
				request(6, "shutdown", empty),
				request(7, "create", codec.payload(options))
		);
		var output = new ByteArrayOutputStream();
		var writer = new WorkerMessageWriter(new PrintStream(output, true, StandardCharsets.UTF_8));
		var context = NativeWorkerContext.builder().libraryId("probelib").version(MinecraftVersion.parse("1.21.11"))
				.protocolNumber(774).nativeSessionType(Object.class).build();
		var registry = new WorkerCapabilityRegistry(context, List.of(identityAdapter()));
		try (var worker = new ProtocolWorker(new RecordingClient(), SegmentRoots.none(), registry, writer)) {
			worker.run(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
		}
		var responses = output.toString(StandardCharsets.UTF_8).lines()
				.map(line -> assertInstanceOf(WorkerResponse.class, codec.decodeMessage(line).orElseThrow())).toList();
		assertEquals(6, responses.size());
		assertTrue(responses.getFirst().isSuccess());
		assertEquals(Set.of("example.identity"), codec.decodePayload(responses.getFirst().getResult(), WorkerPlayerCapabilities.class).getCapabilities());
		assertEquals(identity.toString(), new String(codec.messageBytes(responses.get(1).getResult()), StandardCharsets.UTF_8));
		assertTrue(responses.get(2).getError().contains("already exists"));
		assertTrue(responses.get(3).isSuccess());
		assertTrue(responses.get(4).getError().contains("Unknown worker player"));
		assertTrue(responses.get(5).isSuccess());
	}

	private String request(long id, String operation, JsonNode arguments) {
		return codec.encodeRequest(WorkerRequest.builder().id(id).operation(operation)
				.player("player").arguments(arguments).build());
	}

	private NativeWorkerProvider identityAdapter() {
		return new NativeWorkerProvider() {
			public String id() { return "example.identity"; }
			public NativeWorkerExtension<Object> create(NativeWorkerContext context) {
				assertEquals(Object.class, context.getNativeSessionType());
				return new NativeWorkerExtension<>() {
					public Set<String> capabilities() { return Set.of("example.identity"); }
					public NativeBinding bind(NativePlayer<Object> player, NativeOperations operations) {
						operations.register("example.identity", ignored -> player.uniqueId().toString().getBytes(StandardCharsets.UTF_8));
						return () -> { };
					}
				};
			}
		};
	}
}

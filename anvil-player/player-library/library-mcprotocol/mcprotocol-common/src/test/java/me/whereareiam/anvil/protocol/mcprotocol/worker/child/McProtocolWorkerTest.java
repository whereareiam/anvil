package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerConnection;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerCapabilities;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerProfile;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerRequest;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.RecordingClient;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import me.whereareiam.anvil.protocol.worker.WorkerExtensions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class McProtocolWorkerTest {
	private static final OperationDescriptor<Void, String> IDENTITY = new OperationDescriptor<>("example.identity", Void.class, String.class);
	private static final OperationDescriptor<String, String> GREET = new OperationDescriptor<>("example.greet", String.class, String.class);

	private final WorkerMessageCodec codec = new WorkerMessageCodec();

	@Test
	void dispatchesPlayerLifecycleAndTypedExtensionOperationsUntilShutdown() throws Exception {
		UUID identity = UUID.randomUUID();
		var options = WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder().name("Alice").uniqueId(identity).build())
				.connection(WorkerConnection.builder().host("localhost").port(25565).build())
				.build();
		var empty = JsonNodeFactory.instance.objectNode();
		String input = String.join("\n",
				request(1, "create", codec.payload(options)),
				request(2, IDENTITY.getId(), codec.envelope(null)),
				request(3, GREET.getId(), codec.envelope("Bob")),
				request(4, GREET.getId(), codec.envelope(null)),
				request(5, "create", codec.payload(options)),
				request(6, "destroy", empty),
				request(7, IDENTITY.getId(), codec.envelope(null)),
				request(8, "shutdown", empty),
				request(9, "create", codec.payload(options))
		);
		var output = new ByteArrayOutputStream();
		var writer = new WorkerMessageWriter(new PrintStream(output, true, StandardCharsets.UTF_8));
		var registry = new WorkerExtensions(
				McProtocolClient.LIBRARY_ID,
				Object.class,
				WorkerControlOperation.wireNames(),
				List.of(new IdentityExtension())
		);
		try (var worker = new McProtocolWorker(new RecordingClient(), SegmentRoots.none(), registry, writer)) {
			worker.run(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
		}

		var responses = output.toString(StandardCharsets.UTF_8).lines()
				.map(line -> assertInstanceOf(WorkerResponse.class, codec.decodeMessage(line).orElseThrow())).toList();
		assertEquals(8, responses.size());
		assertTrue(responses.getFirst().isSuccess());
		assertEquals(Set.of("example.identity"), codec.decodePayload(responses.getFirst().getResult(), WorkerPlayerCapabilities.class).getCapabilities());
		assertEquals(identity.toString(), codec.open(responses.get(1).getResult(), String.class));
		assertEquals("Alice greets Bob", codec.open(responses.get(2).getResult(), String.class));
		assertEquals("IllegalArgumentException: Invalid player message for java.lang.String", responses.get(3).getError());
		assertTrue(responses.get(4).getError().contains("already exists"));
		assertTrue(responses.get(5).isSuccess());
		assertTrue(responses.get(6).getError().contains("Unknown worker player"));
		assertTrue(responses.get(7).isSuccess());
	}

	private String request(long id, String operation, JsonNode arguments) {
		return codec.encodeRequest(WorkerRequest.builder()
				.id(id)
				.operation(operation)
				.player("player")
				.arguments(arguments)
				.build());
	}

	private static final class IdentityExtension implements WorkerExtension<Object> {
		public @NotNull String id() { return "example.identity"; }
		public @NotNull Optional<String> libraryId() { return Optional.of("mcprotocol"); }
		public @NotNull Class<Object> nativeSessionType() { return Object.class; }

		public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
			operations.register(IDENTITY, ignored -> player.uniqueId().toString());
			operations.register(GREET, name -> player.name() + " greets " + name);
			return () -> { };
		}
	}
}

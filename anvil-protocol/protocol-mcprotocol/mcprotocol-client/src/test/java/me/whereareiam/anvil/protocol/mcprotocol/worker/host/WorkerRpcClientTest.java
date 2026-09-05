package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class WorkerRpcClientTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	@Test
	void correlatesResponsesAndPermanentlyFailsOutstandingRequests() throws Exception {
		try (var input = new PipedInputStream();
			 var output = new PipedOutputStream(input);
			 var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
			 var executor = Executors.newSingleThreadExecutor()) {
			WorkerRpcClient client = new WorkerRpcClient(output);
			WorkerMessageCodec codec = new WorkerMessageCodec();
			var arguments = JsonNodeFactory.instance.objectNode().put("value", 7);
			var request = executor.submit(() -> client.request("example.action", "player", arguments, TIMEOUT));
			var sent = codec.decodeRequest(reader.readLine());
			assertEquals("player", sent.getPlayer());
			assertEquals(7, sent.getArguments().path("value").asInt());
			client.accept(WorkerResponse.builder().id(sent.getId()).success(true)
					.result(JsonNodeFactory.instance.objectNode().put("accepted", true)).build());
			assertTrue(request.get().path("accepted").asBoolean());

			var pending = executor.submit(() -> client.request("example.pending", "player", arguments, TIMEOUT));
			assertNotNull(reader.readLine());
			client.fail(new IllegalStateException("worker stopped"));
			var failure = assertThrows(ExecutionException.class, pending::get);
			assertTrue(failure.getCause().getMessage().contains("example.pending"));
			assertThrows(IllegalStateException.class, () -> client.request("example.later", "player", arguments, TIMEOUT));
		}
	}

	@Test
	void preservesInterruptionWhileWaitingForAResponse() {
		WorkerRpcClient client = new WorkerRpcClient(new ByteArrayOutputStream());
		Thread.currentThread().interrupt();
		try {
			assertThrows(IllegalStateException.class, () -> client.request(
					"example.wait", null, JsonNodeFactory.instance.objectNode(), TIMEOUT
			));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void ignoresLateRepliesAfterTimeout() {
		WorkerRpcClient client = new WorkerRpcClient(new ByteArrayOutputStream());
		assertThrows(IllegalStateException.class, () -> client.request(
				"example.timeout", null, JsonNodeFactory.instance.objectNode(), Duration.ofMillis(1)
		));
		assertDoesNotThrow(() -> client.accept(WorkerResponse.builder().id(1).success(true)
				.result(JsonNodeFactory.instance.objectNode()).build()));
	}
}

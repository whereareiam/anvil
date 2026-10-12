package me.whereareiam.anvil.integration.intellij.tooling.protocol;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import me.whereareiam.anvil.integration.intellij.tooling.process.ControlledToolingProcess;
import me.whereareiam.anvil.integration.intellij.tooling.process.ToolingConnection;
import me.whereareiam.anvil.tooling.api.ProcessOperations;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import me.whereareiam.anvil.tooling.api.model.process.console.ConsoleCommandRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolingClientTest {
	@Test
	void concurrentRequestsAreCorrelatedIndependentlyOfReplyOrder() throws Exception {
		try (var fixture = new Fixture()) {
			var first = fixture.client.request(ProcessOperations.CONSOLE, ConsoleCommandRequest.builder().target("one").text("first").build());
			var second = fixture.client.request(ProcessOperations.CONSOLE, ConsoleCommandRequest.builder().target("two").text("second").build());
			assertTrue(first.submitted().join());
			assertTrue(second.submitted().join());
			assertFalse(first.result().isDone());
			assertNotEquals(fixture.process.getRequests().get(0).path("id"), fixture.process.getRequests().get(1).path("id"));

			fixture.reply(1, "{\"accepted\":false}");
			assertFalse(second.result().join().getAccepted());
			assertFalse(first.result().isDone());
			fixture.reply(0, "{\"accepted\":true}");
			assertTrue(first.result().join().getAccepted());
		}
	}

	@Test
	void malformedPayloadCompletesItsRequestAndAllOtherPendingRequests() throws Exception {
		try (var fixture = new Fixture()) {
			var first = fixture.client.request(ProcessOperations.CONSOLE, ConsoleCommandRequest.builder().target("one").text("first").build());
			var second = fixture.client.request(ProcessOperations.CONSOLE, ConsoleCommandRequest.builder().target("two").text("second").build());
			fixture.reply(0, "{\"accepted\":\"yes\"}");

			assertThrows(CompletionException.class, first.result()::join);
			assertThrows(CompletionException.class, second.result()::join);
			assertEquals(1, fixture.failures.size());
		}
	}

	@Test
	void shutdownCompletesRequestsWaitingForTheWriterAndRejectsLaterRequests() throws Exception {
		List<Runnable> writes = new ArrayList<>();
		try (var fixture = new Fixture(writes::add)) {
			var pending = fixture.client.request(ProcessOperations.CONSOLE, ConsoleCommandRequest.builder().target("one").text("first").build());
			fixture.client.close(new CancellationException("closed"));

			assertFalse(pending.submitted().join());
			assertThrows(CancellationException.class, pending.result()::join);
			writes.forEach(Runnable::run);
			assertTrue(fixture.process.getRequests().isEmpty());
			assertFalse(fixture.client.request(ProcessOperations.CONSOLE, ConsoleCommandRequest.builder().target("one").text("late").build()).submitted().join());
		}
	}

	@Test
	void shutdownDoesNotWaitForABlockedWriter() throws Exception {
		try (var fixture = new Fixture(task -> CompletableFuture.runAsync(task))) {
			fixture.process.holdCommandFlush();
			var pending = fixture.client.request(ProcessOperations.CONSOLE, ConsoleCommandRequest.builder().target("one").text("first").build());
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (!fixture.process.isCommandFlushWaiting() && System.nanoTime() < deadline) Thread.sleep(5);
			assertTrue(fixture.process.isCommandFlushWaiting());

			CompletableFuture.runAsync(() -> fixture.client.close(new CancellationException("closed")))
					.get(5, TimeUnit.SECONDS);
			assertFalse(pending.submitted().join());
			assertThrows(CancellationException.class, pending.result()::join);
			fixture.process.releaseCommandFlush();
		}
	}

	@Test
	void failedWriteCompletesBothResultsAndLateResponseCannotReviveThem() throws Exception {
		try (var fixture = new Fixture()) {
			fixture.process.setFailCommandFlush(true);
			var pending = fixture.client.request(ProcessOperations.CONSOLE, ConsoleCommandRequest.builder().target("one").text("first").build());
			assertFalse(pending.submitted().join());
			assertThrows(CompletionException.class, pending.result()::join);
			fixture.reply(0, "{\"accepted\":true}");
			assertTrue(pending.result().isCompletedExceptionally());
		}
	}

	@Test
	void aDeclaredCollectionResultNeedsNoOperationSpecificClientCode() throws Exception {
		var operation = ToolingOperation.<ConsoleCommandRequest, List<String>>builder()
				.name("fixture.lines")
				.requestType(ConsoleCommandRequest.class)
				.responseType(new TypeReference<List<String>>() {})
				.build();
		try (var fixture = new Fixture()) {
			var exchange = fixture.client.request(operation, ConsoleCommandRequest.builder().target("one").text("list").build());
			assertTrue(exchange.submitted().join());
			assertEquals("fixture.lines", fixture.process.getRequests().getFirst().path("operation").asText());
			fixture.reply(0, "[\"first\",\"second\"]");
			assertEquals(List.of("first", "second"), exchange.result().join());
		}
	}

	private static final class Fixture implements AutoCloseable {
		private final ControlledToolingProcess process = new ControlledToolingProcess(true);
		private final List<Throwable> failures = new ArrayList<>();
		private final ToolingConnection connection = new ToolingConnection(builder -> process, Runnable::run);
		private final ToolingClient client;

		private Fixture() throws IOException {
			this(Runnable::run);
		}

		private Fixture(java.util.concurrent.Executor executor) throws IOException {
			connection.startRunner(new ProcessBuilder("unused"));
			client = new ToolingClient(connection, executor, snapshot -> {}, output -> {}, failures::add);
			client.receive("{\"type\":\"ready\",\"protocolVersion\":7}");
		}

		private void reply(int index, String payload) {
			String id = process.getRequests().get(index).path("id").asText();
			client.receive("{\"type\":\"response\",\"id\":\"" + id + "\",\"success\":true,\"result\":" + payload + "}");
		}

		@Override
		public void close() {
			process.releaseCommandFlush();
			client.close(new CancellationException("closed"));
			connection.stop();
		}
	}
}

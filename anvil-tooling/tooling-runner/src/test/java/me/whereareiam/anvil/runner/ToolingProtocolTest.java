package me.whereareiam.anvil.runner;
import me.whereareiam.anvil.runner.protocol.ToolingProtocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.junit.jupiter.api.Test;

import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ToolingProtocolTest {
	@Test
	void correlatesFailuresWithoutKillingTheConnectionAndClosesOnEof() throws Exception {
		AtomicInteger closes = new AtomicInteger();
		try (Client client = new Client((ignored, method, arguments) -> switch (method.getName()) {
			case "snapshot" -> SessionSnapshot.builder().state(SessionState.IDLE).build();
			case "drainLogs", "scenarios" -> List.of();
			case "start" -> throw new AssertionError("setup assertion");
			case "close" -> { closes.incrementAndGet(); yield null; }
			default -> null;
		})) {
			client.send("bad", "unknown", Map.of());
			assertFalse(client.response("bad").path("success").asBoolean());
			client.send("assertion", "start", Map.of("definition", "demo"));
			assertTrue(client.response("assertion").path("error").asText().contains("setup assertion"));
			client.send("scenarios", "scenarios", Map.of());
			assertTrue(client.response("scenarios").path("success").asBoolean());
		}
		assertEquals(1, closes.get());
	}

	@Test
	void failedSnapshotPollIsNotAFatalErrorFrame() throws Exception {
		AtomicInteger polls = new AtomicInteger();
		try (Client client = new Client((ignored, method, arguments) -> switch (method.getName()) {
			case "snapshot" -> {
				if (polls.incrementAndGet() <= 2) throw new IllegalStateException("Transient projection failure");
				yield SessionSnapshot.builder().state(SessionState.RUNNING).build();
			}
			case "drainLogs" -> List.of();
			default -> null;
		})) {
			while (true) {
				String line = client.lines.poll(5, TimeUnit.SECONDS);
				assertNotNull(line, "Timed out waiting for a snapshot after failed polls");
				JsonNode frame = client.mapper.readTree(line);
				assertNotEquals("error", frame.path("type").asText(), "A failed poll must not close the connection");
				if (frame.path("type").asText().equals("snapshot")) break;
			}
			assertTrue(polls.get() > 2);
		}
	}

	@Test
	void cancelsQueuedStartBeforeItCanAcquireResources() throws Exception {
		CountDownLatch catalogEntered = new CountDownLatch(1);
		CountDownLatch finishCatalog = new CountDownLatch(1);
		AtomicInteger starts = new AtomicInteger();
		try (Client client = new Client((ignored, method, arguments) -> switch (method.getName()) {
			case "snapshot" -> SessionSnapshot.builder().state(SessionState.IDLE).build();
			case "drainLogs" -> List.of();
			case "scenarios" -> { catalogEntered.countDown(); finishCatalog.await(5, TimeUnit.SECONDS); yield List.of(); }
			case "start" -> { starts.incrementAndGet(); yield null; }
			default -> null;
		})) {
			client.send("scenarios", "scenarios", Map.of());
			assertTrue(catalogEntered.await(5, TimeUnit.SECONDS));
			client.send("start", "start", Map.of("definition", "demo"));
			client.send("stop", "stop", Map.of());
			// Wait until the reader has consumed the stop frame before unblocking the worker.
			client.queued("stop");
			finishCatalog.countDown();
			assertFalse(client.response("start").path("success").asBoolean());
			assertTrue(client.response("stop").path("success").asBoolean());
			assertEquals(0, starts.get());
		} finally {
			finishCatalog.countDown();
		}
	}

	@Test
	void interruptsBlockingProcessRestartWhenStopArrives() throws Exception {
		CountDownLatch restart = new CountDownLatch(1);
		CountDownLatch interrupted = new CountDownLatch(1);
		try (Client client = new Client((ignored, method, arguments) -> switch (method.getName()) {
			case "snapshot" -> SessionSnapshot.builder().state(SessionState.RUNNING).build();
			case "drainLogs", "scenarios" -> List.of();
			case "restartProcess" -> {
				restart.countDown();
				try { new CountDownLatch(1).await(); }
				catch (InterruptedException failure) { interrupted.countDown(); throw new IllegalStateException("cancelled", failure); }
				yield null;
			}
			default -> null;
		})) {
			client.send("restart", "restartProcess", Map.of("target", "server"));
			assertTrue(restart.await(5, TimeUnit.SECONDS));
			client.send("stop", "stop", Map.of());
			assertTrue(interrupted.await(5, TimeUnit.SECONDS));
			assertFalse(client.response("restart").path("success").asBoolean());
			assertTrue(client.response("stop").path("success").asBoolean());
		}
	}

	@Test
	void interruptsAnExtensionActionBeforeProcessingStop() throws Exception {
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch interrupted = new CountDownLatch(1);
		try (Client client = new Client((ignored, method, arguments) -> switch (method.getName()) {
			case "snapshot" -> SessionSnapshot.builder().state(SessionState.RUNNING).build();
			case "drainLogs", "scenarios" -> List.of();
			case "invoke" -> {
				entered.countDown();
				try { new CountDownLatch(1).await(); }
				catch (InterruptedException failure) { interrupted.countDown(); throw new IllegalStateException("Action cancelled", failure); }
				yield null;
			}
			default -> null;
		})) {
			client.action("action", "fixture.block");
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			client.send("stop", "stop", Map.of());
			assertTrue(interrupted.await(5, TimeUnit.SECONDS));
			assertFalse(client.response("action").path("success").asBoolean());
			assertTrue(client.response("stop").path("success").asBoolean());
		}
	}

	@Test
	void routesSelectedStartsAndSubsequentProcessControls() throws Exception {
		AtomicReference<String> initial = new AtomicReference<>();
		AtomicReference<String> process = new AtomicReference<>();
		AtomicBoolean setup = new AtomicBoolean();
		try (Client client = new Client((ignored, method, arguments) -> switch (method.getName()) {
			case "snapshot" -> SessionSnapshot.builder().state(SessionState.RUNNING).setupComplete(setup.get()).build();
			case "drainLogs", "scenarios" -> List.of();
			case "start" -> { initial.set((String) arguments[1]); yield null; }
			case "startProcess", "stopProcess" -> { process.set(method.getName() + ":" + arguments[0]); yield null; }
			case "startAll" -> { setup.set(true); yield null; }
			default -> null;
		})) {
			client.send("selected", "start", Map.of("definition", "network", "target", "proxy"));
			assertFalse(client.response("selected").path("result").path("setupComplete").asBoolean());
			assertEquals("proxy", initial.get());
			client.send("backend", "startProcess", Map.of("target", "lobby"));
			assertTrue(client.response("backend").path("success").asBoolean());
			assertEquals("startProcess:lobby", process.get());
			client.send("stop-backend", "stopProcess", Map.of("target", "lobby"));
			assertTrue(client.response("stop-backend").path("success").asBoolean());
			assertEquals("stopProcess:lobby", process.get());
			client.send("full", "startAll", Map.of());
			assertTrue(client.response("full").path("result").path("setupComplete").asBoolean());
		}
	}

	@Test
	void wholeStopCancelsQueuedIndividualLifecycleActions() throws Exception {
		for (String operation : List.of("startAll", "startProcess", "stopProcess")) {
			CountDownLatch entered = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			AtomicInteger executed = new AtomicInteger();
			try (Client client = new Client((ignored, method, arguments) -> switch (method.getName()) {
				case "snapshot" -> SessionSnapshot.builder().state(SessionState.IDLE).build();
				case "drainLogs" -> List.of();
				case "scenarios" -> { entered.countDown(); release.await(5, TimeUnit.SECONDS); yield List.of(); }
				case "startAll", "startProcess", "stopProcess" -> { executed.incrementAndGet(); yield null; }
				default -> null;
			})) {
				client.send("scenarios", "scenarios", Map.of());
				assertTrue(entered.await(5, TimeUnit.SECONDS));
				client.send("component", operation, Map.of("target", "server"));
				client.send("stop", "stop", Map.of());
				client.queued("stop");
				release.countDown();
				assertFalse(client.response("component").path("success").asBoolean());
				assertTrue(client.response("stop").path("success").asBoolean());
				assertEquals(0, executed.get());
			} finally {
				release.countDown();
			}
		}
	}

	@Test
	void preservesProtocolSevenScenarioNameSelection() throws Exception {
		var selected = new AtomicReference<String>();
		try (Client client = new Client((ignored, method, arguments) -> switch (method.getName()) {
			case "snapshot" -> SessionSnapshot.builder().state(SessionState.IDLE).build();
			case "drainLogs" -> List.of();
			case "start" -> { selected.set((String) arguments[0]); yield null; }
			default -> null;
		})) {
			client.send("alias", "start", Map.of("scenario", "example"));
			assertTrue(client.response("alias").path("success").asBoolean());
			assertEquals("example", selected.get());
		}
	}

	private static final class Client implements AutoCloseable {
		private final ObjectMapper mapper = new ObjectMapper();
		private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
		private final PipedReader input = new PipedReader();
		private final PipedWriter requests;
		private final Thread server;

		private Client(InvocationHandler handler) throws Exception {
			requests = new PipedWriter(input);
			ToolingSession session = (ToolingSession) Proxy.newProxyInstance(ToolingSession.class.getClassLoader(),
					new Class<?>[]{ToolingSession.class}, handler);
			Writer writer = new Writer() {
				private final StringBuilder line = new StringBuilder();
				@Override public synchronized void write(char[] buffer, int offset, int length) {
					for (int index = offset; index < offset + length; index++) {
						if (buffer[index] == '\n') { lines.add(line.toString()); line.setLength(0); }
						else line.append(buffer[index]);
					}
				}
				@Override public void flush() { }
				@Override public void close() { }
			};
			server = new Thread(() -> {
				try { new ToolingProtocol(session, input, new PrintWriter(writer)).run(); }
				catch (Exception failure) { throw new AssertionError(failure); }
			});
			server.start();
			JsonNode handshake = mapper.readTree(lines.poll(5, TimeUnit.SECONDS));
			assertEquals("ready", handshake.path("type").asText());
			assertEquals(ToolingSession.PROTOCOL_VERSION, handshake.path("protocolVersion").asInt());
		}

		private void send(String id, String operation, Map<String, String> values) throws Exception {
			var request = mapper.createObjectNode().put("id", id).put("operation", operation);
			values.forEach(request::put);
			requests.write(mapper.writeValueAsString(request) + "\n");
			requests.flush();
		}

		private void action(String id, String action) throws Exception {
			var request = mapper.createObjectNode().put("id", id).put("operation", "action").put("sessionId", "fixture")
					.put("actionId", action);
			request.putObject("target").put("type", "SCENARIO").put("name", "fixture");
			request.putObject("arguments");
			requests.write(mapper.writeValueAsString(request) + "\n");
			requests.flush();
		}

		private void queued(String id) throws Exception {
			while (true) {
				String line = lines.poll(5, TimeUnit.SECONDS);
				assertNotNull(line, "Timed out waiting for queue acknowledgement");
				JsonNode frame = mapper.readTree(line);
				if (frame.path("type").asText().equals("queued") && frame.path("requestId").asText().equals(id)) return;
			}
		}

		private JsonNode response(String id) throws Exception {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (System.nanoTime() < deadline) {
				String line = lines.poll(5, TimeUnit.SECONDS);
				assertNotNull(line, "Timed out waiting for response " + id);
				JsonNode frame = mapper.readTree(line);
				if (frame.path("id").asText().equals(id)) return frame;
			}
			throw new AssertionError("No response " + id);
		}

		@Override
		public void close() throws Exception {
			requests.close();
			server.join(5000);
			assertFalse(server.isAlive());
		}
	}
}

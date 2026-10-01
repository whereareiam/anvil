package me.whereareiam.anvil.integration.intellij.tooling.protocol;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import me.whereareiam.anvil.runner.protocol.ToolingProtocol;
import me.whereareiam.anvil.tooling.api.EnvironmentOperations;
import me.whereareiam.anvil.tooling.api.ProcessOperations;
import me.whereareiam.anvil.tooling.api.ScenarioOperations;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.LogEvent;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionAvailability;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.console.ConsoleCommandRequest;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioLaunchRequest;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the actual runner writer/reader against the IDE codec across the wire boundary.
 */
class ToolingProtocolRoundTripTest {
	private final ScenarioDescriptor scenario = ScenarioDescriptor.builder()
			.definition("example.Scenario").name("example").displayName("Example").build();
	private final ActionDescriptor action = ActionDescriptor.builder()
			.definition(ActionDefinition.builder().id("example.inspect").displayName("Inspect").build())
			.target(ActionTarget.builder().type(ActionTargetType.PROCESS).name("lobby").build())
			.availability(ActionAvailability.builder().enabled(true).build()).build();
	private final UUID executionId = UUID.fromString("00000000-0000-0000-0000-000000000123");
	private final SessionSnapshot snapshot = SessionSnapshot.builder().sessionId("session-1")
			.definition(scenario.getDefinition()).scenario(scenario.getName()).state(SessionState.RUNNING)
			.processes(List.of(ProcessSnapshot.builder().name("lobby").displayName("Lobby").executionId(executionId)
					.state(ProcessState.READY).host("localhost").port(25565).workDirectory("/work/lobby").build()))
			.actions(List.of(action)).build();

	@Test
	void sharedPayloadsAndNestedActionTargetsRoundTripThroughBothPeers() throws Exception {
		var invoked = new AtomicReference<ActionRequest>();
		var console = new AtomicReference<String>();
		ActionResult outcome = ActionResult.builder().message("Done").column("name").row(List.of("value")).build();
		try (var peer = new Peer((proxy, method, arguments) -> switch (method.getName()) {
			case "scenarios" -> List.of(scenario);
			case "snapshot" -> snapshot;
			case "drainLogs" -> List.of();
			case "invoke" -> { invoked.set((ActionRequest) arguments[0]); yield outcome; }
			case "console" -> { console.set(arguments[0] + ":" + arguments[1]); yield null; }
			default -> null;
		})) {
			assertEquals(List.of(scenario), peer.request(peer.encode(ScenarioOperations.DISCOVER, null)));
			assertEquals(snapshot, peer.request(peer.encode(ScenarioOperations.START, ScenarioLaunchRequest.builder()
					.definition(scenario.getDefinition()).scenario(scenario.getName()).target("lobby").build())));

			var request = peer.encode(EnvironmentOperations.INVOKE, ActionRequest.builder()
					.sessionId("session-1").actionId(action.getDefinition().getId()).target(action.getTarget())
					.argument("argument", "hello").build());
			JsonNode encoded = new ObjectMapper().readTree(request.line());
			assertEquals("PROCESS", encoded.path("target").path("type").asText());
			assertEquals("lobby", encoded.path("target").path("name").asText());
			assertFalse(encoded.has("targetType"));
			assertEquals(outcome, peer.request(request));
			assertEquals(ActionRequest.builder().sessionId("session-1").actionId("example.inspect")
					.target(action.getTarget()).argument("argument", "hello").build(), invoked.get());
			assertTrue(peer.request(peer.encode(ProcessOperations.CONSOLE,
					ConsoleCommandRequest.builder().target("lobby").text("list").build())).getAccepted());
			assertEquals("lobby:list", console.get());
		}
	}

	@Test
	void emittedLogsMapDirectlyToTheSharedLogModel() throws Exception {
		LogEvent log = LogEvent.builder().sessionId("session-1").process("lobby").executionId(executionId)
				.sequence(1).text("\u001B[32mready\u001B[0m").build();
		var delivered = new AtomicBoolean();
		try (var peer = new Peer((proxy, method, arguments) -> switch (method.getName()) {
			case "snapshot" -> snapshot;
			case "drainLogs" -> delivered.compareAndSet(false, true) ? List.of(log) : List.of();
			default -> null;
		})) {
			ToolingMessageCodec.Message event;
			do event = peer.next(); while (!(event instanceof ToolingMessageCodec.Output));
			assertEquals(log, ((ToolingMessageCodec.Output) event).value());
		}
	}

	@Test
	void legacyTargetsAndNonTextArgumentsFailWithoutInvokingTheAction() throws Exception {
		var invoked = new AtomicBoolean();
		try (var peer = new Peer((proxy, method, arguments) -> switch (method.getName()) {
			case "snapshot" -> snapshot;
			case "scenarios", "drainLogs" -> List.of();
			case "invoke" -> { invoked.set(true); yield ActionResult.builder().build(); }
			default -> null;
		})) {
			for (String invalid : List.of(
					"\"targetType\":\"PROCESS\",\"target\":\"lobby\",\"arguments\":{}",
					"\"target\":{\"type\":\"PROCESS\",\"name\":\"lobby\"},\"arguments\":{\"count\":42}",
					"\"target\":{\"type\":\"MISSING\",\"name\":\"lobby\"},\"arguments\":{}")) {
				peer.send("{\"id\":\"bad\",\"operation\":\"action\",\"sessionId\":\"session-1\",\"actionId\":\"example.inspect\"," + invalid + "}");
				assertNotNull(peer.response("bad").error());
				assertFalse(invoked.get());
			}
			assertEquals(List.of(), peer.request(peer.encode(ScenarioOperations.DISCOVER, null)));
		}
	}

	private static final class Peer implements AutoCloseable {
		private final ToolingMessageCodec codec = new ToolingMessageCodec();
		private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
		private final PipedWriter requests;
		private final Thread server;
		private final AtomicReference<Throwable> failure = new AtomicReference<>();

		private Peer(InvocationHandler handler) throws Exception {
			PipedReader input = new PipedReader();
			requests = new PipedWriter(input);
			ToolingSession session = (ToolingSession) Proxy.newProxyInstance(ToolingSession.class.getClassLoader(),
					new Class<?>[] {ToolingSession.class}, handler);
			Writer output = new Writer() {
				private final StringBuilder line = new StringBuilder();

				@Override
				public synchronized void write(char[] buffer, int offset, int length) {
					for (int index = offset; index < offset + length; index++) {
						if (buffer[index] != '\n') {
							line.append(buffer[index]);
							continue;
						}
						lines.add(line.toString());
						line.setLength(0);
					}
				}

				@Override public void flush() {}
				@Override public void close() {}
			};
			server = new Thread(() -> {
				try {
					new ToolingProtocol(session, input, new PrintWriter(output)).run();
				} catch (Throwable exception) {
					failure.set(exception);
				}
			});
			server.start();
			assertInstanceOf(ToolingMessageCodec.Ready.class, next());
		}

		private void send(String line) throws Exception {
			requests.write(line + "\n");
			requests.flush();
		}

		private <Q, R> Encoded<R> encode(ToolingOperation<Q, R> operation, Q payload) throws Exception {
			String id = UUID.randomUUID().toString();
			return new Encoded<>(id, codec.write(id, operation, payload), operation.getResponseType());
		}

		private record Encoded<R>(String id, String line, TypeReference<R> responseType) {}

		private <T> T request(Encoded<T> request) throws Exception {
			send(request.line());
			var response = response(request.id());
			assertNull(response.error());
			return codec.decode(response.result(), request.responseType());
		}

		private ToolingMessageCodec.Response response(String id) throws Exception {
			while (true) {
				var message = next();
				if (message instanceof ToolingMessageCodec.Response response && id.equals(response.id())) return response;
			}
		}

		private ToolingMessageCodec.Message next() throws Exception {
			String line = lines.poll(5, TimeUnit.SECONDS);
			assertNotNull(line, "Runner did not produce a frame");
			return codec.read(line);
		}

		@Override
		public void close() throws Exception {
			requests.close();
			server.join(5000);
			assertFalse(server.isAlive());
			assertNull(failure.get());
		}
	}
}

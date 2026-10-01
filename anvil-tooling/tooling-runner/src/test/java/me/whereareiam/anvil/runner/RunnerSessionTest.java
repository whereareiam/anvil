package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;
import me.whereareiam.anvil.api.model.player.PlayerState;
import me.whereareiam.anvil.api.model.process.console.ConsoleLine;
import me.whereareiam.anvil.api.model.process.console.ConsoleOutput;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.runner.scenario.ScenarioRepository;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.model.LogEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RunnerSessionTest {
	private ActionRequest request(RunnerSession session, String action, String text) {
		return ActionRequest.builder().sessionId(session.snapshot().getSessionId()).actionId(action)
				.target(ActionTarget.builder().type(ActionTargetType.PLAYER).name("alice").build()).arguments(Map.of("text", text)).build();
	}

	@Test
	void routesCoreCommandsAndRejectsUnknownOrStaleExtensionRequests() {
		Harness fixture = new Harness();
		try (RunnerSession session = fixture.session()) {
			assertEquals("User registration", session.scenarios().getFirst().getDisplayName());
			assertEquals("user-registration", session.scenarios().getFirst().getName());
			session.start("user-registration", null);
			assertEquals("Lobby", session.snapshot().getProcesses().getFirst().getDisplayName());
			assertEquals(fixture.process.executionId(), session.snapshot().getProcesses().getFirst().getExecutionId());
			session.console("server", "say hi");
			assertThrows(NoSuchElementException.class, () -> session.invoke(request(session, "fixture.unknown", "value")));
			assertThrows(IllegalStateException.class, () -> session.invoke(request(session, "fixture.unknown", "value")
					.toBuilder().sessionId("previous-environment").build()));
			assertEquals(List.of("console:say hi"), fixture.commands);
			assertThrows(IllegalArgumentException.class, () -> session.console("server", "say hi\nstop"));
		}
		assertEquals(1, fixture.closedContexts);
		assertEquals(1, fixture.closedEngines);
		assertEquals(List.of(true), fixture.finishes);
	}

	@Test
	void unknownDefinitionIsRejectedEvenWhenTheProcessNamesAnotherScenario() {
		Harness fixture = new Harness();
		try (RunnerSession session = fixture.session()) {
			var failure = assertThrows(NoSuchElementException.class,
					() -> session.start("example.RenamedDefinition", "user-registration"));

			assertTrue(failure.getMessage().contains("example.RenamedDefinition"));
			assertEquals(0, fixture.setupCalls);
			assertEquals("IDLE", session.snapshot().getState().name());
		}
	}

	@Test
	void finalizesFailedSetupUnsuccessfullyAndPreservesCleanupDiagnostics() {
		Harness fixture = new Harness();
		fixture.setupFailure = new IllegalStateException("Setup failed");
		fixture.cleanupFailure = new IllegalStateException("Cleanup failed");
		try (RunnerSession session = fixture.session()) {
			IllegalStateException failure = assertThrows(IllegalStateException.class,
					() -> session.start("user-registration", null));
			assertSame(fixture.setupFailure, failure);
			assertEquals(List.of(fixture.cleanupFailure), List.of(failure.getSuppressed()));
			assertEquals(List.of(false), fixture.finishes);
			assertEquals("FAILED", session.snapshot().getState().name());
			assertTrue(session.snapshot().getFailure().contains("Setup failed"));
			assertTrue(session.snapshot().getFailure().contains("Cleanup failed"));
		}
		assertEquals(1, fixture.closedContexts);
		assertEquals(1, fixture.closedEngines);
	}

	@Test
	void aFailedProcessMutationKeepsItsOutcomeUntilFinalization() {
		Harness fixture = new Harness();
		try (RunnerSession session = fixture.session()) {
			session.start("user-registration", null);
			fixture.restartFailure = new IllegalStateException("Replacement failed");
			assertThrows(IllegalStateException.class, () -> session.restartProcess("server"));
			assertEquals(List.of(false), fixture.finishes);
			assertEquals("FAILED", session.snapshot().getState().name());
			assertTrue(session.snapshot().getFailure().contains("Replacement failed"));
			session.stop();
			assertEquals(List.of(false), fixture.finishes);
		}
	}

	@Test
	void observedProcessFailurePreventsSuccessfulFinalization() {
		Harness fixture = new Harness();
		try (RunnerSession session = fixture.session()) {
			session.start("user-registration", null);
			fixture.processFailed = true;
			assertEquals("FAILED", session.snapshot().getState().name());
			session.stop();
			assertEquals(List.of(false), fixture.finishes);
			assertEquals("FAILED", session.snapshot().getState().name());
		}
	}

	@Test
	void observerMakesStartupOutputAvailableBeforePreparedStartReturns() throws Exception {
		Harness fixture = new Harness();
		fixture.started = new CountDownLatch(1);
		fixture.release = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		try (RunnerSession session = fixture.session()) {
			Thread starter = new Thread(() -> {
				try {
					session.start("user-registration", null);
				} catch (Throwable exception) {
					failure.set(exception);
				}
			});
			starter.start();
			try {
				assertTrue(fixture.started.await(5, TimeUnit.SECONDS));
				assertEquals("STARTING", session.snapshot().getState().name());
				assertEquals(List.of("Preparing server"), session.drainLogs().stream().map(LogEvent::getText).toList());
				assertEquals("server", session.snapshot().getProcesses().getFirst().getName());
			} finally {
				fixture.release.countDown();
				starter.join(5000);
			}
			assertFalse(starter.isAlive());
			assertNull(failure.get());
			assertEquals("RUNNING", session.snapshot().getState().name());
		}
	}

	@Test
	void retainsFinalLogsAndSeparatesProcessGenerationsOnRestart() {
		Harness fixture = new Harness();
		try (RunnerSession session = fixture.session()) {
			session.start("user-registration", null);
			fixture.output.add("first");
			assertEquals(List.of("first"), session.drainLogs().stream().map(LogEvent::getText).toList());
			assertTrue(session.drainLogs().isEmpty());
			session.restartProcess("server");
			fixture.output.add("second");
			LogEvent replacement = session.drainLogs().getFirst();
			assertEquals(fixture.process.executionId(), replacement.getExecutionId());
			assertEquals(1, replacement.getSequence());
			session.stop();
			assertEquals("STOPPED", session.snapshot().getState().name());
			assertEquals(List.of("second", "closed"), session.logs("server", 10));
			assertEquals(List.of("closed"), session.drainLogs().stream().map(LogEvent::getText).toList());
			session.stop();
			assertEquals(1, fixture.closedContexts);
		}
	}

	@Test
	void usesOpaqueRuntimeExecutionIdsAndIgnoresPreviouslyObservedExecutions() {
		Harness fixture = new Harness();
		try (RunnerSession session = fixture.session()) {
			session.start("user-registration", null);
			RunningProcess previous = fixture.process;
			UUID replacementId = UUID.randomUUID();
			fixture.nextExecutionId = replacementId;
			session.restartProcess("server");
			fixture.observer.processCreated(previous);
			fixture.output.add("Current generation");
			LogEvent event = session.drainLogs().getFirst();
			assertEquals(replacementId, event.getExecutionId());
			assertEquals(replacementId, session.snapshot().getProcesses().getFirst().getExecutionId());
			assertEquals("Current generation", event.getText());
			assertEquals(me.whereareiam.anvil.tooling.api.type.ProcessState.READY, session.snapshot().getProcesses().getFirst().getState());
		}
	}

	@Test
	void preservesUndeliveredFinalOutputWhenReplacingAnEnvironment() {
		Harness fixture = new Harness();
		try (RunnerSession session = fixture.session()) {
			session.start("user-registration", null);
			String oldId = session.snapshot().getSessionId();
			fixture.output.add("old run");
			session.start("user-registration", null);
			List<LogEvent> retained = session.drainLogs();
			assertEquals(List.of("old run", "closed"), retained.stream().map(LogEvent::getText).toList());
			assertTrue(retained.stream().allMatch(event -> oldId.equals(event.getSessionId())));
			assertNotEquals(oldId, session.snapshot().getSessionId());
		}
	}

	@Test
	void keepsSnapshotsResponsiveDuringStartupAndPreservesStartupFailure() throws Exception {
		CountDownLatch starting = new CountDownLatch(1);
		CountDownLatch finish = new CountDownLatch(1);
		ScenarioEngine engine = proxy(ScenarioEngine.class, (ignored, method, arguments) -> {
			if (method.getName().equals("close")) return null;
			starting.countDown();
			finish.await(5, TimeUnit.SECONDS);
			throw new IllegalStateException("startup failed");
		});
		try (RunnerSession session = new RunnerSession(() -> engine, repository())) {
			Thread start = new Thread(() -> assertThrows(IllegalStateException.class,
					() -> session.start("user-registration", null)));
			start.start();
			try {
				assertTrue(starting.await(5, TimeUnit.SECONDS));
				assertEquals("STARTING", session.snapshot().getState().name());
				assertTrue(session.drainLogs().isEmpty());
			} finally {
				finish.countDown();
				start.join(5000);
			}
			assertFalse(start.isAlive());
			assertEquals("FAILED", session.snapshot().getState().name());
			assertTrue(session.snapshot().getFailure().contains("startup failed"));
		}
	}

	@Test
	void selectedProcessStartPreservesRunAndDefersWholeScenarioSetup() {
		Harness fixture = new Harness();
		try (RunnerSession session = fixture.session()) {
			session.start("user-registration", "server");
			String runId = session.snapshot().getSessionId();
			assertEquals(List.of("prepare", "start:server"), fixture.lifecycle);
			assertEquals(0, fixture.setupCalls);
			assertFalse(session.snapshot().isSetupComplete());
			assertEquals(me.whereareiam.anvil.tooling.api.type.ProcessState.READY, session.snapshot().getProcesses().getFirst().getState());
			session.stopProcess("server");
			assertEquals(me.whereareiam.anvil.tooling.api.type.ProcessState.STOPPED, session.snapshot().getProcesses().getFirst().getState());
			session.startProcess("server");
			assertEquals(me.whereareiam.anvil.tooling.api.type.ProcessState.READY, session.snapshot().getProcesses().getFirst().getState());
			session.startAll();
			session.startAll();
			assertEquals(1, fixture.setupCalls);
			assertTrue(session.snapshot().isSetupComplete());
			assertEquals(runId, session.snapshot().getSessionId());
			assertThrows(NoSuchElementException.class, () -> session.startProcess("absent"));
			assertEquals("RUNNING", session.snapshot().getState().name());
		}
	}

	private static ScenarioRepository repository() {
		return ScenarioRepository.fromScenarios(List.of(AnvilScenario.builder()
				.name("user-registration").entrypoint("server")
				.server(MinecraftServer.builder().name("server").platform("paper").distribution(Distribution.remote("test", "1"))
						.metadata(PresentationMetadata.builder().displayName("Lobby").build()).build()).build()));
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}

	private static final class Harness {
		private final List<String> commands = new ArrayList<>();
		private List<String> output = new ArrayList<>();
		private RunningProcess process;
		private UUID nextExecutionId = UUID.randomUUID();
		private boolean active;
		private int closedContexts;
		private final List<Boolean> finishes = new ArrayList<>();
		private RuntimeException setupFailure;
		private RuntimeException restartFailure;
		private RuntimeException cleanupFailure;
		private ScenarioObserver observer;
		private CountDownLatch started;
		private CountDownLatch release;
		private boolean processFailed;
		private int closedEngines;
		private boolean setupComplete;
		private int setupCalls;
		private boolean processReady;
		private final List<String> lifecycle = new ArrayList<>();

		private RunnerSession session() {
			ScenarioEngine engine = proxy(ScenarioEngine.class, (ignored, method, arguments) -> {
				if (method.getName().equals("close")) { closedEngines++; return null; }
				assertEquals("prepare", method.getName());
				observer = (ScenarioObserver) arguments[1];
				active = true;
				setupComplete = false;
				processReady = false;
				process = null;
				nextExecutionId = UUID.randomUUID();
				output = new ArrayList<>();
				lifecycle.add("prepare");
				return proxy(ScenarioContext.class, (context, member, values) -> {
					if (member.getName().equals("finish")) {
						finishes.add((boolean) values[0]);
						if (active) { closedContexts++; output.add("closed"); active = false; }
						if (cleanupFailure != null) throw cleanupFailure;
						return null;
					}
					if (!active) throw new IllegalStateException("Context is closed");
					return switch (member.getName()) {
						case "start" -> {
							if (process == null || !processReady) { process = process(); processReady = true; }
							if (started != null) {
								output.add("Preparing server");
								started.countDown();
								if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture startup timeout");
							}
							if (setupFailure != null) throw setupFailure;
							if (!setupComplete) { setupCalls++; setupComplete = true; }
							yield null;
						}
						case "definition" -> arguments[0];
						case "processes" -> processes();
						case "players" -> players();
						default -> throw new AssertionError(member);
					};
				});
			});
			return new RunnerSession(() -> engine, repository());
		}

		private ScenarioProcesses processes() {
			return proxy(ScenarioProcesses.class, (ignored, method, arguments) -> switch (method.getName()) {
				case "all" -> process == null ? List.of() : List.of(process);
				case "get" -> process;
				case "start" -> {
					lifecycle.add("start:" + arguments[0]);
					if (process == null || !processReady) process = process();
					processReady = true;
					yield process;
				}
				case "stop" -> { processReady = false; yield null; }
				case "restart" -> {
					if (restartFailure != null) throw restartFailure;
					process = process();
					processReady = true;
					yield process;
				}
				default -> throw new AssertionError(method);
			});
		}

		private RunningProcess process() {
			UUID currentExecutionId = nextExecutionId;
			nextExecutionId = UUID.randomUUID();
			output = new ArrayList<>();
			List<String> captured = output;
			ProcessConsole console = proxy(ProcessConsole.class, (ignored, method, arguments) -> switch (method.getName()) {
				case "sendCommand" -> { commands.add("console:" + arguments[0]); yield null; }
				case "tail" -> List.copyOf(captured);
				case "read" -> {
					long after = (long) arguments[0];
					List<ConsoleLine> lines = new ArrayList<>();
					for (int index = (int) after; index < captured.size(); index++)
						lines.add(new ConsoleLine(index + 1, captured.get(index)));
					yield new ConsoleOutput(captured.size(), false, !active, lines);
				}
				default -> throw new AssertionError(method);
			});
			RunningProcess created = proxy(RunningProcess.class, (ignored, method, arguments) -> switch (method.getName()) {
				case "name" -> "server";
				case "executionId" -> currentExecutionId;
				case "address" -> new InetSocketAddress("127.0.0.1", 25565);
				case "workDirectory" -> Path.of("workspace");
				case "state" -> active && processFailed ? ProcessState.FAILED
						: ignored == process && active && processReady ? ProcessState.READY : ProcessState.STOPPED;
				case "console" -> console;
				default -> throw new AssertionError(method);
			});
			observer.processCreated(created);
			return created;
		}

		private PlayerManager players() {
			SimulatedPlayer player = proxy(SimulatedPlayer.class, (ignored, method, arguments) -> switch (method.getName()) {
				case "name" -> "alice";
				case "metadata" -> null;
				case "state" -> PlayerState.builder().build();
				case "hasCapability" -> false;
				case "capability" -> throw new UnsupportedOperationException("Fixture has no capabilities");
				default -> throw new AssertionError(method);
			});
			return proxy(PlayerManager.class, (ignored, method, arguments) -> method.getName().equals("all") ? List.of(player) : player);
		}
	}
}

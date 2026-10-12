package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.AnvilScenarioDefinition;
import me.whereareiam.anvil.runner.scenario.ScenarioRepository;
import me.whereareiam.anvil.runner.command.InteractiveSession;
import me.whereareiam.anvil.runner.command.RunnerTerminal;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.LogEvent;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractiveSessionTest {
	@Test
	void routesTerminalCommandsThroughTheBorrowedSession() throws Exception {
		StubSession session = new StubSession();
		ScenarioRepository repository = repository();
		new InteractiveSession(session, repository, FirstScenario.class.getName(),
				new StringReader("send paper say hello world\nlogs paper 12\nrestart\nstop\nstart second\nquit\n"),
				new RunnerTerminal(new PrintWriter(new StringWriter()))).run();

		assertEquals(List.of("start " + FirstScenario.class.getName(), "console paper say hello world", "logs paper 12",
				"start " + FirstScenario.class.getName(), "stop", "start second"), session.actions);
	}

	@Test
	void returnsAtEndOfInputWithoutClosingTheBorrowedSession() throws Exception {
		StubSession session = new StubSession();
		new InteractiveSession(session, repository(), FirstScenario.class.getName(), new StringReader(""),
				new RunnerTerminal(new PrintWriter(new StringWriter()))).run();

		assertEquals(List.of("start " + FirstScenario.class.getName()), session.actions);
	}

	@Test
	void invokesPortableActionsWithJsonInputs() throws Exception {
		StubSession session = new StubSession();
		StringWriter output = new StringWriter();
		new InteractiveSession(session, repository(), FirstScenario.class.getName(),
				new StringReader("action fixture.echo player alice {\"text\":\"hello world\",\"amount\":4}\nquit\n"),
				new RunnerTerminal(new PrintWriter(output))).run();

		assertEquals("hello world", session.invocation.getArguments().get("text"));
		assertEquals("4", session.invocation.getArguments().get("amount"));
		assertTrue(output.toString().contains("fixture result"));
	}

	private static ScenarioRepository repository() throws Exception {
		return ScenarioRepository.load(List.of(FirstScenario.class.getName(), SecondScenario.class.getName()));
	}

	public static final class FirstScenario implements AnvilScenarioDefinition {
		@Override
		public @NotNull AnvilScenario define() {
			return AnvilScenario.builder().name("first").entrypoint("paper").build();
		}
	}

	public static final class SecondScenario implements AnvilScenarioDefinition {
		@Override
		public @NotNull AnvilScenario define() {
			return AnvilScenario.builder().name("second").entrypoint("paper").build();
		}
	}

	private static final class StubSession implements ToolingSession {
		private final List<String> actions = new ArrayList<>();
		private ActionRequest invocation;
		private SessionSnapshot snapshot = SessionSnapshot.builder().state(SessionState.IDLE).build();

		@Override
		public @NotNull List<ScenarioDescriptor> scenarios() {
			return List.of();
		}

		@Override
		public void start(@NotNull String definition, @Nullable String process) {
			actions.add("start " + definition);
			snapshot = SessionSnapshot.builder().sessionId("fixture").definition(definition).scenario(
					definition.equals(FirstScenario.class.getName()) ? "first" : "second")
					.displayName(snapshot.getScenario()).state(SessionState.RUNNING).build();
		}

		@Override
		public void startAll() { throw new UnsupportedOperationException(); }
		@Override
		public void startProcess(@NotNull String process) { throw new UnsupportedOperationException(); }
		@Override
		public void stopProcess(@NotNull String process) { throw new UnsupportedOperationException(); }
		@Override
		public @NotNull SessionSnapshot snapshot() { return snapshot; }
		@Override
		public void console(@NotNull String process, @NotNull String command) { actions.add("console " + process + " " + command); }
		@Override
		public @NotNull ActionResult invoke(@NotNull ActionRequest request) {
			invocation = request;
			return ActionResult.builder().message("fixture result").build();
		}
		@Override
		public void restartProcess(@NotNull String process) { throw new UnsupportedOperationException(); }
		@Override
		public @NotNull List<String> logs(@NotNull String process, int maximumLines) {
			actions.add("logs " + process + " " + maximumLines);
			return List.of("captured console output");
		}
		@Override
		public @NotNull List<LogEvent> drainLogs() { return List.of(); }
		@Override
		public void stop() { actions.add("stop"); snapshot = snapshot.toBuilder().state(SessionState.STOPPED).build(); }
		@Override
		public void close() { actions.add("close"); }
	}
}

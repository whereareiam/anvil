package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.tooling.api.type.ProcessState;

import me.whereareiam.anvil.runner.command.RunnerTerminal;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerTerminalTest {
	@Test
	void displaysDiscoveredDefinitionsAndMetadata() {
		StringWriter buffer = new StringWriter();
		RunnerTerminal terminal = new RunnerTerminal(new PrintWriter(new BufferedWriter(buffer)));
		terminal.showScenarios(List.of(ScenarioDescriptor.builder().definition("example.DemoScenario")
				.name("demo").displayName("Demo").manual(true).build()));

		assertEquals(lines("Scenarios:", "  Demo (demo) [example.DemoScenario] (manual)"), buffer.toString());
	}

	@Test
	void flushesDisplaysWithoutClosingTheBorrowedStreamOrInterpretingLogText() {
		TrackedWriter buffer = new TrackedWriter();
		RunnerTerminal terminal = new RunnerTerminal(new PrintWriter(new BufferedWriter(buffer)));

		terminal.showScenario(SessionSnapshot.builder().state(SessionState.IDLE).build());
		terminal.showUnknownCommand("invalid");
		terminal.showLogs(List.of("[INFO] 100% complete", "\u001B[31mcolored\u001B[0m"));
		assertEquals(lines("No scenario is running.", "Unknown command: invalid",
				"[INFO] 100% complete", "\u001B[31mcolored\u001B[0m"), buffer.toString());

		buffer.getBuffer().setLength(0);
		terminal.showHelp();
		assertTrue(buffer.toString().startsWith("Commands: status, list, start <scenario>"));
		assertTrue(buffer.toString().endsWith("quit" + System.lineSeparator()));
		assertFalse(buffer.closed);
	}

	@Test
	void displaysPresentationNamesAlongsideTechnicalTargets() {
		StringWriter buffer = new StringWriter();
		RunnerTerminal terminal = new RunnerTerminal(new PrintWriter(buffer));
		terminal.showScenario(SessionSnapshot.builder()
				.scenario("registration").displayName("Player Registration").entrypoint("paper")
				.state(SessionState.RUNNING)
				.processes(List.of(ProcessSnapshot.builder().executionId(UUID.randomUUID()).name("paper")
						.displayName("Paper Backend").state(ProcessState.READY).host("127.0.0.1").port(25565)
						.workDirectory("work/paper").build())).build());

		String output = buffer.toString();
		assertTrue(output.contains("Scenario 'Player Registration (registration)' is ready:"));
		assertTrue(output.contains("Join: 127.0.0.1:25565"));
		assertTrue(output.contains("Paper Backend (paper) [READY] 127.0.0.1:25565"));
	}

	private String lines(String... lines) {
		return String.join(System.lineSeparator(), lines) + System.lineSeparator();
	}

	private static final class TrackedWriter extends StringWriter {
		private boolean closed;

		@Override
		public void close() {
			closed = true;
		}
	}
}

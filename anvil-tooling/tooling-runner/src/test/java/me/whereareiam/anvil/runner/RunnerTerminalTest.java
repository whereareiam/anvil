package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerTerminalTest {
	@Test
	void displaysTheSameCatalogForBothEntryPointsAndSupportsGroupListings() {
		ScenarioRegistry registry = new ScenarioRegistry();
		new AnvilRunnerTest.TestProvider().register(registry);
		StringWriter buffer = new StringWriter();
		RunnerTerminal terminal = new RunnerTerminal(new PrintWriter(new BufferedWriter(buffer)));

		terminal.showScenarios(registry);
		assertEquals(lines("Scenarios:", "  demo (manual)", "Groups:", "  default -> [demo]"), buffer.toString());

		buffer.getBuffer().setLength(0);
		terminal.showGroup(registry.groups().iterator().next());
		assertEquals(lines("[demo]"), buffer.toString());
	}

	@Test
	void flushesDisplaysWithoutClosingTheBorrowedStreamOrInterpretingLogText() {
		TrackedWriter buffer = new TrackedWriter();
		RunnerTerminal terminal = new RunnerTerminal(new PrintWriter(new BufferedWriter(buffer)));

		terminal.showScenario(null);
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

package me.whereareiam.anvil.runner;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import me.whereareiam.anvil.api.scenario.AnvilContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.runner.command.RunnerCommandParser;
import me.whereareiam.anvil.runner.model.command.RunnerCommand;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

final class InteractiveSession implements AutoCloseable {
	private final ScenarioEngine engine;
	private final ScenarioRegistry registry;
	private final ScenarioGroup group;
	private final BufferedReader input;
	private final RunnerTerminal terminal;
	private AnvilContext context;

	InteractiveSession(
			ScenarioEngine engine,
			ScenarioRegistry registry,
			AnvilScenario initial,
			ScenarioGroup group,
			Reader input,
			@NotNull RunnerTerminal terminal
	) {
		this.engine = engine;
		this.registry = registry;
		this.group = group;
		this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
		this.terminal = terminal;
		this.context = engine.start(initial);
	}

	void run() throws IOException {
		Runtime.getRuntime().addShutdownHook(new Thread(this::close, "anvil-runner-shutdown"));
		terminal.showScenario(context);
		terminal.showHelp();

		String line;
		while ((line = input.readLine()) != null) {
			if (line.isBlank()) continue;
			if (execute(RunnerCommandParser.parse(line))) return;
		}
	}

	private boolean execute(RunnerCommand command) {
		return switch (command.getType()) {
			case QUIT, EXIT -> {
				close();
				yield true;
			}
			case STATUS -> {
				showStatus();
				yield false;
			}
			case LIST -> {
				list();
				yield false;
			}
			case START -> {
				start(command);
				yield false;
			}
			case RESTART -> {
				restart();
				yield false;
			}
			case LOGS -> {
				showLogs(command);
				yield false;
			}
			case SEND -> {
				send(command);
				yield false;
			}
			case STOP -> {
				stop();
				yield false;
			}
			case UNKNOWN -> {
				terminal.showUnknownCommand(command.getToken());
				yield false;
			}
		};
	}

	private synchronized void showStatus() {
		terminal.showScenario(context);
	}

	private void list() {
		if (group == null) {
			terminal.showScenarios(registry);
			return;
		}
		terminal.showGroup(group);
	}

	private synchronized void start(RunnerCommand command) {
		requireArguments(command, 1, "Usage: start <scenario>");
		String scenarioName = command.getArguments().getFirst();
		if (group != null && !group.getScenarios().contains(scenarioName))
			throw new IllegalArgumentException("Scenario is not in group '" + group.getName() + "'");

		replace(registry.requireScenario(scenarioName));
	}

	private synchronized void restart() {
		ensureRunning();
		replace(context.scenario());
	}

	private synchronized void showLogs(RunnerCommand command) {
		requireArguments(command, 1, "Usage: logs <process> [lines]");
		int lines = command.getArguments().size() < 2
				? 30
				: parseLineCount(command.getArguments().get(1));
		ensureRunning();
		terminal.showLogs(context.processes().get(command.getArguments().getFirst()).console().tail(lines));
	}

	private synchronized void send(RunnerCommand command) {
		requireArguments(command, 2, "Usage: send <process> <command>");
		ensureRunning();
		context.processes().get(command.getArguments().getFirst()).console().sendCommand(command.getArguments().get(1));
	}

	private synchronized void stop() {
		close();
	}

	private synchronized void replace(AnvilScenario scenario) {
		close();
		context = engine.start(scenario);
		terminal.showScenario(context);
	}

	private void ensureRunning() {
		if (context == null) throw new IllegalStateException("No scenario is running. Use start <scenario> first");
	}

	@Override
	public synchronized void close() {
		AnvilContext current = context;
		context = null;
		if (current != null) current.close();
	}

	private static void requireArguments(RunnerCommand command, int required, String usage) {
		if (command.getArguments().size() < required) throw new IllegalArgumentException(usage);
	}

	private static int parseLineCount(String value) {
		int lines;
		try {
			lines = Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Log line count must be a positive integer: " + value, exception);
		}

		if (lines <= 0) throw new IllegalArgumentException("Log line count must be a positive integer: " + value);

		return lines;
	}
}

package me.whereareiam.anvil.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Objects;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import me.whereareiam.anvil.api.runtime.AnvilContext;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.engine.AnvilEngine;
import me.whereareiam.anvil.runner.command.RunnerCommandParser;
import me.whereareiam.anvil.runner.model.command.RunnerCommand;
import me.whereareiam.anvil.runner.type.RunnerCommandType;

final class InteractiveSession implements AutoCloseable {
	private final AnvilEngine engine;
	private final ScenarioRegistry registry;
	private final ScenarioGroup group;
	private final BufferedReader input;
	private final PrintWriter output;
	private AnvilContext context;

	InteractiveSession(AnvilEngine engine, ScenarioRegistry registry, AnvilScenario initial, ScenarioGroup group,
					Reader input, PrintWriter output) {
		this.engine = engine;
		this.registry = registry;
		this.group = group;
		this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
		this.output = Objects.requireNonNull(output, "output");
		this.context = engine.start(initial);
	}

	void run() throws IOException {
		Runtime.getRuntime().addShutdownHook(new Thread(this::close, "anvil-runner-shutdown"));
		RunnerOutput.printAddresses(context, output);
		output.println(RunnerCommandType.help());

		try {
			String line;
			while ((line = input.readLine()) != null) {
				if (line.isBlank())
					continue;
				if (execute(RunnerCommandParser.parse(line)))
					return;
				output.flush();
			}
		} finally {
			output.flush();
		}
	}

	private boolean execute(RunnerCommand command) {
		return switch (command.getType()) {
			case QUIT, EXIT -> {
				close();
				yield true;
			}
			case STATUS -> {
				printStatus();
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
				printLogs(command);
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
				output.println("Unknown command: " + command.getToken());
				yield false;
			}
		};
	}

	private synchronized void printStatus() {
		if (context == null) {
			output.println("No scenario is running.");
			return;
		}
		RunnerOutput.printAddresses(context, output);
	}

	private void list() {
		if (group == null) {
			RunnerOutput.printRegistry(registry, output);
			return;
		}
		output.println(group.getScenarios());
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

	private synchronized void printLogs(RunnerCommand command) {
		requireArguments(command, 1, "Usage: logs <process> [lines]");
		int lines = command.getArguments().size() < 2 ? 30 : parseLineCount(command.getArguments().get(1));
		ensureRunning();
		context.process(command.getArguments().getFirst()).console().tail(lines).forEach(output::println);
	}

	private synchronized void send(RunnerCommand command) {
		requireArguments(command, 2, "Usage: send <process> <command>");
		ensureRunning();
		context.process(command.getArguments().getFirst()).console().sendCommand(command.getArguments().get(1));
	}

	private synchronized void stop() { close(); }

	private synchronized void replace(AnvilScenario scenario) {
		close();
		context = engine.start(scenario);
		RunnerOutput.printAddresses(context, output);
	}

	private void ensureRunning() {
		if (context == null)
			throw new IllegalStateException("No scenario is running. Use start <scenario> first");
	}

	@Override
	public synchronized void close() {
		AnvilContext current = context;
		context = null;
		if (current != null)
			current.close();
	}

	private static void requireArguments(RunnerCommand command, int required, String usage) {
		if (command.getArguments().size() < required)
			throw new IllegalArgumentException(usage);
	}

	private static int parseLineCount(String value) {
		int lines;
		try {
			lines = Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Log line count must be a positive integer: " + value, exception);
		}

		if (lines <= 0)
			throw new IllegalArgumentException("Log line count must be a positive integer: " + value);

		return lines;
	}
}

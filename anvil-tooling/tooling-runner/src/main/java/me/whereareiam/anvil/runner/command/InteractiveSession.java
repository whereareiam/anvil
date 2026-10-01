package me.whereareiam.anvil.runner.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.runner.scenario.ScenarioRepository;
import me.whereareiam.anvil.runner.model.command.RunnerCommand;
import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the foreground command loop for one direct-definition selection.
 * The session owns the runtime; this class only translates terminal commands.
 */
public final class InteractiveSession {
	private final ToolingSession session;
	private final ScenarioRepository scenarios;
	private final String initial;
	private final BufferedReader input;
	private final RunnerTerminal terminal;

	/**
	 * Creates an interactive command loop.
	 *
	 * @param session borrowed tooling session
	 * @param scenarios discovered definition repository used for listing and name validation
	 * @param initial definition class name or unique scenario name to start first
	 * @param input command input
	 * @param terminal output presenter
	 */
	public InteractiveSession(
			@NotNull ToolingSession session,
			@NotNull ScenarioRepository scenarios,
			@NotNull String initial,
			@NotNull Reader input,
			@NotNull RunnerTerminal terminal
	) {
		this.session = session;
		this.scenarios = scenarios;
		this.initial = initial;
		this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
		this.terminal = terminal;
	}

	/**
	 * Starts the selected definition and consumes commands until EOF or quit.
	 *
	 * @throws IOException when command input cannot be read
	 */
	public void run() throws IOException {
		session.start(initial, null);
		terminal.showScenario(session.snapshot());
		terminal.showHelp();

		String line;
		while ((line = input.readLine()) != null) {
			if (line.isBlank()) continue;
			if (execute(RunnerCommandParser.parse(line))) return;
		}
	}

	private boolean execute(RunnerCommand command) {
		return switch (command.getType()) {
			case QUIT, EXIT -> true;
			case STATUS -> {
				terminal.showScenario(session.snapshot());
				yield false;
			}
			case LIST -> {
				terminal.showScenarios(descriptors());
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
			case ACTIONS -> {
				terminal.showActions(session.snapshot());
				yield false;
			}
			case ACTION -> {
				try {
					action(command);
				} catch (RuntimeException failure) {
					terminal.showActionResult(ActionResult.builder().successful(false).message(failure.getMessage()).build());
				}
				yield false;
			}
			case SEND -> {
				send(command);
				yield false;
			}
			case STOP -> {
				session.stop();
				yield false;
			}
			case UNKNOWN -> {
				terminal.showUnknownCommand(command.getToken());
				yield false;
			}
		};
	}

	private @NotNull List<ScenarioDescriptor> descriptors() {
		return scenarios.scenarios();
	}

	private void start(RunnerCommand command) {
		requireArguments(command, 1, "Usage: start <scenario-or-definition>");
		String selection = command.getArguments().getFirst();
		scenarios.require(selection);
		session.start(selection, null);
		terminal.showScenario(session.snapshot());
	}

	private void restart() {
		SessionSnapshot current = session.snapshot();
		if (current.getState() != SessionState.RUNNING)
			throw new IllegalStateException("No scenario is running. Use start <scenario> first");

		session.start(current.getDefinition(), null);
		terminal.showScenario(session.snapshot());
	}

	private void showLogs(RunnerCommand command) {
		requireArguments(command, 1, "Usage: logs <process> [lines]");
		int lines = command.getArguments().size() < 2 ? 30 : parseLineCount(command.getArguments().get(1));
		terminal.showLogs(session.logs(command.getArguments().getFirst(), lines));
	}

	private void action(RunnerCommand command) {
		requireArguments(command, 3, "Usage: action <id> <scenario|process|player> <target> [JSON inputs]");
		Map<String, String> arguments = new LinkedHashMap<>();
		if (command.getArguments().size() > 3) {
			try {
				var values = new ObjectMapper().readTree(command.getArguments().get(3));
				if (values == null || !values.isObject()) throw new IllegalArgumentException("Action inputs must be a JSON object");
				values.properties().forEach(entry -> {
					if (!entry.getValue().isValueNode() || entry.getValue().isNull())
						throw new IllegalArgumentException("Action inputs must be scalar values");
					arguments.put(entry.getKey(), entry.getValue().asText());
				});
			} catch (IOException failure) {
				throw new IllegalArgumentException("Action inputs must be valid JSON", failure);
			}
		}
		SessionSnapshot snapshot = session.snapshot();
		if (snapshot.getSessionId() == null) throw new IllegalStateException("No environment is running");
		terminal.showActionResult(session.invoke(ActionRequest.builder().sessionId(snapshot.getSessionId())
				.actionId(command.getArguments().getFirst()).arguments(arguments)
				.target(ActionTarget.builder().type(ActionTargetType.valueOf(command.getArguments().get(1).toUpperCase(Locale.ROOT)))
						.name(command.getArguments().get(2)).build()).build()));
	}

	private void send(RunnerCommand command) {
		requireArguments(command, 2, "Usage: send <process> <command>");
		session.console(command.getArguments().getFirst(), command.getArguments().get(1));
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

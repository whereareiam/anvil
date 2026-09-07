package me.whereareiam.anvil.runner;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.scenario.ScenarioGroup;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioRegistry;
import me.whereareiam.anvil.runner.type.RunnerCommandType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.util.Collection;

/**
 * Presents foreground runner output through a borrowed terminal stream.
 * Each display is flushed immediately; the caller retains ownership of the stream.
 */
@RequiredArgsConstructor
final class RunnerTerminal {
	private final @NotNull PrintWriter output;

	void showScenarios(@NotNull ScenarioRegistry registry) {
		output.println("Scenarios:");
		registry.scenarios().forEach(scenario ->
				output.println("  " + scenario.getName() + (scenario.isManual() ? " (manual)" : ""))
		);

		output.println("Groups:");
		registry.groups().forEach(group ->
				output.println("  " + group.getName() + " -> " + group.getScenarios())
		);
		output.flush();
	}

	void showGroup(@NotNull ScenarioGroup group) {
		output.println(group.getScenarios());
		output.flush();
	}

	void showScenario(@Nullable ScenarioContext context) {
		if (context == null) {
			output.println("No scenario is running.");
			output.flush();
			return;
		}

		output.println("Scenario '" + context.definition().getName() + "' is ready:");
		output.println("  Join: " + context.processes().get(context.definition().getEntrypoint()).address());
		for (RunningProcess process : context.processes().all())
			output.println("  " + process.name() + " [" + process.state() + "] " + process.address());

		output.flush();
	}

	void showHelp() {
		output.println(RunnerCommandType.help());
		output.flush();
	}

	void showUnknownCommand(@NotNull String token) {
		output.println("Unknown command: " + token);
		output.flush();
	}

	void showLogs(@NotNull Collection<String> lines) {
		lines.forEach(output::println);
		output.flush();
	}
}

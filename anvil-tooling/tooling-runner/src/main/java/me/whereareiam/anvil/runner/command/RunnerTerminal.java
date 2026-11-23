package me.whereareiam.anvil.runner.command;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.runner.type.RunnerCommandType;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.util.Collection;

/**
 * Presents foreground runner output through a borrowed terminal stream.
 * Each display is flushed immediately; the caller retains ownership of the stream.
 */
@RequiredArgsConstructor
public final class RunnerTerminal {
	private final @NotNull PrintWriter output;

	public void showScenarios(@NotNull Collection<ScenarioDescriptor> scenarios) {
		output.println("Scenarios:");
		scenarios.forEach(scenario ->
				output.println("  " + label(scenario.name(), scenario.displayName())
						+ " [" + scenario.definition() + "]"
						+ (scenario.manual() ? " (manual)" : ""))
		);
		output.flush();
	}

	public void showScenario(@NotNull SessionSnapshot snapshot) {
		if (snapshot.state() == SessionState.IDLE || snapshot.state() == SessionState.STOPPED) {
			output.println("No scenario is running.");
			output.flush();
			return;
		}

		String state = snapshot.state() == SessionState.RUNNING ? "ready" : snapshot.state().name().toLowerCase();
		output.println("Scenario '" + label(snapshot.scenario(), snapshot.displayName()) + "' is " + state + ":");
		for (ProcessSnapshot process : snapshot.processes()) {
			String address = process.host() + ":" + process.port();
			if (process.name().equals(snapshot.entrypoint())) output.println("  Join: " + address);
			output.println("  " + label(process.name(), process.displayName()) + " [" + process.state() + "] " + address);
		}
		if (snapshot.failure() != null) output.println("  Failure: " + snapshot.failure());

		output.flush();
	}

	public void showActions(SessionSnapshot snapshot) {
		if (snapshot.actions().isEmpty()) output.println("No actions are available in this environment.");
		for (var action : snapshot.actions()) {
			output.println(action.definition().id() + " " + action.target().type().name().toLowerCase()
					+ " " + action.target().name()
					+ (action.availability().enabled() ? "" : " — " + action.availability().reason()));
			for (var input : action.definition().inputs())
				output.println("  " + input.name() + ": " + input.type().name().toLowerCase()
						+ (input.required() ? " (required)" : ""));
		}
		for (var observation : snapshot.observations())
			output.println(observation.target().name() + " · " + (observation.definition().displayName() == null
					? observation.definition().id() : observation.definition().displayName())
					+ ": " + observation.value().text());
		output.flush();
	}

	public void showActionResult(ActionResult result) {
		output.println(result.message() == null ? (result.successful() ? "Completed" : "Action failed") : result.message());
		if (!result.columns().isEmpty()) output.println(String.join("\t", result.columns()));
		for (var row : result.rows()) output.println(String.join("\t", row));
		output.flush();
	}

	public void showHelp() {
		output.println(RunnerCommandType.help());
		output.flush();
	}

	public void showUnknownCommand(@NotNull String token) {
		output.println("Unknown command: " + token);
		output.flush();
	}

	public void showLogs(@NotNull Collection<String> lines) {
		lines.forEach(output::println);
		output.flush();
	}

	private String label(String name, @Nullable String displayName) {
		if (displayName == null || displayName.isBlank() || displayName.equals(name)) return name;
		return displayName + " (" + name + ")";
	}
}

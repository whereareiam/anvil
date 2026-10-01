package me.whereareiam.anvil.runner.command;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.runner.type.RunnerCommandType;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
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
				output.println("  " + label(scenario.getName(), scenario.getDisplayName())
						+ " [" + scenario.getDefinition() + "]"
						+ (scenario.isManual() ? " (manual)" : ""))
		);
		output.flush();
	}

	public void showScenario(@NotNull SessionSnapshot snapshot) {
		if (snapshot.getState() == SessionState.IDLE || snapshot.getState() == SessionState.STOPPED) {
			output.println("No scenario is running.");
			output.flush();
			return;
		}

		String state = snapshot.getState() == SessionState.RUNNING ? "ready" : snapshot.getState().name().toLowerCase();
		output.println("Scenario '" + label(snapshot.getScenario(), snapshot.getDisplayName()) + "' is " + state + ":");
		for (ProcessSnapshot process : snapshot.getProcesses()) {
			String address = process.getHost() + ":" + process.getPort();
			if (process.getName().equals(snapshot.getEntrypoint())) output.println("  Join: " + address);
			output.println("  " + label(process.getName(), process.getDisplayName()) + " [" + process.getState() + "] " + address);
		}
		if (snapshot.getFailure() != null) output.println("  Failure: " + snapshot.getFailure());

		output.flush();
	}

	public void showActions(SessionSnapshot snapshot) {
		if (snapshot.getActions().isEmpty()) output.println("No actions are available in this environment.");
		for (var action : snapshot.getActions()) {
			output.println(action.getDefinition().getId() + " " + action.getTarget().getType().name().toLowerCase()
					+ " " + action.getTarget().getName() + (action.getAvailability().isEnabled() ? "" : " — " + action.getAvailability().getReason()));
			for (var input : action.getDefinition().getInputs())
				output.println("  " + input.getName() + ": " + input.getType().name().toLowerCase() + (input.isRequired() ? " (required)" : ""));
		}
		for (var observation : snapshot.getObservations())
			output.println(observation.getTarget().getName() + " · " + (observation.getDefinition().getDisplayName() == null
					? observation.getDefinition().getId() : observation.getDefinition().getDisplayName()) + ": " + observation.getValue().getText());
		output.flush();
	}

	public void showActionResult(ActionResult result) {
		output.println(result.getMessage() == null ? (result.isSuccessful() ? "Completed" : "Action failed") : result.getMessage());
		if (!result.getColumns().isEmpty()) output.println(String.join("\t", result.getColumns()));
		for (var row : result.getRows()) output.println(String.join("\t", row));
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

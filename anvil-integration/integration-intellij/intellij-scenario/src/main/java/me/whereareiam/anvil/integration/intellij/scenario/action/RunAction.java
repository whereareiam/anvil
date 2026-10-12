package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

import me.whereareiam.anvil.integration.intellij.scenario.ScenarioNotifications;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.source.SourceDiscovery;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.window.ToolWindowController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the selected scenario, or starts the selected server or proxy, and shows its environment tab.
 * <p>
 * While the action is unavailable its description says why, for example which environment must stop first.
 */
public final class RunAction extends DumbAwareAction {
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent event) {
		Selection selection = Selection.of(event);
		var presentation = event.getPresentation();
		presentation.setIcon(AllIcons.Actions.Execute);
		if (selection == null) {
			presentation.setText("Run Scenario");
			presentation.setDescription("Select a scenario to run");
			presentation.setEnabled(false);
			return;
		}

		presentation.setText(selection.getProcess() == null ? "Run Scenario" : "Start " + selection.role());
		String refusal = refusal(selection);
		presentation.setEnabled(refusal == null);
		presentation.setDescription(refusal == null ? description(selection) : refusal);
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		Selection selection = Selection.of(event);
		if (selection == null || refusal(selection) != null) return;

		try {
			EnvironmentSession running = selection.runningSession();
			if (running != null) {
				if (selection.getProcess() == null) running.startAll();
				else running.startProcess(selection.getProcess());
				return;
			}

			EnvironmentSession session = EnvironmentLifecycle.getInstance(selection.getProject())
					.run(selection.getSource(), selection.getScenario(), selection.getProcess());
			ToolWindowController.reveal(selection.getProject(), session);
		} catch (RuntimeException failure) {
			ScenarioNotifications.error(selection.getProject(), "Could not run " + selection.getScenario().getDisplayName(),
					String.valueOf(failure.getMessage()));
		}
	}

	private static @Nullable String refusal(@NotNull Selection selection) {
		var discovery = SourceDiscovery.getInstance(selection.getProject()).snapshot();
		if (discovery.getState() == SourceDiscovery.State.SYNCING) return "Wait until the project sync has finished";
		if (ScenarioCatalog.getInstance(selection.getProject()).snapshot().getState() == ScenarioCatalog.State.LOADING
				&& selection.getSession() == null)
			return "Wait until the scenarios have loaded";

		EnvironmentSession running = selection.runningSession();
		var availability = running != null
				? running.availability(selection.getProcess())
				: EnvironmentLifecycle.getInstance(selection.getProject())
						.availability(selection.getSource(), selection.getScenario(), selection.getProcess());

		return availability.available() ? null : availability.reason();
	}

	private static @NotNull String description(@NotNull Selection selection) {
		if (selection.getProcess() != null)
			return "Start " + selection.getProcess() + " and the processes it depends on";
		if (selection.runningSession() != null) return "Start the scenario's remaining processes and its setup";

		return "Prepare the project and start " + selection.getScenario().getDisplayName();
	}
}

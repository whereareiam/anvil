package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

import me.whereareiam.anvil.integration.intellij.scenario.ScenarioNotifications;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.window.ToolWindowController;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Starts the selected scenario, or the selected server or proxy, in a new environment with the IDE's debugger
 * attached to each debugged process.
 */
public final class DebugAction extends DumbAwareAction {
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent event) {
		Selection selection = Selection.of(event);
		var presentation = event.getPresentation();
		presentation.setIcon(AllIcons.Actions.StartDebugger);
		if (selection == null) {
			presentation.setText("Debug Scenario");
			presentation.setDescription("Select a scenario to debug");
			presentation.setEnabled(false);
			return;
		}

		presentation.setText(selection.getProcess() == null ? "Debug Scenario" : "Debug " + selection.role());
		String refusal = refusal(selection);
		presentation.setEnabled(refusal == null);
		presentation.setDescription(refusal != null ? refusal : selection.getProcess() == null
				? "Start the scenario and attach the debugger to each of its servers and proxies"
				: "Start " + selection.getProcess() + " with the debugger attached");
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		Selection selection = Selection.of(event);
		if (selection == null || refusal(selection) != null) return;

		try {
			EnvironmentSession session = EnvironmentLifecycle.getInstance(selection.getProject())
					.debug(selection.getSource(), selection.getScenario(), selection.getProcess());
			ToolWindowController.reveal(selection.getProject(), session);
		} catch (RuntimeException failure) {
			ScenarioNotifications.error(selection.getProject(), "Could not debug " + selection.getScenario().getDisplayName(),
					String.valueOf(failure.getMessage()));
		}
	}

	private static @Nullable String refusal(@NotNull Selection selection) {
		String waiting = selection.waiting();
		if (waiting != null) return waiting;

		var availability = EnvironmentLifecycle.getInstance(selection.getProject())
				.debugAvailability(selection.getSource(), selection.getScenario(), selection.getProcess());
		return availability.available() ? null : availability.reason();
	}
}

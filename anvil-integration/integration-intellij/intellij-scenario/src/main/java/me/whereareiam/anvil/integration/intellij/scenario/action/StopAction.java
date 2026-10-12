package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

import me.whereareiam.anvil.integration.intellij.scenario.ScenarioDataKeys;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stops the selected process of a running scenario, or else the environment the view controls.
 */
public final class StopAction extends DumbAwareAction {
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent event) {
		var presentation = event.getPresentation();
		presentation.setIcon(AllIcons.Actions.Suspend);
		Selection selection = Selection.of(event);
		EnvironmentSession running = selection == null ? null : selection.runningSession();
		if (running != null && selection.getProcess() != null) {
			presentation.setText("Stop " + selection.role());
			presentation.setEnabled(running.isReady(selection.getProcess()));
			presentation.setDescription(presentation.isEnabled() ? "Stop " + selection.getProcess() + " and keep the environment"
					: "This process is not running");
			return;
		}

		EnvironmentSession session = session(event);
		presentation.setText(session == null ? "Stop Environment" : "Stop “" + session.getScenario().getDisplayName() + "”");
		presentation.setEnabled(session != null && session.canStop());
		presentation.setDescription(session == null ? "No environment is running" : "Stop every process and clean up");
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		Selection selection = Selection.of(event);
		EnvironmentSession running = selection == null ? null : selection.runningSession();
		if (running != null && selection.getProcess() != null) {
			if (running.isReady(selection.getProcess())) running.stopProcess(selection.getProcess());
			return;
		}

		EnvironmentSession session = session(event);
		if (session != null && session.canStop()) session.stop();
	}

	private static @Nullable EnvironmentSession session(@NotNull AnActionEvent event) {
		EnvironmentSession session = event.getData(ScenarioDataKeys.SESSION);
		return session != null && session.isActive() ? session : null;
	}
}

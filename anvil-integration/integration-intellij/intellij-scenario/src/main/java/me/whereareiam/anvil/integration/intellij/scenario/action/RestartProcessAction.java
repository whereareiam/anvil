package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import org.jetbrains.annotations.NotNull;

/**
 * Restarts the selected process of a running scenario, keeping its workspace.
 */
public final class RestartProcessAction extends DumbAwareAction {
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent event) {
		var presentation = event.getPresentation();
		presentation.setIcon(AllIcons.Actions.Restart);
		Selection selection = Selection.of(event);
		EnvironmentSession running = selection == null ? null : selection.runningSession();
		boolean process = selection != null && selection.getProcess() != null;
		presentation.setText(process ? "Restart " + selection.role() : "Restart Process");
		presentation.setEnabled(running != null && process && running.isReady(selection.getProcess()));
		presentation.setDescription(presentation.isEnabled() ? "Stop and start " + selection.getProcess() + " with its workspace"
				: "Select a running server or proxy");
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		Selection selection = Selection.of(event);
		EnvironmentSession running = selection == null ? null : selection.runningSession();
		if (running == null || selection.getProcess() == null || !running.isReady(selection.getProcess())) return;

		running.restartProcess(selection.getProcess());
	}
}

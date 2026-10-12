package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

import java.util.concurrent.CompletionException;

import me.whereareiam.anvil.integration.intellij.scenario.ScenarioNotifications;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import org.jetbrains.annotations.NotNull;

/**
 * Rebuilds the project and restarts the selected server or proxy, so it runs the rebuilt plugin.
 * <p>
 * The rebuild runs the source's preparation, which builds the artifacts the scenario installs as workspace assets;
 * the restart installs them again. Changes to the scenario definitions themselves need a new run.
 */
public final class RebuildRestartAction extends DumbAwareAction {
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent event) {
		var presentation = event.getPresentation();
		presentation.setIcon(AllIcons.Actions.ForceRefresh);
		Selection selection = Selection.of(event);
		EnvironmentSession running = selection == null ? null : selection.runningSession();
		boolean process = selection != null && selection.getProcess() != null;
		presentation.setText(process ? "Rebuild and Restart " + selection.role() : "Rebuild and Restart");
		if (running != null && running.isRebuilding()) {
			presentation.setEnabled(false);
			presentation.setDescription("The project is being rebuilt");
			return;
		}

		presentation.setEnabled(running != null && process && running.isReady(selection.getProcess()));
		presentation.setDescription(presentation.isEnabled()
				? "Rebuild the project, then restart " + selection.getProcess() + " with the rebuilt artifacts"
				: "Select a running server or proxy");
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		Selection selection = Selection.of(event);
		EnvironmentSession running = selection == null ? null : selection.runningSession();
		String process = selection == null ? null : selection.getProcess();
		if (running == null || process == null || !running.isReady(process) || running.isRebuilding()) return;

		running.rebuildAndRestart(process).whenComplete((ignored, failure) -> {
			if (failure == null) return;

			Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
			ScenarioNotifications.error(selection.getProject(), "Could not rebuild and restart " + process, String.valueOf(cause.getMessage()));
		});
	}
}

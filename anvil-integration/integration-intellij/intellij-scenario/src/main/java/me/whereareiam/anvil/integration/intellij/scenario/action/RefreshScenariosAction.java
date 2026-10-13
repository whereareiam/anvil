package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.icons.AllIcons;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;

import me.whereareiam.anvil.integration.intellij.scenario.catalog.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.source.SourceDiscovery;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Prepares the selected source again and reloads its scenarios.
 */
public final class RefreshScenariosAction extends DumbAwareAction {
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent event) {
		var presentation = event.getPresentation();
		presentation.setIcon(AllIcons.Actions.Refresh);
		Project project = event.getProject();
		if (project == null) {
			presentation.setEnabled(false);
			return;
		}

		var catalog = ScenarioCatalog.getInstance(project).snapshot();
		var source = SourceDiscovery.getInstance(project).snapshot().getSelectedSource();
		presentation.setText(source != null && catalog.isReadyFor(source) ? "Reload Scenarios" : "Load Scenarios");
		String refusal = refusal(project);
		presentation.setEnabled(refusal == null);
		presentation.setDescription(refusal == null ? "Compile the project and read its scenario definitions" : refusal);
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project != null && refusal(project) == null) SourceDiscovery.getInstance(project).refresh();
	}

	private static @Nullable String refusal(@NotNull Project project) {
		if (!TrustedProjects.isProjectTrusted(project)) return "Trust the project to load its scenarios";
		if (EnvironmentLifecycle.getInstance(project).hasActiveSession()) return "Stop the running environment first";
		if (SourceDiscovery.getInstance(project).snapshot().getState() != SourceDiscovery.State.IDLE)
			return "Wait until the project has been inspected";
		if (ScenarioCatalog.getInstance(project).snapshot().getState() == ScenarioCatalog.State.LOADING)
			return "The scenarios are loading";

		return null;
	}
}

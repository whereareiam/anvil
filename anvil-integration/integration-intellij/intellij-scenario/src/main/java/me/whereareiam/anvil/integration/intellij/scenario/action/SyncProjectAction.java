package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.icons.AllIcons;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;

import me.whereareiam.anvil.integration.intellij.scenario.catalog.source.SourceDiscovery;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Re-imports the linked build so new or renamed scenario sources are discovered.
 */
public final class SyncProjectAction extends DumbAwareAction {
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent event) {
		var presentation = event.getPresentation();
		presentation.setIcon(AllIcons.Actions.SyncPanels);
		Project project = event.getProject();
		String refusal = project == null ? "No project" : refusal(project);
		presentation.setEnabled(refusal == null);
		presentation.setDescription(refusal == null ? "Import the build again and discover its scenario sources" : refusal);
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project != null && refusal(project) == null) SourceDiscovery.getInstance(project).sync();
	}

	private static @Nullable String refusal(@NotNull Project project) {
		if (!TrustedProjects.isProjectTrusted(project)) return "Trust the project to sync it";
		var discovery = SourceDiscovery.getInstance(project).snapshot();
		if (!discovery.isCanSync()) return "No linked build to sync";
		if (discovery.getState() != SourceDiscovery.State.IDLE) return "The project is being inspected";
		if (EnvironmentLifecycle.getInstance(project).hasActiveSession()) return "Stop the running environment first";

		return null;
	}
}

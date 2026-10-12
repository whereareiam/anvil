package me.whereareiam.anvil.integration.intellij.account.view;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the project's account window. Signing in uses the scenario source of the component the action was
 * invoked from, when it offers one through {@link ScenarioSource#DATA_KEY}.
 */
public final class ManageAccountsAction extends DumbAwareAction {
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent event) {
		event.getPresentation().setEnabledAndVisible(event.getProject() != null);
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		new AccountsDialog(project, event.getData(ScenarioSource.DATA_KEY)).show();
	}
}

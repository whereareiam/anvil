package me.whereareiam.anvil.integration.intellij.scenario.action;

import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;

import me.whereareiam.anvil.integration.intellij.scenario.ScenarioNotifications;
import me.whereareiam.anvil.integration.intellij.scenario.runconfiguration.RunConfigurationService;
import org.jetbrains.annotations.NotNull;

/**
 * Saves the selected scenario, or the selected process, as a run configuration and selects it in the toolbar.
 */
public final class SaveRunConfigurationAction extends DumbAwareAction {
	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(@NotNull AnActionEvent event) {
		var presentation = event.getPresentation();
		presentation.setIcon(AllIcons.Actions.MenuSaveall);
		Selection selection = Selection.of(event);
		presentation.setEnabled(selection != null);
		presentation.setText(selection != null && selection.getProcess() != null
				? "Save " + selection.role() + " as Run Configuration"
				: "Save as Run Configuration");
	}

	@Override
	public void actionPerformed(@NotNull AnActionEvent event) {
		Selection selection = Selection.of(event);
		if (selection == null) return;

		var settings = RunConfigurationService.getInstance(selection.getProject())
				.save(selection.getSource(), selection.getScenario(), selection.definition());
		ScenarioNotifications.info(selection.getProject(), "Run configuration saved",
				"“" + settings.getName() + "” is selected in the run toolbar.",
				ScenarioNotifications.action("Edit…",
						() -> new EditConfigurationsDialog(selection.getProject()).show()));
	}
}

package me.whereareiam.anvil.integration.intellij.view.window.main.catalog;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

import java.util.function.BiConsumer;

import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.runconfiguration.RunConfigurationService;
import me.whereareiam.anvil.integration.intellij.view.window.account.ProjectAccountsDialog;
import me.whereareiam.anvil.integration.intellij.view.window.main.ScenarioPresentation;
import me.whereareiam.anvil.integration.intellij.view.window.main.navigation.DefinitionNavigator;
import me.whereareiam.anvil.integration.intellij.view.window.main.navigation.WorkspaceNavigator;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Executes catalog commands, rechecking current selection and lifecycle state at invocation.
 */
final class CatalogCommandController implements Disposable {
	private final @NotNull Project project;
	private final @NotNull CatalogView view;
	private final @NotNull CatalogDiscoveryController discovery;
	private final @NotNull EnvironmentLifecycle environments;
	private final @NotNull RunConfigurationService configurations;
	private final @NotNull DefinitionNavigator navigator;
	private final @NotNull WorkspaceNavigator workspaceNavigator;
	private final @NotNull BiConsumer<ScenarioSource, ScenarioDescriptor> starter;
	private final @NotNull ScenarioCatalogPanel.ProcessStarter processStarter;
	private boolean disposed;

	CatalogCommandController(
			@NotNull Project project,
			@NotNull CatalogView view,
			@NotNull CatalogDiscoveryController discovery,
			@NotNull EnvironmentLifecycle environments,
			@NotNull BiConsumer<ScenarioSource, ScenarioDescriptor> starter,
			@NotNull ScenarioCatalogPanel.ProcessStarter processStarter
	) {
		this.project = project;
		this.view = view;
		this.discovery = discovery;
		this.environments = environments;
		this.starter = starter;
		this.processStarter = processStarter;
		configurations = project.getService(RunConfigurationService.class);
		navigator = new DefinitionNavigator(project);
		workspaceNavigator = new WorkspaceNavigator(project);
	}

	private @Nullable Selection selection() {
		if (disposed) return null;

		ScenarioSource source = discovery.selectedSource();
		ScenarioDescriptor scenario = view.selectedScenario();
		if (source == null || scenario == null) return null;

		return new Selection(source, scenario, view.selectedProcess());
	}

	boolean hasSelection() {
		return selection() != null;
	}

	boolean canStart() {
		return canStart(selection());
	}

	private boolean canStart(@Nullable Selection selected) {
		if (selected == null || !discovery.readyForCommands()) return false;

		EnvironmentSession active = environments.getActiveSession();
		if (active == null) return !environments.hasActiveSession();
		if (!ScenarioPresentation.runs(active, selected.source(), selected.scenario())) return false;

		SessionSnapshot snapshot = active.getSnapshot();
		if (snapshot.getState() != SessionState.RUNNING) return false;
		if (selected.process() == null) return ScenarioPresentation.canStartScenario(selected.scenario(), snapshot);

		ProcessSnapshot live = ScenarioPresentation.liveProcess(snapshot, selected.process().getName());

		return ScenarioPresentation.canStartProcess(live);
	}

	@NotNull String startLabel() {
		ProcessDefinition process = view.selectedProcess();
		if (process == null) return "Run scenario";

		return ScenarioPresentation.startProcessLabel(process.getRole());
	}

	void startSelected() {
		Selection selected = selection();
		if (!canStart(selected)) return;
		try {
			if (selected.process() == null) {
				starter.accept(selected.source(), selected.scenario());
				return;
			}
			processStarter.start(selected.source(), selected.scenario(), selected.process().getName());
		} catch (RuntimeException failure) {
			discovery.showActionFailure(failure.getMessage());
		}
	}

	void saveSelected() {
		Selection selected = selection();
		if (selected == null) return;
		var saved = configurations.save(selected.source(), selected.scenario(), selected.process());
		view.details().showNavigationStatus("Saved run configuration: " + saved.getName());
	}

	void openSource() {
		Selection selected = selection();
		if (selected != null) openDefinition(selected.scenario().getDefinition());
	}

	void openDefinition(@NotNull String definition) {
		if (disposed) return;
		ScenarioSource source = discovery.selectedSource();
		if (source != null) navigator.open(source, definition, this, view.details()::showNavigationStatus);
	}

	void openWorkspace(@NotNull String workspace) {
		if (disposed) return;
		workspaceNavigator.open(workspace, this, view.details()::showNavigationStatus);
	}

	void accounts() {
		if (disposed) return;
		new ProjectAccountsDialog(project, discovery.selectedSource()).show();
	}

	@Override
	public void dispose() {
		disposed = true;
	}

	private record Selection(
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@Nullable ProcessDefinition process
	) {}
}

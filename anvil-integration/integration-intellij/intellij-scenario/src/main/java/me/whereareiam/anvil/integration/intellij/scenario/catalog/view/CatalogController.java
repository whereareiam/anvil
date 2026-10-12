package me.whereareiam.anvil.integration.intellij.scenario.catalog.view;

import com.intellij.build.BuildContentManager;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.source.SourceDiscovery;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.navigation.DefinitionNavigator;
import me.whereareiam.anvil.integration.intellij.scenario.navigation.WorkspaceNavigator;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettings;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decides what the catalog screen shows: the sources, the scenarios, a status message, the live overlay of the
 * running environment, and the details of the selection. Commands are registered actions, not this controller's.
 */
final class CatalogController implements Disposable {
	private final @NotNull Project project;
	private final @NotNull CatalogPanel view;
	private final @NotNull SourceDiscovery discovery;
	private final @NotNull ScenarioCatalog catalog;
	private final @NotNull EnvironmentLifecycle environments;
	private final @NotNull DefinitionNavigator definitions;
	private final @NotNull WorkspaceNavigator workspaces;
	private @Nullable EnvironmentSession execution;
	private @Nullable Disposable executionSubscription;
	private boolean disposed;

	CatalogController(@NotNull Project project, @NotNull CatalogPanel view) {
		this.project = project;
		this.view = view;
		discovery = SourceDiscovery.getInstance(project);
		catalog = ScenarioCatalog.getInstance(project);
		environments = EnvironmentLifecycle.getInstance(project);
		definitions = new DefinitionNavigator(project);
		workspaces = new WorkspaceNavigator(project);
	}

	void initialize() {
		ScenarioSettings.getInstance().subscribe(this::settingsChanged, this);
		discovery.subscribe(this::update, this);
		catalog.subscribe(this::update, this);
		environments.subscribe(this::update, this);

		settingsChanged();
		discovery.initialize();
		update();
	}

	@Nullable ScenarioSource selectedSource() {
		return discovery.snapshot().getSelectedSource();
	}

	@Nullable EnvironmentSession activeSession() {
		return environments.getActiveSession();
	}

	void select(@NotNull ScenarioSource source) {
		if (!disposed) discovery.select(source);
	}

	void openDefinition(@NotNull String definition) {
		ScenarioSource source = selectedSource();
		if (!disposed && source != null) definitions.open(source, definition, this, view.details::showNavigationStatus);
	}

	void openWorkspace(@NotNull String workspace) {
		if (!disposed) workspaces.open(workspace, this, view.details::showNavigationStatus);
	}

	private void settingsChanged() {
		if (!disposed) view.setAutoExpand(ScenarioSettings.getInstance().values().isExpandScenarios());
	}

	/**
	 * Returns the catalog when its latest load belongs to the selected source.
	 */
	private @Nullable ScenarioCatalog.Snapshot selectedCatalog() {
		ScenarioSource selected = selectedSource();
		ScenarioCatalog.Snapshot current = catalog.snapshot();
		return selected != null && current.isFor(selected) ? current : null;
	}

	private void update() {
		if (disposed) return;

		var state = discovery.snapshot();
		ScenarioCatalog.Snapshot loaded = selectedCatalog();
		List<ScenarioDescriptor> available = loaded == null ? List.of() : loaded.getScenarios();
		boolean loading = loaded != null && loaded.getState() == ScenarioCatalog.State.LOADING;
		view.renderSources(state.getListing().getSources(), state.getSelectedSource(),
				TrustedProjects.isProjectTrusted(project) && state.getState() != SourceDiscovery.State.SYNCING
						&& !loading && !environments.hasActiveSession());
		view.applyCatalog(available);
		observe(environments.getActiveSession());
		renderStatus(available.isEmpty(), loaded);
		view.updateActions();
	}

	private void renderStatus(boolean empty, @Nullable ScenarioCatalog.Snapshot loaded) {
		var state = discovery.snapshot();
		if (!TrustedProjects.isProjectTrusted(project)) {
			view.showMessage("Project is in Safe Mode", "Trust this project to load its Anvil scenarios.", null, null);
			return;
		}
		if (state.getState() == SourceDiscovery.State.SYNCING) {
			view.showMessage("Syncing project", "Scenarios load after the project import finishes.", null, null);
			return;
		}
		if (state.getSyncFailure() != null) {
			view.showMessage("Project sync needs attention", state.getSyncFailure().getMessage(), "View Sync Details",
					this::showSyncDetails);
			view.showSecondaryAction("Retry Sync", discovery::sync);
			return;
		}
		if (state.getFailure() != null) {
			view.showMessage("Anvil needs your attention", state.getFailure(), "Load Scenarios", discovery::refresh);
			return;
		}
		if (state.getState() == SourceDiscovery.State.DETECTING && empty) {
			view.showMessage("Finding Anvil projects", "Reading the imported project.", null, null);
			return;
		}
		if (state.getSelectedSource() == null) {
			boolean imported = state.getListing().getStatus() != SourceListingStatus.NOT_IMPORTED;
			view.showMessage(imported ? "Set up Anvil" : "Sync to discover scenarios", state.getListing().getMessage(),
					state.isCanSync() ? "Sync Project" : "Check Again", state.isCanSync() ? discovery::sync : discovery::refresh);
			return;
		}
		if (!empty) {
			view.showCatalog();
			return;
		}

		renderCatalogState(loaded);
	}

	private void renderCatalogState(@Nullable ScenarioCatalog.Snapshot loaded) {
		if (loaded != null && loaded.getFailure() != null) {
			view.showMessage("Could not load scenarios", loaded.getFailure(), "Show Output", this::showPreparationOutput);
			view.showSecondaryAction("Try Again", discovery::refresh);
			return;
		}
		if (loaded != null && loaded.getState() == ScenarioCatalog.State.LOADING) {
			view.showMessage("Loading scenarios", "Compiling the project and reading its scenario definitions.",
					"Show Output", this::showPreparationOutput);
			return;
		}

		boolean ready = loaded != null && loaded.getState() == ScenarioCatalog.State.READY;
		view.showMessage("No scenarios",
				ready ? "Add a class that implements AnvilScenarioDefinition, then load the scenarios again."
						: "Load this source's scenarios to see their servers and proxies.",
				"Load Scenarios", discovery::refresh);
	}

	/**
	 * Follows the active environment of the selected source, for the tree's live overlay and the details.
	 */
	void observe(@Nullable EnvironmentSession session) {
		ScenarioSource source = selectedSource();
		EnvironmentSession next = session != null && session.isActive() && source != null
				&& session.getSource().getId().equals(source.getId()) ? session : null;
		if (execution != next) {
			if (executionSubscription != null) Disposer.dispose(executionSubscription);
			executionSubscription = null;
			execution = next;
			if (next != null) {
				executionSubscription = Disposer.newDisposable("Anvil catalog execution");
				Disposer.register(this, executionSubscription);
				next.subscribe(this::executionChanged, executionSubscription);
			}
		}

		executionChanged();
	}

	private void executionChanged() {
		if (disposed) return;
		if (execution != null && !execution.isActive()) {
			observe(null);
			return;
		}

		view.showExecution(
				execution == null ? null : execution.getScenario(),
				execution == null ? null : execution.getSnapshot(),
				execution == null ? null : execution.getStatus());
		selectionChanged();
	}

	void selectionChanged() {
		if (disposed) return;

		view.updateActions();
		ScenarioDescriptor selected = view.selectedScenario();
		if (selected == null) {
			view.details.empty();
			return;
		}

		ProcessDefinition process = view.selectedProcess();
		ScenarioSource source = selectedSource();
		if (execution == null || !execution.isActive() || source == null || !execution.runs(source, selected)) {
			showDeclaration(selected, process);
			return;
		}

		ScenarioDescriptor scenario = execution.getScenario();
		var snapshot = execution.getSnapshot();
		if (process == null) {
			view.details.showEnvironment(scenario, snapshot, execution.getStatus());
			return;
		}

		var current = scenario.getProcesses().stream()
				.filter(candidate -> candidate.getName().equals(process.getName()))
				.findFirst()
				.orElse(null);
		if (current == null) {
			showDeclaration(selected, process);
			return;
		}

		view.details.showProcess(scenario, current, execution.process(current.getName()), snapshot.getState());
	}

	private void showDeclaration(@NotNull ScenarioDescriptor scenario, @Nullable ProcessDefinition process) {
		if (process == null) {
			view.details.showScenario(scenario);
			return;
		}

		view.details.showProcess(scenario, process, null, SessionState.IDLE);
	}

	private void showPreparationOutput() {
		CatalogDialogs.showPreparationOutput(project, catalog.getLog(), catalog::cancel);
	}

	private void showSyncDetails() {
		var failure = discovery.snapshot().getSyncFailure();
		if (failure == null) return;

		CatalogDialogs.showSyncDetails(project, failure.getDetails(),
				() -> BuildContentManager.getInstance(project).getOrCreateToolWindow().activate(null));
	}

	@Override
	public void dispose() {
		disposed = true;
		if (executionSubscription != null) Disposer.dispose(executionSubscription);
		executionSubscription = null;
		execution = null;
	}
}

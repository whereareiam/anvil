package me.whereareiam.anvil.integration.intellij.view.window.main.catalog;

import com.intellij.build.BuildContentManager;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.model.CatalogSnapshot;
import me.whereareiam.anvil.integration.intellij.model.source.DiscoverySnapshot;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.source.SourceDiscovery;
import me.whereareiam.anvil.integration.intellij.type.CatalogState;
import me.whereareiam.anvil.integration.intellij.type.source.DiscoveryState;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Observes discovery and catalog data and owns the catalog screen's loading and failure messages.
 */
final class CatalogDiscoveryController implements Disposable {
	private final @NotNull Project project;
	private final @NotNull CatalogView view;
	private final @NotNull EnvironmentLifecycle environments;
	private final @NotNull Runnable changed;
	private final @NotNull SourceDiscovery discovery;
	private final @NotNull ScenarioCatalog scenarioCatalog;
	private final @NotNull Preferences preferences = ApplicationManager.getApplication().getService(Preferences.class);
	private @Nullable String actionFailure;
	private boolean disposed;

	CatalogDiscoveryController(
			@NotNull Project project,
			@NotNull CatalogView view,
			@NotNull EnvironmentLifecycle environments,
			@NotNull Runnable changed
	) {
		this.project = project;
		this.view = view;
		this.environments = environments;
		this.changed = changed;

		discovery = project.getService(SourceDiscovery.class);
		scenarioCatalog = project.getService(ScenarioCatalog.class);
	}

	void initialize() {
		preferences.subscribe(this::preferencesChanged, this);
		discovery.subscribe(this::update, this);
		scenarioCatalog.subscribe(this::update, this);
		environments.subscribe(this::update, this);

		preferencesChanged();
		discovery.initialize();
		update();
	}

	void refresh() {
		if (disposed) return;
		actionFailure = null;
		discovery.refresh();
	}

	void sync() {
		if (disposed) return;
		actionFailure = null;
		discovery.sync();
	}

	void select(@NotNull ScenarioSource source) {
		if (disposed) return;
		actionFailure = null;
		discovery.select(source);
	}

	private void preferencesChanged() {
		if (disposed) return;
		view.setAutoExpand(preferences.snapshot().isExpandScenarioGroups());
	}

	@Nullable ScenarioSource selectedSource() {
		return discovery.snapshot().getSelectedSource();
	}

	private boolean loading() {
		return scenarioCatalog.snapshot().getState() == CatalogState.LOADING;
	}

	private boolean idle() {
		return discovery.snapshot().getState() == DiscoveryState.IDLE;
	}

	boolean canRefresh() {
		return !disposed && TrustedProjects.isProjectTrusted(project) && idle() && !loading() && !environments.hasActiveSession();
	}

	boolean canSync() {
		return !disposed && TrustedProjects.isProjectTrusted(project) && idle() && discovery.snapshot().isCanSync()
				&& !environments.hasActiveSession();
	}

	/**
	 * Returns the catalog when its latest load belongs to the selected source.
	 */
	private @Nullable CatalogSnapshot selectedCatalog() {
		ScenarioSource selected = selectedSource();
		CatalogSnapshot catalog = scenarioCatalog.snapshot();
		return selected != null && catalog.isFor(selected) ? catalog : null;
	}

	private void update() {
		if (disposed) return;

		var state = discovery.snapshot();
		CatalogSnapshot catalog = selectedCatalog();
		List<ScenarioDescriptor> available = catalog == null ? List.of() : catalog.getScenarios();
		boolean loadingSelected = catalog != null && catalog.getState() == CatalogState.LOADING;
		view.renderSources(state.getListing().getSources(), state.getSelectedSource(),
				TrustedProjects.isProjectTrusted(project) && state.getState() != DiscoveryState.SYNCING
						&& !loadingSelected && !environments.hasActiveSession());
		view.applyCatalog(available);

		changed.run();
		renderStatus(available.isEmpty(), catalog);
	}

	private void renderStatus(boolean empty, @Nullable CatalogSnapshot catalog) {
		var state = discovery.snapshot();
		if (renderProjectState(state, empty)) return;
		if (state.getSelectedSource() == null) {
			renderSourceSelection(state);
			return;
		}

		if (!empty) {
			view.showCatalog();
			return;
		}

		renderCatalogState(catalog);
	}

	private boolean renderProjectState(
			@NotNull DiscoverySnapshot state,
			boolean empty
	) {
		if (!TrustedProjects.isProjectTrusted(project)) {
			view.showMessage(
					"Project is in Safe Mode",
					"Trust this project in IntelliJ IDEA to load Anvil scenarios.",
					null,
					null);
			return true;
		}

		if (state.getState() == DiscoveryState.SYNCING) {
			view.showMessage("Syncing project", "Scenarios will load after the project import finishes.", null, null);
			return true;
		}

		if (state.getSyncFailure() != null) {
			view.showSyncFailure(state.getSyncFailure().getMessage(), this::showSyncDetails, this::sync);
			return true;
		}

		String failure = actionFailure == null ? state.getFailure() : actionFailure;
		if (failure != null) {
			view.showMessage("Anvil needs your attention", failure, "Load scenarios", this::refresh);
			return true;
		}
		if (state.getState() == DiscoveryState.DETECTING && empty) {
			view.showMessage("Finding Anvil scenario sources", "Reading imported project information.", null, null);
			return true;
		}

		return false;
	}

	private void renderSourceSelection(
			@NotNull DiscoverySnapshot state
	) {
		boolean imported = state.getListing().getStatus() != SourceListingStatus.NOT_IMPORTED;
		String title = imported ? "Set up Anvil" : "Sync to discover scenarios";
		String action = state.isCanSync() ? "Sync project" : "Check imported projects";

		Runnable callback = state.isCanSync() ? this::sync : this::refresh;
		view.showMessage(title, state.getListing().getMessage(), action, callback);
	}

	private void renderCatalogState(@Nullable CatalogSnapshot catalog) {
		if (catalog != null && catalog.getFailure() != null) {
			view.showMessage(
					"Could not load scenarios",
					catalog.getFailure(),
					"Show preparation output",
					this::showPreparationOutput);
			return;
		}

		if (catalog != null && catalog.getState() == CatalogState.LOADING) {
			view.showMessage(
					"Loading scenarios",
					"Compiling and reading the project's scenario definitions.",
					"Show preparation output",
					this::showPreparationOutput);
			return;
		}

		String explanation = catalog != null && catalog.getState() == CatalogState.READY
				? "Add an AnvilScenarioDefinition to this scenario source, then load its scenarios."
				: "Load this source's scenarios to inspect their servers and proxies.";
		view.showMessage("No scenarios available", explanation, "Load scenarios", this::refresh);
	}

	@NotNull String refreshLabel() {
		CatalogSnapshot catalog = selectedCatalog();
		return catalog != null && catalog.getState() == CatalogState.READY ? "Refresh scenarios" : "Load scenarios";
	}

	private void showPreparationOutput() {
		CatalogDialogs.showPreparationOutput(project, scenarioCatalog.getLog(), scenarioCatalog::cancel);
	}

	private void showSyncDetails() {
		if (discovery.snapshot().getSyncFailure() == null) return;
		CatalogDialogs.showSyncDetails(
				project,
				discovery.snapshot().getSyncFailure().getDetails(),
				() -> BuildContentManager.getInstance(project).getOrCreateToolWindow().activate(null));
	}

	boolean readyForCommands() {
		return !disposed && TrustedProjects.isProjectTrusted(project) && idle() && !loading();
	}

	void showActionFailure(@Nullable String message) {
		if (disposed) return;
		actionFailure = message;
		update();
	}

	@Override
	public void dispose() {
		disposed = true;
	}
}

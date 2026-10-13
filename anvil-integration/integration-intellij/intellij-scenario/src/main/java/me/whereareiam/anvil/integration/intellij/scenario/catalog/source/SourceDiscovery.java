package me.whereareiam.anvil.integration.intellij.scenario.catalog.source;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import me.whereareiam.anvil.integration.intellij.exception.ProjectSyncException;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.scenario.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettings;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Which source the catalog shows, and when it loads: detection, source selection, native sync, and loading.
 * <p>
 * Commands and workflow transitions run on the IDE event thread; immutable snapshots support background
 * readers. Once initialized, discovery observes the project independently of open views.
 * {@link ImportRefreshPolicy} decides when a completed import reloads the selected source.
 */
@Service(Service.Level.PROJECT)
@RequiredArgsConstructor
public final class SourceDiscovery implements Disposable {
	private final @NotNull Project project;
	private final @NotNull ChangeListeners listeners = new ChangeListeners();
	private final @NotNull Set<String> attempted = new HashSet<>();
	private final @NotNull ImportRefreshPolicy importRefresh = new ImportRefreshPolicy(this::refreshAfterSync);

	// Written only on the IDE event thread; background readers observe complete snapshots.
	private volatile @NotNull Snapshot snapshot = Snapshot.builder()
			.listing(SourceListing.builder()
					.status(SourceListingStatus.UNSUPPORTED)
					.message("Open an Anvil-enabled project to discover its scenarios.")
					.build())
			.state(State.IDLE)
			.build();

	private @Nullable CompletableFuture<Void> observedImport;
	private boolean initialized;
	private boolean initialSyncRequested;
	private boolean loadRequested;
	private long generation;
	private volatile boolean disposed;

	/**
	 * Starts project observation once and discovers the initial source selection.
	 */
	@RequiresEdt
	public void initialize() {
		requireEventThread();
		if (initialized || disposed) return;
		initialized = true;
		sources().subscribe(this::projectChanged, this);
		ScenarioSettings.getInstance().subscribe(this::preferencesChanged, this);
		catalog().subscribe(this::loadWhenReady, this);
		environments().subscribe(this::loadWhenReady, this);
		detect(false);
	}

	/**
	 * Rediscovers imported sources and loads the selected source's scenarios.
	 */
	@RequiresEdt
	public void refresh() {
		requireEventThread();
		detect(true);
	}

	/**
	 * Requests native project sync; an import already in progress is observed rather than repeated.
	 */
	@RequiresEdt
	public void sync() {
		requireEventThread();
		sync(null, true);
	}

	/**
	 * Selects a discovered source and loads its scenarios when no environment is running.
	 */
	@RequiresEdt
	public void select(@NotNull ScenarioSource source) {
		requireEventThread();
		if (disposed || environments().hasActiveSession()) return;
		importRefresh.selectionChanged();
		update(state -> state.toBuilder().selectedSource(source).build());
		loadRequested = true;
		changed();
		loadWhenReady();
	}

	/**
	 * Returns the latest selection and operation state; safe to read from any thread.
	 */
	public @NotNull Snapshot snapshot() {
		return snapshot;
	}

	/**
	 * Calls the listener on the IDE event thread after each change, until the owner is disposed.
	 */
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener, owner);
	}

	private static void requireEventThread() {
		if (!ApplicationManager.getApplication().isDispatchThread())
			throw new IllegalStateException("Scenario discovery commands must run on the IDE event thread.");
	}

	private void projectChanged(ProjectChange change) {
		if (change == ProjectChange.IMPORTED) {
			if (snapshot.getState() != State.SYNCING)
				update(state -> state.toBuilder().syncFailure(null).build());

			importRefresh.importSucceeded();
		}

		if (change == ProjectChange.IMPORT_FAILED) importRefresh.importFailed();

		detect(false);
	}

	private void detect(boolean load) {
		if (disposed || snapshot.getState() == State.SYNCING) return;

		loadRequested |= load;
		long request = ++generation;
		update(state -> state.toBuilder().state(State.DETECTING).build());
		changed();

		CompletableFuture.supplyAsync(() -> new Detection(
						ReadAction.nonBlocking(sources()::discover).expireWith(this).executeSynchronously(),
						sources().canSync()),
				task -> ApplicationManager.getApplication().executeOnPooledThread(task))
				.whenComplete((result, failure) -> onEventThread(() -> detected(request, result, failure)));
	}

	private void detected(long request, @Nullable Detection result, @Nullable Throwable failure) {
		if (disposed || request != generation) return;

		update(state -> state.toBuilder().state(State.IDLE).build());
		if (failure != null) {
			loadRequested = false;
			update(state -> state.toBuilder().failure(failure.getMessage()).build());
			changed();
			return;
		}

		apply(result);
		if (!TrustedProjects.isProjectTrusted(project)) return;

		CompletableFuture<Void> importing = result.listing().getActiveSync();
		if (snapshot.getSyncFailure() == null && !environments().hasActiveSession() && result.canSync()
				&& (importing != null || !initialSyncRequested
						&& result.listing().getStatus() == SourceListingStatus.NOT_IMPORTED)) {
			sync(importing, importing == null);
			return;
		}

		ScenarioSource selected = snapshot.getSelectedSource();
		loadRequested |= selected != null && !attempted.contains(selected.getId()) && !hasLoadedCatalog(selected);
		loadWhenReady();
	}

	private void apply(Detection result) {
		ScenarioCatalog.Snapshot loaded = catalog().snapshot();
		ScenarioSource current = loaded.getSource();
		if (current != null && loaded.getState() == ScenarioCatalog.State.READY) importRefresh.catalogLoaded(current);

		ScenarioSource previous = snapshot.getSelectedSource();
		if (previous == null) previous = current;

		String preferred = previous == null ? null : previous.getId();
		ScenarioSource selected = result.listing().getSources().stream()
				.filter(source -> source.getId().equals(preferred)).findFirst()
				.orElse(result.listing().getSources().isEmpty() ? null : result.listing().getSources().getFirst());
		update(state -> state.toBuilder()
				.listing(result.listing())
				.selectedSource(selected)
				.canSync(result.canSync())
				.build());

		importRefresh.detected(selected);
		observeImport(result.listing().getActiveSync());
		changed();
	}

	private void sync(@Nullable CompletableFuture<Void> existing, boolean load) {
		if (disposed || !TrustedProjects.isProjectTrusted(project)
				|| !snapshot.isCanSync()
				|| snapshot.getState() == State.SYNCING
				|| environments().hasActiveSession())
			return;

		initialSyncRequested = true;
		generation++;
		loadRequested |= load;
		update(state -> state.toBuilder()
				.state(State.SYNCING)
				.syncFailure(null)
				.failure(null)
				.build());
		changed();

		try {
			CompletableFuture<Void> operation = existing == null ? sources().sync() : existing;
			operation.whenComplete((ignored, failure) -> onEventThread(() -> syncCompleted(load, failure)));
		} catch (RuntimeException failure) {
			update(state -> state.toBuilder()
					.state(State.IDLE)
					.build());

			syncFailed(failure);
			changed();
		}
	}

	private void syncCompleted(boolean load, @Nullable Throwable failure) {
		if (disposed) return;

		update(state -> state.toBuilder()
				.state(State.IDLE)
				.build());

		if (failure != null) {
			syncFailed(failure);
			detect(false);
			return;
		}
		importRefresh.importSucceeded();
		detect(load);
	}

	private void observeImport(@Nullable CompletableFuture<Void> operation) {
		if (observedImport == operation) return;

		observedImport = operation;
		if (operation == null) return;

		operation.whenComplete((ignored, failure) -> onEventThread(() -> {
			if (observedImport != operation || snapshot.getState() == State.SYNCING) return;
			if (failure != null) {
				syncFailed(failure);
				detect(false);
				return;
			}

			importRefresh.importSucceeded();
			detect(false);
		}));
	}

	private void syncFailed(Throwable failure) {
		update(state -> state.toBuilder().syncFailure(ProjectSyncException.from(failure)).build());
		loadRequested = false;
		importRefresh.importFailed();
	}

	private void preferencesChanged() {
		importRefresh.preferencesChanged(snapshot.getSelectedSource());
		loadWhenReady();
	}

	private void loadWhenReady() {
		ScenarioSource selected = snapshot.getSelectedSource();
		if (disposed || snapshot.getState() != State.IDLE || observedImport != null
				|| snapshot.getSyncFailure() != null || selected == null
				|| !TrustedProjects.isProjectTrusted(project)
				|| environments().hasActiveSession()
				|| catalog().snapshot().getState() == ScenarioCatalog.State.LOADING)
			return;

		if (!loadRequested && !importRefresh.refreshDue(selected)) return;

		loadRequested = false;
		importRefresh.loading(selected);
		attempted.add(selected.getId());
		update(state -> state.toBuilder().syncFailure(null).failure(null).build());

		try {
			catalog().load(selected);
		} catch (RuntimeException failure) {
			update(state -> state.toBuilder().failure(failure.getMessage()).build());
		}
		changed();
	}

	private boolean hasLoadedCatalog(ScenarioSource source) {
		return catalog().snapshot().isReadyFor(source);
	}

	private boolean refreshAfterSync() {
		return ScenarioSettings.getInstance().values().isRefreshAfterSync();
	}

	private BuildIntegrations sources() {
		return project.getService(BuildIntegrations.class);
	}

	private ScenarioCatalog catalog() {
		return ScenarioCatalog.getInstance(project);
	}

	private EnvironmentLifecycle environments() {
		return EnvironmentLifecycle.getInstance(project);
	}

	private void update(@NotNull UnaryOperator<Snapshot> change) {
		snapshot = change.apply(snapshot);
	}

	private void changed() {
		onEventThread(listeners::notifyNow);
	}

	private void onEventThread(Runnable action) {
		ApplicationManager.getApplication().invokeLater(() -> {
			if (!disposed && !project.isDisposed()) action.run();
		}, ModalityState.any());
	}

	@Override
	public void dispose() {
		disposed = true;
		listeners.clear();
	}

	private record Detection(@NotNull SourceListing listing, boolean canSync) {}

	/**
	 * Returns the discovery of the given project.
	 */
	public static @NotNull SourceDiscovery getInstance(@NotNull Project project) {
		return project.getService(SourceDiscovery.class);
	}

	/**
	 * Immutable source selection and discovery progress, independent of any view.
	 */
	@Value
	@Builder(toBuilder = true)
	public static class Snapshot {
		/**
		 * Imported source metadata from the latest completed discovery.
		 */
		@NotNull SourceListing listing;
		/**
		 * Selected source, or null when no supported source is available.
		 */
		@Nullable ScenarioSource selectedSource;
		/**
		 * Operation currently inspecting or syncing the project.
		 */
		@NotNull State state;
		/**
		 * Whether an installed build integration supports native sync.
		 */
		boolean canSync;
		/**
		 * Latest native sync failure and its details, if any.
		 */
		@Nullable ProjectSyncException syncFailure;
		/**
		 * Latest source inspection or catalog-load diagnostic, if any.
		 */
		@Nullable String failure;
	}

	/**
	 * Operation in progress while locating and importing scenario sources.
	 */
	public enum State {
		/**
		 * No discovery or sync operation is in progress.
		 */
		IDLE,
		/**
		 * Imported source metadata is being inspected.
		 */
		DETECTING,
		/**
		 * Native project sync is in progress.
		 */
		SYNCING
	}
}

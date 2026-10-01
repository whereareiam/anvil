package me.whereareiam.anvil.integration.intellij.source;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.exception.ProjectSyncException;
import me.whereareiam.anvil.integration.intellij.model.CatalogSnapshot;
import me.whereareiam.anvil.integration.intellij.model.source.DiscoverySnapshot;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.type.CatalogState;
import me.whereareiam.anvil.integration.intellij.type.source.DiscoveryState;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project-owned source discovery: detection, source selection, native sync, and scenario loading.
 * Commands and workflow transitions run on the IDE event thread; immutable snapshots support background
 * readers. {@link ImportRefreshPolicy} decides when a completed import reloads the selected source.
 */
@RequiredArgsConstructor
public final class ProjectSourceDiscovery implements SourceDiscovery, Disposable {
	private final @NotNull Project project;
	private final @NotNull ChangeListeners listeners = new ChangeListeners();
	private final @NotNull Set<String> attempted = new HashSet<>();
	private final @NotNull ImportRefreshPolicy importRefresh = new ImportRefreshPolicy(this::refreshAfterSync);

	// Written only on the IDE event thread; background readers observe complete snapshots.
	private volatile @NotNull DiscoverySnapshot snapshot = DiscoverySnapshot.builder()
			.listing(SourceListing.builder()
					.status(SourceListingStatus.UNSUPPORTED)
					.message("Open an Anvil-enabled project to discover its scenarios.")
					.build())
			.state(DiscoveryState.IDLE)
			.build();

	private @Nullable CompletableFuture<Void> observedImport;
	private boolean initialized;
	private boolean initialSyncRequested;
	private boolean loadRequested;
	private long generation;
	private volatile boolean disposed;

	@Override
	public void initialize() {
		requireEventThread();
		if (initialized || disposed) return;
		initialized = true;
		sources().subscribe(this::projectChanged, this);
		preferences().subscribe(this::preferencesChanged, this);
		catalog().subscribe(this::loadWhenReady, this);
		environments().subscribe(this::loadWhenReady, this);
		detect(false);
	}

	@Override
	public void refresh() {
		requireEventThread();
		detect(true);
	}

	@Override
	public void sync() {
		requireEventThread();
		sync(null, true);
	}

	@Override
	public void select(@NotNull ScenarioSource source) {
		requireEventThread();
		if (disposed || environments().hasActiveSession()) return;
		importRefresh.selectionChanged();
		update(state -> state.toBuilder().selectedSource(source).build());
		loadRequested = true;
		changed();
		loadWhenReady();
	}

	@Override
	public @NotNull DiscoverySnapshot snapshot() {
		return snapshot;
	}

	@Override
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener, owner);
	}

	private static void requireEventThread() {
		if (!ApplicationManager.getApplication().isDispatchThread())
			throw new IllegalStateException("Scenario discovery commands must run on the IDE event thread.");
	}

	private void projectChanged(ProjectChange change) {
		if (change == ProjectChange.IMPORTED) {
			if (snapshot.getState() != DiscoveryState.SYNCING)
				update(state -> state.toBuilder().syncFailure(null).build());

			importRefresh.importSucceeded();
		}

		if (change == ProjectChange.IMPORT_FAILED) importRefresh.importFailed();

		detect(false);
	}

	private void detect(boolean load) {
		if (disposed || snapshot.getState() == DiscoveryState.SYNCING) return;

		loadRequested |= load;
		long request = ++generation;
		update(state -> state.toBuilder().state(DiscoveryState.DETECTING).build());
		changed();

		CompletableFuture.supplyAsync(() -> new Detection(
						ReadAction.nonBlocking(sources()::discover).expireWith(this).executeSynchronously(),
						sources().canSync()),
				task -> ApplicationManager.getApplication().executeOnPooledThread(task))
				.whenComplete((result, failure) -> onEventThread(() -> detected(request, result, failure)));
	}

	private void detected(long request, @Nullable Detection result, @Nullable Throwable failure) {
		if (disposed || request != generation) return;

		update(state -> state.toBuilder().state(DiscoveryState.IDLE).build());
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
		CatalogSnapshot loaded = catalog().snapshot();
		ScenarioSource current = loaded.getSource();
		if (current != null && loaded.getState() == CatalogState.READY) importRefresh.catalogLoaded(current);

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
				|| snapshot.getState() == DiscoveryState.SYNCING
				|| environments().hasActiveSession())
			return;

		initialSyncRequested = true;
		generation++;
		loadRequested |= load;
		update(state -> state.toBuilder()
				.state(DiscoveryState.SYNCING)
				.syncFailure(null)
				.failure(null)
				.build());
		changed();

		try {
			CompletableFuture<Void> operation = existing == null ? sources().sync() : existing;
			operation.whenComplete((ignored, failure) -> onEventThread(() -> syncCompleted(load, failure)));
		} catch (RuntimeException failure) {
			update(state -> state.toBuilder()
					.state(DiscoveryState.IDLE)
					.build());

			syncFailed(failure);
			changed();
		}
	}

	private void syncCompleted(boolean load, @Nullable Throwable failure) {
		if (disposed) return;

		update(state -> state.toBuilder()
				.state(DiscoveryState.IDLE)
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
			if (observedImport != operation || snapshot.getState() == DiscoveryState.SYNCING) return;
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
		if (disposed || snapshot.getState() != DiscoveryState.IDLE || observedImport != null
				|| snapshot.getSyncFailure() != null || selected == null
				|| !TrustedProjects.isProjectTrusted(project)
				|| environments().hasActiveSession()
				|| catalog().snapshot().getState() == CatalogState.LOADING)
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
		return preferences().snapshot().isRefreshCatalogAfterSync();
	}

	private Preferences preferences() {
		return ApplicationManager.getApplication().getService(Preferences.class);
	}

	private BuildIntegrations sources() {
		return project.getService(BuildIntegrations.class);
	}

	private ScenarioCatalog catalog() {
		return project.getService(ScenarioCatalog.class);
	}

	private EnvironmentLifecycle environments() {
		return project.getService(EnvironmentLifecycle.class);
	}

	private void update(@NotNull UnaryOperator<DiscoverySnapshot> change) {
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
}

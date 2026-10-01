package me.whereareiam.anvil.integration.intellij.scenario;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

import java.util.List;
import java.util.concurrent.CancellationException;

import me.whereareiam.anvil.integration.intellij.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.log.SessionLog;
import me.whereareiam.anvil.integration.intellij.log.StoredSessionLog;
import me.whereareiam.anvil.integration.intellij.model.CatalogSnapshot;
import me.whereareiam.anvil.integration.intellij.model.SessionLogEntry;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.tooling.ProjectToolingHost;
import me.whereareiam.anvil.integration.intellij.tooling.ToolingLaunch;
import me.whereareiam.anvil.integration.intellij.type.CatalogState;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns discovered definitions independently of the environment that may use their launch.
 */
public final class ProjectScenarioCatalog implements ScenarioCatalog, Disposable {
	private final @NotNull Project project;
	private final @NotNull ProjectToolingHost host;
	private final @NotNull StoredSessionLog log = new StoredSessionLog();
	private final @NotNull ChangeListeners listeners = new ChangeListeners();
	private volatile @NotNull CatalogSnapshot catalog = CatalogSnapshot.builder().state(CatalogState.NOT_LOADED).build();
	private @Nullable ToolingLaunch launch;
	private volatile boolean disposed;

	public ProjectScenarioCatalog(@NotNull Project project) {
		this.project = project;
		host = project.getService(ProjectToolingHost.class);
		host.subscribe(this::observe, this);
	}

	@Override
	public void load(@NotNull ScenarioSource source) {
		host.reload(source);
	}

	@Override
	public @NotNull CatalogSnapshot snapshot() {
		return catalog;
	}

	@Override
	public @NotNull SessionLog getLog() {
		return log;
	}

	@Override
	public void cancel() {
		ToolingLaunch current;
		synchronized (this) {
			current = launch;
		}
		if (current != null) host.cancelDiscovery(current);
	}

	@Override
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener, owner);
	}

	private synchronized void observe(ToolingLaunch current, ProjectToolingHost.Purpose purpose) {
		if (disposed) return;
		// An environment launch for the loaded source keeps the scenarios shown while it prepares.
		boolean retain = purpose == ProjectToolingHost.Purpose.ENVIRONMENT && catalog.isReadyFor(current.source());
		launch = current;
		log.clear();
		catalog = retain
				? catalog.toBuilder().source(current.source()).build()
				: loadState(current, CatalogState.LOADING);
		changed();
		current.subscribe(new ToolingLaunch.Listener() {
			@Override
			public void output(@NotNull SessionLogEntry entry) {
				append(current, entry);
			}
		}, this);
		current.discover().whenComplete((definitions, failure) -> complete(current, definitions, failure));
	}

	private synchronized void complete(
			ToolingLaunch current, @Nullable List<ScenarioDescriptor> definitions, @Nullable Throwable failure
	) {
		if (disposed || launch != current) return;
		if (failure == null) {
			catalog = loadState(current, CatalogState.READY).toBuilder().scenarios(List.copyOf(definitions)).build();
			changed();
			return;
		}

		if (catalog.getState() == CatalogState.READY) {
			changed();
			return;
		}

		Throwable cause = failure;
		while (cause.getCause() != null) cause = cause.getCause();
		if (cause instanceof CancellationException) {
			catalog = loadState(current, CatalogState.NOT_LOADED);
			changed();
			return;
		}

		catalog = loadState(current, CatalogState.FAILED).toBuilder().failure(cause.toString()).build();
		log.append(null, null, 0, cause + "\n", true);
		changed();
	}

	private synchronized void append(ToolingLaunch current, SessionLogEntry entry) {
		if (disposed || launch != current || host.purpose(current) == ProjectToolingHost.Purpose.ENVIRONMENT) return;
		log.append(entry.getProcess(), entry.getExecutionId(), entry.getSequence(), entry.getText(), entry.isError());
	}

	private void changed() {
		listeners.notifyLater(() -> !disposed && !project.isDisposed());
	}

	@Override
	public synchronized void dispose() {
		disposed = true;
		listeners.clear();
		log.releaseHistory();
	}

	private static @NotNull CatalogSnapshot loadState(@NotNull ToolingLaunch launch, @NotNull CatalogState state) {
		return CatalogSnapshot.builder().source(launch.source()).state(state).build();
	}
}

package me.whereareiam.anvil.integration.intellij.scenario.catalog;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import java.util.List;
import java.util.concurrent.CancellationException;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.scenario.log.SessionLog;
import me.whereareiam.anvil.integration.intellij.scenario.log.SessionLogEntry;
import me.whereareiam.anvil.integration.intellij.scenario.tooling.ToolingHost;
import me.whereareiam.anvil.integration.intellij.scenario.tooling.ToolingLaunch;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Loads and retains the scenarios of the selected source, independently of the environment that may later reuse
 * the launch that loaded them.
 * <p>
 * Loading prepares the source and asks the tooling runner for its scenario descriptors. Preparation and runner
 * output is kept in {@link #getLog()} until the next load, so it can be inspected after a failure.
 */
@Service(Service.Level.PROJECT)
public final class ScenarioCatalog implements Disposable {
	private final @NotNull Project project;
	private final @NotNull ToolingHost host;
	private final @NotNull SessionLog log = new SessionLog();
	private final @NotNull ChangeListeners listeners = new ChangeListeners();
	private volatile @NotNull Snapshot catalog = Snapshot.builder().state(State.NOT_LOADED).build();
	private @Nullable ToolingLaunch launch;
	private volatile boolean disposed;

	public ScenarioCatalog(@NotNull Project project) {
		this.project = project;
		host = project.getService(ToolingHost.class);
		host.subscribe(this::observe, this);
	}

	/**
	 * Returns the catalog of the given project.
	 */
	public static @NotNull ScenarioCatalog getInstance(@NotNull Project project) {
		return project.getService(ScenarioCatalog.class);
	}

	/**
	 * Replaces an idle catalog launch and loads the source's current definitions.
	 *
	 * @throws IllegalStateException if the project is closed or an environment is running
	 */
	public void load(@NotNull ScenarioSource source) {
		host.reload(source);
	}

	/**
	 * Returns the latest load's source, scenarios, state, and failure as one consistent value.
	 */
	public @NotNull Snapshot snapshot() {
		return catalog;
	}

	/**
	 * Returns preparation and catalog-launch output, retained independently of any view.
	 */
	public @NotNull SessionLog getLog() {
		return log;
	}

	/**
	 * Cancels preparation or loading while no environment uses the launch.
	 */
	public void cancel() {
		ToolingLaunch current;
		synchronized (this) {
			current = launch;
		}
		if (current != null) host.cancelDiscovery(current);
	}

	/**
	 * Calls the listener on the IDE event thread after each change, until the owner is disposed.
	 */
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener, owner);
	}

	private synchronized void observe(ToolingLaunch current, ToolingHost.Purpose purpose) {
		if (disposed) return;

		// An environment launch for the loaded source keeps the scenarios shown while it prepares.
		boolean retain = purpose == ToolingHost.Purpose.ENVIRONMENT && catalog.isReadyFor(current.source());
		launch = current;
		log.clear();
		catalog = retain ? catalog.toBuilder().source(current.source()).build() : loadState(current, State.LOADING);
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
			ToolingLaunch current,
			@Nullable List<ScenarioDescriptor> definitions,
			@Nullable Throwable failure
	) {
		if (disposed || launch != current) return;
		if (failure == null) {
			catalog = loadState(current, State.READY).toBuilder().scenarios(List.copyOf(definitions)).build();
			changed();
			return;
		}

		if (catalog.getState() == State.READY) {
			changed();
			return;
		}

		Throwable cause = failure;
		while (cause.getCause() != null) cause = cause.getCause();
		if (cause instanceof CancellationException) {
			catalog = loadState(current, State.NOT_LOADED);
			changed();
			return;
		}

		String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
		catalog = loadState(current, State.FAILED).toBuilder().failure(message).build();
		log.append(null, null, 0, cause + "\n", true);
		changed();
	}

	private synchronized void append(ToolingLaunch current, SessionLogEntry entry) {
		if (disposed || launch != current || host.purpose(current) == ToolingHost.Purpose.ENVIRONMENT) return;

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

	private static @NotNull Snapshot loadState(@NotNull ToolingLaunch launch, @NotNull State state) {
		return Snapshot.builder().source(launch.source()).state(state).build();
	}

	/**
	 * Immutable state of one catalog load, read atomically.
	 * <p>
	 * The source, scenarios, state, and failure always describe the same load, so a reader never combines one
	 * source with another source's scenarios.
	 */
	@Value
	@Builder(toBuilder = true)
	public static class Snapshot {
		/**
		 * Source of the latest load, or null before the first load.
		 */
		@Nullable ScenarioSource source;
		/**
		 * Descriptors discovered for the source; empty until a load succeeds.
		 */
		@NotNull
		@Builder.Default
		List<ScenarioDescriptor> scenarios = List.of();
		/**
		 * Lifecycle of the latest load.
		 */
		@NotNull State state;
		/**
		 * Preparation or discovery failure of the latest load, if one occurred.
		 */
		@Nullable String failure;

		/**
		 * Reports whether this snapshot holds a successful load of the given source.
		 */
		public boolean isReadyFor(@NotNull ScenarioSource candidate) {
			return state == State.READY && isFor(candidate);
		}

		/**
		 * Reports whether this snapshot describes a load of the given source, in any state.
		 */
		public boolean isFor(@NotNull ScenarioSource candidate) {
			return source != null && source.getId().equals(candidate.getId());
		}

		/**
		 * Returns the loaded scenario declared by a definition class, or null when it is not loaded.
		 */
		public @Nullable ScenarioDescriptor find(@NotNull String definition) {
			return scenarios.stream().filter(scenario -> scenario.getDefinition().equals(definition)).findFirst().orElse(null);
		}
	}

	/**
	 * Lifecycle of the selected source's catalog.
	 */
	public enum State {
		/**
		 * No catalog has been loaded for the selected source.
		 */
		NOT_LOADED,
		/**
		 * Source preparation or scenario discovery is in progress.
		 */
		LOADING,
		/**
		 * Scenario discovery completed successfully.
		 */
		READY,
		/**
		 * Preparation or discovery failed.
		 */
		FAILED
	}
}

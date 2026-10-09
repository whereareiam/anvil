package me.whereareiam.anvil.integration.intellij.tooling;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.tooling.process.ToolingConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Arbitrates reuse and replacement of the single tooling launch available to a project.
 * <p>
 * A discovery launch may be reused by an environment for the same source; its purpose then changes to
 * {@link Purpose#ENVIRONMENT} without a new launch. A replacement starts only after its predecessor
 * has released its processes and files.
 */
public final class ProjectToolingHost implements Disposable {
	private final @NotNull Project project;
	private final @NotNull ToolingConnection.Starter starter;
	private final @NotNull List<Listener> listeners = new CopyOnWriteArrayList<>();

	private volatile @Nullable Allocation allocation;
	private boolean disposed;

	public ProjectToolingHost(@NotNull Project project) {
		this(project, ProcessBuilder::start);
	}

	public ProjectToolingHost(@NotNull Project project, @NotNull ToolingConnection.Starter starter) {
		this.project = project;
		this.starter = starter;
	}

	/**
	 * Replaces the current launch with a new discovery launch for the source.
	 */
	public synchronized void reload(@NotNull ScenarioSource source) {
		ensureAvailable();
		replace(source, Purpose.DISCOVERY);
	}

	/**
	 * Reserves a launch for an environment, reusing a running discovery launch of the same source.
	 */
	public synchronized @NotNull ToolingLaunch reserve(@NotNull ScenarioSource source) {
		ensureAvailable();
		Allocation current = allocation;
		if (current != null && reusable(current.launch(), source)) {
			allocation = new Allocation(current.launch(), Purpose.ENVIRONMENT);
			return current.launch();
		}

		return replace(source, Purpose.ENVIRONMENT);
	}

	/**
	 * Stops the launch while it only serves discovery; an environment's launch is left running.
	 */
	public synchronized void cancelDiscovery(@NotNull ToolingLaunch launch) {
		if (purpose(launch) == Purpose.DISCOVERY) launch.stop();
	}

	/**
	 * Returns the launch's current purpose, or null when it is no longer the project's launch.
	 */
	public @Nullable Purpose purpose(@NotNull ToolingLaunch launch) {
		Allocation current = allocation;
		return current != null && current.launch() == launch ? current.purpose() : null;
	}

	public @Nullable ToolingLaunch current() {
		Allocation current = allocation;
		return current == null ? null : current.launch();
	}

	/**
	 * Delivers each new launch until the owner is disposed, starting with the current one.
	 */
	public synchronized void subscribe(@NotNull Listener listener, @NotNull Disposable owner) {
		listeners.add(listener);
		Disposer.register(owner, () -> listeners.remove(listener));
		if (allocation != null) listener.launched(allocation.launch(), allocation.purpose());
	}

	private static boolean reusable(@NotNull ToolingLaunch launch, @NotNull ScenarioSource source) {
		return !launch.isFinished() && !launch.isStopping() && launch.source().getId().equals(source.getId());
	}

	private ToolingLaunch replace(ScenarioSource source, Purpose purpose) {
		ToolingLaunch previous = current();
		if (previous != null && !previous.isFinished()) previous.stop();

		ToolingLaunch launch = new ToolingLaunch(source, ToolingLaunch.Inputs.of(project), starter);
		allocation = new Allocation(launch, purpose);
		RuntimeException listenerFailure = notifyLaunched(launch, purpose);
		// A launch that never starts would block every later replacement waiting for its outcome.
		launch.startAfter(previous);
		if (listenerFailure != null) throw listenerFailure;

		return launch;
	}

	/**
	 * Notifies every listener before the launch produces events, returning the first failure with later
	 * failures suppressed.
	 */
	private @Nullable RuntimeException notifyLaunched(@NotNull ToolingLaunch launch, @NotNull Purpose purpose) {
		RuntimeException first = null;
		for (Listener listener : listeners) {
			try {
				listener.launched(launch, purpose);
			} catch (RuntimeException failure) {
				if (first == null) first = failure;
				else first.addSuppressed(failure);
			}
		}

		return first;
	}

	private void ensureAvailable() {
		if (disposed || project.isDisposed()) throw new IllegalStateException("The project is closed.");
		if (allocation != null && allocation.purpose() == Purpose.ENVIRONMENT && !allocation.launch().isFinished())
			throw new IllegalStateException("Stop the current scenario before starting another.");
	}

	@Override
	public synchronized void dispose() {
		disposed = true;
		if (allocation != null) allocation.launch().stop();
		listeners.clear();
	}

	/**
	 * Observes launches created by the host.
	 */
	@FunctionalInterface
	public interface Listener {
		/**
		 * Receives a new launch before it starts, with the purpose it was created for.
		 */
		void launched(@NotNull ToolingLaunch launch, @NotNull Purpose purpose);
	}

	private record Allocation(@NotNull ToolingLaunch launch, @NotNull Purpose purpose) {
	}

	/**
	 * Why the project's single tooling launch is currently allocated.
	 */
	public enum Purpose {
		/**
		 * Loading the selected source's scenarios for the catalog; the catalog owns its output.
		 */
		DISCOVERY,
		/**
		 * Running an environment; the environment owns its output until the launch finishes.
		 */
		ENVIRONMENT
	}
}

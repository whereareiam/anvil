package me.whereareiam.anvil.integration.intellij.scenario.environment;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.scenario.tooling.ToolingHost;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the project's active environment and the sessions retained for its views.
 * <p>
 * One environment runs per project at a time. {@link #availability} says whether a scenario or one of its
 * processes can run now and, when it cannot, why; {@link #run} applies the same rule, so every entry point
 * (the catalog, the editor gutter, and run configurations) behaves alike.
 */
@Service(Service.Level.PROJECT)
@RequiredArgsConstructor
public final class EnvironmentLifecycle implements Disposable {
	private final @NotNull Project project;
	private final @NotNull List<EnvironmentSession> sessions = new CopyOnWriteArrayList<>();
	private final @NotNull ChangeListeners listeners = new ChangeListeners();
	private volatile boolean disposed;
	private volatile @Nullable EnvironmentSession active;

	/**
	 * Returns the environment lifecycle of the given project.
	 */
	public static @NotNull EnvironmentLifecycle getInstance(@NotNull Project project) {
		return project.getService(EnvironmentLifecycle.class);
	}

	/**
	 * Reports whether a scenario, or one of its processes, can run now.
	 *
	 * @param process declared process name, or null for the whole scenario
	 * @return whether running is possible, with the reason when it is not
	 */
	public @NotNull EnvironmentSession.Availability availability(
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@Nullable String process
	) {
		EnvironmentSession current = getActiveSession();
		if (current == null) {
			EnvironmentSession closing = active;
			if (closing != null && closing.isActive())
				return EnvironmentSession.Availability.unavailable("Wait until “" + closing.getScenario().getDisplayName() + "” has stopped");

			return EnvironmentSession.Availability.AVAILABLE;
		}

		if (!current.runs(source, scenario))
			return EnvironmentSession.Availability.unavailable("Stop “" + current.getScenario().getDisplayName() + "” to run another scenario");

		return current.availability(process);
	}

	/**
	 * Reports whether a scenario, or one of its processes, can be debugged now. Debugging always starts a new
	 * environment, so no other environment may be active.
	 *
	 * @param process declared process name, or null for every server and proxy of the scenario
	 * @return whether debugging is possible, with the reason when it is not
	 */
	public @NotNull EnvironmentSession.Availability debugAvailability(
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@Nullable String process
	) {
		EnvironmentSession current = getActiveSession();
		if (current != null)
			return EnvironmentSession.Availability.unavailable("Stop “" + current.getScenario().getDisplayName() + "” to debug a scenario");

		return availability(source, scenario, process);
	}

	/**
	 * Starts a scenario, or one of its processes, in a new environment and attaches the IDE's debugger to each
	 * debugged process as it starts.
	 *
	 * @param process declared process to debug and start, or null to start the scenario and debug every process
	 * @return the session that runs it
	 * @throws IllegalStateException if {@link #debugAvailability} refuses it, or the project is closed
	 */
	public synchronized @NotNull EnvironmentSession debug(
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@Nullable String process
	) {
		EnvironmentSession.Availability availability = debugAvailability(source, scenario, process);
		if (!availability.available()) throw new IllegalStateException(availability.reason() + ".");

		return open(source, scenario, process, true);
	}

	/**
	 * Runs a scenario or one of its processes: in the active environment when it runs that scenario, otherwise
	 * in a new environment.
	 *
	 * @param process declared process name, or null for the whole scenario
	 * @return the session that runs it
	 * @throws IllegalStateException if {@link #availability} refuses it, or the project is closed
	 */
	public synchronized @NotNull EnvironmentSession run(
			@NotNull ScenarioSource source,
			@NotNull ScenarioDescriptor scenario,
			@Nullable String process
	) {
		EnvironmentSession.Availability availability = availability(source, scenario, process);
		if (!availability.available()) throw new IllegalStateException(availability.reason() + ".");

		EnvironmentSession current = getActiveSession();
		if (current == null) return open(source, scenario, process, false);

		if (process == null) current.startAll();
		else current.startProcess(process);

		return current;
	}

	private EnvironmentSession open(ScenarioSource source, ScenarioDescriptor scenario, @Nullable String process, boolean debug) {
		if (disposed || project.isDisposed()) throw new IllegalStateException("The project is closed.");

		// The catalog observes launches; it must exist before a cold start allocates one.
		ScenarioCatalog.getInstance(project);
		var launch = project.getService(ToolingHost.class).reserve(source);
		var session = new EnvironmentSession(project, launch, scenario, process, debug, this::removeDisposed, this::changed);

		Disposer.register(this, session);
		sessions.add(session);
		active = session;
		session.start();
		changed();

		return session;
	}

	/**
	 * Returns retained sessions in creation order.
	 */
	public @NotNull List<EnvironmentSession> getSessions() {
		return List.copyOf(sessions);
	}

	/**
	 * Returns the session that is preparing, running, or cleaning up, if its handle is still retained.
	 */
	public @Nullable EnvironmentSession getActiveSession() {
		EnvironmentSession current = active;
		return current != null && current.isActive() && sessions.contains(current) ? current : null;
	}

	/**
	 * Reports whether an environment is preparing, running, or cleaning up, even after its tab was closed.
	 */
	public boolean hasActiveSession() {
		EnvironmentSession current = active;
		return current != null && current.isActive();
	}

	/**
	 * Calls the listener on the IDE event thread after each change, until the owner is disposed.
	 */
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener, owner);
	}

	private void removeDisposed() {
		sessions.removeIf(EnvironmentSession::isDisposed);
		changed();
	}

	private void changed() {
		listeners.notifyLater(() -> !disposed && !project.isDisposed());
	}

	@Override
	public void dispose() {
		disposed = true;
		listeners.clear();
		sessions.clear();
	}
}

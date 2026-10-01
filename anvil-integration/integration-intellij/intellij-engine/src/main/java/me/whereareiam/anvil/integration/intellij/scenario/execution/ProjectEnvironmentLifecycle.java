package me.whereareiam.anvil.integration.intellij.scenario.execution;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.ScenarioCatalog;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.tooling.ProjectToolingHost;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the active environment and the retained handles exposed to the project UI.
 */
@RequiredArgsConstructor
public final class ProjectEnvironmentLifecycle implements EnvironmentLifecycle, Disposable {
	private final @NotNull Project project;
	private final @NotNull List<RetainedEnvironmentSession> sessions = new CopyOnWriteArrayList<>();
	private final @NotNull ChangeListeners listeners = new ChangeListeners();
	private volatile boolean disposed;
	private volatile @Nullable RetainedEnvironmentSession active;

	@Override
	public synchronized @NotNull RetainedEnvironmentSession start(
			@NotNull ScenarioSource source, @NotNull ScenarioDescriptor scenario
	) {
		return open(source, scenario, null);
	}

	@Override
	public synchronized @NotNull RetainedEnvironmentSession startProcess(
			@NotNull ScenarioSource source, @NotNull ScenarioDescriptor scenario, @NotNull String process
	) {
		return open(source, scenario, process);
	}

	private RetainedEnvironmentSession open(ScenarioSource source, ScenarioDescriptor scenario, @Nullable String process) {
		if (disposed || project.isDisposed()) throw new IllegalStateException("The project is closed.");
		if (hasActiveSession()) throw new IllegalStateException("Stop the current scenario before starting another.");

		// Initialize the catalog observer before allocating a launch, including a cold environment start.
		project.getService(ScenarioCatalog.class);
		var launch = project.getService(ProjectToolingHost.class).reserve(source);
		var session = new RetainedEnvironmentSession(project, launch, scenario, process, this::removeDisposed, this::changed);

		Disposer.register(this, session);
		sessions.add(session);
		active = session;
		session.start();
		changed();

		return session;
	}

	@Override
	public @NotNull List<RetainedEnvironmentSession> getSessions() {
		return List.copyOf(sessions);
	}

	@Override
	public @Nullable RetainedEnvironmentSession getActiveSession() {
		RetainedEnvironmentSession current = active;
		return current != null && current.isActive() && sessions.contains(current) ? current : null;
	}

	@Override
	public boolean hasActiveSession() {
		RetainedEnvironmentSession current = active;
		return current != null && current.isActive();
	}

	@Override
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener, owner);
	}

	private void removeDisposed() {
		sessions.removeIf(RetainedEnvironmentSession::isDisposed);
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

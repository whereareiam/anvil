package me.whereareiam.anvil.integration.intellij.scenario.environment.view.services;

import com.intellij.execution.services.ServiceEventListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.EnvironmentTab;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the Services view in step with the project's environments, and the view each one shows there.
 * <p>
 * Created when the Services view first lists environments. Each environment's view is created once and
 * released with its session; the Anvil tool window's tab, not this view, decides when a session ends.
 */
@Service(Service.Level.PROJECT)
public final class EnvironmentServices implements Disposable {
	private final @NotNull Project project;
	private final Map<String, EnvironmentTab> views = new HashMap<>();
	private final Map<String, Disposable> subscriptions = new HashMap<>();

	public EnvironmentServices(@NotNull Project project) {
		this.project = project;
		EnvironmentLifecycle.getInstance(project).subscribe(this::sessionsChanged, this);
	}

	/**
	 * Returns the services of the given project.
	 */
	public static @NotNull EnvironmentServices getInstance(@NotNull Project project) {
		return project.getService(EnvironmentServices.class);
	}

	/**
	 * Returns the retained environments.
	 */
	@NotNull List<EnvironmentSession> sessions() {
		return EnvironmentLifecycle.getInstance(project).getSessions();
	}

	/**
	 * Returns the view the Services view shows for an environment, creating it once.
	 */
	synchronized @NotNull EnvironmentTab view(@NotNull EnvironmentSession session) {
		EnvironmentTab existing = views.get(session.getId());
		if (existing != null) return existing;

		var view = new EnvironmentTab(project, session);
		view.consolePresentation().start();
		views.put(session.getId(), view);
		Disposer.register(session, view);
		Disposer.register(view, () -> forget(session.getId(), view));

		return view;
	}

	/**
	 * Refreshes an environment's row in the Services view after each of its changes, until it ends.
	 */
	synchronized void observe(@NotNull EnvironmentSession session) {
		if (subscriptions.containsKey(session.getId())) return;

		Disposable subscription = Disposer.newDisposable("Anvil Services view subscription");
		Disposer.register(session, subscription);
		Disposer.register(subscription, () -> {
			synchronized (this) {
				subscriptions.remove(session.getId());
			}
		});
		subscriptions.put(session.getId(), subscription);
		session.subscribe(() -> publish(ServiceEventListener.ServiceEvent.createEvent(
				ServiceEventListener.EventType.SERVICE_CHANGED, session, EnvironmentServiceContributor.class)), subscription);
	}

	private synchronized void forget(@NotNull String id, @NotNull EnvironmentTab view) {
		views.remove(id, view);
	}

	private void sessionsChanged() {
		publish(ServiceEventListener.ServiceEvent.createResetEvent(EnvironmentServiceContributor.class));
	}

	private void publish(@NotNull ServiceEventListener.ServiceEvent event) {
		if (!project.isDisposed()) project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(event);
	}

	@Override
	public synchronized void dispose() {
		views.clear();
		subscriptions.clear();
	}
}

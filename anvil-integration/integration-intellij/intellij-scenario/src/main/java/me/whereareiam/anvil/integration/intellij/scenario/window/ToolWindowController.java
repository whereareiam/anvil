package me.whereareiam.anvil.integration.intellij.scenario.window;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.scenario.ScenarioNotifications;
import me.whereareiam.anvil.integration.intellij.scenario.catalog.view.CatalogPanel;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.environment.view.EnvironmentTab;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettings;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;

/**
 * Owns the Anvil tool window's catalog tab and one closable tab per retained environment session.
 */
public final class ToolWindowController implements Disposable {
	private static final Key<ToolWindowController> KEY = Key.create("anvil.toolWindowController");

	private final @NotNull Project project;
	private final @NotNull ToolWindow window;
	private final @NotNull ContentManager contents;
	private final @NotNull EnvironmentLifecycle environments;
	private final Map<String, SessionTab> tabs = new LinkedHashMap<>();
	private final Set<String> closedSessions = new HashSet<>();
	private final Map<String, Integer> nameCounts = new HashMap<>();
	private boolean disposed;

	ToolWindowController(@NotNull Project project, @NotNull ToolWindow window) {
		this(project, window, window.getContentManager());
	}

	ToolWindowController(@NotNull Project project, @NotNull ToolWindow window, @NotNull ContentManager contents) {
		this.project = project;
		this.window = window;
		this.contents = contents;
		environments = EnvironmentLifecycle.getInstance(project);
		Disposer.register(contents, this);
		ClientProperty.put(window.getComponent(), KEY, this);

		var catalog = new CatalogPanel(project);
		Content content = contents.getFactory().createContent(catalog, "Scenarios", false);
		content.setCloseable(false);
		content.setDisposer(catalog);
		contents.addContent(content);
		contents.addContentManagerListener(new ContentManagerListener() {
			@Override
			public void contentRemoveQuery(@NotNull ContentManagerEvent event) {
				if (!confirmClose(event.getContent())) event.consume();
			}

			@Override
			public void contentRemoved(@NotNull ContentManagerEvent event) {
				tabClosed(event.getContent());
			}
		});

		ScenarioSettings.getInstance().subscribe(this::pruneCompletedTabs, this);
		environments.subscribe(this::updateTabs, this);
		updateTabs();
	}

	/**
	 * Shows the Anvil tool window with the session's tab selected, creating the tab when it was closed.
	 */
	public static void reveal(@NotNull Project project, @NotNull EnvironmentSession session) {
		ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(AnvilToolWindowFactory.ID);
		if (window == null) return;

		window.show(() -> {
			ToolWindowController controller = ClientProperty.get(window.getComponent(), KEY);
			if (controller != null) controller.select(session, false);
		});
	}

	private void select(@NotNull EnvironmentSession session, boolean console) {
		if (disposed || contents.isDisposed()) return;

		closedSessions.remove(session.getId());
		SessionTab tab = tabs.get(session.getId());
		Content content = tab == null ? addSession(session) : tab.content;
		contents.setSelectedContent(content, true);
		if (console) tabs.get(session.getId()).panel.revealConsole();
	}

	/**
	 * Closing a tab disposes its session, which stops an environment that is still active. Ask first.
	 */
	private boolean confirmClose(@NotNull Content content) {
		SessionTab entry = tabs.values().stream().filter(candidate -> candidate.content == content).findFirst().orElse(null);
		if (entry == null || !entry.session.isActive()) return true;

		return Messages.showYesNoDialog(
				project,
				"Closing this tab stops “" + content.getDisplayName() + "” and its processes.",
				"Stop Environment",
				"Stop and Close",
				"Keep Running",
				Messages.getWarningIcon()
		) == Messages.YES;
	}

	private void tabClosed(@NotNull Content content) {
		tabs.entrySet().removeIf(entry -> {
			if (entry.getValue().content != content) return false;
			closedSessions.add(entry.getKey());
			return true;
		});
	}

	private void updateTabs() {
		if (disposed || contents.isDisposed()) return;

		List<EnvironmentSession> sessions = environments.getSessions();
		// A closed tab stays closed only while its session is retained.
		closedSessions.retainAll(sessions.stream().map(EnvironmentSession::getId).collect(Collectors.toSet()));
		for (EnvironmentSession session : sessions)
			if (!closedSessions.contains(session.getId()) && !tabs.containsKey(session.getId())) addSession(session);
	}

	@NotNull Content addSession(@NotNull EnvironmentSession session) {
		String name = session.getScenario().getDisplayName();
		int count = nameCounts.merge(name, 1, Integer::sum);
		var panel = new EnvironmentTab(project, session);
		panel.consolePresentation().start();

		Content content = contents.getFactory().createContent(panel, title(session, count), false);
		content.setCloseable(true);
		content.setDisposer(panel);

		SessionTab entry = new SessionTab(session, panel, content);
		tabs.put(session.getId(), entry);
		contents.addContent(content);
		session.subscribe(() -> {
			content.setDisplayName(title(session, count));
			updateTab(entry);
		}, panel);
		updateTab(entry);

		return content;
	}

	private void updateTab(@NotNull SessionTab entry) {
		if (disposed || contents.isDisposed() || !tabs.containsKey(entry.session.getId())) return;

		if (!entry.failureObserved && entry.session.getSnapshot().getState() == SessionState.FAILED) {
			entry.failureObserved = true;
			failed(entry);
		}

		pruneCompletedTabs();
	}

	private void failed(@NotNull SessionTab entry) {
		boolean inView = window.isVisible() && contents.getSelectedContent() == entry.content;
		if (ScenarioSettings.getInstance().values().isRevealConsoleOnFailure()) {
			entry.panel.revealConsole();
			contents.setSelectedContent(entry.content, false);
			if (!window.isVisible()) window.show(null);
			inView = true;
		}
		if (inView) return;

		String failure = entry.session.getSnapshot().getFailure();
		ScenarioNotifications.error(
				project,
				"“" + entry.session.getScenario().getDisplayName() + "” failed",
				failure == null ? "Open its console for details." : failure,
				ScenarioNotifications.action("Show Console", () -> window.show(() -> select(entry.session, true))));
	}

	private void pruneCompletedTabs() {
		if (disposed || contents.isDisposed()) return;

		int limit = ScenarioSettings.getInstance().values().getCompletedTabs();
		if (limit == 0) return;

		List<SessionTab> successful = tabs.values().stream().filter(ToolWindowController::completedSuccessfully).toList();
		for (int index = 0; index < successful.size() - limit; index++)
			contents.removeContent(successful.get(index).content, true);
	}

	private static boolean completedSuccessfully(@NotNull SessionTab entry) {
		EnvironmentSession session = entry.session;
		ProcessHandler handler = entry.panel.consolePresentation().getProcessHandler();
		Integer exitCode = handler.getExitCode();

		return !entry.failureObserved
				&& !session.isActive()
				&& handler.isProcessTerminated()
				&& exitCode != null
				&& exitCode == 0
				&& session.getSnapshot().getState() == SessionState.STOPPED
				&& session.getSnapshot().getFailure() == null;
	}

	private static @NotNull String title(@NotNull EnvironmentSession session, int count) {
		return session.getScenario().getDisplayName() + (count > 1 ? " (" + count + ")" : "");
	}

	@Override
	public void dispose() {
		disposed = true;
		tabs.clear();
		closedSessions.clear();
		if (ClientProperty.get(window.getComponent(), KEY) == this) ClientProperty.put(window.getComponent(), KEY, null);
	}

	@RequiredArgsConstructor
	private static final class SessionTab {
		private final @NotNull EnvironmentSession session;
		private final @NotNull EnvironmentTab panel;
		private final @NotNull Content content;
		private boolean failureObserved;
	}
}

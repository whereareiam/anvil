package me.whereareiam.anvil.integration.intellij.view.window.main;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.content.ContentManagerListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentLifecycle;
import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.view.window.main.catalog.ScenarioCatalogPanel;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.EnvironmentSessionPanel;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import org.jetbrains.annotations.NotNull;

/**
 * Owns the Anvil tool window's catalog tab and one closable tab per retained environment session.
 */
final class MainWindowController implements Disposable {
	private final Preferences preferences = ApplicationManager.getApplication().getService(Preferences.class);

	private final ContentManager contents;
	private final Project project;
	private final EnvironmentLifecycle environments;

	private final Map<String, SessionTab> tabs = new LinkedHashMap<>();
	private final Set<String> closedSessions = new HashSet<>();
	private final Map<String, Integer> nameCounts = new HashMap<>();
	private boolean disposed;

	MainWindowController(Project project, ContentManager contents) {
		this.contents = contents;
		this.project = project;
		environments = project.getService(EnvironmentLifecycle.class);

		Disposer.register(contents, this);
		var scenarios = new ScenarioCatalogPanel(project, this::start, this::startProcess);

		Content content = contents.getFactory().createContent(scenarios, "Scenarios", false);
		content.setCloseable(false);
		content.setDisposer(scenarios);

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

		preferences.subscribe(this::pruneCompletedTabs, this);
		environments.subscribe(this::updateTabs, this);
		updateTabs();
	}

	EnvironmentSession start(ScenarioSource source, ScenarioDescriptor scenario) {
		EnvironmentSession active = matchingRun(source, scenario);
		if (active != null) {
			if (active.getSnapshot().getState() != SessionState.RUNNING
					|| !ScenarioPresentation.canStartScenario(scenario, active.getSnapshot())) {
				throw new IllegalStateException("This scenario is already starting or running.");
			}

			active.startAll();
			contents.setSelectedContent(addSession(active));
			return active;
		}

		EnvironmentSession session = environments.start(source, scenario);
		Content content = addSession(session);
		contents.setSelectedContent(content);

		return session;
	}

	void startProcess(
			ScenarioSource source,
			ScenarioDescriptor scenario,
			String processName
	) {
		EnvironmentSession active = matchingRun(source, scenario);
		if (active != null) {
			active.startProcess(processName);
			contents.setSelectedContent(addSession(active));
			return;
		}

		EnvironmentSession session = environments.startProcess(source, scenario, processName);
		contents.setSelectedContent(addSession(session));
	}

	private EnvironmentSession matchingRun(ScenarioSource source, ScenarioDescriptor scenario) {
		EnvironmentSession active = environments.getActiveSession();
		if (active == null || !ScenarioPresentation.runs(active, source, scenario)) return null;

		return active;
	}

	/**
	 * Closing a tab disposes its session, which stops an environment that is still active. Ask first.
	 */
	private boolean confirmClose(Content content) {
		SessionTab entry = tabs.values().stream()
				.filter(candidate -> candidate.content == content)
				.findFirst()
				.orElse(null);
		if (entry == null || !entry.session.isActive()) return true;

		String message = "Closing this tab stops \"" + content.getDisplayName() + "\" and its processes.";

		return Messages.showYesNoDialog(
				project,
				message,
				"Stop Environment",
				"Stop and Close",
				"Keep Running",
				Messages.getWarningIcon()
		) == Messages.YES;
	}

	private void tabClosed(Content content) {
		tabs.entrySet().removeIf(entry -> {
			if (entry.getValue().content != content) return false;
			closedSessions.add(entry.getKey());
			return true;
		});
	}

	private void updateTabs() {
		if (disposed || contents.isDisposed()) return;
		List<? extends EnvironmentSession> sessions = environments.getSessions();
		// A closed tab stays closed only while its session is retained.
		closedSessions.retainAll(sessions.stream().map(EnvironmentSession::getId).collect(Collectors.toSet()));
		for (EnvironmentSession session : sessions)
			if (!closedSessions.contains(session.getId())) addSession(session);
	}

	Content addSession(EnvironmentSession session) {
		SessionTab existing = tabs.get(session.getId());
		if (existing != null) return existing.content;

		String name = session.getScenario().getDisplayName();
		int count = nameCounts.merge(name, 1, Integer::sum);
		var panel = new EnvironmentSessionPanel(project, session);
		panel.consolePresentation().start();

		Content content = contents.getFactory().createContent(panel, title(session, count), false);
		content.setCloseable(true);
		content.setDisposer(panel);

		SessionTab entry = new SessionTab(session, panel, content);
		tabs.put(session.getId(), entry);
		contents.addContent(content);

		session.subscribe(
				() -> {
					content.setDisplayName(title(session, count));
					updateTab(entry);
				},
				panel);

		updateTab(entry);

		return content;
	}

	private void updateTab(SessionTab entry) {
		if (disposed || contents.isDisposed() || !tabs.containsKey(entry.session.getId())) return;
		if (!entry.failureObserved && entry.session.getSnapshot().getState() == SessionState.FAILED) {
			entry.failureObserved = true;
			if (preferences.snapshot().isShowConsoleOnFailure()) {
				entry.panel.revealConsole();
				contents.setSelectedContent(entry.content, false);
				var window = ToolWindowManager.getInstance(project).getToolWindow(AnvilToolWindowFactory.ID);
				if (window != null && !window.isVisible()) window.show(null);
			}
		}

		pruneCompletedTabs();
	}

	private void pruneCompletedTabs() {
		if (disposed || contents.isDisposed()) return;
		int limit = preferences.snapshot().getCompletedSuccessfulTabs();
		if (limit == 0) return;

		List<SessionTab> successful = tabs.values().stream()
				.filter(MainWindowController::completedSuccessfully)
				.toList();

		for (int index = 0; index < successful.size() - limit; index++)
			contents.removeContent(successful.get(index).content, true);
	}

	private static boolean completedSuccessfully(SessionTab entry) {
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

	private static String title(EnvironmentSession session, int count) {
		return session.getScenario().getDisplayName() + (count > 1 ? " (" + count + ")" : "");
	}

	@Override
	public void dispose() {
		disposed = true;
		tabs.clear();
		closedSessions.clear();
	}

	@RequiredArgsConstructor
	private static final class SessionTab {
		private final EnvironmentSession session;
		private final EnvironmentSessionPanel panel;
		private final Content content;
		private boolean failureObserved;
	}
}

package me.whereareiam.anvil.integration.intellij.view.window.main.environment;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.settings.Preferences;
import me.whereareiam.anvil.integration.intellij.type.settings.SessionSection;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Owns session subscriptions, remembered section selection, and disposal.
 */
final class EnvironmentSessionController implements Disposable {
	private static final @NotNull List<SessionSection> SECTIONS = List.of(
			SessionSection.ENVIRONMENT,
			SessionSection.CONSOLE,
			SessionSection.PLAYERS
	);

	private final @NotNull EnvironmentSession session;
	private final @NotNull EnvironmentViewState viewState;
	private final @NotNull EnvironmentSessionPanel panel;
	private boolean changingSection = true;
	private boolean disposed;

	EnvironmentSessionController(
			@NotNull Project project,
			@NotNull EnvironmentSession session,
			@NotNull EnvironmentSessionPanel panel
	) {
		this.session = session;
		this.panel = panel;
		viewState = project.getService(EnvironmentViewState.class);

		installTabNavigation();
		selectInitialSection(project);
		changingSection = false;
		session.subscribe(this::update, panel);
		update();
	}

	private void installTabNavigation() {
		panel.tabs().registerNavigation(panel, panel);
		panel.tabs().addChangeListener(event -> sectionChanged());
	}

	private void selectInitialSection(@NotNull Project project) {
		Preferences preferences = ApplicationManager.getApplication().getService(Preferences.class);
		SessionSection configured = preferences.snapshot().getDefaultSessionSection();
		SessionSection initial = configured == SessionSection.LAST_USED
				? viewState.getLastSection()
				: configured;
		panel.tabs().setSelectedIndex(SECTIONS.indexOf(initial));
	}

	private void sectionChanged() {
		int selected = panel.tabs().getSelectedIndex();
		if (selected < 0) return;

		panel.showSection(selected);
		if (!changingSection) viewState.rememberSection(SECTIONS.get(selected));
	}

	private void update() {
		if (disposed) return;

		ScenarioDescriptor scenario = session.getScenario();
		SessionSnapshot snapshot = session.getSnapshot();
		panel.status.update(scenario, snapshot, session.getEnvironmentState());
		panel.environment.update();
		panel.console.update();
		panel.players.update();
	}

	void revealConsole() {
		if (disposed) return;

		changingSection = true;
		try {
			panel.tabs().setSelectedIndex(SECTIONS.indexOf(SessionSection.CONSOLE));
		} finally {
			changingSection = false;
		}
	}

	@Override
	public void dispose() {
		if (disposed) return;
		disposed = true;
		Disposer.dispose(session);
	}
}

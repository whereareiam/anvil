package me.whereareiam.anvil.integration.intellij.scenario.environment.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettings;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Owns an environment tab's session subscription and its remembered section. The tab shows the session; whoever
 * created the tab decides when the session ends.
 */
final class EnvironmentTabController implements Disposable {
	private static final @NotNull List<ScenarioSettings.Section> SECTIONS = List.of(
			ScenarioSettings.Section.ENVIRONMENT,
			ScenarioSettings.Section.CONSOLE,
			ScenarioSettings.Section.PLAYERS
	);

	private final @NotNull EnvironmentSession session;
	private final @NotNull SectionMemory viewState;
	private final @NotNull EnvironmentTab panel;
	private boolean changingSection = true;
	private boolean disposed;

	EnvironmentTabController(
			@NotNull Project project,
			@NotNull EnvironmentSession session,
			@NotNull EnvironmentTab panel
	) {
		this.session = session;
		this.panel = panel;
		viewState = project.getService(SectionMemory.class);

		installTabNavigation();
		selectInitialSection();
		changingSection = false;
		session.subscribe(this::update, panel);
		update();
	}

	private void installTabNavigation() {
		panel.tabs().registerNavigation(panel, panel);
		panel.tabs().addChangeListener(event -> sectionChanged());
	}

	private void selectInitialSection() {
		ScenarioSettings.Section configured = ScenarioSettings.getInstance().values().getOpeningSection();
		ScenarioSettings.Section initial = configured == ScenarioSettings.Section.LAST_USED
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
		panel.status.update(scenario, snapshot, session.getStatus());
		panel.environment.update();
		panel.console.update();
		panel.players.update();
	}

	void revealConsole() {
		if (disposed) return;

		changingSection = true;
		try {
			panel.tabs().setSelectedIndex(SECTIONS.indexOf(ScenarioSettings.Section.CONSOLE));
		} finally {
			changingSection = false;
		}
	}

	@Override
	public void dispose() {
		disposed = true;
	}
}

package me.whereareiam.anvil.integration.intellij.view.window.main.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.HeadlessDataManager;
import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.SwingUtilities;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.component.console.ConsolePresentation;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.execution.RetainedEnvironmentSession;
import me.whereareiam.anvil.integration.intellij.settings.PersistentPreferences;
import me.whereareiam.anvil.integration.intellij.type.settings.SessionSection;
import me.whereareiam.anvil.integration.intellij.view.window.main.component.details.DefinitionDetailsPanel;
import me.whereareiam.anvil.integration.intellij.view.window.main.component.status.StatusBadge;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.console.ConsolePanel;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.overview.EnvironmentPanel;
import me.whereareiam.anvil.integration.intellij.view.window.main.environment.players.PlayersPanel;
import me.whereareiam.anvil.tooling.api.model.PlayerDescriptor;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.action.binding.*;
import me.whereareiam.anvil.tooling.api.model.action.definition.*;
import me.whereareiam.anvil.tooling.api.model.action.invocation.*;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationValue;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;

public class EnvironmentSessionPanelPlatformTest extends UiPlatformTestCase {
	private PersistentPreferences.PreferenceState savedSettings;

	@Override
	protected boolean isIconRequired() {
		return true;
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		savedSettings = PersistentPreferences.getInstance().getState();
		PersistentPreferences.getInstance().loadState(new PersistentPreferences.PreferenceState());
		getProject().getService(EnvironmentViewState.class).loadState(new EnvironmentViewState.Options());
		// Native tab navigation and overflow actions read the component's actual UI data provider.
		HeadlessDataManager.fallbackToProductionDataManager(getTestRootDisposable());
	}

	@Override
	protected void tearDown() throws Exception {
		try {
			PersistentPreferences.getInstance().loadState(savedSettings);
		} finally {
			super.tearDown();
		}
	}

	public void testNewRunUsesLastUserSectionAndExplicitChoiceWithoutOverwritingHistory() {
		EnvironmentSessionPanel first = panel(createRun());
		assertEquals("Environment", first.tabs().getTitleAt(first.tabs().getSelectedIndex()));
		first.tabs().setSelectedIndex(2);
		EnvironmentSessionPanel remembered = panel(createRun());
		assertEquals("Players", remembered.tabs().getTitleAt(remembered.tabs().getSelectedIndex()));
		for (SessionSection section :
				List.of(SessionSection.ENVIRONMENT, SessionSection.CONSOLE, SessionSection.PLAYERS)) {
			PersistentPreferences.getInstance().setDefaultSessionSection(section);
			EnvironmentSessionPanel explicit = panel(createRun());
			assertEquals(
					section.toString(), explicit.tabs().getTitleAt(explicit.tabs().getSelectedIndex()));
		}
		assertEquals(
				SessionSection.PLAYERS, getProject().getService(EnvironmentViewState.class).getLastSection());
		PersistentPreferences.getInstance().setDefaultSessionSection(SessionSection.LAST_USED);
		EnvironmentSessionPanel restored = panel(createRun());
		assertEquals("Players", restored.tabs().getTitleAt(restored.tabs().getSelectedIndex()));
	}

	public void testAutomaticConsoleRevealDoesNotReplaceManualSectionPreference() {
		EnvironmentSessionPanel first = panel(createRun());
		first.tabs().setSelectedIndex(2);
		first.revealConsole();
		assertEquals("Console", first.tabs().getTitleAt(first.tabs().getSelectedIndex()));
		assertEquals(
				SessionSection.PLAYERS, getProject().getService(EnvironmentViewState.class).getLastSection());
		first.tabs().setSelectedIndex(0);
		assertEquals(
				SessionSection.ENVIRONMENT,
				getProject().getService(EnvironmentViewState.class).getLastSection());
	}

	public void testLastSectionStateCopiesItsBeanAndUsesWorkspaceStorage() {
		EnvironmentViewState state = getProject().getService(EnvironmentViewState.class);
		EnvironmentViewState.Options loaded = new EnvironmentViewState.Options();
		loaded.setLastSection(SessionSection.CONSOLE);
		state.loadState(loaded);
		loaded.setLastSection(SessionSection.PLAYERS);
		assertEquals(SessionSection.CONSOLE, state.getLastSection());
		state.getState().setLastSection(SessionSection.ENVIRONMENT);
		assertEquals(SessionSection.CONSOLE, state.getLastSection());
		EnvironmentViewState restored = new EnvironmentViewState();
		restored.loadState(state.getState());
		assertEquals(SessionSection.CONSOLE, restored.getLastSection());
		assertEquals(
				com.intellij.openapi.components.StoragePathMacros.WORKSPACE_FILE,
				EnvironmentViewState.class
						.getAnnotation(com.intellij.openapi.components.State.class)
						.storages()[0]
						.value());
	}

	public void testRunHasDistinctEnvironmentConsoleAndPlayersViews() {
		RetainedEnvironmentSession run = createRun();
		EnvironmentSessionPanel panel = panel(run);
		JBTabbedPane tabs = panel.tabs();
		assertEquals(
				List.of("Environment", "Console", "Players"),
				List.of(tabs.getTitleAt(0), tabs.getTitleAt(1), tabs.getTitleAt(2)));
		assertTrue(WindowTestSupport.text(panel).contains("127.0.0.1:25565"));
		selectProcess(panel, 1);
		assertTrue(WindowTestSupport.text(panel).contains("Pinned Paper distribution"));
		tabs.setSelectedIndex(2);
		assertEquals(
				"Inspect player",
				((ActionDescriptor)
								WindowTestSupport.find(
												WindowTestSupport.find(panel, PlayersPanel.class),
												javax.swing.JComboBox.class)
										.getSelectedItem())
						.getDefinition()
						.getDisplayName());
	}

	public void testContributedObservationUpdatesDoNotResetEnvironmentTextSelection() {
		RetainedEnvironmentSession run = createRun();
		EnvironmentSessionPanel panel = panel(run);
		var details = WindowTestSupport.find(panel, DefinitionDetailsPanel.class);
		var text = WindowTestSupport.find(details, javax.swing.JTextArea.class);
		text.select(0, Math.min(4, text.getText().length()));
		String selected = text.getSelectedText();

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.observations(
								List.of(
										ObservationDescriptor.builder()
												.definition(ObservationDefinition.builder().id("fixture.progress").build())
												.target(
														ActionTarget.builder()
																.type(ActionTargetType.SCENARIO)
																.name("registration")
																.build())
												.value(ObservationValue.builder().text("updated").build())
												.build()))
						.build());
		UIUtil.dispatchAllInvocationEvents();
		assertSame(text, WindowTestSupport.find(details, javax.swing.JTextArea.class));
		assertEquals(selected, text.getSelectedText());
	}

	public void testCompletedTabRetainsDefinitionsAndFilteredOutput() {
		RetainedEnvironmentSession run = createRun();
		EnvironmentSessionPanel panel = panel(run);
		selectProcess(panel, 1);
		EnvironmentSessionFixture.append(run, null, "Preparation complete\n", false);
		EnvironmentSessionFixture.append(run, "paper", "Alice registered\n", false);
		EnvironmentSessionFixture.append(run, "proxy", "Proxy transfer complete\n", false);

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().state(SessionState.STOPPED).build());
		EnvironmentSessionFixture.finish(run, 0);
		UIUtil.dispatchAllInvocationEvents();
		assertTrue(WindowTestSupport.text(panel).contains("Pinned Paper distribution"));
		assertFalse(WindowTestSupport.actionEnabled(panel, "Restart process"));
		assertFalse(WindowTestSupport.actionEnabled(panel, "Stop process"));
		panel.tabs().setSelectedIndex(1);
		var consolePanel = WindowTestSupport.find(panel, ConsolePanel.class);
		JList<?> sources = WindowTestSupport.find(consolePanel, JList.class);
		sources.setSelectedIndex(1);
		ConsoleViewImpl console = (ConsoleViewImpl) panel.consolePresentation().getConsole();
		console.waitAllRequests();
		String text = console.getEditor().getDocument().getText();
		assertTrue(text.contains("Preparation complete"));
		assertTrue(text.contains("Alice registered"));
		assertFalse(text.contains("Proxy transfer complete"));
	}

	public void testNarrowHeaderCompactsTheStatusBadgeInsteadOfClippingSections() throws Exception {
		EnvironmentSessionPanel panel = panel(createRun());

		WindowTestSupport.capture(panel, "anvil-session-header-wide.png", 1000, 300);
		assertFalse(panel.status.getCompact());
		assertNotNull(panel.status.getText());

		WindowTestSupport.capture(panel, "anvil-session-header-narrow.png", 320, 300);
		assertTrue(panel.status.getCompact());
		assertNull(panel.status.getText());
		assertNotNull(panel.status.getToolTipText());
		var tabs = panel.tabs();
		assertTrue(tabs.getX() + tabs.runWidth() <= tabs.getParent().getWidth());
	}

	public void testRemovedPlayerRemainsInspectableWithoutAcceptingCommands() {
		RetainedEnvironmentSession run = createRun();
		EnvironmentSessionPanel panel = panel(run);
		panel.tabs().setSelectedIndex(2);
		PlayersPanel players = WindowTestSupport.find(panel, PlayersPanel.class);
		assertTrue(WindowTestSupport.button(players, "Open action…").isEnabled());

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.players(List.of())
						.actions(List.of())
						.observations(List.of())
						.build());
		UIUtil.dispatchAllInvocationEvents();
		assertTrue(WindowTestSupport.text(players).contains("No longer in this environment"));
		assertFalse(WindowTestSupport.button(players, "Open action…").isEnabled());
		@SuppressWarnings("unchecked")
		JList<PlayerDescriptor> list = WindowTestSupport.find(players, JList.class);
		Component row = list.getCellRenderer()
				.getListCellRendererComponent(list, list.getModel().getElementAt(0), 0, false, false);
		assertTrue(((JComponent) row).getToolTipText().contains("No longer in this environment"));
	}

	public void testProcessControlsFollowIndependentLifecycleState() {
		RetainedEnvironmentSession run = createRun();
		EnvironmentSessionPanel panel = panel(run);
		JTree tree = WindowTestSupport.find(panel, JTree.class);
		SessionSnapshot ready = run.getSnapshot();
		tree.expandRow(0);
		tree.setSelectionRow(1);
		assertFalse(WindowTestSupport.actionEnabled(panel, "Start server"));
		assertTrue(WindowTestSupport.actionEnabled(panel, "Stop process"));
		assertTrue(WindowTestSupport.actionEnabled(panel, "Restart process"));
		var stopped = run.getSnapshot().getProcesses().getFirst().toBuilder().state(ProcessState.STOPPED).build();

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().processes(List.of(stopped)).build());
		UIUtil.dispatchAllInvocationEvents();
		assertTrue(WindowTestSupport.actionEnabled(panel, "Start server"));
		assertFalse(WindowTestSupport.actionEnabled(panel, "Stop process"));
		assertFalse(WindowTestSupport.actionEnabled(panel, "Restart process"));
		tree.setSelectionRow(0);
		assertTrue(WindowTestSupport.actionEnabled(panel, "Start scenario"));

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().setupComplete(true).build());
		UIUtil.dispatchAllInvocationEvents();
		assertTrue(
				"Start scenario must restart remaining components without repeating setup",
				WindowTestSupport.actionEnabled(panel, "Start scenario"));
		EnvironmentSessionFixture.update(run, ready.toBuilder().setupComplete(true).build());
		UIUtil.dispatchAllInvocationEvents();
		assertFalse(WindowTestSupport.actionEnabled(panel, "Start scenario"));
	}

	public void testStandaloneRunKeepsOnlyItsProcessLeaf() {
		var scenario =
				WindowTestSupport.scenario().toBuilder()
						.clearProcesses()
						.process(WindowTestSupport.scenario().getProcesses().getFirst())
						.build();
		var run =
				EnvironmentSessionFixture.create(
						getProject(), WindowTestSupport.source(), scenario, getTestRootDisposable());
		EnvironmentSessionPanel panel = panel(run);
		JTree tree = WindowTestSupport.find(panel, JTree.class);
		assertEquals(1, tree.getRowCount());
		assertEquals(0, tree.getModel().getChildCount(tree.getModel().getRoot()));
		assertTrue(WindowTestSupport.text(panel).contains("Pinned Paper distribution"));
		assertFalse(WindowTestSupport.actionEnabled(panel, "Start scenario"));
	}

	public void testScenarioStopRemainsAvailableForPartialAndPreparedEnvironments() {
		RetainedEnvironmentSession run = createRun();
		EnvironmentSessionPanel panel = panel(run);
		JTree tree = WindowTestSupport.find(panel, JTree.class);
		var onlyServer = run.getSnapshot().getProcesses().getFirst();

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().processes(List.of(onlyServer)).build());
		UIUtil.dispatchAllInvocationEvents();
		tree.setSelectionRow(0);
		assertTrue(
				"The root must stop the environment even when only one component is started",
				WindowTestSupport.actionEnabled(panel, "Stop scenario"));
		tree.expandRow(0);
		tree.setSelectionRow(2);
		assertFalse(
				"The unstarted proxy has no process to stop",
				WindowTestSupport.actionEnabled(panel, "Stop process"));
		tree.setSelectionRow(0);

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.processes(List.of(onlyServer.toBuilder().state(ProcessState.STOPPED).build()))
						.build());
		UIUtil.dispatchAllInvocationEvents();
		assertTrue(
				"Stopping the root must still release prepared workspaces and other owned resources",
				WindowTestSupport.actionEnabled(panel, "Stop scenario"));

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().state(SessionState.STOPPING).build());
		UIUtil.dispatchAllInvocationEvents();
		assertFalse(WindowTestSupport.actionEnabled(panel, "Stop scenario"));

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().state(SessionState.STOPPED).build());
		EnvironmentSessionFixture.finish(run, 0);
		UIUtil.dispatchAllInvocationEvents();
		assertFalse(WindowTestSupport.actionEnabled(panel, "Stop scenario"));
	}

	public void testRunViewsRenderAtCompactAndRegularSizes() throws Exception {
		WindowTestSupport.useDarcula(getTestRootDisposable());
		RetainedEnvironmentSession run = createRun();
		EnvironmentSessionPanel panel = panel(run);
		EnvironmentSessionFixture.append(run, null, "Environment ready\n", false);
		EnvironmentSessionFixture.append(
				run, "paper", "Done! Server is ready.\nAlice joined the game\n", false);
		EnvironmentSessionFixture.append(run, "proxy", "Connected Alice to Paper backend\n", false);
		((ConsoleViewImpl) panel.consolePresentation().getConsole()).waitAllRequests();
		JBTabbedPane tabs = panel.tabs();
		for (int index = 0; index < tabs.getTabCount(); index++) {
			tabs.setSelectedIndex(index);
			String name = tabs.getTitleAt(index).toLowerCase();
			captureRun(panel, "anvil-run-" + name + "-1200x400.png", 1200, 400);
			captureRun(panel, "anvil-run-" + name + "-1000x560.png", 1000, 560);
		}
	}

	public void testLightRunViewsUseNativeTheme() throws Exception {
		WindowTestSupport.useTheme(getTestRootDisposable(), false);
		EnvironmentSessionPanel panel = panel(createRun());
		for (int index = 0; index < panel.tabs().getTabCount(); index++) {
			panel.tabs().setSelectedIndex(index);
			String name = panel.tabs().getTitleAt(index).toLowerCase();
			captureRun(panel, "anvil-run-" + name + "-light-1200x400.png", 1200, 400);
			captureRun(panel, "anvil-run-" + name + "-light-1000x560.png", 1000, 560);
		}
	}

	public void testSelectedServerInspectorWrapsLongDistributionAndWorkspace() throws Exception {
		WindowTestSupport.useDarcula(getTestRootDisposable());
		String distribution =
				"Local JAR: /home/developer/projects/minecraft/integration-environments/"
						+ "registration-backend-fixtures/".repeat(7)
						+ "paper-1.21.11-117.jar";
		String workspace =
				"/home/developer/.cache/anvil/scenarios/player-registration/"
						+ "persistent-integration-workspace/".repeat(7)
						+ "paper-backend";
		var base = WindowTestSupport.scenario();
		var scenario =
				base.toBuilder()
						.clearProcesses()
						.process(base.getProcesses().getFirst().toBuilder().distribution(distribution).build())
						.process(base.getProcesses().getLast())
						.build();
		var run =
				EnvironmentSessionFixture.create(
						getProject(), WindowTestSupport.source(), scenario, getTestRootDisposable());

				EnvironmentSessionFixture.update(run,
				SessionSnapshot.builder()
						.state(SessionState.RUNNING)
						.entrypoint("paper")
						.processes(
								List.of(
										ProcessSnapshot.builder()
												.name("paper")
												.executionId(EnvironmentSessionFixture.executionId("paper"))
												.displayName("Paper backend")
												.state(ProcessState.READY)
												.host("127.0.0.1")
												.port(25566)
												.workDirectory(workspace)
												.build()))
						.build());
		var panel = panel(run);
		selectProcess(panel, 1);
		captureRun(panel, "anvil-run-server-details-long-1000x560.png", 1000, 560);
		JTextArea value = findValue(panel, distribution);
		assertNotNull(value);
		assertTrue(
				"Wrapped values must receive multiple lines of layout height",
				value.getHeight() >= 2 * value.getFontMetrics(value.getFont()).getHeight());
		assertTrue(
				WindowTestSupport.find(panel, DefinitionDetailsPanel.class)
						.getScrollableTracksViewportWidth());
	}

	public void testScenarioBadgeAndSectionsShareOneNativeHeaderRow() throws Exception {
		WindowTestSupport.useDarcula(getTestRootDisposable());
		RetainedEnvironmentSession run = createRun();

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.processes(List.of(run.getSnapshot().getProcesses().getFirst()))
						.build());
		EnvironmentSessionPanel panel = panel(run);
		int narrow = widthWithoutLabelRoom(panel);
		for (int width : new int[] {1200, 700, narrow})
			for (int index = 0; index < panel.tabs().getTabCount(); index++) {
				panel.tabs().setSelectedIndex(index);
				UIUtil.dispatchAllInvocationEvents();
				captureRun(panel, "anvil-inline-header-" + index + "-" + width + ".png", width, 400);
				assertInlineHeader(panel);
				var badge = WindowTestSupport.find(panel, StatusBadge.class);
				assertEquals("Partially running", badge.getAccessibleContext().getAccessibleName());
				// Without room for it, the badge gives up its label so every section tab stays visible.
				assertEquals(width == narrow ? null : "Partially running", badge.getText());
				assertNotNull(badge.getIcon());
			}

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().state(SessionState.STOPPED).build());
		UIUtil.dispatchAllInvocationEvents();
		var badge = WindowTestSupport.find(panel, StatusBadge.class);
		assertEquals("Stopped", badge.getAccessibleContext().getAccessibleName());
	}

	public void testNativeKeyboardActionsReachEverySectionInACompactHeader() throws Exception {
		EnvironmentSessionPanel panel = panel(createRun());
		captureRun(panel, "anvil-inline-header-keyboard-420.png", 420, 400);
		performTabAction(
				panel,
				IdeActions.ACTION_NEXT_TAB,
				WindowTestSupport.find(panel, EnvironmentPanel.class));
		assertEquals("Console", panel.tabs().getTitleAt(panel.tabs().getSelectedIndex()));
		performTabAction(
				panel,
				IdeActions.ACTION_NEXT_TAB,
				WindowTestSupport.find(panel, ConsolePanel.class));
		assertEquals("Players", panel.tabs().getTitleAt(panel.tabs().getSelectedIndex()));
		performTabAction(
				panel,
				IdeActions.ACTION_PREVIOUS_TAB,
				WindowTestSupport.find(panel, PlayersPanel.class));
		assertEquals("Console", panel.tabs().getTitleAt(panel.tabs().getSelectedIndex()));
		performTabAction(
				panel,
				IdeActions.ACTION_PREVIOUS_TAB,
				WindowTestSupport.find(panel, ConsolePanel.class));
		assertEquals("Environment", panel.tabs().getTitleAt(panel.tabs().getSelectedIndex()));
	}

	public void testActualCompatibilityCatalogRendersWithNativeChromeInBothThemes() throws Exception {
		// The declaration is an actual AnvilTooling export; process states below are UI rendering data.
		EnvironmentSessionPanel panel = panel(createCompatibilityRun());
		for (String themeId :
				List.of("ExperimentalDark", "ExperimentalLight", "Islands Dark", "Islands Light")) {
			if (UiThemeProviderListManager.Companion.getInstance().findThemeById(themeId) == null)
				continue;
			boolean dark = themeId.endsWith("Dark");
			String theme = themeId.startsWith("Islands") ? "islands-" : "";
			theme += dark ? "dark" : "light";
			WindowTestSupport.useTheme(getTestRootDisposable(), themeId, dark);
			SwingUtilities.updateComponentTreeUI(panel);
			panel.tabs().setSelectedIndex(0);
			for (int width : new int[] {1200, 700, 420}) {
				captureRun(
						panel, "anvil-compatibility-environment-" + theme + "-" + width + ".png", width, 500);
				assertInlineHeader(panel);
			}
			panel.tabs().setSelectedIndex(1);
			captureRun(panel, "anvil-compatibility-console-" + theme + ".png", 1200, 500);
			assertInlineHeader(panel);
			panel.tabs().setSelectedIndex(2);
			captureRun(panel, "anvil-compatibility-players-" + theme + ".png", 1200, 500);
			assertInlineHeader(panel);
		}
	}

	private static void captureRun(EnvironmentSessionPanel panel, String name, int width, int height)
			throws Exception {
		WindowTestSupport.capture(panel, name, width, height);
		updateToolbars(panel);
		UIUtil.dispatchAllInvocationEvents();
		WindowTestSupport.capture(panel, name, width, height);
	}

	private static void updateToolbars(Container container) throws Exception {
		if (container instanceof ActionToolbar toolbar) {
			var update = toolbar.updateActionsAsync();
			WindowTestSupport.await(update::isDone);
			update.get();
		}
		for (Component component : container.getComponents())
			if (component instanceof Container child) updateToolbars(child);
	}

	/**
	 * Returns the widest panel that is one pixel short of showing the badge's label beside every section tab.
	 * Label and tab widths follow the machine's fonts, so a fixed width is compact on one machine and not on another.
	 */
	private static int widthWithoutLabelRoom(EnvironmentSessionPanel panel) {
		var badge = WindowTestSupport.find(panel, StatusBadge.class);
		var header = (JComponent) panel.tabs().getParent();
		Insets headerInsets = header.getInsets();
		Insets leadingInsets = ((JComponent) badge.getParent()).getInsets();
		return headerInsets.left + headerInsets.right + leadingInsets.left + leadingInsets.right
				+ ((BorderLayout) header.getLayout()).getHgap() + panel.tabs().runWidth() + badge.fullWidth() - 1;
	}

	private static void assertInlineHeader(EnvironmentSessionPanel panel) {
		var header = (JComponent) panel.tabs().getParent();
		assertEquals(
				"A divider separates the complete header row from every section",
				JBUI.scale(1),
				header.getInsets().bottom);
		var badge = WindowTestSupport.find(panel, StatusBadge.class);
		Component label = panel.tabs().getTabComponentAt(panel.tabs().getSelectedIndex());
		Rectangle state = visibleBounds(badge, panel);
		Rectangle tab = visibleBounds(label, panel);
		assertTrue(
				"The status badge precedes the sections: " + state + " / " + tab,
				state.width > 0 && state.x + state.width <= tab.x);
		assertTrue(
				"The selected section remains visible at compact widths",
				tab.width > 0 && tab.x + tab.width <= panel.getWidth());
		assertTrue(
				"Badge and sections share a row",
				state.y < tab.y + tab.height && tab.y < state.y + state.height);
		assertTrue("One native header replaces the previous two rows", tab.y + tab.height <= 48);
	}

	private static Rectangle visibleBounds(Component component, Container root) {
		Rectangle visible =
				SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
		for (Container parent = component.getParent(); parent != root; parent = parent.getParent()) {
			if (!parent.isVisible()) return new Rectangle();
			visible =
					visible.intersection(
							SwingUtilities.convertRectangle(parent.getParent(), parent.getBounds(), root));
		}
		return visible;
	}

	private static void performTabAction(EnvironmentSessionPanel panel, String actionId, Component origin) {
		var source = ActionManager.getInstance().getAction(actionId);
		var actions = ActionUtil.getActions(panel);
		var action =
				actions.stream()
						.filter(
								candidate ->
										source
												.getTemplatePresentation()
												.getText()
												.equals(candidate.getTemplatePresentation().getText()))
						.findFirst()
						.orElseThrow(
								() -> new AssertionError("Native section shortcut is not registered: " + actionId));
		var context = DataManager.getInstance().getDataContext(origin);
		var event =
				AnActionEvent.createEvent(action, context, null, "Anvil.Test", ActionUiKind.NONE, null);
		action.update(event);
		assertTrue(
				"The native keyboard action must be visible: " + action + " / " + context,
				event.getPresentation().isVisible());
		assertTrue("The native keyboard action must be enabled", event.getPresentation().isEnabled());
		action.actionPerformed(event);
		UIUtil.dispatchAllInvocationEvents();
	}

	private RetainedEnvironmentSession createCompatibilityRun() throws Exception {
		try (var input =
				getClass().getResourceAsStream("/window/compatibility-backend-switching.json")) {
			assertNotNull(input);
			var scenario =
					EnvironmentSessionFixture.scenarios(new ObjectMapper().readTree(input)).getFirst();
			var run =
					EnvironmentSessionFixture.create(
							getProject(), WindowTestSupport.source(), scenario, getTestRootDisposable());

					EnvironmentSessionFixture.update(run,
					SessionSnapshot.builder()
							.state(SessionState.RUNNING)
							.scenario(scenario.getName())
							.displayName(scenario.getDisplayName())
							.definition(scenario.getDefinition())
							.entrypoint("proxy")
							.processes(
									List.of(
											ProcessSnapshot.builder()
													.name("lobby")
													.executionId(EnvironmentSessionFixture.executionId("lobby"))
													.displayName("Lobby")
													.state(ProcessState.READY)
													.host("127.0.0.1")
													.port(25566)
													.workDirectory("/workspace/anvil/lobby")
													.build(),
											ProcessSnapshot.builder()
													.name("proxy")
													.executionId(EnvironmentSessionFixture.executionId("proxy"))
													.displayName("Proxy")
													.state(ProcessState.READY)
													.host("127.0.0.1")
													.port(25565)
													.workDirectory("/workspace/anvil/proxy")
													.build()))
							.build());
			EnvironmentSessionFixture.append(run, "lobby", "Done! Server is ready.\n", false);
			EnvironmentSessionFixture.append(run, "proxy", "Listening on 127.0.0.1:25565\n", false);
			return run;
		}
	}

	private static void selectProcess(EnvironmentSessionPanel panel, int row) {
		JTree tree = WindowTestSupport.find(panel, JTree.class);
		tree.expandRow(0);
		tree.setSelectionRow(row);
	}

	private static JTextArea findValue(Container parent, String text) {
		for (Component component : parent.getComponents()) {
			if (component instanceof JTextArea area && area.getText().equals(text)) return area;
			if (component instanceof Container child) {
				JTextArea found = findValue(child, text);
				if (found != null) return found;
			}
		}
		return null;
	}

	private EnvironmentSessionPanel panel(RetainedEnvironmentSession run) {
		EnvironmentSessionPanel panel = new EnvironmentSessionPanel(getProject(), run);
		Disposer.register(getTestRootDisposable(), panel);
		return panel;
	}

	private RetainedEnvironmentSession createRun() {
		RetainedEnvironmentSession run =
				EnvironmentSessionFixture.create(
						getProject(),
						WindowTestSupport.source(),
						WindowTestSupport.scenario(),
						getTestRootDisposable());

				EnvironmentSessionFixture.update(run,
				SessionSnapshot.builder()
						.sessionId("fixture-run")
						.definition("example.AuthenticationScenarios")
						.scenario("registration")
						.displayName("Player registration")
						.entrypoint("proxy")
						.state(SessionState.RUNNING)
						.processes(
								List.of(
										ProcessSnapshot.builder()
												.name("paper")
												.executionId(EnvironmentSessionFixture.executionId("paper"))
												.displayName("Paper backend")
												.state(ProcessState.READY)
												.host("127.0.0.1")
												.port(25566)
												.workDirectory("/workspace/anvil/paper")
												.build(),
										ProcessSnapshot.builder()
												.name("proxy")
												.executionId(EnvironmentSessionFixture.executionId("proxy"))
												.displayName("Velocity proxy")
												.state(ProcessState.READY)
												.host("127.0.0.1")
												.port(25565)
												.workDirectory("/workspace/anvil/proxy")
												.build()))
						.players(
								List.of(
										PlayerDescriptor.builder()
												.name("Alice")
												.displayName("Returning player")
												.build()))
						.actions(
								List.of(
										ActionDescriptor.builder()
												.definition(
														ActionDefinition.builder()
																.id("fixture.inspect")
																.displayName("Inspect player")
																.build())
												.target(
														ActionTarget.builder()
																.type(ActionTargetType.PLAYER)
																.name("Alice")
																.build())
												.availability(ActionAvailability.builder().enabled(true).build())
												.build()))
						.build());
		return run;
	}
}

package me.whereareiam.anvil.integration.intellij.view.window.main.environment.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.ui.JBUI;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;

import java.awt.Component;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.component.console.ConsolePresentation;
import me.whereareiam.anvil.integration.intellij.scenario.execution.EnvironmentSessionFixture;
import me.whereareiam.anvil.integration.intellij.scenario.execution.RetainedEnvironmentSession;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.process.ProcessSnapshot;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessState;
import me.whereareiam.anvil.tooling.api.type.SessionState;

public class ConsolePanelPlatformTest extends UiPlatformTestCase {
	@Override
	protected boolean isIconRequired() {
		return true;
	}

	public void testSidebarFiltersEachProcessAndKeepsCommonAndCompletedOutput() {
		RetainedEnvironmentSession run = run(WindowTestSupport.scenario());
		ConsolePanel panel = panel(run);
		JList<?> sources = sources(panel);
		assertEquals(
				List.of("All processes", "Paper backend", "Velocity proxy"),
				List.of(
						sources.getModel().getElementAt(0).toString(),
						sources.getModel().getElementAt(1).toString(),
						sources.getModel().getElementAt(2).toString()));
		EnvironmentSessionFixture.append(run, null, "Preparation complete\n", false);
		EnvironmentSessionFixture.append(run, "paper", "Paper command output\n", false);
		EnvironmentSessionFixture.append(run, "proxy", "Proxy routing output\n", false);
		sources.setSelectedIndex(1);
		String paper = consoleText(panel);
		assertTrue(paper.contains("Preparation complete"));
		assertTrue(paper.contains("Paper command output"));
		assertFalse(paper.contains("Proxy routing output"));
		EnvironmentSessionFixture.append(run, "proxy", "Hidden proxy output\n", false);
		EnvironmentSessionFixture.append(run, null, "Shared diagnostic\n", true);
		assertTrue(consoleText(panel).contains("Shared diagnostic"));
		assertFalse(consoleText(panel).contains("Hidden proxy output"));

		sources.setSelectedIndex(2);
		assertTrue(consoleText(panel).contains("Hidden proxy output"));
		assertTrue(consoleText(panel).contains("Shared diagnostic"));
		assertFalse(consoleText(panel).contains("Paper command output"));

				EnvironmentSessionFixture.update(run, run.getSnapshot().toBuilder().state(SessionState.STOPPED).build());
		EnvironmentSessionFixture.finish(run, 0);
		UIUtil.dispatchAllInvocationEvents();
		sources.setSelectedIndex(0);
		assertTrue(consoleText(panel).contains("Paper command output"));
		assertTrue(consoleText(panel).contains("Hidden proxy output"));
		assertFalse(send(panel).isEnabled());
		assertTrue(
				render(sources, 1).getAccessibleContext().getAccessibleDescription().contains("Stopped"));
		assertNotNull(((SimpleColoredComponent) render(sources, 2)).getIcon());
	}

	public void testStatusAndCatalogUpdatesPreserveSelectionAndDoNotReplayTheConsole() {
		RetainedEnvironmentSession run = run(WindowTestSupport.scenario());
		ConsolePanel panel = panel(run);
		JList<?> sources = sources(panel);
		sources.setSelectedIndex(1);
		EnvironmentSessionFixture.append(run, "paper", "A log entry selected for copying\n", false);
		consoleText(panel);
		var editor = ((ConsoleViewImpl) panel.presentation().getConsole()).getEditor();
		editor.getSelectionModel().setSelection(0, 12);
		String selection = editor.getSelectionModel().getSelectedText();
		long stamp = editor.getDocument().getModificationStamp();
		Object selected = sources.getSelectedValue();
		var stopped = run.getSnapshot().getProcesses().getFirst().toBuilder().state(ProcessState.STOPPED).build();

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.processes(List.of(stopped, run.getSnapshot().getProcesses().getLast()))
						.build());
		UIUtil.dispatchAllInvocationEvents();
		consoleText(panel);
		assertSame("State changes keep the stable sidebar model", selected, sources.getSelectedValue());
		assertEquals(
				"State updates must not clear/replay output",
				stamp,
				editor.getDocument().getModificationStamp());
		assertEquals(selection, editor.getSelectionModel().getSelectedText());
		assertTrue(
				render(sources, 1).getAccessibleContext().getAccessibleDescription().contains("Stopped"));
		assertFalse(send(panel).isEnabled());

		var extra = live("worker", "Fixture worker", "READY");

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.processes(List.of(stopped, run.getSnapshot().getProcesses().getLast(), extra))
						.build());
		UIUtil.dispatchAllInvocationEvents();
		consoleText(panel);
		assertEquals(4, sources.getModel().getSize());
		assertEquals("Paper backend", sources.getSelectedValue().toString());
		assertEquals(
				"Adding an output source must not replay an unchanged filter",
				stamp,
				editor.getDocument().getModificationStamp());
		assertEquals(selection, editor.getSelectionModel().getSelectedText());
	}

	public void testAllOutputRequiresAnExplicitCommandTargetAndNeverSelectsABroadcastTarget() {
		RetainedEnvironmentSession run = run(WindowTestSupport.scenario());
		ConsolePanel panel = panel(run);
		JList<?> sources = sources(panel);
		JComboBox<?> target = target(panel);
		JBTextField command = command(panel);
		assertEquals(0, sources.getSelectedIndex());
		assertEquals(0, target.getSelectedIndex());
		assertFalse(send(panel).isEnabled());
		command.setText("list");
		command.postActionEvent();
		assertEquals("No command target means the command remains unsent", "list", command.getText());

		sources.setSelectedIndex(1);
		assertSame(
				"A selected output source chooses that exact process as target",
				sources.getSelectedValue(),
				target.getSelectedItem());
		assertTrue(send(panel).isEnabled());
		sources.setSelectedIndex(0);
		assertEquals(
				"Returning to combined output requires an explicit target", 0, target.getSelectedIndex());
		assertFalse(send(panel).isEnabled());
		target.setSelectedIndex(2);
		assertEquals(
				"An explicit target does not change combined output", 0, sources.getSelectedIndex());
		assertEquals("Velocity proxy", target.getSelectedItem().toString());
		assertTrue(send(panel).isEnabled());
		assertEquals("A new target has its own command draft", "", command.getText());
	}

	public void testIndependentCommandTargetAndDraftSurviveLifecycleUpdates() {
		RetainedEnvironmentSession run = run(WindowTestSupport.scenario());
		ConsolePanel panel = panel(run);
		sources(panel).setSelectedIndex(1);
		target(panel).setSelectedIndex(2);
		command(panel).setText("send Alice game");
		var stopped = run.getSnapshot().getProcesses().getLast().toBuilder().state(ProcessState.STOPPED).build();

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.processes(List.of(run.getSnapshot().getProcesses().getFirst(), stopped))
						.build());
		UIUtil.dispatchAllInvocationEvents();
		assertEquals("Paper backend", sources(panel).getSelectedValue().toString());
		assertEquals("Velocity proxy", target(panel).getSelectedItem().toString());
		assertEquals("send Alice game", command(panel).getText());
		assertFalse(send(panel).isEnabled());
		command(panel).postActionEvent();
		assertEquals(
				"A stopped target cannot consume the draft", "send Alice game", command(panel).getText());


				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.processes(
								List.of(
										run.getSnapshot().getProcesses().getFirst(),
										stopped.toBuilder().state(ProcessState.READY).build()))
						.build());
		UIUtil.dispatchAllInvocationEvents();
		assertTrue(send(panel).isEnabled());
		assertEquals("Velocity proxy", target(panel).getSelectedItem().toString());
		assertEquals("send Alice game", command(panel).getText());
	}

	public void testSendRechecksAStoppedTargetBeforeTheQueuedUiRefresh() {
		RetainedEnvironmentSession run = run(WindowTestSupport.scenario());
		ConsolePanel panel = panel(run);
		sources(panel).setSelectedIndex(1);
		command(panel).setText("list");
		assertTrue(send(panel).isEnabled());
		var stopped = run.getSnapshot().getProcesses().getFirst().toBuilder().state(ProcessState.STOPPED).build();

				EnvironmentSessionFixture.update(run,
				run.getSnapshot().toBuilder()
						.processes(List.of(stopped, run.getSnapshot().getProcesses().getLast()))
						.build());
		assertTrue("The queued UI refresh has not run yet", send(panel).isEnabled());
		command(panel).postActionEvent();
		assertEquals(
				"The draft must not be sent to a newly stopped process", "list", command(panel).getText());
		assertFalse(send(panel).isEnabled());
	}

	public void testNarrowToolWindowKeepsReadableSidebarWithoutHorizontalScrolling() throws Exception {
		ConsolePanel panel = panel(run(WindowTestSupport.scenario()));
		WindowTestSupport.capture(panel, "anvil-console-sidebar-compact.png", 420, 300);
		JList<?> sources = sources(panel);
		assertTrue(sources.getScrollableTracksViewportWidth());
		var split = WindowTestSupport.find(panel, OnePixelSplitter.class);
		assertTrue(split.getFirstComponent().getWidth() >= JBUI.scale(160));
		var all = (SimpleColoredComponent) render(sources, 0);
		assertEquals("All processes", all.getCharSequence(false).toString());
		assertTrue(all.getToolTipText().contains("All processes"));
		var process = (SimpleColoredComponent) render(sources, 1);
		assertEquals("Paper backend", process.getCharSequence(false).toString());
		assertTrue(process.getToolTipText().contains("Running"));
		var sidebar = (JScrollPane) split.getFirstComponent();
		assertFalse(sidebar.getHorizontalScrollBar().isVisible());
	}

	public void testNativeSidebarAndConsoleRenderWithRealCatalogAcrossThemes() throws Exception {
		ScenarioDescriptor scenario;
		try (var input =
				getClass().getResourceAsStream("/window/compatibility-backend-switching.json")) {
			assertNotNull(input);
			scenario = EnvironmentSessionFixture.scenarios(new ObjectMapper().readTree(input)).getFirst();
		}
		RetainedEnvironmentSession run = run(scenario);

				EnvironmentSessionFixture.update(run,
				SessionSnapshot.builder()
						.state(SessionState.RUNNING)
						.entrypoint("proxy")
						.processes(List.of(live("lobby", "Lobby", "READY"), live("proxy", "Proxy", "READY")))
						.build());
		ConsolePanel panel = panel(run);
		EnvironmentSessionFixture.append(
				run, null, "Prepared Backend switching · Velocity / Paper 1.21.11\n", false);
		EnvironmentSessionFixture.append(
				run, "lobby", "Starting Minecraft server version 1.21.11\nDone! Server is ready.\n", false);
		EnvironmentSessionFixture.append(run, "proxy", "Listening on 127.0.0.1:25565\n", false);
		for (String themeId :
				List.of("ExperimentalDark", "ExperimentalLight", "Islands Dark", "Islands Light")) {
			if (UiThemeProviderListManager.Companion.getInstance().findThemeById(themeId) == null)
				continue;
			WindowTestSupport.useTheme(getTestRootDisposable(), themeId, themeId.endsWith("Dark"));
			SwingUtilities.updateComponentTreeUI(panel);
			sources(panel).setSelectedIndex(1);
			consoleText(panel);
			for (int width : new int[] {1200, 700, 420}) {
				WindowTestSupport.capture(
						panel,
						"anvil-console-sidebar-"
								+ themeId.replace(' ', '-').toLowerCase()
								+ "-"
								+ width
								+ ".png",
						width,
						460);
				var split = WindowTestSupport.find(panel, OnePixelSplitter.class);
				assertTrue(
						"The source sidebar leaves most width for output",
						split.getFirstComponent().getWidth() < split.getSecondComponent().getWidth());
				assertTrue(
						"The log editor starts at the top beside the sidebar",
						((ConsoleViewImpl) panel.presentation().getConsole()).getComponent().getY() == 0);
			}
		}
	}

	private RetainedEnvironmentSession run(ScenarioDescriptor scenario) {
		RetainedEnvironmentSession run =
				EnvironmentSessionFixture.create(
						getProject(), WindowTestSupport.source(), scenario, getTestRootDisposable());

				EnvironmentSessionFixture.update(run,
				SessionSnapshot.builder()
						.state(SessionState.RUNNING)
						.entrypoint("proxy")
						.processes(
								List.of(
										live("paper", "Paper backend", "READY"),
										live("proxy", "Velocity proxy", "READY")))
						.build());
		return run;
	}

	private ConsolePanel panel(RetainedEnvironmentSession run) {
		ConsolePanel panel = new ConsolePanel(run);
		run.subscribe(panel::update, getTestRootDisposable());
		return panel;
	}

	private static ProcessSnapshot live(String name, String displayName, String state) {
		return ProcessSnapshot.builder()
				.name(name)
				.executionId(EnvironmentSessionFixture.executionId(name))
				.displayName(displayName)
				.state(ProcessState.fromWireValue(state))
				.host("127.0.0.1")
				.port(25565)
				.workDirectory("/work/" + name)
				.build();
	}

	private static JList<?> sources(ConsolePanel panel) {
		return WindowTestSupport.find(panel, JList.class);
	}

	private static JComboBox<?> target(ConsolePanel panel) {
		return WindowTestSupport.find(panel, JComboBox.class);
	}

	private static JBTextField command(ConsolePanel panel) {
		return WindowTestSupport.find(panel, JBTextField.class);
	}

	private static AbstractButton send(ConsolePanel panel) {
		return WindowTestSupport.button(panel, "Send");
	}

	private static String consoleText(ConsolePanel panel) {
		ConsoleViewImpl console = (ConsoleViewImpl) panel.presentation().getConsole();
		console.waitAllRequests();
		return console.getEditor().getDocument().getText();
	}

	private static <T> Component render(JList<T> list, int index) {
		return list.getCellRenderer()
				.getListCellRendererComponent(
						list, list.getModel().getElementAt(index), index, false, false);
	}
}

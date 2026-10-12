package me.whereareiam.anvil.integration.intellij.scenario.console;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializer;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.ScenarioPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettings;

public class CommandHistoryPlatformTest extends ScenarioPlatformTestCase {
	private ScenarioSettings settings;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		settings = new ScenarioSettings();
		ServiceContainerUtil.replaceService(
				ApplicationManager.getApplication(),
				ScenarioSettings.class,
				settings,
				getTestRootDisposable());
		int previousLimit = UISettings.getInstance().getConsoleCommandHistoryLimit();
		Disposer.register(
				getTestRootDisposable(),
				() -> UISettings.getInstance().setConsoleCommandHistoryLimit(previousLimit));
		UISettings.getInstance().setConsoleCommandHistoryLimit(100);
	}

	public void testHistoryIsIsolatedByEveryTargetContextAndOperation() {
		CommandHistory history = history();
		CommandHistory.Scope key = key();
		history.submitted(key, "say Lobby maintenance");
		for (CommandHistory.Scope other :
				List.of(
						key.toBuilder().source("second-module").build(),
						key.toBuilder().definition("other.Provider").build(),
						key.toBuilder().target("game").build(),
						key.toBuilder().operation("anvil.messages.command:text").build(),
						key.toBuilder().operation("anvil.messages.chat:text").build()))
			assertTrue(history.history(other).isEmpty());
		assertEquals(List.of("say Lobby maintenance"), history.history(key));
		assertTrue(
				"Session history must not enter workspace state",
				history.getState().getCommands().isEmpty());
	}

	public void testNativeConsoleLimitBoundsRecentProjectSubmissionsAndMovesDuplicatesToFront() {
		UISettings.getInstance().setConsoleCommandHistoryLimit(3);
		CommandHistory history = history();
		CommandHistory.Scope key = key();
		history.submitted(key, "first");
		history.submitted(key, "second");
		history.submitted(key, "third");
		history.submitted(key.toBuilder().target("game").build(), "other target");
		assertEquals(List.of("third", "second"), history.history(key));
		history.submitted(key, "second");
		assertEquals(List.of("second", "third"), history.history(key));
		settings.update(settings.values().toBuilder().commandHistory(ScenarioSettings.History.PROJECT).build());
		assertEquals(3, history.getState().getCommands().size());
		UISettings.getInstance().setConsoleCommandHistoryLimit(0);
		assertTrue(history.history(key).isEmpty());
		assertTrue(history.getState().getCommands().isEmpty());
	}

	public void testProjectOptInRoundTripsNativeWorkspaceStateWithoutAliasingBeans() {
		CommandHistory history = history();
		history.submitted(key(), "  say exact submitted text  ");
		settings.update(settings.values().toBuilder().commandHistory(ScenarioSettings.History.PROJECT).build());
		var state = history.getState();
		var xml = XmlSerializer.serialize(state);
		state.getCommands().getFirst().setText("mutated state copy");
		assertEquals(List.of("  say exact submitted text  "), history.history(key()));
		CommandHistory restored = history();
		restored.loadState(XmlSerializer.deserialize(xml, CommandHistory.Stored.class));
		assertEquals(history.history(key()), restored.history(key()));
		assertEquals(
				StoragePathMacros.WORKSPACE_FILE,
				CommandHistory.class.getAnnotation(State.class).storages()[0].value());
	}

	public void testSwitchingToSessionRemovesPersistentHistoryButKeepsCurrentCommands() {
		CommandHistory history = history();
		settings.update(settings.values().toBuilder().commandHistory(ScenarioSettings.History.PROJECT).build());
		history.submitted(key(), "list");
		assertEquals(1, history.getState().getCommands().size());
		settings.update(settings.values().toBuilder().commandHistory(ScenarioSettings.History.SESSION).build());
		UIUtil.dispatchAllInvocationEvents();
		assertTrue(history.getState().getCommands().isEmpty());
		assertEquals(List.of("list"), history.history(key()));
		settings.update(settings.values().toBuilder().commandHistory(ScenarioSettings.History.PROJECT).build());
		assertEquals(1, history.getState().getCommands().size());
	}

	public void testWorkspaceLoadCanPrecedeApplicationPreferencesAndCopiesImmutableValues() {
		CommandHistory.Stored state = stored("list");
		CommandHistory history = history();
		history.loadState(state);
		state.getCommands().getFirst().setText("later mutation");
		settings.update(settings.values().toBuilder().commandHistory(ScenarioSettings.History.PROJECT).build());
		assertEquals(List.of("list"), history.history(key()));
	}

	public void testSessionModeDoesNotRestoreLeftoverWorkspaceCommands() {
		CommandHistory history = history();
		history.loadState(stored("old project command"));
		assertTrue(history.history(key()).isEmpty());
		assertTrue(history.getState().getCommands().isEmpty());
		history.submitted(key(), "current session command");
		settings.update(settings.values().toBuilder().commandHistory(ScenarioSettings.History.PROJECT).build());
		assertEquals(List.of("current session command"), history.history(key()));
	}

	public void testBlankAndIncompleteStoredCommandsAreNotImported() {
		CommandHistory history = history();
		history.submitted(key(), " \t ");
		assertTrue(history.history(key()).isEmpty());
		var state = stored("list");
		state.getCommands().getFirst().setTarget(null);
		history.loadState(state);
		settings.update(settings.values().toBuilder().commandHistory(ScenarioSettings.History.PROJECT).build());
		assertTrue(history.history(key()).isEmpty());
	}

	private CommandHistory history() {
		CommandHistory history = new CommandHistory();
		Disposer.register(getTestRootDisposable(), history);
		return history;
	}

	private static CommandHistory.Scope key() {
		return CommandHistory.Scope.builder()
				.source("gradle:./#testing")
				.definition("example.Networks")
				.target("lobby")
				.operation("console")
				.build();
	}

	private static CommandHistory.Stored stored(String text) {
		var command = new CommandHistory.StoredCommand();
		command.setSource(key().getSource());
		command.setDefinition(key().getDefinition());
		command.setTarget(key().getTarget());
		command.setOperation(key().getOperation());
		command.setText(text);
		var state = new CommandHistory.Stored();
		state.getCommands().add(command);
		return state;
	}
}

package me.whereareiam.anvil.integration.intellij.settings;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializer;

import java.util.List;

import me.whereareiam.anvil.integration.intellij.EnginePlatformTestCase;
import me.whereareiam.anvil.integration.intellij.model.settings.CommandScope;
import me.whereareiam.anvil.integration.intellij.type.settings.CommandHistoryPersistence;

public class StoredCommandHistoryPlatformTest extends EnginePlatformTestCase {
	private PersistentPreferences settings;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		settings = new PersistentPreferences();
		ServiceContainerUtil.replaceService(
				ApplicationManager.getApplication(),
				Preferences.class,
				settings,
				getTestRootDisposable());
		int previousLimit = UISettings.getInstance().getConsoleCommandHistoryLimit();
		Disposer.register(
				getTestRootDisposable(),
				() -> UISettings.getInstance().setConsoleCommandHistoryLimit(previousLimit));
		UISettings.getInstance().setConsoleCommandHistoryLimit(100);
	}

	public void testHistoryIsIsolatedByEveryTargetContextAndOperation() {
		StoredCommandHistory history = history();
		CommandScope key = key();
		history.submitted(key, "say Lobby maintenance");
		for (CommandScope other :
				List.of(
						key.toBuilder().source("second-module").build(),
						key.toBuilder().definition("other.Provider").build(),
						key.toBuilder().scenario("other-environment").build(),
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
		StoredCommandHistory history = history();
		CommandScope key = key();
		history.submitted(key, "first");
		history.submitted(key, "second");
		history.submitted(key, "third");
		history.submitted(key.toBuilder().target("game").build(), "other target");
		assertEquals(List.of("third", "second"), history.history(key));
		history.submitted(key, "second");
		assertEquals(List.of("second", "third"), history.history(key));
		settings.setCommandHistoryPersistence(CommandHistoryPersistence.PROJECT);
		assertEquals(3, history.getState().getCommands().size());
		UISettings.getInstance().setConsoleCommandHistoryLimit(0);
		assertTrue(history.history(key).isEmpty());
		assertTrue(history.getState().getCommands().isEmpty());
	}

	public void testProjectOptInRoundTripsNativeWorkspaceStateWithoutAliasingBeans() {
		StoredCommandHistory history = history();
		history.submitted(key(), "  say exact submitted text  ");
		settings.setCommandHistoryPersistence(CommandHistoryPersistence.PROJECT);
		var state = history.getState();
		var xml = XmlSerializer.serialize(state);
		state.getCommands().getFirst().setText("mutated state copy");
		assertEquals(List.of("  say exact submitted text  "), history.history(key()));
		StoredCommandHistory restored = history();
		restored.loadState(XmlSerializer.deserialize(xml, StoredCommandHistory.StoredHistory.class));
		assertEquals(history.history(key()), restored.history(key()));
		assertEquals(
				StoragePathMacros.WORKSPACE_FILE,
				StoredCommandHistory.class.getAnnotation(State.class).storages()[0].value());
	}

	public void testSwitchingToSessionRemovesPersistentHistoryButKeepsCurrentCommands() {
		StoredCommandHistory history = history();
		settings.setCommandHistoryPersistence(CommandHistoryPersistence.PROJECT);
		history.submitted(key(), "list");
		assertEquals(1, history.getState().getCommands().size());
		settings.setCommandHistoryPersistence(CommandHistoryPersistence.SESSION);
		UIUtil.dispatchAllInvocationEvents();
		assertTrue(history.getState().getCommands().isEmpty());
		assertEquals(List.of("list"), history.history(key()));
		settings.setCommandHistoryPersistence(CommandHistoryPersistence.PROJECT);
		assertEquals(1, history.getState().getCommands().size());
	}

	public void testWorkspaceLoadCanPrecedeApplicationPreferencesAndCopiesImmutableValues() {
		StoredCommandHistory.StoredHistory state = stored("list");
		StoredCommandHistory history = history();
		history.loadState(state);
		state.getCommands().getFirst().setText("later mutation");
		settings.setCommandHistoryPersistence(CommandHistoryPersistence.PROJECT);
		assertEquals(List.of("list"), history.history(key()));
	}

	public void testSessionModeDoesNotRestoreLeftoverWorkspaceCommands() {
		StoredCommandHistory history = history();
		history.loadState(stored("old project command"));
		assertTrue(history.history(key()).isEmpty());
		assertTrue(history.getState().getCommands().isEmpty());
		history.submitted(key(), "current session command");
		settings.setCommandHistoryPersistence(CommandHistoryPersistence.PROJECT);
		assertEquals(List.of("current session command"), history.history(key()));
	}

	public void testBlankAndIncompleteStoredCommandsAreNotImported() {
		StoredCommandHistory history = history();
		history.submitted(key(), " \t ");
		assertTrue(history.history(key()).isEmpty());
		var state = stored("list");
		state.getCommands().getFirst().setTarget(null);
		history.loadState(state);
		settings.setCommandHistoryPersistence(CommandHistoryPersistence.PROJECT);
		assertTrue(history.history(key()).isEmpty());
	}

	private StoredCommandHistory history() {
		StoredCommandHistory history = new StoredCommandHistory();
		Disposer.register(getTestRootDisposable(), history);
		return history;
	}

	private static CommandScope key() {
		return CommandScope.builder()
				.source("gradle:./#testing")
				.definition("example.Networks")
				.scenario("network")
				.target("lobby")
				.operation("console")
				.build();
	}

	private static StoredCommandHistory.StoredHistory stored(String text) {
		var command = new StoredCommandHistory.StoredCommand();
		command.setModule(key().getSource());
		command.setDefinition(key().getDefinition());
		command.setScenario(key().getScenario());
		command.setTarget(key().getTarget());
		command.setOperation(key().getOperation());
		command.setText(text);
		var state = new StoredCommandHistory.StoredHistory();
		state.getCommands().add(command);
		return state;
	}
}

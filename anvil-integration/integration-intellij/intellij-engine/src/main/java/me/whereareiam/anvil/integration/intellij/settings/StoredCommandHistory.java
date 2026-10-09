package me.whereareiam.anvil.integration.intellij.settings;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.integration.intellij.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.model.settings.CommandScope;
import me.whereareiam.anvil.integration.intellij.type.settings.CommandHistoryPersistence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps submitted command history in memory, with optional persistence in this IDE project's
 * private workspace state. Drafts are owned by their input fields and are never persisted.
 */
@State(name = "AnvilCommandHistory", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class StoredCommandHistory implements ProjectCommandHistory, PersistentStateComponent<StoredCommandHistory.StoredHistory>, Disposable {
	private final ArrayDeque<Submission> commands = new ArrayDeque<>();
	private final ChangeListeners listeners = new ChangeListeners();
	private final @NotNull Preferences preferences;

	private @Nullable List<Submission> pending;
	private boolean disposed;

	public StoredCommandHistory() {
		preferences = ApplicationManager.getApplication().getService(Preferences.class);
		preferences.subscribe(this::settingsChanged, this);
	}

	public static @NotNull StoredCommandHistory getInstance(@NotNull Project project) {
		return (StoredCommandHistory) project.getService(ProjectCommandHistory.class);
	}

	/**
	 * Returns the shared IntelliJ console history limit; zero disables retained history.
	 */
	public int limit() {
		return Math.max(0, UISettings.getInstance().getConsoleCommandHistoryLimit());
	}

	public synchronized @NotNull List<String> history(@NotNull CommandScope key) {
		loadPending();
		trim();

		return commands.stream()
				.filter(command -> command.key.equals(key))
				.map(Submission::text)
				.toList();
	}

	/**
	 * Records a command only after its submission has been accepted by the run's transport.
	 */
	public synchronized void submitted(@NotNull CommandScope key, @NotNull String text) {
		if (text.isBlank()) return;

		loadPending();
		commands.removeIf(command -> command.key.equals(key) && command.text.equals(text));
		commands.addFirst(new Submission(key, text));
		trim();
		changed();
	}

	public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener, owner);
	}

	@Override
	public synchronized @NotNull StoredHistory getState() {
		loadPending();
		trim();
		StoredHistory state = new StoredHistory();
		if (preferences.snapshot().getCommandHistoryPersistence() == CommandHistoryPersistence.PROJECT) {
			for (Submission submission : commands) {
				state.commands.add(StoredCommand.from(submission));
			}
		}

		return state;
	}

	@Override
	public synchronized void loadState(@NotNull StoredHistory state) {
		// The application settings service may not have been initialized yet. Resolve that service
		// before deciding whether the workspace's opt-in history can enter this session.
		pending = state.commands.stream()
				.filter(StoredCommand::isValid)
				.map(command -> new Submission(command.key(), command.text))
				.limit(limit())
				.toList();

		changed();
	}

	private void loadPending() {
		if (pending == null) return;

		List<Submission> loaded = pending;
		pending = null;
		if (preferences.snapshot().getCommandHistoryPersistence() != CommandHistoryPersistence.PROJECT)
			return;

		for (Submission submission : loaded) {
			if (!commands.contains(submission)) commands.addLast(submission);
			if (commands.size() >= limit()) break;
		}

		trim();
	}

	private void trim() {
		int limit = limit();
		while (commands.size() > limit) {
			commands.removeLast();
		}
	}

	private synchronized void settingsChanged() {
		if (disposed) return;

		loadPending();
		trim();
		changed();
	}

	private void changed() {
		listeners.notifyLater(() -> !disposed);
	}

	@Override
	public void dispose() {
		disposed = true;
		listeners.clear();
	}

	@Getter
	@Setter
	public static final class StoredHistory {
		private List<StoredCommand> commands = new ArrayList<>();
	}

	@Getter
	@Setter
	public static final class StoredCommand {
		private String module = "";
		private String definition = "";
		private String scenario = "";
		private String target = "";
		private @Nullable String operation;
		private String text = "";

		private boolean isValid() {
			return operation != null
					&& module != null
					&& !module.isBlank()
					&& definition != null
					&& !definition.isBlank()
					&& scenario != null
					&& !scenario.isBlank()
					&& target != null
					&& !target.isBlank()
					&& text != null
					&& !text.isBlank();
		}

		private CommandScope key() {
			return CommandScope.builder()
					.source(module)
					.definition(definition)
					.scenario(scenario)
					.target(target)
					.operation(operation)
					.build();
		}

		private static StoredCommand from(Submission submission) {
			StoredCommand command = new StoredCommand();
			command.module = submission.key.getSource();
			command.definition = submission.key.getDefinition();
			command.scenario = submission.key.getScenario();
			command.target = submission.key.getTarget();
			command.operation = submission.key.getOperation();
			command.text = submission.text;
			return command;
		}
	}

	private record Submission(CommandScope key, String text) {
	}
}

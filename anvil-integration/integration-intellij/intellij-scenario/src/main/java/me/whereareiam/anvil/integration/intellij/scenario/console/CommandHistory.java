package me.whereareiam.anvil.integration.intellij.scenario.console;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import me.whereareiam.anvil.integration.intellij.scenario.ChangeListeners;
import me.whereareiam.anvil.integration.intellij.scenario.settings.ScenarioSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Accepted console commands and action inputs, shared by the environments of one IntelliJ project.
 * <p>
 * History is kept in memory and, when {@link ScenarioSettings.History#PROJECT} is chosen, in the project's private
 * workspace file. Each {@link Scope} keeps its own history. Unsent drafts belong to their input fields and are never
 * stored.
 */
@Service(Service.Level.PROJECT)
@State(name = "AnvilCommandHistory", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class CommandHistory implements PersistentStateComponent<CommandHistory.Stored>, Disposable {
	private final ArrayDeque<Submission> commands = new ArrayDeque<>();
	private final ChangeListeners listeners = new ChangeListeners();

	private @Nullable List<Submission> pending;
	private boolean disposed;

	public CommandHistory() {
		ScenarioSettings.getInstance().subscribe(this::settingsChanged, this);
	}

	/**
	 * Returns the command history of the given project.
	 */
	public static @NotNull CommandHistory getInstance(@NotNull Project project) {
		return project.getService(CommandHistory.class);
	}

	/**
	 * Returns the IDE's console history limit; zero disables history.
	 */
	public int limit() {
		return Math.max(0, UISettings.getInstance().getConsoleCommandHistoryLimit());
	}

	/**
	 * Returns accepted entries of one scope, most recent first.
	 */
	public synchronized @NotNull List<String> history(@NotNull Scope scope) {
		loadPending();
		trim();

		return commands.stream().filter(command -> command.scope.equals(scope)).map(Submission::text).toList();
	}

	/**
	 * Records an entry after its transport accepted it; an equal earlier entry moves to the front.
	 */
	public synchronized void submitted(@NotNull Scope scope, @NotNull String text) {
		if (text.isBlank()) return;

		loadPending();
		commands.removeIf(command -> command.scope.equals(scope) && command.text.equals(text));
		commands.addFirst(new Submission(scope, text));
		trim();
		changed();
	}

	/**
	 * Calls the listener on the IDE event thread after each change, until the owner is disposed.
	 */
	public void subscribe(@NotNull Runnable listener, @NotNull Disposable owner) {
		listeners.add(listener, owner);
	}

	@Override
	public synchronized @NotNull Stored getState() {
		loadPending();
		trim();
		Stored state = new Stored();
		if (persisted())
			for (Submission submission : commands)
				state.commands.add(StoredCommand.from(submission));

		return state;
	}

	@Override
	public synchronized void loadState(@NotNull Stored state) {
		// Settings may load after this state; whether stored history enters the session is decided on first use.
		pending = state.commands.stream()
				.filter(StoredCommand::isValid)
				.map(command -> new Submission(command.scope(), command.text))
				.limit(limit())
				.toList();
		changed();
	}

	private boolean persisted() {
		return ScenarioSettings.getInstance().values().getCommandHistory() == ScenarioSettings.History.PROJECT;
	}

	private void loadPending() {
		if (pending == null) return;

		List<Submission> loaded = pending;
		pending = null;
		if (!persisted()) return;

		for (Submission submission : loaded) {
			if (!commands.contains(submission)) commands.addLast(submission);
			if (commands.size() >= limit()) break;
		}
		trim();
	}

	private void trim() {
		int limit = limit();
		while (commands.size() > limit) commands.removeLast();
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

	/**
	 * Identifies one history: the source, scenario definition, target, and operation it belongs to.
	 */
	@Value
	@Builder(toBuilder = true)
	public static class Scope {
		@NotNull String source;
		@NotNull String definition;
		@NotNull String target;
		@NotNull String operation;
	}

	/**
	 * Persisted history, written only when history is kept across restarts.
	 */
	@Getter
	@Setter
	public static final class Stored {
		private List<StoredCommand> commands = new ArrayList<>();
	}

	/**
	 * One persisted entry.
	 */
	@Getter
	@Setter
	public static final class StoredCommand {
		private String source = "";
		private String definition = "";
		private String target = "";
		private String operation = "";
		private String text = "";

		private boolean isValid() {
			return !isBlank(source) && !isBlank(definition) && !isBlank(target) && !isBlank(operation) && !isBlank(text);
		}

		private static boolean isBlank(@Nullable String value) {
			return value == null || value.isBlank();
		}

		private Scope scope() {
			return Scope.builder().source(source).definition(definition).target(target).operation(operation).build();
		}

		private static StoredCommand from(Submission submission) {
			StoredCommand command = new StoredCommand();
			command.source = submission.scope.getSource();
			command.definition = submission.scope.getDefinition();
			command.target = submission.scope.getTarget();
			command.operation = submission.scope.getOperation();
			command.text = submission.text;
			return command;
		}
	}

	private record Submission(Scope scope, String text) {}
}

package me.whereareiam.anvil.integration.intellij.log;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import me.whereareiam.anvil.integration.intellij.model.SessionLogEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Retains neutral output events independently of any IntelliJ console presentation.
 * <p>
 * History is bounded by a character budget, so a long-running server cannot exhaust IDE memory. The
 * oldest entries are discarded first, and a replay starts with a notice that names how many were lost.
 * Live listeners still receive every entry.
 */
public final class StoredSessionLog implements SessionLog {
	/**
	 * Default history budget, in characters of retained output.
	 */
	public static final int DEFAULT_RETAINED_CHARACTERS = 4_000_000;

	private final int retainedCharacters;
	private final Deque<SessionLogEntry> entries = new ArrayDeque<>();
	private final List<SessionLogListener> listeners = new ArrayList<>();

	private @Nullable Integer exitCode;
	private boolean retaining = true;
	private long characters;
	private long discarded;

	public StoredSessionLog() {
		this(DEFAULT_RETAINED_CHARACTERS);
	}

	StoredSessionLog(int retainedCharacters) {
		this.retainedCharacters = retainedCharacters;
	}

	@Override
	public synchronized void replay(@NotNull SessionLogListener listener) {
		replayState(listener);
	}

	@Override
	public synchronized void subscribe(
			@NotNull SessionLogListener listener,
			@NotNull Disposable owner
	) {
		listeners.add(listener);
		Disposer.register(owner, () -> remove(listener));
		replayState(listener);
	}

	@Override
	public synchronized boolean isFinished() {
		return exitCode != null;
	}

	@Override
	public synchronized @Nullable Integer getExitCode() {
		return exitCode;
	}

	public synchronized void append(
			@Nullable String process,
			@Nullable UUID executionId,
			long sequence,
			@NotNull String text,
			boolean error
	) {
		if (exitCode != null) return;

		SessionLogEntry entry = new SessionLogEntry(process, executionId, sequence, text, error);
		if (retaining) retain(entry);

		List.copyOf(listeners).forEach(listener -> listener.appended(entry));
	}

	public synchronized void clear() {
		if (exitCode != null) return;

		forgetHistory();
		List.copyOf(listeners).forEach(SessionLogListener::cleared);
	}

	public synchronized void finish(int code) {
		if (exitCode != null) return;

		exitCode = code;
		List.copyOf(listeners).forEach(listener -> listener.finished(code));
	}

	/**
	 * Stops retaining history after its owning view closes while preserving completion delivery.
	 */
	public synchronized void releaseHistory() {
		retaining = false;
		forgetHistory();
	}

	private synchronized void remove(SessionLogListener listener) {
		listeners.remove(listener);
	}

	private void retain(SessionLogEntry entry) {
		entries.addLast(entry);
		characters += entry.getText().length();
		// The newest entry stays even when it alone exceeds the budget.
		while (characters > retainedCharacters && entries.size() > 1) {
			characters -= entries.removeFirst().getText().length();
			discarded++;
		}
	}

	private void forgetHistory() {
		entries.clear();
		characters = 0;
		discarded = 0;
	}

	private void replayState(SessionLogListener listener) {
		listener.cleared();
		if (discarded > 0) {
			listener.appended(new SessionLogEntry(null, null, 0,
					"[Anvil] " + discarded + " earlier output entries were discarded to limit memory use.\n", false));
		}
		entries.forEach(listener::appended);
		if (exitCode != null) listener.finished(exitCode);
	}
}

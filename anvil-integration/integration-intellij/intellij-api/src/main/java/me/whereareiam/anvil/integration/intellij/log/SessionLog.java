package me.whereareiam.anvil.integration.intellij.log;

import com.intellij.openapi.Disposable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only retained output for one preparation or scenario-session lifetime.
 */
public interface SessionLog {
	/**
	 * Replays retained entries and completion state to the listener.
	 */
	void replay(@NotNull SessionLogListener listener);

	/**
	 * Atomically replays retained state and then delivers new events until the owner is disposed.
	 */
	void subscribe(@NotNull SessionLogListener listener, @NotNull Disposable owner);

	/**
	 * Reports whether the producer has published final completion.
	 */
	boolean isFinished();

	/**
	 * Returns the final exit code, or null while output is active.
	 */
	@Nullable Integer getExitCode();
}

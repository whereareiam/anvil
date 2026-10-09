package me.whereareiam.anvil.integration.intellij.log;

import me.whereareiam.anvil.integration.intellij.model.SessionLogEntry;
import org.jetbrains.annotations.NotNull;

/**
 * Receives ordered changes from a retained session log.
 */
public interface SessionLogListener {
	/**
	 * Clears all previously delivered entries.
	 */
	void cleared();

	/**
	 * Delivers one raw output entry.
	 */
	void appended(@NotNull SessionLogEntry entry);

	/**
	 * Publishes final completion after process cleanup.
	 */
	void finished(int exitCode);
}

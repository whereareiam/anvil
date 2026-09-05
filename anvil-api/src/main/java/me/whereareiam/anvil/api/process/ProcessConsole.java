package me.whereareiam.anvil.api.process;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Sends commands to one running process and observes its captured console output.
 *
 * <p>Use a checkpoint before an operation when the assertion must ignore earlier output.</p>
 */
public interface ProcessConsole {
	/**
	 * Sends a command without a leading slash.
	 *
	 * @param command command to send
	 */
	void sendCommand(@NotNull String command);

	/**
	 * Returns the most recent captured output lines.
	 *
	 * @param maximumLines maximum number of lines
	 * @return captured output tail
	 */
	@NotNull List<String> tail(int maximumLines);

	/**
	 * Captures the current output position before an operation.
	 *
	 * @return process-local checkpoint
	 */
	long checkpoint();

	/**
	 * Waits for a newly captured line containing text after a checkpoint.
	 *
	 * @param text text the line must contain
	 * @param after checkpoint captured before the operation
	 * @param timeout maximum wait
	 * @return matching line with its original formatting
	 */
	@NotNull String await(@NotNull String text, long after, @NotNull Duration timeout);
}

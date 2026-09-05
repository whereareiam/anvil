package me.whereareiam.anvil.api.runtime;

import me.whereareiam.anvil.api.type.ProcessState;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Runtime view of one managed Minecraft server or proxy process.
 */
public interface RunningProcess {
	/**
	 * Returns the scenario process name.
	 *
	 * @return process name
	 */
	@NotNull String name();

	/**
	 * Returns the player-facing listener address.
	 *
	 * @return listener address
	 */
	@NotNull InetSocketAddress address();

	/**
	 * Returns the disposable process workspace.
	 *
	 * @return process workspace
	 */
	@NotNull Path workDirectory();

	/**
	 * Returns the current lifecycle state.
	 *
	 * @return lifecycle state
	 */
	@NotNull ProcessState state();

	/**
	 * Sends a command to the process console.
	 *
	 * @param command command without a leading slash
	 */
	void sendCommand(@NotNull String command);

	/**
	 * Returns a tail of captured output.
	 *
	 * @param maximumLines maximum returned lines
	 * @return immutable output tail
	 */
	@NotNull List<String> logs(int maximumLines);

	/**
	 * Captures the sequence number of the last console line observed so far.
	 * Capture this immediately before sending a command to exclude earlier output.
	 * Cursors belong to this process instance and must not be reused for another process.
	 *
	 * @return console cursor, or zero before any output has been captured
	 */
	long logCursor();

	/**
	 * Waits for a new console line containing the given text after a captured cursor.
	 * Fails on timeout, interruption, end of output, or eviction of the cursor's history.
	 * The returned line includes the process's original formatting and prefixes.
	 *
	 * <pre>{@code
	 * long cursor = proxy.logCursor();
	 * proxy.sendCommand("fixture create Alice");
	 * proxy.awaitLog("Created Alice", cursor, Duration.ofSeconds(10));
	 * }</pre>
	 *
	 * @param text text that the new line must contain
	 * @param after cursor captured from this process before the operation
	 * @param timeout positive maximum wait
	 * @return matching console line
	 */
	@NotNull String awaitLog(@NotNull String text, long after, @NotNull Duration timeout);
}

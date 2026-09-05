package me.whereareiam.anvil.api.runtime;

import me.whereareiam.anvil.api.type.ProcessState;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.file.Path;
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

}

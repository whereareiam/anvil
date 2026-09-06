package me.whereareiam.anvil.api.runtime;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import me.whereareiam.anvil.api.type.ProcessState;
import org.jetbrains.annotations.NotNull;

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
	 * Returns the process console for commands and output observations.
	 *
	 * @return process console
	 */
	@NotNull ProcessConsole console();
}

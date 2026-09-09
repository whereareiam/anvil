package me.whereareiam.anvil.api.process;

import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.type.ProcessState;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * Runtime view of one generation of a managed Minecraft server or proxy process.
 * Lifecycle state, listener address, workspace, and console describe this generation. Capabilities
 * belong to the logical process declared in the scenario: their instances remain shared across
 * replacement generations, although operations can be unavailable while a process restarts.
 * Capabilities are finalized with the scenario; subsequent lookup fails and availability checks
 * return false. Implementations that do not supply capabilities inherit empty lookup behavior.
 */
public interface RunningProcess extends CapabilityOwner<ProcessCapability> {
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
	 * Returns the lifecycle state of this process generation.
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

	/**
	 * Resolves a capability belonging to this logical process.
	 * Availability depends on the installed providers and their requirements, such as an embedded
	 * agent. Supporting implementations share capability instances across process generations and
	 * reject lookup after scenario finalization. The default implementation supplies no capabilities.
	 *
	 * @param type public process capability interface
	 * @param <T> requested capability type
	 * @return scenario-owned capability for this logical process
	 * @throws CapabilityUnavailableException when the capability is unavailable
	 */
	@Override
	default @NotNull <T extends ProcessCapability> T capability(@NotNull Class<T> type) {
		throw new CapabilityUnavailableException(
				"Process '" + name() + "' does not expose capability " + type.getName()
		);
	}

	/**
	 * Reports whether this logical process supplies the requested capability.
	 * This describes installed capability support, not whether an operation can run during a restart.
	 * Returns false after scenario finalization. The default implementation supplies no capabilities.
	 *
	 * @param type public process capability interface
	 * @return whether the capability is installed and its owner has not been finalized
	 */
	@Override
	default boolean hasCapability(@NotNull Class<? extends ProcessCapability> type) {
		return false;
	}
}

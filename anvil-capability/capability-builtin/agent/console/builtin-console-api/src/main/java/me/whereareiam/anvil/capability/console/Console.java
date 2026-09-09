package me.whereareiam.anvil.capability.console;

import me.whereareiam.anvil.api.process.ProcessCapability;
import org.jetbrains.annotations.NotNull;

/**
 * Executes commands through the native platform console of a server or proxy process.
 * This capability belongs to the logical process and does not require a simulated player.
 */
public interface Console extends ProcessCapability {
	/**
	 * Dispatches a command through the owning process's platform console.
	 *
	 * @param command command without a leading slash
	 * @return whether the platform accepted the command
	 */
	boolean execute(@NotNull String command);
}

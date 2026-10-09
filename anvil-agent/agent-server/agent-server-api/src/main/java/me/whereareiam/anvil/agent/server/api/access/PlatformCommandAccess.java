package me.whereareiam.anvil.agent.server.api.access;

import org.jetbrains.annotations.NotNull;

/**
 * Executes commands through a platform's console.
 */
public interface PlatformCommandAccess {
	/**
	 * Executes a command as the platform console.
	 *
	 * @param command command without a leading slash
	 * @return whether the platform accepted the command
	 */
	boolean executeCommand(@NotNull String command);
}

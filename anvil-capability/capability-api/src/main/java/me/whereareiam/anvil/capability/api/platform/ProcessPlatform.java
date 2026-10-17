package me.whereareiam.anvil.capability.api.platform;

import me.whereareiam.anvil.capability.api.model.platform.PlatformEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The general facts the platform agent of one process provides to every capability, so capabilities do not each
 * reimplement them: running console commands and the players joining and leaving. Anything else a capability needs
 * from the platform goes through an agent extension.
 */
public interface ProcessPlatform {
	/**
	 * Runs a command through the process's platform console.
	 *
	 * @param command command without a leading slash
	 * @return whether the platform accepted the command
	 * @throws me.whereareiam.anvil.capability.api.exception.ChannelUnavailableException when the process's agent
	 *         cannot be reached now, for example while the process restarts
	 */
	boolean execute(@NotNull String command);

	/**
	 * Returns every join and leave the process reported that is still kept, oldest first, reading what is new from
	 * the agent. A restarted process's earlier events stay, followed by a leave of every player it held then.
	 *
	 * @return the events, by increasing position
	 */
	@NotNull List<PlatformEvent> events();
}

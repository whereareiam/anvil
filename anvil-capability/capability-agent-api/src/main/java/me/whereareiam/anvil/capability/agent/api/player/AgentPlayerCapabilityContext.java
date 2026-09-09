package me.whereareiam.anvil.capability.agent.api.player;

import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import org.jetbrains.annotations.NotNull;

/**
 * Player capability context with borrowed request access to scenario process agents.
 * The player owns the capability, while the scenario retains each request channel until player
 * cleanup finishes. Channels follow process restarts and can be temporarily unavailable.
 */
public interface AgentPlayerCapabilityContext extends PlayerCapabilityContext {
	/**
	 * Resolves borrowed request access to the agent installed in a scenario process.
	 * Connection discovery, authentication, and closure belong to the scenario.
	 *
	 * @param processName declared server or proxy name
	 * @return request channel for the specified logical process
	 * @throws CapabilityException when the process has no available agent channel
	 */
	@NotNull RequestChannel channel(@NotNull String processName);
}

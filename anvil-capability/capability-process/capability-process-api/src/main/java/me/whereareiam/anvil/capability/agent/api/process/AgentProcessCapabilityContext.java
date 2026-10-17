package me.whereareiam.anvil.capability.agent.api.process;

import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.process.ProcessCapabilityContext;
import org.jetbrains.annotations.NotNull;

/**
 * Process identity, declared dependencies, the platform's general facts, and request access for an agent-backed
 * capability. The logical process owns the capability. Its request channel is borrowed from the scenario and
 * follows replacement process generations; operations can be unavailable during reconnection. Registered cleanup
 * completes before the scenario releases that channel.
 */
public interface AgentProcessCapabilityContext extends ProcessCapabilityContext {
	/**
	 * Returns borrowed request access to this process's embedded agent, for the operations of an agent extension.
	 * Connection discovery, authentication, and closure belong to the scenario.
	 *
	 * @return request channel for this logical process
	 * @throws CapabilityException when the process has no available agent channel
	 */
	@NotNull RequestChannel channel();
}

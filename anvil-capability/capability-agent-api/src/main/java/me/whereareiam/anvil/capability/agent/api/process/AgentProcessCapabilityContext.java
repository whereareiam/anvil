package me.whereareiam.anvil.capability.agent.api.process;

import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.channel.RequestChannel;
import org.jetbrains.annotations.NotNull;

/**
 * Process identity, declared dependencies, and request access for an agent-backed capability.
 * The logical process owns the capability. Its request channel is borrowed from the scenario and
 * follows replacement process generations; operations can be unavailable during reconnection.
 * Registered cleanup completes before the scenario releases that channel.
 */
public interface AgentProcessCapabilityContext extends CapabilityContext<ProcessCapability> {
	/**
	 * Returns the server or proxy name declared in the scenario.
	 *
	 * @return scenario process name
	 */
	@NotNull String processName();

	/**
	 * Returns the platform provider identifier declared by this process.
	 *
	 * @return declared platform identifier
	 */
	@NotNull String platformId();

	/**
	 * Returns borrowed request access to this process's embedded agent.
	 * Connection discovery, authentication, and closure belong to the scenario.
	 *
	 * @return request channel for this logical process
	 */
	@NotNull RequestChannel channel();
}

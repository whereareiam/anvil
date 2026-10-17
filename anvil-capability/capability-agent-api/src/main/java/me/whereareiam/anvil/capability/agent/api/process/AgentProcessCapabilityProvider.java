package me.whereareiam.anvil.capability.agent.api.process;

import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.agent.api.model.AgentExtension;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Service-loaded factory for process-owned behavior implemented through an embedded agent.
 * The capability uses a borrowed request channel and remains independent of simulated players.
 * It is created after initial agent readiness, remains associated with the logical process across
 * restarts, and is finalized with the scenario before its request channel is released.
 * Provider descriptors declare identities and required capabilities belonging to the same process.
 *
 * @param <C> public process capability interface
 */
public interface AgentProcessCapabilityProvider<C extends ProcessCapability>
		extends CapabilityProvider<C, AgentProcessCapabilityContext> {
	/**
	 * Reports whether this provider can serve the declared platform.
	 *
	 * @param platformId platform provider identifier declared by the process
	 * @return whether this provider supports the platform
	 */
	default boolean supportsPlatform(@NotNull String platformId) {
		return true;
	}

	/**
	 * Returns the agent extensions this provider's capabilities need in the scenario's processes. Anvil installs
	 * each into the processes of its platforms, whatever capabilities a test uses.
	 *
	 * @return agent extensions; none by default
	 */
	default @NotNull Set<AgentExtension> agentExtensions() {
		return Set.of();
	}
}

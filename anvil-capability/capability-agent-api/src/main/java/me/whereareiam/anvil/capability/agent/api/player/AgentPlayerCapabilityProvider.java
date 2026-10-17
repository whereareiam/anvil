package me.whereareiam.anvil.capability.agent.api.player;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.agent.api.model.AgentExtension;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Service-loaded factory for player-owned behavior implemented through scenario agents.
 * Request channels belong to the enclosing scenario and are borrowed by each created capability.
 * Provider descriptors declare identities and required capabilities belonging to the same player.
 *
 * @param <C> public player capability interface
 */
public interface AgentPlayerCapabilityProvider<C extends PlayerCapability>
		extends CapabilityProvider<C, AgentPlayerCapabilityContext> {
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

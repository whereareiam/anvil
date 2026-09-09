package me.whereareiam.anvil.capability.agent.api.player;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.CapabilityProvider;

/**
 * Service-loaded factory for player-owned behavior implemented through scenario agents.
 * Request channels belong to the enclosing scenario and are borrowed by each created capability.
 * Provider descriptors declare identities and required capabilities belonging to the same player.
 *
 * @param <C> public player capability interface
 */
public interface AgentPlayerCapabilityProvider<C extends PlayerCapability>
		extends CapabilityProvider<C, AgentPlayerCapabilityContext> { }

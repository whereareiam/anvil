package me.whereareiam.anvil.agent.api.model.location;

/**
 * Location reported for a player by a platform agent.
 */
public sealed interface AgentLocation permits ServerLocation, ProxyLocation {
}

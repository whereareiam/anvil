package me.whereareiam.anvil.agent.api.model.location;

/**
 * Location reported for a player by a platform agent.
 *
 * <p>{@link ServerLocation} and {@link ProxyLocation} are the only supported implementations. The interface is
 * not sealed because agent contracts compile for the Java 11 runtime of the oldest supported servers.</p>
 */
public interface AgentLocation {
}

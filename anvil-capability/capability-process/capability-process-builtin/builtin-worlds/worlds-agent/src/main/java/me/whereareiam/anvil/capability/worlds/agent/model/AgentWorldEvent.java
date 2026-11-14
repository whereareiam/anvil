package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * A server loading or unloading one of its worlds, as the server reported it.
 */
@Value
@Builder
@Jacksonized
public class AgentWorldEvent {
	/**
	 * The event's position in its log, from {@code 1} on.
	 */
	long position;
	/**
	 * The key of the world's level, with its namespace.
	 */
	@NotNull String world;
	/**
	 * What happened to the world: {@code LOADED} or {@code UNLOADED}.
	 */
	@NotNull String type;
	/**
	 * When the server reported the event, in milliseconds since the epoch.
	 */
	long at;
}

package me.whereareiam.anvil.agent.api.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.agent.api.type.AgentPlayerEventType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A fact about a player's connection that a platform agent recorded: a player joining or leaving.
 */
@Value
@Builder
@Jacksonized
public class AgentPlayerEvent {
	/**
	 * Position among the events of the agent's process generation, increasing with every event it records for any
	 * player.
	 */
	long position;

	/**
	 * When the agent recorded the event, in milliseconds since the epoch.
	 */
	long at;

	/**
	 * Whether the player joined or left.
	 */
	@NotNull AgentPlayerEventType type;

	/**
	 * The player as this platform reports it.
	 */
	@NotNull AgentProfile profile;

	/**
	 * On a proxy, the backend the player joined or left, as the proxy names it, or null for the player joining and
	 * leaving the proxy itself; null on a server, whose events are about the player joining and leaving it.
	 */
	@Nullable String server;
}

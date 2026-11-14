package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A player moving from one of a server's worlds to another, as the server reported it.
 */
@Value
@Builder
@Jacksonized
public class AgentWorldChange {
	/**
	 * The change's position in its log, from {@code 1} on.
	 */
	long position;
	/**
	 * The player's name as the server reports it.
	 */
	@NotNull String username;
	/**
	 * The UUID the server reports for the player.
	 */
	@NotNull UUID uniqueId;
	/**
	 * The key of the world the player left.
	 */
	@NotNull String from;
	/**
	 * The key of the world the player is in afterwards.
	 */
	@NotNull String to;
	/**
	 * When the server reported the change, in milliseconds since the epoch.
	 */
	long at;
}

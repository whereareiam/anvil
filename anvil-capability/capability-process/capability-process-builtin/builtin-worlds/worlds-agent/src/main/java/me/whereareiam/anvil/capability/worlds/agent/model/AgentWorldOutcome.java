package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

/**
 * What became of a request to create, load, unload or delete a world: done, naming a world the server does not have
 * or already has, refused by the server, or one the platform cannot do. At most one of {@link #missing},
 * {@link #refusal} and {@link #unsupported} is set.
 */
@Value
@Builder
@Jacksonized
public class AgentWorldOutcome {
	/**
	 * The world the server created or loaded, when that is done; {@code null} for an unloading or a request not done.
	 */
	@Nullable AgentWorld world;
	/**
	 * Why the request names a world wrongly, such as one the server has no folder for or has already loaded, or
	 * {@code null}.
	 */
	@Nullable String missing;
	/**
	 * Why the server refused the request, or {@code null}.
	 */
	@Nullable String refusal;
	/**
	 * Why the platform cannot do the request at all, or {@code null}.
	 */
	@Nullable String unsupported;
}

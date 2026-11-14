package me.whereareiam.anvil.capability.worlds.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

/**
 * What became of a request to create or unload a world: done, refused by the server, or one the platform cannot do.
 * At most one of {@link #refusal} and {@link #unsupported} is set.
 */
@Value
@Builder
@Jacksonized
public class AgentWorldOutcome {
	/**
	 * The world the server created, when a creation is done; {@code null} for an unloading or a request not done.
	 */
	@Nullable AgentWorld world;
	/**
	 * Why the server refused the request, or {@code null}.
	 */
	@Nullable String refusal;
	/**
	 * Why the platform cannot do the request at all, or {@code null}.
	 */
	@Nullable String unsupported;
}

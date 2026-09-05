package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * Aggregate route observed for a simulated player across the proxy and backend server.
 *
 * <p>Both values may be present when a player is connected through a proxy. A missing value means
 * that no corresponding platform agent has observed the player.</p>
 */
@Value
@Builder(toBuilder = true)
public class PlayerRoute {
	/**
	 * Proxy process through which the player is connected.
	 */
	@Nullable String proxy;
	/**
	 * Backend server currently observing the player.
	 */
	@Nullable String server;
}

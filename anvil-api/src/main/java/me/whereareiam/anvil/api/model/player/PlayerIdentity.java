package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Identity observed for a simulated player by its client, proxy, and Minecraft server.
 */
@Value
@Builder(toBuilder = true)
public class PlayerIdentity {
	@NotNull String username;
	@NotNull UUID clientUniqueId;
	@Nullable String observedUsername;
	@Nullable UUID observedUniqueId;
	@NotNull
	@Builder.Default
	PlayerRoute route = PlayerRoute.builder().build();

	/**
	 * Returns the proxy from the aggregate route.
	 *
	 * @return proxy name, or {@code null} when no proxy observed the player
	 * @deprecated use {@link #getRoute()} instead
	 */
	@Deprecated
	@Nullable
	public String getProxy() {
		return route.getProxy();
	}

	/**
	 * Returns the backend server from the aggregate route.
	 *
	 * @return server name, or {@code null} when no server observed the player
	 * @deprecated use {@link #getRoute()} instead
	 */
	@Deprecated
	@Nullable
	public String getServer() {
		return route.getServer();
	}

	/**
	 * @return client unique ID
	 * @deprecated use {@link #getClientUniqueId()}
	 */
	@Deprecated
	@NotNull
	public UUID getClientUuid() {
		return clientUniqueId;
	}

	/**
	 * @return observed unique ID
	 * @deprecated use {@link #getObservedUniqueId()}
	 */
	@Deprecated
	@Nullable
	public UUID getObservedUuid() {
		return observedUniqueId;
	}
}

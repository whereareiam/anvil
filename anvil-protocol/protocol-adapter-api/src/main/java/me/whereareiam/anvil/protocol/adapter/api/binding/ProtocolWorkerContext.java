package me.whereareiam.anvil.protocol.adapter.api.binding;

import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolWorkerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Worker-owned state and callbacks supplied to the selected native session binding.
 */
public interface ProtocolWorkerContext {
	/**
	 * Returns shared player identity, capability state, and event services.
	 */
	@NotNull ProtocolWorkerPlayer player();

	/**
	 * Returns the selected game endpoint hostname.
	 */
	@NotNull String host();

	/**
	 * Returns the selected game listener port.
	 */
	int port();

	/**
	 * Returns the private online session token, or null for offline mode. Never log this value.
	 */
	@Nullable String accessToken();

	/**
	 * Dispatches an inbound native packet to capability-owned listeners.
	 *
	 * @param packet packet value owned by the selected library
	 */
	void received(@NotNull Object packet);

	/**
	 * Announces successful entry into the game.
	 */
	void connected();

	/**
	 * Announces the first disconnect observation for the current native session.
	 */
	void disconnected(@NotNull String reason);
}

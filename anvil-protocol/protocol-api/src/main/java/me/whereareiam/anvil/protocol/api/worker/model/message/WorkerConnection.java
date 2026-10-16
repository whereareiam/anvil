package me.whereareiam.anvil.protocol.api.worker.model.message;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Game listener of a worker player, the host its handshake announces, the address literal it binds to and the
 * native wire-protocol number it speaks there. A release that maps its versions to several protocol numbers selects
 * the player's protocol by that number.
 */
@Value
@Builder
@Jacksonized
public class WorkerConnection {
	/**
	 * Host name or address literal of the game listener the player connects to.
	 */
	@NotNull String host;
	/**
	 * Port of the game listener.
	 */
	int port;
	/**
	 * Host the handshake announces instead of {@link #host}, or null to announce the host itself.
	 */
	@Nullable String virtualHost;
	/**
	 * Local address literal the connection binds to, or null for the system's choice.
	 */
	@Nullable String sourceAddress;
	/**
	 * Native wire-protocol number the player speaks to the listener.
	 */
	int protocol;
}

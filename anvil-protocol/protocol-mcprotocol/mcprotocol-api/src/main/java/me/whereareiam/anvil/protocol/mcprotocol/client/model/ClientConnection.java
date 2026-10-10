package me.whereareiam.anvil.protocol.mcprotocol.client.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where a client connects: the game listener, the host its handshake announces and the local address its socket
 * binds to. A client segment honours both optional values; it never ignores one.
 *
 * <pre>{@code
 * ClientConnection connection = ClientConnection.builder()
 *         .host("127.0.0.1")
 *         .port(25577)
 *         .virtualHost("games.example.test")
 *         .sourceAddress("127.0.0.2")
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class ClientConnection {
	/**
	 * Host name or address of the game listener.
	 */
	@NotNull String host;

	/**
	 * Port of the game listener.
	 */
	int port;

	/**
	 * Host the handshake announces instead of {@link #getHost() host}, or null for that host. The session still
	 * connects to the host and port, and the handshake still announces the port.
	 */
	@Nullable String virtualHost;

	/**
	 * Local IP address literal the client socket binds to before connecting, or null for the system's choice.
	 */
	@Nullable String sourceAddress;
}

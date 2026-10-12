package me.whereareiam.anvil.protocol.cloudburst.client.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where a Bedrock client connects: the game listener, the local address its datagram socket binds to and the
 * native protocol number it speaks there. Bedrock has no handshake host, so a connection has no virtual host.
 *
 * <pre>{@code
 * ClientConnection connection = ClientConnection.builder()
 *         .host("127.0.0.1")
 *         .port(19132)
 *         .sourceAddress("127.0.0.2")
 *         .protocol(2193)
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
	 * UDP port of the game listener.
	 */
	int port;

	/**
	 * Local IP address literal the client's datagram socket binds to before connecting, or null for the system's
	 * choice.
	 */
	@Nullable String sourceAddress;

	/**
	 * Native protocol number the client speaks; a segment selects its codec by it.
	 */
	int protocol;
}

package me.whereareiam.anvil.protocol.api.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Resolved connection of one player to the game listener of a server or proxy: where the client connects, the host
 * its handshake announces and the local address it connects from. A protocol library must honour the virtual host
 * and the source address, or refuse the player when it is created; it never ignores either one.
 *
 * <pre>{@code
 * GameConnection connection = GameConnection.builder()
 *         .address(new InetSocketAddress("127.0.0.1", 25577))
 *         .virtualHost("games.example.test")
 *         .sourceAddress(InetAddress.getByName("127.0.0.2"))
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class GameConnection {
	/**
	 * Game listener of the selected server or proxy, which the client connects to.
	 */
	@NotNull InetSocketAddress address;

	/**
	 * Host the client writes into its handshake instead of the {@link #getAddress() address}'s host, or null for
	 * that host. The client still connects to the address and announces its port.
	 */
	@Nullable String virtualHost;

	/**
	 * Local loopback address the client's socket binds to before connecting, or null for the system's choice.
	 * The engine has checked that this machine can bind it and that the joined process sees it unchanged.
	 */
	@Nullable InetAddress sourceAddress;
}

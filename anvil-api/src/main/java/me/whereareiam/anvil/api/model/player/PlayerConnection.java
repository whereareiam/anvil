package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Where and how one simulated player connects: the process it joins, the server address it announces in its
 * handshake and the local address its connection leaves from. Every value is optional; the default connection
 * joins the scenario entrypoint at its real address from the address the system chooses, which is
 * {@code 127.0.0.1} for a loopback listener.
 *
 * <pre>{@code
 * PlayerConnection connection = PlayerConnection.builder()
 *         .target("proxy")
 *         .virtualHost("lobby.example.test")
 *         .sourceAddress("127.0.0.2")
 *         .build();
 * }</pre>
 *
 * <p>Whether a source address can be used on this machine and by the scenario's execution is checked when the
 * player is created, before it connects.</p>
 */
@Value
public class PlayerConnection {
	private static final int MAXIMUM_HOST_LENGTH = 255;
	private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
	private static final PlayerConnection ENTRYPOINT = new PlayerConnection(null, null, null);

	/**
	 * Name of the server or proxy the player joins, or null for the scenario entrypoint.
	 */
	@Nullable String target;

	/**
	 * Server address the client writes into its handshake, or null for the target's real host. A proxy reads it
	 * to route by host, for example through Velocity's {@code forced-hosts}; the connection itself still goes to
	 * the target's real address and port.
	 */
	@Nullable String virtualHost;

	/**
	 * Loopback IP address the client's socket binds to before it connects, or null for the address the system
	 * chooses. The joined process sees the player connect from this address. Linux routes all of
	 * {@code 127.0.0.0/8} to the loopback interface, so {@code 127.0.0.2} and later addresses work without
	 * setup; macOS configures only {@code 127.0.0.1} unless an alias is added.
	 */
	@Nullable String sourceAddress;

	/**
	 * Creates a connection, refusing values that no machine could use.
	 *
	 * @param target name of the process the player joins, or null for the scenario entrypoint
	 * @param virtualHost host announced in the handshake, or null for the target's real host
	 * @param sourceAddress loopback IP literal the connection leaves from, or null for the system's choice
	 * @throws IllegalArgumentException when the target or virtual host is blank, the virtual host is longer than
	 * a handshake allows or contains whitespace, or the source address is not a loopback IP literal
	 */
	@Builder(toBuilder = true)
	private PlayerConnection(@Nullable String target, @Nullable String virtualHost, @Nullable String sourceAddress) {
		if (target != null && target.isBlank())
			throw new IllegalArgumentException("A player connection's target must not be blank; omit it for the entrypoint");
		this.target = target;
		this.virtualHost = virtualHost == null ? null : virtualHost(virtualHost);
		this.sourceAddress = sourceAddress == null ? null : sourceAddress(sourceAddress);
	}

	/**
	 * Returns the default connection: the scenario entrypoint at its real address, from the system's choice of
	 * source address.
	 *
	 * @return default connection
	 */
	public static @NotNull PlayerConnection entrypoint() {
		return ENTRYPOINT;
	}

	/**
	 * Returns a connection to one named server or proxy with every other value left to its default.
	 *
	 * <pre>{@code
	 * PlayerOptions.builder().name("Alice").connection(PlayerConnection.to("secondary")).build();
	 * }</pre>
	 *
	 * @param target name of the process the player joins
	 * @return connection to that process
	 * @throws IllegalArgumentException when the target is blank
	 */
	public static @NotNull PlayerConnection to(@NotNull String target) {
		return new PlayerConnection(target, null, null);
	}

	private static String virtualHost(String value) {
		if (value.isBlank())
			throw new IllegalArgumentException("A player connection's virtual host must not be blank; omit it for the real host");
		if (value.length() > MAXIMUM_HOST_LENGTH)
			throw new IllegalArgumentException("A player connection's virtual host is longer than the " + MAXIMUM_HOST_LENGTH
					+ " characters a handshake allows");
		if (value.chars().anyMatch(Character::isWhitespace))
			throw new IllegalArgumentException("A player connection's virtual host '" + value + "' contains whitespace");
		return value;
	}

	/**
	 * A source address is a literal, never a name: resolving a name could select a different address on another
	 * machine. It must be a loopback address, which every machine routes locally without network setup.
	 */
	private static String sourceAddress(String value) {
		InetAddress address = literal(value);
		if (!address.isLoopbackAddress())
			throw new IllegalArgumentException("A player connection's source address '" + value + "' is not a loopback "
					+ "address; players connect from the loopback interface, for example from 127.0.0.2");
		return value;
	}

	/**
	 * Parses an address literal without a name lookup: IPv4 octets directly, and IPv6 through
	 * {@link InetAddress#getByName(String)}, which never resolves a value containing a colon.
	 */
	private static InetAddress literal(String value) {
		try {
			if (value.contains(":")) return InetAddress.getByName(value);
			if (IPV4_LITERAL.matcher(value).matches()) {
				String[] parts = value.split("\\.");
				byte[] octets = new byte[parts.length];
				for (int index = 0; index < parts.length; index++) {
					int octet = Integer.parseInt(parts[index]);
					if (octet > 255) throw new UnknownHostException(value);
					octets[index] = (byte) octet;
				}
				return InetAddress.getByAddress(octets);
			}
		} catch (UnknownHostException invalid) {
			throw new IllegalArgumentException("A player connection's source address '" + value
					+ "' is not a valid IP address literal", invalid);
		}

		throw new IllegalArgumentException("A player connection's source address '" + value
				+ "' must be an IP address literal such as 127.0.0.2");
	}
}

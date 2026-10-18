package me.whereareiam.anvil.protocol.player.remote;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.scenario.RemoteEndpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Resolves a remote's address the way the vanilla Java Edition client resolves a server address before it connects,
 * as {@code ServerNameResolver}, {@code ServerRedirectHandler} and {@code ConnectScreen} of Minecraft 1.20.5 and
 * later do:
 *
 * <ol>
 *     <li>The typed address splits into host and port; a missing port means 25565.</li>
 *     <li>On port 25565, and only there, whether typed or not, the client asks DNS for the
 *     {@code _minecraft._tcp.<host>} SRV record and follows the first record the answer lists, without weighing
 *     priorities: its target host, with the trailing dot DNS writes, and its port.</li>
 *     <li>The host, the SRV target when there is one, resolves to an IP address through the system resolver; the
 *     typed host is not resolved when an SRV record replaced it.</li>
 *     <li>The handshake announces the resolved address's host name and the port it connects to. A host name stays as
 *     it was resolved, letter case and a trailing dot included, so a player following
 *     {@code _minecraft._tcp.example.com} to {@code play.example.com.} announces {@code play.example.com.}. An IP
 *     literal has no host name, so the client announces the name a reverse lookup finds for it, or the literal
 *     itself when there is none.</li>
 * </ol>
 *
 * <p>The vanilla client also refuses addresses that Mojang's server blocklist names; Anvil does not consult it.</p>
 */
public final class RemoteAddressResolver {
	private static final int DEFAULT_PORT = 25565;
	private static final String SERVICE_PREFIX = "_minecraft._tcp.";

	private final Lookup lookup;

	/**
	 * Creates a resolver over the given name lookups.
	 *
	 * @param lookup SRV and address lookups, {@link DnsLookup} for the system's DNS
	 */
	public RemoteAddressResolver(@NotNull Lookup lookup) {
		this.lookup = lookup;
	}

	/**
	 * Resolves where a player joining the remote connects and which host its handshake announces.
	 *
	 * @param remote validated remote
	 * @return the address to connect to, as an IP literal, and the host the vanilla client announces
	 * @throws ScenarioValidationException when the host, or the target of its SRV record, does not resolve
	 */
	public @NotNull Resolution resolve(@NotNull RemoteEndpoint remote) {
		String host = remote.host();
		int port = remote.port();
		String record = port == DEFAULT_PORT ? lookup.serviceRecord(SERVICE_PREFIX + host) : null;
		if (record != null) {
			// The record reads "<priority> <weight> <port> <target>"; vanilla splits it into at most four parts.
			String[] parts = record.split(" ", 4);
			if (parts.length == 4) {
				host = target(remote, parts[3]);
				port = parsePort(parts[2]);
			}
		}

		InetAddress resolved = address(remote, host);
		String announced = resolved.getHostName();

		return new Resolution(new InetSocketAddress(withoutName(resolved), port), announced);
	}

	private static String target(RemoteEndpoint remote, String target) {
		try {
			return IDN.toASCII(target);
		} catch (IllegalArgumentException invalid) {
			throw new ScenarioValidationException("Remote '" + remote.getName() + "' at '" + remote.getAddress()
					+ "' cannot be reached: its SRV record names the invalid host '" + target + "'", invalid);
		}
	}

	private InetAddress address(RemoteEndpoint remote, String host) {
		try {
			return lookup.address(host);
		} catch (UnknownHostException unknown) {
			throw new ScenarioValidationException("Remote '" + remote.getName() + "' at '" + remote.getAddress()
					+ "' cannot be reached: the host '" + host + "' does not resolve", unknown);
		}
	}

	/**
	 * Returns the address without its host name, so that a protocol library connecting to it reads an IP literal
	 * and resolves nothing again.
	 */
	private static InetAddress withoutName(InetAddress address) {
		try {
			if (address instanceof Inet6Address inet6 && inet6.getScopeId() != 0)
				return Inet6Address.getByAddress(null, inet6.getAddress(), inet6.getScopeId());

			return InetAddress.getByAddress(address.getAddress());
		} catch (UnknownHostException impossible) {
			throw new IllegalStateException("A resolved address has an invalid length", impossible);
		}
	}

	/**
	 * Reads an SRV record's port as vanilla does: a value that is not a number means 25565.
	 */
	private static int parsePort(String port) {
		try {
			return Integer.parseInt(port.trim());
		} catch (NumberFormatException invalid) {
			return DEFAULT_PORT;
		}
	}

	/**
	 * Name lookups the resolution needs, which tests replace without a real DNS.
	 */
	public interface Lookup {
		/**
		 * Looks up the SRV record of a service name.
		 *
		 * @param name service name such as {@code _minecraft._tcp.example.com}
		 * @return the first record of the answer as {@code <priority> <weight> <port> <target>}, or null when the
		 * name has no SRV record or the lookup failed
		 */
		@Nullable String serviceRecord(@NotNull String name);

		/**
		 * Resolves a host name or IP literal to one address.
		 *
		 * @param host host name or IP literal
		 * @return the address, carrying the host name it was resolved from, or none for a literal
		 * @throws UnknownHostException when the host does not resolve
		 */
		@NotNull InetAddress address(@NotNull String host) throws UnknownHostException;
	}

	/**
	 * Where a player connects and what its handshake announces.
	 *
	 * @param address IP literal and port the client connects to
	 * @param announcedHost host the vanilla client writes into its handshake
	 */
	public record Resolution(@NotNull InetSocketAddress address, @NotNull String announcedHost) { }
}

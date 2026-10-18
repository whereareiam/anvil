package me.whereareiam.anvil.api.model.scenario;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.IDN;

/**
 * Immutable declaration of a Minecraft server or proxy that Anvil does not run, such as an existing network
 * reachable over the internet. Its simulated players join it like a player who types its address into the
 * multiplayer screen, while the scenario starts, stops and observes nothing on its side.
 *
 * <pre>{@code
 * AnvilScenario.builder()
 *         .name("public-network")
 *         .entrypoint("network")
 *         .remote(RemoteEndpoint.builder()
 *                 .name("network")
 *                 .address("play.example.com")
 *                 .minecraftVersion("1.21.11")
 *                 .build())
 *         .build();
 * }</pre>
 *
 * <p>A remote is no {@code RunningProcess}: it is missing from the scenario's processes, and nothing that needs a
 * process's agent, such as its process capabilities, is available for it. Its players keep every capability the
 * client itself owns.</p>
 */
@Value
@Builder(toBuilder = true)
public class RemoteEndpoint {
	private static final int DEFAULT_PORT = 25565;
	private static final int MAXIMUM_PORT = 65535;

	/**
	 * Scenario-unique name, shared with the scenario's servers and proxies, that players name as their target.
	 */
	@NotNull String name;

	/**
	 * Optional labels for tooling; the remote's name remains its identity.
	 */
	@Nullable PresentationMetadata metadata;

	/**
	 * Server address exactly as a player types it into the multiplayer screen: a host name such as
	 * {@code play.example.com}, a host with a port such as {@code play.example.com:25570}, an IPv4 literal such as
	 * {@code 203.0.113.7:25565}, or an IPv6 literal in brackets such as {@code [2001:db8::7]:25565}. Without a port
	 * the address means port 25565, and on that port players follow the host's
	 * {@code _minecraft._tcp} SRV record as the vanilla client does.
	 */
	@NotNull String address;

	/**
	 * Release version of Minecraft the remote speaks, such as {@code 1.21.11}; its players select their client
	 * version and protocol library from it.
	 */
	@NotNull String minecraftVersion;

	/**
	 * Returns the Minecraft version the remote speaks.
	 *
	 * @return the declared version
	 * @throws IllegalArgumentException when the version is blank or not a release version
	 */
	public @NotNull MinecraftVersion nativeVersion() {
		if (minecraftVersion.isBlank())
			throw new IllegalArgumentException("Remote '" + name + "' declares no Minecraft version");

		try {
			return MinecraftVersion.parse(minecraftVersion);
		} catch (IllegalArgumentException invalid) {
			throw new IllegalArgumentException("Remote '" + name + "' declares an invalid Minecraft version: "
					+ invalid.getMessage(), invalid);
		}
	}

	/**
	 * Returns the host of the {@link #getAddress() address} as the vanilla client reads it: without the brackets
	 * of an IPv6 literal and with internationalised labels converted to their ASCII form, letter case and a
	 * trailing dot kept.
	 *
	 * @return the host the client resolves
	 * @throws IllegalArgumentException when the address cannot be parsed
	 */
	public @NotNull String host() {
		String host = parsedHost();
		try {
			return IDN.toASCII(host);
		} catch (IllegalArgumentException invalid) {
			throw new IllegalArgumentException("Remote '" + name + "' declares the address '" + address
					+ "', whose host '" + host + "' is not a valid host name", invalid);
		}
	}

	/**
	 * Returns the port of the {@link #getAddress() address}, or 25565 when it names none.
	 *
	 * @return the port the client connects to before it follows an SRV record
	 * @throws IllegalArgumentException when the address cannot be parsed
	 */
	public int port() {
		String port = parsedPort();
		if (port == null || port.isEmpty()) return DEFAULT_PORT;
		if (!port.chars().allMatch(character -> character >= '0' && character <= '9') || port.length() > 5)
			throw invalid("its port '" + port + "' is not a number");

		int value = Integer.parseInt(port);
		if (value < 1 || value > MAXIMUM_PORT)
			throw invalid("its port " + value + " is outside 1 to " + MAXIMUM_PORT);

		return value;
	}

	/**
	 * Splits the address as the vanilla client's {@code HostAndPort} does: a bracketed IPv6 literal, a host with
	 * one colon before its port, or a host with several colons and no port, which is an IPv6 literal without
	 * brackets.
	 */
	private String parsedHost() {
		String text = requireText();
		if (text.startsWith("[")) {
			int closing = text.indexOf(']');
			if (closing < 0) throw invalid("its IPv6 literal has no closing bracket");

			String host = text.substring(1, closing);
			if (host.isEmpty()) throw invalid("its brackets hold no host");
			return host;
		}

		int colon = text.indexOf(':');
		String host = colon >= 0 && text.indexOf(':', colon + 1) < 0 ? text.substring(0, colon) : text;
		if (host.isEmpty()) throw invalid("it names no host");

		return host;
	}

	private @Nullable String parsedPort() {
		String text = requireText();
		if (text.startsWith("[")) {
			int closing = text.indexOf(']');
			if (closing < 0) throw invalid("its IPv6 literal has no closing bracket");
			if (closing == text.length() - 1) return null;
			if (text.charAt(closing + 1) != ':') throw invalid("only a port may follow its IPv6 literal");

			return text.substring(closing + 2);
		}

		int colon = text.indexOf(':');
		if (colon < 0 || text.indexOf(':', colon + 1) >= 0) return null;

		return text.substring(colon + 1);
	}

	private String requireText() {
		if (address.isBlank())
			throw new IllegalArgumentException("Remote '" + name + "' declares no address");
		if (address.chars().anyMatch(Character::isWhitespace)) throw invalid("it contains whitespace");

		return address;
	}

	private IllegalArgumentException invalid(String reason) {
		return new IllegalArgumentException("Remote '" + name + "' declares the address '" + address
				+ "', which cannot be parsed: " + reason);
	}
}

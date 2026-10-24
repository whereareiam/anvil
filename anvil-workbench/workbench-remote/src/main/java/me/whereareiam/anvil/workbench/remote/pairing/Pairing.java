package me.whereareiam.anvil.workbench.remote.pairing;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * An engine's pairing with one workbench: where it listens, the certificate it presented when pairing, and the
 * token it issued. The token is a credential: whoever holds it can run processes on the workbench.
 */
@Value
@Builder(toBuilder = true)
public class Pairing {
	/**
	 * Name the engine knows the workbench by, chosen when pairing.
	 */
	@NotNull String name;

	/**
	 * Host and port the workbench listens on, such as {@code 192.168.1.20:7400}.
	 */
	@NotNull String address;

	/**
	 * SHA-256 fingerprint of the workbench's certificate.
	 */
	@NotNull String fingerprint;

	/**
	 * Token the workbench issued to this engine.
	 */
	@NotNull String token;

	/**
	 * Returns the host part of the address.
	 *
	 * @return host name or address
	 */
	public @NotNull String host() {
		int separator = address.lastIndexOf(':');
		return separator < 0 ? address : address.substring(0, separator);
	}

	/**
	 * Returns the port part of the address.
	 *
	 * @param fallback port when the address names none
	 * @return port
	 */
	public int port(int fallback) {
		int separator = address.lastIndexOf(':');
		return separator < 0 ? fallback : Integer.parseInt(address.substring(separator + 1));
	}
}

package me.whereareiam.anvil.edition.java.data.packet;

import me.whereareiam.anvil.edition.java.data.packet.exception.PacketDataUnavailableException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The packet names Anvil ships for every Minecraft: Java Edition protocol it supports, by protocol number.
 *
 * <p>Every Minecraft version of one protocol numbers its packets alike, so the data is keyed by the protocol number a
 * player's release speaks, such as {@code 767} for 1.21 and 1.21.1. It is generated at build time from the packet
 * mappings of PrismarineJS minecraft-data (MIT), pinned to one release of it. A supported protocol the data source has
 * no protocol file for is listed as missing and reported as such, never served another protocol's names. Each registry
 * is read on first use and kept; lookups are safe from any thread.</p>
 *
 * <pre>{@code
 * PacketRegistry registry = JavaPacketData.shipped().require(767);
 * Optional<String> name = registry.clientbound(ConnectionState.PLAY, packetId);
 * }</pre>
 */
public final class JavaPacketData {
	private static final String INDEX = "packet-data.dat";

	private final @NotNull Set<Integer> protocols;
	private final @NotNull Map<Integer, String> missing;
	private final @NotNull Map<Integer, PacketRegistry> registries = new ConcurrentHashMap<>();

	/**
	 * Creates the index of shipped packet data.
	 *
	 * @param protocols the protocols with packet names
	 * @param missing the reason of each supported protocol without packet names
	 * @throws IllegalArgumentException when a protocol is listed both with and without data
	 */
	JavaPacketData(@NotNull Set<Integer> protocols, @NotNull Map<Integer, String> missing) {
		Set<Integer> both = new TreeSet<>(protocols);
		both.retainAll(missing.keySet());
		if (!both.isEmpty()) throw new IllegalArgumentException("Protocols " + both + " are listed with and without data");

		this.protocols = Collections.unmodifiableSet(new TreeSet<>(protocols));
		this.missing = Collections.unmodifiableMap(new TreeMap<>(missing));
	}

	/**
	 * Returns the packet data shipped with Anvil, read from its resources on first use.
	 *
	 * @return the shipped packet data
	 * @throws IllegalStateException when the shipped resources are missing
	 */
	public static @NotNull JavaPacketData shipped() {
		return Shipped.DATA;
	}

	/**
	 * The protocol numbers Anvil has packet names for.
	 *
	 * @return the protocols, lowest first
	 */
	public @NotNull Set<Integer> protocols() {
		return protocols;
	}

	/**
	 * The supported protocol numbers Anvil has no packet names for, because the data source lacks them.
	 *
	 * @return the protocols, lowest first
	 */
	public @NotNull Set<Integer> missingProtocols() {
		return missing.keySet();
	}

	/**
	 * Looks up the packet registry of a protocol.
	 *
	 * @param protocol protocol number, such as {@code 767}
	 * @return the registry, or empty when Anvil has no packet names for the protocol
	 * @throws IllegalStateException when the protocol's shipped resource is missing
	 */
	public @NotNull Optional<PacketRegistry> find(int protocol) {
		if (!protocols.contains(protocol)) return Optional.empty();

		return Optional.of(registries.computeIfAbsent(protocol, JavaPacketData::read));
	}

	/**
	 * Returns the packet registry of a protocol, or reports why there is none.
	 *
	 * @param protocol protocol number, such as {@code 767}
	 * @return the registry
	 * @throws PacketDataUnavailableException when the protocol is not supported, or the data source has no packet
	 *         names for it
	 * @throws IllegalStateException when the protocol's shipped resource is missing
	 */
	public @NotNull PacketRegistry require(int protocol) {
		String reason = missing.get(protocol);
		if (reason != null) throw new PacketDataUnavailableException(protocol, reason);

		return find(protocol).orElseThrow(() -> new PacketDataUnavailableException(protocol, "Anvil supports no such "
				+ "protocol; it ships packet data for " + protocols + " and knows " + missing.keySet() + " without it"));
	}

	/**
	 * Why a supported protocol has no packet names.
	 */
	@NotNull String missingReason(int protocol) {
		return missing.get(protocol);
	}

	/**
	 * The resource holding a protocol's registry, beside this class.
	 */
	static @NotNull String registryResource(int protocol) {
		return "packets-" + protocol + ".dat";
	}

	/**
	 * The resource holding the index, beside this class.
	 */
	static @NotNull String indexResource() {
		return INDEX;
	}

	private static @NotNull PacketRegistry read(int protocol) {
		try (InputStream input = resource(registryResource(protocol))) {
			return PacketDataCodec.readRegistry(input);
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read the shipped packet data of protocol " + protocol, exception);
		}
	}

	private static @NotNull InputStream resource(@NotNull String name) {
		InputStream stream = JavaPacketData.class.getResourceAsStream(name);
		if (stream == null) throw new IllegalStateException("Anvil's packet data resource " + name + " is missing");

		return stream;
	}

	/**
	 * Reads the shipped index once, when {@link #shipped()} is first called.
	 */
	private static final class Shipped {
		private static final JavaPacketData DATA = index();

		private static JavaPacketData index() {
			try (InputStream input = resource(INDEX)) {
				return PacketDataCodec.readIndex(input);
			} catch (IOException exception) {
				throw new UncheckedIOException("Could not read the shipped packet data index", exception);
			}
		}
	}
}

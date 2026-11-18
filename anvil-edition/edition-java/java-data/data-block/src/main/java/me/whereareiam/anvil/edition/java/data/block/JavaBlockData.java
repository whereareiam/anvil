package me.whereareiam.anvil.edition.java.data.block;

import me.whereareiam.anvil.edition.java.data.block.exception.BlockDataUnavailableException;
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
 * The block data Anvil ships for every Minecraft: Java Edition protocol it supports, by protocol number.
 *
 * <p>Every Minecraft version of one protocol has the same block registry, so the data is keyed by the protocol number
 * a player's release speaks, such as {@code 767} for 1.21 and 1.21.1. It is generated at build time from PrismarineJS
 * minecraft-data (MIT), pinned to one release of it, with the facts minecraft-data does not carry maintained by hand
 * from vanilla's code. A supported protocol the data source has no block data for is listed as missing and reported as
 * such, never served another protocol's data. Each registry is read on first use and kept; lookups are safe from any
 * thread.</p>
 *
 * <pre>{@code
 * JavaBlockData data = JavaBlockData.shipped();
 * BlockRegistry registry = data.require(766);
 * float friction = registry.state(stateId).getBlock().getMovement().getFriction();
 * }</pre>
 */
public final class JavaBlockData {
	private static final String INDEX = "block-data.dat";

	private final @NotNull Set<Integer> protocols;
	private final @NotNull Map<Integer, String> missing;
	private final @NotNull Map<Integer, BlockRegistry> registries = new ConcurrentHashMap<>();

	/**
	 * Creates the index of shipped block data.
	 *
	 * @param protocols the protocols with block data
	 * @param missing the reason of each supported protocol without block data
	 * @throws IllegalArgumentException when a protocol is listed both with and without data
	 */
	JavaBlockData(@NotNull Set<Integer> protocols, @NotNull Map<Integer, String> missing) {
		Set<Integer> both = new TreeSet<>(protocols);
		both.retainAll(missing.keySet());
		if (!both.isEmpty()) throw new IllegalArgumentException("Protocols " + both + " are listed with and without data");

		this.protocols = Collections.unmodifiableSet(new TreeSet<>(protocols));
		this.missing = Collections.unmodifiableMap(new TreeMap<>(missing));
	}

	/**
	 * Returns the block data shipped with Anvil, read from its resources on first use.
	 *
	 * @return the shipped block data
	 * @throws IllegalStateException when the shipped resources are missing
	 */
	public static @NotNull JavaBlockData shipped() {
		return Shipped.DATA;
	}

	/**
	 * The protocol numbers Anvil has block data for.
	 *
	 * @return the protocols, lowest first
	 */
	public @NotNull Set<Integer> protocols() {
		return protocols;
	}

	/**
	 * The supported protocol numbers Anvil has no block data for, because the data source lacks them.
	 *
	 * @return the protocols, lowest first
	 */
	public @NotNull Set<Integer> missingProtocols() {
		return missing.keySet();
	}

	/**
	 * Looks up the block registry of a protocol.
	 *
	 * @param protocol protocol number, such as {@code 767}
	 * @return the registry, or empty when Anvil has no block data for the protocol
	 * @throws IllegalStateException when the protocol's shipped resource is missing
	 */
	public @NotNull Optional<BlockRegistry> find(int protocol) {
		if (!protocols.contains(protocol)) return Optional.empty();

		return Optional.of(registries.computeIfAbsent(protocol, JavaBlockData::read));
	}

	/**
	 * Returns the block registry of a protocol, or reports why there is none.
	 *
	 * @param protocol protocol number, such as {@code 767}
	 * @return the registry
	 * @throws BlockDataUnavailableException when the protocol is not supported, or the data source has no block data
	 *         for it
	 * @throws IllegalStateException when the protocol's shipped resource is missing
	 */
	public @NotNull BlockRegistry require(int protocol) {
		String reason = missing.get(protocol);
		if (reason != null) throw new BlockDataUnavailableException(protocol, reason);

		return find(protocol).orElseThrow(() -> new BlockDataUnavailableException(protocol, "Anvil supports no such "
				+ "protocol; it ships block data for " + protocols + " and knows " + missing.keySet() + " without it"));
	}

	/**
	 * Why a supported protocol has no block data.
	 */
	@NotNull String missingReason(int protocol) {
		return missing.get(protocol);
	}

	/**
	 * The resource holding a protocol's registry, beside this class.
	 */
	static @NotNull String registryResource(int protocol) {
		return "blocks-" + protocol + ".dat";
	}

	/**
	 * The resource holding the index, beside this class.
	 */
	static @NotNull String indexResource() {
		return INDEX;
	}

	private static @NotNull BlockRegistry read(int protocol) {
		try (InputStream input = resource(registryResource(protocol))) {
			return BlockDataCodec.readRegistry(input);
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read the shipped block data of protocol " + protocol, exception);
		}
	}

	private static @NotNull InputStream resource(@NotNull String name) {
		InputStream stream = JavaBlockData.class.getResourceAsStream(name);
		if (stream == null) throw new IllegalStateException("Anvil's block data resource " + name + " is missing");

		return stream;
	}

	/**
	 * Reads the shipped index once, when {@link #shipped()} is first called.
	 */
	private static final class Shipped {
		private static final JavaBlockData DATA = index();

		private static JavaBlockData index() {
			try (InputStream input = resource(INDEX)) {
				return BlockDataCodec.readIndex(input);
			} catch (IOException exception) {
				throw new UncheckedIOException("Could not read the shipped block data index", exception);
			}
		}
	}
}

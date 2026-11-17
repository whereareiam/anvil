package me.whereareiam.anvil.protocol.java.item;

import me.whereareiam.anvil.protocol.java.item.exception.ItemDataUnavailableException;
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
 * The item keys Anvil ships for every Minecraft: Java Edition protocol it supports, by protocol number.
 *
 * <p>Every Minecraft version of one protocol numbers its items alike, so the data is keyed by the protocol number a
 * player's release speaks, such as {@code 767} for 1.21 and 1.21.1. It is generated at build time from the item files
 * of PrismarineJS minecraft-data (MIT), pinned to one release of it. A supported protocol the data source has no item
 * file for is listed as missing and reported as such, never served another protocol's keys. Each registry
 * is read on first use and kept; lookups are safe from any thread.</p>
 *
 * <pre>{@code
 * ItemRegistry registry = JavaItemData.shipped().require(767);
 * Optional<String> key = registry.key(stack.getId());
 * }</pre>
 */
public final class JavaItemData {
	private static final String INDEX = "item-data.dat";

	private final @NotNull Set<Integer> protocols;
	private final @NotNull Map<Integer, String> missing;
	private final @NotNull Map<Integer, ItemRegistry> registries = new ConcurrentHashMap<>();

	/**
	 * Creates the index of shipped item data.
	 *
	 * @param protocols the protocols with item keys
	 * @param missing the reason of each supported protocol without item keys
	 * @throws IllegalArgumentException when a protocol is listed both with and without data
	 */
	JavaItemData(@NotNull Set<Integer> protocols, @NotNull Map<Integer, String> missing) {
		Set<Integer> both = new TreeSet<>(protocols);
		both.retainAll(missing.keySet());
		if (!both.isEmpty()) throw new IllegalArgumentException("Protocols " + both + " are listed with and without data");

		this.protocols = Collections.unmodifiableSet(new TreeSet<>(protocols));
		this.missing = Collections.unmodifiableMap(new TreeMap<>(missing));
	}

	/**
	 * Returns the item data shipped with Anvil, read from its resources on first use.
	 *
	 * @return the shipped item data
	 * @throws IllegalStateException when the shipped resources are missing
	 */
	public static @NotNull JavaItemData shipped() {
		return Shipped.DATA;
	}

	/**
	 * The protocol numbers Anvil has item keys for.
	 *
	 * @return the protocols, lowest first
	 */
	public @NotNull Set<Integer> protocols() {
		return protocols;
	}

	/**
	 * The supported protocol numbers Anvil has no item keys for, because the data source lacks them.
	 *
	 * @return the protocols, lowest first
	 */
	public @NotNull Set<Integer> missingProtocols() {
		return missing.keySet();
	}

	/**
	 * Looks up the item registry of a protocol.
	 *
	 * @param protocol protocol number, such as {@code 767}
	 * @return the registry, or empty when Anvil has no item keys for the protocol
	 * @throws IllegalStateException when the protocol's shipped resource is missing
	 */
	public @NotNull Optional<ItemRegistry> find(int protocol) {
		if (!protocols.contains(protocol)) return Optional.empty();

		return Optional.of(registries.computeIfAbsent(protocol, JavaItemData::read));
	}

	/**
	 * Returns the item registry of a protocol, or reports why there is none.
	 *
	 * @param protocol protocol number, such as {@code 767}
	 * @return the registry
	 * @throws ItemDataUnavailableException when the protocol is not supported, or the data source has no item
	 *         keys for it
	 * @throws IllegalStateException when the protocol's shipped resource is missing
	 */
	public @NotNull ItemRegistry require(int protocol) {
		String reason = missing.get(protocol);
		if (reason != null) throw new ItemDataUnavailableException(protocol, reason);

		return find(protocol).orElseThrow(() -> new ItemDataUnavailableException(protocol, "Anvil supports no such "
				+ "protocol; it ships item data for " + protocols + " and knows " + missing.keySet() + " without it"));
	}

	/**
	 * Why a supported protocol has no item keys.
	 */
	@NotNull String missingReason(int protocol) {
		return missing.get(protocol);
	}

	/**
	 * The resource holding a protocol's registry, beside this class.
	 */
	static @NotNull String registryResource(int protocol) {
		return "items-" + protocol + ".dat";
	}

	/**
	 * The resource holding the index, beside this class.
	 */
	static @NotNull String indexResource() {
		return INDEX;
	}

	private static @NotNull ItemRegistry read(int protocol) {
		try (InputStream input = resource(registryResource(protocol))) {
			return ItemDataCodec.readRegistry(input);
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read the shipped item data of protocol " + protocol, exception);
		}
	}

	private static @NotNull InputStream resource(@NotNull String name) {
		InputStream stream = JavaItemData.class.getResourceAsStream(name);
		if (stream == null) throw new IllegalStateException("Anvil's item data resource " + name + " is missing");

		return stream;
	}

	/**
	 * Reads the shipped index once, when {@link #shipped()} is first called.
	 */
	private static final class Shipped {
		private static final JavaItemData DATA = index();

		private static JavaItemData index() {
			try (InputStream input = resource(INDEX)) {
				return ItemDataCodec.readIndex(input);
			} catch (IOException exception) {
				throw new UncheckedIOException("Could not read the shipped item data index", exception);
			}
		}
	}
}

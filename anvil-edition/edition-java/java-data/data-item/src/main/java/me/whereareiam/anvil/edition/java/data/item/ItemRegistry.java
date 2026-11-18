package me.whereareiam.anvil.edition.java.data.item;

import me.whereareiam.anvil.edition.java.data.item.model.ItemDataSource;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The items of one protocol's item registry, which every Minecraft version speaking it shares, looked up by the
 * numeric item ID that inventory packets carry or by the item's registry key.
 *
 * <p>A key is the game's registry key in the {@code minecraft} namespace, such as {@code minecraft:diamond_sword};
 * minecraft-data names each item by the key's path. {@link JavaItemData} creates registries; they are immutable and
 * safe to share between threads.</p>
 *
 * <pre>{@code
 * ItemRegistry registry = JavaItemData.shipped().require(774);
 * String key = registry.key(stack.getId()).orElse("unknown");
 * }</pre>
 */
public final class ItemRegistry {
	/**
	 * The namespace of every vanilla item.
	 */
	static final String NAMESPACE = "minecraft:";

	private final int protocol;
	private final @NotNull ItemDataSource source;
	private final @NotNull List<String> keys;
	private final @NotNull Map<String, Integer> ids;

	/**
	 * Creates a registry.
	 *
	 * @param protocol the protocol number
	 * @param source where the keys come from
	 * @param keys the registry key of each item, by item ID
	 * @throws IllegalArgumentException when a key is blank, lacks its namespace or is listed twice
	 */
	ItemRegistry(int protocol, @NotNull ItemDataSource source, @NotNull List<String> keys) {
		Map<String, Integer> ids = new HashMap<>();
		for (int id = 0; id < keys.size(); id++) {
			String key = keys.get(id);
			if (!key.startsWith(NAMESPACE) || key.length() == NAMESPACE.length())
				throw new IllegalArgumentException("Protocol " + protocol + " names item " + id + " '" + key + "'");
			if (ids.put(key, id) != null)
				throw new IllegalArgumentException("Protocol " + protocol + " names two items " + key);
		}

		this.protocol = protocol;
		this.source = source;
		this.keys = List.copyOf(keys);
		this.ids = Map.copyOf(ids);
	}

	/**
	 * Returns the protocol number the items belong to.
	 *
	 * @return the protocol, such as {@code 774}
	 */
	public int protocol() {
		return protocol;
	}

	/**
	 * Returns where the keys come from.
	 *
	 * @return the data's source
	 */
	public @NotNull ItemDataSource source() {
		return source;
	}

	/**
	 * Names an item by its ID.
	 *
	 * @param id the item's numeric ID, as inventory packets carry it
	 * @return the item's registry key, such as {@code minecraft:diamond}, or empty when the protocol has no such item
	 */
	public @NotNull Optional<String> key(int id) {
		if (id < 0 || id >= keys.size()) return Optional.empty();

		return Optional.of(keys.get(id));
	}

	/**
	 * Finds the ID of an item by its registry key.
	 *
	 * @param key the item's registry key, such as {@code minecraft:diamond}
	 * @return the item's numeric ID, or empty when the protocol has no such item
	 */
	public @NotNull OptionalInt id(@NotNull String key) {
		Integer id = ids.get(key);
		return id == null ? OptionalInt.empty() : OptionalInt.of(id);
	}

	/**
	 * Lists every item's registry key.
	 *
	 * @return the keys by item ID
	 */
	public @NotNull List<String> keys() {
		return keys;
	}
}

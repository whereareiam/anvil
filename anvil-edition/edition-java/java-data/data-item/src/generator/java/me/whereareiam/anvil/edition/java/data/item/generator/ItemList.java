package me.whereareiam.anvil.edition.java.data.item.generator;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads the items of a release's item registry from minecraft-data's {@code items.json}: an array of objects, each
 * with the item's numeric {@code id} and its {@code name}, the path of its registry key in the {@code minecraft}
 * namespace.
 *
 * <p>Every release's item registry starts with air, ID {@code 0}, but the files of the releases before 1.19.4 leave it
 * out, so a file without ID {@code 0} gets air there.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ItemList {
	private static final String NAMESPACE = "minecraft:";
	private static final String AIR = NAMESPACE + "air";

	/**
	 * Reads the registry key of every item.
	 *
	 * @param context what the file is, named in failures, such as its path and protocol
	 * @param items the file's content
	 * @return the keys, such as {@code minecraft:stone}, indexed by item ID
	 * @throws IllegalStateException when the file breaks the expected shape, or its IDs leave a gap or repeat
	 */
	public static @NotNull List<String> keys(@NotNull String context, @NotNull JsonNode items) {
		if (!items.isArray() || items.isEmpty()) throw new IllegalStateException(context + " lists no items");

		Map<Integer, String> byId = new TreeMap<>();
		for (JsonNode item : items) {
			JsonNode id = item.path("id");
			String name = item.path("name").asText("");
			if (!id.isInt()) throw new IllegalStateException(context + " lists an item without a numeric ID: " + item);
			if (name.isBlank()) throw new IllegalStateException(context + " names item " + id.asInt() + " blankly");
			if (byId.put(id.asInt(), NAMESPACE + name) != null)
				throw new IllegalStateException(context + " lists item ID " + id.asInt() + " twice");
		}

		byId.putIfAbsent(0, AIR);
		List<String> keys = new ArrayList<>(byId.size());
		for (Map.Entry<Integer, String> entry : byId.entrySet()) {
			if (entry.getKey() != keys.size())
				throw new IllegalStateException(context + " has no item with ID " + keys.size());
			keys.add(entry.getValue());
		}

		return keys;
	}
}

package me.whereareiam.anvil.protocol.java.api.model.nbt;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A compound tag: tags under string keys, in the order they were put. Its typed lookups return the tag under a key
 * when it has the asked type, so a reader needs no casts:
 *
 * <pre>{@code
 * NbtCompound payload = NbtCompound.builder()
 *         .put("source", new NbtString("dialog"))
 *         .build()
 *         .with("remember", NbtByte.of(true));
 * Optional<NbtString> source = payload.string("source");
 * }</pre>
 */
@Value
public class NbtCompound implements NbtTag {
	/**
	 * A compound without entries.
	 */
	public static final NbtCompound EMPTY = new NbtCompound(Map.of());

	/**
	 * The entries in the order they were put; unmodifiable.
	 */
	@NotNull Map<String, NbtTag> entries;

	/**
	 * Creates the compound from a copy of the entries, keeping their iteration order.
	 *
	 * @param entries the tags by key
	 */
	@Builder
	public NbtCompound(@NotNull @Singular("put") Map<String, NbtTag> entries) {
		this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
	}

	/**
	 * Returns whether the compound has an entry under the key.
	 *
	 * @param key the entry's key
	 * @return whether the entry exists
	 */
	public boolean contains(@NotNull String key) {
		return entries.containsKey(key);
	}

	/**
	 * Returns the tag under the key, whatever its type.
	 *
	 * @param key the entry's key
	 * @return the tag, or {@code null} when the compound has none under the key
	 */
	public @Nullable NbtTag get(@NotNull String key) {
		return entries.get(key);
	}

	/**
	 * Returns the tag under the key when it has the given type.
	 *
	 * @param key the entry's key
	 * @param type the tag type, such as {@code NbtList.class}
	 * @param <T> the tag type
	 * @return the tag, or empty when the compound has none under the key or one of another type
	 */
	public <T extends NbtTag> @NotNull Optional<T> find(@NotNull String key, @NotNull Class<T> type) {
		return Optional.ofNullable(entries.get(key))
				.filter(type::isInstance)
				.map(type::cast);
	}

	/**
	 * Returns the string tag under the key.
	 *
	 * @param key the entry's key
	 * @return the tag, or empty when the entry is missing or not a string
	 */
	public @NotNull Optional<NbtString> string(@NotNull String key) {
		return find(key, NbtString.class);
	}

	/**
	 * Returns the numeric tag under the key, of any numeric type.
	 *
	 * @param key the entry's key
	 * @return the tag, or empty when the entry is missing or not a number
	 */
	public @NotNull Optional<NbtNumber> number(@NotNull String key) {
		return find(key, NbtNumber.class);
	}

	/**
	 * Returns the list tag under the key.
	 *
	 * @param key the entry's key
	 * @return the tag, or empty when the entry is missing or not a list
	 */
	public @NotNull Optional<NbtList> list(@NotNull String key) {
		return find(key, NbtList.class);
	}

	/**
	 * Returns the compound tag under the key.
	 *
	 * @param key the entry's key
	 * @return the tag, or empty when the entry is missing or not a compound
	 */
	public @NotNull Optional<NbtCompound> compound(@NotNull String key) {
		return find(key, NbtCompound.class);
	}

	/**
	 * Returns a compound with the tag put under the key. An existing entry of the key keeps its position and takes
	 * the new tag; a new key is put last. This compound stays unchanged.
	 *
	 * @param key the entry's key
	 * @param tag the tag to put
	 * @return the new compound
	 */
	public @NotNull NbtCompound with(@NotNull String key, @NotNull NbtTag tag) {
		Map<String, NbtTag> changed = new LinkedHashMap<>(entries);
		changed.put(key, tag);

		return new NbtCompound(changed);
	}
}

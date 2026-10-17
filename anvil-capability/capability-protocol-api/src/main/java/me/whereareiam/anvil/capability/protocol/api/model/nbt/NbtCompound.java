package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * An NBT compound tag: named tags whose order is the order they were put in, which SNBT keeps.
 *
 * <pre>{@code
 * NbtCompound action = NbtCompound.builder()
 *         .entry("type", NbtString.of("minecraft:run_command"))
 *         .entry("command", NbtString.of("/hub"))
 *         .build();
 * String command = action.getString("command");
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class NbtCompound implements NbtTag {
	/**
	 * The compound without entries.
	 */
	public static final NbtCompound EMPTY = NbtCompound.builder().build();

	/**
	 * Named tags in their order.
	 */
	@NotNull @Singular Map<String, NbtTag> entries;

	/**
	 * Tells whether the compound has an entry.
	 *
	 * @param key entry name
	 * @return whether an entry has the name
	 */
	public boolean contains(@NotNull String key) {
		return entries.containsKey(key);
	}

	/**
	 * Returns an entry.
	 *
	 * @param key entry name
	 * @return the entry, or {@code null} when the compound has none of that name
	 */
	public @Nullable NbtTag get(@NotNull String key) {
		return entries.get(key);
	}

	/**
	 * Returns the value of a string entry.
	 *
	 * @param key entry name
	 * @return the string, or {@code null} when the entry is missing or not a string
	 */
	public @Nullable String getString(@NotNull String key) {
		return entries.get(key) instanceof NbtString value ? value.getValue() : null;
	}

	/**
	 * Returns the value of a numeric entry.
	 *
	 * @param key entry name
	 * @return the number, or {@code null} when the entry is missing or not a number
	 */
	public @Nullable Number getNumber(@NotNull String key) {
		return entries.get(key) instanceof NbtNumber value ? value.asNumber() : null;
	}

	/**
	 * Returns a compound entry.
	 *
	 * @param key entry name
	 * @return the compound, or {@code null} when the entry is missing or not a compound
	 */
	public @Nullable NbtCompound getCompound(@NotNull String key) {
		return entries.get(key) instanceof NbtCompound value ? value : null;
	}

	/**
	 * Returns a list entry.
	 *
	 * @param key entry name
	 * @return the list, or {@code null} when the entry is missing or not a list
	 */
	public @Nullable NbtList getList(@NotNull String key) {
		return entries.get(key) instanceof NbtList value ? value : null;
	}
}

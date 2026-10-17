package me.whereareiam.anvil.capability.dialogs.nbt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Typed reads of one compound of the NBT tree a dialogs port reports, with the defaults the vanilla codec applies
 * to missing values: an empty string, zero, and the empty compound.
 */
final class NbtCompound {
	private static final NbtCompound EMPTY = new NbtCompound(Map.of());

	private final Map<?, ?> entries;

	private NbtCompound(Map<?, ?> entries) {
		this.entries = entries;
	}

	static @NotNull NbtCompound of(@NotNull Map<?, ?> entries) {
		return new NbtCompound(entries);
	}

	/**
	 * Reads a list or a single compound as a list of compounds, as the vanilla codec writes a one-element list.
	 */
	static @NotNull List<NbtCompound> compounds(@Nullable Object value) {
		if (value instanceof Map<?, ?> map) return List.of(of(map));
		if (!(value instanceof List<?> list)) return List.of();

		List<NbtCompound> compounds = new ArrayList<>();
		for (Object element : list)
			if (element instanceof Map<?, ?> map) compounds.add(of(map));
		return compounds;
	}

	boolean has(@NotNull String key) {
		return entries.containsKey(key);
	}

	@Nullable Object get(@NotNull String key) {
		return entries.get(key);
	}

	@NotNull String string(@NotNull String key) {
		return string(key, "");
	}

	@NotNull String string(@NotNull String key, @NotNull String fallback) {
		return entries.get(key) instanceof String value ? value : fallback;
	}

	boolean bool(@NotNull String key, boolean fallback) {
		return entries.get(key) instanceof Number value ? value.byteValue() != 0 : fallback;
	}

	int integer(@NotNull String key, int fallback) {
		return entries.get(key) instanceof Number value ? value.intValue() : fallback;
	}

	float decimal(@NotNull String key, float fallback) {
		return entries.get(key) instanceof Number value ? value.floatValue() : fallback;
	}

	@NotNull NbtCompound compound(@NotNull String key) {
		NbtCompound compound = compoundOrNull(key);
		return compound == null ? EMPTY : compound;
	}

	@Nullable NbtCompound compoundOrNull(@NotNull String key) {
		return entries.get(key) instanceof Map<?, ?> map ? of(map) : null;
	}
}

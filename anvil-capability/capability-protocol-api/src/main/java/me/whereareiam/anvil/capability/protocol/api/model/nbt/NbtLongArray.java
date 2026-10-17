package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * An NBT long array tag. The tag keeps its own copy of the values, so it stays immutable.
 */
@ToString
@EqualsAndHashCode
public final class NbtLongArray implements NbtTag {
	private final long[] values;

	private NbtLongArray(long[] values) {
		this.values = values.clone();
	}

	/**
	 * Creates a tag of the given values.
	 *
	 * @param values the values, copied
	 * @return the tag
	 */
	public static @NotNull NbtLongArray of(long @NotNull ... values) {
		return new NbtLongArray(values);
	}

	/**
	 * Returns a copy of the tag's values.
	 *
	 * @return the values
	 */
	public long @NotNull [] getValues() {
		return values.clone();
	}
}

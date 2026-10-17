package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * An NBT int array tag. The tag keeps its own copy of the values, so it stays immutable.
 */
@ToString
@EqualsAndHashCode
public final class NbtIntArray implements NbtTag {
	private final int[] values;

	private NbtIntArray(int[] values) {
		this.values = values.clone();
	}

	/**
	 * Creates a tag of the given values.
	 *
	 * @param values the values, copied
	 * @return the tag
	 */
	public static @NotNull NbtIntArray of(int @NotNull ... values) {
		return new NbtIntArray(values);
	}

	/**
	 * Returns a copy of the tag's values.
	 *
	 * @return the values
	 */
	public int @NotNull [] getValues() {
		return values.clone();
	}
}

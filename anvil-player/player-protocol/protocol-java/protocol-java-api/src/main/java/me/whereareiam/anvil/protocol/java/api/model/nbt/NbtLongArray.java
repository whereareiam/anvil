package me.whereareiam.anvil.protocol.java.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A long array tag. The tag keeps its own copy of the values, so it stays unchanged whatever happens to the array
 * it was created from or to an array {@link #getValues()} returned.
 */
@Value
public class NbtLongArray implements NbtTag {
	/**
	 * The values in order.
	 */
	long @NotNull [] values;

	/**
	 * Creates the tag from a copy of the values.
	 *
	 * @param values the values in order
	 */
	public NbtLongArray(@NotNull long @NotNull [] values) {
		this.values = values.clone();
	}

	/**
	 * Returns a copy of the values.
	 *
	 * @return the values in order
	 */
	public long @NotNull [] getValues() {
		return values.clone();
	}
}

package me.whereareiam.anvil.protocol.java.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * An int array tag. The tag keeps its own copy of the values, so it stays unchanged whatever happens to the array
 * it was created from or to an array {@link #getValues()} returned.
 */
@Value
public class NbtIntArray implements NbtTag {
	/**
	 * The values in order.
	 */
	int @NotNull [] values;

	/**
	 * Creates the tag from a copy of the values.
	 *
	 * @param values the values in order
	 */
	public NbtIntArray(@NotNull int @NotNull [] values) {
		this.values = values.clone();
	}

	/**
	 * Returns a copy of the values.
	 *
	 * @return the values in order
	 */
	public int @NotNull [] getValues() {
		return values.clone();
	}
}

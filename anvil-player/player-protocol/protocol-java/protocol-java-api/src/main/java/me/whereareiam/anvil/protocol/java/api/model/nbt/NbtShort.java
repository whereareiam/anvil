package me.whereareiam.anvil.protocol.java.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A short tag: a signed 16-bit integer.
 */
@Value
public class NbtShort implements NbtNumber {
	/**
	 * The tag's value.
	 */
	short value;

	/**
	 * Returns the value as a {@link Short}.
	 *
	 * @return the boxed value
	 */
	@Override
	public @NotNull Number number() {
		return value;
	}
}

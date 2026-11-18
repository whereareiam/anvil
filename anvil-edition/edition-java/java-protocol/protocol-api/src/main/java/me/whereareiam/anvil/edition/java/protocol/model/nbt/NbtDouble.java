package me.whereareiam.anvil.edition.java.protocol.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A double tag: a 64-bit floating-point number.
 */
@Value
public class NbtDouble implements NbtNumber {
	/**
	 * The tag's value.
	 */
	double value;

	/**
	 * Returns the value as a {@link Double}.
	 *
	 * @return the boxed value
	 */
	@Override
	public @NotNull Number number() {
		return value;
	}
}

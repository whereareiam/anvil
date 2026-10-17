package me.whereareiam.anvil.protocol.java.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A float tag: a 32-bit floating-point number.
 */
@Value
public class NbtFloat implements NbtNumber {
	/**
	 * The tag's value.
	 */
	float value;

	/**
	 * Returns the value as a {@link Float}.
	 *
	 * @return the boxed value
	 */
	@Override
	public @NotNull Number number() {
		return value;
	}
}

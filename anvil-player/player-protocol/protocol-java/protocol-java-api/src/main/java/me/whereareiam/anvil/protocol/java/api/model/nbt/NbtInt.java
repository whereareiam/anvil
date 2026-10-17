package me.whereareiam.anvil.protocol.java.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * An int tag: a signed 32-bit integer.
 */
@Value
public class NbtInt implements NbtNumber {
	/**
	 * The tag's value.
	 */
	int value;

	/**
	 * Returns the value as a {@link Integer}.
	 *
	 * @return the boxed value
	 */
	@Override
	public @NotNull Number number() {
		return value;
	}
}

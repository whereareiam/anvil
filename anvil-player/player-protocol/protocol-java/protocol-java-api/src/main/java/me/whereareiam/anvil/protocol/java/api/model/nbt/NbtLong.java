package me.whereareiam.anvil.protocol.java.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A long tag: a signed 64-bit integer.
 */
@Value
public class NbtLong implements NbtNumber {
	/**
	 * The tag's value.
	 */
	long value;

	/**
	 * Returns the value as a {@link Long}.
	 *
	 * @return the boxed value
	 */
	@Override
	public @NotNull Number number() {
		return value;
	}
}

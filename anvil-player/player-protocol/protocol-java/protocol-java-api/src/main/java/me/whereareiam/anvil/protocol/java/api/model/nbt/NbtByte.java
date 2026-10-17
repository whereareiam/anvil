package me.whereareiam.anvil.protocol.java.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A byte tag: a signed 8-bit integer. The game writes a boolean as a byte tag of {@code 1} or {@code 0}.
 */
@Value
public class NbtByte implements NbtNumber {
	/**
	 * The tag's value.
	 */
	byte value;

	/**
	 * Returns the byte tag the game writes for a boolean.
	 *
	 * @param value the boolean
	 * @return a byte tag of {@code 1} for {@code true} and {@code 0} for {@code false}
	 */
	public static @NotNull NbtByte of(boolean value) {
		return new NbtByte((byte) (value ? 1 : 0));
	}

	/**
	 * Returns the value as a {@link Byte}.
	 *
	 * @return the boxed value
	 */
	@Override
	public @NotNull Number number() {
		return value;
	}
}

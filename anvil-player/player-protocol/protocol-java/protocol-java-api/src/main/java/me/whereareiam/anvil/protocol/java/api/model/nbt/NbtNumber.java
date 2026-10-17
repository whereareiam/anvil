package me.whereareiam.anvil.protocol.java.api.model.nbt;

import org.jetbrains.annotations.NotNull;

/**
 * A numeric tag: {@link NbtByte}, {@link NbtShort}, {@link NbtInt}, {@link NbtLong}, {@link NbtFloat} or
 * {@link NbtDouble}. The game reads a number field from a tag of any numeric type, so a reader that only needs the
 * number reads it through this interface.
 */
public sealed interface NbtNumber extends NbtTag permits NbtByte, NbtShort, NbtInt, NbtLong, NbtFloat, NbtDouble {
	/**
	 * Returns the tag's value boxed in its own Java type, such as a {@link Byte} for a byte tag.
	 *
	 * @return the value
	 */
	@NotNull Number number();

	/**
	 * Returns the tag read as a boolean, as the game reads one: {@code true} for any value other than zero.
	 *
	 * @return whether the value is not zero
	 */
	default boolean asBoolean() {
		return number().doubleValue() != 0;
	}
}

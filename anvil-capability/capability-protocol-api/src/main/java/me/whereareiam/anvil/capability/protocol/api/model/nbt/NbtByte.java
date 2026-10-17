package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * An NBT byte tag, which also carries booleans as 0 and 1.
 */
@Value(staticConstructor = "of")
public class NbtByte implements NbtNumber {
	/**
	 * The tag's value.
	 */
	byte value;

	/**
	 * Creates the byte tag Minecraft uses for a boolean.
	 *
	 * @param value the boolean
	 * @return a byte tag of 1 for {@code true} and 0 for {@code false}
	 */
	public static @NotNull NbtByte of(boolean value) {
		return of((byte) (value ? 1 : 0));
	}

	/**
	 * Reads the byte as Minecraft reads a boolean.
	 *
	 * @return whether the byte is not 0
	 */
	public boolean asBoolean() {
		return value != 0;
	}

	@Override
	public @NotNull Number asNumber() {
		return value;
	}
}

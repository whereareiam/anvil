package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * An NBT byte array tag. The tag keeps its own copy of the values, so it stays immutable.
 */
@ToString
@EqualsAndHashCode
public final class NbtByteArray implements NbtTag {
	private final byte[] values;

	private NbtByteArray(byte[] values) {
		this.values = values.clone();
	}

	/**
	 * Creates a tag of the given values.
	 *
	 * @param values the values, copied
	 * @return the tag
	 */
	public static @NotNull NbtByteArray of(byte @NotNull ... values) {
		return new NbtByteArray(values);
	}

	/**
	 * Returns a copy of the tag's values.
	 *
	 * @return the values
	 */
	public byte @NotNull [] getValues() {
		return values.clone();
	}
}

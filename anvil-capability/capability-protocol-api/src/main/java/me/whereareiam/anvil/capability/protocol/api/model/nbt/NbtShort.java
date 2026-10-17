package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * An NBT short tag.
 */
@Value(staticConstructor = "of")
public class NbtShort implements NbtNumber {
	/**
	 * The tag's value.
	 */
	short value;

	@Override
	public @NotNull Number asNumber() {
		return value;
	}
}

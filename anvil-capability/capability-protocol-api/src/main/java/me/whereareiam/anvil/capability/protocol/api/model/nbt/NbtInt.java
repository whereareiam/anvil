package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * An NBT int tag.
 */
@Value(staticConstructor = "of")
public class NbtInt implements NbtNumber {
	/**
	 * The tag's value.
	 */
	int value;

	@Override
	public @NotNull Number asNumber() {
		return value;
	}
}

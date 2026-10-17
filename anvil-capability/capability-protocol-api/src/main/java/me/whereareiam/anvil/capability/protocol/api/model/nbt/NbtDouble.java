package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * An NBT double tag.
 */
@Value(staticConstructor = "of")
public class NbtDouble implements NbtNumber {
	/**
	 * The tag's value.
	 */
	double value;

	@Override
	public @NotNull Number asNumber() {
		return value;
	}
}

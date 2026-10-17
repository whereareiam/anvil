package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * An NBT long tag.
 */
@Value(staticConstructor = "of")
public class NbtLong implements NbtNumber {
	/**
	 * The tag's value.
	 */
	long value;

	@Override
	public @NotNull Number asNumber() {
		return value;
	}
}

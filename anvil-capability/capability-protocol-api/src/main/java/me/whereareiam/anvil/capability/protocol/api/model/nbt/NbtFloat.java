package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * An NBT float tag.
 */
@Value(staticConstructor = "of")
public class NbtFloat implements NbtNumber {
	/**
	 * The tag's value.
	 */
	float value;

	@Override
	public @NotNull Number asNumber() {
		return value;
	}
}

package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * An NBT string tag.
 */
@Value(staticConstructor = "of")
public class NbtString implements NbtTag {
	/**
	 * The tag's value.
	 */
	@NotNull String value;
}

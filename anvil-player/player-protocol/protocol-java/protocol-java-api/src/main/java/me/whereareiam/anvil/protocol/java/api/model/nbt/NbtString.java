package me.whereareiam.anvil.protocol.java.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A string tag.
 */
@Value
public class NbtString implements NbtTag {
	/**
	 * The tag's text.
	 */
	@NotNull String value;
}

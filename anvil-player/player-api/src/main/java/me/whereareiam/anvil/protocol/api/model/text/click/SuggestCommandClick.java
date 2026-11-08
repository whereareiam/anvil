package me.whereareiam.anvil.protocol.api.model.text.click;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Puts text into the player's chat input.
 */
@Value
public class SuggestCommandClick implements TextClick {
	/**
	 * The text.
	 */
	@NotNull String command;
}

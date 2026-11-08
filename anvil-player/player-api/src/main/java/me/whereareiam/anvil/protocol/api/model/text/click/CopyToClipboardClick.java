package me.whereareiam.anvil.protocol.api.model.text.click;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Copies text to the player's clipboard.
 */
@Value
public class CopyToClipboardClick implements TextClick {
	/**
	 * The text.
	 */
	@NotNull String text;
}

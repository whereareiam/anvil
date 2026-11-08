package me.whereareiam.anvil.protocol.api.model.text.click;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Opens a URL after the player confirms it.
 */
@Value
public class OpenUrlClick implements TextClick {
	/**
	 * The URL.
	 */
	@NotNull String url;
}

package me.whereareiam.anvil.protocol.api.model.text.content;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Text as written.
 */
@Value
public class LiteralContent implements TextContent {
	/**
	 * The text, with its line breaks.
	 */
	@NotNull String text;
}

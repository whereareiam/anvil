package me.whereareiam.anvil.protocol.api.model.text.content;

import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The names of the entities a selector finds, which the server normally resolves before it sends the text.
 */
@Value
public class SelectorContent implements TextContent {
	/**
	 * The selector, such as {@code @p}.
	 */
	@NotNull String pattern;
	/**
	 * The text between two names, or {@code null} for the game's default, from Minecraft 1.17.
	 */
	@Nullable Text separator;
}

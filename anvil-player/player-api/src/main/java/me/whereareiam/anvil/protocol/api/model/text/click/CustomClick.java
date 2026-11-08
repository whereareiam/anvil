package me.whereareiam.anvil.protocol.api.model.text.click;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Sends the server a custom action, from Minecraft 1.21.6. Its payload stays in the player's worker, which
 * sends it with the click, as a dialog's custom actions do.
 */
@Value
public class CustomClick implements TextClick {
	/**
	 * The action's identifier, such as {@code arbor:vote}.
	 */
	@NotNull String id;
}

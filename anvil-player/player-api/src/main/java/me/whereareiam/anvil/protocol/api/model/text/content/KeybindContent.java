package me.whereareiam.anvil.protocol.api.model.text.content;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The key the player bound to a control, which the client shows by its own settings.
 */
@Value
public class KeybindContent implements TextContent {
	/**
	 * The control, such as {@code key.jump}.
	 */
	@NotNull String keybind;
}

package me.whereareiam.anvil.capability.messages.model;

import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * A message the player's client adds to its chat, as the server sent it, with the identity the worker gave it so that
 * a later deletion can name it.
 */
@Value
public class ChatMessage {
	/**
	 * Identity of the message, unique for the player.
	 */
	long id;
	/**
	 * The message as the server sent it, with its components, styles and line breaks.
	 */
	@NotNull Text text;
}

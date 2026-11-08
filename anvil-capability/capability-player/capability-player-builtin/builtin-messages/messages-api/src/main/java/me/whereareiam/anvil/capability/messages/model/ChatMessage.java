package me.whereareiam.anvil.capability.messages.model;

import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.StyledText;
import org.jetbrains.annotations.NotNull;

/**
 * A message the player's client adds to its chat, as the chat shows it, with the identity the worker gave it so that
 * a later deletion can name it.
 */
@Value
public class ChatMessage {
	/**
	 * Identity of the message, unique for the player.
	 */
	long id;
	/**
	 * The message as the chat shows it, with its styles and line breaks.
	 */
	@NotNull StyledText text;
}

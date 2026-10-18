package me.whereareiam.anvil.capability.messages.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A message the player's client adds to its chat, as plain text, with the identity the worker gave it so that a
 * later deletion can name it.
 */
@Value
public class ChatMessage {
	/**
	 * Identity of the message, unique for the player.
	 */
	long id;
	/**
	 * The message as plain text, with its line breaks.
	 */
	@NotNull String text;
}

package me.whereareiam.anvil.capability.messages.model;

import lombok.Value;
import me.whereareiam.anvil.capability.messages.type.ChatDropReason;
import org.jetbrains.annotations.NotNull;

/**
 * A chat message that reached the player's client but that its chat left out, as plain text, with the reason. A
 * failed wait names such messages, so a test can tell a message the server never sent from one the player could not
 * see.
 */
@Value
public class DroppedChat {
	/**
	 * The message as plain text.
	 */
	@NotNull String text;
	/**
	 * Why the chat left it out.
	 */
	@NotNull ChatDropReason reason;
}

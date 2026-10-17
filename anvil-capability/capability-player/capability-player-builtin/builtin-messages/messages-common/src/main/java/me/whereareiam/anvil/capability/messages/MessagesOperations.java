package me.whereareiam.anvil.capability.messages;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;

/**
 * Typed messages operations and the received-message event shared by the host messages capability and the
 * worker's messages binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessagesOperations {
	/**
	 * Sends a public chat message.
	 */
	public static final ChannelOperation<MessageText, Void> CHAT = new ChannelOperation<>("messages.chat", MessageText.class, Void.class);
	/**
	 * Sends a server command without its leading slash.
	 */
	public static final ChannelOperation<MessageText, Void> COMMAND = new ChannelOperation<>("messages.command", MessageText.class, Void.class);
	/**
	 * A message the player's current native session received, as plain text.
	 */
	public static final EventDescriptor<MessageText> RECEIVED = new EventDescriptor<>("messages.received", MessageText.class);
}

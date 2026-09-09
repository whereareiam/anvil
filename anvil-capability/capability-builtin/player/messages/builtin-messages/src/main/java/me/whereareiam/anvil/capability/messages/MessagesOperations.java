package me.whereareiam.anvil.capability.messages;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.messages.model.MessageText;

/**
 * Typed messages operations implemented by native capability bindings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessagesOperations {
	/**
	 * Executes the chat operation with a registered request schema.
	 */
	public static final ChannelOperation<MessageText, Void> CHAT = new ChannelOperation<>("messages.chat", MessageText.class, Void.class);
	/**
	 * Executes the command operation with a registered request schema.
	 */
	public static final ChannelOperation<MessageText, Void> COMMAND = new ChannelOperation<>("messages.command", MessageText.class, Void.class);
	/**
	 * Plain-text message observation emitted by the native binding.
	 */
	public static final EventDescriptor<MessageText> RECEIVED = new EventDescriptor<>("messages.received", MessageText.class);
}

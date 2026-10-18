package me.whereareiam.anvil.capability.messages.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.messages.model.ChatMessage;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * The channel contract between the host messages capability and its worker extensions: the capability ID both
 * register under, the operations the host requests and the events the worker emits: every received message for the
 * history, and what the client's chat does with them.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessagesChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.messages";
	/**
	 * Sends a public chat message.
	 */
	public static final OperationDescriptor<MessageText, Void> CHAT = new OperationDescriptor<>(
			"messages.chat", MessageText.class, Void.class
	);
	/**
	 * Sends a server command without its leading slash.
	 */
	public static final OperationDescriptor<MessageText, Void> COMMAND = new OperationDescriptor<>(
			"messages.command", MessageText.class, Void.class
	);
	/**
	 * A message the player's current connection received, as plain text.
	 */
	public static final EventDescriptor<MessageText> RECEIVED = new EventDescriptor<>(
			"messages.received", MessageText.class
	);
	/**
	 * A message the client adds to its chat; action-bar text and fully filtered player chat never are.
	 */
	public static final EventDescriptor<ChatMessage> CHAT_ADDED = new EventDescriptor<>(
			"messages.chat-added", ChatMessage.class
	);
	/**
	 * The server deleted the chat message with this identity, which the client replaces with its deleted marker.
	 */
	public static final EventDescriptor<Long> CHAT_DELETED = new EventDescriptor<>("messages.chat-deleted", Long.class);
	/**
	 * The client starts a new connection, which starts with an empty chat.
	 */
	public static final EventDescriptor<Void> CHAT_CLEARED = new EventDescriptor<>("messages.chat-cleared", Void.class);
}

package me.whereareiam.anvil.capability.messages.java;

import me.whereareiam.anvil.capability.messages.channel.MessagesChannel;
import me.whereareiam.anvil.capability.messages.model.ChatMessage;
import me.whereareiam.anvil.capability.messages.model.DroppedChat;
import me.whereareiam.anvil.capability.messages.type.ChatDropReason;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.chat.MessageSignature;
import me.whereareiam.anvil.protocol.java.api.model.chat.PackedMessageSignature;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDeleteChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.type.FilterMaskType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Installs the built-in messages capability for players that speak the Java Edition protocol: it sends chat and
 * commands and reports, as plain text, what the vanilla client's chat does with each chat message that reaches it. A
 * system message shown as an overlay is action-bar text, which the screen capability follows, not chat.
 *
 * <p>The chat shows the other system messages, disguised chat and player chat, but only while the client has a player:
 * from the server's login packet until the server returns the client to configuration or the connection ends. It
 * leaves out a message that arrives at another time, such as one a proxy sends while the client still configures,
 * and player chat the server's filter hides completely; the worker reports such a message as dropped, with the
 * reason. A new connection starts the chat empty, while a return to configuration keeps it.</p>
 *
 * <p>The server deletes a player's message by its signature, which the worker unpacks against the client's
 * {@link MessageSignatureCache}, as the client does, and reports by the identity it gave the message. A release
 * without disguised chat or deletions simply never delivers one, and a protocol without logins keeps one signature
 * cache for each connection and, having no login to wait for, shows every message.</p>
 */
public final class JavaMessagesExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return MessagesChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		operations.register(MessagesChannel.CHAT, request -> {
			protocol.send(new ServerboundChat(request.getText()));
			return null;
		});
		operations.register(MessagesChannel.COMMAND, request -> {
			protocol.send(new ServerboundChatCommand(request.getText()));
			return null;
		});

		AtomicLong ids = new AtomicLong();
		boolean logsIn = protocol.supports(ClientboundLogin.class);
		AtomicReference<ConnectionChat> connection = new AtomicReference<>(new ConnectionChat(!logsIn));
		List<Subscription> listening = new ArrayList<>();
		listening.add(player.bindConnection(() -> {
			connection.set(new ConnectionChat(!logsIn));
			player.emit(MessagesChannel.CHAT_CLEARED, null);
			return () -> {
			};
		}));
		if (logsIn)
			listening.add(protocol.listen(ClientboundLogin.class, ignored -> connection.get().login()));
		if (protocol.supports(ClientboundStartConfiguration.class))
			listening.add(protocol.listen(ClientboundStartConfiguration.class, ignored -> connection.get().configure()));
		listening.add(protocol.listen(ClientboundSystemChat.class, chat -> {
			if (chat.isOverlay()) return;

			show(player, connection.get(), ids, chat.getText());
		}));
		listening.add(protocol.listen(ClientboundPlayerChat.class, chat -> {
			ConnectionChat current = connection.get();
			if (!current.accept(chat)) return;
			if (chat.getFilterMask() == FilterMaskType.FULLY_FILTERED) {
				dropped(player, chat.getText(), ChatDropReason.FULLY_FILTERED);
				return;
			}

			Long id = show(player, current, ids, chat.getText());
			if (id != null) current.remember(chat.getSignature(), id);
		}));
		if (protocol.supports(ClientboundDisguisedChat.class))
			listening.add(protocol.listen(ClientboundDisguisedChat.class, chat ->
					show(player, connection.get(), ids, chat.getText())));
		if (protocol.supports(ClientboundDeleteChat.class))
			listening.add(protocol.listen(ClientboundDeleteChat.class, delete -> {
				Long id = connection.get().deleted(delete.getSignature());
				if (id != null) player.emit(MessagesChannel.CHAT_DELETED, id);
			}));
		Subscription subscriptions = Subscription.all(listening);

		return subscriptions::close;
	}

	/**
	 * Shows a message in the chat while the client has a player, and reports it as dropped otherwise.
	 *
	 * @return the identity the shown message got, or null when the chat dropped it
	 */
	private static @Nullable Long show(
			PlayerBindingContext<Object> player,
			ConnectionChat connection,
			AtomicLong ids,
			String text
	) {
		if (!connection.hasPlayer()) {
			dropped(player, text, ChatDropReason.NO_PLAYER);
			return null;
		}

		long id = ids.getAndIncrement();
		player.emit(MessagesChannel.CHAT_ADDED, new ChatMessage(id, text));
		return id;
	}

	private static void dropped(PlayerBindingContext<Object> player, String text, ChatDropReason reason) {
		player.emit(MessagesChannel.CHAT_DROPPED, new DroppedChat(text, reason));
	}

	/**
	 * The chat state of one connection: whether the client has a player, which the login creates and a return to
	 * configuration removes, the client's signature cache, which every login replaces, and the signed messages still in
	 * the client's chat, by signature, which last as long as the chat does.
	 */
	private static final class ConnectionChat {
		/**
		 * Number of messages the vanilla client keeps in its chat, and so can still delete.
		 */
		private static final int KEPT_MESSAGES = 100;

		private final Map<MessageSignature, Long> shown = new LinkedHashMap<>();
		private MessageSignatureCache cache = new MessageSignatureCache();
		private boolean hasPlayer;

		private ConnectionChat(boolean hasPlayer) {
			this.hasPlayer = hasPlayer;
		}

		synchronized void login() {
			cache = new MessageSignatureCache();
			hasPlayer = true;
		}

		synchronized void configure() {
			hasPlayer = false;
		}

		/**
		 * Tells whether the client has a player, without which the vanilla client drops every chat message.
		 */
		synchronized boolean hasPlayer() {
			return hasPlayer;
		}

		/**
		 * Pushes a player message's signatures to the cache, as the client does before it decides whether to show it,
		 * and tells whether the client goes on to decide: a message whose last seen signatures the client cannot unpack
		 * is a protocol error.
		 */
		synchronized boolean accept(ClientboundPlayerChat chat) {
			List<MessageSignature> lastSeen = new ArrayList<>();
			for (PackedMessageSignature packed : chat.getLastSeen()) {
				MessageSignature signature = cache.unpack(packed);
				if (signature == null) return false;

				lastSeen.add(signature);
			}
			cache.push(lastSeen, chat.getSignature());

			return true;
		}

		synchronized void remember(@Nullable MessageSignature signature, long id) {
			if (signature == null) return;

			shown.put(signature, id);
			if (shown.size() > KEPT_MESSAGES) shown.remove(shown.keySet().iterator().next());
		}

		synchronized @Nullable Long deleted(PackedMessageSignature packed) {
			MessageSignature signature = cache.unpack(packed);

			return signature == null ? null : shown.remove(signature);
		}
	}
}

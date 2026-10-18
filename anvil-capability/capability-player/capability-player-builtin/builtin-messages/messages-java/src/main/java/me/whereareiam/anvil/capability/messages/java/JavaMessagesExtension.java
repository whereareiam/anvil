package me.whereareiam.anvil.capability.messages.java;

import me.whereareiam.anvil.capability.messages.channel.MessagesChannel;
import me.whereareiam.anvil.capability.messages.model.ChatMessage;
import me.whereareiam.anvil.capability.messages.model.MessageText;
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
 * commands, emits every chat message the player receives, overlays included, as plain text, and reports what the
 * vanilla client's chat does with them. The chat takes system messages that are not overlays, disguised chat and
 * player chat the server's filter does not hide completely; a new connection starts it empty, while a return to
 * configuration keeps it. The server deletes a player's message by its signature, which the worker unpacks against
 * the client's {@link MessageSignatureCache}, as the client does, and reports by the identity it gave the message. A
 * release without disguised chat or deletions simply never delivers one, and a protocol without logins keeps one
 * signature cache for each connection.
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
		AtomicReference<ConnectionChat> connection = new AtomicReference<>(new ConnectionChat());
		List<Subscription> listening = new ArrayList<>();
		listening.add(player.bindConnection(() -> {
			connection.set(new ConnectionChat());
			player.emit(MessagesChannel.CHAT_CLEARED, null);
			return () -> {
			};
		}));
		if (protocol.supports(ClientboundLogin.class))
			listening.add(protocol.listen(ClientboundLogin.class, ignored -> connection.get().login()));
		listening.add(protocol.listen(ClientboundSystemChat.class, chat -> {
			received(player, chat.getText());
			if (!chat.isOverlay()) added(player, ids.getAndIncrement(), chat.getText());
		}));
		listening.add(protocol.listen(ClientboundPlayerChat.class, chat -> {
			received(player, chat.getText());
			if (!connection.get().accept(chat)) return;

			long id = ids.getAndIncrement();
			connection.get().remember(chat.getSignature(), id);
			added(player, id, chat.getText());
		}));
		if (protocol.supports(ClientboundDisguisedChat.class))
			listening.add(protocol.listen(ClientboundDisguisedChat.class, chat -> {
				received(player, chat.getText());
				added(player, ids.getAndIncrement(), chat.getText());
			}));
		if (protocol.supports(ClientboundDeleteChat.class))
			listening.add(protocol.listen(ClientboundDeleteChat.class, delete -> {
				Long id = connection.get().deleted(delete.getSignature());
				if (id != null) player.emit(MessagesChannel.CHAT_DELETED, id);
			}));
		Subscription subscriptions = Subscription.all(listening);

		return subscriptions::close;
	}

	private static void received(PlayerBindingContext<Object> player, String text) {
		player.emit(MessagesChannel.RECEIVED, new MessageText(text));
	}

	private static void added(PlayerBindingContext<Object> player, long id, String text) {
		player.emit(MessagesChannel.CHAT_ADDED, new ChatMessage(id, text));
	}

	/**
	 * The chat state of one connection: the client's signature cache, which every login replaces, and the signed
	 * messages still in the client's chat, by signature, which last as long as the chat does.
	 */
	private static final class ConnectionChat {
		/**
		 * Number of messages the vanilla client keeps in its chat, and so can still delete.
		 */
		private static final int KEPT_MESSAGES = 100;

		private final Map<MessageSignature, Long> shown = new LinkedHashMap<>();
		private MessageSignatureCache cache = new MessageSignatureCache();

		synchronized void login() {
			cache = new MessageSignatureCache();
		}

		/**
		 * Pushes a player message's signatures to the cache, as the client does before it decides whether to show it,
		 * and tells whether the client shows it: a message whose last seen signatures the client cannot unpack is a
		 * protocol error, and a fully filtered one is hidden.
		 */
		synchronized boolean accept(ClientboundPlayerChat chat) {
			List<MessageSignature> lastSeen = new ArrayList<>();
			for (PackedMessageSignature packed : chat.getLastSeen()) {
				MessageSignature signature = cache.unpack(packed);
				if (signature == null) return false;

				lastSeen.add(signature);
			}
			cache.push(lastSeen, chat.getSignature());

			return chat.getFilterMask() != FilterMaskType.FULLY_FILTERED;
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

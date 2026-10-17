package me.whereareiam.anvil.capability.messages.java;

import me.whereareiam.anvil.capability.messages.channel.MessagesChannel;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Installs the built-in messages capability for players that speak the Java Edition protocol: it sends chat and
 * commands, and emits every chat message the player receives, overlays included, as plain text. A release without
 * disguised chat simply never delivers one.
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

		List<Subscription> received = new ArrayList<>();
		received.add(protocol.listen(ClientboundSystemChat.class, chat -> received(player, chat.getText())));
		received.add(protocol.listen(ClientboundPlayerChat.class, chat -> received(player, chat.getText())));
		if (protocol.supports(ClientboundDisguisedChat.class))
			received.add(protocol.listen(ClientboundDisguisedChat.class, chat -> received(player, chat.getText())));
		Subscription listening = Subscription.all(received);

		return listening::close;
	}

	private static void received(PlayerBindingContext<Object> player, String text) {
		player.emit(MessagesChannel.RECEIVED, new MessageText(text));
	}
}

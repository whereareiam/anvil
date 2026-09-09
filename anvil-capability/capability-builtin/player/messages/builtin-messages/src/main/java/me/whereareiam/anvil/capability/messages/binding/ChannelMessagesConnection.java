package me.whereareiam.anvil.capability.messages.binding;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.messages.MessagesConnection;
import me.whereareiam.anvil.capability.messages.MessagesOperations;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Maps the scoped typed channel to the messages implementation's own connection contract.
 */
@RequiredArgsConstructor
final class ChannelMessagesConnection implements MessagesConnection {
	private final @NotNull ProtocolPlayerCapabilityContext context;

	public void chat(@NotNull String message) { context.channel().request(MessagesOperations.CHAT, new MessageText(message)); }
	public void command(@NotNull String command) { context.channel().request(MessagesOperations.COMMAND, new MessageText(command)); }
	public void observe(@NotNull Consumer<String> observer) {
		context.channel().subscribe(MessagesOperations.RECEIVED, text -> observer.accept(text.getText()));
	}

	public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
		context.channel().await(condition, description, timeout);
	}
}

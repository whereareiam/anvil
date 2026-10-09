package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Messages capability driven over a player's typed channel: chat and commands go to the worker's messages binding,
 * and every received-message event the worker emits is kept in the player's history, across reconnects. The
 * channel releases the event subscription together with the player.
 */
final class ChannelMessages implements Messages {
	private final CapabilityChannel channel;
	private final CopyOnWriteArrayList<String> history = new CopyOnWriteArrayList<>();

	ChannelMessages(@NotNull CapabilityChannel channel) {
		this.channel = channel;
		channel.subscribe(MessagesOperations.RECEIVED, message -> history.add(message.getText()));
	}

	@Override
	public void chat(@NotNull String message) {
		channel.request(MessagesOperations.CHAT, new MessageText(message));
	}

	@Override
	public void command(@NotNull String command) {
		String withoutSlash = command.startsWith("/") ? command.substring(1) : command;
		channel.request(MessagesOperations.COMMAND, new MessageText(withoutSlash));
	}

	@Override
	public @NotNull List<String> history() {
		return List.copyOf(history);
	}

	@Override
	public @NotNull String received(@NotNull String text, @NotNull Duration timeout) {
		channel.await(() -> history.stream().anyMatch(message -> message.contains(text)),
				"receive a message containing '" + text + "'", timeout);
		return history.reversed().stream()
				.filter(message -> message.contains(text))
				.findFirst()
				.orElseThrow();
	}
}

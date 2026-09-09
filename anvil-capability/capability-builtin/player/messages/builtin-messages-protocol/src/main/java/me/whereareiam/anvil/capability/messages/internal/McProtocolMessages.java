package me.whereareiam.anvil.capability.messages.internal;

import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.messages.MessagesConnection;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Message capability backed by MCProtocol operations and inbound packet events.
 */
public final class McProtocolMessages implements Messages {
	private final MessagesConnection connection;
	private final CopyOnWriteArrayList<String> history = new CopyOnWriteArrayList<>();

	public McProtocolMessages(MessagesConnection connection) {
		this.connection = connection;
		connection.observe(history::add);
	}

	@Override
	public void chat(@NotNull String message) {
		connection.chat(message);
	}

	@Override
	public void command(@NotNull String command) {
		connection.command(command.startsWith("/") ? command.substring(1) : command);
	}

	@Override
	public @NotNull List<String> history() {
		return List.copyOf(history);
	}

	@Override
	public @NotNull String received(@NotNull String text, @NotNull Duration timeout) {
		connection.await(() -> history.stream().anyMatch(message -> message.contains(text)),
				"receive a message containing '" + text + "'", timeout);
		return history.reversed().stream()
				.filter(message -> message.contains(text))
				.findFirst()
				.orElseThrow();
	}
}

package me.whereareiam.anvil.capability.messages.internal;

import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
import me.whereareiam.anvil.capability.messages.Messages;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Message capability backed by MCProtocol operations and inbound packet events.
 */
final class McProtocolMessages implements Messages {
	private final ProtocolPlayerConnection connection;
	private final CopyOnWriteArrayList<String> history = new CopyOnWriteArrayList<>();

	McProtocolMessages(ProtocolPlayerConnection connection) {
		this.connection = connection;
		connection.subscribe("messages.received", payload -> history.add(payload.path("text").asText()));
	}

	@Override
	public void chat(@NotNull String message) {
		connection.execute("messages.chat", arguments -> arguments.put("message", message));
	}

	@Override
	public void command(@NotNull String command) {
		connection.execute("messages.command", arguments -> arguments.put(
				"command",
				command.startsWith("/") ? command.substring(1) : command
		));
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

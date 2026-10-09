package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.messages.model.ReceivedMessage;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.Objects;
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

	@Override
	public int checkpoint() {
		return history.size();
	}

	@Override
	public @NotNull ReceivedMessage received(@NotNull Predicate<String> matcher, int after, @NotNull Duration timeout) {
		channel.await(() -> find(matcher, after) != null, "receive a matching message after message " + after, timeout);
		return Objects.requireNonNull(find(matcher, after));
	}

	@Override
	public void notReceived(@NotNull Predicate<String> matcher, int after, @NotNull Duration duration) {
		try {
			channel.await(() -> find(matcher, after) != null, "receive a matching message after message " + after, duration);
		} catch (IllegalStateException quiet) {
			if (find(matcher, after) == null) return;
		}

		throw new IllegalStateException("Player received a message it must not receive: "
				+ Objects.requireNonNull(find(matcher, after)).getText());
	}

	private @Nullable ReceivedMessage find(Predicate<String> matcher, int after) {
		List<String> snapshot = List.copyOf(history);
		for (int index = Math.max(0, after); index < snapshot.size(); index++)
			if (matcher.test(snapshot.get(index)))
				return new ReceivedMessage(snapshot.get(index), index + 1);

		return null;
	}
}

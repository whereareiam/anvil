package me.whereareiam.anvil.capability.messages;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Messages capability driven over a player's typed channel: chat and commands go to the worker's messages binding,
 * and every received-message event the worker emits is kept in the player's history, across reconnects. The
 * channel releases the event subscription together with the player.
 */
final class ChannelMessages implements Messages {
	private final ProtocolChannel channel;
	private final CopyOnWriteArrayList<String> history = new CopyOnWriteArrayList<>();

	ChannelMessages(@NotNull ProtocolChannel channel) {
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
	public @NotNull MessageWatch watch() {
		return new Watch(new HistoryCursor<>(this::history));
	}

	/**
	 * A watch over this player's history, which waits through the player's channel.
	 */
	@RequiredArgsConstructor
	private final class Watch implements MessageWatch {
		private final HistoryCursor<String> cursor;

		@Override
		public @NotNull String received(@NotNull Predicate<String> matcher, @NotNull Duration timeout) {
			channel.await(() -> cursor.find(matcher) != null, "receive a matching message", timeout);
			return Objects.requireNonNull(cursor.take(matcher));
		}

		@Override
		public void neverReceived(@NotNull Predicate<String> matcher, @NotNull Duration duration) {
			try {
				channel.await(() -> cursor.find(matcher) != null, "receive a matching message", duration);
			} catch (IllegalStateException quiet) {
				if (cursor.find(matcher) == null) return;
			}

			throw new IllegalStateException("Player received a message it must not receive: "
					+ Objects.requireNonNull(cursor.find(matcher)));
		}

		@Override
		public @NotNull Expectation expectReceived(@NotNull Predicate<String> matcher) {
			return Expectation.of("receive a matching message", () -> cursor.find(matcher) != null);
		}

		@Override
		public @NotNull List<String> unread() {
			return cursor.unread();
		}
	}
}

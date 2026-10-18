package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.messages.channel.MessagesChannel;
import me.whereareiam.anvil.capability.messages.model.ChatLines;
import me.whereareiam.anvil.capability.messages.model.DroppedChat;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.messages.type.ChatDropReason;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Messages capability driven over a player's typed channel: chat and commands go to the worker's messages binding.
 * Every message the worker reports the chat showing is kept in the player's history, across reconnects, and in a
 * {@link ChatBuffer} together with the server's deletions and the empty chat of a new connection. The messages the
 * worker reports the chat leaving out are kept only to explain a wait that fails. The channel releases the event
 * subscriptions together with the player.
 */
final class ChannelMessages implements Messages {
	private final ProtocolChannel channel;
	private final CopyOnWriteArrayList<String> history = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<DroppedChat> dropped = new CopyOnWriteArrayList<>();
	private final ChatBuffer chat = new ChatBuffer();

	ChannelMessages(@NotNull ProtocolChannel channel) {
		this.channel = channel;
		channel.subscribe(MessagesChannel.CHAT_ADDED, message -> {
			chat.add(message.getId(), message.getText());
			history.add(message.getText());
		});
		channel.subscribe(MessagesChannel.CHAT_DROPPED, dropped::add);
		channel.subscribe(MessagesChannel.CHAT_DELETED, chat::delete);
		channel.subscribe(MessagesChannel.CHAT_CLEARED, ignored -> chat.clear());
	}

	@Override
	public void chat(@NotNull String message) {
		channel.request(MessagesChannel.CHAT, new MessageText(message));
	}

	@Override
	public void command(@NotNull String command) {
		String withoutSlash = command.startsWith("/") ? command.substring(1) : command;
		channel.request(MessagesChannel.COMMAND, new MessageText(withoutSlash));
	}

	@Override
	public @NotNull List<String> history() {
		return List.copyOf(history);
	}

	@Override
	public @NotNull String received(@NotNull String text, @NotNull Duration timeout) {
		await(() -> history.stream().anyMatch(message -> message.contains(text)),
				"see a message containing '" + text + "' in chat", timeout, () -> List.copyOf(dropped),
				message -> message.contains(text));
		return history.reversed().stream()
				.filter(message -> message.contains(text))
				.findFirst()
				.orElseThrow();
	}

	@Override
	public @NotNull ChatLines chatLines() {
		return chat.lines();
	}

	@Override
	public @NotNull MessageWatch watch() {
		return new Watch();
	}

	/**
	 * Waits for a condition and, when it fails, names the matching messages among those the chat left out, which tells a
	 * message the server never sent from one the player could not see.
	 */
	private void await(
			@NotNull BooleanSupplier condition,
			@NotNull String description,
			@NotNull Duration timeout,
			@NotNull Supplier<List<DroppedChat>> left,
			@NotNull Predicate<String> matcher
	) {
		try {
			channel.await(condition, description, timeout);
		} catch (IllegalStateException failure) {
			List<DroppedChat> matching = left.get().stream().filter(message -> matcher.test(message.getText())).toList();
			if (matching.isEmpty()) throw failure;

			throw new IllegalStateException(failure.getMessage() + "\nThe chat left out matching messages that reached "
					+ "the client: " + matching.stream().map(ChannelMessages::describe).collect(Collectors.joining(", ")),
					failure);
		}
	}

	private static String describe(DroppedChat message) {
		String reason = message.getReason() == ChatDropReason.NO_PLAYER
				? "the client had no player"
				: "the server's filter hid it";
		return "'" + message.getText() + "' (" + reason + ")";
	}

	/**
	 * A watch over this player's history, which waits through the player's channel. It keeps a position in the
	 * dropped messages too, so a failed wait names only those left out since the previous wait.
	 */
	private final class Watch implements MessageWatch {
		private final HistoryCursor<String> cursor = new HistoryCursor<>(ChannelMessages.this::history);
		private HistoryCursor<DroppedChat> droppedSince = droppedCursor();

		@Override
		public @NotNull String received(@NotNull Predicate<String> matcher, @NotNull Duration timeout) {
			await(() -> cursor.find(matcher) != null, "see a matching message in chat", timeout, droppedSince::unread,
					matcher);
			droppedSince = droppedCursor();
			return Objects.requireNonNull(cursor.take(matcher));
		}

		@Override
		public void neverReceived(@NotNull Predicate<String> matcher, @NotNull Duration duration) {
			try {
				channel.await(() -> cursor.find(matcher) != null, "see a matching message in chat", duration);
			} catch (IllegalStateException quiet) {
				if (cursor.find(matcher) == null) return;
			}

			throw new IllegalStateException("Player saw a message in chat it must not see: "
					+ Objects.requireNonNull(cursor.find(matcher)));
		}

		@Override
		public @NotNull Expectation expectReceived(@NotNull Predicate<String> matcher) {
			return Expectation.of("see a matching message in chat", () -> cursor.find(matcher) != null);
		}

		@Override
		public @NotNull List<String> unread() {
			return cursor.unread();
		}

		private HistoryCursor<DroppedChat> droppedCursor() {
			return new HistoryCursor<>(() -> List.copyOf(dropped));
		}
	}
}

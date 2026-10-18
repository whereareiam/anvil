package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Follows the messages a player's chat shows from the moment the watch was created. Each wait continues after the
 * message the previous one found, so a text that repeats, such as a prompt shown again after a wrong answer, is matched
 * once per wait and never by an occurrence from before the watch. A wait that fails names the matching messages the
 * chat left out since the previous wait, such as one that arrived while the client had no player.
 *
 * <pre>{@code
 * MessageWatch chat = messages.watch();
 * messages.command("login secret");
 * chat.received("Welcome back");
 * chat.neverReceived("Invalid password", Duration.ofSeconds(3));
 * }</pre>
 */
public interface MessageWatch {
	/**
	 * Waits, using the default timeout, for the next message containing the supplied text.
	 *
	 * @param text required message content
	 * @return the matching message
	 */
	default @NotNull String received(@NotNull String text) {
		return received(text, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the next message containing the supplied text.
	 *
	 * @param text required message content
	 * @param timeout maximum wait
	 * @return the matching message
	 * @throws IllegalStateException when no such message arrives in time
	 */
	default @NotNull String received(@NotNull String text, @NotNull Duration timeout) {
		return received(message -> message.contains(text), timeout);
	}

	/**
	 * Waits for the next message that satisfies a condition. The following wait looks only at later messages.
	 *
	 * @param matcher condition on the message's plain text
	 * @param timeout maximum wait
	 * @return the matching message
	 * @throws IllegalStateException when no such message arrives in time
	 */
	@NotNull String received(@NotNull Predicate<String> matcher, @NotNull Duration timeout);

	/**
	 * Expects that no message containing the supplied text arrives for a duration.
	 *
	 * @param text forbidden message content
	 * @param duration how long no such message may arrive
	 * @throws IllegalStateException as soon as such a message has arrived since the previous wait
	 */
	default void neverReceived(@NotNull String text, @NotNull Duration duration) {
		neverReceived(message -> message.contains(text), duration);
	}

	/**
	 * Expects that no message satisfying a condition arrives for a duration. It fails as soon as such a message has
	 * arrived since the previous wait, naming it, and returns normally once the duration has passed without one. It
	 * does not move the watch.
	 *
	 * @param matcher condition on the message's plain text
	 * @param duration how long no matching message may arrive
	 * @throws IllegalStateException when such a message arrives
	 */
	void neverReceived(@NotNull Predicate<String> matcher, @NotNull Duration duration);

	/**
	 * Expects the next message that satisfies a condition, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #received(Predicate, Duration)} with the same condition returns the message at once.
	 *
	 * <pre>{@code
	 * MessageWatch chat = messages.watch();
	 * messages.command("confirm 123456");
	 * int outcome = player.awaitFirst(chat.expectReceived(text -> text.contains("Welcome")), session.expectKicked());
	 * }</pre>
	 *
	 * @param matcher condition on the message's plain text
	 * @return expectation met once such a message has arrived since the previous wait
	 */
	@NotNull Expectation expectReceived(@NotNull Predicate<String> matcher);

	/**
	 * Returns the messages received since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread messages, oldest first
	 */
	@NotNull List<String> unread();
}

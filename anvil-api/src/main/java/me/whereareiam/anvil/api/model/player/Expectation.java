package me.whereareiam.anvil.api.model.player;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

/**
 * Something a test expects to happen to a simulated player, such as a message arriving or the server kicking the
 * player, checked without waiting. Capabilities create expectations for what they observe; a player waits for the
 * first of several with {@link me.whereareiam.anvil.api.player.SimulatedPlayer#awaitFirst(java.time.Duration,
 * Expectation...)} when more than one outcome is correct and the test learns afterwards which one happened.
 *
 * <pre>{@code
 * Messages messages = player.capability(Messages.class);
 * Session session = player.capability(Session.class);
 * int first = player.awaitFirst(Duration.ofSeconds(10),
 *         messages.expectReceived(text -> text.contains("Invalid code"), before),
 *         session.expectKicked());
 * }</pre>
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Expectation {
	/**
	 * What is expected, completing the sentence "the player did not ...", as in "be kicked".
	 */
	private final @NotNull String description;
	@Getter(AccessLevel.NONE)
	private final @NotNull BooleanSupplier condition;

	/**
	 * Creates an expectation from a check that never waits.
	 *
	 * @param description what is expected, completing the sentence "the player did not ..."
	 * @param condition returns {@code true} once the expected thing has happened; called repeatedly while waiting
	 * @return expectation
	 */
	public static @NotNull Expectation of(@NotNull String description, @NotNull BooleanSupplier condition) {
		return new Expectation(description, condition);
	}

	/**
	 * Returns whether the expected thing has happened, without waiting.
	 *
	 * @return {@code true} once it has happened
	 */
	public boolean met() {
		return condition.getAsBoolean();
	}
}

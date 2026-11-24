package me.whereareiam.anvil.api.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Something a test waits for: what it is, how to look at the state it depends on, and when that state satisfies it.
 * A wait looks again after every step it paces itself by, such as each tick of a server, and returns the state it saw
 * once the condition holds. When it gives up, it names the condition and the state it saw last, so a failed wait says
 * how far the test got.
 *
 * <pre>{@code
 * Condition<Integer> grown = Condition.of("the wheat at 4 64 4 is fully grown",
 *         () -> age(messages, "4 64 4"), age -> age == 7);
 * int age = ticks.sprintUntil(grown, 24_000, Duration.ofSeconds(60));
 * }</pre>
 *
 * @param <T> the state the condition looks at
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Condition<T> {
	/**
	 * What is awaited, completing the sentence "waiting until ...", as in "the chunk at 4 0 is unloaded".
	 */
	private final @NotNull String description;
	@Getter(AccessLevel.NONE)
	private final @NotNull Supplier<T> observation;
	@Getter(AccessLevel.NONE)
	private final @NotNull Predicate<? super T> test;

	/**
	 * Creates a condition on a state that a wait looks at and reports when it gives up.
	 *
	 * @param description what is awaited, completing the sentence "waiting until ..."
	 * @param observation looks at the current state; called once on every check, and may itself fail the wait
	 * @param test returns {@code true} once the state satisfies the condition
	 * @param <T> the state's type
	 * @return condition
	 */
	public static <T> @NotNull Condition<T> of(
			@NotNull String description,
			@NotNull Supplier<T> observation,
			@NotNull Predicate<? super T> test
	) {
		return new Condition<>(description, observation, test);
	}

	/**
	 * Creates a condition from a check without a state of its own to report; a wait that gives up reports
	 * {@code false} as the state it saw last.
	 *
	 * @param description what is awaited, completing the sentence "waiting until ..."
	 * @param holds returns {@code true} once the condition holds; called once on every check
	 * @return condition
	 */
	public static @NotNull Condition<Boolean> of(@NotNull String description, @NotNull BooleanSupplier holds) {
		return new Condition<>(description, holds::getAsBoolean, Boolean::booleanValue);
	}

	/**
	 * Looks at the current state once.
	 *
	 * @return the state, which may be {@code null} when the observation reports nothing
	 */
	public @Nullable T observe() {
		return observation.get();
	}

	/**
	 * Tells whether a state satisfies the condition.
	 *
	 * @param state a state {@link #observe()} returned
	 * @return {@code true} when the condition holds for it
	 */
	public boolean holds(@Nullable T state) {
		return test.test(state);
	}
}

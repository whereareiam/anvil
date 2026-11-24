package me.whereareiam.anvil.capability.ticks;

import me.whereareiam.anvil.capability.ticks.type.TickPace;
import org.jetbrains.annotations.NotNull;

/**
 * A pace a server runs at until it is closed, which {@link Ticks#pace(TickPace)} sets. Closing it gives the server
 * back the pace it ran at before, so paces nest: close them in the reverse order they were set, as
 * try-with-resources does.
 *
 * <pre>{@code
 * try (Pacing fast = ticks.pace(TickPace.fastest())) {
 *     ticks.await(1_200, Duration.ofSeconds(30));
 * }
 * }</pre>
 */
public interface Pacing extends AutoCloseable {
	/**
	 * Returns the pace this sets.
	 *
	 * @return the pace
	 */
	@NotNull TickPace pace();

	/**
	 * Gives the server back the pace it ran at before this was set. Closing it again has no effect.
	 *
	 * @throws IllegalStateException when a pace set after this one is still open
	 */
	@Override
	void close();
}

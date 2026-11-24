package me.whereareiam.anvil.capability.ticks.type;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * How fast a server runs its ticks, as vanilla's {@code /tick} command sets it: at the normal 20 ticks a second, at a
 * rate of its own, or as fast as it can, as {@code /tick sprint} does.
 *
 * <pre>{@code
 * try (Pacing fast = ticks.pace(TickPace.fastest())) {
 *     ticks.until(grown, Duration.ofSeconds(60));
 * }
 * }</pre>
 */
public sealed interface TickPace permits TickPace.Normal, TickPace.Fastest, TickPace.Rate {
	/**
	 * The ticks a second a server runs at its normal pace.
	 */
	float NORMAL_RATE = 20;
	/**
	 * The lowest rate a server runs at, in ticks a second, as {@code /tick rate} accepts it.
	 */
	float MINIMUM_RATE = 1;
	/**
	 * The highest rate a server runs at, in ticks a second, as {@code /tick rate} accepts it.
	 */
	float MAXIMUM_RATE = 10_000;

	/**
	 * Returns the normal pace: 20 ticks a second.
	 *
	 * @return the pace
	 */
	static @NotNull TickPace normal() {
		return Normal.INSTANCE;
	}

	/**
	 * Returns the fastest pace: the server runs each tick as soon as the previous one is done, as {@code /tick sprint}
	 * does, for as long as the pace lasts.
	 *
	 * @return the pace
	 */
	static @NotNull TickPace fastest() {
		return Fastest.INSTANCE;
	}

	/**
	 * Returns a pace of a number of ticks a second, as {@code /tick rate} sets it.
	 *
	 * @param ticksPerSecond the rate, from {@value #MINIMUM_RATE} to {@value #MAXIMUM_RATE}
	 * @return the pace
	 * @throws IllegalArgumentException when the rate is outside that range
	 */
	static @NotNull TickPace rate(float ticksPerSecond) {
		if (!(ticksPerSecond >= MINIMUM_RATE && ticksPerSecond <= MAXIMUM_RATE))
			throw new IllegalArgumentException("A server runs from " + MINIMUM_RATE + " to " + MAXIMUM_RATE
					+ " ticks a second, not " + ticksPerSecond);

		return new Rate(ticksPerSecond);
	}

	/**
	 * The normal pace of 20 ticks a second.
	 */
	@ToString
	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	final class Normal implements TickPace {
		private static final Normal INSTANCE = new Normal();
	}

	/**
	 * The fastest pace, at which the server runs each tick as soon as the previous one is done.
	 */
	@ToString
	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	final class Fastest implements TickPace {
		private static final Fastest INSTANCE = new Fastest();
	}

	/**
	 * A pace of a number of ticks a second.
	 */
	@Getter
	@ToString
	@EqualsAndHashCode
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	final class Rate implements TickPace {
		/**
		 * The ticks a second the server runs.
		 */
		private final float ticksPerSecond;
	}
}

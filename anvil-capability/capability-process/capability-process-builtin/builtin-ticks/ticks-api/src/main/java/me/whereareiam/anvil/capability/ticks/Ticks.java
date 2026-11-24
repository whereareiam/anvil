package me.whereareiam.anvil.capability.ticks;

import me.whereareiam.anvil.api.model.Condition;
import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.ticks.type.TickPace;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * The ticks one server runs: how many it has run, waiting for it to run some or until a condition holds, and how fast
 * it runs them. A test that waits for something the server does over time, such as crops growing, water spreading
 * or chunks unloading, waits in the server's ticks instead of in wall-clock time, so it waits exactly as long as the
 * server needs however fast the machine is. How fast the server runs is a separate {@link #pace(TickPace) pace},
 * which the test sets for as long as it needs it: at the fastest pace, as with vanilla's {@code /tick sprint},
 * minutes of game time pass in seconds, whatever the test waits for or does meanwhile.
 *
 * <p>A wait checks its condition at once and again after each tick the server completes; at the fastest pace the
 * server completes ticks faster than a check takes, so a check then follows each answer the server gives instead of
 * each tick. A wait that gives up throws {@link IllegalStateException} naming what it waited for, how long it waited,
 * how many ticks the server ran meanwhile, and the state the condition saw last. A server that restarts while a wait
 * runs counts on from the first tick of its new process.</p>
 *
 * <p>Ticks works through Minecraft's tick rate manager on NeoForge, for every release Anvil supports. A proxy runs no
 * ticks, so it has no {@code Ticks}.</p>
 *
 * <pre>{@code
 * Ticks ticks = context.processes().server("server").capability(Ticks.class);
 * console.execute("setblock 4 64 4 minecraft:wheat");
 * try (Pacing fast = ticks.pace(TickPace.fastest())) {
 *     ticks.until(Condition.of("the wheat at 4 64 4 is fully grown",
 *             () -> blockAt(messages, "4 64 4"), block -> block.contains("age=7")), Duration.ofSeconds(60));
 * }
 * }</pre>
 */
public interface Ticks extends ProcessCapability {
	/**
	 * Returns how many ticks the server's current process has run, without waiting.
	 *
	 * @return the ticks the server has completed since its process started
	 */
	long tick();

	/**
	 * Waits until the server has run a number of further ticks, at whatever pace it runs.
	 *
	 * @param ticks positive number of ticks to wait for
	 * @param timeout maximum wall-clock wait
	 * @throws IllegalArgumentException when the ticks or the timeout are not positive
	 * @throws IllegalStateException when the server has not run them within the timeout, naming how many it ran
	 */
	void await(int ticks, @NotNull Duration timeout);

	/**
	 * Waits until a condition holds, checking it now and after each tick the server completes.
	 *
	 * <pre>{@code
	 * ticks.until(Condition.of("Alice stands at x 64", () -> movement.position()
	 *         .map(Position::getX).orElse(Double.NaN), x -> x >= 64), Duration.ofSeconds(10));
	 * }</pre>
	 *
	 * @param condition what to wait for
	 * @param timeout maximum wall-clock wait
	 * @param <T> the state the condition looks at
	 * @return the state the condition held for
	 * @throws IllegalArgumentException when the timeout is not positive
	 * @throws IllegalStateException when the condition did not hold within the timeout, naming it, the time and ticks
	 *         waited and the state seen last
	 */
	<T> T until(@NotNull Condition<T> condition, @NotNull Duration timeout);

	/**
	 * Has the server run at a pace until the returned {@link Pacing} is closed, which gives it back the pace it ran
	 * at before. Paces nest. A pace still open when the scenario finishes is closed then, so a server kept for later
	 * scenarios runs at its own pace again, and a server that restarts runs at the open pace again once it is ready.
	 *
	 * <p>Players stay connected at every pace; at the fastest one their clients see the world change in bursts, as
	 * with {@code /tick sprint}.</p>
	 *
	 * @param pace how fast the server runs
	 * @return the open pace, to close once the test no longer needs it
	 */
	@NotNull Pacing pace(@NotNull TickPace pace);
}

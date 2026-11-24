package me.whereareiam.anvil.capability.ticks;

import me.whereareiam.anvil.api.model.Condition;
import me.whereareiam.anvil.api.process.ProcessCapability;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * The ticks one server runs: how many it has run, waiting for it to run some or until a condition holds, and having it
 * run them as fast as it can, as vanilla's {@code /tick sprint} does. A test that waits for something the server does
 * over time, such as crops growing, water spreading or chunks unloading, waits in the server's ticks instead of in
 * wall-clock time, so it waits exactly as long as the server needs however fast the machine is, and a sprint makes
 * minutes of game time pass in seconds.
 *
 * <p>Every wait checks its condition once before the server's next tick and again after each tick the server
 * completes; while the server sprints it completes ticks faster than a check takes, so a check then follows each
 * answer the server gives instead of each tick. A wait that gives up throws {@link IllegalStateException} naming what
 * it waited for, how long it waited, how many ticks the server ran meanwhile, and the state the condition saw last.
 * A server that restarts while a wait runs counts on from the first tick of its new process.</p>
 *
 * <p>Ticks works through Minecraft's tick rate manager on NeoForge, for every release Anvil supports. A proxy runs no
 * ticks, so it has no {@code Ticks}.</p>
 *
 * <pre>{@code
 * Ticks ticks = context.processes().server("server").capability(Ticks.class);
 * console.execute("setblock 4 64 4 minecraft:wheat");
 * ticks.sprintUntil(Condition.of("the wheat at 4 64 4 is fully grown",
 *         () -> blockAt(messages, "4 64 4"), block -> block.contains("age=7")), 24_000, Duration.ofSeconds(60));
 * ticks.sprint(1_200, Duration.ofSeconds(30));
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
	 * Waits until the server has run a number of further ticks at its own pace.
	 *
	 * @param ticks positive number of ticks to wait for
	 * @param timeout maximum wall-clock wait
	 * @throws IllegalArgumentException when the ticks or the timeout are not positive
	 * @throws IllegalStateException when the server has not run them within the timeout, naming how many it ran
	 */
	void await(int ticks, @NotNull Duration timeout);

	/**
	 * Has the server run a number of ticks as fast as it can and waits until it has run them. A sprint already running
	 * is replaced by this one.
	 *
	 * @param ticks positive number of ticks to sprint
	 * @param timeout maximum wall-clock wait
	 * @throws IllegalArgumentException when the ticks or the timeout are not positive
	 * @throws IllegalStateException when the server has not run them within the timeout, naming how many it ran; the
	 *         sprint is stopped then
	 */
	void sprint(int ticks, @NotNull Duration timeout);

	/**
	 * Waits until a condition holds, checking it now and after each tick the server runs at its own pace.
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
	 * Has the server sprint for at most a number of ticks until a condition holds, checking it now and after each
	 * answer the sprinting server gives, and stops the sprint once it holds. The server runs a few ticks more while
	 * the last check is made and the sprint is stopped, so a condition must be one that stays true once it holds.
	 *
	 * @param condition what to wait for
	 * @param ticks positive number of ticks the server may sprint at most
	 * @param timeout maximum wall-clock wait
	 * @param <T> the state the condition looks at
	 * @return the state the condition held for
	 * @throws IllegalArgumentException when the ticks or the timeout are not positive
	 * @throws IllegalStateException when the condition did not hold within those ticks or the timeout, naming it, the
	 *         time and ticks waited and the state seen last; the sprint is stopped then
	 */
	<T> T sprintUntil(@NotNull Condition<T> condition, int ticks, @NotNull Duration timeout);
}

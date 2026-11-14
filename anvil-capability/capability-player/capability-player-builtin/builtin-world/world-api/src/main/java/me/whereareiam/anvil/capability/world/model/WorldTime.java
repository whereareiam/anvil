package me.whereareiam.anvil.capability.world.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.OptionalLong;

/**
 * The time the client was told last: the age of the world and the time of day of the player's level. Releases before
 * Minecraft 26.1 send the time of day of the player's level. From 26.1 the server sends its world clocks, and the time
 * of day is the time of the clock the level's dimension type follows; a dimension type without a clock, such as the
 * nether's, has no time of day.
 */
@Value
@Builder(toBuilder = true)
public class WorldTime {
	/**
	 * The number of ticks in a Minecraft day.
	 */
	public static final long DAY_TICKS = 24000;

	/**
	 * The age of the world in ticks, which keeps advancing whatever the time of day does.
	 */
	long gameTime;
	/**
	 * The time of day of the player's level in ticks since the world began, so that {@code 6000} is noon of the first
	 * day and {@code 30000} noon of the second, or {@code null} when the level has no time of day.
	 */
	@Nullable Long dayTime;
	/**
	 * Whether the time of day advances: {@code false} while the server's rule that advances it is off, or the level's
	 * clock is paused or has no time of day.
	 */
	boolean advancing;
	/**
	 * The world clocks the server keeps, by their keys, such as {@code minecraft:overworld}; empty before Minecraft
	 * 26.1, which has none.
	 */
	@NotNull @Singular Map<String, ClockInfo> clocks;

	/**
	 * Returns the time within the current day, from {@code 0} at sunrise to {@code 23999}, as the game's
	 * {@code /time query daytime} reports it.
	 *
	 * <pre>{@code
	 * assertEquals(OptionalLong.of(6000), world.time().orElseThrow().timeOfDay());
	 * }</pre>
	 *
	 * @return the time within the day, or empty when the level has no time of day
	 */
	public @NotNull OptionalLong timeOfDay() {
		return dayTime == null ? OptionalLong.empty() : OptionalLong.of(Math.floorMod(dayTime, DAY_TICKS));
	}
}

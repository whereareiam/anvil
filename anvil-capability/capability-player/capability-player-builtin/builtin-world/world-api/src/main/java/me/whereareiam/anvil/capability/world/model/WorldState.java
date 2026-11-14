package me.whereareiam.anvil.capability.world.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.capability.world.type.WeatherType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the client was told last about the world around its level: the time, the weather, the world border and the
 * spawn point. Each is {@code null} until the server tells it, and all are while the player is not connected.
 */
@Value
@Builder(toBuilder = true)
public class WorldState {
	/**
	 * The state of a player that is not in a level.
	 */
	public static final @NotNull WorldState EMPTY = WorldState.builder().build();

	/**
	 * The time, or {@code null} until the server sent it.
	 */
	@Nullable WorldTime time;
	/**
	 * The weather of the player's level, clear once the player is in a level, or {@code null} while it is in none.
	 */
	@Nullable Weather weather;
	/**
	 * The world border of the player's level, or {@code null} until the server sent it for the level.
	 */
	@Nullable WorldBorder border;
	/**
	 * The world's spawn point, or {@code null} until the server sent it.
	 */
	@Nullable SpawnPoint spawn;

	/**
	 * Tells the weather the client shows, for conditions on the state.
	 *
	 * <pre>{@code
	 * world.states().until(state -> state.weatherType() == WeatherType.RAIN);
	 * }</pre>
	 *
	 * @return the weather's type, or {@code null} while the player is in no level
	 */
	public @Nullable WeatherType weatherType() {
		return weather == null ? null : weather.type();
	}
}

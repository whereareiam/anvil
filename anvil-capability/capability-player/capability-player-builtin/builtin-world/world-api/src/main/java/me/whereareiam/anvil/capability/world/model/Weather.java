package me.whereareiam.anvil.capability.world.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.capability.world.type.WeatherType;
import org.jetbrains.annotations.NotNull;

/**
 * The weather of the player's level as the client knows it: a rain level and a thunder level, which the server
 * changes gradually, so rain and thunderstorms fade in and out. A level the player enters is clear until the server
 * says otherwise. The client keeps no other weather: when the server says rain starts or stops, it only sets the rain
 * level to where the following changes begin, {@code 0} and {@code 1}, and Paper servers before Minecraft
 * 26.2 send the two the other way round.
 */
@Value
@Builder(toBuilder = true)
public class Weather {
	/**
	 * The weather of a level the server sent no weather for.
	 */
	public static final @NotNull Weather CLEAR = Weather.builder().build();

	private static final float RAIN_THRESHOLD = 0.2F;
	private static final float THUNDER_THRESHOLD = 0.9F;

	/**
	 * How hard it rains, from {@code 0} for no rain to {@code 1} for full rain.
	 */
	float rainLevel;
	/**
	 * How strong the thunderstorm is, from {@code 0} for none to {@code 1} for a full thunderstorm.
	 */
	float thunderLevel;

	/**
	 * Tells the weather the client shows, as the game judges it: a thunderstorm while the rain level is above
	 * {@code 0.2} and the thunder level above {@code 0.9}, rain while only the rain level is, and clear weather
	 * otherwise.
	 *
	 * <pre>{@code
	 * world.states().until(state -> state.weatherType() == WeatherType.THUNDER);
	 * }</pre>
	 *
	 * @return the weather the client shows
	 */
	public @NotNull WeatherType type() {
		if (rainLevel <= RAIN_THRESHOLD) return WeatherType.CLEAR;

		return thunderLevel > THUNDER_THRESHOLD ? WeatherType.THUNDER : WeatherType.RAIN;
	}
}

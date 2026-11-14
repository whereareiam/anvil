package me.whereareiam.anvil.capability.world;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.world.model.WorldState;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Waits for what the client knows about its world: the time, the weather, the world border and the spawn point. Each
 * condition is checked against the state now and against every state the client is told afterwards.
 *
 * <pre>{@code
 * WorldStateWatch states = world.states();
 * lobby.console().command("weather rain");
 * states.until(state -> state.weatherType() == WeatherType.RAIN);
 * states.never(state -> state.weatherType() == WeatherType.THUNDER, Duration.ofSeconds(2));
 * }</pre>
 */
public interface WorldStateWatch {
	/**
	 * Returns the state now, without waiting.
	 *
	 * @return the state, {@link WorldState#EMPTY} while the player is not in a level
	 */
	@NotNull WorldState current();

	/**
	 * Waits, using the default timeout, until the state satisfies a condition.
	 *
	 * @param condition condition on the state
	 * @return the first state that satisfies it
	 */
	default @NotNull WorldState until(@NotNull Predicate<WorldState> condition) {
		return until(condition, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until the state satisfies a condition: returns the state now when it does, otherwise the first later state
	 * that does.
	 *
	 * @param condition condition on the state
	 * @param timeout maximum wait
	 * @return the first state that satisfies it
	 * @throws IllegalStateException when no state satisfies it in time
	 */
	@NotNull WorldState until(@NotNull Predicate<WorldState> condition, @NotNull Duration timeout);

	/**
	 * Expects that no state satisfies a condition for a duration: neither the state now nor any state the client is
	 * told meanwhile. It fails as soon as one does, naming it, and returns normally once the duration has passed.
	 *
	 * @param condition condition on the state
	 * @param duration how long no state may satisfy it
	 * @throws IllegalStateException when a state satisfies it
	 */
	void never(@NotNull Predicate<WorldState> condition, @NotNull Duration duration);

	/**
	 * Creates an expectation that the state satisfies a condition, to wait for together with other expectations of
	 * the player. It is met by the state at the time it is checked.
	 *
	 * @param condition condition on the state
	 * @return the expectation
	 */
	@NotNull Expectation expect(@NotNull Predicate<WorldState> condition);
}

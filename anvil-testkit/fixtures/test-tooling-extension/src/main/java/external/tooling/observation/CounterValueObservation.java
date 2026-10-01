package external.tooling.observation;

import external.tooling.Counter;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationValue;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.player.PlayerCapabilityObservation;
import org.jetbrains.annotations.NotNull;

/**
 * Reads the current value from the supplied external capability.
 */
public final class CounterValueObservation extends PlayerCapabilityObservation<Counter> {
	/**
	 * Declares the contribution without acquiring runtime resources.
	 */
	public CounterValueObservation() {
		super(Counter.class, ObservationDefinition.builder().id("external.counter.value").displayName("Counter").build());
	}

	@Override
	public @NotNull ObservationValue observe(
			@NotNull SimulatedPlayer player,
			@NotNull Counter counter
	) {
		return ObservationValue.builder().text(Long.toString(counter.value())).build();
	}
}

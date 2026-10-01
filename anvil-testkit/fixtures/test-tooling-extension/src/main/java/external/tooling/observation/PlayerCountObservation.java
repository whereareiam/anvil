package external.tooling.observation;

import me.whereareiam.anvil.api.scenario.ScenarioAccess;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationValue;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.ScenarioObservation;
import org.jetbrains.annotations.NotNull;

/**
 * Reports the current number of simulated players in the scenario.
 */
public final class PlayerCountObservation extends ScenarioObservation {
	/**
	 * Declares the contribution without acquiring runtime resources.
	 */
	public PlayerCountObservation() {
		super(ObservationDefinition.builder().id("external.scenario.players").displayName("Players").build());
	}

	@Override
	public @NotNull ObservationValue observe(
			@NotNull ScenarioAccess scenario
	) {
		return ObservationValue.builder().text(Integer.toString(scenario.players().all().size())).build();
	}
}

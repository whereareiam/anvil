package me.whereareiam.anvil.tooling.extension.api.observation.scoped;

import me.whereareiam.anvil.api.scenario.ScenarioAccess;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.extension.api.observation.ToolingObservation;
import org.jetbrains.annotations.NotNull;

/**
 * An observation evaluated against a current scenario target.
 */
public abstract class ScenarioObservation extends ToolingObservation<ScenarioAccess> {
	/**
	 * Declares this scenario contribution.
	 *
	 * @param definition portable identity and presentation
	 */
	protected ScenarioObservation(@NotNull ObservationDefinition definition) {
		super(definition);
	}
}

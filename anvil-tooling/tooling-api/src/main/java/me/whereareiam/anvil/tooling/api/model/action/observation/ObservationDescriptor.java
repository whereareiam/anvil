package me.whereareiam.anvil.tooling.api.model.action.observation;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import org.jetbrains.annotations.NotNull;

/**
 * Contributed observation bound to a current runtime target.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ObservationDescriptor {
	/**
	 * Observation identity and label.
	 */
	@NotNull
	ObservationDefinition definition;

	/**
	 * Target described by the observation.
	 */
	@NotNull
	ActionTarget target;

	/**
	 * Current value and presentation severity.
	 */
	@NotNull
	ObservationValue value;

}

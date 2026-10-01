package me.whereareiam.anvil.tooling.api.model.action.binding;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * One registered action bound to a current runtime target.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ActionDescriptor {
	/**
	 * Declared identity and input schema.
	 */
	@NotNull
	ActionDefinition definition;

	/**
	 * Target that exposes this action.
	 */
	@NotNull
	ActionTarget target;

	/**
	 * Current availability for this target.
	 */
	@NotNull
	ActionAvailability availability;
}

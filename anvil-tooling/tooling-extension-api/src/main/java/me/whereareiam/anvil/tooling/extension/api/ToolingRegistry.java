package me.whereareiam.anvil.tooling.extension.api;

import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionDescriptor;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionRequest;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Resolves registered tooling contributions against a borrowed scenario context.
 * Implementations own contribution validation and current-target availability checks. They must
 * not close the context or retain its process/player handles beyond the call. The session serializes
 * calls to its registry and validates request session identities before invocation.
 *
 * <p>Extensions declare contributions through {@link ToolingRegistration}; this runtime boundary
 * deliberately has no mutable registration phase.</p>
 */
public interface ToolingRegistry {
	/**
	 * Describes the actions supported by the scenario and its current targets.
	 *
	 * @param context borrowed active scenario context
	 * @return portable action descriptors with current availability
	 */
	@NotNull List<ActionDescriptor> actions(@NotNull ScenarioContext context);

	/**
	 * Evaluates observations supported by the scenario and its current targets.
	 *
	 * @param context borrowed active scenario context
	 * @return portable observation descriptors and values
	 */
	@NotNull List<ObservationDescriptor> observations(@NotNull ScenarioContext context);

	/**
	 * Resolves the current target, checks availability and arguments, then invokes the action.
	 *
	 * @param context borrowed active scenario context
	 * @param request request whose session identity has already been checked by the caller
	 * @return validated portable action result
	 * @throws java.util.NoSuchElementException if the action or target no longer exists
	 * @throws IllegalArgumentException if the target type or arguments are invalid
	 * @throws IllegalStateException if the action is unavailable
	 */
	@NotNull ActionResult invoke(@NotNull ScenarioContext context, @NotNull ActionRequest request);
}

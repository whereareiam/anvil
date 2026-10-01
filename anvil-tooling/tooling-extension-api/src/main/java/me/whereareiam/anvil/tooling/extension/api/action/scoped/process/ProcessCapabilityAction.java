package me.whereareiam.anvil.tooling.extension.api.action.scoped.process;

import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionAvailability;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.extension.api.model.ToolingArguments;
import org.jetbrains.annotations.NotNull;

/**
 * A process action requiring a typed capability.
 * The runner filters unsupported targets and checks lifecycle readiness before invoking it.
 * Each invocation resolves the current capability; instances are not retained across calls.
 *
 * @param <C> required process capability type
 */
public abstract class ProcessCapabilityAction<C extends ProcessCapability> extends ProcessAction {
	private final @NotNull Class<C> capabilityType;

	/**
	 * Declares the required capability and portable definition.
	 *
	 * @param capabilityType required capability type
	 * @param definition portable identity and presentation
	 */
	protected ProcessCapabilityAction(
			@NotNull Class<C> capabilityType,
			@NotNull ActionDefinition definition
	) {
		super(definition);
		this.capabilityType = capabilityType;
	}

	/**
	 * Restricts this contribution to processes exposing the required capability.
	 *
	 * @param target current borrowed process
	 * @return whether the required capability is available
	 */
	@Override
	public final boolean supports(@NotNull RunningProcess target) {
		return target.hasCapability(capabilityType);
	}

	/**
	 * Supplies the current capability to the typed availability check.
	 *
	 * @param target current borrowed process
	 * @return availability and an optional explanation
	 */
	@Override
	public final @NotNull ActionAvailability availability(@NotNull RunningProcess target) {
		return availability(target, target.capability(capabilityType));
	}

	/**
	 * Checks action-specific requirements using the current capability.
	 *
	 * @param target current borrowed process
	 * @param capability current borrowed capability
	 * @return availability and an optional explanation
	 */
	public @NotNull ActionAvailability availability(@NotNull RunningProcess target, @NotNull C capability) {
		return super.availability(target);
	}

	/**
	 * Supplies the current capability to the typed operation.
	 *
	 * @param target current borrowed process
	 * @param arguments validated action inputs
	 * @return portable action outcome
	 */
	@Override
	public final @NotNull ActionResult execute(@NotNull RunningProcess target, @NotNull ToolingArguments arguments) {
		return execute(target, target.capability(capabilityType), arguments);
	}

	/**
	 * Executes using the supplied target and capability without repeating capability lookup.
	 *
	 * @param target current borrowed process
	 * @param capability current borrowed capability
	 * @param arguments validated action inputs
	 * @return portable action outcome
	 */
	public abstract @NotNull ActionResult execute(
			@NotNull RunningProcess target,
			@NotNull C capability,
			@NotNull ToolingArguments arguments
	);
}

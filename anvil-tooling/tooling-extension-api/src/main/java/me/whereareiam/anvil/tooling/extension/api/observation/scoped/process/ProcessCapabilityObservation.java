package me.whereareiam.anvil.tooling.extension.api.observation.scoped.process;

import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationValue;
import org.jetbrains.annotations.NotNull;

/**
 * A process observation requiring a typed capability.
 * The runner filters unsupported targets and checks lifecycle readiness before invoking it.
 * Each invocation resolves the current capability; instances are not retained across calls.
 *
 * @param <C> required process capability type
 */
public abstract class ProcessCapabilityObservation<C extends ProcessCapability> extends ProcessObservation {
	private final @NotNull Class<C> capabilityType;

	/**
	 * Declares the required capability and portable definition.
	 *
	 * @param capabilityType required capability type
	 * @param definition portable identity and presentation
	 */
	protected ProcessCapabilityObservation(
			@NotNull Class<C> capabilityType,
			@NotNull ObservationDefinition definition
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
	 * Supplies the current capability to the typed observation.
	 *
	 * @param target current borrowed process
	 * @return portable observation value
	 */
	@Override
	public final @NotNull ObservationValue observe(@NotNull RunningProcess target) {
		return observe(target, target.capability(capabilityType));
	}

	/**
	 * Reads a value using the supplied target and capability without repeating capability lookup.
	 *
	 * @param target current borrowed process
	 * @param capability current borrowed capability
	 * @return portable observation value
	 */
	public abstract @NotNull ObservationValue observe(@NotNull RunningProcess target, @NotNull C capability);
}

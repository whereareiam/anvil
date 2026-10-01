package me.whereareiam.anvil.tooling.extension.api.observation.scoped.player;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationValue;
import org.jetbrains.annotations.NotNull;

/**
 * A player observation requiring a typed capability.
 * The runner filters unsupported targets and checks lifecycle readiness before invoking it.
 * Each invocation resolves the current capability; instances are not retained across calls.
 *
 * @param <C> required player capability type
 */
public abstract class PlayerCapabilityObservation<C extends PlayerCapability> extends PlayerObservation {
	private final @NotNull Class<C> capabilityType;

	/**
	 * Declares the required capability and portable definition.
	 *
	 * @param capabilityType required capability type
	 * @param definition portable identity and presentation
	 */
	protected PlayerCapabilityObservation(
			@NotNull Class<C> capabilityType,
			@NotNull ObservationDefinition definition
	) {
		super(definition);
		this.capabilityType = capabilityType;
	}

	/**
	 * Restricts this contribution to players exposing the required capability.
	 *
	 * @param target current borrowed player
	 * @return whether the required capability is available
	 */
	@Override
	public final boolean supports(@NotNull SimulatedPlayer target) {
		return target.hasCapability(capabilityType);
	}

	/**
	 * Supplies the current capability to the typed observation.
	 *
	 * @param target current borrowed player
	 * @return portable observation value
	 */
	@Override
	public final @NotNull ObservationValue observe(@NotNull SimulatedPlayer target) {
		return observe(target, target.capability(capabilityType));
	}

	/**
	 * Reads a value using the supplied target and capability without repeating capability lookup.
	 *
	 * @param target current borrowed player
	 * @param capability current borrowed capability
	 * @return portable observation value
	 */
	public abstract @NotNull ObservationValue observe(@NotNull SimulatedPlayer target, @NotNull C capability);
}

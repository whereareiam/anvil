package me.whereareiam.anvil.tooling.extension.api.observation;

import lombok.Getter;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationValue;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.ScenarioObservation;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.player.PlayerObservation;
import me.whereareiam.anvil.tooling.extension.api.observation.scoped.process.ProcessObservation;
import org.jetbrains.annotations.NotNull;

/**
 * A typed observation contribution with a portable definition and runtime behavior.
 * Extend {@link ScenarioObservation}, {@link ProcessObservation}, or {@link PlayerObservation}
 * to select its scope. Registration rejects contributions outside these scoped specializations.
 * Runtime targets are borrowed for each call and must not be retained by the contribution.
 *
 * @param <T> target supplied by the typed specialization
 */
public abstract class ToolingObservation<T> {
	/**
	 * Portable identity and presentation captured when this contribution is registered.
	 */
	@Getter
	private final @NotNull ObservationDefinition definition;

	/**
	 * Binds the contribution's portable definition without acquiring runtime resources.
	 *
	 * @param definition identity and presentation
	 */
	protected ToolingObservation(@NotNull ObservationDefinition definition) {
		this.definition = definition;
	}

	/**
	 * Reports whether this contribution applies to a target, independently of readiness.
	 * Unsupported targets do not advertise the contribution. This must not mutate the target.
	 *
	 * @param target current borrowed target
	 * @return whether the contribution applies
	 */
	public boolean supports(@NotNull T target) {
		return true;
	}

	/**
	 * Reads a current value without mutating the target. Keep the operation short.
	 * Failures are surfaced as failed observations by the runner.
	 *
	 * @param target current borrowed target
	 * @return portable observation text and presentation severity
	 */
	public abstract @NotNull ObservationValue observe(@NotNull T target);
}

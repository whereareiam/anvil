package me.whereareiam.anvil.tooling.extension.api.action;

import lombok.Getter;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionAvailability;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.ScenarioAction;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.player.PlayerAction;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.process.ProcessAction;
import me.whereareiam.anvil.tooling.extension.api.model.ToolingArguments;
import org.jetbrains.annotations.NotNull;

/**
 * A typed action contribution with a portable definition and runtime behavior.
 * Extend {@link ScenarioAction}, {@link ProcessAction}, or {@link PlayerAction}
 * to select its scope. Registration rejects contributions outside these scoped specializations.
 * Runtime targets are borrowed for each call and must not be retained by the contribution.
 *
 * @param <T> target supplied by the typed specialization
 */
public abstract class ToolingAction<T> {
	/**
	 * Portable identity and presentation captured when this contribution is registered.
	 */
	@Getter
	private final @NotNull ActionDefinition definition;

	/**
	 * Binds the contribution's portable definition without acquiring runtime resources.
	 *
	 * @param definition identity, presentation, and any declared inputs
	 */
	protected ToolingAction(@NotNull ActionDefinition definition) {
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
	 * Checks action-specific readiness after target lifecycle and support checks.
	 * This must not mutate the target and is checked again before execution.
	 *
	 * @param target current borrowed target
	 * @return availability and an optional explanation
	 */
	public @NotNull ActionAvailability availability(@NotNull T target) {
		return ActionAvailability.builder().enabled(true).build();
	}

	/**
	 * Invokes this action using validated inputs with declared defaults applied.
	 *
	 * @param target current borrowed target
	 * @param arguments validated inputs
	 * @return portable action outcome
	 */
	public abstract @NotNull ActionResult execute(@NotNull T target, @NotNull ToolingArguments arguments);
}

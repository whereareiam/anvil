package me.whereareiam.anvil.tooling.extension.api;

import me.whereareiam.anvil.tooling.extension.api.action.ToolingAction;
import me.whereareiam.anvil.tooling.extension.api.observation.ToolingObservation;
import org.jetbrains.annotations.NotNull;

/**
 * Registers typed contributions during one extension's registration call.
 * Definitions and namespaced identifiers are validated centrally. Registration is confined to
 * the installing thread and closes when the extension returns; runtime targets are resolved later.
 */
public interface ToolingRegistration {
	/**
	 * Registers an action whose specialization determines the target and capability requirements.
	 *
	 * @param action contribution owning its definition, availability rules, and operation
	 * @throws IllegalArgumentException when its scope or definition is invalid, or its action ID is already registered
	 * @throws IllegalStateException when registration is closed or called from another thread
	 */
	void action(@NotNull ToolingAction<?> action);

	/**
	 * Registers an observation whose specialization determines the target and capability requirements.
	 *
	 * @param observation contribution owning its definition and read operation
	 * @throws IllegalArgumentException when its scope or ID is invalid, or its observation ID is already registered
	 * @throws IllegalStateException when registration is closed or called from another thread
	 */
	void observation(@NotNull ToolingObservation<?> observation);
}

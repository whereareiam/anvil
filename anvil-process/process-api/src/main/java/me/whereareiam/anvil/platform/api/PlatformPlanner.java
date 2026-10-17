package me.whereareiam.anvil.platform.api;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.platform.api.model.PlatformPlan;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves declared artifacts and validates compatible platform choices before process preparation.
 * Implementations own provider selection and forwarding negotiation.
 */
public interface PlatformPlanner {
	/**
	 * Plans the platforms for a structurally valid scenario without starting processes.
	 *
	 * @param scenario scenario with validated names, routes, and entrypoint
	 * @return resolved declarations and immutable process preparation inputs
	 */
	@NotNull PlatformPlan plan(@NotNull AnvilScenario scenario);
}

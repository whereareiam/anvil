package me.whereareiam.anvil.environment.execution.api;

import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import org.jetbrains.annotations.NotNull;

/**
 * Creates an execution environment for one scenario topology.
 */
public interface ExecutionProvider {
	/**
	 * Returns the identifier used by scenario execution selection.
	 *
	 * @return provider identifier
	 */
	@NotNull String id();

	/**
	 * Acquires a scenario execution environment, owned until all its processes are finalized.
	 *
	 * @param context host resources and runtime validation policy
	 * @return caller-owned execution environment
	 */
	@NotNull ExecutionSession open(@NotNull ExecutionContext context);
}

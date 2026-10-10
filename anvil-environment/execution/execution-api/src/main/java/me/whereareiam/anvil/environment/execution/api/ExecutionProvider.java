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
	 * Returns whether a game connection reaches a process with the client's own source address. A provider that
	 * forwards published ports through its own network, such as Docker, hands the process another address, so a
	 * simulated player cannot choose the address the process sees.
	 *
	 * @return true when processes see the source address a client connected from
	 */
	boolean preservesClientAddress();

	/**
	 * Returns whether a process of one execution environment can connect to a process of another environment
	 * at that process's host listener address. Processes that keep running across scenarios live in an
	 * environment of their own, so each scenario's processes must reach them there.
	 *
	 * @return true when processes of separate environments reach each other's host listener addresses
	 */
	boolean connectsEnvironments();

	/**
	 * Acquires a scenario execution environment, owned until all its processes are finalized.
	 *
	 * @param context host resources and runtime validation policy
	 * @return caller-owned execution environment
	 */
	@NotNull ExecutionSession open(@NotNull ExecutionContext context);
}

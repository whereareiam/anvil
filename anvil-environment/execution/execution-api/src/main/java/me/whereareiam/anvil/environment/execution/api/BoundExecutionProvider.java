package me.whereareiam.anvil.environment.execution.api;

import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An execution provider as one workbench offers it, bound to that workbench's machine services, so its sessions
 * open from the engine's {@link ExecutionContext} alone. Managed execution opens sessions through these, whether
 * the workbench runs in the engine's JVM or on another machine.
 */
public interface BoundExecutionProvider {
	/**
	 * Returns the identifier used by scenario execution selection.
	 *
	 * @return provider identifier
	 */
	@NotNull String id();

	/**
	 * Returns whether processes see the source address a game connection came from.
	 *
	 * @return true when processes see the client's own address
	 * @see ExecutionProvider#preservesClientAddress()
	 */
	boolean preservesClientAddress();

	/**
	 * Returns whether processes of separate environments reach each other's host listener addresses.
	 *
	 * @return true when separate environments connect
	 * @see ExecutionProvider#connectsEnvironments()
	 */
	boolean connectsEnvironments();

	/**
	 * Returns why the provider cannot run processes now.
	 *
	 * @return the reason, or null when it can
	 * @see ExecutionProvider#unavailableReason()
	 */
	@Nullable String unavailableReason();

	/**
	 * Acquires a scenario execution environment, owned until all its processes are finalized.
	 *
	 * @param context the engine's decisions about the scenario's sessions
	 * @return caller-owned execution environment
	 */
	@NotNull ExecutionSession open(@NotNull ExecutionContext context);
}

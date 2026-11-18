package me.whereareiam.anvil.agent.server.api.operation;

import me.whereareiam.anvil.agent.api.model.AgentOperation;
import org.jetbrains.annotations.NotNull;

/**
 * Registration surface supplied to one agent-operation provider during endpoint startup.
 */
public interface AgentOperationRegistry {
	/**
	 * Registers an operation in this provider's namespace. Registration is valid only during
	 * {@link AgentOperationProvider#install}; duplicate names and foreign namespaces are rejected.
	 *
	 * @param operation shared operation contract
	 * @param handler platform implementation
	 * @param <Q> request model
	 * @param <R> response model
	 */
	<Q, R> void register(@NotNull AgentOperation<Q, R> operation, @NotNull AgentOperationHandler<Q, R> handler);
}

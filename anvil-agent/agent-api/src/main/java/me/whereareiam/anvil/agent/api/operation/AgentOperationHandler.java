package me.whereareiam.anvil.agent.api.operation;

import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Executes one typed operation inside a managed platform process.
 *
 * @param <Q> request model
 * @param <R> response model
 */
@FunctionalInterface
public interface AgentOperationHandler<Q, R> {
	/**
	 * Handles an authenticated request. Use {@link PlatformAgent#call} for work that requires
	 * the platform's execution thread, and {@link PlatformAgent#requireService} for native APIs.
	 *
	 * @param platform platform services and scheduling
	 * @param request decoded request
	 * @return result, or {@code null} when the operation has no result
	 * @throws Exception when the operation fails; the failure is returned to its host caller
	 */
	@Nullable R execute(@NotNull PlatformAgent platform, @NotNull Q request) throws Exception;
}

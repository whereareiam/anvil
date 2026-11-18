package me.whereareiam.anvil.agent.server.api.operation;

import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Executes one typed operation inside a managed platform process.
 *
 * @param <Q> request model
 * @param <R> response model
 */
public interface AgentOperationHandler<Q, R> {
	/**
	 * Handles an authenticated request. Use {@link PlatformAgent#call} for work that requires
	 * the platform's execution thread, and {@link PlatformAgent#requireService} for native APIs.
	 *
	 * @param platform platform services and scheduling
	 * @param request decoded request, or {@code null} when the operation declares a {@link Void}
	 *                request; null requests for other schemas are rejected before handler invocation
	 * @return result, or {@code null} when the operation has no result
	 * @throws Exception when the operation fails; the failure is returned to its host caller
	 */
	@Nullable R execute(@NotNull PlatformAgent platform, @Nullable Q request) throws Exception;
}

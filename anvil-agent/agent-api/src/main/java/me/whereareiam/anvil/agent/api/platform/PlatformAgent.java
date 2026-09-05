package me.whereareiam.anvil.agent.api.platform;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.platform.access.PlatformCommandAccess;
import me.whereareiam.anvil.agent.api.platform.access.PlatformPlayerIdentityAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import me.whereareiam.anvil.agent.api.exception.AgentException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Required platform behavior implemented by every in-process Anvil agent.
 *
 * <p>The agent runtime handles authentication and serialization. Platform integrations only
 * implement these domain operations. The inherited interfaces make the required baseline
 * explicit while allowing future operations to be added as focused capabilities.</p>
 */
public interface PlatformAgent extends PlatformPlayerIdentityAccess, PlatformCommandAccess {
	/**
	 * Reports platform information used to verify agent readiness.
	 *
	 * @return current platform information
	 */
	@NotNull AgentInfo info();

	/**
	 * Finds a native API service exposed by this platform integration.
	 *
	 * @param type service interface, such as a server or proxy API
	 * @param <T> service type
	 * @return matching service when available
	 */
	default <T> @NotNull Optional<T> findService(@NotNull Class<T> type) {
		return Optional.empty();
	}

	/**
	 * Resolves a required native platform service.
	 *
	 * @param type service interface
	 * @param <T> service type
	 * @return exposed service
	 * @throws AgentException when the platform does not expose the requested service
	 */
	default <T> @NotNull T requireService(@NotNull Class<T> type) {
		return findService(type).orElseThrow(() -> new AgentException("Platform '" + info().getPlatform()
				+ "' does not expose " + type.getName()));
	}

	/**
	 * Executes work using this platform's threading rules and waits for the result.
	 * Platforms without a dedicated API thread execute it on the request thread.
	 *
	 * @param action platform work
	 * @param <T> result type
	 * @return action result, which may be {@code null}
	 * @throws Exception when scheduling or execution fails
	 */
	default <T> @Nullable T call(@NotNull Supplier<T> action) throws Exception {
		return action.get();
	}
}

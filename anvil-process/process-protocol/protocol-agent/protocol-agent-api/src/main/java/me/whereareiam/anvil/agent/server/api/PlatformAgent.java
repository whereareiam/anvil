package me.whereareiam.anvil.agent.server.api;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Required platform behavior implemented by every in-process Anvil agent.
 *
 * <p>The agent runtime handles authentication and serialization. A platform integration reports what it is, runs
 * console commands, reports players joining and leaving to the {@code PlayerEvents} of its endpoint, exposes its
 * native services, and runs work by the platform's threading rules. Anything else a capability needs from the
 * platform belongs to an agent extension, which reaches the platform through these.</p>
 */
public interface PlatformAgent {
	/**
	 * Reports platform information used to verify agent readiness.
	 *
	 * @return current platform information
	 */
	@NotNull AgentInfo info();

	/**
	 * Executes a command as the platform console.
	 *
	 * @param command command without a leading slash
	 * @return whether the platform accepted the command
	 */
	boolean executeCommand(@NotNull String command);

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

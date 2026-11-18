package me.whereareiam.anvil.agent.server.api.operation;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import org.jetbrains.annotations.NotNull;

import java.util.ServiceLoader;

/**
 * Contributes external platform-agent operations through {@link ServiceLoader}.
 *
 * <p>Install the provider JAR and its dependencies as workspace assets under
 * {@code plugins/anvil-agent-extensions}. Compile against the agent and platform APIs without
 * bundling those APIs. The endpoint owns the extension class loader for the lifetime of the run.</p>
 */
public interface AgentOperationProvider {
	/**
	 * Returns the stable namespace owned by this provider, for example {@code com.example.health}.
	 *
	 * @return globally unique provider ID
	 */
	@NotNull String id();

	/**
	 * Reports whether the provider can install on this platform.
	 *
	 * @param platform platform identity
	 * @return whether installation is supported
	 */
	default boolean supports(@NotNull AgentInfo platform) {
		return true;
	}

	/**
	 * Registers the operations supplied by this provider. It runs once when the endpoint starts, before the
	 * platform's players join, so a provider can also start observing the platform here, for example by
	 * registering a native listener with the plugin or server it reaches through
	 * {@link PlatformAgent#requireService(Class)}.
	 *
	 * <pre>{@code
	 * public void install(PlatformAgent platform, AgentOperationRegistry registry) {
	 *     Server server = platform.requireService(Server.class);
	 *     registry.register(PLAYERS, (agent, request) -> agent.call(() -> server.getOnlinePlayers().size()));
	 * }
	 * }</pre>
	 *
	 * @param platform the platform agent the endpoint runs in
	 * @param registry registration surface restricted to this provider's namespace
	 */
	void install(@NotNull PlatformAgent platform, @NotNull AgentOperationRegistry registry);
}

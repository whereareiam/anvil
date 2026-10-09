package me.whereareiam.anvil.agent.server.api.operation;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
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
	 * Registers the operations supplied by this provider.
	 *
	 * @param registry registration surface restricted to this provider's namespace
	 */
	void install(@NotNull AgentOperationRegistry registry);
}

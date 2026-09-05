package me.whereareiam.anvil.agent.api.transport;

import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import org.jetbrains.annotations.NotNull;

import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Creates the transport endpoint embedded in a managed platform.
 */
public interface AgentServerProvider {
	/**
	 * Starts an endpoint using the connection settings supplied by Anvil to the platform process.
	 *
	 * @param agent platform behavior exposed by this endpoint
	 * @param logger platform logging callback
	 * @return running endpoint owned by the platform plugin
	 */
	@NotNull AgentServer start(@NotNull PlatformAgent agent, @NotNull Consumer<String> logger);

	/**
	 * Discovers the transport packaged with this platform agent.
	 *
	 * <p>Discovery uses the API's owning class loader because platform plugins load their
	 * embedded runtime separately from the server and other plugins.</p>
	 *
	 * @return packaged transport provider
	 * @throws IllegalStateException when the platform-agent artifact contains no provider
	 */
	static @NotNull AgentServerProvider discover() {
		return ServiceLoader.load(AgentServerProvider.class, AgentServerProvider.class.getClassLoader())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No Anvil agent server provider is installed"));
	}
}

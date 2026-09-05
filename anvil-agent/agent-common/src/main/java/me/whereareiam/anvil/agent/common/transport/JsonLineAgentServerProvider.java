package me.whereareiam.anvil.agent.common.transport;

import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import me.whereareiam.anvil.agent.api.transport.AgentServer;
import me.whereareiam.anvil.agent.api.transport.AgentServerProvider;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.nio.file.Path;

/**
 * Starts the authenticated JSON-lines transport inside a platform plugin.
 */
public final class JsonLineAgentServerProvider implements AgentServerProvider {
	@Override
	public @NotNull AgentServer start(@NotNull PlatformAgent agent, @NotNull Consumer<String> logger) {
		AgentExtensionLoader extensions = new AgentExtensionLoader(
				Path.of("plugins", "anvil-agent-extensions"), agent.getClass().getClassLoader());
		try {
			AgentServer server = new PlatformAgentServer(agent, extensions.providers(), logger);
			return () -> {
				try {
					server.close();
				} finally {
					extensions.close();
				}
			};
		} catch (RuntimeException | Error failure) {
			try {
				extensions.close();
			} catch (RuntimeException closeFailure) {
				failure.addSuppressed(closeFailure);
			}
			throw failure;
		}
	}
}

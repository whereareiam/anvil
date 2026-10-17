package me.whereareiam.anvil.agent.server.transport;

import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.event.PlayerEvents;
import me.whereareiam.anvil.agent.server.api.transport.AgentServer;
import me.whereareiam.anvil.agent.server.api.transport.AgentServerProvider;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.function.Consumer;

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
			return new AgentServer() {
				@Override
				public @NotNull PlayerEvents playerEvents() {
					return server.playerEvents();
				}

				@Override
				public void close() {
					try {
						server.close();
					} finally {
						extensions.close();
					}
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

package me.whereareiam.anvil.agent.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import me.whereareiam.anvil.agent.api.transport.AgentServer;
import me.whereareiam.anvil.agent.api.transport.AgentServerProvider;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.api.type.Platforms;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Internal Velocity plugin that exposes authenticated test observations to Anvil.
 */
@Plugin(
		id = "anvil-platform-agent",
		name = "Anvil Platform Agent",
		version = "${version}",
		description = "Loopback-only runtime agent installed by Anvil"
)
public final class AnvilVelocityAgent implements PlatformAgent {
	private final ProxyServer proxy;
	private final Logger logger;
	private AgentServer agent;

	/**
	 * Creates the injected Velocity agent.
	 */
	@Inject
	public AnvilVelocityAgent(ProxyServer proxy, Logger logger) {
		this.proxy = proxy;
		this.logger = logger;
	}

	/**
	 * Starts the agent when Velocity is initialized.
	 */
	@Subscribe
	public void initialize(ProxyInitializeEvent event) {
		agent = AgentServerProvider.discover().start(this, logger::info);
	}

	/**
	 * Stops the agent before Velocity shuts down.
	 */
	@Subscribe
	public void shutdown(ProxyShutdownEvent event) {
		if (agent != null) agent.close();
	}

	@Override
	public @NotNull AgentInfo info() {
		return AgentInfo.builder()
				.platform(Platforms.VELOCITY)
				.version(proxy.getVersion().getVersion())
				.role(AgentRole.PROXY)
				.build();
	}

	@Override
	public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
		Player player = proxy.getPlayer(username).orElse(null);
		if (player == null) return Optional.empty();

		return Optional.of(AgentIdentity.builder()
				.username(player.getUsername())
				.uniqueId(player.getUniqueId())
				.location(ProxyLocation.builder()
						.proxy(Platforms.VELOCITY)
						.connectedServer(player
								.getCurrentServer()
								.map(connection -> connection.getServerInfo().getName()).orElse(null))
						.build())
				.build());
	}

	@Override
	public boolean executeCommand(@NotNull String command) {
		return proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command).join();
	}

	@Override
	public <T> @NotNull Optional<T> findService(@NotNull Class<T> type) {
		if (type.isInstance(proxy)) return Optional.of(type.cast(proxy));
		if (type.isInstance(this)) return Optional.of(type.cast(this));

		return Optional.empty();
	}
}

package me.whereareiam.anvil.agent.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentProfile;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.transport.AgentServer;
import me.whereareiam.anvil.agent.server.api.transport.AgentServerProvider;
import me.whereareiam.anvil.api.type.Platforms;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
	private final Set<UUID> online = ConcurrentHashMap.newKeySet();
	private final Map<UUID, String> current = new ConcurrentHashMap<>();
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
	 * Reports a player joining the proxy itself, before the proxy connects it to any server.
	 */
	@Subscribe
	public void loggedIn(PostLoginEvent event) {
		online.add(event.getPlayer().getUniqueId());
		agent.playerEvents().joined(profile(event.getPlayer()), null);
	}

	/**
	 * Reports a player leaving the server it was on, if any, and joining the one the proxy connected it to.
	 */
	@Subscribe
	public void connected(ServerPostConnectEvent event) {
		Player player = event.getPlayer();
		String server = player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse(null);
		if (server == null) return;

		String previous = current.put(player.getUniqueId(), server);
		if (previous != null) agent.playerEvents().left(profile(player), previous);
		agent.playerEvents().joined(profile(player), server);
	}

	/**
	 * Reports a player that disconnects leaving the server it was on, then the proxy itself.
	 */
	@Subscribe
	public void disconnected(DisconnectEvent event) {
		UUID uniqueId = event.getPlayer().getUniqueId();
		String server = current.remove(uniqueId);
		if (server != null) agent.playerEvents().left(profile(event.getPlayer()), server);
		if (online.remove(uniqueId)) agent.playerEvents().left(profile(event.getPlayer()), null);
	}

	private static AgentProfile profile(Player player) {
		return AgentProfile.builder().username(player.getUsername()).uniqueId(player.getUniqueId()).build();
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

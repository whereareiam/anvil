package me.whereareiam.anvil.agent.bungeecord;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentProfile;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.transport.AgentServer;
import me.whereareiam.anvil.agent.server.api.transport.AgentServerProvider;
import me.whereareiam.anvil.api.type.Platforms;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal BungeeCord plugin that exposes authenticated test observations to Anvil.
 */
public final class AnvilBungeeCordAgent extends Plugin implements PlatformAgent, Listener {
	private final Set<UUID> online = ConcurrentHashMap.newKeySet();
	private final Map<UUID, String> current = new ConcurrentHashMap<>();
	private AgentServer agent;

	@Override
	public void onEnable() {
		agent = AgentServerProvider.discover().start(this, message -> getLogger().info(message));
		getProxy().getPluginManager().registerListener(this, this);
	}

	/**
	 * Reports a player joining the proxy itself, before the proxy connects it to any server.
	 *
	 * @param event the login
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void loggedIn(PostLoginEvent event) {
		online.add(event.getPlayer().getUniqueId());
		agent.playerEvents().joined(profile(event.getPlayer()), null);
	}

	/**
	 * Reports a player leaving its previous server and joining the one the proxy switched it to. BungeeCord reports
	 * the switch before it reports leaving the previous server, so the leave is recorded here first.
	 *
	 * @param event the switch
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void switched(ServerSwitchEvent event) {
		ProxiedPlayer player = event.getPlayer();
		if (player.getServer() == null) return;

		String server = player.getServer().getInfo().getName();
		String previous = current.put(player.getUniqueId(), server);
		if (previous != null) agent.playerEvents().left(profile(player), previous);
		agent.playerEvents().joined(profile(player), server);
	}

	/**
	 * Reports a player leaving a server it is still recorded on.
	 *
	 * @param event the disconnection from a server
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void disconnectedFrom(ServerDisconnectEvent event) {
		String server = event.getTarget().getName();
		if (current.remove(event.getPlayer().getUniqueId(), server)) agent.playerEvents().left(profile(event.getPlayer()), server);
	}

	/**
	 * Reports a player that disconnects from the proxy leaving the server it was on, then the proxy itself.
	 *
	 * @param event the disconnection from the proxy
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void disconnected(PlayerDisconnectEvent event) {
		UUID uniqueId = event.getPlayer().getUniqueId();
		String server = current.remove(uniqueId);
		if (server != null) agent.playerEvents().left(profile(event.getPlayer()), server);
		if (online.remove(uniqueId)) agent.playerEvents().left(profile(event.getPlayer()), null);
	}

	private static AgentProfile profile(ProxiedPlayer player) {
		return AgentProfile.builder().username(player.getName()).uniqueId(player.getUniqueId()).build();
	}

	@Override
	public void onDisable() {
		if (agent != null)
			agent.close();
	}

	@Override
	public @NotNull AgentInfo info() {
		return AgentInfo.builder().platform(Platforms.BUNGEECORD).version(getProxy().getVersion())
				.role(AgentRole.PROXY).build();
	}

	@Override
	public boolean executeCommand(@NotNull String command) {
		return getProxy().getPluginManager().dispatchCommand(getProxy().getConsole(), command);
	}

	@Override
	public <T> @NotNull Optional<T> findService(@NotNull Class<T> type) {
		if (type.isInstance(getProxy())) return Optional.of(type.cast(getProxy()));
		if (type.isInstance(this)) return Optional.of(type.cast(this));
		return Optional.empty();
	}
}

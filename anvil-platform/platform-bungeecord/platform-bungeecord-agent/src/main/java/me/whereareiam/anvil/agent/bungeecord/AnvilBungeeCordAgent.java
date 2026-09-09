package me.whereareiam.anvil.agent.bungeecord;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.transport.AgentServer;
import me.whereareiam.anvil.agent.server.api.transport.AgentServerProvider;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
import me.whereareiam.anvil.api.type.Platforms;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Internal BungeeCord plugin that exposes authenticated test observations to Anvil.
 */
public final class AnvilBungeeCordAgent extends Plugin implements PlatformAgent {
	private AgentServer agent;

	@Override
	public void onEnable() {
		agent = AgentServerProvider.discover().start(this, message -> getLogger().info(message));
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
	public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
		ProxiedPlayer player = getProxy().getPlayer(username);
		if (player == null) return Optional.empty();

		return Optional.of(AgentIdentity.builder()
				.username(player.getName()).uniqueId(player.getUniqueId())
				.location(ProxyLocation.builder().proxy(Platforms.BUNGEECORD)
						.connectedServer(player.getServer() == null
								? null
								: player.getServer().getInfo().getName()).build())
				.build());
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

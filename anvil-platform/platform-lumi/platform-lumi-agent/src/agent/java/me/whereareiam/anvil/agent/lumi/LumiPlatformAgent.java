package me.whereareiam.anvil.agent.lumi;

import cn.nukkit.Server;
import cn.nukkit.plugin.Plugin;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.transport.AgentServer;
import me.whereareiam.anvil.agent.server.api.transport.AgentServerProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Exposes authenticated test observations of a Lumi server to Anvil.
 *
 * <p>The {@code AnvilLumiAgent} plugin creates it in the class loader that holds the agent server and its Jackson,
 * so the agent server and the extensions it loads see that Jackson rather than Lumi's. Creating the agent starts
 * its server; closing it stops the server.
 */
public final class LumiPlatformAgent implements PlatformAgent, AutoCloseable {
	private final Plugin plugin;
	private final AgentServer agent;

	/**
	 * Starts the agent server for the plugin that loaded this agent.
	 *
	 * @param plugin the Lumi plugin whose scheduler and logger the agent uses
	 */
	public LumiPlatformAgent(@NotNull Plugin plugin) {
		this.plugin = plugin;
		this.agent = AgentServerProvider.discover().start(this, message -> plugin.getLogger().info(message));
	}

	@Override
	public void close() {
		agent.close();
	}

	@Override
	public @NotNull AgentInfo info() {
		Server server = plugin.getServer();
		return AgentInfo.builder()
				.platform(server.getName())
				.version(server.getNukkitVersion() + " (Minecraft " + server.getVersion() + ")")
				.role(AgentRole.SERVER)
				.build();
	}

	/**
	 * Finds the player whose client logged in with the UUID, the one Lumi lists it under for clients, and reports
	 * Lumi's own UUID of the player: Lumi keeps another one for a player that logged in offline, derived from its
	 * name or taken from the names it has seen before.
	 */
	@Override
	public @NotNull Optional<AgentIdentity> identity(@NotNull UUID uniqueId) {
		Server server = plugin.getServer();
		try {
			return call(() -> server.getOnlinePlayers().values().stream()
					.filter(player -> uniqueId.equals(player.getLoginUuid()))
					.findFirst())
					.map(player -> AgentIdentity.builder()
							.username(player.getName())
							.uniqueId(player.getUniqueId())
							.location(ServerLocation.builder().server(server.getName()).build())
							.build());
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read Lumi player identity", exception);
		}
	}

	@Override
	public boolean executeCommand(@NotNull String command) {
		Server server = plugin.getServer();
		try {
			return call(() -> server.dispatchCommand(server.getConsoleSender(), command));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not execute Lumi command", exception);
		}
	}

	@Override
	public <T> @NotNull Optional<T> findService(@NotNull Class<T> type) {
		if (type.isInstance(plugin.getServer()))
			return Optional.of(type.cast(plugin.getServer()));
		if (type.isInstance(plugin))
			return Optional.of(type.cast(plugin));
		if (type.isInstance(this))
			return Optional.of(type.cast(this));
		return Optional.empty();
	}

	@Override
	public <T> T call(@NotNull Supplier<T> supplier) throws Exception {
		Server server = plugin.getServer();
		if (server.isPrimaryThread())
			return supplier.get();

		CompletableFuture<T> result = new CompletableFuture<>();
		server.getScheduler().scheduleTask(plugin, () -> {
			try {
				result.complete(supplier.get());
			} catch (Throwable failure) {
				result.completeExceptionally(failure);
			}
		});

		return result.get(5, TimeUnit.SECONDS);
	}
}

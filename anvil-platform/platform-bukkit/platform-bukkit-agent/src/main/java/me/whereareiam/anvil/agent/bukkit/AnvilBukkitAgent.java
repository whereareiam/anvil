package me.whereareiam.anvil.agent.bukkit;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentProfile;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.transport.AgentServer;
import me.whereareiam.anvil.agent.server.api.transport.AgentServerProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Internal Paper/Spigot plugin that exposes authenticated test observations to Anvil.
 */
public final class AnvilBukkitAgent extends JavaPlugin implements PlatformAgent, Listener {
	private AgentServer agent;

	@Override
	public void onEnable() {
		agent = AgentServerProvider.discover().start(this, message -> getLogger().info(message));
		getServer().getPluginManager().registerEvents(this, this);
	}

	/**
	 * Reports a player joining once every other plugin has seen the join.
	 *
	 * @param event the join
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void joined(PlayerJoinEvent event) {
		agent.playerEvents().joined(profile(event.getPlayer()), null);
	}

	/**
	 * Reports a player leaving.
	 *
	 * @param event the quit
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void left(PlayerQuitEvent event) {
		agent.playerEvents().left(profile(event.getPlayer()), null);
	}

	private static AgentProfile profile(Player player) {
		return AgentProfile.builder().username(player.getName()).uniqueId(player.getUniqueId()).build();
	}

	@Override
	public void onDisable() {
		if (agent != null)
			agent.close();
	}

	@Override
	public @NotNull AgentInfo info() {
		return AgentInfo.builder()
				.platform(Bukkit.getName())
				.version(Bukkit.getVersion())
				.role(AgentRole.SERVER)
				.build();
	}

	@Override
	public boolean executeCommand(@NotNull String command) {
		try {
			return call(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not execute Bukkit command", exception);
		}
	}

	@Override
	public <T> @NotNull Optional<T> findService(@NotNull Class<T> type) {
		if (type.isInstance(getServer()))
			return Optional.of(type.cast(getServer()));
		if (type.isInstance(this))
			return Optional.of(type.cast(this));
		return Optional.empty();
	}

	@Override
	public <T> T call(@NotNull Supplier<T> supplier) throws Exception {
		if (Bukkit.isPrimaryThread())
			return supplier.get();
		CompletableFuture<T> result = new CompletableFuture<>();
		Bukkit.getScheduler().runTask(this, () -> {
			try {
				result.complete(supplier.get());
			} catch (Throwable failure) {
				result.completeExceptionally(failure);
			}
		});
		return result.get(5, TimeUnit.SECONDS);
	}
}

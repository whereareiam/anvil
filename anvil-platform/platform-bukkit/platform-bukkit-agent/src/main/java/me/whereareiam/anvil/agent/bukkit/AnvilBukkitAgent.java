package me.whereareiam.anvil.agent.bukkit;

import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import me.whereareiam.anvil.agent.api.transport.AgentServer;
import me.whereareiam.anvil.agent.api.transport.AgentServerProvider;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.AgentInfo;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Internal Paper/Spigot plugin that exposes authenticated test observations to Anvil.
 */
public final class AnvilBukkitAgent extends JavaPlugin implements PlatformAgent {
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
		return AgentInfo.builder()
				.platform(Bukkit.getName())
				.version(Bukkit.getVersion())
				.role(AgentRole.SERVER)
				.build();
	}

	@Override
	public @NotNull Optional<AgentIdentity> identity(@NotNull String username) {
		try {
			return Optional.ofNullable(call(() -> Bukkit.getPlayerExact(username)))
					.map(player -> AgentIdentity.builder()
							.username(player.getName())
							.uniqueId(player.getUniqueId())
							.location(ServerLocation.builder().server(Bukkit.getServer().getName()).build())
							.build());
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read Bukkit player identity", exception);
		}
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

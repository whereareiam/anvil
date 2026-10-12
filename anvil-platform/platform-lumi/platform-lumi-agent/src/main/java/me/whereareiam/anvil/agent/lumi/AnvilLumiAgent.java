package me.whereareiam.anvil.agent.lumi;

import cn.nukkit.Server;
import cn.nukkit.plugin.PluginBase;
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
 * Internal Lumi plugin that exposes authenticated test observations to Anvil.
 */
public final class AnvilLumiAgent extends PluginBase implements PlatformAgent {
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
		Server server = getServer();
		return AgentInfo.builder()
				.platform(server.getName())
				.version(server.getNukkitVersion() + " (Minecraft " + server.getVersion() + ")")
				.role(AgentRole.SERVER)
				.build();
	}

	@Override
	public @NotNull Optional<AgentIdentity> identity(@NotNull UUID uniqueId) {
		try {
			return call(() -> getServer().getPlayer(uniqueId))
					.map(player -> AgentIdentity.builder()
							.username(player.getName())
							.uniqueId(player.getUniqueId())
							.location(ServerLocation.builder().server(getServer().getName()).build())
							.build());
		} catch (Exception exception) {
			throw new IllegalStateException("Could not read Lumi player identity", exception);
		}
	}

	@Override
	public boolean executeCommand(@NotNull String command) {
		try {
			return call(() -> getServer().dispatchCommand(getServer().getConsoleSender(), command));
		} catch (Exception exception) {
			throw new IllegalStateException("Could not execute Lumi command", exception);
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
		if (getServer().isPrimaryThread())
			return supplier.get();

		CompletableFuture<T> result = new CompletableFuture<>();
		getServer().getScheduler().scheduleTask(this, () -> {
			try {
				result.complete(supplier.get());
			} catch (Throwable failure) {
				result.completeExceptionally(failure);
			}
		});

		return result.get(5, TimeUnit.SECONDS);
	}
}

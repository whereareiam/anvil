package me.whereareiam.anvil.agent.neoforge;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.api.model.AgentProfile;
import me.whereareiam.anvil.agent.api.type.AgentRole;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.transport.AgentServer;
import me.whereareiam.anvil.agent.server.api.transport.AgentServerProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Internal NeoForge mod that exposes authenticated test observations to Anvil.
 */
@Mod("anvil_platform_agent")
public final class AnvilNeoForgeAgent implements PlatformAgent {
	private static final String PLATFORM = "NeoForge";
	private static final Logger LOGGER = LoggerFactory.getLogger("AnvilPlatformAgent");

	private volatile MinecraftServer server;
	private AgentServer agent;

	/**
	 * Registers the server lifecycle listeners that start and stop the agent.
	 */
	public AnvilNeoForgeAgent() {
		NeoForge.EVENT_BUS.addListener(this::starting);
		NeoForge.EVENT_BUS.addListener(this::stopping);
		NeoForge.EVENT_BUS.addListener(this::joined);
		NeoForge.EVENT_BUS.addListener(this::left);
	}

	private void starting(ServerAboutToStartEvent event) {
		server = event.getServer();
		agent = AgentServerProvider.discover().start(this, LOGGER::info);
	}

	private void joined(PlayerEvent.PlayerLoggedInEvent event) {
		if (agent != null) agent.playerEvents().joined(profile(event.getEntity()), null);
	}

	private void left(PlayerEvent.PlayerLoggedOutEvent event) {
		if (agent != null) agent.playerEvents().left(profile(event.getEntity()), null);
	}

	private static AgentProfile profile(Player player) {
		return AgentProfile.builder().username(player.getScoreboardName()).uniqueId(player.getUUID()).build();
	}

	private void stopping(ServerStoppingEvent event) {
		if (agent != null)
			agent.close();
	}

	@Override
	public @NotNull AgentInfo info() {
		return AgentInfo.builder()
				.platform(PLATFORM)
				.version(server.getServerVersion())
				.role(AgentRole.SERVER)
				.build();
	}

	@Override
	public boolean executeCommand(@NotNull String command) {
		try {
			call(() -> {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
				return null;
			});
			return true;
		} catch (Exception exception) {
			throw new IllegalStateException("Could not execute NeoForge command", exception);
		}
	}

	@Override
	public <T> @NotNull Optional<T> findService(@NotNull Class<T> type) {
		if (type.isInstance(server))
			return Optional.of(type.cast(server));
		if (type.isInstance(this))
			return Optional.of(type.cast(this));
		return Optional.empty();
	}

	@Override
	public <T> T call(@NotNull Supplier<T> supplier) throws Exception {
		if (server.isSameThread())
			return supplier.get();
		return server.submit(supplier).get(5, TimeUnit.SECONDS);
	}
}

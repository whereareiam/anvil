package me.whereareiam.anvil.capability.worlds.neoforge.v1_21_1;

import me.whereareiam.anvil.agent.api.model.AgentInfo;
import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.capability.worlds.agent.MinecraftRelease;
import me.whereareiam.anvil.capability.worlds.agent.ServerWorlds;
import me.whereareiam.anvil.capability.worlds.agent.WorldChangeLog;
import me.whereareiam.anvil.capability.worlds.agent.WorldEventLog;
import me.whereareiam.anvil.capability.worlds.agent.WorldsAgentOperations;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

/**
 * Answers the worlds capability's operations on the NeoForge servers of Minecraft 1.21.1 until 1.21.11, through
 * the server the NeoForge agent holds, and records the players' dimension changes and the levels NeoForge loads and
 * unloads.
 */
public final class NeoForgeWorldsOperations extends WorldsAgentOperations {
	@Override
	public boolean supports(@NotNull AgentInfo platform) {
		return MinecraftRelease.within(platform.getVersion(), "1.21.1", "1.21.11");
	}

	@Override
	protected @NotNull ServerWorlds open(
			@NotNull PlatformAgent platform,
			@NotNull WorldChangeLog changes,
			@NotNull WorldEventLog events
	) {
		NeoForgeWorlds worlds = new NeoForgeWorlds(platform.requireService(MinecraftServer.class), changes, events);
		NeoForge.EVENT_BUS.addListener(worlds::changed);
		NeoForge.EVENT_BUS.addListener(worlds::loaded);
		NeoForge.EVENT_BUS.addListener(worlds::unloaded);

		return worlds;
	}
}

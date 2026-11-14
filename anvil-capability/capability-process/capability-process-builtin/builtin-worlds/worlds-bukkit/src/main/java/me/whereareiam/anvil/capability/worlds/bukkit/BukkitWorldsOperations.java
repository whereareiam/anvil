package me.whereareiam.anvil.capability.worlds.bukkit;

import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.capability.worlds.agent.ServerWorlds;
import me.whereareiam.anvil.capability.worlds.agent.WorldChangeLog;
import me.whereareiam.anvil.capability.worlds.agent.WorldEventLog;
import me.whereareiam.anvil.capability.worlds.agent.WorldsAgentOperations;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Answers the worlds capability's operations on Paper and Spigot, through the Bukkit API of the platform agent's
 * plugin, which also registers the listener that records the players' world changes and the worlds the server loads and
 * unloads.
 */
public final class BukkitWorldsOperations extends WorldsAgentOperations {
	@Override
	protected @NotNull ServerWorlds open(
			@NotNull PlatformAgent platform,
			@NotNull WorldChangeLog changes,
			@NotNull WorldEventLog events
	) {
		Plugin plugin = platform.requireService(Plugin.class);
		BukkitWorlds worlds = new BukkitWorlds(plugin.getServer(), changes, events);
		plugin.getServer().getPluginManager().registerEvents(worlds, plugin);

		return worlds;
	}
}

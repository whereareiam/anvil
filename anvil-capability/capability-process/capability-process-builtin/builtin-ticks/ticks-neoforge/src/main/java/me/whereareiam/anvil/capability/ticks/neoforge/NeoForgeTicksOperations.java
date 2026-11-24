package me.whereareiam.anvil.capability.ticks.neoforge;

import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.capability.ticks.agent.ServerTicks;
import me.whereareiam.anvil.capability.ticks.agent.TickClock;
import me.whereareiam.anvil.capability.ticks.agent.TicksAgentOperations;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

/**
 * Answers the ticks capability's operations on NeoForge, through the tick rate manager of the server the NeoForge
 * agent holds, and reports each tick the server completes from NeoForge's server tick event.
 */
public final class NeoForgeTicksOperations extends TicksAgentOperations {
	@Override
	protected @NotNull ServerTicks open(@NotNull PlatformAgent platform, @NotNull TickClock clock) {
		NeoForgeTicks ticks = new NeoForgeTicks(platform.requireService(MinecraftServer.class), clock);
		NeoForge.EVENT_BUS.addListener(ticks::ticked);

		return ticks;
	}
}

package me.whereareiam.anvil.capability.ticks.neoforge;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.ticks.agent.ServerTicks;
import me.whereareiam.anvil.capability.ticks.agent.TickClock;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A NeoForge server's ticks: it sprints through Minecraft's own tick rate manager, as {@code /tick sprint} does, and
 * counts a tick each time NeoForge reports that the server completed one.
 */
@RequiredArgsConstructor
final class NeoForgeTicks implements ServerTicks {
	private final MinecraftServer server;
	private final TickClock clock;

	@Override
	public void sprint(int ticks) {
		server.tickRateManager().requestGameToSprint(ticks);
	}

	@Override
	public boolean stopSprinting() {
		return server.tickRateManager().stopSprinting();
	}

	/**
	 * Counts a tick the server completed.
	 *
	 * @param event the end of the tick
	 */
	void ticked(@NotNull ServerTickEvent.Post event) {
		clock.ticked();
	}
}

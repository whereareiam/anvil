package me.whereareiam.anvil.capability.ticks.neoforge;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.ticks.agent.ServerTicks;
import me.whereareiam.anvil.capability.ticks.agent.TickClock;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickPace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A NeoForge server's ticks: it sets their pace through Minecraft's own tick rate manager, as {@code /tick rate} and
 * {@code /tick sprint} do, and counts a tick each time NeoForge reports that the server completed one. A sprint is
 * asked for as many ticks as a sprint can run, so it lasts until another pace is set.
 */
@RequiredArgsConstructor
final class NeoForgeTicks implements ServerTicks {
	private final MinecraftServer server;
	private final TickClock clock;

	@Override
	public @NotNull AgentTickPace pace() {
		ServerTickRateManager manager = server.tickRateManager();

		return AgentTickPace.builder().rate(manager.tickrate()).sprinting(manager.isSprinting()).build();
	}

	@Override
	public void pace(@NotNull AgentTickPace pace) {
		ServerTickRateManager manager = server.tickRateManager();
		manager.setTickRate(pace.getRate());
		if (pace.isSprinting()) manager.requestGameToSprint(Integer.MAX_VALUE);
		else manager.stopSprinting();
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

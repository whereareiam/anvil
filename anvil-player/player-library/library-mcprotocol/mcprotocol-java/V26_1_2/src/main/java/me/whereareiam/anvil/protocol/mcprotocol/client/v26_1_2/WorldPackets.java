package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.java.api.model.world.ClockState;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.WorldTranslation;
import org.geysermc.mcprotocollib.protocol.data.game.level.ClockNetworkState;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * The world of the MCProtocolLib releases from 26.1 on, whose time carries the state of world clocks, each named by
 * its ID in the {@code minecraft:world_clock} registry the configuration synchronized; the translation reads that
 * registry's entries as they arrive.
 */
public final class WorldPackets extends WorldTranslation {
	private static final String WORLD_CLOCKS = "minecraft:world_clock";

	private volatile List<String> clocks = List.of();

	@Override
	protected void registry(@NotNull ClientboundRegistryDataPacket registry) {
		if (registry.getRegistry().asString().equals(WORLD_CLOCKS))
			clocks = registry.getEntries().stream().map(entry -> entry.getId().asString()).toList();
	}

	@Override
	protected @NotNull ClientboundSetTime time(@NotNull ClientboundSetTimePacket time) {
		ClientboundSetTime.ClientboundSetTimeBuilder translated = ClientboundSetTime.builder().gameTime(time.getGameTime());
		for (Map.Entry<Integer, ClockNetworkState> update : time.getClockUpdates().entrySet()) {
			ClockNetworkState state = update.getValue();
			translated.clockUpdate(clock(update.getKey()),
					new ClockState(state.totalTicks(), state.partialTick(), state.rate()));
		}

		return translated.build();
	}

	private @NotNull String clock(int id) {
		List<String> known = clocks;
		if (id < 0 || id >= known.size())
			throw new IllegalStateException("The server sent the world clock " + id + ", which its configuration did not list");

		return known.get(id);
	}
}

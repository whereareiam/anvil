package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry;
import me.whereareiam.anvil.edition.java.protocol.model.world.ClockState;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.SynchronizedRegistries;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.WorldTranslation;
import org.geysermc.mcprotocollib.protocol.data.game.level.ClockNetworkState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The world of the MCProtocolLib releases from 26.1 on, whose time carries the state of world clocks, each named by
 * its ID in the {@code minecraft:world_clock} registry, which the translation looks up in the connection's
 * {@link SynchronizedRegistries}. A clock the configuration did not list breaks the protocol, at which a vanilla client
 * disconnects; the translation throws, which makes MCProtocolLib disconnect the session as well.
 */
public final class WorldPackets extends WorldTranslation {
	private static final String WORLD_CLOCKS = "minecraft:world_clock";

	private final SynchronizedRegistries registries;

	/**
	 * Creates the world translation of one connection.
	 *
	 * @param registries the connection's registries, in which the translation looks up world clocks
	 */
	public WorldPackets(@NotNull SynchronizedRegistries registries) {
		this.registries = registries;
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
		RegistryEntry clock = registries.entry(WORLD_CLOCKS, id);
		if (clock == null)
			throw new IllegalStateException("The server sent the world clock " + id + ", which its configuration did not list");

		return clock.getKey();
	}
}

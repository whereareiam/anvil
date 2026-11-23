package me.whereareiam.anvil.player.library.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.edition.java.protocol.model.world.ClockState;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTable;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.ClockNetworkState;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The release's time carries world clocks by their IDs in the configuration's {@code minecraft:world_clock} registry,
 * which the release's configuration translation records for the world translation of the same packet table.
 */
class WorldPacketsTest {
	private final PacketTable<Packet> packets = McProtocolClientAdapter.packets(PacketDemand.EVERY);

	@Test
	void namesEachClockByItsKeyInTheConfigurationsRegistry() {
		ClientboundRegistryData registry = assertInstanceOf(ClientboundRegistryData.class, packets.decode(
				new ClientboundRegistryDataPacket(Key.key("minecraft:world_clock"), List.of(
						new RegistryEntry(Key.key("minecraft:overworld"), null),
						new RegistryEntry(Key.key("minecraft:the_end"), null)
				))));

		ClientboundSetTime time = (ClientboundSetTime) packets.decode(new ClientboundSetTimePacket(500, Map.of(
				0, new ClockNetworkState(13000, 0.5F, 1),
				1, new ClockNetworkState(40, 0, 0))));

		assertEquals("minecraft:world_clock", registry.registry());
		assertEquals(ClientboundSetTime.builder()
				.gameTime(500)
				.clockUpdate("minecraft:overworld", new ClockState(13000, 0.5F, 1))
				.clockUpdate("minecraft:the_end", new ClockState(40, 0, 0))
				.build(), time);
		assertNull(time.dayTime());
	}

	@Test
	void refusesAClockTheConfigurationDidNotList() {
		assertThrows(IllegalStateException.class, () -> packets.decode(new ClientboundSetTimePacket(500, Map.of(
				0, new ClockNetworkState(1, 0, 1)))));
	}

	@Test
	void namesTheClockOfEachDimensionType() {
		packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"), List.of(
				new RegistryEntry(Key.key("minecraft:overworld"), null),
				new RegistryEntry(Key.key("minecraft:the_nether"), null),
				new RegistryEntry(Key.key("example:void"), NbtMap.builder()
						.putString("skybox", "end")
						.putString("default_clock", "example:slow")
						.putInt("min_y", 0)
						.putInt("height", 256)
						.build())
		)));

		assertEquals(DimensionType.known("minecraft:overworld").toBuilder().defaultClock("minecraft:overworld").build(),
				respawnInto(0).dimensionType());
		assertNull(respawnInto(1).dimensionType().defaultClock());
		assertEquals("example:slow", respawnInto(2).dimensionType().defaultClock());
	}

	private ClientboundRespawn respawnInto(int type) {
		return (ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(new PlayerSpawnInfo(type,
				Key.key("minecraft:arena"), 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, null, 0, 63), false, false));
	}
}

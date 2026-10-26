package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionPacketsTest {
	@Test
	void readsTheHeightOfTheDimensionTypeFromRegistryDataTypedAsAnyTagWhileTheTableStillTranslatesTheRegistryData() {
		PacketTable<Packet> packets = McProtocolClientAdapter.packets();

		ClientboundPacket registry = packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"),
				List.of(new RegistryEntry(Key.key("minecraft:overworld"),
						NbtMap.builder().putInt("min_y", -64).putInt("height", 384).build()))));

		assertEquals("minecraft:dimension_type", assertInstanceOf(ClientboundRegistryData.class, registry).getRegistry());
		assertEquals(new ClientboundLogin(42, new Dimension("minecraft:overworld", -64, 384)), packets.decode(login(0)));
	}

	@Test
	void givesNoHeightForAnEntryWhoseDataIsNoCompound() {
		ConnectionPackets packets = new ConnectionPackets(new ConnectionLevel());
		packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"),
				List.of(new RegistryEntry(Key.key("minecraft:overworld"), "not a compound"))));

		assertThrows(IllegalStateException.class, () -> packets.decode(login(0)));
	}

	private static ClientboundLoginPacket login(int type) {
		return new ClientboundLoginPacket(42, false, new Key[0], 20, 10, 10, false, true, false,
				new PlayerSpawnInfo(type, Key.key("minecraft:overworld"), 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false,
						false, null, 0, 63), false, false);
	}
}

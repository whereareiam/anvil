package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.configuration.ClientboundRegistryData;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigurationPacketsTest {
	private final ConfigurationPackets packets = new ConfigurationPackets();

	@Test
	void decodesTheDialogRegistryInRegistryOrder() {
		ClientboundRegistryDataPacket registry = new ClientboundRegistryDataPacket(Key.key("minecraft:dialog"), List.of(
				new RegistryEntry(Key.key("minecraft:server_links"), null),
				new RegistryEntry(Key.key("example:rules"), NbtMap.builder()
						.putString("type", "minecraft:notice")
						.putString("title", "Rules")
						.build())
		));

		assertEquals(new ClientboundRegistryData("minecraft:dialog", List.of(
				new me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry("minecraft:server_links", null),
				new me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry("example:rules", NbtCompound.builder()
						.put("type", new NbtString("minecraft:notice"))
						.put("title", new NbtString("Rules"))
						.build())
		)), packets.decode(registry));
	}

	@Test
	void decodesEveryOtherRegistryAsWell() {
		ClientboundRegistryDataPacket registry = new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"), List.of(
				new RegistryEntry(Key.key("minecraft:overworld"), NbtMap.builder().putInt("height", 384).build())
		));

		assertEquals(new ClientboundRegistryData("minecraft:dimension_type", List.of(
				new me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry("minecraft:overworld",
						NbtCompound.EMPTY.with("height", new NbtInt(384)))
		)), packets.decode(registry));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesTheRegistryData() {
		assertEquals(Set.of(ClientboundRegistryData.class), packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

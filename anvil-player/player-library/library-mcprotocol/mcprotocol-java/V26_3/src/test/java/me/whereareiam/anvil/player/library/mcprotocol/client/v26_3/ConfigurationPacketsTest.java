package me.whereareiam.anvil.player.library.mcprotocol.client.v26_3;

import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtCompound;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtString;
import me.whereareiam.anvil.edition.java.protocol.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.SynchronizedRegistries;
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
	private final ConfigurationPackets packets = new ConfigurationPackets(new SynchronizedRegistries());

	@Test
	void decodesCompoundEntriesAndEntriesWithoutData() {
		ClientboundRegistryDataPacket registry = new ClientboundRegistryDataPacket(Key.key("minecraft:dialog"), List.of(
				new RegistryEntry(Key.key("minecraft:server_links"), null),
				new RegistryEntry(Key.key("example:rules"), NbtMap.builder()
						.putString("type", "minecraft:notice")
						.putString("title", "Rules")
						.build())
		));

		assertEquals(new ClientboundRegistryData("minecraft:dialog", List.of(
				new me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry("minecraft:server_links", null),
				new me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry("example:rules", NbtCompound.builder()
						.put("type", new NbtString("minecraft:notice"))
						.put("title", new NbtString("Rules"))
						.build())
		)), packets.decode(registry));
	}

	@Test
	void carriesNoDataForAnEntryWhoseTagIsNoCompound() {
		ClientboundRegistryDataPacket registry = new ClientboundRegistryDataPacket(Key.key("example:names"), List.of(
				new RegistryEntry(Key.key("example:name"), "Alice")
		));

		assertEquals(new ClientboundRegistryData("example:names", List.of(
				new me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry("example:name", null)
		)), packets.decode(registry));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertEquals(Set.of(ClientboundRegistryData.class), packets.packets());
	}
}

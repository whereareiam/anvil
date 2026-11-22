package me.whereareiam.anvil.player.library.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTable;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The release's packet table reads whether a dimension type's lava is fast from its
 * {@code minecraft:gameplay/fast_lava} environment attribute, and knows the vanilla nether's lava as fast.
 */
class ConnectionPacketsTest {
	private final PacketTable<Packet> packets = McProtocolClientAdapter.packets(PacketDemand.EVERY);

	@Test
	void readsFastLavaFromTheEnvironmentAttributeOfADescriptionAndOfTheVanillaNether() {
		packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"), List.of(
				new RegistryEntry(Key.key("minecraft:the_nether"), null),
				new RegistryEntry(Key.key("minecraft:overworld"), null),
				new RegistryEntry(Key.key("example:hot"), described(NbtMap.builder()
						.putCompound("attributes", NbtMap.builder()
								.putBoolean("minecraft:gameplay/fast_lava", true)
								.build()))),
				new RegistryEntry(Key.key("example:flagged"), described(NbtMap.builder().putBoolean("ultrawarm", true)))
		)));

		assertTrue(respawnInto(0).getDimensionType().isFastLava());
		assertFalse(respawnInto(1).getDimensionType().isFastLava());
		assertTrue(respawnInto(2).getDimensionType().isFastLava());
		assertFalse(respawnInto(3).getDimensionType().isFastLava());
	}

	private ClientboundRespawn respawnInto(int type) {
		return (ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(new PlayerSpawnInfo(type,
				Key.key("minecraft:arena"), 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, null, 0, 63), false, false));
	}

	private static NbtMap described(NbtMapBuilder description) {
		return description.putString("skybox", "none").putInt("min_y", 0).putInt("height", 256).build();
	}
}

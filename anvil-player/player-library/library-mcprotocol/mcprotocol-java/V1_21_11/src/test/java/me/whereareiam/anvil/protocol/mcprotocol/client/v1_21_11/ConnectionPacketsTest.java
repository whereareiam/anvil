package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.type.CardinalLight;
import me.whereareiam.anvil.protocol.java.api.type.Skybox;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The release's packet table decodes the registry data of the configuration and names the dimension types of joins
 * and respawns by it.
 */
class ConnectionPacketsTest {
	private final PacketTable<Packet> packets = McProtocolClientAdapter.packets();

	@Test
	void decodesTheRegistryDataAndNamesTheTypeOfEachLevelByIt() {
		assertEquals(new ClientboundRegistryData("minecraft:dimension_type", List.of(
				new RegistryEntry("minecraft:overworld", null),
				new RegistryEntry("example:void", NbtCompound.builder()
						.put("effects", new NbtString("minecraft:the_end"))
						.put("min_y", new NbtInt(0))
						.put("height", new NbtInt(256))
						.build())
		)), packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft", "dimension_type"), List.of(
				new org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry(Key.key("minecraft", "overworld"), null),
				new org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry(Key.key("example", "void"),
						NbtMap.builder()
								.putString("effects", "minecraft:the_end")
								.putInt("min_y", 0)
								.putInt("height", 256)
								.build())
		))));

		assertEquals(ClientboundLogin.builder()
				.entityId(42)
				.level("minecraft:overworld")
				.dimension("minecraft:overworld")
				.dimensionType(DimensionType.known("minecraft:overworld"))
				.showDeathScreen(false)
				.build(), packets.decode(new ClientboundLoginPacket(42, false, new Key[]{Key.key("minecraft", "overworld")},
				20, 10, 10, false, false, false, spawnIn(Key.key("minecraft", "overworld"), 0), false)));
		assertEquals(DimensionType.withEffects("example:void", "minecraft:the_end", 0, 256),
				((ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(spawnIn(Key.key("example", "arena"), 1),
						false, false))).getDimensionType());
		assertEquals(new ClientboundStartConfiguration(), packets.decode(new ClientboundStartConfigurationPacket()));
	}

	@Test
	void describesATypeByTheSkyboxAndShadingItNamesAndTakesTheDefaultsForThoseItLeavesOut() {
		packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft", "dimension_type"), List.of(
				new org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry(Key.key("minecraft", "the_nether"),
						NbtMap.builder()
								.putString("skybox", "none")
								.putString("cardinal_light", "nether")
								.putInt("min_y", 0)
								.putInt("height", 256)
								.build()),
				new org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry(Key.key("example", "plain"),
						NbtMap.builder().putInt("min_y", -64).putInt("height", 384).build())
		)));

		assertEquals(DimensionType.known("minecraft:the_nether"),
				((ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(spawnIn(Key.key("minecraft", "the_nether"), 0),
						false, false))).getDimensionType());
		assertEquals(DimensionType.builder()
						.key("example:plain")
						.skybox(Skybox.OVERWORLD)
						.cardinalLight(CardinalLight.DEFAULT)
						.minY(-64)
						.height(384)
						.build(),
				((ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(spawnIn(Key.key("example", "plain"), 1),
						false, false))).getDimensionType());
	}

	private static PlayerSpawnInfo spawnIn(Key level, int type) {
		return new PlayerSpawnInfo(type, level, 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, null, 0, 63);
	}
}

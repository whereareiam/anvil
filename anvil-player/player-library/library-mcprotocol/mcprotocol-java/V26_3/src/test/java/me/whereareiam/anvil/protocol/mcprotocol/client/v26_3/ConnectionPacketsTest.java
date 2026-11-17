package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The release's registry entries carry any tag as their data, and the release's packet table reads a dimension type's
 * description only from a compound, which names the type's block heights.
 */
class ConnectionPacketsTest {
	private final PacketTable<Packet> packets = McProtocolClientAdapter.packets(PacketDemand.EVERY);

	@Test
	void readsTheEffectsOfACompoundDescriptionAndTakesVanillaEffectsWithoutOne() {
		packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"), List.of(
				new RegistryEntry(Key.key("minecraft:the_end"), null),
				new RegistryEntry(Key.key("example:void"), NbtMap.builder()
						.putString("effects", "minecraft:the_nether")
						.putInt("min_y", 0)
						.putInt("height", 128)
						.build())
		)));

		assertEquals(DimensionType.known("minecraft:the_end").toBuilder().defaultClock("minecraft:the_end").build(),
				respawnInto(0).getDimensionType());
		assertEquals(DimensionType.withEffects("example:void", "minecraft:the_nether", 0, 128),
				respawnInto(1).getDimensionType());
	}

	@Test
	void readsTheClockADescriptionNamesAndTheClockOfAVanillaTypeWithoutOne() {
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

		assertEquals("minecraft:overworld", respawnInto(0).getDimensionType().getDefaultClock());
		assertNull(respawnInto(1).getDimensionType().getDefaultClock());
		assertEquals("example:slow", respawnInto(2).getDimensionType().getDefaultClock());
	}

	@Test
	void readsFastLavaFromTheEnvironmentAttributeOfADescriptionAndOfTheVanillaNether() {
		packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"), List.of(
				new RegistryEntry(Key.key("minecraft:the_nether"), null),
				new RegistryEntry(Key.key("example:hot"), NbtMap.builder()
						.putString("skybox", "none")
						.putInt("min_y", 0)
						.putInt("height", 256)
						.putCompound("attributes", NbtMap.builder()
								.putCompound("minecraft:gameplay/fast_lava", NbtMap.builder()
										.putString("modifier", "xnor")
										.putBoolean("argument", false)
										.build())
								.build())
						.build()),
				new RegistryEntry(Key.key("example:flagged"), NbtMap.builder()
						.putInt("min_y", 0)
						.putInt("height", 256)
						.putBoolean("ultrawarm", true)
						.build())
		)));

		assertTrue(respawnInto(0).getDimensionType().isFastLava());
		assertTrue(respawnInto(1).getDimensionType().isFastLava());
		assertFalse(respawnInto(2).getDimensionType().isFastLava());
	}

	private ClientboundRespawn respawnInto(int type) {
		return (ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(new PlayerSpawnInfo(type,
				Key.key("minecraft:arena"), 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, null, 0, 63), false, false));
	}
}

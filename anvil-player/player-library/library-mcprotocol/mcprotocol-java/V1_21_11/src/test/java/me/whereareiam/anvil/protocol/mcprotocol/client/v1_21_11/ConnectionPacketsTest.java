package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtCompound;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtInt;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtString;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.edition.java.protocol.type.world.CardinalLight;
import me.whereareiam.anvil.edition.java.protocol.type.world.Skybox;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTable;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The release's packet table decodes the registry data of the configuration and names the dimension types of joins
 * and respawns by it, reading fast lava from a type's {@code minecraft:gameplay/fast_lava} environment attribute.
 */
class ConnectionPacketsTest {
	private final PacketTable<Packet> packets = McProtocolClientAdapter.packets(PacketDemand.EVERY);

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
				.entityId(new EntityId(42))
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
								.putCompound("attributes", NbtMap.builder()
										.putBoolean("minecraft:gameplay/fast_lava", true)
										.build())
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

	@Test
	void readsFastLavaFromTheEnvironmentAttributeOfADescriptionAndNotFromTheUltrawarmFlagThisReleaseDropped() {
		types(
				attributed(NbtMap.builder().putBoolean("minecraft:gameplay/fast_lava", true).build()),
				attributed(NbtMap.builder().putBoolean("gameplay/fast_lava", true).build()),
				attributed(NbtMap.EMPTY),
				NbtMap.builder().putInt("min_y", 0).putInt("height", 256).putBoolean("ultrawarm", true).build(),
				attributed(NbtMap.builder().putBoolean("minecraft:gameplay/water_evaporates", true).build())
		);

		assertEquals(List.of(true, true, false, false, false), fastLava(5));
	}

	@Test
	void appliesAFastLavaModifierToTheAttributesDefault() {
		types(
				modified("override", true), modified("and", true), modified("nand", false), modified("or", true),
				modified("nor", false), modified("xor", true), modified("xnor", true), modified("or", false)
		);

		assertEquals(List.of(true, false, true, true, true, true, false, false), fastLava(8));
	}

	@Test
	void refusesAFastLavaAttributeNoVanillaClientCanRead() {
		types(modified("multiply", true), attributed(NbtMap.builder().putString("minecraft:gameplay/fast_lava", "yes")
				.build()), attributed(NbtMap.builder().putCompound("minecraft:gameplay/fast_lava",
				NbtMap.builder().putString("modifier", "or").build()).build()));

		for (int type = 0; type < 3; type++) {
			int id = type;
			assertThrows(IllegalStateException.class, () -> respawnInto(id), "type " + id);
		}
	}

	@Test
	void reportsTheGameModeOfEachLoginAndRespawn() {
		packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft", "dimension_type"), List.of(
				new org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry(Key.key("minecraft", "overworld"), null))));

		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.CREATIVE,
				((ClientboundLogin) packets.decode(new ClientboundLoginPacket(42, false,
						new Key[]{Key.key("minecraft", "overworld")}, 20, 10, 10, false, false, false,
						spawnIn(Key.key("minecraft", "overworld"), 0, GameMode.CREATIVE), false))).getGameMode());
		assertEquals(me.whereareiam.anvil.edition.java.protocol.type.player.GameMode.SPECTATOR,
				((ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(
						spawnIn(Key.key("minecraft", "overworld"), 0, GameMode.SPECTATOR), false, false))).getGameMode());
	}

	private void types(NbtMap... descriptions) {
		List<org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry> entries = new ArrayList<>();
		for (int id = 0; id < descriptions.length; id++)
			entries.add(new org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry(
					Key.key("example", "type_" + id), descriptions[id]));
		packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft", "dimension_type"), entries));
	}

	private List<Boolean> fastLava(int types) {
		List<Boolean> fastLava = new ArrayList<>();
		for (int type = 0; type < types; type++) fastLava.add(respawnInto(type).getDimensionType().isFastLava());
		return fastLava;
	}

	private ClientboundRespawn respawnInto(int type) {
		return (ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(
				spawnIn(Key.key("example", "arena"), type), false, false));
	}

	private static NbtMap attributed(NbtMap attributes) {
		return NbtMap.builder().putInt("min_y", 0).putInt("height", 256).putCompound("attributes", attributes).build();
	}

	private static NbtMap modified(String modifier, boolean argument) {
		return attributed(NbtMap.builder().putCompound("minecraft:gameplay/fast_lava", NbtMap.builder()
				.putString("modifier", modifier)
				.putBoolean("argument", argument)
				.build()).build());
	}

	private static PlayerSpawnInfo spawnIn(Key level, int type) {
		return spawnIn(level, type, GameMode.SURVIVAL);
	}

	private static PlayerSpawnInfo spawnIn(Key level, int type, GameMode gameMode) {
		return new PlayerSpawnInfo(type, level, 0, gameMode, GameMode.SURVIVAL, false, false, null, 0, 63);
	}
}

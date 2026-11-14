package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import net.kyori.adventure.key.Key;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets();

	@Test
	void namesTheJoinedLevelsTypeByItsIdInTheRegistryTheConfigurationSynchronized() {
		assertNull(packets.decode(types()));

		assertEquals(ClientboundLogin.builder()
				.entityId(42)
				.level("minecraft:overworld")
				.level("minecraft:arena")
				.hardcore(true)
				.dimension("minecraft:arena")
				.dimensionType(DimensionType.withEffects("example:void", "minecraft:the_end"))
				.flat(true)
				.showDeathScreen(false)
				.build(), packets.decode(new ClientboundLoginPacket(42, true,
				new Key[]{Key.key("minecraft", "overworld"), Key.key("minecraft", "arena")}, 20, 10, 10, false, false, false,
				spawnIn(Key.key("minecraft", "arena"), 2, false, true), false)));
	}

	@Test
	void givesAVanillaTypeWithoutDescriptionTheEffectsOfTheClientsOwnDataPack() {
		packets.decode(types());

		assertEquals(ClientboundRespawn.builder()
				.dimension("minecraft:the_nether")
				.dimensionType(DimensionType.withEffects("minecraft:the_nether", "minecraft:the_nether"))
				.debug(true)
				.build(), packets.decode(new ClientboundRespawnPacket(spawnIn(Key.key("minecraft", "the_nether"), 1, true,
				false), false, false)));
	}

	@Test
	void leavesTheEffectsOfAnUndescribedModTypeUnknownAndTakesTheOverworldsForAnEmptyDescription() {
		packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft", "dimension_type"), List.of(
				new RegistryEntry(Key.key("example", "modded"), null),
				new RegistryEntry(Key.key("example", "plain"), NbtMap.EMPTY)
		)));

		assertEquals(new DimensionType("example:modded", null, null), respawnInto(0).getDimensionType());
		assertEquals(DimensionType.withEffects("example:plain", "minecraft:overworld"), respawnInto(1).getDimensionType());
	}

	@Test
	void readsOnlyTheDimensionTypeRegistryAndRefusesATypeItDidNotList() {
		packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft", "dialog"), List.of(
				new RegistryEntry(Key.key("example", "menu"), NbtMap.EMPTY))));

		assertThrows(IllegalStateException.class, () -> respawnInto(0));
	}

	@Test
	void decodesReturningToConfigurationAndLeavesOtherPacketsToOtherTranslations() {
		assertEquals(new ClientboundStartConfiguration(), packets.decode(new ClientboundStartConfigurationPacket()));
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryConnectionPacket() {
		assertEquals(Set.of(ClientboundLogin.class, ClientboundRespawn.class, ClientboundStartConfiguration.class),
				packets.packets());
	}

	private ClientboundRespawn respawnInto(int type) {
		return (ClientboundRespawn) packets.decode(new ClientboundRespawnPacket(spawnIn(Key.key("minecraft", "overworld"),
				type, false, false), false, false));
	}

	/**
	 * The dimension types a vanilla server synchronizes, which the client knows from its own data pack, and a data
	 * pack's type with the end's effects.
	 */
	private static ClientboundRegistryDataPacket types() {
		return new ClientboundRegistryDataPacket(Key.key("minecraft", "dimension_type"), List.of(
				new RegistryEntry(Key.key("minecraft", "overworld"), null),
				new RegistryEntry(Key.key("minecraft", "the_nether"), null),
				new RegistryEntry(Key.key("example", "void"), NbtMap.builder().putString("effects", "minecraft:the_end").build())
		));
	}

	private static PlayerSpawnInfo spawnIn(Key level, int type, boolean debug, boolean flat) {
		return new PlayerSpawnInfo(type, level, 0, GameMode.SURVIVAL, GameMode.SURVIVAL, debug, flat, null, 0);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

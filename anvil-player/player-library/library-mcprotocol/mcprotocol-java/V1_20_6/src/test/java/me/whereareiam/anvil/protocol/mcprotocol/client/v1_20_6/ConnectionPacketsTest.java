package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
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
	private final ConnectionPackets packets = new ConnectionPackets(new ConnectionLevel());

	@Test
	void decodesJoiningALevelAndRespawningWithTheHeightOfTheDimensionTypeTheyName() {
		assertNull(packets.decode(dimensionTypes()));

		assertEquals(new ClientboundLogin(42, new Dimension("minecraft:overworld", -64, 384)),
				packets.decode(new ClientboundLoginPacket(42, false, new Key[0], 20, 10, 10, false, true, false,
						spawn(0, "minecraft:overworld"), false)));
		assertEquals(new ClientboundRespawn(new Dimension("minecraft:the_nether", 0, 256)),
				packets.decode(new ClientboundRespawnPacket(spawn(1, "minecraft:the_nether"), false, false)));
		assertEquals(new ClientboundStartConfiguration(), packets.decode(new ClientboundStartConfigurationPacket()));
	}

	@Test
	void refusesADimensionTypeTheRegistryDataDoesNotGive() {
		packets.decode(dimensionTypes());

		assertThrows(IllegalStateException.class,
				() -> packets.decode(new ClientboundRespawnPacket(spawn(2, "minecraft:the_end"), false, false)));
	}

	@Test
	void leavesTheRegistryDataAndPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundRegistryDataPacket(Key.key("minecraft:dialog"), List.of())));
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryConnectionPacket() {
		assertEquals(Set.of(ClientboundLogin.class, ClientboundRespawn.class, ClientboundStartConfiguration.class),
				packets.packets());
	}

	private static ClientboundRegistryDataPacket dimensionTypes() {
		return new ClientboundRegistryDataPacket(Key.key("minecraft:dimension_type"), List.of(
				new RegistryEntry(Key.key("minecraft:overworld"), type(-64, 384)),
				new RegistryEntry(Key.key("minecraft:the_nether"), type(0, 256))
		));
	}

	private static NbtMap type(int minY, int height) {
		return NbtMap.builder().putInt("min_y", minY).putInt("height", height).build();
	}

	private static PlayerSpawnInfo spawn(int type, String level) {
		return new PlayerSpawnInfo(type, Key.key(level), 0, GameMode.SURVIVAL, GameMode.SURVIVAL, false, false, null, 0);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

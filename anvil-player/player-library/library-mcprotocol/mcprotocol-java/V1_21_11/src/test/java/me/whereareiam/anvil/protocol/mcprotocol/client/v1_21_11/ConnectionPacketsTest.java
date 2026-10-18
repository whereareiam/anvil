package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ConnectionPackets;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets();

	@Test
	void decodesJoiningALevelRespawningAndReturningToConfiguration() {
		assertEquals(new ClientboundLogin(42), packets.decode(new ClientboundLoginPacket(42, false, new Key[0], 20, 10, 10,
				false, true, false, null, false)));
		assertEquals(new ClientboundRespawn(), packets.decode(new ClientboundRespawnPacket(null, false, false)));
		assertEquals(new ClientboundStartConfiguration(), packets.decode(new ClientboundStartConfigurationPacket()));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryConnectionPacket() {
		assertEquals(Set.of(ClientboundLogin.class, ClientboundRespawn.class, ClientboundStartConfiguration.class),
				packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

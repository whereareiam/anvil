package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundAcceptTeleportation;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerLoaded;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPacketsTest {
	private final PlayerPackets packets = new PlayerPackets();

	@Test
	void acceptsATeleportWithThePositionAndViewItLeftThePlayerAt() {
		assertEquals(new ServerboundAcceptTeleportationPacket(2, 12, 67, 16, 50, 30),
				packets.encode(ServerboundAcceptTeleportation.builder().id(2).position(new Vec3d(12, 67, 16)).yaw(50)
						.pitch(30).build()));
	}

	@Test
	void tellsThatThePlayerLoadedItsLevel() {
		assertSame(ServerboundPlayerLoadedPacket.INSTANCE, packets.encode(new ServerboundPlayerLoaded()));
		assertTrue(packets.packets().contains(ServerboundPlayerLoaded.class));
	}
}

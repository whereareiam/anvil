package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundInitializeBorder;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderCenter;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderWarningDistance;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundInitializeBorderPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderCenterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderLerpSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDelayPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDistancePacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BorderPacketsTest {
	private final BorderPackets packets = new BorderPackets();

	@Test
	void reportsTheWorldBorderWithItsMovementInTicks() {
		assertEquals(ClientboundInitializeBorder.builder()
				.centerX(1)
				.centerZ(2)
				.oldSize(100)
				.newSize(50)
				.lerpTime(200)
				.absoluteMaxSize(29999984)
				.warningBlocks(5)
				.warningTime(15)
				.build(), packets.decode(new ClientboundInitializeBorderPacket(1, 2, 100, 50, 10_000, 29999984, 5, 15)));
		assertEquals(new ClientboundSetBorderLerpSize(100, 50, 40), packets.decode(new ClientboundSetBorderLerpSizePacket(100, 50, 2_000)));
		assertEquals(new ClientboundSetBorderSize(64), packets.decode(new ClientboundSetBorderSizePacket(64)));
		assertEquals(new ClientboundSetBorderCenter(10, -20), packets.decode(new ClientboundSetBorderCenterPacket(10, -20)));
		assertEquals(new ClientboundSetBorderWarningDelay(30), packets.decode(new ClientboundSetBorderWarningDelayPacket(30)));
		assertEquals(new ClientboundSetBorderWarningDistance(8), packets.decode(new ClientboundSetBorderWarningDistancePacket(8)));
	}
}

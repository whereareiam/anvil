package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerWorldBorderPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundInitializeBorder;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderCenter;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.protocol.java.api.packet.world.border.ClientboundSetBorderWarningDistance;
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
				.build(), packets.decode(new ServerWorldBorderPacket(1, 2, 100, 50, 10_000, 29999984, 15, 5)));
		assertEquals(new ClientboundSetBorderLerpSize(100, 50, 40), packets.decode(new ServerWorldBorderPacket(100, 50, 2_000)));
		assertEquals(new ClientboundSetBorderSize(64), packets.decode(new ServerWorldBorderPacket(64)));
		assertEquals(new ClientboundSetBorderCenter(10, -20), packets.decode(new ServerWorldBorderPacket(10, -20)));
		assertEquals(new ClientboundSetBorderWarningDelay(30), packets.decode(new ServerWorldBorderPacket(true, 30)));
		assertEquals(new ClientboundSetBorderWarningDistance(8), packets.decode(new ServerWorldBorderPacket(false, 8)));
	}
}

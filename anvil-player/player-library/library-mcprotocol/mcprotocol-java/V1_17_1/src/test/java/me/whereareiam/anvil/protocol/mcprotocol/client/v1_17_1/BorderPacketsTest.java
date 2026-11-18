package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerInitializeBorderPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderCenterPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderLerpSizePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderSizePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderWarningDelayPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderWarningDistancePacket;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundInitializeBorder;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderCenter;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderSize;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.edition.java.protocol.packet.world.border.ClientboundSetBorderWarningDistance;
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
				.build(), packets.decode(new ServerInitializeBorderPacket(1, 2, 100, 50, 10_000, 29999984, 5, 15)));
		assertEquals(new ClientboundSetBorderLerpSize(100, 50, 40), packets.decode(new ServerSetBorderLerpSizePacket(100, 50, 2_000)));
		assertEquals(new ClientboundSetBorderSize(64), packets.decode(new ServerSetBorderSizePacket(64)));
		assertEquals(new ClientboundSetBorderCenter(10, -20), packets.decode(new ServerSetBorderCenterPacket(10, -20)));
		assertEquals(new ClientboundSetBorderWarningDelay(30), packets.decode(new ServerSetBorderWarningDelayPacket(30)));
		assertEquals(new ClientboundSetBorderWarningDistance(8), packets.decode(new ServerSetBorderWarningDistancePacket(8)));
	}
}

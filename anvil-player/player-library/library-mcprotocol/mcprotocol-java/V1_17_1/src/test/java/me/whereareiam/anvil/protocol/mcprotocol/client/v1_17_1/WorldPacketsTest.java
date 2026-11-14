package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.world.notify.ClientNotification;
import com.github.steveice10.mc.protocol.data.game.world.notify.RainStrengthValue;
import com.github.steveice10.mc.protocol.data.game.world.notify.ThunderStrengthValue;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerNotifyClientPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerSpawnPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUpdateTimePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerInitializeBorderPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderCenterPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderLerpSizePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderSizePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderWarningDelayPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.border.ServerSetBorderWarningDistancePacket;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundInitializeBorder;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderCenter;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderLerpSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderSize;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderWarningDelay;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetBorderWarningDistance;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetDefaultSpawnPosition;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldPacketsTest {
	private final WorldPackets packets = new WorldPackets();

	@Test
	void reportsANegativeTimeOfDayAsATimeThatStandsStill() {
		assertEquals(ClientboundSetTime.builder().gameTime(500).dayTime(6000L).tickDayTime(true).build(),
				packets.decode(new ServerUpdateTimePacket(500, 6000L)));
		assertEquals(ClientboundSetTime.builder().gameTime(500).dayTime(6000L).tickDayTime(false).build(),
				packets.decode(new ServerUpdateTimePacket(500, -6000L)));
	}

	@Test
	void reportsTheWeatherEventsByTheGamesIds() {
		assertEquals(new ClientboundGameEvent(GameEventType.START_RAINING, 0), packets.decode(new ServerNotifyClientPacket(ClientNotification.STOP_RAIN, new RainStrengthValue(0))));
		assertEquals(new ClientboundGameEvent(GameEventType.STOP_RAINING, 0), packets.decode(new ServerNotifyClientPacket(ClientNotification.START_RAIN, new RainStrengthValue(0))));
		assertEquals(new ClientboundGameEvent(GameEventType.RAIN_LEVEL_CHANGE, 0.5F), packets.decode(new ServerNotifyClientPacket(ClientNotification.RAIN_STRENGTH, new RainStrengthValue(0.5F))));
		assertEquals(new ClientboundGameEvent(GameEventType.THUNDER_LEVEL_CHANGE, 1), packets.decode(new ServerNotifyClientPacket(ClientNotification.THUNDER_STRENGTH, new ThunderStrengthValue(1))));
	}

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

	@Test
	void reportsTheSpawnPointWithItsAngleWithoutALevel() {
		assertEquals(ClientboundSetDefaultSpawnPosition.builder()
				.position(BlockPosition.builder().x(1).y(64).z(-3).build())
				.yaw(90)
				.build(), packets.decode(new ServerSpawnPositionPacket(new Position(1, 64, -3), 90)));
	}
}

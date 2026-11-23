package me.whereareiam.anvil.player.library.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.world.notify.ClientNotification;
import com.github.steveice10.mc.protocol.data.game.world.notify.RainStrengthValue;
import com.github.steveice10.mc.protocol.data.game.world.notify.ThunderStrengthValue;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerNotifyClientPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerSpawnPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerUpdateTimePacket;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetDefaultSpawnPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundSetTime;
import me.whereareiam.anvil.edition.java.protocol.type.world.GameEventType;
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
		assertEquals(new ClientboundGameEvent(GameEventType.START_RAINING, 0),
				packets.decode(new ServerNotifyClientPacket(ClientNotification.STOP_RAIN, new RainStrengthValue(0))));
		assertEquals(new ClientboundGameEvent(GameEventType.STOP_RAINING, 0),
				packets.decode(new ServerNotifyClientPacket(ClientNotification.START_RAIN, new RainStrengthValue(0))));
		assertEquals(new ClientboundGameEvent(GameEventType.RAIN_LEVEL_CHANGE, 0.5F),
				packets.decode(new ServerNotifyClientPacket(ClientNotification.RAIN_STRENGTH, new RainStrengthValue(0.5F))));
		assertEquals(new ClientboundGameEvent(GameEventType.THUNDER_LEVEL_CHANGE, 1),
				packets.decode(new ServerNotifyClientPacket(ClientNotification.THUNDER_STRENGTH, new ThunderStrengthValue(1))));
	}

	@Test
	void reportsTheSpawnPointWithItsAngleWithoutALevel() {
		assertEquals(ClientboundSetDefaultSpawnPosition.builder()
				.position(BlockPosition.builder().x(1).y(64).z(-3).build())
				.yaw(90)
				.build(), packets.decode(new ServerSpawnPositionPacket(new Position(1, 64, -3), 90)));
	}
}

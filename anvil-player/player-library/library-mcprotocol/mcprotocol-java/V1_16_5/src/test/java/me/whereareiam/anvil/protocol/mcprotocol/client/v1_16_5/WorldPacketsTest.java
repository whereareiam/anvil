package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.world.notify.ClientNotification;
import com.github.steveice10.mc.protocol.data.game.world.notify.DemoMessageValue;
import com.github.steveice10.mc.protocol.data.game.world.notify.EnterCreditsValue;
import com.github.steveice10.mc.protocol.data.game.world.notify.RainStrengthValue;
import com.github.steveice10.mc.protocol.data.game.world.notify.RespawnScreenValue;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerNotifyClientPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldPacketsTest {
	private final WorldPackets packets = new WorldPackets();

	@Test
	void decodesEachEventWithTheNumberTheGameSent() {
		assertEquals(new ClientboundGameEvent(GameEventType.IMMEDIATE_RESPAWN, 1F), packets.decode(
				new ServerNotifyClientPacket(ClientNotification.ENABLE_RESPAWN_SCREEN, RespawnScreenValue.IMMEDIATE_RESPAWN)));
		assertEquals(new ClientboundGameEvent(GameEventType.IMMEDIATE_RESPAWN, 0F), packets.decode(
				new ServerNotifyClientPacket(ClientNotification.ENABLE_RESPAWN_SCREEN, RespawnScreenValue.ENABLE_RESPAWN_SCREEN)));
		assertEquals(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 3F), packets.decode(
				new ServerNotifyClientPacket(ClientNotification.CHANGE_GAMEMODE, GameMode.SPECTATOR)));
		assertEquals(new ClientboundGameEvent(GameEventType.RAIN_LEVEL_CHANGE, 0.5F), packets.decode(
				new ServerNotifyClientPacket(ClientNotification.RAIN_STRENGTH, new RainStrengthValue(0.5F))));
		assertEquals(new ClientboundGameEvent(GameEventType.DEMO_EVENT, 102F), packets.decode(
				new ServerNotifyClientPacket(ClientNotification.DEMO_MESSAGE, DemoMessageValue.JUMP_CONTROL)));
		assertEquals(new ClientboundGameEvent(GameEventType.WIN_GAME, 1F), packets.decode(
				new ServerNotifyClientPacket(ClientNotification.ENTER_CREDITS, EnterCreditsValue.FIRST_TIME)));
	}

	@Test
	void carriesOnlyGameEvents() {
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
		assertEquals(Set.of(ClientboundGameEvent.class), packets.packets());
	}
}

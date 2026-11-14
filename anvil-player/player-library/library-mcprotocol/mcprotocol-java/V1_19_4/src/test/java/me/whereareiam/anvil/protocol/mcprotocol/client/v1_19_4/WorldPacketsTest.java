package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.level.notify.DemoMessageValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.GameEvent;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.WorldPackets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldPacketsTest {
	private final WorldPackets packets = new WorldPackets();

	@Test
	void decodesTheGameModesAndDemoMessagesOfThisRelease() {
		assertEquals(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 3F),
				packets.decode(new ClientboundGameEventPacket(GameEvent.CHANGE_GAMEMODE, GameMode.SPECTATOR)));
		assertEquals(new ClientboundGameEvent(GameEventType.DEMO_EVENT, 104F), packets.decode(
				new ClientboundGameEventPacket(GameEvent.DEMO_MESSAGE, DemoMessageValue.SCREENSHOT_CONTROL)));
	}
}

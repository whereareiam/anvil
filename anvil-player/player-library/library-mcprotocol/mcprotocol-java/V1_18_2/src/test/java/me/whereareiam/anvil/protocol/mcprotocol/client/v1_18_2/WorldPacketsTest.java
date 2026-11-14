package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.level.notify.DemoMessageValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.GameEvent;
import com.github.steveice10.mc.protocol.data.game.level.notify.RespawnScreenValue;
import com.github.steveice10.mc.protocol.data.game.level.notify.ThunderStrengthValue;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
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
				new ClientboundGameEventPacket(GameEvent.ENABLE_RESPAWN_SCREEN, RespawnScreenValue.IMMEDIATE_RESPAWN)));
		assertEquals(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 2F),
				packets.decode(new ClientboundGameEventPacket(GameEvent.CHANGE_GAMEMODE, GameMode.ADVENTURE)));
		assertEquals(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, -1F),
				packets.decode(new ClientboundGameEventPacket(GameEvent.CHANGE_GAMEMODE, GameMode.UNKNOWN)));
		assertEquals(new ClientboundGameEvent(GameEventType.THUNDER_LEVEL_CHANGE, 0.25F), packets.decode(
				new ClientboundGameEventPacket(GameEvent.THUNDER_STRENGTH, new ThunderStrengthValue(0.25F))));
		assertEquals(new ClientboundGameEvent(GameEventType.DEMO_EVENT, 0F),
				packets.decode(new ClientboundGameEventPacket(GameEvent.DEMO_MESSAGE, DemoMessageValue.WELCOME)));
		assertEquals(new ClientboundGameEvent(GameEventType.NO_RESPAWN_BLOCK_AVAILABLE, 0F),
				packets.decode(new ClientboundGameEventPacket(GameEvent.INVALID_BED, null)));
	}

	@Test
	void carriesOnlyGameEvents() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertEquals(Set.of(ClientboundGameEvent.class), packets.packets());
	}
}

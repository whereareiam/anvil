package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.GameEventType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.DemoMessageValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.ElderGuardianEffectValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.LimitedCraftingValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RainStrengthValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RespawnScreenValue;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
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
		assertEquals(new ClientboundGameEvent(GameEventType.IMMEDIATE_RESPAWN, 0F), packets.decode(
				new ClientboundGameEventPacket(GameEvent.ENABLE_RESPAWN_SCREEN, RespawnScreenValue.ENABLE_RESPAWN_SCREEN)));
		assertEquals(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, 1F),
				packets.decode(new ClientboundGameEventPacket(GameEvent.CHANGE_GAMEMODE, GameMode.CREATIVE)));
		assertEquals(new ClientboundGameEvent(GameEventType.RAIN_LEVEL_CHANGE, 0.75F),
				packets.decode(new ClientboundGameEventPacket(GameEvent.RAIN_STRENGTH, new RainStrengthValue(0.75F))));
		assertEquals(new ClientboundGameEvent(GameEventType.GUARDIAN_ELDER_EFFECT, 1F), packets.decode(
				new ClientboundGameEventPacket(GameEvent.AFFECTED_BY_ELDER_GUARDIAN, new ElderGuardianEffectValue(1F))));
		assertEquals(new ClientboundGameEvent(GameEventType.LIMITED_CRAFTING, 1F), packets.decode(
				new ClientboundGameEventPacket(GameEvent.LIMITED_CRAFTING, LimitedCraftingValue.LIMITED_CRAFTING)));
		assertEquals(new ClientboundGameEvent(GameEventType.DEMO_EVENT, 104F), packets.decode(
				new ClientboundGameEventPacket(GameEvent.DEMO_MESSAGE, DemoMessageValue.SCREENSHOT_CONTROL)));
		assertEquals(new ClientboundGameEvent(GameEventType.LEVEL_CHUNKS_LOAD_START, 0F),
				packets.decode(new ClientboundGameEventPacket(GameEvent.LEVEL_CHUNKS_LOAD_START, null)));
	}

	@Test
	void carriesOnlyGameEvents() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertEquals(Set.of(ClientboundGameEvent.class), packets.packets());
	}
}

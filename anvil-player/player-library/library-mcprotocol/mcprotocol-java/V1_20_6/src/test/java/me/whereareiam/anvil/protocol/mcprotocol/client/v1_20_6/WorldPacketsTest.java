package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

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
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.DemoMessageValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.ElderGuardianEffectValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.LimitedCraftingValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RainStrengthValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RespawnScreenValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.ThunderStrengthValue;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundInitializeBorderPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderCenterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderLerpSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderSizePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDelayPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.border.ClientboundSetBorderWarningDistancePacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorldPacketsTest {
	private final WorldPackets packets = new WorldPackets();

	@Test
	void reportsANegativeTimeOfDayAsATimeThatStandsStill() {
		assertEquals(ClientboundSetTime.builder().gameTime(500).dayTime(6000L).tickDayTime(true).build(),
				packets.decode(new ClientboundSetTimePacket(500, 6000L)));
		assertEquals(ClientboundSetTime.builder().gameTime(500).dayTime(6000L).tickDayTime(false).build(),
				packets.decode(new ClientboundSetTimePacket(500, -6000L)));
	}

	@Test
	void reportsTheWeatherEvents() {
		assertEquals(new ClientboundGameEvent(GameEventType.START_RAINING, 0), packets.decode(new ClientboundGameEventPacket(GameEvent.START_RAIN, null)));
		assertEquals(new ClientboundGameEvent(GameEventType.STOP_RAINING, 0), packets.decode(new ClientboundGameEventPacket(GameEvent.STOP_RAIN, null)));
		assertEquals(new ClientboundGameEvent(GameEventType.RAIN_LEVEL_CHANGE, 0.5F), packets.decode(new ClientboundGameEventPacket(GameEvent.RAIN_STRENGTH, new RainStrengthValue(0.5F))));
		assertEquals(new ClientboundGameEvent(GameEventType.THUNDER_LEVEL_CHANGE, 1), packets.decode(new ClientboundGameEventPacket(GameEvent.THUNDER_STRENGTH, new ThunderStrengthValue(1))));
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
				.build(), packets.decode(new ClientboundInitializeBorderPacket(1, 2, 100, 50, 10_000, 29999984, 5, 15)));
		assertEquals(new ClientboundSetBorderLerpSize(100, 50, 40), packets.decode(new ClientboundSetBorderLerpSizePacket(100, 50, 2_000)));
		assertEquals(new ClientboundSetBorderSize(64), packets.decode(new ClientboundSetBorderSizePacket(64)));
		assertEquals(new ClientboundSetBorderCenter(10, -20), packets.decode(new ClientboundSetBorderCenterPacket(10, -20)));
		assertEquals(new ClientboundSetBorderWarningDelay(30), packets.decode(new ClientboundSetBorderWarningDelayPacket(30)));
		assertEquals(new ClientboundSetBorderWarningDistance(8), packets.decode(new ClientboundSetBorderWarningDistancePacket(8)));
	}

	@Test
	void reportsTheSpawnPointWithItsAngleWithoutALevel() {
		assertEquals(ClientboundSetDefaultSpawnPosition.builder()
				.position(BlockPosition.builder().x(1).y(64).z(-3).build())
				.yaw(90)
				.build(), packets.decode(new ClientboundSetDefaultSpawnPositionPacket(Vector3i.from(1, 64, -3), 90)));
	}

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
	void ignoresOtherPackets() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
	}
}

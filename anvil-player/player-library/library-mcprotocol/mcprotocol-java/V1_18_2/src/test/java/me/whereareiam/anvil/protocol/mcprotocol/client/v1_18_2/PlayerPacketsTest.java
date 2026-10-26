package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerState;
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.type.PlayerCommandAction;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPacketsTest {
	private final PlayerPackets packets = new PlayerPackets();

	@Test
	void encodesThePositionAndRotationAsOnePacketWithoutHorizontalCollision() {
		Object encoded = packets.encode(move(false, true));

		ServerboundMovePlayerPosRotPacket packet = assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, encoded);
		assertFalse(packet.isOnGround());
		assertEquals(1.5, packet.getX());
		assertEquals(64, packet.getY());
		assertEquals(-2.5, packet.getZ());
		assertEquals(90, packet.getYaw());
		assertEquals(-15, packet.getPitch());
	}

	@Test
	void encodesTheGroundState() {
		Object encoded = packets.encode(move(true, false));

		assertTrue(assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, encoded).isOnGround());
	}

	@Test
	void decodesThePlayersPlacementKeepingTheVelocityAlongEachRelativeCoordinate() {
		ClientboundPlayerPositionPacket position = new ClientboundPlayerPositionPacket(10, 64, -10, 90, 5, 1, false,
				PositionElement.X, PositionElement.PITCH);

		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(10, 64, -10)).yaw(90).pitch(5)
				.relatives(Set.of(Relative.X, Relative.DELTA_X, Relative.PITCH)).build(), packets.decode(position));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void encodesThePositionTheRotationAndTheGroundStateAloneWithoutHorizontalCollision() {
		ServerboundMovePlayerPosPacket position = assertInstanceOf(ServerboundMovePlayerPosPacket.class,
				packets.encode(ServerboundMovePlayerPos.builder().position(new Vec3d(1, 2, 3)).onGround(true)
						.horizontalCollision(true).build()));
		ServerboundMovePlayerRotPacket rotation = assertInstanceOf(ServerboundMovePlayerRotPacket.class,
				packets.encode(ServerboundMovePlayerRot.builder().yaw(45).pitch(10).build()));
		ServerboundMovePlayerStatusOnlyPacket status = assertInstanceOf(ServerboundMovePlayerStatusOnlyPacket.class,
				packets.encode(ServerboundMovePlayerStatusOnly.builder().onGround(true).build()));

		assertEquals(List.of(1.0, 2.0, 3.0), List.of(position.getX(), position.getY(), position.getZ()));
		assertTrue(position.isOnGround());
		assertEquals(45, rotation.getYaw());
		assertEquals(10, rotation.getPitch());
		assertFalse(rotation.isOnGround());
		assertTrue(status.isOnGround());
	}

	@Test
	void encodesSprintingAndSneakingAsPlayerStates() {
		ServerboundPlayerCommandPacket sprint = assertInstanceOf(ServerboundPlayerCommandPacket.class,
				packets.encode(new ServerboundPlayerCommand(7, PlayerCommandAction.START_SPRINTING)));
		ServerboundPlayerCommandPacket sneak = assertInstanceOf(ServerboundPlayerCommandPacket.class,
				packets.encode(new ServerboundPlayerCommand(7, PlayerCommandAction.PRESS_SHIFT_KEY)));

		assertEquals(7, sprint.getEntityId());
		assertEquals(PlayerState.START_SPRINTING, sprint.getState());
		assertEquals(PlayerState.START_SNEAKING, sneak.getState());
		assertEquals(PlayerState.STOP_SNEAKING, assertInstanceOf(ServerboundPlayerCommandPacket.class,
				packets.encode(new ServerboundPlayerCommand(7, PlayerCommandAction.RELEASE_SHIFT_KEY))).getState());
		assertEquals(PlayerState.STOP_SPRINTING, assertInstanceOf(ServerboundPlayerCommandPacket.class,
				packets.encode(new ServerboundPlayerCommand(7, PlayerCommandAction.STOP_SPRINTING))).getState());
	}

	@Test
	void carriesThePlacementAndMovementPackets() {
		assertEquals(Set.of(ClientboundPlayerPosition.class, ServerboundMovePlayerPos.class,
				ServerboundMovePlayerPosRot.class, ServerboundMovePlayerRot.class, ServerboundMovePlayerStatusOnly.class,
				ServerboundPlayerCommand.class), packets.packets());
	}

	private static ServerboundMovePlayerPosRot move(boolean onGround, boolean horizontalCollision) {
		return ServerboundMovePlayerPosRot.builder()
				.position(new Vec3d(1.5, 64, -2.5))
				.yaw(90)
				.pitch(-15)
				.onGround(onGround)
				.horizontalCollision(horizontalCollision)
				.build();
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

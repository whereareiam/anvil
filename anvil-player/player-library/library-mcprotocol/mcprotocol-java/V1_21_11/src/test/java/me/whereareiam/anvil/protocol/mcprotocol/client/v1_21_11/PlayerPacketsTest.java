package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.protocol.java.api.type.PlayerCommandAction;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPacketsTest {
	private final PlayerPackets packets = new PlayerPackets();

	@Test
	void encodesThePositionRotationAndBothFlagsAsOnePacket() {
		Object encoded = packets.encode(move(true, false));

		ServerboundMovePlayerPosRotPacket packet = assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, encoded);
		assertTrue(packet.isOnGround());
		assertFalse(packet.isHorizontalCollision());
		assertEquals(1.5, packet.getX());
		assertEquals(64, packet.getY());
		assertEquals(-2.5, packet.getZ());
		assertEquals(90, packet.getYaw());
		assertEquals(-15, packet.getPitch());
	}

	@Test
	void encodesHorizontalCollision() {
		Object encoded = packets.encode(move(false, true));

		ServerboundMovePlayerPosRotPacket packet = assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, encoded);
		assertFalse(packet.isOnGround());
		assertTrue(packet.isHorizontalCollision());
	}

	@Test
	void decodesThePlayersPlacementWithItsVelocityAndEverythingItMakesRelative() {
		ClientboundPlayerPositionPacket position = new ClientboundPlayerPositionPacket(1, Vector3d.from(10, 64, -10),
				Vector3d.from(0.5, -0.08, 0), 90, 5, List.of(PositionElement.X, PositionElement.X_ROT,
				PositionElement.DELTA_Y, PositionElement.ROTATE_DELTA));

		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(10, 64, -10))
				.deltaMovement(new Vec3d(0.5, -0.08, 0)).yaw(90).pitch(5)
				.relatives(Set.of(Relative.X, Relative.PITCH, Relative.DELTA_Y, Relative.ROTATE_DELTA)).build(),
				packets.decode(position));
	}

	@Test
	void encodesThePositionTheRotationAndTheGroundStateAloneWithHorizontalCollision() {
		ServerboundMovePlayerPosPacket position = assertInstanceOf(ServerboundMovePlayerPosPacket.class,
				packets.encode(ServerboundMovePlayerPos.builder().position(new Vec3d(1, 2, 3)).onGround(true)
						.horizontalCollision(true).build()));
		ServerboundMovePlayerRotPacket rotation = assertInstanceOf(ServerboundMovePlayerRotPacket.class,
				packets.encode(ServerboundMovePlayerRot.builder().yaw(45).pitch(10).horizontalCollision(true).build()));
		ServerboundMovePlayerStatusOnlyPacket status = assertInstanceOf(ServerboundMovePlayerStatusOnlyPacket.class,
				packets.encode(ServerboundMovePlayerStatusOnly.builder().onGround(true).build()));

		assertEquals(List.of(1.0, 2.0, 3.0), List.of(position.getX(), position.getY(), position.getZ()));
		assertTrue(position.isOnGround());
		assertTrue(position.isHorizontalCollision());
		assertEquals(45, rotation.getYaw());
		assertEquals(10, rotation.getPitch());
		assertTrue(rotation.isHorizontalCollision());
		assertTrue(status.isOnGround());
		assertFalse(status.isHorizontalCollision());
	}

	@Test
	void encodesTheKeysThePlayerHoldsAndTheEndOfItsTick() {
		ServerboundPlayerInputPacket input = assertInstanceOf(ServerboundPlayerInputPacket.class,
				packets.encode(ServerboundPlayerInput.builder().forward(true).jump(true).sprint(true).build()));

		assertTrue(input.isForward());
		assertFalse(input.isBackward());
		assertFalse(input.isLeft());
		assertFalse(input.isRight());
		assertTrue(input.isJump());
		assertFalse(input.isShift());
		assertTrue(input.isSprint());
		assertSame(ServerboundClientTickEndPacket.INSTANCE, packets.encode(new ServerboundClientTickEnd()));
	}

	@Test
	void encodesSprintingAsAPlayerStateAndRefusesSneakingThroughIt() {
		ServerboundPlayerCommandPacket sprint = assertInstanceOf(ServerboundPlayerCommandPacket.class,
				packets.encode(new ServerboundPlayerCommand(7, PlayerCommandAction.START_SPRINTING)));

		assertEquals(7, sprint.getEntityId());
		assertEquals(PlayerState.START_SPRINTING, sprint.getState());
		assertEquals(PlayerState.STOP_SPRINTING, assertInstanceOf(ServerboundPlayerCommandPacket.class,
				packets.encode(new ServerboundPlayerCommand(7, PlayerCommandAction.STOP_SPRINTING))).getState());
		assertThrows(ProtocolUnavailableException.class,
				() -> packets.encode(new ServerboundPlayerCommand(7, PlayerCommandAction.PRESS_SHIFT_KEY)));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesThePlacementMovementInputAndTickPackets() {
		assertEquals(Set.of(ClientboundPlayerPosition.class, ServerboundClientTickEnd.class,
				ServerboundMovePlayerPos.class, ServerboundMovePlayerPosRot.class, ServerboundMovePlayerRot.class,
				ServerboundMovePlayerStatusOnly.class, ServerboundPlayerCommand.class, ServerboundPlayerInput.class),
				packets.packets());
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

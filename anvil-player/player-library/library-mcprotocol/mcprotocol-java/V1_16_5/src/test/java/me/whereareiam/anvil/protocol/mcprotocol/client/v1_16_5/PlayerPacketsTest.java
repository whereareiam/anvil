package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerState;
import com.github.steveice10.mc.protocol.data.game.entity.player.PositionElement;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerMovementPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerStatePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket;
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
		ClientPlayerPositionRotationPacket packet = assertInstanceOf(ClientPlayerPositionRotationPacket.class,
				packets.encode(ServerboundMovePlayerPosRot.builder().position(new Vec3d(1.5, 64, -2.5)).yaw(90)
						.pitch(-15).onGround(true).horizontalCollision(true).build()));

		assertTrue(packet.isOnGround());
		assertEquals(List.of(1.5, 64.0, -2.5), List.of(packet.getX(), packet.getY(), packet.getZ()));
		assertEquals(90, packet.getYaw());
		assertEquals(-15, packet.getPitch());
	}

	@Test
	void encodesThePositionTheRotationAndTheGroundStateAlone() {
		ClientPlayerPositionPacket position = assertInstanceOf(ClientPlayerPositionPacket.class,
				packets.encode(ServerboundMovePlayerPos.builder().position(new Vec3d(1, 2, 3)).onGround(true).build()));
		ClientPlayerRotationPacket rotation = assertInstanceOf(ClientPlayerRotationPacket.class,
				packets.encode(ServerboundMovePlayerRot.builder().yaw(45).pitch(10).build()));
		ClientPlayerMovementPacket status = assertInstanceOf(ClientPlayerMovementPacket.class,
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
		ClientPlayerStatePacket sprint = assertInstanceOf(ClientPlayerStatePacket.class,
				packets.encode(new ServerboundPlayerCommand(7, PlayerCommandAction.START_SPRINTING)));

		assertEquals(7, sprint.getEntityId());
		assertEquals(PlayerState.START_SPRINTING, sprint.getState());
		assertEquals(PlayerState.START_SNEAKING, assertInstanceOf(ClientPlayerStatePacket.class,
				packets.encode(new ServerboundPlayerCommand(7, PlayerCommandAction.PRESS_SHIFT_KEY))).getState());
	}

	@Test
	void decodesThePlayersPlacementKeepingTheVelocityAlongEachRelativeCoordinate() {
		ServerPlayerPositionRotationPacket position = new ServerPlayerPositionRotationPacket(10, 64, -10, 90, 5, 1,
				PositionElement.Z, PositionElement.YAW);

		assertEquals(ClientboundPlayerPosition.builder().position(new Vec3d(10, 64, -10)).yaw(90).pitch(5)
				.relatives(Set.of(Relative.Z, Relative.DELTA_Z, Relative.YAW)).build(), packets.decode(position));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesThePlacementAndMovementPackets() {
		assertEquals(Set.of(ClientboundPlayerPosition.class, ServerboundMovePlayerPos.class,
				ServerboundMovePlayerPosRot.class, ServerboundMovePlayerRot.class, ServerboundMovePlayerStatusOnly.class,
				ServerboundPlayerCommand.class), packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.player.Position;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
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
	void decodesThePlayersPlacementWithItsRelativeCoordinatesAndAnglesButNotItsVelocity() {
		ClientboundPlayerPositionPacket position = new ClientboundPlayerPositionPacket(1, Vector3d.from(10, 64, -10),
				Vector3d.ZERO, 90, 5, List.of(PositionElement.X, PositionElement.X_ROT, PositionElement.DELTA_Y));

		assertEquals(ClientboundPlayerPosition.builder().x(10).y(64).z(-10).yaw(90).pitch(5)
				.relatives(Set.of(Relative.X, Relative.PITCH)).build(), packets.decode(position));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesThePlacementAndMovementPackets() {
		assertEquals(Set.of(ClientboundPlayerPosition.class, ServerboundMovePlayerPosRot.class), packets.packets());
	}

	private static ServerboundMovePlayerPosRot move(boolean onGround, boolean horizontalCollision) {
		return ServerboundMovePlayerPosRot.builder()
				.position(Position.builder().x(1.5).y(64).z(-2.5).build())
				.yaw(90)
				.pitch(-15)
				.onGround(onGround)
				.horizontalCollision(horizontalCollision)
				.build();
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import me.whereareiam.anvil.protocol.java.api.model.player.Position;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import org.junit.jupiter.api.Test;

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
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesTheMovementPacket() {
		assertEquals(Set.of(ServerboundMovePlayerPosRot.class), packets.packets());
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

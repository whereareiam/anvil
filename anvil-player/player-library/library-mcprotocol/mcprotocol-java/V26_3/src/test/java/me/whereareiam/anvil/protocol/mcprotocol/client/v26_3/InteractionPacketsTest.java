package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundPunch;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.java.api.type.Direction;
import me.whereareiam.anvil.protocol.java.api.type.InteractAction;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPunchPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand.MAIN_HAND;
import static org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand.OFF_HAND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionPacketsTest {
	private final InteractionPackets packets = new InteractionPackets();

	@Test
	void carriesThePunchAndNoSwing() {
		assertEquals(Set.of(
				ServerboundUseItem.class,
				ServerboundUseItemOn.class,
				ServerboundInteract.class,
				ServerboundAttack.class,
				ServerboundPunch.class
		), packets.packets());
		assertNull(packets.encode(new ServerboundSwing(InteractionHand.MAIN_HAND)));
	}

	@Test
	void attacksInThePacketOfItsOwnAndPunchesWithoutAHand() {
		assertEquals(43, assertInstanceOf(ServerboundAttackPacket.class, packets.encode(new ServerboundAttack(43)))
				.getEntityId());
		assertEquals(ServerboundPunchPacket.INSTANCE, packets.encode(new ServerboundPunch()));
	}

	@Test
	void usesTheItemOfTheHandWithTheSequenceAndTheView() {
		ServerboundUseItemPacket packet = assertInstanceOf(ServerboundUseItemPacket.class, packets.encode(
				ServerboundUseItem.builder().hand(InteractionHand.OFF_HAND).sequence(7).yaw(90).pitch(-15).build()));

		assertEquals(OFF_HAND, packet.getHand());
		assertEquals(7, packet.getSequence());
		assertEquals(90, packet.getYRot());
		assertEquals(-15, packet.getXRot());
	}

	@Test
	void usesTheItemOnABlockFaceWithTheSequence() {
		ServerboundUseItemOnPacket packet = assertInstanceOf(ServerboundUseItemOnPacket.class, packets.encode(
				ServerboundUseItemOn.builder()
						.hand(InteractionHand.MAIN_HAND)
						.position(BlockPosition.builder().x(4).y(-60).z(-7).build())
						.face(Direction.WEST)
						.cursorX(0.5F)
						.cursorY(0.25F)
						.cursorZ(0.75F)
						.insideBlock(true)
						.sequence(7)
						.build()));

		assertEquals(-60, packet.getPosition().getY());
		assertEquals("WEST", packet.getFace().name());
		assertEquals(MAIN_HAND, packet.getHand());
		assertEquals(0.25F, packet.getCursorY());
		assertTrue(packet.isInsideBlock());
		assertEquals(7, packet.getSequence());
	}

	@Test
	void interactsWithAnEntityAtItsPositionWithTheHand() {
		ServerboundInteractPacket packet = assertInstanceOf(ServerboundInteractPacket.class, packets.encode(
				ServerboundInteract.builder().entityId(42).action(InteractAction.INTERACT).hand(InteractionHand.OFF_HAND)
						.build()));

		assertEquals(42, packet.getEntityId());
		assertEquals(OFF_HAND, packet.getHand());
		assertEquals(Vector3d.ZERO, packet.getLocation());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

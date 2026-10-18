package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.github.steveice10.mc.protocol.data.game.entity.player.Hand.MAIN_HAND;
import static com.github.steveice10.mc.protocol.data.game.entity.player.Hand.OFF_HAND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionPacketsTest {
	private final InteractionPackets packets = new InteractionPackets();

	@Test
	void usesTheItemOfTheHandWithTheSequenceButNoView() {
		Object encoded = packets.encode(ServerboundUseItem.builder()
				.hand(InteractionHand.OFF_HAND)
				.sequence(7)
				.yaw(90)
				.pitch(-15)
				.build());

		ServerboundUseItemPacket packet = assertInstanceOf(ServerboundUseItemPacket.class, encoded);
		assertEquals(OFF_HAND, packet.getHand());
		assertEquals(7, packet.getSequence());
	}

	@ParameterizedTest
	@EnumSource(me.whereareiam.anvil.protocol.java.api.type.Direction.class)
	void usesTheItemOnEachBlockFaceWithTheSequence(me.whereareiam.anvil.protocol.java.api.type.Direction face) {
		Object encoded = packets.encode(ServerboundUseItemOn.builder()
				.hand(InteractionHand.MAIN_HAND)
				.position(BlockPosition.builder().x(4).y(-60).z(-7).build())
				.face(face)
				.cursorX(0.5F)
				.cursorY(0.25F)
				.cursorZ(0.75F)
				.insideBlock(true)
				.sequence(7)
				.build());

		ServerboundUseItemOnPacket packet = assertInstanceOf(ServerboundUseItemOnPacket.class, encoded);
		assertEquals(4, packet.getPosition().getX());
		assertEquals(-60, packet.getPosition().getY());
		assertEquals(-7, packet.getPosition().getZ());
		assertEquals(face.name(), packet.getFace().name());
		assertEquals(MAIN_HAND, packet.getHand());
		assertEquals(0.5F, packet.getCursorX());
		assertEquals(0.25F, packet.getCursorY());
		assertEquals(0.75F, packet.getCursorZ());
		assertTrue(packet.isInsideBlock());
		assertEquals(7, packet.getSequence());
	}

	@Test
	void attacksThroughTheSharedEntityPacket() {
		ServerboundInteractPacket attack = assertInstanceOf(ServerboundInteractPacket.class,
				packets.encode(new ServerboundAttack(43)));

		assertEquals(43, attack.getEntityId());
		assertEquals(InteractAction.ATTACK, attack.getAction());
	}
}

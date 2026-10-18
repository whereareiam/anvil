package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.java.api.type.Direction;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand.MAIN_HAND;
import static org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand.OFF_HAND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionPacketsTest {
	private final InteractionPackets packets = new InteractionPackets();

	@Test
	void swingsTheArmOfEachHand() {
		assertEquals(MAIN_HAND, assertInstanceOf(ServerboundSwingPacket.class,
				packets.encode(new ServerboundSwing(InteractionHand.MAIN_HAND))).getHand());
		assertEquals(OFF_HAND, assertInstanceOf(ServerboundSwingPacket.class,
				packets.encode(new ServerboundSwing(InteractionHand.OFF_HAND))).getHand());
	}

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
	@EnumSource(Direction.class)
	void usesTheItemOnEachBlockFaceWithTheSequence(Direction face) {
		Object encoded = packets.encode(useItemOn(face));

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
	void interactsWithAndAttacksEntitiesInTheSharedEntityPacket() {
		ServerboundInteractPacket interact = assertInstanceOf(ServerboundInteractPacket.class, packets.encode(ServerboundInteract.builder()
				.entityId(42)
				.action(me.whereareiam.anvil.protocol.java.api.type.InteractAction.INTERACT)
				.hand(InteractionHand.OFF_HAND)
				.build()));
		assertEquals(42, interact.getEntityId());
		assertEquals(InteractAction.INTERACT, interact.getAction());
		assertEquals(OFF_HAND, interact.getHand());
		assertFalse(interact.isSneaking());

		ServerboundInteractPacket attack = assertInstanceOf(ServerboundInteractPacket.class,
				packets.encode(new ServerboundAttack(43)));
		assertEquals(43, attack.getEntityId());
		assertEquals(InteractAction.ATTACK, attack.getAction());
		assertFalse(attack.isSneaking());
	}

	@Test
	void interactsAtAPointOfTheEntity() {
		ServerboundInteractPacket packet = assertInstanceOf(ServerboundInteractPacket.class, packets.encode(ServerboundInteract.builder()
				.entityId(42)
				.action(me.whereareiam.anvil.protocol.java.api.type.InteractAction.INTERACT_AT)
				.hand(InteractionHand.MAIN_HAND)
				.point(new Vec3d(0.25, 1.5, -0.25))
				.build()));

		assertEquals(InteractAction.INTERACT_AT, packet.getAction());
		assertEquals(0.25F, packet.getTargetX());
		assertEquals(1.5F, packet.getTargetY());
		assertEquals(-0.25F, packet.getTargetZ());
		assertEquals(MAIN_HAND, packet.getHand());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesTheInteractionPacketsWithTheAttack() {
		assertEquals(Set.of(
				ServerboundSwing.class,
				ServerboundUseItem.class,
				ServerboundUseItemOn.class,
				ServerboundInteract.class,
				ServerboundAttack.class
		), packets.packets());
	}

	private static ServerboundUseItemOn useItemOn(Direction face) {
		return ServerboundUseItemOn.builder()
				.hand(InteractionHand.MAIN_HAND)
				.position(BlockPosition.builder().x(4).y(-60).z(-7).build())
				.face(face)
				.cursorX(0.5F)
				.cursorY(0.25F)
				.cursorZ(0.75F)
				.insideBlock(true)
				.sequence(7)
				.build();
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

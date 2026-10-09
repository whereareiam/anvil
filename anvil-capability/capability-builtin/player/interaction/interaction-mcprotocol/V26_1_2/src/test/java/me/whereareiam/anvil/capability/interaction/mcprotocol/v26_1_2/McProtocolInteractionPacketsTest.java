package me.whereareiam.anvil.capability.interaction.mcprotocol.v26_1_2;

import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.packet.InteractionPackets;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.cloudburstmc.math.vector.Vector3d;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand.MAIN_HAND;
import static org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand.OFF_HAND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class McProtocolInteractionPacketsTest {
	private static final BlockPosition POSITION = BlockPosition.builder().x(4).y(-60).z(-7).build();

	private final List<Object> sent = new ArrayList<>();
	private final Session session = session(sent);
	private final McProtocolInteractionPackets packets = new McProtocolInteractionPackets();

	@Test
	void swingsTheArmOfEachHand() {
		packets.swing(session, Hand.MAIN);
		packets.swing(session, Hand.OFF);

		assertEquals(2, sent.size());
		assertEquals(MAIN_HAND, assertInstanceOf(ServerboundSwingPacket.class, sent.get(0)).getHand());
		assertEquals(OFF_HAND, assertInstanceOf(ServerboundSwingPacket.class, sent.get(1)).getHand());
	}

	@Test
	void usesTheItemOfTheHandWithTheSequenceAndTheView() {
		packets.useItem(session, Hand.OFF, 7, 90, -15);

		assertEquals(1, sent.size());
		ServerboundUseItemPacket packet = assertInstanceOf(ServerboundUseItemPacket.class, sent.getFirst());
		assertEquals(OFF_HAND, packet.getHand());
		assertEquals(7, packet.getSequence());
		assertEquals(90, packet.getYRot());
		assertEquals(-15, packet.getXRot());
	}

	@ParameterizedTest
	@EnumSource(BlockFace.class)
	void usesTheItemOnTheCenterOfEachBlockFace(BlockFace face) {
		packets.useItemOn(session, POSITION, face, Hand.MAIN, 7);

		assertEquals(1, sent.size());
		ServerboundUseItemOnPacket packet = assertInstanceOf(ServerboundUseItemOnPacket.class, sent.getFirst());
		assertEquals(4, packet.getPosition().getX());
		assertEquals(-60, packet.getPosition().getY());
		assertEquals(-7, packet.getPosition().getZ());
		assertEquals(face.name(), packet.getFace().name());
		assertEquals(MAIN_HAND, packet.getHand());
		assertEquals(0.5F, packet.getCursorX());
		assertEquals(0.5F, packet.getCursorY());
		assertEquals(0.5F, packet.getCursorZ());
		assertFalse(packet.isInsideBlock());
		assertFalse(packet.isHitWorldBorder());
		assertEquals(7, packet.getSequence());
	}

	@Test
	void interactsWithAnEntityAtItsOriginWithTheHand() {
		packets.entity(session, 42, EntityInteraction.INTERACT, Hand.OFF);

		assertEquals(1, sent.size());
		ServerboundInteractPacket packet = assertInstanceOf(ServerboundInteractPacket.class, sent.getFirst());
		assertEquals(42, packet.getEntityId());
		assertEquals(OFF_HAND, packet.getHand());
		assertEquals(Vector3d.ZERO, packet.getLocation());
		assertFalse(packet.isSneaking());
	}

	@Test
	void attacksAnEntityInThePacketOfItsOwn() {
		packets.entity(session, 43, EntityInteraction.ATTACK, Hand.OFF);

		assertEquals(1, sent.size());
		assertEquals(43, assertInstanceOf(ServerboundAttackPacket.class, sent.getFirst()).getEntityId());
	}

	@Test
	void isTheOnlyInteractionPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(InteractionPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolInteractionPackets.class), ports);
		assertEquals(Session.class, packets.sessionType());
	}

	private static Session session(List<Object> sent) {
		return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
				(proxy, method, arguments) -> {
					if (!method.getName().equals("send")) throw new UnsupportedOperationException(method.getName());

					sent.add(arguments[0]);
					return null;
				});
	}
}

package me.whereareiam.anvil.capability.interaction.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import com.github.steveice10.packetlib.Session;
import me.whereareiam.anvil.capability.interaction.model.BlockPosition;
import me.whereareiam.anvil.capability.interaction.packet.InteractionPackets;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static com.github.steveice10.mc.protocol.data.game.entity.player.Hand.MAIN_HAND;
import static com.github.steveice10.mc.protocol.data.game.entity.player.Hand.OFF_HAND;
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
	void usesTheItemOfTheHandWithoutSequenceOrView() {
		packets.useItem(session, Hand.OFF, 7, 90, -15);

		assertEquals(1, sent.size());
		assertEquals(OFF_HAND, assertInstanceOf(ServerboundUseItemPacket.class, sent.getFirst()).getHand());
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
	}

	@Test
	void interactsWithAndAttacksEntitiesInTheSharedEntityPacket() {
		packets.entity(session, 42, EntityInteraction.INTERACT, Hand.OFF);
		packets.entity(session, 43, EntityInteraction.ATTACK, Hand.MAIN);

		assertEquals(2, sent.size());
		ServerboundInteractPacket interact = assertInstanceOf(ServerboundInteractPacket.class, sent.get(0));
		assertEquals(42, interact.getEntityId());
		assertEquals(InteractAction.INTERACT, interact.getAction());
		assertEquals(OFF_HAND, interact.getHand());
		assertFalse(interact.isSneaking());
		ServerboundInteractPacket attack = assertInstanceOf(ServerboundInteractPacket.class, sent.get(1));
		assertEquals(43, attack.getEntityId());
		assertEquals(InteractAction.ATTACK, attack.getAction());
		assertFalse(attack.isSneaking());
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

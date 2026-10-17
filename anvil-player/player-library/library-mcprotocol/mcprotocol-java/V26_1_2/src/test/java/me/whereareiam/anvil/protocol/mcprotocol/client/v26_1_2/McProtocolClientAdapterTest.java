package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundCustomSound;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAttackPacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolClientAdapterTest {
	@Test
	void isTheOnlyClientOfTheSegment() {
		List<Class<?>> clients = ServiceLoader.load(McProtocolClient.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolClientAdapter.class), clients);
	}

	@Test
	void speaksTheProtocolOfTheLoadedRelease() {
		McProtocolClientAdapter client = new McProtocolClientAdapter();

		assertEquals(MinecraftCodec.CODEC.getProtocolVersion(), client.protocolNumber());
		assertEquals(ClientSession.class, client.sessionType());
	}

	@Test
	void sendsAnAttackEncodedThroughTheReleaseTable() {
		List<Packet> sent = new ArrayList<>();

		new McProtocolClientAdapter().send(recording(sent), new ServerboundAttack(43));

		assertEquals(1, sent.size());
		assertEquals(43, assertInstanceOf(ServerboundAttackPacket.class, sent.getFirst()).getEntityId());
	}

	@Test
	void supportsThe1_21_11PacketsWithThisReleasesInteraction() {
		McProtocolClientAdapter client = new McProtocolClientAdapter();

		assertTrue(client.supports(ClientboundSystemChat.class));
		assertTrue(client.supports(ServerboundChat.class));
		assertTrue(client.supports(ServerboundMovePlayerPosRot.class));
		assertTrue(client.supports(ServerboundContainerClick.class));
		assertTrue(client.supports(ClientboundShowDialog.class));
		assertTrue(client.supports(ServerboundSwing.class));
		assertTrue(client.supports(ServerboundUseItem.class));
		assertTrue(client.supports(ServerboundUseItemOn.class));
		assertTrue(client.supports(ServerboundInteract.class));
		assertTrue(client.supports(ServerboundAttack.class));
		assertFalse(client.supports(ClientboundCustomSound.class));
	}

	/**
	 * A session that records the packets sent on it and supports nothing else.
	 */
	private static ClientSession recording(List<Packet> sent) {
		return (ClientSession) Proxy.newProxyInstance(ClientSession.class.getClassLoader(), new Class<?>[]{ClientSession.class},
				(proxy, method, arguments) -> {
					if (!method.getName().equals("send")) throw new UnsupportedOperationException(method.getName());

					sent.add((Packet) arguments[0]);
					return null;
				});
	}
}

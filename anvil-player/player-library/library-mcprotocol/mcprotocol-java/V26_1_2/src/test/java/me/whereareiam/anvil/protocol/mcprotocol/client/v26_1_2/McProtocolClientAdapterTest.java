package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundEntityPositionSync;
import me.whereareiam.anvil.protocol.java.api.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundSetEntityData;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.protocol.java.api.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundInteract;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.java.api.packet.inventory.ServerboundContainerClick;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerStatusOnly;
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

		new McProtocolClientAdapter().send(recording(sent), new ServerboundAttack(new EntityId(43)));

		assertEquals(1, sent.size());
		assertEquals(43, assertInstanceOf(ServerboundAttackPacket.class, sent.getFirst()).getEntityId());
	}

	@Test
	void supportsThe1_21_11PacketsWithThisReleasesInteraction() {
		McProtocolClientAdapter client = new McProtocolClientAdapter();

		assertTrue(client.supports(ClientboundAddEntity.class));
		assertTrue(client.supports(ClientboundEntityPositionSync.class));
		assertTrue(client.supports(ClientboundSetEntityData.class));
		assertTrue(client.supports(ClientboundPlayerPosition.class));
		assertTrue(client.supports(ClientboundSystemChat.class));
		assertTrue(client.supports(ServerboundChat.class));
		assertTrue(client.supports(ServerboundMovePlayerPosRot.class));
		assertTrue(client.supports(ClientboundSetEntityMotion.class));
		assertTrue(client.supports(ClientboundUpdateAttributes.class));
		assertTrue(client.supports(ClientboundUpdateMobEffect.class));
		assertTrue(client.supports(ClientboundRemoveMobEffect.class));
		assertTrue(client.supports(ClientboundPlayerAbilities.class));
		assertTrue(client.supports(ServerboundClientTickEnd.class));
		assertTrue(client.supports(ServerboundPlayerCommand.class));
		assertTrue(client.supports(ServerboundPlayerInput.class));
		assertTrue(client.supports(ServerboundMovePlayerPos.class));
		assertTrue(client.supports(ServerboundMovePlayerRot.class));
		assertTrue(client.supports(ServerboundMovePlayerStatusOnly.class));
		assertTrue(client.supports(ServerboundContainerClick.class));
		assertTrue(client.supports(ClientboundShowDialog.class));
		assertTrue(client.supports(ClientboundRegistryData.class));
		assertTrue(client.supports(ServerboundSwing.class));
		assertTrue(client.supports(ServerboundUseItem.class));
		assertTrue(client.supports(ServerboundUseItemOn.class));
		assertTrue(client.supports(ServerboundInteract.class));
		assertTrue(client.supports(ServerboundAttack.class));
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

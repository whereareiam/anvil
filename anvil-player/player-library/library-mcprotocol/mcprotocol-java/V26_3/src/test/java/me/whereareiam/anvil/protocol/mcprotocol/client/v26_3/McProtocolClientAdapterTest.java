package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChat;
import me.whereareiam.anvil.edition.java.protocol.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.edition.java.protocol.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.ClientboundAddEntity;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundEntityPositionSync;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateAttributes;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundAttack;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundPunch;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ServerboundContainerClick;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerCommand;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPos;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerStatusOnly;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.InteractionHand;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPunchPacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
		assertEquals(777, client.protocolNumber());
		assertEquals(ClientSession.class, client.sessionType());
	}

	@Test
	void sendsAPunchEncodedThroughTheReleaseTableAndRefusesASwing() {
		List<Packet> sent = new ArrayList<>();
		McProtocolClientAdapter client = new McProtocolClientAdapter();

		client.send(recording(sent), new ServerboundPunch());

		assertEquals(List.of(ServerboundPunchPacket.INSTANCE), sent);
		assertThrows(ProtocolUnavailableException.class,
				() -> client.send(recording(sent), new ServerboundSwing(InteractionHand.MAIN_HAND)));
	}

	@Test
	void sendsTheFlightThePlayerStartsOrStopsAsTheFlyingFlagOfTheAbilities() {
		List<Packet> sent = new ArrayList<>();
		McProtocolClientAdapter client = new McProtocolClientAdapter();

		client.send(recording(sent), new ServerboundPlayerAbilities(true));
		client.send(recording(sent), new ServerboundPlayerAbilities(false));

		assertEquals(2, sent.size());
		assertTrue(assertInstanceOf(ServerboundPlayerAbilitiesPacket.class, sent.get(0)).isFlying());
		assertFalse(assertInstanceOf(ServerboundPlayerAbilitiesPacket.class, sent.get(1)).isFlying());
	}

	@Test
	void supportsThe26_1PacketsWithThisReleasesInteraction() {
		McProtocolClientAdapter client = new McProtocolClientAdapter();

		assertTrue(client.supports(ClientboundAddEntity.class));
		assertTrue(client.supports(ClientboundEntityPositionSync.class));
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
		assertTrue(client.supports(ServerboundPlayerAbilities.class));
		assertTrue(client.supports(ServerboundPlayerCommand.class));
		assertTrue(client.supports(ServerboundPlayerInput.class));
		assertTrue(client.supports(ServerboundMovePlayerPos.class));
		assertTrue(client.supports(ServerboundMovePlayerRot.class));
		assertTrue(client.supports(ServerboundMovePlayerStatusOnly.class));
		assertTrue(client.supports(ServerboundContainerClick.class));
		assertTrue(client.supports(ClientboundShowDialog.class));
		assertTrue(client.supports(ClientboundRegistryData.class));
		assertTrue(client.supports(ServerboundUseItem.class));
		assertTrue(client.supports(ServerboundAttack.class));
		assertTrue(client.supports(ServerboundPunch.class));
		assertFalse(client.supports(ServerboundSwing.class));
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

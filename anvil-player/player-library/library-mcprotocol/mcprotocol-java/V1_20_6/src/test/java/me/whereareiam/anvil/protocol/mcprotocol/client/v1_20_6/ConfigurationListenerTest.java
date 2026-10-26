package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.mcprotocol.client.ClientBrand;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientSettings;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationListenerTest {
	private final List<Packet> sent = new ArrayList<>();
	private final Session session = session();
	private final ConfigurationListener listener = new ConfigurationListener(
			ClientSettings.builder().locale("de_de").viewDistance(6).build());

	@Test
	void sendsTheBrandAndThenTheClientInformationOnceTheLoginIsAcknowledged() {
		listener.packetSent(session, new ServerboundLoginAcknowledgedPacket());

		assertEquals(List.of(ServerboundCustomPayloadPacket.class, ServerboundClientInformationPacket.class),
				sent.stream().map(Packet::getClass).toList());
		ServerboundCustomPayloadPacket brand = (ServerboundCustomPayloadPacket) sent.getFirst();
		assertEquals("minecraft:brand", brand.getChannel().asString());
		assertArrayEquals(ClientBrand.payload(), brand.getData());
		ServerboundClientInformationPacket information = (ServerboundClientInformationPacket) sent.get(1);
		assertEquals("de_de", information.getLocale());
		assertEquals(6, information.getRenderDistance());
		assertEquals(ChatVisibility.FULL, information.getChatVisibility());
		assertTrue(information.isUseChatColors());
		assertEquals(Arrays.asList(SkinPart.values()), information.getVisibleParts());
		assertEquals(HandPreference.RIGHT_HAND, information.getMainHand());
		assertFalse(information.isTextFilteringEnabled());
		assertTrue(information.isAllowsListing());
	}

	@Test
	void sendsNothingAgainWhenTheServerStartsAnotherConfiguration() {
		listener.packetSent(session, new ServerboundConfigurationAcknowledgedPacket());
		listener.packetReceived(session, new ClientboundFinishConfigurationPacket());

		assertEquals(List.of(), sent);
	}

	@Test
	void answersEveryKeepAliveWithItsIdentifier() {
		listener.packetReceived(session, new ClientboundKeepAlivePacket(7));
		listener.packetReceived(session, new ClientboundKeepAlivePacket(8));

		assertEquals(7, assertInstanceOf(ServerboundKeepAlivePacket.class, sent.getFirst()).getPingId());
		assertEquals(8, assertInstanceOf(ServerboundKeepAlivePacket.class, sent.get(1)).getPingId());
	}

	private Session session() {
		Class<?>[] types = {Session.class};
		return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), types, (proxy, method, arguments) -> {
			if (!method.getName().equals("send")) throw new UnsupportedOperationException(method.getName());

			sent.add((Packet) arguments[0]);
			return null;
		});
	}
}

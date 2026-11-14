package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundKeepAlivePacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class KeepAliveAnswersTest {
	private final List<Packet> sent = new ArrayList<>();
	private final Session session = new TcpClientSession("127.0.0.1", 25565, new MinecraftProtocol("Alice"));

	@Test
	void takesTheKeepAliveAnswersOverFromTheLibrary() {
		KeepAliveAnswers.install(session);

		assertFalse(session.getFlag(MinecraftConstants.AUTOMATIC_KEEP_ALIVE_MANAGEMENT, true));
		assertEquals(List.of(KeepAliveAnswers.class), session.getListeners().stream()
				.<Class<?>>map(SessionListener::getClass)
				.toList());
	}

	@Test
	void answersEveryKeepAliveWithItsIdentifierInAnyPhase() {
		KeepAliveAnswers.install(session);
		SessionListener answers = session.getListeners().getFirst();
		Session recording = recording();

		answers.packetReceived(recording, new ClientboundKeepAlivePacket(7));
		answers.packetReceived(recording, new ClientboundKeepAlivePacket(8));

		assertEquals(7, assertInstanceOf(ServerboundKeepAlivePacket.class, sent.getFirst()).getPingId());
		assertEquals(8, assertInstanceOf(ServerboundKeepAlivePacket.class, sent.get(1)).getPingId());
	}

	private Session recording() {
		Class<?>[] types = {Session.class};
		return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), types, (proxy, method, arguments) -> {
			if (!method.getName().equals("send")) throw new UnsupportedOperationException(method.getName());

			sent.add((Packet) arguments[0]);
			return null;
		});
	}
}

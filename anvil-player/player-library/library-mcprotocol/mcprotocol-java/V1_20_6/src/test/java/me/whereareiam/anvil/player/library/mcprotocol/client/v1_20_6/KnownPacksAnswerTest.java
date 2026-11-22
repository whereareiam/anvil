package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.KnownPack;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundSelectKnownPacks;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundSelectKnownPacks;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class KnownPacksAnswerTest {
	private static final KnownPack CORE = new KnownPack("minecraft", "core", "1.20.5");

	private final List<Packet> sent = new ArrayList<>();
	private final Session session = new TcpClientSession("127.0.0.1", 25565, new MinecraftProtocol("Alice"));

	@Test
	void takesTheAnswerOverFromTheLibrary() {
		KnownPacksAnswer.install(session, "1.20.5");

		assertFalse(session.getFlag(MinecraftConstants.SEND_BLANK_KNOWN_PACKS_RESPONSE, true));
		assertEquals(List.of(KnownPacksAnswer.class), session.getListeners().stream()
				.<Class<?>>map(SessionListener::getClass)
				.toList());
	}

	@Test
	void namesTheVanillaCorePackOfThePlayersVersionAmongTheOfferedPacks() {
		answer("1.20.5", new ClientboundSelectKnownPacks(List.of(
				new KnownPack("minecraft", "bundle", "1.20.5"),
				CORE,
				new KnownPack("example", "rules", "1")
		)));

		ServerboundSelectKnownPacks answer = assertInstanceOf(ServerboundSelectKnownPacks.class, sent.getFirst());
		assertEquals(List.of(CORE), answer.getKnownPacks());
	}

	@Test
	void namesNoPackWhenTheServerOffersTheCorePackOfAnotherVersion() {
		answer("1.20.5", new ClientboundSelectKnownPacks(List.of(new KnownPack("minecraft", "core", "1.20.6"))));

		assertEquals(List.of(), assertInstanceOf(ServerboundSelectKnownPacks.class, sent.getFirst()).getKnownPacks());
	}

	@Test
	void answersNothingElse() {
		answer("1.20.5", new ClientboundKeepAlivePacket(7));

		assertEquals(List.of(), sent);
	}

	private void answer(String minecraftVersion, Packet packet) {
		KnownPacksAnswer.install(session, minecraftVersion);
		session.getListeners().getFirst().packetReceived(recording(), packet);
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

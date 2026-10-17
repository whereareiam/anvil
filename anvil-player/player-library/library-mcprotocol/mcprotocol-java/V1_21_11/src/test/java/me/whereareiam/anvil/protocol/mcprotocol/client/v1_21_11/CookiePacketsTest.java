package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundCookieRequest;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundStoreCookie;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ServerboundCookieResponse;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundStoreCookiePacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.clientbound.ClientboundCookieRequestPacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.serverbound.ServerboundCookieResponsePacket;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class CookiePacketsTest {
	private static final byte[] TICKET = "ticket".getBytes(StandardCharsets.UTF_8);

	private final CookiePackets packets = new CookiePackets();

	@Test
	void decodesRequestsAndStoredCookiesWithTheirNamespace() {
		ClientboundStoreCookie stored = assertInstanceOf(ClientboundStoreCookie.class,
				packets.decode(new ClientboundStoreCookiePacket(Key.key("example:ticket"), TICKET)));

		assertEquals("example:ticket", stored.getKey());
		assertArrayEquals(TICKET, stored.getPayload());
		assertEquals(new ClientboundCookieRequest("minecraft:ticket"),
				packets.decode(new ClientboundCookieRequestPacket(Key.key("ticket"))));
	}

	@Test
	void encodesAnAnswerWithThePayloadOrWithNone() {
		ServerboundCookieResponsePacket answered = assertInstanceOf(ServerboundCookieResponsePacket.class,
				packets.encode(new ServerboundCookieResponse("example:ticket", TICKET)));
		ServerboundCookieResponsePacket none = assertInstanceOf(ServerboundCookieResponsePacket.class,
				packets.encode(new ServerboundCookieResponse("example:other", null)));

		assertEquals(Key.key("example:ticket"), answered.getKey());
		assertArrayEquals(TICKET, answered.getPayload());
		assertEquals(Key.key("example:other"), none.getKey());
		assertNull(none.getPayload());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryCookiePacket() {
		assertEquals(Set.of(
				ClientboundCookieRequest.class,
				ClientboundStoreCookie.class,
				ServerboundCookieResponse.class
		), packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

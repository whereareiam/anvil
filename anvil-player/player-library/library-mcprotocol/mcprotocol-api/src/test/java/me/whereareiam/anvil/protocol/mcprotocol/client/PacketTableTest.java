package me.whereareiam.anvil.protocol.mcprotocol.client;

import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundCookieRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketTableTest {
	private final PacketTable<String> table = new PacketTable<>(List.of(new ChatTranslation(),
			new CookieTranslation()));

	@Test
	void carriesThePacketsOfEveryTranslation() {
		assertTrue(table.supports(ClientboundSystemChat.class));
		assertTrue(table.supports(ServerboundChat.class));
		assertTrue(table.supports(ClientboundCookieRequest.class));
		assertFalse(table.supports(ServerboundChatCommand.class));
	}

	@Test
	void decodesThroughTheTranslationThatKnowsThePacket() {
		assertEquals(new ClientboundSystemChat("hello", false), table.decode("chat:hello"));
		assertEquals(new ClientboundCookieRequest("minecraft:session"), table.decode("cookie:minecraft:session"));
		assertNull(table.decode("keep-alive"));
	}

	@Test
	void encodesThroughTheTranslationThatCarriesThePacketAndRefusesTheOthers() {
		assertEquals("chat:hello", table.encode(new ServerboundChat("hello")));

		ProtocolUnavailableException failure = assertThrows(ProtocolUnavailableException.class,
				() -> table.encode(new ServerboundChatCommand("spawn")));
		assertEquals("the loaded mcprotocol release does not carry " + ServerboundChatCommand.class.getName(),
				failure.getMessage());
	}

	@Test
	void refusesTwoTranslationsCarryingOnePacket() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new PacketTable<>(List.of(new ChatTranslation(), new ChatTranslation())));

		assertTrue(failure.getMessage().startsWith(ClientboundSystemChat.class.getName())
				|| failure.getMessage().startsWith(ServerboundChat.class.getName()), failure.getMessage());
	}

	/**
	 * Translates chat to and from strings that start with {@code chat:}.
	 */
	private static final class ChatTranslation implements PacketTranslation<String> {
		private static final String PREFIX = "chat:";

		@Override
		public @NotNull Set<Class<? extends JavaPacket>> packets() {
			return Set.of(ClientboundSystemChat.class, ServerboundChat.class);
		}

		@Override
		public @Nullable ClientboundPacket decode(@NotNull String packet) {
			return packet.startsWith(PREFIX) ? new ClientboundSystemChat(packet.substring(PREFIX.length()), false)
					: null;
		}

		@Override
		public @Nullable String encode(@NotNull ServerboundPacket packet) {
			return packet instanceof ServerboundChat chat ? PREFIX + chat.getText() : null;
		}
	}

	/**
	 * Translates cookie requests from strings that start with {@code cookie:}.
	 */
	private static final class CookieTranslation implements PacketTranslation<String> {
		private static final String PREFIX = "cookie:";

		@Override
		public @NotNull Set<Class<? extends JavaPacket>> packets() {
			return Set.of(ClientboundCookieRequest.class);
		}

		@Override
		public @Nullable ClientboundPacket decode(@NotNull String packet) {
			return packet.startsWith(PREFIX) ? new ClientboundCookieRequest(packet.substring(PREFIX.length())) : null;
		}

		@Override
		public @Nullable String encode(@NotNull ServerboundPacket packet) {
			return null;
		}
	}
}

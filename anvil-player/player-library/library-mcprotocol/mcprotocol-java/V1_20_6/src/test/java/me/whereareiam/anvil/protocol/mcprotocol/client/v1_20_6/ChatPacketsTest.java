package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.chat.MessageSignature;
import me.whereareiam.anvil.protocol.java.api.model.chat.PackedMessageSignature;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDeleteChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.java.api.type.FilterMaskType;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.chat.ChatFilterType;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDeleteChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPacketsTest {
	private final ChatPackets packets = new ChatPackets();

	@Test
	void decodesSystemChatWithTranslationArgumentsAndChildren() {
		Component message = Component.translatable("multiplayer.player.joined", Component.text("Alice")).append(Component.text("!"));

		assertEquals(new ClientboundSystemChat("multiplayer.player.joined Alice!", false), packets.decode(new ClientboundSystemChatPacket(message, false)));
	}

	@Test
	void decodesOverlaySystemChatShownAboveTheHotbar() {
		ClientboundSystemChatPacket overlay = new ClientboundSystemChatPacket(Component.text("anvil:action-bar"), true);

		assertEquals(new ClientboundSystemChat("anvil:action-bar", true), packets.decode(overlay));
	}

	@Test
	void decodesPlayerChatAsItsSignedContentWithoutUnsignedContent() {
		assertEquals(ClientboundPlayerChat.unsigned("signed hello"), packets.decode(playerChat("signed hello", null)));
	}

	@Test
	void decodesPlayerChatAsItsUnsignedContentWhenTheServerDecoratedIt() {
		Component decorated = Component.text("decorated ").append(Component.text("hello"));

		assertEquals(ClientboundPlayerChat.unsigned("decorated hello"), packets.decode(playerChat("signed hello", decorated)));
	}

	@Test
	void decodesPlayerChatWithItsSignatureFilterAndTheMessagesItsSenderHadSeen() {
		byte[] signature = signature(7);
		ClientboundPlayerChatPacket chat = new ClientboundPlayerChatPacket(new UUID(0, 1), 0, signature, "hello", 0L, 0L,
				List.of(new org.geysermc.mcprotocollib.protocol.data.game.chat.MessageSignature(3, null),
						new org.geysermc.mcprotocollib.protocol.data.game.chat.MessageSignature(-1, signature(9))),
				null, ChatFilterType.FULLY_FILTERED, 0, Component.text("Alice"), null);

		assertEquals(new ClientboundPlayerChat("hello", FilterMaskType.FULLY_FILTERED, new MessageSignature(signature(7)),
				List.of(PackedMessageSignature.cached(3), PackedMessageSignature.full(new MessageSignature(signature(9))))),
				packets.decode(chat));
	}

	@Test
	void decodesDeletionsByCachedOrWholeSignature() {
		assertEquals(new ClientboundDeleteChat(PackedMessageSignature.cached(5)), packets.decode(
				new ClientboundDeleteChatPacket(new org.geysermc.mcprotocollib.protocol.data.game.chat.MessageSignature(5, null))));
		assertEquals(new ClientboundDeleteChat(PackedMessageSignature.full(new MessageSignature(signature(2)))),
				packets.decode(new ClientboundDeleteChatPacket(
						new org.geysermc.mcprotocollib.protocol.data.game.chat.MessageSignature(-1, signature(2)))));
	}

	@Test
	void decodesDisguisedChat() {
		ClientboundDisguisedChatPacket disguised = new ClientboundDisguisedChatPacket(Component.text("journey-ready-1"), 1,
				Component.text("Server"), null);

		assertEquals(new ClientboundDisguisedChat("journey-ready-1"), packets.decode(disguised));
	}

	@Test
	void flattensKeybindsAndSelectorsWithoutScores() {
		Component message = Component.text("press ").append(Component.keybind("key.jump"))
				.append(Component.text(" near ")).append(Component.selector("@p"))
				.append(Component.score("Alice", "kills")).append(Component.text("!"));

		assertEquals(new ClientboundSystemChat("press key.jump near @p!", false), packets.decode(new ClientboundSystemChatPacket(message, false)));
	}

	@Test
	void encodesChatUnsignedWithNothingAcknowledged() {
		long before = Instant.now().toEpochMilli();

		ServerboundChatPacket chat = assertInstanceOf(ServerboundChatPacket.class, packets.encode(new ServerboundChat("hello there")));

		assertEquals("hello there", chat.getMessage());
		assertTrue(chat.getTimeStamp() >= before, "timestamp " + chat.getTimeStamp());
		assertEquals(0L, chat.getSalt());
		assertNull(chat.getSignature());
		assertEquals(0, chat.getOffset());
		assertEquals(new BitSet(20), chat.getAcknowledgedMessages());
	}

	@Test
	void encodesCommandsThroughTheUnsignedCommandPacket() {
		Object command = packets.encode(new ServerboundChatCommand("anvil-fixture ping"));

		assertEquals("anvil-fixture ping", assertInstanceOf(ServerboundChatCommandPacket.class, command).getCommand());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryChatPacket() {
		assertEquals(Set.of(
				ClientboundSystemChat.class,
				ClientboundPlayerChat.class,
				ClientboundDisguisedChat.class,
				ClientboundDeleteChat.class,
				ServerboundChat.class,
				ServerboundChatCommand.class
		), packets.packets());
	}

	private static ClientboundPlayerChatPacket playerChat(String content, @Nullable Component unsignedContent) {
		return new ClientboundPlayerChatPacket(new UUID(0, 1), 0, null, content, 0L, 0L, List.of(), unsignedContent,
				ChatFilterType.PASS_THROUGH, 0, Component.text("Alice"), null);
	}

	private static byte[] signature(int seed) {
		byte[] bytes = new byte[256];
		bytes[0] = (byte) seed;

		return bytes;
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

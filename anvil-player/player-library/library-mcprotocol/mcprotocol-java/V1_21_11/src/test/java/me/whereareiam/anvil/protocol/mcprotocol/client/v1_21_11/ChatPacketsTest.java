package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.chat.ChatFilterType;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
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
		assertEquals(new ClientboundPlayerChat("signed hello"), packets.decode(playerChat("signed hello", null)));
	}

	@Test
	void decodesPlayerChatAsItsUnsignedContentWhenTheServerDecoratedIt() {
		Component decorated = Component.text("decorated ").append(Component.text("hello"));

		assertEquals(new ClientboundPlayerChat("decorated hello"), packets.decode(playerChat("signed hello", decorated)));
	}

	@Test
	void decodesDisguisedChat() {
		ClientboundDisguisedChatPacket disguised = new ClientboundDisguisedChatPacket(Component.text("journey-ready-1"), Holder.ofId(1),
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
	void encodesChatUnsignedWithNothingAcknowledgedAndAnUnverifiedChecksum() {
		long before = Instant.now().toEpochMilli();

		ServerboundChatPacket chat = assertInstanceOf(ServerboundChatPacket.class, packets.encode(new ServerboundChat("hello there")));

		assertEquals("hello there", chat.getMessage());
		assertTrue(chat.getTimeStamp() >= before, "timestamp " + chat.getTimeStamp());
		assertEquals(0L, chat.getSalt());
		assertNull(chat.getSignature());
		assertEquals(0, chat.getOffset());
		assertEquals(new BitSet(20), chat.getAcknowledgedMessages());
		assertEquals(0, chat.getChecksum());
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
				ServerboundChat.class,
				ServerboundChatCommand.class
		), packets.packets());
	}

	private static ClientboundPlayerChatPacket playerChat(String content, @Nullable Component unsignedContent) {
		return new ClientboundPlayerChatPacket(0, new UUID(0, 1), 0, null, content, 0L, 0L, List.of(), unsignedContent,
				ChatFilterType.PASS_THROUGH, Holder.ofId(0), Component.text("Alice"), null);
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

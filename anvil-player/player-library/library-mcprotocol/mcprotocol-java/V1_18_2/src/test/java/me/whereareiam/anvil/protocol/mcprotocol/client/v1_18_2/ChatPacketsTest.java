package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.MessageType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatPacketsTest {
	private final ChatPackets packets = new ChatPackets();

	@Test
	void decodesChatAsPlayerChatWithTranslationArgumentsAndChildren() {
		Component message = Component.translatable("chat.type.text", Component.text("Alice"), Component.text("hello"))
				.append(Component.text("!"));

		assertEquals(new ClientboundPlayerChat("chat.type.text Alice hello!"), packets.decode(new ClientboundChatPacket(message, MessageType.CHAT)));
	}

	@Test
	void decodesSystemMessagesAsSystemChat() {
		Component message = Component.text("anvil:").append(Component.text("welcome"));

		assertEquals(new ClientboundSystemChat("anvil:welcome", false), packets.decode(new ClientboundChatPacket(message, MessageType.SYSTEM)));
	}

	@Test
	void decodesGameInfoShownAboveTheHotbarAsOverlaySystemChat() {
		ClientboundChatPacket notification = new ClientboundChatPacket(Component.text("anvil:action-bar"), MessageType.NOTIFICATION);

		assertEquals(new ClientboundSystemChat("anvil:action-bar", true), packets.decode(notification));
	}

	@Test
	void flattensKeybindsAndSelectorsWithoutScores() {
		Component message = Component.text("press ").append(Component.keybind("key.jump"))
				.append(Component.text(" near ")).append(Component.selector("@p"))
				.append(Component.score("Alice", "kills")).append(Component.text("!"));

		assertEquals(new ClientboundSystemChat("press key.jump near @p!", false), packets.decode(new ClientboundChatPacket(message, MessageType.SYSTEM)));
	}

	@Test
	void encodesChatAsOneChatPacket() {
		ServerboundChatPacket chat = assertInstanceOf(ServerboundChatPacket.class, packets.encode(new ServerboundChat("hello there")));

		assertEquals("hello there", chat.getMessage());
	}

	@Test
	void encodesCommandsAsChatWithALeadingSlash() {
		ServerboundChatPacket command = assertInstanceOf(ServerboundChatPacket.class, packets.encode(new ServerboundChatCommand("anvil-fixture ping")));

		assertEquals("/anvil-fixture ping", command.getMessage());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryChatPacketExceptDisguisedChat() {
		assertEquals(Set.of(ClientboundSystemChat.class, ClientboundPlayerChat.class, ServerboundChat.class, ServerboundChatCommand.class),
				packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}

package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.MessageType;
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatPacketsTest {
	private final ChatPackets packets = new ChatPackets();

	@Test
	void decodesEachMessageTypeAsTheProtocolsChat() {
		Component joined = Component.translatable("multiplayer.player.joined", Component.text("Alice"));

		assertEquals(new ClientboundPlayerChat("chat.type.text Alice hello"), packets.decode(new ServerChatPacket(
				Component.translatable("chat.type.text", Component.text("Alice"), Component.text("hello")),
				MessageType.CHAT, new UUID(0, 1))));
		assertEquals(new ClientboundSystemChat("multiplayer.player.joined Alice", false),
				packets.decode(new ServerChatPacket(joined, MessageType.SYSTEM)));
		assertEquals(new ClientboundSystemChat("anvil:action-bar", true),
				packets.decode(new ServerChatPacket(Component.text("anvil:action-bar"), MessageType.NOTIFICATION)));
	}

	@Test
	void sendsChatAndCommandsThroughTheChatPacket() {
		assertEquals("hello there", assertInstanceOf(ClientChatPacket.class,
				packets.encode(new ServerboundChat("hello there"))).getMessage());
		assertEquals("/anvil-fixture ping", assertInstanceOf(ClientChatPacket.class,
				packets.encode(new ServerboundChatCommand("anvil-fixture ping"))).getMessage());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
	}

	@Test
	void carriesTheChatPacketsOfTheRelease() {
		assertEquals(Set.of(ClientboundSystemChat.class, ClientboundPlayerChat.class, ServerboundChat.class,
				ServerboundChatCommand.class), packets.packets());
	}
}

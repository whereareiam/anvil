package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.mcprotocol.client.AdventureText;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Chat of Minecraft 1.18.2, which has one chat packet in each direction: a received message names its kind, and a
 * command is sent as a chat message starting with a slash.
 */
final class ChatPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSystemChat.class, ClientboundPlayerChat.class, ServerboundChat.class, ServerboundChatCommand.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ClientboundChatPacket chat)) return null;

		String text = AdventureText.plain(chat.getMessage());
		return switch (chat.getType()) {
			case CHAT -> new ClientboundPlayerChat(text);
			case SYSTEM -> new ClientboundSystemChat(text, false);
			case NOTIFICATION -> new ClientboundSystemChat(text, true);
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundChat chat) return new ServerboundChatPacket(chat.getText());
		if (packet instanceof ServerboundChatCommand command) return new ServerboundChatPacket("/" + command.getCommand());

		return null;
	}
}

package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureText;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Chat of the MCProtocolLib releases from 1.16.5 until 1.17.1, which have one chat packet in each direction: a
 * received message names its kind, and a command is sent as a chat message starting with a slash.
 */
public final class ChatPackets implements PacketTranslation<Packet> {
	/**
	 * What a chat message starts with to run the rest as a command.
	 */
	private static final String COMMAND_PREFIX = "/";

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSystemChat.class, ClientboundPlayerChat.class, ServerboundChat.class,
				ServerboundChatCommand.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ServerChatPacket chat)) return null;

		String text = AdventureText.plain(chat.getMessage());
		return switch (chat.getType()) {
			case CHAT -> new ClientboundPlayerChat(text);
			case SYSTEM -> new ClientboundSystemChat(text, false);
			case NOTIFICATION -> new ClientboundSystemChat(text, true);
		};
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundChat chat) return new ClientChatPacket(chat.getText());
		if (packet instanceof ServerboundChatCommand command)
			return new ClientChatPacket(COMMAND_PREFIX + command.getCommand());

		return null;
	}
}

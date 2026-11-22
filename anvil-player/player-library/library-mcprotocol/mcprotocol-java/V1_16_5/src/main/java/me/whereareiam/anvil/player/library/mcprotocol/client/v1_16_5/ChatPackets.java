package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChat;
import me.whereareiam.anvil.edition.java.protocol.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.player.api.model.text.Text;
import me.whereareiam.anvil.player.library.mcprotocol.adventure.ComponentTranslator;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
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

	private final ComponentTranslator text;

	/**
	 * Creates the chat translation of the release.
	 *
	 * @param text translates the release's text components
	 */
	public ChatPackets(@NotNull ComponentTranslator text) {
		this.text = text;
	}

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundSystemChat.class, ClientboundPlayerChat.class, ServerboundChat.class,
				ServerboundChatCommand.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (!(packet instanceof ServerChatPacket chat)) return null;

		Text message = text.translate(chat.getMessage());
		return switch (chat.getType()) {
			case CHAT -> ClientboundPlayerChat.unsigned(message);
			case SYSTEM -> new ClientboundSystemChat(message, false);
			case NOTIFICATION -> new ClientboundSystemChat(message, true);
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

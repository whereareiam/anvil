package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureText;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Chat of the MCProtocolLib releases from 1.20.5 on: system, player and disguised messages in, chat and commands out.
 * The player sends chat unsigned, as a client without a profile key does; each release's {@code ChatPackets} builds
 * the chat packet, whose fields change between releases.
 */
public abstract class ChatTranslation implements PacketTranslation<Packet> {
	/**
	 * Number of last seen messages a chat message acknowledges.
	 */
	protected static final int ACKNOWLEDGED_MESSAGES = 20;
	/**
	 * The salt of an unsigned message, which no signature uses.
	 */
	protected static final long UNSIGNED_SALT = 0L;
	/**
	 * The signature of an unsigned message.
	 */
	protected static final byte[] NO_SIGNATURE = null;
	/**
	 * The offset of the first acknowledged message; the player acknowledges none.
	 */
	protected static final int NO_ACKNOWLEDGED_OFFSET = 0;

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundSystemChat.class,
				ClientboundPlayerChat.class,
				ClientboundDisguisedChat.class,
				ServerboundChat.class,
				ServerboundChatCommand.class
		);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundSystemChatPacket chat)
			return new ClientboundSystemChat(AdventureText.plain(chat.getContent()), chat.isOverlay());
		if (packet instanceof ClientboundDisguisedChatPacket chat)
			return new ClientboundDisguisedChat(AdventureText.plain(chat.getMessage()));
		if (!(packet instanceof ClientboundPlayerChatPacket chat)) return null;

		Component unsigned = chat.getUnsignedContent();

		return new ClientboundPlayerChat(unsigned == null ? chat.getContent() : AdventureText.plain(unsigned));
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundChat chat) return chat(chat);
		if (packet instanceof ServerboundChatCommand command)
			return new ServerboundChatCommandPacket(command.getCommand());

		return null;
	}

	/**
	 * Builds the release's unsigned chat packet.
	 *
	 * @param chat the message to send
	 * @return the release's chat packet
	 */
	protected abstract @NotNull Packet chat(@NotNull ServerboundChat chat);
}

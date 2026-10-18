package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.packetlib.packet.Packet;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

/**
 * Chat of the MCProtocolLib 1.19.4 release, which signs chat and commands: system, player and disguised messages in,
 * chat and commands out. The player sends both unsigned, as a client without a profile key does, with a timestamp, no
 * salt and nothing acknowledged; a command carries no argument signatures.
 */
public final class ChatPackets implements PacketTranslation<Packet> {
	/**
	 * Number of last seen messages a chat message acknowledges.
	 */
	private static final int ACKNOWLEDGED_MESSAGES = 20;
	/**
	 * The salt of an unsigned message, which no signature uses.
	 */
	private static final long UNSIGNED_SALT = 0L;
	/**
	 * The signature of an unsigned message.
	 */
	private static final byte[] NO_SIGNATURE = null;
	/**
	 * The offset of the first acknowledged message; the player acknowledges none.
	 */
	private static final int NO_ACKNOWLEDGED_OFFSET = 0;

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(
				ClientboundSystemChat.class,
				ClientboundPlayerChat.class,
				ClientboundDisguisedChat.class,
				ServerboundChat.class,
				ServerboundChatCommand.class
		);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundSystemChatPacket chat)
			return new ClientboundSystemChat(AdventureText.plain(chat.getContent()), chat.isOverlay());
		if (packet instanceof ClientboundDisguisedChatPacket chat)
			return new ClientboundDisguisedChat(AdventureText.plain(chat.getMessage()));
		if (!(packet instanceof ClientboundPlayerChatPacket chat)) return null;

		Component unsigned = chat.getUnsignedContent();

		return new ClientboundPlayerChat(unsigned == null ? chat.getContent() : AdventureText.plain(unsigned));
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundChat chat)
			return new ServerboundChatPacket(chat.getText(), Instant.now().toEpochMilli(), UNSIGNED_SALT, NO_SIGNATURE,
					NO_ACKNOWLEDGED_OFFSET, new BitSet(ACKNOWLEDGED_MESSAGES));
		if (packet instanceof ServerboundChatCommand command)
			return new ServerboundChatCommandPacket(command.getCommand(), Instant.now().toEpochMilli(), UNSIGNED_SALT,
					List.of(), NO_ACKNOWLEDGED_OFFSET, new BitSet(ACKNOWLEDGED_MESSAGES));

		return null;
	}
}

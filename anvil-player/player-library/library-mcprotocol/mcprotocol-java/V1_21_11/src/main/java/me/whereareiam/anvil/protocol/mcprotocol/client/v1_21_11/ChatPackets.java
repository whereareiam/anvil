package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundDisguisedChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.mcprotocol.client.AdventureText;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundDisguisedChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.BitSet;
import java.util.Set;

/**
 * Chat of the MCProtocolLib 1.21.11 release, whose chat packet carries a checksum of the acknowledged messages.
 */
final class ChatPackets implements PacketTranslation<Packet> {
	private static final int ACKNOWLEDGED_MESSAGES = 20;
	private static final int UNVERIFIED_CHECKSUM = 0;

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
		if (packet instanceof ClientboundDisguisedChatPacket chat) return new ClientboundDisguisedChat(AdventureText.plain(chat.getMessage()));
		if (!(packet instanceof ClientboundPlayerChatPacket chat)) return null;

		Component unsigned = chat.getUnsignedContent();
		return new ClientboundPlayerChat(unsigned == null ? chat.getContent() : AdventureText.plain(unsigned));
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundChat chat)
			return new ServerboundChatPacket(chat.getText(), Instant.now().toEpochMilli(), 0L, null, 0, new BitSet(ACKNOWLEDGED_MESSAGES),
					UNVERIFIED_CHECKSUM);
		if (packet instanceof ServerboundChatCommand command) return new ServerboundChatCommandPacket(command.getCommand());

		return null;
	}
}

package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1.ChatTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.BitSet;

/**
 * Chat of the MCProtocolLib 1.21.11 release, whose chat packet carries a checksum of the acknowledged messages.
 */
public final class ChatPackets extends ChatTranslation {
	/**
	 * The checksum of no acknowledged messages, which the server does not verify.
	 */
	private static final int UNVERIFIED_CHECKSUM = 0;

	@Override
	protected @NotNull Packet chat(@NotNull ServerboundChat chat) {
		return new ServerboundChatPacket(chat.getText(), Instant.now().toEpochMilli(), UNSIGNED_SALT, NO_SIGNATURE,
				NO_ACKNOWLEDGED_OFFSET, new BitSet(ACKNOWLEDGED_MESSAGES), UNVERIFIED_CHECKSUM);
	}
}

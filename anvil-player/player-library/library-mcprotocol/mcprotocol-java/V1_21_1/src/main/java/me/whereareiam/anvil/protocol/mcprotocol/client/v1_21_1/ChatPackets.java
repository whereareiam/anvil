package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.BitSet;

/**
 * Chat of the MCProtocolLib 1.21.1 release, whose chat packet carries the acknowledged messages without a checksum.
 */
final class ChatPackets extends ChatTranslation {
	@Override
	protected @NotNull Packet chat(@NotNull ServerboundChat chat) {
		return new ServerboundChatPacket(chat.getText(), Instant.now().toEpochMilli(), UNSIGNED_SALT, NO_SIGNATURE,
				NO_ACKNOWLEDGED_OFFSET, new BitSet(ACKNOWLEDGED_MESSAGES));
	}
}

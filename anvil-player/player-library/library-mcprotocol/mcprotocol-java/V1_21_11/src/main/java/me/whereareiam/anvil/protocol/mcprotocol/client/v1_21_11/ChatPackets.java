package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureRelease;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ChatTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.BitSet;

/**
 * Chat of the MCProtocolLib releases from 1.21.11 on, whose chat packet carries a checksum of the acknowledged messages,
 * and whose Adventure is 4.25 or 5.
 */
public final class ChatPackets extends ChatTranslation {
	/**
	 * The checksum of no acknowledged messages, which the server does not verify.
	 */
	private static final int UNVERIFIED_CHECKSUM = 0;
	private static final ReleaseAdventure ADVENTURE = new ReleaseAdventure();

	@Override
	protected @NotNull Packet chat(@NotNull ServerboundChat chat) {
		return new ServerboundChatPacket(chat.getText(), Instant.now().toEpochMilli(), UNSIGNED_SALT, NO_SIGNATURE,
				NO_ACKNOWLEDGED_OFFSET, new BitSet(ACKNOWLEDGED_MESSAGES), UNVERIFIED_CHECKSUM);
	}

	@Override
	protected @NotNull AdventureRelease adventure() {
		return ADVENTURE;
	}
}

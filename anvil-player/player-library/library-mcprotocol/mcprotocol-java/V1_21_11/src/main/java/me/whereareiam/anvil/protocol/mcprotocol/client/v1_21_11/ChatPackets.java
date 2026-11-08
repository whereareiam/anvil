package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ChatTranslation;
import net.kyori.adventure.text.event.ClickEvent;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.BitSet;

/**
 * Chat of the MCProtocolLib releases from 1.21.11 on, whose chat packet carries a checksum of the acknowledged messages,
 * and whose Adventure, 4.25 or 5, keeps a click's value as a typed payload.
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

	@Override
	protected @Nullable String clickValue(@NotNull ClickEvent click) {
		ClickEvent.Payload payload = click.payload();
		if (payload instanceof ClickEvent.Payload.Text text) return text.value();
		if (payload instanceof ClickEvent.Payload.Int page) return Integer.toString(page.integer());
		if (payload instanceof ClickEvent.Payload.Custom custom) return custom.key().asString();

		return null;
	}
}

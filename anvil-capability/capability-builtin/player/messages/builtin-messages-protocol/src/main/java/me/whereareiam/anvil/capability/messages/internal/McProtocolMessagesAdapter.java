package me.whereareiam.anvil.capability.messages.internal;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.BitSet;
import java.util.function.Supplier;

/**
 * Encodes native chat actions and translates received native chat packets to text.
 */
@RequiredArgsConstructor
public final class McProtocolMessagesAdapter {
	private final @NotNull Supplier<ClientSession> sessions;
	private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

	public void chat(@NotNull String message) {
		sessions.get().send(new ServerboundChatPacket(
				message,
				Instant.now().toEpochMilli(),
				0L,
				null,
				0,
				new BitSet(20), 0)
		);
	}

	public void command(@NotNull String command) {
		sessions.get().send(new ServerboundChatCommandPacket(command));
	}

	public @Nullable String receive(@NotNull Packet packet) {
		if (packet instanceof ClientboundSystemChatPacket chat) return plain.serialize(chat.getContent());
		if (!(packet instanceof ClientboundPlayerChatPacket chat)) return null;

		Component content = chat.getUnsignedContent();
		return content == null
				? chat.getContent()
				: plain.serialize(content);
	}
}

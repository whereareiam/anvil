package me.whereareiam.anvil.capability.messages.internal;

import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.BitSet;

/**
 * Installs chat packet operations and listeners into an MCProtocol worker.
 */
public final class McProtocolMessagesAdapter implements ProtocolCapabilityAdapter {
	private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

	@Override
	public @NotNull String id() {
		return McProtocolMessagesProvider.ID;
	}

	@Override
	public boolean supports(int protocolNumber) {
		return McProtocolMessagesAdapterProvider.supportsProtocol(protocolNumber);
	}

	@Override
	public void install(@NotNull ProtocolCapabilityAdapterRegistry registry) {
		registry.operation("messages.chat", (player, arguments) -> {
			player.send(new ServerboundChatPacket(
					arguments.path("message").asText(),
					Instant.now().toEpochMilli(),
					0L,
					null,
					0,
					new BitSet(20),
					0
			));
			return player.mapper().createObjectNode();
		});
		registry.operation("messages.command", (player, arguments) -> {
			player.send(new ServerboundChatCommandPacket(arguments.path("command").asText()));
			return player.mapper().createObjectNode();
		});
		registry.packets((player, packet) -> {
			if (packet instanceof ClientboundSystemChatPacket chat) {
				player.emit("messages.received", payload -> payload.put("text", plain.serialize(chat.getContent())));
				return;
			}
			if (packet instanceof ClientboundPlayerChatPacket chat) {
				Component content = chat.getUnsignedContent();
				player.emit("messages.received", payload -> payload.put(
						"text",
						content == null ? chat.getContent() : plain.serialize(content)
				));
			}
		});
	}
}

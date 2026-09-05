package me.whereareiam.anvil.capability.messages.internal;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapter;
import me.whereareiam.anvil.protocol.adapter.api.capability.ProtocolCapabilityAdapterRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Chat and slash-command binding for the pre-signed-chat protocol.
 */
public final class McProtocol1182MessagesAdapter implements ProtocolCapabilityAdapter {
	private final LegacyMessageText text = new LegacyMessageText();

	@Override
	public @NotNull String id() {
		return "me.whereareiam.anvil.messages";
	}

	@Override
	public boolean supports(int protocolNumber) {
		return McProtocol1182MessagesAdapterProvider.supportsProtocol(protocolNumber);
	}

	@Override
	public void install(@NotNull ProtocolCapabilityAdapterRegistry registry) {
		registry.operation("messages.chat", (player, arguments) -> {
			player.send(new ServerboundChatPacket(arguments.path("message").asText()));
			return player.mapper().createObjectNode();
		});
		registry.operation("messages.command", (player, arguments) -> {
			player.send(new ServerboundChatPacket("/" + arguments.path("command").asText()));
			return player.mapper().createObjectNode();
		});
		registry.packets((player, packet) -> {
			if (packet instanceof ClientboundChatPacket chat)
				player.emit("messages.received", value -> value.put("text", text.render(chat.getMessage())));
		});
	}
}

package external.fixture.java;

import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundPlayerChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChat;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * A release that carries only chat, and a server that answers it: chat comes back as the player's chat and a command
 * as a system message naming it.
 */
final class EchoServer {
	private static final Set<Class<? extends JavaPacket>> CARRIED = Set.of(
			ServerboundChat.class,
			ServerboundChatCommand.class,
			ClientboundPlayerChat.class,
			ClientboundSystemChat.class
	);

	/**
	 * Tells whether the release carries a packet.
	 */
	boolean carries(@NotNull Class<? extends JavaPacket> type) {
		return CARRIED.contains(type);
	}

	/**
	 * Returns what the server sends a player in answer to one of its packets.
	 */
	@NotNull List<ClientboundPacket> answer(@NotNull String player, @NotNull ServerboundPacket packet) {
		if (packet instanceof ServerboundChat chat)
			return List.of(ClientboundPlayerChat.unsigned("<" + player + "> " + chat.getText()));
		if (packet instanceof ServerboundChatCommand command)
			return List.of(new ClientboundSystemChat("ran /" + command.getCommand(), false));

		return List.of();
	}
}

package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;
import org.jetbrains.annotations.NotNull;

/**
 * Commands of the MCProtocolLib 1.21.1 release, whose nodes carry no permission flag, which Minecraft 1.21.6 added.
 */
final class CommandPackets extends CommandTranslation {
	@Override
	protected boolean restricted(@NotNull CommandNode node) {
		return false;
	}
}

package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;
import org.jetbrains.annotations.NotNull;

/**
 * Commands of the MCProtocolLib 1.20.6 and 1.21.1 releases, whose nodes carry no permission flag, which Minecraft
 * 1.21.6 added.
 */
public final class CommandPackets extends CommandTranslation {
	@Override
	protected boolean restricted(@NotNull CommandNode node) {
		return false;
	}
}

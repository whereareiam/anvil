package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.CommandTranslation;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;
import org.jetbrains.annotations.NotNull;

/**
 * Commands of the MCProtocolLib 1.21.11 release, whose nodes carry the flag that marks a node as needing permissions,
 * which the library reads as {@code allowsRestricted}.
 */
public final class CommandPackets extends CommandTranslation {
	@Override
	protected boolean restricted(@NotNull CommandNode node) {
		return node.isAllowsRestricted();
	}
}

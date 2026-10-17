package me.whereareiam.anvil.protocol.java.api.packet.command;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.command.CommandNode;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The command tree the server declares to the player: the commands it may run, as nodes that refer to each other by
 * index. A new tree replaces the previous one.
 */
@Value
public class ClientboundCommands implements ClientboundPacket {
	/**
	 * The tree's nodes, in the order the server sent them.
	 */
	@NotNull List<CommandNode> nodes;
	/**
	 * Index of the root node.
	 */
	int rootIndex;
}

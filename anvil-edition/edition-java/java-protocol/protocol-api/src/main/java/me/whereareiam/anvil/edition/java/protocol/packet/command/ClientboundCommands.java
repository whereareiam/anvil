package me.whereareiam.anvil.edition.java.protocol.packet.command;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.command.CommandNode;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The command tree the server declares to the player: the commands it may run, as nodes that refer to each other by
 * index. A new tree replaces the previous one.
 */
@Value
@FieldNameConstants
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

package me.whereareiam.anvil.capability.commands.model;

import lombok.Value;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * The command tree the server sent to the player: the commands it may run, as nodes that refer to their children by
 * index.
 */
@Value
public class CommandTree {
	@NotNull List<CommandTreeNode> nodes;
	/**
	 * Index of the root node.
	 */
	int root;

	/**
	 * Finds a command by its name, a literal child of the root.
	 *
	 * @param name the command's name without a slash, such as {@code login}
	 * @return the command's node, or empty when the player may not run the command
	 */
	public @NotNull Optional<CommandTreeNode> literal(@NotNull String name) {
		return nodes.get(root).getChildren().stream()
				.map(nodes::get)
				.filter(node -> node.getType() == CommandNodeType.LITERAL && node.getName().equals(name))
				.findFirst();
	}
}

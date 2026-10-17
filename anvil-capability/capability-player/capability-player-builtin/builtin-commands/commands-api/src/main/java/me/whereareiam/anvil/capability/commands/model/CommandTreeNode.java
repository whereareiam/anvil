package me.whereareiam.anvil.capability.commands.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import me.whereareiam.anvil.capability.commands.type.StringArgumentMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One node of the command tree the server sent, as the client reads it.
 */
@Value
@Builder
public class CommandTreeNode {
	/**
	 * The kind of the node.
	 */
	@NotNull CommandNodeType type;
	/**
	 * The literal's word or the argument's name, or {@code null} for the root.
	 */
	@Nullable String name;
	/**
	 * Indexes of the child nodes in the tree.
	 */
	@NotNull @Singular List<Integer> children;
	/**
	 * Index of the node the command continues at, such as the root after {@code execute run}, or {@code null}.
	 */
	@Nullable Integer redirect;
	/**
	 * Whether a command may end at this node.
	 */
	boolean executable;
	/**
	 * Whether the node needs permissions, so the client asks before it runs a command using it on its own. Releases
	 * before Minecraft 1.21.6 do not mark nodes.
	 */
	boolean restricted;
	/**
	 * Parser ID of an argument, such as {@code brigadier:string} or {@code minecraft:message}, or {@code null} for
	 * other nodes.
	 */
	@Nullable String parser;
	/**
	 * Mode of a {@code brigadier:string} argument, or {@code null} for other nodes.
	 */
	@Nullable StringArgumentMode stringMode;
	/**
	 * Lowest value of a number argument, or {@code null} for none.
	 */
	@Nullable Double min;
	/**
	 * Highest value of a number argument, or {@code null} for none.
	 */
	@Nullable Double max;
}

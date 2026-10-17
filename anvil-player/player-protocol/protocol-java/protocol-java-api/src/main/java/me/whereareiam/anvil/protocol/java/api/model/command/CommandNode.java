package me.whereareiam.anvil.protocol.java.api.model.command;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.type.CommandNodeType;
import me.whereareiam.anvil.protocol.java.api.type.StringArgumentMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One node of the command tree the server declares, which refers to other nodes by their index in the tree.
 */
@Value
@Builder
public class CommandNode {
	/**
	 * The node's kind.
	 */
	@NotNull CommandNodeType type;
	/**
	 * The literal's word or the argument's name, or {@code null} for the root.
	 */
	@Nullable String name;
	/**
	 * Indexes of the child nodes.
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
	 * Whether the node needs permissions, so that the client asks before it runs a command using it on its own.
	 * Releases before Minecraft 1.21.6 do not send the flag and report {@code false}.
	 */
	boolean restricted;
	/**
	 * Vanilla ID of an argument's parser, {@code brigadier:} for the Brigadier types and {@code minecraft:} for the
	 * others, such as {@code brigadier:string} or {@code minecraft:message}; {@code null} for other nodes.
	 */
	@Nullable String parser;
	/**
	 * Mode of a {@code brigadier:string} argument, or {@code null} for other nodes.
	 */
	@Nullable StringArgumentMode stringMode;
	/**
	 * Lowest value of a Brigadier number argument as the server declares it, or {@code null} for other nodes.
	 */
	@Nullable Double min;
	/**
	 * Highest value of a Brigadier number argument as the server declares it, or {@code null} for other nodes.
	 */
	@Nullable Double max;
	/**
	 * Vanilla ID of the suggestions an argument offers, such as {@code minecraft:ask_server}, or {@code null} for
	 * none.
	 */
	@Nullable String suggestions;
}

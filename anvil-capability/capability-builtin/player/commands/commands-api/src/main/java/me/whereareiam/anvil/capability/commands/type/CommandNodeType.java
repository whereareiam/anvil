package me.whereareiam.anvil.capability.commands.type;

/**
 * Kind of a node of the command tree the server sends.
 */
public enum CommandNodeType {
	/**
	 * The root, whose children are the commands.
	 */
	ROOT,
	/**
	 * A fixed word, such as a command name.
	 */
	LITERAL,
	/**
	 * An argument read by its parser.
	 */
	ARGUMENT
}

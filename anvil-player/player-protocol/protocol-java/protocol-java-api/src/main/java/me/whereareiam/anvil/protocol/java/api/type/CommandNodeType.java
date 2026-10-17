package me.whereareiam.anvil.protocol.java.api.type;

/**
 * Kind of a node of the command tree the server declares.
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

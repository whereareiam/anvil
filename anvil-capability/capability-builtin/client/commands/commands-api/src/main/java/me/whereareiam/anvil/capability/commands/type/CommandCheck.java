package me.whereareiam.anvil.capability.commands.type;

/**
 * What the client finds when it checks a command it runs on its own, for a dialog button or a clicked message,
 * against the command tree the server sent. From Minecraft 1.21.6 the client sends only a command without issues
 * at once and asks the player about every other one.
 */
public enum CommandCheck {
	/**
	 * The command parses and needs no permissions, so the client sends it.
	 */
	NO_ISSUES,
	/**
	 * The command does not parse, so the client asks the player whether to run it anyway.
	 */
	PARSE_ERRORS,
	/**
	 * The command has a signed argument, such as the message of {@code /msg}, which the client cannot sign for a
	 * click. The client never sends it; it offers to put the command into the chat input or the clipboard.
	 */
	SIGNATURE_REQUIRED,
	/**
	 * The command uses a part the server marked as needing permissions, so the client asks the player whether to
	 * run it.
	 */
	PERMISSIONS_REQUIRED
}

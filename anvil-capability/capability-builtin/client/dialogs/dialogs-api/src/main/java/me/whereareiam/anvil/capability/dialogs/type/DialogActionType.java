package me.whereareiam.anvil.capability.dialogs.type;

/**
 * What a dialog button does when it is pressed, after the click actions of Minecraft dialogs.
 */
public enum DialogActionType {
	/**
	 * Runs the command in the action's value.
	 */
	RUN_COMMAND,
	/**
	 * Runs the command built from the action's template, in which each {@code $(key)} is replaced with the value of
	 * the input with that key.
	 */
	DYNAMIC_RUN_COMMAND,
	/**
	 * Sends a custom click with the action's ID and payload to the server.
	 */
	CUSTOM,
	/**
	 * Sends a custom click with the action's ID to the server, whose payload holds the value of every input under
	 * its key, on top of the action's additions.
	 */
	DYNAMIC_CUSTOM,
	/**
	 * Puts the command in the action's value into the chat input; nothing reaches the server.
	 */
	SUGGEST_COMMAND,
	/**
	 * Asks the player to open the URL in the action's value; nothing reaches the server.
	 */
	OPEN_URL,
	/**
	 * Copies the action's value to the clipboard; nothing reaches the server.
	 */
	COPY_TO_CLIPBOARD,
	/**
	 * Turns a book page; nothing reaches the server.
	 */
	CHANGE_PAGE,
	/**
	 * Opens another dialog on the client, which replaces the dialog the button belongs to; nothing reaches the
	 * server.
	 */
	SHOW_DIALOG,
	/**
	 * An action type this Anvil version does not know.
	 */
	OTHER
}

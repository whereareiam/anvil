package me.whereareiam.anvil.protocol.api.type;

/**
 * The action a click on styled text takes, as the game names it in text components.
 */
public enum ClickAction {
	/**
	 * Opens a URL after the player confirms it.
	 */
	OPEN_URL,
	/**
	 * Opens a file; only the game itself sends it, such as for a screenshot.
	 */
	OPEN_FILE,
	/**
	 * Runs a command, or sends chat, as the player.
	 */
	RUN_COMMAND,
	/**
	 * Puts text into the player's chat input.
	 */
	SUGGEST_COMMAND,
	/**
	 * Turns a book to a page.
	 */
	CHANGE_PAGE,
	/**
	 * Copies text to the clipboard.
	 */
	COPY_TO_CLIPBOARD,
	/**
	 * Shows a dialog, from Minecraft 1.21.6.
	 */
	SHOW_DIALOG,
	/**
	 * Sends the server a custom action with an identifier, from Minecraft 1.21.6.
	 */
	CUSTOM
}

package me.whereareiam.anvil.protocol.java.api.type;

/**
 * The click actions a dialog button runs, named after the action types of the game's dialogs.
 */
public enum DialogActionType {
	/**
	 * {@code minecraft:run_command}: runs the command in the action's value.
	 */
	RUN_COMMAND,
	/**
	 * {@code minecraft:dynamic/run_command}: runs the command built from the template in the action's value, in which
	 * each {@code $(key)} stands for the value of the input with that key.
	 */
	DYNAMIC_RUN_COMMAND,
	/**
	 * {@code minecraft:custom}: sends a custom click with the ID in the action's value and its payload.
	 */
	CUSTOM,
	/**
	 * {@code minecraft:dynamic/custom}: sends a custom click with the ID in the action's value, whose payload holds the
	 * value of every input under its key on top of the action's additions.
	 */
	DYNAMIC_CUSTOM,
	/**
	 * {@code minecraft:suggest_command}: puts the command in the action's value into the chat input.
	 */
	SUGGEST_COMMAND,
	/**
	 * {@code minecraft:open_url}: asks the player to open the URL in the action's value.
	 */
	OPEN_URL,
	/**
	 * {@code minecraft:copy_to_clipboard}: copies the action's value to the clipboard.
	 */
	COPY_TO_CLIPBOARD,
	/**
	 * {@code minecraft:change_page}: turns a book to the page in the action's value.
	 */
	CHANGE_PAGE,
	/**
	 * {@code minecraft:show_dialog}: opens another dialog, named from the registry or carried whole.
	 */
	SHOW_DIALOG,
	/**
	 * An action type the protocol does not model; the action's value holds its ID.
	 */
	OTHER
}

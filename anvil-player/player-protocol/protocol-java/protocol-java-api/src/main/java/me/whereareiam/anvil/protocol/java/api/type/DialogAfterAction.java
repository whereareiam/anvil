package me.whereareiam.anvil.protocol.java.api.type;

/**
 * What the client does with a dialog after one of its buttons ran its action, named as the game names it.
 */
public enum DialogAfterAction {
	/**
	 * Closes the dialog.
	 */
	CLOSE,
	/**
	 * Keeps the dialog open.
	 */
	NONE,
	/**
	 * Replaces the dialog with a waiting screen until the server shows another dialog or clears it.
	 */
	WAIT_FOR_RESPONSE
}

package me.whereareiam.anvil.capability.dialogs.type;

/**
 * What the client does with a dialog after one of its buttons ran its action.
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
	 * Replaces the dialog with a waiting screen until the server shows another dialog or clears it. The dialog stays
	 * the player's current one until then.
	 */
	WAIT_FOR_RESPONSE,
	/**
	 * An after-action this Anvil version does not know; pressing a button of the dialog fails without sending
	 * anything.
	 */
	OTHER
}

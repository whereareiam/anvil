package me.whereareiam.anvil.capability.messages.type;

/**
 * Why the vanilla client's chat left out a chat message that reached it.
 */
public enum ChatDropReason {
	/**
	 * The client had no player: the message arrived before the server's login, such as while the client configured,
	 * or after a return to configuration and before the next login.
	 */
	NO_PLAYER,
	/**
	 * The server's text filter hid the whole player message.
	 */
	FULLY_FILTERED
}

package me.whereareiam.anvil.api.type;

/**
 * Whether a protocol library can sign a player in with a stored account. Each library determines it for itself
 * when the account signs in and whenever it derives a session for a player, and the account file records it.
 */
public enum AccountAvailability {
	/**
	 * The library derived a session from the account the last time it tried, so its players can use the account.
	 */
	AVAILABLE,
	/**
	 * The library's services refused the account, for example because it does not own the game the library
	 * speaks to. The library's reason says why; signing the account in again checks it anew.
	 */
	UNAVAILABLE,
	/**
	 * The library has not determined it yet, for example because the account was stored before the library was
	 * installed, or because its services could not be reached. A player that names the account lets the library
	 * determine it; a pool does not lend such an account.
	 */
	UNKNOWN
}

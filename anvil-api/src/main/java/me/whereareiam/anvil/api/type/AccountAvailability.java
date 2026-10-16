package me.whereareiam.anvil.api.type;

/**
 * Whether players of one Minecraft edition can sign in with a stored account. A protocol library of the edition
 * determines it when the account signs in, whenever it derives a session for a player, and when a pool checks an
 * account it has no record for; the account file records it for the edition, so it counts for every library of
 * that edition.
 */
public enum AccountAvailability {
	/**
	 * A library of the edition derived a session from the account the last time it tried, so the edition's players
	 * can use the account.
	 */
	AVAILABLE,
	/**
	 * The edition's services refused the account, for example because it does not own that edition of the game. The
	 * checking library's reason says why; signing the account in again checks it anew.
	 */
	UNAVAILABLE,
	/**
	 * No library of the edition has determined it, for example because none was installed when the account signed
	 * in, or because the edition's services could not be reached. A player that names the account lets its library
	 * determine it, and a pool that has no available account for the edition checks such an account while leasing.
	 */
	UNKNOWN
}

package me.whereareiam.anvil.capability.cookies.packet;

import org.jetbrains.annotations.NotNull;

/**
 * Receives what one release's packets tell a client about cookies, translated by {@link CookiesPackets} into
 * library-neutral values.
 *
 * <pre>{@code
 * Runnable detach = packets.listen(session, new CookiesPacketListener() {
 *     public void requested(String key) { packets.answer(session, key, jar.get(key)); }
 *     public void stored(String key, byte[] payload) { jar.put(key, payload); }
 *     public void closed() { jar.clear(); }
 * });
 * }</pre>
 */
public interface CookiesPacketListener {
	/**
	 * Reports that the server asked for a cookie, in the login, configuration or game phase. The client answers it
	 * with {@link CookiesPackets#answer}.
	 *
	 * @param key requested key, with its namespace
	 */
	void requested(@NotNull String key);

	/**
	 * Reports that the server stored a cookie on the client, in the configuration or game phase.
	 *
	 * @param key cookie key, with its namespace
	 * @param payload cookie payload
	 */
	void stored(@NotNull String key, byte @NotNull [] payload);

	/**
	 * Reports that the session's connection ended, which ends the cookies it held.
	 */
	void closed();
}

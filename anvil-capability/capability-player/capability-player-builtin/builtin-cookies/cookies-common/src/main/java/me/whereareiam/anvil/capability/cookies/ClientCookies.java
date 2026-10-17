package me.whereareiam.anvil.capability.cookies;

import me.whereareiam.anvil.capability.cookies.exchange.AnsweredRequest;
import me.whereareiam.anvil.capability.cookies.exchange.HeldCookies;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The cookies one player's client holds, as a vanilla client keeps them: each connection has its own, which end with
 * it, and the cookies given while the player is disconnected become the next connection's. Every change is emitted
 * to the host as the cookies the player now holds.
 */
final class ClientCookies {
	private final PlayerBindingContext<Object> player;
	private Map<String, byte[]> next = new LinkedHashMap<>();
	private Map<String, byte[]> connection = new LinkedHashMap<>();
	private @Nullable Object open;
	private long version;

	ClientCookies(@NotNull PlayerBindingContext<Object> player) {
		this.player = player;
	}

	/**
	 * Starts a connection with the cookies given since the previous one.
	 *
	 * @param nativeSession the native session that starts connecting
	 */
	synchronized void opened(@NotNull Object nativeSession) {
		open = nativeSession;
		connection = next;
		next = new LinkedHashMap<>();
		emit();
	}

	/**
	 * Ends a connection and its cookies.
	 *
	 * @param nativeSession the native session whose connection ended
	 */
	synchronized void closed(@NotNull Object nativeSession) {
		if (open != nativeSession) return;

		open = null;
		connection = new LinkedHashMap<>();
		emit();
	}

	/**
	 * Returns the payload a request of a session is answered with and reports the request, when the session's
	 * connection is the open one; a request of an earlier connection is answered with none and not reported.
	 *
	 * @param nativeSession the native session the request arrived on
	 * @param key requested key
	 * @return payload to answer with, or {@code null} for none
	 */
	synchronized byte @Nullable [] requested(@NotNull Object nativeSession, @NotNull String key) {
		if (open != nativeSession || !player.isCurrentNativeSession(nativeSession)) return null;

		byte[] answer = connection.get(key);
		player.emit(CookiesOperations.REQUESTED, new AnsweredRequest(key, answer));
		return answer;
	}

	/**
	 * Keeps a cookie a server stored through a session, when the session's connection is the open one.
	 *
	 * @param nativeSession the native session the cookie arrived on
	 * @param key cookie key
	 * @param payload cookie payload
	 */
	synchronized void serverStored(@NotNull Object nativeSession, @NotNull String key, byte @NotNull [] payload) {
		if (open != nativeSession || !player.isCurrentNativeSession(nativeSession)) return;

		connection.put(key, payload.clone());
		emit();
	}

	/**
	 * Gives the player a cookie: the open connection's or, without one, the next connection's.
	 *
	 * @param key cookie key
	 * @param payload cookie payload
	 * @return the cookies the player then holds
	 */
	synchronized @NotNull HeldCookies store(@NotNull String key, byte @NotNull [] payload) {
		held().put(key, payload.clone());
		return emit();
	}

	/**
	 * Takes a cookie away from the player: from the open connection or, without one, from the next connection.
	 *
	 * @param key cookie key
	 * @return the cookies the player then holds
	 */
	synchronized @NotNull HeldCookies remove(@NotNull String key) {
		held().remove(key);
		return emit();
	}

	private Map<String, byte[]> held() {
		return open == null ? next : connection;
	}

	private HeldCookies emit() {
		Map<String, byte[]> copy = new HashMap<>();
		held().forEach((key, payload) -> copy.put(key, payload.clone()));
		HeldCookies held = new HeldCookies(++version, Map.copyOf(copy));
		player.emit(CookiesOperations.HELD, held);
		return held;
	}
}

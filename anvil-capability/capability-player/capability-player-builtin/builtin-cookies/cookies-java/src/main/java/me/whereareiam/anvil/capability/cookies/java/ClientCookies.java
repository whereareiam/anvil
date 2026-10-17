package me.whereareiam.anvil.capability.cookies.java;

import me.whereareiam.anvil.capability.cookies.channel.CookiesChannel;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import me.whereareiam.anvil.capability.cookies.model.HeldCookies;
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
	private boolean open;
	private long version;

	ClientCookies(@NotNull PlayerBindingContext<Object> player) {
		this.player = player;
	}

	/**
	 * Starts a connection with the cookies given since the previous one.
	 */
	synchronized void opened() {
		open = true;
		connection = next;
		next = new LinkedHashMap<>();
		emit();
	}

	/**
	 * Ends the open connection with its cookies; cookies given from now on are the next connection's.
	 */
	synchronized void ended() {
		open = false;
		connection = new LinkedHashMap<>();
		emit();
	}

	/**
	 * Returns the payload a request of the current connection is answered with and reports the request.
	 *
	 * @param key requested key
	 * @return payload to answer with, or {@code null} for none
	 */
	synchronized byte @Nullable [] requested(@NotNull String key) {
		byte[] answer = connection.get(key);
		player.emit(CookiesChannel.REQUESTED, new CookieRequest(key, answer));
		return answer;
	}

	/**
	 * Keeps a cookie the server stored on the current connection.
	 *
	 * @param key cookie key
	 * @param payload cookie payload
	 */
	synchronized void serverStored(@NotNull String key, byte @NotNull [] payload) {
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

	/**
	 * Returns the cookies a change applies to: the open connection's, or without one the next connection's.
	 */
	private Map<String, byte[]> held() {
		return open ? connection : next;
	}

	private HeldCookies emit() {
		Map<String, byte[]> copy = new HashMap<>();
		(open ? connection : next).forEach((key, payload) -> copy.put(key, payload.clone()));
		HeldCookies held = new HeldCookies(++version, Map.copyOf(copy));
		player.emit(CookiesChannel.HELD, held);
		return held;
	}
}

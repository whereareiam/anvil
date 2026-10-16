package me.whereareiam.anvil.capability.cookies;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Holds the player's cookies as a vanilla client does and observes what the server asks for.
 *
 * <p>Cookies arrived in Minecraft 1.20.5. A server stores a cookie on the client under a key, such as
 * {@code example:ticket}, and asks for it later; the client answers every request, with the stored payload or with
 * none. This capability answers on its own, in the login, configuration and game phases, so a player with it behaves
 * like a vanilla client whether or not a test looks at its cookies.</p>
 *
 * <p>A vanilla client keeps the cookies of a connection only across a transfer to another server, which an Anvil
 * player does not follow. Every connection therefore starts with no cookies, except the ones the test stored with
 * {@link #store(String, byte[])} while the player was disconnected, as if it had arrived through a transfer. The
 * history of requests is kept across reconnects.</p>
 *
 * <p>A key without a namespace means the {@code minecraft} namespace, as in the game: {@code ticket} and
 * {@code minecraft:ticket} name the same cookie.</p>
 */
public interface Cookies extends PlayerCapability {
	/**
	 * Largest payload a cookie can hold, in bytes, as the game limits it.
	 */
	int MAX_PAYLOAD = 5120;

	/**
	 * Returns the cookies the player holds now, without waiting: the cookies of its connection or, while it is
	 * disconnected, the ones its next connection starts with.
	 *
	 * @return immutable snapshot of payloads by key, each key with its namespace; each payload is a copy
	 */
	@NotNull Map<String, byte[]> stored();

	/**
	 * Waits until the player holds a cookie under a key, using the default timeout.
	 *
	 * @param key cookie key
	 * @return copy of the cookie's payload
	 */
	default byte @NotNull [] stored(@NotNull String key) {
		return stored(key, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until the player holds a cookie under a key, for example after the server stored it.
	 *
	 * @param key cookie key
	 * @param timeout maximum wait
	 * @return copy of the cookie's payload
	 */
	byte @NotNull [] stored(@NotNull String key, @NotNull Duration timeout);

	/**
	 * Gives the player a cookie, replacing one with the same key. While the player is connected the cookie belongs to
	 * the connection; while it is disconnected, its next connection starts with it.
	 *
	 * @param key cookie key
	 * @param payload cookie payload, at most {@link #MAX_PAYLOAD} bytes; the capability keeps a copy
	 * @throws IllegalArgumentException when the key is not a valid resource location or the payload is too large
	 */
	void store(@NotNull String key, byte @NotNull [] payload);

	/**
	 * Takes a cookie away from the player, so the next request for it is answered with none.
	 *
	 * @param key cookie key
	 * @throws IllegalArgumentException when the key is not a valid resource location
	 */
	void remove(@NotNull String key);

	/**
	 * Returns every cookie request the server made, in order and across reconnects, without waiting.
	 *
	 * @return immutable snapshot of the requests
	 */
	@NotNull List<CookieRequest> requests();

	/**
	 * Captures the current end of the request history. A wait that starts from a checkpoint ignores every request
	 * made before it, so a key that is requested on every join is not satisfied by an earlier request.
	 *
	 * <pre>{@code
	 * int before = cookies.checkpoint();
	 * session.rejoin();
	 * CookieRequest request = cookies.requested("example:ticket", before, Duration.ofSeconds(10));
	 * assertNull(request.getAnswer());
	 * }</pre>
	 *
	 * @return number of requests made so far
	 */
	int checkpoint();

	/**
	 * Waits for the first request for a key after a checkpoint, using the default timeout.
	 *
	 * @param key cookie key
	 * @param after checkpoint; only requests made after it are considered
	 * @return the request, with the answer the player gave and the checkpoint directly after it
	 */
	default @NotNull CookieRequest requested(@NotNull String key, int after) {
		return requested(key, after, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the first request for a key after a checkpoint.
	 *
	 * @param key cookie key
	 * @param after checkpoint; only requests made after it are considered
	 * @param timeout maximum wait
	 * @return the request, with the answer the player gave and the checkpoint directly after it
	 */
	@NotNull CookieRequest requested(@NotNull String key, int after, @NotNull Duration timeout);

	/**
	 * Expects that the server does not ask for a key after a checkpoint for a duration. It fails as soon as such a
	 * request arrives and returns normally once the duration has passed without one.
	 *
	 * @param key cookie key
	 * @param after checkpoint; only requests made after it are considered
	 * @param duration how long no such request may arrive
	 */
	void notRequested(@NotNull String key, int after, @NotNull Duration duration);

	/**
	 * Expects a request for a key after a checkpoint, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #requested(String, int, Duration)} with the same arguments returns the request at once.
	 *
	 * @param key cookie key
	 * @param after checkpoint; only requests made after it are considered
	 * @return expectation met once such a request has been made
	 */
	@NotNull Expectation expectRequested(@NotNull String key, int after);
}

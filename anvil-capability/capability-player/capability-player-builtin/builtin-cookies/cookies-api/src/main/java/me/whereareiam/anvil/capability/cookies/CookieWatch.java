package me.whereareiam.anvil.capability.cookies;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * Follows the cookie requests the server makes from the moment the watch was created. Each wait continues after the
 * request the previous one found, so a key that is requested on every join is matched once per wait and never by a
 * request from before the watch. A key without a namespace means the {@code minecraft} namespace.
 *
 * <pre>{@code
 * CookieWatch asked = cookies.watch();
 * session.rejoin();
 * CookieRequest request = asked.requested("example:ticket", Duration.ofSeconds(10));
 * assertNull(request.getAnswer());
 * }</pre>
 */
public interface CookieWatch {
	/**
	 * Waits, using the default timeout, for the next request for a key.
	 *
	 * @param key cookie key
	 * @return the request and the answer the player gave
	 */
	default @NotNull CookieRequest requested(@NotNull String key) {
		return requested(key, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the next request for a key. The following wait looks only at later requests.
	 *
	 * @param key cookie key
	 * @param timeout maximum wait
	 * @return the request and the answer the player gave
	 * @throws IllegalArgumentException when the key is not a valid resource location
	 * @throws IllegalStateException when the server does not ask for the key in time
	 */
	@NotNull CookieRequest requested(@NotNull String key, @NotNull Duration timeout);

	/**
	 * Expects that the server does not ask for a key for a duration. It fails as soon as the server has asked for it
	 * since the previous wait and returns normally once the duration has passed without such a request. It does not
	 * move the watch.
	 *
	 * @param key cookie key
	 * @param duration how long the server must not ask
	 * @throws IllegalArgumentException when the key is not a valid resource location
	 * @throws IllegalStateException when the server asks for the key
	 */
	void neverRequested(@NotNull String key, @NotNull Duration duration);

	/**
	 * Expects the next request for a key, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #requested(String, Duration)} for the same key returns the request at once.
	 *
	 * @param key cookie key
	 * @return expectation met once the server has asked for the key since the previous wait
	 * @throws IllegalArgumentException when the key is not a valid resource location
	 */
	@NotNull Expectation expectRequested(@NotNull String key);

	/**
	 * Returns the requests made since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread requests, oldest first
	 */
	@NotNull List<CookieRequest> unread();
}

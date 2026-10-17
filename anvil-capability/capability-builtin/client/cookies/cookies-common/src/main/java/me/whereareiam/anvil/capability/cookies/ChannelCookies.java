package me.whereareiam.anvil.capability.cookies;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.cookies.exchange.CookieKey;
import me.whereareiam.anvil.capability.cookies.exchange.CookieKeys;
import me.whereareiam.anvil.capability.cookies.exchange.HeldCookies;
import me.whereareiam.anvil.capability.cookies.exchange.StoredCookie;
import me.whereareiam.anvil.capability.cookies.model.CookieRequest;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Cookies capability driven over a player's typed channel: the worker's cookies binding answers the server and
 * reports every request and every change of the cookies the player holds, which this side keeps; changes the test
 * makes go to the binding. The channel releases the event subscriptions together with the player.
 */
final class ChannelCookies implements Cookies {
	private final CapabilityChannel channel;
	private final CopyOnWriteArrayList<CookieRequest> requests = new CopyOnWriteArrayList<>();
	private volatile @Nullable HeldCookies held;

	ChannelCookies(@NotNull CapabilityChannel channel) {
		this.channel = channel;
		channel.subscribe(CookiesOperations.REQUESTED, request -> requests.add(new CookieRequest(request.getKey(), request.getAnswer())));
		channel.subscribe(CookiesOperations.HELD, this::hold);
	}

	@Override
	public @NotNull Map<String, byte[]> stored() {
		HeldCookies current = held;
		if (current == null) return Map.of();

		Map<String, byte[]> copy = new HashMap<>();
		current.getCookies().forEach((key, payload) -> copy.put(key, payload.clone()));
		return Map.copyOf(copy);
	}

	@Override
	public byte @NotNull [] stored(@NotNull String key, @NotNull Duration timeout) {
		String normalized = CookieKeys.normalize(key);
		channel.await(() -> payload(normalized) != null, "hold a cookie under '" + normalized + "'", timeout);
		return Objects.requireNonNull(payload(normalized)).clone();
	}

	@Override
	public void store(@NotNull String key, byte @NotNull [] payload) {
		String normalized = CookieKeys.normalize(key);
		if (payload.length > MAX_PAYLOAD)
			throw new IllegalArgumentException("Cookie '" + normalized + "' holds " + payload.length + " bytes; a cookie holds at most "
					+ MAX_PAYLOAD);

		hold(channel.request(CookiesOperations.STORE, new StoredCookie(normalized, payload.clone())));
	}

	@Override
	public void remove(@NotNull String key) {
		hold(channel.request(CookiesOperations.REMOVE, new CookieKey(CookieKeys.normalize(key))));
	}

	@Override
	public @NotNull List<CookieRequest> requests() {
		return List.copyOf(requests);
	}

	@Override
	public @NotNull CookieWatch watch() {
		return new Watch(new HistoryCursor<>(this::requests));
	}

	/**
	 * Keeps the latest cookies the player holds; an older change that arrives late is ignored.
	 */
	private synchronized void hold(@Nullable HeldCookies change) {
		if (change == null) return;

		HeldCookies current = held;
		if (current == null || change.getVersion() > current.getVersion()) held = change;
	}

	private byte @Nullable [] payload(String key) {
		HeldCookies current = held;
		return current == null ? null : current.getCookies().get(key);
	}

	/**
	 * A watch over this player's cookie requests, which waits through the player's channel.
	 */
	@RequiredArgsConstructor
	private final class Watch implements CookieWatch {
		private final HistoryCursor<CookieRequest> cursor;

		@Override
		public @NotNull CookieRequest requested(@NotNull String key, @NotNull Duration timeout) {
			Predicate<CookieRequest> request = forKey(key);
			channel.await(() -> cursor.find(request) != null, describe(key), timeout);
			return Objects.requireNonNull(cursor.take(request));
		}

		@Override
		public void neverRequested(@NotNull String key, @NotNull Duration duration) {
			Predicate<CookieRequest> request = forKey(key);
			try {
				channel.await(() -> cursor.find(request) != null, describe(key), duration);
			} catch (IllegalStateException quiet) {
				if (cursor.find(request) == null) return;
			}

			throw new IllegalStateException("Server asked the player for the cookie '" + CookieKeys.normalize(key)
					+ "', which it must not ask for");
		}

		@Override
		public @NotNull Expectation expectRequested(@NotNull String key) {
			Predicate<CookieRequest> request = forKey(key);
			return Expectation.of(describe(key), () -> cursor.find(request) != null);
		}

		@Override
		public @NotNull List<CookieRequest> unread() {
			return cursor.unread();
		}

		private static Predicate<CookieRequest> forKey(String key) {
			String normalized = CookieKeys.normalize(key);
			return request -> request.getKey().equals(normalized);
		}

		private static String describe(String key) {
			return "be asked for the cookie '" + CookieKeys.normalize(key) + "'";
		}
	}
}

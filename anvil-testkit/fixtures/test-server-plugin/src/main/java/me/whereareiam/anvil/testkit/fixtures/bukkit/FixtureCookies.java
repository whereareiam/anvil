package me.whereareiam.anvil.testkit.fixtures.bukkit;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Stores cookies on a player and asks for them through the server API that Minecraft 1.20.5 added, reported back as
 * chat. The fixture compiles against an older API, so it reaches the cookie methods by reflection; a server without
 * them answers {@code anvil:cookie:unsupported}.
 */
final class FixtureCookies {
	private FixtureCookies() {
		throw new AssertionError("No instances");
	}

	/**
	 * Stores a cookie with a text payload and confirms it as {@code anvil:cookie-stored:<key>}.
	 */
	static void store(Player player, String key, String value) {
		Method store = method(player, "storeCookie", NamespacedKey.class, byte[].class);
		if (store == null) {
			player.sendMessage("anvil:cookie:unsupported");
			return;
		}

		invoke(store, player, namespaced(key), value.getBytes(StandardCharsets.UTF_8));
		player.sendMessage("anvil:cookie-stored:" + key);
	}

	/**
	 * Asks the client for a cookie and reports the answer as {@code anvil:cookie:<key>:<value>}, with {@code none}
	 * for a client that holds no cookie under the key.
	 */
	static void request(Player player, String key) {
		Method retrieve = method(player, "retrieveCookie", NamespacedKey.class);
		if (retrieve == null) {
			player.sendMessage("anvil:cookie:unsupported");
			return;
		}

		CompletableFuture<?> answer = (CompletableFuture<?>) invoke(retrieve, player, namespaced(key));
		answer.whenComplete((payload, failure) -> {
			if (failure != null) {
				player.sendMessage("anvil:cookie:" + key + ":failed");
				return;
			}

			String value = payload == null ? "none" : new String((byte[]) payload, StandardCharsets.UTF_8);
			player.sendMessage("anvil:cookie:" + key + ":" + value);
		});
	}

	@SuppressWarnings("deprecation")
	private static NamespacedKey namespaced(String key) {
		int separator = key.indexOf(':');
		return new NamespacedKey(key.substring(0, separator), key.substring(separator + 1));
	}

	private static Method method(Player player, String name, Class<?>... parameters) {
		try {
			return player.getClass().getMethod(name, parameters);
		} catch (NoSuchMethodException unsupported) {
			return null;
		}
	}

	private static Object invoke(Method method, Player player, Object... arguments) {
		try {
			return method.invoke(player, arguments);
		} catch (IllegalAccessException | InvocationTargetException failure) {
			throw new IllegalStateException("Could not call " + method.getName() + " on the player", failure);
		}
	}
}

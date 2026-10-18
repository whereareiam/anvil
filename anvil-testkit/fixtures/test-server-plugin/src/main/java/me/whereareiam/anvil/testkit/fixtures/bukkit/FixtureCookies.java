package me.whereareiam.anvil.testkit.fixtures.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Stores cookies on a player and asks for them, reported back as chat, through the server API that Minecraft 1.20.5
 * added. The plugin compiles against an older API, so the module {@code V1_21_1}, compiled against Paper 1.21.1,
 * implements it, and only servers from 1.20.5 on load that module.
 */
public interface FixtureCookies {
	/**
	 * Stores a cookie with a text payload and confirms it as {@code anvil:cookie-stored:<key>}.
	 *
	 * @param player the player
	 * @param key cookie key with its namespace
	 * @param value text payload
	 */
	void store(Player player, String key, String value);

	/**
	 * Asks the client for a cookie and reports the answer as {@code anvil:cookie:<key>:<value>}, with {@code none} for a
	 * client that holds no cookie under the key.
	 *
	 * @param player the player
	 * @param key cookie key with its namespace
	 */
	void request(Player player, String key);

	/**
	 * Loads the implementation for this server, or none before Minecraft 1.20.5.
	 *
	 * @param loader the plugin's class loader
	 * @return the implementation, or null when the server has no cookies
	 */
	static FixtureCookies load(ClassLoader loader) {
		if (!supported(Bukkit.getBukkitVersion())) return null;

		Iterator<FixtureCookies> implementations = ServiceLoader.load(FixtureCookies.class, loader).iterator();
		return implementations.hasNext() ? implementations.next() : null;
	}

	/**
	 * Tells whether a Bukkit version, such as {@code 1.21.1-R0.1-SNAPSHOT}, has the cookie API.
	 */
	static boolean supported(String bukkitVersion) {
		return BukkitVersion.atLeast(bukkitVersion, 1, 20, 5);
	}
}

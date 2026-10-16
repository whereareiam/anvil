package me.whereareiam.anvil.capability.cookies.exchange;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Cookie keys as the game reads them: resource locations, whose namespace is {@code minecraft} when left out.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CookieKeys {
	private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
	private static final Pattern PATH = Pattern.compile("[a-z0-9_.\\-/]+");

	/**
	 * Returns a key with its namespace.
	 *
	 * @param key key with or without a namespace
	 * @return key with its namespace, such as {@code minecraft:ticket}
	 * @throws IllegalArgumentException when the key is not a valid resource location
	 */
	public static @NotNull String normalize(@NotNull String key) {
		int separator = key.indexOf(':');
		String namespace = separator < 0 ? "minecraft" : key.substring(0, separator);
		String path = separator < 0 ? key : key.substring(separator + 1);
		if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches())
			throw new IllegalArgumentException("Cookie key '" + key + "' is not a valid resource location");

		return namespace + ":" + path;
	}
}

package me.whereareiam.anvil.capability.cookies.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A cookie key the host sends to the worker to take a cookie away from the player.
 */
@Value
public class CookieKey {
	/**
	 * The cookie's key, with its namespace, such as {@code example:ticket}.
	 */
	@NotNull String key;
}

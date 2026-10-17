package me.whereareiam.anvil.capability.cookies.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A cookie the host sends to the worker to give it to the player.
 */
@Value
public class StoredCookie {
	/**
	 * The cookie's key, with its namespace, such as {@code example:ticket}.
	 */
	@NotNull String key;
	/**
	 * The cookie's payload, at most 5120 bytes.
	 */
	byte @NotNull [] payload;
}

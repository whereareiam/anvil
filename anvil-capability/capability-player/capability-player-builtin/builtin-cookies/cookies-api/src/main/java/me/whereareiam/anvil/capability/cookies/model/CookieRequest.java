package me.whereareiam.anvil.capability.cookies.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A cookie request the server made, with the answer the player gave.
 */
@Value
public class CookieRequest {
	/**
	 * Requested key, with its namespace, such as {@code example:ticket}.
	 */
	@NotNull String key;
	/**
	 * Payload the player answered with, or {@code null} when it held no cookie under the key.
	 */
	byte @Nullable [] answer;
}

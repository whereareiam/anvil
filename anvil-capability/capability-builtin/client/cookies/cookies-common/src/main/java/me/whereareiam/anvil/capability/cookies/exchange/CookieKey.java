package me.whereareiam.anvil.capability.cookies.exchange;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A cookie key, with its namespace, sent to the worker to take a cookie away.
 */
@Value
public class CookieKey {
	@NotNull String key;
}

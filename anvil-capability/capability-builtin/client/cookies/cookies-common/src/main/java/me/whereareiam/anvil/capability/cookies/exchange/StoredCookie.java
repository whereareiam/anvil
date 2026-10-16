package me.whereareiam.anvil.capability.cookies.exchange;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A cookie the test gives the player, sent to the worker.
 */
@Value
public class StoredCookie {
	@NotNull String key;
	byte @NotNull [] payload;
}

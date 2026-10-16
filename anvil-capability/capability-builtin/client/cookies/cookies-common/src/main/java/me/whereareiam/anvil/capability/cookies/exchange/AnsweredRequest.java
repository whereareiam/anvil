package me.whereareiam.anvil.capability.cookies.exchange;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A cookie request of the player's current connection and the answer the worker sent.
 */
@Value
public class AnsweredRequest {
	@NotNull String key;
	byte @Nullable [] answer;
}

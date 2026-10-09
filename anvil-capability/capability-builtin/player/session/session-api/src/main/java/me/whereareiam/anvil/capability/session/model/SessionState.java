package me.whereareiam.anvil.capability.session.model;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable immediate snapshot of a player's current protocol session.
 */
@Value
@Accessors(fluent = true)
@Builder
public class SessionState {
	boolean connected;
	@Nullable String kickReason;
}

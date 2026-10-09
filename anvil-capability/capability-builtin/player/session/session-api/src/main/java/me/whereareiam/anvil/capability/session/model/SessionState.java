package me.whereareiam.anvil.capability.session.model;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import me.whereareiam.anvil.api.type.DisconnectCause;
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
	/**
	 * What ended the last connection: the server's kick, a refused request for online authentication, a
	 * connection that closed without a reason, or the player's own disconnect. Null while connected and when
	 * the protocol library does not report it.
	 */
	@Nullable DisconnectCause disconnectCause;
}

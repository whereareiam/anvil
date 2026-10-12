package me.whereareiam.anvil.protocol.api.worker.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import org.jetbrains.annotations.NotNull;

/**
 * A stored account that {@link WorkerAuthentication#resolve} signed in: the session a player signs in with, and the
 * record the native worker stores in place of the one it read, such as one with refreshed tokens.
 *
 * <pre>{@code
 * return ResolvedAccount.builder()
 *         .account(account.toBuilder().credentials(manager.toJson().toString()).build())
 *         .session(session)
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class ResolvedAccount {
	/**
	 * Record to store, which may carry refreshed credentials.
	 */
	@NotNull AccountRecord account;
	/**
	 * Session the player signs in with.
	 */
	@NotNull AuthenticationSession session;
}

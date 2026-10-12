package me.whereareiam.anvil.protocol.api.worker.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a library receives from a stored account to derive a session: the account's Microsoft token and the session
 * the same library cached in the account file the last time, which only that library reads. The native worker reads
 * both from the account's private file and hands them to {@link WorkerAuthentication#derive}.
 *
 * <pre>{@code
 * AccountCredentials credentials = AccountCredentials.builder()
 *         .accountId("alice")
 *         .token(token)
 *         .cachedSession(null)
 *         .build();
 * }</pre>
 */
@Value
@Builder
public class AccountCredentials {
	/**
	 * Owner-local account ID, which messages about the account name.
	 */
	@NotNull String accountId;
	/**
	 * The account's current Microsoft token.
	 */
	@NotNull MicrosoftToken token;
	/**
	 * The session this library derived last time, as the text the library returned in
	 * {@link DerivedSession#getCachedSession()}, or null when it has none. Its own copy of a Microsoft token can be
	 * older than {@link #getToken()}, which another library may have refreshed since.
	 */
	@ToString.Exclude
	@Nullable String cachedSession;
}

package me.whereareiam.anvil.protocol.api.worker.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.worker.WorkerAuthentication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What {@link WorkerAuthentication#derive} made of a stored account: either a session its players sign in with, or
 * the library's refusal when its services refuse the account, such as one that does not own the game the library
 * speaks to. Both carry the account's Microsoft token as the library left it, which the native worker stores in
 * place of the one it handed over when the library refreshed it.
 *
 * <pre>{@code
 * return DerivedSession.available(session, manager.toJson().toString(), token);
 * return DerivedSession.unavailable("Account 'alice' owns no Example Edition profile", token);
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DerivedSession {
	/**
	 * Session the player signs in with, or null when the library refused the account.
	 */
	@Nullable AuthenticationSession session;
	/**
	 * What the library keeps of the derivation for the next one, in a text form it defines, such as its serialized
	 * token chain; the worker stores it in the account file for this library only. Null when there is nothing to keep.
	 */
	@ToString.Exclude
	@Nullable String cachedSession;
	/**
	 * The library's reason for refusing the account, naming it, or null when the session is available.
	 */
	@Nullable String refusal;
	/**
	 * The account's Microsoft token after the derivation, refreshed or not.
	 */
	@NotNull MicrosoftToken token;

	/**
	 * Describes a session the library's players can sign in with.
	 *
	 * @param session session to sign in with
	 * @param cachedSession what the library keeps for the next derivation, or null
	 * @param token the account's Microsoft token after the derivation
	 * @return available result
	 */
	public static @NotNull DerivedSession available(
			@NotNull AuthenticationSession session,
			@Nullable String cachedSession,
			@NotNull MicrosoftToken token
	) {
		return new DerivedSession(session, cachedSession, null, token);
	}

	/**
	 * Describes an account the library's services refused. The library words the reason for people: it names the
	 * account and what it lacks, never credentials.
	 *
	 * @param refusal the library's reason
	 * @param token the account's Microsoft token after the derivation
	 * @return refused result
	 */
	public static @NotNull DerivedSession unavailable(@NotNull String refusal, @NotNull MicrosoftToken token) {
		return new DerivedSession(null, null, refusal, token);
	}

	/**
	 * Returns whether the library derived a session.
	 *
	 * @return true when {@link #getSession()} is present
	 */
	public boolean isAvailable() {
		return session != null;
	}
}

package me.whereareiam.anvil.api.model.player;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import me.whereareiam.anvil.api.type.AuthenticationMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * How one simulated player logs in: its authentication mode and what identifies it in that mode. Each factory
 * creates one valid combination, so a login can never carry a username next to an account or an account ID next
 * to a session identity.
 *
 * <ul>
 *     <li>{@link #offline()} and {@link #offline(String)} log in without an account, as the player's name or
 *     as another username that several players may share;</li>
 *     <li>{@link #account(AuthenticationMode, String)} signs in with an account stored outside the project,
 *     whose profile supplies the username;</li>
 *     <li>{@link #session(AuthenticationMode, SessionIdentity)} signs in with an identity that a session server
 *     chosen by the scenario verifies;</li>
 *     <li>{@link #leased(AuthenticationMode)} signs in with whichever account an
 *     {@code AccountPool.AccountLease} hands to {@code PlayerManager.create(PlayerOptions, AccountLease)}.</li>
 * </ul>
 *
 * <pre>{@code
 * players.create(PlayerOptions.builder().name("alice-again").login(PlayerLogin.offline("Alice")).build());
 * players.create(PlayerOptions.builder().name("premium")
 *         .login(PlayerLogin.account(AuthenticationMode.ONLINE, "main"))
 *         .build());
 * }</pre>
 *
 * <p>Whether the login suits the process the player joins, for example an offline login on an online-mode
 * process or an account that the player's protocol library does not store, is checked when the player is
 * created.</p>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlayerLogin {
	private static final PlayerLogin OFFLINE = new PlayerLogin(AuthenticationMode.OFFLINE, null, null, null);

	/**
	 * Authentication mode the player logs in with.
	 */
	@NotNull AuthenticationMode authentication;

	/**
	 * Username of an offline login, or null when an offline player logs in as its name or the login uses an
	 * account, which supplies the username itself.
	 */
	@Nullable String username;

	/**
	 * Local account identifier resolved by the player's protocol library, or null when the login uses no stored
	 * account, a session identity or a leased account.
	 */
	@Nullable String accountId;

	/**
	 * Identity verified by a session server the scenario chose, or null when the login uses none.
	 */
	@Nullable SessionIdentity sessionIdentity;

	/**
	 * Returns the default login: offline, with the player's name as its username.
	 *
	 * @return offline login without a username of its own
	 */
	public static @NotNull PlayerLogin offline() {
		return OFFLINE;
	}

	/**
	 * Returns an offline login with a username that differs from the player's name. Several players may log in
	 * with one username, so a test can connect the same username twice.
	 *
	 * <pre>{@code
	 * PlayerOptions.builder().name("alice-again").login(PlayerLogin.offline("Alice")).build();
	 * }</pre>
	 *
	 * @param username Minecraft username the player logs in with
	 * @return offline login with that username
	 * @throws IllegalArgumentException when the username is blank
	 */
	public static @NotNull PlayerLogin offline(@NotNull String username) {
		return new PlayerLogin(AuthenticationMode.OFFLINE, requireText(username, "username"), null, null);
	}

	/**
	 * Returns a login with an account stored outside the project, selected by its local ID. The account's
	 * profile supplies the username, and the account must be stored by the player's protocol library.
	 *
	 * <pre>{@code
	 * PlayerLogin.account(AuthenticationMode.ON_REQUEST, "main");
	 * }</pre>
	 *
	 * @param authentication {@link AuthenticationMode#ONLINE} or {@link AuthenticationMode#ON_REQUEST}
	 * @param accountId local account identifier
	 * @return login with the stored account
	 * @throws IllegalArgumentException when the mode uses no account or the account ID is blank
	 */
	public static @NotNull PlayerLogin account(@NotNull AuthenticationMode authentication, @NotNull String accountId) {
		return new PlayerLogin(requireAccountMode(authentication), null, requireText(accountId, "account ID"), null);
	}

	/**
	 * Returns a login with an identity that a session server chosen by the scenario verifies, instead of a
	 * stored account. The process the player joins must verify logins against the same session server.
	 *
	 * <pre>{@code
	 * PlayerLogin.session(AuthenticationMode.ONLINE, yggdrasil.register("Alice"));
	 * }</pre>
	 *
	 * @param authentication {@link AuthenticationMode#ONLINE} or {@link AuthenticationMode#ON_REQUEST}
	 * @param identity identity the session server verifies
	 * @return login with the session identity
	 * @throws IllegalArgumentException when the mode uses no account
	 */
	public static @NotNull PlayerLogin session(@NotNull AuthenticationMode authentication, @NotNull SessionIdentity identity) {
		return new PlayerLogin(requireAccountMode(authentication), null, null, identity);
	}

	/**
	 * Returns a login whose account an exclusively reserved lease supplies. Only
	 * {@code PlayerManager.create(PlayerOptions, AccountLease)} accepts it; the leased account replaces it there.
	 *
	 * <pre>{@code
	 * players.create(PlayerOptions.builder().name("premium")
	 *         .login(PlayerLogin.leased(AuthenticationMode.ON_REQUEST))
	 *         .build(), accounts.lease());
	 * }</pre>
	 *
	 * @param authentication {@link AuthenticationMode#ONLINE} or {@link AuthenticationMode#ON_REQUEST}
	 * @return login waiting for a leased account
	 * @throws IllegalArgumentException when the mode uses no account
	 */
	public static @NotNull PlayerLogin leased(@NotNull AuthenticationMode authentication) {
		return new PlayerLogin(requireAccountMode(authentication), null, null, null);
	}

	/**
	 * Returns whether this login waits for the account of a lease, as created by
	 * {@link #leased(AuthenticationMode)}.
	 *
	 * @return true for an account mode without an account ID or session identity
	 */
	public boolean isLeased() {
		return authentication.usesAccount() && accountId == null && sessionIdentity == null;
	}

	private static AuthenticationMode requireAccountMode(AuthenticationMode authentication) {
		if (!authentication.usesAccount())
			throw new IllegalArgumentException(authentication + " authentication uses no account; use PlayerLogin.offline()");
		return authentication;
	}

	private static String requireText(String value, String description) {
		if (value.isBlank()) throw new IllegalArgumentException("A player login's " + description + " must not be blank");
		return value;
	}
}

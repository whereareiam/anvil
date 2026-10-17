package me.whereareiam.anvil.api.player;

import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.model.player.PlayerState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * A real network client exposed through version-independent capabilities.
 *
 * <p>Capabilities group related operations so the player API can grow without turning this
 * interface into one flat collection of unrelated methods.</p>
 */
public interface SimulatedPlayer extends CapabilityOwner<PlayerCapability> {
	/**
	 * Default maximum wait used by observations without an explicit timeout.
	 */
	@NotNull Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

	/**
	 * Returns the configured player name.
	 *
	 * @return player name
	 */
	@NotNull String name();

	/**
	 * Returns optional presentation details supplied when this player was created.
	 * The configured player name remains its connection and lookup identity.
	 *
	 * @return presentation metadata, or null when none was supplied
	 */
	default @Nullable PresentationMetadata metadata() {
		return null;
	}

	/**
	 * Returns the resolved Minecraft client version.
	 *
	 * @return client version
	 */
	@NotNull String clientVersion();

	/**
	 * Returns the profile the client asks for when it logs in: its offline profile, or the profile of the online
	 * account it signs in with.
	 *
	 * @return requested profile
	 */
	@NotNull PlayerProfile requestedProfile();

	/**
	 * Returns the profile the process the player joined granted at the latest login. A proxy or a login plugin may
	 * have changed it, such as a proxy that renames {@code Steve} to {@code Steve_Premium}. What a server or proxy
	 * knows about the player belongs to that process's Players capability.
	 *
	 * @return granted profile, or null before the player logged in and after it disconnected
	 */
	@Nullable PlayerProfile grantedProfile();

	/**
	 * Returns every profile a process granted the client at its logins, oldest first and without repeats, also after
	 * the player disconnected. A process capability finds the player by them in what the process reported, whatever
	 * UUID an earlier connection had. The default keeps no history and returns the current granted profile, or
	 * nothing while there is none.
	 *
	 * @return immutable list of the granted profiles
	 */
	default @NotNull List<PlayerProfile> grantedProfiles() {
		PlayerProfile granted = grantedProfile();
		return granted == null ? List.of() : List.of(granted);
	}

	/**
	 * Resolves a typed behavior available on this player.
	 *
	 * <pre>{@code
	 * Session session = player.capability(Session.class);
	 * session.connect();
	 * }</pre>
	 *
	 * @param type capability interface
	 * @param <C> capability type
	 * @return player-scoped capability implementation
	 * @throws CapabilityUnavailableException when the requested behavior is unavailable
	 */
	@NotNull <C extends PlayerCapability> C capability(@NotNull Class<C> type);

	/**
	 * Reports whether the requested typed behavior is available.
	 *
	 * @param type behavior interface
	 * @return {@code true} when the capability is available
	 */
	boolean hasCapability(@NotNull Class<? extends PlayerCapability> type);

	/**
	 * Waits, using the default timeout, until the first of several expectations is met.
	 *
	 * @param expectations expectations in order of preference when several are met at once
	 * @return index of the first expectation met
	 * @see #awaitFirst(Duration, Expectation...)
	 */
	default int awaitFirst(@NotNull Expectation... expectations) {
		return awaitFirst(DEFAULT_TIMEOUT, expectations);
	}

	/**
	 * Waits until the first of several expectations is met, for a test in which more than one outcome is correct,
	 * such as a race whose winner decides whether the player is answered or disconnected. When several are met by
	 * the time they are checked, the earliest in the arguments wins. The wait fails with the player's diagnostics
	 * when the player fails or none is met in time.
	 *
	 * <pre>{@code
	 * MessageWatch chat = messages.watch();
	 * messages.command("confirm 123456");
	 * int outcome = player.awaitFirst(Duration.ofSeconds(10),
	 *         chat.expectReceived(text -> text.contains("Invalid code")),
	 *         session.expectKicked());
	 * if (outcome == 1) assertTrue(session.kicked().contains("Reconnect"));
	 * }</pre>
	 *
	 * @param timeout maximum wait
	 * @param expectations at least one expectation, created by this player's capabilities or with
	 *                     {@link Expectation#of}
	 * @return index of the first expectation met
	 * @throws IllegalArgumentException when no expectation is given
	 * @throws IllegalStateException when none is met within the timeout
	 */
	int awaitFirst(@NotNull Duration timeout, @NotNull Expectation... expectations);

	/**
	 * Returns an immutable snapshot immediately without waiting.
	 *
	 * @return current player state
	 */
	@NotNull PlayerState state();

	/**
	 * Permanently releases and unregisters this player.
	 *
	 * <p>Calling this method repeatedly has no additional effect. Other operations are invalid after
	 * destruction.</p>
	 */
	void destroy();
}

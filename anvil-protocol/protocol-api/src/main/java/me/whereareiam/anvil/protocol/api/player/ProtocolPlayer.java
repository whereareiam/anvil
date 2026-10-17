package me.whereareiam.anvil.protocol.api.player;

import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Library-owned protocol client before dependency-discovered capabilities are composed around it.
 */
public interface ProtocolPlayer {
	/**
	 * Returns the configured player name.
	 *
	 * @return player name
	 */
	@NotNull String name();

	/**
	 * Returns the resolved native client version.
	 *
	 * @return Minecraft client version
	 */
	@NotNull String clientVersion();

	/**
	 * Returns the identifier of the protocol library that owns this client. Composition selects the
	 * player's capability providers by this identifier.
	 *
	 * @return protocol-library identifier
	 */
	@NotNull String libraryId();

	/**
	 * Returns the library release that speaks this client's version.
	 *
	 * @return selected library release
	 */
	@NotNull ProtocolRelease release();

	/**
	 * Returns the profile the client asks for when it logs in.
	 *
	 * @return requested profile
	 */
	@NotNull PlayerProfile requestedProfile();

	/**
	 * Returns the profile the joined process granted at the latest login.
	 *
	 * @return granted profile, or null before the player logged in and after it disconnected
	 */
	@Nullable PlayerProfile grantedProfile();

	/**
	 * Returns every profile a process granted the client at its logins, oldest first and without repeats, so that a
	 * capability can find what processes reported about earlier connections, whose UUID may differ. The default
	 * keeps no history and returns the current granted profile, or nothing while there is none.
	 *
	 * @return immutable list of the granted profiles
	 */
	default @NotNull List<PlayerProfile> grantedProfiles() {
		PlayerProfile granted = grantedProfile();
		return granted == null ? List.of() : List.of(granted);
	}

	/**
	 * Finds an implementation-specific service that capability providers may target through a stable API.
	 *
	 * @param type service type
	 * @param <T> service type
	 * @return matching service when supported
	 */
	@NotNull <T> Optional<T> findService(@NotNull Class<T> type);

	/**
	 * Returns the typed capability channel when the library supports native worker operations.
	 *
	 * @return channel when available
	 */
	default @NotNull Optional<ProtocolChannel> channel() {
		return Optional.empty();
	}

	/**
	 * Reports whether this library-owned player was permanently destroyed.
	 *
	 * @return destruction state
	 */
	boolean destroyed();

	/**
	 * Permanently releases the protocol client and its worker registration.
	 */
	void destroy();
}

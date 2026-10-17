package me.whereareiam.anvil.capability.api.player;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Player identity and lifetime that the player's owner, such as a protocol library, supplies to
 * capability composition.
 * The composed player owns permanent destruction through this input. Mechanism-specific inputs
 * can extend it with their own services without exposing those services to every player capability.
 */
public interface CapabilityPlayer {
	/**
	 * Returns the configured player name.
	 *
	 * @return player name
	 */
	@NotNull String name();

	/**
	 * Returns optional presentation metadata without changing the underlying player's identity.
	 *
	 * @return metadata, or null when no label was declared
	 */
	default @Nullable PresentationMetadata metadata() {
		return null;
	}

	/**
	 * Returns the selected Minecraft client version.
	 *
	 * @return client version
	 */
	@NotNull String clientVersion();

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
	 * Reports permanent destruction of the underlying player.
	 *
	 * @return whether the underlying player has been destroyed
	 */
	boolean destroyed();

	/**
	 * Permanently destroys the underlying player and its owned resources.
	 */
	void destroy();
}

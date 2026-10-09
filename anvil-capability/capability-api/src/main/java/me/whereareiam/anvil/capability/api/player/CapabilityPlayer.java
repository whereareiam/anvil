package me.whereareiam.anvil.capability.api.player;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

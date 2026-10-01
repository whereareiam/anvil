package me.whereareiam.anvil.capability.api.player;

import me.whereareiam.anvil.api.model.PresentationMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Player identity and backend lifetime supplied to capability composition.
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
	 * Returns optional presentation metadata without changing the backend's player identity.
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
	 * Reports permanent destruction of the backend player.
	 *
	 * @return whether the backend player has been destroyed
	 */
	boolean destroyed();

	/**
	 * Permanently releases the backend player and its owned resources.
	 */
	void destroy();
}

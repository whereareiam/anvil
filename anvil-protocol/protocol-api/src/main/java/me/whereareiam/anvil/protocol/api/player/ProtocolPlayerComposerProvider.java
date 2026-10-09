package me.whereareiam.anvil.protocol.api.player;

import org.jetbrains.annotations.NotNull;

/**
 * Service-provider contract for the public player composition layer.
 */
public interface ProtocolPlayerComposerProvider {
	/**
	 * Returns the stable composer identifier.
	 *
	 * @return composer identifier
	 */
	@NotNull String id();

	/**
	 * Creates a composer for players of every installed protocol library. The composer selects
	 * library-specific behavior for each player from {@link ProtocolPlayer#libraryId()}.
	 *
	 * @return player composer
	 */
	@NotNull ProtocolPlayerComposer create();
}

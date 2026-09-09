package me.whereareiam.anvil.protocol.api.player;

import org.jetbrains.annotations.NotNull;

/**
 * Service-provider contract for the public player composition layer.
 */
public interface ProtocolPlayerComposerProvider {
	/*
	 * Returns the stable composer identifier.
	 *
	 * @return composer identifier
	 */
	@NotNull String id();

	/**
	 * Creates a composer compatible with the selected protocol backend.
	 *
	 * @param protocolId resolved protocol-provider ID
	 * @return player composer
	 */
	@NotNull ProtocolPlayerComposer create(@NotNull String protocolId);
}

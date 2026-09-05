package me.whereareiam.anvil.protocol.api.provider;

import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolSupport;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Runtime backend capable of creating one or more real protocol clients.
 */
public interface ProtocolBackend extends AutoCloseable {
	/**
	 * Returns the stable provider ID owning this backend.
	 *
	 * @return protocol-provider ID
	 */
	@NotNull String id();

	/**
	 * Returns verified protocol support entries.
	 *
	 * @return verified protocol support
	 */
	@NotNull Collection<ProtocolSupport> supportedProtocols();

	/**
	 * Creates an initially disconnected simulated player.
	 *
	 * @param request explicit version and connection request
	 * @return controlled protocol client
	 */
	@NotNull ProtocolPlayer create(@NotNull PlayerRequest request);

	/**
	 * Stops all clients and worker processes owned by this backend.
	 */
	@Override
	void close();
}

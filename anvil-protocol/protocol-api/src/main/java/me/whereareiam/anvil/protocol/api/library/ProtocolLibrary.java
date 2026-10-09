package me.whereareiam.anvil.protocol.api.library;

import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Run-scoped protocol library that creates real protocol clients for its releases.
 * The engine creates one instance per library on first use and closes it when the engine closes.
 */
public interface ProtocolLibrary extends AutoCloseable {
	/**
	 * Returns the identifier of the provider that created this library.
	 *
	 * @return protocol-library identifier
	 */
	@NotNull String id();

	/**
	 * Returns the releases this library instance can run.
	 *
	 * @return immutable releases
	 */
	@NotNull List<ProtocolRelease> releases();

	/**
	 * Creates an initially disconnected simulated player for the release selected by the engine.
	 *
	 * @param request selected Minecraft version and release, address, and authentication
	 * @return controlled protocol client
	 */
	@NotNull ProtocolPlayer create(@NotNull PlayerRequest request);

	/**
	 * Stops all clients and worker processes owned by this library.
	 */
	@Override
	void close();
}

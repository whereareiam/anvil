package me.whereareiam.anvil.protocol.adapter.api.binding;

import org.jetbrains.annotations.NotNull;

/**
 * Native connection lifecycle, independent of shared worker state and capability discovery.
 */
public interface ProtocolWorkerSession extends AutoCloseable {
	/**
	 * Starts native login, leaving an already connected session unchanged.
	 */
	void connect();

	/**
	 * Disconnects the current session.
	 */
	void disconnect();

	/**
	 * Replaces the current session and starts login again.
	 */
	void rejoin();

	/**
	 * Sends a packet owned by this session's library.
	 */
	void send(@NotNull Object packet);

	/**
	 * Releases the native session permanently.
	 */
	@Override
	void close();
}

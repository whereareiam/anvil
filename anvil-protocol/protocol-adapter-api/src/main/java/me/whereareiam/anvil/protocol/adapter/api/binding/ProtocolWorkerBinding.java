package me.whereareiam.anvil.protocol.adapter.api.binding;

import org.jetbrains.annotations.NotNull;

/**
 * Native session binding for a compatible family of packet-library APIs.
 * Providers are discovered before native classes are initialized; constructors and family()
 * must not access the packet library. Only the selected binding inspects the loaded codec.
 */
public interface ProtocolWorkerBinding {
	/**
	 * Returns the binding-family identifier used by the pinned support catalog.
	 */
	@NotNull String family();

	/**
	 * Reads the exact protocol number from the selected native library.
	 */
	int protocolNumber();

	/**
	 * Creates an initially disconnected session for one worker-owned player.
	 *
	 * @param context player identity, private credentials, observations, and packet dispatch
	 * @return native connection owned by the worker
	 */
	@NotNull ProtocolWorkerSession create(@NotNull ProtocolWorkerContext context);
}

package me.whereareiam.anvil.protocol.adapter.api.capability;

import org.jetbrains.annotations.NotNull;

/**
 * Selects an adapter before loading its packet-library implementation.
 * Provider constructors and supports() must not reference native packet types. This allows one
 * capability bundle to carry adapters compiled against incompatible library generations safely.
 */
public interface ProtocolCapabilityAdapterProvider {
	/**
	 * Reports compatibility using the verified native protocol number without loading packet classes.
	 *
	 * @param protocolNumber selected protocol number
	 * @return whether this provider can construct its adapter
	 */
	boolean supports(int protocolNumber);

	/**
	 * Constructs the native adapter after compatibility has been established.
	 *
	 * @return adapter for the selected library
	 */
	@NotNull ProtocolCapabilityAdapter create();
}

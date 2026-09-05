package me.whereareiam.anvil.protocol.adapter.api.capability;

import org.jetbrains.annotations.NotNull;

/**
 * Service-provider contract installed into each isolated protocol worker.
 */
public interface ProtocolCapabilityAdapter {
	/**
	 * Returns the capability ID shared with its host-side provider.
	 *
	 * @return stable capability ID
	 */
	@NotNull String id();

	/**
	 * Reports whether this capability can bind to an exact Minecraft protocol.
	 *
	 * <p>Returning {@code false} keeps the capability out of that worker. Its host provider then fails
	 * capability creation with the installed capability set instead of executing an incompatible
	 * packet binding.</p>
	 *
	 * @param protocolNumber exact Minecraft protocol number
	 * @return {@code true} when this provider supports the protocol
	 */
	default boolean supports(int protocolNumber) {
		return true;
	}

	/**
	 * Registers namespaced operations and packet listeners.
	 *
	 * @param registry worker registry
	 */
	void install(@NotNull ProtocolCapabilityAdapterRegistry registry);
}

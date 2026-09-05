package me.whereareiam.anvil.protocol.adapter.api.capability;

import org.jetbrains.annotations.NotNull;

/**
 * Mutable worker registry available only while a capability is installed.
 */
public interface ProtocolCapabilityAdapterRegistry {
	/**
	 * Registers one globally unique namespaced operation.
	 *
	 * @param operation operation name
	 * @param handler operation handler
	 */
	void operation(@NotNull String operation, @NotNull ProtocolWorkerOperation handler);

	/**
	 * Registers a listener invoked for every packet received by every worker player.
	 *
	 * @param listener packet listener
	 */
	void packets(@NotNull ProtocolPacketListener listener);
}

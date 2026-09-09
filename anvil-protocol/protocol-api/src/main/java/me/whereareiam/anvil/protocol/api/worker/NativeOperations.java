package me.whereareiam.anvil.protocol.api.worker;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Registers opaque named-message operations during creation of a native player.
 */
public interface NativeOperations {
	/**
	 * Registers a unique namespaced operation. Payload schemas and codecs belong to its owner.
	 * @param operation namespaced operation ID
	 * @param handler encoded request-to-response function
	 */
	void register(@NotNull String operation, @NotNull Function<byte[], byte[]> handler);
}

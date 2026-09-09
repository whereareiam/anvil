package me.whereareiam.anvil.protocol.api.worker;

import org.jetbrains.annotations.NotNull;

/**
 * Service-loaded assembly bridge installing extensions for an exact native worker runtime.
 * @param <B> actual external SDK context type
 */
public interface NativeWorkerProvider<B> {
	/**
	 * Returns the unique provider ID.
	 * @return provider ID
	 */
	@NotNull String id();
	/**
	 * Returns the backend this provider targets.
	 * @return backend ID
	 */
	@NotNull String backendId();
	/**
	 * Returns the actual external SDK context class required by the provider.
	 * @return native context class
	 */
	@NotNull Class<B> backendType();
	/**
	 * Prepares extension installation for an exact Minecraft protocol before player creation.
	 * @param protocolNumber exact Minecraft protocol number
	 * @return prepared worker extension
	 */
	@NotNull NativeWorkerExtension<B> create(int protocolNumber);
}

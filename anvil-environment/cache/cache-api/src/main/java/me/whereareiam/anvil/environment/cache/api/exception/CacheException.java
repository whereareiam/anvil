package me.whereareiam.anvil.environment.cache.api.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Reports failure to acquire, use, or release shared cache storage.
 */
public class CacheException extends RuntimeException {
	/**
	 * Describes a failed cache operation.
	 *
	 * @param message operation and failure context
	 */
	public CacheException(@NotNull String message) {
		super(message);
	}

	/**
	 * Describes a failed cache operation while preserving its original cause.
	 *
	 * @param message operation and failure context
	 * @param cause original failure
	 */
	public CacheException(@NotNull String message, @NotNull Throwable cause) {
		super(message, cause);
	}
}

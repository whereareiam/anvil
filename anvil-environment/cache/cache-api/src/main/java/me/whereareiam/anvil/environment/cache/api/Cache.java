package me.whereareiam.anvil.environment.cache.api;

import me.whereareiam.anvil.environment.cache.api.model.CacheKey;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Locates cache entries and grants exclusive access to their stored contents.
 *
 * <p>The cache has no independent close operation. Every acquired entry owns its access lifetime
 * and must be closed after the caller's reads and writes finish.</p>
 */
public interface Cache {
	/**
	 * Resolves a logical key without opening the entry or creating its contents.
	 *
	 * @param key namespace, opaque value, and optional filename suffix
	 * @return filesystem location selected for that key
	 */
	@NotNull Path path(@NotNull CacheKey key);

	/**
	 * Waits for exclusive entry access within this JVM and across cooperating processes.
	 *
	 * <p>The entry path may identify a cached file, a directory, or prepared data that does not
	 * exist yet. Use and close the returned entry on the acquiring thread. Merely opening an entry
	 * does not create its payload.</p>
	 *
	 * @param entry location whose complete read or publication operation is protected
	 * @return caller-owned entry access
	 */
	@NotNull CacheEntry open(@NotNull Path entry);

	/**
	 * Resolves a logical key and acquires its entry access.
	 *
	 * @param key logical cache entry key
	 * @return caller-owned entry access
	 */
	default @NotNull CacheEntry open(@NotNull CacheKey key) {
		return open(path(key));
	}
}

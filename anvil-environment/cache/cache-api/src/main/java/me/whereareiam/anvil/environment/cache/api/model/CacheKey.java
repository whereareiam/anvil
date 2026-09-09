package me.whereareiam.anvil.environment.cache.api.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Identifies a cache entry within a relative namespace without exposing its filename encoding.
 *
 * <p>The namespace selects a directory below the cache root. The opaque value identifies its
 * contents; the optional suffix is a filename extension such as ".json", not a relative path.</p>
 */
@Value
@Builder
public class CacheKey {
	@NotNull String namespace;
	@NotNull String value;
	@NotNull
	@Builder.Default
	String suffix = "";
}

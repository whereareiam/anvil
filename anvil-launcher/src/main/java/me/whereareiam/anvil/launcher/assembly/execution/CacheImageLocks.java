package me.whereareiam.anvil.launcher.assembly.execution;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.execution.api.image.ImageLease;
import me.whereareiam.anvil.environment.execution.api.image.ImageLocks;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Coordinates Docker image preparation through shared cache leases without owning image policy.
 */
@RequiredArgsConstructor
public final class CacheImageLocks implements ImageLocks {
	private final @NotNull Cache cache;

	@Override
	public @NotNull ImageLease acquire(@NotNull Path image) throws IOException {
		try {
			var entry = cache.open(image);

			return () -> {
				try {
					entry.close();
				} catch (CacheException failure) {
					throw new IOException("Cannot release image access " + image, failure);
				}
			};
		} catch (CacheException failure) {
			throw new IOException("Cannot acquire image access " + image, failure);
		}
	}
}

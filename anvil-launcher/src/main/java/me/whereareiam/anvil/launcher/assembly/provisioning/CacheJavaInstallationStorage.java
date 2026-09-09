package me.whereareiam.anvil.launcher.assembly.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.provisioning.java.api.installatiion.JavaInstallationAccess;
import me.whereareiam.anvil.environment.provisioning.java.api.installatiion.JavaInstallationStorage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Holds a cache lease for the complete Java installation inspection and preparation operation.
 */
@RequiredArgsConstructor
final class CacheJavaInstallationStorage implements JavaInstallationStorage {
	private final @NotNull Cache cache;

	@Override
	public @NotNull JavaInstallationAccess acquire(@NotNull Path installation) throws IOException {
		try {
			CacheEntry entry = cache.open(installation);
			return () -> {
				try {
					entry.close();
				} catch (CacheException failure) {
					throw new IOException("Cannot release Java installation " + installation, failure);
				}
			};
		} catch (CacheException failure) {
			throw new IOException("Cannot acquire Java installation " + installation, failure);
		}
	}
}

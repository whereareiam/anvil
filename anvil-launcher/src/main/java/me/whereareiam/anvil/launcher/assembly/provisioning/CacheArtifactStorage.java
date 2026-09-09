package me.whereareiam.anvil.launcher.assembly.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.provisioning.artifact.api.storage.ArtifactContent;
import me.whereareiam.anvil.environment.provisioning.artifact.api.storage.ArtifactEntry;
import me.whereareiam.anvil.environment.provisioning.artifact.api.storage.ArtifactStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Binds verified artifact publication to exclusive cache file transactions.
 */
@RequiredArgsConstructor
public final class CacheArtifactStorage implements ArtifactStorage {
	private final @NotNull Cache cache;

	@Override
	public @NotNull ArtifactEntry open(@NotNull Path destination) throws IOException {
		try {
			return new Entry(cache.open(destination));
		} catch (CacheException failure) {
			throw new IOException("Cannot acquire artifact storage " + destination, failure);
		}
	}

	@RequiredArgsConstructor
	private static final class Entry implements ArtifactEntry {
		private final @NotNull CacheEntry entry;

		@Override
		public @NotNull Path path() {
			return entry.path();
		}

		@Override
		public <T> @Nullable T replace(@NotNull ArtifactContent<T> content) throws IOException {
			try (var write = entry.stageFile()) {
				T result = content.write(write.path());
				write.commit();

				return result;
			} catch (CacheException failure) {
				throw new IOException("Cannot publish artifact " + path(), failure);
			}
		}

		@Override
		public void close() throws IOException {
			try {
				entry.close();
			} catch (CacheException failure) {
				throw new IOException("Cannot release artifact storage " + path(), failure);
			}
		}
	}
}

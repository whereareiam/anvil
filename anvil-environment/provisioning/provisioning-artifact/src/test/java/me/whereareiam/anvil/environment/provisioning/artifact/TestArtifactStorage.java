package me.whereareiam.anvil.environment.provisioning.artifact;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.cache.api.Cache;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.CacheWrite;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.provisioning.artifact.api.storage.ArtifactContent;
import me.whereareiam.anvil.environment.provisioning.artifact.api.storage.ArtifactEntry;
import me.whereareiam.anvil.environment.provisioning.artifact.api.storage.ArtifactStorage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

@RequiredArgsConstructor
final class TestArtifactStorage implements ArtifactStorage {
	private final Cache cache;

	@Override
	public @NotNull ArtifactEntry open(@NotNull Path destination) throws IOException {
		try {
			return new Entry(cache.open(destination));
		} catch (CacheException failure) {
			throw new IOException("Could not access artifact storage", failure);
		}
	}

	@RequiredArgsConstructor
	private static final class Entry implements ArtifactEntry {
		private final CacheEntry entry;

		@Override
		public @NotNull Path path() {
			return entry.path();
		}

		@Override
		public <T> T replace(@NotNull ArtifactContent<T> content) throws IOException {
			try (CacheWrite write = entry.stageFile()) {
				T result = content.write(write.path());
				write.commit();
				return result;
			}
		}

		@Override
		public void close() throws IOException {
			try {
				entry.close();
			} catch (CacheException failure) {
				throw new IOException("Could not release artifact storage", failure);
			}
		}
	}
}

package me.whereareiam.anvil.api.model.workspace;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Selects the local file or named build artifact from which a workspace asset is copied.
 */
@Value
@Builder
public class AssetSource {
	@Nullable Path path;
	@Nullable String artifactReference;

	/**
	 * Creates a source backed by a local file or directory.
	 *
	 * @param path source path
	 * @return local asset source
	 */
	public static @NotNull AssetSource path(@NotNull Path path) {
		return builder().path(Objects.requireNonNull(path, "path")).build();
	}

	/**
	 * Creates a source backed by an artifact registered in the engine options or build-tool plugin.
	 *
	 * @param reference named artifact reference
	 * @return named artifact source
	 */
	public static @NotNull AssetSource artifact(@NotNull String reference) {
		return builder().artifactReference(Objects.requireNonNull(reference, "reference")).build();
	}

	/**
	 * Returns whether this source resolves to a local path.
	 *
	 * @return {@code true} for a local path source
	 */
	public boolean isPath() {
		return path != null;
	}

	/**
	 * Returns whether this source resolves through a named artifact reference.
	 *
	 * @return {@code true} for an artifact source
	 */
	public boolean isArtifact() {
		return artifactReference != null;
	}
}

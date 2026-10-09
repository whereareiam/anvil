package me.whereareiam.anvil.api.model.workspace;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Selects what a workspace asset installs: a local file or directory, a named build artifact, or inline text.
 */
@Value
@Builder
public class AssetSource {
	@Nullable Path path;
	@Nullable String artifactReference;
	@Nullable String text;

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
	 * Creates a source whose content is written to the asset's target as a UTF-8 file. Use it for small
	 * generated files, such as a configuration file a scenario builds from its settings.
	 *
	 * <pre>{@code
	 * WorkspaceAsset.builder()
	 *         .source(AssetSource.text("motd: Anvil\n"))
	 *         .target(Path.of("plugins", "example", "config.yml"))
	 *         .build();
	 * }</pre>
	 *
	 * @param text file content
	 * @return inline text source
	 */
	public static @NotNull AssetSource text(@NotNull String text) {
		return builder().text(Objects.requireNonNull(text, "text")).build();
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

	/**
	 * Returns whether this source carries its content inline.
	 *
	 * @return {@code true} for an inline text source
	 */
	public boolean isText() {
		return text != null;
	}

	/**
	 * Returns how many of the three kinds of source this value declares; a valid source declares exactly one.
	 *
	 * @return number of declared sources
	 */
	public int declaredSources() {
		return (path == null ? 0 : 1) + (artifactReference == null ? 0 : 1) + (text == null ? 0 : 1);
	}
}

package me.whereareiam.anvil.api.model.process;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Selects the executable distribution for a Minecraft server or proxy.
 * A distribution is resolved from its version and build or content checksum, supplied as a local
 * JAR, or obtained from a named build-tool artifact mapping.
 */
@Value
@Builder(toBuilder = true)
public class Distribution {
	@Nullable String version;
	@Nullable String build;
	@Nullable Path localJar;
	@Nullable String artifactReference;
	@Nullable String sha256;

	/**
	 * Creates a pinned remote distribution.
	 *
	 * @param version platform or Minecraft version
	 * @param build   immutable provider build identifier
	 * @return distribution selection
	 */
	public static @NotNull Distribution remote(@NotNull String version, @NotNull String build) {
		return builder().version(version).build(build).build();
	}

	/**
	 * Selects a remote artifact pinned by its content instead of a supplier build identifier.
	 * The selected platform provider must support checksum-based distribution selection.
	 *
	 * @param version exact platform or Minecraft version
	 * @param sha256 expected SHA-256 of the executable artifact
	 * @return content-pinned distribution selection
	 */
	public static @NotNull Distribution pinned(@NotNull String version, @NotNull String sha256) {
		return builder().version(version).sha256(sha256).build();
	}

	/**
	 * Creates a local distribution selection.
	 *
	 * @param jar executable server or proxy JAR
	 * @return distribution selection
	 */
	public static @NotNull Distribution local(@NotNull Path jar) {
		return builder().localJar(jar).build();
	}

	/**
	 * Selects a named artifact supplied by the embedding build tool or engine options.
	 *
	 * @param reference stable artifact name
	 * @return artifact-backed distribution selection
	 */
	public static @NotNull Distribution artifact(@NotNull String reference) {
		return builder().artifactReference(reference).build();
	}

	/**
	 * Returns whether this distribution uses a user-supplied JAR.
	 *
	 * @return whether a local JAR supplies the distribution
	 */
	public boolean isLocal() {
		return localJar != null;
	}

	/**
	 * Returns whether this distribution refers to a named build artifact.
	 *
	 * @return whether a build artifact supplies the distribution
	 */
	public boolean isArtifact() {
		return artifactReference != null;
	}

	/**
	 * Returns whether this distribution intentionally tracks the provider's latest build.
	 *
	 * @return whether the build selector is {@code latest}
	 */
	public boolean isLatest() {
		return "latest".equalsIgnoreCase(build);
	}
}

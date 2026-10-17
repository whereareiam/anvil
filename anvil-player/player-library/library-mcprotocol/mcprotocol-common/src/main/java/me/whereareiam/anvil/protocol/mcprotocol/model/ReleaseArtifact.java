package me.whereareiam.anvil.protocol.mcprotocol.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * One JAR of a release's worker runtime closure, downloaded from its URL and verified against its pin.
 */
@Value
@Builder
public class ReleaseArtifact {
	/**
	 * Maven coordinate {@code group:name:version[:classifier]} the artifact was resolved from.
	 */
	@NotNull String module;
	/**
	 * Absolute download URL of the JAR.
	 */
	@NotNull URI url;
	/**
	 * Lower-case hexadecimal SHA-256 pin, or empty when the artifact is not pinned yet.
	 */
	@NotNull String sha256;

	/**
	 * Returns the JAR's file name, the last segment of its URL path.
	 *
	 * @return file name used in the release's cache directory
	 */
	public @NotNull String fileName() {
		String path = url.getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}

	/**
	 * Tests whether the artifact has a checksum pin.
	 *
	 * @return whether {@link #getSha256()} is not empty
	 */
	public boolean pinned() {
		return !sha256.isEmpty();
	}
}

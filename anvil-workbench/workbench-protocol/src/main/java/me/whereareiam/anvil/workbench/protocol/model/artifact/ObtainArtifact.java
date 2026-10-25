package me.whereareiam.anvil.workbench.protocol.model.artifact;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;

/**
 * Downloads an artifact, verifying its checksum when one is given.
 */
@Value
public class ObtainArtifact implements ArtifactRequest<ObtainedArtifact> {
	/**
	 * Artifact to download.
	 */
	@NotNull URI uri;

	/**
	 * Where in the workbench's cache it goes.
	 */
	@NotNull Path destination;

	/**
	 * Expected SHA-256, or null for an unpinned artifact.
	 */
	@Nullable String sha256;

	@Override
	public @NotNull Class<ObtainedArtifact> responseType() {
		return ObtainedArtifact.class;
	}
}

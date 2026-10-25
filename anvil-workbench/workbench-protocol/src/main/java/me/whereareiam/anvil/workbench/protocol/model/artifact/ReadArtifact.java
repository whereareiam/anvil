package me.whereareiam.anvil.workbench.protocol.model.artifact;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * Reads a document, such as a distribution catalog, through the workbench's cache.
 */
@Value
public class ReadArtifact implements ArtifactRequest<ArtifactDocument> {
	/**
	 * Document to read.
	 */
	@NotNull URI uri;

	@Override
	public @NotNull Class<ArtifactDocument> responseType() {
		return ArtifactDocument.class;
	}
}

package me.whereareiam.anvil.workbench.protocol.model.artifact;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A document the workbench read.
 */
@Value
public class ArtifactDocument {
	/**
	 * The document's content.
	 */
	@NotNull String content;
}

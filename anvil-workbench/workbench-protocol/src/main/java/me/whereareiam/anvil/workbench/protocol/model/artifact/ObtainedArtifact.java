package me.whereareiam.anvil.workbench.protocol.model.artifact;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Where the workbench keeps an artifact it obtained.
 */
@Value
public class ObtainedArtifact {
	/**
	 * Path on the workbench.
	 */
	@NotNull Path path;
}

package me.whereareiam.anvil.workbench.protocol.model.java;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * The executable of a resolved Java installation.
 */
@Value
public class ResolvedJava {
	/**
	 * Executable on the workbench.
	 */
	@NotNull Path executable;
}

package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * What a command inside a container printed.
 */
@Value
public class CommandOutput {
	/**
	 * Its exit code.
	 */
	int exitCode;

	/**
	 * What it printed.
	 */
	@NotNull String output;
}

package me.whereareiam.anvil.workbench.protocol.model.workbench;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * The workbench's answer to a greeting: its name, its roots, and the execution providers it offers.
 */
@Value
public class Greeting {
	/**
	 * Name of the workbench.
	 */
	@NotNull String name;

	/**
	 * Work root on the workbench.
	 */
	@NotNull Path workDirectory;

	/**
	 * Cache root on the workbench.
	 */
	@NotNull Path cacheDirectory;

	/**
	 * Execution providers the workbench offers.
	 */
	@NotNull List<OfferedExecution> executions;
}

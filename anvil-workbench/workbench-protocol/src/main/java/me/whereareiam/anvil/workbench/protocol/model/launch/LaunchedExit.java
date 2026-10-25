package me.whereareiam.anvil.workbench.protocol.model.launch;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a started JVM exited, and how.
 */
@Value
public class LaunchedExit {
	/**
	 * Its exit code, or null while it runs.
	 */
	@Nullable Integer exitCode;
}

package me.whereareiam.anvil.workbench.protocol.model.launch;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Waits a while for a started JVM to exit.
 */
@Value
public class AwaitLaunched implements LaunchRequest<LaunchedExit> {
	/**
	 * Handle of the JVM.
	 */
	@NotNull String handle;

	/**
	 * Longest wait in milliseconds; negative waits for the exit.
	 */
	long timeoutMillis;

	@Override
	public @NotNull Class<LaunchedExit> responseType() {
		return LaunchedExit.class;
	}
}

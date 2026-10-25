package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

/**
 * Ends a process.
 */
@Value
public class TerminateProcess implements ExecutionRequest<Done> {
	/**
	 * Handle of the running process.
	 */
	@NotNull String handle;

	/**
	 * Whether to kill it rather than ask it to stop.
	 */
	boolean force;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}

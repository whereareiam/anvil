package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Asks whether a process runs.
 */
@Value
public class CheckProcess implements ExecutionRequest<ProcessLiveness> {
	/**
	 * Handle of the running process.
	 */
	@NotNull String handle;

	@Override
	public @NotNull Class<ProcessLiveness> responseType() {
		return ProcessLiveness.class;
	}
}

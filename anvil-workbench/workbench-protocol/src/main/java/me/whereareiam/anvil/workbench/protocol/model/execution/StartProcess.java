package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import me.whereareiam.anvil.environment.execution.api.model.ProcessLaunch;
import me.whereareiam.anvil.workbench.protocol.model.Handle;
import org.jetbrains.annotations.NotNull;

/**
 * Starts a prepared process.
 */
@Value
public class StartProcess implements ExecutionRequest<Handle> {
	/**
	 * Handle of the target.
	 */
	@NotNull String target;

	/**
	 * What to start.
	 */
	@NotNull ProcessLaunch launch;

	@Override
	public @NotNull Class<Handle> responseType() {
		return Handle.class;
	}
}

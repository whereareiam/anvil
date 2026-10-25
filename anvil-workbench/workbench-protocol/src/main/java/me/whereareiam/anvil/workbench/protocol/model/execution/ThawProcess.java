package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

/**
 * Resumes a suspended process.
 */
@Value
public class ThawProcess implements ExecutionRequest<Done> {
	/**
	 * Handle of the running process.
	 */
	@NotNull String handle;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}

package me.whereareiam.anvil.workbench.protocol.model.launch;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

/**
 * Stops a started JVM.
 */
@Value
public class DestroyLaunched implements LaunchRequest<Done> {
	/**
	 * Handle of the JVM.
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

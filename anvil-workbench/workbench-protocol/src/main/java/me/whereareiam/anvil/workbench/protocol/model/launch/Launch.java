package me.whereareiam.anvil.workbench.protocol.model.launch;

import lombok.Value;
import me.whereareiam.anvil.workbench.api.model.JavaMainLaunch;
import me.whereareiam.anvil.workbench.protocol.model.Handle;
import org.jetbrains.annotations.NotNull;

/**
 * Starts a JVM on the workbench.
 */
@Value
public class Launch implements LaunchRequest<Handle> {
	/**
	 * What to start.
	 */
	@NotNull JavaMainLaunch launch;

	@Override
	public @NotNull Class<Handle> responseType() {
		return Handle.class;
	}
}

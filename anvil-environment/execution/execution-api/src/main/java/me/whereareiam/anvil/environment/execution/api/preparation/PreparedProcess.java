package me.whereareiam.anvil.environment.execution.api.preparation;

import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.process.ProcessCapability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns prepared process inputs that remain available across execution generations.
 */
public interface PreparedProcess {
	/**
	 * Configures a fresh launch using the allocated topology and retained files.
	 * Called only when starting a new execution, including the first start and every restart.
	 * All process inputs are prepared before the first launch. Calls for independent processes may overlap.
	 * The previous launch is closed and its process stopped before creating a replacement.
	 * A failing call must release any generation resources not transferred to the returned launch.
	 *
	 * @return owned command and attachment lifecycle for one generation
	 */
	@NotNull PreparedLaunch launch();

	/**
	 * Returns the logical process's capability owner, which every execution generation borrows.
	 * Composition and cleanup belong to the preparing application.
	 *
	 * @return borrowed capability owner, or null when the process has no capabilities
	 */
	default @Nullable CapabilityOwner<ProcessCapability> capabilities() {
		return null;
	}

	/**
	 * Finalizes retained inputs after all generations, targets, and the execution session are closed.
	 *
	 * @param successful whether caller work and preceding execution cleanup succeeded
	 */
	void finish(boolean successful);
}

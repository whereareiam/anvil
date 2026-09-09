package me.whereareiam.anvil.environment.execution.api.preparation;

import org.jetbrains.annotations.NotNull;

/**
 * Owns prepared process inputs that remain available across execution generations.
 */
public interface PreparedProcess {
	/**
	 * Configures a fresh launch using the allocated topology and retained files.
	 * Initial calls run in declaration order after all inputs are prepared; restarts call this again.
	 * A failing call must release any generation resources not transferred to the returned launch.
	 *
	 * @return owned command and attachment lifecycle for one generation
	 */
	@NotNull PreparedLaunch launch();

	/**
	 * Finalizes retained inputs after all generations, targets, and the execution session are closed.
	 *
	 * @param successful whether caller work and preceding execution cleanup succeeded
	 */
	void finish(boolean successful);
}

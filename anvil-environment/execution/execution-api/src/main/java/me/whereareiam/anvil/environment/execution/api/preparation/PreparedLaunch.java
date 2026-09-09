package me.whereareiam.anvil.environment.execution.api.preparation;

import me.whereareiam.anvil.environment.execution.api.model.JavaCommand;
import org.jetbrains.annotations.NotNull;

/**
 * Owns generation-specific launch values and attachments, including failed startup cleanup.
 */
public interface PreparedLaunch extends AutoCloseable {
	/**
	 * Returns the command with this generation's final runtime values.
	 *
	 * @return immutable command used once for this generation
	 */
	@NotNull JavaCommand command();

	/**
	 * Attaches dependent services after the process reports readiness.
	 * A failure prevents the generation from being exposed as successfully started.
	 */
	void started();

	/**
	 * Detaches generation services before the process stops, including after a failed startup.
	 * The process owner attempts termination even if this method throws.
	 */
	@Override
	void close();
}

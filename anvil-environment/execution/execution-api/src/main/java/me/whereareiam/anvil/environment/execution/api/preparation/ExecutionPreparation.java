package me.whereareiam.anvil.environment.execution.api.preparation;

import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Prepares process inputs after execution has allocated the complete scenario topology.
 * The caller binds file preparation and launch attachments without exposing their providers.
 */
public interface ExecutionPreparation {
	/**
	 * Opens preparation for one execution. If this fails, {@link #finish(boolean)} still runs.
	 */
	void open();

	/**
	 * Prepares inputs retained across process generations. Calls for independent processes may overlap.
	 * A failing call must release resources that were not transferred through its return value.
	 *
	 * @param process execution declaration
	 * @param target allocated execution location and endpoints
	 * @param peers complete immutable map of process names to peer-visible addresses
	 * @return owned preparation retained until execution resources are closed
	 */
	@NotNull PreparedProcess prepare(
			@NotNull ProcessSpec process,
			@NotNull ProcessTarget target,
			@NotNull Map<String, InetSocketAddress> peers
	);

	/**
	 * Finalizes the execution after processes, targets, sessions, and prepared inputs are released.
	 * This runs once even when opening or preparation failed.
	 *
	 * @param successful whether caller work and every preceding lifecycle operation succeeded
	 */
	void finish(boolean successful);
}

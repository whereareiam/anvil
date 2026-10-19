package me.whereareiam.anvil.environment.execution.api.preparation;

import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
	 * Starts what the scenario runs inside the opened execution environment beside its processes, such as its
	 * services, before any process target is prepared. {@link #closeEnvironment()} runs whenever this was called,
	 * even when it failed.
	 *
	 * @param containerNetwork the {@link me.whereareiam.anvil.environment.execution.api.ExecutionSession#containerNetwork()
	 *                         container network} of the environment, or null when its processes run on the host
	 */
	default void openEnvironment(@Nullable String containerNetwork) {
	}

	/**
	 * Stops what {@link #openEnvironment(String)} started, after every process target is closed and before the
	 * execution environment closes.
	 */
	default void closeEnvironment() {
	}

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

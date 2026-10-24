package me.whereareiam.anvil.protocol.api.library;

import me.whereareiam.anvil.protocol.api.model.WorkerLaunch;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Starts a protocol library's worker JVM where the scenario's processes run, so that its players connect to them
 * there and their game traffic never crosses to the engine's machine. The library talks to the worker over the
 * returned process's standard streams.
 */
public interface ProtocolWorkerLauncher {
	/**
	 * Starts a worker JVM.
	 *
	 * @param launch the worker's class path, main class, arguments and minimum Java version
	 * @return the started worker, owned by the caller
	 * @throws IOException when the worker cannot be started
	 * @throws IllegalStateException when no Java runtime of the required version is available
	 */
	@NotNull Process launch(@NotNull WorkerLaunch launch) throws IOException;
}

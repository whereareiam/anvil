package me.whereareiam.anvil.workbench.api;

import me.whereareiam.anvil.environment.execution.api.BoundExecutionProvider;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactResolver;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.workbench.api.model.JavaMainLaunch;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * The place a scenario's processes run: the machine's caches, downloaded artifacts, Java installations,
 * workspaces and execution providers, assembled from the environment's blocks. The host plans a scenario and
 * orchestrates its processes; everything it plans against and everything that runs is held by a workbench.
 *
 * <p>A workbench is opened for an engine and closed with it. It reports what it can offer through
 * {@link #facts()}, against which a scenario's requirements are checked before anything is prepared.</p>
 */
public interface Workbench extends AutoCloseable {
	/**
	 * Returns the name this workbench is known by, such as {@code local} for the one running in the engine's own
	 * JVM. Diagnostics and refused requirements name it.
	 *
	 * @return workbench name
	 */
	@NotNull String name();

	/**
	 * Returns what this workbench can offer now. Facts that change while the workbench is open, such as whether
	 * Docker answers, are read again on every call.
	 *
	 * @return current facts
	 */
	@NotNull WorkbenchFacts facts();

	/**
	 * Returns the execution providers this workbench runs processes with, by identifier, each bound to the
	 * workbench's own ports, image locks, Java runtimes and cache.
	 *
	 * @return the workbench's execution providers
	 */
	@NotNull Map<String, BoundExecutionProvider> executions();

	/**
	 * Returns the directory below which this workbench lays out scenario runs and their process workspaces.
	 * Like every path a workbench hands out, it names a directory on the workbench's machine.
	 *
	 * @return work root on the workbench
	 */
	@NotNull Path workDirectory();

	/**
	 * Returns the directory this workbench caches distributions, Java installations and snapshots in.
	 *
	 * @return cache root on the workbench
	 */
	@NotNull Path cacheDirectory();

	/**
	 * Makes a file or directory of the engine's machine available to the workbench's processes, such as a plugin
	 * JAR a build produced, and returns where the workbench keeps it. The engine passes no path of its own machine
	 * to the workbench in any other way.
	 *
	 * @param file existing file or directory on the engine's machine
	 * @return the same content on the workbench
	 */
	@NotNull Path store(@NotNull Path file);

	/**
	 * Makes a service on the engine's loopback reachable from the workbench's processes at the same port, such as a
	 * session server a test runs. A workbench in the engine's own JVM reaches it already; exposing a port again has
	 * no further effect.
	 *
	 * @param port loopback port the service listens on
	 * @throws IOException when the workbench cannot offer the port
	 */
	void expose(int port) throws IOException;

	/**
	 * Starts a JVM on the workbench outside managed execution, such as a protocol library's player worker, so that
	 * it reaches the scenario's processes where they run.
	 *
	 * @param launch class path on the workbench, main class, arguments and minimum Java version
	 * @return the started JVM, owned by the caller, whose standard streams reach the engine
	 * @throws IOException when it cannot be started
	 * @throws IllegalStateException when no Java runtime of the required version is available
	 */
	@NotNull Process launch(@NotNull JavaMainLaunch launch) throws IOException;

	/**
	 * Returns how this workbench downloads and stores artifacts such as distributions.
	 *
	 * @return artifact resolver of this workbench
	 */
	@NotNull ArtifactResolver artifacts();

	/**
	 * Returns the Java installations this workbench resolves, inspects and validates.
	 *
	 * @return Java provisioner of this workbench
	 */
	@NotNull JavaProvisioner java();

	/**
	 * Returns how this workbench lays out, prepares and retains process workspaces.
	 *
	 * @return workspace provisioner of this workbench
	 */
	@NotNull WorkspaceProvisioner workspaces();

	/**
	 * Releases what the workbench holds open, such as its download pool. Running processes are not its to stop;
	 * their scenarios finish them first.
	 */
	@Override
	void close();
}

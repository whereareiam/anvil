package me.whereareiam.anvil.workbench.api;

import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.environment.provisioning.artifact.api.ArtifactResolver;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.WorkspaceProvisioner;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import org.jetbrains.annotations.NotNull;

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
	 * Returns the execution providers this workbench runs processes with, by identifier.
	 *
	 * @return installed execution providers
	 */
	@NotNull Map<String, ExecutionProvider> executions();

	/**
	 * Returns the context an execution session of one scenario opens with: the workbench's own ports, image
	 * locks, Java runtimes and cache, together with the scenario's network policy.
	 *
	 * @param networkPolicy the scenario's network policy
	 * @return execution context for the scenario's sessions
	 */
	@NotNull ExecutionContext context(@NotNull NetworkPolicy networkPolicy);

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

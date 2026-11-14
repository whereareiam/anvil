package me.whereareiam.anvil.environment.provisioning.workspace.api;

import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Owns a prepared process directory until its run is finalized.
 *
 * <p>The directory and any persistent-workspace lock survive process restarts. Finalization must
 * happen after managed processes stop, so enabled snapshots observe stable source files.</p>
 */
public interface PreparedWorkspace extends AutoCloseable {
	/**
	 * Returns the absolute process working directory.
	 *
	 * @return prepared directory
	 */
	@NotNull Path workspace();

	/**
	 * Brings the workspace up to date for a process start. Preparation already installed the assets for the
	 * first start; each later start, such as a restart, installs them again from the sources it is given, so an
	 * {@link me.whereareiam.anvil.api.type.AssetInstallMode#ALWAYS ALWAYS} asset is replaced from its current
	 * source, for example a rebuilt plugin JAR, and a missing {@code SEED_ONCE} asset is seeded again. Caches and
	 * cleanups apply only at preparation and finalization.
	 *
	 * <p>The assets are the prepared plan's, in its order and with its targets and install modes; only their sources
	 * may differ. A workbench on another machine is given fresh copies of the engine's files this way, because it
	 * installs from its own copies, never from the engine's paths.</p>
	 *
	 * @param assets the workspace's assets with their current sources
	 * @throws IllegalArgumentException if the assets are not the prepared plan's
	 * @throws IllegalStateException if the workspace was finalized
	 */
	void beforeStart(@NotNull List<WorkspaceAsset> assets);

	/**
	 * Saves enabled snapshots for successful runs, applies cleanup, and releases directory ownership.
	 * All cleanup is attempted, with later failures suppressed on the first. Repeated calls do nothing.
	 *
	 * @param successful whether the scenario lifecycle completed successfully
	 */
	void finish(boolean successful);

	/**
	 * Finalizes a directly used workspace as successful.
	 * Call {@link #finish(boolean)} with false before closing when its run failed.
	 */
	@Override
	default void close() {
		finish(true);
	}
}

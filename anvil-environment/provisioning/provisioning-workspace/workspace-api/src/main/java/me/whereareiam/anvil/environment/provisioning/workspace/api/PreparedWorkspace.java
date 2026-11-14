package me.whereareiam.anvil.environment.provisioning.workspace.api;

import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRefresh;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

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
	 * Brings the workspace up to date before a generation of its process. Preparation set it up for the first
	 * generation, so that one applies nothing; each later generation, such as after a restart, installs the assets
	 * again: an {@link me.whereareiam.anvil.api.type.AssetInstallMode#ALWAYS ALWAYS} asset from its current source,
	 * for example a rebuilt plugin JAR, and a missing {@code SEED_ONCE} asset seeded again. Caches and cleanups apply
	 * only at preparation and finalization.
	 *
	 * <p>A workbench on another machine installs from its own copies of the engine's files, never from the engine's
	 * paths, so the refresh hands it a fresh copy of each file asset.</p>
	 *
	 * @param refresh what the workspace is brought up to date with
	 * @throws IllegalArgumentException if the refresh names an asset target the workspace's plan has not
	 * @throws IllegalStateException if the workspace was finalized
	 */
	void refresh(@NotNull WorkspaceRefresh refresh);

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

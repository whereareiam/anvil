package me.whereareiam.anvil.environment.provisioning.workspace.api;

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
	 * Brings the workspace up to date for a process start. Preparation already installed the assets for the
	 * first start; each later start, such as a restart, installs them again, so an
	 * {@link me.whereareiam.anvil.api.type.AssetInstallMode#ALWAYS ALWAYS} asset is replaced from its current
	 * source, for example a rebuilt plugin JAR, and a missing {@code SEED_ONCE} asset is seeded again. Caches and
	 * cleanups apply only at preparation and finalization.
	 *
	 * @throws IllegalStateException if the workspace was finalized
	 */
	void beforeStart();

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

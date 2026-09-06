package me.whereareiam.anvil.engine.provisioning.workspace;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.AssetInstallMode;
import me.whereareiam.anvil.api.type.CachePolicy;
import me.whereareiam.anvil.api.type.CleanupPhase;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns a process workspace and its persistent lock from preparation through finalization.
 * Cache storage and filesystem operations are collaborators within the workspace boundary.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkspaceSession implements AutoCloseable {
	private final Path workspace;
	private final WorkspacePlan plan;
	private final String cacheIdentity;
	private final WorkspaceFiles files;
	private final WorkspaceCacheStore cacheStore;

	private @Nullable FileChannel lockChannel;
	private boolean ownsWorkspace;
	private boolean finalized;

	/**
	 * Validates the workspace plan, acquires ownership, restores caches, and installs assets.
	 * Failed preparation finalizes only resources acquired by this attempt.
	 *
	 * @param workspaceRoot configured workspace root
	 * @param workspace process workspace directory
	 * @param declared declared assets, caches, and cleanup
	 * @param providerDefaults provider-contributed cache defaults
	 * @param cacheIdentity process/distribution identity
	 * @param cacheDirectory shared cache directory
	 * @param files confined workspace operations
	 * @return prepared workspace session
	 */
	public static @NotNull WorkspaceSession prepare(
			@NotNull Path workspaceRoot,
			@NotNull Path workspace,
			@NotNull WorkspacePlan declared,
			@NotNull List<WorkspaceCache> providerDefaults,
			@NotNull String cacheIdentity,
			@NotNull Path cacheDirectory,
			@NotNull WorkspaceFiles files
	) {
		WorkspacePlan plan = new WorkspacePlanValidator(files).validate(declared, providerDefaults);
		Path root = workspaceRoot.toAbsolutePath().normalize();
		Path directory = workspace.toAbsolutePath().normalize();
		if (!directory.startsWith(root) || directory.equals(root))
			throw new ProvisioningException("Workspace is outside the configured root: " + directory);

		WorkspaceSession session = new WorkspaceSession(
				directory,
				plan,
				WorkspaceCacheStore.identity(cacheIdentity, plan.getAssets()),
				files,
				new WorkspaceCacheStore(cacheDirectory, files)
		);

		try {
			session.acquire(root);
			session.install();

			return session;
		} catch (RuntimeException | Error failure) {
			session.rollback(failure);
			throw failure;
		}
	}

	/**
	 * Returns the absolute process workspace path.
	 *
	 * @return workspace path
	 */
	public @NotNull Path workspace() {
		return workspace;
	}

	/**
	 * Saves successful caches, performs applicable cleanup, and releases workspace ownership.
	 * Every cleanup is attempted and secondary failures are suppressed on the first failure.
	 *
	 * @param successful whether the scenario completed normally
	 */
	public synchronized void finish(boolean successful) {
		if (finalized) return;
		finalized = true;

		List<Throwable> failures = new ArrayList<>();
		if (ownsWorkspace) {
			if (successful) attempt(this::saveCaches, failures);
			for (WorkspaceCleanup cleanup : plan.getCleanups()) {
				boolean applies = cleanup.getPhase() == CleanupPhase.AFTER_STOP
						|| ((!successful || !failures.isEmpty()) && cleanup.getPhase() == CleanupPhase.ON_FAILURE);
				if (applies) attempt(() -> files.delete(workspace, cleanup.getPath()), failures);
			}
		}
		attempt(this::release, failures);
		if (failures.isEmpty()) return;

		Throwable first = failures.getFirst();
		for (Throwable failure : failures.subList(1, failures.size()))
			if (failure != first) first.addSuppressed(failure);

		if (first instanceof Error error) throw error;
		throw (RuntimeException) first;
	}

	/**
	 * Finalizes a directly used session as successful.
	 */
	@Override
	public void close() {
		finish(true);
	}

	private void acquire(Path root) {
		if (plan.getMode() == WorkspaceMode.FRESH) {
			ownsWorkspace = true;
			files.recreate(root, workspace);
			return;
		}

		try {
			Files.createDirectories(workspace);
			lockChannel = FileChannel.open(workspace.resolve(".anvil.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			if (lockChannel.tryLock() == null)
				throw new ProvisioningException("Persistent workspace is already in use: " + workspace);
			ownsWorkspace = true;
		} catch (OverlappingFileLockException failure) {
			throw new ProvisioningException("Persistent workspace is already in use: " + workspace, failure);
		} catch (IOException failure) {
			throw new ProvisioningException("Could not acquire workspace " + workspace, failure);
		}
	}

	private void saveCaches() {
		for (WorkspaceCache cache : plan.getCaches())
			if (cache.getPolicy() == CachePolicy.RESTORE_AND_SAVE || cache.getPolicy() == CachePolicy.SAVE_ONLY)
				cacheStore.save(cache, workspace, cacheIdentity);
	}

	private void install() {
		for (WorkspaceCleanup cleanup : plan.getCleanups())
			if (cleanup.getPhase() == CleanupPhase.BEFORE_START)
				files.delete(workspace, cleanup.getPath());

		for (WorkspaceCache cache : plan.getCaches())
			if (cache.getPolicy() == CachePolicy.RESTORE_AND_SAVE
					|| cache.getPolicy() == CachePolicy.RESTORE_ONLY)
				cacheStore.restore(cache, workspace, cacheIdentity);

		for (WorkspaceAsset asset : plan.getAssets()) {
			Path target = files.resolveRelative(workspace, asset.getTarget(), "Asset target");
			if (asset.getMode() == AssetInstallMode.SEED_ONCE && Files.exists(target))
				continue;
			AssetSource source = asset.getSource();
			Path sourcePath = source.getPath();
			if (sourcePath == null)
				throw new ProvisioningException("Workspace asset source must be resolved to a local path: " + source);
			if (asset.getMode() == AssetInstallMode.ALWAYS && Files.isDirectory(sourcePath))
				files.deleteAbsolute(target);
			files.copy(sourcePath, workspace, asset.getTarget());
		}
	}

	private void release() {
		if (lockChannel == null) return;

		try {
			// Closing the owning channel releases its file lock as well.
			lockChannel.close();
		} catch (IOException failure) {
			throw new ProvisioningException("Could not release persistent workspace lock", failure);
		} finally {
			lockChannel = null;
		}
	}

	private void rollback(Throwable failure) {
		try {
			finish(false);
		} catch (RuntimeException | Error cleanup) {
			if (cleanup != failure) failure.addSuppressed(cleanup);
		}
	}

	private void attempt(Runnable action, List<Throwable> failures) {
		try {
			action.run();
		} catch (RuntimeException | Error failure) {
			failures.add(failure);
		}
	}
}

package me.whereareiam.anvil.engine.provisioning;

import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.AssetInstallMode;
import me.whereareiam.anvil.api.type.CachePolicy;
import me.whereareiam.anvil.api.type.CleanupPhase;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import me.whereareiam.anvil.engine.AnvilException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns one process workspace from preparation through final cleanup and cache persistence.
 *
 * <p>The session is intentionally engine-local. Public scenarios declare a {@link WorkspacePlan};
 * platform providers only contribute default cache paths and never perform workspace lifecycle
 * operations themselves.</p>
 */
public final class WorkspaceSession implements AutoCloseable {
	private final Path workspace;
	private final WorkspacePlan plan;
	private final String cacheIdentity;
	private final String assetFingerprint;
	private final WorkspaceFiles files;
	private final WorkspaceCacheStore cacheStore;
	private final FileChannel lockChannel;
	private final FileLock lock;
	private final AtomicBoolean finalized = new AtomicBoolean();

	private WorkspaceSession(
			Path workspace,
			WorkspacePlan plan,
			String cacheIdentity,
			WorkspaceFiles files,
			WorkspaceCacheStore cacheStore,
			FileChannel lockChannel,
			FileLock lock
	) {
		this.workspace = workspace;
		this.plan = plan;
		this.cacheIdentity = cacheIdentity;
		this.assetFingerprint = new WorkspaceAssetFingerprint().compute(plan.getAssets());
		this.files = files;
		this.cacheStore = cacheStore;
		this.lockChannel = lockChannel;
		this.lock = lock;
	}

	/**
	 * Prepares a workspace by validating declarations, applying pre-start cleanup, restoring caches,
	 * and installing assets.
	 *
	 * @param workspaceRoot configured Anvil workspace root
	 * @param workspace process workspace directory
	 * @param declared user workspace plan
	 * @param providerDefaults provider-contributed cache declarations
	 * @param cacheIdentity stable process/platform/distribution identity
	 * @param cacheDirectory configured global cache directory
	 * @param files confined workspace file operations
	 * @return prepared lifecycle session
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
		Path normalizedRoot = workspaceRoot.toAbsolutePath().normalize();
		Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
		if (!normalizedWorkspace.startsWith(normalizedRoot) || normalizedWorkspace.equals(normalizedRoot))
			throw new AnvilException("Workspace is outside the configured root: " + normalizedWorkspace);

		FileChannel lockChannel = null;
		FileLock lock = null;
		WorkspaceSession session = null;
		try {
			if (plan.getMode() == WorkspaceMode.FRESH)
				files.recreate(normalizedRoot, normalizedWorkspace);
			else {
				Files.createDirectories(normalizedWorkspace);
				lockChannel = FileChannel.open(
						normalizedWorkspace.resolve(".anvil.lock"),
						StandardOpenOption.CREATE,
						StandardOpenOption.WRITE
				);
				try {
					lock = lockChannel.tryLock();
				} catch (OverlappingFileLockException e) {
					throw new AnvilException("Persistent workspace is already in use: " + normalizedWorkspace, e);
				}
				if (lock == null)
					throw new AnvilException("Persistent workspace is already in use: " + normalizedWorkspace);
			}

			WorkspaceCacheStore cacheStore = new WorkspaceCacheStore(cacheDirectory, files);
			session = new WorkspaceSession(
					normalizedWorkspace,
					plan,
					cacheIdentity,
					files,
					cacheStore,
					lockChannel,
					lock
			);
			session.prepare();
			return session;
		} catch (Exception exception) {
			if (session != null) {
				try {
					session.finish(false);
				} catch (RuntimeException ignored) {
					// Preserve the original preparation failure.
				}
			} else
				release(lock, lockChannel);
			if (exception instanceof AnvilException anvilException)
				throw anvilException;
			throw new AnvilException("Could not prepare workspace " + normalizedWorkspace, exception);
		}
	}

	/**
	 * Returns the absolute process workspace directory.
	 *
	 * @return process workspace
	 */
	public @NotNull Path workspace() {
		return workspace;
	}

	/**
	 * Finalizes this workspace. Successful completion saves enabled caches; failed startup only runs
	 * cleanup and never writes a cache snapshot.
	 *
	 * @param successful whether the scenario reached normal completion
	 */
	public void finish(boolean successful) {
		if (!finalized.compareAndSet(false, true))
			return;

		RuntimeException failure = null;
		try {
			if (successful)
				for (WorkspaceCache cache : plan.getCaches())
					if (cache.getPolicy() == CachePolicy.RESTORE_AND_SAVE
							|| cache.getPolicy() == CachePolicy.SAVE_ONLY)
						cacheStore.save(cache, workspace, identity(cache));
		} catch (RuntimeException exception) {
			failure = exception;
		}

		for (WorkspaceCleanup cleanup : plan.getCleanups()) {
			boolean applies = cleanup.getPhase() == CleanupPhase.AFTER_STOP
					|| (!successful && cleanup.getPhase() == CleanupPhase.ON_FAILURE);
			if (!applies)
				continue;
			try {
				files.delete(workspace, cleanup.getPath());
			} catch (RuntimeException exception) {
				if (failure == null)
					failure = exception;
			}
		}

		release(lock, lockChannel);
		if (failure != null)
			throw failure;
	}

	/**
	 * Finishes a directly used workspace as a successful run.
	 */
	@Override
	public void close() {
		finish(true);
	}

	private String identity(WorkspaceCache cache) {
		return switch (cache.getIdentity()) {
			case PROCESS_AND_ASSETS -> cacheIdentity + "\nassets=" + assetFingerprint;
			case PROCESS -> cacheIdentity;
		};
	}

	private void prepare() {
		for (WorkspaceCleanup cleanup : plan.getCleanups())
			if (cleanup.getPhase() == CleanupPhase.BEFORE_START)
				files.delete(workspace, cleanup.getPath());

		for (WorkspaceCache cache : plan.getCaches())
			if (cache.getPolicy() == CachePolicy.RESTORE_AND_SAVE
					|| cache.getPolicy() == CachePolicy.RESTORE_ONLY)
				cacheStore.restore(cache, workspace, identity(cache));

		for (WorkspaceAsset asset : plan.getAssets()) {
			Path target = files.resolveRelative(workspace, asset.getTarget(), "Asset target");
			if (asset.getMode() == AssetInstallMode.SEED_ONCE && Files.exists(target))
				continue;
			AssetSource source = asset.getSource();
			Path sourcePath = source.getPath();
			if (sourcePath == null)
				throw new AnvilException("Workspace asset source must be resolved to a local path: " + source);
			if (asset.getMode() == AssetInstallMode.ALWAYS && Files.isDirectory(sourcePath))
				files.deleteAbsolute(target);
			files.copy(sourcePath, workspace, asset.getTarget());
		}
	}

	private static void release(FileLock lock, FileChannel channel) {
		try {
			if (lock != null && lock.isValid())
				lock.release();
			if (channel != null && channel.isOpen())
				channel.close();
		} catch (IOException e) {
			throw new AnvilException("Could not release persistent workspace lock", e);
		}
	}
}

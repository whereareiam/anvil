package me.whereareiam.anvil.workbench.local;

import lombok.Getter;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import me.whereareiam.anvil.environment.provisioning.artifact.HttpArtifactAcquirer;
import me.whereareiam.anvil.environment.provisioning.java.JavaRuntimeResolver;
import me.whereareiam.anvil.environment.provisioning.workspace.DefaultWorkspaceProvisioner;
import me.whereareiam.anvil.workbench.local.adapter.provisioning.ArtifactJavaPackageSource;
import me.whereareiam.anvil.workbench.local.adapter.provisioning.CacheArtifactStorage;
import me.whereareiam.anvil.workbench.local.adapter.provisioning.CacheJavaInstallationStorage;
import me.whereareiam.anvil.workbench.local.adapter.provisioning.CacheWorkspaceSnapshotStore;
import org.jetbrains.annotations.NotNull;

/**
 * Constructs shared provisioning collaborators and owns the artifact acquisition pool.
 */
@Getter
final class ProvisioningServices implements AutoCloseable {
	private final @NotNull FileCache cache;
	private final @NotNull HttpArtifactAcquirer artifacts;
	private final @NotNull JavaRuntimeResolver java;
	private final @NotNull DefaultWorkspaceProvisioner workspaces;

	ProvisioningServices(@NotNull EngineOptions options) {
		cache = new FileCache(options.getCacheDirectory());
		artifacts = new HttpArtifactAcquirer(
				options.getCacheDirectory(),
				new CacheArtifactStorage(cache),
				options.isOffline(),
				options.isRefresh(),
				options.getDownloadParallelism()
		);
		try {
			java = new JavaRuntimeResolver(
					options.getCacheDirectory(),
					new ArtifactJavaPackageSource(artifacts),
					new CacheJavaInstallationStorage(cache),
					options.isDownloadJava(),
					options.isRefresh()
			);
			workspaces = new DefaultWorkspaceProvisioner(
					options.getCacheDirectory(),
					new CacheWorkspaceSnapshotStore(cache)
			);
		} catch (RuntimeException | Error failure) {
			try (artifacts) {
				throw failure;
			}
		}
	}

	@Override
	public void close() {
		artifacts.close();
	}
}

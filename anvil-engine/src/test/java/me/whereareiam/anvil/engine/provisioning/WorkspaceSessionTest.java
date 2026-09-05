package me.whereareiam.anvil.engine.provisioning;

import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.AssetInstallMode;
import me.whereareiam.anvil.api.type.CachePolicy;
import me.whereareiam.anvil.api.type.CacheIdentity;
import me.whereareiam.anvil.api.type.CleanupPhase;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import me.whereareiam.anvil.engine.AnvilException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceSessionTest {
	@TempDir
	Path temporary;

	@Test
	void installsFilesAndDirectoriesWithSeedAndAlwaysModes() throws Exception {
		Path source = Files.createFile(temporary.resolve("plugin.jar"));
		Files.writeString(source, "first");
		Path directory = Files.createDirectory(temporary.resolve("assets"));
		Files.writeString(directory.resolve("config.yml"), "one");
		Path root = temporary.resolve("work");
		Path workspace = root.resolve("scenario/run/server");
		WorkspaceFiles files = new WorkspaceFiles();
		WorkspacePlan plan = WorkspacePlan.builder()
				.mode(WorkspaceMode.PERSISTENT)
				.asset(WorkspaceAsset.builder()
						.source(AssetSource.path(source))
						.target(Path.of("plugins/test.jar"))
						.mode(AssetInstallMode.SEED_ONCE)
						.build())
				.asset(WorkspaceAsset.builder()
						.source(AssetSource.path(directory))
						.target(Path.of("config"))
						.mode(AssetInstallMode.ALWAYS)
						.build())
				.build();

		try (WorkspaceSession ignored = WorkspaceSession.prepare(
				root, workspace, plan, List.of(), "process", temporary.resolve("cache"), files)) {
			assertEquals("first", Files.readString(workspace.resolve("plugins/test.jar")));
			assertEquals("one", Files.readString(workspace.resolve("config/config.yml")));
		}

		Files.writeString(source, "second");
		Files.writeString(directory.resolve("config.yml"), "two");
		try (WorkspaceSession ignored = WorkspaceSession.prepare(
				root, workspace, plan, List.of(), "process", temporary.resolve("cache"), files)) {
			assertEquals("first", Files.readString(workspace.resolve("plugins/test.jar")));
			assertEquals("two", Files.readString(workspace.resolve("config/config.yml")));
		}
	}

	@Test
	void rejectsTraversalAndCacheCleanupOverlap() {
		WorkspaceFiles files = new WorkspaceFiles();
		Path root = temporary.resolve("work");
		WorkspacePlan traversal = WorkspacePlan.builder()
				.cleanup(WorkspaceCleanup.builder().path(Path.of("../outside")).build())
				.build();
		assertThrows(AnvilException.class, () -> WorkspaceSession.prepare(
				root, root.resolve("run"), traversal, List.of(), "process", temporary.resolve("cache"), files));

		WorkspacePlan overlap = WorkspacePlan.builder()
				.cache(WorkspaceCache.builder().path(Path.of("plugins")).build())
				.cleanup(WorkspaceCleanup.builder().path(Path.of("plugins/config.yml")).build())
				.build();
		assertThrows(AnvilException.class, () -> WorkspaceSession.prepare(
				root, root.resolve("run"), overlap, List.of(), "process", temporary.resolve("cache"), files));
	}

	@Test
	void restoresAndSavesCacheAndHonorsDisabledOverride() throws Exception {
		WorkspaceFiles files = new WorkspaceFiles();
		Path root = temporary.resolve("work");
		Path workspace = root.resolve("run/server");
		WorkspaceCache defaultCache = WorkspaceCache.builder().path(Path.of("libraries")).build();
		WorkspacePlan seed = WorkspacePlan.builder()
				.cache(WorkspaceCache.builder().path(Path.of("libraries")).policy(CachePolicy.SAVE_ONLY).build())
				.build();
		try (WorkspaceSession ignored = WorkspaceSession.prepare(
				root, workspace, seed, List.of(defaultCache), "paper|1", temporary.resolve("cache"), files)) {
			Files.createDirectories(workspace.resolve("libraries"));
			Files.writeString(workspace.resolve("libraries/library.jar"), "cached");
		}

		WorkspacePlan restore = WorkspacePlan.builder()
				.cache(WorkspaceCache.builder().path(Path.of("libraries")).policy(CachePolicy.RESTORE_ONLY).build())
				.build();
		try (WorkspaceSession ignored = WorkspaceSession.prepare(
				root, root.resolve("run2/server"), restore, List.of(), "paper|1", temporary.resolve("cache"), files)) {
			assertEquals("cached", Files.readString(root.resolve("run2/server/libraries/library.jar")));
		}

		WorkspacePlan disabled = WorkspacePlan.builder()
				.cache(WorkspaceCache.builder().path(Path.of("libraries")).policy(CachePolicy.DISABLED).build())
				.build();
		try (WorkspaceSession ignored = WorkspaceSession.prepare(
				root, root.resolve("run3/server"), disabled, List.of(defaultCache), "paper|1", temporary.resolve("cache"), files)) {
			assertFalse(Files.exists(root.resolve("run3/server/libraries/library.jar")));
		}
	}

	@Test
	void runsFailureCleanupAndLocksPersistentWorkspace() throws Exception {
		WorkspaceFiles files = new WorkspaceFiles();
		Path root = temporary.resolve("work");
		Path workspace = root.resolve("persistent/server");
		WorkspacePlan plan = WorkspacePlan.builder()
				.mode(WorkspaceMode.PERSISTENT)
				.cleanup(WorkspaceCleanup.builder().path(Path.of("temporary")).phase(CleanupPhase.ON_FAILURE).build())
				.build();
		WorkspaceSession first = WorkspaceSession.prepare(
				root, workspace, plan, List.of(), "process", temporary.resolve("cache"), files);
		Files.createDirectories(workspace.resolve("temporary"));
		Files.writeString(workspace.resolve("temporary/file"), "x");
		assertThrows(AnvilException.class, () -> WorkspaceSession.prepare(
				root, workspace, plan, List.of(), "process", temporary.resolve("cache"), files));
		first.finish(false);
		assertFalse(Files.exists(workspace.resolve("temporary")));
	}

	@Test
	void appliesFailureCleanupWhenPreparationFails() throws Exception {
		WorkspaceFiles files = new WorkspaceFiles();
		Path root = temporary.resolve("work");
		Path workspace = root.resolve("run/server");
		WorkspacePlan plan = WorkspacePlan.builder()
				.cleanup(WorkspaceCleanup.builder().path(Path.of("failure-state"))
						.phase(CleanupPhase.ON_FAILURE).build())
				.asset(WorkspaceAsset.builder()
						.source(AssetSource.path(temporary.resolve("missing.jar")))
						.target(Path.of("plugins/missing.jar"))
						.build())
				.build();
		Files.createDirectories(workspace.resolve("failure-state"));
		assertThrows(AnvilException.class, () -> WorkspaceSession.prepare(
				root, workspace, plan, List.of(), "process", temporary.resolve("cache"), files));
		assertFalse(Files.exists(workspace.resolve("failure-state")));
	}
	@Test
	void processCachesSurviveAssetEditsWhileDefaultCachesRemainIsolated() throws Exception {
		Path source = temporary.resolve("plugin.jar");
		Files.writeString(source, "first plugin");
		Path root = temporary.resolve("runs");
		Path first = root.resolve("first");
		Path cache = temporary.resolve("cache");
		WorkspacePlan plan = WorkspacePlan.builder()
				.asset(WorkspaceAsset.builder().source(AssetSource.path(source)).target(Path.of("plugins/plugin.jar")).build())
				.cache(WorkspaceCache.builder().path(Path.of("libraries")).identity(CacheIdentity.PROCESS).build())
				.cache(WorkspaceCache.builder().path(Path.of("state")).build()).build();
		try (WorkspaceSession ignored = WorkspaceSession.prepare(root, first, plan, List.of(), "paper|1", cache, new WorkspaceFiles())) {
			Files.createDirectories(first.resolve("libraries"));
			Files.writeString(first.resolve("libraries/dependency.jar"), "dependency");
			Files.createDirectories(first.resolve("state"));
			Files.writeString(first.resolve("state/account.db"), "account state");
		}
		Files.writeString(source, "rebuilt plugin");
		Path next = root.resolve("next");
		try (WorkspaceSession ignored = WorkspaceSession.prepare(root, next, plan, List.of(), "paper|1", cache, new WorkspaceFiles())) {
			assertEquals("dependency", Files.readString(next.resolve("libraries/dependency.jar")));
			assertEquals("rebuilt plugin", Files.readString(next.resolve("plugins/plugin.jar")));
			assertFalse(Files.exists(next.resolve("state/account.db")));
		}
	}

	@Test
	void processCachesStillRespectProcessIdentityKeysAndFailureOutcomes() throws Exception {
		Path root = temporary.resolve("runs");
		Path cache = temporary.resolve("cache");
		WorkspaceCache libraryCache = WorkspaceCache.builder().path(Path.of("libraries"))
				.identity(CacheIdentity.PROCESS).key("dependencies-v1").build();
		WorkspacePlan plan = WorkspacePlan.builder().cache(libraryCache).build();
		Path first = root.resolve("first");
		try (WorkspaceSession ignored = WorkspaceSession.prepare(root, first, plan, List.of(), "paper|1", cache, new WorkspaceFiles())) {
			Files.createDirectories(first.resolve("libraries"));
			Files.writeString(first.resolve("libraries/dependency.jar"), "good");
		}
		Path failed = root.resolve("failed");
		WorkspaceSession failure = WorkspaceSession.prepare(root, failed, plan, List.of(), "paper|1", cache, new WorkspaceFiles());
		Files.writeString(failed.resolve("libraries/dependency.jar"), "bad");
		failure.finish(false);
		Path restored = root.resolve("restored");
		try (WorkspaceSession ignored = WorkspaceSession.prepare(root, restored, plan, List.of(), "paper|1", cache, new WorkspaceFiles())) {
			assertEquals("good", Files.readString(restored.resolve("libraries/dependency.jar")));
		}
		Path another = root.resolve("another-version");
		try (WorkspaceSession ignored = WorkspaceSession.prepare(root, another, plan, List.of(), "paper|2", cache, new WorkspaceFiles())) {
			assertFalse(Files.exists(another.resolve("libraries/dependency.jar")));
		}
		WorkspacePlan newKey = WorkspacePlan.builder().cache(WorkspaceCache.builder().path(Path.of("libraries"))
				.identity(CacheIdentity.PROCESS).key("dependencies-v2").build()).build();
		Path keyed = root.resolve("another-key");
		try (WorkspaceSession ignored = WorkspaceSession.prepare(root, keyed, newKey, List.of(), "paper|1", cache, new WorkspaceFiles())) {
			assertFalse(Files.exists(keyed.resolve("libraries/dependency.jar")));
		}
	}

}

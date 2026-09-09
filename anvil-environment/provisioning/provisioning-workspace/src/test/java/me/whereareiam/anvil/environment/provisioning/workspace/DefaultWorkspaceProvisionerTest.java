package me.whereareiam.anvil.environment.provisioning.workspace;

import me.whereareiam.anvil.api.exception.AnvilException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.AssetInstallMode;
import me.whereareiam.anvil.api.type.CachePolicy;
import me.whereareiam.anvil.api.type.CleanupPhase;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import me.whereareiam.anvil.environment.provisioning.workspace.api.PreparedWorkspace;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRequest;
import me.whereareiam.anvil.environment.provisioning.workspace.snapshot.TestWorkspaceSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultWorkspaceProvisionerTest {
	private static final MinecraftServer PROCESS = MinecraftServer.builder()
			.name("server")
			.platform("paper")
			.distribution(Distribution.remote("1.21.11", "132"))
			.build();

	@TempDir
	Path temporary;

	private DefaultWorkspaceProvisioner workspaces;

	@BeforeEach
	void createProvisioner() {
		Path cache = temporary.resolve("cache");
		workspaces = new DefaultWorkspaceProvisioner(cache, new TestWorkspaceSnapshotStore(new FileCache(cache)));
	}

	@Test
	void installsFilesAndDirectoriesWithSeedAndAlwaysModes() throws Exception {
		Path source = Files.createFile(temporary.resolve("plugin.jar"));
		Files.writeString(source, "first");
		Path directory = Files.createDirectory(temporary.resolve("assets"));
		Files.writeString(directory.resolve("config.yml"), "one");
		Path root = temporary.resolve("work");
		Path workspace = root.resolve("scenario/run/server");
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

		try (PreparedWorkspace ignored = prepare(
				root, workspace, plan, List.of())) {
			assertEquals("first", Files.readString(workspace.resolve("plugins/test.jar")));
			assertEquals("one", Files.readString(workspace.resolve("config/config.yml")));
		}

		Files.writeString(source, "second");
		Files.writeString(directory.resolve("config.yml"), "two");
		try (PreparedWorkspace ignored = prepare(
				root, workspace, plan, List.of())) {
			assertEquals("first", Files.readString(workspace.resolve("plugins/test.jar")));
			assertEquals("two", Files.readString(workspace.resolve("config/config.yml")));
		}
	}

	@Test
	void rejectsTraversalAndCacheCleanupOverlap() {
		Path root = temporary.resolve("work");
		WorkspacePlan traversal = WorkspacePlan.builder()
				.cleanup(WorkspaceCleanup.builder().path(Path.of("../outside")).build())
				.build();
		assertThrows(AnvilException.class, () -> prepare(
				root, root.resolve("run"), traversal, List.of()));

		WorkspacePlan overlap = WorkspacePlan.builder()
				.cache(WorkspaceCache.builder().path(Path.of("plugins")).build())
				.cleanup(WorkspaceCleanup.builder().path(Path.of("plugins/config.yml")).build())
				.build();
		assertThrows(AnvilException.class, () -> prepare(
				root, root.resolve("run"), overlap, List.of()));
	}

	@Test
	void restoresAndSavesCacheAndHonorsDisabledOverride() throws Exception {
		Path root = temporary.resolve("work");
		Path workspace = root.resolve("run/server");
		WorkspaceCache defaultCache = WorkspaceCache.builder().path(Path.of("libraries")).build();
		WorkspacePlan seed = WorkspacePlan.builder()
				.cache(WorkspaceCache.builder().path(Path.of("libraries")).policy(CachePolicy.SAVE_ONLY).build())
				.build();
		try (PreparedWorkspace ignored = prepare(
				root, workspace, seed, List.of(defaultCache))) {
			Files.createDirectories(workspace.resolve("libraries"));
			Files.writeString(workspace.resolve("libraries/library.jar"), "cached");
		}

		WorkspacePlan restore = WorkspacePlan.builder()
				.cache(WorkspaceCache.builder().path(Path.of("libraries")).policy(CachePolicy.RESTORE_ONLY).build())
				.build();
		try (PreparedWorkspace ignored = prepare(
				root, root.resolve("run2/server"), restore, List.of())) {
			assertEquals("cached", Files.readString(root.resolve("run2/server/libraries/library.jar")));
		}

		WorkspacePlan disabled = WorkspacePlan.builder()
				.cache(WorkspaceCache.builder().path(Path.of("libraries")).policy(CachePolicy.DISABLED).build())
				.build();
		try (PreparedWorkspace ignored = prepare(
				root, root.resolve("run3/server"), disabled, List.of(defaultCache))) {
			assertFalse(Files.exists(root.resolve("run3/server/libraries/library.jar")));
		}
	}

	@Test
	void runsFailureCleanupAndLocksPersistentWorkspace() throws Exception {
		Path root = temporary.resolve("work");
		Path workspace = root.resolve("persistent/server");
		WorkspacePlan plan = WorkspacePlan.builder()
				.mode(WorkspaceMode.PERSISTENT)
				.cleanup(WorkspaceCleanup.builder().path(Path.of("temporary")).phase(CleanupPhase.ON_FAILURE).build())
				.build();
		PreparedWorkspace first = prepare(
				root, workspace, plan, List.of());
		Files.createDirectories(workspace.resolve("temporary"));
		Files.writeString(workspace.resolve("temporary/file"), "x");
		assertThrows(AnvilException.class, () -> prepare(
				root, workspace, plan, List.of()));
		first.finish(false);
		assertFalse(Files.exists(workspace.resolve("temporary")));
	}

	@Test
	void appliesFailureCleanupWhenPreparationFails() throws Exception {
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
		assertThrows(AnvilException.class, () -> prepare(
				root, workspace, plan, List.of()));
		assertFalse(Files.exists(workspace.resolve("failure-state")));
	}

	@Test
	void rejectedLockAttemptCannotRunCleanupInAnotherSessionsWorkspace() throws Exception {
		Path root = temporary.resolve("work");
		Path workspace = root.resolve("persistent");
		WorkspacePlan plan = WorkspacePlan.builder().mode(WorkspaceMode.PERSISTENT)
				.cleanup(WorkspaceCleanup.builder().path(Path.of("owned"))
						.phase(CleanupPhase.ON_FAILURE).build()).build();
		try (PreparedWorkspace owner = prepare(root, workspace, plan, List.of())) {
			Files.writeString(workspace.resolve("owned"), "keep");
			assertThrows(AnvilException.class,
					() -> prepare(root, workspace, plan, List.of()));
			assertEquals("keep", Files.readString(workspace.resolve("owned")));
		}
	}

	@Test
	void preparationFailureKeepsItsCauseAndReleasesLockAfterCleanupFails() throws Exception {
		Path root = temporary.resolve("work");
		Path workspace = root.resolve("persistent");
		Path outside = temporary.resolve("outside");
		Files.createDirectories(workspace);
		Files.createDirectories(outside);
		Files.writeString(outside.resolve("file"), "untouched");
		Files.createSymbolicLink(workspace.resolve("link"), outside);
		WorkspacePlan plan = WorkspacePlan.builder().mode(WorkspaceMode.PERSISTENT)
				.asset(WorkspaceAsset.builder().source(AssetSource.path(temporary.resolve("missing")))
						.target(Path.of("plugins/missing")).build())
				.cleanup(WorkspaceCleanup.builder().path(Path.of("link/file")).phase(CleanupPhase.ON_FAILURE).build())
				.build();
		AnvilException failure = assertThrows(AnvilException.class,
				() -> prepare(root, workspace, plan, List.of()));
		assertTrue(failure.getMessage().contains("Workspace asset does not exist"));
		assertEquals(1, failure.getSuppressed().length);
		assertTrue(failure.getSuppressed()[0].getMessage().contains("symbolic link"));
		assertEquals("untouched", Files.readString(outside.resolve("file")));

		WorkspacePlan retry = WorkspacePlan.builder().mode(WorkspaceMode.PERSISTENT).build();
		try (PreparedWorkspace ignored = prepare(root, workspace, retry, List.of())) {
			assertTrue(Files.exists(workspace));
		}
	}

	@Test
	void finalizationAttemptsEveryCleanupAndReleasesItsLock() throws Exception {
		Path root = temporary.resolve("work");
		Path workspace = root.resolve("persistent");
		Path outside = temporary.resolve("outside");
		Files.createDirectories(outside);
		WorkspacePlan plan = WorkspacePlan.builder().mode(WorkspaceMode.PERSISTENT)
				.cleanup(WorkspaceCleanup.builder().path(Path.of("first/file")).phase(CleanupPhase.AFTER_STOP).build())
				.cleanup(WorkspaceCleanup.builder().path(Path.of("second/file")).phase(CleanupPhase.AFTER_STOP).build())
				.cleanup(WorkspaceCleanup.builder().path(Path.of("remove")).phase(CleanupPhase.AFTER_STOP).build())
				.build();
		PreparedWorkspace session = prepare(root, workspace, plan, List.of());
		Files.createSymbolicLink(workspace.resolve("first"), outside);
		Files.createSymbolicLink(workspace.resolve("second"), outside);
		Files.writeString(workspace.resolve("remove"), "cleanup");
		AnvilException failure = assertThrows(AnvilException.class, () -> session.finish(false));
		assertEquals(1, failure.getSuppressed().length);
		assertFalse(Files.exists(workspace.resolve("remove")));
		session.finish(false);

		WorkspacePlan retry = WorkspacePlan.builder().mode(WorkspaceMode.PERSISTENT).build();
		try (PreparedWorkspace ignored = prepare(root, workspace, retry, List.of())) {
			assertTrue(Files.exists(workspace));
		}
	}

	@Test
	void restoresBeforeInstallingAssetsAndSavesOnlySuccessfulRuns() throws Exception {
		Path source = temporary.resolve("config.yml");
		Files.writeString(source, "fresh");
		Path root = temporary.resolve("work");
		WorkspacePlan plan = WorkspacePlan.builder()
				.asset(WorkspaceAsset.builder().source(AssetSource.path(source))
						.target(Path.of("data/config.yml")).build())
				.cache(WorkspaceCache.builder().path(Path.of("data")).build())
				.build();

		try (PreparedWorkspace seed = prepare(root, root.resolve("seed"), plan, List.of())) {
			Files.writeString(seed.workspace().resolve("data/config.yml"), "snapshot");
			Files.writeString(seed.workspace().resolve("data/marker"), "original");
		}

		try (PreparedWorkspace failed = prepare(root, root.resolve("failed"), plan, List.of())) {
			assertEquals("fresh", Files.readString(failed.workspace().resolve("data/config.yml")));
			assertEquals("original", Files.readString(failed.workspace().resolve("data/marker")));
			Files.writeString(failed.workspace().resolve("data/marker"), "failed");
			failed.finish(false);
		}

		try (PreparedWorkspace successful = prepare(root, root.resolve("successful"), plan, List.of())) {
			assertEquals("fresh", Files.readString(successful.workspace().resolve("data/config.yml")));
			assertEquals("original", Files.readString(successful.workspace().resolve("data/marker")));
			Files.writeString(successful.workspace().resolve("data/marker"), "successful");
		}

		try (PreparedWorkspace restored = prepare(root, root.resolve("restored"), plan, List.of())) {
			assertEquals("successful", Files.readString(restored.workspace().resolve("data/marker")));
		}
	}

	@Test
	void rejectsSymbolicDirectoriesAndAncestorsWithoutTouchingOutsideFiles() throws Exception {
		Path root = Files.createDirectory(temporary.resolve("work"));
		Path outside = Files.createDirectory(temporary.resolve("outside"));
		Files.createDirectories(outside.resolve("run"));
		Files.writeString(outside.resolve("keep"), "outside root");
		Files.writeString(outside.resolve("run/keep"), "outside run");
		Path link = Files.createSymbolicLink(root.resolve("linked"), outside);

		for (Path directory : List.of(link, link.resolve("run"))) {
			for (WorkspaceMode mode : WorkspaceMode.values())
				assertThrows(ScenarioValidationException.class, () -> prepare(root, directory,
						WorkspacePlan.builder().mode(mode).build(), List.of()));
			assertThrows(ScenarioValidationException.class, () -> workspaces.recreateRunDirectory(root, directory));
			assertThrows(ScenarioValidationException.class, () -> workspaces.deleteRunDirectory(root, directory));
			assertThrows(ScenarioValidationException.class, () -> workspaces.finishRunDirectory(root, directory, true, true));
			assertThrows(ScenarioValidationException.class, () -> workspaces.finishRunDirectory(root, directory, true, false));
			assertThrows(ScenarioValidationException.class, () -> workspaces.finishRunDirectory(root, directory, false, false));
		}

		assertEquals("outside root", Files.readString(outside.resolve("keep")));
		assertEquals("outside run", Files.readString(outside.resolve("run/keep")));
		assertFalse(Files.exists(outside.resolve(".anvil.lock")));
		assertFalse(Files.exists(outside.resolve("run/.anvil.lock")));
		assertTrue(Files.isSymbolicLink(link));
	}

	@Test
	void rejectsTheConfiguredRootAndExternalDirectoriesForEveryDirectoryOperation() throws Exception {
		Path root = Files.createDirectory(temporary.resolve("work"));
		Path outside = Files.createDirectory(temporary.resolve("outside"));
		Files.writeString(root.resolve("keep"), "root");
		Files.writeString(outside.resolve("keep"), "outside");

		for (Path directory : List.of(root, outside)) {
			for (WorkspaceMode mode : WorkspaceMode.values())
				assertThrows(ScenarioValidationException.class, () -> prepare(root, directory,
						WorkspacePlan.builder().mode(mode).build(), List.of()));
			assertThrows(ScenarioValidationException.class, () -> workspaces.recreateRunDirectory(root, directory));
			assertThrows(ScenarioValidationException.class, () -> workspaces.deleteRunDirectory(root, directory));
			assertThrows(ScenarioValidationException.class, () -> workspaces.finishRunDirectory(root, directory, true, true));
			assertThrows(ScenarioValidationException.class, () -> workspaces.finishRunDirectory(root, directory, true, false));
			assertThrows(ScenarioValidationException.class, () -> workspaces.finishRunDirectory(root, directory, false, false));
		}

		assertEquals("root", Files.readString(root.resolve("keep")));
		assertEquals("outside", Files.readString(outside.resolve("keep")));
	}

	@Test
	void permitsASymbolicConfiguredRootWhileKeepingOperationsBelowIt() throws Exception {
		Path physicalRoot = Files.createDirectory(temporary.resolve("physical-work"));
		Path root = Files.createSymbolicLink(temporary.resolve("work"), physicalRoot);
		Path run = root.resolve("scenario/run");
		workspaces.recreateRunDirectory(root, run);
		Files.writeString(run.resolve("old"), "previous run");
		workspaces.recreateRunDirectory(root, run);
		assertFalse(Files.exists(run.resolve("old")));

		for (WorkspaceMode mode : WorkspaceMode.values()) {
			Path directory = run.resolve(mode.name());
			try (PreparedWorkspace workspace = prepare(root, directory,
					WorkspacePlan.builder().mode(mode).build(), List.of())) {
				assertEquals(directory, workspace.workspace());
				Files.writeString(workspace.workspace().resolve("prepared"), "ready");
			}
			assertEquals("ready", Files.readString(physicalRoot.resolve("scenario/run")
					.resolve(mode.name()).resolve("prepared")));
		}

		workspaces.deleteRunDirectory(root, run);
		assertFalse(Files.exists(physicalRoot.resolve("scenario/run")));
		assertTrue(Files.isDirectory(physicalRoot));
		assertTrue(Files.isSymbolicLink(root));
	}

	@ParameterizedTest
	@CsvSource({"true, true, false", "true, false, false", "false, true, true", "false, false, false"})
	void finalizesRunDirectoryAccordingToOutcomeAndRetainsPersistentSiblings(
			boolean successful,
			boolean keepFailedWorkspaces,
			boolean retained
	) throws Exception {
		Path root = temporary.resolve("work");
		Path run = Files.createDirectories(root.resolve("scenario/run"));
		Path persistent = Files.createDirectories(root.resolve("scenario/persistent/server"));
		Files.writeString(run.resolve("anvil-console.log"), "diagnostics");
		Files.writeString(persistent.resolve("world.dat"), "saved world");

		workspaces.finishRunDirectory(root, run, successful, keepFailedWorkspaces);

		assertEquals(retained, Files.exists(run));
		if (retained)
			assertEquals("diagnostics", Files.readString(run.resolve("anvil-console.log")));
		assertEquals("saved world", Files.readString(persistent.resolve("world.dat")));
		workspaces.finishRunDirectory(root, run, successful, keepFailedWorkspaces);
	}

	@Test
	void runFinalizationRemovesNestedSymbolicLinksWithoutFollowingThem() throws Exception {
		Path root = temporary.resolve("work");
		Path run = Files.createDirectories(root.resolve("scenario/run"));
		Path outside = Files.createDirectory(temporary.resolve("outside"));
		Files.writeString(outside.resolve("keep"), "outside");
		Files.createSymbolicLink(run.resolve("linked"), outside);

		workspaces.finishRunDirectory(root, run, true, true);

		assertFalse(Files.exists(run));
		assertEquals("outside", Files.readString(outside.resolve("keep")));
	}

	private PreparedWorkspace prepare(Path root, Path directory, WorkspacePlan plan, List<WorkspaceCache> providerDefaults) {
		return workspaces.prepare(WorkspaceRequest.builder()
				.root(root)
				.directory(directory)
				.plan(plan)
				.providerDefaults(providerDefaults)
				.process(PROCESS)
				.build());
	}
}

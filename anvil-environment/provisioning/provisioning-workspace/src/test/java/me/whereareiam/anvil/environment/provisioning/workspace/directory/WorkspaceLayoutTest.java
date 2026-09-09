package me.whereareiam.anvil.environment.provisioning.workspace.directory;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import me.whereareiam.anvil.environment.provisioning.workspace.DefaultWorkspaceProvisioner;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import me.whereareiam.anvil.environment.provisioning.workspace.snapshot.TestWorkspaceSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceLayoutTest {
	@TempDir
	Path temporary;

	private DefaultWorkspaceProvisioner workspaces;

	@BeforeEach
	void createProvisioner() {
		Path cache = temporary.resolve("cache");
		workspaces = new DefaultWorkspaceProvisioner(cache, new TestWorkspaceSnapshotStore(new FileCache(cache)));
	}

	@Test
	void preservesPersistentPathsAndAssignsIndependentFreshRunPathsWithoutCreatingDirectories() {
		Path root = temporary.resolve("unused/../work");
		AnvilScenario scenario = scenario()
				.server(server("saved/server", WorkspaceMode.PERSISTENT))
				.server(server("fresh/server", WorkspaceMode.FRESH))
				.proxy(proxy("saved?proxy", WorkspaceMode.PERSISTENT))
				.proxy(proxy("fresh?proxy", WorkspaceMode.FRESH))
				.build();
		WorkspaceLayout first = workspaces.layout(root, scenario);
		WorkspaceLayout second = workspaces.layout(root, scenario);
		Path scenarioDirectory = temporary.resolve("work/Mixed-world");

		assertEquals(scenarioDirectory, first.getRunDirectory().getParent());
		assertTrue(first.getRunDirectory().getFileName().toString().matches("[0-9]+-[0-9a-f]{8}"));
		assertNotEquals(first.getRunDirectory(), second.getRunDirectory());
		for (WorkspaceLayout layout : List.of(first, second)) {
			assertEquals(scenarioDirectory.resolve("persistent/saved-server"), layout.processDirectory("saved/server"));
			assertEquals(scenarioDirectory.resolve("persistent/saved-proxy"), layout.processDirectory("saved?proxy"));
			assertEquals(layout.getRunDirectory().resolve("fresh-server"), layout.processDirectory("fresh/server"));
			assertEquals(layout.getRunDirectory().resolve("fresh-proxy"), layout.processDirectory("fresh?proxy"));
			assertEquals(4, layout.getProcessDirectories().size());
		}
		assertFalse(Files.exists(temporary.resolve("work")));
		assertFalse(Files.exists(temporary.resolve("unused")));
		assertFalse(Files.exists(temporary.resolve("cache")));
	}

	@ParameterizedTest
	@EnumSource(WorkspaceMode.class)
	void rejectsCollidingDirectoryNamesWithinAndAcrossProcessTypes(WorkspaceMode mode) {
		List<AnvilScenario> scenarios = List.of(
				scenario().server(server("process/a", mode)).server(server("process?a", mode)).build(),
				scenario().proxy(proxy("process/a", mode)).proxy(proxy("process?a", mode)).build(),
				scenario().server(server("process/a", mode)).proxy(proxy("process?a", mode)).build()
		);
		for (AnvilScenario scenario : scenarios) {
			ScenarioValidationException failure = assertThrows(ScenarioValidationException.class,
					() -> workspaces.layout(temporary.resolve("work"), scenario));
			assertTrue(failure.getMessage().startsWith(
					"Processes 'process/a' and 'process?a' resolve to the same workspace directory: "));
		}
		assertFalse(Files.exists(temporary.resolve("work")));
	}

	@ParameterizedTest
	@EnumSource(WorkspaceMode.class)
	void permitsMatchingDirectoryNamesWhenWorkspaceModesKeepThemSeparate(WorkspaceMode serverMode) {
		WorkspaceMode proxyMode = serverMode == WorkspaceMode.PERSISTENT ? WorkspaceMode.FRESH : WorkspaceMode.PERSISTENT;
		AnvilScenario scenario = scenario()
				.server(server("process/a", serverMode))
				.proxy(proxy("process?a", proxyMode))
				.build();
		WorkspaceLayout layout = workspaces.layout(temporary.resolve("work"), scenario);

		assertNotEquals(layout.processDirectory("process/a"), layout.processDirectory("process?a"));
		assertEquals(layout.processDirectory("process/a").getFileName(), layout.processDirectory("process?a").getFileName());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", ".", ".."})
	void rejectsUnsafeScenarioAndProcessNames(String name) {
		Path root = temporary.resolve("work");
		assertThrows(ScenarioValidationException.class,
				() -> workspaces.layout(root, scenario().name(name).build()));
		assertThrows(ScenarioValidationException.class,
				() -> workspaces.layout(root, scenario().server(server(name, WorkspaceMode.FRESH)).build()));
		assertThrows(ScenarioValidationException.class,
				() -> workspaces.layout(root, scenario().proxy(proxy(name, WorkspaceMode.PERSISTENT)).build()));
		assertFalse(Files.exists(root));
	}

	@Test
	void layoutKeepsAnImmutableSnapshotOfProcessDirectoryAssignments() {
		Path run = temporary.resolve("run");
		Path server = run.resolve("server");
		Map<String, Path> directories = new HashMap<>(Map.of("server", server));
		var builder = WorkspaceLayout.builder().runDirectory(run).processDirectories(directories);
		WorkspaceLayout layout = builder.build();
		directories.put("server", temporary.resolve("other"));
		builder.processDirectory("proxy", run.resolve("proxy"));

		assertEquals(Map.of("server", server), layout.getProcessDirectories());
		assertEquals(server, layout.processDirectory("server"));
		assertThrows(UnsupportedOperationException.class, () -> layout.getProcessDirectories().put("proxy", run.resolve("proxy")));
		assertThrows(IllegalArgumentException.class, () -> layout.processDirectory("missing"));
	}

	private AnvilScenario.AnvilScenarioBuilder scenario() {
		return AnvilScenario.builder().name("Mixed world").entrypoint("server");
	}

	private MinecraftServer server(String name, WorkspaceMode mode) {
		return MinecraftServer.builder()
				.name(name)
				.platform("paper")
				.distribution(Distribution.remote("1.21.11", "132"))
				.workspace(WorkspacePlan.builder().mode(mode).build())
				.build();
	}

	private MinecraftProxy proxy(String name, WorkspaceMode mode) {
		return MinecraftProxy.builder()
				.name(name)
				.platform("velocity")
				.distribution(Distribution.remote("3.4.0", "1"))
				.workspace(WorkspacePlan.builder().mode(mode).build())
				.server("server")
				.defaultServer("server")
				.build();
	}
}
